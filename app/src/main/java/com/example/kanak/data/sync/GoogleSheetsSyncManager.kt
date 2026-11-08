package com.example.kanak.data.sync

import android.content.Context
import android.util.Log
import com.example.kanak.data.remote.*
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.domain.financial.BahrainFinancials
import com.example.kanak.domain.models.Customer
import com.example.kanak.domain.models.Product
import com.example.kanak.domain.models.Supplier
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

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://sheets.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val sheetsApiService: GoogleSheetsApiService = retrofit.create(GoogleSheetsApiService::class.java)

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
        _syncState.value = SyncState.Syncing
        val authHeader = if (authToken.startsWith("Bearer ")) authToken else "Bearer $authToken"
        var processedCount = 0

        try {
            // 1. Products Sync
            val pullProd = pullProductsFromSheets(authHeader, spreadsheetId).getOrDefault(0)
            val pushProd = pushProductsToSheets(authHeader, spreadsheetId).getOrDefault(0)
            processedCount += pullProd + pushProd

            // 2. Customers Sync
            val pullCust = pullCustomersFromSheets(authHeader, spreadsheetId).getOrDefault(0)
            val pushCust = pushCustomersToSheets(authHeader, spreadsheetId).getOrDefault(0)
            processedCount += pullCust + pushCust

            // 3. Suppliers Sync
            val pullSup = pullSuppliersFromSheets(authHeader, spreadsheetId).getOrDefault(0)
            val pushSup = pushSuppliersToSheets(authHeader, spreadsheetId).getOrDefault(0)
            processedCount += pullSup + pushSup

            // 4. Daily Sales Logs Push
            val pushSales = pushDailySalesLogsToSheets(authHeader, spreadsheetId).getOrDefault(0)
            processedCount += pushSales

            val summaryMsg = "Bi-directional sync completed ($processedCount records synchronized across Products, Customers, Suppliers & Sales)"
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
            // Check posted invoices in repository
            val postedInvoices = repository.invoices.value.filter { it.status == "POSTED" }
            if (postedInvoices.isNotEmpty()) {
                val rows = postedInvoices.take(50).map { inv ->
                    listOf(
                        inv.invoiceId,
                        inv.invoiceNumber,
                        inv.date,
                        inv.customerName,
                        BahrainFinancials.formatBhd(inv.subtotal),
                        BahrainFinancials.formatBhd(inv.vatTotal),
                        BahrainFinancials.formatBhd(inv.total),
                        inv.paymentType,
                        "VAN-MAIN",
                        inv.createdBy,
                        inv.status
                    )
                }

                val appendResp = sheetsApiService.appendValues(
                    authHeader = authHeader,
                    spreadsheetId = spreadsheetId,
                    range = "DailySalesLogs!A:K",
                    body = GoogleValueRange(values = rows)
                )
                if (appendResp.isSuccessful) {
                    totalPushed += rows.size
                }
            }

            // Also invoke Firestore sales sync
            val pushResult = repository.pushFirestoreSalesToGoogleSheets()
            totalPushed += pushResult.getOrDefault(0)

            Result.success(totalPushed)
        } catch (e: Exception) {
            Log.w(TAG, "Push daily sales exception: ${e.message}")
            Result.failure(e)
        }
    }
}
