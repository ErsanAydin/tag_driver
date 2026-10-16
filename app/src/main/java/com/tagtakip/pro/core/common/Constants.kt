package com.tagtakip.pro.core.common

/** Uygulama genelindeki sabitler. */
object Constants {
    const val DATABASE_NAME = "tag_takip.db"
    const val DATASTORE_NAME = "tag_takip_prefs"
    const val SECURE_PREFS_NAME = "tag_takip_secure_prefs"

    // Para birimi
    const val CURRENCY_CODE = "TRY"
    const val CURRENCY_SYMBOL = "₺"

    // Bildirimler
    const val TRACKING_CHANNEL_ID = "location_tracking_channel"
    const val BREAK_CHANNEL_ID = "break_reminder_channel"
    const val TRACKING_NOTIFICATION_ID = 1001
    const val BREAK_NOTIFICATION_ID = 1002

    // Konum güncelleme aralıkları (ms)
    const val ACTIVE_INTERVAL_MS = 5_000L
    const val ACTIVE_FASTEST_INTERVAL_MS = 3_000L
    const val SLOW_INTERVAL_MS = 15_000L
    const val SLOW_FASTEST_INTERVAL_MS = 10_000L
    const val IDLE_INTERVAL_MS = 60_000L
    const val IDLE_FASTEST_INTERVAL_MS = 30_000L

    // Hareket eşikleri
    const val ACTIVE_SPEED_THRESHOLD_MPS = 1.5f
    const val STATIONARY_SPEED_THRESHOLD_MPS = 0.5f
    const val STATIONARY_BREAK_THRESHOLD_MS = 10 * 60 * 1000L

    // Konum kalite filtreleri
    const val MAX_ACCEPTED_ACCURACY_METERS = 50f
    const val MIN_DISTANCE_DELTA_METERS = 3f
    const val MAX_REALISTIC_SPEED_MPS = 70f // ~250 km/s üstü sıçramalar GPS hatası kabul edilir

    // Vardiya mesafesinin DB'ye periyodik yazılma aralığı
    const val DISTANCE_PERSIST_INTERVAL_MS = 30_000L

    const val TRIP_HISTORY_DEFAULT_LIMIT = 50
}
