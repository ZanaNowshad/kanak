package com.example.kanak.ui.purchases

import androidx.compose.foundation.background
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
import com.example.kanak.domain.models.Purchase
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
fun PurchasesScreen(
    repository: KanakRepository,
    currentUserId: String,
    isAdmin: Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val purchases by repository.purchases.collectAsState()
    val suppliers by repository.suppliers.collectAsState()
    val products by repository.products.collectAsState()
    val scope = rememberCoroutineScope()

    // Sub-view: "LIST", "NEW_PURCHASE", "CORRECT_PURCHASE", "REVERSE_PURCHASE"
    var currentSubView by remember { mutableStateOf("LIST") }
    var selectedPurchase by remember { mutableStateOf<Purchase?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    // =========================================================================
    // PERMISSION DENIED (BOARD 7 SCREEN 5)
    // =========================================================================
    if (!isAdmin) {
        Scaffold(
            topBar = {
                KanakTopBar(
                    title = "Purchases",
                    onBack = onNavigateBack
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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Shield with lock icon inside container with soft warning tone
                Surface(
                    shape = CircleShape,
                    color = KanakAccentLight,
                    modifier = Modifier.size(96.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = KanakPrimaryBlue,
                            modifier = Modifier.size(48.dp)
                        )
                        Surface(
                            shape = CircleShape,
                            color = KanakError,
                            modifier = Modifier
                                .size(28.dp)
                                .align(Alignment.BottomEnd)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Admin access required",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = KanakPrimaryText
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "You don't have permission to access purchase management. Purchase management is only available for administrators.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = KanakSecondaryText,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onNavigateBack,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KanakPrimaryBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Go to Home", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    when (currentSubView) {
        "NEW_PURCHASE" -> {
            // =========================================================================
            // NEW PURCHASE SCREEN (BOARD 7 SCREEN 2)
            // =========================================================================
            var selectedSupplierId by remember { mutableStateOf(suppliers.firstOrNull()?.supplierId ?: "") }
            var selectedProductId by remember { mutableStateOf(products.firstOrNull()?.productId ?: "") }
            var qtyStr by remember { mutableStateOf("50") }
            var costStr by remember { mutableStateOf("") }
            var isSubmitting by remember { mutableStateOf(false) }
            var errorMsg by remember { mutableStateOf<String?>(null) }
            var supplierExpanded by remember { mutableStateOf(false) }
            var productExpanded by remember { mutableStateOf(false) }

            val selectedProduct = products.find { it.productId == selectedProductId }
            val selectedSupplier = suppliers.find { it.supplierId == selectedSupplierId }

            LaunchedEffect(selectedProduct) {
                if (selectedProduct != null && costStr.isBlank()) {
                    costStr = selectedProduct.costPrice.toPlainString()
                }
            }

            val todayDate = remember {
                SimpleDateFormat("d MMM yyyy", Locale.US).format(Date())
            }

            Scaffold(
                topBar = {
                    KanakTopBar(
                        title = "New Purchase",
                        onBack = { currentSubView = "LIST" }
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
                    // Supplier Dropdown
                    ExposedDropdownMenuBox(
                        expanded = supplierExpanded,
                        onExpandedChange = { supplierExpanded = !supplierExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedSupplier?.name ?: "Select Supplier",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Supplier *") },
                            leadingIcon = { Icon(Icons.Outlined.LocalShipping, contentDescription = null, tint = KanakSecondaryText) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = supplierExpanded) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = supplierExpanded,
                            onDismissRequest = { supplierExpanded = false }
                        ) {
                            suppliers.forEach { sup ->
                                DropdownMenuItem(
                                    text = { Text(sup.name) },
                                    onClick = {
                                        selectedSupplierId = sup.supplierId
                                        supplierExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Product Dropdown
                    ExposedDropdownMenuBox(
                        expanded = productExpanded,
                        onExpandedChange = { productExpanded = !productExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedProduct?.name ?: "Select Product",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Product *") },
                            leadingIcon = { Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = KanakSecondaryText) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = productExpanded) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = productExpanded,
                            onDismissRequest = { productExpanded = false }
                        ) {
                            products.forEach { prod ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(prod.name, fontWeight = FontWeight.SemiBold)
                                            Text("Barcode: ${prod.barcode}", fontSize = 11.sp, color = KanakSecondaryText)
                                        }
                                    },
                                    onClick = {
                                        selectedProductId = prod.productId
                                        costStr = prod.costPrice.toPlainString()
                                        productExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Quantity Stepper / Input
                    OutlinedTextField(
                        value = qtyStr,
                        onValueChange = { qtyStr = it },
                        label = { Text("Quantity *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("purchase_qty_input")
                    )

                    // Unit Cost (BHD)
                    OutlinedTextField(
                        value = costStr,
                        onValueChange = { costStr = it },
                        label = { Text("Unit Cost (BHD) *") },
                        leadingIcon = { Icon(Icons.Outlined.MonetizationOn, contentDescription = null, tint = KanakSecondaryText) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Purchase Date Read-only
                    OutlinedTextField(
                        value = todayDate,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Purchase Date *") },
                        leadingIcon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = KanakSecondaryText) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Info Card matching Board 7 Screen 2
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakAccentLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "This will add stock to inventory",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                                )
                                Text(
                                    text = "${qtyStr.ifBlank { "0" }} units will be added to current stock.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                                )
                            }
                        }
                    }

                    if (errorMsg != null) {
                        Text(text = errorMsg ?: "", color = KanakError, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val qty = BahrainFinancials.parseBhd(qtyStr)
                            val cost = BahrainFinancials.parseBhd(costStr)
                            if (qty <= BigDecimal.ZERO) {
                                errorMsg = "Quantity must be greater than zero"
                                return@Button
                            }
                            scope.launch {
                                isSubmitting = true
                                val res = repository.createPurchase(
                                    supplierId = selectedSupplierId,
                                    productId = selectedProductId,
                                    qty = qty,
                                    cost = cost,
                                    createdBy = currentUserId
                                )
                                isSubmitting = false
                                res.onSuccess {
                                    currentSubView = "LIST"
                                }.onFailure { errorMsg = it.message }
                            }
                        },
                        enabled = !isSubmitting && selectedSupplierId.isNotBlank() && selectedProductId.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_purchase_button")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Purchase", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        "CORRECT_PURCHASE" -> {
            // =========================================================================
            // CORRECT PURCHASE SCREEN (BOARD 7 SCREEN 3)
            // =========================================================================
            val p = selectedPurchase ?: return
            var newQtyStr by remember { mutableStateOf(p.qty.stripTrailingZeros().toPlainString()) }
            var correctionReason by remember { mutableStateOf("") }
            var isCorrecting by remember { mutableStateOf(false) }
            var correctError by remember { mutableStateOf<String?>(null) }

            val newQty = BahrainFinancials.parseBhd(newQtyStr)
            val delta = newQty.subtract(p.qty)

            Scaffold(
                topBar = {
                    KanakTopBar(
                        title = "Correct Purchase",
                        onBack = { currentSubView = "LIST" }
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
                    // Original Purchase Details Card
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Original Purchase", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                }
                                Surface(shape = RoundedCornerShape(6.dp), color = KanakSuccessBackground) {
                                    Text("Received", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = KanakSuccess), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(p.productName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("Supplier: ${p.supplierName}", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                            Text("Unit Cost: ${BahrainFinancials.formatBhd(p.cost)}", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                        }
                    }

                    // Original Quantity -> New Quantity Stepper
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Original Quantity", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                Text("${p.qty.stripTrailingZeros().toPlainString()} units", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                            }
                            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = KanakSecondaryText)
                            Column(horizontalAlignment = Alignment.End) {
                                Text("New Quantity", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                OutlinedTextField(
                                    value = newQtyStr,
                                    onValueChange = { newQtyStr = it },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(100.dp).height(52.dp),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }

                    // Inventory Change Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (delta < BigDecimal.ZERO) KanakErrorBackground else KanakSuccessBackground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (delta < BigDecimal.ZERO) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = if (delta < BigDecimal.ZERO) KanakError else KanakSuccess,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Inventory Change: ${if (delta >= BigDecimal.ZERO) "+" else ""}${delta.stripTrailingZeros().toPlainString()} units",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (delta < BigDecimal.ZERO) KanakError else KanakSuccess
                                    )
                                )
                                Text(
                                    text = "Stock will be adjusted by ${delta.abs().stripTrailingZeros().toPlainString()} units.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = KanakPrimaryText)
                                )
                            }
                        }
                    }

                    // Reason for Correction
                    OutlinedTextField(
                        value = correctionReason,
                        onValueChange = { correctionReason = it },
                        label = { Text("Reason for Correction *") },
                        placeholder = { Text("e.g. Received only 7 units instead of 10 as per invoice") },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (correctError != null) {
                        Text(text = correctError ?: "", color = KanakError, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (newQty <= BigDecimal.ZERO) {
                                correctError = "New quantity must be greater than zero"
                                return@Button
                            }
                            scope.launch {
                                isCorrecting = true
                                val res = repository.correctPurchase(
                                    purchaseId = p.purchaseId,
                                    newQty = newQty,
                                    newCost = p.cost,
                                    adminUserId = currentUserId
                                )
                                isCorrecting = false
                                res.onSuccess {
                                    currentSubView = "LIST"
                                }.onFailure { correctError = it.message }
                            }
                        },
                        enabled = !isCorrecting && newQty > BigDecimal.ZERO,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apply Correction", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        "REVERSE_PURCHASE" -> {
            // =========================================================================
            // REVERSE PURCHASE SCREEN (BOARD 7 SCREEN 4)
            // =========================================================================
            val p = selectedPurchase ?: return
            val prod = products.find { it.productId == p.productId }
            var isReversing by remember { mutableStateOf(false) }
            var revError by remember { mutableStateOf<String?>(null) }

            Scaffold(
                topBar = {
                    KanakTopBar(
                        title = "Reverse Purchase",
                        onBack = { currentSubView = "LIST" }
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
                    // Orange Warning Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakWarningBackground),
                        border = androidx.compose.foundation.BorderStroke(1.dp, KanakWarning.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.Warning, contentDescription = null, tint = KanakWarning, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Reverse Purchase",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakWarning)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "This will completely remove the stock contribution of this purchase from inventory. A compensating adjustment will be logged.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = KanakPrimaryText)
                                )
                            }
                        }
                    }

                    // Purchase Details Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Purchase Details", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Product: ${p.productName}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("Supplier: ${p.supplierName}", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                            Text("Unit Cost: ${BahrainFinancials.formatBhd(p.cost)}", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                        }
                    }

                    // Stock Impact Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = KanakSurface),
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Quantity to Reverse", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                Text("${p.qty.stripTrailingZeros().toPlainString()} units", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = KanakErrorBackground),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Stock Effect", style = MaterialTheme.typography.bodySmall.copy(color = KanakError))
                                Text("-${p.qty.stripTrailingZeros().toPlainString()} units", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = KanakError))
                            }
                        }
                    }

                    // Stock Available Indicator
                    val currentStock = prod?.stockQty ?: BigDecimal.ZERO
                    val hasSufficientStock = currentStock >= p.qty

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = if (hasSufficientStock) KanakSuccessBackground else KanakErrorBackground),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasSufficientStock) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (hasSufficientStock) KanakSuccess else KanakError,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Current stock: ${currentStock.stripTrailingZeros().toPlainString()} units. ${if (hasSufficientStock) "Sufficient stock to reverse." else "Insufficient stock!"}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (hasSufficientStock) KanakSuccess else KanakError
                                )
                            )
                        }
                    }

                    if (revError != null) {
                        Text(text = revError ?: "", color = KanakError, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { currentSubView = "LIST" },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    isReversing = true
                                    val res = repository.reversePurchase(p.purchaseId, currentUserId)
                                    isReversing = false
                                    res.onSuccess {
                                        currentSubView = "LIST"
                                    }.onFailure { revError = it.message }
                                }
                            },
                            enabled = !isReversing && hasSufficientStock,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KanakError, contentColor = Color.White),
                            modifier = Modifier.weight(1.3f).height(50.dp).testTag("confirm_reversal_button")
                        ) {
                            if (isReversing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                            } else {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Confirm Reversal", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        else -> {
            // =========================================================================
            // PURCHASES LIST (BOARD 7 SCREEN 1)
            // =========================================================================
            val filterOptions = listOf("All", "Today", "This Week", "This Month")

            val filteredPurchases = remember(purchases, searchQuery) {
                if (searchQuery.isBlank()) {
                    purchases
                } else {
                    val q = searchQuery.trim().lowercase()
                    purchases.filter {
                        it.productName.lowercase().contains(q) || it.supplierName.lowercase().contains(q)
                    }
                }
            }

            Scaffold(
                topBar = {
                    KanakTopBar(
                        title = "Purchases",
                        onBack = onNavigateBack
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { currentSubView = "NEW_PURCHASE" },
                        shape = CircleShape,
                        containerColor = KanakPrimaryBlue,
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(4.dp),
                        modifier = Modifier.testTag("record_purchase_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Purchase", modifier = Modifier.size(28.dp))
                    }
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
                        placeholder = "Search by supplier, product or purchase..."
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filterOptions.forEach { opt ->
                            KanakFilterChip(
                                label = opt,
                                selected = selectedFilter == opt,
                                onClick = { selectedFilter = opt }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (filteredPurchases.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.ShoppingCart,
                            title = "No Purchases Recorded",
                            message = "Recording purchases increases warehouse/van product inventory atomically."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredPurchases, key = { it.purchaseId }) { p ->
                                val prod = products.find { it.productId == p.productId }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            if (!p.isReversed) {
                                                selectedPurchase = p
                                                currentSubView = "CORRECT_PURCHASE"
                                            }
                                        }
                                        .testTag("purchase_item_${p.purchaseId}"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    border = CardDefaults.outlinedCardBorder()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = p.supplierName.ifBlank { "Supplier" },
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                                )
                                                Text(
                                                    text = p.date.take(10),
                                                    style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp)
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (p.isReversed) KanakErrorBackground else KanakSuccessBackground
                                            ) {
                                                Text(
                                                    text = if (p.isReversed) "Reversed" else "Received",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (p.isReversed) KanakError else KanakSuccess
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = KanakSurfaceVariant,
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                if (prod?.photoUrl?.isNotBlank() == true) {
                                                    AsyncImage(
                                                        model = prod.photoUrl,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Fit,
                                                        modifier = Modifier.fillMaxSize().padding(3.dp)
                                                    )
                                                } else {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = KanakSecondaryText, modifier = Modifier.size(22.dp))
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = p.productName.ifBlank { prod?.name ?: "Product" },
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${p.qty.stripTrailingZeros().toPlainString()} units",
                                                    style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                                                )
                                            }

                                            Text(
                                                text = BahrainFinancials.formatBhd(p.cost.multiply(p.qty)),
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                                            )
                                        }

                                        if (!p.isReversed) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                TextButton(
                                                    onClick = {
                                                        selectedPurchase = p
                                                        currentSubView = "REVERSE_PURCHASE"
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                                ) {
                                                    Text("Reverse", color = KanakError, fontSize = 12.sp)
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                                TextButton(
                                                    onClick = {
                                                        selectedPurchase = p
                                                        currentSubView = "CORRECT_PURCHASE"
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                                ) {
                                                    Text("Correct", color = KanakPrimaryBlue, fontSize = 12.sp)
                                                }
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
