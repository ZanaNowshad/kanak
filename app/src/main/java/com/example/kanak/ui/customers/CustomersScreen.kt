package com.example.kanak.ui.customers

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.domain.financial.BahrainFinancials
import com.example.kanak.domain.models.Customer
import com.example.kanak.domain.models.Invoice
import com.example.kanak.ui.components.CustomerListItem
import com.example.kanak.ui.components.EmptyStateView
import com.example.kanak.ui.components.KanakConfirmDialog
import com.example.kanak.ui.components.KanakSearchField
import com.example.kanak.ui.components.KanakTopBar
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    repository: KanakRepository,
    currentUserId: String = "driver",
    onNavigateToInvoiceDetail: ((String) -> Unit)? = null,
    onNavigateToNewInvoice: ((String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val customers by repository.customers.collectAsState()
    val invoices by repository.invoices.collectAsState()
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var filterOnlyCreditDue by remember { mutableStateOf(false) }

    // Screen State: List, Detail View, or Add/Edit Form
    var viewingCustomer by remember { mutableStateOf<Customer?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Payment Dialog State
    var payingInvoice by remember { mutableStateOf<Invoice?>(null) }

    // Precalculate customer credit balances for fast UI display
    val customerCredits = remember(customers, invoices) {
        customers.associate { cust ->
            cust.customerId to repository.getCustomerOutstandingCredit(cust.customerId)
        }
    }

    val totalOutstandingCredit = remember(customerCredits) {
        customerCredits.values.fold(BigDecimal.ZERO) { acc, c -> acc.add(c) }
    }

    val customersWithCreditCount = remember(customerCredits) {
        customerCredits.count { it.value > BigDecimal.ZERO }
    }

    val filteredCustomers = remember(customers, searchQuery, filterOnlyCreditDue, customerCredits) {
        var list = customers
        if (filterOnlyCreditDue) {
            list = list.filter { (customerCredits[it.customerId] ?: BigDecimal.ZERO) > BigDecimal.ZERO }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.businessName.lowercase().contains(q) ||
                        it.contact.lowercase().contains(q) ||
                        it.phone.contains(q)
            }
        }
        list
    }

    if (viewingCustomer != null) {
        val cust = viewingCustomer!!
        val customerInvoices = remember(cust, invoices) {
            invoices.filter { it.customerId == cust.customerId }.sortedByDescending { it.date }
        }
        val customerCreditBalance = remember(cust, invoices) {
            repository.getCustomerOutstandingCredit(cust.customerId)
        }
        val unpaidInvoices = remember(customerInvoices) {
            customerInvoices.filter { it.balanceDue > BigDecimal.ZERO && !it.isVoid }
        }
        val paidInvoices = remember(customerInvoices) {
            customerInvoices.filter { (it.balanceDue == BigDecimal.ZERO || it.isPaid) && !it.isVoid }
        }
        val totalPurchasesAmount = remember(customerInvoices) {
            customerInvoices.filter { !it.isVoid }.fold(BigDecimal.ZERO) { acc, inv -> acc.add(inv.total) }
        }

        if (isEditing) {
            // =========================================================================
            // EDIT CUSTOMER FORM
            // =========================================================================
            var editName by remember { mutableStateOf(cust.businessName) }
            var editContact by remember { mutableStateOf(cust.contact) }
            var editPhone by remember { mutableStateOf(cust.phone) }
            var editAddress by remember { mutableStateOf(cust.address) }
            var editError by remember { mutableStateOf<String?>(null) }
            var isSaving by remember { mutableStateOf(false) }

            Scaffold(
                topBar = {
                    KanakTopBar(
                        title = "Edit Customer",
                        onBack = { isEditing = false }
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
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = KanakAccentLight,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Storefront, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(32.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Edit Customer", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText))
                    Text("Update customer information and contact details.", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                    Spacer(modifier = Modifier.height(20.dp))

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text("Business Name *") },
                                leadingIcon = { Icon(Icons.Outlined.Storefront, contentDescription = null, tint = KanakSecondaryText) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("customer_name_input")
                            )
                            OutlinedTextField(
                                value = editContact,
                                onValueChange = { editContact = it },
                                label = { Text("Contact Person *") },
                                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, tint = KanakSecondaryText) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("customer_contact_input")
                            )
                            OutlinedTextField(
                                value = editPhone,
                                onValueChange = { editPhone = it },
                                label = { Text("Phone *") },
                                leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null, tint = KanakSecondaryText) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("customer_phone_input")
                            )
                            OutlinedTextField(
                                value = editAddress,
                                onValueChange = { editAddress = it },
                                label = { Text("Address *") },
                                leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = KanakSecondaryText) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("customer_address_input")
                            )
                        }
                    }

                    if (editError != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = editError ?: "", color = KanakError, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                val res = repository.updateCustomer(
                                    cust.copy(
                                        businessName = editName,
                                        contact = editContact,
                                        phone = editPhone,
                                        address = editAddress
                                    )
                                )
                                isSaving = false
                                res.onSuccess {
                                    viewingCustomer = it
                                    isEditing = false
                                }.onFailure { editError = it.message }
                            }
                        },
                        enabled = !isSaving && editName.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_customer_button")
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Update Customer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // =========================================================================
            // CUSTOMER DETAIL VIEW WITH PURCHASE HISTORY & CREDIT LEDGER
            // =========================================================================
            var historyFilter by remember { mutableStateOf("ALL") } // ALL, UNPAID, PAID

            Scaffold(
                topBar = {
                    KanakTopBar(
                        title = cust.businessName,
                        onBack = { viewingCustomer = null }
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
                    // Header Card with Business Profile
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = KanakAccentLight,
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Outlined.Storefront, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(28.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cust.businessName,
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                                    )
                                    Text(
                                        text = "Customer ID: ${cust.customerId.take(8)}",
                                        style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = KanakSuccessBackground
                                ) {
                                    Text("Active", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = KanakSuccess), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = KanakDivider)

                            // Contact Details with Dial & WhatsApp
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Person, contentDescription = null, tint = KanakSecondaryText, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(cust.contact.ifBlank { "No contact" }, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = KanakPrimaryText))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Phone, contentDescription = null, tint = KanakSecondaryText, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(cust.phone.ifBlank { "No phone" }, style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                    }
                                }

                                if (cust.phone.isNotBlank()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IconButton(onClick = {
                                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${cust.phone}"))
                                            context.startActivity(dialIntent)
                                        }) {
                                            Surface(shape = CircleShape, color = KanakAccentLight, modifier = Modifier.size(36.dp)) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Phone, contentDescription = "Call", tint = KanakPrimaryBlue, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                        IconButton(onClick = {
                                            val cleanPhone = cust.phone.replace("+", "").replace(" ", "").replace("-", "")
                                            val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanPhone"))
                                            context.startActivity(waIntent)
                                        }) {
                                            Surface(shape = CircleShape, color = KanakSuccessBackground, modifier = Modifier.size(36.dp)) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = KanakSuccess, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (cust.address.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = KanakSecondaryText, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(cust.address, style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                }
                            }
                        }
                    }

                    // =========================================================================
                    // CUSTOMER CREDIT OVERVIEW CARD (CRITICAL FEATURE)
                    // =========================================================================
                    val isCreditPending = customerCreditBalance > BigDecimal.ZERO
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCreditPending) Color(0xFFFFFBEB) else Color(0xFFF0FDF4)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (isCreditPending) Color(0xFFF59E0B) else Color(0xFF22C55E)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("customer_credit_overview_card")
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
                                        color = if (isCreditPending) Color(0xFFFEF3C7) else Color(0xFFDCFCE7),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isCreditPending) Icons.Default.AccountBalanceWallet else Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = if (isCreditPending) Color(0xFFD97706) else Color(0xFF16A34A),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Customer Credit Account",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isCreditPending) Color(0xFFFDE68A) else Color(0xFFBBF7D0)
                                ) {
                                    Text(
                                        text = if (isCreditPending) "PAYMENT DUE" else "ALL CLEARED",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCreditPending) Color(0xFF92400E) else Color(0xFF15803D)
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Outstanding Credit Balance",
                                style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                            )
                            Text(
                                text = BahrainFinancials.formatBhd(customerCreditBalance),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = if (isCreditPending) Color(0xFFD97706) else Color(0xFF16A34A),
                                    fontSize = 28.sp
                                ),
                                modifier = Modifier.testTag("customer_outstanding_credit_amount")
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (isCreditPending) {
                                Text(
                                    text = "This customer has ${unpaidInvoices.size} invoice(s) pending payment. You can receive payments directly using the buttons on each bill below.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF78350F), lineHeight = 16.sp)
                                )
                            } else {
                                Text(
                                    text = "No outstanding balance. Customer has fully cleared all issued sales.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF166534))
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = KanakDivider)

                            // Lifetime Statistics Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Total Bills Issued", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp))
                                    Text("${customerInvoices.size} invoices", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Lifetime Sales Total", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp))
                                    Text(BahrainFinancials.formatBhd(totalPurchasesAmount), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }

                    // Action buttons: Edit Customer & Delete Customer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { isEditing = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(46.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KanakPrimaryBlue)
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Info", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = { showDeleteConfirm = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KanakError, contentColor = Color.White)
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }

                    // =========================================================================
                    // PURCHASE / BILL HISTORY SECTION (WITH CREDIT PAYMENT OPTIONS)
                    // =========================================================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Purchase & Bill History",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = KanakPrimaryText
                            )
                        )
                        Text(
                            text = "${customerInvoices.size} total",
                            style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                        )
                    }

                    // Filter Chips: ALL, UNPAID CREDIT, PAID
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = historyFilter == "ALL",
                            onClick = { historyFilter = "ALL" },
                            label = { Text("All (${customerInvoices.size})", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = historyFilter == "UNPAID",
                            onClick = { historyFilter = "UNPAID" },
                            label = { Text("Unpaid Credit (${unpaidInvoices.size})", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFEF3C7),
                                selectedLabelColor = Color(0xFF92400E)
                            )
                        )
                        FilterChip(
                            selected = historyFilter == "PAID",
                            onClick = { historyFilter = "PAID" },
                            label = { Text("Paid (${paidInvoices.size})", fontSize = 12.sp) }
                        )
                    }

                    val displayedInvoices = when (historyFilter) {
                        "UNPAID" -> unpaidInvoices
                        "PAID" -> paidInvoices
                        else -> customerInvoices
                    }

                    if (displayedInvoices.isEmpty()) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = KanakSurfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                                    contentDescription = null,
                                    tint = KanakSecondaryText,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (historyFilter == "UNPAID") "No Unpaid Credit Bills" else "No Invoices Recorded Yet",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (historyFilter == "UNPAID") "All credit invoices for this customer have been cleared." else "Create a sale invoice to start recording purchases.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            displayedInvoices.forEach { invoice ->
                                CustomerBillHistoryCard(
                                    invoice = invoice,
                                    onReceivePaymentClick = { payingInvoice = invoice },
                                    onViewDetailClick = { onNavigateToInvoiceDetail?.invoke(invoice.invoiceId) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(30.dp))
                }
            }

            if (showDeleteConfirm) {
                KanakConfirmDialog(
                    title = "Delete Customer?",
                    message = "Are you sure you want to delete ${cust.businessName}? This action cannot be undone.",
                    warningBoxText = "Customers with historical invoices cannot be deleted to preserve accounting records.",
                    confirmText = "Delete",
                    isDestructive = true,
                    onConfirm = {
                        showDeleteConfirm = false
                        scope.launch {
                            val res = repository.deleteCustomer(cust.customerId)
                            res.onSuccess {
                                viewingCustomer = null
                            }.onFailure { err ->
                                Toast.makeText(context, err.message ?: "Cannot delete customer", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onDismiss = { showDeleteConfirm = false }
                )
            }
        }
    } else {
        // =========================================================================
        // CUSTOMER LIST (BOARD 3 SCREEN 1) WITH OUTSTANDING CREDIT BADGES
        // =========================================================================
        var showAddCustomerSheet by remember { mutableStateOf(false) }

        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddCustomerSheet = true },
                    shape = CircleShape,
                    containerColor = KanakPrimaryBlue,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp),
                    modifier = Modifier.testTag("add_customer_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Customer", modifier = Modifier.size(28.dp))
                }
            },
            modifier = modifier,
            containerColor = KanakBackground
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(KanakBackground)
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Customers",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = KanakPrimaryText,
                                fontSize = 26.sp
                            )
                        )
                        Text(
                            text = "${customers.size} registered retail accounts",
                            style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Outstanding Credit Global Summary Banner (if any customer has credit)
                if (totalOutstandingCredit > BigDecimal.ZERO) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFEF3C7),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Total Credit Outstanding: ${BahrainFinancials.formatBhd(totalOutstandingCredit)}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                                )
                                Text(
                                    text = "$customersWithCreditCount customer account(s) have unpaid credit balances",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFB45309), fontSize = 11.sp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                KanakSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search customers by name, phone, area...",
                    onScanClick = {}
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Filters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !filterOnlyCreditDue,
                        onClick = { filterOnlyCreditDue = false },
                        label = { Text("All (${customers.size})", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = filterOnlyCreditDue,
                        onClick = { filterOnlyCreditDue = true },
                        label = { Text("Credit Due ($customersWithCreditCount)", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFEF3C7),
                            selectedLabelColor = Color(0xFF92400E)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredCustomers.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.People,
                        title = if (searchQuery.isEmpty()) {
                            if (filterOnlyCreditDue) "No Outstanding Credit" else "No Customers Added"
                        } else "No Matching Customers",
                        message = if (searchQuery.isEmpty()) {
                            if (filterOnlyCreditDue) "All customer accounts are clear with zero credit balance."
                            else "Tap '+ Add Customer' to register retail cold stores and grocers on your route."
                        } else "Try a different name or phone number."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredCustomers, key = { it.customerId }) { customer ->
                            val creditDue = customerCredits[customer.customerId] ?: BigDecimal.ZERO
                            CustomerListItem(
                                customer = customer,
                                outstandingCredit = creditDue,
                                onClick = { viewingCustomer = customer }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }

        // Add Customer Dialog
        if (showAddCustomerSheet) {
            var newName by remember { mutableStateOf("") }
            var newContact by remember { mutableStateOf("") }
            var newPhone by remember { mutableStateOf("") }
            var newAddress by remember { mutableStateOf("") }
            var addError by remember { mutableStateOf<String?>(null) }
            var isCreating by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { if (!isCreating) showAddCustomerSheet = false },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Surface(shape = CircleShape, color = KanakAccentLight, modifier = Modifier.size(54.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Storefront, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Add New Customer", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText))
                        Text("Enter customer details below.", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Business Name *") },
                            placeholder = { Text("e.g. Al Noor Cold Store") },
                            leadingIcon = { Icon(Icons.Outlined.Storefront, contentDescription = null, tint = KanakSecondaryText) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newContact,
                            onValueChange = { newContact = it },
                            label = { Text("Contact Person *") },
                            placeholder = { Text("e.g. Mohammed Ali") },
                            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, tint = KanakSecondaryText) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newPhone,
                            onValueChange = { newPhone = it },
                            label = { Text("Phone *") },
                            placeholder = { Text("e.g. +973 3312 4567") },
                            leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null, tint = KanakSecondaryText) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newAddress,
                            onValueChange = { newAddress = it },
                            label = { Text("Address *") },
                            placeholder = { Text("e.g. Shop 12, Road 2803, Seef") },
                            leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = KanakSecondaryText) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (addError != null) {
                            Text(text = addError ?: "", color = KanakError, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                isCreating = true
                                val res = repository.createCustomer(
                                    businessName = newName,
                                    contact = newContact,
                                    address = newAddress,
                                    phone = newPhone
                                )
                                isCreating = false
                                res.onSuccess {
                                    showAddCustomerSheet = false
                                }.onFailure { addError = it.message }
                            }
                        },
                        enabled = !isCreating && newName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Customer", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showAddCustomerSheet = false }, shape = RoundedCornerShape(10.dp)) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    // =========================================================================
    // MODAL DIALOG: RECEIVE PAYMENT FOR UNPAID/PARTIAL CREDIT INVOICE
    // =========================================================================
    if (payingInvoice != null) {
        val invoice = payingInvoice!!
        val balanceDue = invoice.balanceDue
        var paymentAmountStr by remember(invoice) { mutableStateOf(balanceDue.stripTrailingZeros().toPlainString()) }
        var paymentError by remember { mutableStateOf<String?>(null) }
        var isProcessingPayment by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isProcessingPayment) payingInvoice = null },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFEF3C7),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "Receive Credit Payment",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Recording cash receipt against invoice ${invoice.invoiceNumber} for ${invoice.customerName}.",
                        style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                    )

                    // Financial Summary Box
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Invoice Total:", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                Text(BahrainFinancials.formatBhd(invoice.total), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Already Paid:", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                Text(BahrainFinancials.formatBhd(invoice.amountPaid), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = Color(0xFF16A34A)))
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = KanakDivider)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Remaining Due:", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText))
                                Text(
                                    BahrainFinancials.formatBhd(balanceDue),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = Color(0xFFD97706))
                                )
                            }
                        }
                    }

                    // Payment Amount Input
                    OutlinedTextField(
                        value = paymentAmountStr,
                        onValueChange = {
                            paymentAmountStr = it
                            paymentError = null
                        },
                        label = { Text("Payment Amount (BHD) *") },
                        placeholder = { Text("0.000") },
                        leadingIcon = {
                            Text(
                                "BHD",
                                fontWeight = FontWeight.Bold,
                                color = KanakPrimaryBlue,
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("payment_amount_input")
                    )

                    // Quick Pay Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { paymentAmountStr = balanceDue.stripTrailingZeros().toPlainString() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Full Amount", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        if (balanceDue > BigDecimal.ONE) {
                            OutlinedButton(
                                onClick = {
                                    val half = BahrainFinancials.scaleAmount(balanceDue.divide(BigDecimal(2), 3, java.math.RoundingMode.HALF_UP))
                                    paymentAmountStr = half.stripTrailingZeros().toPlainString()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("50%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    if (paymentError != null) {
                        Text(
                            text = paymentError ?: "",
                            color = KanakError,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = BahrainFinancials.parseBhd(paymentAmountStr)
                        if (parsed == null || parsed <= BigDecimal.ZERO) {
                            paymentError = "Please enter a valid positive payment amount."
                            return@Button
                        }
                        if (parsed > balanceDue) {
                            paymentError = "Payment cannot exceed remaining balance due (${BahrainFinancials.formatBhd(balanceDue)})."
                            return@Button
                        }

                        scope.launch {
                            isProcessingPayment = true
                            val result = repository.recordInvoicePayment(
                                invoiceId = invoice.invoiceId,
                                amountToPay = parsed,
                                recordedBy = currentUserId
                            )
                            isProcessingPayment = false
                            result.onSuccess { updatedInv ->
                                val paidFormatted = BahrainFinancials.formatBhd(parsed)
                                val remainingFormatted = BahrainFinancials.formatBhd(updatedInv.balanceDue)
                                Toast.makeText(
                                    context,
                                    "Payment of $paidFormatted recorded. Remaining due: $remainingFormatted",
                                    Toast.LENGTH_LONG
                                ).show()
                                payingInvoice = null
                            }.onFailure { err ->
                                paymentError = err.message ?: "Failed to record payment"
                            }
                        }
                    },
                    enabled = !isProcessingPayment && paymentAmountStr.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706), contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_credit_payment_button")
                ) {
                    if (isProcessingPayment) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Confirm Payment", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { payingInvoice = null },
                    enabled = !isProcessingPayment,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Reusable Customer Bill History Card showing invoice number, date, payment terms,
 * financial totals, and direct action buttons for receiving credit payment.
 */
@Composable
fun CustomerBillHistoryCard(
    invoice: Invoice,
    onReceivePaymentClick: () -> Unit,
    onViewDetailClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCredit = invoice.isCredit
    val balanceDue = invoice.balanceDue
    val isUnpaid = balanceDue > BigDecimal.ZERO && !invoice.isVoid

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = KanakSurface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onViewDetailClick() }
            .testTag("bill_history_item_${invoice.invoiceNumber}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Invoice No & Payment Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = invoice.invoiceNumber,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                    )
                    Text(
                        text = invoice.date.take(16).replace("T", " "),
                        style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp)
                    )
                }

                // Payment Status Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        invoice.isVoid -> KanakErrorBackground
                        !isCredit || invoice.isPaid -> KanakSuccessBackground
                        invoice.paymentStatus == Invoice.PAYMENT_STATUS_PARTIAL -> Color(0xFFFEF3C7)
                        else -> Color(0xFFFEE2E2)
                    }
                ) {
                    Text(
                        text = when {
                            invoice.isVoid -> "VOIDED"
                            !isCredit -> "CASH • PAID"
                            invoice.isPaid -> "CREDIT • PAID"
                            invoice.paymentStatus == Invoice.PAYMENT_STATUS_PARTIAL -> "PARTIAL • DUE: ${BahrainFinancials.formatBhd(balanceDue)}"
                            else -> "CREDIT • UNPAID"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = when {
                                invoice.isVoid -> KanakError
                                !isCredit || invoice.isPaid -> KanakSuccess
                                invoice.paymentStatus == Invoice.PAYMENT_STATUS_PARTIAL -> Color(0xFFB45309)
                                else -> Color(0xFFDC2626)
                            },
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = KanakDivider)

            // Items Preview
            Text(
                text = "${invoice.lines.size} item(s): " + invoice.lines.joinToString(", ") { "${it.productName} (${it.qty.stripTrailingZeros().toPlainString()})" }.take(75) + if (invoice.lines.size > 2) "..." else "",
                style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 12.sp),
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Financial Summary Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Total Bill", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp))
                    Text(
                        text = BahrainFinancials.formatBhd(invoice.total),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                    )
                }

                if (isCredit) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Paid", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp))
                        Text(
                            text = BahrainFinancials.formatBhd(invoice.amountPaid),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color(0xFF16A34A))
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("Balance Due", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp))
                        Text(
                            text = BahrainFinancials.formatBhd(balanceDue),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = if (balanceDue > BigDecimal.ZERO) Color(0xFFDC2626) else Color(0xFF16A34A)
                            )
                        )
                    }
                }
            }

            // Options & Action Buttons Next to / Under each bill
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // If cash is not received (credit / balance due > 0), show "Receive Payment" button
                if (isUnpaid) {
                    Button(
                        onClick = onReceivePaymentClick,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD97706),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(42.dp)
                            .testTag("receive_payment_button_${invoice.invoiceNumber}")
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Receive Payment", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                OutlinedButton(
                    onClick = onViewDetailClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KanakPrimaryBlue),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("view_bill_detail_button_${invoice.invoiceNumber}")
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View Bill", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        }
    }
}
