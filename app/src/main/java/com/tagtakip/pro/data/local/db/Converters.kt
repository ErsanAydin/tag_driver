package com.tagtakip.pro.data.local.db

import androidx.room.TypeConverter
import com.tagtakip.pro.domain.model.EarningType

class Converters {

    @TypeConverter
    fun fromEarningType(type: EarningType): String = type.name

    /** Bilinmeyen değerler (ileriki sürümlerden gelen) OTHER'a düşer. */
    @TypeConverter
    fun toEarningType(value: String): EarningType =
        EarningType.entries.firstOrNull { it.name == value } ?: EarningType.OTHER
}
