package com.tagtakip.pro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tagtakip.pro.data.local.entity.VehicleInfoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleInfoDao {

    @Query("SELECT * FROM vehicle_info WHERE id = 1")
    fun getVehicleInfo(): Flow<VehicleInfoEntity?>

    @Query("SELECT * FROM vehicle_info WHERE id = 1")
    suspend fun getVehicleInfoOnce(): VehicleInfoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVehicleInfo(entity: VehicleInfoEntity)
}
