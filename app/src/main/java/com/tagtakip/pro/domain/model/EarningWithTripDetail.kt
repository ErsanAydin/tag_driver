package com.tagtakip.pro.domain.model

/** Kazanç kaydı + (varsa) bağlı olduğu yolculuk. [trip] null ise kayıt bir yolculuğa bağlı değildir. */
data class EarningWithTripDetail(
    val earning: Earning,
    val trip: Trip?
)
