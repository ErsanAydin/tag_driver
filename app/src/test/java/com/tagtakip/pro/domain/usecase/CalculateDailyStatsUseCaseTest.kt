package com.tagtakip.pro.domain.usecase

import app.cash.turbine.test
import com.tagtakip.pro.domain.model.Earning
import com.tagtakip.pro.domain.model.EarningType
import com.tagtakip.pro.domain.model.Shift
import com.tagtakip.pro.domain.model.Trip
import com.tagtakip.pro.domain.repository.EarningRepository
import com.tagtakip.pro.domain.repository.ShiftRepository
import com.tagtakip.pro.domain.repository.TripRepository
import com.tagtakip.pro.domain.usecase.earnings.CalculateDailyStatsUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CalculateDailyStatsUseCaseTest {

    private val hour = 3_600_000L
    private val dayStart = 0L
    private val dayEnd = 24 * hour - 1

    private lateinit var earningRepository: EarningRepository
    private lateinit var tripRepository: TripRepository
    private lateinit var shiftRepository: ShiftRepository
    private lateinit var useCase: CalculateDailyStatsUseCase

    @Before
    fun setUp() {
        earningRepository = mockk()
        tripRepository = mockk()
        shiftRepository = mockk()
        useCase = CalculateDailyStatsUseCase(earningRepository, tripRepository, shiftRepository)
    }

    @Test
    fun `bos veride tum degerler sifirdir`() {
        val stats = useCase.calculate(emptyList(), emptyList(), emptyList(), dayStart, dayEnd, now = 10 * hour)

        assertEquals(0.0, stats.totalEarnings, 0.0)
        assertEquals(0, stats.tripCount)
        assertEquals(0L, stats.onlineDurationMillis)
        assertEquals(0.0, stats.earningsPerHour, 0.0)
        assertEquals(0.0, stats.earningsPerKm, 0.0)
    }

    @Test
    fun `kazanc saatlik ve km basi degerler dogru hesaplanir`() {
        val shifts = listOf(Shift(id = 1, startTime = 8 * hour, endTime = 12 * hour, totalDistanceMeters = 40_000f))
        val trips = listOf(
            Trip(id = 1, shiftId = 1, startTime = 9 * hour, endTime = 9 * hour + 1_000, fare = 300.0),
            Trip(id = 2, shiftId = 1, startTime = 10 * hour, endTime = 10 * hour + 1_000, fare = 500.0),
            Trip(id = 3, shiftId = 1, startTime = 11 * hour, endTime = null) // devam eden, sayılmaz
        )
        val earnings = listOf(
            Earning(tripId = 1, amount = 300.0, type = EarningType.INCOME, timestamp = 9 * hour),
            Earning(tripId = 2, amount = 500.0, type = EarningType.INCOME, timestamp = 10 * hour),
            Earning(amount = 400.0, type = EarningType.INCOME, timestamp = 10 * hour),
            Earning(amount = -200.0, type = EarningType.EXPENSE, description = "Yakıt", timestamp = 11 * hour)
        )

        val stats = useCase.calculate(earnings, trips, shifts, dayStart, dayEnd, now = 13 * hour)

        assertEquals(1_000.0, stats.totalEarnings, 0.001)
        assertEquals(800.0, stats.tripEarnings, 0.001)
        assertEquals(1_200.0, stats.totalIncome, 0.001)
        assertEquals(200.0, stats.totalExpenses, 0.001)
        assertEquals(2, stats.tripCount)
        assertEquals(4 * hour, stats.onlineDurationMillis)
        assertEquals(250.0, stats.earningsPerHour, 0.001)   // 1000 TL / 4 saat
        assertEquals(25.0, stats.earningsPerKm, 0.001)      // 1000 TL / 40 km
        assertEquals(400.0, stats.averagePerTrip, 0.001)    // 800 TL / 2 yolculuk
    }

    @Test
    fun `gece yarisini asan ve aktif vardiya sureleri gun sinirina kirpilir`() {
        val shifts = listOf(
            Shift(id = 1, startTime = -2 * hour, endTime = 2 * hour),   // önceki günden taşan: 2 saat sayılır
            Shift(id = 2, startTime = 20 * hour, endTime = null)        // aktif: now'a kadar 1 saat
        )

        val stats = useCase.calculate(emptyList(), emptyList(), shifts, dayStart, dayEnd, now = 21 * hour)

        assertEquals(3 * hour, stats.onlineDurationMillis)
    }

    @Test
    fun `vardiya mesafesi yoksa yolculuk mesafeleri kullanilir`() {
        val trips = listOf(
            Trip(id = 1, shiftId = 1, startTime = hour, endTime = 2 * hour, distanceMeters = 5_000f),
            Trip(id = 2, shiftId = 1, startTime = 3 * hour, endTime = 4 * hour, distanceMeters = 7_000f)
        )
        val shifts = listOf(Shift(id = 1, startTime = 0, endTime = 5 * hour, totalDistanceMeters = 0f))

        val stats = useCase.calculate(emptyList(), trips, shifts, dayStart, dayEnd, now = 6 * hour)

        assertEquals(12_000f, stats.totalDistanceMeters, 0.001f)
        assertEquals(12.0, stats.totalDistanceKm, 0.001)
    }

    @Test
    fun `invoke repository akislarini birlestirip istatistik yayinlar`() = runTest {
        every { earningRepository.observeEarningsBetween(dayStart, dayEnd) } returns
            flowOf(listOf(Earning(amount = 200.0, type = EarningType.INCOME, timestamp = hour)))
        every { tripRepository.observeTripsBetween(dayStart, dayEnd) } returns
            flowOf(listOf(Trip(id = 1, shiftId = 1, startTime = hour, endTime = 2 * hour, fare = 200.0)))
        every { shiftRepository.observeShiftsBetween(dayStart, dayEnd) } returns
            flowOf(listOf(Shift(id = 1, startTime = 0, endTime = 2 * hour, totalDistanceMeters = 10_000f)))

        useCase(dayStart, dayEnd, nowTicker = flowOf(3 * hour)).test {
            val stats = awaitItem()
            assertEquals(200.0, stats.totalEarnings, 0.001)
            assertEquals(1, stats.tripCount)
            assertEquals(100.0, stats.earningsPerHour, 0.001)
            assertEquals(20.0, stats.earningsPerKm, 0.001)
            awaitComplete()
        }
    }
}
