package com.tagtakip.pro.presentation.shift

import kotlin.math.roundToInt

/** Mesai sonu yakıt hesabı (saf fonksiyon, birim testli). */
object FuelCostCalculator {

    sealed interface Outcome {
        /** Araç bilgisi yok ya da 100 km tüketimi girilmemiş. */
        data object MissingConsumption : Outcome
        /** Vardiyada mesafe kaydedilmemiş. */
        data object NoDistance : Outcome
        data class Ready(
            val distanceKm: Double,
            val litre: Double,
            val cost: Double,
            val needsFuelPrice: Boolean
        ) : Outcome
    }

    fun calculate(distanceMeters: Float, lt100km: Double?, pricePerLitre: Double?): Outcome {
        if (lt100km == null || lt100km <= 0.0) return Outcome.MissingConsumption
        if (distanceMeters <= 0f) return Outcome.NoDistance
        val km = distanceMeters / 1000.0
        val litre = km * (lt100km / 100.0)
        val price = pricePerLitre ?: 0.0
        return Outcome.Ready(
            distanceKm = km,
            litre = litre,
            cost = round2(litre * price),
            needsFuelPrice = price <= 0.0
        )
    }

    fun round2(value: Double): Double = (value * 100.0).roundToInt() / 100.0
}
