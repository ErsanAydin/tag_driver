package com.tagtakip.pro.data.repository

import com.tagtakip.pro.core.di.IoDispatcher
import com.tagtakip.pro.data.local.dao.TripDao
import com.tagtakip.pro.data.local.entity.toDomain
import com.tagtakip.pro.data.local.entity.toEntity
import com.tagtakip.pro.domain.model.Trip
import com.tagtakip.pro.domain.repository.TripRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepositoryImpl @Inject constructor(
    private val tripDao: TripDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : TripRepository {

    override suspend fun startTrip(trip: Trip): Long = withContext(ioDispatcher) {
        tripDao.insert(trip.toEntity())
    }

    override suspend fun updateTrip(trip: Trip) = withContext(ioDispatcher) {
        tripDao.update(trip.toEntity())
    }

    override suspend fun getTripById(tripId: Long): Trip? = withContext(ioDispatcher) {
        tripDao.getById(tripId)?.toDomain()
    }

    override suspend fun getActiveTrip(shiftId: Long): Trip? = withContext(ioDispatcher) {
        tripDao.getActiveTrip(shiftId)?.toDomain()
    }

    override fun observeActiveTrip(shiftId: Long): Flow<Trip?> =
        tripDao.observeActiveTrip(shiftId).map { it?.toDomain() }.flowOn(ioDispatcher)

    override fun observeTripHistory(limit: Int): Flow<List<Trip>> =
        tripDao.observeHistory(limit).map { list -> list.map { it.toDomain() } }.flowOn(ioDispatcher)

    override fun observeTripsByShift(shiftId: Long): Flow<List<Trip>> =
        tripDao.observeByShift(shiftId).map { list -> list.map { it.toDomain() } }.flowOn(ioDispatcher)

    override fun observeTripsBetween(start: Long, end: Long): Flow<List<Trip>> =
        tripDao.observeBetween(start, end).map { list -> list.map { it.toDomain() } }.flowOn(ioDispatcher)
}
