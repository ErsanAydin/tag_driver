package com.tagtakip.pro.presentation.permission

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Uygulama için zorunlu izinleri ilk açılışta kontrol eder.
 * GPS, bildirim (Android 13+) ve üste gösterme izinleri verilmeden [content] gösterilmez.
 */
@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    fun hasLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasNotification(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        else true

    fun hasOverlay(): Boolean = Settings.canDrawOverlays(context)

    var locationGranted by remember { mutableStateOf(hasLocation()) }
    var notificationGranted by remember { mutableStateOf(hasNotification()) }
    var overlayGranted by remember { mutableStateOf(hasOverlay()) }

    // Kullanıcı sistem ayarlarından döndüğünde izinleri yeniden kontrol et
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                locationGranted = hasLocation()
                notificationGranted = hasNotification()
                overlayGranted = hasOverlay()
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    // Tüm izinler tamam → içeriği göster
    if (locationGranted && notificationGranted && overlayGranted) {
        content()
        return
    }

    // Çalışma zamanı izin başlatıcı (konum + bildirim)
    val runtimePerms = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            results[Manifest.permission.POST_NOTIFICATIONS] == true
        else true
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Uygulama İzinleri",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "TAG Takip'in düzgün çalışması için aşağıdaki izinler gereklidir.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))

            // Konum izni
            PermissionItem(
                icon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                title = "Konum (GPS)",
                description = "Mesai sırasında gidilen km'yi ölçmek için gereklidir.",
                granted = locationGranted,
                actionLabel = "İzin Ver",
                onRequest = { runtimeLauncher.launch(runtimePerms) }
            )

            // Bildirim izni (yalnızca Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionItem(
                    icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    title = "Bildirimler",
                    description = "Arka planda çalışan mesai servisi için bildirim izni gereklidir.",
                    granted = notificationGranted,
                    actionLabel = "İzin Ver",
                    onRequest = { runtimeLauncher.launch(runtimePerms) }
                )
            }

            // Üste gösterme izni (özel — sistem ayarlarına yönlendir)
            PermissionItem(
                icon = { Icon(Icons.Filled.Layers, contentDescription = null) },
                title = "Diğer Uygulamaların Üzerinde Göster",
                description = "Sürüş sırasında diğer uygulamaların üzerinde yüzen tuşu göstermek için gereklidir.",
                granted = overlayGranted,
                actionLabel = "Ayarlara Git",
                isSpecialPermission = true,
                onRequest = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            )

            // Hepsi verilmemişse toplu buton
            val anyMissing = !locationGranted ||
                (!notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            if (anyMissing) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { runtimeLauncher.launch(runtimePerms) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tüm İzinleri Ver")
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onRequest: () -> Unit,
    isSpecialPermission: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            icon()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (granted) {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!granted) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onRequest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSpecialPermission) {
                            Icon(
                                Icons.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}
