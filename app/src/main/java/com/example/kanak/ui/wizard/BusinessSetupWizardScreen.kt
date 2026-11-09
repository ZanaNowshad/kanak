package com.example.kanak.ui.wizard

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.auth.GoogleAuthManager
import com.example.kanak.auth.OAuthConsentRequired
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import com.example.kanak.data.sync.BootstrapCloudManager
import com.example.kanak.data.sync.BootstrapStatus
import com.example.kanak.domain.models.User
import com.example.kanak.licensing.LicenseManager
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun BusinessSetupWizardScreen(
    repository: KanakRepository,
    authManager: GoogleAuthManager,
    currentUser: User,
    bootstrapCloudManager: BootstrapCloudManager,
    licenseManager: LicenseManager,
    onSetupComplete: () -> Unit,
    onSignOut: () -> Unit,
    onNavigateToJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentStep by rememberSaveable { mutableIntStateOf(0) }

    // Step 2 Data: Business Details
    var companyName by rememberSaveable { mutableStateOf("") }
    var businessType by rememberSaveable { mutableStateOf("Wholesale FMCG") }
    var locationAddress by rememberSaveable { mutableStateOf("") }
    var contactPhone by rememberSaveable { mutableStateOf("+973 ") }
    var crNumber by rememberSaveable { mutableStateOf("") }
    var vatNumber by rememberSaveable { mutableStateOf("BH2000") }

    // Step 3 Data: Google Sheet Connection
    var sheetMode by rememberSaveable { mutableStateOf("CREATE_NEW") } // "CREATE_NEW" or "EXISTING_ID"
    var customSheetId by rememberSaveable { mutableStateOf("") }
    var provisionedSheetId by rememberSaveable { mutableStateOf<String?>(null) }
    var isCreatingSheet by remember { mutableStateOf(false) }
    var sheetCreationError by remember { mutableStateOf<String?>(null) }
    val sheetToken by authManager.oauthSheetsToken.collectAsState()
    val scopeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            authManager.completeGoogleSheetsOAuth(result.data)
                .onFailure { sheetCreationError = it.message }
        } else sheetCreationError = "Google Sheets permission was not granted"
    }

    // Step 4 Data: Initial Fleet & Staff
    var createInitialVan by rememberSaveable { mutableStateOf(false) }
    var initialVanCode by rememberSaveable { mutableStateOf("") }
    var initialVanName by rememberSaveable { mutableStateOf("") }
    var initialPlateNumber by rememberSaveable { mutableStateOf("") }
    var initialStaffName by rememberSaveable { mutableStateOf("") }

    // Step 5 Data: Finalization
    var isSubmittingSetup by remember { mutableStateOf(false) }
    var setupErrorMessage by remember { mutableStateOf<String?>(null) }
    var showSignOutConfirmation by remember { mutableStateOf(false) }

    // Hardware back press handler: go back if not at step 0
    BackHandler(enabled = currentStep > 0) {
        if (currentStep > 0) {
            currentStep--
        }
    }

    val stepTitles = listOf(
        "Welcome & Overview",
        "Business Details",
        "Cloud Database",
        "Fleet & Route Setup",
        "Review & Finish"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = KanakBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KanakSurface)
                    .statusBarsPadding()
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (currentStep > 0) {
                        IconButton(
                            onClick = { currentStep-- },
                            modifier = Modifier.testTag("wizard_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Go to previous step",
                                tint = KanakPrimaryBlue
                            )
                        }
                    } else {
                        // Logo / Icon representation
                        Surface(
                            shape = CircleShape,
                            color = KanakSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = KanakPrimaryBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Business Setup Wizard",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = KanakPrimaryText
                            )
                        )
                        Text(
                            text = "Step ${currentStep + 1} of 5 • ${stepTitles[currentStep]}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = KanakSecondaryText,
                                fontSize = 12.sp
                            )
                        )
                    }

                    // Sign Out affordance for users who want to exit or switch accounts
                    TextButton(
                        onClick = { showSignOutConfirmation = true },
                        modifier = Modifier.testTag("wizard_signout_button")
                    ) {
                        Text(
                            text = "Sign Out",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = KanakError,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                // Linear Progress Indicator
                LinearProgressIndicator(
                    progress = { (currentStep + 1) / 5f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = KanakPrimaryBlue,
                    trackColor = KanakSurfaceVariant,
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(tween(300)) { width -> width } + fadeIn(tween(300)))
                            .togetherWith(slideOutHorizontally(tween(300)) { width -> -width } + fadeOut(tween(300)))
                    } else {
                        (slideInHorizontally(tween(300)) { width -> -width } + fadeIn(tween(300)))
                            .togetherWith(slideOutHorizontally(tween(300)) { width -> width } + fadeOut(tween(300)))
                    }
                },
                label = "WizardStepAnimation"
            ) { step ->
                when (step) {
                    0 -> Step1Welcome(
                        userEmail = currentUser.email,
                        onStart = { currentStep = 1 },
                        onJoinTeam = onNavigateToJoin
                    )
                    1 -> Step2BusinessDetails(
                        companyName = companyName,
                        onCompanyNameChange = { companyName = it },
                        businessType = businessType,
                        onBusinessTypeChange = { businessType = it },
                        locationAddress = locationAddress,
                        onLocationAddressChange = { locationAddress = it },
                        contactPhone = contactPhone,
                        onContactPhoneChange = { contactPhone = it },
                        crNumber = crNumber,
                        onCrNumberChange = { crNumber = it },
                        vatNumber = vatNumber,
                        onVatNumberChange = { vatNumber = it },
                        onNext = { currentStep = 2 }
                    )
                    2 -> Column {
                        if (sheetToken.isNullOrBlank()) {
                            Button(onClick = {
                                coroutineScope.launch {
                                    authManager.requestGoogleSheetsOAuth(context)
                                        .onFailure { err ->
                                            if (err is OAuthConsentRequired)
                                                scopeLauncher.launch(
                                                    IntentSenderRequest.Builder(err.pendingIntent.intentSender).build()
                                                )
                                            else sheetCreationError = err.message
                                        }
                                }
                            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Text("Authorize Google Sheets")
                            }
                        }
                        Step3GoogleSheetConnection(
                        companyName = companyName.ifBlank { "My FMCG Enterprise" },
                        sheetMode = sheetMode,
                        onSheetModeChange = { sheetMode = it },
                        customSheetId = customSheetId,
                        onCustomSheetIdChange = { customSheetId = it },
                        provisionedSheetId = provisionedSheetId,
                        isCreating = isCreatingSheet,
                        errorMessage = sheetCreationError,
                        onCreateTemplate = {
                            coroutineScope.launch {
                                isCreatingSheet = true
                                sheetCreationError = null
                                try {
                                    val token = sheetToken
                                    if (token.isNullOrBlank()) {
                                        sheetCreationError = "Authorize Google Sheets before creating the workbook."
                                        return@launch
                                    }
                                    val name = companyName.ifBlank { "Kanak FMCG" }
                                    val res = bootstrapCloudManager.executeBootstrapCreation(
                                        token, name, currentUser.email, currentUser.userId)
                                    res.onSuccess { newId ->
                                        provisionedSheetId = newId
                                        Toast.makeText(context, "Google Sheet template ready!", Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        sheetCreationError = "Could not create sheet: ${err.message}. You can continue and connect it later or enter an ID."
                                        provisionedSheetId = null
                                    }
                                } catch (e: Exception) {
                                    sheetCreationError = "Connection note: ${e.message}"
                                    provisionedSheetId = null
                                } finally {
                                    isCreatingSheet = false
                                }
                            }
                        },
                        onNext = { currentStep = 3 }
                        )
                    }
                    3 -> Step4FleetAndStaff(
                        createInitialVan = createInitialVan,
                        onToggleCreate = { createInitialVan = it },
                        vanCode = initialVanCode,
                        onVanCodeChange = { initialVanCode = it },
                        vanName = initialVanName,
                        onVanNameChange = { initialVanName = it },
                        plateNumber = initialPlateNumber,
                        onPlateNumberChange = { initialPlateNumber = it },
                        staffName = initialStaffName,
                        onStaffNameChange = { initialStaffName = it },
                        onNext = { currentStep = 4 }
                    )
                    4 -> Step5ReviewAndActivate(
                        companyName = companyName,
                        businessType = businessType,
                        locationAddress = locationAddress,
                        contactPhone = contactPhone,
                        crNumber = crNumber,
                        vatNumber = vatNumber,
                        sheetIdentifier = if (sheetMode == "CREATE_NEW") (provisionedSheetId ?: "Not connected") else customSheetId,
                        createInitialVan = createInitialVan,
                        vanCode = initialVanCode,
                        staffName = initialStaffName,
                        userEmail = currentUser.email,
                        isSubmitting = isSubmittingSetup,
                        errorMessage = setupErrorMessage,
                        onFinish = {
                            coroutineScope.launch {
                                isSubmittingSetup = true
                                setupErrorMessage = null

                                val targetSheetId = if (sheetMode == "CREATE_NEW") {
                                    provisionedSheetId.orEmpty()
                                } else {
                                    customSheetId.trim()
                                }

                                if (targetSheetId.isBlank()) {
                                    isSubmittingSetup = false
                                    setupErrorMessage = "Connect a real Google Sheet before completing setup."
                                    return@launch
                                }
                                val result = repository.completeBusinessSetup(
                                    userId = currentUser.userId,
                                    userEmail = currentUser.email,
                                    companyName = companyName.trim(),
                                    businessType = businessType,
                                    address = locationAddress.trim(),
                                    phone = contactPhone.trim(),
                                    crNumber = crNumber.trim(),
                                    vatNumber = vatNumber.trim(),
                                    spreadsheetId = targetSheetId,
                                    createInitialVan = createInitialVan,
                                    vanCode = initialVanCode.trim().uppercase(),
                                    vanName = initialVanName.trim(),
                                    registrationNo = initialPlateNumber.trim(),
                                    initialStaffName = initialStaffName.trim()
                                )

                                isSubmittingSetup = false
                                result.onSuccess {
                                    Toast.makeText(context, "Business setup completed. Welcome to Kanak.", Toast.LENGTH_LONG).show()
                                    onSetupComplete()
                                }.onFailure { err ->
                                    setupErrorMessage = "Registration error: ${err.message ?: "Please verify your details and try again."}"
                                }
                            }
                        }
                    )
                }
            }

            // Sign Out Confirmation Dialog
            if (showSignOutConfirmation) {
                AlertDialog(
                    onDismissRequest = { showSignOutConfirmation = false },
                    title = {
                        Text(
                            text = "Leave Business Setup?",
                            fontWeight = FontWeight.Bold,
                            color = KanakPrimaryText
                        )
                    },
                    text = {
                        Text(
                            text = "Your business is not registered yet. If you sign out now, you will need to complete this setup before accessing any sales or inventory features.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = KanakSecondaryText)
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showSignOutConfirmation = false
                                onSignOut()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = KanakError)
                        ) {
                            Text("Sign Out", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSignOutConfirmation = false }) {
                            Text("Stay Here", color = KanakPrimaryBlue)
                        }
                    },
                    containerColor = KanakSurface,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}

// =========================================================================
// STEP 1: WELCOME & OVERVIEW
// =========================================================================
@Composable
private fun Step1Welcome(
    userEmail: String,
    onStart: () -> Unit,
    onJoinTeam: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = CircleShape,
            color = KanakAccentLight,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.RocketLaunch,
                    contentDescription = null,
                    tint = KanakPrimaryBlue,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Text(
            text = "Welcome to Kanak FMCG",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Black,
                color = KanakPrimaryText,
                textAlign = TextAlign.Center
            )
        )

        Text(
            text = "You're signed in as $userEmail.\nTo use the app, let's register your business in a few simple steps.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = KanakSecondaryText,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Value Cards
        SetupFeatureCard(
            icon = Icons.Default.Domain,
            title = "1. Business Details",
            description = "Your company name, trading address, and Bahrain VAT/CR credentials for compliant tax invoices."
        )

        SetupFeatureCard(
            icon = Icons.Default.CloudQueue,
            title = "2. Google Sheets Cloud Database",
            description = "We set up a master spreadsheet in your Google Drive so you own 100% of your customer and invoice data."
        )

        SetupFeatureCard(
            icon = Icons.Default.LocalShipping,
            title = "3. Initial Delivery Van & Staff",
            description = "Pre-configure your first route sales van and driver to start billing immediately upon entering the app."
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Large Primary Button
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("wizard_start_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = KanakPrimaryBlue,
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Get Started",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }

        // Alternative for invited employees
        TextButton(
            onClick = onJoinTeam,
            modifier = Modifier.testTag("wizard_join_team_button")
        ) {
            Icon(Icons.Default.GroupAdd, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Joining an existing company with an invite code?",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = KanakPrimaryBlue,
                    fontWeight = FontWeight.Medium
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// =========================================================================
// STEP 2: BUSINESS BASIC DETAILS
// =========================================================================
@Composable
private fun Step2BusinessDetails(
    companyName: String,
    onCompanyNameChange: (String) -> Unit,
    businessType: String,
    onBusinessTypeChange: (String) -> Unit,
    locationAddress: String,
    onLocationAddressChange: (String) -> Unit,
    contactPhone: String,
    onContactPhoneChange: (String) -> Unit,
    crNumber: String,
    onCrNumberChange: (String) -> Unit,
    vatNumber: String,
    onVatNumberChange: (String) -> Unit,
    onNext: () -> Unit
) {
    val scrollState = rememberScrollState()

    val isNameValid = companyName.trim().length >= 3
    val isLocationValid = locationAddress.trim().length >= 3
    val canProceed = isNameValid && isLocationValid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Tell us about your business",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = KanakPrimaryText
            )
        )

        Text(
            text = "This information appears on your printed thermal receipts and official VAT invoices.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = KanakSecondaryText
            )
        )

        // Business Name
        OutlinedTextField(
            value = companyName,
            onValueChange = onCompanyNameChange,
            label = { Text("Business / Trading Name *") },
            placeholder = { Text("e.g. Al-Manama FMCG Distribution") },
            leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = KanakPrimaryBlue) },
            isError = companyName.isNotEmpty() && !isNameValid,
            supportingText = {
                if (companyName.isNotEmpty() && !isNameValid) {
                    Text("Company name must be at least 3 characters", color = KanakError)
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wizard_company_name_input")
        )

        // Business Type Selector
        Text(
            text = "Business Type",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                color = KanakPrimaryText
            )
        )

        val businessTypes = listOf(
            "Wholesale FMCG",
            "Van Sales Operator",
            "F&B & Bakery Supplier",
            "General Trading"
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            businessTypes.forEach { type ->
                val isSelected = businessType == type
                Surface(
                    onClick = { onBusinessTypeChange(type) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) KanakAccentLight else KanakSurface,
                    border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(KanakPrimaryBlue)) else CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onBusinessTypeChange(type) },
                            colors = RadioButtonDefaults.colors(selectedColor = KanakPrimaryBlue)
                        )
                        Text(
                            text = type,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) KanakPrimaryBlue else KanakPrimaryText
                            )
                        )
                    }
                }
            }
        }

        // Location Address
        OutlinedTextField(
            value = locationAddress,
            onValueChange = onLocationAddressChange,
            label = { Text("Business Address / Warehouse *") },
            placeholder = { Text("e.g. Building 12, Road 45, Block 301, Manama, Bahrain") },
            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = KanakPrimaryBlue) },
            isError = locationAddress.isNotEmpty() && !isLocationValid,
            supportingText = {
                if (locationAddress.isNotEmpty() && !isLocationValid) {
                    Text("Address is required for Bahrain tax receipts", color = KanakError)
                }
            },
            singleLine = false,
            maxLines = 3,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wizard_location_input")
        )

        // Contact Phone
        OutlinedTextField(
            value = contactPhone,
            onValueChange = onContactPhoneChange,
            label = { Text("Phone / WhatsApp Contact") },
            placeholder = { Text("+973 3300 0000") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = KanakSecondaryText) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wizard_phone_input")
        )

        // Row for CR & VAT Number
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = crNumber,
                onValueChange = onCrNumberChange,
                label = { Text("CR Number") },
                placeholder = { Text("123456-1") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("wizard_cr_input")
            )

            OutlinedTextField(
                value = vatNumber,
                onValueChange = onVatNumberChange,
                label = { Text("VAT TRN") },
                placeholder = { Text("BH2000...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("wizard_vat_input")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onNext,
            enabled = canProceed,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("wizard_step2_next_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = KanakPrimaryBlue,
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Continue to Cloud Database",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// =========================================================================
// STEP 3: GOOGLE SHEET CONNECTION / CREATION
// =========================================================================
@Composable
private fun Step3GoogleSheetConnection(
    companyName: String,
    sheetMode: String,
    onSheetModeChange: (String) -> Unit,
    customSheetId: String,
    onCustomSheetIdChange: (String) -> Unit,
    provisionedSheetId: String?,
    isCreating: Boolean,
    errorMessage: String?,
    onCreateTemplate: () -> Unit,
    onNext: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Connect Your Cloud Database",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = KanakPrimaryText
            )
        )

        // Plain-language Permission & Security Explanation
        Card(
            colors = CardDefaults.cardColors(containerColor = KanakAccentLight),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = KanakPrimaryBlue,
                    modifier = Modifier.size(24.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "You own 100% of your data",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = KanakPrimaryBlue
                        )
                    )
                    Text(
                        text = "Kanak uses your connected Google Account to store products, customers, invoices, vans, and staff in Google Sheets. You can view or export your records from any browser at any time.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = KanakPrimaryText,
                            lineHeight = 18.sp
                        )
                    )
                }
            }
        }

        Text(
            text = "Choose Connection Method",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                color = KanakPrimaryText
            )
        )

        // Option 1: Automatic 1-Tap Creation (Recommended)
        Surface(
            onClick = { onSheetModeChange("CREATE_NEW") },
            shape = RoundedCornerShape(14.dp),
            color = if (sheetMode == "CREATE_NEW") KanakSurface else KanakSurfaceVariant.copy(alpha = 0.5f),
            border = if (sheetMode == "CREATE_NEW") CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(KanakPrimaryBlue)) else CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RadioButton(
                        selected = sheetMode == "CREATE_NEW",
                        onClick = { onSheetModeChange("CREATE_NEW") },
                        colors = RadioButtonDefaults.colors(selectedColor = KanakPrimaryBlue)
                    )
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Create New Master FMCG Ledger",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = KanakPrimaryText
                                )
                            )
                            Surface(
                                color = KanakSuccessBackground,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "Recommended",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = KanakSuccess,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Automatically builds the 6 required sheets (Products, Customers, Suppliers, DailySalesLogs, Vans, Staff) with verified headers.",
                            style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                        )
                    }
                }

                if (sheetMode == "CREATE_NEW") {
                    if (provisionedSheetId != null) {
                        Surface(
                            color = KanakSuccessBackground,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = KanakSuccess, modifier = Modifier.size(18.dp))
                                Column {
                                    Text(
                                        text = "Spreadsheet Ready",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = KanakSuccess)
                                    )
                                    Text(
                                        text = "ID: $provisionedSheetId",
                                        style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp)
                                    )
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = onCreateTemplate,
                            enabled = !isCreating,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("wizard_create_sheet_button")
                        ) {
                            if (isCreating) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Building Master Spreadsheet...", fontSize = 13.sp)
                            } else {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create Master Spreadsheet Now", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // Option 2: Link Existing Spreadsheet ID
        Surface(
            onClick = { onSheetModeChange("EXISTING_ID") },
            shape = RoundedCornerShape(14.dp),
            color = if (sheetMode == "EXISTING_ID") KanakSurface else KanakSurfaceVariant.copy(alpha = 0.5f),
            border = if (sheetMode == "EXISTING_ID") CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(KanakPrimaryBlue)) else CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RadioButton(
                        selected = sheetMode == "EXISTING_ID",
                        onClick = { onSheetModeChange("EXISTING_ID") },
                        colors = RadioButtonDefaults.colors(selectedColor = KanakPrimaryBlue)
                    )
                    Column {
                        Text(
                            text = "Link Existing Google Sheet ID",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = KanakPrimaryText
                            )
                        )
                        Text(
                            text = "Use an already created company spreadsheet from your Drive.",
                            style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                        )
                    }
                }

                if (sheetMode == "EXISTING_ID") {
                    OutlinedTextField(
                        value = customSheetId,
                        onValueChange = onCustomSheetIdChange,
                        label = { Text("Google Sheet ID") },
                        placeholder = { Text("e.g. 1BxiMVs0XRA5nFMdKvBdBZj...") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wizard_custom_sheet_input")
                    )
                }
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = KanakError,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("wizard_step3_next_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = KanakPrimaryBlue,
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Continue to Fleet Setup",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// =========================================================================
// STEP 4: CONFIRM INITIAL STAFF / VAN PLACEHOLDERS
// =========================================================================
@Composable
private fun Step4FleetAndStaff(
    createInitialVan: Boolean,
    onToggleCreate: (Boolean) -> Unit,
    vanCode: String,
    onVanCodeChange: (String) -> Unit,
    vanName: String,
    onVanNameChange: (String) -> Unit,
    plateNumber: String,
    onPlateNumberChange: (String) -> Unit,
    staffName: String,
    onStaffNameChange: (String) -> Unit,
    onNext: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Fleet & Route Team Setup",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = KanakPrimaryText
            )
        )

        Text(
            text = "Kanak connects every invoice to a specific delivery van and salesperson for daily stock accounting and cash reconciliation.",
            style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
        )

        // Toggle Switch Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = KanakSurface,
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Create Initial Delivery Van & Driver",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = KanakPrimaryText
                        )
                    )
                    Text(
                        text = "Recommended so you can immediately compose invoices and track stock upon launch.",
                        style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                    )
                }
                Switch(
                    checked = createInitialVan,
                    onCheckedChange = onToggleCreate,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = KanakPrimaryBlue),
                    modifier = Modifier.testTag("wizard_initial_van_switch")
                )
            }
        }

        if (createInitialVan) {
            Card(
                colors = CardDefaults.cardColors(containerColor = KanakSurface),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Van Details",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = KanakPrimaryBlue
                        )
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = vanCode,
                            onValueChange = onVanCodeChange,
                            label = { Text("Van Code") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("wizard_van_code_input")
                        )

                        OutlinedTextField(
                            value = plateNumber,
                            onValueChange = onPlateNumberChange,
                            label = { Text("Plate Number") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("wizard_van_plate_input")
                        )
                    }

                    OutlinedTextField(
                        value = vanName,
                        onValueChange = onVanNameChange,
                        label = { Text("Van Description / Route Name") },
                        placeholder = { Text("e.g. North Route Delivery Van") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wizard_van_name_input")
                    )

                    Divider(color = KanakSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "Assigned Route Salesperson",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = KanakPrimaryBlue
                        )
                    )

                    OutlinedTextField(
                        value = staffName,
                        onValueChange = onStaffNameChange,
                        label = { Text("Driver / Salesperson Full Name") },
                        placeholder = { Text("e.g. Ali Ahmed") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = KanakPrimaryBlue) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wizard_driver_name_input")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("wizard_step4_next_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = KanakPrimaryBlue,
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Continue to Final Review",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// =========================================================================
// STEP 5: REVIEW & FINISH
// =========================================================================
@Composable
private fun Step5ReviewAndActivate(
    companyName: String,
    businessType: String,
    locationAddress: String,
    contactPhone: String,
    crNumber: String,
    vatNumber: String,
    sheetIdentifier: String,
    createInitialVan: Boolean,
    vanCode: String,
    staffName: String,
    userEmail: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onFinish: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Review & Launch",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = KanakPrimaryText
            )
        )

        Text(
            text = "Please verify your setup summary below. Once confirmed, your business is registered and full access to Kanak FMCG is unlocked.",
            style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
        )

        // Summary Card: Business Details
        Card(
            colors = CardDefaults.cardColors(containerColor = KanakSurface),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Business, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(20.dp))
                    Text(
                        text = "Company Profile",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                    )
                }

                ReviewRow(label = "Company Name", value = companyName.ifBlank { "Not specified" })
                ReviewRow(label = "Business Type", value = businessType)
                ReviewRow(label = "Address", value = locationAddress.ifBlank { "Not specified" })
                if (contactPhone.isNotBlank()) ReviewRow(label = "Contact Phone", value = contactPhone)
                if (crNumber.isNotBlank()) ReviewRow(label = "CR Number", value = crNumber)
                if (vatNumber.isNotBlank()) ReviewRow(label = "VAT TRN", value = vatNumber)
            }
        }

        // Summary Card: Database & Cloud
        Card(
            colors = CardDefaults.cardColors(containerColor = KanakSurface),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = KanakSuccess, modifier = Modifier.size(20.dp))
                    Text(
                        text = "Cloud Database",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                    )
                }

                ReviewRow(label = "Storage", value = "Google Sheets & Drive")
                ReviewRow(label = "Workbook", value = sheetIdentifier)
                ReviewRow(label = "Admin Owner", value = userEmail)
            }
        }

        // Summary Card: Fleet & Staff
        if (createInitialVan) {
            Card(
                colors = CardDefaults.cardColors(containerColor = KanakSurface),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.LocalShipping, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Initial Fleet & Team",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                        )
                    }

                    ReviewRow(label = "Primary Van", value = vanCode)
                    ReviewRow(label = "Assigned Driver", value = staffName.ifBlank { "Route Driver" })
                }
            }
        }

        if (errorMessage != null) {
            Surface(
                color = KanakError.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage,
                    color = KanakError,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Large Primary Button
        Button(
            onClick = onFinish,
            enabled = !isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("wizard_complete_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = KanakPrimaryBlue,
                contentColor = Color.White
            )
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Registering Business...", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            } else {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Complete Setup & Launch Kanak", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                color = KanakPrimaryText
            ),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun SetupFeatureCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KanakSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = CircleShape,
                color = KanakSurfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = KanakPrimaryBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = KanakPrimaryText
                    )
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = KanakSecondaryText,
                        lineHeight = 18.sp
                    )
                )
            }
        }
    }
}
