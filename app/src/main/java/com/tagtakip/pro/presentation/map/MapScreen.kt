package com.tagtakip.pro.presentation.map

import com.tagtakip.pro.presentation.shift.GlobalFabClearance
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagtakip.pro.R
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

// Koyu temada okunaklı parlak sayaç rengi
private val CounterAmber = Color(0xFFFFC107)
private val ActiveGreen = Color(0xFF4CAF50)
private val StoppedRed = Color(0xFFE57373)
private val PausedOrange = Color(0xFFFFB74D)

/** Vardiya ilerleme çubuğu için ara hedef (km). */
private const val MILESTONE_KM = 10f

/** Sade GPS mesafe sayacı ekranı (harita yok, API anahtarı gerekmez). */
@Composable
fun DistanceScreen(viewModel: DistanceViewModel = hiltViewModel()) {
    val totalKm by viewModel.totalDistanceKm.collectAsStateWithLifecycle()
    val sessionKm by viewModel.sessionDistanceKm.collectAsStateWithLifecycle()
    val speedKmh by viewModel.currentSpeedKmh.collectAsStateWithLifecycle()
    val isTracking by viewModel.isTracking.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()

    // Sayaçların yumuşak animasyonlu güncellenmesi
    val animatedTotal by animateFloatAsState(totalKm, tween(800), label = "totalKm")
    val animatedSession by animateFloatAsState(sessionKm, tween(800), label = "sessionKm")
    val animatedSpeed by animateFloatAsState(speedKmh, tween(500), label = "speed")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + GlobalFabClearance),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.distance_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            ServiceStatusChip(isTracking = isTracking, isPaused = isPaused)
        }

        // Büyük toplam km sayacı
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.distance_today_total),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.distance_km_format, formatKm(animatedTotal)),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CounterAmber
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoCard(
                icon = Icons.Filled.Speed,
                text = stringResource(R.string.distance_speed_format, animatedSpeed.roundToInt()),
                modifier = Modifier.weight(1f)
            )
            InfoCard(
                icon = Icons.Filled.Route,
                text = stringResource(R.string.distance_shift_format, formatKm(animatedSession)),
                modifier = Modifier.weight(1f)
            )
        }

        // Vardiya içinde bir sonraki 10 km hedefine ilerleme
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                val nextMilestone = (floor(animatedSession / MILESTONE_KM) + 1f) * MILESTONE_KM
                val progress = ((animatedSession % MILESTONE_KM) / MILESTONE_KM).coerceIn(0f, 1f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.distance_next_milestone, nextMilestone.roundToInt()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = CounterAmber
                )
            }
        }

        if (!isTracking) {
            Text(
                text = stringResource(R.string.distance_not_tracking_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ServiceStatusChip(isTracking: Boolean, isPaused: Boolean) {
    val (labelRes, icon, color) = when {
        isPaused -> Triple(R.string.distance_status_paused, Icons.Filled.PauseCircle, PausedOrange)
        isTracking -> Triple(R.string.distance_status_active, Icons.Filled.CheckCircle, ActiveGreen)
        else -> Triple(R.string.distance_status_stopped, Icons.Filled.StopCircle, StoppedRed)
    }
    AssistChip(
        onClick = {},
        label = { Text(stringResource(labelRes)) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color)
    )
}

@Composable
private fun InfoCard(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = CounterAmber)
            Spacer(Modifier.width(8.dp))
            Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatKm(km: Float): String = String.format(Locale.US, "%.1f", km)
