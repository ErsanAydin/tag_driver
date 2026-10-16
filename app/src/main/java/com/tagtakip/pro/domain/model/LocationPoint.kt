package com.tagtakip.pro.domain.model

/** GPS'ten alınmış tek bir konum noktası. */
data class LocationPoint(
    val id: Long = 0,
    val shiftId: Long,
    val latitude: Double,
    val longitude: Double,
    val speed: Float = 0f,        // m/s
    val accuracy: Float = 0f,     // metre
    val bearing: Float = 0f,      // derece
    val altitude: Double = 0.0,   // metre
    val timestamp: Long
) {
    val speedKmh: Float get() = speed * 3.6f
}
