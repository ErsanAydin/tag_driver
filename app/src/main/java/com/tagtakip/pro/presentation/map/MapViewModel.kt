package com.tagtakip.pro.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.tagtakip.pro.domain.model.LocationPoint
import com.tagtakip.pro.domain.repository.ShiftRepository
import com.tagtakip.pro.domain.usecase.shift.GetActiveShiftUseCase
import com.tagtakip.pro.service.LocationServiceManager
import com.tagtakip.pro.service.ServiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class MapUiState(
    val currentLocation: LocationPoint? = null,
    val routePoints: List<LatLng> = emptyList(),
    val totalDistanceMeters: Float = 0f,
    val serviceState: ServiceState = ServiceState.STOPPED,
    val followUser: Boolean = true
) {
    val currentLatLng: LatLng? get() = currentLocation?.let { LatLng(it.latitude, it.longitude) }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    getActiveShift: GetActiveShiftUseCase,
    shiftRepository: ShiftRepository,
    locationServiceManager: LocationServiceManager
) : ViewModel() {

    private val followUser = MutableStateFlow(true)

    /** Aktif vardiyanın rotası (DB'den, yeni nokta eklendikçe güncellenir). */
    private val routeFlow = getActiveShift().flatMapLatest { shift ->
        if (shift == null) flowOf(emptyList())
        else shiftRepository.observeLocationPoints(shift.id).map { points ->
            simplify(points).map { LatLng(it.latitude, it.longitude) }
        }
    }

    val uiState: StateFlow<MapUiState> = combine(
        locationServiceManager.currentLocation,
        routeFlow,
        locationServiceManager.totalDistance,
        locationServiceManager.serviceState,
        followUser
    ) { location, route, distance, state, follow ->
        MapUiState(location, route, distance, state, follow)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    fun setFollowUser(enabled: Boolean) = followUser.update { enabled }

    /** Çok uzun rotalarda çizim maliyetini düşürmek için basit örnekleme. */
    private fun simplify(points: List<LocationPoint>): List<LocationPoint> {
        if (points.size <= MAX_POLYLINE_POINTS) return points
        val step = points.size / MAX_POLYLINE_POINTS + 1
        return points.filterIndexed { index, _ -> index % step == 0 } + points.last()
    }

    private companion object {
        const val MAX_POLYLINE_POINTS = 2_000
    }
}
