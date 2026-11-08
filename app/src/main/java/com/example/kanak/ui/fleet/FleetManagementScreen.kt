package com.example.kanak.ui.fleet

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.domain.financial.BahrainFinancials
import com.example.kanak.domain.models.*
import com.example.kanak.ui.components.EmptyStateView
import com.example.kanak.ui.components.KanakTopBar
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetManagementScreen(
    repository: KanakRepository,
    currentUser: User,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vans by repository.vans.collectAsState()
    val products by repository.products.collectAsState()
    val teamMembers by repository.teamMemberships.collectAsState()
    val stockTransfers by repository.stockTransfers.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Vans, 1: Transfer Stock, 2: Inventory Matrix
    var showAddVanDialog by remember { mutableStateOf(false) }
    var assigningVan by remember { mutableStateOf<Van?>(null) }
    var deletingVan by remember { mutableStateOf<Van?>(null) }

    Scaffold(
        topBar = {
            KanakTopBar(
                title = "Fleet & Van Management",
                onBack = onNavigateBack
            )
        },
        floatingActionButton = {
            if (selectedTab == 0 && (currentUser.isOwner || currentUser.hasPermission(KanakPermissions.VANS_CREATE))) {
                FloatingActionButton(
                    onClick = { showAddVanDialog = true },
                    shape = CircleShape,
                    containerColor = KanakPrimaryBlue,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_van_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Van")
                }
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
        ) {
            // Navigation Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = KanakSurface,
                contentColor = KanakPrimaryBlue
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Vans (${vans.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Stock Transfer", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Van Inventory", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
            }

            when (selectedTab) {
                0 -> VansListTab(
                    vans = vans,
                    currentUser = currentUser,
                    onAssignDriverClick = { assigningVan = it },
                    onDeleteVanClick = { deletingVan = it }
                )
                1 -> StockTransferTab(
                    repository = repository,
                    vans = vans,
                    products = products,
                    currentUser = currentUser,
                    stockTransfers = stockTransfers
                )
                2 -> VanInventoryMatrixTab(
                    repository = repository,
                    vans = vans,
                    products = products
                )
            }
        }
    }

    // Add Van Dialog
    if (showAddVanDialog) {
        var vanCode by remember { mutableStateOf("") }
        var vanName by remember { mutableStateOf("") }
        var regNo by remember { mutableStateOf("") }
        var selectedSalesperson by remember { mutableStateOf<EmployeeMembership?>(null) }
        var isSaving by remember { mutableStateOf(false) }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        val availableSalespeople = remember(teamMembers) {
            teamMembers.filter { it.status == "ACTIVE" && it.role == User.ROLE_SALESPERSON }
        }

        AlertDialog(
            onDismissRequest = { if (!isSaving) showAddVanDialog = false },
            icon = {
                Surface(shape = CircleShape, color = KanakAccentLight, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(26.dp))
                    }
                }
            },
            title = { Text("Add Fleet Van", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = vanCode,
                        onValueChange = { vanCode = it },
                        label = { Text("Van Code (e.g. VAN-004) *") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_van_code_input")
                    )
                    OutlinedTextField(
                        value = vanName,
                        onValueChange = { vanName = it },
                        label = { Text("Van Name (e.g. Toyota HiAce East) *") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_van_name_input")
                    )
                    OutlinedTextField(
                        value = regNo,
                        onValueChange = { regNo = it },
                        label = { Text("Vehicle Registration No.") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Assign Initial Driver (Optional):", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                    var expandedDropdown by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { expandedDropdown = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedSalesperson?.displayName ?: "Unassigned (Select Driver)")
                        }
                        DropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None (Available)") },
                                onClick = {
                                    selectedSalesperson = null
                                    expandedDropdown = false
                                }
                            )
                            availableSalespeople.forEach { sp ->
                                DropdownMenuItem(
                                    text = { Text("${sp.displayName} (${sp.email})") },
                                    onClick = {
                                        selectedSalesperson = sp
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    if (errorMsg != null) {
                        Text(errorMsg ?: "", color = KanakError, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            val res = repository.createVan(
                                vanCode = vanCode,
                                vanName = vanName,
                                registrationNo = regNo,
                                assignedSalespersonId = selectedSalesperson?.userId,
                                assignedSalespersonName = selectedSalesperson?.displayName
                            )
                            isSaving = false
                            res.onSuccess {
                                Toast.makeText(context, "Van ${it.vanCode} registered successfully", Toast.LENGTH_SHORT).show()
                                showAddVanDialog = false
                            }.onFailure {
                                errorMsg = it.message ?: "Failed to create van"
                            }
                        }
                    },
                    enabled = !isSaving && vanCode.isNotBlank() && vanName.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue, contentColor = Color.White)
                ) {
                    Text("Save Van", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddVanDialog = false }, shape = RoundedCornerShape(10.dp)) {
                    Text("Cancel")
                }
            }
        )
    }

    // Assign Driver to Van Dialog
    if (assigningVan != null) {
        val van = assigningVan!!
        val availableSalespeople = remember(teamMembers) {
            teamMembers.filter { it.status == "ACTIVE" && it.role == User.ROLE_SALESPERSON }
        }

        AlertDialog(
            onDismissRequest = { assigningVan = null },
            title = { Text("Assign Driver to ${van.vanCode}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current Driver: ${van.assignedSalespersonName ?: "None (Available)"}", style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(color = KanakDivider)
                    Text("Select Driver to Assign:", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                repository.assignSalespersonToVan(van.vanId, null, null)
                                Toast.makeText(context, "Van ${van.vanCode} marked as available", Toast.LENGTH_SHORT).show()
                                assigningVan = null
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Unassign Driver (Mark Available)")
                    }

                    availableSalespeople.forEach { sp ->
                        Button(
                            onClick = {
                                scope.launch {
                                    repository.assignSalespersonToVan(van.vanId, sp.userId, sp.displayName)
                                    Toast.makeText(context, "Assigned ${sp.displayName} to ${van.vanCode}", Toast.LENGTH_SHORT).show()
                                    assigningVan = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (van.assignedSalespersonId == sp.userId) KanakPrimaryBlue else KanakSurfaceVariant,
                                contentColor = if (van.assignedSalespersonId == sp.userId) Color.White else KanakPrimaryText
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${sp.displayName} (${sp.email})")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = { assigningVan = null }, shape = RoundedCornerShape(10.dp)) {
                    Text("Close")
                }
            }
        )
    }

    // Decommission / Delete Van Dialog
    if (deletingVan != null) {
        val van = deletingVan!!
        var isDeleting by remember { mutableStateOf(false) }
        var deleteError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isDeleting) deletingVan = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = KanakError) },
            title = { Text("Decommission Van ${van.vanCode}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Are you sure you want to decommission van ${van.vanCode} (${van.vanName})? All allocated inventory must be transferred back to the warehouse first, and no invoices can be recorded against it.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (deleteError != null) {
                        Text(
                            deleteError!!,
                            style = MaterialTheme.typography.bodySmall.copy(color = KanakError, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isDeleting = true
                            deleteError = null
                            val res = repository.deleteVan(van.vanId)
                            isDeleting = false
                            if (res.isSuccess) {
                                Toast.makeText(context, "Van ${van.vanCode} decommissioned successfully", Toast.LENGTH_SHORT).show()
                                deletingVan = null
                            } else {
                                deleteError = res.exceptionOrNull()?.message ?: "Failed to delete van"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KanakError, contentColor = Color.White),
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Decommission")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { deletingVan = null },
                    enabled = !isDeleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VansListTab(
    vans: List<Van>,
    currentUser: User,
    onAssignDriverClick: (Van) -> Unit,
    onDeleteVanClick: (Van) -> Unit
) {
    if (vans.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.DirectionsBus,
            title = "No Vans Registered",
            message = "Add delivery vans to your fleet to track inventory and assign drivers."
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(vans, key = { it.vanId }) { van ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth().testTag("van_card_${van.vanCode}")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = KanakAccentLight,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(24.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(van.vanCode, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText))
                                    Text(van.vanName, style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (van.status == Van.STATUS_ACTIVE) KanakSuccessBackground else Color(0xFFFEF3C7)
                            ) {
                                Text(
                                    text = van.status,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (van.status == Van.STATUS_ACTIVE) KanakSuccess else Color(0xFFD97706)
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = KanakDivider)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Assigned Salesperson", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp))
                                Text(
                                    text = van.assignedSalespersonName ?: "None (Unassigned)",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (van.assignedSalespersonName != null) KanakPrimaryText else KanakSecondaryText
                                    )
                                )
                            }
                            if (van.registrationNo.isNotBlank()) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Plate No.", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp))
                                    Text(van.registrationNo, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                }
                            }
                        }

                        val canAssign = currentUser.isOwner || currentUser.hasPermission(KanakPermissions.VANS_ASSIGN)
                        val canDecommission = currentUser.isOwner || currentUser.hasPermission(KanakPermissions.VANS_CREATE)

                        if (canAssign || canDecommission) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (canAssign) {
                                    OutlinedButton(
                                        onClick = { onAssignDriverClick(van) },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).height(42.dp)
                                    ) {
                                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Assign Driver", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    }
                                }
                                if (canDecommission) {
                                    OutlinedButton(
                                        onClick = { onDeleteVanClick(van) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KanakError),
                                        modifier = Modifier.height(42.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Decommission Van", tint = KanakError, modifier = Modifier.size(18.dp))
                                    }
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
private fun StockTransferTab(
    repository: KanakRepository,
    vans: List<Van>,
    products: List<Product>,
    currentUser: User,
    stockTransfers: List<StockTransfer>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceType by remember { mutableStateOf("WAREHOUSE") } // "WAREHOUSE" or "VAN"
    var sourceVanId by remember { mutableStateOf<String?>(vans.firstOrNull()?.vanId) }
    var destinationVanId by remember { mutableStateOf<String?>(vans.firstOrNull()?.vanId) }
    var selectedProduct by remember { mutableStateOf<Product?>(products.firstOrNull()) }
    var qtyString by remember { mutableStateOf("10") }
    var transferNotes by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var transferError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Stock Transfer Form Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = KanakSurface),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Transfer Stock to Van",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                )
                Text(
                    text = "Move items from central warehouse to van or transfer stock between vans.",
                    style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                )

                HorizontalDivider(color = KanakDivider)

                // Source Selection
                Text("Source Location:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = sourceType == "WAREHOUSE",
                        onClick = { sourceType = "WAREHOUSE" },
                        label = { Text("Central Warehouse") }
                    )
                    FilterChip(
                        selected = sourceType == "VAN",
                        onClick = { sourceType = "VAN" },
                        label = { Text("Van to Van") }
                    )
                }

                if (sourceType == "VAN") {
                    var expSource by remember { mutableStateOf(false) }
                    val srcVan = vans.find { it.vanId == sourceVanId }
                    Box {
                        OutlinedButton(
                            onClick = { expSource = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Source: ${srcVan?.vanCode ?: "Select Van"}")
                        }
                        DropdownMenu(expanded = expSource, onDismissRequest = { expSource = false }) {
                            vans.forEach { v ->
                                DropdownMenuItem(
                                    text = { Text("${v.vanCode} (${v.vanName})") },
                                    onClick = {
                                        sourceVanId = v.vanId
                                        expSource = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Destination Van Selection
                Text("Destination Van:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                var expDest by remember { mutableStateOf(false) }
                val destVan = vans.find { it.vanId == destinationVanId }
                Box {
                    OutlinedButton(
                        onClick = { expDest = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Destination: ${destVan?.vanCode ?: "Select Destination Van"}")
                    }
                    DropdownMenu(expanded = expDest, onDismissRequest = { expDest = false }) {
                        vans.filter { sourceType != "VAN" || it.vanId != sourceVanId }.forEach { v ->
                            DropdownMenuItem(
                                text = { Text("${v.vanCode} (${v.vanName})") },
                                onClick = {
                                    destinationVanId = v.vanId
                                    expDest = false
                                }
                            )
                        }
                    }
                }

                // Product Selection
                Text("Select Product:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                var expProd by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { expProd = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedProduct?.name ?: "Select Product")
                    }
                    DropdownMenu(expanded = expProd, onDismissRequest = { expProd = false }) {
                        products.forEach { p ->
                            DropdownMenuItem(
                                text = { Text("${p.name} (Whse: ${p.stockQty})") },
                                onClick = {
                                    selectedProduct = p
                                    expProd = false
                                }
                            )
                        }
                    }
                }

                // Available stock preview
                if (selectedProduct != null) {
                    val availableSourceQty = if (sourceType == "WAREHOUSE") {
                        selectedProduct!!.stockQty
                    } else {
                        repository.getVanStock(sourceVanId ?: "", selectedProduct!!.productId)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = KanakAccentLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Available in Source:", style = MaterialTheme.typography.bodySmall)
                            Text("${availableSourceQty.stripTrailingZeros().toPlainString()} units", fontWeight = FontWeight.Bold, color = KanakPrimaryBlue)
                        }
                    }
                }

                // Quantity Input
                OutlinedTextField(
                    value = qtyString,
                    onValueChange = {
                        qtyString = it
                        transferError = null
                    },
                    label = { Text("Transfer Quantity *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("transfer_qty_input")
                )

                OutlinedTextField(
                    value = transferNotes,
                    onValueChange = { transferNotes = it },
                    label = { Text("Notes / Purpose (Optional)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (transferError != null) {
                    Text(transferError ?: "", color = KanakError, style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = {
                        val prod = selectedProduct
                        val targetVan = destinationVanId
                        val qty = BahrainFinancials.parseBhd(qtyString)
                        if (prod == null || targetVan.isNullOrBlank() || qty <= BigDecimal.ZERO) {
                            transferError = "Please select product, destination van, and valid quantity"
                            return@Button
                        }

                        scope.launch {
                            isSubmitting = true
                            val res = repository.transferStock(
                                sourceType = sourceType,
                                sourceId = if (sourceType == "WAREHOUSE") "WAREHOUSE" else (sourceVanId ?: ""),
                                destinationVanId = targetVan,
                                productId = prod.productId,
                                qty = qty,
                                transferredBy = currentUser.displayName,
                                notes = transferNotes
                            )
                            isSubmitting = false
                            res.onSuccess {
                                Toast.makeText(context, "Transferred ${qty.stripTrailingZeros().toPlainString()} of ${prod.name} successfully!", Toast.LENGTH_LONG).show()
                                transferNotes = ""
                            }.onFailure {
                                transferError = it.message ?: "Failed to transfer stock"
                            }
                        }
                    },
                    enabled = !isSubmitting && selectedProduct != null && !destinationVanId.isNullOrBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("confirm_transfer_button")
                ) {
                    Text("Confirm Stock Transfer", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Recent Stock Movement History
        Text("Recent Transfer Log", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        if (stockTransfers.isEmpty()) {
            Text("No transfers recorded yet.", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
        } else {
            stockTransfers.take(10).forEach { tr ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(tr.productName, fontWeight = FontWeight.Bold)
                            Text("${tr.sourceName} -> ${tr.destinationVanName}", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp))
                            Text("By: ${tr.transferredBy} • ${tr.timestamp.take(16).replace("T", " ")}", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 10.sp))
                        }
                        Surface(shape = RoundedCornerShape(6.dp), color = KanakAccentLight) {
                            Text("+${tr.qty.stripTrailingZeros().toPlainString()}", fontWeight = FontWeight.Bold, color = KanakPrimaryBlue, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VanInventoryMatrixTab(
    repository: KanakRepository,
    vans: List<Van>,
    products: List<Product>
) {
    val vanStocks by repository.vanStocks.collectAsState()

    if (products.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.Inventory2,
            title = "No Products in Catalogue",
            message = "Add products to view stock matrix across your warehouse and vans."
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "Inventory Location Balances",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                )
                Text(
                    text = "Per-location stock isolation ensures independent simultaneous van sales.",
                    style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            items(products, key = { it.productId }) { prod ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(prod.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text("Total: BHD ${BahrainFinancials.formatBhd(prod.sellingPrice)}", style = MaterialTheme.typography.bodySmall.copy(color = KanakPrimaryBlue, fontWeight = FontWeight.Bold))
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Warehouse Stock
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Central Warehouse:", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                            Text("${prod.stockQty.stripTrailingZeros().toPlainString()} units", fontWeight = FontWeight.SemiBold)
                        }

                        // Each Van Stock
                        vans.forEach { van ->
                            val vStock = vanStocks[van.vanId]?.get(prod.productId) ?: BigDecimal.ZERO
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${van.vanCode} (${van.vanName}):", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 12.sp))
                                Text(
                                    "${vStock.stripTrailingZeros().toPlainString()} units",
                                    fontWeight = FontWeight.Bold,
                                    color = if (vStock > BigDecimal.ZERO) Color(0xFF16A34A) else KanakSecondaryText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
