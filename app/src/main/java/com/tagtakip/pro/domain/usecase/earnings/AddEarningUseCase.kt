package com.tagtakip.pro.domain.usecase.earnings

import com.tagtakip.pro.core.common.Result
import com.tagtakip.pro.core.common.resultOf
import com.tagtakip.pro.domain.model.Earning
import com.tagtakip.pro.domain.repository.EarningRepository
import com.tagtakip.pro.domain.repository.ShiftRepository
import javax.inject.Inject

/**
 * Gelir veya gider ekler. Tutarın işareti türe göre otomatik ayarlanır
 * (kullanıcı 500 yazar → gider ise -500 kaydedilir). shiftId yoksa aktif vardiyaya bağlanır.
 */
class AddEarningUseCase @Inject constructor(
    private val earningRepository: EarningRepository,
    private val shiftRepository: ShiftRepository
) {
    suspend operator fun invoke(earning: Earning): Result<Long> {
        EarningValidator.validate(earning.amount)?.let { return Result.Error(IllegalArgumentException(it)) }
        return resultOf {
            val shiftId = earning.shiftId ?: shiftRepository.getActiveShift()?.id
            earningRepository.addEarning(earning.normalized().copy(shiftId = shiftId))
        }
    }

    companion object {
        const val MAX_SINGLE_EARNING_TL = EarningValidator.MAX_SINGLE_AMOUNT_TL
    }
}
