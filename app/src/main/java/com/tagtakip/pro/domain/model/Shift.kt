package com.tagtakip.pro.domain.model

/** Sürücünün bir çalışma vardiyası (mesai). */
data class Shift(
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val totalDistanceMeters: Float = 0f,
    val note: String? = null
) {
    val isActive: Boolean get() = endTime == null

    /** Aktif vardiyada [now]'a kadar geçen süre, bitmişse toplam süre. */
    fun durationMillis(now: Long = System.currentTimeMillis()): Long =
        ((endTime ?: now) - startTime).coerceAtLeast(0)
}
