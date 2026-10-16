package com.tagtakip.pro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tagtakip.pro.data.local.entity.LocationPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationPointDao {

    @Insert
    suspend fun insert(point: LocationPointEntity): Long

    @Insert
    suspend fun insertAll(points: List<LocationPointEntity>)

    @Query("SELECT * FROM location_points WHERE shift_id = :shiftId ORDER BY timestamp ASC")
    fun observeByShift(shiftId: Long): Flow<List<LocationPointEntity>>

    @Query("SELECT * FROM location_points WHERE shift_id = :shiftId ORDER BY timestamp ASC")
    suspend fun getByShift(shiftId: Long): List<LocationPointEntity>

    @Query("SELECT * FROM location_points WHERE shift_id = :shiftId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastForShift(shiftId: Long): LocationPointEntity?

    @Query("SELECT COUNT(*) FROM location_points WHERE shift_id = :shiftId")
    suspend fun countForShift(shiftId: Long): Int

    @Query("DELETE FROM location_points WHERE shift_id = :shiftId")
    suspend fun deleteByShift(shiftId: Long)

    /** Eski rota verisini temizlemek için (ör. 90 günden eski). */
    @Query("DELETE FROM location_points WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long): Int
}
