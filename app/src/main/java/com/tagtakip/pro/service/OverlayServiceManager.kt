package com.tagtakip.pro.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Overlay tuşu ile uygulama arasındaki explicit broadcast sözleşmesi. */
object OverlayActions {
    const val ACTION_OVERLAY_TRIP_TOGGLE = "com.tagtakip.pro.ACTION_OVERLAY_TRIP_TOGGLE"

    /** true: yolculuk başlat (kırmızı tuş), false: yolculuğu bitir (mavi tuş). */
    const val EXTRA_START_TRIP = "com.tagtakip.pro.EXTRA_START_TRIP"

    /** MainActivity'nin dinamik receiver'ı kayıtlı mı (activity yaşıyor mu)? */
    @Volatile
    var isReceiverRegistered: Boolean = false

    /** Uygulama ön plandayken overlay gizlenir; ekranda zaten in-app FAB vardır. */
    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    fun setAppInForeground(value: Boolean) {
        _isAppInForeground.value = value
    }
}

/** OverlayService'i başlatır / durdurur ve izin durumunu kontrol eder. */
@Singleton
class OverlayServiceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    /** İzin yoksa false döner; servis başlatılmaz. */
    fun start(): Boolean {
        if (!canDrawOverlays()) return false
        return try {
            context.startService(Intent(context, OverlayService::class.java))
            true
        } catch (e: IllegalStateException) {
            // Android 8+: arka plandan normal servis başlatılamayabilir
            Log.e(TAG, "Overlay servisi başlatılamadı", e)
            false
        }
    }

    fun stop() {
        context.stopService(Intent(context, OverlayService::class.java))
    }

    companion object {
        private const val TAG = "OverlayServiceManager"

        /** "Diğer uygulamaların üzerinde göster" ayar ekranı. */
        fun permissionSettingsIntent(context: Context): Intent =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
