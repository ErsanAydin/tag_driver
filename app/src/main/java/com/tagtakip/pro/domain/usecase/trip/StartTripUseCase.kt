package com.tagtakip.pro.domain.usecase.trip

import com.tagtakip.pro.core.common.Result
import com.tagtakip.pro.core.common.resultOf
import com.tagtakip.pro.domain.model.LocationPoint
import com.tagtakip.pro.domain.model.Trip
import com.tagtakip.pro.domain.repository.ShiftRepository
import com.tagtakip.pro.domain.repository.TripRepository
import com.tagtakip.pro.domain.usecase.shift.NoActiveShiftException
import javax.inject.Inject

/** Aktif vardiya içinde yeni yolculuk başlatır. Aynı anda tek aktif yolculuk olabilir. */
class StartTripUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val shiftRepository: ShiftRepository
) {
    suspend operator fun invoke(
        startLocation: LocationPoint? = null,
        platform: String? = null,
        startTime: Long = System.currentTimeMillis()
    ): Result<Long> = resultOf {
        val shift = shiftRepository.getActiveShift() ?: throw NoActiveShiftException()
        tripRepository.getActiveTrip(shift.id)?.let {
            throw IllegalStateException("Zaten devam eden bir yolculuk var (id=${it.id})")
        }
        tripRepository.startTrip(
            Trip(
                shiftId = shift.id,
                startTime = startTime,
                startLatitude = startLocation?.latitude,
                startLongitude = startLocation?.longitude,
                platform = platform
            )
        )
    }
}
