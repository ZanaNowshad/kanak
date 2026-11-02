package com.example.kanak.licensing

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanak.domain.models.User
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun CompanyRegistrationScreen(
    licenseManager: LicenseManager,
    currentUser: User,
    onRegistrationCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var companyName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf(currentUser.displayName) }
    var phone by remember { mutableStateOf("") }
    var crNumber by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var registeredProfile by remember { mutableStateOf<CompanyProfile?>(null) }

    Scaffold(
        containerColor = KanakBackground,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // KANAK Header Icon & Branding
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(KanakAccentLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    tint = KanakPrimaryBlue,
                    modifier = Modifier.size(38.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "'Kanak' by zedosai.com",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = KanakSecondaryText,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = "Register Your Company",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = KanakPrimaryText
                    )
                )
            }

            Text(
                text = "Enter your business details to request your official lifetime Kanak license. Once registered, send your details to WhatsApp (+973 3305 0666) or Email (${LicenseManager.MASTER_ADMIN_EMAIL}) to receive your activation code.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = KanakSecondaryText,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Registration Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = KanakSurface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Company Name
                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = { Text("Company / Trade Name *") },
                        placeholder = { Text("e.g. Al Noor Mini Market W.L.L.") },
                        leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = KanakSecondaryText) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reg_company_name_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Owner / Contact Name
                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = { Text("Owner / Manager Name *") },
                        placeholder = { Text("e.g. Ahmed Ali") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = KanakSecondaryText) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reg_owner_name_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Phone / WhatsApp
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Mobile / WhatsApp Number *") },
                        placeholder = { Text("+973 3900 1122") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = KanakSecondaryText) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reg_phone_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Google Email (Read-only)
                    OutlinedTextField(
                        value = currentUser.email,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Google Account Email") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = KanakSecondaryText) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // CR Number / Tax TRN
                    OutlinedTextField(
                        value = crNumber,
                        onValueChange = { crNumber = it },
                        label = { Text("Commercial Registration (CR) / TRN (Optional)") },
                        placeholder = { Text("CR: 123456-1") },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = KanakSecondaryText) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Business Address
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Business Address / City *") },
                        placeholder = { Text("Shop 12, Road 2803, Manama, Bahrain") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = KanakSecondaryText) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reg_address_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = KanakErrorBackground,
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = KanakError)
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(color = KanakError)
                        )
                    }
                }
            }

            // Submit Button
            Button(
                onClick = {
                    if (companyName.isBlank()) {
                        errorMessage = "Please enter your company or business name."
                        return@Button
                    }
                    if (ownerName.isBlank()) {
                        errorMessage = "Please enter the owner or manager name."
                        return@Button
                    }
                    if (phone.isBlank()) {
                        errorMessage = "Please enter a valid contact phone number."
                        return@Button
                    }
                    if (address.isBlank()) {
                        errorMessage = "Please enter your business address."
                        return@Button
                    }

                    errorMessage = null
                    isSubmitting = true

                    scope.launch {
                        val result = licenseManager.registerCompany(
                            companyName = companyName,
                            ownerName = ownerName,
                            phone = phone,
                            email = currentUser.email,
                            crNumber = crNumber,
                            address = address
                        )
                        isSubmitting = false

                        result.onSuccess { profile ->
                            registeredProfile = profile
                            Toast.makeText(context, "Registration submitted successfully!", Toast.LENGTH_LONG).show()

                            // Automatically offer immediate WhatsApp to admin
                            try {
                                val waIntent = licenseManager.createWhatsAppIntent(profile)
                                context.startActivity(waIntent)
                            } catch (_: Exception) {
                                try {
                                    val emailIntent = licenseManager.createEmailIntent(profile)
                                    context.startActivity(emailIntent)
                                } catch (_: Exception) {}
                            }
                        }.onFailure { ex ->
                            errorMessage = "Registration error: ${ex.message}"
                        }
                    }
                },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_registration_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Register & Request Activation Code", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }

            // Post-registration Direct Actions Card
            val reg = registeredProfile
            if (reg != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = KanakSuccessBackground,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Registration Successful! Request ID: ${reg.requestId}",
                                color = KanakSuccess,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Text(
                            text = "Send your details to the administrator to complete your payment and receive your Activation Code:",
                            style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                        )

                        // WhatsApp to +97333050666 Button
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
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Send via WhatsApp (+973 3305 0666)", fontWeight = FontWeight.Bold)
                        }

                        // Email to zanabalmuhamed@gmail.com Button
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
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp), tint = KanakPrimaryBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Send via Email (${LicenseManager.MASTER_ADMIN_EMAIL})", fontWeight = FontWeight.SemiBold, color = KanakPrimaryBlue)
                        }

                        // Proceed Button
                        Button(
                            onClick = onRegistrationCompleted,
                            colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("I Have My Code -> Enter Activation Code", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Note on Administrator Notification
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = KanakAccentLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = KanakPrimaryBlue)
                    Text(
                        text = "Your registration details will be sent directly to the administrator (${LicenseManager.MASTER_ADMIN_EMAIL}) for verification and activation.",
                        style = MaterialTheme.typography.bodySmall.copy(color = KanakPrimaryBlue, lineHeight = 18.sp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
