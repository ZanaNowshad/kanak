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
        val MASTER_SCHEMA_TITLES = listOf("Products", "Customers", "Suppliers", "DailySalesLogs", "Vans", "Staff", "Users")

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
        val USERS_HEADER: List<Any> = listOf("user_id", "email", "role", "active")
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
        val sheetId = repository.getGoogleSheetId()
        if (sheetId.isNullOrBlank() || !prefs.getBoolean("bootstrap_completed", false)) {
            _bootstrapStatus.value = BootstrapStatus.PromptRequired
            return@withContext
        }
        if (authToken.isBlank()) {
            _bootstrapStatus.value = BootstrapStatus.Error("Authorize Google Sheets before verification")
            return@withContext
        }
        try {
            val authHeader = if (authToken.startsWith("Bearer ")) authToken else "Bearer $authToken"
            val response = syncManager.sheetsApiService.getSpreadsheet(authHeader, sheetId)
            if (!response.isSuccessful || response.body() == null) {
                _bootstrapStatus.value = BootstrapStatus.Error("Google Sheets verification failed: HTTP ${response.code()}")
                return@withContext
            }
            val titles = response.body()?.sheets?.map { it.properties.title } ?: emptyList()
            val missing = MASTER_SCHEMA_TITLES.filterNot { it in titles }
            if (missing.isNotEmpty()) {
                _bootstrapStatus.value = BootstrapStatus.Error("Workbook missing sheets: ${missing.joinToString()}")
            } else {
                _bootstrapStatus.value = BootstrapStatus.Completed(sheetId, "Google Sheet verified")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Sheet verification failed", e)
            _bootstrapStatus.value = BootstrapStatus.Error(e.message ?: "Unable to verify workbook")
        }
    }
    /**
     * Prompts execution to automatically create the master spreadsheet template from the predefined JSON schema.
     */
    suspend fun executeBootstrapCreation(
        authToken: String,
        companyName: String,
        ownerEmail: String = "",
        ownerUserId: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        if (authToken.isBlank() || ownerEmail.isBlank() || ownerUserId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Verified owner and Google Sheets OAuth required"))
        }
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

                val usersHeaderResult = syncManager.sheetsApiService.updateValues(
                    authHeader = authHeader,
                    spreadsheetId = newSheetId,
                    range = "Users!A1:D1",
                    body = GoogleValueRange(values = listOf(USERS_HEADER))
                )
                val ownerRowResult = syncManager.sheetsApiService.updateValues(
                    authHeader = authHeader,
                    spreadsheetId = newSheetId,
                    range = "Users!A2:D2",
                    body = GoogleValueRange(values = listOf(listOf(ownerUserId, ownerEmail, "admin", true)))
                )
                if (!usersHeaderResult.isSuccessful || !ownerRowResult.isSuccessful) {
                    error("Unable to initialize the company admin in Google Sheets")
                }
                // Save config to repository & preferences
                repository.autoProvisionGoogleWorkbook(companyName, newSheetId)
                prefs.edit().putBoolean("bootstrap_completed", true).putString("sheet_id", newSheetId).apply()

                val successMsg = "Successfully created master Google Sheet template ($newSheetId)"
                _bootstrapStatus.value = BootstrapStatus.Completed(newSheetId, successMsg)
                Log.i(TAG, successMsg)

                // Trigger initial bi-directional sync
                val initialSync = syncManager.performBiDirectionalSync(authToken, newSheetId)
                if (initialSync.isFailure) {
                    val reason = initialSync.exceptionOrNull()?.message ?: "Initial sync failed"
                    _bootstrapStatus.value = BootstrapStatus.Error(reason)
                    return@withContext Result.failure(IllegalStateException(reason))
                }

                Result.success(newSheetId)
            } else {
                val error = "Google Sheets API could not create the workbook: HTTP ${createResponse.code()}"
                _bootstrapStatus.value = BootstrapStatus.Error(error)
                Result.failure(IllegalStateException(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Workbook creation failed", e)
            _bootstrapStatus.value = BootstrapStatus.Error(e.message ?: "Unable to create workbook")
            Result.failure(e)
        }
    }

    fun dismissPrompt() {
        _bootstrapStatus.value = BootstrapStatus.Idle
    }

    fun resetBootstrapPrompt() {
        _bootstrapStatus.value = BootstrapStatus.PromptRequired
    }
}
