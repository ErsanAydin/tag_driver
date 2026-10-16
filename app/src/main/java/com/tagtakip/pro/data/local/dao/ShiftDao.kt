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

    /** [start, end] aralığında BAŞLAYAN vardiyalar (gün ataması mesai başlangıcına göre). */
    @Query("SELECT * FROM shifts WHERE start_time BETWEEN :start AND :end ORDER BY start_time DESC")
    fun observeShiftsStartedBetween(start: Long, end: Long): Flow<List<ShiftEntity>>

    /** Molayı başlatır (zaten moladaysa dokunmaz). */
    @Query("UPDATE shifts SET pause_start_time = :time WHERE id = :id AND pause_start_time IS NULL AND end_time IS NULL")
    suspend fun startPause(id: Long, time: Long)

    /** Devam eden molayı kapatır ve süresini toplam molaya ekler. */
    @Query(
        """
        UPDATE shifts SET
            total_paused_millis = total_paused_millis + MAX(0, :time - pause_start_time),
            pause_start_time = NULL
        WHERE id = :id AND pause_start_time IS NOT NULL
        """
    )
    suspend fun endPause(id: Long, time: Long)

    @Query("UPDATE shifts SET total_distance_meters = :distance WHERE id = :id")
    suspend fun updateDistance(id: Long, distance: Float)

    @Query("UPDATE shifts SET end_time = :endTime, total_distance_meters = :distance WHERE id = :id")
    suspend fun endShift(id: Long, endTime: Long, distance: Float)
}
