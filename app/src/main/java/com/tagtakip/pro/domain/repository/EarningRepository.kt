package com.tagtakip.pro.domain.repository

import com.tagtakip.pro.domain.model.Earning
import kotlinx.coroutines.flow.Flow

interface EarningRepository {
    suspend fun addEarning(earning: Earning): Long
    suspend fun deleteEarning(earningId: Long)
    fun observeEarningsBetween(start: Long, end: Long): Flow<List<Earning>>
    fun observeEarningsByShift(shiftId: Long): Flow<List<Earning>>
    fun observeTotalBetween(start: Long, end: Long): Flow<Double>
}
