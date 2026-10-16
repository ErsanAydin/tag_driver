package com.tagtakip.pro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tagtakip.pro.data.local.entity.ShiftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(shift: ShiftEntity): Long

    @Update
    suspend fun update(shift: ShiftEntity)

    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun getById(id: Long): ShiftEntity?

    @Query("SELECT * FROM shifts WHERE end_time IS NULL ORDER BY start_time DESC LIMIT 1")
    suspend fun getActive(): ShiftEntity?

    @Query("SELECT * FROM shifts WHERE end_time IS NULL ORDER BY start_time DESC LIMIT 1")
    fun observeActive(): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts ORDER BY start_time DESC")
    fun observeAll(): Flow<List<ShiftEntity>>

    /** [start, end] ile kesişen vardiyalar (aktif vardiya dahil). */
    @Query(
        """
        SELECT * FROM shifts
        WHERE start_time <= :end AND (end_time IS NULL OR end_time >= :start)
        ORDER BY start_time DESC
        """
    )
    fun observeOverlapping(start: Long, end: Long): Flow<List<ShiftEntity>>

    @Query("UPDATE shifts SET total_distance_meters = :distance WHERE id = :id")
    suspend fun updateDistance(id: Long, distance: Float)

    @Query("UPDATE shifts SET end_time = :endTime, total_distance_meters = :distance WHERE id = :id")
    suspend fun endShift(id: Long, endTime: Long, distance: Float)
}
