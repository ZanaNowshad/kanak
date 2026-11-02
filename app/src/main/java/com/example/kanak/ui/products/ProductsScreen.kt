package com.example.kanak.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.ui.components.EmptyStateView
import com.example.kanak.ui.components.KanakFilterChip
import com.example.kanak.ui.components.KanakSearchField
import com.example.kanak.ui.components.ProductListItem
import com.example.ui.theme.*

@Composable
fun ProductsScreen(
    repository: KanakRepository,
    onNavigateToAddProduct: () -> Unit,
    onNavigateToEditProduct: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val products by repository.products.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All", "Beverages", "Snacks", "Dairy", "Household")

    val filteredProducts = remember(products, searchQuery, selectedCategory) {
        var list = products
        if (selectedCategory != "All") {
            list = list.filter {
                when (selectedCategory) {
                    "Beverages" -> it.name.contains("juice", ignoreCase = true) || it.name.contains("water", ignoreCase = true) || it.name.contains("cola", ignoreCase = true)
                    "Snacks" -> it.name.contains("biscuit", ignoreCase = true) || it.name.contains("chips", ignoreCase = true) || it.name.contains("snack", ignoreCase = true)
                    "Dairy" -> it.name.contains("milk", ignoreCase = true) || it.name.contains("butter", ignoreCase = true) || it.name.contains("cheese", ignoreCase = true)
                    "Household" -> it.name.contains("wash", ignoreCase = true) || it.name.contains("powder", ignoreCase = true) || it.name.contains("soap", ignoreCase = true)
                    else -> true
                }
            }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) || it.barcode.lowercase().contains(q)
            }
        }
        list
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddProduct,
                shape = CircleShape,
                containerColor = KanakPrimaryBlue,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(4.dp),
                modifier = Modifier.testTag("add_product_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Product", modifier = Modifier.size(28.dp))
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
            Spacer(modifier = Modifier.height(8.dp))

            // Screen Title
            Text(
                text = "Products",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = KanakPrimaryText,
                    fontSize = 26.sp
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Modern Search Field with Scanner Action
            KanakSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search products by name or barcode...",
                onScanClick = {}
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips Horizontal Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    KanakFilterChip(
                        label = cat,
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Products List
            if (filteredProducts.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Inventory2,
                    title = if (searchQuery.isEmpty()) "No Products in Catalogue" else "No Matching Products",
                    message = if (searchQuery.isEmpty())
                        "Tap '+ Add Product' to record your first wholesale SKU."
                    else
                        "Try a different barcode or product name."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredProducts, key = { it.productId }) { product ->
                        ProductListItem(
                            product = product,
                            onClick = { onNavigateToEditProduct(product.productId) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}
