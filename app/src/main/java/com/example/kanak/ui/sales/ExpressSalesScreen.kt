package com.example.kanak.ui.sales

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanak.barcode.BarcodeScannerManager
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.domain.financial.BahrainFinancials
import com.example.kanak.domain.models.*
import com.example.kanak.printing.BluetoothPrinterManager
import com.example.kanak.ui.components.BarcodeScanDialog
import com.example.kanak.ui.components.KanakBrandLoadingDialog
import com.example.kanak.ui.components.SyncStatusBar
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

/**
 * Express Sales Mode for Kanak Van Sales.
 * Ultra-fast workflow: Customer -> Add Products -> Keypad Quantity -> Payment -> Confirm -> Print.
 * Features adaptive 2-pane tablet support, persistent sticky totals, and numerical touch keypad.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressSalesScreen(
    repository: KanakRepository,
    currentUser: User,
    printerManager: BluetoothPrinterManager,
    onNavigateToInvoiceDetail: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scannerManager = remember { BarcodeScannerManager(context) }

    val products by repository.products.collectAsState()
    val customers by repository.customers.collectAsState()
    val vans by repository.vans.collectAsState()
    val vanStocks by repository.vanStocks.collectAsState()

    // Van context for salesperson
    val assignedVan = remember(currentUser, vans) {
        vans.find { it.vanId == currentUser.assignedVanId } ?: vans.firstOrNull()
    }
    val vanStockMap = remember(assignedVan, vanStocks) {
        if (assignedVan != null) vanStocks[assignedVan.vanId] ?: emptyMap() else emptyMap()
    }

    // Default Walk-in Customer
    val defaultCustomer = remember(customers) {
        customers.find { it.businessName.contains("Walk-in", ignoreCase = true) || it.businessName.contains("Cash", ignoreCase = true) }
            ?: customers.firstOrNull()
            ?: Customer(customerId = "cust_walkin_cash", businessName = "Walk-in Cash Customer (زبون نقدي)")
    }

    var selectedCustomer by remember { mutableStateOf(defaultCustomer) }
    var showCustomerPicker by remember { mutableStateOf(false) }

    // Draft cart lines: Map of productId -> Quantity
    var cartItems by remember { mutableStateOf<Map<String, BigDecimal>>(emptyMap()) }
    var activeProductIdForPad by remember { mutableStateOf<String?>(null) }

    var productSearchQuery by remember { mutableStateOf("") }
    var showBarcodeDialog by remember { mutableStateOf(false) }

    var selectedPaymentType by remember { mutableStateOf(Invoice.PAYMENT_CASH) }
    var autoPrintReceipt by remember { mutableStateOf(true) }

    // State guards & posting status
    var isPosting by remember { mutableStateOf(false) }
    var postErrorMessage by remember { mutableStateOf<String?>(null) }
    var postedInvoice by remember { mutableStateOf<Invoice?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    // Calculate line items with Bahrain Financials
    val lineCalculations = remember(cartItems, products) {
        cartItems.mapNotNull { (prodId, qty) ->
            val prod = products.find { it.productId == prodId } ?: return@mapNotNull null
            BahrainFinancials.calculateLine(qty, prod.sellingPrice, prod.vatRate)
        }
    }

    val totals = remember(lineCalculations) {
        BahrainFinancials.calculateInvoiceTotals(lineCalculations)
    }

    val filteredProducts = remember(productSearchQuery, products) {
        if (productSearchQuery.isBlank()) {
            products.take(30)
        } else {
            val q = productSearchQuery.trim().lowercase()
            products.filter {
                it.name.lowercase().contains(q) ||
                it.barcode.lowercase().contains(q) ||
                it.productId.lowercase().contains(q)
            }
        }
    }

    fun addOrIncrementProduct(product: Product, isIncrement: Boolean = false) {
        scannerManager.triggerScanHaptic()
        val currentQty = cartItems[product.productId] ?: BigDecimal.ZERO
        val availableStock = vanStockMap[product.productId] ?: product.stockQty
        val nextQty = if (isIncrement || currentQty > BigDecimal.ZERO) currentQty.add(BigDecimal.ONE) else BigDecimal.ONE

        if (nextQty > availableStock) {
            postErrorMessage = "Warning: Only $availableStock available in van stock."
        } else {
            postErrorMessage = null
        }
        cartItems = cartItems + (product.productId to nextQty)
        activeProductIdForPad = product.productId
    }

    // Top Bar & Scaffold
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "EXPRESS SALES",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp,
                                    color = KanakPrimaryText
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = KanakPrimaryBlue) {
                                Text(
                                    text = assignedVan?.vanCode ?: "UNASSIGNED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Customer: ${selectedCustomer.businessName}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = KanakSecondaryText,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Quick Customer Change
                    IconButton(
                        onClick = { showCustomerPicker = true },
                        modifier = Modifier.testTag("express_select_customer_button")
                    ) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = "Select Customer", tint = KanakPrimaryBlue)
                    }

                    // Barcode Scanner Action
                    IconButton(
                        onClick = { showBarcodeDialog = true },
                        modifier = Modifier.testTag("express_barcode_scan_button")
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode", tint = KanakPrimaryBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KanakBackground)
            )
        },
        bottomBar = {
            // Sticky Bottom Summary & Instant Confirmation Action
            Surface(
                color = KanakSurface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, KanakBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("express_sticky_bottom_bar")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Payment Selector Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            Invoice.PAYMENT_CASH to "CASH (نقد)",
                            "BENEFITPAY" to "BENEFIT (بنفت)",
                            "CARD" to "CARD (بطاقة)",
                            Invoice.PAYMENT_CREDIT to "CREDIT (آجل)"
                        ).forEach { (type, label) ->
                            val isSelected = selectedPaymentType == type
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) KanakPrimaryBlue else KanakBackground,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) KanakPrimaryBlue else KanakBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedPaymentType = type }
                                    .testTag("express_pay_$type")
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isSelected) Color.White else KanakPrimaryText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Totals Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${cartItems.size} Item(s) • VAT 10%: ${BahrainFinancials.formatBhd(totals.vatTotal)}",
                                style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp)
                            )
                            Text(
                                text = BahrainFinancials.formatBhd(totals.total),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    color = KanakPrimaryBlue,
                                    fontSize = 22.sp
                                )
                            )
                        }

                        // Auto-print receipt checkbox toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { autoPrintReceipt = !autoPrintReceipt }
                        ) {
                            Checkbox(
                                checked = autoPrintReceipt,
                                onCheckedChange = { autoPrintReceipt = it },
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Print Receipt", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp))
                        }

                        // Confirm Sale Button with Double-Submit Protection
                        Button(
                            onClick = {
                                if (cartItems.isEmpty()) {
                                    postErrorMessage = "Add at least one product to the sale."
                                    return@Button
                                }
                                if (isPosting) return@Button

                                isPosting = true
                                postErrorMessage = null

                                scope.launch {
                                    val proposedLines = cartItems.mapNotNull { (prodId, qty) ->
                                        if (qty > BigDecimal.ZERO) KanakRepository.ProposedLine(prodId, qty) else null
                                    }

                                    val preAssignedId = UUID.randomUUID().toString()
                                    val result = repository.createInvoice(
                                        customerId = selectedCustomer.customerId,
                                        proposedLines = proposedLines,
                                        createdBy = currentUser.displayName,
                                        preAssignedInvoiceId = preAssignedId,
                                        paymentType = selectedPaymentType,
                                        vanId = assignedVan?.vanId ?: "",
                                        vanCode = assignedVan?.vanCode ?: ""
                                    )

                                    result.onSuccess { inv ->
                                        postedInvoice = inv
                                        showSuccessDialog = true
                                        // Auto-print receipt if enabled
                                        if (autoPrintReceipt) {
                                            printerManager.printInvoice(inv, repository.getBusinessName(), repository.getVatNumber())
                                        }
                                    }.onFailure { err ->
                                        postErrorMessage = err.message ?: "Failed to post invoice."
                                    }
                                    isPosting = false
                                }
                            },
                            enabled = cartItems.isNotEmpty() && !isPosting,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("express_confirm_sale_button")
                        ) {
                            if (isPosting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("CONFIRM SALE", fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        containerColor = KanakBackground,
        modifier = modifier
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isTablet = maxWidth >= 600.dp

            Column(modifier = Modifier.fillMaxSize()) {
                // Always-visible Live Connection and Sync Bar
                SyncStatusBar(repository = repository)

                // Error feedback banner
                if (postErrorMessage != null) {
                    Surface(
                        color = KanakErrorBackground,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = KanakError, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = postErrorMessage ?: "", style = MaterialTheme.typography.bodySmall.copy(color = KanakError))
                        }
                    }
                }

                if (isTablet) {
                    // Two-Pane Adaptive Tablet Layout
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Left Pane: Catalog & Barcode
                        Column(modifier = Modifier.weight(0.55f)) {
                            ProductCatalogSection(
                                searchQuery = productSearchQuery,
                                onSearchQueryChange = { productSearchQuery = it },
                                products = filteredProducts,
                                cartItems = cartItems,
                                vanStockMap = vanStockMap,
                                onOpenScanner = { showBarcodeDialog = true },
                                onSelectProduct = { prod -> addOrIncrementProduct(prod, isIncrement = false) }
                            )
                        }

                        // Right Pane: Active Bill & Rapid Keypad
                        Column(modifier = Modifier.weight(0.45f)) {
                            CartBillAndKeypadSection(
                                products = products,
                                cartItems = cartItems,
                                activeProductId = activeProductIdForPad,
                                onSetActiveProduct = { activeProductIdForPad = it },
                                onUpdateQty = { prodId, newQty ->
                                    if (newQty <= BigDecimal.ZERO) {
                                        cartItems = cartItems - prodId
                                        if (activeProductIdForPad == prodId) activeProductIdForPad = null
                                    } else {
                                        cartItems = cartItems + (prodId to newQty)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    // Phone Layout: Product Catalog with Quick-Cart Header
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        // Quick Cart Overview Pill
                        if (cartItems.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = KanakAccentLight,
                                border = androidx.compose.foundation.BorderStroke(1.dp, KanakPrimaryBlue.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${cartItems.size} items in cart",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryBlue)
                                        )
                                    }
                                    Text(
                                        text = "Tap any item below to edit qty",
                                        style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp)
                                    )
                                }
                            }
                        }

                        ProductCatalogSection(
                            searchQuery = productSearchQuery,
                            onSearchQueryChange = { productSearchQuery = it },
                            products = filteredProducts,
                            cartItems = cartItems,
                            vanStockMap = vanStockMap,
                            onOpenScanner = { showBarcodeDialog = true },
                            onSelectProduct = { prod -> addOrIncrementProduct(prod, isIncrement = true) }
                        )
                    }
                }
            }
        }
    }

    // Customer Picker Dialog
    if (showCustomerPicker) {
        CustomerPickerDialog(
            customers = customers,
            currentSelected = selectedCustomer,
            onSelect = {
                selectedCustomer = it
                showCustomerPicker = false
            },
            onDismiss = { showCustomerPicker = false }
        )
    }

    // Barcode Scanner Modal
    if (showBarcodeDialog) {
        BarcodeScanDialog(
            products = products,
            vanStockMap = vanStockMap,
            existingProductIdsInInvoice = cartItems.keys,
            onProductSelected = { prod, isDuplicate ->
                addOrIncrementProduct(prod, isIncrement = isDuplicate)
            },
            onDismiss = { showBarcodeDialog = false }
        )
    }

    // Success & Print Completed Dialog
    if (showSuccessDialog && postedInvoice != null) {
        val inv = postedInvoice!!
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                cartItems = emptyMap()
                activeProductIdForPad = null
            },
            icon = {
                Surface(shape = CircleShape, color = KanakSuccessBackground, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = KanakSuccess, modifier = Modifier.size(28.dp))
                    }
                }
            },
            title = {
                Text(
                    text = "Invoice Posted Successfully!",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Invoice: ${inv.invoiceNumber}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryBlue)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Total: ${BahrainFinancials.formatBhd(inv.total)} • Status: ${inv.syncStatus}",
                        style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Stock atomically deducted from ${assignedVan?.vanCode ?: "van"}. Receipt sent to thermal printer.",
                        style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp),
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        cartItems = emptyMap()
                        activeProductIdForPad = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue),
                    modifier = Modifier.testTag("express_next_sale_button")
                ) {
                    Text("START NEXT SALE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            printerManager.printInvoice(inv, repository.getBusinessName(), repository.getVatNumber())
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("REPRINT")
                }
            }
        )
    }

    if (isPosting) {
        KanakBrandLoadingDialog(
            message = "Posting Bahrain Tax Invoice...",
            subMessage = "Recording Stock Movement & Preparing Thermal Print"
        )
    }
}

@Composable
private fun ProductCatalogSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    products: List<Product>,
    cartItems: Map<String, BigDecimal>,
    vanStockMap: Map<String, BigDecimal>,
    onOpenScanner: () -> Unit,
    onSelectProduct: (Product) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search Input with integrated Scan Button
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .testTag("express_product_search_input"),
            shape = RoundedCornerShape(12.dp),
            placeholder = { Text("Search product name, barcode, or SKU...", fontSize = 12.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = KanakSecondaryText)
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = KanakSecondaryText)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = KanakAccentLight,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenScanner)
                            .testTag("express_search_scan_icon")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Scan", tint = KanakPrimaryBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SCAN", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryBlue, fontSize = 10.sp))
                        }
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = KanakPrimaryBlue,
                unfocusedBorderColor = KanakBorder
            )
        )

        // Products List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(products, key = { it.productId }) { prod ->
                val currentQtyInCart = cartItems[prod.productId] ?: BigDecimal.ZERO
                val vanStock = vanStockMap[prod.productId] ?: prod.stockQty
                val isOutOfStock = vanStock <= BigDecimal.ZERO

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (currentQtyInCart > BigDecimal.ZERO) KanakAccentLight else KanakSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (currentQtyInCart > BigDecimal.ZERO) KanakPrimaryBlue else KanakBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !isOutOfStock) { onSelectProduct(prod) }
                        .testTag("express_prod_${prod.productId}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = prod.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOutOfStock) KanakSecondaryText else KanakPrimaryText
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "BC: ${prod.barcode.ifBlank { "N/A" }}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isOutOfStock) KanakErrorBackground else KanakBackground
                                ) {
                                    Text(
                                        text = if (isOutOfStock) "NO VAN STOCK" else "Stock: $vanStock",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isOutOfStock) KanakError else KanakSecondaryText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = BahrainFinancials.formatBhd(prod.sellingPrice),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = KanakPrimaryBlue
                                )
                            )
                            if (currentQtyInCart > BigDecimal.ZERO) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Surface(shape = CircleShape, color = KanakPrimaryBlue) {
                                    Text(
                                        text = "Qty: $currentQtyInCart",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

@Composable
private fun CartBillAndKeypadSection(
    products: List<Product>,
    cartItems: Map<String, BigDecimal>,
    activeProductId: String?,
    onSetActiveProduct: (String) -> Unit,
    onUpdateQty: (String, BigDecimal) -> Unit
) {
    val activeProduct = remember(activeProductId, products) {
        products.find { it.productId == activeProductId }
    }
    val currentQty = activeProductId?.let { cartItems[it] } ?: BigDecimal.ZERO

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KanakSurface, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "ACTIVE INVOICE BILL",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Black,
                color = KanakSecondaryText,
                letterSpacing = 0.5.sp
            )
        )
        Spacer(modifier = Modifier.height(6.dp))

        // Selected Product Header for Keypad
        if (activeProduct != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = KanakAccentLight,
                border = androidx.compose.foundation.BorderStroke(1.dp, KanakPrimaryBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeProduct.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                        Text(
                            text = "${BahrainFinancials.formatBhd(activeProduct.sellingPrice)} each",
                            style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp)
                        )
                    }
                    Text(
                        text = "QTY: $currentQty",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = KanakPrimaryBlue)
                    )
                }
            }
        } else {
            Text(
                text = "Select an item to adjust with numerical keypad",
                style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Numerical Touch Keypad optimized for van sales
        NumericalTouchKeypad(
            currentQty = currentQty,
            onQtyChange = { next ->
                if (activeProductId != null) {
                    onUpdateQty(activeProductId, next)
                }
            }
        )
    }
}

@Composable
private fun NumericalTouchKeypad(
    currentQty: BigDecimal,
    onQtyChange: (BigDecimal) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Quick Carton / Case Increment row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(1 to "+1", 5 to "+5", 10 to "+10", 24 to "+24 (Box)").forEach { (delta, label) ->
                Button(
                    onClick = { onQtyChange(currentQty.add(BigDecimal(delta))) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KanakAccentLight, contentColor = KanakPrimaryBlue),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                ) {
                    Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        // Standard Digits Grid
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("CLR", "0", "DEL")
        )

        rows.forEach { rowDigits ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowDigits.forEach { digit ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = when (digit) {
                            "CLR" -> KanakErrorBackground
                            "DEL" -> KanakBackground
                            else -> KanakBackground
                        },
                        border = androidx.compose.foundation.BorderStroke(1.dp, KanakBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                when (digit) {
                                    "CLR" -> onQtyChange(BigDecimal.ZERO)
                                    "DEL" -> {
                                        val str = currentQty.toPlainString()
                                        if (str.length > 1) {
                                            val truncated = str.dropLast(1)
                                            onQtyChange(BigDecimal(truncated))
                                        } else {
                                            onQtyChange(BigDecimal.ZERO)
                                        }
                                    }
                                    else -> {
                                        val str = if (currentQty == BigDecimal.ZERO) digit else "${currentQty.toPlainString()}$digit"
                                        try {
                                            onQtyChange(BigDecimal(str))
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                            .testTag("keypad_btn_$digit")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = digit,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = when (digit) {
                                        "CLR" -> KanakError
                                        "DEL" -> KanakSecondaryText
                                        else -> KanakPrimaryText
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerPickerDialog(
    customers: List<Customer>,
    currentSelected: Customer,
    onSelect: (Customer) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(search, customers) {
        if (search.isBlank()) customers else {
            val q = search.trim().lowercase()
            customers.filter { it.businessName.lowercase().contains(q) || it.contact.lowercase().contains(q) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Customer Account", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search customer or shop name...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered) { cust ->
                        val isSelected = cust.customerId == currentSelected.customerId
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) KanakAccentLight else KanakBackground,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) KanakPrimaryBlue else KanakBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelect(cust) }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(cust.businessName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (cust.address.isNotBlank()) {
                                    Text(cust.address, fontSize = 11.sp, color = KanakSecondaryText)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE") }
        }
    )
}
