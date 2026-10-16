package com.tagtakip.pro.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tagtakip.pro.domain.model.Trip

@Entity(
    tableName = "trips",
    foreignKeys = [
        ForeignKey(
            entity = ShiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["shift_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("shift_id"), Index("start_time")]
)
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "shift_id") val shiftId: Long,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "start_lat") val startLatitude: Double? = null,
    @ColumnInfo(name = "start_lng") val startLongitude: Double? = null,
    @ColumnInfo(name = "end_lat") val endLatitude: Double? = null,
    @ColumnInfo(name = "end_lng") val endLongitude: Double? = null,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Float = 0f,
    @ColumnInfo(name = "fare") val fare: Double = 0.0,
    @ColumnInfo(name = "platform") val platform: String? = null,
    @ColumnInfo(name = "earning_amount") val earningAmount: Double? = null,
    @ColumnInfo(name = "description") val description: String? = null
)

fun TripEntity.toDomain() = Trip(
    id = id,
    shiftId = shiftId,
    startTime = startTime,
    endTime = endTime,
    startLatitude = startLatitude,
    startLongitude = startLongitude,
    endLatitude = endLatitude,
    endLongitude = endLongitude,
    distanceMeters = distanceMeters,
    fare = fare,
    platform = platform,
    earningAmount = earningAmount,
    description = description
)

fun Trip.toEntity() = TripEntity(
    id = id,
    shiftId = shiftId,
    startTime = startTime,
    endTime = endTime,
    startLatitude = startLatitude,
    startLongitude = startLongitude,
    endLatitude = endLatitude,
    endLongitude = endLongitude,
    distanceMeters = distanceMeters,
    fare = fare,
    platform = platform,
    earningAmount = earningAmount,
    description = description
)
