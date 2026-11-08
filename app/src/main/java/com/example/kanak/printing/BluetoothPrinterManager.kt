package com.example.kanak.printing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID

data class PrinterDevice(
    val name: String,
    val address: String,
    val isBonded: Boolean = false
)

sealed class PrintResult {
    data class Success(val message: String = "Receipt printed successfully") : PrintResult()
    data class Failure(val error: String, val canRetry: Boolean = true) : PrintResult()
}

sealed class PrinterConnectionState {
    object Idle : PrinterConnectionState()
    data class Connecting(val device: PrinterDevice) : PrinterConnectionState()
    data class Connected(val device: PrinterDevice) : PrinterConnectionState()
    data class Error(val message: String) : PrinterConnectionState()
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
 * Connects to paired and discovered thermal printers using RFCOMM Serial Port Profile (SPP).
 * Features active Bluetooth discovery, pairing requests, connection verification, and ESC/POS transmission.
 */
class BluetoothPrinterManager(
    private val context: Context,
    private val testTransport: PrinterTransport? = null
) {
    companion object {
        private const val TAG = "BluetoothPrinterManager"
        // Standard SPP UUID for Serial Port Profile (used by 99% of portable ESC/POS thermal printers)
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

    private val _discoveredPrinters = MutableStateFlow<List<PrinterDevice>>(emptyList())
    val discoveredPrinters: StateFlow<List<PrinterDevice>> = _discoveredPrinters.asStateFlow()

    private val _selectedPrinter = MutableStateFlow<PrinterDevice?>(null)
    val selectedPrinter: StateFlow<PrinterDevice?> = _selectedPrinter.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _connectionState = MutableStateFlow<PrinterConnectionState>(PrinterConnectionState.Idle)
    val connectionState: StateFlow<PrinterConnectionState> = _connectionState.asStateFlow()

    private val _isPrinting = MutableStateFlow(false)
    val isPrinting: StateFlow<Boolean> = _isPrinting.asStateFlow()

    private val _lastPrintedText = MutableStateFlow<String?>(null)
    val lastPrintedText: StateFlow<String?> = _lastPrintedText.asStateFlow()

    private var discoveryReceiverRegistered = false

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    if (device != null) {
                        val name = try { device.name } catch (e: SecurityException) { null } ?: "Unknown Bluetooth Device"
                        val address = device.address ?: return
                        val isBonded = try { device.bondState == BluetoothDevice.BOND_BONDED } catch (e: SecurityException) { false }

                        val printerDevice = PrinterDevice(name = name, address = address, isBonded = isBonded)
                        val currentList = _discoveredPrinters.value
                        if (currentList.none { it.address.equals(address, ignoreCase = true) }) {
                            _discoveredPrinters.value = currentList + printerDevice
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isDiscovering.value = false
                }
            }
        }
    }

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
                    name = device.name ?: "Bluetooth Printer",
                    address = device.address,
                    isBonded = true
                )
            }
            _pairedPrinters.value = list

            val preferredAddr = getSavedPreferredAddress()
            if (preferredAddr != null) {
                _selectedPrinter.value = list.find { it.address.equals(preferredAddr, ignoreCase = true) }
                    ?: PrinterDevice(name = "Saved Thermal Printer", address = preferredAddr, isBonded = true)
            } else if (list.isNotEmpty() && _selectedPrinter.value == null) {
                _selectedPrinter.value = list.first()
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException refreshing paired printers: ${e.message}")
            _pairedPrinters.value = emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        if (!adapter.isEnabled) return false

        try {
            if (!discoveryReceiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
                context.registerReceiver(discoveryReceiver, filter)
                discoveryReceiverRegistered = true
            }

            _discoveredPrinters.value = emptyList()
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            val started = adapter.startDiscovery()
            _isDiscovering.value = started
            return started
        } catch (e: SecurityException) {
            Log.w(TAG, "Bluetooth scan permission missing: ${e.message}")
            _isDiscovering.value = false
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        val adapter = bluetoothAdapter ?: return
        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException stopping discovery: ${e.message}")
        } finally {
            _isDiscovering.value = false
            unregisterReceiverSafely()
        }
    }

    private fun unregisterReceiverSafely() {
        if (discoveryReceiverRegistered) {
            try {
                context.unregisterReceiver(discoveryReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering discovery receiver: ${e.message}")
            }
            discoveryReceiverRegistered = false
        }
    }

    fun selectPrinter(device: PrinterDevice) {
        _selectedPrinter.value = device
        savePreferredAddress(device.address)
        _connectionState.value = PrinterConnectionState.Connected(device)
    }

    @SuppressLint("MissingPermission")
    fun pairDevice(device: PrinterDevice): Boolean {
        val adapter = bluetoothAdapter ?: return false
        try {
            val remoteDevice = adapter.getRemoteDevice(device.address)
            return remoteDevice.createBond()
        } catch (e: Exception) {
            Log.w(TAG, "Pairing initiation failed: ${e.message}")
            return false
        }
    }

    /**
     * Attempts a real RFCOMM socket connection to verify communication and optionally sends
     * an ESC/POS alignment & test banner.
     */
    suspend fun connectAndTest(device: PrinterDevice): PrintResult = withContext(Dispatchers.IO) {
        _connectionState.value = PrinterConnectionState.Connecting(device)

        if (testTransport != null) {
            val testBytes = buildTestPatternBytes(device.name)
            val res = testTransport.print(testBytes)
            if (res is PrintResult.Success) {
                selectPrinter(device)
                _connectionState.value = PrinterConnectionState.Connected(device)
            } else {
                _connectionState.value = PrinterConnectionState.Error((res as PrintResult.Failure).error)
            }
            return@withContext res
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            val err = "Bluetooth is disabled on this device"
            _connectionState.value = PrinterConnectionState.Error(err)
            return@withContext PrintResult.Failure(err)
        }

        stopDiscovery()

        val remoteDevice: BluetoothDevice = try {
            adapter.getRemoteDevice(device.address)
        } catch (e: Exception) {
            val err = "Invalid Bluetooth address: ${device.address}"
            _connectionState.value = PrinterConnectionState.Error(err)
            return@withContext PrintResult.Failure(err)
        }

        var socket: BluetoothSocket? = null
        try {
            socket = remoteDevice.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()

            val testPattern = buildTestPatternBytes(device.name)
            val stream = socket.outputStream
            stream.write(testPattern)
            stream.flush()

            selectPrinter(device)
            _connectionState.value = PrinterConnectionState.Connected(device)
            PrintResult.Success("Successfully connected to ${device.name}!")
        } catch (e: IOException) {
            val err = "Failed to connect to ${device.name}: ${e.message ?: "Printer unreachable"}. Ensure printer is powered on and within range."
            _connectionState.value = PrinterConnectionState.Error(err)
            PrintResult.Failure(err)
        } finally {
            try {
                socket?.close()
            } catch (ignored: Exception) {}
        }
    }

    private fun buildTestPatternBytes(printerName: String): ByteArray {
        val stream = ByteArrayOutputStream()
        // ESC @ Initialize
        stream.write(EscPosReceiptRenderer.ESC_INIT)
        // Center align
        stream.write(EscPosReceiptRenderer.ESC_ALIGN_CENTER)
        // Bold on
        stream.write(EscPosReceiptRenderer.ESC_BOLD_ON)
        stream.write("================================\n".toByteArray(Charsets.US_ASCII))
        stream.write("    KANAK ESC/POS PRINTER OK    \n".toByteArray(Charsets.US_ASCII))
        stream.write("================================\n".toByteArray(Charsets.US_ASCII))
        stream.write(EscPosReceiptRenderer.ESC_BOLD_OFF)
        stream.write("Connected: $printerName\n".toByteArray(Charsets.US_ASCII))
        stream.write("Standard 58/80mm Thermal Mode\n".toByteArray(Charsets.US_ASCII))
        stream.write("Ready to Issue Bahrain VAT Sales\n".toByteArray(Charsets.US_ASCII))
        stream.write("--------------------------------\n\n\n".toByteArray(Charsets.US_ASCII))
        stream.write(byteArrayOf(EscPosReceiptRenderer.LF, EscPosReceiptRenderer.LF))
        return stream.toByteArray()
    }

    /**
     * Sends binary ESC/POS commands to the currently selected or preferred thermal printer.
     */
    suspend fun printReceipt(bytes: ByteArray, plainTextPreview: String? = null): PrintResult = withContext(Dispatchers.IO) {
        _isPrinting.value = true
        _lastPrintedText.value = plainTextPreview

        try {
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
            _selectedPrinter.value = PrinterDevice(name = "Saved Thermal Printer", address = saved, isBonded = true)
        }
    }

    suspend fun printInvoice(
        invoice: com.example.kanak.domain.models.Invoice,
        businessName: String = "KANAK TRADING W.L.L.",
        vatNumber: String = "BH200012345600"
    ): PrintResult {
        val renderer = EscPosReceiptRenderer(
            columnWidth = 32,
            businessName = businessName,
            vatNumber = vatNumber
        )
        val bytes = renderer.renderEscPosBytes(invoice)
        val plainText = renderer.renderPlainText(invoice)
        return printReceipt(bytes, plainText)
    }
}

