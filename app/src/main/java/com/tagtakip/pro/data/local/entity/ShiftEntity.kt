package com.tagtakip.pro.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tagtakip.pro.domain.model.Shift

@Entity(
    tableName = "shifts",
    indices = [Index("start_time"), Index("end_time")]
)
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "total_distance_meters") val totalDistanceMeters: Float = 0f,
    @ColumnInfo(name = "note") val note: String? = null
)

fun ShiftEntity.toDomain() = Shift(
    id = id,
    startTime = startTime,
    endTime = endTime,
    totalDistanceMeters = totalDistanceMeters,
    note = note
)

fun Shift.toEntity() = ShiftEntity(
    id = id,
    startTime = startTime,
    endTime = endTime,
    totalDistanceMeters = totalDistanceMeters,
    note = note
)
