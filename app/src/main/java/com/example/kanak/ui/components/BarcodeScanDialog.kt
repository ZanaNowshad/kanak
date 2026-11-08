package com.example.kanak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.kanak.barcode.BarcodeScannerManager
import com.example.kanak.domain.financial.BahrainFinancials
import com.example.kanak.domain.models.Product
import com.example.ui.theme.*
import java.math.BigDecimal

/**
 * High-speed Barcode Scanner Dialog with Camera Scanner trigger,
 * instant manual SKU / Barcode fallback, and duplicate SKU detection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScanDialog(
    products: List<Product>,
    vanStockMap: Map<String, BigDecimal> = emptyMap(),
    existingProductIdsInInvoice: Set<String> = emptySet(),
    onProductSelected: (Product, Boolean) -> Unit, // Product, isDuplicateIncrement
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scannerManager = remember { BarcodeScannerManager(context) }
    var manualBarcode by remember { mutableStateOf("") }
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }
    var matchedProduct by remember { mutableStateOf<Product?>(null) }

    // Launch Google Code Scanner immediately on mount
    LaunchedEffect(Unit) {
        scannerManager.startScan(
            onBarcodeScanned = { scannedCode ->
                manualBarcode = scannedCode
                val found = scannerManager.lookupProductByBarcode(products, scannedCode)
                if (found != null) {
                    matchedProduct = found
                    val isDuplicate = existingProductIdsInInvoice.contains(found.productId)
                    onProductSelected(found, isDuplicate)
                    onDismiss()
                } else {
                    scanErrorMessage = "No product found with barcode: $scannedCode"
                }
            },
            onError = { err ->
                scanErrorMessage = err
            }
        )
    }

    val filteredProducts = remember(manualBarcode, products) {
        if (manualBarcode.isBlank()) {
            emptyList()
        } else {
            val q = manualBarcode.trim().lowercase()
            products.filter {
                it.barcode.lowercase().contains(q) ||
                it.productId.lowercase().contains(q) ||
                it.name.lowercase().contains(q)
            }.take(6)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = KanakSurface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("barcode_scan_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = KanakAccentLight,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = KanakPrimaryBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Barcode Scanner",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = KanakPrimaryText
                                )
                            )
                            Text(
                                text = "Camera scan or manual SKU entry",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = KanakSecondaryText,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = KanakSecondaryText)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Camera Scanner Action Button
                Button(
                    onClick = {
                        scanErrorMessage = null
                        scannerManager.startScan(
                            onBarcodeScanned = { scannedCode ->
                                manualBarcode = scannedCode
                                val found = scannerManager.lookupProductByBarcode(products, scannedCode)
                                if (found != null) {
                                    matchedProduct = found
                                    val isDuplicate = existingProductIdsInInvoice.contains(found.productId)
                                    onProductSelected(found, isDuplicate)
                                    onDismiss()
                                } else {
                                    scanErrorMessage = "No product found with barcode: $scannedCode"
                                }
                            },
                            onError = { err ->
                                scanErrorMessage = err
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("start_camera_scan_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("START CAMERA SCAN", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                // Error / Feedback Banner
                if (scanErrorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = KanakErrorBackground,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = KanakError, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = scanErrorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(color = KanakError, fontSize = 11.sp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Manual Input Divider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = KanakBorder)
                    Text(
                        text = "OR ENTER BARCODE / SKU",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = KanakSecondaryText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = KanakBorder)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Outlined Input Field for Manual Barcode
                OutlinedTextField(
                    value = manualBarcode,
                    onValueChange = {
                        manualBarcode = it
                        scanErrorMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_barcode_input"),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("Enter EAN-13, SKU, or Product Name", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Outlined.QrCode, contentDescription = null, tint = KanakPrimaryBlue)
                    },
                    trailingIcon = {
                        if (manualBarcode.isNotBlank()) {
                            IconButton(onClick = { manualBarcode = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = KanakSecondaryText)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            val found = scannerManager.lookupProductByBarcode(products, manualBarcode)
                            if (found != null) {
                                val isDuplicate = existingProductIdsInInvoice.contains(found.productId)
                                onProductSelected(found, isDuplicate)
                                onDismiss()
                            } else {
                                scanErrorMessage = "Product '$manualBarcode' not found in inventory."
                            }
                        }
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KanakPrimaryBlue,
                        unfocusedBorderColor = KanakBorder
                    )
                )

                // Live search matches
                if (filteredProducts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "MATCHING PRODUCTS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = KanakSecondaryText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredProducts) { prod ->
                            val isDuplicate = existingProductIdsInInvoice.contains(prod.productId)
                            val vanStock = vanStockMap[prod.productId] ?: prod.stockQty

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isDuplicate) KanakAccentLight.copy(alpha = 0.5f) else KanakBackground,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isDuplicate) KanakPrimaryBlue else KanakBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        scannerManager.triggerScanHaptic()
                                        onProductSelected(prod, isDuplicate)
                                        onDismiss()
                                    }
                                    .testTag("barcode_match_${prod.productId}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = prod.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = KanakPrimaryText
                                            )
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "BC: ${prod.barcode} • Stock: $vanStock",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = KanakSecondaryText,
                                                    fontSize = 11.sp
                                                )
                                            )
                                            if (isDuplicate) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(shape = RoundedCornerShape(4.dp), color = KanakPrimaryBlue) {
                                                    Text(
                                                        text = "IN INVOICE (+1)",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 9.sp
                                                        ),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Text(
                                        text = BahrainFinancials.formatBhd(prod.sellingPrice),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            color = KanakPrimaryBlue
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
