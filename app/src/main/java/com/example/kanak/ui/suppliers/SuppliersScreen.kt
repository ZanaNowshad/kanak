package com.example.kanak.ui.suppliers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.domain.models.Supplier
import com.example.kanak.ui.components.EmptyStateView
import com.example.kanak.ui.components.KanakConfirmDialog
import com.example.kanak.ui.components.KanakSearchField
import com.example.kanak.ui.components.KanakTopBar
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuppliersScreen(
    repository: KanakRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val suppliers by repository.suppliers.collectAsState()
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }

    var viewingSupplier by remember { mutableStateOf<Supplier?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val filteredSuppliers = remember(suppliers, searchQuery) {
        if (searchQuery.isBlank()) {
            suppliers
        } else {
            val q = searchQuery.trim().lowercase()
            suppliers.filter {
                it.name.lowercase().contains(q) || it.contact.lowercase().contains(q)
            }
        }
    }

    if (viewingSupplier != null) {
        val sup = viewingSupplier!!

        if (isEditing) {
            // =========================================================================
            // EDIT SUPPLIER (BOARD 4 SCREEN 4)
            // =========================================================================
            var name by remember { mutableStateOf(sup.name) }
            var contactPhone by remember { mutableStateOf(sup.contact) }
            var editError by remember { mutableStateOf<String?>(null) }
            var isSaving by remember { mutableStateOf(false) }

            Scaffold(
                containerColor = KanakBackground,
                topBar = {
                    KanakTopBar(
                        title = "Edit Supplier",
                        onBack = { isEditing = false }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(KanakBackground)
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Supplier Name *") },
                                leadingIcon = { Icon(Icons.Outlined.Storefront, contentDescription = null, tint = KanakSecondaryText) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = contactPhone,
                                onValueChange = { contactPhone = it },
                                label = { Text("Contact Number") },
                                leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null, tint = KanakSecondaryText) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (editError != null) {
                        Text(text = editError ?: "", color = KanakError, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                val res = repository.updateSupplier(sup.copy(name = name, contact = contactPhone))
                                isSaving = false
                                res.onSuccess {
                                    viewingSupplier = it
                                    isEditing = false
                                }.onFailure { editError = it.message }
                            }
                        },
                        enabled = !isSaving && name.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // =========================================================================
            // SUPPLIER DETAIL (BOARD 4 SCREEN 2)
            // =========================================================================
            Scaffold(
                containerColor = KanakBackground,
                topBar = {
                    KanakTopBar(
                        title = "Supplier Detail",
                        onBack = { viewingSupplier = null }
                    )
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
                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = CircleShape,
                        color = KanakAccentLight,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Storefront, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(36.dp))
                        }
                    }

                    Text(
                        text = sup.name,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Contact Card
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
                                Icon(Icons.Outlined.Phone, contentDescription = null, tint = KanakSecondaryText, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Contact", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                    Text(text = sup.contact.ifBlank { "Not provided" }, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, color = KanakPrimaryText))
                                }
                            }
                            if (sup.contact.isNotBlank()) {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Contact", sup.contact))
                                    Toast.makeText(context, "Contact copied", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = KanakPrimaryBlue, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

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
                    title = "Delete Supplier?",
                    message = "Are you sure you want to delete ${sup.name}? This action cannot be undone.",
                    warningBoxText = "Suppliers with recorded purchases cannot be deleted to preserve inventory audit history.",
                    confirmText = "Delete",
                    isDestructive = true,
                    onConfirm = {
                        showDeleteConfirm = false
                        scope.launch {
                            val res = repository.deleteSupplier(sup.supplierId)
                            res.onSuccess {
                                viewingSupplier = null
                            }.onFailure { err ->
                                Toast.makeText(context, err.message ?: "Cannot delete supplier", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onDismiss = { showDeleteConfirm = false }
                )
            }
        }
    } else {
        // =========================================================================
        // SUPPLIERS LIST (BOARD 4 SCREEN 1)
        // =========================================================================
        var showAddSupplierSheet by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                KanakTopBar(
                    title = "Suppliers",
                    subtitle = "Manage your product suppliers",
                    onBack = onNavigateBack
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddSupplierSheet = true },
                    shape = CircleShape,
                    containerColor = KanakPrimaryBlue,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp),
                    modifier = Modifier.testTag("add_supplier_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Supplier", modifier = Modifier.size(28.dp))
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
                    placeholder = "Search suppliers by name or contact..."
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (filteredSuppliers.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.LocalShipping,
                        title = if (searchQuery.isEmpty()) "No Suppliers Registered" else "No Matching Suppliers",
                        message = if (searchQuery.isEmpty())
                            "Add FMCG distributors and suppliers to manage purchase stock."
                        else
                            "Try a different supplier name or phone number."
                    )
                } else {
                    val colors = listOf(
                        KanakAccentLight to KanakPrimaryBlue,
                        KanakSuccessBackground to KanakSuccess,
                        Color(0xFFFEF3C7) to Color(0xFFD97706),
                        Color(0xFFF3E8FF) to Color(0xFF7E22CE),
                        KanakErrorBackground to KanakError
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredSuppliers, key = { it.supplierId }) { supplier ->
                            val colorIdx = (supplier.supplierId.hashCode() and 0x7FFFFFFF) % colors.size
                            val (bg, fg) = colors[colorIdx]

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { viewingSupplier = supplier }
                                    .testTag("supplier_item_${supplier.supplierId}"),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = KanakSurface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = bg,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Outlined.Storefront, contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = supplier.name,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = KanakPrimaryText
                                            )
                                        )
                                        if (supplier.contact.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = supplier.contact,
                                                style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                                            )
                                        }
                                    }

                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = KanakSecondaryText)
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

        // Add Supplier Dialog (Board 4 Screen 3)
        if (showAddSupplierSheet) {
            var newName by remember { mutableStateOf("") }
            var newContact by remember { mutableStateOf("") }
            var addError by remember { mutableStateOf<String?>(null) }
            var isCreating by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { if (!isCreating) showAddSupplierSheet = false },
                title = { Text("Add Supplier", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Supplier Name *") },
                            placeholder = { Text("Enter supplier name") },
                            leadingIcon = { Icon(Icons.Outlined.Storefront, contentDescription = null, tint = KanakSecondaryText) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newContact,
                            onValueChange = { newContact = it },
                            label = { Text("Contact Number") },
                            placeholder = { Text("Enter contact number") },
                            leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null, tint = KanakSecondaryText) },
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
                                val res = repository.createSupplier(name = newName, contact = newContact)
                                isCreating = false
                                res.onSuccess {
                                    showAddSupplierSheet = false
                                }.onFailure { addError = it.message }
                            }
                        },
                        enabled = !isCreating && newName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Supplier", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showAddSupplierSheet = false }, shape = RoundedCornerShape(10.dp)) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
