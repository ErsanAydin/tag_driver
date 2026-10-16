package com.tagtakip.pro.presentation.shift

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagtakip.pro.core.common.Result
import com.tagtakip.pro.domain.model.LocationPoint
import com.tagtakip.pro.domain.model.Shift
import com.tagtakip.pro.domain.model.Trip
import com.tagtakip.pro.domain.usecase.shift.EndShiftUseCase
import com.tagtakip.pro.domain.usecase.shift.GetActiveShiftUseCase
import com.tagtakip.pro.domain.usecase.shift.ShiftAlreadyActiveException
import com.tagtakip.pro.domain.usecase.shift.StartShiftUseCase
import com.tagtakip.pro.domain.usecase.trip.EndTripUseCase
import com.tagtakip.pro.domain.usecase.trip.GetTripHistoryUseCase
import com.tagtakip.pro.domain.usecase.trip.StartTripUseCase
import com.tagtakip.pro.service.LocationServiceManager
import com.tagtakip.pro.service.MovementMode
import com.tagtakip.pro.service.ServiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShiftUiState(
    val activeShift: Shift? = null,
    val activeTrip: Trip? = null,
    val serviceState: ServiceState = ServiceState.STOPPED,
    val movementMode: MovementMode? = null,
    val totalDistanceMeters: Float = 0f,
    val currentLocation: LocationPoint? = null,
    val elapsedMillis: Long = 0L,
    val isBusy: Boolean = false,
    val message: String? = null
) {
    val isShiftActive: Boolean get() = activeShift != null
}

private data class TransientState(val isBusy: Boolean = false, val message: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShiftViewModel @Inject constructor(
    private val startShiftUseCase: StartShiftUseCase,
    private val endShiftUseCase: EndShiftUseCase,
    private val getActiveShift: GetActiveShiftUseCase,
    private val startTripUseCase: StartTripUseCase,
    private val endTripUseCase: EndTripUseCase,
    private val getTripHistory: GetTripHistoryUseCase,
    private val locationServiceManager: LocationServiceManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val transient = MutableStateFlow(TransientState())

    private val activeShiftFlow = getActiveShift()

    private val activeTripFlow = activeShiftFlow.flatMapLatest { shift ->
        if (shift == null) flowOf(null) else getTripHistory.activeTrip(shift.id)
    }

    private val secondTicker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    private val serviceFlow = combine(
        locationServiceManager.serviceState,
        locationServiceManager.movementMode,
        locationServiceManager.totalDistance,
        locationServiceManager.currentLocation
    ) { state, mode, distance, location -> ServiceSnapshot(state, mode, distance, location) }

    val uiState: StateFlow<ShiftUiState> = combine(
        activeShiftFlow,
        activeTripFlow,
        serviceFlow,
        secondTicker,
        transient
    ) { shift, trip, service, now, t ->
        ShiftUiState(
            activeShift = shift,
            activeTrip = trip,
            serviceState = service.state,
            movementMode = service.mode,
            totalDistanceMeters = maxOf(service.distance, shift?.totalDistanceMeters ?: 0f),
            currentLocation = service.location,
            elapsedMillis = shift?.durationMillis(now) ?: 0L,
            isBusy = t.isBusy,
            message = t.message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShiftUiState())

    init {
        // Süreç öldürülüp servis yeniden başlamadıysa, aktif vardiya için takibi sürdür
        viewModelScope.launch {
            val shift = activeShiftFlow.first()
            if (shift != null && !locationServiceManager.isTracking && locationServiceManager.hasLocationPermission()) {
                locationServiceManager.start(shift.id)
            }
        }
    }

    fun onStartShift() = launchBusy {
        when (val result = startShiftUseCase()) {
            is Result.Success -> startTracking(result.data)
            is Result.Error -> {
                val e = result.exception
                if (e is ShiftAlreadyActiveException) startTracking(e.shiftId)
                else showMessage(result.message ?: "Mesai başlatılamadı")
            }
            Result.Loading -> Unit
        }
    }

    fun onEndShift() = launchBusy {
        val state = uiState.value
        // Devam eden yolculuk varsa ücretsiz olarak kapat
        state.activeTrip?.let { trip ->
            endTripUseCase(trip.id, fare = 0.0, distanceMeters = tripDistance(state), endLocation = state.currentLocation)
        }
        val distance = locationServiceManager.totalDistance.value
        locationServiceManager.stop()
        when (val result = endShiftUseCase(totalDistanceMeters = distance)) {
            is Result.Success -> showMessage("Mesai tamamlandı")
            is Result.Error -> showMessage(result.message ?: "Mesai bitirilemedi")
            Result.Loading -> Unit
        }
    }

    fun onPauseTracking() = locationServiceManager.pause()

    fun onResumeTracking() = locationServiceManager.resume()

    fun onStartTrip(platform: String? = null) = launchBusy {
        val location = locationServiceManager.currentLocation.value
        when (val result = startTripUseCase(startLocation = location, platform = platform)) {
            is Result.Success -> savedStateHandle[KEY_TRIP_START_DISTANCE] = locationServiceManager.totalDistance.value
            is Result.Error -> showMessage(result.message ?: "Yolculuk başlatılamadı")
            Result.Loading -> Unit
        }
    }

    fun onEndTrip(fare: Double) = launchBusy {
        val state = uiState.value
        val trip = state.activeTrip ?: return@launchBusy showMessage("Aktif yolculuk yok")
        when (val result = endTripUseCase(trip.id, fare, tripDistance(state), state.currentLocation)) {
            is Result.Success -> {
                savedStateHandle.remove<Float>(KEY_TRIP_START_DISTANCE)
                showMessage("Yolculuk kaydedildi")
            }
            is Result.Error -> showMessage(result.message ?: "Yolculuk bitirilemedi")
            Result.Loading -> Unit
        }
    }

    fun consumeMessage() = transient.update { it.copy(message = null) }

    private fun startTracking(shiftId: Long) {
        if (!locationServiceManager.start(shiftId)) {
            showMessage("Konum izni olmadan takip başlatılamaz")
        }
    }

    private fun tripDistance(state: ShiftUiState): Float {
        val startDistance = savedStateHandle.get<Float>(KEY_TRIP_START_DISTANCE) ?: state.totalDistanceMeters
        return (state.totalDistanceMeters - startDistance).coerceAtLeast(0f)
    }

    private fun showMessage(text: String) = transient.update { it.copy(message = text) }

    private fun launchBusy(block: suspend () -> Unit) {
        if (transient.value.isBusy) return
        viewModelScope.launch {
            transient.update { it.copy(isBusy = true) }
            try {
                block()
            } finally {
                transient.update { it.copy(isBusy = false) }
            }
        }
    }

    private data class ServiceSnapshot(
        val state: ServiceState,
        val mode: MovementMode?,
        val distance: Float,
        val location: LocationPoint?
    )

    private companion object {
        const val KEY_TRIP_START_DISTANCE = "trip_start_distance"
    }
}
