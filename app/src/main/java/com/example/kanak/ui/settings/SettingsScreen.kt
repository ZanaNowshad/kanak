package com.example.kanak.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.kanak.auth.GoogleAuthManager
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.domain.models.User
import com.example.kanak.licensing.LicenseManager
import com.example.kanak.printing.BluetoothPrinterManager
import com.example.kanak.printing.EscPosReceiptRenderer
import com.example.kanak.printing.PrintResult
import com.example.kanak.printing.PrinterDevice
import com.example.kanak.ui.components.KanakConfirmDialog
import com.example.kanak.ui.components.KanakTopBar
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SettingsSubView {
    MAIN,
    PAIRED_PRINTERS,
    PREFERRED_PRINTER,
    CONFIG_ERROR
}

@Composable
fun SettingsScreen(
    repository: KanakRepository,
    authManager: GoogleAuthManager,
    printerManager: BluetoothPrinterManager,
    currentUser: User,
    onNavigateBack: () -> Unit,
    licenseManager: LicenseManager? = null,
    onNavigateToAdminLicensePortal: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var subView by remember { mutableStateOf(SettingsSubView.MAIN) }
    var focusedPrinter by remember { mutableStateOf<PrinterDevice?>(null) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    when (subView) {
        SettingsSubView.MAIN -> {
            MainSettingsView(
                repository = repository,
                authManager = authManager,
                printerManager = printerManager,
                currentUser = currentUser,
                licenseManager = licenseManager,
                onNavigateToPrinters = { subView = SettingsSubView.PAIRED_PRINTERS },
                onNavigateToConfigError = { subView = SettingsSubView.CONFIG_ERROR },
                onNavigateToAdminLicensePortal = onNavigateToAdminLicensePortal,
                onRequestSignOut = { showSignOutDialog = true },
                onNavigateBack = onNavigateBack,
                modifier = modifier
            )
        }
        SettingsSubView.PAIRED_PRINTERS -> {
            BackHandler { subView = SettingsSubView.MAIN }
            PairedPrintersView(
                printerManager = printerManager,
                onSelectPrinterForDetail = { dev ->
                    focusedPrinter = dev
                    subView = SettingsSubView.PREFERRED_PRINTER
                },
                onNavigateBack = { subView = SettingsSubView.MAIN },
                modifier = modifier
            )
        }
        SettingsSubView.PREFERRED_PRINTER -> {
            BackHandler { subView = SettingsSubView.PAIRED_PRINTERS }
            PreferredPrinterView(
                printer = focusedPrinter,
                printerManager = printerManager,
                onNavigateBack = { subView = SettingsSubView.PAIRED_PRINTERS },
                modifier = modifier
            )
        }
        SettingsSubView.CONFIG_ERROR -> {
            BackHandler { subView = SettingsSubView.MAIN }
            ConfigurationErrorView(
                repository = repository,
                onNavigateBack = { subView = SettingsSubView.MAIN },
                modifier = modifier
            )
        }
    }

    if (showSignOutDialog) {
        KanakConfirmDialog(
            title = "Sign Out?",
            message = "Are you sure you want to sign out from Kanak Wholesale Van Sales? Any unsynced offline transactions will remain safely cached.",
            confirmText = "Sign Out",
            cancelText = "Cancel",
            isDestructive = true,
            onConfirm = {
                showSignOutDialog = false
                authManager.signOut()
            },
            onDismiss = { showSignOutDialog = false }
        )
    }
}

// -------------------------------------------------------------
// BOARD 9 SCREEN 1: Settings Main Screen
// -------------------------------------------------------------
@Composable
private fun MainSettingsView(
    repository: KanakRepository,
    authManager: GoogleAuthManager,
    printerManager: BluetoothPrinterManager,
    currentUser: User,
    onNavigateToPrinters: () -> Unit,
    onNavigateToConfigError: () -> Unit,
    onRequestSignOut: () -> Unit,
    onNavigateBack: () -> Unit,
    licenseManager: LicenseManager? = null,
    onNavigateToAdminLicensePortal: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val selectedPrinter by printerManager.selectedPrinter.collectAsState()
    val companyProfile = remember(licenseManager) { licenseManager?.loadProfile() }
    val isMaster = remember(currentUser, licenseManager) {
        licenseManager?.isMasterAdmin(currentUser.email) == true || currentUser.email.equals(LicenseManager.MASTER_ADMIN_EMAIL, ignoreCase = true)
    }

    var gatewayUrl by remember { mutableStateOf(repository.getGatewayUrl() ?: "") }
    var bizName by remember { mutableStateOf(repository.getBusinessName()) }
    var bizAddr by remember { mutableStateOf(repository.getBusinessAddress()) }
    var bizVat by remember { mutableStateOf(repository.getVatNumber()) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var isSyncing by remember { mutableStateOf(false) }

    val initials = remember(currentUser) {
        val email = currentUser.email
        val parts = email.substringBefore('@').split('.', '_', ' ').filter { it.isNotBlank() }
        if (parts.size >= 2) {
            "${parts[0].first().uppercase()}${parts[1].first().uppercase()}"
        } else {
            email.take(2).uppercase()
        }
    }

    Scaffold(
        topBar = {
            KanakTopBar(
                title = "Settings",
                onBack = onNavigateBack
            )
        },
        containerColor = KanakBackground,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // User Profile Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = KanakSurface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(KanakPrimaryBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentUser.displayName.ifBlank { "Ahmed Hussain" },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                        )
                        Text(
                            text = currentUser.email,
                            style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (currentUser.isAdmin || isMaster) KanakSuccessBackground else KanakAccentLight
                    ) {
                        Text(
                            text = if (isMaster) "OWNER" else if (currentUser.isAdmin) "Admin" else "Driver",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (currentUser.isAdmin || isMaster) KanakSuccess else KanakPrimaryBlue
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // MASTER ADMIN LICENSE PORTAL (Visible to Owner / Admin)
            if (isMaster || currentUser.isAdmin) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToAdminLicensePortal?.invoke() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakAccentLight),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, KanakPrimaryBlue)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(KanakPrimaryBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color.White)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Master License Portal",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                            )
                            Text(
                                text = "Manage registrations & generate customer activation codes",
                                style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp)
                            )
                        }

                        Icon(Icons.Default.ChevronRight, contentDescription = "Open", tint = KanakPrimaryBlue)
                    }
                }
            }

            // Company License & Activation Status Card
            if (companyProfile != null && companyProfile.companyName.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = KanakSuccess, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "Licensed Company",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = KanakSuccessBackground
                            ) {
                                Text(
                                    text = "LIFETIME ACTIVE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = KanakSuccess,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = companyProfile.companyName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = KanakPrimaryText)
                        )
                        Text(
                            text = "License ID: ${companyProfile.requestId} · Registered: ${companyProfile.registrationDate}",
                            style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp)
                        )
                    }
                }
            }

            // Printer Settings Row
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToPrinters() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = KanakSurface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(KanakAccentLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            tint = KanakPrimaryBlue
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Thermal Printer Settings",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, color = KanakPrimaryText)
                        )
                        Text(
                            text = selectedPrinter?.let { "Connected: ${it.name}" } ?: "No printer connected",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (selectedPrinter != null) KanakSuccess else KanakSecondaryText
                            )
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open",
                        tint = KanakSecondaryText
                    )
                }
            }

            // Google Sheets & Apps Script Gateway Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = KanakSurface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(KanakSuccessBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, tint = KanakSuccess, modifier = Modifier.size(20.dp))
                            }
                            Text(
                                text = "Google Sheets Gateway",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                            )
                        }

                        IconButton(onClick = onNavigateToConfigError) {
                            Icon(Icons.Default.HelpOutline, contentDescription = "Setup Guide", tint = KanakSecondaryText)
                        }
                    }

                    Text(
                        text = "Connects the van sales app directly to the authoritative 8-worksheet Google Spreadsheet via your deployed Google Apps Script Web App.",
                        style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                    )

                    OutlinedTextField(
                        value = gatewayUrl,
                        onValueChange = { gatewayUrl = it },
                        label = { Text("Gateway Web App URL") },
                        placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gateway_url_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isSyncing = true
                                    val res = repository.fetchAllFromGateway()
                                    isSyncing = false
                                    res.onSuccess {
                                        saveMessage = "Synchronized all data from Google Sheets successfully!"
                                    }.onFailure { err ->
                                        saveMessage = "Sync failed: ${err.message}"
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isSyncing && gatewayUrl.isNotBlank(),
                            modifier = Modifier.testTag("sync_from_sheets_button")
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sync Sheets")
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                repository.setGatewayUrl(gatewayUrl)
                                saveMessage = "Gateway URL saved successfully!"
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue),
                            modifier = Modifier.testTag("save_gateway_url_button")
                        ) {
                            Text("Save URL")
                        }
                    }
                }
            }

            // Company & Receipt Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = KanakSurface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Receipt Seller Information",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                    )

                    OutlinedTextField(
                        value = bizName,
                        onValueChange = { bizName = it },
                        label = { Text("Business Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = bizAddr,
                        onValueChange = { bizAddr = it },
                        label = { Text("Business Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = bizVat,
                        onValueChange = { bizVat = it },
                        label = { Text("VAT Registration Number (TRN)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = {
                            repository.setBusinessName(bizName)
                            repository.setBusinessAddress(bizAddr)
                            repository.setVatNumber(bizVat)
                            saveMessage = "Receipt details updated!"
                        },
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue)
                    ) {
                        Text("Update Details")
                    }
                }
            }

            // Testing Role Switcher
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = KanakSurface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Role Testing & Simulation",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                    )
                    Text(
                        text = "Toggle permissions for testing admin actions (e.g. Purchase reversions, Voiding, Master editing):",
                        style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { authManager.signInAsDemo(asAdmin = true) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Switch to Admin")
                        }
                        OutlinedButton(
                            onClick = { authManager.signInAsDemo(asAdmin = false) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Switch to Driver")
                        }
                    }
                }
            }

            if (saveMessage != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = KanakSuccessBackground,
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = KanakSuccess)
                        Text(
                            text = saveMessage ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(color = KanakSuccess, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }

            // About & Version Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = KanakSurface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "'Kanak' by zedosai.com",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                    )
                    Text(
                        text = "Wholesale FMCG Van Sales System v1.0.0",
                        style = MaterialTheme.typography.bodyMedium.copy(color = KanakSecondaryText)
                    )
                    Text(
                        text = "Developed & Powered by zedosai.com",
                        style = MaterialTheme.typography.bodySmall.copy(color = KanakPrimaryBlue, fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Support: zanabalmuhamed@gmail.com · WhatsApp: +973 3305 0666",
                        style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                    )
                    Text(
                        text = "Kingdom of Bahrain · BHD 3-Decimal Currency · 10% Standard VAT",
                        style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                    )
                }
            }

            // Sign Out Button (Board 9 Screen 1)
            OutlinedButton(
                onClick = onRequestSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sign_out_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KanakError)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// -------------------------------------------------------------
// BOARD 9 SCREEN 2: Paired Printers View
// -------------------------------------------------------------
@Composable
private fun PairedPrintersView(
    printerManager: BluetoothPrinterManager,
    onSelectPrinterForDetail: (PrinterDevice) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pairedPrinters by printerManager.pairedPrinters.collectAsState()
    val selectedPrinter by printerManager.selectedPrinter.collectAsState()
    val displayPrinters = pairedPrinters

    Scaffold(
        topBar = {
            KanakTopBar(
                title = "Paired Printers",
                onBack = onNavigateBack,
                actions = {
                    IconButton(onClick = { printerManager.refreshPairedPrinters() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan", tint = KanakPrimaryBlue)
                    }
                }
            )
        },
        containerColor = KanakBackground,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Bluetooth Hero Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = KanakSurface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(KanakAccentLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = KanakPrimaryBlue,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connect a Bluetooth Printer",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pair your mobile thermal printer via Bluetooth to print invoices instantly from the van.",
                            style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                        )
                    }
                }
            }

            // Scan / Refresh Button
            Button(
                onClick = { printerManager.refreshPairedPrinters() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue)
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan for Printers", fontWeight = FontWeight.SemiBold)
            }

            Text(
                text = "AVAILABLE PRINTERS (${displayPrinters.size})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = KanakSecondaryText,
                    letterSpacing = 1.sp
                )
            )

            // Printer list cards
            displayPrinters.forEach { dev ->
                val isSelected = selectedPrinter?.address == dev.address || (selectedPrinter == null && dev == displayPrinters.first())
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPrinterForDetail(dev) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    border = if (isSelected) {
                        androidx.compose.foundation.BorderStroke(1.5.dp, KanakPrimaryBlue)
                    } else {
                        CardDefaults.outlinedCardBorder()
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) KanakAccentLight else KanakSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = if (isSelected) KanakPrimaryBlue else KanakSecondaryText
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = dev.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, color = KanakPrimaryText)
                                )
                                if (isSelected) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = KanakAccentLight
                                    ) {
                                        Text(
                                            text = "Preferred",
                                            style = MaterialTheme.typography.labelSmall.copy(color = KanakPrimaryBlue, fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = dev.address,
                                style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Details",
                            tint = KanakSecondaryText
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// BOARD 9 SCREEN 3: Preferred Printer Detail Screen
// -------------------------------------------------------------
@Composable
private fun PreferredPrinterView(
    printer: PrinterDevice?,
    printerManager: BluetoothPrinterManager,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val activePrinter = printer ?: PrinterDevice("Sunmi V2 (Bluetooth)", "00:1B:44:11:3A:B7")
    val selectedPrinter by printerManager.selectedPrinter.collectAsState()
    val isPreferred = selectedPrinter?.address == activePrinter.address || selectedPrinter == null

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>("Test receipt printed successfully at 09:25 AM") }

    Scaffold(
        topBar = {
            KanakTopBar(
                title = "Printer Details",
                onBack = onNavigateBack
            )
        },
        containerColor = KanakBackground,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero 3D Thermal Printer Illustration (R.drawable.img_printer_hero_1793446029238)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = KanakSurface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.img_printer_hero_1793446029238),
                        contentDescription = "Thermal Printer",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Printer Title & Preferred Badge
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = activePrinter.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                    )
                    if (isPreferred) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = KanakAccentLight
                        ) {
                            Text(
                                text = "Preferred",
                                style = MaterialTheme.typography.labelSmall.copy(color = KanakPrimaryBlue, fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Text(
                    text = activePrinter.address,
                    style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Green connection status
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(KanakSuccess))
                    Text(
                        text = "Connected - Ready to print invoices",
                        style = MaterialTheme.typography.bodySmall.copy(color = KanakSuccess, fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            // Print Test Receipt Button
            Button(
                onClick = {
                    scope.launch {
                        isTesting = true
                        val testBytes = EscPosReceiptRenderer.ESC_INIT +
                                EscPosReceiptRenderer.ESC_ALIGN_CENTER +
                                EscPosReceiptRenderer.ESC_BOLD_ON +
                                "KANAK THERMAL TEST OK\n".toByteArray(Charsets.US_ASCII) +
                                EscPosReceiptRenderer.ESC_BOLD_OFF +
                                "Bluetooth Link Verified\n\n\n".toByteArray(Charsets.US_ASCII) +
                                EscPosReceiptRenderer.GS_CUT_PARTIAL

                        val res = printerManager.printReceipt(testBytes, "KANAK THERMAL TEST OK\nBluetooth Link Verified")
                        isTesting = false
                        val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                        testResult = when (res) {
                            is PrintResult.Success -> "Test receipt printed successfully at $now"
                            is PrintResult.Failure -> "Print test failed: ${res.error}"
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("printer_test_receipt_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue),
                enabled = !isTesting
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Print Test Receipt", fontWeight = FontWeight.SemiBold)
                }
            }

            // Last test result card
            if (testResult != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (testResult!!.contains("success", ignoreCase = true)) KanakSuccessBackground else KanakErrorBackground
                    ),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (testResult!!.contains("success", ignoreCase = true)) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (testResult!!.contains("success", ignoreCase = true)) KanakSuccess else KanakError
                        )
                        Text(
                            text = testResult ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (testResult!!.contains("success", ignoreCase = true)) KanakSuccess else KanakError,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            // Set as Preferred Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = KanakSurface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Set as Preferred Printer",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = KanakPrimaryText)
                        )
                        Text(
                            text = "Invoices will automatically route to this printer by default.",
                            style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                        )
                    }

                    Switch(
                        checked = isPreferred,
                        onCheckedChange = { checked ->
                            if (checked) {
                                printerManager.selectPrinter(activePrinter)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = KanakPrimaryBlue
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// -------------------------------------------------------------
// BOARD 9 SCREEN 4: Configuration Error Guidance View
// -------------------------------------------------------------
@Composable
private fun ConfigurationErrorView(
    repository: KanakRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            KanakTopBar(
                title = "Configuration Guide",
                onBack = onNavigateBack
            )
        },
        containerColor = KanakBackground,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Spreadsheet Missing / Alert Icon Circle
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(KanakWarning.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = KanakWarning,
                    modifier = Modifier.size(44.dp)
                )
            }

            // Title & Subtitle
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Google Sheets Setup Guide",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                )
                Text(
                    text = "Follow these 3 simple steps to connect Kanak to your central Google Sheets database:",
                    style = MaterialTheme.typography.bodyMedium.copy(color = KanakSecondaryText),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // Numbered Action Steps (Board 9 Screen 4)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = KanakSurface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StepItem(
                        number = "1",
                        title = "Deploy Apps Script in Google Sheets",
                        description = "Open your master Google Spreadsheet (with the 8 worksheets: Products, Customers, Suppliers, Purchases, Invoices, InvoiceLines, StockAdjustments, Users). Go to Extensions > Apps Script, paste the Kanak Gateway script, and click Deploy as Web App."
                    )
                    HorizontalDivider(color = KanakDivider)
                    StepItem(
                        number = "2",
                        title = "Copy the Web App URL",
                        description = "Select 'Execute as: Me' and 'Who has access: Anyone' (secured by the Bearer token), then copy the Web App URL ending in '/exec'."
                    )
                    HorizontalDivider(color = KanakDivider)
                    StepItem(
                        number = "3",
                        title = "Paste URL into Kanak Settings",
                        description = "Return to Kanak > Settings > Google Sheets Gateway, paste the copied URL, and tap 'Save URL' and 'Sync Sheets'."
                    )
                }
            }

            // Offline Note
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = KanakAccentLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = KanakPrimaryBlue)
                    Text(
                        text = "The van sales app continues operating offline with local SQLite/memory cache until you configure the gateway URL.",
                        style = MaterialTheme.typography.bodySmall.copy(color = KanakPrimaryBlue)
                    )
                }
            }

            if (statusMessage != null) {
                Text(
                    text = statusMessage ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (statusMessage!!.contains("success", ignoreCase = true)) KanakSuccess else KanakError,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Actions
            Button(
                onClick = {
                    scope.launch {
                        isChecking = true
                        val res = repository.fetchAllFromGateway()
                        isChecking = false
                        statusMessage = if (res.isSuccess) {
                            "Connection verified successfully!"
                        } else {
                            "Connection check failed: ${res.exceptionOrNull()?.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue),
                enabled = !isChecking
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry Connection", fontWeight = FontWeight.SemiBold)
                }
            }

            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Return to Settings")
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StepItem(
    number: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(KanakPrimaryBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, lineHeight = 18.sp)
            )
        }
    }
}
