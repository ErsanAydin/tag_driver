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
import kotlinx.coroutines.flow.mapLatest
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
    val recentEarnings: List<EarningWithTripDetail> = emptyList(),
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

    private val dayRange = ticker
        .map { DateTimeUtils.startOfDay() to DateTimeUtils.endOfDay() }
        .distinctUntilChanged()

    private val statsFlow = dayRange.flatMapLatest { (start, end) ->
        calculateDailyStats(start, end, ticker)
    }

    private val recentEarningsFlow = dayRange.flatMapLatest { (start, end) ->
        getEarningsByDateRange(start, end)
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

    private companion object {
        const val TICK_INTERVAL_MS = 60_000L
        const val RECENT_EARNINGS_LIMIT = 10
    }
}
