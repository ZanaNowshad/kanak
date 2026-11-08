package com.example.kanak.ui.invoices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.ui.components.EmptyStateView
import com.example.kanak.ui.components.InvoiceListItem
import com.example.kanak.ui.components.KanakFilterChip
import com.example.kanak.ui.components.KanakSearchField
import com.example.kanak.ui.components.KanakTopBar
import com.example.ui.theme.*

@Composable
fun InvoicesHistoryScreen(
    repository: KanakRepository,
    onNavigateToNewInvoice: () -> Unit,
    onNavigateToInvoiceDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val invoices by repository.invoices.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, POSTED, VOID

    val filteredInvoices = remember(invoices, searchQuery, selectedFilter) {
        var list = invoices
        if (selectedFilter == "POSTED") {
            list = list.filter { !it.isVoid }
        } else if (selectedFilter == "VOID") {
            list = list.filter { it.isVoid }
        } else if (selectedFilter == "PENDING") {
            list = list.filter { it.isPendingSync }
        }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.invoiceNumber.lowercase().contains(q) || it.customerName.lowercase().contains(q)
            }
        }
        list
    }

    Scaffold(
        topBar = {
            KanakTopBar(title = "Sales & Invoices")
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToNewInvoice,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Sale / Invoice", fontWeight = FontWeight.Bold) },
                containerColor = KanakPrimaryBlue,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("invoices_new_fab")
            )
        },
        containerColor = KanakBackground,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Live Connection and Sync Banner
            com.example.kanak.ui.components.SyncStatusBar(repository = repository)

            Spacer(modifier = Modifier.height(6.dp))

            // Unified Quick Sale / New Invoice Action Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = KanakSurface),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToNewInvoice() }
                    .testTag("sales_invoice_quick_banner")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = KanakAccentLight,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PointOfSale,
                                contentDescription = null,
                                tint = KanakPrimaryBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Start New Sale",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = KanakPrimaryText
                            )
                        )
                        Text(
                            text = "Walk-in, barcode scan, cash/card/credit & instant receipt",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = com.example.ui.theme.KanakSecondaryText,
                                fontSize = 11.sp
                            )
                        )
                    }

                    Button(
                        onClick = onNavigateToNewInvoice,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Sale", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            KanakSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search by invoice # or customer...",
                modifier = Modifier.testTag("invoices_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips (All, Posted, Pending Sync, Void)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KanakFilterChip(
                    label = "All (${invoices.size})",
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" }
                )
                KanakFilterChip(
                    label = "Posted (${invoices.count { !it.isVoid }})",
                    selected = selectedFilter == "POSTED",
                    onClick = { selectedFilter = "POSTED" }
                )
                KanakFilterChip(
                    label = "Pending (${invoices.count { it.isPendingSync }})",
                    selected = selectedFilter == "PENDING",
                    onClick = { selectedFilter = "PENDING" }
                )
                KanakFilterChip(
                    label = "Void (${invoices.count { it.isVoid }})",
                    selected = selectedFilter == "VOID",
                    onClick = { selectedFilter = "VOID" }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredInvoices.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.ReceiptLong,
                    title = "No Sales or Invoices Found",
                    message = "Tap '+ New Sale' above to start billing van customers."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredInvoices, key = { it.invoiceId }) { invoice ->
                        InvoiceListItem(
                            invoice = invoice,
                            onClick = { onNavigateToInvoiceDetail(invoice.invoiceId) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(88.dp))
                    }
                }
            }
        }
    }
}
