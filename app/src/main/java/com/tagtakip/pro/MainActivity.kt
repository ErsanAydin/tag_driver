package com.tagtakip.pro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.tagtakip.pro.presentation.navigation.TagTakipNavGraph
import com.tagtakip.pro.presentation.shift.ShiftViewModel
import com.tagtakip.pro.presentation.theme.TagTakipTheme
import com.tagtakip.pro.service.OverlayActions
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** NavGraph'taki hiltViewModel() ile aynı activity kapsamlı örnek (aynı varsayılan anahtar). */
    private val shiftViewModel: ShiftViewModel by viewModels()

    /** OverlayService'in yüzen tuşundan gelen explicit broadcast'ler. */
    private val overlayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                OverlayActions.ACTION_TAKE_SCREENSHOT -> takeScreenshotAndBroadcast()
                else -> handleOverlayIntent(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ContextCompat.registerReceiver(
            this,
            overlayReceiver,
            IntentFilter().apply {
                addAction(OverlayActions.ACTION_OVERLAY_TRIP_TOGGLE)
                addAction(OverlayActions.ACTION_TAKE_SCREENSHOT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        OverlayActions.isReceiverRegistered = true
        if (savedInstanceState == null) handleOverlayIntent(intent)
        setContent {
            TagTakipTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TagTakipNavGraph()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOverlayIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        OverlayActions.setAppInForeground(true)
    }

    override fun onStop() {
        OverlayActions.setAppInForeground(false)
        super.onStop()
    }

    override fun onDestroy() {
        OverlayActions.isReceiverRegistered = false
        unregisterReceiver(overlayReceiver)
        super.onDestroy()
    }

    /** Başlat → onStartTrip, Bitir → özet diyaloğunu açan onEndTripRequested (ViewModel üzerinden). */
    private fun handleOverlayIntent(intent: Intent?) {
        if (intent?.action != OverlayActions.ACTION_OVERLAY_TRIP_TOGGLE) return
        val startTrip = intent.getBooleanExtra(OverlayActions.EXTRA_START_TRIP, true)
        // Aynı intent'in (ör. yeniden oluşturmada) ikinci kez işlenmesini önle
        if (intent === this.intent) this.intent = Intent(intent).setAction(null)
        shiftViewModel.onOverlayTripAction(startTrip)
    }

    /**
     * Uygulama penceresinin görüntüsünü PixelCopy ile alır, filesDir/screenshots altına JPEG olarak kaydeder,
     * yolu ViewModel'e iletir ve ACTION_SCREENSHOT_TAKEN yayınlar.
     * Not: PixelCopy yalnızca bu uygulamanın penceresini yakalayabilir; başka uygulamaların ekranını yakalamaz.
     */
    private fun takeScreenshotAndBroadcast() {
        val decor = window?.decorView ?: return
        val width = decor.width
        val height = decor.height
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "Pencere boyutu yok, ekran görüntüsü atlandı")
            return
        }
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (e: Throwable) {
            Log.e(TAG, "Bitmap oluşturulamadı", e)
            return
        }
        val mainHandler = Handler(Looper.getMainLooper())
        try {
            PixelCopy.request(window, bitmap, { copyResult ->
                if (copyResult != PixelCopy.SUCCESS) {
                    Log.w(TAG, "PixelCopy başarısız: $copyResult")
                    bitmap.recycle()
                    return@request
                }
                // Dosya yazımı ana thread dışında
                val worker = HandlerThread("screenshot-writer").apply { start() }
                Handler(worker.looper).post {
                    val path = saveScreenshot(bitmap)
                    bitmap.recycle()
                    worker.quitSafely()
                    if (path != null) mainHandler.post { onScreenshotSaved(path) }
                }
            }, mainHandler)
        } catch (e: Exception) {
            Log.e(TAG, "PixelCopy isteği başarısız", e)
            bitmap.recycle()
        }
    }

    private fun saveScreenshot(bitmap: Bitmap): String? = try {
        val dir = File(filesDir, "screenshots").apply { mkdirs() }
        val file = File(dir, "trip_start_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out) }
        file.absolutePath
    } catch (e: Exception) {
        Log.e(TAG, "Ekran görüntüsü kaydedilemedi", e)
        null
    }

    private fun onScreenshotSaved(path: String) {
        shiftViewModel.setLastScreenshotPath(path)
        sendBroadcast(
            Intent(OverlayActions.ACTION_SCREENSHOT_TAKEN)
                .setPackage(packageName)
                .putExtra(OverlayActions.EXTRA_SCREENSHOT_PATH, path)
        )
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
