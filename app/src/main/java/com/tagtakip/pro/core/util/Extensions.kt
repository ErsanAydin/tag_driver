package com.tagtakip.pro.core.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.tagtakip.pro.core.common.Constants
import com.tagtakip.pro.domain.model.LocationPoint
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

val TURKISH_LOCALE: Locale = Locale("tr", "TR")

/** 1234.5 -> "₺1.234,50" */
fun Double.toTlCurrency(): String =
    NumberFormat.getCurrencyInstance(TURKISH_LOCALE).apply {
        currency = Currency.getInstance(Constants.CURRENCY_CODE)
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }.format(this)

fun Float.metersToKm(): Double = this / 1000.0

/** 12345f -> "12,3 km", 850f -> "850 m" */
fun Float.toDistanceText(): String =
    if (this < 1000f) "${this.toInt()} m"
    else String.format(TURKISH_LOCALE, "%.1f km", metersToKm())

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.hasLocationPermission(): Boolean =
    hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
        hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

fun Context.hasBackgroundLocationPermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        hasLocationPermission()
    }

/** Android 13+ için POST_NOTIFICATIONS kontrolü; daha eski sürümlerde her zaman true. */
fun Context.hasNotificationPermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        true
    }

/** Mesai için istenmesi gereken çalışma zamanı izinleri (arka plan konumu ayrı istenir). */
fun requiredForegroundPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

fun Location.toLocationPoint(shiftId: Long): LocationPoint = LocationPoint(
    shiftId = shiftId,
    latitude = latitude,
    longitude = longitude,
    speed = if (hasSpeed()) speed else 0f,
    accuracy = if (hasAccuracy()) accuracy else 0f,
    bearing = if (hasBearing()) bearing else 0f,
    altitude = if (hasAltitude()) altitude else 0.0,
    timestamp = time
)
