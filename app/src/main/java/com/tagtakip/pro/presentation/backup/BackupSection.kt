package com.tagtakip.pro.presentation.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagtakip.pro.core.util.DateTimeUtils
import com.tagtakip.pro.data.backup.BackupManager

/** Araç sekmesinin en üstündeki "Yedekleme (Google Drive)" bölümü. Mesajlar [onMessage] ile snackbar'a iletilir. */
@Composable
fun BackupSection(
    onMessage: suspend (String) -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { viewModel.onCreateTarget(it) }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        viewModel.onOpenExisting(it)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            onMessage(it)
            viewModel.consumeMessage()
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Yedekleme (Google Drive)", style = MaterialTheme.typography.titleMedium)
            Text(
                if (state.hasTarget) "Yedek dosyası: ${state.fileName ?: BackupManager.DEFAULT_FILE_NAME}"
                else "Henüz yedek konumu seçilmedi",
                style = MaterialTheme.typography.bodyMedium
            )
            state.lastBackupTime?.let {
                Text("Son yedek: ${DateTimeUtils.formatDateTime(it)}", style = MaterialTheme.typography.bodySmall)
            }
            if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!state.hasTarget) {
                Button(
                    onClick = { createLauncher.launch(BackupManager.DEFAULT_FILE_NAME) },
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null)
                    Text("  Drive'da yedek oluştur")
                }
                OutlinedButton(
                    onClick = { openLauncher.launch(arrayOf("*/*")) },
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null)
                    Text("  Mevcut yedeği seç (2. cihaz)")
                }
                Text(
                    "Dosya seçicide sol menüden Google Drive'ı seçin. İkinci cihazda aynı Google hesabıyla " +
                        "'Mevcut yedeği seç' deyip ${BackupManager.DEFAULT_FILE_NAME} dosyasını seçin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::backupNow, enabled = !state.isBusy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                        Text("  Şimdi Yedekle")
                    }
                    OutlinedButton(onClick = viewModel::requestRestore, enabled = !state.isBusy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Restore, contentDescription = null)
                        Text("  Geri Yükle")
                    }
                }
                Text(
                    "Her mesai bitişinde otomatik yedeklenir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = viewModel::changeLocation, enabled = !state.isBusy) { Text("Konumu değiştir") }
            }
        }
    }

    if (state.confirmRestore) {
        RestoreDialog(
            text = "Bu cihazdaki veriler yedekteki verilerle değiştirilecek ve uygulama yeniden başlayacak. Devam edilsin mi?",
            onConfirm = viewModel::restore,
            onDismiss = viewModel::dismissRestore
        )
    }
}

/** Uygulama açılışında (MainActivity) yedek dosyası bu cihazdan yeniyse geri yükleme önerir. */
@Composable
fun DriveRestorePrompt(viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var checked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!checked) {
            checked = true
            viewModel.checkForNewerBackup()
        }
    }
    state.newerBackupTime?.let { time ->
        RestoreDialog(
            text = "Drive'da daha yeni bir yedek var (${DateTimeUtils.formatDateTime(time)}). Geri yüklensin mi?",
            onConfirm = viewModel::restore,
            onDismiss = viewModel::dismissRestore
        )
    }
}

@Composable
private fun RestoreDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yedekten Geri Yükle") },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Evet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hayır") } }
    )
}
