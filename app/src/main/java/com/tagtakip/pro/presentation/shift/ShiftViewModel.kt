package com.tagtakip.pro.presentation.shift

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagtakip.pro.core.common.Result
import com.tagtakip.pro.domain.model.LocationPoint
import com.tagtakip.pro.domain.model.Shift
import com.tagtakip.pro.domain.model.Trip
import com.tagtakip.pro.domain.repository.ShiftRepository
import com.tagtakip.pro.domain.usecase.shift.EndShiftUseCase
import com.tagtakip.pro.domain.usecase.shift.GetActiveShiftUseCase
import com.tagtakip.pro.domain.usecase.shift.ShiftAlreadyActiveException
import com.tagtakip.pro.domain.usecase.shift.StartShiftUseCase
import com.tagtakip.pro.domain.usecase.trip.EndTripUseCase
import com.tagtakip.pro.domain.usecase.trip.GetTripHistoryUseCase
import com.tagtakip.pro.domain.usecase.trip.StartTripUseCase
import com.tagtakip.pro.domain.util.RouteDistance
import com.tagtakip.pro.service.LocationServiceManager
import com.tagtakip.pro.service.MovementMode
import com.tagtakip.pro.service.OverlayServiceManager
import com.tagtakip.pro.service.ServiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

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

/** Bitirilmek üzere olan yolculuğun özeti (TripSummaryDialog'da gösterilir). */
data class TripSummary(
    val tripId: Long,
    val startTime: Long,
    val endTime: Long,
    val distanceMeters: Float,
    val endLocation: LocationPoint?
) {
    val distanceKm: Float get() = distanceMeters / 1000f
    val durationMillis: Long get() = (endTime - startTime).coerceAtLeast(0L)
}

