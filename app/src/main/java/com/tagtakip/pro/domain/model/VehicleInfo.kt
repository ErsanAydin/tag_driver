package com.tagtakip.pro.domain.model

/** Tek kayıtlık araç bilgisi (id her zaman 1). Yakıt maliyeti hesabında kullanılır. */
data class VehicleInfo(
    val id: Int = 1,
    val marka: String = "",
    val model: String = "",
    val yakitTuru: YakitTuru = YakitTuru.BENZIN,
    val lt100km: Double = 0.0,        // 100 km'de kaç litre
    val litreBasiFiyat: Double = 0.0  // TL/litre (son kaydedilen)
)

enum class YakitTuru {
    DIZEL, BENZIN, LPG;

    companion object {
        fun fromStorage(value: String?): YakitTuru = entries.firstOrNull { it.name == value } ?: BENZIN
    }
}
