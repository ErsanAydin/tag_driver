package com.tagtakip.pro.presentation.shift

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.tagtakip.pro.R
import com.tagtakip.pro.core.util.DateTimeUtils
import com.tagtakip.pro.presentation.earnings.MAX_DESCRIPTION_LENGTH
import com.tagtakip.pro.presentation.earnings.parseAmount
import com.tagtakip.pro.presentation.earnings.sanitizeAmountInput
import java.util.Locale

/**
 * Yolculuk bitince gösterilen özet. "Kaydet" yolculuk + (tutar girildiyse) gelir kaydeder,
 * "Atla" yalnızca yolculuğu kaydeder. Yanlışlıkla kapanmaması için dışarı dokunarak kapatılamaz.
 */
@Composable
fun TripSummaryDialog(
    summary: TripSummary,
    isBusy: Boolean,
    onSave: (amount: Double?, description: String?) -> Unit,
    onSkip: () -> Unit
) {
    var amountText by rememberSaveable(summary.tripId) { mutableStateOf("") }
    var description by rememberSaveable(summary.tripId) { mutableStateOf("") }
    val amount = parseAmount(amountText)
    // Boş tutar = kazançsız kayıt; yazılmışsa geçerli olmalı
    val canSave = !isBusy && (amountText.isBlank() || amount != null)

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.trip_summary_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryRow("📍", stringResource(R.string.trip_summary_distance),
                    stringResource(R.string.distance_km_format, String.format(Locale.US, "%.1f", summary.distanceKm)))
                SummaryRow("⏱", stringResource(R.string.trip_summary_duration), DateTimeUtils.formatDuration(summary.durationMillis))
                SummaryRow("📅", stringResource(R.string.trip_summary_start), DateTimeUtils.formatTime(summary.startTime))
                SummaryRow("🏁", stringResource(R.string.trip_summary_end), DateTimeUtils.formatTime(summary.endTime))
                HorizontalDivider(Modifier.fillMaxWidth())
                Text(stringResource(R.string.trip_summary_question), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = sanitizeAmountInput(it) },
                    label = { Text(stringResource(R.string.amount_hint)) },
                    suffix = { Text("₺") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(MAX_DESCRIPTION_LENGTH) },
                    label = { Text(stringResource(R.string.description_optional)) },
                    placeholder = { Text(stringResource(R.string.trip_description_placeholder)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = { onSave(amount, description.trim().ifEmpty { null }) }) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(enabled = !isBusy, onClick = onSkip) { Text(stringResource(R.string.skip)) }
        }
    )
}

@Composable
private fun SummaryRow(emoji: String, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("$emoji  $label", style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
