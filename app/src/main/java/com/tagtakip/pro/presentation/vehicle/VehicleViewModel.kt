package com.tagtakip.pro.presentation.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagtakip.pro.domain.model.VehicleInfo
import com.tagtakip.pro.domain.repository.VehicleInfoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** null: henüz yükleniyor; yüklendikten sonra kayıt yoksa varsayılan VehicleInfo. */
@HiltViewModel
class VehicleViewModel @Inject constructor(
    private val repository: VehicleInfoRepository
) : ViewModel() {

    val vehicleInfo: StateFlow<VehicleInfo?> = repository.getVehicleInfo()
        .map { it ?: VehicleInfo() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun saveVehicleInfo(info: VehicleInfo) {
        viewModelScope.launch {
            _message.value = runCatching { repository.saveVehicleInfo(info.copy(id = 1)) }
                .fold(onSuccess = { "Kaydedildi" }, onFailure = { "Kaydedilemedi: ${it.message}" })
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
