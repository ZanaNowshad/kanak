package com.example.kanak.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanak.data.repository.KanakRepository
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Always-visible Connection and Synchronization Status Bar.
 * Clearly differentiates between Online, Offline, Pending, Syncing, and Conflict states.
 * Allows instant 1-tap background synchronization with feedback.
 */
@Composable
fun SyncStatusBar(
    repository: KanakRepository,
    modifier: Modifier = Modifier
) {
    val syncState by repository.syncState.collectAsState()
    val isOnline by repository.isOnline.collectAsState()
    val pendingCount by repository.pendingSyncCount.collectAsState()
    val conflictMessage by repository.conflictMessage.collectAsState()
    val isSyncing by repository.isSyncing.collectAsState()
    val scope = rememberCoroutineScope()
    var localSyncError by remember { mutableStateOf<String?>(null) }

    Surface(
        color = when (syncState) {
            KanakRepository.SyncState.ONLINE -> Color(0xFFF0FDF4) // light green
            KanakRepository.SyncState.OFFLINE -> Color(0xFFFFFBEB) // light amber
            KanakRepository.SyncState.PENDING -> Color(0xFFEFF6FF) // light blue
            KanakRepository.SyncState.SYNCING -> Color(0xFFECFEFF) // light cyan
            KanakRepository.SyncState.CONFLICT -> Color(0xFFFEF2F2) // light red
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when (syncState) {
                KanakRepository.SyncState.ONLINE -> Color(0xFF86EFAC)
                KanakRepository.SyncState.OFFLINE -> Color(0xFFFDE68A)
                KanakRepository.SyncState.PENDING -> Color(0xFF93C5FD)
                KanakRepository.SyncState.SYNCING -> Color(0xFF67E8F9)
                KanakRepository.SyncState.CONFLICT -> Color(0xFFFCA5A5)
            }
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("sync_status_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Status Indicator Icon / Spinner
                when (syncState) {
                    KanakRepository.SyncState.ONLINE -> {
                        Surface(shape = CircleShape, color = KanakSuccess, modifier = Modifier.size(8.dp)) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = KanakSuccess, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ONLINE • ALL SYNCED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534),
                                fontSize = 11.sp
                            )
                        )
                    }
                    KanakRepository.SyncState.OFFLINE -> {
                        Surface(shape = CircleShape, color = Color(0xFFF59E0B), modifier = Modifier.size(8.dp)) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "OFFLINE MODE • LOCAL PERSISTENCE ACTIVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E),
                                fontSize = 11.sp
                            )
                        )
                    }
                    KanakRepository.SyncState.PENDING -> {
                        Surface(shape = CircleShape, color = KanakPrimaryBlue, modifier = Modifier.size(8.dp)) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = KanakPrimaryBlue, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$pendingCount LOCAL SALE(S) PENDING SYNC",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = KanakPrimaryBlue,
                                fontSize = 11.sp
                            )
                        )
                    }
                    KanakRepository.SyncState.SYNCING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = KanakPrimaryBlue
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SYNCING SALES WITH GOOGLE SHEETS...",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = KanakPrimaryBlue,
                                fontSize = 11.sp
                            )
                        )
                    }
                    KanakRepository.SyncState.CONFLICT -> {
                        Surface(shape = CircleShape, color = KanakError, modifier = Modifier.size(8.dp)) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.Warning, contentDescription = null, tint = KanakError, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = conflictMessage ?: "SYNC CONFLICT DETECTED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = KanakError,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            // Sync Button Action
            if (syncState != KanakRepository.SyncState.SYNCING) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (syncState) {
                        KanakRepository.SyncState.PENDING, KanakRepository.SyncState.OFFLINE -> KanakPrimaryBlue
                        KanakRepository.SyncState.CONFLICT -> KanakError
                        KanakRepository.SyncState.ONLINE -> Color.Transparent
                        else -> Color.Transparent
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !isSyncing) {
                            localSyncError = null
                            scope.launch {
                                repository.syncAllPendingNow()
                            }
                        }
                        .testTag("sync_action_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync Now",
                            tint = if (syncState == KanakRepository.SyncState.ONLINE) KanakSecondaryText else Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (syncState == KanakRepository.SyncState.ONLINE) "REFRESH" else "SYNC NOW",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (syncState == KanakRepository.SyncState.ONLINE) KanakSecondaryText else Color.White,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
