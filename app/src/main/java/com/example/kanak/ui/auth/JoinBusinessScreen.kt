package com.example.kanak.ui.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.domain.models.User
import com.example.kanak.ui.components.KanakBrandLoadingDialog
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinBusinessScreen(
    repository: KanakRepository,
    currentUser: User,
    onJoinedSuccess: () -> Unit,
    onRegisterNewCompany: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pendingInvitations by repository.pendingInvitations.collectAsState()

    var inviteCode by remember { mutableStateOf("") }
    var isJoining by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Check if there is an invitation matching current user's email
    val matchingInvite = remember(pendingInvitations, currentUser.email) {
        pendingInvitations.find {
            it.recipientEmail.equals(currentUser.email, ignoreCase = true) &&
                    it.status == com.example.kanak.domain.models.EmployeeInvitation.STATUS_PENDING
        }
    }

    LaunchedEffect(matchingInvite) {
        if (matchingInvite != null && inviteCode.isBlank()) {
            inviteCode = matchingInvite.inviteCode
        }
    }

    Scaffold(
        containerColor = KanakBackground,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(KanakBackground)
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = KanakAccentLight,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.GroupAdd,
                        contentDescription = null,
                        tint = KanakPrimaryBlue,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Join Your Business Fleet",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = KanakPrimaryText,
                    fontSize = 24.sp
                )
            )

            Text(
                text = "Welcome ${currentUser.displayName}! Enter the invitation code provided by your business owner to access your assigned van and products.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = KanakSecondaryText,
                    fontSize = 13.sp
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

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
                    if (matchingInvite != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF0FDF4),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Found Invitation for ${currentUser.email}!",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                                )
                                Text(
                                    text = "Company: ${matchingInvite.businessName} • Role: ${matchingInvite.role.uppercase()} • Van: ${matchingInvite.assignedVanCode ?: "Fleet"}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF15803D), fontSize = 11.sp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = {
                            inviteCode = it.uppercase()
                            errorMessage = null
                        },
                        label = { Text("Invitation Code (e.g. KNK-8294)") },
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = null, tint = KanakPrimaryBlue)
                        },
                        placeholder = { Text("KNK-XXXX") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("join_invite_code_input")
                    )

                    if (errorMessage != null) {
                        Text(errorMessage ?: "", color = KanakError, style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isJoining = true
                                val res = repository.acceptInvitation(inviteCode, currentUser)
                                isJoining = false
                                res.onSuccess {
                                    Toast.makeText(context, "Successfully joined ${it.businessName}!", Toast.LENGTH_LONG).show()
                                    // Mark licensing as activated via company membership
                                    onJoinedSuccess()
                                }.onFailure {
                                    errorMessage = it.message ?: "Invalid or expired invitation code"
                                }
                            }
                        },
                        enabled = !isJoining && inviteCode.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("accept_invitation_button")
                    ) {
                        if (isJoining) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Accept Invitation & Enter POS", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Or Register New Business Option
            Text("Are you a business owner?", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = onRegisterNewCompany,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KanakPrimaryBlue),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Register a New Business", fontWeight = FontWeight.Bold)
            }
        }

        if (isJoining) {
            KanakBrandLoadingDialog(
                message = "Joining Business Fleet...",
                subMessage = "Authorizing Employee Credentials & Van Permissions"
            )
        }
    }
}
