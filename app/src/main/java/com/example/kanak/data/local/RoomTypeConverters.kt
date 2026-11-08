package com.example.kanak.data.local

import androidx.room.TypeConverter
import java.math.BigDecimal

/**
 * Type converters for Room database.
 * Encodes BigDecimals to strings to guarantee exact 3-decimal Bahrain financial precision
 * without IEEE 754 floating-point inaccuracies.
 */
class RoomTypeConverters {
    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? {
        return value?.toPlainString()
    }

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? {
        return if (value.isNullOrBlank()) {
            BigDecimal.ZERO
        } else {
            try {
                BigDecimal(value)
            } catch (e: Exception) {
                BigDecimal.ZERO
            }
        }
    }
}
