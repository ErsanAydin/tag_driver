package com.tagtakip.pro.domain.usecase.earnings

import com.tagtakip.pro.core.common.Result
import com.tagtakip.pro.core.common.resultOf
import com.tagtakip.pro.domain.model.Earning
import com.tagtakip.pro.domain.repository.EarningRepository
import com.tagtakip.pro.domain.repository.ShiftRepository
import javax.inject.Inject

/**
 * Kazanç ekler. shiftId verilmemişse aktif vardiyaya otomatik bağlar.
 * Tutar pozitif ve makul bir üst sınırın altında olmalıdır.
 */
class AddEarningUseCase @Inject constructor(
    private val earningRepository: EarningRepository,
    private val shiftRepository: ShiftRepository
) {
    suspend operator fun invoke(earning: Earning): Result<Long> {
        if (earning.amount <= 0.0 || earning.amount.isNaN()) {
            return Result.Error(IllegalArgumentException("Tutar sıfırdan büyük olmalıdır"))
        }
        if (earning.amount > MAX_SINGLE_EARNING_TL) {
            return Result.Error(IllegalArgumentException("Tutar çok yüksek görünüyor"))
        }
        return resultOf {
            val shiftId = earning.shiftId ?: shiftRepository.getActiveShift()?.id
            earningRepository.addEarning(earning.copy(shiftId = shiftId))
        }
    }

    companion object {
        const val MAX_SINGLE_EARNING_TL = 100_000.0
    }
}
