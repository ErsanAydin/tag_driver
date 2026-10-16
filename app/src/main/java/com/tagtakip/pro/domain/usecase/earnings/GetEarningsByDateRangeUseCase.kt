package com.tagtakip.pro.domain.usecase.earnings

import com.tagtakip.pro.domain.model.Earning
import com.tagtakip.pro.domain.repository.EarningRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/** Verilen [start, end] (ms, dahil) aralığındaki kazançları en yeniden eskiye yayınlar. */
class GetEarningsByDateRangeUseCase @Inject constructor(
    private val earningRepository: EarningRepository
) {
    operator fun invoke(start: Long, end: Long): Flow<List<Earning>> =
        if (start > end) flowOf(emptyList())
        else earningRepository.observeEarningsBetween(start, end)
}
