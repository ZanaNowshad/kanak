package com.example.kanak.licensing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanak.ui.components.KanakSearchField
import com.example.kanak.ui.components.KanakTopBar
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AdminLicensePortalScreen(
    licenseManager: LicenseManager,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var registrations by remember { mutableStateOf<List<CompanyProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, PENDING, ACTIVATED

    // Quick Code Generator state
    var quickReqId by remember { mutableStateOf("") }
    var quickCompName by remember { mutableStateOf("") }
    var quickResultCode by remember { mutableStateOf<String?>(null) }

    // Active code dialog state
    var dialogCode by remember { mutableStateOf<String?>(null) }
    var dialogCustomer by remember { mutableStateOf<CompanyProfile?>(null) }

    fun refreshList() {
        scope.launch {
            isLoading = true
            val res = licenseManager.fetchAllRegistrations()
            isLoading = false
            res.onSuccess {
                registrations = it
            }.onFailure {
                Toast.makeText(context, "Error fetching registrations: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshList()
    }

    val filteredList = remember(registrations, searchQuery, selectedFilter) {
        var list = registrations
        if (selectedFilter == "PENDING") {
            list = list.filter { !it.isActivated }
        } else if (selectedFilter == "ACTIVATED") {
            list = list.filter { it.isActivated }
        }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.companyName.lowercase().contains(q) ||
                        it.requestId.lowercase().contains(q) ||
                        it.ownerName.lowercase().contains(q) ||
                        it.email.lowercase().contains(q)
            }
        }
        list
    }

    Scaffold(
        topBar = {
            KanakTopBar(
                title = "Master License Portal",
                subtitle = "'Kanak' by zedosai.com · ${LicenseManager.MASTER_ADMIN_EMAIL}",
                onBack = onNavigateBack,
                actions = {
                    IconButton(onClick = { refreshList() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = KanakPrimaryBlue)
                    }
                }
            )
        },
        containerColor = KanakBackground,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))

                // Quick Code Generator Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(KanakAccentLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.VpnKey, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(20.dp))
                            }
                            Text(
                                text = "Quick Activation Code Generator",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                            )
                        }

                        Text(
                            text = "Compute valid activation code for any customer offline or on-demand:",
                            style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                        )

                        OutlinedTextField(
                            value = quickReqId,
                            onValueChange = { quickReqId = it },
                            label = { Text("License Request ID (e.g. KNK-REQ-XXXX-XXXX)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = quickCompName,
                            onValueChange = { quickCompName = it },
                            label = { Text("Company / Business Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Button(
                            onClick = {
                                if (quickReqId.isNotBlank() && quickCompName.isNotBlank()) {
                                    quickResultCode = licenseManager.computeActivationCode(quickReqId, quickCompName)
                                } else {
                                    Toast.makeText(context, "Please enter both Request ID and Company Name", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.align(Alignment.End),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue)
                        ) {
                            Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Compute Code")
                        }

                        if (quickResultCode != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = KanakSuccessBackground,
                                border = CardDefaults.outlinedCardBorder(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Generated Activation Code:", style = MaterialTheme.typography.labelSmall.copy(color = KanakSuccess))
                                        Text(
                                            text = quickResultCode ?: "",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = KanakSuccess
                                            )
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Activation Code", quickResultCode))
                                            Toast.makeText(context, "Code copied to clipboard!", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = KanakSuccess)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                // Search and Filters
                KanakSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search company, contact, or request ID..."
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text("All (${registrations.size})") }
                    )
                    FilterChip(
                        selected = selectedFilter == "PENDING",
                        onClick = { selectedFilter = "PENDING" },
                        label = { Text("Pending (${registrations.count { !it.isActivated }})") }
                    )
                    FilterChip(
                        selected = selectedFilter == "ACTIVATED",
                        onClick = { selectedFilter = "ACTIVATED" },
                        label = { Text("Active (${registrations.count { it.isActivated }})") }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "COMPANY REGISTRATIONS (${filteredList.size})",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = KanakSecondaryText)
                )
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = KanakPrimaryBlue)
                    }
                }
            } else if (filteredList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Inbox, contentDescription = null, tint = KanakSecondaryText, modifier = Modifier.size(36.dp))
                            Text("No registrations found", style = MaterialTheme.typography.bodyMedium.copy(color = KanakSecondaryText))
                        }
                    }
                }
            } else {
                items(filteredList, key = { it.requestId }) { item ->
                    CompanyRegistrationItemCard(
                        item = item,
                        onApprove = {
                            scope.launch {
                                val code = licenseManager.computeActivationCode(item.requestId, item.companyName)
                                licenseManager.adminActivateCompany(item.requestId, item.companyName)
                                dialogCustomer = item
                                dialogCode = code
                                refreshList()
                            }
                        },
                        onSendCode = {
                            val code = item.activationCode.ifBlank {
                                licenseManager.computeActivationCode(item.requestId, item.companyName)
                            }
                            dialogCustomer = item
                            dialogCode = code
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Code Delivery Dialog
    if (dialogCode != null && dialogCustomer != null) {
        val cust = dialogCustomer!!
        val code = dialogCode!!

        AlertDialog(
            onDismissRequest = { dialogCode = null },
            shape = RoundedCornerShape(16.dp),
            containerColor = KanakSurface,
            title = {
                Text(
                    text = "Activation Code Ready",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Company: ${cust.companyName}\nOwner: ${cust.ownerName} (${cust.phone})",
                        style = MaterialTheme.typography.bodyMedium.copy(color = KanakSecondaryText)
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = KanakSuccessBackground,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("LIFETIME ACTIVATION CODE", style = MaterialTheme.typography.labelSmall.copy(color = KanakSuccess, fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = code,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = KanakSuccess
                                )
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Activation Code", code))
                                Toast.makeText(context, "Code copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Code")
                        }

                        OutlinedButton(
                            onClick = {
                                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:${cust.email}")
                                    putExtra(Intent.EXTRA_SUBJECT, "Your Kanak Van Sales Activation Code - ${cust.companyName}")
                                    putExtra(Intent.EXTRA_TEXT, "Dear ${cust.ownerName},\n\nThank you for your payment. Your official lifetime Activation Code for Kanak is:\n\n$code\n\nEnter this code in the app to unlock unlimited access.\n\nBest regards,\nKanak Administration")
                                }
                                try {
                                    context.startActivity(emailIntent)
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Email Code")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogCode = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun CompanyRegistrationItemCard(
    item: CompanyProfile,
    onApprove: () -> Unit,
    onSendCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = KanakSurface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = item.companyName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                    )
                    Text(
                        text = "${item.ownerName} · ${item.phone}",
                        style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (item.isActivated) KanakSuccessBackground else KanakWarning.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = if (item.isActivated) "ACTIVATED" else "PENDING PAYMENT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (item.isActivated) KanakSuccess else KanakWarning
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Request ID: ${item.requestId} · Email: ${item.email}",
                style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!item.isActivated) {
                    Button(
                        onClick = onApprove,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KanakSuccess)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Approve & Generate Code")
                    }
                } else {
                    OutlinedButton(
                        onClick = onSendCode,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("View / Send Code")
                    }
                }
            }
        }
    }
}
