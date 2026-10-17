package com.tagtakip.pro.presentation.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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

/** Araç sekmesindeki "Google Drive Yedekleme" bölümü. Mesajlar [onMessage] ile ekranın snackbar'ına iletilir. */
@Composable
fun BackupSection(
    onMessage: suspend (String) -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onSignInResult(it.data)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            onMessage(it)
            viewModel.consumeMessage()
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Google Drive Yedekleme", style = MaterialTheme.typography.titleMedium)
            Text(
                "Her mesai bitişinde veriler Drive'daki gizli uygulama klasörüne otomatik yedeklenir. " +
                    "Aynı hesapla giriş yapan başka bir cihaz yedeği geri yükleyebilir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val email = state.signedInEmail
            Text(
                if (email != null) "Bağlı hesap: $email" else "Giriş yapılmadı",
                style = MaterialTheme.typography.bodyMedium
            )
            state.lastBackupTime?.let {
                Text("Son yedek: ${DateTimeUtils.formatDateTime(it)}", style = MaterialTheme.typography.bodySmall)
            }
            if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (email == null) {
                Button(
                    onClick = { signInLauncher.launch(viewModel.signInIntent()) },
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Google Drive ile giriş yap") }
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
                TextButton(onClick = viewModel::signOut, enabled = !state.isBusy) { Text("Çıkış yap") }
            }
        }
    }

    if (state.confirmRestore) {
        RestoreDialog(
            text = "Yedekten geri yüklensin mi? Bu cihazdaki mevcut veriler Drive'daki yedekle değiştirilir ve uygulama yeniden başlar.",
            onConfirm = viewModel::restore,
            onDismiss = viewModel::dismissRestore
        )
    }
}

/** Uygulama açılışında (MainActivity) Drive'da daha yeni yedek varsa geri yükleme önerir. */
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
            text = "Google Drive'da daha yeni bir yedek var (${DateTimeUtils.formatDateTime(time)}). Geri yüklensin mi?",
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