private data class TransientState(val isBusy: Boolean = false, val message: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShiftViewModel @Inject constructor(
    private val startShiftUseCase: StartShiftUseCase,
    private val endShiftUseCase: EndShiftUseCase,
    getActiveShift: GetActiveShiftUseCase,
    private val startTripUseCase: StartTripUseCase,
    private val endTripUseCase: EndTripUseCase,
    private val getTripHistory: GetTripHistoryUseCase,
    private val shiftRepository: ShiftRepository,
    private val locationServiceManager: LocationServiceManager,
    private val overlayServiceManager: OverlayServiceManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val transient = MutableStateFlow(TransientState())

    private val activeShiftFlow = getActiveShift()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val activeTripFlow = activeShiftFlow
        .flatMapLatest { shift -> if (shift == null) flowOf(null) else getTripHistory.activeTrip(shift.id) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val secondTicker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** Yolculuk başladığında servisin vardiya mesafesi; yolculuk mesafesi = güncel mesafe - bu değer. */
    private val tripStartDistance = MutableStateFlow(savedStateHandle.get<Float>(KEY_TRIP_START_DISTANCE))

    private val _tripSummary = MutableStateFlow<TripSummary?>(null)
    /** null değilse TripSummaryDialog gösterilir. */
    val tripSummary: StateFlow<TripSummary?> = _tripSummary.asStateFlow()

    val isTripActive: StateFlow<Boolean> = activeTripFlow
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val tripStartTime: StateFlow<Long?> = activeTripFlow
        .map { it?.startTime }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val currentTripDistanceKm: StateFlow<Float> = combine(
        activeTripFlow,
        locationServiceManager.totalDistance,
        tripStartDistance
    ) { trip, total, start ->
        if (trip == null || start == null) 0f
        else ((total - start).coerceAtLeast(0f) / 1000f * 100f).roundToInt() / 100f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    val currentTripDurationMinutes: StateFlow<Int> = combine(activeTripFlow, secondTicker) { trip, now ->
        trip?.let { ((now - it.startTime).coerceAtLeast(0L) / 60_000L).toInt() } ?: 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Aktif yolculuğun saniye hassasiyetinde geçen süresi (ekrandaki sayaç için). */
    val currentTripElapsedMillis: StateFlow<Long> = combine(activeTripFlow, secondTicker) { trip, now ->
        trip?.let { (now - it.startTime).coerceAtLeast(0L) } ?: 0L
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

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
            // Aktif vardiya varsa sistem overlay tuşunu da geri getir
            if (shift != null) overlayServiceManager.start()
        }
        // Yolculuk başlangıç mesafesi kaybolduysa (ör. süreç öldürüldü) DB'deki rota noktalarından yeniden hesapla
        viewModelScope.launch {
            activeTripFlow.distinctUntilChanged { a, b -> a?.id == b?.id }.collect { trip ->
                if (trip == null) {
                    if (_tripSummary.value == null) setTripStartDistance(null)
                } else if (tripStartDistance.value == null) {
                    val points = shiftRepository.observeLocationPoints(trip.shiftId).first()
                        .filter { it.timestamp >= trip.startTime }
                    val travelled = RouteDistance.total(points)
                    setTripStartDistance((currentShiftDistance() - travelled).coerceAtLeast(0f))
                }
            }
        }
    }

    fun onStartShift() = launchBusy {
        ensureShiftAndTracking()
    }

    fun onEndShift() = launchBusy {
        val state = uiState.value
        // Devam eden yolculuk varsa kazançsız olarak kapat
        state.activeTrip?.let { trip ->
            endTripUseCase(trip.id, distanceMeters = tripDistanceMeters(), endLocation = state.currentLocation)
        }
        _tripSummary.value = null
        setTripStartDistance(null)
        val distance = locationServiceManager.totalDistance.value
        locationServiceManager.stop()
        overlayServiceManager.stop()
        when (val result = endShiftUseCase(totalDistanceMeters = distance)) {
            is Result.Success -> showMessage("Mesai tamamlandı")
            is Result.Error -> showMessage(result.message ?: "Mesai bitirilemedi")
            Result.Loading -> Unit
        }
    }

    fun onPauseTracking() = locationServiceManager.pause()

    fun onResumeTracking() = locationServiceManager.resume()

    /** Yolculuk başlatır. Mesai kapalıysa önce mesaiyi açar; GPS takibi çalışmıyorsa başlatır / devam ettirir. */
    fun onStartTrip() = launchBusy {
        if (!ensureShiftAndTracking()) return@launchBusy
        if (locationServiceManager.serviceState.value == ServiceState.PAUSED) locationServiceManager.resume()

        val location = locationServiceManager.currentLocation.value
        when (val result = startTripUseCase(startLocation = location)) {
            is Result.Success -> {
                setTripStartDistance(currentShiftDistance())
                showMessage("Yolculuk başladı")
            }
            is Result.Error -> showMessage(result.message ?: "Yolculuk başlatılamadı")
            Result.Loading -> Unit
        }
    }

    /** "Yolculuğu Bitir": değerleri dondurur ve özet dialog'unu açar. Kayıt dialog'daki seçime göre yapılır. */
    fun onEndTripRequested() {
        val trip = uiState.value.activeTrip ?: return showMessage("Aktif yolculuk yok")
        openTripSummary(trip)
    }

    /**
     * Sistem overlay tuşundan (MainActivity'deki BroadcastReceiver üzerinden) gelen komut.
     * Uygulama arka plandayken uiState akışı durmuş olabileceğinden durum DB'den taze okunur.
     */
    fun onOverlayTripAction(startTrip: Boolean) {
        if (transient.value.isBusy || _tripSummary.value != null) return
        viewModelScope.launch {
            val shift = shiftRepository.getActiveShift()
            val trip = shift?.let { getTripHistory.activeTrip(it.id).first() }
            when {
                startTrip && trip != null -> showMessage("Yolculuk zaten devam ediyor")
                startTrip -> onStartTrip()
                trip == null -> showMessage("Aktif yolculuk yok")
                else -> openTripSummary(trip)
            }
        }
    }

    /** Overlay izni ayarlardan verilip uygulamaya dönülünce: vardiya aktifse overlay'i başlat. */
    fun onOverlayPermissionChanged() {
        if (!overlayServiceManager.canDrawOverlays()) return
        viewModelScope.launch {
            if (shiftRepository.getActiveShift() != null) overlayServiceManager.start()
        }
    }

    private fun openTripSummary(trip: Trip) {
        _tripSummary.value = TripSummary(
            tripId = trip.id,
            startTime = trip.startTime,
            endTime = System.currentTimeMillis(),
            distanceMeters = tripDistanceMeters(),
            endLocation = locationServiceManager.currentLocation.value
        )
    }

    /** "Kaydet": yolculuk + (tutar girildiyse) INCOME kazancı kaydedilir. */
    fun onTripSummarySave(amount: Double?, description: String?) = finishTrip(amount, description)

    /** "Atla": yalnızca yolculuk kaydedilir. */
    fun onTripSummarySkip() = finishTrip(amount = null, description = null)

    fun consumeMessage() = transient.update { it.copy(message = null) }

    private fun finishTrip(amount: Double?, description: String?) = launchBusy {
        val summary = _tripSummary.value ?: return@launchBusy
        val result = endTripUseCase(
            tripId = summary.tripId,
            distanceMeters = summary.distanceMeters,
            endLocation = summary.endLocation,
            endTime = summary.endTime,
            earningAmount = amount,
            description = description
        )
        when (result) {
            is Result.Success -> {
                _tripSummary.value = null
                setTripStartDistance(null)
                showMessage(if (result.data.earningAmount != null) "Yolculuk ve kazanç kaydedildi" else "Yolculuk kaydedildi")
            }
            is Result.Error -> showMessage(result.message ?: "Yolculuk kaydedilemedi")
            Result.Loading -> Unit
        }
    }

    /** Aktif mesai yoksa başlatır, takip kapalıysa açar. Başarısızsa false döner. */
    private suspend fun ensureShiftAndTracking(): Boolean {
        val shiftId = activeShiftFlow.first()?.id ?: when (val result = startShiftUseCase()) {
            is Result.Success -> result.data
            is Result.Error -> (result.exception as? ShiftAlreadyActiveException)?.shiftId
                ?: return false.also { showMessage(result.message ?: "Mesai başlatılamadı") }
            Result.Loading -> return false
        }
        if (!locationServiceManager.isTracking && !locationServiceManager.start(shiftId)) {
            showMessage("Konum izni olmadan takip başlatılamaz")
            return false
        }
        // İzin yoksa sessizce atlanır; Mesai ekranında izin kartı gösterilir
        overlayServiceManager.start()
        return true
    }

    /** Servis yeni başlarken mesafesini DB'den geri yükleyene kadar 0 görünebilir; DB değeriyle korunur. */
    private fun currentShiftDistance(): Float =
        maxOf(locationServiceManager.totalDistance.value, uiState.value.activeShift?.totalDistanceMeters ?: 0f)

    private fun tripDistanceMeters(): Float {
        val start = tripStartDistance.value ?: return 0f
        return (locationServiceManager.totalDistance.value - start).coerceAtLeast(0f)
    }

    private fun setTripStartDistance(value: Float?) {
        tripStartDistance.value = value
        if (value == null) savedStateHandle.remove<Float>(KEY_TRIP_START_DISTANCE)
        else savedStateHandle[KEY_TRIP_START_DISTANCE] = value
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
