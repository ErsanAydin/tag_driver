package com.tagtakip.pro.domain.usecase.earnings

import kotlin.math.abs

/** Gelir/gider tutarı için ortak doğrulama kuralları. */
internal object EarningValidator {
    const val MAX_SINGLE_AMOUNT_TL = 100_000.0

    /** Geçersizse hata mesajını, geçerliyse null döner. İşaret (gelir/gider) burada önemsizdir. */
    fun validate(amount: Double): String? = when {
        amount.isNaN() || amount.isInfinite() || abs(amount) == 0.0 -> "Tutar sıfırdan büyük olmalıdır"
        abs(amount) > MAX_SINGLE_AMOUNT_TL -> "Tutar çok yüksek görünüyor"
        else -> null
    }
}
