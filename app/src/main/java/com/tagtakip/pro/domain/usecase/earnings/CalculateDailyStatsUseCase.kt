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
 *
 * Gün ataması mesai BAŞLANGICINA göredir:
 * - Bir vardiya (süresi, mesafesi) tamamen başladığı güne aittir; 23:59'u aşsa da ertesi güne sayılmaz.
 * - Vardiyaya bağlı kazanç/yolculuklar vardiyanın başladığı güne aittir (shiftId üzerinden).
 * - Vardiyasız kazançlar kendi zaman damgasına göre sayılır.
 * Hesaplama saf fonksiyondur ([calculate]) — test edilebilirlik için repository'den ayrıdır.
 */
class CalculateDailyStatsUseCase @Inject constructor(
    private val earningRepository: EarningRepository,
    private val tripRepository: TripRepository,
    private val shiftRepository: ShiftRepository
) {

    /**
     * [dayStart, dayEnd] günü için istatistikleri reaktif olarak yayınlar.
     * Gece yarısını aşan vardiyaların kayıtları için gün aralığı her iki yönde genişletilip
     * [calculate] içinde vardiya başlangıcına göre filtrelenir.
     * [nowTicker] her emit ettiğinde (ör. dakikada bir) aktif vardiya süresi yeniden hesaplanır.
     */
    operator fun invoke(
        dayStart: Long,
        dayEnd: Long,
        nowTicker: Flow<Long> = flowOf(System.currentTimeMillis())
    ): Flow<DailyStats> {
        val (wideStart, wideEnd) = widenedRange(dayStart, dayEnd)
        return combine(
            earningRepository.observeEarningsBetween(wideStart, wideEnd),
            tripRepository.observeTripsBetween(wideStart, wideEnd),
            shiftRepository.observeShiftsBetween(wideStart, wideEnd),
            nowTicker
        ) { earnings, trips, shifts, now ->
            calculate(earnings, trips, shifts, dayStart, dayEnd, now)
        }
    }

    /** Güne atanmış kazanç kayıtları (yeniden eskiye), Özet listesi için. */
    fun observeDayEarnings(dayStart: Long, dayEnd: Long): Flow<List<Earning>> {
        val (wideStart, wideEnd) = widenedRange(dayStart, dayEnd)
        return combine(
            earningRepository.observeEarningsBetween(wideStart, wideEnd),
            shiftRepository.observeShiftsBetween(wideStart, wideEnd)
        ) { earnings, shifts ->
            val byId = shifts.associateBy { it.id }
            earnings.filter { belongsToDay(it.attributionTime(byId), dayStart, dayEnd) }
                .sortedByDescending { it.timestamp }
        }
    }

    fun calculate(
        allEarnings: List<Earning>,
        allTrips: List<Trip>,
        allShifts: List<Shift>,
        dayStart: Long,
        dayEnd: Long,
        now: Long
    ): DailyStats {
        val shiftsById = allShifts.associateBy { it.id }
        // Yalnızca bu gün BAŞLAYAN vardiyalar
        val shifts = allShifts.filter { belongsToDay(it.startTime, dayStart, dayEnd) }
        val earnings = allEarnings.filter { belongsToDay(it.attributionTime(shiftsById), dayStart, dayEnd) }
        val trips = allTrips.filter { trip ->
            belongsToDay(shiftsById[trip.shiftId]?.startTime ?: trip.startTime, dayStart, dayEnd)
        }

        val income = earnings.filter { it.type == EarningType.INCOME }.sumOf { abs(it.amount) }
        val expenses = earnings.filter { it.type == EarningType.EXPENSE }.sumOf { abs(it.amount) }
        val total = income - expenses
        val tripEarnings = earnings.filter { it.type == EarningType.INCOME && it.tripId != null }.sumOf { abs(it.amount) }

        val completedTrips = trips.filter { !it.isActive }
        // Yolculuk sayısı gelire göre: geliri silinmiş yolculuklar sayılmaz.
        val tripCount = earnings
            .filter { it.type == EarningType.INCOME }
            .mapNotNull { it.tripId }
            .distinct()
            .size

        // Vardiyanın tamamı başladığı güne sayılır (kırpma yok); molalar düşülür, aktif vardiya now'a kadar
        val onlineMillis = shifts.sumOf { it.durationMillis(now) }

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

    companion object {
        /** Gece yarısını aşan vardiyaların kayıtlarını yakalamak için aralık genişletme payı. */
        private const val ATTRIBUTION_MARGIN_MS = 2 * 24 * 60 * 60 * 1000L

        private fun widenedRange(dayStart: Long, dayEnd: Long): Pair<Long, Long> =
            (dayStart - ATTRIBUTION_MARGIN_MS) to (dayEnd + ATTRIBUTION_MARGIN_MS)

        private fun belongsToDay(time: Long, dayStart: Long, dayEnd: Long): Boolean = time in dayStart..dayEnd

        /** Vardiyaya bağlıysa vardiya başlangıcı, değilse (veya vardiya bulunamazsa) kendi zamanı. */
        private fun Earning.attributionTime(shiftsById: Map<Long, Shift>): Long =
            shiftId?.let { shiftsById[it]?.startTime } ?: timestamp
    }
}
