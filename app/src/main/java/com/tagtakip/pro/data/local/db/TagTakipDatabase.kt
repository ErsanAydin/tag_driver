package com.tagtakip.pro.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tagtakip.pro.data.local.dao.EarningDao
import com.tagtakip.pro.data.local.dao.LocationPointDao
import com.tagtakip.pro.data.local.dao.ShiftDao
import com.tagtakip.pro.data.local.dao.TripDao
import com.tagtakip.pro.data.local.entity.EarningEntity
import com.tagtakip.pro.data.local.entity.LocationPointEntity
import com.tagtakip.pro.data.local.entity.ShiftEntity
import com.tagtakip.pro.data.local.entity.TripEntity
import com.tagtakip.pro.data.local.entity.VehicleInfoEntity
import com.tagtakip.pro.data.local.dao.VehicleInfoDao

@Database(
    entities = [
        ShiftEntity::class,
        TripEntity::class,
        EarningEntity::class,
        LocationPointEntity::class,
        VehicleInfoEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class TagTakipDatabase : RoomDatabase() {
    abstract fun shiftDao(): ShiftDao
    abstract fun tripDao(): TripDao
    abstract fun earningDao(): EarningDao
    abstract fun locationPointDao(): LocationPointDao
    abstract fun vehicleInfoDao(): VehicleInfoDao
}
