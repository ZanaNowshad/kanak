package com.example.kanak.ui.customers

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.domain.models.Customer
import com.example.kanak.ui.components.CustomerListItem
import com.example.kanak.ui.components.EmptyStateView
import com.example.kanak.ui.components.KanakConfirmDialog
import com.example.kanak.ui.components.KanakSearchField
import com.example.kanak.ui.components.KanakTopBar
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    repository: KanakRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val customers by repository.customers.collectAsState()
    val invoices by repository.invoices.collectAsState()
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }

    // Screen State: List, Detail View, or Add/Edit Form
    var viewingCustomer by remember { mutableStateOf<Customer?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val filteredCustomers = remember(customers, searchQuery) {
        if (searchQuery.isBlank()) {
            customers
        } else {
            val q = searchQuery.trim().lowercase()
            customers.filter {
                it.businessName.lowercase().contains(q) ||
                        it.contact.lowercase().contains(q) ||
                        it.phone.contains(q)
            }
        }
    }

    if (viewingCustomer != null) {
        val cust = viewingCustomer!!
        val customerInvoices = remember(cust, invoices) {
            invoices.filter { it.customerId == cust.customerId }
        }

        if (isEditing) {
            // =========================================================================
            // EDIT CUSTOMER FORM (BOARD 3 SCREEN 4)
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
                    Text("Update the customer information.", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
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
                                label = { Text("Contact *") },
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
            // CUSTOMER DETAIL VIEW (BOARD 3 SCREEN 2)
            // =========================================================================
            Scaffold(
                topBar = {
                    KanakTopBar(
                        title = "Customer Detail",
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
                    // Header Card with circular storefront icon & Active badge
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                                Text(text = cust.businessName, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText))
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = KanakSuccessBackground
                            ) {
                                Text("Active", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = KanakSuccess), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }

                    // Contact Details Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Contact Person
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Person, contentDescription = null, tint = KanakSecondaryText, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Contact Person", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                    Text(text = cust.contact.ifBlank { "Not specified" }, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, color = KanakPrimaryText))
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = KanakDivider)

                            // Phone with Call & WhatsApp Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Phone, contentDescription = null, tint = KanakSecondaryText, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Phone", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                        Text(text = cust.phone.ifBlank { "Not specified" }, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, color = KanakPrimaryText))
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
                                            val cleanPhone = cust.phone.replace("+", "").replace(" ", "")
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

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = KanakDivider)

                            // Address
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = KanakSecondaryText, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Address", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                    Text(text = cust.address.ifBlank { "Not specified" }, style = MaterialTheme.typography.bodyMedium.copy(color = KanakPrimaryText))
                                }
                            }
                        }
                    }

                    // Invoice History Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Invoice History", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText))
                                    Text("${customerInvoices.size} invoices recorded", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = KanakSecondaryText)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Buttons: Edit & Delete
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { isEditing = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(50.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KanakPrimaryBlue)
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { showDeleteConfirm = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KanakError, contentColor = Color.White)
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete", fontWeight = FontWeight.SemiBold)
                        }
                    }
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
                                android.widget.Toast.makeText(context, err.message ?: "Cannot delete customer", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onDismiss = { showDeleteConfirm = false }
                )
            }
        }
    } else {
        // =========================================================================
        // CUSTOMER LIST (BOARD 3 SCREEN 1)
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

                Text(
                    text = "Customers",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = KanakPrimaryText,
                        fontSize = 26.sp
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                KanakSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search customers by name, contact...",
                    onScanClick = {}
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (filteredCustomers.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.People,
                        title = if (searchQuery.isEmpty()) "No Customers Added" else "No Matching Customers",
                        message = if (searchQuery.isEmpty())
                            "Tap '+ Add Customer' to register retail cold stores and grocers on your route."
                        else
                            "Try a different name or phone number."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredCustomers, key = { it.customerId }) { customer ->
                            CustomerListItem(
                                customer = customer,
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

        // Add Customer Dialog / Sheet matching Board 3 Screen 3
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
                        Text("Enter the customer details below.", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Business Name *") },
                            placeholder = { Text("e.g. Al Noor Mini Market") },
                            leadingIcon = { Icon(Icons.Outlined.Storefront, contentDescription = null, tint = KanakSecondaryText) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newContact,
                            onValueChange = { newContact = it },
                            label = { Text("Contact *") },
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
}
