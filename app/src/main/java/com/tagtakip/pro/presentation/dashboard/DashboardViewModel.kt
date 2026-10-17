package com.tagtakip.pro.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagtakip.pro.core.common.Result
import com.tagtakip.pro.core.util.DateTimeUtils
import com.tagtakip.pro.domain.model.Earning
import com.tagtakip.pro.domain.model.EarningType
import com.tagtakip.pro.domain.model.EarningWithTripDetail
import com.tagtakip.pro.domain.repository.TripRepository
import com.tagtakip.pro.domain.model.Shift
import com.tagtakip.pro.domain.usecase.earnings.AddEarningUseCase
import com.tagtakip.pro.domain.usecase.earnings.CalculateDailyStatsUseCase
import com.tagtakip.pro.domain.usecase.earnings.DailyStats
import com.tagtakip.pro.domain.usecase.earnings.DeleteEarningUseCase
import com.tagtakip.pro.domain.usecase.earnings.UpdateEarningUseCase
import com.tagtakip.pro.domain.usecase.shift.GetActiveShiftUseCase
import com.tagtakip.pro.service.LocationServiceManager
import com.tagtakip.pro.service.ServiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Bugün / bu hafta (Pzt–bugün) / bu ay net kazançları (gelir − gider). */
data class PeriodEarnings(
    val daily: Double = 0.0,
    val weekly: Double = 0.0,
    val monthly: Double = 0.0
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val stats: DailyStats = DailyStats(),
    val periodEarnings: PeriodEarnings = PeriodEarnings(),
    val activeShift: Shift? = null,
    val serviceState: ServiceState = ServiceState.STOPPED,
    val liveDistanceMeters: Float = 0f,
    val recentEarnings: List<EarningWithTripDetail> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val isToday: Boolean = true,
    val message: String? = null
) {
    /** Canlı servis mesafesi yalnızca bugün için eklenir (DB'deki periyodik kayıttan daha güncel olabilir). */
    val displayDistanceMeters: Float
        get() = if (isToday) maxOf(stats.totalDistanceMeters, liveDistanceMeters) else stats.totalDistanceMeters
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val calculateDailyStats: CalculateDailyStatsUseCase,
    private val addEarningUseCase: AddEarningUseCase,
    private val updateEarningUseCase: UpdateEarningUseCase,
    private val deleteEarningUseCase: DeleteEarningUseCase,
    private val tripRepository: TripRepository,
    getActiveShift: GetActiveShiftUseCase,
    locationServiceManager: LocationServiceManager
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    /** Dakikada bir tetiklenir: çevrimiçi süre ve gün değişimi için. */
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_INTERVAL_MS)
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** Özet'te gösterilen gün (varsayılan bugün; gelecek gün seçilemez). */
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    /** Gün değişimini yakalamak için dakikalık "bugün". */
    private val today = ticker.map { LocalDate.now() }.distinctUntilChanged()

    private val dayRange = _selectedDate
        .map { DateTimeUtils.startOfDay(it) to DateTimeUtils.endOfDay(it) }
        .distinctUntilChanged()

    private val statsFlow = dayRange.flatMapLatest { (start, end) ->
        calculateDailyStats(start, end, ticker)
    }

    /** Günlük/haftalık/aylık özet her zaman bugüne göre (seçili günden bağımsız); gün değişince yenilenir. */
    private val periodEarningsFlow = today.flatMapLatest { date ->
        val todayEnd = DateTimeUtils.endOfDay(date)
        val monthEnd = DateTimeUtils.endOfDay(date.withDayOfMonth(date.lengthOfMonth()))
        combine(
            calculateDailyStats(DateTimeUtils.startOfDay(date), todayEnd, ticker),
            calculateDailyStats(DateTimeUtils.startOfWeek(date), todayEnd, ticker),
            calculateDailyStats(DateTimeUtils.startOfDay(date.withDayOfMonth(1)), monthEnd, ticker)
        ) { d, w, m -> PeriodEarnings(d.totalEarnings, w.totalEarnings, m.totalEarnings) }
    }

    private val recentEarningsFlow = dayRange.flatMapLatest { (start, end) ->
        calculateDailyStats.observeDayEarnings(start, end)
            .map { it.take(RECENT_EARNINGS_LIMIT) }
            .mapLatest { earnings -> earnings.withTripDetails() }
    }

    /** Yolculuğa bağlı kayıtlar için yolculuğu DB'den çeker (aynı yolculuk bir kez sorgulanır). */
    private suspend fun List<Earning>.withTripDetails(): List<EarningWithTripDetail> {
        val trips = mapNotNull { it.tripId }.distinct()
            .associateWith { id -> runCatching { tripRepository.getTripById(id) }.getOrNull() }
        return map { EarningWithTripDetail(it, it.tripId?.let(trips::get)) }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(statsFlow, periodEarningsFlow) { st, p -> st to p },
        recentEarningsFlow,
        getActiveShift(),
        combine(locationServiceManager.serviceState, locationServiceManager.totalDistance) { s, d -> s to d },
        combine(message, _selectedDate, today) { msg, date, now -> Triple(msg, date, now) }
    ) { (stats, period), recent, shift, (state, distance), (msg, date, now) ->
        DashboardUiState(
            isLoading = false,
            stats = stats,
            periodEarnings = period,
            activeShift = shift,
            serviceState = state,
            liveDistanceMeters = distance,
            recentEarnings = recent,
            selectedDate = date,
            isToday = date == now,
            message = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    /** [amount] pozitif girilir; gider ise use case negatife çevirir. */
    fun addEarning(amount: Double, type: EarningType, description: String? = null) {
        viewModelScope.launch {
            val result = addEarningUseCase(Earning(amount = amount, type = type, description = description))
            message.value = when (result) {
                is Result.Success -> if (type == EarningType.EXPENSE) "Gider eklendi" else "Gelir eklendi"
                is Result.Error -> result.message ?: "Kayıt eklenemedi"
                Result.Loading -> null
            }
        }
    }

    fun updateEarning(original: Earning, amount: Double, type: EarningType, description: String?) {
        viewModelScope.launch {
            val result = updateEarningUseCase(original.copy(amount = amount, type = type, description = description))
            message.value = when (result) {
                is Result.Success -> "Kayıt güncellendi"
                is Result.Error -> result.message ?: "Kayıt güncellenemedi"
                Result.Loading -> null
            }
        }
    }

    fun deleteEarning(id: Long) {
        viewModelScope.launch {
            message.value = when (val result = deleteEarningUseCase(id)) {
                is Result.Success -> "Kayıt silindi"
                is Result.Error -> result.message ?: "Kayıt silinemedi"
                Result.Loading -> null
            }
        }
    }

    fun consumeMessage() = message.update { null }

    fun onPreviousDay() = _selectedDate.update { it.minusDays(1) }

    fun onNextDay() = _selectedDate.update { current ->
        current.plusDays(1).takeIf { !it.isAfter(LocalDate.now()) } ?: current
    }

    /** Takvimden seçilen gün; gelecek günler bugüne sabitlenir. */
    fun onDatePicked(date: LocalDate) {
        val today = LocalDate.now()
        _selectedDate.value = if (date.isAfter(today)) today else date
    }

    private companion object {
        const val TICK_INTERVAL_MS = 60_000L
        const val RECENT_EARNINGS_LIMIT = 10
    }
}
