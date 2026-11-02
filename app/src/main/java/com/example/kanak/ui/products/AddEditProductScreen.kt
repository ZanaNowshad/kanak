package com.example.kanak.ui.products

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.domain.financial.BahrainFinancials
import com.example.kanak.images.BingThumbnailUrlBuilder
import com.example.kanak.ui.components.KanakConfirmDialog
import com.example.kanak.ui.components.KanakTopBar
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductScreen(
    repository: KanakRepository,
    productId: String?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val products by repository.products.collectAsState()
    val existing = remember(productId, products) {
        products.find { it.productId == productId }
    }

    // Toggle between Detail View (Board 2 Screen 1) and Form Mode (Board 2 Screen 2 & 3)
    var isEditing by remember(existing) { mutableStateOf(existing == null) }

    var barcode by remember(existing) { mutableStateOf(existing?.barcode ?: "") }
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var costPriceStr by remember(existing) { mutableStateOf(existing?.costPrice?.toPlainString() ?: "") }
    var sellingPriceStr by remember(existing) { mutableStateOf(existing?.sellingPrice?.toPlainString() ?: "") }
    var vatRateDropdownExpanded by remember { mutableStateOf(false) }
    var selectedVatRate by remember(existing) { mutableStateOf(existing?.vatRate ?: BigDecimal("0.10")) }

    var manualPhotoUrl by remember(existing) { mutableStateOf(existing?.photoUrl) }
    var isManualOverride by remember(existing) {
        val autoUrl = if (existing != null) BingThumbnailUrlBuilder.buildDefaultProductImageUrl(existing.barcode, existing.name) else null
        mutableStateOf(existing?.photoUrl != null && existing.photoUrl != autoUrl)
    }

    var showPasteUrlDialog by remember { mutableStateOf(false) }
    var pastedUrlInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            manualPhotoUrl = uri.toString()
            isManualOverride = true
        }
    }

    val autoImageUrl = remember(barcode, name) {
        BingThumbnailUrlBuilder.buildDefaultProductImageUrl(barcode, name)
    }
    val currentPhotoUrl = if (isManualOverride && !manualPhotoUrl.isNullOrBlank()) {
        manualPhotoUrl
    } else {
        autoImageUrl
    }

    Scaffold(
        topBar = {
            KanakTopBar(
                title = when {
                    !isEditing && existing != null -> "Product Detail"
                    existing != null -> "Edit Product"
                    else -> "Add Product"
                },
                onBack = {
                    if (isEditing && existing != null) {
                        isEditing = false
                    } else {
                        onNavigateBack()
                    }
                }
            )
        },
        modifier = modifier,
        containerColor = KanakBackground
    ) { innerPadding ->
        if (!isEditing && existing != null) {
            // =========================================================================
            // PRODUCT DETAIL VIEW (BOARD 2 SCREEN 1)
            // =========================================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(KanakBackground)
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Large Hero Product Image
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!existing.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = existing.photoUrl,
                                contentDescription = existing.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = KanakSecondaryText,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }

                // Title & Active Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = existing.name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = KanakPrimaryText,
                            fontSize = 22.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = KanakSuccessBackground
                    ) {
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = KanakSuccess
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Barcode Card with copy icon
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.QrCode,
                                contentDescription = null,
                                tint = KanakPrimaryBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = existing.barcode.ifBlank { "No Barcode" },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = KanakPrimaryText
                                )
                            )
                        }
                        if (existing.barcode.isNotBlank()) {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Barcode", existing.barcode))
                                Toast.makeText(context, "Barcode copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy Barcode",
                                    tint = KanakPrimaryBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Financial Specifications Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        SpecRow(
                            icon = Icons.Outlined.Sell,
                            label = "Selling Price",
                            value = BahrainFinancials.formatBhd(existing.sellingPrice),
                            isBold = true
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = KanakDivider)
                        SpecRow(
                            icon = Icons.Outlined.MonetizationOn,
                            label = "Cost Price",
                            value = BahrainFinancials.formatBhd(existing.costPrice)
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = KanakDivider)
                        SpecRow(
                            icon = Icons.Outlined.Percent,
                            label = "VAT Rate",
                            value = if (existing.vatRate > BigDecimal.ZERO) "10%" else "0%"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = KanakDivider)
                        SpecRow(
                            icon = Icons.Outlined.Calculate,
                            label = "VAT Amount (per unit)",
                            value = BahrainFinancials.formatBhd(existing.vatAmount)
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = KanakDivider)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Inventory2,
                                    contentDescription = null,
                                    tint = KanakSecondaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Stock Quantity",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = KanakSecondaryText)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = existing.stockQty.stripTrailingZeros().toPlainString(),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = KanakPrimaryText
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = KanakSuccessBackground
                                ) {
                                    Text(
                                        text = "In stock",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = KanakSuccess,
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

                // Info Banner
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakAccentLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = KanakPrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Stock quantity is adjusted through purchases or stock adjustments.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = KanakPrimaryText,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Action Buttons matching Board 2 Screen 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { isEditing = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("edit_product_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KanakPrimaryBlue)
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { showDeleteConfirm = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("delete_product_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KanakError,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            // =========================================================================
            // ADD / EDIT PRODUCT FORM (BOARD 2 SCREENS 2 & 3)
            // =========================================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(KanakBackground)
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Barcode Field with Scan Icon
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("Barcode") },
                    leadingIcon = {
                        Icon(Icons.Outlined.QrCode, contentDescription = null, tint = KanakSecondaryText)
                    },
                    trailingIcon = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Scan", tint = KanakPrimaryBlue)
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_barcode_input")
                )

                // Product Name Field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name *") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Storefront, contentDescription = null, tint = KanakSecondaryText)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_name_input")
                )

                // Cost Price & Selling Price in Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = costPriceStr,
                        onValueChange = { costPriceStr = it },
                        label = { Text("Cost Price (BHD)") },
                        leadingIcon = {
                            Icon(Icons.Outlined.MonetizationOn, contentDescription = null, tint = KanakSecondaryText)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_cost_input")
                    )

                    OutlinedTextField(
                        value = sellingPriceStr,
                        onValueChange = { sellingPriceStr = it },
                        label = { Text("Selling Price (BHD)") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Sell, contentDescription = null, tint = KanakSecondaryText)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_selling_input")
                    )
                }

                // VAT Rate Dropdown
                ExposedDropdownMenuBox(
                    expanded = vatRateDropdownExpanded,
                    onExpandedChange = { vatRateDropdownExpanded = !vatRateDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = if (selectedVatRate > BigDecimal.ZERO) "% 10% (Standard)" else "% 0% (Zero-rated)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("VAT Rate") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Percent, contentDescription = null, tint = KanakSecondaryText)
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vatRateDropdownExpanded) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = vatRateDropdownExpanded,
                        onDismissRequest = { vatRateDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("10% (Standard Bahrain VAT)") },
                            onClick = {
                                selectedVatRate = BigDecimal("0.10")
                                vatRateDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("0% (Zero-rated basic foodstuffs)") },
                            onClick = {
                                selectedVatRate = BigDecimal.ZERO
                                vatRateDropdownExpanded = false
                            }
                        )
                    }
                }

                // Product Photo Section matching Board 2
                Text(
                    text = "Product Photo",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, color = KanakPrimaryText)
                )

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left photo preview box
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(KanakSurfaceVariant)
                                .border(1.dp, KanakDivider, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!currentPhotoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = currentPhotoUrl,
                                    contentDescription = "Preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(4.dp)
                                )
                                if (isManualOverride) {
                                    IconButton(
                                        onClick = {
                                            manualPhotoUrl = null
                                            isManualOverride = false
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(24.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            } else {
                                Icon(
                                    Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = KanakSecondaryText,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Right 3 action buttons
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    manualPhotoUrl = null
                                    isManualOverride = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = KanakPrimaryBlue)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Use Automatic", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    pastedUrlInput = ""
                                    showPasteUrlDialog = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(14.dp), tint = KanakPrimaryBlue)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paste URL", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(14.dp), tint = KanakPrimaryBlue)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Stock Quantity (Read Only)
                OutlinedTextField(
                    value = (existing?.stockQty ?: BigDecimal.ZERO).stripTrailingZeros().toPlainString(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Stock Quantity (Read Only)") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = KanakSecondaryText)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledContainerColor = KanakSurfaceVariant,
                        unfocusedContainerColor = KanakSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = KanakError,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Primary Action
                Button(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            errorMessage = null

                            val cost = BahrainFinancials.parseBhd(costPriceStr)
                            val selling = BahrainFinancials.parseBhd(sellingPriceStr)

                            val res = if (existing != null) {
                                repository.updateProduct(
                                    productId = existing.productId,
                                    barcode = barcode,
                                    name = name,
                                    costPrice = cost,
                                    sellingPrice = selling,
                                    vatRate = selectedVatRate,
                                    photoUrl = if (isManualOverride) manualPhotoUrl else null
                                )
                            } else {
                                repository.createProduct(
                                    barcode = barcode,
                                    name = name,
                                    costPrice = cost,
                                    sellingPrice = selling,
                                    vatRate = selectedVatRate,
                                    photoUrl = if (isManualOverride) manualPhotoUrl else null
                                )
                            }

                            isSaving = false
                            res.onSuccess {
                                onNavigateBack()
                            }.onFailure { err ->
                                errorMessage = err.message ?: "Failed to save product"
                            }
                        }
                    },
                    enabled = !isSaving && name.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KanakPrimaryBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_product_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (existing != null) "Save Changes" else "Save Product",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }

    // Paste URL Dialog
    if (showPasteUrlDialog) {
        AlertDialog(
            onDismissRequest = { showPasteUrlDialog = false },
            title = { Text("Paste Image URL", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = pastedUrlInput,
                    onValueChange = { pastedUrlInput = it },
                    placeholder = { Text("https://example.com/photo.jpg") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (pastedUrlInput.isNotBlank()) {
                        manualPhotoUrl = pastedUrlInput.trim()
                        isManualOverride = true
                    }
                    showPasteUrlDialog = false
                }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPasteUrlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog matching Board 2 Screen 4
    if (showDeleteConfirm && existing != null) {
        KanakConfirmDialog(
            title = "Delete Product?",
            message = "Are you sure you want to delete \"${existing.name}\"?",
            warningBoxText = "This product can only be deleted if it is not used in any purchases, invoices or stock adjustments. Historical records cannot be compromised.",
            confirmText = "Delete",
            cancelText = "Cancel",
            isDestructive = true,
            icon = Icons.Outlined.Delete,
            onConfirm = {
                showDeleteConfirm = false
                scope.launch {
                    val res = repository.deleteProduct(existing.productId)
                    res.onSuccess {
                        onNavigateBack()
                    }.onFailure { err ->
                        errorMessage = err.message ?: "Cannot delete product"
                    }
                }
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
private fun SpecRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = KanakSecondaryText,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(color = KanakSecondaryText)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
                color = KanakPrimaryText
            )
        )
    }
}
