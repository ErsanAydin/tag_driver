package com.tagtakip.pro.domain.usecase.trip

import com.tagtakip.pro.core.common.Result
import com.tagtakip.pro.core.common.resultOf
import com.tagtakip.pro.domain.model.Earning
import com.tagtakip.pro.domain.model.EarningType
import com.tagtakip.pro.domain.model.LocationPoint
import com.tagtakip.pro.domain.model.Trip
import com.tagtakip.pro.domain.repository.EarningRepository
import com.tagtakip.pro.domain.repository.TripRepository
import com.tagtakip.pro.domain.usecase.earnings.EarningValidator
import javax.inject.Inject

/**
 * Yolculuğu bitirir. [earningAmount] verilmişse (> 0) yolculuğa bağlı bir INCOME kaydı da oluşturur;
 * null ise ("Atla") yalnızca yolculuk kaydedilir.
 */
class EndTripUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val earningRepository: EarningRepository
) {
    suspend operator fun invoke(
        tripId: Long,
        distanceMeters: Float,
        endLocation: LocationPoint? = null,
        endTime: Long = System.currentTimeMillis(),
        earningAmount: Double? = null,
        description: String? = null
    ): Result<Trip> {
        val amount = earningAmount?.takeIf { it != 0.0 }
        if (amount != null) {
            if (amount < 0.0) return Result.Error(IllegalArgumentException("Kazanç negatif olamaz"))
            EarningValidator.validate(amount)?.let { return Result.Error(IllegalArgumentException(it)) }
        }
        val note = description?.trim()?.takeIf { it.isNotEmpty() }

        return resultOf {
            val trip = tripRepository.getTripById(tripId)
                ?: throw NoSuchElementException("Yolculuk bulunamadı (id=$tripId)")
            check(trip.isActive) { "Yolculuk zaten tamamlanmış" }

            val finished = trip.copy(
                endTime = maxOf(endTime, trip.startTime),
                endLatitude = endLocation?.latitude,
                endLongitude = endLocation?.longitude,
                distanceMeters = distanceMeters.coerceAtLeast(0f),
                fare = amount ?: 0.0,
                earningAmount = amount,
                description = note
            )
            tripRepository.updateTrip(finished)

            if (amount != null) {
                earningRepository.addEarning(
                    Earning(
                        shiftId = trip.shiftId,
                        tripId = trip.id,
                        amount = amount,
                        type = EarningType.INCOME,
                        description = note,
                        timestamp = finished.endTime ?: endTime
                    )
                )
            }
            finished
        }
    }
}
