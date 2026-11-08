package com.example.kanak.barcode

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.kanak.domain.models.Product
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Enterprise Barcode Scanning Manager for Kanak Van Sales.
 * Combines Google Play Services Code Scanner (zero permissions required)
 * with instant product lookup, duplicate SKU handling, and tactile haptic feedback.
 */
class BarcodeScannerManager(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Triggers the zero-permission Google Code Scanner overlay.
     * When a barcode is recognized, plays tactile feedback and invokes [onBarcodeScanned].
     */
    fun startScan(
        onBarcodeScanned: (String) -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)

        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_ALL_FORMATS
                )
                .enableAutoZoom()
                .build()

            val scanner = GmsBarcodeScanning.getClient(context, options)
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val rawValue = barcode.rawValue ?: barcode.displayValue
                    if (!rawValue.isNullOrBlank()) {
                        triggerScanHaptic()
                        onBarcodeScanned(rawValue.trim())
                    } else {
                        onError("Unreadable barcode. Please enter manually.")
                    }
                }
                .addOnCanceledListener {
                    onCancelled()
                }
                .addOnFailureListener { e ->
                    Log.w("BarcodeScannerManager", "Code scanner failure: ${e.message}")
                    onError(e.localizedMessage ?: "Barcode scanner could not be opened. Use manual search.")
                }
        } catch (e: Exception) {
            Log.e("BarcodeScannerManager", "Error initializing scanner", e)
            onError(e.localizedMessage ?: "Scanner unavailable")
        }
    }

    /**
     * Provides meaningful tactile confirmation upon barcode recognition.
     */
    fun triggerScanHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(70)
            }
        } catch (_: Exception) {}
    }

    /**
     * Matches a scanned barcode against the active product catalog.
     * Searches both exact barcode matches and SKU/ProductId references.
     */
    fun lookupProductByBarcode(products: List<Product>, barcodeString: String): Product? {
        val cleaned = barcodeString.trim()
        if (cleaned.isBlank()) return null
        return products.find { it.barcode.equals(cleaned, ignoreCase = true) }
            ?: products.find { it.productId.equals(cleaned, ignoreCase = true) }
            ?: products.find { it.barcode.replace("-", "").equals(cleaned.replace("-", ""), ignoreCase = true) }
    }
}
