package com.tagtakip.pro.data.repository

import com.tagtakip.pro.core.di.IoDispatcher
import com.tagtakip.pro.data.local.dao.LocationPointDao
import com.tagtakip.pro.data.local.dao.ShiftDao
import com.tagtakip.pro.data.local.entity.ShiftEntity
import com.tagtakip.pro.data.local.entity.toDomain
import com.tagtakip.pro.data.local.entity.toEntity
import com.tagtakip.pro.domain.model.LocationPoint
import com.tagtakip.pro.domain.model.Shift
import com.tagtakip.pro.domain.repository.ShiftRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShiftRepositoryImpl @Inject constructor(
    private val shiftDao: ShiftDao,
    private val locationPointDao: LocationPointDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ShiftRepository {

    override suspend fun startShift(startTime: Long): Long = withContext(ioDispatcher) {
        shiftDao.insert(ShiftEntity(startTime = startTime))
    }

    override suspend fun endShift(shiftId: Long, endTime: Long, totalDistanceMeters: Float) =
        withContext(ioDispatcher) { shiftDao.endShift(shiftId, endTime, totalDistanceMeters) }

    override suspend fun getActiveShift(): Shift? = withContext(ioDispatcher) {
        shiftDao.getActive()?.toDomain()
    }

    override suspend fun getShiftById(shiftId: Long): Shift? = withContext(ioDispatcher) {
        shiftDao.getById(shiftId)?.toDomain()
    }

    override suspend fun updateShiftDistance(shiftId: Long, totalDistanceMeters: Float) =
        withContext(ioDispatcher) { shiftDao.updateDistance(shiftId, totalDistanceMeters) }

    override fun observeActiveShift(): Flow<Shift?> =
        shiftDao.observeActive().map { it?.toDomain() }.flowOn(ioDispatcher)

    override fun observeShifts(): Flow<List<Shift>> =
        shiftDao.observeAll().map { list -> list.map { it.toDomain() } }.flowOn(ioDispatcher)

    override fun observeShiftsBetween(start: Long, end: Long): Flow<List<Shift>> =
        shiftDao.observeOverlapping(start, end)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun saveLocationPoint(point: LocationPoint): Long = withContext(ioDispatcher) {
        locationPointDao.insert(point.toEntity())
    }

    override fun observeLocationPoints(shiftId: Long): Flow<List<LocationPoint>> =
        locationPointDao.observeByShift(shiftId)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
}
