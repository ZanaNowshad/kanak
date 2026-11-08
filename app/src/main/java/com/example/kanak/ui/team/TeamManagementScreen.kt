package com.example.kanak.ui.team

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.domain.models.*
import com.example.kanak.ui.components.EmptyStateView
import com.example.kanak.ui.components.KanakTopBar
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamManagementScreen(
    repository: KanakRepository,
    currentUser: User,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val memberships by repository.teamMemberships.collectAsState()
    val pendingInvitations by repository.pendingInvitations.collectAsState()
    val vans by repository.vans.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Team Members, 1: Invitations
    var showInviteDialog by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<EmployeeMembership?>(null) }
    var editingPermissionsMember by remember { mutableStateOf<EmployeeMembership?>(null) }

    Scaffold(
        topBar = {
            KanakTopBar(
                title = "Team Members & Access",
                onBack = onNavigateBack
            )
        },
        floatingActionButton = {
            if (currentUser.isOwner || currentUser.hasPermission(KanakPermissions.TEAM_INVITE)) {
                FloatingActionButton(
                    onClick = { showInviteDialog = true },
                    shape = CircleShape,
                    containerColor = KanakPrimaryBlue,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("invite_employee_fab")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Invite Employee")
                }
            }
        },
        containerColor = KanakBackground,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(KanakBackground)
                .padding(innerPadding)
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = KanakSurface,
                contentColor = KanakPrimaryBlue
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Members (${memberships.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Invitations (${pendingInvitations.count { it.status == EmployeeInvitation.STATUS_PENDING }})", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
            }

            when (selectedTab) {
                0 -> TeamMembersTab(
                    memberships = memberships,
                    currentUser = currentUser,
                    onChangeVanClick = { editingMember = it },
                    onPermissionsClick = { editingPermissionsMember = it },
                    onToggleSuspendClick = { member ->
                        scope.launch {
                            val suspend = member.status == "ACTIVE"
                            repository.toggleMemberStatus(member.membershipId, suspend, currentUser.displayName)
                            Toast.makeText(context, "${if (suspend) "Suspended" else "Activated"} ${member.displayName}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRemoveClick = { member ->
                        scope.launch {
                            repository.removeMember(member.membershipId, currentUser.displayName)
                            Toast.makeText(context, "Removed ${member.displayName}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                1 -> PendingInvitationsTab(
                    invitations = pendingInvitations,
                    currentUser = currentUser,
                    onRevokeClick = { inv ->
                        scope.launch {
                            repository.revokeInvitation(inv.invitationId)
                            Toast.makeText(context, "Revoked invitation for ${inv.recipientEmail}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    // Invite Employee Dialog
    if (showInviteDialog) {
        var email by remember { mutableStateOf("") }
        var selectedRole by remember { mutableStateOf(User.ROLE_SALESPERSON) }
        var selectedVanId by remember { mutableStateOf<String?>(vans.firstOrNull()?.vanId) }
        var selectedPermissions by remember { mutableStateOf(KanakPermissions.DEFAULT_SALESPERSON_PERMISSIONS.toMutableSet()) }
        var isInviting by remember { mutableStateOf(false) }
        var inviteError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isInviting) showInviteDialog = false },
            icon = {
                Surface(shape = CircleShape, color = KanakAccentLight, modifier = Modifier.size(52.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(28.dp))
                    }
                }
            },
            title = { Text("Invite Team Member", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Employees join your business using their individual Google account. No separate company activation needed.",
                        style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText)
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            inviteError = null
                        },
                        label = { Text("Employee Email Address *") },
                        placeholder = { Text("e.g. driver@kanak-fmcg.bh") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("invite_email_input")
                    )

                    Text("Select Role:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedRole == User.ROLE_SALESPERSON,
                            onClick = {
                                selectedRole = User.ROLE_SALESPERSON
                                selectedPermissions = KanakPermissions.DEFAULT_SALESPERSON_PERMISSIONS.toMutableSet()
                            },
                            label = { Text("Salesperson") }
                        )
                        FilterChip(
                            selected = selectedRole == User.ROLE_MANAGER,
                            onClick = {
                                selectedRole = User.ROLE_MANAGER
                                selectedPermissions = KanakPermissions.DEFAULT_MANAGER_PERMISSIONS.toMutableSet()
                            },
                            label = { Text("Manager") }
                        )
                    }

                    Text("Assigned Van:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    var expandedVan by remember { mutableStateOf(false) }
                    val currentVan = vans.find { it.vanId == selectedVanId }
                    Box {
                        OutlinedButton(
                            onClick = { expandedVan = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(currentVan?.vanCode ?: "No Assigned Van (Warehouse/Office)")
                        }
                        DropdownMenu(expanded = expandedVan, onDismissRequest = { expandedVan = false }) {
                            DropdownMenuItem(
                                text = { Text("None (Warehouse/Office)") },
                                onClick = {
                                    selectedVanId = null
                                    expandedVan = false
                                }
                            )
                            vans.forEach { v ->
                                DropdownMenuItem(
                                    text = { Text("${v.vanCode} (${v.vanName})") },
                                    onClick = {
                                        selectedVanId = v.vanId
                                        expandedVan = false
                                    }
                                )
                            }
                        }
                    }

                    Text("Granted Permissions:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    val previewPerms = listOf(
                        KanakPermissions.SALES_CREATE to "Create Invoices & Sell",
                        KanakPermissions.CUSTOMERS_CREATE to "Register New Customers",
                        KanakPermissions.PAYMENTS_RECORD to "Accept Cash & Credit Payments",
                        KanakPermissions.INVENTORY_READ_ASSIGNED to "View Van Stock",
                        KanakPermissions.INVENTORY_TRANSFER to "Transfer Stock Between Vans",
                        KanakPermissions.PRODUCTS_CHANGE_PRICE to "Change Selling Prices",
                        KanakPermissions.TEAM_INVITE to "Invite Other Staff"
                    )

                    previewPerms.forEach { (perm, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (selectedPermissions.contains(perm)) {
                                    selectedPermissions.remove(perm)
                                } else {
                                    selectedPermissions.add(perm)
                                }
                            }
                        ) {
                            Checkbox(
                                checked = selectedPermissions.contains(perm),
                                onCheckedChange = { checked ->
                                    if (checked) selectedPermissions.add(perm) else selectedPermissions.remove(perm)
                                }
                            )
                            Text(label, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (inviteError != null) {
                        Text(inviteError ?: "", color = KanakError, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isInviting = true
                            val res = repository.inviteEmployee(
                                recipientEmail = email,
                                role = selectedRole,
                                assignedVanId = selectedVanId,
                                permissions = selectedPermissions,
                                invitedBy = currentUser.displayName
                            )
                            isInviting = false
                            res.onSuccess { inv ->
                                Toast.makeText(context, "Invitation sent! Code: ${inv.inviteCode}", Toast.LENGTH_LONG).show()
                                showInviteDialog = false
                            }.onFailure {
                                inviteError = it.message ?: "Failed to invite employee"
                            }
                        }
                    },
                    enabled = !isInviting && email.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue, contentColor = Color.White),
                    modifier = Modifier.testTag("send_invitation_button")
                ) {
                    Text("Send Invitation", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showInviteDialog = false }, shape = RoundedCornerShape(10.dp)) {
                    Text("Cancel")
                }
            }
        )
    }

    // Change Van Dialog
    if (editingMember != null) {
        val member = editingMember!!
        var selectedVan by remember { mutableStateOf<String?>(member.assignedVanId) }

        AlertDialog(
            onDismissRequest = { editingMember = null },
            title = { Text("Change Van for ${member.displayName}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current Van: ${member.assignedVanCode ?: "None"}")
                    HorizontalDivider(color = KanakDivider)
                    Text("Select New Assigned Van:", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                repository.updateMemberRoleAndVan(member.membershipId, member.role, null, member.permissions, currentUser.displayName)
                                Toast.makeText(context, "Removed van assignment", Toast.LENGTH_SHORT).show()
                                editingMember = null
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("None (Warehouse/Office)")
                    }

                    vans.forEach { v ->
                        Button(
                            onClick = {
                                scope.launch {
                                    repository.updateMemberRoleAndVan(member.membershipId, member.role, v.vanId, member.permissions, currentUser.displayName)
                                    Toast.makeText(context, "Assigned ${member.displayName} to ${v.vanCode}", Toast.LENGTH_SHORT).show()
                                    editingMember = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedVan == v.vanId) KanakPrimaryBlue else KanakSurfaceVariant,
                                contentColor = if (selectedVan == v.vanId) Color.White else KanakPrimaryText
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${v.vanCode} (${v.vanName})")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = { editingMember = null }, shape = RoundedCornerShape(10.dp)) {
                    Text("Close")
                }
            }
        )
    }

    // Permissions Editor Dialog
    if (editingPermissionsMember != null) {
        val member = editingPermissionsMember!!
        var currentPerms by remember { mutableStateOf(member.permissions.toSet()) }

        val allPerms = listOf(
            KanakPermissions.SALES_CREATE to "Create Invoices",
            KanakPermissions.SALES_VOID to "Void Invoices",
            KanakPermissions.PAYMENTS_RECORD to "Record Payments",
            KanakPermissions.CUSTOMERS_CREATE to "Add Customers",
            KanakPermissions.SUPPLIERS_CREATE to "Add Suppliers",
            KanakPermissions.PRODUCTS_CHANGE_PRICE to "Change Selling Prices",
            KanakPermissions.INVENTORY_TRANSFER to "Transfer Van Stock",
            KanakPermissions.INVENTORY_ADJUST to "Adjust Inventory",
            KanakPermissions.VANS_CREATE to "Create Vans",
            KanakPermissions.TEAM_INVITE to "Invite Employees"
        )

        AlertDialog(
            onDismissRequest = { editingPermissionsMember = null },
            title = { Text("Permissions: ${member.displayName}", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Configure fine-grained permissions for this team member:", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))

                    allPerms.forEach { (perm, label) ->
                        val isChecked = currentPerms.contains(perm)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentPerms = if (isChecked) currentPerms - perm else currentPerms + perm
                                }
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    currentPerms = if (checked) currentPerms + perm else currentPerms - perm
                                }
                            )
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.updateMemberRoleAndVan(
                                membershipId = member.membershipId,
                                newRole = member.role,
                                newVanId = member.assignedVanId,
                                newPermissions = currentPerms,
                                updatedBy = currentUser.displayName
                            )
                            Toast.makeText(context, "Permissions updated for ${member.displayName}", Toast.LENGTH_SHORT).show()
                            editingPermissionsMember = null
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue, contentColor = Color.White)
                ) {
                    Text("Save Permissions", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { editingPermissionsMember = null }, shape = RoundedCornerShape(10.dp)) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TeamMembersTab(
    memberships: List<EmployeeMembership>,
    currentUser: User,
    onChangeVanClick: (EmployeeMembership) -> Unit,
    onPermissionsClick: (EmployeeMembership) -> Unit,
    onToggleSuspendClick: (EmployeeMembership) -> Unit,
    onRemoveClick: (EmployeeMembership) -> Unit
) {
    if (memberships.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.People,
            title = "No Team Members",
            message = "Invite salespeople and managers to join your fleet."
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(memberships, key = { it.membershipId }) { member ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth().testTag("member_card_${member.email}")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = when (member.role) {
                                        User.ROLE_OWNER -> Color(0xFFFEF3C7)
                                        User.ROLE_MANAGER -> KanakAccentLight
                                        else -> Color(0xFFDCFCE7)
                                    },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = when (member.role) {
                                                User.ROLE_OWNER -> Icons.Default.Shield
                                                User.ROLE_MANAGER -> Icons.Default.AdminPanelSettings
                                                else -> Icons.Default.Person
                                            },
                                            contentDescription = null,
                                            tint = when (member.role) {
                                                User.ROLE_OWNER -> Color(0xFFD97706)
                                                User.ROLE_MANAGER -> KanakPrimaryBlue
                                                else -> Color(0xFF16A34A)
                                            },
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(member.displayName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text(member.email, style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                                }
                            }

                            // Role Badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (member.role) {
                                    User.ROLE_OWNER -> Color(0xFFFEF3C7)
                                    User.ROLE_MANAGER -> KanakAccentLight
                                    else -> Color(0xFFDCFCE7)
                                }
                            ) {
                                Text(
                                    text = member.role.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = when (member.role) {
                                            User.ROLE_OWNER -> Color(0xFF92400E)
                                            User.ROLE_MANAGER -> KanakPrimaryBlue
                                            else -> Color(0xFF15803D)
                                        }
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = KanakDivider)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = KanakSecondaryText, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (member.assignedVanCode != null) "Van: ${member.assignedVanCode}" else "No Van Assigned",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (member.status == "ACTIVE") KanakSuccessBackground else Color(0xFFFEE2E2)
                            ) {
                                Text(
                                    text = member.status,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (member.status == "ACTIVE") KanakSuccess else KanakError,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Owner Action Buttons
                        if (currentUser.isOwner && member.role != User.ROLE_OWNER) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onChangeVanClick(member) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) {
                                    Text("Change Van", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = { onPermissionsClick(member) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) {
                                    Text("Permissions", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = { onToggleSuspendClick(member) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (member.status == "ACTIVE") Color(0xFFD97706) else KanakSuccess),
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) {
                                    Text(if (member.status == "ACTIVE") "Suspend" else "Activate", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingInvitationsTab(
    invitations: List<EmployeeInvitation>,
    currentUser: User,
    onRevokeClick: (EmployeeInvitation) -> Unit
) {
    val context = LocalContext.current
    val pendingList = remember(invitations) {
        invitations.filter { it.status == EmployeeInvitation.STATUS_PENDING }
    }

    if (pendingList.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.Mail,
            title = "No Pending Invitations",
            message = "Tap '+ Invite Employee' to invite salespeople to join your fleet."
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(pendingList, key = { it.invitationId }) { inv ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakSurface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(inv.recipientEmail, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text("Role: ${inv.role.uppercase()} • Van: ${inv.assignedVanCode ?: "None"}", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp))
                            }
                            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFEF3C7)) {
                                Text("PENDING", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF92400E), fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Invitation Code Pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = KanakAccentLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Invitation Acceptance Code:", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText, fontSize = 11.sp))
                                    Text(inv.inviteCode, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = KanakPrimaryBlue, letterSpacing = 2.sp))
                                }
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Invitation Code", inv.inviteCode))
                                    Toast.makeText(context, "Copied code ${inv.inviteCode}", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy Code", tint = KanakPrimaryBlue)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val shareMsg = "You're invited to join ${inv.businessName} on KANAK FMCG Mobile POS as ${inv.role}!\nInstall KANAK, sign in with your Google account ($inv.recipientEmail), and enter Invitation Code: ${inv.inviteCode}"
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, shareMsg)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share KANAK Invitation"))
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue, contentColor = Color.White),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share Invite", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { onRevokeClick(inv) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = KanakError),
                                modifier = Modifier.weight(0.7f).height(38.dp)
                            ) {
                                Text("Revoke", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
