package com.example.kanak.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.kanak.auth.AuthState
import com.example.kanak.auth.GoogleAuthManager
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.domain.models.User
import com.example.kanak.printing.BluetoothPrinterManager
import com.example.kanak.ui.auth.AuthScreen
import com.example.kanak.ui.auth.JoinBusinessScreen
import com.example.kanak.ui.customers.CustomersScreen
import com.example.kanak.ui.dashboard.DashboardScreen
import com.example.kanak.ui.fleet.FleetManagementScreen
import com.example.kanak.ui.invoices.InvoiceComposerScreen
import com.example.kanak.ui.invoices.InvoiceDetailScreen
import com.example.kanak.ui.invoices.InvoicesHistoryScreen
import com.example.kanak.ui.more.MoreHubScreen
import com.example.kanak.ui.products.AddEditProductScreen
import com.example.kanak.ui.purchases.PurchasesScreen
import com.example.kanak.ui.reports.ReportsScreen
import com.example.kanak.ui.sales.DailySalesSummaryScreen
import com.example.kanak.ui.settings.SettingsScreen
import com.example.kanak.ui.stock.StockScreen
import com.example.kanak.ui.suppliers.SuppliersScreen
import com.example.kanak.ui.team.TeamManagementScreen
import com.example.ui.theme.*

enum class PrimaryTab(val label: String) {
    HOME("Home"),
    INVOICES("Invoices"),
    PRODUCTS("Products"),
    REPORTS("Reports"),
    MORE("More")
}

sealed class Screen {
    object TabView : Screen()
    data class AddEditProduct(val productId: String?) : Screen()
    object InvoiceComposer : Screen()
    object Reports : Screen()
    data class InvoiceDetail(val invoiceId: String) : Screen()
    object Purchases : Screen()
    object Suppliers : Screen()
    object Settings : Screen()
    object DailySalesSummary : Screen()
    object FleetManagement : Screen()
    object TeamManagement : Screen()
    object JoinBusiness : Screen()
    object Customers : Screen()
    object Stock : Screen()
    object SystemDiagnostics : Screen()
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
    var showingJoinBusiness by remember { mutableStateOf(false) }
    var showStartupSplash by remember { mutableStateOf(true) }
    if (showStartupSplash) {
        com.example.kanak.ui.components.KanakStartupLoader(
            onStartupComplete = { showStartupSplash = false }, modifier = modifier)
        return
    }
    when (val state = authState) {
        is AuthState.Authenticated -> {
            val memberships by repository.teamMemberships.collectAsState()
            val currentUser = remember(state.user, memberships) {
                repository.resolveUserWithMembership(state.user)
            }
            val syncManager = remember(repository) {
                com.example.kanak.data.sync.GoogleSheetsSyncManager(context, repository)
            }
            val bootstrap = remember(repository) {
                com.example.kanak.data.sync.BootstrapCloudManager(context, repository, syncManager)
            }
            LaunchedEffect(state.idToken, currentUser) {
                repository.currentAuthToken = state.idToken
                try {
                    com.example.kanak.data.firebase.KanakFirebaseManager(context).syncUserToFirestore(currentUser)
                } catch (_: Exception) {}
            }
            when {
                showingJoinBusiness -> JoinBusinessScreen(
                    repository = repository,
                    currentUser = currentUser,
                    onJoinedSuccess = { showingJoinBusiness = false },
                    onRegisterNewCompany = { showingJoinBusiness = false },
                    modifier = modifier
                )
                !repository.isBusinessSetupCompleted(currentUser.email) ->
                    com.example.kanak.ui.wizard.BusinessSetupWizardScreen(
                        repository = repository,
                        authManager = authManager,
                        currentUser = currentUser,
                        bootstrapCloudManager = bootstrap,
                        onSetupComplete = {},
                        onSignOut = { authManager.signOut() },
                        onNavigateToJoin = { showingJoinBusiness = true },
                        modifier = modifier
                    )
                else -> AuthenticatedApp(
                    repository = repository,
                    authManager = authManager,
                    printerManager = printerManager,
                    currentUser = currentUser,
                    modifier = modifier
                )
            }
        }
        else -> AuthScreen(authManager = authManager, modifier = modifier)
    }
}

