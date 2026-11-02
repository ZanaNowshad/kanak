package com.example.kanak.licensing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ActivationGateScreen(
    licenseManager: LicenseManager,
    onActivationSuccess: () -> Unit,
    onReEditRegistration: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by licenseManager.companyProfile.collectAsState()

    var enteredCode by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var isVerifyingOnline by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

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
            Spacer(modifier = Modifier.height(10.dp))

            // Activation Key Icon
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(KanakWarning.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = KanakWarning,
                    modifier = Modifier.size(40.dp)
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
                    text = "Activation Required",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = KanakPrimaryText
                    )
                )
            }

            Text(
                text = "Your business is registered. Please provide your official Activation Code to unlock unlimited lifetime van sales access.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = KanakSecondaryText,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Registered Company Snapshot Card
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = profile.companyName.ifBlank { "Registered Business" },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                            )
                            Text(
                                text = profile.ownerName,
                                style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = KanakWarning.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "PENDING CODE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = KanakWarning
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = KanakDivider)

                    // License Request ID with Copy Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(KanakSurfaceVariant)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "LICENSE REQUEST ID",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = KanakSecondaryText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = profile.requestId.ifBlank { "KNK-REQ-PENDING" },
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = KanakPrimaryBlue
                                )
                            )
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("License Request ID", profile.requestId))
                                Toast.makeText(context, "Request ID copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Request ID", tint = KanakPrimaryBlue)
                        }
                    }

                    // Contact Administrator CTAs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val emailIntent = licenseManager.createEmailIntent(profile)
                                    context.startActivity(emailIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open email client", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Email Admin", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    val waIntent = licenseManager.createWhatsAppIntent(profile)
                                    context.startActivity(waIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open WhatsApp", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("WhatsApp (+973 3305 0666)", fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }

            // Payment Instructions Card
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "How to Activate:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                    )
                    Text(
                        text = "1. Send your License Request ID (${profile.requestId}) to the administrator.\n2. Complete your payment directly with the administrator via WhatsApp (+973 3305 0666) or Email (${LicenseManager.MASTER_ADMIN_EMAIL}).\n3. The administrator will send your unique Activation Code.\n4. Enter the code below to permanently unlock the app on this device.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = KanakSecondaryText,
                            lineHeight = 20.sp
                        )
                    )
                }
            }

            // Activation Code Input Card
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Enter Activation Code",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
                    )

                    OutlinedTextField(
                        value = enteredCode,
                        onValueChange = { enteredCode = it },
                        placeholder = { Text("e.g. KNK-ACT-XXXX-XXXX") },
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null, tint = KanakPrimaryBlue) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("activation_code_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = {
                            if (enteredCode.isBlank()) {
                                errorMessage = "Please enter the activation code provided by the administrator."
                                return@Button
                            }

                            isChecking = true
                            errorMessage = null
                            successMessage = null

                            scope.launch {
                                val result = licenseManager.validateAndActivate(enteredCode)
                                isChecking = false
                                result.onSuccess {
                                    successMessage = "License successfully validated! Welcome to Kanak."
                                    Toast.makeText(context, "Activation Successful!", Toast.LENGTH_LONG).show()
                                    onActivationSuccess()
                                }.onFailure { ex ->
                                    errorMessage = ex.message ?: "Invalid Activation Code"
                                }
                            }
                        },
                        enabled = !isChecking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("validate_activation_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue)
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.LockOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Validate & Activate Company", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Online sync check button
                    TextButton(
                        onClick = {
                            isVerifyingOnline = true
                            errorMessage = null
                            scope.launch {
                                val res = licenseManager.checkCloudActivationStatus()
                                isVerifyingOnline = false
                                res.onSuccess { activated ->
                                    if (activated) {
                                        Toast.makeText(context, "Approved by Administrator!", Toast.LENGTH_LONG).show()
                                        onActivationSuccess()
                                    } else {
                                        Toast.makeText(context, "Activation still pending. Please contact admin.", Toast.LENGTH_SHORT).show()
                                    }
                                }.onFailure {
                                    Toast.makeText(context, "Could not check cloud status: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isVerifyingOnline
                    ) {
                        if (isVerifyingOnline) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Check Online Activation Status")
                        }
                    }
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

            if (successMessage != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = KanakSuccessBackground,
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = KanakSuccess)
                        Text(
                            text = successMessage ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(color = KanakSuccess, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            TextButton(onClick = onReEditRegistration) {
                Text("Need to modify company details?", color = KanakSecondaryText, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
