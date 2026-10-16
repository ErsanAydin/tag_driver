package com.tagtakip.pro.domain.usecase.earnings

import com.tagtakip.pro.core.common.Result
import com.tagtakip.pro.domain.model.Earning
import com.tagtakip.pro.domain.repository.EarningRepository
import javax.inject.Inject

/** Mevcut bir gelir/gider kaydını günceller; tutar işareti türe göre düzeltilir. */
class UpdateEarningUseCase @Inject constructor(
    private val earningRepository: EarningRepository
) {
    suspend operator fun invoke(earning: Earning): Result<Unit> {
        if (earning.id <= 0L) return Result.Error(IllegalArgumentException("Geçersiz kayıt"))
        EarningValidator.validate(earning.amount)?.let { return Result.Error(IllegalArgumentException(it)) }
        return earningRepository.updateEarning(earning.normalized())
    }
}
