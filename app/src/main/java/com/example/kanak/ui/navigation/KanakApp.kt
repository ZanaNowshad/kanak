package com.example.kanak.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanak.auth.AuthState
import com.example.kanak.auth.GoogleAuthManager
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.domain.models.User
import com.example.kanak.licensing.*
import com.example.kanak.printing.BluetoothPrinterManager
import com.example.kanak.ui.auth.AuthScreen
import com.example.kanak.ui.customers.CustomersScreen
import com.example.kanak.ui.dashboard.DashboardScreen
import com.example.kanak.ui.invoices.InvoiceComposerScreen
import com.example.kanak.ui.invoices.InvoiceDetailScreen
import com.example.kanak.ui.invoices.InvoicesHistoryScreen
import com.example.kanak.ui.products.AddEditProductScreen
import com.example.kanak.ui.products.ProductsScreen
import com.example.kanak.ui.purchases.PurchasesScreen
import com.example.kanak.ui.settings.SettingsScreen
import com.example.kanak.ui.stock.StockScreen
import com.example.kanak.ui.suppliers.SuppliersScreen
import com.example.ui.theme.*

enum class PrimaryTab(val label: String) {
    HOME("Home"),
    PRODUCTS("Products"),
    CUSTOMERS("Customers"),
    INVOICES("Invoices"),
    STOCK("Stock")
}

sealed class Screen {
    object TabView : Screen()
    data class AddEditProduct(val productId: String?) : Screen()
    object InvoiceComposer : Screen()
    data class InvoiceDetail(val invoiceId: String) : Screen()
    object Purchases : Screen()
    object Suppliers : Screen()
    object Settings : Screen()
    object AdminLicensePortal : Screen()
}

