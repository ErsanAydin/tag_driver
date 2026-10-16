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
    fun `geliri silinen yolculuk sayilmaz`() {
        val trips = listOf(
            Trip(id = 1, shiftId = 1, startTime = hour, endTime = 2 * hour),
            Trip(id = 2, shiftId = 1, startTime = 3 * hour, endTime = 4 * hour) // geliri silindi
        )
        val earnings = listOf(
            Earning(tripId = 1, amount = 300.0, type = EarningType.INCOME, timestamp = 2 * hour),
            Earning(amount = -50.0, type = EarningType.EXPENSE, timestamp = 3 * hour)
        )

        val stats = useCase.calculate(earnings, trips, emptyList(), dayStart, dayEnd, now = 5 * hour)

        assertEquals(1, stats.tripCount)
        assertEquals(300.0, stats.averagePerTrip, 0.001)
    }

    @Test
    fun `gece yarisini asan vardiya tamamen basladigi gune sayilir`() {
        // 1. gün 22:00 – 2. gün 02:00
        val shifts = listOf(Shift(id = 1, startTime = 22 * hour, endTime = 26 * hour, totalDistanceMeters = 30_000f))
        val earnings = listOf(
            Earning(shiftId = 1, tripId = 1, amount = 400.0, type = EarningType.INCOME, timestamp = 25 * hour)
        )
        val trips = listOf(Trip(id = 1, shiftId = 1, startTime = 24 * hour + 1, endTime = 25 * hour))

        val day1 = useCase.calculate(earnings, trips, shifts, dayStart, dayEnd, now = 30 * hour)
        assertEquals(4 * hour, day1.onlineDurationMillis)
        assertEquals(400.0, day1.totalEarnings, 0.001)
        assertEquals(1, day1.tripCount)
        assertEquals(30_000f, day1.totalDistanceMeters, 0.001f)

        val day2 = useCase.calculate(earnings, trips, shifts, dayEnd + 1, dayEnd + 24 * hour, now = 30 * hour)
        assertEquals(0L, day2.onlineDurationMillis)
        assertEquals(0.0, day2.totalEarnings, 0.001)
        assertEquals(0, day2.tripCount)
        assertEquals(0f, day2.totalDistanceMeters, 0.001f)
    }

    @Test
    fun `gece yarisini asan vardiyada mola suresi dusulur`() {
        // 22:00 – 02:00, 1 saat mola => 3 saat çevrimiçi
        val shifts = listOf(Shift(id = 1, startTime = 22 * hour, endTime = 26 * hour, totalPausedMillis = hour))
        val stats = useCase.calculate(emptyList(), emptyList(), shifts, dayStart, dayEnd, now = 30 * hour)
        assertEquals(3 * hour, stats.onlineDurationMillis)
    }

    @Test
    fun `aktif vardiyada devam eden mola dusulur`() {
        // 20:00'de başladı, 1 saat önceki mola + 22:30'dan beri molada, now = 23:00 => 3s - 1s - 0.5s = 1.5 saat
        val shifts = listOf(
            Shift(id = 1, startTime = 20 * hour, endTime = null, totalPausedMillis = hour, pauseStartTime = 22 * hour + hour / 2)
        )
        val stats = useCase.calculate(emptyList(), emptyList(), shifts, dayStart, dayEnd, now = 23 * hour)
        assertEquals(hour + hour / 2, stats.onlineDurationMillis)
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
        every { earningRepository.observeEarningsBetween(any(), any()) } returns
            flowOf(listOf(Earning(tripId = 1, amount = 200.0, type = EarningType.INCOME, timestamp = hour)))
        every { tripRepository.observeTripsBetween(any(), any()) } returns
            flowOf(listOf(Trip(id = 1, shiftId = 1, startTime = hour, endTime = 2 * hour, fare = 200.0)))
        every { shiftRepository.observeShiftsBetween(any(), any()) } returns
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