@Composable
private fun AuthenticatedApp(
    repository: KanakRepository,
    authManager: GoogleAuthManager,
    printerManager: BluetoothPrinterManager,
    currentUser: User,
    modifier: Modifier = Modifier
) {
    val visibleTabs = PrimaryTab.values()

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
                        visibleTabs.forEach { tab ->
                            val isSelected = selectedTab == tab
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            PrimaryTab.HOME -> if (isSelected) Icons.Filled.Home else Icons.Outlined.Home
                                            PrimaryTab.INVOICES -> if (isSelected) Icons.Filled.ReceiptLong else Icons.Outlined.ReceiptLong
                                            PrimaryTab.PRODUCTS -> if (isSelected) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2
                                            PrimaryTab.REPORTS -> if (isSelected) Icons.Filled.Assessment else Icons.Outlined.Assessment
                                            PrimaryTab.MORE -> if (isSelected) Icons.Filled.MoreHoriz else Icons.Outlined.MoreHoriz
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
                val isReducedMotion = LocalReducedMotion.current
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        if (isReducedMotion) {
                            fadeIn(animationSpec = snap()) togetherWith fadeOut(animationSpec = snap())
                        } else {
                            (fadeIn(animationSpec = tween(MotionDuration.TabSwitch, easing = MotionCurves.EaseOut)) +
                                scaleIn(initialScale = 0.99f, animationSpec = tween(MotionDuration.TabSwitch, easing = MotionCurves.EaseOut)))
                                .togetherWith(
                                    fadeOut(animationSpec = tween(120, easing = MotionCurves.EaseOut))
                                )
                        }
                    },
                    label = "tab_crossfade"
                ) { targetTab ->
                    when (targetTab) {
                        PrimaryTab.HOME -> {
                            DashboardScreen(
                                repository = repository,
                                currentUser = currentUser,
                                onNavigateToNewInvoice = { currentScreen = Screen.InvoiceComposer },
                                onNavigateToSettings = { currentScreen = Screen.Settings },
                                onNavigateToDailySalesSummary = { currentScreen = Screen.DailySalesSummary },
                                onNavigateToFleet = { currentScreen = Screen.FleetManagement },
                                onNavigateToTeam = { currentScreen = Screen.TeamManagement },
                                onNavigateToProducts = { selectedTab = PrimaryTab.PRODUCTS },
                                onNavigateToCustomers = { currentScreen = Screen.Customers },
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
                        PrimaryTab.PRODUCTS -> {
                            com.example.kanak.ui.products.ProductsScreen(
                                repository = repository,
                                onNavigateToAddProduct = { currentScreen = Screen.AddEditProduct(null) },
                                onNavigateToEditProduct = { prodId -> currentScreen = Screen.AddEditProduct(prodId) },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        PrimaryTab.REPORTS -> {
                            ReportsScreen(
                                repository = repository,
                                currentUser = currentUser,
                                onNavigateBack = { selectedTab = PrimaryTab.HOME },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        PrimaryTab.MORE -> {
                            MoreHubScreen(
                                currentUser = currentUser,
                                onNavigateToCustomers = { currentScreen = Screen.Customers },
                                onNavigateToStock = { currentScreen = Screen.Stock },
                                onNavigateToPurchases = { currentScreen = Screen.Purchases },
                                onNavigateToSuppliers = { currentScreen = Screen.Suppliers },
                                onNavigateToFleet = { currentScreen = Screen.FleetManagement },
                                onNavigateToReports = { selectedTab = PrimaryTab.REPORTS },
                                onNavigateToTeam = { currentScreen = Screen.TeamManagement },
                                onNavigateToSettings = { currentScreen = Screen.Settings },
                                onSignOut = { authManager.signOut() },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
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

        is Screen.Reports -> {
            BackHandler { currentScreen = Screen.TabView }
            ReportsScreen(
                repository = repository,
                currentUser = currentUser,
                onNavigateBack = { currentScreen = Screen.TabView }
            )
        }

        is Screen.InvoiceComposer -> {
            BackHandler { currentScreen = Screen.TabView }
            InvoiceComposerScreen(
                repository = repository,
                currentUserId = currentUser.userId,
                currentUser = currentUser,
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
                currentUserId = currentUser.userId,
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
                onNavigateToFleet = { currentScreen = Screen.FleetManagement },
                onNavigateToTeam = { currentScreen = Screen.TeamManagement },
                onNavigateToDailySummary = { currentScreen = Screen.DailySalesSummary },
                onNavigateToJoinBusiness = { currentScreen = Screen.JoinBusiness },
                onNavigateToSystemDiagnostics = { currentScreen = Screen.SystemDiagnostics },
                onNavigateBack = { currentScreen = Screen.TabView }
            )
        }

        is Screen.SystemDiagnostics -> {
            BackHandler { currentScreen = Screen.Settings }
            com.example.kanak.ui.settings.SystemDiagnosticsScreen(
                repository = repository,
                onNavigateBack = { currentScreen = Screen.Settings }
            )
        }

        is Screen.DailySalesSummary -> {
            BackHandler { currentScreen = Screen.TabView }
            DailySalesSummaryScreen(
                repository = repository,
                currentUser = currentUser,
                printerManager = printerManager,
                onNavigateBack = { currentScreen = Screen.TabView }
            )
        }

        is Screen.FleetManagement -> {
            BackHandler { currentScreen = Screen.TabView }
            FleetManagementScreen(
                repository = repository,
                currentUser = currentUser,
                onNavigateBack = { currentScreen = Screen.TabView }
            )
        }

        is Screen.TeamManagement -> {
            BackHandler { currentScreen = Screen.TabView }
            TeamManagementScreen(
                repository = repository,
                currentUser = currentUser,
                onNavigateBack = { currentScreen = Screen.TabView }
            )
        }

        is Screen.JoinBusiness -> {
            BackHandler { currentScreen = Screen.TabView }
            JoinBusinessScreen(
                repository = repository,
                currentUser = currentUser,
                onJoinedSuccess = { currentScreen = Screen.TabView },
                onRegisterNewCompany = { currentScreen = Screen.TabView }
            )
        }

        is Screen.Customers -> {
            BackHandler { currentScreen = Screen.TabView }
            CustomersScreen(
                repository = repository,
                currentUserId = currentUser.userId,
                onNavigateToInvoiceDetail = { id -> currentScreen = Screen.InvoiceDetail(id) },
                onNavigateToNewInvoice = { currentScreen = Screen.InvoiceComposer },
                modifier = modifier
            )
        }

        is Screen.Stock -> {
            BackHandler { currentScreen = Screen.TabView }
            StockScreen(
                repository = repository,
                currentUserId = currentUser.userId,
                isAdmin = currentUser.isAdmin,
                onNavigateToPurchases = { currentScreen = Screen.Purchases },
                onNavigateToSuppliers = { currentScreen = Screen.Suppliers },
                modifier = modifier
            )
        }
    }
}
