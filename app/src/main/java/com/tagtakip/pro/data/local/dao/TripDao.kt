package com.tagtakip.pro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tagtakip.pro.data.local.entity.TripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Insert
    suspend fun insert(trip: TripEntity): Long

    @Update
    suspend fun update(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getById(id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE shift_id = :shiftId AND end_time IS NULL ORDER BY start_time DESC LIMIT 1")
    suspend fun getActiveTrip(shiftId: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE shift_id = :shiftId AND end_time IS NULL ORDER BY start_time DESC LIMIT 1")
    fun observeActiveTrip(shiftId: Long): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE end_time IS NOT NULL ORDER BY start_time DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE shift_id = :shiftId ORDER BY start_time DESC")
    fun observeByShift(shiftId: Long): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE start_time BETWEEN :start AND :end ORDER BY start_time DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<TripEntity>>
}
