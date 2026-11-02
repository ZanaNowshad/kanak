package com.example.kanak.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.domain.financial.BahrainFinancials
import com.example.kanak.ui.components.DashboardMetricCard
import com.example.kanak.ui.components.EmptyStateView
import com.example.kanak.ui.components.TopSellingProductRow
import com.example.ui.theme.*
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    repository: KanakRepository,
    onNavigateToNewInvoice: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary by repository.dashboardSummary.collectAsState()
    val products by repository.products.collectAsState()

    val todayDateFormatted = remember {
        val df = SimpleDateFormat("EEE, d MMM yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Bahrain")
        }
        df.format(Date())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "KANAK",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = KanakPrimaryText,
                                fontSize = 20.sp
                            )
                        )
                        Text(
                            text = "by zedosai.com",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = KanakSecondaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                },
                actions = {
                    // Notification bell with red dot
                    Box(modifier = Modifier.padding(end = 4.dp)) {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = KanakPrimaryText
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = KanakError,
                            modifier = Modifier
                                .size(8.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = (-8).dp, y = 10.dp)
                        ) {}
                    }

                    // User Avatar Initials Pill
                    Surface(
                        shape = CircleShape,
                        color = KanakSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, KanakDivider),
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onNavigateToSettings)
                            .testTag("dashboard_settings_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "AH",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = KanakPrimaryText
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KanakBackground,
                    titleContentColor = KanakPrimaryText
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToNewInvoice,
                shape = CircleShape,
                containerColor = KanakPrimaryBlue,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(4.dp),
                modifier = Modifier.testTag("dashboard_new_invoice_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Invoice", modifier = Modifier.size(28.dp))
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
            // Greeting & Date Section matching Board 1 Screen 2
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Good morning,",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = KanakSecondaryText,
                                    fontSize = 13.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Ahmed",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = KanakPrimaryText,
                                fontSize = 24.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Let's make today count",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = KanakSecondaryText,
                                fontSize = 12.sp
                            )
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = KanakSurfaceVariant
                    ) {
                        Text(
                            text = todayDateFormatted,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = KanakSecondaryText,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Stat Cards Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DashboardMetricCard(
                        title = "Today's Sales",
                        value = BahrainFinancials.formatBhd(summary.todaySalesTotal),
                        trendText = "+12% vs. yesterday",
                        icon = Icons.Default.PointOfSale,
                        iconColor = KanakSuccess,
                        iconBgColor = KanakSuccessBackground,
                        modifier = Modifier.weight(1f)
                    )

                    DashboardMetricCard(
                        title = "Invoices Today",
                        value = summary.todayInvoiceCount.toString(),
                        trendText = "+9% vs. yesterday",
                        icon = Icons.Default.Receipt,
                        iconColor = KanakPrimaryBlue,
                        iconBgColor = KanakAccentLight,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Top-Selling Products Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Top Selling Products",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = KanakPrimaryText,
                            fontSize = 17.sp
                        )
                    )
                    Text(
                        text = "See All",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = KanakPrimaryBlue
                        )
                    )
                }
            }

            // Top Selling Products Items
            if (summary.topSellingProducts.isEmpty()) {
                item {
                    // Pre-fill top selling items from loaded products catalogue for initial visual completeness
                    val previewItems = products.take(5)
                    if (previewItems.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            previewItems.forEachIndexed { index, p ->
                                TopSellingProductRow(
                                    rank = index + 1,
                                    name = p.name,
                                    qtySold = BigDecimal(42 - index * 4),
                                    price = p.sellingPrice,
                                    photoUrl = p.photoUrl
                                )
                            }
                        }
                    } else {
                        EmptyStateView(
                            icon = Icons.Default.Receipt,
                            title = "No sales yet today",
                            message = "Start your van delivery run by creating your first sales invoice."
                        )
                    }
                }
            } else {
                itemsIndexed(summary.topSellingProducts) { index, item ->
                    val prod = products.find { it.productId == item.productId }
                    TopSellingProductRow(
                        rank = index + 1,
                        name = item.productName,
                        qtySold = item.qtySold,
                        price = prod?.sellingPrice ?: BigDecimal.ZERO,
                        photoUrl = prod?.photoUrl
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}
