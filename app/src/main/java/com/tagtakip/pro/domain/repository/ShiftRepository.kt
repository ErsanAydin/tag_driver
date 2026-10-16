package com.tagtakip.pro.domain.repository

import com.tagtakip.pro.domain.model.LocationPoint
import com.tagtakip.pro.domain.model.Shift
import kotlinx.coroutines.flow.Flow

interface ShiftRepository {
    /** Yeni vardiya oluşturur, ID'sini döner. */
    suspend fun startShift(startTime: Long): Long
    suspend fun endShift(shiftId: Long, endTime: Long, totalDistanceMeters: Float)
    suspend fun getActiveShift(): Shift?
    suspend fun getShiftById(shiftId: Long): Shift?
    suspend fun updateShiftDistance(shiftId: Long, totalDistanceMeters: Float)
    fun observeActiveShift(): Flow<Shift?>
    fun observeShifts(): Flow<List<Shift>>
    /** [start, end] aralığında BAŞLAYAN vardiyalar (gün ataması mesai başlangıcına göre). */
    fun observeShiftsBetween(start: Long, end: Long): Flow<List<Shift>>

    /** Mola başlat / bitir (bitirmede süre toplam molaya eklenir). */
    suspend fun startPause(shiftId: Long, time: Long)
    suspend fun endPause(shiftId: Long, time: Long)

    // Vardiyaya ait rota noktaları
    suspend fun saveLocationPoint(point: LocationPoint): Long
    fun observeLocationPoints(shiftId: Long): Flow<List<LocationPoint>>
}
