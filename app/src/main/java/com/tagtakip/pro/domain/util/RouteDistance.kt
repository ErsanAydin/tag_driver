package com.tagtakip.pro.domain.util

import com.tagtakip.pro.domain.model.LocationPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Android bağımlılığı olmayan rota mesafesi hesaplayıcı (Haversine). */
object RouteDistance {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun between(a: LocationPoint, b: LocationPoint): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Zaman sırasına göre ardışık noktalar arasındaki toplam mesafe (metre). */
    fun total(points: List<LocationPoint>): Float =
        points.sortedBy { it.timestamp }.zipWithNext { a, b -> between(a, b) }.sum().toFloat()
}
