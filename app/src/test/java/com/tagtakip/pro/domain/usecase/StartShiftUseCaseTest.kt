package com.tagtakip.pro.domain.usecase

import com.tagtakip.pro.core.common.Result
import com.tagtakip.pro.domain.model.Shift
import com.tagtakip.pro.domain.repository.ShiftRepository
import com.tagtakip.pro.domain.usecase.shift.ShiftAlreadyActiveException
import com.tagtakip.pro.domain.usecase.shift.StartShiftUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StartShiftUseCaseTest {

    private lateinit var repository: ShiftRepository
    private lateinit var useCase: StartShiftUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = StartShiftUseCase(repository)
    }

    @Test
    fun `aktif vardiya yoksa yeni vardiya olusturur ve id doner`() = runTest {
        coEvery { repository.getActiveShift() } returns null
        coEvery { repository.startShift(1_000L) } returns 42L

        val result = useCase(startTime = 1_000L)

        assertEquals(Result.Success(42L), result)
        coVerify(exactly = 1) { repository.startShift(1_000L) }
    }

    @Test
    fun `aktif vardiya varsa ShiftAlreadyActiveException ile hata doner`() = runTest {
        coEvery { repository.getActiveShift() } returns Shift(id = 7L, startTime = 500L)

        val result = useCase(startTime = 1_000L)

        assertTrue(result is Result.Error)
        val error = (result as Result.Error).exception
        assertTrue(error is ShiftAlreadyActiveException)
        assertEquals(7L, (error as ShiftAlreadyActiveException).shiftId)
        coVerify(exactly = 0) { repository.startShift(any()) }
    }

    @Test
    fun `repository hata firlatirsa Result Error doner`() = runTest {
        coEvery { repository.getActiveShift() } returns null
        coEvery { repository.startShift(any()) } throws IllegalStateException("db hatası")

        val result = useCase(startTime = 1_000L)

        assertTrue(result is Result.Error)
        assertEquals("db hatası", (result as Result.Error).message)
    }

    @Test
    fun `aktif vardiya sorgusu hata verirse vardiya olusturulmaz`() = runTest {
        coEvery { repository.getActiveShift() } throws RuntimeException("okuma hatası")

        val result = useCase()

        assertTrue(result is Result.Error)
        coVerify(exactly = 0) { repository.startShift(any()) }
    }
}
