package com.tagtakip.pro.domain.usecase.earnings

import com.tagtakip.pro.core.common.Result
import com.tagtakip.pro.domain.repository.EarningRepository
import javax.inject.Inject

/** Gelir/gider kaydını siler. */
class DeleteEarningUseCase @Inject constructor(
    private val earningRepository: EarningRepository
) {
    suspend operator fun invoke(id: Long): Result<Unit> {
        if (id <= 0L) return Result.Error(IllegalArgumentException("Geçersiz kayıt"))
        return earningRepository.deleteEarning(id)
    }
}
