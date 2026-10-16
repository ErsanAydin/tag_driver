package com.tagtakip.pro.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagtakip.pro.core.common.Result
import com.tagtakip.pro.core.util.DateTimeUtils
import com.tagtakip.pro.domain.model.Earning
import com.tagtakip.pro.domain.model.EarningType
import com.tagtakip.pro.domain.model.Shift
import com.tagtakip.pro.domain.usecase.earnings.AddEarningUseCase
import com.tagtakip.pro.domain.usecase.earnings.CalculateDailyStatsUseCase
import com.tagtakip.pro.domain.usecase.earnings.DailyStats
import com.tagtakip.pro.domain.usecase.earnings.GetEarningsByDateRangeUseCase
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
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val stats: DailyStats = DailyStats(),
    val activeShift: Shift? = null,
    val serviceState: ServiceState = ServiceState.STOPPED,
    val liveDistanceMeters: Float = 0f,
    val recentEarnings: List<Earning> = emptyList(),
    val message: String? = null
) {
    /** Canlı servis mesafesi DB'deki periyodik kayıttan daha güncel olabilir. */
    val displayDistanceMeters: Float get() = maxOf(stats.totalDistanceMeters, liveDistanceMeters)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val calculateDailyStats: CalculateDailyStatsUseCase,
    private val getEarningsByDateRange: GetEarningsByDateRangeUseCase,
    private val addEarningUseCase: AddEarningUseCase,
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

    private val dayRange = ticker
        .map { DateTimeUtils.startOfDay() to DateTimeUtils.endOfDay() }
        .distinctUntilChanged()

    private val statsFlow = dayRange.flatMapLatest { (start, end) ->
        calculateDailyStats(start, end, ticker)
    }

    private val recentEarningsFlow = dayRange.flatMapLatest { (start, end) ->
        getEarningsByDateRange(start, end).map { it.take(RECENT_EARNINGS_LIMIT) }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        statsFlow,
        recentEarningsFlow,
        getActiveShift(),
        combine(locationServiceManager.serviceState, locationServiceManager.totalDistance) { s, d -> s to d },
        message
    ) { stats, recent, shift, (state, distance), msg ->
        DashboardUiState(
            isLoading = false,
            stats = stats,
            activeShift = shift,
            serviceState = state,
            liveDistanceMeters = distance,
            recentEarnings = recent,
            message = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun addEarning(amount: Double, type: EarningType, note: String? = null) {
        viewModelScope.launch {
            when (val result = addEarningUseCase(Earning(amount = amount, type = type, note = note))) {
                is Result.Success -> message.value = "Kazanç eklendi"
                is Result.Error -> message.value = result.message ?: "Kazanç eklenemedi"
                Result.Loading -> Unit
            }
        }
    }

    fun consumeMessage() = message.update { null }

    private companion object {
        const val TICK_INTERVAL_MS = 60_000L
        const val RECENT_EARNINGS_LIMIT = 10
    }
}
