package com.tagtakip.pro.domain.usecase.shift

import com.tagtakip.pro.domain.model.Shift
import com.tagtakip.pro.domain.repository.ShiftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/** Aktif vardiyayı reaktif olarak yayınlar (yoksa null). */
class GetActiveShiftUseCase @Inject constructor(
    private val shiftRepository: ShiftRepository
) {
    operator fun invoke(): Flow<Shift?> =
        shiftRepository.observeActiveShift().distinctUntilChanged()

    suspend fun current(): Shift? = shiftRepository.getActiveShift()
}
