package com.tagtakip.pro.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.tagtakip.pro.R
import kotlinx.serialization.Serializable

/** Type-safe navigation route'ları (Navigation Compose 2.8+ / kotlinx.serialization). */
@Serializable
sealed class Screen {
    @Serializable data object Dashboard : Screen()
    @Serializable data object Shift : Screen()
    @Serializable data object Distance : Screen()
}

/** Alt navigasyon çubuğu öğeleri. */
enum class BottomNavItem(
    val route: Screen,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    DASHBOARD(Screen.Dashboard, R.string.nav_dashboard, Icons.Filled.Dashboard),
    SHIFT(Screen.Shift, R.string.nav_shift, Icons.Filled.Timer),
    DISTANCE(Screen.Distance, R.string.nav_distance, Icons.Filled.Route)
}
