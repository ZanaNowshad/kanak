package com.example.kanak.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.domain.financial.BahrainFinancials
import com.example.kanak.domain.models.User
import com.example.kanak.ui.components.KanakTopBar
import com.example.ui.theme.*
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

enum class DateRangeType {
    ONE_DAY,
    ONE_MONTH,
    CUSTOM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    repository: KanakRepository,
    currentUser: User,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val purchases by repository.purchases.collectAsState()
    val invoices by repository.invoices.collectAsState()

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Bahrain") } }
    val monthFormat = remember { SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Bahrain") } }
    val todayStr = remember { dateFormat.format(Date()) }
    val currentMonthStr = remember { monthFormat.format(Date()) }

    var selectedRangeType by remember { mutableStateOf(DateRangeType.ONE_DAY) }
    var selectedSingleDate by remember { mutableStateOf(todayStr) }
    var selectedMonth by remember { mutableStateOf(currentMonthStr) }
    var startDateInput by remember { mutableStateOf(todayStr) }
    var endDateInput by remember { mutableStateOf(todayStr) }

    // Date range calculation logic
    val (effectiveStartDate, effectiveEndDate) = remember(
        selectedRangeType, selectedSingleDate, selectedMonth, startDateInput, endDateInput
    ) {
        when (selectedRangeType) {
            DateRangeType.ONE_DAY -> Pair(selectedSingleDate, selectedSingleDate)
            DateRangeType.ONE_MONTH -> Pair("$selectedMonth-01", "$selectedMonth-31")
            DateRangeType.CUSTOM -> Pair(startDateInput, endDateInput)
        }
    }

    // Filter purchases & sales for range
    val purchaseTotals = remember(purchases, effectiveStartDate, effectiveEndDate) {
        purchases.filter { p ->
            !p.isReversed && p.date.take(10) >= effectiveStartDate && p.date.take(10) <= effectiveEndDate
        }.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.cost.multiply(p.qty)) }
    }

    val salesTotals = remember(invoices, effectiveStartDate, effectiveEndDate) {
        invoices.filter { inv ->
            !inv.isVoid && inv.date.take(10) >= effectiveStartDate && inv.date.take(10) <= effectiveEndDate
        }.fold(BigDecimal.ZERO) { acc, inv -> acc.add(inv.total) }
    }

    val profitMarginAmount = remember(salesTotals, purchaseTotals) {
        salesTotals.subtract(purchaseTotals)
    }

    val profitMarginPercentage = remember(salesTotals, profitMarginAmount) {
        if (salesTotals.compareTo(BigDecimal.ZERO) > 0) {
            profitMarginAmount.multiply(BigDecimal("100"))
                .divide(salesTotals, 2, java.math.RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
    }

    Scaffold(
        topBar = {
            KanakTopBar(
                title = "Financial Reports",
                onBack = onNavigateBack
            )
        },
        containerColor = KanakBackground,
        modifier = modifier
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
            // Date Range Selection Header & Chips
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = KanakSurface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.DateRange, contentDescription = null, tint = KanakPrimaryBlue)
                        Text(
                            text = "Select Report Range",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = KanakPrimaryText
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    PrimaryTabRow(
                        selectedTabIndex = selectedRangeType.ordinal,
                        containerColor = KanakSurfaceVariant,
                        contentColor = KanakPrimaryBlue,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    ) {
                        Tab(
                            selected = selectedRangeType == DateRangeType.ONE_DAY,
                            onClick = { selectedRangeType = DateRangeType.ONE_DAY },
                            text = { Text("One Day", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        )
                        Tab(
                            selected = selectedRangeType == DateRangeType.ONE_MONTH,
                            onClick = { selectedRangeType = DateRangeType.ONE_MONTH },
                            text = { Text("One Month", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        )
                        Tab(
                            selected = selectedRangeType == DateRangeType.CUSTOM,
                            onClick = { selectedRangeType = DateRangeType.CUSTOM },
                            text = { Text("Custom Range", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Inputs based on selected range
                    when (selectedRangeType) {
                        DateRangeType.ONE_DAY -> {
                            OutlinedTextField(
                                value = selectedSingleDate,
                                onValueChange = { selectedSingleDate = it },
                                label = { Text("Date (YYYY-MM-DD)") },
                                leadingIcon = { Icon(Icons.Default.Today, contentDescription = null, tint = KanakPrimaryBlue) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = KanakPrimaryBlue,
                                    unfocusedBorderColor = KanakDivider
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("report_date_input")
                            )
                        }
                        DateRangeType.ONE_MONTH -> {
                            OutlinedTextField(
                                value = selectedMonth,
                                onValueChange = { selectedMonth = it },
                                label = { Text("Month (YYYY-MM)") },
                                leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = KanakPrimaryBlue) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = KanakPrimaryBlue,
                                    unfocusedBorderColor = KanakDivider
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("report_month_input")
                            )
                        }
                        DateRangeType.CUSTOM -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = startDateInput,
                                    onValueChange = { startDateInput = it },
                                    label = { Text("Start Date") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = KanakPrimaryBlue,
                                        unfocusedBorderColor = KanakDivider
                                    ),
                                    modifier = Modifier.weight(1f).testTag("report_start_date_input")
                                )
                                OutlinedTextField(
                                    value = endDateInput,
                                    onValueChange = { endDateInput = it },
                                    label = { Text("End Date") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = KanakPrimaryBlue,
                                        unfocusedBorderColor = KanakDivider
                                    ),
                                    modifier = Modifier.weight(1f).testTag("report_end_date_input")
                                )
                            }
                        }
                    }
                }
            }

            // Summary Heading
            Text(
                text = "Financial Report Summary ($effectiveStartDate to $effectiveEndDate)",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = KanakPrimaryText
                )
            )

            // METRIC 1: PURCHASE TOTALS
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = KanakSurface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = KanakAccentLight,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = KanakPrimaryBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Purchase Totals",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = KanakSecondaryText,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = BahrainFinancials.formatBhd(purchaseTotals),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = KanakPrimaryText
                            ),
                            modifier = Modifier.testTag("report_purchase_totals_text")
                        )
                    }
                }
            }

            // METRIC 2: SALES TOTALS
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = KanakSurface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = KanakAccentLight,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PointOfSale,
                                contentDescription = null,
                                tint = KanakPrimaryBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sales Totals",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = KanakSecondaryText,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = BahrainFinancials.formatBhd(salesTotals),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = KanakPrimaryBlue
                            ),
                            modifier = Modifier.testTag("report_sales_totals_text")
                        )
                    }
                }
            }

            // METRIC 3: PROFIT MARGIN
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = KanakSurface),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (profitMarginAmount >= BigDecimal.ZERO) KanakSuccessBackground else KanakErrorBackground,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (profitMarginAmount >= BigDecimal.ZERO) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = if (profitMarginAmount >= BigDecimal.ZERO) KanakSuccess else KanakError,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Profit Margin",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = KanakSecondaryText,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${BahrainFinancials.formatBhd(profitMarginAmount)} (${profitMarginPercentage.toPlainString()}%)",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (profitMarginAmount >= BigDecimal.ZERO) KanakSuccess else KanakError
                            ),
                            modifier = Modifier.testTag("report_profit_margin_text")
                        )
                    }
                }
            }
        }
    }
}
