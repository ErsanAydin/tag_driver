package com.tagtakip.pro.presentation.dashboard

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagtakip.pro.R
import com.tagtakip.pro.core.util.DateTimeUtils
import com.tagtakip.pro.core.util.toDistanceText
import com.tagtakip.pro.core.util.toTlCurrency
import com.tagtakip.pro.domain.model.Earning
import com.tagtakip.pro.domain.model.EarningType
import com.tagtakip.pro.presentation.theme.EarningGreen
import com.tagtakip.pro.service.ServiceState

@Composable
fun DashboardScreen(
    onNavigateToShift: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(stringResource(R.string.dashboard_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    DateTimeUtils.formatDate(System.currentTimeMillis()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    StatCard(stringResource(R.string.dashboard_tips), state.stats.tips.toTlCurrency(), Modifier.weight(1f))
                }
            }
            item { ShiftStatusCard(state, onNavigateToShift) }
            if (state.recentEarnings.isNotEmpty()) {
                item { Text("Son Kazançlar", style = MaterialTheme.typography.titleMedium) }
                items(state.recentEarnings, key = { it.id }) { EarningRow(it) }
            }
            item { Spacer(Modifier.padding(bottom = 72.dp)) }
        }
    }

    if (showAddDialog) {
        AddEarningDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { amount, type ->
                viewModel.addEarning(amount, type)
                showAddDialog = false
            }
        )
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

@Composable
private fun EarningRow(earning: Earning) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(earning.type.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    DateTimeUtils.formatTime(earning.timestamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(earning.amount.toTlCurrency(), style = MaterialTheme.typography.titleMedium, color = EarningGreen)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEarningDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, EarningType) -> Unit
) {
    var amountText by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(EarningType.TRIP_FARE) }
    val amount = amountText.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_add_earning)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                    label = { Text(stringResource(R.string.amount_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EarningType.entries.take(3).forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.displayName) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = amount != null && amount > 0, onClick = { amount?.let { onConfirm(it, type) } }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
