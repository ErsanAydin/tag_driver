package com.tagtakip.pro.domain.repository

import com.tagtakip.pro.domain.model.VehicleInfo
import kotlinx.coroutines.flow.Flow

interface VehicleInfoRepository {
    fun getVehicleInfo(): Flow<VehicleInfo?>
    suspend fun getVehicleInfoOnce(): VehicleInfo?
    suspend fun saveVehicleInfo(info: VehicleInfo)
}