@Composable
fun KanakApp(
    repository: KanakRepository,
    authManager: GoogleAuthManager,
    printerManager: BluetoothPrinterManager,
    modifier: Modifier = Modifier
) {
    val authState by authManager.authState.collectAsState()
    val context = LocalContext.current
    val licenseManager = remember { LicenseManager(context) }
    val licenseState by licenseManager.licenseState.collectAsState()
    var reEditingRegistration by remember { mutableStateOf(false) }

    when (val state = authState) {
        is AuthState.Authenticated -> {
            val isMasterAdmin = remember(state.user) {
                licenseManager.isMasterAdmin(state.user.email)
            }

            LaunchedEffect(state.idToken) {
                repository.currentAuthToken = state.idToken
                try {
                    com.example.kanak.data.firebase.KanakFirebaseManager(context).syncUserToFirestore(state.user)
                } catch (_: Exception) {
                }
            }

            if (!isMasterAdmin && (licenseState == LicenseState.UNREGISTERED || reEditingRegistration)) {
                CompanyRegistrationScreen(
                    licenseManager = licenseManager,
                    currentUser = state.user,
                    onRegistrationCompleted = {
                        reEditingRegistration = false
                    },
                    modifier = modifier
                )
            } else if (!isMasterAdmin && licenseState == LicenseState.PENDING_ACTIVATION) {
                ActivationGateScreen(
                    licenseManager = licenseManager,
                    onActivationSuccess = {
                        reEditingRegistration = false
                    },
                    onReEditRegistration = {
                        reEditingRegistration = true
                    },
                    modifier = modifier
                )
            } else {
                AuthenticatedApp(
                    repository = repository,
                    authManager = authManager,
                    printerManager = printerManager,
                    licenseManager = licenseManager,
                    currentUser = state.user,
                    modifier = modifier
                )
            }
        }
        else -> {
            AuthScreen(
                authManager = authManager,
                licenseManager = licenseManager,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun AuthenticatedApp(
    repository: KanakRepository,
    authManager: GoogleAuthManager,
    printerManager: BluetoothPrinterManager,
    licenseManager: LicenseManager,
    currentUser: User,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(PrimaryTab.HOME) }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.TabView) }

    // Sub-screen back handlers
    when (val scr = currentScreen) {
        is Screen.TabView -> {
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = KanakSurface,
                        tonalElevation = 6.dp,
                        modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        PrimaryTab.values().forEach { tab ->
                            val isSelected = selectedTab == tab
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            PrimaryTab.HOME -> if (isSelected) Icons.Filled.Home else Icons.Outlined.Home
                                            PrimaryTab.PRODUCTS -> if (isSelected) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2
                                            PrimaryTab.CUSTOMERS -> if (isSelected) Icons.Filled.People else Icons.Outlined.People
                                            PrimaryTab.INVOICES -> if (isSelected) Icons.Filled.ReceiptLong else Icons.Outlined.ReceiptLong
                                            PrimaryTab.STOCK -> if (isSelected) Icons.Filled.Warehouse else Icons.Outlined.Warehouse
                                        },
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.sp
                                        )
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = KanakPrimaryBlue,
                                    selectedTextColor = KanakPrimaryBlue,
                                    indicatorColor = KanakAccentLight,
                                    unselectedIconColor = KanakSecondaryText,
                                    unselectedTextColor = KanakSecondaryText
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                },
                modifier = modifier.fillMaxSize()
            ) { innerPadding ->
                when (selectedTab) {
                    PrimaryTab.HOME -> {
                        DashboardScreen(
                            repository = repository,
                            onNavigateToNewInvoice = { currentScreen = Screen.InvoiceComposer },
                            onNavigateToSettings = { currentScreen = Screen.Settings },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    PrimaryTab.PRODUCTS -> {
                        ProductsScreen(
                            repository = repository,
                            onNavigateToAddProduct = { currentScreen = Screen.AddEditProduct(null) },
                            onNavigateToEditProduct = { prodId -> currentScreen = Screen.AddEditProduct(prodId) },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    PrimaryTab.CUSTOMERS -> {
                        CustomersScreen(
                            repository = repository,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    PrimaryTab.INVOICES -> {
                        InvoicesHistoryScreen(
                            repository = repository,
                            onNavigateToNewInvoice = { currentScreen = Screen.InvoiceComposer },
                            onNavigateToInvoiceDetail = { id -> currentScreen = Screen.InvoiceDetail(id) },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    PrimaryTab.STOCK -> {
                        StockScreen(
                            repository = repository,
                            currentUserId = currentUser.userId,
                            isAdmin = currentUser.isAdmin,
                            onNavigateToPurchases = { currentScreen = Screen.Purchases },
                            onNavigateToSuppliers = { currentScreen = Screen.Suppliers },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }

        is Screen.AddEditProduct -> {
            BackHandler { currentScreen = Screen.TabView }
            AddEditProductScreen(
                repository = repository,
                productId = scr.productId,
                onNavigateBack = { currentScreen = Screen.TabView }
            )
        }

        is Screen.InvoiceComposer -> {
            BackHandler { currentScreen = Screen.TabView }
            InvoiceComposerScreen(
                repository = repository,
                currentUserId = currentUser.userId,
                onNavigateToInvoiceDetail = { id -> currentScreen = Screen.InvoiceDetail(id) },
                onNavigateBack = { currentScreen = Screen.TabView }
            )
        }

        is Screen.InvoiceDetail -> {
            BackHandler { currentScreen = Screen.TabView }
            InvoiceDetailScreen(
                repository = repository,
                printerManager = printerManager,
                invoiceId = scr.invoiceId,
                isAdmin = currentUser.isAdmin,
                onNavigateBack = { currentScreen = Screen.TabView }
            )
        }

        is Screen.Purchases -> {
            BackHandler { currentScreen = Screen.TabView }
            PurchasesScreen(
                repository = repository,
                currentUserId = currentUser.userId,
                isAdmin = currentUser.isAdmin,
                onNavigateBack = { currentScreen = Screen.TabView }
            )
        }

        is Screen.Suppliers -> {
            BackHandler { currentScreen = Screen.TabView }
            SuppliersScreen(
                repository = repository,
                onNavigateBack = { currentScreen = Screen.TabView }
            )
        }

        is Screen.Settings -> {
            BackHandler { currentScreen = Screen.TabView }
            SettingsScreen(
                repository = repository,
                authManager = authManager,
                printerManager = printerManager,
                currentUser = currentUser,
                licenseManager = licenseManager,
                onNavigateToAdminLicensePortal = { currentScreen = Screen.AdminLicensePortal },
                onNavigateBack = { currentScreen = Screen.TabView }
            )
        }

        is Screen.AdminLicensePortal -> {
            BackHandler { currentScreen = Screen.Settings }
            AdminLicensePortalScreen(
                licenseManager = licenseManager,
                onNavigateBack = { currentScreen = Screen.Settings }
            )
        }
    }
}
