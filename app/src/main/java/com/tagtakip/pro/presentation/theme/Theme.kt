package com.tagtakip.pro.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = AmberPrimary,
    onPrimary = OnAmber,
    primaryContainer = AmberPrimaryContainer,
    onPrimaryContainer = OnAmberContainer,
    secondary = TealSecondary,
    onSecondary = OnAmber,
    secondaryContainer = TealSecondaryContainer,
    onSecondaryContainer = OnTealSecondaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = ErrorRed
)

private val LightColors = lightColorScheme(
    primary = AmberPrimaryLight,
    onPrimary = LightSurface,
    secondary = TealSecondaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = ErrorRed
)

/**
 * Uygulama teması. Varsayılan olarak koyu tema (sürücüler için gece kullanımı öncelikli);
 * [followSystem] true ise sistem ayarı izlenir.
 */
@Composable
fun TagTakipTheme(
    followSystem: Boolean = false,
    darkTheme: Boolean = if (followSystem) isSystemInDarkTheme() else true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = TagTakipTypography,
        content = content
    )
}
