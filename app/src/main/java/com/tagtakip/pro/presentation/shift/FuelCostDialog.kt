package com.tagtakip.pro.presentation.shift

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale

private val TR = Locale("tr", "TR")

/** "45,50" / "45.50" → 45.5; geçersizse null. */
fun parseDecimalInput(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/**
 * Vardiya sonu yakıt gideri diyaloğu.
 * needsFuelPrice: önce litre fiyatı istenir, ardından onay ekranı gösterilir.
 */
@Composable
fun FuelCostDialog(
    state: FuelCostDialogState,
    onFuelPriceEntered: (Double) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (state.needsFuelPrice) {
        var priceText by rememberSaveable { mutableStateOf("") }
        val price = parseDecimalInput(priceText)
        val valid = price != null && price > 0.0
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Yakıt Gideri") },
            text = {
                Column {
                    Text("Litre başı yakıt fiyatını girin (₺):")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(8) },
                        singleLine = true,
                        placeholder = { Text("ör. 45,50") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = priceText.isNotEmpty() && !valid,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { price?.let(onFuelPriceEntered) }, enabled = valid) { Text("Hesapla") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
        )
    } else {
        val km = String.format(TR, "%.1f", state.distanceKm)
        val litre = String.format(TR, "%.2f", state.yakitLitre)
        val cost = String.format(TR, "%.2f", state.maliyet)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Yakıt Gideri") },
            text = {
                Text(
                    "Bu vardiyada $km km yol gidildi.\nYakıt tüketimi: $litre litre\nToplam maliyet: ₺$cost\n\nGider olarak kaydedeyim mi?"
                )
            },
            confirmButton = { TextButton(onClick = onConfirm) { Text("Evet") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Hayır") } }
        )
    }
}
