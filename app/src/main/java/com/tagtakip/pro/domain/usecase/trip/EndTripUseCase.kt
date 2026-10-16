package com.tagtakip.pro.domain.usecase.trip

import com.tagtakip.pro.core.common.Result
import com.tagtakip.pro.core.common.resultOf
import com.tagtakip.pro.domain.model.Earning
import com.tagtakip.pro.domain.model.EarningType
import com.tagtakip.pro.domain.model.LocationPoint
import com.tagtakip.pro.domain.model.Trip
import com.tagtakip.pro.domain.repository.EarningRepository
import com.tagtakip.pro.domain.repository.TripRepository
import javax.inject.Inject

/**
 * Yolculuğu bitirir; ücret > 0 ise otomatik TRIP_FARE kazancı oluşturur.
 */
class EndTripUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val earningRepository: EarningRepository
) {
    suspend operator fun invoke(
        tripId: Long,
        fare: Double,
        distanceMeters: Float,
        endLocation: LocationPoint? = null,
        endTime: Long = System.currentTimeMillis()
    ): Result<Trip> {
        if (fare < 0.0) return Result.Error(IllegalArgumentException("Ücret negatif olamaz"))
        return resultOf {
            val trip = tripRepository.getTripById(tripId)
                ?: throw NoSuchElementException("Yolculuk bulunamadı (id=$tripId)")
            check(trip.isActive) { "Yolculuk zaten tamamlanmış" }

            val finished = trip.copy(
                endTime = maxOf(endTime, trip.startTime),
                endLatitude = endLocation?.latitude,
                endLongitude = endLocation?.longitude,
                distanceMeters = distanceMeters.coerceAtLeast(0f),
                fare = fare
            )
            tripRepository.updateTrip(finished)

            if (fare > 0.0) {
                earningRepository.addEarning(
                    Earning(
                        shiftId = trip.shiftId,
                        tripId = trip.id,
                        amount = fare,
                        type = EarningType.TRIP_FARE,
                        platform = trip.platform,
                        timestamp = finished.endTime ?: endTime
                    )
                )
            }
            finished
        }
    }
}
