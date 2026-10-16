package com.tagtakip.pro.data.repository

import com.tagtakip.pro.core.di.IoDispatcher
import com.tagtakip.pro.data.local.dao.VehicleInfoDao
import com.tagtakip.pro.data.local.entity.toDomain
import com.tagtakip.pro.data.local.entity.toEntity
import com.tagtakip.pro.domain.model.VehicleInfo
import com.tagtakip.pro.domain.repository.VehicleInfoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleInfoRepositoryImpl @Inject constructor(
    private val dao: VehicleInfoDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : VehicleInfoRepository {

    override fun getVehicleInfo(): Flow<VehicleInfo?> =
        dao.getVehicleInfo().map { it?.toDomain() }.flowOn(ioDispatcher)

    override suspend fun getVehicleInfoOnce(): VehicleInfo? = withContext(ioDispatcher) {
        dao.getVehicleInfoOnce()?.toDomain()
    }

    override suspend fun saveVehicleInfo(info: VehicleInfo) = withContext(ioDispatcher) {
        dao.upsertVehicleInfo(info.toEntity())
    }
}
