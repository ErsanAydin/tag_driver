package com.tagtakip.pro.data.repository

import com.tagtakip.pro.core.di.IoDispatcher
import com.tagtakip.pro.data.local.dao.EarningDao
import com.tagtakip.pro.data.local.entity.toDomain
import com.tagtakip.pro.data.local.entity.toEntity
import com.tagtakip.pro.domain.model.Earning
import com.tagtakip.pro.domain.repository.EarningRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EarningRepositoryImpl @Inject constructor(
    private val earningDao: EarningDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : EarningRepository {

    override suspend fun addEarning(earning: Earning): Long = withContext(ioDispatcher) {
        earningDao.insert(earning.toEntity())
    }

    override suspend fun deleteEarning(earningId: Long) = withContext(ioDispatcher) {
        earningDao.deleteById(earningId)
    }

    override fun observeEarningsBetween(start: Long, end: Long): Flow<List<Earning>> =
        earningDao.observeBetween(start, end).map { list -> list.map { it.toDomain() } }.flowOn(ioDispatcher)

    override fun observeEarningsByShift(shiftId: Long): Flow<List<Earning>> =
        earningDao.observeByShift(shiftId).map { list -> list.map { it.toDomain() } }.flowOn(ioDispatcher)

    override fun observeTotalBetween(start: Long, end: Long): Flow<Double> =
        earningDao.observeTotalBetween(start, end).flowOn(ioDispatcher)
}
