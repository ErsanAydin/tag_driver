package com.tagtakip.pro.domain.model

import kotlin.math.abs

/** Bir gelir veya gider kaydı (TL cinsinden). */
data class Earning(
    val id: Long = 0,
    val shiftId: Long? = null,
    val tripId: Long? = null,
    val amount: Double,               // negatif = gider, pozitif = gelir
    val type: EarningType,            // INCOME veya EXPENSE
    val description: String? = null,  // opsiyonel açıklama
    val timestamp: Long = System.currentTimeMillis()
) {
    /** Tutarın işaretini türe göre düzeltir (gelir pozitif, gider negatif). */
    fun normalized(): Earning = copy(
        amount = if (type == EarningType.EXPENSE) -abs(amount) else abs(amount),
        description = description?.trim()?.takeIf { it.isNotEmpty() }
    )
}

enum class EarningType {
    INCOME,   // Gelir (pozitif)
    EXPENSE;  // Gider (negatif)

    companion object {
        /** DB'deki metin değerini çözer; bilinmeyen değerde tutarın işaretine bakar. */
        fun fromStorage(value: String?, amount: Double): EarningType =
            entries.firstOrNull { it.name == value } ?: if (amount < 0) EXPENSE else INCOME
    }
}
