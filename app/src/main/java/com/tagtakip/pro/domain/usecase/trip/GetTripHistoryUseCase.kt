package com.tagtakip.pro.domain.usecase.trip

import com.tagtakip.pro.core.common.Constants
import com.tagtakip.pro.domain.model.Trip
import com.tagtakip.pro.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Yolculuk geçmişi: tüm geçmiş (limitli) veya belirli bir vardiyanın yolculukları. */
class GetTripHistoryUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    operator fun invoke(limit: Int = Constants.TRIP_HISTORY_DEFAULT_LIMIT): Flow<List<Trip>> =
        tripRepository.observeTripHistory(limit.coerceIn(1, 1000))

    fun forShift(shiftId: Long): Flow<List<Trip>> = tripRepository.observeTripsByShift(shiftId)

    fun activeTrip(shiftId: Long): Flow<Trip?> = tripRepository.observeActiveTrip(shiftId)
}
