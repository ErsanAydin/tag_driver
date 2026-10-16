package com.tagtakip.pro.domain.model

/** Sürücünün bir çalışma vardiyası (mesai). */
data class Shift(
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val totalDistanceMeters: Float = 0f,
    val note: String? = null,
    /** Devam eden molanın başlangıcı; null ise molada değil. */
    val pauseStartTime: Long? = null,
    /** Tamamlanmış molaların toplam süresi. */
    val totalPausedMillis: Long = 0L
) {
    val isActive: Boolean get() = endTime == null

    val isPaused: Boolean get() = pauseStartTime != null

    /** Toplam mola süresi (devam eden mola dahil). */
    fun pausedMillis(now: Long = System.currentTimeMillis()): Long {
        val ongoing = pauseStartTime?.let { ((endTime ?: now) - it).coerceAtLeast(0L) } ?: 0L
        return totalPausedMillis.coerceAtLeast(0L) + ongoing
    }

    /** Molalar hariç çalışma süresi: aktif vardiyada [now]'a kadar, bitmişse toplam. Mola sırasında donar. */
    fun durationMillis(now: Long = System.currentTimeMillis()): Long =
        ((endTime ?: now) - startTime - pausedMillis(now)).coerceAtLeast(0)
}
