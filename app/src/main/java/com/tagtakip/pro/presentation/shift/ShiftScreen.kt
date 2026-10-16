package com.tagtakip.pro.presentation.shift

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagtakip.pro.R
import com.tagtakip.pro.core.util.DateTimeUtils
import com.tagtakip.pro.core.util.hasBackgroundLocationPermission
import com.tagtakip.pro.core.util.hasLocationPermission
import com.tagtakip.pro.core.util.requiredForegroundPermissions
import com.tagtakip.pro.core.util.toDistanceText
import com.tagtakip.pro.presentation.theme.ErrorRed
import com.tagtakip.pro.service.MovementMode
import com.tagtakip.pro.service.ServiceState
import java.util.Locale

@Composable
fun ShiftScreen(viewModel: ShiftViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showFareDialog by rememberSaveable { mutableStateOf(false) }
    var hasLocation by remember { mutableStateOf(context.hasLocationPermission()) }
    var hasBackground by remember { mutableStateOf(context.hasBackgroundLocationPermission()) }

    // Konum + (Android 13+) bildirim izinleri; verilirse mesai otomatik başlar
    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasLocation = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocation) viewModel.onStartShift()
    }

    // Arka plan konumu ayrı istenmeli (Android 11+ sistem ayarlar ekranına yönlendirir)
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasBackground = granted }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.shift_title), style = MaterialTheme.typography.titleLarge)

            ShiftSummaryCard(state)

            if (!hasLocation) {
                Text(stringResource(R.string.permission_location_required), color = ErrorRed)
            } else if (!hasBackground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.permission_background_hint), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = {
                            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }) { Text(stringResource(R.string.grant_permission)) }
                    }
                }
            }

            if (!state.isShiftActive) {
                Button(
                    onClick = {
                        if (context.hasLocationPermission()) viewModel.onStartShift()
                        else foregroundPermissionLauncher.launch(requiredForegroundPermissions())
                    },
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) { Text(stringResource(R.string.shift_start), style = MaterialTheme.typography.titleMedium) }
            } else {
                TripControls(
                    hasActiveTrip = state.activeTrip != null,
                    isBusy = state.isBusy,
                    onStartTrip = { viewModel.onStartTrip() },
                    onEndTrip = { showFareDialog = true }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (state.serviceState == ServiceState.PAUSED) viewModel.onResumeTracking()
                            else viewModel.onPauseTracking()
                        },
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Text(
                            stringResource(
                                if (state.serviceState == ServiceState.PAUSED) R.string.shift_resume else R.string.shift_pause
                            )
                        )
                    }
                    Button(
                        onClick = viewModel::onEndShift,
                        enabled = !state.isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) { Text(stringResource(R.string.shift_end)) }
                }
            }
        }
    }

    if (showFareDialog) {
        FareDialog(
            onDismiss = { showFareDialog = false },
            onConfirm = { fare ->
                viewModel.onEndTrip(fare)
                showFareDialog = false
            }
        )
    }
}

@Composable
private fun ShiftSummaryCard(state: ShiftUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val statusText = when {
                !state.isShiftActive -> "Mesai kapalı"
                state.serviceState == ServiceState.PAUSED -> "Takip duraklatıldı"
                state.serviceState == ServiceState.RUNNING -> "Takip aktif"
                else -> "Takip başlatılıyor…"
            }
            AssistChip(onClick = {}, label = { Text(statusText) })
            Text(formatClock(state.elapsedMillis), style = MaterialTheme.typography.displayLarge)
            Text(state.totalDistanceMeters.toDistanceText(), style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary)
            state.currentLocation?.let {
                Text(
                    String.format(Locale.getDefault(), "%.0f km/sa • %s", it.speedKmh, state.movementMode.label()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.activeShift?.let {
                Text(
                    "Başlangıç ${DateTimeUtils.formatTime(it.startTime)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TripControls(
    hasActiveTrip: Boolean,
    isBusy: Boolean,
    onStartTrip: () -> Unit,
    onEndTrip: () -> Unit
) {
    Button(
        onClick = if (hasActiveTrip) onEndTrip else onStartTrip,
        enabled = !isBusy,
        colors = if (hasActiveTrip) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        else ButtonDefaults.buttonColors(),
        modifier = Modifier.fillMaxWidth().height(64.dp)
    ) {
        Text(
            stringResource(if (hasActiveTrip) R.string.trip_end else R.string.trip_start),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun FareDialog(onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val fare = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trip_end)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                label = { Text(stringResource(R.string.amount_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        },
        confirmButton = {
            TextButton(enabled = fare != null && fare >= 0, onClick = { fare?.let(onConfirm) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

private fun MovementMode?.label(): String = when (this) {
    MovementMode.ACTIVE -> "Hareket halinde"
    MovementMode.SLOW -> "Yavaş / duruyor"
    MovementMode.IDLE -> "Mola"
    null -> "—"
}

private fun formatClock(millis: Long): String {
    val totalSeconds = millis / 1000
    return String.format(Locale.US, "%02d:%02d:%02d", totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
}
