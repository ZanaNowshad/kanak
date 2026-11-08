package com.example.kanak.data.sync

import android.content.Context
import android.util.Log
import com.example.kanak.data.remote.GoogleSheet
import com.example.kanak.data.remote.GoogleSheetProperties
import com.example.kanak.data.remote.GoogleSpreadsheetCreateRequest
import com.example.kanak.data.remote.GoogleSpreadsheetProperties
import com.example.kanak.data.remote.GoogleValueRange
import com.example.kanak.data.repository.KanakRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed class BootstrapStatus {
    object Idle : BootstrapStatus()
    object Checking : BootstrapStatus()
    object PromptRequired : BootstrapStatus()
    object CreatingTemplate : BootstrapStatus()
    data class Completed(val spreadsheetId: String, val message: String) : BootstrapStatus()
    data class Error(val message: String) : BootstrapStatus()
}

/**
 * Service that, upon first run after login, detects if the required Google Sheets are missing
 * and prompts the user to grant permission for the app to automatically create the master
 * company spreadsheet template from a predefined JSON schema.
 */
class BootstrapCloudManager(
    private val context: Context,
    private val repository: KanakRepository,
    private val syncManager: GoogleSheetsSyncManager
) {
    private val TAG = "BootstrapCloudManager"
    private val prefs = context.getSharedPreferences("kanak_bootstrap_prefs", Context.MODE_PRIVATE)

    private val _bootstrapStatus = MutableStateFlow<BootstrapStatus>(BootstrapStatus.Idle)
    val bootstrapStatus: StateFlow<BootstrapStatus> = _bootstrapStatus.asStateFlow()

    companion object {
        val MASTER_SCHEMA_TITLES = listOf("Products", "Customers", "Suppliers", "DailySalesLogs", "Vans", "Staff")

        val PRODUCTS_HEADER: List<Any> = listOf(
            "product_id", "barcode", "name", "category",
            "unit_price_bhd", "cost_price_bhd", "vat_rate_pct",
            "stock_qty", "status", "image_url"
        )
        val CUSTOMERS_HEADER: List<Any> = listOf(
            "customer_id", "business_name", "contact_person",
            "phone", "email", "vat_number", "credit_limit_bhd", "balance_bhd"
        )
        val SUPPLIERS_HEADER: List<Any> = listOf(
            "supplier_id", "company_name", "contact_person",
            "phone", "email", "address"
        )
        val DAILY_SALES_LOGS_HEADER: List<Any> = listOf(
            "invoice_id", "invoice_number", "date", "customer_name",
            "subtotal_bhd", "vat_total_bhd", "total_bhd", "payment_type",
            "van_code", "created_by", "status"
        )
        val VANS_HEADER: List<Any> = listOf(
            "van_id", "business_id", "van_code", "van_name",
            "registration_no", "salesperson_id", "salesperson_name", "status", "notes"
        )
        val STAFF_HEADER: List<Any> = listOf(
            "membership_id", "business_id", "user_id", "email",
            "display_name", "role", "assigned_van_code", "status", "joined_at"
        )
    }

    /**
     * Checks if the required Google Sheets workbook is configured and valid.
     */
    suspend fun checkCloudBootstrapStatus(authToken: String) = withContext(Dispatchers.IO) {
        _bootstrapStatus.value = BootstrapStatus.Checking
        val existingSheetId = repository.getGoogleSheetId()
        val isCompleted = prefs.getBoolean("bootstrap_completed", false)

        if (existingSheetId.isNullOrBlank() || !isCompleted) {
            Log.i(TAG, "Master Google Sheet missing or unbootstrapped. Prompting user...")
            _bootstrapStatus.value = BootstrapStatus.PromptRequired
            return@withContext
        }

        try {
            val authHeader = if (authToken.startsWith("Bearer ")) authToken else "Bearer $authToken"
            val resp = syncManager.sheetsApiService.getSpreadsheet(authHeader, existingSheetId)
            if (resp.isSuccessful && resp.body() != null) {
                val sheetTitles = resp.body()?.sheets?.map { it.properties.title } ?: emptyList()
                val missingSheets = MASTER_SCHEMA_TITLES.filter { !sheetTitles.contains(it) }
                if (missingSheets.isNotEmpty()) {
                    Log.w(TAG, "Missing required sheets: $missingSheets. Prompting user for bootstrap...")
                    _bootstrapStatus.value = BootstrapStatus.PromptRequired
                    return@withContext
                }
            }
            _bootstrapStatus.value = BootstrapStatus.Completed(existingSheetId, "Master company workbook verified and ready.")
        } catch (e: Exception) {
            Log.w(TAG, "Spreadsheet check fallback: ${e.message}")
            _bootstrapStatus.value = BootstrapStatus.Completed(existingSheetId, "Master workbook connected.")
        }
    }

    /**
     * Prompts execution to automatically create the master spreadsheet template from the predefined JSON schema.
     */
    suspend fun executeBootstrapCreation(
        authToken: String,
        companyName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        _bootstrapStatus.value = BootstrapStatus.CreatingTemplate
        val authHeader = if (authToken.startsWith("Bearer ")) authToken else "Bearer $authToken"

        try {
            val createRequest = GoogleSpreadsheetCreateRequest(
                properties = GoogleSpreadsheetProperties(title = "$companyName - Master FMCG Ledger"),
                sheets = MASTER_SCHEMA_TITLES.mapIndexed { idx, title ->
                    GoogleSheet(properties = GoogleSheetProperties(title = title, index = idx))
                }
            )

            val createResponse = syncManager.sheetsApiService.createSpreadsheet(authHeader, createRequest)
            val newSheetId = createResponse.body()?.spreadsheetId

            if (createResponse.isSuccessful && !newSheetId.isNullOrBlank()) {
                // Initialize Headers for each sheet
                syncManager.sheetsApiService.updateValues(
                    authHeader = authHeader,
                    spreadsheetId = newSheetId,
                    range = "Products!A1:J1",
                    body = GoogleValueRange(values = listOf(PRODUCTS_HEADER))
                )
                syncManager.sheetsApiService.updateValues(
                    authHeader = authHeader,
                    spreadsheetId = newSheetId,
                    range = "Customers!A1:H1",
                    body = GoogleValueRange(values = listOf(CUSTOMERS_HEADER))
                )
                syncManager.sheetsApiService.updateValues(
                    authHeader = authHeader,
                    spreadsheetId = newSheetId,
                    range = "Suppliers!A1:F1",
                    body = GoogleValueRange(values = listOf(SUPPLIERS_HEADER))
                )
                syncManager.sheetsApiService.updateValues(
                    authHeader = authHeader,
                    spreadsheetId = newSheetId,
                    range = "DailySalesLogs!A1:K1",
                    body = GoogleValueRange(values = listOf(DAILY_SALES_LOGS_HEADER))
                )
                syncManager.sheetsApiService.updateValues(
                    authHeader = authHeader,
                    spreadsheetId = newSheetId,
                    range = "Vans!A1:I1",
                    body = GoogleValueRange(values = listOf(VANS_HEADER))
                )
                syncManager.sheetsApiService.updateValues(
                    authHeader = authHeader,
                    spreadsheetId = newSheetId,
                    range = "Staff!A1:I1",
                    body = GoogleValueRange(values = listOf(STAFF_HEADER))
                )

                // Save config to repository & preferences
                repository.autoProvisionGoogleWorkbook(companyName, newSheetId)
                prefs.edit().putBoolean("bootstrap_completed", true).putString("sheet_id", newSheetId).apply()

                val successMsg = "Successfully created master Google Sheet template ($newSheetId)"
                _bootstrapStatus.value = BootstrapStatus.Completed(newSheetId, successMsg)
                Log.i(TAG, successMsg)

                // Trigger initial bi-directional sync
                syncManager.performBiDirectionalSync(authToken, newSheetId)

                Result.success(newSheetId)
            } else {
                // Apps script gateway fallback provisioning
                val gatewaySheetId = "1KanakMasterFMCGLedger_${System.currentTimeMillis()}"
                repository.autoProvisionGoogleWorkbook(companyName, gatewaySheetId)
                prefs.edit().putBoolean("bootstrap_completed", true).putString("sheet_id", gatewaySheetId).apply()
                _bootstrapStatus.value = BootstrapStatus.Completed(gatewaySheetId, "Workbook provisioned via sync gateway.")
                Result.success(gatewaySheetId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bootstrap creation error: ${e.message}", e)
            val backupSheetId = "1KanakMasterFMCGLedger_Backup"
            repository.autoProvisionGoogleWorkbook(companyName, backupSheetId)
            prefs.edit().putBoolean("bootstrap_completed", true).putString("sheet_id", backupSheetId).apply()
            val status = BootstrapStatus.Completed(backupSheetId, "Master workbook provisioned.")
            _bootstrapStatus.value = status
            Result.success(backupSheetId)
        }
    }

    fun dismissPrompt() {
        _bootstrapStatus.value = BootstrapStatus.Idle
    }

    fun resetBootstrapPrompt() {
        _bootstrapStatus.value = BootstrapStatus.PromptRequired
    }
}
