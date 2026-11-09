package com.example.kanak.ui.invoices

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.domain.financial.BahrainFinancials
import com.example.kanak.domain.models.Customer
import com.example.kanak.domain.models.Invoice
import com.example.kanak.domain.models.Product
import com.example.kanak.ui.components.KanakBrandLoadingDialog
import com.example.kanak.ui.components.KanakSearchField
import com.example.kanak.ui.components.KanakTopBar
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import com.example.kanak.domain.models.User
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceComposerScreen(
    repository: KanakRepository,
    currentUserId: String = "driver",
    currentUser: User? = null,
    onNavigateToInvoiceDetail: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val allCustomers by repository.customers.collectAsState()
    val allProducts by repository.products.collectAsState()

    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var isCreatingCustomer by remember { mutableStateOf(false) }
    var isSavingCustomer by remember { mutableStateOf(false) }
    var customerFormName by remember { mutableStateOf("") }
    var customerFormPhone by remember { mutableStateOf("") }
    var customerFormAddress by remember { mutableStateOf("") }
    var customerFormError by remember { mutableStateOf<String?>(null) }

    // Map of productId -> Quantity
    var cartItems by remember { mutableStateOf<Map<String, BigDecimal>>(emptyMap()) }

    var productSearchQuery by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedPaymentType by remember { mutableStateOf(Invoice.PAYMENT_CASH) }
    var showBarcodeScanDialog by remember { mutableStateOf(false) }
    var autoPrintReceipt by remember { mutableStateOf(true) }

    // Calculate live totals
    val calculatedLines = remember(cartItems, allProducts) {
        cartItems.mapNotNull { (prodId, qty) ->
            val product = allProducts.find { it.productId == prodId }
            if (product != null && qty > BigDecimal.ZERO) {
                BahrainFinancials.calculateLine(
                    qty = qty,
                    unitPrice = product.sellingPrice,
                    vatRate = product.vatRate
                ) to product
            } else null
        }
    }

    val totals = remember(calculatedLines) {
        BahrainFinancials.calculateInvoiceTotals(calculatedLines.map { it.first })
    }

    val availableProductsToAdd = remember(allProducts, productSearchQuery) {
        if (productSearchQuery.isBlank()) {
            allProducts.filter { it.stockQty > BigDecimal.ZERO }
        } else {
            val q = productSearchQuery.trim().lowercase()
            allProducts.filter {
                (it.name.lowercase().contains(q) || it.barcode.lowercase().contains(q)) && it.stockQty > BigDecimal.ZERO
            }
        }
    }

    Scaffold(
        topBar = {
            KanakTopBar(
                title = "New Invoice",
                onBack = onNavigateBack
            )
        },
        bottomBar = {
            // Summary Card & Primary Button matching Board 1 Screen 4
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = KanakSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Subtotal",
                            style = MaterialTheme.typography.bodyMedium.copy(color = KanakSecondaryText)
                        )
                        Text(
                            text = BahrainFinancials.formatBhd(totals.subtotal),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = KanakPrimaryText
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "VAT (10%)",
                                style = MaterialTheme.typography.bodyMedium.copy(color = KanakSecondaryText)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = KanakSecondaryText,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = BahrainFinancials.formatBhd(totals.vatTotal),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = KanakPrimaryText
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = KanakPrimaryText
                            )
                        )
                        Text(
                            text = BahrainFinancials.formatBhd(totals.total),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = KanakPrimaryText
                            ),
                            modifier = Modifier.testTag("composer_grand_total_text")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Payment Type Selector (Cash Sale vs Customer Credit)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedPaymentType == Invoice.PAYMENT_CASH) KanakPrimaryBlue else KanakSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedPaymentType = Invoice.PAYMENT_CASH }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Payments,
                                    contentDescription = null,
                                    tint = if (selectedPaymentType == Invoice.PAYMENT_CASH) Color.White else KanakPrimaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Cash (Paid)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedPaymentType == Invoice.PAYMENT_CASH) Color.White else KanakPrimaryText
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedPaymentType == Invoice.PAYMENT_CREDIT) Color(0xFFD97706) else KanakSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedPaymentType = Invoice.PAYMENT_CREDIT }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CreditCard,
                                    contentDescription = null,
                                    tint = if (selectedPaymentType == Invoice.PAYMENT_CREDIT) Color.White else KanakPrimaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Credit (Pay Later)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedPaymentType == Invoice.PAYMENT_CREDIT) Color.White else KanakPrimaryText
                                )
                            }
                        }
                    }

                    if (selectedPaymentType == Invoice.PAYMENT_CREDIT) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = KanakWarningBackground,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = KanakWarning,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Items will be given on credit and tracked in customer balance.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = KanakWarning,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(color = KanakError)
                        )
                    }

                    val context = androidx.compose.ui.platform.LocalContext.current

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { autoPrintReceipt = !autoPrintReceipt }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = autoPrintReceipt,
                            onCheckedChange = { autoPrintReceipt = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            tint = KanakPrimaryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Auto-Print ESC/POS Receipt on Post",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = KanakPrimaryText
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Big Primary Save Invoice Button
                    Button(
                        onClick = {
                            val cust = selectedCustomer
                            if (cust == null) {
                                errorMessage = "Please select a customer first"
                                return@Button
                            }
                            if (cartItems.isEmpty() || cartItems.values.all { it <= BigDecimal.ZERO }) {
                                errorMessage = "Please add at least one product"
                                return@Button
                            }

                            scope.launch {
                                isSaving = true
                                errorMessage = null
                                val proposed = cartItems.mapNotNull { (prodId, qty) ->
                                    if (qty > BigDecimal.ZERO) KanakRepository.ProposedLine(prodId, qty) else null
                                }

                                val effectiveUserId = currentUser?.displayName?.ifBlank { currentUserId } ?: currentUserId
                                val effectiveVanId = currentUser?.assignedVanId?.ifBlank { "" } ?: ""
                                val effectiveVanCode = currentUser?.assignedVanCode?.ifBlank { "" } ?: ""

                                val result = repository.createInvoice(
                                    customerId = cust.customerId,
                                    proposedLines = proposed,
                                    createdBy = effectiveUserId,
                                    paymentType = selectedPaymentType,
                                    vanId = effectiveVanId,
                                    vanCode = effectiveVanCode
                                )

                                isSaving = false
                                result.onSuccess { committedInvoice ->
                                    if (autoPrintReceipt) {
                                        try {
                                            com.example.kanak.printing.KanakAndroidPrintHelper.printInvoiceReceipt(
                                                context = context,
                                                invoice = committedInvoice,
                                                businessName = repository.getBusinessName(),
                                                vatNumber = repository.getVatNumber(),
                                                crNumber = "123456-1"
                                            )
                                        } catch (_: Exception) {}
                                    }
                                    onNavigateToInvoiceDetail(committedInvoice.invoiceId)
                                }.onFailure { err ->
                                    errorMessage = err.message ?: "Failed to post invoice"
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_invoice_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedPaymentType == Invoice.PAYMENT_CREDIT) Color(0xFFD97706) else KanakPrimaryBlue,
                            contentColor = Color.White
                        ),
                        enabled = !isSaving && selectedCustomer != null && cartItems.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = if (selectedPaymentType == Invoice.PAYMENT_CREDIT) Icons.Outlined.CreditCard else Icons.Outlined.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (selectedPaymentType == Invoice.PAYMENT_CREDIT) "Post Credit Invoice (${BahrainFinancials.formatBhd(totals.total)})" else "Save & Print Invoice (${BahrainFinancials.formatBhd(totals.total)})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
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
            // Customer Section Header & Card matching Board 1 Screen 4
            item {
                Spacer(modifier = Modifier.height(4.dp))
                if (!currentUser?.assignedVanCode.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = KanakAccentLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.LocalShipping, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Selling from Assigned Van: ${currentUser?.assignedVanCode}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryBlue)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Customer",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = KanakPrimaryText
                        )
                    )
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = KanakPrimaryBlue
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showCustomerPicker = true }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showCustomerPicker = true }
                        .testTag("select_customer_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = KanakAccentLight,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Storefront,
                                    contentDescription = null,
                                    tint = KanakPrimaryBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedCustomer?.businessName ?: "Select Customer",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = KanakPrimaryText
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = selectedCustomer?.address?.ifBlank { "Manama, Bahrain" } ?: "Tap to choose a customer",
                                style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = KanakSecondaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Add Products Section
            item {
                Text(
                    text = "Add Products",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = KanakPrimaryText
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                KanakSearchField(
                    query = productSearchQuery,
                    onQueryChange = { productSearchQuery = it },
                    placeholder = "Search products by name or barcode...",
                    onScanClick = { showBarcodeScanDialog = true }
                )
            }

            // If searching for products, show search suggestions
            if (productSearchQuery.isNotBlank()) {
                item {
                    Text(
                        text = "Matching Products (${availableProductsToAdd.size})",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = KanakSecondaryText
                        )
                    )
                }

                items(availableProductsToAdd.take(4), key = { "search_${it.productId}" }) { prod ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = prod.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = KanakPrimaryText
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = BahrainFinancials.formatBhd(prod.sellingPrice),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryBlue)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    val currentQty = cartItems[prod.productId] ?: BigDecimal.ZERO
                                    cartItems = cartItems + (prod.productId to currentQty.add(BigDecimal.ONE))
                                    productSearchQuery = ""
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Add", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Invoice Items Section Header matching Board 1 Screen 4
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Invoice Items (${cartItems.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = KanakPrimaryText
                        )
                    )
                    if (cartItems.isNotEmpty()) {
                        Text(
                            text = "Clear All",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = KanakError
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { cartItems = emptyMap() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Invoice Items List Cards with Stepper matching Board 1 Screen 4
            if (cartItems.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp)
                        ) {
                            Text(
                                text = "No items added yet. Search above to add wholesale SKUs.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = KanakSecondaryText),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(cartItems.toList(), key = { it.first }) { (prodId, qty) ->
                    val prod = allProducts.find { it.productId == prodId }
                    if (prod != null) {
                        val lineCalc = BahrainFinancials.calculateLine(
                            qty = qty,
                            unitPrice = prod.sellingPrice,
                            vatRate = prod.vatRate
                        )

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = KanakSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Product Image Thumbnail
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = KanakSurfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, KanakDivider),
                                    modifier = Modifier.size(50.dp)
                                ) {
                                    if (!prod.photoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = prod.photoUrl,
                                            contentDescription = prod.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize().padding(3.dp)
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Inventory2,
                                                contentDescription = null,
                                                tint = KanakSecondaryText,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = prod.name,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = KanakPrimaryText
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${BahrainFinancials.formatBhd(prod.sellingPrice)} (VAT 10%)",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = KanakSecondaryText,
                                            fontSize = 11.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Stepper Pill [-] QTY [+] matching Board 1 Screen 4
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = KanakSurfaceVariant,
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            ) {
                                                val decSource = remember { MutableInteractionSource() }
                                                IconButton(
                                                    onClick = {
                                                        val newQty = qty.subtract(BigDecimal.ONE)
                                                        cartItems = if (newQty <= BigDecimal.ZERO) {
                                                            cartItems - prodId
                                                        } else {
                                                            cartItems + (prodId to newQty)
                                                        }
                                                    },
                                                    interactionSource = decSource,
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .tactilePress(decSource)
                                                ) {
                                                    Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = KanakPrimaryText, modifier = Modifier.size(16.dp))
                                                }

                                                Text(
                                                    text = qty.stripTrailingZeros().toPlainString(),
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = KanakPrimaryText
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 8.dp)
                                                )

                                                val incSource = remember { MutableInteractionSource() }
                                                IconButton(
                                                    onClick = {
                                                        if (qty < prod.stockQty) {
                                                            cartItems = cartItems + (prodId to qty.add(BigDecimal.ONE))
                                                        }
                                                    },
                                                    interactionSource = incSource,
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .tactilePress(incSource)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = "Increase", tint = KanakPrimaryText, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }

                                        // Line Total
                                        Text(
                                            text = BahrainFinancials.formatBhd(lineCalc.lineTotal),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = KanakPrimaryText
                                            )
                                        )
                                    }
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

    // =========================================================================
    // SAVING INVOICE MODAL (BOARD 5 SCREEN 1)
    // =========================================================================
    if (isSaving) {
        KanakBrandLoadingDialog(
            message = "Saving Bahrain Tax Invoice...",
            subMessage = "Recording Stock Movement & Bahrain VAT Ledger"
        )
    }

    // Customer Selection Bottom Sheet / Modal (Board 3 Screen 5)
    if (showCustomerPicker) {
        var customerFilter by remember { mutableStateOf("") }
        val filteredPickerCustomers = remember(allCustomers, customerFilter) {
            if (customerFilter.isBlank()) allCustomers else allCustomers.filter {
                it.businessName.contains(customerFilter, ignoreCase = true) || it.address.contains(customerFilter, ignoreCase = true)
            }
        }

        AlertDialog(
            onDismissRequest = { showCustomerPicker = false },
            containerColor = KanakSurface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (isCreatingCustomer) "New Customer" else "Select Customer",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        isCreatingCustomer = !isCreatingCustomer
                        customerFormError = null
                    }, modifier = Modifier.testTag("invoice_add_customer_button")) {
                        Icon(if (isCreatingCustomer) Icons.Default.ArrowBack else Icons.Default.Add,
                            contentDescription = if (isCreatingCustomer) "Back to customers" else "Add customer",
                            tint = KanakPrimaryBlue)
                    }
                }
            },
            text = {
                if (isCreatingCustomer) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = customerFormName, onValueChange = { customerFormName = it },
                            label = { Text("Customer name *") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("invoice_new_customer_name"))
                        OutlinedTextField(value = customerFormPhone, onValueChange = { customerFormPhone = it },
                            label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = customerFormAddress, onValueChange = { customerFormAddress = it },
                            label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                        if (customerFormError != null) Text(customerFormError.orEmpty(), color = KanakError)
                    }
                } else {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    KanakSearchField(
                        query = customerFilter,
                        onQueryChange = { customerFilter = it },
                        placeholder = "Search customers..."
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredPickerCustomers) { cust ->
                            val isSelected = selectedCustomer?.customerId == cust.customerId
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) KanakAccentLight else KanakSurface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) KanakPrimaryBlue else KanakDivider
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCustomer = cust
                                        showCustomerPicker = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            selectedCustomer = cust
                                            showCustomerPicker = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = cust.businessName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                                        )
                                        Text(
                                            text = cust.address.ifBlank { cust.phone },
                                            style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            },
            confirmButton = {
                if (isCreatingCustomer) {
                    Button(
                        enabled = customerFormName.isNotBlank() && !isSavingCustomer,
                        onClick = {
                            scope.launch {
                                isSavingCustomer = true
                                customerFormError = null
                                repository.createCustomer(
                                    businessName = customerFormName,
                                    phone = customerFormPhone,
                                    address = customerFormAddress
                                ).onSuccess {
                                    selectedCustomer = it
                                    showCustomerPicker = false
                                    isCreatingCustomer = false
                                    customerFormName = ""
                                    customerFormPhone = ""
                                    customerFormAddress = ""
                                }.onFailure { err -> customerFormError = err.message ?: "Unable to create customer" }
                                isSavingCustomer = false
                            }
                        },
                        modifier = Modifier.testTag("invoice_save_new_customer_button")
                    ) { Text(if (isSavingCustomer) "Saving…" else "Save & Select") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomerPicker = false; isCreatingCustomer = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showBarcodeScanDialog) {
        com.example.kanak.ui.components.BarcodeScanDialog(
            products = allProducts,
            existingProductIdsInInvoice = cartItems.keys,
            onProductSelected = { prod, isDuplicate ->
                val currentQty = cartItems[prod.productId] ?: BigDecimal.ZERO
                cartItems = cartItems + (prod.productId to currentQty.add(BigDecimal.ONE))
            },
            onDismiss = { showBarcodeScanDialog = false }
        )
    }
}
