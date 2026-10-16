package com.tagtakip.pro.domain.model

/** Bir kazanç kaydı (TL cinsinden). */
data class Earning(
    val id: Long = 0,
    val shiftId: Long? = null,
    val tripId: Long? = null,
    val amount: Double,
    val type: EarningType = EarningType.TRIP_FARE,
    val platform: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null
)

enum class EarningType(val displayName: String) {
    TRIP_FARE("Yolculuk Ücreti"),
    TIP("Bahşiş"),
    BONUS("Bonus / Prim"),
    OTHER("Diğer")
}
