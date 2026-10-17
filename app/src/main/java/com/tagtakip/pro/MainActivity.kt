package com.tagtakip.pro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tagtakip.pro.presentation.backup.DriveRestorePrompt
import com.tagtakip.pro.presentation.navigation.TagTakipNavGraph
import com.tagtakip.pro.presentation.shift.ShiftViewModel
import com.tagtakip.pro.presentation.theme.TagTakipTheme
import com.tagtakip.pro.service.OverlayActions
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** NavGraph'taki hiltViewModel() ile aynı activity kapsamlı örnek (aynı varsayılan anahtar). */
    private val shiftViewModel: ShiftViewModel by viewModels()

    /** OverlayService'in yüzen tuşundan gelen explicit broadcast'ler. */
    private val overlayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = handleOverlayIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12 SplashScreen API (Theme.App.Starting) — super.onCreate'ten önce kurulmalı
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ContextCompat.registerReceiver(
            this,
            overlayReceiver,
            IntentFilter(OverlayActions.ACTION_OVERLAY_TRIP_TOGGLE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        OverlayActions.isReceiverRegistered = true
        // Yüzen tuştan açılışta açılış ekranı gösterilmez (işlem hemen görünmeli)
        val showSplashInitially = savedInstanceState == null &&
            intent?.action != OverlayActions.ACTION_OVERLAY_TRIP_TOGGLE
        if (savedInstanceState == null) {
            handleOverlayIntent(intent)
            // Kaldırılan ekran görüntüsü özelliğinden kalan eski dosyaları temizle
            runCatching { File(filesDir, "screenshots").deleteRecursively() }
        }
        setContent {
            TagTakipTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var showSplash by rememberSaveable { mutableStateOf(showSplashInitially) }
                    LaunchedEffect(Unit) {
                        if (showSplash) {
                            delay(SPLASH_DURATION_MS)
                            showSplash = false
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        TagTakipNavGraph()
                        // Drive'daki yedek bu cihazdan yeniyse geri yükleme önerisi (yedek konumu seçilmişse)
                        if (!showSplash) DriveRestorePrompt()
                        AnimatedVisibility(visible = showSplash, enter = EnterTransition.None, exit = fadeOut()) {
                            AppSplash()
                        }
                    }
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
    private companion object {
        const val SPLASH_DURATION_MS = 1_500L
    }

    private fun handleOverlayIntent(intent: Intent?) {
        if (intent?.action != OverlayActions.ACTION_OVERLAY_TRIP_TOGGLE) return
        val startTrip = intent.getBooleanExtra(OverlayActions.EXTRA_START_TRIP, true)
        // Aynı intent'in (ör. yeniden oluşturmada) ikinci kez işlenmesini önle
        if (intent === this.intent) this.intent = Intent(intent).setAction(null)
        shiftViewModel.onOverlayTripAction(startTrip)
    }
}

/** Uygulama içi açılış ekranı: yazılı TAG SÜRÜCÜ logosu, beyaz zemin üzerinde tam görünür. */
@Composable
private fun AppSplash() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            // Alttaki ekrana dokunuşları engelle
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.splash_full),
            contentDescription = "TAG Sürücü",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().padding(24.dp)
        )
    }
}
