package com.tagtakip.pro.domain.model

/** Vardiya içindeki tek bir yolculuk / teslimat. */
data class Trip(
    val id: Long = 0,
    val shiftId: Long,
    val startTime: Long,
    val endTime: Long? = null,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val endLatitude: Double? = null,
    val endLongitude: Double? = null,
    val distanceMeters: Float = 0f,
    val fare: Double = 0.0,
    val platform: String? = null,
    val earningAmount: Double? = null,
    val description: String? = null
) {
    val isActive: Boolean get() = endTime == null

    fun durationMillis(now: Long = System.currentTimeMillis()): Long =
        ((endTime ?: now) - startTime).coerceAtLeast(0)
}
