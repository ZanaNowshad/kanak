package com.example.kanak.data.sync

import android.content.Context
import android.util.Log
import com.example.kanak.data.remote.*
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.domain.financial.BahrainFinancials
import com.example.kanak.domain.models.Customer
import com.example.kanak.domain.models.EmployeeMembership
import com.example.kanak.domain.models.Product
import com.example.kanak.domain.models.Supplier
import com.example.kanak.domain.models.Van
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(val itemsProcessed: Int, val message: String) : SyncState()
    data class Error(val message: String) : SyncState()
}

/**
 * Service managing direct bi-directional synchronization of product data, customers,
 * suppliers, and daily sales logs with Google Sheets API v4.
 */
class GoogleSheetsSyncManager(
    private val context: Context,
    private val repository: KanakRepository
) {
    private val TAG = "GoogleSheetsSyncManager"

    val sheetsService: GoogleSheetsService = GoogleSheetsService {
        com.example.kanak.auth.GoogleAuthManager.activeGoogleSheetsAccessToken()
    }

    val sheetsApiService: GoogleSheetsApiService get() = sheetsService.api

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    /**
     * Performs complete bi-directional sync across all 4 worksheets:
     * 1. Products: Bi-directional sync between Google Sheets and local database.
     * 2. Customers: Bi-directional directory sync with credit limits and contacts.
     * 3. Suppliers: Bi-directional vendor register sync.
     * 4. DailySalesLogs: Authoritative sales invoice append with BHD 3-decimal precision.
     */
    suspend fun performBiDirectionalSync(
        authToken: String,
        spreadsheetId: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (authToken.isBlank() || spreadsheetId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Google Sheets OAuth and a real spreadsheet ID are required"))
        }
        _syncState.value = SyncState.Syncing
        val authHeader = if (authToken.startsWith("Bearer ")) authToken else "Bearer $authToken"
        var processedCount = 0

        try {
            // 1. Products Sync
            val pullProd = pullProductsFromSheets(authHeader, spreadsheetId).getOrThrow()
            val pushProd = pushProductsToSheets(authHeader, spreadsheetId).getOrThrow()
            processedCount += pullProd + pushProd

            // 2. Customers Sync
            val pullCust = pullCustomersFromSheets(authHeader, spreadsheetId).getOrThrow()
            val pushCust = pushCustomersToSheets(authHeader, spreadsheetId).getOrThrow()
            processedCount += pullCust + pushCust

            // 3. Suppliers Sync
            val pullSup = pullSuppliersFromSheets(authHeader, spreadsheetId).getOrThrow()
            val pushSup = pushSuppliersToSheets(authHeader, spreadsheetId).getOrThrow()
            processedCount += pullSup + pushSup

            // 4. Daily Sales Logs Push
            val pushSales = pushDailySalesLogsToSheets(authHeader, spreadsheetId).getOrThrow()
            processedCount += pushSales

            // 5. Vans Sync
            val pullVans = pullVansFromSheets(authHeader, spreadsheetId).getOrThrow()
            val pushVans = pushVansToSheets(authHeader, spreadsheetId).getOrThrow()
            processedCount += pullVans + pushVans

            // 6. Staff Sync
            val pullStaff = pullStaffFromSheets(authHeader, spreadsheetId).getOrThrow()
            val pushStaff = pushStaffToSheets(authHeader, spreadsheetId).getOrThrow()
            processedCount += pullStaff + pushStaff

            val summaryMsg = "Bi-directional sync completed ($processedCount records synchronized across Products, Customers, Suppliers, Vans, Staff & Sales)"
            _syncState.value = SyncState.Success(
                itemsProcessed = processedCount,
                message = summaryMsg
            )
            Log.i(TAG, summaryMsg)
            Result.success(processedCount)
        } catch (e: Exception) {
            val err = "Sync error: ${e.message ?: "Network error"}"
            Log.e(TAG, err, e)
            _syncState.value = SyncState.Error(err)
            Result.failure(e)
        }
    }

    /**
     * Pulls product rows from Google Sheets 'Products!A2:J1000' and updates local repository.
     */
    suspend fun pullProductsFromSheets(
        authHeader: String,
        spreadsheetId: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val response = sheetsApiService.getValues(authHeader, spreadsheetId, "Products!A2:J1000")
            if (!response.isSuccessful || response.body() == null) {
                return@withContext Result.success(0)
            }

            val values = response.body()?.values ?: emptyList()
            var count = 0
            for (row in values) {
                if (row.size < 3) continue
                val productId = row.getOrNull(0)?.toString()?.takeIf { it.isNotBlank() } ?: continue
                val barcode = row.getOrNull(1)?.toString() ?: ""
                val name = row.getOrNull(2)?.toString() ?: ""
                val unitPriceStr = row.getOrNull(4)?.toString() ?: "0.000"
                val costPriceStr = row.getOrNull(5)?.toString() ?: "0.000"
                val vatRateStr = row.getOrNull(6)?.toString() ?: "0.10"
                val stockQtyStr = row.getOrNull(7)?.toString() ?: "0.000"
                val imageUrl = row.getOrNull(9)?.toString() ?: ""

                val costPrice = BahrainFinancials.parseBhd(costPriceStr)
                val sellingPrice = BahrainFinancials.parseBhd(unitPriceStr)
                val vatRate = try { BigDecimal(vatRateStr.ifBlank { "0.10" }) } catch (_: Exception) { BigDecimal("0.10") }
                val vatAmount = sellingPrice.multiply(vatRate)
                val stockQty = BahrainFinancials.parseBhd(stockQtyStr)

                val product = Product(
                    productId = productId,
                    barcode = barcode,
                    name = name,
                    costPrice = costPrice,
                    sellingPrice = sellingPrice,
                    vatRate = vatRate,
                    vatAmount = vatAmount,
                    photoUrl = imageUrl.ifBlank { null },
                    stockQty = stockQty
                )
                repository.upsertProductLocally(product)
                count++
            }
            Result.success(count)
        } catch (e: Exception) {
            Log.w(TAG, "Pull products exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Pushes local products to Google Sheets 'Products!A2:J'.
     */
    suspend fun pushProductsToSheets(
        authHeader: String,
        spreadsheetId: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val prods = repository.products.value
            if (prods.isEmpty()) return@withContext Result.success(0)

            val rows = prods.map { p ->
                listOf(
                    p.productId,
                    p.barcode,
                    p.name,
                    "General FMCG",
                    BahrainFinancials.formatBhd(p.sellingPrice),
                    BahrainFinancials.formatBhd(p.costPrice),
                    p.vatRate.toPlainString(),
                    BahrainFinancials.formatBhd(p.stockQty),
                    "ACTIVE",
                    p.photoUrl ?: ""
                )
            }

            val range = "Products!A2:J${rows.size + 1}"
            val resp = sheetsApiService.updateValues(
                authHeader = authHeader,
                spreadsheetId = spreadsheetId,
                range = range,
                body = GoogleValueRange(range = range, values = rows)
            )
            if (resp.isSuccessful) {
                Result.success(rows.size)
            } else {
                Result.success(0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Push products exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Pulls customer directory from Google Sheets 'Customers!A2:H1000' and updates local repository.
     */
    suspend fun pullCustomersFromSheets(
        authHeader: String,
        spreadsheetId: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val response = sheetsApiService.getValues(authHeader, spreadsheetId, "Customers!A2:H1000")
            if (!response.isSuccessful || response.body() == null) {
                return@withContext Result.success(0)
            }

            val values = response.body()?.values ?: emptyList()
            var count = 0
            for (row in values) {
                if (row.size < 2) continue
                val customerId = row.getOrNull(0)?.toString()?.takeIf { it.isNotBlank() } ?: continue
                val businessName = row.getOrNull(1)?.toString()?.takeIf { it.isNotBlank() } ?: continue
                val contactPerson = row.getOrNull(2)?.toString() ?: ""
                val phone = row.getOrNull(3)?.toString() ?: ""
                val address = row.getOrNull(5)?.toString() ?: ""

                val customer = Customer(
                    customerId = customerId,
                    businessName = businessName,
                    contact = contactPerson,
                    phone = phone,
                    address = address
                )
                repository.upsertCustomerLocally(customer)
                count++
            }
            Result.success(count)
        } catch (e: Exception) {
            Log.w(TAG, "Pull customers exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Pushes local customers to Google Sheets 'Customers!A2:H'.
     */
    suspend fun pushCustomersToSheets(
        authHeader: String,
        spreadsheetId: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val custs = repository.customers.value
            if (custs.isEmpty()) return@withContext Result.success(0)

            val rows = custs.map { c ->
                listOf(
                    c.customerId,
                    c.businessName,
                    c.contact,
                    c.phone,
                    "", // email
                    "", // vat number
                    "500.000", // credit limit
                    "0.000" // balance
                )
            }

            val range = "Customers!A2:H${rows.size + 1}"
            val resp = sheetsApiService.updateValues(
                authHeader = authHeader,
                spreadsheetId = spreadsheetId,
                range = range,
                body = GoogleValueRange(range = range, values = rows)
            )
            if (resp.isSuccessful) {
                Result.success(rows.size)
            } else {
                Result.success(0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Push customers exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Pulls suppliers directory from Google Sheets 'Suppliers!A2:F1000' and updates local repository.
     */
    suspend fun pullSuppliersFromSheets(
        authHeader: String,
        spreadsheetId: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val response = sheetsApiService.getValues(authHeader, spreadsheetId, "Suppliers!A2:F1000")
            if (!response.isSuccessful || response.body() == null) {
                return@withContext Result.success(0)
            }

            val values = response.body()?.values ?: emptyList()
            var count = 0
            for (row in values) {
                if (row.size < 2) continue
                val supplierId = row.getOrNull(0)?.toString()?.takeIf { it.isNotBlank() } ?: continue
                val companyName = row.getOrNull(1)?.toString()?.takeIf { it.isNotBlank() } ?: continue
                val contactPerson = row.getOrNull(2)?.toString() ?: ""

                val supplier = Supplier(
                    supplierId = supplierId,
                    name = companyName,
                    contact = contactPerson
                )
                repository.upsertSupplierLocally(supplier)
                count++
            }
            Result.success(count)
        } catch (e: Exception) {
            Log.w(TAG, "Pull suppliers exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Pushes local suppliers to Google Sheets 'Suppliers!A2:F'.
     */
    suspend fun pushSuppliersToSheets(
        authHeader: String,
        spreadsheetId: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val sups = repository.suppliers.value
            if (sups.isEmpty()) return@withContext Result.success(0)

            val rows = sups.map { s ->
                listOf(
                    s.supplierId,
                    s.name,
                    s.contact,
                    "", // phone
                    "", // email
                    ""  // address
                )
            }

            val range = "Suppliers!A2:F${rows.size + 1}"
            val resp = sheetsApiService.updateValues(
                authHeader = authHeader,
                spreadsheetId = spreadsheetId,
                range = range,
                body = GoogleValueRange(range = range, values = rows)
            )
            if (resp.isSuccessful) {
                Result.success(rows.size)
            } else {
                Result.success(0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Push suppliers exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Pushes completed sales records directly to Google Sheets 'DailySalesLogs!A:K'
     * and triggers authoritative Firestore gateway sync.
     */
    suspend fun pushDailySalesLogsToSheets(
        authHeader: String,
        spreadsheetId: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        var totalPushed = 0
        try {
            // 1. Fetch pending sales transactions stored in local SQLite Room database
            val pendingRoomInvoices = repository.getPendingOfflineInvoicesFromRoom()
            val targetInvoices = if (pendingRoomInvoices.isNotEmpty()) {
                pendingRoomInvoices
            } else {
                repository.invoices.value.filter { it.status == "POSTED" && it.syncStatus == "PENDING" }
            }

            if (targetInvoices.isNotEmpty()) {
                val rows = targetInvoices.map { inv ->
                    listOf(
                        inv.invoiceId,
                        inv.invoiceNumber,
                        inv.date,
                        inv.customerName,
                        BahrainFinancials.formatBhd(inv.subtotal),
                        BahrainFinancials.formatBhd(inv.vatTotal),
                        BahrainFinancials.formatBhd(inv.total),
                        inv.paymentType,
                        inv.vanCode.ifBlank { "VAN-MAIN" },
                        inv.createdBy,
                        inv.status
                    )
                }

                val appendResp = sheetsService.appendRows(
                    spreadsheetId = spreadsheetId,
                    range = "DailySalesLogs!A:K",
                    rows = rows
                )

                appendResp.onSuccess { count ->
                    totalPushed += count
                    // Mark sales transactions as SYNCED in local SQLite Room database
                    for (inv in targetInvoices) {
                        repository.markInvoiceSyncedInRoom(inv.invoiceId)
                    }
                    Log.i(TAG, "Uploaded $count pending offline sales to Google Sheets 'DailySalesLogs' worksheet and marked SYNCED in Room.")
                }.onFailure { err ->
                    Log.w(TAG, "Direct Sheets append error for offline sales: ${err.message}")
                }
            }

            // 2. Also flush pending items from Room sync queue and Firestore backup
            val pushResult = repository.pushFirestoreSalesToGoogleSheets()
            totalPushed += pushResult.getOrThrow()

            val queueResult = repository.flushPendingRoomSyncQueue()
            totalPushed += queueResult.getOrThrow()

            Result.success(totalPushed)
        } catch (e: Exception) {
            Log.w(TAG, "Push daily sales exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Pulls van fleet records from Google Sheets 'Vans!A2:I1000' and updates local repository.
     */
    suspend fun pullVansFromSheets(
        authHeader: String,
        spreadsheetId: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val response = sheetsApiService.getValues(authHeader, spreadsheetId, "Vans!A2:I1000")
            if (!response.isSuccessful || response.body() == null) {
                return@withContext Result.success(0)
            }

            val values = response.body()?.values ?: emptyList()
            var count = 0
            for (row in values) {
                if (row.size < 4) continue
                val vanId = row.getOrNull(0)?.toString()?.takeIf { it.isNotBlank() } ?: continue
                val businessId = row.getOrNull(1)?.toString() ?: repository.getBusinessId()
                val vanCode = row.getOrNull(2)?.toString() ?: ""
                val vanName = row.getOrNull(3)?.toString() ?: ""
                val regNo = row.getOrNull(4)?.toString() ?: ""
                val spId = row.getOrNull(5)?.toString()?.takeIf { it.isNotBlank() }
                val spName = row.getOrNull(6)?.toString()?.takeIf { it.isNotBlank() }
                val status = row.getOrNull(7)?.toString() ?: Van.STATUS_ACTIVE
                val notes = row.getOrNull(8)?.toString() ?: ""

                val van = Van(
                    vanId = vanId,
                    businessId = businessId,
                    vanCode = vanCode,
                    vanName = vanName,
                    registrationNo = regNo,
                    assignedSalespersonId = spId,
                    assignedSalespersonName = spName,
                    status = status,
                    notes = notes
                )
                repository.upsertVanLocally(van)
                count++
            }
            Result.success(count)
        } catch (e: Exception) {
            Log.w(TAG, "Pull vans exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Pushes local vans to Google Sheets 'Vans!A2:I'.
     */
    suspend fun pushVansToSheets(
        authHeader: String,
        spreadsheetId: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val vans = repository.vans.value
            if (vans.isEmpty()) return@withContext Result.success(0)

            val rows = vans.map { v ->
                listOf(
                    v.vanId,
                    v.businessId,
                    v.vanCode,
                    v.vanName,
                    v.registrationNo,
                    v.assignedSalespersonId ?: "",
                    v.assignedSalespersonName ?: "",
                    v.status,
                    v.notes
                )
            }

            val range = "Vans!A2:I${rows.size + 1}"
            val resp = sheetsApiService.updateValues(
                authHeader = authHeader,
                spreadsheetId = spreadsheetId,
                range = range,
                body = GoogleValueRange(range = range, values = rows)
            )
            if (resp.isSuccessful) {
                Result.success(rows.size)
            } else {
                Result.success(0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Push vans exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Pulls staff records from Google Sheets 'Staff!A2:I1000' and updates local repository.
     */
    suspend fun pullStaffFromSheets(
        authHeader: String,
        spreadsheetId: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val response = sheetsApiService.getValues(authHeader, spreadsheetId, "Staff!A2:I1000")
            if (!response.isSuccessful || response.body() == null) {
                return@withContext Result.success(0)
            }

            val values = response.body()?.values ?: emptyList()
            var count = 0
            for (row in values) {
                if (row.size < 4) continue
                val membershipId = row.getOrNull(0)?.toString()?.takeIf { it.isNotBlank() } ?: continue
                val businessId = row.getOrNull(1)?.toString() ?: repository.getBusinessId()
                val userId = row.getOrNull(2)?.toString() ?: java.util.UUID.randomUUID().toString()
                val email = row.getOrNull(3)?.toString() ?: ""
                val displayName = row.getOrNull(4)?.toString() ?: "Staff"
                val role = row.getOrNull(5)?.toString() ?: "salesperson"
                val vanCode = row.getOrNull(6)?.toString()?.takeIf { it.isNotBlank() }
                val status = row.getOrNull(7)?.toString() ?: "ACTIVE"
                val joinedAt = row.getOrNull(8)?.toString() ?: "2026-11-01"

                val member = EmployeeMembership(
                    membershipId = membershipId,
                    businessId = businessId,
                    businessName = repository.getBusinessName(),
                    userId = userId,
                    email = email,
                    displayName = displayName,
                    role = role,
                    assignedVanCode = vanCode,
                    status = status,
                    joinedAt = joinedAt
                )
                repository.upsertStaffLocally(member)
                count++
            }
            Result.success(count)
        } catch (e: Exception) {
            Log.w(TAG, "Pull staff exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Pushes local staff to Google Sheets 'Staff!A2:I'.
     */
    suspend fun pushStaffToSheets(
        authHeader: String,
        spreadsheetId: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val staff = repository.teamMemberships.value
            if (staff.isEmpty()) return@withContext Result.success(0)

            val rows = staff.map { m ->
                listOf(
                    m.membershipId,
                    m.businessId,
                    m.userId,
                    m.email,
                    m.displayName,
                    m.role,
                    m.assignedVanCode ?: "",
                    m.status,
                    m.joinedAt
                )
            }

            val range = "Staff!A2:I${rows.size + 1}"
            val resp = sheetsApiService.updateValues(
                authHeader = authHeader,
                spreadsheetId = spreadsheetId,
                range = range,
                body = GoogleValueRange(range = range, values = rows)
            )
            if (resp.isSuccessful) {
                Result.success(rows.size)
            } else {
                Result.success(0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Push staff exception: ${e.message}")
            Result.failure(e)
        }
    }
}
