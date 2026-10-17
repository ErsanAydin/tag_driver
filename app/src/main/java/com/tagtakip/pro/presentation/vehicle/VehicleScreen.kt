package com.tagtakip.pro.presentation.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.tagtakip.pro.presentation.backup.BackupSection
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagtakip.pro.domain.model.VehicleInfo
import com.tagtakip.pro.domain.model.YakitTuru
import com.tagtakip.pro.presentation.shift.parseDecimalInput

private fun YakitTuru.label(): String = when (this) {
    YakitTuru.DIZEL -> "Dizel"
    YakitTuru.BENZIN -> "Benzin"
    YakitTuru.LPG -> "LPG"
}

/** 0.0 → "", 7.5 → "7,5" */
private fun Double.toInputText(): String =
    if (this <= 0.0) "" else toBigDecimal().stripTrailingZeros().toPlainString().replace('.', ',')

private fun String.decimalFilter(): String = filter { it.isDigit() || it == ',' || it == '.' }.take(8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScreen(viewModel: VehicleViewModel = hiltViewModel()) {
    val info by viewModel.vehicleInfo.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var marka by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var yakitTuru by rememberSaveable { mutableStateOf(YakitTuru.BENZIN) }
    var tuketim by rememberSaveable { mutableStateOf("") }
    var fiyat by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }

    // Kayıtlı bilgiyi alanlara bir kez yükle (kullanıcının yazdıklarını ezme)
    LaunchedEffect(info) {
        val current = info ?: return@LaunchedEffect
        if (!loaded) {
            marka = current.marka
            model = current.model
            yakitTuru = current.yakitTuru
            tuketim = current.lt100km.toInputText()
            fiyat = current.litreBasiFiyat.toInputText()
            loaded = true
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val tuketimValue = parseDecimalInput(tuketim)
    val fiyatValue = parseDecimalInput(fiyat)
    val tuketimError = tuketim.isNotBlank() && (tuketimValue == null || tuketimValue < 0.0 || tuketimValue > 100.0)
    val fiyatError = fiyat.isNotBlank() && (fiyatValue == null || fiyatValue < 0.0)

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Araç Bilgileri", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Yakıt tüketimi girilirse vardiya sonunda yakıt gideri otomatik hesaplanır.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = marka,
                onValueChange = { marka = it.take(40) },
                label = { Text("Marka") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it.take(40) },
                label = { Text("Model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Yakıt türü", style = MaterialTheme.typography.titleSmall)
            val types = YakitTuru.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                types.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = yakitTuru == type,
                        onClick = { yakitTuru = type },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size)
                    ) { Text(type.label()) }
                }
            }
            OutlinedTextField(
                value = tuketim,
                onValueChange = { tuketim = it.decimalFilter() },
                label = { Text("100 km'de kaç litre?") },
                placeholder = { Text("ör. 7,5") },
                singleLine = true,
                isError = tuketimError,
                supportingText = if (tuketimError) { { Text("0 – 100 arası bir değer girin") } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = fiyat,
                onValueChange = { fiyat = it.decimalFilter() },
                label = { Text("Litre başı yakıt fiyatı (₺)") },
                placeholder = { Text("ör. 45,50") },
                singleLine = true,
                isError = fiyatError,
                supportingText = if (fiyatError) { { Text("Geçerli bir fiyat girin") } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    viewModel.saveVehicleInfo(
                        VehicleInfo(
                            marka = marka.trim(),
                            model = model.trim(),
                            yakitTuru = yakitTuru,
                            lt100km = tuketimValue ?: 0.0,
                            litreBasiFiyat = fiyatValue ?: 0.0
                        )
                    )
                },
                enabled = info != null && !tuketimError && !fiyatError,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Kaydet") }

            Spacer(Modifier.height(8.dp))
            Text("Ayarlar", style = MaterialTheme.typography.headlineSmall)
            BackupSection(onMessage = { snackbarHostState.showSnackbar(it) })
        }
    }
}
