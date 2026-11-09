package com.example.kanak.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.kanak.domain.models.User
import com.example.ui.theme.*

/**
 * More Hub Screen (KANAK-SPEC-1.0.0 Section 11.1).
 * Unifies all secondary operational, financial, and administrative destinations:
 * Customers, Stock & Warehouse, Purchases, Suppliers, Fleet & Vans,
 * Reports & Daily Close, Team Management, Settings, and Admin Licensing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreHubScreen(
    currentUser: User,
    onNavigateToCustomers: () -> Unit,
    onNavigateToStock: () -> Unit,
    onNavigateToPurchases: () -> Unit,
    onNavigateToSuppliers: () -> Unit,
    onNavigateToFleet: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToTeam: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.more_hub_title),
                        fontWeight = FontWeight.Bold,
                        color = KanakPrimaryText
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KanakSurface)
            )
        },
        containerColor = KanakBackground,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // User Profile Card with Role Badge
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("more_profile_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(KanakPrimaryBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (currentUser.displayName.takeIf { it.isNotBlank() } ?: currentUser.email)
                                    .take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentUser.displayName.ifBlank { "Kanak Operator" },
                                fontWeight = FontWeight.Bold,
                                color = KanakPrimaryText,
                                fontSize = 16.sp
                            )
                            Text(
                                text = currentUser.email,
                                color = KanakSecondaryText,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val roleColor = when {
                                currentUser.isPlatformAdmin -> Color(0xFFD97706)
                                currentUser.isOwner -> KanakPrimaryBlue
                                currentUser.isManager -> Color(0xFF7C3AED)
                                else -> KanakSuccess
                            }
                            val roleBg = when {
                                currentUser.isPlatformAdmin -> Color(0xFFFEF3C7)
                                currentUser.isOwner -> KanakAccentLight
                                currentUser.isManager -> Color(0xFFEDE9FE)
                                else -> KanakSuccessBackground
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = roleBg
                            ) {
                                Text(
                                    text = currentUser.role.uppercase(),
                                    color = roleColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Group: Commercial & Customers
            item {
                SectionHeader(title = stringResource(id = R.string.section_commercial))
            }
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        HubMenuItem(
                            icon = Icons.Outlined.People,
                            iconTint = KanakPrimaryBlue,
                            title = stringResource(id = R.string.customers),
                            subtitle = stringResource(id = R.string.customers_desc),
                            onClick = onNavigateToCustomers,
                            testTag = "more_item_customers"
                        )
                        HorizontalDivider(color = KanakBorder, thickness = 0.5.dp)
                        HubMenuItem(
                            icon = Icons.Outlined.LocalShipping,
                            iconTint = Color(0xFF0284C7),
                            title = stringResource(id = R.string.suppliers),
                            subtitle = stringResource(id = R.string.suppliers_desc),
                            onClick = onNavigateToSuppliers,
                            testTag = "more_item_suppliers"
                        )
                    }
                }
            }

            // Group: Logistics & Stock
            item {
                SectionHeader(title = stringResource(id = R.string.section_logistics))
            }
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        HubMenuItem(
                            icon = Icons.Outlined.Warehouse,
                            iconTint = Color(0xFF0F766E),
                            title = stringResource(id = R.string.stock),
                            subtitle = stringResource(id = R.string.stock_desc),
                            onClick = onNavigateToStock,
                            testTag = "more_item_stock"
                        )
                        HorizontalDivider(color = KanakBorder, thickness = 0.5.dp)
                        HubMenuItem(
                            icon = Icons.Outlined.ShoppingBag,
                            iconTint = Color(0xFF2563EB),
                            title = stringResource(id = R.string.purchases),
                            subtitle = stringResource(id = R.string.purchases_desc),
                            onClick = onNavigateToPurchases,
                            testTag = "more_item_purchases"
                        )
                        HorizontalDivider(color = KanakBorder, thickness = 0.5.dp)
                        HubMenuItem(
                            icon = Icons.Outlined.DirectionsCar,
                            iconTint = Color(0xFFD97706),
                            title = stringResource(id = R.string.fleet_management),
                            subtitle = stringResource(id = R.string.fleet_desc),
                            onClick = onNavigateToFleet,
                            testTag = "more_item_fleet"
                        )
                    }
                }
            }

            // Group: Reporting & Reconciliation
            item {
                SectionHeader(title = stringResource(id = R.string.section_reports))
            }
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HubMenuItem(
                        icon = Icons.Outlined.Summarize,
                        iconTint = Color(0xFF7C3AED),
                        title = stringResource(id = R.string.reports_daily_close),
                        subtitle = stringResource(id = R.string.reports_desc),
                        onClick = onNavigateToReports,
                        testTag = "more_item_reports"
                    )
                }
            }

            // Group: Administration & System
            item {
                SectionHeader(title = stringResource(id = R.string.section_admin))
            }
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        if (currentUser.isManager || currentUser.isOwner) {
                            HubMenuItem(
                                icon = Icons.Outlined.Group,
                                iconTint = Color(0xFF0D9488),
                                title = stringResource(id = R.string.team_management),
                                subtitle = stringResource(id = R.string.team_desc),
                                onClick = onNavigateToTeam,
                                testTag = "more_item_team"
                            )
                            HorizontalDivider(color = KanakBorder, thickness = 0.5.dp)
                        }

                        HubMenuItem(
                            icon = Icons.Outlined.Settings,
                            iconTint = KanakSecondaryText,
                            title = stringResource(id = R.string.settings),
                            subtitle = stringResource(id = R.string.settings_desc),
                            onClick = onNavigateToSettings,
                            testTag = "more_item_settings"
                        )

                    }
                }
            }

            // Sign Out Action
            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KanakError),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("more_sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.sign_out),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            color = KanakSecondaryText,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 0.5.sp
        ),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
private fun HubMenuItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                color = KanakPrimaryText,
                fontSize = 15.sp
            )
            Text(
                text = subtitle,
                color = KanakSecondaryText,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = KanakSecondaryText.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}
