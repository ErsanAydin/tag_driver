package com.tagtakip.pro.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Tarih/saat yardımcıları (java.time — minSdk 26 ile desugaring gerekmez). */
object DateTimeUtils {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", TURKISH_LOCALE)
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", TURKISH_LOCALE)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm", TURKISH_LOCALE)

    fun startOfDay(date: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Günün son milisaniyesi (dahil). */
    fun endOfDay(date: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Long =
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    fun startOfWeek(date: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Long =
        startOfDay(date.minusDays((date.dayOfWeek.value - 1).toLong()), zone)

    fun formatTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        timeFormatter.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun formatDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateFormatter.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun formatDateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTimeFormatter.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** 12_300_000 -> "3 sa 25 dk" */
    fun formatDuration(millis: Long): String {
        val safe = millis.coerceAtLeast(0)
        val hours = TimeUnit.MILLISECONDS.toHours(safe)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(safe) % 60
        return if (hours > 0) "$hours sa $minutes dk" else "$minutes dk"
    }

    fun millisToHours(millis: Long): Double = millis / 3_600_000.0
}
