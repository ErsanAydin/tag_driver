package com.tagtakip.pro.presentation.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagtakip.pro.data.backup.BackupManager
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
    val hasTarget: Boolean = false,
    val fileName: String? = null,
    val lastBackupTime: Long? = null,
    val isBusy: Boolean = false,
    /** Yedek dosyası bu cihazdan daha yeniyse değiştirilme zamanı (geri yükleme önerisi). */
    val newerBackupTime: Long? = null,
    /** Manuel "Yedekten Geri Yükle" onayı bekleniyor. */
    val confirmRestore: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val shiftRepository: ShiftRepository
) : ViewModel() {

    private val local = MutableStateFlow(BackupUiState())

    val uiState: StateFlow<BackupUiState> = combine(
        local, backupManager.backupUri, backupManager.displayName, backupManager.lastBackupTime
    ) { state, uri, name, last ->
        state.copy(hasTarget = uri != null, fileName = name, lastBackupTime = last)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    /** "Drive'da yedek oluştur": yeni belge seçildi, hemen ilk yedeği yaz. */
    fun onCreateTarget(uri: Uri?) {
        uri ?: return
        backupManager.setTarget(uri)
        backupNow()
    }

    /** "Mevcut yedeği seç": başka cihazın yedeği seçildi, geri yükleme onayı iste. */
    fun onOpenExisting(uri: Uri?) {
        uri ?: return
        backupManager.setTarget(uri)
        local.update { it.copy(confirmRestore = true) }
    }

    fun changeLocation() {
        backupManager.clear()
        message("Yedek konumu kaldırıldı; yeni bir konum seçin")
    }

    fun backupNow() = busy {
        val result = backupManager.backup()
        message(
            if (result.isSuccess) "Yedek Google Drive'a kaydedildi"
            else "Yedekleme başarısız: ${result.exceptionOrNull()?.message.orEmpty()}"
        )
    }

    /** Uygulama açılışında: aktif mesai yoksa ve yedek dosyası daha yeniyse onay iste. */
    fun checkForNewerBackup() {
        if (!backupManager.hasTarget) return
        viewModelScope.launch {
            if (runCatching { shiftRepository.getActiveShift() }.getOrNull() != null) return@launch
            val newer = backupManager.newerRemote() ?: return@launch
            local.update { it.copy(newerBackupTime = newer) }
        }
    }

    fun requestRestore() = local.update { it.copy(confirmRestore = true) }

    fun dismissRestore() {
        local.value.newerBackupTime?.let(backupManager::dismiss)
        local.update { it.copy(newerBackupTime = null, confirmRestore = false) }
    }

    /** Onaylanan geri yükleme; başarılıysa uygulama yeniden başlatılır. */
    fun restore() {
        local.update { it.copy(newerBackupTime = null, confirmRestore = false) }
        busy {
            if (shiftRepository.getActiveShift() != null) return@busy message("Önce mesaiyi bitirin")
            val result = backupManager.restore()
            if (result.isSuccess) {
                backupManager.restartApp()
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
