package com.tagtakip.pro.domain.repository

import com.tagtakip.pro.domain.model.Trip
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    suspend fun startTrip(trip: Trip): Long
    suspend fun updateTrip(trip: Trip)
    suspend fun getTripById(tripId: Long): Trip?
    suspend fun getActiveTrip(shiftId: Long): Trip?
    fun observeActiveTrip(shiftId: Long): Flow<Trip?>
    fun observeTripHistory(limit: Int): Flow<List<Trip>>
    fun observeTripsByShift(shiftId: Long): Flow<List<Trip>>
    fun observeTripsBetween(start: Long, end: Long): Flow<List<Trip>>
}
