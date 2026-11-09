package com.example.kanak.barcode

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.kanak.domain.models.Product

/** Matches a scanned identifier to the active product catalog. Camera is owned by Compose. */
class BarcodeScannerManager(private val context: Context) {
    fun lookupProductByBarcode(products: List<Product>, barcodeString: String): Product? {
        val key = barcodeString.trim()
        if (key.isBlank()) return null
        return products.firstOrNull { it.barcode.equals(key, ignoreCase = true) }
            ?: products.firstOrNull { it.productId.equals(key, ignoreCase = true) }
    }

    fun triggerScanHaptic() {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(70)
        }
    }
}
