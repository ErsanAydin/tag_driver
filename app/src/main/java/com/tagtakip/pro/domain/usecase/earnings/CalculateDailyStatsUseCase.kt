package com.tagtakip.pro.domain.usecase.earnings

import com.tagtakip.pro.domain.model.Earning
import com.tagtakip.pro.domain.model.EarningType
import com.tagtakip.pro.domain.model.Shift
import com.tagtakip.pro.domain.model.Trip
import com.tagtakip.pro.domain.repository.EarningRepository
import com.tagtakip.pro.domain.repository.ShiftRepository
import com.tagtakip.pro.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import kotlin.math.abs

/** Günlük performans özeti. Tüm tutarlar TL. */
data class DailyStats(
    /** Net kazanç = gelirler - giderler. */
    val totalEarnings: Double = 0.0,
    val totalIncome: Double = 0.0,
    /** Giderlerin mutlak toplamı (pozitif gösterilir). */
    val totalExpenses: Double = 0.0,
    val tripEarnings: Double = 0.0,
    val tripCount: Int = 0,
    val totalDistanceMeters: Float = 0f,
    val onlineDurationMillis: Long = 0L,
    val earningsPerHour: Double = 0.0,
    val earningsPerKm: Double = 0.0,
    val averagePerTrip: Double = 0.0
) {
    val totalDistanceKm: Double get() = totalDistanceMeters / 1000.0
}

/**
 * Kazanç, yolculuk ve vardiya listelerinden günlük istatistikleri hesaplar.
 * Hesaplama saf fonksiyondur ([calculate]) — test edilebilirlik için repository'den ayrıdır.
 */
class CalculateDailyStatsUseCase @Inject constructor(
    private val earningRepository: EarningRepository,
    private val tripRepository: TripRepository,
    private val shiftRepository: ShiftRepository
) {

    /**
     * [dayStart, dayEnd] aralığı için istatistikleri reaktif olarak yayınlar.
     * [nowTicker] her emit ettiğinde (ör. dakikada bir) aktif vardiya süresi yeniden hesaplanır.
     */
    operator fun invoke(
        dayStart: Long,
        dayEnd: Long,
        nowTicker: Flow<Long> = flowOf(System.currentTimeMillis())
    ): Flow<DailyStats> = combine(
        earningRepository.observeEarningsBetween(dayStart, dayEnd),
        tripRepository.observeTripsBetween(dayStart, dayEnd),
        shiftRepository.observeShiftsBetween(dayStart, dayEnd),
        nowTicker
    ) { earnings, trips, shifts, now ->
        calculate(earnings, trips, shifts, dayStart, dayEnd, now)
    }

    fun calculate(
        earnings: List<Earning>,
        trips: List<Trip>,
        shifts: List<Shift>,
        dayStart: Long,
        dayEnd: Long,
        now: Long
    ): DailyStats {
        val income = earnings.filter { it.type == EarningType.INCOME }.sumOf { abs(it.amount) }
        val expenses = earnings.filter { it.type == EarningType.EXPENSE }.sumOf { abs(it.amount) }
        val total = income - expenses
        val tripEarnings = earnings.filter { it.type == EarningType.INCOME && it.tripId != null }.sumOf { abs(it.amount) }

        val completedTrips = trips.filter { !it.isActive }
        val tripCount = completedTrips.size

        // Vardiya süresini gün sınırlarına kırparak hesapla (gece yarısını aşan vardiyalar için)
        val onlineMillis = shifts.sumOf { shift ->
            val start = maxOf(shift.startTime, dayStart)
            val end = minOf(shift.endTime ?: now, dayEnd, now)
            (end - start).coerceAtLeast(0L)
        }

        // Mesafe: vardiya GPS mesafesi öncelikli, yoksa yolculuk mesafeleri
        val shiftDistance = shifts.sumOf { it.totalDistanceMeters.toDouble() }.toFloat()
        val tripDistance = completedTrips.sumOf { it.distanceMeters.toDouble() }.toFloat()
        val distance = if (shiftDistance > 0f) shiftDistance else tripDistance

        val hours = onlineMillis / 3_600_000.0
        val km = distance / 1000.0

        return DailyStats(
            totalEarnings = total,
            totalIncome = income,
            totalExpenses = expenses,
            tripEarnings = tripEarnings,
            tripCount = tripCount,
            totalDistanceMeters = distance,
            onlineDurationMillis = onlineMillis,
            earningsPerHour = if (hours > 0.0) total / hours else 0.0,
            earningsPerKm = if (km > 0.0) total / km else 0.0,
            averagePerTrip = if (tripCount > 0) tripEarnings / tripCount else 0.0
        )
    }
}
