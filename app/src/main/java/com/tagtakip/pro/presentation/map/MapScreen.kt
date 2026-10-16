package com.tagtakip.pro.presentation.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.tagtakip.pro.R
import com.tagtakip.pro.core.util.hasLocationPermission
import com.tagtakip.pro.core.util.toDistanceText
import com.tagtakip.pro.presentation.theme.RoutePolyline
import java.util.Locale

private val DEFAULT_LOCATION = LatLng(41.0082, 28.9784) // İstanbul
private const val FOLLOW_ZOOM = 16f

@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasPermission = remember { context.hasLocationPermission() }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(state.currentLatLng ?: DEFAULT_LOCATION, 12f)
    }
    val markerState = rememberMarkerState(position = state.currentLatLng ?: DEFAULT_LOCATION)

    // Kullanıcı haritayı elle kaydırırsa takip modunu kapat
    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving &&
            cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE
        ) {
            viewModel.setFollowUser(false)
        }
    }

    LaunchedEffect(state.currentLatLng, state.followUser) {
        val latLng = state.currentLatLng ?: return@LaunchedEffect
        markerState.position = latLng
        if (state.followUser) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, FOLLOW_ZOOM), 800)
        }
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasPermission),
            uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false)
        ) {
            if (state.routePoints.size >= 2) {
                Polyline(points = state.routePoints, color = RoutePolyline, width = 12f)
            }
            if (state.currentLatLng != null) {
                Marker(state = markerState, title = stringResource(R.string.app_name))
            }
        }

        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Mesafe", style = MaterialTheme.typography.labelMedium)
                    Text(state.totalDistanceMeters.toDistanceText(), style = MaterialTheme.typography.titleLarge)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Hız", style = MaterialTheme.typography.labelMedium)
                    Text(
                        state.currentLocation?.let { String.format(Locale.getDefault(), "%.0f km/sa", it.speedKmh) }
                            ?: stringResource(R.string.map_no_location),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { viewModel.setFollowUser(true) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Filled.MyLocation, contentDescription = "Konumuma git") }
    }
}
