package com.tagtakip.pro.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagtakip.pro.core.util.DateTimeUtils
import com.tagtakip.pro.domain.repository.ShiftRepository
import com.tagtakip.pro.service.LocationServiceManager
import com.tagtakip.pro.service.ServiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * GPS mesafe sayacı ViewModel'i.
 * LocationTrackingService'in ham konum/mesafe akışlarını (metre) dinler ve km'ye çevirir.
 * Harita veya API anahtarı gerektirmez.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DistanceViewModel @Inject constructor(
    locationServiceManager: LocationServiceManager,
    shiftRepository: ShiftRepository
) : ViewModel() {

    /** Servis çalışıyor mu? (RUNNING veya PAUSED → true) */
    val isTracking: StateFlow<Boolean> = locationServiceManager.serviceState
        .map { it != ServiceState.STOPPED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** Servis tam olarak duraklatılmış mı? */
    val isPaused: StateFlow<Boolean> = locationServiceManager.serviceState
        .map { it == ServiceState.PAUSED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** Bu vardiya için kat edilen mesafe (km, 2 ondalık). */
    val sessionDistanceKm: StateFlow<Float> = locationServiceManager.totalDistance
        .map { meters -> meters.toKm2() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0f)

    /** Anlık hız (km/sa). Servis çalışmıyorsa veya son konum bayatsa 0. */
    val currentSpeedKmh: StateFlow<Float> = combine(
        locationServiceManager.currentLocation,
        locationServiceManager.serviceState
    ) { location, state ->
        val fresh = location != null &&
            System.currentTimeMillis() - location.timestamp <= STALE_LOCATION_MS
        if (state == ServiceState.RUNNING && fresh) location!!.speedKmh.coerceAtLeast(0f) else 0f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0f)

    /** Gün değişimini yakalamak için dakikalık gün aralığı. */
    private val dayRange = flow {
        while (true) {
            emit(DateTimeUtils.startOfDay() to DateTimeUtils.endOfDay())
            delay(DAY_CHECK_INTERVAL_MS)
        }
    }.distinctUntilChanged()

    /** Bugün bitmiş vardiyaların toplam mesafesi (metre). */
    private val completedTodayMeters = dayRange.flatMapLatest { (start, end) ->
        shiftRepository.observeShiftsBetween(start, end).map { shifts ->
            shifts.filter { !it.isActive }.sumOf { it.totalDistanceMeters.toDouble() }.toFloat()
        }
    }

    /** Bugünkü toplam mesafe (km, 2 ondalık) = bitmiş vardiyalar + aktif vardiya. */
    val totalDistanceKm: StateFlow<Float> = combine(
        completedTodayMeters,
        locationServiceManager.totalDistance
    ) { completed, live -> (completed + live).toKm2() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0f)

    private fun Float.toKm2(): Float = (this / 1000f * 100f).roundToInt() / 100f

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val STALE_LOCATION_MS = 30_000L
        const val DAY_CHECK_INTERVAL_MS = 60_000L
    }
}
