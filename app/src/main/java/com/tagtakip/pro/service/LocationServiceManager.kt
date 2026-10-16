package com.tagtakip.pro.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.tagtakip.pro.core.util.hasLocationPermission
import com.tagtakip.pro.domain.model.LocationPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** LocationTrackingService için Intent üreticileri. */
object LocationServiceIntents {

    fun start(context: Context, shiftId: Long? = null): Intent =
        baseIntent(context, LocationTrackingService.ACTION_START).apply {
            putExtra(LocationTrackingService.EXTRA_SHIFT_ID, shiftId ?: LocationTrackingService.NO_SHIFT_ID)
        }

    fun pause(context: Context): Intent = baseIntent(context, LocationTrackingService.ACTION_PAUSE)

    fun resume(context: Context): Intent = baseIntent(context, LocationTrackingService.ACTION_RESUME)

    fun stop(context: Context): Intent = baseIntent(context, LocationTrackingService.ACTION_STOP)

    private fun baseIntent(context: Context, action: String): Intent =
        Intent(context, LocationTrackingService::class.java).setAction(action)
}

private const val TAG = "LocationServiceManager"

/** Servisi foreground olarak başlatır. Konum izni yoksa false döner. */
fun Context.startLocationTracking(shiftId: Long? = null): Boolean {
    if (!hasLocationPermission()) return false
    return try {
        ContextCompat.startForegroundService(this, LocationServiceIntents.start(this, shiftId))
        true
    } catch (e: IllegalStateException) {
        // Android 12+: uygulama arka plandayken FGS başlatılamaz
        Log.e(TAG, "Takip servisi başlatılamadı", e)
        false
    }
}

fun Context.pauseLocationTracking() = sendToRunningService(LocationServiceIntents.pause(this))

fun Context.resumeLocationTracking() = sendToRunningService(LocationServiceIntents.resume(this))

fun Context.stopLocationTracking() {
    if (LocationTrackingService.serviceStateFlow.value == ServiceState.STOPPED) {
        stopService(Intent(this, LocationTrackingService::class.java))
        return
    }
    sendToRunningService(LocationServiceIntents.stop(this))
}

/** Zaten çalışan servise komut gönderir (startService arka plan kısıtına takılmaz). */
private fun Context.sendToRunningService(intent: Intent) {
    if (LocationTrackingService.serviceStateFlow.value == ServiceState.STOPPED) return
    try {
        startService(intent)
    } catch (e: IllegalStateException) {
        Log.e(TAG, "Servise komut gönderilemedi: ${intent.action}", e)
    }
}

/**
 * ViewModel'lara inject edilen yardımcı: servisi kontrol eder ve durum akışlarını sunar.
 */
@Singleton
class LocationServiceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val serviceState: StateFlow<ServiceState> = LocationTrackingService.serviceStateFlow
    val currentLocation: StateFlow<LocationPoint?> = LocationTrackingService.currentLocationFlow
    val totalDistance: StateFlow<Float> = LocationTrackingService.totalDistanceFlow
    val movementMode: StateFlow<MovementMode?> = LocationTrackingService.movementModeFlow

    val isTracking: Boolean get() = serviceState.value != ServiceState.STOPPED

    fun hasLocationPermission(): Boolean = context.hasLocationPermission()

    fun start(shiftId: Long? = null): Boolean = context.startLocationTracking(shiftId)

    fun pause() = context.pauseLocationTracking()

    fun resume() = context.resumeLocationTracking()

    fun stop() = context.stopLocationTracking()
}
