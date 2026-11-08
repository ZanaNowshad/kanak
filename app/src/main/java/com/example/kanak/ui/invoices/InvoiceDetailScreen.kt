package com.example.kanak.ui.invoices

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.domain.financial.BahrainFinancials
import com.example.kanak.printing.BluetoothPrinterManager
import com.example.kanak.printing.EscPosReceiptRenderer
import com.example.kanak.printing.PrintResult
import com.example.kanak.ui.components.KanakTopBar
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.kanak.domain.models.Invoice
import com.example.kanak.printing.KanakAndroidPrintHelper
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(
    repository: KanakRepository,
    printerManager: BluetoothPrinterManager,
    invoiceId: String,
    isAdmin: Boolean,
    currentUserId: String = "driver",
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val invoices by repository.invoices.collectAsState()
    val invoice = remember(invoiceId, invoices) {
        invoices.find { it.invoiceId == invoiceId }
    }

    var showPaymentDialog by remember { mutableStateOf(false) }

    // Sub-view: "DETAIL", "PRINT_RECEIPT", "VOID_INVOICE"
    var currentSubView by remember { mutableStateOf("DETAIL") }

    // Print Receipt View States
    var selectedPaperSize by remember { mutableStateOf("80mm") }
    var printStatusMessage by remember { mutableStateOf<String?>(null) }
    var isPrinting by remember { mutableStateOf(false) }

    // Void Invoice States
    var voidReason by remember { mutableStateOf("") }
    var voidError by remember { mutableStateOf<String?>(null) }
    var isVoiding by remember { mutableStateOf(false) }

    val renderer = remember(selectedPaperSize) {
        EscPosReceiptRenderer(
            columnWidth = if (selectedPaperSize == "80mm") 42 else 32,
            businessName = repository.getBusinessName(),
            businessAddress = repository.getBusinessAddress(),
            vatNumber = repository.getVatNumber()
        )
    }

    val receiptPlainText = remember(invoice, selectedPaperSize) {
        if (invoice != null) renderer.renderPlainText(invoice) else ""
    }

    if (invoice == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Invoice not found", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    when (currentSubView) {
        "PRINT_RECEIPT" -> {
            // =========================================================================
            // PRINT RECEIPT SCREEN (BOARD 5 SCREEN 3)
            // =========================================================================
            Scaffold(
                topBar = {
                    KanakTopBar(
                        title = "Print Receipt",
                        onBack = { currentSubView = "DETAIL" }
                    )
                },
                bottomBar = {
                    Surface(
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        color = KanakSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isPrinting = true
                                        val bytes = renderer.renderEscPosBytes(invoice)
                                        val res = printerManager.printReceipt(bytes, receiptPlainText)
                                        isPrinting = false
                                        printStatusMessage = when (res) {
                                            is PrintResult.Success -> res.message
                                            is PrintResult.Failure -> res.error
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                enabled = !isPrinting
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry", fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        isPrinting = true
                                        val bytes = renderer.renderEscPosBytes(invoice)
                                        val res = printerManager.printReceipt(bytes, receiptPlainText)
                                        isPrinting = false
                                        printStatusMessage = when (res) {
                                            is PrintResult.Success -> res.message
                                            is PrintResult.Failure -> res.error
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(52.dp)
                                    .testTag("print_receipt_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = KanakPrimaryBlue,
                                    contentColor = Color.White
                                ),
                                enabled = !isPrinting
                            ) {
                                if (isPrinting) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                } else {
                                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Print", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                containerColor = KanakBackground
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(KanakBackground)
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Android System Print / PDF Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(shape = CircleShape, color = KanakAccentLight, modifier = Modifier.size(40.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Print, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Column {
                                    Text("Android Print / PDF", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text("Print to standard roll, A4 or PDF", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp))
                                }
                            }
                            Button(
                                onClick = {
                                    KanakAndroidPrintHelper.printInvoiceReceipt(
                                        context = context,
                                        invoice = invoice,
                                        businessName = repository.getBusinessName(),
                                        businessAddress = repository.getBusinessAddress(),
                                        vatNumber = repository.getVatNumber()
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue)
                            ) {
                                Text("Print PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    // Select Printer Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Select Printer",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = KanakPrimaryText
                                    )
                                )
                                Text(
                                    text = "Refresh",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = KanakPrimaryBlue
                                    ),
                                    modifier = Modifier.clickable {
                                        Toast.makeText(context, "Scanning Bluetooth paired printers...", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = KanakPrimaryBlue,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Bluetooth,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = printerManager.selectedPrinter.value?.name ?: "Sunmi V2 (Bluetooth)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = printerManager.selectedPrinter.value?.address ?: "00:1B:44:11:3A:B7",
                                        style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = KanakSuccessBackground
                                ) {
                                    Text(
                                        text = "Connected",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = KanakSuccess,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Paper Size Toggle
                    Column {
                        Text(
                            text = "Paper Size",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = KanakPrimaryText
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { selectedPaperSize = "80mm" },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedPaperSize == "80mm") KanakPrimaryBlue else KanakSurfaceVariant,
                                    contentColor = if (selectedPaperSize == "80mm") Color.White else KanakPrimaryText
                                ),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("80mm (Thermal)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = { selectedPaperSize = "58mm" },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedPaperSize == "58mm") KanakPrimaryBlue else KanakSurfaceVariant,
                                    contentColor = if (selectedPaperSize == "58mm") Color.White else KanakPrimaryText
                                ),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("58mm", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Receipt Preview Section with Serrated Thermal Paper Effect matching Board 5 Screen 3
                    Column {
                        Text(
                            text = "Receipt Preview",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = KanakPrimaryText
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val isReducedMotion = LocalReducedMotion.current
                        AnimatedVisibility(
                            visible = true,
                            enter = if (isReducedMotion) {
                                fadeIn(animationSpec = snap())
                            } else {
                                fadeIn(animationSpec = tween(durationMillis = 180, easing = MotionCurves.EaseOut)) +
                                    expandVertically(
                                        expandFrom = Alignment.Top,
                                        animationSpec = tween(durationMillis = 250, easing = MotionCurves.EaseOut)
                                    )
                            }
                        ) {
                            Card(
                                shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFCFDFD)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = repository.getBusinessName(),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.Black
                                    ),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = repository.getBusinessAddress(),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color.DarkGray
                                    ),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "VAT TRN: ${repository.getVatNumber()}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color.DarkGray
                                    ),
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "------------------------------------------",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = Color.Gray)
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Invoice: ${invoice.invoiceNumber}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Black)
                                    Text("Status: ${invoice.status}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Black)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Customer: ${invoice.customerName.take(16)}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Black)
                                    Text(invoice.date.take(10), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Black)
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "------------------------------------------",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = Color.Gray)
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                // Items Table
                                invoice.lines.forEach { line ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${line.qty.stripTrailingZeros().toPlainString()}x ${line.productName.take(16)}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Color.Black
                                        )
                                        Text(
                                            text = BahrainFinancials.formatBhd(line.lineTotal),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "------------------------------------------",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = Color.Gray)
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Subtotal:", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Black)
                                    Text(BahrainFinancials.formatBhd(invoice.subtotal), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Black)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("VAT (10%):", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Black)
                                    Text(BahrainFinancials.formatBhd(invoice.vatTotal), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Black)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Total:", fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                    Text(BahrainFinancials.formatBhd(invoice.total), fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Thank you for your business!",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color.DarkGray
                                )
                                Text(
                                    text = "www.kanak.bh",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                    }

                    if (printStatusMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = KanakSuccessBackground,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = printStatusMessage ?: "",
                                color = KanakSuccess,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        "VOID_INVOICE" -> {
            // =========================================================================
            // VOID INVOICE SCREEN (BOARD 5 SCREEN 4)
            // =========================================================================
            Scaffold(
                topBar = {
                    KanakTopBar(
                        title = "Void Invoice",
                        onBack = { currentSubView = "DETAIL" }
                    )
                },
                containerColor = KanakBackground
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(KanakBackground)
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Red Warning Banner
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakErrorBackground),
                        border = androidx.compose.foundation.BorderStroke(1.dp, KanakError.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.Warning, contentDescription = null, tint = KanakError, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Void This Invoice?",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakError)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "This action will cancel the invoice and restore the stock quantities exactly once.\nThis cannot be undone.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = KanakPrimaryText, lineHeight = 16.sp)
                                )
                            }
                        }
                    }

                    // Invoice Summary Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Invoice No.", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                    Text(invoice.invoiceNumber, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                }
                                Surface(shape = RoundedCornerShape(6.dp), color = KanakSuccessBackground) {
                                    Text(invoice.status, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = KanakSuccess), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = KanakDivider)
                            Text("Customer: ${invoice.customerName}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("Date & Time: ${invoice.date}", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Total Amount: ${BahrainFinancials.formatBhd(invoice.total)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText))
                        }
                    }

                    // Reason for voiding input field
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Reason for voiding (required)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                            Text("${voidReason.length}/200", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = voidReason,
                            onValueChange = { if (it.length <= 200) voidReason = it },
                            placeholder = { Text("Enter reason (e.g. customer returned items, wrong invoice, etc.)") },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Info Card
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakAccentLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Info, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Stock Restoration: Product quantities from this invoice will be restored to stock exactly once after confirmation.",
                                style = MaterialTheme.typography.bodySmall.copy(color = KanakPrimaryText, fontSize = 11.sp)
                            )
                        }
                    }

                    if (voidError != null) {
                        Text(text = voidError ?: "", color = KanakError, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { currentSubView = "DETAIL" },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (voidReason.isBlank()) {
                                    voidError = "Reason is required to void an invoice"
                                    return@Button
                                }
                                scope.launch {
                                    isVoiding = true
                                    voidError = null
                                    val res = repository.voidInvoice(invoice.invoiceId, "admin_user")
                                    isVoiding = false
                                    res.onSuccess {
                                        currentSubView = "DETAIL"
                                    }.onFailure { err ->
                                        voidError = err.message ?: "Failed to void invoice"
                                    }
                                }
                            },
                            enabled = !isVoiding && voidReason.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KanakError, contentColor = Color.White),
                            modifier = Modifier.weight(1.3f).height(50.dp).testTag("confirm_void_button")
                        ) {
                            if (isVoiding) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                            } else {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Confirm Void", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        else -> {
            // =========================================================================
            // INVOICE DETAIL VIEW (BOARD 5 SCREEN 2)
            // =========================================================================
            Scaffold(
                topBar = {
                    KanakTopBar(
                        title = "Invoice Detail",
                        onBack = onNavigateBack
                    )
                },
                bottomBar = {
                    Surface(
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        color = KanakSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { currentSubView = "PRINT_RECEIPT" },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("print_receipt_nav_button"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = KanakPrimaryBlue)
                            ) {
                                Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Print Receipt", fontWeight = FontWeight.Bold)
                            }

                            if (isAdmin && !invoice.isVoid) {
                                Button(
                                    onClick = { currentSubView = "VOID_INVOICE" },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = KanakError, contentColor = Color.White),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("void_invoice_nav_button")
                                ) {
                                    Icon(Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Void Invoice", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = { currentSubView = "PRINT_RECEIPT" },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue, contentColor = Color.White),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Reprint", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                containerColor = KanakBackground
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(KanakBackground)
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Green Banner at top matching Board 5 Screen 2
                    if (!invoice.isVoid) {
                        val isReducedMotion = LocalReducedMotion.current
                        var hasAnimated by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            hasAnimated = true
                        }

                        val bannerScale by animateFloatAsState(
                            targetValue = if (isReducedMotion || hasAnimated) 1.0f else 0.92f,
                            animationSpec = if (isReducedMotion) snap() else MotionSprings.Pop,
                            label = "invoice_success_badge_scale"
                        )
                        val bannerAlpha by animateFloatAsState(
                            targetValue = if (isReducedMotion || hasAnimated) 1.0f else 0.0f,
                            animationSpec = tween(
                                durationMillis = if (isReducedMotion) 0 else 160,
                                easing = MotionCurves.EaseOut
                            ),
                            label = "invoice_success_badge_alpha"
                        )

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = KanakSuccessBackground),
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = bannerScale
                                    scaleY = bannerScale
                                    alpha = bannerAlpha
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = KanakSuccess,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Invoice Posted Successfully",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = KanakSuccess
                                        )
                                    )
                                    Text(
                                        text = "Stock has been updated.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = KanakSuccess)
                                    )
                                }
                            }
                        }
                    } else {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = KanakErrorBackground),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Block, contentDescription = null, tint = KanakError, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Invoice VOIDED", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = KanakError))
                                    Text("Stock restored: ${invoice.voidedAt ?: ""}", style = MaterialTheme.typography.bodySmall.copy(color = KanakError))
                                }
                            }
                        }
                    }

                    // Invoice Header Card (No, Status, Date, Cashier)
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Invoice No.", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = invoice.invoiceNumber,
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                                        )
                                        IconButton(onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Invoice Number", invoice.invoiceNumber))
                                            Toast.makeText(context, "Copied invoice number", Toast.LENGTH_SHORT).show()
                                        }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = KanakPrimaryBlue, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (invoice.isVoid) KanakErrorBackground else KanakSuccessBackground
                                    ) {
                                        Text(
                                            text = invoice.status,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (invoice.isVoid) KanakError else KanakSuccess
                                            ),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = when {
                                            invoice.isSyncConflict -> KanakErrorBackground
                                            invoice.isSynced -> KanakSuccessBackground
                                            else -> KanakAccentLight
                                        }
                                    ) {
                                        Text(
                                            text = when {
                                                invoice.isSyncConflict -> "CONFLICT"
                                                invoice.isSynced -> "SERVER CONFIRMED"
                                                else -> "LOCAL ONLY"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    invoice.isSyncConflict -> KanakError
                                                    invoice.isSynced -> KanakSuccess
                                                    else -> KanakPrimaryBlue
                                                },
                                                fontSize = 10.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = KanakDivider)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Date & Time", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                    Text(invoice.date.take(16).replace("T", " "), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Cashier", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                    Text(invoice.createdBy.ifBlank { "Sales Operator" }, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                }
                            }

                            if (invoice.vanCode.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Fleet Van", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = KanakAccentLight
                                    ) {
                                        Text(
                                            text = invoice.vanCode,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = KanakPrimaryBlue
                                            ),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Customer Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = KanakAccentLight,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Storefront, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(24.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(invoice.customerName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Text("CR: 123456-1 | Manama", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = KanakSecondaryText)
                        }
                    }

                    // Items List Header & Cards
                    Text(
                        text = "Items (${invoice.lines.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                    )

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            invoice.lines.forEachIndexed { idx, line ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(line.productName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = KanakPrimaryText))
                                        Text(
                                            text = "${line.qty.stripTrailingZeros().toPlainString()} x ${BahrainFinancials.formatBhd(line.unitPrice)} (VAT 10%)",
                                            style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp)
                                        )
                                    }
                                    Text(
                                        text = BahrainFinancials.formatBhd(line.lineTotal),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                                    )
                                }
                                if (idx < invoice.lines.size - 1) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = KanakDivider)
                                }
                            }
                        }
                    }

                    // Totals Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Subtotal", style = MaterialTheme.typography.bodyMedium.copy(color = KanakSecondaryText))
                                Text(BahrainFinancials.formatBhd(invoice.subtotal), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("VAT (10%)", style = MaterialTheme.typography.bodyMedium.copy(color = KanakSecondaryText))
                                Text(BahrainFinancials.formatBhd(invoice.vatTotal), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = KanakDivider)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText))
                                Text(BahrainFinancials.formatBhd(invoice.total), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = KanakPrimaryText))
                            }
                        }
                    }

                    // Payment Terms & Credit Ledger Status Card
                    if (invoice.isCredit) {
                        val isCleared = invoice.balanceDue <= BigDecimal.ZERO || invoice.isPaid
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCleared) Color(0xFFF0FDF4) else Color(0xFFFFFBEB)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (isCleared) Color(0xFF22C55E) else Color(0xFFF59E0B)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isCleared) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = if (isCleared) Icons.Default.CheckCircle else Icons.Default.AccountBalanceWallet,
                                                    contentDescription = null,
                                                    tint = if (isCleared) Color(0xFF16A34A) else Color(0xFFD97706),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("Payment Mode: CREDIT", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                            Text(
                                                text = if (isCleared) "All payments received" else "Payment pending from customer",
                                                style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isCleared) Color(0xFFBBF7D0) else Color(0xFFFDE68A)
                                    ) {
                                        Text(
                                            text = if (isCleared) "PAID" else "DUE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCleared) Color(0xFF15803D) else Color(0xFF92400E)
                                            ),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = KanakDivider)

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Amount Paid:", style = MaterialTheme.typography.bodyMedium.copy(color = KanakSecondaryText))
                                    Text(
                                        BahrainFinancials.formatBhd(invoice.amountPaid),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Remaining Balance Due:", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        BahrainFinancials.formatBhd(invoice.balanceDue),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            color = if (isCleared) Color(0xFF16A34A) else Color(0xFFDC2626)
                                        )
                                    )
                                }

                                if (!isCleared && !invoice.isVoid) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = { showPaymentDialog = true },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFD97706),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth().height(46.dp).testTag("detail_receive_payment_button")
                                    ) {
                                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Receive Payment", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(72.dp))
                }
            }

            // Payment Receipt Dialog
            if (showPaymentDialog) {
                val balanceDue = invoice.balanceDue
                var paymentAmountStr by remember(invoice) { mutableStateOf(balanceDue.stripTrailingZeros().toPlainString()) }
                var paymentError by remember { mutableStateOf<String?>(null) }
                var isProcessing by remember { mutableStateOf(false) }

                AlertDialog(
                    onDismissRequest = { if (!isProcessing) showPaymentDialog = false },
                    icon = {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFEF3C7),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Payments, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(28.dp))
                            }
                        }
                    },
                    title = {
                        Text("Receive Credit Payment", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Customer: ${invoice.customerName}")
                            Text("Remaining Due: ${BahrainFinancials.formatBhd(balanceDue)}", fontWeight = FontWeight.Bold, color = Color(0xFFD97706))

                            OutlinedTextField(
                                value = paymentAmountStr,
                                onValueChange = {
                                    paymentAmountStr = it
                                    paymentError = null
                                },
                                label = { Text("Payment Amount (BHD)") },
                                leadingIcon = { Text("BHD", fontWeight = FontWeight.Bold, color = KanakPrimaryBlue, modifier = Modifier.padding(start = 12.dp, end = 4.dp)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("payment_amount_input_dialog")
                            )

                            if (paymentError != null) {
                                Text(paymentError ?: "", color = KanakError, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val amt = BahrainFinancials.parseBhd(paymentAmountStr)
                                if (amt == null || amt <= BigDecimal.ZERO) {
                                    paymentError = "Enter valid positive payment amount."
                                    return@Button
                                }
                                if (amt > balanceDue) {
                                    paymentError = "Payment cannot exceed balance due."
                                    return@Button
                                }

                                scope.launch {
                                    isProcessing = true
                                    val res = repository.recordInvoicePayment(
                                        invoiceId = invoice.invoiceId,
                                        amountToPay = amt,
                                        recordedBy = currentUserId
                                    )
                                    isProcessing = false
                                    res.onSuccess {
                                        Toast.makeText(context, "Payment of ${BahrainFinancials.formatBhd(amt)} recorded!", Toast.LENGTH_LONG).show()
                                        showPaymentDialog = false
                                    }.onFailure {
                                        paymentError = it.message ?: "Failed to record payment"
                                    }
                                }
                            },
                            enabled = !isProcessing && paymentAmountStr.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706), contentColor = Color.White)
                        ) {
                            Text("Confirm Payment", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showPaymentDialog = false }, shape = RoundedCornerShape(10.dp)) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
