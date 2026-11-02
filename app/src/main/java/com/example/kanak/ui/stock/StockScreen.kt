package com.example.kanak.ui.stock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.domain.financial.BahrainFinancials
import com.example.kanak.domain.models.Product
import com.example.kanak.domain.models.StockAdjustment
import com.example.kanak.ui.components.EmptyStateView
import com.example.kanak.ui.components.KanakFilterChip
import com.example.kanak.ui.components.KanakSearchField
import com.example.kanak.ui.components.KanakTopBar
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    repository: KanakRepository,
    currentUserId: String,
    isAdmin: Boolean,
    onNavigateToPurchases: () -> Unit,
    onNavigateToSuppliers: () -> Unit,
    modifier: Modifier = Modifier
) {
    val products by repository.products.collectAsState()
    val stockAdjustments by repository.stockAdjustments.collectAsState()
    val purchases by repository.purchases.collectAsState()
    val scope = rememberCoroutineScope()

    var currentSubView by remember { mutableStateOf("LIST") } // "LIST", "DETAIL", "ADJUST", "CONFIRMATION"
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var confirmedAdjustment by remember { mutableStateOf<StockAdjustment?>(null) }
    var previousStockVal by remember { mutableStateOf<BigDecimal>(BigDecimal.ZERO) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Beverages", "Snacks", "Dairy", "Household")

    val filteredProducts = remember(products, searchQuery, selectedCategory) {
        var list = products
        if (selectedCategory != "All") {
            list = list.filter {
                when (selectedCategory) {
                    "Beverages" -> it.name.contains("juice", ignoreCase = true) || it.name.contains("water", ignoreCase = true) || it.name.contains("cola", ignoreCase = true)
                    "Snacks" -> it.name.contains("biscuit", ignoreCase = true) || it.name.contains("chips", ignoreCase = true)
                    "Dairy" -> it.name.contains("milk", ignoreCase = true) || it.name.contains("butter", ignoreCase = true)
                    "Household" -> it.name.contains("wash", ignoreCase = true) || it.name.contains("powder", ignoreCase = true)
                    else -> true
                }
            }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) || it.barcode.lowercase().contains(q)
            }
        }
        list
    }

    when (currentSubView) {
        "CONFIRMATION" -> {
            // =========================================================================
            // ADJUSTMENT CONFIRMATION (BOARD 8 SCREEN 4)
            // =========================================================================
            val adj = confirmedAdjustment
            val prod = selectedProduct

            Scaffold(
                containerColor = KanakBackground,
                bottomBar = {
                    Surface(
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        color = KanakSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                currentSubView = "LIST"
                                selectedProduct = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue, contentColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .height(52.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(KanakBackground)
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Green Celebration Checkmark
                    Surface(
                        shape = CircleShape,
                        color = KanakSuccessBackground,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = KanakSuccess,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    Text(
                        text = "Stock Updated",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                    )

                    Text(
                        text = "The inventory has been successfully adjusted.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = KanakSecondaryText),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Product Summary Card
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
                                shape = RoundedCornerShape(8.dp),
                                color = KanakSurfaceVariant,
                                modifier = Modifier.size(46.dp)
                            ) {
                                if (prod?.photoUrl?.isNotBlank() == true) {
                                    AsyncImage(model = prod.photoUrl, contentDescription = null, modifier = Modifier.fillMaxSize().padding(3.dp))
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = KanakSecondaryText)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(prod?.name ?: "Product", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Text(prod?.barcode ?: "", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp))
                            }
                        }
                    }

                    // Adjustment Breakdown Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Previous Quantity", style = MaterialTheme.typography.bodyMedium.copy(color = KanakSecondaryText))
                                Text(previousStockVal.stripTrailingZeros().toPlainString(), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Adjustment", style = MaterialTheme.typography.bodyMedium.copy(color = KanakSecondaryText))
                                val d = adj?.deltaQty ?: BigDecimal.ZERO
                                Text(
                                    text = "${if (d >= BigDecimal.ZERO) "+" else ""}${d.stripTrailingZeros().toPlainString()}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = if (d >= BigDecimal.ZERO) KanakSuccess else KanakError)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("New Quantity", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Text(previousStockVal.add(adj?.deltaQty ?: BigDecimal.ZERO).stripTrailingZeros().toPlainString(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = KanakDivider)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Reason", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                Text(adj?.reason ?: "Adjustment", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Date & Time", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                Text(adj?.date?.take(16)?.replace("T", " ") ?: "", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Adjusted By", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                Text("Ahmed (Admin)", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        "ADJUST" -> {
            // =========================================================================
            // ADJUST STOCK (BOARD 8 SCREEN 3)
            // =========================================================================
            val prod = selectedProduct ?: return
            var isAddDirection by remember { mutableStateOf(true) }
            var qtyInputStr by remember { mutableStateOf("50") }
            var selectedReason by remember { mutableStateOf("Purchase Received") }
            var reasonDropdownExpanded by remember { mutableStateOf(false) }
            var notesStr by remember { mutableStateOf("") }
            var isSubmitting by remember { mutableStateOf(false) }
            var errorMsg by remember { mutableStateOf<String?>(null) }

            val reasons = listOf("Purchase Received", "Physical Count Audit", "Damaged Goods", "Returned Items", "Expired / Spoiled", "Correction")

            val qtyInput = BahrainFinancials.parseBhd(qtyInputStr)
            val delta = if (isAddDirection) qtyInput else qtyInput.negate()
            val resultingStock = prod.stockQty.add(delta)

            Scaffold(
                containerColor = KanakBackground,
                topBar = {
                    KanakTopBar(
                        title = "Adjust Stock",
                        onBack = { currentSubView = "DETAIL" }
                    )
                }
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
                    // Header Product Summary
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
                                color = KanakSurfaceVariant,
                                modifier = Modifier.size(50.dp)
                            ) {
                                if (!prod.photoUrl.isNullOrBlank()) {
                                    AsyncImage(model = prod.photoUrl, contentDescription = null, modifier = Modifier.fillMaxSize().padding(3.dp))
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = KanakSecondaryText)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(prod.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Text(prod.barcode, style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp))
                            }
                        }
                    }

                    // Current Quantity
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = KanakPrimaryBlue)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Current Quantity:", style = MaterialTheme.typography.bodyMedium.copy(color = KanakSecondaryText))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${prod.stockQty.stripTrailingZeros().toPlainString()} in stock",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                            )
                        }
                    }

                    // Adjustment Direction Toggle matching Board 8 Screen 3
                    Column {
                        Text("Adjustment Direction", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { isAddDirection = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isAddDirection) KanakPrimaryBlue else KanakSurfaceVariant,
                                    contentColor = if (isAddDirection) Color.White else KanakPrimaryText
                                ),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add to Stock", fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = { isAddDirection = false },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!isAddDirection) KanakError else KanakSurfaceVariant,
                                    contentColor = if (!isAddDirection) Color.White else KanakPrimaryText
                                ),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Remove from Stock", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Adjustment Quantity
                    OutlinedTextField(
                        value = qtyInputStr,
                        onValueChange = { qtyInputStr = it },
                        label = { Text("Adjustment Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("adjustment_qty_input")
                    )

                    // Resulting Quantity Card
                    val isNegative = resultingStock < BigDecimal.ZERO
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isNegative) KanakErrorBackground else KanakSuccessBackground),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isNegative) Icons.Default.Cancel else Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = if (isNegative) KanakError else KanakSuccess,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Resulting Quantity", style = MaterialTheme.typography.bodySmall.copy(color = if (isNegative) KanakError else KanakSuccess))
                                Text(
                                    text = "${resultingStock.stripTrailingZeros().toPlainString()} in stock",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isNegative) KanakError else KanakSuccess
                                    )
                                )
                            }
                        }
                    }

                    // Reason Dropdown
                    ExposedDropdownMenuBox(
                        expanded = reasonDropdownExpanded,
                        onExpandedChange = { reasonDropdownExpanded = !reasonDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedReason,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Reason for Adjustment *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasonDropdownExpanded) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = reasonDropdownExpanded,
                            onDismissRequest = { reasonDropdownExpanded = false }
                        ) {
                            reasons.forEach { r ->
                                DropdownMenuItem(
                                    text = { Text(r) },
                                    onClick = {
                                        selectedReason = r
                                        reasonDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Notes
                    OutlinedTextField(
                        value = notesStr,
                        onValueChange = { notesStr = it },
                        label = { Text("Notes (Optional)") },
                        placeholder = { Text("Received from supplier - GRN #PO-00421") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(80.dp)
                    )

                    if (errorMsg != null) {
                        Text(text = errorMsg ?: "", color = KanakError, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (isNegative) {
                                errorMsg = "Cannot adjust stock below zero"
                                return@Button
                            }
                            if (qtyInput <= BigDecimal.ZERO) {
                                errorMsg = "Adjustment quantity must be greater than zero"
                                return@Button
                            }
                            scope.launch {
                                isSubmitting = true
                                previousStockVal = prod.stockQty
                                val fullReason = if (notesStr.isNotBlank()) "$selectedReason: $notesStr" else selectedReason
                                val res = repository.adjustStock(
                                    productId = prod.productId,
                                    deltaQty = delta,
                                    reason = fullReason,
                                    createdBy = currentUserId
                                )
                                isSubmitting = false
                                res.onSuccess { adj ->
                                    confirmedAdjustment = adj
                                    currentSubView = "CONFIRMATION"
                                }.onFailure { errorMsg = it.message }
                            }
                        },
                        enabled = !isSubmitting && !isNegative && qtyInput > BigDecimal.ZERO,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("confirm_adjustment_button")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Confirm Adjustment", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        "DETAIL" -> {
            // =========================================================================
            // STOCK DETAIL (BOARD 8 SCREEN 2)
            // =========================================================================
            val prod = selectedProduct ?: return
            val relatedPurchases = remember(prod, purchases) {
                purchases.filter { it.productId == prod.productId }
            }
            val relatedAdjustments = remember(prod, stockAdjustments) {
                stockAdjustments.filter { it.productId == prod.productId }
            }

            Scaffold(
                containerColor = KanakBackground,
                topBar = {
                    KanakTopBar(
                        title = "Product Detail",
                        onBack = { currentSubView = "LIST" }
                    )
                }
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
                    // Hero Image
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            if (!prod.photoUrl.isNullOrBlank()) {
                                AsyncImage(model = prod.photoUrl, contentDescription = null, modifier = Modifier.fillMaxSize().padding(12.dp))
                            } else {
                                Icon(Icons.Default.Inventory2, contentDescription = null, tint = KanakSecondaryText, modifier = Modifier.size(54.dp))
                            }
                        }
                    }

                    Text(prod.name, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText))
                    Text("Barcode: ${prod.barcode}", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))

                    // Large Current Stock Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(12.dp), color = KanakAccentLight, modifier = Modifier.size(50.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(26.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text("Current Stock", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                Text(
                                    text = "${prod.stockQty.stripTrailingZeros().toPlainString()} in stock",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = KanakPrimaryText)
                                )
                            }
                        }
                    }

                    // Admin Adjust Button
                    if (isAdmin) {
                        Button(
                            onClick = { currentSubView = "ADJUST" },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Adjust Stock (Admin Only)", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Recent Inventory Events
                    Text("Recent Inventory Events", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

                    if (relatedPurchases.isEmpty() && relatedAdjustments.isEmpty()) {
                        Text("No recent inventory adjustments recorded for this SKU.", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            relatedPurchases.take(3).forEach { p ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                                    border = CardDefaults.outlinedCardBorder(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(shape = CircleShape, color = KanakSuccessBackground, modifier = Modifier.size(32.dp)) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = KanakSuccess, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text("Purchase Received", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                                Text(p.date.take(10), style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp))
                                            }
                                        }
                                        Text("+${p.qty.stripTrailingZeros().toPlainString()}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakSuccess))
                                    }
                                }
                            }

                            relatedAdjustments.take(3).forEach { a ->
                                val isPositive = a.deltaQty >= BigDecimal.ZERO
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                                    border = CardDefaults.outlinedCardBorder(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isPositive) KanakSuccessBackground else KanakErrorBackground,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                                        contentDescription = null,
                                                        tint = if (isPositive) KanakSuccess else KanakError,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(a.reason, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(a.date.take(10), style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp))
                                            }
                                        }
                                        Text(
                                            text = "${if (isPositive) "+" else ""}${a.deltaQty.stripTrailingZeros().toPlainString()}",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPositive) KanakSuccess else KanakError
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

        else -> {
            // =========================================================================
            // STOCK LIST (BOARD 8 SCREEN 1)
            // =========================================================================
            Scaffold(
                topBar = {
                    KanakTopBar(
                        title = "Stock",
                        actions = {
                            IconButton(onClick = onNavigateToSuppliers) {
                                Icon(Icons.Outlined.Storefront, contentDescription = "Suppliers", tint = KanakPrimaryText)
                            }
                            if (isAdmin) {
                                IconButton(onClick = onNavigateToPurchases) {
                                    Icon(Icons.Outlined.LocalShipping, contentDescription = "Purchases", tint = KanakPrimaryText)
                                }
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
                        .background(KanakBackground)
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    KanakSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = "Search products by name or barcode...",
                        onScanClick = {}
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            KanakFilterChip(
                                label = cat,
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (filteredProducts.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.Inventory,
                            title = "No Inventory Found",
                            message = "Products in your catalogue will show their live van stock quantities here."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredProducts, key = { it.productId }) { prod ->
                                val isOutOfStock = prod.stockQty <= BigDecimal.ZERO
                                val isLowStock = prod.stockQty > BigDecimal.ZERO && prod.stockQty < BigDecimal("15")

                                val badgeBg = when {
                                    isOutOfStock -> KanakErrorBackground
                                    isLowStock -> KanakWarningBackground
                                    else -> KanakSuccessBackground
                                }
                                val badgeColor = when {
                                    isOutOfStock -> KanakError
                                    isLowStock -> KanakWarning
                                    else -> KanakSuccess
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            selectedProduct = prod
                                            currentSubView = "DETAIL"
                                        }
                                        .testTag("stock_item_${prod.productId}"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    border = CardDefaults.outlinedCardBorder()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = KanakSurfaceVariant,
                                            modifier = Modifier.size(50.dp)
                                        ) {
                                            if (!prod.photoUrl.isNullOrBlank()) {
                                                AsyncImage(model = prod.photoUrl, contentDescription = null, modifier = Modifier.fillMaxSize().padding(3.dp))
                                            } else {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = KanakSecondaryText)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(prod.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText))
                                            Text(prod.barcode, style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp))
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = badgeBg,
                                            modifier = Modifier.widthIn(min = 60.dp)
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = prod.stockQty.stripTrailingZeros().toPlainString(),
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = badgeColor)
                                                )
                                                Text(
                                                    text = if (isOutOfStock) "out" else "in stock",
                                                    style = MaterialTheme.typography.labelSmall.copy(color = badgeColor, fontSize = 10.sp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
