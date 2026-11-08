package com.example.kanak.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanak.data.sync.BootstrapStatus
import com.example.ui.theme.KanakBackground
import com.example.ui.theme.KanakPrimaryBlue
import com.example.ui.theme.KanakPrimaryText
import com.example.ui.theme.KanakSecondaryText

@Composable
fun BootstrapCloudDialog(
    status: BootstrapStatus,
    companyName: String,
    onGrantPermissionAndCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    if (status !is BootstrapStatus.PromptRequired && status !is BootstrapStatus.CreatingTemplate) {
        return
    }

    AlertDialog(
        onDismissRequest = { if (status !is BootstrapStatus.CreatingTemplate) onDismiss() },
        shape = RoundedCornerShape(20.dp),
        containerColor = KanakBackground,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = KanakPrimaryBlue.copy(alpha = 0.15f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = KanakPrimaryBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Bootstrap Master Cloud Ledger",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = KanakPrimaryText,
                            fontSize = 16.sp
                        )
                    )
                    Text(
                        text = "Google Sheets Integration",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = KanakSecondaryText,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "The application detected that the master company spreadsheet for \"$companyName\" is missing or not initialized.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = KanakPrimaryText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                )

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, KanakPrimaryBlue.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "AUTOMATED TEMPLATE GENERATION INCLUDES:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = KanakPrimaryBlue,
                                fontSize = 10.sp
                            )
                        )
                        Text("• Products Master (Barcodes, Prices, Stock)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = KanakPrimaryText))
                        Text("• Customers Directory (VAT Numbers, Credit Limits)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = KanakPrimaryText))
                        Text("• Suppliers Register (Addresses, Contact)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = KanakPrimaryText))
                        Text("• Daily Sales Logs (BHD Invoice Ledger)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = KanakPrimaryText))
                    }
                }

                if (status is BootstrapStatus.CreatingTemplate) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = KanakPrimaryBlue,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Creating master spreadsheet schema...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = KanakPrimaryBlue,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onGrantPermissionAndCreate,
                enabled = status !is BootstrapStatus.CreatingTemplate,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue),
                modifier = Modifier.testTag("bootstrap_create_template_button")
            ) {
                Text(
                    text = "Grant & Create Master Workbook",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        dismissButton = {
            if (status !is BootstrapStatus.CreatingTemplate) {
                TextButton(onClick = onDismiss) {
                    Text("Skip for Now", style = MaterialTheme.typography.labelMedium.copy(color = KanakSecondaryText))
                }
            }
        }
    )
}
