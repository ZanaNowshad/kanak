package com.example.kanak.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemDiagnosticsScreen(
    repository: KanakRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dbFileSizeBytes by remember { mutableStateOf(0L) }
    var pendingSyncCount by remember { mutableStateOf(0) }
    var productCount by remember { mutableStateOf(0) }
    var invoiceCount by remember { mutableStateOf(0) }
    var customerCount by remember { mutableStateOf(0) }
    var isChecking by remember { mutableStateOf(true) }

    val products by repository.products.collectAsState()
    val invoices by repository.invoices.collectAsState()
    val customers by repository.customers.collectAsState()
    val pendingSyncFlow by repository.pendingSyncCount.collectAsState()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val dbFile = context.getDatabasePath("kanak_local_cache.db")
                dbFileSizeBytes = if (dbFile.exists()) dbFile.length() else 0L
            } catch (_: Exception) {}
            isChecking = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "System Diagnostics & Telemetry",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KanakBackground)
            )
        },
        containerColor = KanakBackground,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakPrimaryBlue),
                    modifier = Modifier.fillMaxWidth().testTag("diagnostics_hero_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "KANAK SYSTEM HEALTH MONITOR",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black, color = Color.White)
                            )
                        }
                        Text(
                            "Real-time telemetry of Room local database, WorkManager outbox queue depth, network gateway, and peripheral state.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                        )
                    }
                }
            }

            // Database Telemetry Card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakBackground),
                    border = androidx.compose.foundation.BorderStroke(1.dp, KanakSurfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("LOCAL ROOM DATABASE STATUS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, color = KanakSecondaryText))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Database Cache Size", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                            Text("${dbFileSizeBytes / 1024} KB", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText))
                        }
                        Divider(color = KanakSurfaceVariant)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cached Products Count", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                            Text("${products.size} records", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cached Invoices Count", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                            Text("${invoices.size} records", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cached Customers Count", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                            Text("${customers.size} records", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText))
                        }
                    }
                }
            }

            // Synchronization & WorkManager Outbox Card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = KanakBackground),
                    border = androidx.compose.foundation.BorderStroke(1.dp, KanakSurfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("SYNCHRONIZATION OUTBOX TELEMETRY", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, color = KanakSecondaryText))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Pending Outbox Queue Depth", style = MaterialTheme.typography.bodySmall.copy(color = KanakSecondaryText))
                            Surface(shape = RoundedCornerShape(6.dp), color = if (pendingSyncFlow > 0) Color(0xFFFEF3C7) else KanakSuccessBackground) {
                                Text(
                                    text = "$pendingSyncFlow Pending",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = if (pendingSyncFlow > 0) Color(0xFF92400E) else KanakSuccess),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    repository.syncAllPendingNow()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue),
                            modifier = Modifier.fillMaxWidth().testTag("diagnostics_force_sync_button")
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Force Flush Outbox Queue Now")
                        }
                    }
                }
            }
        }
    }
}
