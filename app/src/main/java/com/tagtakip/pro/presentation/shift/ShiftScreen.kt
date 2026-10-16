package com.tagtakip.pro.presentation.shift

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    val isTripActive by viewModel.isTripActive.collectAsStateWithLifecycle()
    val tripStartTime by viewModel.tripStartTime.collectAsStateWithLifecycle()
    val tripDistanceKm by viewModel.currentTripDistanceKm.collectAsStateWithLifecycle()
    val tripElapsed by viewModel.currentTripElapsedMillis.collectAsStateWithLifecycle()
    val tripSummary by viewModel.tripSummary.collectAsStateWithLifecycle()
    var pendingAction by rememberSaveable { mutableStateOf<PendingAction?>(null) }
    var hasLocation by remember { mutableStateOf(context.hasLocationPermission()) }
    var hasBackground by remember { mutableStateOf(context.hasBackgroundLocationPermission()) }

    // Konum + (Android 13+) bildirim izinleri; verilirse mesai otomatik başlar
    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasLocation = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocation) {
            if (pendingAction == PendingAction.START_TRIP) viewModel.onStartTrip() else viewModel.onStartShift()
        }
        pendingAction = null
    }

    // Arka plan konumu ayrı istenmeli (Android 11+ sistem ayarlar ekranına yönlendirir)
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasBackground = granted }

    // Mesajlar (snackbar) ve yolculuk özeti diyaloğu kök Scaffold'da (NavGraph) gösterilir.
    Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + GlobalFabClearance),
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

            // 1) Mesai butonu
            if (!state.isShiftActive) {
                Button(
                    onClick = {
                        if (context.hasLocationPermission()) viewModel.onStartShift()
                        else {
                            pendingAction = PendingAction.START_SHIFT
                            foregroundPermissionLauncher.launch(requiredForegroundPermissions())
                        }
                    },
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.shift_start), style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Button(
                    onClick = viewModel::onEndShift,
                    enabled = !state.isBusy && tripSummary == null,
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.shift_end), style = MaterialTheme.typography.titleMedium)
                }
            }

            // 2) Yolculuk butonu
            TripButton(
                isTripActive = isTripActive,
                enabled = !state.isBusy && tripSummary == null,
                onStartTrip = {
                    if (context.hasLocationPermission()) viewModel.onStartTrip()
                    else {
                        pendingAction = PendingAction.START_TRIP
                        foregroundPermissionLauncher.launch(requiredForegroundPermissions())
                    }
                },
                onEndTrip = viewModel::onEndTripRequested
            )
            if (!state.isShiftActive) {
                Text(
                    stringResource(R.string.trip_auto_shift_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isTripActive) {
                ActiveTripCard(elapsedMillis = tripElapsed, distanceKm = tripDistanceKm, startTime = tripStartTime)
            }

            if (state.isShiftActive) {
                OutlinedButton(
                    onClick = {
                        if (state.serviceState == ServiceState.PAUSED) viewModel.onResumeTracking()
                        else viewModel.onPauseTracking()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(
                        stringResource(
                            if (state.serviceState == ServiceState.PAUSED) R.string.shift_resume else R.string.shift_pause
                        )
                    )
                }
            }
    }
}

private enum class PendingAction { START_SHIFT, START_TRIP }

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
private fun TripButton(
    isTripActive: Boolean,
    enabled: Boolean,
    onStartTrip: () -> Unit,
    onEndTrip: () -> Unit
) {
    Button(
        onClick = if (isTripActive) onEndTrip else onStartTrip,
        enabled = enabled,
        colors = if (isTripActive) ButtonDefaults.buttonColors(containerColor = ErrorRed)
        else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
        modifier = Modifier.fillMaxWidth().height(64.dp)
    ) {
        Icon(if (isTripActive) Icons.Filled.Flag else Icons.Filled.DirectionsCar, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(if (isTripActive) R.string.trip_end else R.string.trip_start),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun ActiveTripCard(elapsedMillis: Long, distanceKm: Float, startTime: Long?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(stringResource(R.string.trip_in_progress), style = MaterialTheme.typography.labelLarge)
            Text(formatClock(elapsedMillis), style = MaterialTheme.typography.headlineLarge)
            Text(
                stringResource(R.string.distance_km_format, String.format(Locale.US, "%.2f", distanceKm)),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            startTime?.let {
                Text(
                    stringResource(R.string.trip_started_at, DateTimeUtils.formatTime(it)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
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
