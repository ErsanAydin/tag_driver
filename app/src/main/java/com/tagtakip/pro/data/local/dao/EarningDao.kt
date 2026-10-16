package com.tagtakip.pro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tagtakip.pro.data.local.entity.EarningEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EarningDao {

    @Insert
    suspend fun insert(earning: EarningEntity): Long

    @Query("DELETE FROM earnings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM earnings WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<EarningEntity>>

    @Query("SELECT * FROM earnings WHERE shift_id = :shiftId ORDER BY timestamp DESC")
    fun observeByShift(shiftId: Long): Flow<List<EarningEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM earnings WHERE timestamp BETWEEN :start AND :end")
    fun observeTotalBetween(start: Long, end: Long): Flow<Double>
}
