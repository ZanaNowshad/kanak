package com.example.kanak.printing

import com.example.kanak.domain.models.Invoice

sealed class PrinterConnectionType {
    data class Bluetooth(val macAddress: String) : PrinterConnectionType()
    data class NetworkIp(val ipAddress: String, val port: Int = 9100) : PrinterConnectionType()
    object UsbOtg : PrinterConnectionType()
}

/**
 * Enterprise Unified Thermal Printer Driver interface supporting
 * Bluetooth SPP, Network IP Socket (Port 9100), and USB OTG label printers.
 */
interface ThermalPrinterDriver {
    val connectionType: PrinterConnectionType

    suspend fun connect(): Result<Unit>
    suspend fun disconnect()
    suspend fun isConnected(): Boolean

    suspend fun printTaxInvoice(
        invoice: Invoice,
        businessName: String,
        vatNumber: String,
        paperWidthMm: Int = 58
    ): Result<Unit>
}
