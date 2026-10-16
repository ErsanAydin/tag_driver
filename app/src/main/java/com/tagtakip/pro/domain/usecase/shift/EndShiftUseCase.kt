package com.tagtakip.pro.domain.usecase.shift

import com.tagtakip.pro.core.common.Result
import com.tagtakip.pro.core.common.resultOf
import com.tagtakip.pro.domain.model.Shift
import com.tagtakip.pro.domain.repository.ShiftRepository
import javax.inject.Inject

/**
 * Aktif vardiyayı bitirir. [totalDistanceMeters] verilmezse DB'deki son kaydedilen mesafe kullanılır.
 * Bitmiş vardiyayı döner.
 */
class EndShiftUseCase @Inject constructor(
    private val shiftRepository: ShiftRepository
) {
    suspend operator fun invoke(
        totalDistanceMeters: Float? = null,
        endTime: Long = System.currentTimeMillis()
    ): Result<Shift> = resultOf {
        val active = shiftRepository.getActiveShift()
            ?: throw NoActiveShiftException()
        val distance = maxOf(totalDistanceMeters ?: 0f, active.totalDistanceMeters)
        val safeEnd = maxOf(endTime, active.startTime)
        // Moladayken bitiriliyorsa önce mola kapatılır (süresi toplam molaya eklenir)
        var ended = active
        active.pauseStartTime?.let { pauseStart ->
            shiftRepository.endPause(active.id, safeEnd)
            ended = active.copy(
                pauseStartTime = null,
                totalPausedMillis = active.totalPausedMillis + (safeEnd - pauseStart).coerceAtLeast(0L)
            )
        }
        shiftRepository.endShift(active.id, safeEnd, distance)
        ended.copy(endTime = safeEnd, totalDistanceMeters = distance)
    }
}

class NoActiveShiftException : IllegalStateException("Aktif mesai bulunamadı")
