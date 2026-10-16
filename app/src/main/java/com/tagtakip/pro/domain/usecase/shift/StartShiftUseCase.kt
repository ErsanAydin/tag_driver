package com.tagtakip.pro.domain.usecase.shift

import com.tagtakip.pro.core.common.Result
import com.tagtakip.pro.core.common.resultOf
import com.tagtakip.pro.domain.repository.ShiftRepository
import javax.inject.Inject

/** Aktif vardiya yoksa yeni vardiya başlatır ve ID'sini döner. */
class StartShiftUseCase @Inject constructor(
    private val shiftRepository: ShiftRepository
) {
    suspend operator fun invoke(startTime: Long = System.currentTimeMillis()): Result<Long> {
        val active = try {
            shiftRepository.getActiveShift()
        } catch (e: Exception) {
            return Result.Error(e)
        }
        if (active != null) {
            return Result.Error(ShiftAlreadyActiveException(active.id))
        }
        return resultOf { shiftRepository.startShift(startTime) }
    }
}

class ShiftAlreadyActiveException(val shiftId: Long) :
    IllegalStateException("Zaten aktif bir mesai var (id=$shiftId)")
