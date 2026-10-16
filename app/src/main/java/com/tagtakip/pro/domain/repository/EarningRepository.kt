package com.tagtakip.pro.domain.repository

import com.tagtakip.pro.core.common.Result
import com.tagtakip.pro.domain.model.Earning
import kotlinx.coroutines.flow.Flow

interface EarningRepository {
    suspend fun addEarning(earning: Earning): Long
    suspend fun updateEarning(earning: Earning): Result<Unit>
    suspend fun deleteEarning(id: Long): Result<Unit>
    fun observeEarningsBetween(start: Long, end: Long): Flow<List<Earning>>
    fun observeIncomesBetween(start: Long, end: Long): Flow<List<Earning>>
    fun observeExpensesBetween(start: Long, end: Long): Flow<List<Earning>>
    fun observeEarningsByShift(shiftId: Long): Flow<List<Earning>>
    fun observeTotalBetween(start: Long, end: Long): Flow<Double>
}
