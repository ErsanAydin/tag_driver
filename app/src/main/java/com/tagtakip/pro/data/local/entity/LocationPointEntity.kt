package com.tagtakip.pro.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tagtakip.pro.domain.model.LocationPoint

@Entity(
    tableName = "location_points",
    foreignKeys = [
        ForeignKey(
            entity = ShiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["shift_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["shift_id", "timestamp"])]
)
data class LocationPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "shift_id") val shiftId: Long,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "speed") val speed: Float,
    @ColumnInfo(name = "accuracy") val accuracy: Float,
    @ColumnInfo(name = "bearing") val bearing: Float,
    @ColumnInfo(name = "altitude") val altitude: Double,
    @ColumnInfo(name = "timestamp") val timestamp: Long
)

fun LocationPointEntity.toDomain() = LocationPoint(
    id = id,
    shiftId = shiftId,
    latitude = latitude,
    longitude = longitude,
    speed = speed,
    accuracy = accuracy,
    bearing = bearing,
    altitude = altitude,
    timestamp = timestamp
)

fun LocationPoint.toEntity() = LocationPointEntity(
    id = id,
    shiftId = shiftId,
    latitude = latitude,
    longitude = longitude,
    speed = speed,
    accuracy = accuracy,
    bearing = bearing,
    altitude = altitude,
    timestamp = timestamp
)
