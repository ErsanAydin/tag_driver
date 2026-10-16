package com.tagtakip.pro.data.repository

import com.tagtakip.pro.core.common.Result
import com.tagtakip.pro.core.common.resultOf
import com.tagtakip.pro.core.di.IoDispatcher
import com.tagtakip.pro.data.local.dao.EarningDao
import com.tagtakip.pro.data.local.entity.EarningEntity
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

    override suspend fun updateEarning(earning: Earning): Result<Unit> = withContext(ioDispatcher) {
        resultOf {
            val updated = earningDao.updateEarning(earning.toEntity())
            if (updated == 0) throw NoSuchElementException("Kayıt bulunamadı (id=${earning.id})")
        }
    }

    override suspend fun deleteEarning(id: Long): Result<Unit> = withContext(ioDispatcher) {
        resultOf {
            val deleted = earningDao.deleteById(id)
            if (deleted == 0) throw NoSuchElementException("Kayıt bulunamadı (id=$id)")
        }
    }

    override fun observeEarningsBetween(start: Long, end: Long): Flow<List<Earning>> =
        earningDao.observeBetween(start, end).toDomainList()

    override fun observeIncomesBetween(start: Long, end: Long): Flow<List<Earning>> =
        earningDao.getIncomesByDateRange(start, end).toDomainList()

    override fun observeExpensesBetween(start: Long, end: Long): Flow<List<Earning>> =
        earningDao.getExpensesByDateRange(start, end).toDomainList()

    override fun observeEarningsByShift(shiftId: Long): Flow<List<Earning>> =
        earningDao.observeByShift(shiftId).toDomainList()

    override fun observeTotalBetween(start: Long, end: Long): Flow<Double> =
        earningDao.observeTotalBetween(start, end).flowOn(ioDispatcher)

    private fun Flow<List<EarningEntity>>.toDomainList(): Flow<List<Earning>> =
        map { list -> list.map { it.toDomain() } }.flowOn(ioDispatcher)
}
