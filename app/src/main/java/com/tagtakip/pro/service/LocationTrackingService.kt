package com.tagtakip.pro.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.tagtakip.pro.MainActivity
import com.tagtakip.pro.R
import com.tagtakip.pro.core.common.Constants
import com.tagtakip.pro.core.di.ApplicationScope
import com.tagtakip.pro.core.di.IoDispatcher
import com.tagtakip.pro.core.util.hasLocationPermission
import com.tagtakip.pro.core.util.hasNotificationPermission
import com.tagtakip.pro.core.util.toDistanceText
import com.tagtakip.pro.core.util.toLocationPoint
import com.tagtakip.pro.data.local.dao.LocationPointDao
import com.tagtakip.pro.data.local.entity.toEntity
import com.tagtakip.pro.domain.model.LocationPoint
import com.tagtakip.pro.domain.repository.ShiftRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Servisin yaşam döngüsü durumu. */
enum class ServiceState { RUNNING, PAUSED, STOPPED }

/** Hareket rejimi — konum isteği aralığını belirler. */
enum class MovementMode { ACTIVE, SLOW, IDLE }

/**
 * LocationTrackingService:
 * - Foreground Service olarak çalışır (kalıcı bildirim gösterir)
 * - FusedLocationProviderClient kullanır
 * - Hareket durumuna göre interval dinamik ayarlanır:
 *   - Aktif hareket (> 1.5 m/s): interval=5s, fastest=3s, HIGH_ACCURACY
 *   - Yavaş hareket / kısa duruş: interval=15s, fastest=10s, BALANCED
 *   - Duraklamış (< 0.5 m/s, 10+ dk): interval=60s, fastest=30s, BALANCED — mola bildirimi gönderilir
 * - Her kabul edilen LocationPoint Room DB'ye kaydedilir (coroutine scope)
 * - ServiceState (RUNNING, PAUSED, STOPPED) StateFlow ile yayınlanır
 * - Activity/ViewModel'lar companion StateFlow'larını (veya bind edip instance StateFlow'larını) collect edebilir
 * - Android 14 uyumlu: foregroundServiceType=location, POST_NOTIFICATIONS kontrolü
 * - Intent action'ları: ACTION_START, ACTION_PAUSE, ACTION_RESUME, ACTION_STOP
 */
