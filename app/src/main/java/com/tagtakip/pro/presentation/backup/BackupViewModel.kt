package com.tagtakip.pro.presentation.backup

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagtakip.pro.data.backup.DriveBackupManager
import com.tagtakip.pro.domain.repository.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val signedInEmail: String? = null,
    val lastBackupTime: Long? = null,
    val isBusy: Boolean = false,
    /** Drive'da daha yeni yedek varsa değiştirilme zamanı (geri yükleme önerisi). */
    val newerBackupTime: Long? = null,
    /** Manuel "Yedekten Geri Yükle" onayı bekleniyor. */
    val confirmRestore: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val driveBackupManager: DriveBackupManager,
    private val shiftRepository: ShiftRepository
) : ViewModel() {

    private val local = MutableStateFlow(BackupUiState())

    val uiState: StateFlow<BackupUiState> = combine(
        local, driveBackupManager.signedInEmail, driveBackupManager.lastBackupTime
    ) { state, email, last -> state.copy(signedInEmail = email, lastBackupTime = last) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    fun signInIntent(): Intent = driveBackupManager.signInIntent()

    fun onSignInResult(data: Intent?) {
        val ok = driveBackupManager.handleSignInResult(data)
        message(if (ok) "Google Drive bağlandı" else "Google girişi başarısız veya Drive izni verilmedi")
        if (ok) checkForNewerBackup()
    }

    fun signOut() = busy {
        driveBackupManager.signOut()
        message("Google hesabından çıkış yapıldı")
    }

    fun backupNow() = busy {
        val result = driveBackupManager.backupDatabase()
        message(if (result.isSuccess) "Yedekleme tamamlandı" else "Yedekleme başarısız: ${result.exceptionOrNull()?.message.orEmpty()}")
    }

    /** Uygulama açılışında: aktif mesai yoksa ve Drive'daki yedek daha yeniyse onay iste. */
    fun checkForNewerBackup() {
        if (!driveBackupManager.isSignedIn) return
        viewModelScope.launch {
            if (runCatching { shiftRepository.getActiveShift() }.getOrNull() != null) return@launch
            val newer = driveBackupManager.checkAndPromptRestore() ?: return@launch
            local.update { it.copy(newerBackupTime = newer) }
        }
    }

    fun requestRestore() = local.update { it.copy(confirmRestore = true) }

    fun dismissRestore() = local.update { it.copy(newerBackupTime = null, confirmRestore = false) }

    /** Onaylanan geri yükleme; başarılıysa uygulama yeniden başlatılır. */
    fun restore() {
        dismissRestore()
        busy {
            if (shiftRepository.getActiveShift() != null) return@busy message("Önce mesaiyi bitirin")
            val result = driveBackupManager.restoreDatabase()
            if (result.isSuccess) {
                driveBackupManager.restartApp()
            } else {
                message("Geri yükleme başarısız: ${result.exceptionOrNull()?.message.orEmpty()}")
            }
        }
    }

    fun consumeMessage() = local.update { it.copy(message = null) }

    private fun message(text: String) = local.update { it.copy(message = text) }

    private fun busy(block: suspend () -> Unit) {
        if (local.value.isBusy) return
        viewModelScope.launch {
            local.update { it.copy(isBusy = true) }
            try {
                block()
            } finally {
                local.update { it.copy(isBusy = false) }
            }
        }
    }
}
