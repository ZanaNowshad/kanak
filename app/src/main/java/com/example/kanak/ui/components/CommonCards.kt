package com.example.kanak.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.kanak.domain.financial.BahrainFinancials
import com.example.kanak.domain.models.Customer
import com.example.kanak.domain.models.Invoice
import com.example.kanak.domain.models.Product
import com.example.ui.theme.*
import java.math.BigDecimal

/**
 * Top App Bar matching KANAK Design Language
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KanakTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = KanakPrimaryText,
                        fontSize = 20.sp
                    )
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = KanakSecondaryText,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("topbar_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = KanakPrimaryText
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = KanakBackground,
            titleContentColor = KanakPrimaryText
        )
    )
}

/**
 * Modern Search Field with optional barcode scanner action icon
 */
@Composable
fun KanakSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search...",
    onScanClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium.copy(color = KanakSecondaryText)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search",
                tint = KanakSecondaryText,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = KanakSecondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (onScanClick != null) {
                    IconButton(onClick = onScanClick) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, KanakDivider),
                            color = Color.Transparent,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.QrCodeScanner,
                                    contentDescription = "Scan Barcode",
                                    tint = KanakPrimaryBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = KanakSurface,
            unfocusedContainerColor = KanakSurface,
            focusedBorderColor = KanakPrimaryBlue,
            unfocusedBorderColor = KanakDivider
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("kanak_search_field")
    )
}

/**
 * Filter Chip for Categories & Lists
 */
@Composable
fun KanakFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) KanakPrimaryBlue else KanakSurface,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, KanakDivider),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) Color.White else KanakPrimaryText
            ),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

/**
 * Dashboard Metric Card matching Board 1 Screen 2
 */
@Composable
fun DashboardMetricCard(
    title: String,
    value: String,
    trendText: String,
    icon: ImageVector,
    iconColor: Color = KanakSuccess,
    iconBgColor: Color = KanakSuccessBackground,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.testTag("stat_card_${title.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KanakSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = iconBgColor,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = KanakSecondaryText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = KanakPrimaryText,
                    fontSize = 20.sp,
                    letterSpacing = (-0.5).sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Trend indicator pill
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = KanakSuccessBackground
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropUp,
                        contentDescription = null,
                        tint = KanakSuccess,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = trendText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = KanakSuccess,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Top Selling Product Row item matching Board 1 Screen 2
 */
@Composable
fun TopSellingProductRow(
    rank: Int,
    name: String,
    qtySold: BigDecimal,
    price: BigDecimal,
    photoUrl: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = KanakSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank Circle
            Surface(
                shape = CircleShape,
                color = KanakSurfaceVariant,
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = rank.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = KanakSecondaryText
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Product Image
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = KanakSurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha = 0.10f)),
                modifier = Modifier.size(46.dp)
            ) {
                if (!photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Inventory2,
                            contentDescription = null,
                            tint = KanakSecondaryText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = KanakPrimaryText
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${qtySold.stripTrailingZeros().toPlainString()} units",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = KanakSecondaryText,
                        fontSize = 12.sp
                    )
                )
            }

            Text(
                text = BahrainFinancials.formatBhd(price),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = KanakPrimaryText
                )
            )
        }
    }
}

/**
 * Product List Item Card matching Board 1 Screen 3 (Products List)
 */
