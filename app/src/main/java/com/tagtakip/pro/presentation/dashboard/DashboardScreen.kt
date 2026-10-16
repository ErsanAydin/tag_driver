package com.tagtakip.pro.presentation.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagtakip.pro.R
import com.tagtakip.pro.core.util.DateTimeUtils
import com.tagtakip.pro.core.util.toDistanceText
import com.tagtakip.pro.core.util.toTlCurrency
import com.tagtakip.pro.domain.model.EarningWithTripDetail
import com.tagtakip.pro.domain.model.Trip
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import com.tagtakip.pro.presentation.earnings.AddEarningDialog
import com.tagtakip.pro.presentation.earnings.EditEarningDialog
import com.tagtakip.pro.presentation.earnings.amountColor
import com.tagtakip.pro.presentation.earnings.displayTitle
import com.tagtakip.pro.presentation.earnings.signedAmountText
import com.tagtakip.pro.service.ServiceState

@Composable
fun DashboardScreen(
    onNavigateToShift: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val editingEarning = editingId?.let { id -> state.recentEarnings.firstOrNull { it.earning.id == id }?.earning }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.dashboard_add_earning)) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            // Alt boşluk: Kazanç Ekle FAB'ı son kaydı kapatmasın
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(stringResource(R.string.dashboard_title), style = MaterialTheme.typography.titleLarge)
                DateSelector(
                    date = state.selectedDate,
                    isToday = state.isToday,
                    onPrevious = viewModel::onPreviousDay,
                    onNext = viewModel::onNextDay,
                    onOpenPicker = { showDatePicker = true }
                )
            }
            item { TotalEarningsCard(state) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(stringResource(R.string.dashboard_trips), state.stats.tripCount.toString(), Modifier.weight(1f))
                    StatCard(stringResource(R.string.dashboard_distance), state.displayDistanceMeters.toDistanceText(), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(stringResource(R.string.dashboard_online_time), DateTimeUtils.formatDuration(state.stats.onlineDurationMillis), Modifier.weight(1f))
                    StatCard(stringResource(R.string.dashboard_per_hour), state.stats.earningsPerHour.toTlCurrency(), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(stringResource(R.string.dashboard_per_km), state.stats.earningsPerKm.toTlCurrency(), Modifier.weight(1f))
                    StatCard(stringResource(R.string.dashboard_expenses), state.stats.totalExpenses.toTlCurrency(), Modifier.weight(1f))
                }
            }
            if (state.isToday) item { ShiftStatusCard(state, onNavigateToShift) }
            if (state.recentEarnings.isNotEmpty()) {
                item { Text(stringResource(R.string.dashboard_recent_records), style = MaterialTheme.typography.titleMedium) }
                items(state.recentEarnings, key = { it.earning.id }) { item ->
                    EarningRow(item, onEdit = { editingId = item.earning.id })
                }
            }
            item { Spacer(Modifier.padding(bottom = 72.dp)) }
        }
    }

    if (showDatePicker) {
        DashboardDatePickerDialog(
            initial = state.selectedDate,
            onDismiss = { showDatePicker = false },
            onPicked = {
                viewModel.onDatePicked(it)
                showDatePicker = false
            }
        )
    }

    if (showAddDialog) {
        AddEarningDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { amount, type, description ->
                viewModel.addEarning(amount, type, description)
                showAddDialog = false
            }
        )
    }

    editingEarning?.let { earning ->
        EditEarningDialog(
            earning = earning,
            onDismiss = { editingId = null },
            onUpdate = { amount, type, description ->
                viewModel.updateEarning(earning, amount, type, description)
                editingId = null
            },
            onDelete = {
                viewModel.deleteEarning(earning.id)
                editingId = null
            }
        )
    }
}

/** "‹  Bugün · 16 Ekim 2026  ›" — tarihe dokununca takvim açılır; ileri ok bugünde pasiftir. */
@Composable
private fun DateSelector(
    date: LocalDate,
    isToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPicker: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.dashboard_previous_day))
        }
        TextButton(onClick = onOpenPicker, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            val formatted = DateTimeUtils.formatDate(DateTimeUtils.startOfDay(date))
            Text(
                if (isToday) "${stringResource(R.string.dashboard_today)} · $formatted" else formatted,
                style = MaterialTheme.typography.titleSmall
            )
        }
        IconButton(onClick = onNext, enabled = !isToday) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.dashboard_next_day))
        }
    }
}

/** Gelecek günlerin seçilemediği takvim. DatePicker UTC gece yarısı milisaniyeleriyle çalışır. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit
) {
    val todayUtcMillis = remember { LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = 2020..LocalDate.now().year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtcMillis
            override fun isSelectableYear(year: Int) = year <= LocalDate.now().year
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let {
                    onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                } ?: onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun TotalEarningsCard(state: DashboardUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.dashboard_total_earnings),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                state.stats.totalEarnings.toTlCurrency(),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (state.stats.totalExpenses > 0.0) {
                Text(
                    stringResource(
                        R.string.dashboard_income_expense_format,
                        state.stats.totalIncome.toTlCurrency(),
                        state.stats.totalExpenses.toTlCurrency()
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (state.stats.tripCount > 0) {
                Text(
                    "Yolculuk başına ort. ${state.stats.averagePerTrip.toTlCurrency()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ShiftStatusCard(state: DashboardUiState, onNavigateToShift: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                val title = when {
                    state.activeShift == null -> "Mesai kapalı"
                    state.activeShift.isPaused -> "Molada"
                    state.serviceState == ServiceState.PAUSED -> "Mesai duraklatıldı"
                    else -> "Mesai aktif"
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
                state.activeShift?.let {
                    Text(
                        "Başlangıç: ${DateTimeUtils.formatTime(it.startTime)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onNavigateToShift) {
                Text(if (state.activeShift == null) stringResource(R.string.shift_start) else "Detay")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EarningRow(item: EarningWithTripDetail, onEdit: () -> Unit) {
    val earning = item.earning
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onEdit, onLongClick = onEdit)
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(earning.displayTitle(), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    DateTimeUtils.formatTime(earning.timestamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                item.trip?.let { trip ->
                    Text(
                        trip.detailText(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Text(earning.signedAmountText(), style = MaterialTheme.typography.titleMedium, color = earning.amountColor())
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.earning_edit_title))
            }
        }
    }
}

/** "12.4 km • 47 dk • 14:30 – 15:17" (yolculuk sürüyorsa bitiş saati yerine "…"). */
@Composable
private fun Trip.detailText(): String {
    val km = String.format(Locale.US, "%.1f", distanceMeters / 1000f)
    val minutes = durationMillis() / 60_000L
    val end = endTime?.let { DateTimeUtils.formatTime(it) } ?: "…"
    return stringResource(R.string.earning_trip_detail_format, km, minutes, DateTimeUtils.formatTime(startTime), end)
}
