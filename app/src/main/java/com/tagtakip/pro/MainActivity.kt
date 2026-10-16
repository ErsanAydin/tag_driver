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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.tagtakip.pro.presentation.navigation.TagTakipNavGraph
import com.tagtakip.pro.presentation.shift.ShiftViewModel
import com.tagtakip.pro.presentation.theme.TagTakipTheme
import com.tagtakip.pro.service.OverlayActions
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** NavGraph'taki hiltViewModel() ile aynı activity kapsamlı örnek (aynı varsayılan anahtar). */
    private val shiftViewModel: ShiftViewModel by viewModels()

    /** OverlayService'in yüzen tuşundan gelen explicit broadcast'ler. */
    private val overlayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = handleOverlayIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ContextCompat.registerReceiver(
            this,
            overlayReceiver,
            IntentFilter(OverlayActions.ACTION_OVERLAY_TRIP_TOGGLE),
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
}
