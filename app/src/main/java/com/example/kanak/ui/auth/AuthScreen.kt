package com.example.kanak.ui.auth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.kanak.auth.AuthState
import com.example.kanak.auth.GoogleAuthManager
import com.example.kanak.licensing.CompanyProfile
import com.example.kanak.licensing.LicenseManager
import com.example.kanak.licensing.LicenseState
import com.example.kanak.ui.components.KanakBrandLoadingDialog
import com.example.kanak.ui.components.KanakBrandInlineLoading
import com.example.ui.theme.*
import kotlinx.coroutines.launch

private enum class LandingTab(val title: String) {
    SIGN_IN("Sign In"),
    REGISTER("Register Company"),
    ACTIVATE("Activate Code")
}

@Composable
fun AuthScreen(
    authManager: GoogleAuthManager,
    licenseManager: LicenseManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authState by authManager.authState.collectAsState()
    val licenseState by licenseManager.licenseState.collectAsState()
    val companyProfile by licenseManager.companyProfile.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var selectedTab by remember {
        mutableStateOf(LandingTab.SIGN_IN)
    }

    // Registration Form State
    var regCompanyName by remember { mutableStateOf(companyProfile.companyName) }
    var regOwnerName by remember { mutableStateOf(companyProfile.ownerName) }
    var regPhone by remember { mutableStateOf(companyProfile.phone) }
    var regEmail by remember { mutableStateOf(companyProfile.email) }
    var regCrNumber by remember { mutableStateOf(companyProfile.crNumber) }
    var regAddress by remember { mutableStateOf(companyProfile.address) }
    var isSubmittingReg by remember { mutableStateOf(false) }
    var regErrorMessage by remember { mutableStateOf<String?>(null) }
    var submittedProfile by remember { mutableStateOf<CompanyProfile?>(null) }

    // Activation Code Input State
    var enteredActivationCode by remember { mutableStateOf("") }
    var isActivatingCode by remember { mutableStateOf(false) }
    var activationErrorMessage by remember { mutableStateOf<String?>(null) }
    var activationSuccessMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KanakBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // =========================================================================
            // BRANDING HEADER: 'Kanak' by zedosai.com
            // =========================================================================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text(
                    text = "'Kanak'",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = KanakPrimaryText,
                        fontSize = 32.sp
                    )
                )
                Text(
                    text = "by zedosai.com",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = KanakPrimaryBlue,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 15.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Wholesale FMCG Van Sales & Billing System",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = KanakSecondaryText,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.2.sp
                    )
                )
            }

            // Hero Van Illustration matching Board 1
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = KanakSurface),
                border = CardDefaults.outlinedCardBorder(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_van_hero_1793446015335),
                    contentDescription = "'Kanak' by zedosai.com Wholesale Van",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Value Proposition Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LandingFeaturePill(icon = Icons.Filled.Inventory2, title = "Authentic\nInventory")
                LandingFeaturePill(icon = Icons.Filled.LocalShipping, title = "Van Sales\nBilling")
                LandingFeaturePill(icon = Icons.Filled.CloudDone, title = "Google Sheets\nAuthoritative")
            }

            // =========================================================================
            // LANDING TABS: [ Sign In ] | [ Register Company ] | [ Activate Code ]
            // =========================================================================
            Surface(
                color = KanakSurfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LandingTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        Surface(
                            onClick = { selectedTab = tab },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) KanakSurface else Color.Transparent,
                            shadowElevation = if (isSelected) 2.dp else 0.dp,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) KanakPrimaryBlue else KanakSecondaryText,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // TAB 1: SIGN IN (CONTINUE WITH GOOGLE)
            // =========================================================================
            if (selectedTab == LandingTab.SIGN_IN) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Current Device Status Indicator
                        when (licenseState) {
                            LicenseState.ACTIVATED -> {
                                Surface(
                                    color = KanakSuccessBackground,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = KanakSuccess, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "Company License Active: ${companyProfile.companyName.ifBlank { "Registered" }}",
                                            style = MaterialTheme.typography.bodySmall.copy(color = KanakSuccess, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                            LicenseState.PENDING_ACTIVATION -> {
                                Surface(
                                    color = KanakWarning.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Key, contentDescription = null, tint = KanakWarning, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "License Pending Activation (ID: ${companyProfile.requestId})",
                                            style = MaterialTheme.typography.bodySmall.copy(color = KanakWarning, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                            LicenseState.UNREGISTERED -> {
                                Surface(
                                    color = KanakAccentLight,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "New business? Register your company to receive an activation code.",
                                            style = MaterialTheme.typography.bodySmall.copy(color = KanakPrimaryBlue)
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Connect Google Account",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                        )

                        Text(
                            text = "Authenticate with your Google Account to authorize your company's Google Drive & Google Sheets database.",
                            style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, textAlign = TextAlign.Center)
                        )

                        // Auth state feedback
                        when (val state = authState) {
                            is AuthState.Error -> {
                                Text(
                                    text = state.message,
                                    color = KanakError,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                            is AuthState.AccessDenied -> {
                                Text(
                                    text = state.reason,
                                    color = KanakError,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                            else -> Unit
                        }

                        if (authState is AuthState.Loading) {
                            KanakBrandInlineLoading(
                                text = "Authenticating with Google Account...",
                                size = 32.dp,
                                modifier = Modifier.testTag("auth_loading_indicator")
                            )
                        } else {
                            Button(
                                onClick = {
                                    scope.launch {
                                        authManager.signInWithGoogle(activityContext = context)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("google_signin_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = KanakPrimaryBlue,
                                    contentColor = Color.White
                                )
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "G",
                                            color = KanakPrimaryBlue,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Continue with Google",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                )
                            }

                            // Optional Demo Sign-In Shortcut for Testing & Offline Preview
                            Surface(
                                color = KanakSurfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Test Preview:",
                                        style = MaterialTheme.typography.labelSmall.copy(color = KanakSecondaryText, fontSize = 11.sp)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = { authManager.signInAsDemo(asAdmin = false) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("New Business Wizard", fontSize = 11.sp, color = KanakPrimaryBlue, fontWeight = FontWeight.Bold)
                                        }
                                        TextButton(
                                            onClick = { authManager.signInAsDemo(asAdmin = true) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Super Admin Demo", fontSize = 11.sp, color = KanakPrimaryBlue, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Quick Navigation Alternatives
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = { selectedTab = LandingTab.REGISTER }) {
                                Text("Register Company", fontSize = 12.sp, color = KanakPrimaryBlue)
                            }
                            TextButton(onClick = { selectedTab = LandingTab.ACTIVATE }) {
                                Text("Enter Activation Code", fontSize = 12.sp, color = KanakPrimaryBlue)
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // TAB 2: REGISTER COMPANY
            // =========================================================================
            if (selectedTab == LandingTab.REGISTER) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Register Company Profile",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                        )
                        Text(
                            text = "Submit your wholesale business information. Your registration details will be sent directly to the owner via WhatsApp (+973 3305 0666) or Email (${LicenseManager.MASTER_ADMIN_EMAIL}) to issue your paid Activation Code.",
                            style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, lineHeight = 18.sp)
                        )

                        OutlinedTextField(
                            value = regCompanyName,
                            onValueChange = { regCompanyName = it },
                            label = { Text("Company / Trade Name *") },
                            placeholder = { Text("e.g. Al Noor Trading Co. W.L.L.") },
                            leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = KanakSecondaryText) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("reg_company_name_input")
                        )

                        OutlinedTextField(
                            value = regOwnerName,
                            onValueChange = { regOwnerName = it },
                            label = { Text("Owner / Manager Name *") },
                            placeholder = { Text("e.g. Ahmed Ali") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = KanakSecondaryText) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("reg_owner_name_input")
                        )

                        OutlinedTextField(
                            value = regPhone,
                            onValueChange = { regPhone = it },
                            label = { Text("Contact Phone / WhatsApp *") },
                            placeholder = { Text("e.g. +973 3312 4567") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = KanakSecondaryText) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("reg_phone_input")
                        )

                        OutlinedTextField(
                            value = regEmail,
                            onValueChange = { regEmail = it },
                            label = { Text("Business Email Address *") },
                            placeholder = { Text("e.g. sales@alnoor.bh") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = KanakSecondaryText) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("reg_email_input")
                        )

                        OutlinedTextField(
                            value = regCrNumber,
                            onValueChange = { regCrNumber = it },
                            label = { Text("CR / TRN VAT Number") },
                            placeholder = { Text("e.g. CR 123456-1 / TRN 200012345600") },
                            leadingIcon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = KanakSecondaryText) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("reg_cr_input")
                        )

                        OutlinedTextField(
                            value = regAddress,
                            onValueChange = { regAddress = it },
                            label = { Text("Business Address *") },
                            placeholder = { Text("e.g. Building 456, Road 2803, Seef, Bahrain") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = KanakSecondaryText) },
                            singleLine = false,
                            maxLines = 3,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("reg_address_input")
                        )

                        if (regErrorMessage != null) {
                            Text(
                                text = regErrorMessage ?: "",
                                color = KanakError,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            onClick = {
                                if (regCompanyName.isBlank() || regOwnerName.isBlank() || regPhone.isBlank() || regEmail.isBlank() || regAddress.isBlank()) {
                                    regErrorMessage = "Please complete all required fields (*)."
                                    return@Button
                                }
                                regErrorMessage = null
                                isSubmittingReg = true
                                scope.launch {
                                    val res = licenseManager.registerCompany(
                                        companyName = regCompanyName,
                                        ownerName = regOwnerName,
                                        phone = regPhone,
                                        email = regEmail,
                                        crNumber = regCrNumber,
                                        address = regAddress
                                    )
                                    isSubmittingReg = false
                                    res.onSuccess { prof ->
                                        submittedProfile = prof
                                        Toast.makeText(context, "Registration submitted! Request ID: ${prof.requestId}", Toast.LENGTH_LONG).show()
                                        // Attempt WhatsApp launch immediately
                                        try {
                                            val waIntent = licenseManager.createWhatsAppIntent(prof)
                                            context.startActivity(waIntent)
                                        } catch (_: Exception) {}
                                    }.onFailure { err ->
                                        regErrorMessage = "Registration failed: ${err.message}"
                                    }
                                }
                            },
                            enabled = !isSubmittingReg,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("submit_company_registration_btn")
                        ) {
                            if (isSubmittingReg) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Register & Request Code", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Success Direct Contact Panel
                        val reg = submittedProfile
                        if (reg != null) {
                            Surface(
                                color = KanakSuccessBackground,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Registered! Request ID: ${reg.requestId}",
                                        color = KanakSuccess,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Send your registration details now to receive your Activation Code:",
                                        style = MaterialTheme.typography.bodySmall.copy(color = KanakPrimaryText)
                                    )

                                    // WhatsApp Button to +97333050666
                                    Button(
                                        onClick = {
                                            try {
                                                val waIntent = licenseManager.createWhatsAppIntent(reg)
                                                context.startActivity(waIntent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Could not open WhatsApp", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366), contentColor = Color.White),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(46.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Send to WhatsApp (+973 3305 0666)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    // Email Button to zanabalmuhamed@gmail.com
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val emailIntent = licenseManager.createEmailIntent(reg)
                                                context.startActivity(emailIntent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Could not open Email client", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(46.dp)
                                    ) {
                                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp), tint = KanakPrimaryBlue)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Send to Email (${LicenseManager.MASTER_ADMIN_EMAIL})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }

                                    // Switch to Activate tab
                                    Button(
                                        onClick = { selectedTab = LandingTab.ACTIVATE },
                                        colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue, contentColor = Color.White),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(46.dp)
                                    ) {
                                        Text("I Have My Code -> Activate", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // TAB 3: ACTIVATE LICENSE
            // =========================================================================
            if (selectedTab == LandingTab.ACTIVATE) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Enter Paid Activation Code",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                        )
                        Text(
                            text = "Enter the 16-character Activation Code (KNK-ACT-XXXX-XXXX) provided by the administrator upon payment confirmation.",
                            style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, lineHeight = 18.sp)
                        )

                        // If Request ID exists, display it for easy reference
                        if (companyProfile.requestId.isNotBlank()) {
                            Surface(
                                color = KanakSurfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Your License Request ID", style = MaterialTheme.typography.labelSmall.copy(color = KanakSecondaryText))
                                        Text(
                                            text = companyProfile.requestId,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText, fontFamily = FontFamily.Monospace)
                                        )
                                    }
                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("License Request ID", companyProfile.requestId))
                                        Toast.makeText(context, "Request ID copied!", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Request ID", tint = KanakPrimaryBlue)
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = enteredActivationCode,
                            onValueChange = { enteredActivationCode = it.uppercase() },
                            label = { Text("Activation Code *") },
                            placeholder = { Text("KNK-ACT-XXXX-XXXX") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = KanakPrimaryBlue) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                            modifier = Modifier.fillMaxWidth().testTag("landing_activation_code_input")
                        )

                        if (activationErrorMessage != null) {
                            Text(
                                text = activationErrorMessage ?: "",
                                color = KanakError,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (activationSuccessMessage != null) {
                            Text(
                                text = activationSuccessMessage ?: "",
                                color = KanakSuccess,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                if (enteredActivationCode.isBlank()) {
                                    activationErrorMessage = "Please enter your activation code."
                                    return@Button
                                }
                                activationErrorMessage = null
                                isActivatingCode = true
                                scope.launch {
                                    val res: Result<Boolean> = licenseManager.validateAndActivate(enteredActivationCode.trim())
                                    isActivatingCode = false
                                    res.onSuccess {
                                        activationSuccessMessage = "License successfully unlocked! You can now sign in with Google."
                                        selectedTab = LandingTab.SIGN_IN
                                    }.onFailure { err ->
                                        activationErrorMessage = err.message ?: "Invalid Activation Code"
                                    }
                                }
                            },
                            enabled = !isActivatingCode,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("activate_code_btn")
                        ) {
                            if (isActivatingCode) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Activate Lifetime License", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Contact Buttons for help/payment
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val waIntent = licenseManager.createWhatsAppIntent(companyProfile)
                                        context.startActivity(waIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not open WhatsApp", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("WhatsApp (+973 3305 0666)", fontSize = 11.sp, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val checkRes = licenseManager.checkCloudActivationStatus()
                                        checkRes.onSuccess { isAct ->
                                            if (isAct) {
                                                activationSuccessMessage = "Cloud activation confirmed! License unlocked."
                                                selectedTab = LandingTab.SIGN_IN
                                            } else {
                                                activationErrorMessage = "Payment pending. Administrator has not activated this request yet."
                                            }
                                        }.onFailure {
                                            activationErrorMessage = "Cloud check error: ${it.message}"
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Check Online", fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // =========================================================================
            // FOOTER: 'Kanak' by zedosai.com
            // =========================================================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFE1001A),
                    modifier = Modifier.size(10.dp)
                ) {}
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "'Kanak' by zedosai.com • Serving Bahrain's Wholesale & Retail Community",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = KanakSecondaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
        if (isSubmittingReg) {
            KanakBrandLoadingDialog(
                message = "Submitting Company Registration...",
                subMessage = "Registering with KANAK Wholesale Bahrain Ledger"
            )
        }
        if (isActivatingCode) {
            KanakBrandLoadingDialog(
                message = "Verifying Activation Code...",
                subMessage = "Cryptographic Key Authorization"
            )
        }
    }
}

@Composable
private fun LandingFeaturePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = KanakSurfaceVariant,
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = KanakPrimaryBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = KanakPrimaryText,
                lineHeight = 14.sp
            ),
            textAlign = TextAlign.Center
        )
    }
}
