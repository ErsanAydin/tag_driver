package com.tagtakip.pro.presentation.shift

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tagtakip.pro.R
import com.tagtakip.pro.presentation.theme.ErrorRed
import com.tagtakip.pro.presentation.theme.TripActiveBlue

/** Ekranlar FAB'ın altında içerik kalmasın diye bu kadar alt boşluk bırakır. */
val GlobalFabClearance = 88.dp

/**
 * Tüm ekranlarda görünen yolculuk butonu (yalnızca mesai aktifken gösterilir).
 * Mavi: yolculuk sürüyor → bitir (özet diyaloğu onay görevi görür). Kırmızı: yolculuk yok → başlat.
 */
@Composable
fun GlobalTripFab(
    isTripActive: Boolean,
    enabled: Boolean,
    onStartTrip: () -> Unit,
    onEndTrip: () -> Unit
) {
    ExtendedFloatingActionButton(
        onClick = { if (enabled) { if (isTripActive) onEndTrip() else onStartTrip() } },
        containerColor = if (isTripActive) TripActiveBlue else ErrorRed,
        contentColor = Color.White,
        icon = {
            Icon(if (isTripActive) Icons.Filled.Stop else Icons.Filled.DirectionsCar, contentDescription = null)
        },
        text = { Text(stringResource(if (isTripActive) R.string.trip_end else R.string.trip_start)) }
    )
}
