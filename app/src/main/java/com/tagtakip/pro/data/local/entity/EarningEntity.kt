package com.tagtakip.pro.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tagtakip.pro.domain.model.Earning
import com.tagtakip.pro.domain.model.EarningType

@Entity(
    tableName = "earnings",
    foreignKeys = [
        ForeignKey(
            entity = ShiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["shift_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("shift_id"), Index("trip_id"), Index("timestamp")]
)
data class EarningEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "shift_id") val shiftId: Long? = null,
    @ColumnInfo(name = "trip_id") val tripId: Long? = null,
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "type", defaultValue = "INCOME") val type: String = "INCOME",
    @ColumnInfo(name = "description") val description: String? = null,
    @ColumnInfo(name = "timestamp") val timestamp: Long
)

fun EarningEntity.toDomain() = Earning(
    id = id,
    shiftId = shiftId,
    tripId = tripId,
    amount = amount,
    type = EarningType.fromStorage(type, amount),
    description = description,
    timestamp = timestamp
)

fun Earning.toEntity() = EarningEntity(
    id = id,
    shiftId = shiftId,
    tripId = tripId,
    amount = amount,
    type = type.name,
    description = description,
    timestamp = timestamp
)