@Composable
fun ProductListItem(
    product: Product,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("product_item_${product.productId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = KanakSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Product photo
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = KanakSurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha = 0.10f)),
                modifier = Modifier.size(54.dp)
            ) {
                if (!product.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = product.photoUrl,
                        contentDescription = product.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = product.name.take(2).uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = KanakSecondaryText
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = KanakPrimaryText
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (product.barcode.isNotBlank()) {
                    Text(
                        text = product.barcode,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = KanakSecondaryText,
                            fontSize = 11.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = BahrainFinancials.formatBhd(product.sellingPrice),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = KanakPrimaryText
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = KanakSurfaceVariant
                    ) {
                        Text(
                            text = if (product.vatRate > BigDecimal.ZERO) "VAT 10%" else "VAT 0%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = KanakSecondaryText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (trailingContent != null) {
                trailingContent()
            } else {
                // Stock Badge matching design board
                val isOutOfStock = product.stockQty <= BigDecimal.ZERO
                val isLowStock = product.stockQty > BigDecimal.ZERO && product.stockQty < BigDecimal("15")

                val badgeBg = when {
                    isOutOfStock -> KanakErrorBackground
                    isLowStock -> KanakWarningBackground
                    else -> KanakSuccessBackground
                }
                val badgeColor = when {
                    isOutOfStock -> KanakError
                    isLowStock -> KanakWarning
                    else -> KanakSuccess
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = badgeBg,
                    modifier = Modifier.widthIn(min = 60.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = product.stockQty.stripTrailingZeros().toPlainString(),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        )
                        Text(
                            text = if (isOutOfStock) "out" else "in stock",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = badgeColor,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Customer List Item Card matching Board 3 Screen 1
 */
@Composable
fun CustomerListItem(
    customer: Customer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outstandingCredit: BigDecimal = BigDecimal.ZERO
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("customer_item_${customer.customerId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = KanakSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Blue Storefront Icon
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (outstandingCredit > BigDecimal.ZERO) KanakWarningBackground else KanakAccentLight,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Storefront,
                        contentDescription = null,
                        tint = if (outstandingCredit > BigDecimal.ZERO) KanakWarning else KanakPrimaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = customer.businessName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = KanakPrimaryText
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (customer.contact.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = customer.contact,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = KanakSecondaryText
                        ),
                        maxLines = 1
                    )
                }
                if (customer.phone.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = customer.phone,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = KanakSecondaryText
                        ),
                        maxLines = 1
                    )
                }

                if (outstandingCredit > BigDecimal.ZERO) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = KanakWarningBackground
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CreditCard,
                                contentDescription = null,
                                tint = KanakWarning,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Credit Due: ${BahrainFinancials.formatBhd(outstandingCredit)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = KanakWarning,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = KanakSecondaryText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Invoice List Item Card matching Board 5 Screen 2
 */
@Composable
fun InvoiceListItem(
    invoice: Invoice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("invoice_item_${invoice.invoiceId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = KanakSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = invoice.invoiceNumber,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = KanakPrimaryText
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (invoice.isVoid) KanakErrorBackground else KanakSuccessBackground
                    ) {
                        Text(
                            text = invoice.status,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (invoice.isVoid) KanakError else KanakSuccess
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (!invoice.isVoid) {
                        Spacer(modifier = Modifier.width(6.dp))
                        if (invoice.isCredit) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (invoice.isPaid) KanakSuccessBackground else KanakWarningBackground
                            ) {
                                Text(
                                    text = if (invoice.isPaid) "CREDIT (PAID)" else "UNPAID CREDIT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (invoice.isPaid) KanakSuccess else KanakWarning,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = KanakAccentLight
                            ) {
                                Text(
                                    text = "CASH",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = KanakPrimaryBlue,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = invoice.customerName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = KanakPrimaryText
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = invoice.date.take(16).replace("T", " "),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = KanakSecondaryText,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when {
                            invoice.isSyncConflict -> KanakErrorBackground
                            invoice.isSynced -> KanakSuccessBackground
                            else -> KanakAccentLight
                        }
                    ) {
                        Text(
                            text = when {
                                invoice.isSyncConflict -> "CONFLICT"
                                invoice.isSynced -> "CONFIRMED"
                                else -> "LOCAL ONLY"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    invoice.isSyncConflict -> KanakError
                                    invoice.isSynced -> KanakSuccess
                                    else -> KanakPrimaryBlue
                                },
                                fontSize = 9.sp
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = BahrainFinancials.formatBhd(invoice.total),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = KanakPrimaryText
                    )
                )
                if (invoice.isCredit && !invoice.isPaid && !invoice.isVoid) {
                    Text(
                        text = "Due: ${BahrainFinancials.formatBhd(invoice.balanceDue)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = KanakWarning,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                } else {
                    Text(
                        text = "${invoice.lines.size} items",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = KanakSecondaryText,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Consistent Empty State Component
 */
@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionButton: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = KanakSurfaceVariant,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = KanakPrimaryBlue
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = KanakPrimaryText
            ),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = KanakSecondaryText,
                fontSize = 13.sp
            ),
            textAlign = TextAlign.Center
        )
        if (actionButton != null) {
            Spacer(modifier = Modifier.height(16.dp))
            actionButton()
        }
    }
}

/**
 * Styled Confirmation Dialog matching Design Board Modals
 */
@Composable
fun KanakConfirmDialog(
    title: String,
    message: String,
    warningBoxText: String? = null,
    confirmText: String = "Confirm",
    cancelText: String = "Cancel",
    isDestructive: Boolean = false,
    icon: ImageVector = Icons.Default.Delete,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isReducedMotion = LocalReducedMotion.current
    var hasAppeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hasAppeared = true
    }

    val animatedScale by animateFloatAsState(
        targetValue = if (isReducedMotion || hasAppeared) 1.0f else 0.95f,
        animationSpec = tween(
            durationMillis = if (isReducedMotion) 0 else MotionDuration.Modal,
            easing = MotionCurves.EaseOut
        ),
        label = "modal_scale"
    )

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isReducedMotion || hasAppeared) 1.0f else 0.0f,
        animationSpec = tween(
            durationMillis = if (isReducedMotion) 0 else MotionDuration.Modal,
            easing = MotionCurves.EaseOut
        ),
        label = "modal_alpha"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = KanakSurface,
        modifier = Modifier.graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
            alpha = animatedAlpha
        },
        icon = {
            Surface(
                shape = CircleShape,
                color = if (isDestructive) KanakErrorBackground else KanakAccentLight,
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isDestructive) KanakError else KanakPrimaryBlue,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = KanakPrimaryText
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = KanakSecondaryText,
                        textAlign = TextAlign.Center
                    )
                )

                if (warningBoxText != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = KanakErrorBackground),
                        border = androidx.compose.foundation.BorderStroke(1.dp, KanakError.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = KanakError,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = warningBoxText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = KanakPrimaryText,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) KanakError else KanakPrimaryBlue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(confirmText, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KanakPrimaryText)
            ) {
                Text(cancelText)
            }
        }
    )
}
