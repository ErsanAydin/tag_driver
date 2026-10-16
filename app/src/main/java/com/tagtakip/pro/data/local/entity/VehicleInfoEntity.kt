package com.tagtakip.pro.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tagtakip.pro.domain.model.VehicleInfo
import com.tagtakip.pro.domain.model.YakitTuru

/** Varsayılan değerler MIGRATION_2_3'teki CREATE TABLE ile birebir aynı olmalı (Room şema doğrulaması). */
@Entity(tableName = "vehicle_info")
data class VehicleInfoEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(defaultValue = "") val marka: String = "",
    @ColumnInfo(defaultValue = "") val model: String = "",
    @ColumnInfo(defaultValue = "BENZIN") val yakitTuru: String = "BENZIN",
    @ColumnInfo(defaultValue = "0.0") val lt100km: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val litreBasiFiyat: Double = 0.0
)

fun VehicleInfoEntity.toDomain() = VehicleInfo(
    id = id,
    marka = marka,
    model = model,
    yakitTuru = YakitTuru.fromStorage(yakitTuru),
    lt100km = lt100km,
    litreBasiFiyat = litreBasiFiyat
)

fun VehicleInfo.toEntity() = VehicleInfoEntity(
    id = 1,
    marka = marka.trim(),
    model = model.trim(),
    yakitTuru = yakitTuru.name,
    lt100km = lt100km,
    litreBasiFiyat = litreBasiFiyat
)
