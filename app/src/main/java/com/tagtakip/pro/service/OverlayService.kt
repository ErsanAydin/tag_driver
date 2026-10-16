package com.tagtakip.pro.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.tagtakip.pro.MainActivity
import com.tagtakip.pro.R
import com.tagtakip.pro.domain.repository.ShiftRepository
import com.tagtakip.pro.domain.repository.TripRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

/** Overlay tuşunun durumu (vardiya/yolculuk DB'den türetilir). */
enum class OverlayTripState { IDLE, SHIFT_ACTIVE, TRIP_ACTIVE }

/**
 * Uygulama dışında da görünen yüzen yolculuk tuşu (TYPE_APPLICATION_OVERLAY).
 * - SHIFT_ACTIVE: kırmızı → yolculuk başlat
 * - TRIP_ACTIVE: mavi → yolculuğu bitir (uygulama öne getirilir, özet diyaloğu açılır)
 * - IDLE: servis kendini durdurur
 * Uygulama ön plandayken tuş gizlenir (in-app FAB zaten görünür). Sürüklenebilir.
 *
 * Not: Vardiya boyunca LocationTrackingService foreground çalıştığı için süreç ön plan önceliğinde kalır;
 * bu servis arka plan servis sınırlamalarına takılmaz.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@AndroidEntryPoint
class OverlayService : Service() {

    @Inject lateinit var shiftRepository: ShiftRepository
    @Inject lateinit var tripRepository: TripRepository
    @Inject lateinit var locationServiceManager: LocationServiceManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var button: ImageView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var currentState = OverlayTripState.IDLE
    private var screenshotReceiverRegistered = false

    /** MainActivity ekran görüntüsünü kaydedince yolunu bildirir (ViewModel'e MainActivity aktarır). */
    private val screenshotReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val path = intent.getStringExtra(OverlayActions.EXTRA_SCREENSHOT_PATH)
            Log.d(TAG, "Yolculuk başlangıç ekran görüntüsü: $path")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay izni yok, servis durduruluyor")
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
        if (windowManager == null) {
            Log.e(TAG, "WindowManager alınamadı")
            stopSelf()
            return
        }
        ContextCompat.registerReceiver(
            this,
            screenshotReceiver,
            IntentFilter(OverlayActions.ACTION_SCREENSHOT_TAKEN),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenshotReceiverRegistered = true
        observeState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun observeState() {
        val tripState = shiftRepository.observeActiveShift().flatMapLatest { shift ->
            if (shift == null) flowOf(OverlayTripState.IDLE)
            else tripRepository.observeActiveTrip(shift.id).map { trip ->
                if (trip == null) OverlayTripState.SHIFT_ACTIVE else OverlayTripState.TRIP_ACTIVE
            }
        }
        scope.launch {
            combine(
                tripState,
                OverlayActions.isAppInForeground,
                locationServiceManager.serviceState
            ) { state, inForeground, _ -> state to inForeground }
                .distinctUntilChanged()
                .collect { (state, inForeground) ->
                    currentState = state
                    when {
                        state == OverlayTripState.IDLE -> {
                            removeButton()
                            stopSelf()
                        }
                        inForeground -> removeButton()
                        else -> showButton(state)
                    }
                }
        }
    }

    private fun showButton(state: OverlayTripState) {
        val wm = windowManager ?: return
        val view = button ?: createButton().also { button = it }
        val isTrip = state == OverlayTripState.TRIP_ACTIVE
        (view.background as? GradientDrawable)?.setColor(
            ContextCompat.getColor(this, if (isTrip) R.color.overlay_trip_active else R.color.overlay_trip_idle)
        )
        view.setImageResource(if (isTrip) R.drawable.ic_overlay_stop else R.drawable.ic_overlay_car)
        view.contentDescription = getString(if (isTrip) R.string.overlay_trip_end_desc else R.string.overlay_trip_start_desc)
        if (view.parent == null) {
            try {
                wm.addView(view, layoutParams ?: createLayoutParams().also { layoutParams = it })
            } catch (e: Exception) {
                // İzin geri alınmış olabilir (BadTokenException / SecurityException)
                Log.e(TAG, "Overlay eklenemedi", e)
                stopSelf()
            }
        }
    }

    private fun removeButton() {
        val view = button ?: return
        if (view.parent != null) {
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Overlay kaldırılamadı", e)
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val metrics = resources.displayMetrics
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Sağ-orta başlangıç konumu
            x = metrics.widthPixels - dp(BUTTON_SIZE_DP) - dp(8)
            y = metrics.heightPixels / 2 - dp(BUTTON_SIZE_DP) / 2
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createButton(): ImageView = ImageView(this).apply {
        val size = dp(BUTTON_SIZE_DP)
        minimumWidth = size
        minimumHeight = size
        val padding = dp(14)
        setPadding(padding, padding, padding, padding)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
        elevation = dp(6).toFloat()

        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        setOnTouchListener { v, event ->
            val params = this@OverlayService.layoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = params.x; startY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
                    if (dragging) {
                        params.x = startX + dx.roundToInt()
                        params.y = startY + dy.roundToInt()
                        try { windowManager?.updateViewLayout(v, params) } catch (e: Exception) { Log.e(TAG, "Konum güncellenemedi", e) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        v.performClick()
                        onButtonClicked()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /** Explicit broadcast gönderir; activity yaşamıyorsa / yolculuk bitiyorsa uygulamayı öne getirir. */
    private fun onButtonClicked() {
        val startTrip = when (currentState) {
            OverlayTripState.SHIFT_ACTIVE -> true
            OverlayTripState.TRIP_ACTIVE -> false
            OverlayTripState.IDLE -> return
        }
        if (OverlayActions.isReceiverRegistered) {
            // Yolculuk başlarken önce ekran görüntüsü iste
            if (startTrip) {
                sendBroadcast(Intent(OverlayActions.ACTION_TAKE_SCREENSHOT).setPackage(packageName))
            }
            sendBroadcast(
                Intent(OverlayActions.ACTION_OVERLAY_TRIP_TOGGLE)
                    .setPackage(packageName)
                    .putExtra(OverlayActions.EXTRA_START_TRIP, startTrip)
            )
            // Bitirmede özet diyaloğu için uygulamayı öne getir (komut broadcast ile iletildi)
            if (!startTrip) launchApp(withAction = false, startTrip = false)
        } else {
            // Activity yok: komutu intent ile ilet
            launchApp(withAction = true, startTrip = startTrip)
        }
    }

    private fun launchApp(withAction: Boolean, startTrip: Boolean) {
        val intent = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        if (withAction) {
            intent.action = OverlayActions.ACTION_OVERLAY_TRIP_TOGGLE
            intent.putExtra(OverlayActions.EXTRA_START_TRIP, startTrip)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Uygulama açılamadı", e)
        }
    }

    override fun onDestroy() {
        if (screenshotReceiverRegistered) {
            unregisterReceiver(screenshotReceiver)
            screenshotReceiverRegistered = false
        }
        removeButton()
        button = null
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "OverlayService"
        const val BUTTON_SIZE_DP = 56
    }
}