@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject lateinit var fusedLocationClient: FusedLocationProviderClient
    @Inject lateinit var shiftRepository: ShiftRepository
    @Inject lateinit var locationPointDao: LocationPointDao
    @Inject @IoDispatcher lateinit var ioDispatcher: CoroutineDispatcher
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val binder = LocalBinder()

    // --- Instance StateFlow'ları (bound client'lar için; companion ile aynı kaynağı paylaşır) ---
    val serviceState: StateFlow<ServiceState> get() = serviceStateFlow
    val currentLocation: StateFlow<LocationPoint?> get() = currentLocationFlow
    val totalDistance: StateFlow<Float> get() = totalDistanceFlow

    // --- Takip durumu ---
    private var currentShiftId: Long? = null
    private var lastAcceptedLocation: Location? = null
    private var totalDistanceMeters: Float = 0f
    private var currentMode: MovementMode? = null
    private var stationarySince: Long? = null
    private var breakNotified: Boolean = false
    private var lastDistancePersistAt: Long = 0L
    private var lastNotificationUpdateAt: Long = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::handleLocation)
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): LocationTrackingService = this@LocationTrackingService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels(this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking(intent.getLongExtra(EXTRA_SHIFT_ID, NO_SHIFT_ID))
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_STOP -> stopTracking()
            // START_STICKY ile sistem tarafından yeniden başlatıldı: aktif vardiya varsa devam et
            null -> startTracking(NO_SHIFT_ID)
            else -> Log.w(TAG, "Bilinmeyen action: ${intent.action}")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        persistDistance(force = true)
        if (serviceStateFlow.value != ServiceState.STOPPED) {
            mutableServiceState.value = ServiceState.STOPPED
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    // region Lifecycle actions

    private fun startTracking(requestedShiftId: Long) {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Konum izni yok, servis başlatılamıyor")
            // startForegroundService sonrası 5 sn kuralı: yine de foreground'a geçmeyi dene, sonra kapan
            startForegroundSafely()
            shutdown()
            return
        }
        // startForegroundService() çağrısından sonra hemen foreground'a geçilmeli
        if (!startForegroundSafely()) {
            shutdown()
            return
        }
        if (serviceStateFlow.value == ServiceState.RUNNING && currentShiftId != null &&
            (requestedShiftId == NO_SHIFT_ID || requestedShiftId == currentShiftId)
        ) {
            return // zaten çalışıyor (idempotent)
        }

        serviceScope.launch {
            val shift = runCatching {
                if (requestedShiftId != NO_SHIFT_ID) shiftRepository.getShiftById(requestedShiftId)
                else shiftRepository.getActiveShift()
            }.onFailure { Log.e(TAG, "Vardiya okunamadı", it) }.getOrNull()

            if (shift == null || !shift.isActive) {
                Log.w(TAG, "Aktif vardiya bulunamadı, servis durduruluyor")
                stopTracking()
                return@launch
            }

            if (currentShiftId != shift.id) {
                resetTrackingState()
                currentShiftId = shift.id
                totalDistanceMeters = shift.totalDistanceMeters
                // Süreç yeniden başlatıldıysa son noktadan devam et (mesafe sıçramasını önlemek için zaman kontrolü)
                lastAcceptedLocation = runCatching { locationPointDao.getLastForShift(shift.id) }
                    .getOrNull()
                    ?.takeIf { System.currentTimeMillis() - it.timestamp < RESUME_POINT_MAX_AGE_MS }
                    ?.let { entity ->
                        Location(RESTORED_PROVIDER).apply {
                            latitude = entity.latitude
                            longitude = entity.longitude
                            time = entity.timestamp
                            accuracy = entity.accuracy
                        }
                    }
            }

            mutableTotalDistance.value = totalDistanceMeters
            requestLocationUpdates(MovementMode.ACTIVE)
            mutableServiceState.value = ServiceState.RUNNING
            refreshNotification(force = true)
        }
    }

    private fun pauseTracking() {
        if (serviceStateFlow.value != ServiceState.RUNNING) return
        fusedLocationClient.removeLocationUpdates(locationCallback)
        currentMode = null
        mutableMovementMode.value = null
        // Duraklatma süresince kat edilen yol sayılmasın
        lastAcceptedLocation = null
        stationarySince = null
        persistDistance(force = true)
        mutableServiceState.value = ServiceState.PAUSED
        refreshNotification(force = true)
    }

    private fun resumeTracking() {
        when (serviceStateFlow.value) {
            ServiceState.PAUSED -> {
                if (currentShiftId == null) {
                    startTracking(NO_SHIFT_ID)
                    return
                }
                requestLocationUpdates(MovementMode.ACTIVE)
                mutableServiceState.value = ServiceState.RUNNING
                refreshNotification(force = true)
            }
            ServiceState.STOPPED -> startTracking(NO_SHIFT_ID)
            ServiceState.RUNNING -> Unit
        }
    }

    private fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        persistDistance(force = true)
        resetTrackingState()
        mutableServiceState.value = ServiceState.STOPPED
        mutableCurrentLocation.value = null
        mutableTotalDistance.value = 0f
        shutdown()
    }

    private fun shutdown() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun resetTrackingState() {
        currentShiftId = null
        lastAcceptedLocation = null
        totalDistanceMeters = 0f
        currentMode = null
        stationarySince = null
        breakNotified = false
        lastDistancePersistAt = 0L
        mutableMovementMode.value = null
    }

    // endregion

    // region Location handling

    @SuppressLint("MissingPermission") // izin hasLocationPermission() ile kontrol ediliyor
    private fun requestLocationUpdates(mode: MovementMode) {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Konum izni geri alındı, takip durduruluyor")
            stopTracking()
            return
        }
        // Aynı callback ile yapılan yeni istek öncekinin yerine geçer
        fusedLocationClient.requestLocationUpdates(
            requestFor(mode),
            locationCallback,
            Looper.getMainLooper()
        ).addOnFailureListener { Log.e(TAG, "Konum güncellemesi istenemedi ($mode)", it) }
        currentMode = mode
        mutableMovementMode.value = mode
        Log.d(TAG, "Konum rejimi: $mode")
    }

    private fun handleLocation(location: Location) {
        val shiftId = currentShiftId ?: return
        if (serviceStateFlow.value != ServiceState.RUNNING) return

        // 1) Düşük doğruluklu noktaları ele
        if (location.hasAccuracy() && location.accuracy > Constants.MAX_ACCEPTED_ACCURACY_METERS) return

        val previous = lastAcceptedLocation
        var delta = 0f
        var impliedSpeed = 0f
        if (previous != null) {
            val results = FloatArray(1)
            Location.distanceBetween(
                previous.latitude, previous.longitude,
                location.latitude, location.longitude,
                results
            )
            delta = results[0]
            val dtSeconds = (location.time - previous.time) / 1000f
            if (dtSeconds <= 0f) return // sıra dışı / tekrar eden nokta
            impliedSpeed = delta / dtSeconds
            // 2) Gerçekçi olmayan sıçramaları (GPS hatası) ele
            if (impliedSpeed > Constants.MAX_REALISTIC_SPEED_MPS) return
        }

        val effectiveSpeed = if (location.hasSpeed()) location.speed else impliedSpeed

        // 3) Mesafe hesabı — dururken GPS titreşimini sayma
        if (previous == null) {
            lastAcceptedLocation = location
        } else {
            val accuracy = if (location.hasAccuracy()) location.accuracy else 0f
            val isRealMovement = delta >= Constants.MIN_DISTANCE_DELTA_METERS &&
                (effectiveSpeed >= Constants.STATIONARY_SPEED_THRESHOLD_MPS || delta > accuracy)
            if (isRealMovement) {
                totalDistanceMeters += delta
                lastAcceptedLocation = location
            }
        }
        mutableTotalDistance.value = totalDistanceMeters

        // 4) Noktayı yayınla ve DB'ye kaydet
        val point = location.toLocationPoint(shiftId)
        mutableCurrentLocation.value = point
        serviceScope.launch(ioDispatcher) {
            runCatching { locationPointDao.insert(point.toEntity()) }
                .onFailure { Log.e(TAG, "Konum noktası kaydedilemedi", it) }
        }

        // 5) Hareket rejimini değerlendir, mesafeyi periyodik yaz, bildirimi güncelle
        evaluateMovement(effectiveSpeed, location.time)
        persistDistance(force = false)
        refreshNotification(force = false)
    }

    private fun evaluateMovement(speedMps: Float, timestamp: Long) {
        val newMode = when {
            speedMps > Constants.ACTIVE_SPEED_THRESHOLD_MPS -> {
                stationarySince = null
                breakNotified = false
                MovementMode.ACTIVE
            }
            speedMps < Constants.STATIONARY_SPEED_THRESHOLD_MPS -> {
                val since = stationarySince ?: timestamp.also { stationarySince = it }
                if (timestamp - since >= Constants.STATIONARY_BREAK_THRESHOLD_MS) {
                    if (!breakNotified) {
                        sendBreakSuggestion()
                        breakNotified = true
                    }
                    MovementMode.IDLE
                } else {
                    MovementMode.SLOW
                }
            }
            else -> {
                stationarySince = null
                breakNotified = false
                MovementMode.SLOW
            }
        }
        if (newMode != currentMode) requestLocationUpdates(newMode)
    }

    private fun persistDistance(force: Boolean) {
        val shiftId = currentShiftId ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastDistancePersistAt < Constants.DISTANCE_PERSIST_INTERVAL_MS) return
        lastDistancePersistAt = now
        val distance = totalDistanceMeters
        // Servis kapanırken iptal edilmemesi için uygulama scope'unda çalıştır
        applicationScope.launch(ioDispatcher) {
            runCatching { shiftRepository.updateShiftDistance(shiftId, distance) }
                .onFailure { Log.e(TAG, "Vardiya mesafesi kaydedilemedi", it) }
        }
    }

    // endregion

    // region Notifications

    /** Foreground'a geçer; Android 14'te izin/arka plan kısıtlarında false döner. */
    private fun startForegroundSafely(): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, Constants.TRACKING_NOTIFICATION_ID, buildTrackingNotification(), type)
        true
    } catch (e: SecurityException) {
        Log.e(TAG, "Foreground servis başlatılamadı (izin)", e)
        false
    } catch (e: IllegalStateException) {
        // Android 12+: ForegroundServiceStartNotAllowedException (arka plandan başlatma)
        Log.e(TAG, "Foreground servis başlatılamadı (arka plan kısıtı)", e)
        false
    }

    private fun refreshNotification(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotificationUpdateAt < NOTIFICATION_UPDATE_INTERVAL_MS) return
        lastNotificationUpdateAt = now
        if (!hasNotificationPermission()) return // Android 13+: izin yoksa güncelleme sessizce atlanır
        notificationManager().notify(Constants.TRACKING_NOTIFICATION_ID, buildTrackingNotification())
    }

    private fun buildTrackingNotification(): Notification {
        val isPaused = serviceStateFlow.value == ServiceState.PAUSED
        val title = getString(if (isPaused) R.string.notification_paused_title else R.string.notification_tracking_title)
        val text = getString(R.string.notification_tracking_text, totalDistanceMeters.toDistanceText())

        val toggleAction = if (isPaused) {
            NotificationCompat.Action(0, getString(R.string.action_resume), servicePendingIntent(ACTION_RESUME, REQ_RESUME))
        } else {
            NotificationCompat.Action(0, getString(R.string.action_pause), servicePendingIntent(ACTION_PAUSE, REQ_PAUSE))
        }

        return NotificationCompat.Builder(this, Constants.TRACKING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(activityPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(toggleAction)
            // "Bitir" uygulamayı açar: vardiya kapanışı (EndShiftUseCase) ekrandan onaylanır
            .addAction(0, getString(R.string.action_stop), activityPendingIntent())
            .build()
    }

    private fun sendBreakSuggestion() {
        if (!hasNotificationPermission()) return
        val notification = NotificationCompat.Builder(this, Constants.BREAK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_break_title))
            .setContentText(getString(R.string.notification_break_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_break_text)))
            .setContentIntent(activityPendingIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager().notify(Constants.BREAK_NOTIFICATION_ID, notification)
    }

    private fun activityPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQ_OPEN_APP,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, LocationTrackingService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // endregion

    companion object {
        private const val TAG = "LocationTrackingService"

        const val ACTION_START = "com.tagtakip.pro.action.START_TRACKING"
        const val ACTION_PAUSE = "com.tagtakip.pro.action.PAUSE_TRACKING"
        const val ACTION_RESUME = "com.tagtakip.pro.action.RESUME_TRACKING"
        const val ACTION_STOP = "com.tagtakip.pro.action.STOP_TRACKING"
        const val EXTRA_SHIFT_ID = "extra_shift_id"
        const val NO_SHIFT_ID = -1L

        private const val RESTORED_PROVIDER = "restored"
        private const val RESUME_POINT_MAX_AGE_MS = 5 * 60 * 1000L
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 15_000L
        private const val REQ_OPEN_APP = 100
        private const val REQ_PAUSE = 101
        private const val REQ_RESUME = 102

        /** Aktif hareket: yüksek doğruluk, 5 sn. */
        val ACTIVE_REQUEST: LocationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, Constants.ACTIVE_INTERVAL_MS)
                .setMinUpdateIntervalMillis(Constants.ACTIVE_FASTEST_INTERVAL_MS)
                .setWaitForAccurateLocation(false)
                .build()

        /** Yavaş hareket / kısa duruş: dengeli güç, 15 sn. */
        val SLOW_REQUEST: LocationRequest =
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, Constants.SLOW_INTERVAL_MS)
                .setMinUpdateIntervalMillis(Constants.SLOW_FASTEST_INTERVAL_MS)
                .build()

        /** Uzun duruş (mola): dengeli güç, 60 sn — pil tasarrufu. */
        val IDLE_REQUEST: LocationRequest =
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, Constants.IDLE_INTERVAL_MS)
                .setMinUpdateIntervalMillis(Constants.IDLE_FASTEST_INTERVAL_MS)
                .build()

        fun requestFor(mode: MovementMode): LocationRequest = when (mode) {
            MovementMode.ACTIVE -> ACTIVE_REQUEST
            MovementMode.SLOW -> SLOW_REQUEST
            MovementMode.IDLE -> IDLE_REQUEST
        }

        // Süreç genelinde paylaşılan durum — bind etmeden de collect edilebilir
        private val mutableServiceState = MutableStateFlow(ServiceState.STOPPED)
        private val mutableCurrentLocation = MutableStateFlow<LocationPoint?>(null)
        private val mutableTotalDistance = MutableStateFlow(0f)
        private val mutableMovementMode = MutableStateFlow<MovementMode?>(null)

        val serviceStateFlow: StateFlow<ServiceState> = mutableServiceState.asStateFlow()
        val currentLocationFlow: StateFlow<LocationPoint?> = mutableCurrentLocation.asStateFlow()
        val totalDistanceFlow: StateFlow<Float> = mutableTotalDistance.asStateFlow()
        val movementModeFlow: StateFlow<MovementMode?> = mutableMovementMode.asStateFlow()

        /** Bildirim kanallarını oluşturur (Application.onCreate ve servis onCreate'te çağrılır). */
        fun createNotificationChannels(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val tracking = NotificationChannel(
                Constants.TRACKING_CHANNEL_ID,
                context.getString(R.string.channel_tracking_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_tracking_desc)
                setShowBadge(false)
            }
            val breakChannel = NotificationChannel(
                Constants.BREAK_CHANNEL_ID,
                context.getString(R.string.channel_break_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.channel_break_desc) }
            manager.createNotificationChannels(listOf(tracking, breakChannel))
        }
    }
}
