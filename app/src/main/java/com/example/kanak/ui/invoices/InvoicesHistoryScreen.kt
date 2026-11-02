package com.example.kanak.ui.invoices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.ui.components.EmptyStateView
import com.example.kanak.ui.components.InvoiceListItem
import com.example.kanak.ui.components.KanakFilterChip
import com.example.kanak.ui.components.KanakSearchField
import com.example.kanak.ui.components.KanakTopBar
import com.example.ui.theme.KanakBackground
import com.example.ui.theme.KanakPrimaryBlue

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
            KanakTopBar(title = "Invoices History")
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToNewInvoice,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Invoice", fontWeight = FontWeight.SemiBold) },
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
            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar
            KanakSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search by invoice # or customer...",
                modifier = Modifier.testTag("invoices_search_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips (All, Posted, Void)
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
                    label = "Void (${invoices.count { it.isVoid }})",
                    selected = selectedFilter == "VOID",
                    onClick = { selectedFilter = "VOID" }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredInvoices.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.ReceiptLong,
                    title = "No Invoices Found",
                    message = "Tap '+ New Invoice' to start billing van customers."
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
