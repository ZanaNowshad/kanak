package com.example.kanak.ui.sales

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.domain.financial.BahrainFinancials
import com.example.kanak.domain.models.DailySalesSummaryData
import com.example.kanak.domain.models.User
import com.example.kanak.printing.BluetoothPrinterManager
import com.example.kanak.printing.EscPosReceiptRenderer
import com.example.kanak.printing.KanakAndroidPrintHelper
import com.example.kanak.printing.PrintResult
import com.example.kanak.ui.components.KanakTopBar
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailySalesSummaryScreen(
    repository: KanakRepository,
    currentUser: User,
    printerManager: BluetoothPrinterManager,
    onNavigateBack: () -> Unit,
    onNavigateToInvoiceDetail: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vans by repository.vans.collectAsState()
    val invoices by repository.invoices.collectAsState()

    // Determine initial selected van: If salesperson, locked or defaulted to assigned van
    val initialVanId = if (currentUser.isSalesperson && !currentUser.assignedVanId.isNullOrBlank()) {
        currentUser.assignedVanId
    } else null

    var selectedVanId by remember { mutableStateOf<String?>(initialVanId) }
    var isBluetoothPrinting by remember { mutableStateOf(false) }

    // Dynamic Summary calculation
    val summaryData = remember(invoices, selectedVanId) {
        repository.getDailySalesSummary(vanIdFilter = selectedVanId)
    }

    Scaffold(
        topBar = {
            KanakTopBar(
                title = "Daily Sales Summary",
                onBack = onNavigateBack
            )
        },
        containerColor = KanakBackground,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(KanakBackground)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Van Route Selector Filter (if owner/manager has fleet access)
                if (currentUser.isOwner || currentUser.isManager) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Filter Fleet Van Route",
                                style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedVanId == null,
                                    onClick = { selectedVanId = null },
                                    label = { Text("All Vans (${vans.size})", fontSize = 12.sp) }
                                )
                                vans.forEach { van ->
                                    FilterChip(
                                        selected = selectedVanId == van.vanId,
                                        onClick = { selectedVanId = van.vanId },
                                        label = { Text(van.vanCode, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                    }
                } else if (currentUser.assignedVanCode != null) {
                    // Salesperson locked van banner
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = KanakAccentLight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, KanakPrimaryBlue.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Active Van Route: ${currentUser.assignedVanCode}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryBlue)
                                )
                                Text(
                                    text = "Showing live route session sales for driver ${currentUser.displayName}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp)
                                )
                            }
                        }
                    }
                }
            }

            // Session Revenue Big Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
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
                                Text(
                                    text = "Session Date: ${summaryData.sessionDate}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 12.sp)
                                )
                                Text(
                                    text = "Total Gross Sales",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = KanakSuccessBackground
                            ) {
                                Text(
                                    text = "${summaryData.totalInvoicesCount} INVOICES",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = KanakSuccess),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "BHD ${BahrainFinancials.formatBhd(summaryData.totalGrossSales)}",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = KanakPrimaryText,
                                fontSize = 32.sp
                            ),
                            modifier = Modifier.testTag("daily_total_gross_sales")
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = KanakDivider)
                        Spacer(modifier = Modifier.height(14.dp))

                        // Cash vs Credit breakdown cards
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Cash Sales
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF0FDF4),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AttachMoney, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Cash Sales", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF15803D), fontWeight = FontWeight.Bold, fontSize = 11.sp))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = BahrainFinancials.formatBhd(summaryData.totalCashSales),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                                    )
                                    Text("${summaryData.cashInvoicesCount} cash bills", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF15803D), fontSize = 11.sp))
                                }
                            }

                            // Credit Sales
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFFFBEB),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CreditCard, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Credit Sales", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFB45309), fontWeight = FontWeight.Bold, fontSize = 11.sp))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = BahrainFinancials.formatBhd(summaryData.totalCreditSales),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                                    )
                                    Text("${summaryData.creditInvoicesCount} credit bills", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFB45309), fontSize = 11.sp))
                                }
                            }
                        }

                        // Credit Collections Detail
                        if (summaryData.totalCreditSales > BigDecimal.ZERO) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = KanakSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Cash Collected on Credit:", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                    Text("BHD ${BahrainFinancials.formatBhd(summaryData.cashCollectedOnCredit)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF16A34A)))
                                }
                            }
                        }
                    }
                }
            }

            // VAT Breakdown Card (Mandatory Requirement for Bahrain NBR)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = KanakAccentLight,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Bahrain VAT Breakdown",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                                )
                                Text(
                                    text = "National Bureau for Revenue (NBR) Standard Rate",
                                    style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Taxable Net Subtotal:", style = MaterialTheme.typography.bodyMedium.copy(color = KanakSecondaryText))
                            Text("BHD ${BahrainFinancials.formatBhd(summaryData.taxableSubtotal)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Standard VAT Rate:", style = MaterialTheme.typography.bodyMedium.copy(color = KanakSecondaryText))
                            Text("10.000%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = KanakDivider)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total VAT Collected:", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText))
                            Text(
                                "BHD ${BahrainFinancials.formatBhd(summaryData.vatAmountCollected)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = KanakPrimaryBlue)
                            )
                        }
                    }
                }
            }

            // Print & Share Action Buttons (Android PrintManager & Bluetooth ESC/POS)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Standard Android PrintManager Button
                    Button(
                        onClick = {
                            val activity = context as? Activity
                            if (activity != null) {
                                KanakAndroidPrintHelper.printDailySalesSummary(
                                    activity = activity,
                                    summary = summaryData,
                                    businessName = repository.getBusinessName(),
                                    vatNumber = repository.getVatNumber(),
                                    crNumber = "123456-1"
                                )
                            } else {
                                Toast.makeText(context, "Printer service not available", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue, contentColor = Color.White),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(50.dp)
                            .testTag("print_android_summary_button")
                    ) {
                        Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Print Summary", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Bluetooth ESC/POS Thermal Button
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isBluetoothPrinting = true
                                val plainText = buildThermalSummaryText(summaryData, repository.getBusinessName(), repository.getVatNumber())
                                val bytes = plainText.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                                val res = printerManager.printReceipt(bytes, plainText)
                                isBluetoothPrinting = false
                                when (res) {
                                    is PrintResult.Success -> Toast.makeText(context, "Printed via Bluetooth ESC/POS", Toast.LENGTH_SHORT).show()
                                    is PrintResult.Failure -> Toast.makeText(context, res.error, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isBluetoothPrinting,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KanakPrimaryBlue),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("print_thermal_summary_button")
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isBluetoothPrinting) "Printing..." else "ESC/POS", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }

            // Route Bills List Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Session Invoices (${summaryData.invoices.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                    )
                }
            }

            // Invoices List
            if (summaryData.invoices.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null, tint = KanakSecondaryText, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("No Sales Recorded For This Route Today", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text("Invoices posted during this van delivery session will appear here.", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                        }
                    }
                }
            } else {
                items(summaryData.invoices, key = { it.invoiceId }) { inv ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        onClick = { onNavigateToInvoiceDetail?.invoke(inv.invoiceId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(inv.invoiceNumber, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text(inv.customerName, style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                Text(
                                    text = if (inv.isCredit) "CREDIT (Due: BHD ${BahrainFinancials.formatBhd(inv.balanceDue)})" else "CASH",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (inv.isCredit) Color(0xFFD97706) else Color(0xFF16A34A),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "BHD ${BahrainFinancials.formatBhd(inv.total)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                                )
                                Text(
                                    text = "VAT: BHD ${BahrainFinancials.formatBhd(inv.vatTotal)}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

private fun buildThermalSummaryText(summary: DailySalesSummaryData, businessName: String, vatNumber: String): String {
    val sep = "=".repeat(32)
    val dash = "-".repeat(32)
    val sb = StringBuilder()
    sb.append("      DAILY SALES REPORT       \n")
    sb.append("   $businessName   \n")
    sb.append("   VAT TRN: $vatNumber   \n")
    sb.append(sep).append("\n")
    sb.append("Date: ${summary.sessionDate}\n")
    sb.append("Route: ${summary.vanCode ?: "All Fleet"}\n")
    sb.append(dash).append("\n")
    sb.append("Total Invoices:     ${summary.totalInvoicesCount.toString().padStart(12)}\n")
    sb.append("Gross Sales (BHD):  ${BahrainFinancials.formatBhd(summary.totalGrossSales).padStart(12)}\n")
    sb.append("Cash Sales:         ${BahrainFinancials.formatBhd(summary.totalCashSales).padStart(12)}\n")
    sb.append("Credit Sales:       ${BahrainFinancials.formatBhd(summary.totalCreditSales).padStart(12)}\n")
    sb.append("Credit Outstanding: ${BahrainFinancials.formatBhd(summary.totalOutstandingCredit).padStart(12)}\n")
    sb.append(dash).append("\n")
    sb.append("Taxable Net (BHD):  ${BahrainFinancials.formatBhd(summary.taxableSubtotal).padStart(12)}\n")
    sb.append("VAT 10% (BHD):      ${BahrainFinancials.formatBhd(summary.vatAmountCollected).padStart(12)}\n")
    sb.append(sep).append("\n")
    sb.append("   End of Session Audit Trail   \n\n\n")
    return sb.toString()
}
