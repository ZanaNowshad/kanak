package com.example.kanak.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.domain.financial.BahrainFinancials
import com.example.kanak.domain.models.Invoice
import com.example.kanak.domain.models.User
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
    currentUser: User,
    isMasterAdmin: Boolean = false,
    onNavigateToNewInvoice: () -> Unit,
    onNavigateToExpressSales: () -> Unit = onNavigateToNewInvoice,
    onNavigateToSettings: () -> Unit,
    onNavigateToDailySalesSummary: () -> Unit,
    onNavigateToFleet: () -> Unit,
    onNavigateToTeam: () -> Unit,
    onNavigateToProducts: () -> Unit,
    onNavigateToCustomers: () -> Unit,
    onNavigateToAdminLicensePortal: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val summary by repository.dashboardSummary.collectAsState()
    val products by repository.products.collectAsState()
    val vans by repository.vans.collectAsState()
    val invoices by repository.invoices.collectAsState()

    val isOwner = currentUser.isOwner || isMasterAdmin
    val isSalesperson = currentUser.isSalesperson && !isOwner

    val todayDateFormatted = remember {
        val df = SimpleDateFormat("EEE, d MMM yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Bahrain")
        }
        df.format(Date())
    }

    // Salesperson-specific metrics (filtered by assigned van)
    val myVanId = currentUser.assignedVanId ?: vans.firstOrNull()?.vanId
    val myVanInvoicesToday = remember(invoices, myVanId) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Bahrain") }.format(Date())
        invoices.filter { !it.isVoid && it.date.startsWith(todayStr) && (myVanId == null || it.vanId == myVanId) }
    }
    val myVanSalesToday = remember(myVanInvoicesToday) {
        myVanInvoicesToday.fold(BigDecimal.ZERO) { acc, inv -> acc.add(inv.total) }
    }
    val myVanCashSalesToday = remember(myVanInvoicesToday) {
        myVanInvoicesToday.filter { it.paymentType.equals(Invoice.PAYMENT_CASH, ignoreCase = true) }
            .fold(BigDecimal.ZERO) { acc, inv -> acc.add(inv.total) }
    }
    val myVanCreditSalesToday = remember(myVanInvoicesToday) {
        myVanInvoicesToday.filter { it.paymentType.equals(Invoice.PAYMENT_CREDIT, ignoreCase = true) }
            .fold(BigDecimal.ZERO) { acc, inv -> acc.add(inv.total) }
    }
    val outstandingCreditTotal = remember(invoices) {
        invoices.filter { !it.isVoid && it.isCredit }.fold(BigDecimal.ZERO) { acc, inv -> acc.add(inv.balanceDue) }
    }
    val lowStockProducts = remember(products) {
        products.filter { it.stockQty <= BigDecimal("10") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = repository.getBusinessName(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                color = KanakPrimaryText,
                                fontSize = 16.sp
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = if (isOwner) "Business Owner & Fleet Controller" else "Salesperson & Driver" + (currentUser.assignedVanCode?.let { " • $it" } ?: ""),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isOwner) Color(0xFFD97706) else KanakPrimaryBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                },
                actions = {
                    // Admin Portal Button for zanabalmuhamed@gmail.com
                    if (isMasterAdmin) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEF3C7),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onNavigateToAdminLicensePortal?.invoke() }
                                .testTag("topbar_admin_portal_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PLATFORM ADMIN", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF92400E), fontWeight = FontWeight.Bold, fontSize = 10.sp))
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Avatar button navigating to settings
                    Surface(
                        shape = CircleShape,
                        color = if (isMasterAdmin) Color(0xFFFEF3C7) else KanakAccentLight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isMasterAdmin) Color(0xFFF59E0B) else KanakPrimaryBlue.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onNavigateToSettings)
                            .testTag("dashboard_settings_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = currentUser.initials,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMasterAdmin) Color(0xFF92400E) else KanakPrimaryBlue
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
            // Master Admin Banner
            if (isMasterAdmin) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onNavigateToAdminLicensePortal?.invoke() }
                            .testTag("dashboard_master_admin_banner"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(shape = CircleShape, color = Color(0xFFD97706), modifier = Modifier.size(40.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("PLATFORM LICENSING CONTROL", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black, color = Color(0xFF92400E), fontSize = 12.sp))
                                Text("Manage company registrations & issue cryptographic activation keys", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF78350F), fontSize = 11.sp))
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF92400E))
                        }
                    }
                }
            }

            // Always-visible Live Connection and Synchronization Status Bar
            item {
                com.example.kanak.ui.components.SyncStatusBar(repository = repository)
            }

            // Salesperson Assigned Van Banner & Express Sales Hero
            if (isSalesperson) {
                item {
                    val assignedVan = vans.find { it.vanId == currentUser.assignedVanId } ?: vans.firstOrNull()
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakAccentLight),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, KanakPrimaryBlue.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(shape = RoundedCornerShape(10.dp), color = KanakPrimaryBlue, modifier = Modifier.size(44.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (assignedVan != null) "Assigned Van: ${assignedVan.vanCode} • ${assignedVan.vanName}" else "No Van Assigned",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryBlue)
                                )
                                Text(
                                    text = "Operator: ${currentUser.displayName} • Active Route Shift",
                                    style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp)
                                )
                            }
                        }
                    }
                }

                // Express Sales Mode Hero Action for Salesperson
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakPrimaryBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onNavigateToExpressSales)
                            .testTag("express_sales_hero_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(44.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "START EXPRESS SALE",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = Color.White, fontSize = 15.sp)
                                )
                                Text(
                                    text = "Rapid workflow: Customer → Products → Keypad → Print",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }

            // Greeting & Session Date
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WbSunny, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Good day,", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 12.sp))
                        }
                        Text(
                            text = currentUser.displayName,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText, fontSize = 22.sp)
                        )
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = KanakSurfaceVariant) {
                        Text(todayDateFormatted, style = MaterialTheme.typography.labelSmall.copy(color = KanakSecondaryText, fontWeight = FontWeight.Medium, fontSize = 11.sp), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }

            // Role-Specific KPI Cards
            item {
                if (isOwner) {
                    // Owner 4-Card Overview + Financials
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DashboardMetricCard(
                                title = "Today's Revenue",
                                value = BahrainFinancials.formatBhd(summary.todaySalesTotal),
                                trendText = "Fleet Total",
                                icon = Icons.Default.PointOfSale,
                                iconColor = KanakSuccess,
                                iconBgColor = KanakSuccessBackground,
                                modifier = Modifier.weight(1f)
                            )
                            DashboardMetricCard(
                                title = "Invoices Today",
                                value = summary.todayInvoiceCount.toString(),
                                trendText = "All Vans",
                                icon = Icons.Default.Receipt,
                                iconColor = KanakPrimaryBlue,
                                iconBgColor = KanakAccentLight,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DashboardMetricCard(
                                title = "Total VAT (10%)",
                                value = BahrainFinancials.formatBhd(summary.todayVatTotal),
                                trendText = "NBR Liability",
                                icon = Icons.Default.AccountBalance,
                                iconColor = KanakPrimaryBlue,
                                iconBgColor = KanakAccentLight,
                                modifier = Modifier.weight(1f)
                            )
                            DashboardMetricCard(
                                title = "Outstanding Credit",
                                value = BahrainFinancials.formatBhd(outstandingCreditTotal),
                                trendText = "Receivables",
                                icon = Icons.Default.CreditCard,
                                iconColor = Color(0xFFDC2626),
                                iconBgColor = Color(0xFFFEE2E2),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    // Salesperson 4-Card Overview (Van Sales, Invoices, Cash, Credit)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DashboardMetricCard(
                                title = "My Van Sales",
                                value = BahrainFinancials.formatBhd(myVanSalesToday),
                                trendText = "${currentUser.assignedVanCode ?: "Van"} Today",
                                icon = Icons.Default.PointOfSale,
                                iconColor = KanakSuccess,
                                iconBgColor = KanakSuccessBackground,
                                modifier = Modifier.weight(1f)
                            )
                            DashboardMetricCard(
                                title = "Invoices Today",
                                value = myVanInvoicesToday.size.toString(),
                                trendText = "Route Invoices",
                                icon = Icons.Default.Receipt,
                                iconColor = KanakPrimaryBlue,
                                iconBgColor = KanakAccentLight,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DashboardMetricCard(
                                title = "Cash Collected",
                                value = BahrainFinancials.formatBhd(myVanCashSalesToday),
                                trendText = "Physical Cash",
                                icon = Icons.Default.Payments,
                                iconColor = KanakSuccess,
                                iconBgColor = KanakSuccessBackground,
                                modifier = Modifier.weight(1f)
                            )
                            DashboardMetricCard(
                                title = "Credit Invoiced",
                                value = BahrainFinancials.formatBhd(myVanCreditSalesToday),
                                trendText = "Postpaid",
                                icon = Icons.Default.CreditCard,
                                iconColor = Color(0xFFD97706),
                                iconBgColor = Color(0xFFFEF3C7),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Low Stock Alert for Owner
            if (isOwner && lowStockProducts.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = KanakError, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("LOW INVENTORY ALERT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = KanakError))
                                Text("${lowStockProducts.size} product(s) have under 10 units in stock. Check purchasing.", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF991B1B), fontSize = 11.sp))
                            }
                        }
                    }
                }
            }

            // Quick Actions Hub Grid
            item {
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionButton(
                        title = if (isSalesperson) "Express Sale" else "New Sale",
                        subtitle = if (isSalesperson) "Rapid 1-Tap" else "Issue Invoice",
                        icon = if (isSalesperson) Icons.Default.FlashOn else Icons.Default.AddShoppingCart,
                        iconBg = KanakPrimaryBlue,
                        onClick = if (isSalesperson) onNavigateToExpressSales else onNavigateToNewInvoice,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionButton(
                        title = "Daily Summary",
                        subtitle = "VAT & Sales",
                        icon = Icons.Default.Assessment,
                        iconBg = Color(0xFF16A34A),
                        onClick = onNavigateToDailySalesSummary,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isOwner) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickActionButton(
                            title = "Fleet & Vans",
                            subtitle = "Transfers & Vans",
                            icon = Icons.Default.DirectionsBus,
                            iconBg = Color(0xFFD97706),
                            onClick = onNavigateToFleet,
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionButton(
                            title = "Team Members",
                            subtitle = "Invites & Roles",
                            icon = Icons.Default.GroupAdd,
                            iconBg = Color(0xFF7C3AED),
                            onClick = onNavigateToTeam,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Fleet Performance Section (Owner Dashboard - Section 8.1 of Spec)
            if (isOwner && summary.vanPerformances.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fleet Performance (Today)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                        )
                        Text(
                            text = "Manage Fleet",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, color = KanakPrimaryBlue),
                            modifier = Modifier.clickable { onNavigateToFleet() }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakSurface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            summary.vanPerformances.forEach { vp ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = KanakAccentLight,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(vp.vanCode, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                            Text(vp.salespersonName, style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp))
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "BHD ${BahrainFinancials.formatBhd(vp.todaySales)}",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                                        )
                                        Text(
                                            text = "${vp.todayInvoicesCount} invoices",
                                            style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Top Selling Products Section
            item {
                Text(
                    text = "Top Selling Products",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                )
            }

            if (summary.topSellingProducts.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Default.Receipt,
                        title = "No sales recorded yet today",
                        message = "Start your wholesale van delivery run by posting your first customer sales invoice."
                    )
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
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = KanakSurface),
        border = CardDefaults.outlinedCardBorder(),
        onClick = onClick,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = iconBg, modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText))
                Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp))
            }
        }
    }
}
