package com.tagtakip.pro.domain.usecase

import com.tagtakip.pro.core.common.Result
import com.tagtakip.pro.domain.model.Earning
import com.tagtakip.pro.domain.model.EarningType
import com.tagtakip.pro.domain.model.Trip
import com.tagtakip.pro.domain.repository.EarningRepository
import com.tagtakip.pro.domain.repository.ShiftRepository
import com.tagtakip.pro.domain.repository.TripRepository
import com.tagtakip.pro.domain.usecase.earnings.AddEarningUseCase
import com.tagtakip.pro.domain.usecase.earnings.DeleteEarningUseCase
import com.tagtakip.pro.domain.usecase.earnings.UpdateEarningUseCase
import com.tagtakip.pro.domain.usecase.trip.EndTripUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EarningUseCasesTest {

    private val earningRepository = mockk<EarningRepository>(relaxed = true)
    private val shiftRepository = mockk<ShiftRepository>(relaxed = true)
    private val tripRepository = mockk<TripRepository>(relaxed = true)

    @Test
    fun `gider pozitif yazilsa da negatif kaydedilir`() = runTest {
        val saved = slot<Earning>()
        coEvery { shiftRepository.getActiveShift() } returns null
        coEvery { earningRepository.addEarning(capture(saved)) } returns 1L

        val result = AddEarningUseCase(earningRepository, shiftRepository)(
            Earning(amount = 500.0, type = EarningType.EXPENSE, description = "  TAG ücreti ")
        )

        assertTrue(result.isSuccess)
        assertEquals(-500.0, saved.captured.amount, 0.0)
        assertEquals("TAG ücreti", saved.captured.description)
    }

    @Test
    fun `gelir pozitif kaydedilir ve bos aciklama null olur`() = runTest {
        val saved = slot<Earning>()
        coEvery { earningRepository.addEarning(capture(saved)) } returns 1L

        AddEarningUseCase(earningRepository, shiftRepository)(
            Earning(amount = 250.0, type = EarningType.INCOME, description = "   ")
        )

        assertEquals(250.0, saved.captured.amount, 0.0)
        assertNull(saved.captured.description)
    }

    @Test
    fun `sifir tutar reddedilir`() = runTest {
        val result = AddEarningUseCase(earningRepository, shiftRepository)(Earning(amount = 0.0, type = EarningType.INCOME))
        assertTrue(result.isError)
    }

    @Test
    fun `guncellemede tur gidere cevrilince tutar negatif olur`() = runTest {
        val saved = slot<Earning>()
        coEvery { earningRepository.updateEarning(capture(saved)) } returns Result.Success(Unit)

        val result = UpdateEarningUseCase(earningRepository)(Earning(id = 5, amount = 120.0, type = EarningType.EXPENSE))

        assertTrue(result.isSuccess)
        assertEquals(-120.0, saved.captured.amount, 0.0)
    }

    @Test
    fun `silme repository'ye iletilir`() = runTest {
        coEvery { earningRepository.deleteEarning(7) } returns Result.Success(Unit)
        assertTrue(DeleteEarningUseCase(earningRepository)(7).isSuccess)
        coVerify { earningRepository.deleteEarning(7) }
    }

    @Test
    fun `yolculuk kazancla bitirilince gelir kaydi olusur`() = runTest {
        coEvery { tripRepository.getTripById(3) } returns Trip(id = 3, shiftId = 1, startTime = 1_000)
        val earning = slot<Earning>()
        coEvery { earningRepository.addEarning(capture(earning)) } returns 1L

        val result = EndTripUseCase(tripRepository, earningRepository)(
            tripId = 3, distanceMeters = 12_400f, endTime = 5_000, earningAmount = 250.0, description = "Havalimanı"
        )

        val trip = (result as Result.Success).data
        assertEquals(250.0, trip.earningAmount!!, 0.0)
        assertEquals("Havalimanı", trip.description)
        assertEquals(EarningType.INCOME, earning.captured.type)
        assertEquals(3L, earning.captured.tripId)
    }

    @Test
    fun `atla secilince sadece yolculuk kaydedilir`() = runTest {
        coEvery { tripRepository.getTripById(3) } returns Trip(id = 3, shiftId = 1, startTime = 1_000)

        val result = EndTripUseCase(tripRepository, earningRepository)(tripId = 3, distanceMeters = 500f, endTime = 2_000)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { tripRepository.updateTrip(any()) }
        coVerify(exactly = 0) { earningRepository.addEarning(any()) }
    }
}
