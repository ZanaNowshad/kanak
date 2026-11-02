package com.example.kanak.printing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

data class PrinterDevice(
    val name: String,
    val address: String
)

sealed class PrintResult {
    data class Success(val message: String = "Receipt printed successfully") : PrintResult()
    data class Failure(val error: String, val canRetry: Boolean = true) : PrintResult()
}

interface PrinterTransport {
    suspend fun print(bytes: ByteArray): PrintResult
}

/**
 * Simulated printer transport for unit tests, local emulator runs, and devices without physical Bluetooth printers.
 * Preserves the exact bytes and human-readable text preview.
 */
class SimulatedPrinterTransport : PrinterTransport {
    var lastPrintedBytes: ByteArray? = null
        private set
    var lastPrintedText: String? = null
        private set
    var shouldSimulateError: Boolean = false

    override suspend fun print(bytes: ByteArray): PrintResult {
        if (shouldSimulateError) {
            return PrintResult.Failure("Simulated printer communication timeout")
        }
        lastPrintedBytes = bytes
        lastPrintedText = String(bytes, Charsets.US_ASCII)
        return PrintResult.Success("Receipt sent to simulated thermal printer")
    }
}

/**
 * Bluetooth Classic ESC/POS Printer Manager.
 * Connects to paired thermal printers using RFCOMM Serial Port Profile (SPP).
 */
class BluetoothPrinterManager(
    private val context: Context,
    private val testTransport: PrinterTransport? = null
) {
    companion object {
        // Standard SPP UUID for Serial Port Profile
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val PREFS_NAME = "kanak_printer_prefs"
        private const val KEY_PREFERRED_PRINTER = "preferred_printer_address"
    }

    private val bluetoothAdapter: BluetoothAdapter? = try {
        BluetoothAdapter.getDefaultAdapter()
    } catch (e: Exception) {
        null
    }

    private val _pairedPrinters = MutableStateFlow<List<PrinterDevice>>(emptyList())
    val pairedPrinters: StateFlow<List<PrinterDevice>> = _pairedPrinters.asStateFlow()

    private val _selectedPrinter = MutableStateFlow<PrinterDevice?>(null)
    val selectedPrinter: StateFlow<PrinterDevice?> = _selectedPrinter.asStateFlow()

    private val _isPrinting = MutableStateFlow(false)
    val isPrinting: StateFlow<Boolean> = _isPrinting.asStateFlow()

    private val _lastPrintedText = MutableStateFlow<String?>(null)
    val lastPrintedText: StateFlow<String?> = _lastPrintedText.asStateFlow()

    init {
        loadPreferredPrinter()
        refreshPairedPrinters()
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedPrinters() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _pairedPrinters.value = emptyList()
            return
        }

        try {
            val bonded = adapter.bondedDevices ?: emptySet()
            val list = bonded.map { device ->
                PrinterDevice(
                    name = device.name ?: "Unknown Bluetooth Device",
                    address = device.address
                )
            }
            _pairedPrinters.value = list

            val preferredAddr = getSavedPreferredAddress()
            if (preferredAddr != null) {
                _selectedPrinter.value = list.find { it.address == preferredAddr }
            } else if (list.isNotEmpty() && _selectedPrinter.value == null) {
                _selectedPrinter.value = list.first()
            }
        } catch (e: SecurityException) {
            _pairedPrinters.value = emptyList()
        }
    }

    fun selectPrinter(device: PrinterDevice) {
        _selectedPrinter.value = device
        savePreferredAddress(device.address)
    }

    /**
     * Sends binary ESC/POS commands to the currently selected or preferred thermal printer.
     */
    suspend fun printReceipt(bytes: ByteArray, plainTextPreview: String? = null): PrintResult = withContext(Dispatchers.IO) {
        _isPrinting.value = true
        _lastPrintedText.value = plainTextPreview

        try {
            // Use test transport if explicitly injected (e.g. In Robolectric or emulator test)
            if (testTransport != null) {
                return@withContext testTransport.print(bytes)
            }

            val targetPrinter = _selectedPrinter.value
            if (targetPrinter == null) {
                return@withContext PrintResult.Failure("No Bluetooth thermal printer selected. Please pair and select your ESC/POS printer in Settings.")
            }

            val adapter = bluetoothAdapter
            if (adapter == null || !adapter.isEnabled) {
                return@withContext PrintResult.Failure("Bluetooth is disabled. Please enable Bluetooth in settings.")
            }

            sendToBluetoothDevice(targetPrinter.address, bytes)
        } catch (e: Exception) {
            PrintResult.Failure("Printing error: ${e.message ?: "Unknown communication failure"}")
        } finally {
            _isPrinting.value = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendToBluetoothDevice(address: String, bytes: ByteArray): PrintResult {
        val adapter = bluetoothAdapter ?: return PrintResult.Failure("Bluetooth unavailable")
        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(address)
        } catch (e: Exception) {
            return PrintResult.Failure("Invalid printer address: $address")
        }

        var socket: BluetoothSocket? = null
        return try {
            adapter.cancelDiscovery()
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            val outputStream = socket.outputStream
            outputStream.write(bytes)
            outputStream.flush()
            PrintResult.Success("Receipt printed on ${device.name ?: address}")
        } catch (e: IOException) {
            PrintResult.Failure("Unable to connect to printer: ${e.message}. Ensure printer is turned on and paired.")
        } finally {
            try {
                socket?.close()
            } catch (ignored: Exception) {}
        }
    }

    private fun getSavedPreferredAddress(): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PREFERRED_PRINTER, null)
    }

    private fun savePreferredAddress(address: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PREFERRED_PRINTER, address).apply()
    }

    private fun loadPreferredPrinter() {
        val saved = getSavedPreferredAddress()
        if (saved != null) {
            _selectedPrinter.value = PrinterDevice(name = "Saved Thermal Printer", address = saved)
        }
    }
}
