package com.example.kanak.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.kanak.data.remote.GatewayRequest
import com.example.kanak.data.remote.KanakApiService
import com.example.kanak.domain.financial.BahrainFinancials
import com.example.kanak.domain.models.*
import com.example.kanak.images.BingThumbnailUrlBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Unified Repository for Kanak Van Sales.
 * Manages authoritative business records, inventory integrity locks, and synchronization
 * with Google Sheets via the trusted Google Apps Script Gateway.
 */
class KanakRepository(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutationMutex = Mutex()
    private val prefs = context.getSharedPreferences("kanak_repo_prefs", Context.MODE_PRIVATE)
    private val roomDb by lazy { com.example.kanak.data.local.KanakDatabase.getInstance(context) }
    private val firebaseManager by lazy { com.example.kanak.data.firebase.KanakFirebaseManager(context) }
    private val moshi = com.squareup.moshi.Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    // Retrofit client for the Apps Script Web App Gateway
    private val apiService: KanakApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl("https://script.google.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(KanakApiService::class.java)
    }

    // Reactive StateFlows for All 8 Entities
    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    private val _customers = MutableStateFlow<List<Customer>>(emptyList())
    val customers: StateFlow<List<Customer>> = _customers.asStateFlow()

    private val _suppliers = MutableStateFlow<List<Supplier>>(emptyList())
    val suppliers: StateFlow<List<Supplier>> = _suppliers.asStateFlow()

    private val _purchases = MutableStateFlow<List<Purchase>>(emptyList())
    val purchases: StateFlow<List<Purchase>> = _purchases.asStateFlow()

    private val _invoices = MutableStateFlow<List<Invoice>>(emptyList())
    val invoices: StateFlow<List<Invoice>> = _invoices.asStateFlow()

    private val _stockAdjustments = MutableStateFlow<List<StockAdjustment>>(emptyList())
    val stockAdjustments: StateFlow<List<StockAdjustment>> = _stockAdjustments.asStateFlow()

    // -------------------------------------------------------------
    // MULTI-TENANT, MULTI-VAN & FLEET STATEFLOWS
    // -------------------------------------------------------------
    private val _currentBusiness = MutableStateFlow(loadCurrentBusiness())
    val currentBusiness: StateFlow<Business> = _currentBusiness.asStateFlow()

    private val _vans = MutableStateFlow<List<Van>>(loadVans())
    val vans: StateFlow<List<Van>> = _vans.asStateFlow()

    private val _vanStocks = MutableStateFlow<Map<String, Map<String, BigDecimal>>>(loadVanStocks())
    val vanStocks: StateFlow<Map<String, Map<String, BigDecimal>>> = _vanStocks.asStateFlow()

    private val _teamMemberships = MutableStateFlow<List<EmployeeMembership>>(loadMemberships())
    val teamMemberships: StateFlow<List<EmployeeMembership>> = _teamMemberships.asStateFlow()

    private val _pendingInvitations = MutableStateFlow<List<EmployeeInvitation>>(loadInvitations())
    val pendingInvitations: StateFlow<List<EmployeeInvitation>> = _pendingInvitations.asStateFlow()

    private val _stockTransfers = MutableStateFlow<List<StockTransfer>>(loadStockTransfers())
    val stockTransfers: StateFlow<List<StockTransfer>> = _stockTransfers.asStateFlow()

    private val _auditRecords = MutableStateFlow<List<AuditRecord>>(loadAuditRecords())
    val auditRecords: StateFlow<List<AuditRecord>> = _auditRecords.asStateFlow()

    private val _dashboardSummary = MutableStateFlow(DashboardSummary())
    val dashboardSummary: StateFlow<DashboardSummary> = _dashboardSummary.asStateFlow()

    enum class SyncState(val label: String) {
        ONLINE("Online"),
        OFFLINE("Offline"),
        PENDING("Pending Sync"),
        SYNCING("Syncing"),
        CONFLICT("Conflict")
    }

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _syncState = MutableStateFlow(SyncState.ONLINE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _pendingSyncCount = MutableStateFlow(0)
    val pendingSyncCount: StateFlow<Int> = _pendingSyncCount.asStateFlow()

    private val _conflictCount = MutableStateFlow(0)
    val conflictCount: StateFlow<Int> = _conflictCount.asStateFlow()

    private val _conflictMessage = MutableStateFlow<String?>(null)
    val conflictMessage: StateFlow<String?> = _conflictMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    init {
        loadLocalDatabase()
        // Never delete persisted data based on product names or ID patterns.
        recalculateDashboard()
        setupNetworkMonitoring()
        refreshPendingSyncCount()
    }

    private fun setupNetworkMonitoring() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (cm != null) {
                @Suppress("DEPRECATION")
                val activeNet = cm.activeNetworkInfo
                @Suppress("DEPRECATION")
                _isOnline.value = activeNet != null && activeNet.isConnectedOrConnecting
                updateSyncState()
                val req = android.net.NetworkRequest.Builder().build()
                cm.registerNetworkCallback(req, object : android.net.ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        _isOnline.value = true
                        updateSyncState()
                    }
                    override fun onLost(network: android.net.Network) {
                        _isOnline.value = false
                        updateSyncState()
                    }
                })
            }
        } catch (_: Exception) {}
    }

    fun updateSyncState() {
        if (_conflictCount.value > 0) {
            _syncState.value = SyncState.CONFLICT
        } else if (_isSyncing.value) {
            _syncState.value = SyncState.SYNCING
        } else if (!_isOnline.value) {
            _syncState.value = SyncState.OFFLINE
        } else if (_pendingSyncCount.value > 0) {
            _syncState.value = SyncState.PENDING
        } else {
            _syncState.value = SyncState.ONLINE
        }
    }

    fun refreshPendingSyncCount() {
        scope.launch {
            try {
                val pendingInvs = roomDb.invoiceDao().getInvoicesBySyncStatus(com.example.kanak.data.local.InvoiceEntity.SYNC_STATUS_PENDING)
                val pendingItems = roomDb.syncQueueDao().getPendingItems()
                _pendingSyncCount.value = pendingInvs.size + pendingItems.size
                updateSyncState()
            } catch (_: Exception) {}
        }
    }

    fun getGatewayUrl(): String? {
        return prefs.getString("gateway_url", null)
    }

    fun getGoogleSheetId(): String? {
        val url = getGatewayUrl() ?: return prefs.getString("google_sheet_id", null)
        return if (url.contains("spreadsheetId=")) {
            url.substringAfter("spreadsheetId=").substringBefore("&")
        } else {
            prefs.getString("google_sheet_id", null)
        }
    }

    fun setGatewayUrl(url: String?) {
        prefs.edit().putString("gateway_url", url?.trim()).apply()
    }

    /**
     * Automated Google Sheets Workbook Provisioning Helper (KANAK-SPEC-1.0.0 Section 6).
     * Automatically binds the auto-cloned master company Google Sheet to the Kanak sync gateway.
     */
    fun autoProvisionGoogleWorkbook(companyName: String, spreadsheetId: String, gatewayBaseUrl: String = "https://script.google.com/macros/s/AKfycbxKanakSyncGateway/exec") {
        val autoGatewayUrl = if (gatewayBaseUrl.contains("?")) {
            "$gatewayBaseUrl&spreadsheetId=$spreadsheetId"
        } else {
            "$gatewayBaseUrl?spreadsheetId=$spreadsheetId"
        }
        setGatewayUrl(autoGatewayUrl)
        setBusinessName(companyName)
        android.util.Log.i("KanakSync", "Automated Google Workbook connected for $companyName (Spreadsheet ID: $spreadsheetId)")
    }

    fun getBusinessName(): String {
        val stored = prefs.getString("biz_name", null)
        if (!stored.isNullOrBlank() && !stored.contains("W.L.L.", ignoreCase = true)) {
            return stored.trim()
        }
        if (!stored.isNullOrBlank()) {
            val cleaned = stored.replace(Regex("(?i)\\s+W\\.?L\\.?L\\.?", RegexOption.IGNORE_CASE), "").trim()
            if (cleaned.isNotBlank()) {
                setBusinessName(cleaned)
                return cleaned
            }
        }
        return "KANAK FMCG Wholesale"
    }

    fun setBusinessName(name: String) {
        val cleaned = name.replace(Regex("(?i)\\s+W\\.?L\\.?L\\.?", RegexOption.IGNORE_CASE), "").trim()
        prefs.edit().putString("biz_name", cleaned.ifBlank { "KANAK FMCG Wholesale" }).apply()
    }

    fun getBusinessAddress(): String {
        return prefs.getString("biz_addr", "Building 844, Road 2415, Block 324, Manama, Bahrain")
            ?: "Building 844, Road 2415, Block 324, Manama, Bahrain"
    }

    fun setBusinessAddress(addr: String) {
        prefs.edit().putString("biz_addr", addr.trim()).apply()
    }

    fun getVatNumber(): String {
        return prefs.getString("biz_vat", "BH200098765400") ?: "BH200098765400"
    }

    fun setVatNumber(trn: String) {
        prefs.edit().putString("biz_vat", trn.trim()).apply()
    }

    // -------------------------------------------------------------
    // BAHRAIN VAT PERCENTAGE CONFIGURATION (Admin Configurable)
    // -------------------------------------------------------------
    companion object {
        const val PREF_KEY_VAT_PERCENTAGE = "bahrain_vat_percentage"
        val DEFAULT_VAT_PERCENTAGE: BigDecimal = BigDecimal("10.00")

        @Volatile
        private var INSTANCE: KanakRepository? = null

        fun getInstance(context: Context): KanakRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: KanakRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val _configuredVatPercentage = MutableStateFlow(loadConfiguredVatPercentage())
    val configuredVatPercentage: StateFlow<BigDecimal> = _configuredVatPercentage.asStateFlow()

    private fun loadConfiguredVatPercentage(): BigDecimal {
        val str = prefs.getString(PREF_KEY_VAT_PERCENTAGE, "10.00") ?: "10.00"
        return try {
            BigDecimal(str).setScale(2, java.math.RoundingMode.HALF_UP)
        } catch (e: Exception) {
            DEFAULT_VAT_PERCENTAGE
        }
    }

    fun getConfiguredVatPercentage(): BigDecimal = _configuredVatPercentage.value

    fun getConfiguredVatRate(): BigDecimal = BahrainFinancials.percentageToRate(_configuredVatPercentage.value)

    fun setConfiguredVatPercentage(percentage: BigDecimal) {
        val scaled = percentage.setScale(2, java.math.RoundingMode.HALF_UP)
        prefs.edit().putString(PREF_KEY_VAT_PERCENTAGE, scaled.toPlainString()).apply()
        _configuredVatPercentage.value = scaled
    }

    // -------------------------------------------------------------
    // PRODUCTS CRUD
    // -------------------------------------------------------------

    suspend fun createProduct(
        barcode: String,
        name: String,
        costPrice: BigDecimal,
        sellingPrice: BigDecimal,
        vatRate: BigDecimal,
        photoUrl: String?
    ): Result<Product> = mutationMutex.withLock {
        val trimmedBarcode = barcode.trim()
        val trimmedName = name.trim()

        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Product name cannot be empty"))
        }

        // Check duplicate barcodes if non-empty
        if (trimmedBarcode.isNotBlank()) {
            val duplicate = _products.value.any { it.barcode.equals(trimmedBarcode, ignoreCase = true) }
            if (duplicate) {
                return Result.failure(IllegalArgumentException("A product with barcode '$trimmedBarcode' already exists"))
            }
        }

        if (costPrice < BigDecimal.ZERO || sellingPrice < BigDecimal.ZERO) {
            return Result.failure(IllegalArgumentException("Prices cannot be negative"))
        }

        val calculatedVatAmount = BahrainFinancials.calculateUnitVat(sellingPrice, vatRate)
        val finalPhotoUrl = photoUrl?.trim() ?: BingThumbnailUrlBuilder.buildDefaultProductImageUrl(trimmedBarcode, trimmedName)

        val product = Product(
            productId = UUID.randomUUID().toString(),
            barcode = trimmedBarcode,
            name = trimmedName,
            costPrice = BahrainFinancials.scaleAmount(costPrice),
            sellingPrice = BahrainFinancials.scaleAmount(sellingPrice),
            vatRate = vatRate,
            vatAmount = calculatedVatAmount,
            photoUrl = finalPhotoUrl,
            stockQty = BigDecimal.ZERO // New products start with 0 stock per Section 5.1
        )

        _products.value = listOf(product) + _products.value
        try {
            roomDb.productDao().insertOrUpdate(com.example.kanak.data.local.ProductEntity.fromDomain(product))
        } catch (_: Exception) {}
        saveLocalDatabase()
        syncToGatewayIfConfigured("create_product", mapOf(
            "product_id" to product.productId,
            "barcode" to product.barcode,
            "name" to product.name,
            "cost_price" to product.costPrice.toPlainString(),
            "selling_price" to product.sellingPrice.toPlainString(),
            "vat_rate" to product.vatRate.toPlainString(),
            "vat_amount" to product.vatAmount.toPlainString(),
            "photo_url" to (product.photoUrl ?: ""),
            "stock_qty" to product.stockQty.toPlainString()
        ))

        return Result.success(product)
    }

    suspend fun upsertProductLocally(product: Product) {
        val current = _products.value.toMutableList()
        val idx = current.indexOfFirst { it.productId == product.productId }
        if (idx >= 0) {
            current[idx] = product
        } else {
            current.add(0, product)
        }
        _products.value = current
        try {
            roomDb.productDao().insertOrUpdate(com.example.kanak.data.local.ProductEntity.fromDomain(product))
        } catch (_: Exception) {}
        saveLocalDatabase()
    }

    suspend fun upsertCustomerLocally(customer: Customer) {
        val current = _customers.value.toMutableList()
        val idx = current.indexOfFirst { it.customerId == customer.customerId }
        if (idx >= 0) {
            current[idx] = customer
        } else {
            current.add(0, customer)
        }
        _customers.value = current
        try {
            roomDb.customerDao().insertOrUpdate(com.example.kanak.data.local.CustomerEntity.fromDomain(customer))
        } catch (_: Exception) {}
        saveLocalDatabase()
    }

    suspend fun upsertSupplierLocally(supplier: Supplier) {
        val current = _suppliers.value.toMutableList()
        val idx = current.indexOfFirst { it.supplierId == supplier.supplierId }
        if (idx >= 0) {
            current[idx] = supplier
        } else {
            current.add(0, supplier)
        }
        _suppliers.value = current
        try {
            roomDb.supplierDao().insertOrUpdate(com.example.kanak.data.local.SupplierEntity.fromDomain(supplier))
        } catch (_: Exception) {}
        saveLocalDatabase()
    }

    suspend fun updateProduct(
        productId: String,
        barcode: String,
        name: String,
        costPrice: BigDecimal,
        sellingPrice: BigDecimal,
        vatRate: BigDecimal,
        photoUrl: String?
    ): Result<Product> = mutationMutex.withLock {
        val existing = _products.value.find { it.productId == productId }
            ?: return Result.failure(NoSuchElementException("Product not found"))

        val trimmedBarcode = barcode.trim()
        val trimmedName = name.trim()

        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Product name cannot be empty"))
        }

        if (trimmedBarcode.isNotBlank()) {
            val duplicate = _products.value.any { it.productId != productId && it.barcode.equals(trimmedBarcode, ignoreCase = true) }
            if (duplicate) {
                return Result.failure(IllegalArgumentException("Another product has barcode '$trimmedBarcode'"))
            }
        }

        val calculatedVatAmount = BahrainFinancials.calculateUnitVat(sellingPrice, vatRate)

        // Image precedence per Section 10.7:
        // If the existing photo was the auto-generated URL from old barcode+name, and barcode/name changed,
        // we can update it; otherwise preserve the manual override.
        val finalPhoto = if (photoUrl != null) {
            photoUrl.trim()
        } else {
            val oldGenerated = BingThumbnailUrlBuilder.buildDefaultProductImageUrl(existing.barcode, existing.name)
            if (existing.photoUrl == oldGenerated) {
                BingThumbnailUrlBuilder.buildDefaultProductImageUrl(trimmedBarcode, trimmedName)
            } else {
                existing.photoUrl
            }
        }

        val updated = existing.copy(
            barcode = trimmedBarcode,
            name = trimmedName,
            costPrice = BahrainFinancials.scaleAmount(costPrice),
            sellingPrice = BahrainFinancials.scaleAmount(sellingPrice),
            vatRate = vatRate,
            vatAmount = calculatedVatAmount,
            photoUrl = finalPhoto
            // Note: stockQty remains completely unchanged on product edit per Section 5.1!
        )

        _products.value = _products.value.map { if (it.productId == productId) updated else it }
        try {
            withContext(Dispatchers.IO) {
                roomDb.productDao().insertOrUpdate(com.example.kanak.data.local.ProductEntity.fromDomain(updated))
            }
        } catch (_: Exception) {}
        saveLocalDatabase()
        syncToGatewayIfConfigured("update_product", mapOf(
            "product_id" to updated.productId,
            "barcode" to updated.barcode,
            "name" to updated.name,
            "cost_price" to updated.costPrice.toPlainString(),
            "selling_price" to updated.sellingPrice.toPlainString(),
            "vat_rate" to updated.vatRate.toPlainString(),
            "vat_amount" to updated.vatAmount.toPlainString(),
            "photo_url" to (updated.photoUrl ?: "")
        ))

        return Result.success(updated)
    }

    suspend fun deleteProduct(productId: String): Result<Unit> = mutationMutex.withLock {
        // Safe check: verify product is not referenced in posted invoices, purchases, or adjustments
        val isReferencedInInvoices = _invoices.value.any { inv ->
            inv.lines.any { it.productId == productId }
        }
        if (isReferencedInInvoices) {
            return Result.failure(IllegalStateException("Cannot delete product: Historical invoices reference this SKU."))
        }

        val isReferencedInPurchases = _purchases.value.any { it.productId == productId }
        if (isReferencedInPurchases) {
            return Result.failure(IllegalStateException("Cannot delete product: Historical purchases reference this SKU."))
        }

        val isReferencedInAdjustments = _stockAdjustments.value.any { it.productId == productId }
        if (isReferencedInAdjustments) {
            return Result.failure(IllegalStateException("Cannot delete product: Inventory adjustments reference this SKU."))
        }

        _products.value = _products.value.filter { it.productId != productId }
        try {
            withContext(Dispatchers.IO) {
                roomDb.productDao().deleteById(productId)
            }
        } catch (_: Exception) {}
        saveLocalDatabase()
        syncToGatewayIfConfigured("delete_product", mapOf("product_id" to productId))
        return Result.success(Unit)
    }

    // -------------------------------------------------------------
    // CUSTOMERS CRUD
    // -------------------------------------------------------------

    suspend fun createCustomer(
        businessName: String,
        contact: String = "",
        address: String = "",
        phone: String = ""
    ): Result<Customer> = mutationMutex.withLock {
        val trimmedName = businessName.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Customer business name cannot be empty"))
        }

        val customer = Customer(
            customerId = UUID.randomUUID().toString(),
            businessName = trimmedName,
            contact = contact.trim(),
            address = address.trim(),
            phone = phone.trim()
        )

        _customers.value = listOf(customer) + _customers.value
        try {
            roomDb.customerDao().insertOrUpdate(com.example.kanak.data.local.CustomerEntity.fromDomain(customer))
        } catch (_: Exception) {}
        saveLocalDatabase()
        syncToGatewayIfConfigured("create_customer", mapOf(
            "customer_id" to customer.customerId,
            "business_name" to customer.businessName,
            "contact" to customer.contact,
            "address" to customer.address,
            "phone" to customer.phone
        ))

        return Result.success(customer)
    }

    suspend fun updateCustomer(customer: Customer): Result<Customer> = mutationMutex.withLock {
        val trimmedName = customer.businessName.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Business name cannot be empty"))
        }

        val updated = customer.copy(
            businessName = trimmedName,
            contact = customer.contact.trim(),
            address = customer.address.trim(),
            phone = customer.phone.trim()
        )

        _customers.value = _customers.value.map { if (it.customerId == customer.customerId) updated else it }
        try {
            withContext(Dispatchers.IO) {
                roomDb.customerDao().insertOrUpdate(com.example.kanak.data.local.CustomerEntity.fromDomain(updated))
            }
        } catch (_: Exception) {}
        saveLocalDatabase()
        syncToGatewayIfConfigured("update_customer", mapOf(
            "customer_id" to updated.customerId,
            "business_name" to updated.businessName,
            "contact" to updated.contact,
            "address" to updated.address,
            "phone" to updated.phone
        ))

        return Result.success(updated)
    }

    suspend fun deleteCustomer(customerId: String): Result<Unit> = mutationMutex.withLock {
        val hasInvoices = _invoices.value.any { it.customerId == customerId }
        if (hasInvoices) {
            return Result.failure(IllegalStateException("Cannot delete customer: Invoices exist for this customer."))
        }

        _customers.value = _customers.value.filter { it.customerId != customerId }
        try {
            withContext(Dispatchers.IO) {
                roomDb.customerDao().deleteById(customerId)
            }
        } catch (_: Exception) {}
        saveLocalDatabase()
        syncToGatewayIfConfigured("delete_customer", mapOf("customer_id" to customerId))
        return Result.success(Unit)
    }

    // -------------------------------------------------------------
    // SUPPLIERS CRUD
    // -------------------------------------------------------------

    suspend fun createSupplier(name: String, contact: String = ""): Result<Supplier> = mutationMutex.withLock {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Supplier name cannot be empty"))
        }

        val supplier = Supplier(
            supplierId = UUID.randomUUID().toString(),
            name = trimmedName,
            contact = contact.trim()
        )

        _suppliers.value = listOf(supplier) + _suppliers.value
        try {
            withContext(Dispatchers.IO) {
                roomDb.supplierDao().insertOrUpdate(com.example.kanak.data.local.SupplierEntity.fromDomain(supplier))
            }
        } catch (_: Exception) {}
        saveLocalDatabase()
        syncToGatewayIfConfigured("create_supplier", mapOf(
            "supplier_id" to supplier.supplierId,
            "name" to supplier.name,
            "contact" to supplier.contact
        ))

        return Result.success(supplier)
    }

    suspend fun updateSupplier(supplier: Supplier): Result<Supplier> = mutationMutex.withLock {
        val trimmedName = supplier.name.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Supplier name cannot be empty"))
        }

        val updated = supplier.copy(name = trimmedName, contact = supplier.contact.trim())
        _suppliers.value = _suppliers.value.map { if (it.supplierId == supplier.supplierId) updated else it }
        try {
            withContext(Dispatchers.IO) {
                roomDb.supplierDao().insertOrUpdate(com.example.kanak.data.local.SupplierEntity.fromDomain(updated))
            }
        } catch (_: Exception) {}
        saveLocalDatabase()
        syncToGatewayIfConfigured("update_supplier", mapOf(
            "supplier_id" to updated.supplierId,
            "name" to updated.name,
            "contact" to updated.contact
        ))

        return Result.success(updated)
    }

    suspend fun deleteSupplier(supplierId: String): Result<Unit> = mutationMutex.withLock {
        val hasPurchases = _purchases.value.any { it.supplierId == supplierId }
        if (hasPurchases) {
            return Result.failure(IllegalStateException("Cannot delete supplier: Purchases exist for this supplier."))
        }

        _suppliers.value = _suppliers.value.filter { it.supplierId != supplierId }
        try {
            withContext(Dispatchers.IO) {
                roomDb.supplierDao().deleteById(supplierId)
            }
        } catch (_: Exception) {}
        saveLocalDatabase()
        syncToGatewayIfConfigured("delete_supplier", mapOf("supplier_id" to supplierId))
        return Result.success(Unit)
    }

    // -------------------------------------------------------------
    // PURCHASES (ADMIN INVENTORY INCREASE)
    // -------------------------------------------------------------

    suspend fun createPurchase(
        supplierId: String,
        productId: String,
        qty: BigDecimal,
        cost: BigDecimal,
        createdBy: String
    ): Result<Purchase> = mutationMutex.withLock {
        val supplier = _suppliers.value.find { it.supplierId == supplierId }
            ?: return Result.failure(IllegalArgumentException("Invalid supplier"))
        val product = _products.value.find { it.productId == productId }
            ?: return Result.failure(IllegalArgumentException("Invalid product"))

        if (qty <= BigDecimal.ZERO) {
            return Result.failure(IllegalArgumentException("Purchase quantity must be positive"))
        }
        if (cost < BigDecimal.ZERO) {
            return Result.failure(IllegalArgumentException("Unit cost cannot be negative"))
        }

        val nowUtc = getCurrentTimestampUtc()
        val purchase = Purchase(
            purchaseId = UUID.randomUUID().toString(),
            supplierId = supplierId,
            productId = productId,
            qty = BahrainFinancials.scaleAmount(qty),
            cost = BahrainFinancials.scaleAmount(cost),
            date = nowUtc,
            createdBy = createdBy,
            supplierName = supplier.name,
            productName = product.name
        )

        // Atomically increase product stock
        val newStock = product.stockQty.add(purchase.qty)
        val updatedProduct = product.copy(stockQty = newStock)

        _purchases.value = listOf(purchase) + _purchases.value
        _products.value = _products.value.map { if (it.productId == productId) updatedProduct else it }
        try {
            withContext(Dispatchers.IO) {
                roomDb.purchaseDao().insertOrUpdate(com.example.kanak.data.local.PurchaseEntity.fromDomain(purchase))
                roomDb.productDao().updateStock(productId, updatedProduct.stockQty)
            }
        } catch (_: Exception) {}
        saveLocalDatabase()

        syncToGatewayIfConfigured("create_purchase", mapOf(
            "purchase_id" to purchase.purchaseId,
            "supplier_id" to purchase.supplierId,
            "product_id" to purchase.productId,
            "qty" to purchase.qty.toPlainString(),
            "cost" to purchase.cost.toPlainString(),
            "date" to purchase.date,
            "created_by" to purchase.createdBy
        ))

        return Result.success(purchase)
    }

    suspend fun correctPurchase(
        purchaseId: String,
        newQty: BigDecimal,
        newCost: BigDecimal,
        adminUserId: String
    ): Result<Purchase> = mutationMutex.withLock {
        val purchase = _purchases.value.find { it.purchaseId == purchaseId }
            ?: return Result.failure(NoSuchElementException("Purchase not found"))
        if (purchase.isReversed) {
            return Result.failure(IllegalStateException("Cannot correct an already reversed purchase"))
        }
        val product = _products.value.find { it.productId == purchase.productId }
            ?: return Result.failure(NoSuchElementException("Product not found"))

        if (newQty <= BigDecimal.ZERO) {
            return Result.failure(IllegalArgumentException("Purchase quantity must be positive"))
        }
        if (newCost < BigDecimal.ZERO) {
            return Result.failure(IllegalArgumentException("Unit cost cannot be negative"))
        }

        val delta = newQty.subtract(purchase.qty)
        val resultingStock = product.stockQty.add(delta)
        if (resultingStock < BigDecimal.ZERO) {
            return Result.failure(IllegalStateException("Purchase correction rejected: Would cause negative stock ($resultingStock)"))
        }

        val updatedPurchase = purchase.copy(
            qty = BahrainFinancials.scaleAmount(newQty),
            cost = BahrainFinancials.scaleAmount(newCost)
        )
        val updatedProduct = product.copy(stockQty = resultingStock)

        _purchases.value = _purchases.value.map { if (it.purchaseId == purchaseId) updatedPurchase else it }
        _products.value = _products.value.map { if (it.productId == product.productId) updatedProduct else it }
        try {
            withContext(Dispatchers.IO) {
                roomDb.purchaseDao().insertOrUpdate(com.example.kanak.data.local.PurchaseEntity.fromDomain(updatedPurchase))
                roomDb.productDao().updateStock(product.productId, updatedProduct.stockQty)
            }
        } catch (_: Exception) {}
        saveLocalDatabase()

        syncToGatewayIfConfigured("correct_purchase", mapOf(
            "purchase_id" to purchaseId,
            "new_qty" to updatedPurchase.qty.toPlainString(),
            "new_cost" to updatedPurchase.cost.toPlainString(),
            "admin_user" to adminUserId
        ))

        return Result.success(updatedPurchase)
    }

    suspend fun reversePurchase(
        purchaseId: String,
        adminUserId: String
    ): Result<Unit> = mutationMutex.withLock {
        val purchase = _purchases.value.find { it.purchaseId == purchaseId }
            ?: return Result.failure(NoSuchElementException("Purchase not found"))
        if (purchase.isReversed) {
            return Result.failure(IllegalStateException("Purchase has already been reversed"))
        }
        val product = _products.value.find { it.productId == purchase.productId }
            ?: return Result.failure(NoSuchElementException("Product not found"))

        val resultingStock = product.stockQty.subtract(purchase.qty)
        if (resultingStock < BigDecimal.ZERO) {
            return Result.failure(IllegalStateException("Purchase reversal rejected: Would cause negative stock ($resultingStock)"))
        }

        val updatedProduct = product.copy(stockQty = resultingStock)
        val reversedPurchase = purchase.copy(isReversed = true)

        // Compensating adjustment preserving transaction history and auditability per Section 7 Directive
        val compensatingAdjustment = StockAdjustment(
            adjustmentId = UUID.randomUUID().toString(),
            productId = purchase.productId,
            deltaQty = purchase.qty.negate(),
            reason = "Reversal of Purchase $purchaseId: ${purchase.productName.ifBlank { product.name }}",
            date = getCurrentTimestampUtc(),
            createdBy = adminUserId,
            productName = purchase.productName.ifBlank { product.name }
        )

        _purchases.value = _purchases.value.map { if (it.purchaseId == purchaseId) reversedPurchase else it }
        _stockAdjustments.value = listOf(compensatingAdjustment) + _stockAdjustments.value
        _products.value = _products.value.map { if (it.productId == product.productId) updatedProduct else it }
        try {
            withContext(Dispatchers.IO) {
                roomDb.purchaseDao().insertOrUpdate(com.example.kanak.data.local.PurchaseEntity.fromDomain(reversedPurchase))
                roomDb.stockAdjustmentDao().insert(com.example.kanak.data.local.StockAdjustmentEntity.fromDomain(compensatingAdjustment))
                roomDb.productDao().updateStock(product.productId, updatedProduct.stockQty)
            }
        } catch (_: Exception) {}
        saveLocalDatabase()

        syncToGatewayIfConfigured("adjust_stock", mapOf(
            "adjustment_id" to compensatingAdjustment.adjustmentId,
            "product_id" to compensatingAdjustment.productId,
            "delta_qty" to compensatingAdjustment.deltaQty.toPlainString(),
            "reason" to compensatingAdjustment.reason,
            "date" to compensatingAdjustment.date,
            "created_by" to adminUserId
        ))

        syncToGatewayIfConfigured("reverse_purchase", mapOf(
            "purchase_id" to purchaseId,
            "admin_user" to adminUserId
        ))

        return Result.success(Unit)
    }

    // -------------------------------------------------------------
    // INVOICES (CORE VAN SALES WORKFLOW)
    // -------------------------------------------------------------

    data class ProposedLine(
        val productId: String,
        val qty: BigDecimal
    )

    /**
     * Creates and posts a new Van Sales Invoice atomically:
     * - Validates customer & products
     * - Re-checks stock availability
     * - Authoritative Bahrain VAT calculation
     * - Atomically decreases stock
     * - Generates unique sequential invoice number
     */
    suspend fun createInvoice(
        customerId: String,
        proposedLines: List<ProposedLine>,
        createdBy: String,
        preAssignedInvoiceId: String? = null,
        paymentType: String = Invoice.PAYMENT_CASH,
        vanId: String = "",
        vanCode: String = ""
    ): Result<Invoice> = mutationMutex.withLock {
        val customer = _customers.value.find { it.customerId == customerId }
            ?: return Result.failure(IllegalArgumentException("Invalid customer"))

        if (proposedLines.isEmpty()) {
            return Result.failure(IllegalArgumentException("Invoice must contain at least one item"))
        }

        // Idempotency check: if preAssignedInvoiceId is already committed, return existing
        val invoiceId = preAssignedInvoiceId ?: UUID.randomUUID().toString()
        val existingInvoice = _invoices.value.find { it.invoiceId == invoiceId }
        if (existingInvoice != null) {
            return Result.success(existingInvoice)
        }

        // Group quantities by product and validate stock
        val lineCalcs = mutableListOf<BahrainFinancials.LineCalculation>()
        val updatedProducts = _products.value.toMutableList()
        val invoiceLines = mutableListOf<InvoiceLine>()

        // Check van stock if van is specified
        if (vanId.isNotBlank()) {
            // An unallocated van has zero stock, not unlimited stock.
            val vanStockMap = _vanStocks.value[vanId] ?: emptyMap()
            val requiredByProduct = proposedLines.groupBy { it.productId }
                .mapValues { (_, lines) -> lines.fold(BigDecimal.ZERO) { sum, line -> sum + line.qty } }
            for ((productId, requiredQty) in requiredByProduct) {
                val availableInVan = vanStockMap[productId] ?: BigDecimal.ZERO
                if (requiredQty > availableInVan) {
                    val productName = _products.value.find { it.productId == productId }?.name ?: productId
                    return Result.failure(IllegalStateException(
                        "Insufficient van stock for '$productName' in $vanCode. Available: $availableInVan, requested: $requiredQty"
                    ))
                }
            }
        }

        for (item in proposedLines) {
            if (item.qty <= BigDecimal.ZERO) {
                return Result.failure(IllegalArgumentException("Quantity must be greater than zero"))
            }

            val prodIndex = updatedProducts.indexOfFirst { it.productId == item.productId }
            if (prodIndex == -1) {
                return Result.failure(IllegalArgumentException("Product not found: ${item.productId}"))
            }
            val prod = updatedProducts[prodIndex]

            if (prod.stockQty < item.qty) {
                return Result.failure(IllegalStateException("Insufficient stock for '${prod.name}'. Available: ${prod.stockQty}, requested: ${item.qty}"))
            }

            // Deduct stock
            val newStock = prod.stockQty.subtract(item.qty)
            updatedProducts[prodIndex] = prod.copy(stockQty = newStock)

            // Authoritative line VAT calculation
            val calc = BahrainFinancials.calculateLine(
                qty = item.qty,
                unitPrice = prod.sellingPrice,
                vatRate = prod.vatRate
            )
            lineCalcs.add(calc)

            invoiceLines.add(
                InvoiceLine(
                    lineId = UUID.randomUUID().toString(),
                    invoiceId = invoiceId,
                    productId = prod.productId,
                    productName = prod.name,
                    qty = calc.qty,
                    unitPrice = calc.unitPrice,
                    vatRate = calc.vatRate,
                    vatAmount = calc.vatAmount,
                    lineTotal = calc.gross
                )
            )
        }

        // Calculate authoritative header totals
        val totals = BahrainFinancials.calculateInvoiceTotals(lineCalcs)
        val invoiceNumber = generateNextInvoiceNumber(vanCode)
        val nowBahrain = getBahrainTimestampString()

        val isCredit = paymentType.equals(Invoice.PAYMENT_CREDIT, ignoreCase = true)
        val paymentStatus = if (isCredit) Invoice.PAYMENT_STATUS_UNPAID else Invoice.PAYMENT_STATUS_PAID
        val amountPaid = if (isCredit) BigDecimal.ZERO else totals.total

        val invoice = Invoice(
            invoiceId = invoiceId,
            invoiceNumber = invoiceNumber,
            customerId = customer.customerId,
            customerName = customer.businessName,
            date = nowBahrain,
            subtotal = totals.subtotal,
            vatTotal = totals.vatTotal,
            total = totals.total,
            status = Invoice.STATUS_POSTED,
            createdBy = createdBy,
            voidedAt = null,
            lines = invoiceLines,
            paymentType = paymentType,
            paymentStatus = paymentStatus,
            amountPaid = amountPaid,
            vanId = vanId,
            vanCode = vanCode,
            businessId = getBusinessId(),
            syncStatus = Invoice.SYNC_STATUS_PENDING,
            idempotencyKey = invoiceId
        )

        // Stage van balance; no business state may change before SQLite commit.
        var committedVanStocks = _vanStocks.value
        if (vanId.isNotBlank()) {
            val currentVanStocks = _vanStocks.value.toMutableMap()
            val vanStockMap = (currentVanStocks[vanId] ?: emptyMap()).toMutableMap()
            for (item in proposedLines) {
                val currentQty = vanStockMap[item.productId] ?: BigDecimal.ZERO
                vanStockMap[item.productId] = currentQty.subtract(item.qty).max(BigDecimal.ZERO)
            }
            currentVanStocks[vanId] = vanStockMap
            committedVanStocks = currentVanStocks
        }

        // Invoice + lines + stock + durable outbox are one Room transaction.
        try {
            withContext(Dispatchers.IO) {
                roomDb.withTransaction {
                roomDb.invoiceDao().insertFullInvoice(
                    com.example.kanak.data.local.InvoiceEntity.fromDomain(invoice, com.example.kanak.data.local.InvoiceEntity.SYNC_STATUS_PENDING),
                    invoice.lines.map { com.example.kanak.data.local.InvoiceLineEntity.fromDomain(it) }
                )
                for (prod in updatedProducts) {
                    roomDb.productDao().updateStock(prod.productId, prod.stockQty)
                }
                // Queue for background sync
                val payloadMap = mapOf(
                    "invoice_id" to invoice.invoiceId,
                    "invoice_number" to invoice.invoiceNumber,
                    "customer_id" to invoice.customerId,
                    "customer_name" to invoice.customerName,
                    "date" to invoice.date,
                    "subtotal" to invoice.subtotal.toPlainString(),
                    "vat_total" to invoice.vatTotal.toPlainString(),
                    "total" to invoice.total.toPlainString(),
                    "status" to invoice.status,
                    "created_by" to invoice.createdBy,
                    "payment_type" to invoice.paymentType,
                    "payment_status" to invoice.paymentStatus,
                    "amount_paid" to invoice.amountPaid.toPlainString(),
                    "lines" to invoice.lines.map { l ->
                        mapOf(
                            "line_id" to l.lineId,
                            "product_id" to l.productId,
                            "product_name" to l.productName,
                            "qty" to l.qty.toPlainString(),
                            "unit_price" to l.unitPrice.toPlainString(),
                            "vat_rate" to l.vatRate.toPlainString(),
                            "vat_amount" to l.vatAmount.toPlainString(),
                            "line_total" to l.lineTotal.toPlainString()
                        )
                    }
                )
                val jsonPayload = moshi.adapter(Map::class.java).toJson(payloadMap)
                roomDb.syncQueueDao().enqueue(
                    com.example.kanak.data.local.SyncQueueEntity(
                        action = "create_invoice",
                        payloadJson = jsonPayload
                    )
                )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("KanakRepository", "Invoice transaction failed", e)
            return Result.failure(IllegalStateException("Invoice not saved; no sale was posted", e))
        }
        _vanStocks.value = committedVanStocks
        _products.value = updatedProducts
        _invoices.value = listOf(invoice) + _invoices.value
        recalculateDashboard()
        refreshPendingSyncCount()

        // One durable outbox owns financial delivery. No duplicate fire-and-forget writes.
        com.example.kanak.data.sync.GoogleSheetsSyncWorker.enqueueImmediateSync(context)
        return Result.success(invoice)
    }

    /**
     * Records payment received against an invoice (e.g. paying off a customer credit bill).
     * Atomically updates amountPaid and paymentStatus (PAID or PARTIALLY_PAID) across Room,
     * Firestore, and Google Sheets gateway.
     */
    suspend fun recordInvoicePayment(
        invoiceId: String,
        amountToPay: BigDecimal,
        recordedBy: String
    ): Result<Invoice> = mutationMutex.withLock {
        val invoice = _invoices.value.find { it.invoiceId == invoiceId }
            ?: return Result.failure(NoSuchElementException("Invoice not found"))

        if (invoice.isVoid) {
            return Result.failure(IllegalStateException("Cannot record payment for a voided invoice"))
        }

        if (amountToPay <= BigDecimal.ZERO) {
            return Result.failure(IllegalArgumentException("Payment amount must be greater than zero"))
        }

        val remainingBalance = invoice.balanceDue
        if (remainingBalance <= BigDecimal.ZERO) {
            return Result.failure(IllegalStateException("Invoice is already fully paid"))
        }

        val scaledPayment = BahrainFinancials.scaleAmount(amountToPay)
        if (scaledPayment > remainingBalance) return Result.failure(IllegalArgumentException("Payment exceeds invoice balance"))
        val newAmountPaid = invoice.amountPaid.add(scaledPayment)
        val newStatus = if (newAmountPaid >= invoice.total) {
            Invoice.PAYMENT_STATUS_PAID
        } else {
            Invoice.PAYMENT_STATUS_PARTIAL
        }

        val updatedInvoice = invoice.copy(
            paymentStatus = newStatus,
            amountPaid = newAmountPaid
        )

        _invoices.value = _invoices.value.map { if (it.invoiceId == invoiceId) updatedInvoice else it }

        try {
            withContext(Dispatchers.IO) {
                roomDb.invoiceDao().updatePayment(invoiceId, newStatus, newAmountPaid)
            }
        } catch (_: Exception) {}
        saveLocalDatabase()

        // Firebase Firestore update
        scope.launch {
            try {
                firebaseManager.backupInvoiceToFirestore(updatedInvoice, recordedBy)
            } catch (_: Exception) {}
        }

        // Google Sheets gateway sync
        syncToGatewayIfConfigured("record_payment", mapOf(
            "invoice_id" to invoiceId,
            "amount_paid" to newAmountPaid.toPlainString(),
            "payment_status" to newStatus,
            "recorded_by" to recordedBy,
            "payment_date" to getBahrainTimestampString()
        ))

        return Result.success(updatedInvoice)
    }

    /**
     * Computes the total outstanding credit balance for a given customer across all active bills.
     */
    fun getCustomerOutstandingCredit(customerId: String): BigDecimal {
        return _invoices.value
            .filter { it.customerId == customerId && !it.isVoid }
            .fold(BigDecimal.ZERO) { acc, inv -> acc.add(inv.balanceDue) }
    }

    /**
     * Voids a posted invoice (Admin-only).
     * Atomically restores product stock quantities exactly once and marks status VOID.
     */
    suspend fun voidInvoice(invoiceId: String, adminUserId: String): Result<Invoice> = mutationMutex.withLock {
        val invoice = _invoices.value.find { it.invoiceId == invoiceId }
            ?: return Result.failure(NoSuchElementException("Invoice not found"))

        if (invoice.isVoid) {
            return Result.failure(IllegalStateException("Invoice is already voided"))
        }

        // Restore inventory effect
        val updatedProducts = _products.value.toMutableList()
        for (line in invoice.lines) {
            val idx = updatedProducts.indexOfFirst { it.productId == line.productId }
            if (idx != -1) {
                val current = updatedProducts[idx]
                updatedProducts[idx] = current.copy(stockQty = current.stockQty.add(line.qty))
            }
        }

        val voided = invoice.copy(
            status = Invoice.STATUS_VOID,
            voidedAt = getCurrentTimestampUtc()
        )

        _products.value = updatedProducts
        _invoices.value = _invoices.value.map { if (it.invoiceId == invoiceId) voided else it }
        try {
            withContext(Dispatchers.IO) {
                roomDb.invoiceDao().updateVoidStatus(invoiceId, Invoice.STATUS_VOID, voided.voidedAt ?: getCurrentTimestampUtc())
                for (restored in updatedProducts) {
                    roomDb.productDao().updateStock(restored.productId, restored.stockQty)
                }
            }
        } catch (_: Exception) {}
        recalculateDashboard()

        syncToGatewayIfConfigured("void_invoice", mapOf(
            "invoice_id" to invoiceId,
            "voided_at" to (voided.voidedAt ?: ""),
            "admin_user" to adminUserId
        ))

        // Update Firebase Firestore status
        scope.launch {
            try {
                firebaseManager.backupInvoiceToFirestore(voided, adminUserId)
            } catch (_: Exception) {
            }
        }

        return Result.success(voided)
    }

    // -------------------------------------------------------------
    // STOCK ADJUSTMENTS (ADMIN ONLY)
    // -------------------------------------------------------------

    suspend fun adjustStock(
        productId: String,
        deltaQty: BigDecimal,
        reason: String,
        createdBy: String
    ): Result<StockAdjustment> = mutationMutex.withLock {
        val product = _products.value.find { it.productId == productId }
            ?: return Result.failure(NoSuchElementException("Product not found"))

        val trimmedReason = reason.trim()
        if (trimmedReason.isBlank()) {
            return Result.failure(IllegalArgumentException("Reason is required for inventory adjustments"))
        }

        val resultingStock = product.stockQty.add(deltaQty)
        if (resultingStock < BigDecimal.ZERO) {
            return Result.failure(IllegalStateException("Stock cannot become negative. Current: ${product.stockQty}, Adjustment: $deltaQty"))
        }

        val adjustment = StockAdjustment(
            adjustmentId = UUID.randomUUID().toString(),
            productId = productId,
            deltaQty = deltaQty,
            reason = trimmedReason,
            date = getCurrentTimestampUtc(),
            createdBy = createdBy,
            productName = product.name
        )

        val updatedProduct = product.copy(stockQty = resultingStock)
        _stockAdjustments.value = listOf(adjustment) + _stockAdjustments.value
        _products.value = _products.value.map { if (it.productId == productId) updatedProduct else it }
        try {
            withContext(Dispatchers.IO) {
                roomDb.stockAdjustmentDao().insert(com.example.kanak.data.local.StockAdjustmentEntity.fromDomain(adjustment))
                roomDb.productDao().updateStock(productId, updatedProduct.stockQty)
            }
        } catch (_: Exception) {}
        saveLocalDatabase()

        syncToGatewayIfConfigured("adjust_stock", mapOf(
            "adjustment_id" to adjustment.adjustmentId,
            "product_id" to adjustment.productId,
            "delta_qty" to adjustment.deltaQty.toPlainString(),
            "reason" to adjustment.reason,
            "date" to adjustment.date,
            "created_by" to adjustment.createdBy
        ))

        return Result.success(adjustment)
    }

    // -------------------------------------------------------------
    // DASHBOARD CALCULATIONS (ASIA/BAHRAIN TIMEZONE)
    // -------------------------------------------------------------

    private fun recalculateDashboard() {
        val bahrainTz = TimeZone.getTimeZone("Asia/Bahrain")
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = bahrainTz }
        val todayStr = df.format(Date())

        val postedToday = _invoices.value.filter { inv ->
            !inv.isVoid && inv.date.startsWith(todayStr)
        }

        var todayTotal = BigDecimal.ZERO
        var todayVat = BigDecimal.ZERO
        val productSalesMap = mutableMapOf<String, Pair<BigDecimal, BigDecimal>>() // productId -> (qty, revenue)

        for (inv in postedToday) {
            todayTotal = todayTotal.add(inv.total)
            todayVat = todayVat.add(inv.vatTotal)
            for (line in inv.lines) {
                val existing = productSalesMap[line.productId] ?: Pair(BigDecimal.ZERO, BigDecimal.ZERO)
                val newQty = existing.first.add(line.qty)
                val newRev = existing.second.add(line.lineTotal)
                productSalesMap[line.productId] = Pair(newQty, newRev)
            }
        }

        val topList = productSalesMap.entries
            .map { (prodId, stats) ->
                val prodName = _products.value.find { it.productId == prodId }?.name ?: "Product $prodId"
                TopProductItem(
                    productId = prodId,
                    productName = prodName,
                    qtySold = stats.first,
                    totalRevenue = stats.second
                )
            }
            .sortedWith(compareByDescending<TopProductItem> { it.qtySold }.thenByDescending { it.totalRevenue })
            .take(5)

        // Fleet Van Performances
        val vanPerfList = _vans.value.map { van ->
            val vanInvoices = postedToday.filter { it.vanId == van.vanId }
            val vanTotal = vanInvoices.fold(BigDecimal.ZERO) { acc, inv -> acc.add(inv.total) }
            VanPerformanceItem(
                vanId = van.vanId,
                vanCode = van.vanCode,
                vanName = van.vanName,
                salespersonName = van.assignedSalespersonName ?: "Unassigned",
                todaySales = BahrainFinancials.scaleAmount(vanTotal),
                todayInvoicesCount = vanInvoices.size
            )
        }

        _dashboardSummary.value = DashboardSummary(
            todaySalesTotal = BahrainFinancials.scaleAmount(todayTotal),
            todayVatTotal = BahrainFinancials.scaleAmount(todayVat),
            todayInvoiceCount = postedToday.size,
            activeVansCount = _vans.value.count { it.status == Van.STATUS_ACTIVE },
            activeSalespeopleCount = _teamMemberships.value.count { it.status == "ACTIVE" && it.role == User.ROLE_SALESPERSON },
            topSellingProducts = topList,
            vanPerformances = vanPerfList
        )
        saveLocalDatabase()
        saveMultiTenantState()
    }

    private fun saveLocalDatabase() {
        // Persist to Room Database for robust local offline caching
        scope.launch(Dispatchers.IO) {
            try {
                val db = roomDb
                db.productDao().insertAll(_products.value.map { com.example.kanak.data.local.ProductEntity.fromDomain(it) })
                db.customerDao().insertAll(_customers.value.map { com.example.kanak.data.local.CustomerEntity.fromDomain(it) })
                db.supplierDao().insertAll(_suppliers.value.map { com.example.kanak.data.local.SupplierEntity.fromDomain(it) })
                db.purchaseDao().insertAll(_purchases.value.map { com.example.kanak.data.local.PurchaseEntity.fromDomain(it) })
                // Invoices are inserted only by the transactional checkout flow.
                // Blind REPLACE here used to reset SYNCED invoices to PENDING.
                db.stockAdjustmentDao().insertAll(_stockAdjustments.value.map { com.example.kanak.data.local.StockAdjustmentEntity.fromDomain(it) })
            } catch (e: Exception) {
                android.util.Log.w("KanakRepository", "Room save error: ${e.message}")
            }
        }

        try {
            val map = mapOf(
                "products" to _products.value.map { p ->
                    mapOf(
                        "product_id" to p.productId,
                        "barcode" to p.barcode,
                        "name" to p.name,
                        "cost_price" to p.costPrice.toPlainString(),
                        "selling_price" to p.sellingPrice.toPlainString(),
                        "vat_rate" to p.vatRate.toPlainString(),
                        "vat_amount" to p.vatAmount.toPlainString(),
                        "photo_url" to (p.photoUrl ?: ""),
                        "stock_qty" to p.stockQty.toPlainString()
                    )
                },
                "customers" to _customers.value.map { c ->
                    mapOf(
                        "customer_id" to c.customerId,
                        "business_name" to c.businessName,
                        "contact" to c.contact,
                        "address" to c.address,
                        "phone" to c.phone
                    )
                },
                "suppliers" to _suppliers.value.map { s ->
                    mapOf(
                        "supplier_id" to s.supplierId,
                        "name" to s.name,
                        "contact" to s.contact
                    )
                },
                "purchases" to _purchases.value.map { pu ->
                    mapOf(
                        "purchase_id" to pu.purchaseId,
                        "supplier_id" to pu.supplierId,
                        "product_id" to pu.productId,
                        "qty" to pu.qty.toPlainString(),
                        "cost" to pu.cost.toPlainString(),
                        "date" to pu.date,
                        "created_by" to pu.createdBy
                    )
                },
                "invoices" to _invoices.value.map { inv ->
                    mapOf(
                        "invoice_id" to inv.invoiceId,
                        "invoice_number" to inv.invoiceNumber,
                        "customer_id" to inv.customerId,
                        "customer_name" to inv.customerName,
                        "date" to inv.date,
                        "subtotal" to inv.subtotal.toPlainString(),
                        "vat_total" to inv.vatTotal.toPlainString(),
                        "total" to inv.total.toPlainString(),
                        "status" to inv.status,
                        "created_by" to inv.createdBy,
                        "voided_at" to (inv.voidedAt ?: ""),
                        "payment_type" to inv.paymentType,
                        "payment_status" to inv.paymentStatus,
                        "amount_paid" to inv.amountPaid.toPlainString(),
                        "lines" to inv.lines.map { l ->
                            mapOf(
                                "line_id" to l.lineId,
                                "invoice_id" to l.invoiceId,
                                "product_id" to l.productId,
                                "product_name" to l.productName,
                                "qty" to l.qty.toPlainString(),
                                "unit_price" to l.unitPrice.toPlainString(),
                                "vat_rate" to l.vatRate.toPlainString(),
                                "vat_amount" to l.vatAmount.toPlainString(),
                                "line_total" to l.lineTotal.toPlainString()
                            )
                        }
                    )
                },
                "stock_adjustments" to _stockAdjustments.value.map { sa ->
                    mapOf(
                        "adjustment_id" to sa.adjustmentId,
                        "product_id" to sa.productId,
                        "delta_qty" to sa.deltaQty.toPlainString(),
                        "reason" to sa.reason,
                        "date" to sa.date,
                        "created_by" to sa.createdBy
                    )
                }
            )
            val json = moshi.adapter(Map::class.java).toJson(map)
            prefs.edit().putString("local_database_json", json).apply()
        } catch (_: Exception) {
        }
    }

    private fun loadLocalDatabase(): Boolean {
        // 1. First attempt to load from Room database
        try {
            val db = roomDb
            val (pList, cList, sList, puList, invList, saList) = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                val p = db.productDao().getAllProducts()
                val c = db.customerDao().getAllCustomers()
                val s = db.supplierDao().getAllSuppliers()
                val pu = db.purchaseDao().getAllPurchases()
                val inv = db.invoiceDao().getAllInvoicesWithLines()
                val sa = db.stockAdjustmentDao().getAllAdjustments()
                Tuple6(p, c, s, pu, inv, sa)
            }

            if (pList.isNotEmpty() || cList.isNotEmpty() || invList.isNotEmpty()) {
                _products.value = pList.map { it.toDomain() }
                _customers.value = cList.map { it.toDomain() }
                _suppliers.value = sList.map { it.toDomain() }
                _purchases.value = puList.map { it.toDomain() }
                _invoices.value = invList.map { it.toDomain() }
                _stockAdjustments.value = saList.map { it.toDomain() }
                return true
            }
        } catch (_: Exception) {}

        // 2. Fallback to existing JSON if present (and migrate to Room)
        val json = prefs.getString("local_database_json", null) ?: return false
        return try {
            @Suppress("UNCHECKED_CAST")
            val data = moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any?> ?: return false

            val prods = (data["products"] as? List<Map<String, Any?>>)?.mapNotNull { map ->
                try {
                    Product(
                        productId = map["product_id"]?.toString() ?: UUID.randomUUID().toString(),
                        barcode = map["barcode"]?.toString() ?: "",
                        name = map["name"]?.toString() ?: "Product",
                        costPrice = BahrainFinancials.parseBhd(map["cost_price"]?.toString() ?: "0"),
                        sellingPrice = BahrainFinancials.parseBhd(map["selling_price"]?.toString() ?: "0"),
                        vatRate = BahrainFinancials.parseBhd(map["vat_rate"]?.toString() ?: "0.10"),
                        vatAmount = BahrainFinancials.parseBhd(map["vat_amount"]?.toString() ?: "0"),
                        photoUrl = map["photo_url"]?.toString()?.takeIf { it.isNotBlank() },
                        stockQty = BahrainFinancials.parseBhd(map["stock_qty"]?.toString() ?: "0")
                    )
                } catch (e: Exception) { null }
            }

            val custs = (data["customers"] as? List<Map<String, Any?>>)?.mapNotNull { map ->
                try {
                    Customer(
                        customerId = map["customer_id"]?.toString() ?: UUID.randomUUID().toString(),
                        businessName = map["business_name"]?.toString() ?: "Store",
                        contact = map["contact"]?.toString() ?: "",
                        address = map["address"]?.toString() ?: "",
                        phone = map["phone"]?.toString() ?: ""
                    )
                } catch (e: Exception) { null }
            }

            val sups = (data["suppliers"] as? List<Map<String, Any?>>)?.mapNotNull { map ->
                try {
                    Supplier(
                        supplierId = map["supplier_id"]?.toString() ?: UUID.randomUUID().toString(),
                        name = map["name"]?.toString() ?: "Supplier",
                        contact = map["contact"]?.toString() ?: ""
                    )
                } catch (e: Exception) { null }
            }

            val invs = (data["invoices"] as? List<Map<String, Any?>>)?.mapNotNull { map ->
                try {
                    val invId = map["invoice_id"]?.toString() ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val rawLines = (map["lines"] as? List<Map<String, Any?>>) ?: emptyList()
                    val lines = rawLines.map { l ->
                        InvoiceLine(
                            lineId = l["line_id"]?.toString() ?: UUID.randomUUID().toString(),
                            invoiceId = invId,
                            productId = l["product_id"]?.toString() ?: "",
                            productName = l["product_name"]?.toString() ?: "",
                            qty = BahrainFinancials.parseBhd(l["qty"]?.toString() ?: "0"),
                            unitPrice = BahrainFinancials.parseBhd(l["unit_price"]?.toString() ?: "0"),
                            vatRate = BahrainFinancials.parseBhd(l["vat_rate"]?.toString() ?: "0.10"),
                            vatAmount = BahrainFinancials.parseBhd(l["vat_amount"]?.toString() ?: "0"),
                            lineTotal = BahrainFinancials.parseBhd(l["line_total"]?.toString() ?: "0")
                        )
                    }
                    val totalBhd = BahrainFinancials.parseBhd(map["total"]?.toString() ?: "0")
                    val pType = map["payment_type"]?.toString() ?: Invoice.PAYMENT_CASH
                    val pStatus = map["payment_status"]?.toString() ?: if (pType == Invoice.PAYMENT_CREDIT) Invoice.PAYMENT_STATUS_UNPAID else Invoice.PAYMENT_STATUS_PAID
                    val amtPaid = if (map.containsKey("amount_paid")) {
                        BahrainFinancials.parseBhd(map["amount_paid"]?.toString() ?: "0")
                    } else {
                        if (pStatus == Invoice.PAYMENT_STATUS_PAID) totalBhd else BigDecimal.ZERO
                    }

                        Invoice(
                            invoiceId = invId,
                            invoiceNumber = map["invoice_number"]?.toString() ?: "INV",
                            customerId = map["customer_id"]?.toString() ?: "",
                            customerName = map["customer_name"]?.toString() ?: "",
                            date = map["date"]?.toString() ?: "",
                            subtotal = BahrainFinancials.parseBhd(map["subtotal"]?.toString() ?: "0"),
                            vatTotal = BahrainFinancials.parseBhd(map["vat_total"]?.toString() ?: "0"),
                            total = totalBhd,
                            status = map["status"]?.toString() ?: Invoice.STATUS_POSTED,
                            createdBy = map["created_by"]?.toString() ?: "",
                            voidedAt = map["voided_at"]?.toString()?.takeIf { it.isNotBlank() },
                            lines = lines,
                            paymentType = pType,
                            paymentStatus = pStatus,
                            amountPaid = amtPaid
                        )
                } catch (e: Exception) { null }
            }

            if (!prods.isNullOrEmpty()) _products.value = prods
            if (!custs.isNullOrEmpty()) _customers.value = custs
            if (!sups.isNullOrEmpty()) _suppliers.value = sups
            if (!invs.isNullOrEmpty()) _invoices.value = invs
            true
        } catch (_: Exception) {
            false
        }
    }

    fun generateNextInvoiceNumber(vanCode: String = ""): String {
        val cleanVan = if (vanCode.isNotBlank()) {
            vanCode.replace(" ", "").replace("-", "").uppercase()
        } else {
            "HQ"
        }
        val dateCompact = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Bahrain")
        }.format(Date())
        val key = "invoice_seq_${cleanVan}_$dateCompact"
        val nextSeq = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, nextSeq).apply()
        return String.format(Locale.US, "INV-%s-%s-%04d", cleanVan, dateCompact, nextSeq)
    }

    private fun getCurrentTimestampUtc(): String {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return df.format(Date())
    }

    private fun getBahrainTimestampString(): String {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Bahrain")
        }
        return df.format(Date())
    }

    var currentAuthToken: String? = null

    suspend fun fetchAllFromGateway(): Result<Unit> = mutationMutex.withLock {
        val gatewayUrl = getGatewayUrl()
        if (gatewayUrl.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Gateway URL not configured in Settings"))
        }

        try {
            _isSyncing.value = true
            _lastSyncError.value = null
            val token = requireNotNull(currentAuthToken?.takeIf { it.isNotBlank() }) { "Sign in before using Google Sheets Gateway" }
            val req = GatewayRequest(action = "get_all", idToken = token, payload = emptyMap())
            val resp = apiService.executeGatewayAction(gatewayUrl, req)
            if (!resp.isSuccessful) {
                val err = "Gateway HTTP ${resp.code()}"
                _lastSyncError.value = err
                return Result.failure(Exception(err))
            }

            val body = resp.body()
            if (body == null || !body.success) {
                val err = body?.error ?: "Unknown gateway response error"
                _lastSyncError.value = err
                return Result.failure(Exception(err))
            }

            @Suppress("UNCHECKED_CAST")
            val data = body.data as? Map<String, Any?> ?: emptyMap()

            val remoteProducts = (data["products"] as? List<Map<String, Any?>>)?.mapNotNull { map ->
                try {
                    Product(
                        productId = map["product_id"]?.toString() ?: UUID.randomUUID().toString(),
                        barcode = map["barcode"]?.toString() ?: "",
                        name = map["name"]?.toString() ?: "Unnamed Product",
                        costPrice = BahrainFinancials.parseBhd(map["cost_price"]?.toString() ?: "0"),
                        sellingPrice = BahrainFinancials.parseBhd(map["selling_price"]?.toString() ?: "0"),
                        vatRate = BahrainFinancials.parseBhd(map["vat_rate"]?.toString() ?: "0.10"),
                        vatAmount = BahrainFinancials.parseBhd(map["vat_amount"]?.toString() ?: "0"),
                        photoUrl = map["photo_url"]?.toString()?.takeIf { it.isNotBlank() },
                        stockQty = BahrainFinancials.parseBhd(map["stock_qty"]?.toString() ?: "0")
                    )
                } catch (e: Exception) { null }
            }
            if (!remoteProducts.isNullOrEmpty()) {
                _products.value = remoteProducts
            }

            val remoteCustomers = (data["customers"] as? List<Map<String, Any?>>)?.mapNotNull { map ->
                try {
                    Customer(
                        customerId = map["customer_id"]?.toString() ?: UUID.randomUUID().toString(),
                        businessName = map["business_name"]?.toString() ?: "Unnamed Store",
                        contact = map["contact"]?.toString() ?: "",
                        address = map["address"]?.toString() ?: "",
                        phone = map["phone"]?.toString() ?: ""
                    )
                } catch (e: Exception) { null }
            }
            if (!remoteCustomers.isNullOrEmpty()) {
                _customers.value = remoteCustomers
            }

            val remoteSuppliers = (data["suppliers"] as? List<Map<String, Any?>>)?.mapNotNull { map ->
                try {
                    Supplier(
                        supplierId = map["supplier_id"]?.toString() ?: UUID.randomUUID().toString(),
                        name = map["name"]?.toString() ?: "Supplier",
                        contact = map["contact"]?.toString() ?: ""
                    )
                } catch (e: Exception) { null }
            }
            if (!remoteSuppliers.isNullOrEmpty()) {
                _suppliers.value = remoteSuppliers
            }

            val remotePurchases = (data["purchases"] as? List<Map<String, Any?>>)?.mapNotNull { map ->
                try {
                    val pId = map["product_id"]?.toString() ?: ""
                    val sId = map["supplier_id"]?.toString() ?: ""
                    val pName = _products.value.find { it.productId == pId }?.name ?: ""
                    val sName = _suppliers.value.find { it.supplierId == sId }?.name ?: ""
                    Purchase(
                        purchaseId = map["purchase_id"]?.toString() ?: UUID.randomUUID().toString(),
                        supplierId = sId,
                        productId = pId,
                        qty = BahrainFinancials.parseBhd(map["qty"]?.toString() ?: "0"),
                        cost = BahrainFinancials.parseBhd(map["cost"]?.toString() ?: "0"),
                        date = map["date"]?.toString() ?: "",
                        createdBy = map["created_by"]?.toString() ?: "",
                        supplierName = sName,
                        productName = pName
                    )
                } catch (e: Exception) { null }
            }
            if (remotePurchases != null) {
                _purchases.value = remotePurchases
            }

            val rawLines = (data["invoice_lines"] as? List<Map<String, Any?>>) ?: emptyList()
            val linesByInvoice = rawLines.groupBy { it["invoice_id"]?.toString() ?: "" }

            val remoteInvoices = (data["invoices"] as? List<Map<String, Any?>>)?.mapNotNull { map ->
                try {
                    val invId = map["invoice_id"]?.toString() ?: return@mapNotNull null
                    val associatedLines = (linesByInvoice[invId] ?: emptyList()).map { lMap ->
                        InvoiceLine(
                            lineId = lMap["line_id"]?.toString() ?: UUID.randomUUID().toString(),
                            invoiceId = invId,
                            productId = lMap["product_id"]?.toString() ?: "",
                            productName = lMap["product_name"]?.toString() ?: "",
                            qty = BahrainFinancials.parseBhd(lMap["qty"]?.toString() ?: "0"),
                            unitPrice = BahrainFinancials.parseBhd(lMap["unit_price"]?.toString() ?: "0"),
                            vatRate = BahrainFinancials.parseBhd(lMap["vat_rate"]?.toString() ?: "0.10"),
                            vatAmount = BahrainFinancials.parseBhd(lMap["vat_amount"]?.toString() ?: "0"),
                            lineTotal = BahrainFinancials.parseBhd(lMap["line_total"]?.toString() ?: "0")
                        )
                    }

                    Invoice(
                        invoiceId = invId,
                        invoiceNumber = map["invoice_number"]?.toString() ?: "INV",
                        customerId = map["customer_id"]?.toString() ?: "",
                        customerName = map["customer_name"]?.toString() ?: "",
                        date = map["date"]?.toString() ?: "",
                        subtotal = BahrainFinancials.parseBhd(map["subtotal"]?.toString() ?: "0"),
                        vatTotal = BahrainFinancials.parseBhd(map["vat_total"]?.toString() ?: "0"),
                        total = BahrainFinancials.parseBhd(map["total"]?.toString() ?: "0"),
                        status = map["status"]?.toString() ?: Invoice.STATUS_POSTED,
                        createdBy = map["created_by"]?.toString() ?: "",
                        voidedAt = map["voided_at"]?.toString()?.takeIf { it.isNotBlank() },
                        lines = associatedLines
                    )
                } catch (e: Exception) { null }
            }
            if (remoteInvoices != null) {
                _invoices.value = remoteInvoices
            }

            recalculateDashboard()
            return Result.success(Unit)
        } catch (e: Exception) {
            _lastSyncError.value = e.message
            return Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    private fun syncToGatewayIfConfigured(action: String, payload: Map<String, Any?>) {
        val gatewayUrl = getGatewayUrl() ?: return
        if (gatewayUrl.isBlank()) return

        scope.launch {
            try {
                _isSyncing.value = true
                val token = requireNotNull(currentAuthToken?.takeIf { it.isNotBlank() }) { "Sign in before using Google Sheets Gateway" }
                val req = GatewayRequest(action = action, idToken = token, payload = payload)
                val resp = apiService.executeGatewayAction(gatewayUrl, req)
                if (!resp.isSuccessful || resp.body()?.success != true) {
                    _lastSyncError.value = resp.body()?.error ?: "Gateway HTTP ${resp.code()}"
                }
            } catch (e: Exception) {
                _lastSyncError.value = "Sync error: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /**
     * Background Synchronization: Queries completed sales records (status == POSTED) from Firestore
     * and pushes them to the authoritative Google Sheets document via the Google Apps Script Gateway.
     * Marks synced documents in Firestore upon confirmation.
     */
    suspend fun pushFirestoreSalesToGoogleSheets(): Result<Int> =
        Result.failure(UnsupportedOperationException(
            "Unsafe Firestore-to-Sheets invoice replay disabled; use durable outbox."
        ))

    /**
     * Retrieves all pending sales transactions stored in the local SQLite Room database.
     */
    suspend fun getPendingOfflineInvoicesFromRoom(): List<Invoice> = withContext(Dispatchers.IO) {
        try {
            val pendingEntities = roomDb.invoiceDao().getInvoicesBySyncStatus(com.example.kanak.data.local.InvoiceEntity.SYNC_STATUS_PENDING)
            pendingEntities.mapNotNull { pEntity ->
                roomDb.invoiceDao().getInvoiceWithLinesById(pEntity.invoiceId)?.toDomain()
            }
        } catch (e: Exception) {
            android.util.Log.w("KanakRepository", "Error fetching pending offline invoices from Room: ${e.message}")
            emptyList()
        }
    }

    /**
     * Updates an offline sales transaction's status to SYNCED in the local SQLite Room database.
     */
    suspend fun markInvoiceSyncedInRoom(invoiceId: String) = withContext(Dispatchers.IO) {
        try {
            roomDb.invoiceDao().updateSyncStatus(invoiceId, com.example.kanak.data.local.InvoiceEntity.SYNC_STATUS_SYNCED)
            // Also mark matching item in Room sync queue as completed
            val queueItems = roomDb.syncQueueDao().getPendingItems()
            for (item in queueItems) {
                if (item.payloadJson.contains(invoiceId)) {
                    roomDb.syncQueueDao().deleteById(item.queueId)
                }
            }
            refreshPendingSyncCount()
            updateSyncState()
        } catch (e: Exception) {
            android.util.Log.w("KanakRepository", "Error updating invoice sync status in Room: ${e.message}")
        }
    }

    /**
     * Flushes offline mutations recorded in the Room sync queue to the Google Sheets gateway.
     */
    suspend fun flushPendingRoomSyncQueue(): Result<Int> {
        return try {
            val pending = roomDb.syncQueueDao().getPendingItems()
            if (pending.isEmpty()) return Result.success(0)
            val gatewayUrl = getGatewayUrl()?.takeIf { it.isNotBlank() }
                ?: return Result.failure(IllegalStateException("Transaction gateway is not configured"))
            val token = currentAuthToken?.takeIf { it.isNotBlank() }
                ?: return Result.failure(IllegalStateException("Sign in before syncing queued operations"))
            var sent = 0
            val failures = mutableListOf<String>()
            for (item in pending) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val payload = moshi.adapter(Map::class.java).fromJson(item.payloadJson) as? Map<String, Any?>
                        ?: error("Unreadable queued operation")
                    val response = apiService.executeGatewayAction(
                        gatewayUrl, GatewayRequest(item.action, token, payload)
                    )
                    if (!response.isSuccessful || response.body()?.success != true)
                        error(response.body()?.error ?: "Gateway HTTP ${response.code()}")
                    roomDb.withTransaction {
                        val invoiceId = payload["invoice_id"]?.toString()
                        if (item.action == "create_invoice" && !invoiceId.isNullOrBlank())
                            roomDb.invoiceDao().updateSyncStatus(
                                invoiceId, com.example.kanak.data.local.InvoiceEntity.SYNC_STATUS_SYNCED
                            )
                        roomDb.syncQueueDao().deleteById(item.queueId)
                    }
                    sent++
                } catch (e: Exception) {
                    roomDb.syncQueueDao().markFailed(item.queueId, e.message)
                    failures.add("Queue ${item.queueId}: ${e.message}")
                }
            }
            if (failures.isNotEmpty()) Result.failure(IllegalStateException(failures.joinToString("; ")))
            else Result.success(sent)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Triggers active bi-directional synchronization of all pending Room invoices and queued actions.
     * Updates live connection/sync state (ONLINE, PENDING, SYNCING, CONFLICT).
     */
    suspend fun syncAllPendingNow(): Result<Int> = mutationMutex.withLock {
        if (!_isOnline.value) {
            updateSyncState()
            return Result.failure(IllegalStateException("Offline: transactions remain queued"))
        }
        _isSyncing.value = true
        updateSyncState()
        try {
            // No nested mutex and no unvalidated Firestore replica writes.
            val sent = flushPendingRoomSyncQueue().getOrThrow()
            _lastSyncError.value = null
            refreshPendingSyncCount()
            Result.success(sent)
        } catch (e: Exception) {
            _lastSyncError.value = e.message
            updateSyncState()
            Result.failure(e)
        } finally {
            _isSyncing.value = false
            refreshPendingSyncCount()
            updateSyncState()
        }
    }

    /**
     * Resets the entire local persistent database to empty production state.
     * Removes any legacy mock data and writes pristine empty collections.
     */
    fun clearAllData() {
        _products.value = emptyList()
        _customers.value = emptyList()
        _suppliers.value = emptyList()
        _purchases.value = emptyList()
        _invoices.value = emptyList()
        _stockAdjustments.value = emptyList()
        prefs.edit().remove("local_database_json").apply()
        runBlocking(Dispatchers.IO) {
            try {
                roomDb.clearAllTables()
            } catch (_: Exception) {}
        }
        recalculateDashboard()
    }

    fun resetToEmptyProductionDatabase() {
        clearAllData()
    }

    // -------------------------------------------------------------
    // MULTI-TENANT, MULTI-VAN & FLEET IMPLEMENTATION (SECTION 4, 5, 7, 12, 16)
    // -------------------------------------------------------------

    fun getBusinessId(): String = _currentBusiness.value.businessId

    fun getBahrainDateString(): String {
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Bahrain")
        }
        return df.format(Date())
    }

    private fun loadCurrentBusiness(): Business {
        val json = prefs.getString("current_business_json", null)
        if (json != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                val m = moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any?>
                if (m != null) {
                    return Business(
                        businessId = m["business_id"]?.toString() ?: "biz_001",
                        name = m["name"]?.toString() ?: getBusinessName(),
                        crNumber = m["cr_number"]?.toString() ?: "",
                        vatNumber = m["vat_number"]?.toString() ?: getVatNumber(),
                        ownerEmail = m["owner_email"]?.toString() ?: "zanabalmuhamed@gmail.com",
                        ownerName = m["owner_name"]?.toString() ?: "Zanabal Mohamed",
                        phone = m["phone"]?.toString() ?: "+973 3305 0666",
                        address = m["address"]?.toString() ?: getBusinessAddress(),
                        status = m["status"]?.toString() ?: Business.STATUS_ACTIVE,
                        createdAt = m["created_at"]?.toString() ?: "2026-11-01T00:00:00Z"
                    )
                }
            } catch (_: Exception) {}
        }
        return Business(
            businessId = "biz_001",
            name = getBusinessName(),
            crNumber = "",
            vatNumber = getVatNumber(),
            ownerEmail = "zanabalmuhamed@gmail.com",
            ownerName = "Zanabal Mohamed",
            phone = "+973 3305 0666",
            address = getBusinessAddress(),
            status = Business.STATUS_ACTIVE
        )
    }

    private fun loadVans(): List<Van> {
        val json = prefs.getString("fleet_vans_json", null)
        if (json != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                val list = moshi.adapter(List::class.java).fromJson(json) as? List<Map<String, Any?>>
                if (!list.isNullOrEmpty()) {
                    return list.map { m ->
                        Van(
                            vanId = m["van_id"]?.toString() ?: UUID.randomUUID().toString(),
                            businessId = m["business_id"]?.toString() ?: "biz_001",
                            vanCode = m["van_code"]?.toString() ?: "VAN-001",
                            vanName = m["van_name"]?.toString() ?: "Van",
                            registrationNo = m["reg_no"]?.toString() ?: "",
                            assignedSalespersonId = m["salesperson_id"]?.toString()?.takeIf { it.isNotBlank() },
                            assignedSalespersonName = m["salesperson_name"]?.toString()?.takeIf { it.isNotBlank() },
                            status = m["status"]?.toString() ?: Van.STATUS_ACTIVE,
                            notes = m["notes"]?.toString() ?: ""
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    private fun loadVanStocks(): Map<String, Map<String, BigDecimal>> {
        val json = prefs.getString("van_stocks_json", null)
        if (json != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                val root = moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Map<String, String>>
                if (root != null) {
                    val result = mutableMapOf<String, Map<String, BigDecimal>>()
                    for ((vanId, prodMap) in root) {
                        val subMap = mutableMapOf<String, BigDecimal>()
                        for ((prodId, qtyStr) in prodMap) {
                            subMap[prodId] = BahrainFinancials.parseBhd(qtyStr)
                        }
                        result[vanId] = subMap
                    }
                    return result
                }
            } catch (_: Exception) {}
        }
        return emptyMap()
    }

    private fun loadMemberships(): List<EmployeeMembership> {
        val json = prefs.getString("team_memberships_json", null)
        if (json != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                val list = moshi.adapter(List::class.java).fromJson(json) as? List<Map<String, Any?>>
                if (!list.isNullOrEmpty()) {
                    return list.map { m ->
                        val permsList = (m["permissions"] as? List<*>)?.mapNotNull { it?.toString() }?.toSet()
                            ?: KanakPermissions.DEFAULT_SALESPERSON_PERMISSIONS
                        EmployeeMembership(
                            membershipId = m["membership_id"]?.toString() ?: UUID.randomUUID().toString(),
                            businessId = m["business_id"]?.toString() ?: "biz_001",
                            businessName = m["business_name"]?.toString() ?: getBusinessName(),
                            userId = m["user_id"]?.toString() ?: UUID.randomUUID().toString(),
                            email = m["email"]?.toString() ?: "",
                            displayName = m["display_name"]?.toString() ?: "Employee",
                            role = m["role"]?.toString() ?: User.ROLE_SALESPERSON,
                            assignedVanId = m["assigned_van_id"]?.toString()?.takeIf { it.isNotBlank() },
                            assignedVanCode = m["assigned_van_code"]?.toString()?.takeIf { it.isNotBlank() },
                            permissions = permsList,
                            status = m["status"]?.toString() ?: "ACTIVE",
                            joinedAt = m["joined_at"]?.toString() ?: "2026-11-01"
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    private fun loadInvitations(): List<EmployeeInvitation> {
        val json = prefs.getString("pending_invitations_json", null)
        if (json != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                val list = moshi.adapter(List::class.java).fromJson(json) as? List<Map<String, Any?>>
                if (!list.isNullOrEmpty()) {
                    return list.map { m ->
                        val permsList = (m["permissions"] as? List<*>)?.mapNotNull { it?.toString() }?.toSet()
                            ?: KanakPermissions.DEFAULT_SALESPERSON_PERMISSIONS
                        EmployeeInvitation(
                            invitationId = m["invitation_id"]?.toString() ?: UUID.randomUUID().toString(),
                            businessId = m["business_id"]?.toString() ?: "biz_001",
                            businessName = m["business_name"]?.toString() ?: getBusinessName(),
                            recipientEmail = m["recipient_email"]?.toString() ?: "",
                            role = m["role"]?.toString() ?: User.ROLE_SALESPERSON,
                            assignedVanId = m["assigned_van_id"]?.toString()?.takeIf { it.isNotBlank() },
                            assignedVanCode = m["assigned_van_code"]?.toString()?.takeIf { it.isNotBlank() },
                            permissions = permsList,
                            inviteCode = m["invite_code"]?.toString() ?: "KNK-0000",
                            status = m["status"]?.toString() ?: EmployeeInvitation.STATUS_PENDING,
                            createdAt = m["created_at"]?.toString() ?: "2026-11-03",
                            expiresAt = m["expires_at"]?.toString() ?: "2026-11-10"
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    private fun loadStockTransfers(): List<StockTransfer> {
        val json = prefs.getString("stock_transfers_json", null)
        if (json != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                val list = moshi.adapter(List::class.java).fromJson(json) as? List<Map<String, Any?>>
                if (!list.isNullOrEmpty()) {
                    return list.map { m ->
                        StockTransfer(
                            transferId = m["transfer_id"]?.toString() ?: UUID.randomUUID().toString(),
                            businessId = m["business_id"]?.toString() ?: "biz_001",
                            sourceType = m["source_type"]?.toString() ?: "WAREHOUSE",
                            sourceId = m["source_id"]?.toString() ?: "WAREHOUSE",
                            sourceName = m["source_name"]?.toString() ?: "Central Warehouse",
                            destinationVanId = m["dest_van_id"]?.toString() ?: "",
                            destinationVanName = m["dest_van_name"]?.toString() ?: "Van",
                            productId = m["product_id"]?.toString() ?: "",
                            productName = m["product_name"]?.toString() ?: "",
                            qty = BahrainFinancials.parseBhd(m["qty"]?.toString() ?: "0"),
                            transferredBy = m["transferred_by"]?.toString() ?: "",
                            timestamp = m["timestamp"]?.toString() ?: "",
                            notes = m["notes"]?.toString() ?: ""
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    private fun loadAuditRecords(): List<AuditRecord> {
        val json = prefs.getString("audit_records_json", null)
        if (json != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                val list = moshi.adapter(List::class.java).fromJson(json) as? List<Map<String, Any?>>
                if (!list.isNullOrEmpty()) {
                    return list.map { m ->
                        AuditRecord(
                            auditId = m["audit_id"]?.toString() ?: UUID.randomUUID().toString(),
                            businessId = m["business_id"]?.toString() ?: "biz_001",
                            actorUserId = m["actor_user_id"]?.toString() ?: "",
                            actorEmail = m["actor_email"]?.toString() ?: "",
                            action = m["action"]?.toString() ?: "",
                            targetType = m["target_type"]?.toString() ?: "",
                            targetId = m["target_id"]?.toString() ?: "",
                            details = m["details"]?.toString() ?: "",
                            timestamp = m["timestamp"]?.toString() ?: ""
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    private fun saveMultiTenantState() {
        scope.launch(Dispatchers.IO) {
            try {
                // Vans
                val vansList = _vans.value.map { v ->
                    mapOf(
                        "van_id" to v.vanId,
                        "business_id" to v.businessId,
                        "van_code" to v.vanCode,
                        "van_name" to v.vanName,
                        "reg_no" to v.registrationNo,
                        "salesperson_id" to (v.assignedSalespersonId ?: ""),
                        "salesperson_name" to (v.assignedSalespersonName ?: ""),
                        "status" to v.status,
                        "notes" to v.notes
                    )
                }
                prefs.edit().putString("fleet_vans_json", moshi.adapter(List::class.java).toJson(vansList)).apply()

                // Van Stocks
                val stockMap = mutableMapOf<String, Map<String, String>>()
                for ((vId, pMap) in _vanStocks.value) {
                    val sub = mutableMapOf<String, String>()
                    for ((pId, qty) in pMap) {
                        sub[pId] = qty.toPlainString()
                    }
                    stockMap[vId] = sub
                }
                prefs.edit().putString("van_stocks_json", moshi.adapter(Map::class.java).toJson(stockMap)).apply()

                // Memberships
                val memList = _teamMemberships.value.map { m ->
                    mapOf(
                        "membership_id" to m.membershipId,
                        "business_id" to m.businessId,
                        "business_name" to m.businessName,
                        "user_id" to m.userId,
                        "email" to m.email,
                        "display_name" to m.displayName,
                        "role" to m.role,
                        "assigned_van_id" to (m.assignedVanId ?: ""),
                        "assigned_van_code" to (m.assignedVanCode ?: ""),
                        "permissions" to m.permissions.toList(),
                        "status" to m.status,
                        "joined_at" to m.joinedAt
                    )
                }
                prefs.edit().putString("team_memberships_json", moshi.adapter(List::class.java).toJson(memList)).apply()

                // Invitations
                val invList = _pendingInvitations.value.map { inv ->
                    mapOf(
                        "invitation_id" to inv.invitationId,
                        "business_id" to inv.businessId,
                        "business_name" to inv.businessName,
                        "recipient_email" to inv.recipientEmail,
                        "role" to inv.role,
                        "assigned_van_id" to (inv.assignedVanId ?: ""),
                        "assigned_van_code" to (inv.assignedVanCode ?: ""),
                        "permissions" to inv.permissions.toList(),
                        "invite_code" to inv.inviteCode,
                        "status" to inv.status,
                        "created_at" to inv.createdAt,
                        "expires_at" to inv.expiresAt
                    )
                }
                prefs.edit().putString("pending_invitations_json", moshi.adapter(List::class.java).toJson(invList)).apply()

                // Stock Transfers
                val trList = _stockTransfers.value.map { tr ->
                    mapOf(
                        "transfer_id" to tr.transferId,
                        "business_id" to tr.businessId,
                        "source_type" to tr.sourceType,
                        "source_id" to tr.sourceId,
                        "source_name" to tr.sourceName,
                        "dest_van_id" to tr.destinationVanId,
                        "dest_van_name" to tr.destinationVanName,
                        "product_id" to tr.productId,
                        "product_name" to tr.productName,
                        "qty" to tr.qty.toPlainString(),
                        "transferred_by" to tr.transferredBy,
                        "timestamp" to tr.timestamp,
                        "notes" to tr.notes
                    )
                }
                prefs.edit().putString("stock_transfers_json", moshi.adapter(List::class.java).toJson(trList)).apply()

                // Audit records
                val audList = _auditRecords.value.take(200).map { a ->
                    mapOf(
                        "audit_id" to a.auditId,
                        "business_id" to a.businessId,
                        "actor_user_id" to a.actorUserId,
                        "actor_email" to a.actorEmail,
                        "action" to a.action,
                        "target_type" to a.targetType,
                        "target_id" to a.targetId,
                        "details" to a.details,
                        "timestamp" to a.timestamp
                    )
                }
                prefs.edit().putString("audit_records_json", moshi.adapter(List::class.java).toJson(audList)).apply()
            } catch (e: Exception) {
                android.util.Log.w("KanakRepository", "Multi-tenant save error: ${e.message}")
            }
        }
    }

    fun recordAuditEvent(
        businessId: String,
        actorUserId: String,
        actorEmail: String,
        action: String,
        targetType: String,
        targetId: String,
        details: String
    ) {
        val record = AuditRecord(
            auditId = UUID.randomUUID().toString(),
            businessId = businessId,
            actorUserId = actorUserId,
            actorEmail = actorEmail,
            action = action,
            targetType = targetType,
            targetId = targetId,
            details = details,
            timestamp = getBahrainTimestampString()
        )
        _auditRecords.value = listOf(record) + _auditRecords.value.take(199)
    }

    suspend fun createVan(
        vanCode: String,
        vanName: String,
        registrationNo: String = "",
        assignedSalespersonId: String? = null,
        assignedSalespersonName: String? = null,
        notes: String = ""
    ): Result<Van> = mutationMutex.withLock {
        val trimmedCode = vanCode.trim().uppercase()
        val trimmedName = vanName.trim()
        if (trimmedCode.isBlank()) return Result.failure(IllegalArgumentException("Van code cannot be empty"))
        if (trimmedName.isBlank()) return Result.failure(IllegalArgumentException("Van name cannot be empty"))

        if (_vans.value.any { it.vanCode.equals(trimmedCode, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("Van with code '$trimmedCode' already exists"))
        }

        val van = Van(
            vanId = UUID.randomUUID().toString(),
            businessId = getBusinessId(),
            vanCode = trimmedCode,
            vanName = trimmedName,
            registrationNo = registrationNo.trim(),
            assignedSalespersonId = assignedSalespersonId,
            assignedSalespersonName = assignedSalespersonName,
            status = if (assignedSalespersonId != null) Van.STATUS_ACTIVE else Van.STATUS_AVAILABLE,
            notes = notes.trim()
        )

        _vans.value = _vans.value + van
        recordAuditEvent(
            businessId = getBusinessId(),
            actorUserId = "admin",
            actorEmail = "owner",
            action = "VAN_CREATED",
            targetType = "VAN",
            targetId = van.vanId,
            details = "Created van ${van.vanCode} (${van.vanName})"
        )
        saveMultiTenantState()
        recalculateDashboard()
        return Result.success(van)
    }

    suspend fun updateVan(van: Van): Result<Van> = mutationMutex.withLock {
        val index = _vans.value.indexOfFirst { it.vanId == van.vanId }
        if (index == -1) return Result.failure(IllegalArgumentException("Van not found"))

        val list = _vans.value.toMutableList()
        list[index] = van
        _vans.value = list

        recordAuditEvent(
            businessId = getBusinessId(),
            actorUserId = "admin",
            actorEmail = "owner",
            action = "VAN_UPDATED",
            targetType = "VAN",
            targetId = van.vanId,
            details = "Updated van ${van.vanCode}"
        )
        saveMultiTenantState()
        recalculateDashboard()
        return Result.success(van)
    }

    suspend fun assignSalespersonToVan(
        vanId: String,
        salespersonId: String?,
        salespersonName: String?
    ): Result<Van> = mutationMutex.withLock {
        val index = _vans.value.indexOfFirst { it.vanId == vanId }
        if (index == -1) return Result.failure(IllegalArgumentException("Van not found"))

        val currentVan = _vans.value[index]
        val updatedVan = currentVan.copy(
            assignedSalespersonId = salespersonId,
            assignedSalespersonName = salespersonName,
            status = if (salespersonId != null) Van.STATUS_ACTIVE else Van.STATUS_AVAILABLE
        )
        val updatedList = _vans.value.toMutableList()
        updatedList[index] = updatedVan
        _vans.value = updatedList

        // Update corresponding employee membership if found
        if (salespersonId != null) {
            val memIndex = _teamMemberships.value.indexOfFirst { it.userId == salespersonId }
            if (memIndex != -1) {
                val mem = _teamMemberships.value[memIndex]
                val updatedMemList = _teamMemberships.value.toMutableList()
                updatedMemList[memIndex] = mem.copy(assignedVanId = vanId, assignedVanCode = updatedVan.vanCode)
                _teamMemberships.value = updatedMemList
            }
        }

        recordAuditEvent(
            businessId = getBusinessId(),
            actorUserId = "admin",
            actorEmail = "owner",
            action = "VAN_ASSIGNED",
            targetType = "VAN",
            targetId = vanId,
            details = "Assigned ${salespersonName ?: "unassigned"} to van ${updatedVan.vanCode}"
        )
        saveMultiTenantState()
        recalculateDashboard()
        return Result.success(updatedVan)
    }

    suspend fun deleteVan(vanId: String): Result<Unit> = mutationMutex.withLock {
        val van = _vans.value.find { it.vanId == vanId }
            ?: return Result.failure(IllegalArgumentException("Van not found"))

        // Check if any invoices are associated with this van
        val hasInvoices = _invoices.value.any { it.vanId == vanId }
        if (hasInvoices) {
            return Result.failure(IllegalStateException("Cannot delete van ${van.vanCode} because invoices are recorded against it. Set status to Maintenance instead."))
        }

        // Check if van has inventory
        val vanStock = _vanStocks.value[vanId]?.values?.any { it > BigDecimal.ZERO } == true
        if (vanStock) {
            return Result.failure(IllegalStateException("Cannot delete van ${van.vanCode} while it has allocated inventory. Please transfer all stock back to warehouse first."))
        }

        val updatedVans = _vans.value.filter { it.vanId != vanId }
        _vans.value = updatedVans

        val currentVanStocks = _vanStocks.value.toMutableMap()
        currentVanStocks.remove(vanId)
        _vanStocks.value = currentVanStocks

        recordAuditEvent(
            businessId = getBusinessId(),
            actorUserId = "admin",
            actorEmail = "owner",
            action = "VAN_DELETED",
            targetType = "VAN",
            targetId = vanId,
            details = "Deleted van ${van.vanCode} (${van.vanName})"
        )
        saveMultiTenantState()
        recalculateDashboard()
        return Result.success(Unit)
    }

    fun resolveUserWithMembership(baseUser: User): User {
        val cleanEmail = baseUser.email.trim()
        val membership = _teamMemberships.value.find {
            it.email.equals(cleanEmail, ignoreCase = true) || it.userId == baseUser.userId
        }
        if (membership != null) {
            return baseUser.copy(
                role = membership.role,
                businessId = membership.businessId,
                businessName = membership.businessName,
                assignedVanId = membership.assignedVanId,
                assignedVanCode = membership.assignedVanCode,
                permissions = membership.permissions,
                status = membership.status
            )
        }

        return baseUser
    }

    /**
     * Checks if a user has an active, registered business linked to their account.
     * Enforces the "no business = no user" policy by requiring completion of the Business Setup Wizard.
     */
    fun isBusinessSetupCompleted(email: String): Boolean {
        val clean = email.trim().lowercase(Locale.US)
        // Check if user is an active team/staff member belonging to a registered business
        val membership = _teamMemberships.value.find {
            (it.email.trim().equals(clean, ignoreCase = true) || it.userId == clean) &&
            it.businessId.isNotBlank() &&
            !it.status.equals("SUSPENDED", ignoreCase = true)
        }
        if (membership != null) return true

        // Check if user completed the first-time setup wizard
        val completedInPrefs = prefs.getBoolean("biz_setup_completed_$clean", false)
        if (completedInPrefs) return true

        return false
    }

    fun markBusinessSetupCompleted(email: String, completed: Boolean = true) {
        val clean = email.trim().lowercase(Locale.US)
        prefs.edit().putBoolean("biz_setup_completed_$clean", completed).apply()
    }

    suspend fun completeBusinessSetup(
        userId: String,
        userEmail: String,
        companyName: String,
        businessType: String,
        address: String,
        phone: String,
        crNumber: String,
        vatNumber: String,
        spreadsheetId: String?,
        createInitialVan: Boolean,
        vanCode: String,
        vanName: String,
        registrationNo: String,
        initialStaffName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            setBusinessName(companyName)
            setBusinessAddress(address)
            if (vatNumber.isNotBlank()) setVatNumber(vatNumber)

            val newBizId = "biz_${UUID.randomUUID().toString().take(8)}"
            val newBusiness = Business(
                businessId = newBizId,
                name = companyName,
                crNumber = crNumber.trim(),
                vatNumber = vatNumber.trim(),
                ownerEmail = userEmail,
                ownerName = companyName,
                phone = phone,
                address = address,
                status = Business.STATUS_ACTIVE,
                createdAt = getBahrainTimestampString()
            )
            _currentBusiness.value = newBusiness
            // Only the authenticated creator receives business administrator permissions.
            val ownerMembership = EmployeeMembership(
                businessId = newBizId,
                businessName = companyName,
                userId = userId,
                email = userEmail.trim(),
                displayName = userEmail.substringBefore('@'),
                role = User.ROLE_ADMIN,
                permissions = KanakPermissions.ALL_PERMISSIONS,
                status = "ACTIVE",
                joinedAt = getBahrainDateString()
            )
            _teamMemberships.value = _teamMemberships.value.filterNot {
                it.userId == userId || it.email.equals(userEmail, ignoreCase = true)
            } + ownerMembership
            saveMultiTenantState()
            prefs.edit().putString("current_business_json", moshi.adapter(Map::class.java).toJson(
                mapOf(
                    "business_id" to newBusiness.businessId,
                    "name" to newBusiness.name,
                    "cr_number" to newBusiness.crNumber,
                    "vat_number" to newBusiness.vatNumber,
                    "owner_email" to newBusiness.ownerEmail,
                    "owner_name" to newBusiness.ownerName,
                    "phone" to newBusiness.phone,
                    "address" to newBusiness.address,
                    "status" to newBusiness.status,
                    "created_at" to newBusiness.createdAt
                )
            )).apply()

            // Link spreadsheet
            if (!spreadsheetId.isNullOrBlank()) {
                autoProvisionGoogleWorkbook(companyName, spreadsheetId)
            }

            // Create initial van and staff if requested
            if (createInitialVan) {
                val vId = "van_${UUID.randomUUID().toString().take(8)}"
                val newVan = Van(
                    vanId = vId,
                    businessId = newBizId,
                    vanCode = vanCode.trim().ifBlank { error("Van code required") },
                    vanName = vanName.trim().ifBlank { error("Van name required") },
                    registrationNo = registrationNo.trim().ifBlank { error("Van plate number required") },
                    assignedSalespersonId = null,
                    assignedSalespersonName = null,
                    status = Van.STATUS_AVAILABLE,
                    notes = ""
                )
                _vans.value = _vans.value + newVan
                saveMultiTenantState()
            }

            // Mark setup complete
            markBusinessSetupCompleted(userEmail, true)
            recalculateDashboard()

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("KanakRepository", "Error completing business setup: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun upsertVanLocally(van: Van) {
        val current = _vans.value.toMutableList()
        val idx = current.indexOfFirst { it.vanId == van.vanId || it.vanCode.equals(van.vanCode, ignoreCase = true) }
        if (idx >= 0) {
            current[idx] = van
        } else {
            current.add(van)
        }
        _vans.value = current
        saveMultiTenantState()
    }

    suspend fun upsertStaffLocally(member: EmployeeMembership) {
        val current = _teamMemberships.value.toMutableList()
        val idx = current.indexOfFirst { it.membershipId == member.membershipId || it.email.equals(member.email, ignoreCase = true) }
        if (idx >= 0) {
            current[idx] = member
        } else {
            current.add(member)
        }
        _teamMemberships.value = current
        saveMultiTenantState()
    }

    suspend fun createStaffMember(
        email: String,
        displayName: String,
        role: String = User.ROLE_SALESPERSON,
        assignedVanId: String? = null,
        permissions: Set<String> = KanakPermissions.DEFAULT_SALESPERSON_PERMISSIONS,
        addedBy: String
    ): Result<EmployeeMembership> = mutationMutex.withLock {
        val trimmedEmail = email.trim().lowercase()
        if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Please provide a valid staff email"))
        }

        if (_teamMemberships.value.any { it.email.equals(trimmedEmail, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("Staff member with email '$trimmedEmail' already exists"))
        }

        val assignedVan = _vans.value.find { it.vanId == assignedVanId }
        val name = displayName.trim().ifBlank {
            trimmedEmail.substringBefore('@').replace('.', ' ')
                .split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }

        val membership = EmployeeMembership(
            membershipId = UUID.randomUUID().toString(),
            businessId = getBusinessId(),
            businessName = getBusinessName(),
            userId = UUID.randomUUID().toString(),
            email = trimmedEmail,
            displayName = name,
            role = role,
            assignedVanId = assignedVanId,
            assignedVanCode = assignedVan?.vanCode,
            permissions = permissions,
            status = "ACTIVE",
            joinedAt = getBahrainDateString()
        )

        _teamMemberships.value = _teamMemberships.value + membership

        if (assignedVanId != null) {
            val vIdx = _vans.value.indexOfFirst { it.vanId == assignedVanId }
            if (vIdx != -1) {
                val v = _vans.value[vIdx]
                val vList = _vans.value.toMutableList()
                vList[vIdx] = v.copy(
                    assignedSalespersonId = membership.userId,
                    assignedSalespersonName = membership.displayName,
                    status = Van.STATUS_ACTIVE
                )
                _vans.value = vList
            }
        }

        recordAuditEvent(
            businessId = getBusinessId(),
            actorUserId = addedBy,
            actorEmail = addedBy,
            action = "STAFF_ADDED",
            targetType = "MEMBERSHIP",
            targetId = membership.membershipId,
            details = "Added staff $trimmedEmail ($name) as $role to van ${assignedVan?.vanCode ?: "None"}"
        )
        saveMultiTenantState()
        return Result.success(membership)
    }

    suspend fun transferStock(
        sourceType: String, // "WAREHOUSE" or "VAN"
        sourceId: String,   // "WAREHOUSE" or vanId
        destinationVanId: String,
        productId: String,
        qty: BigDecimal,
        transferredBy: String,
        notes: String = ""
    ): Result<StockTransfer> = mutationMutex.withLock {
        if (qty <= BigDecimal.ZERO) {
            return Result.failure(IllegalArgumentException("Transfer quantity must be greater than zero"))
        }

        val prod = _products.value.find { it.productId == productId }
            ?: return Result.failure(IllegalArgumentException("Product not found"))

        val isReturnToWarehouse = destinationVanId.equals("WAREHOUSE", ignoreCase = true) ||
                destinationVanId.equals("central_warehouse", ignoreCase = true)

        val destVan = if (!isReturnToWarehouse) {
            _vans.value.find { it.vanId == destinationVanId }
                ?: return Result.failure(IllegalArgumentException("Destination van not found"))
        } else null

        val currentVanStocks = _vanStocks.value.toMutableMap()
        val destVanStockMap = if (!isReturnToWarehouse) {
            (currentVanStocks[destinationVanId] ?: emptyMap()).toMutableMap()
        } else mutableMapOf()

        val sourceName: String
        if (sourceType.equals("WAREHOUSE", ignoreCase = true)) {
            sourceName = "Central Warehouse"
            if (prod.stockQty < qty) {
                return Result.failure(IllegalStateException("Insufficient warehouse stock for '${prod.name}'. Available: ${prod.stockQty}, requested: $qty"))
            }
            // Deduct from warehouse
            val updatedProducts = _products.value.toMutableList()
            val prodIdx = updatedProducts.indexOfFirst { it.productId == productId }
            updatedProducts[prodIdx] = prod.copy(stockQty = prod.stockQty.subtract(qty))
            _products.value = updatedProducts
            scope.launch(Dispatchers.IO) {
                roomDb.productDao().updateStock(productId, updatedProducts[prodIdx].stockQty)
            }
        } else {
            val srcVan = _vans.value.find { it.vanId == sourceId }
                ?: return Result.failure(IllegalArgumentException("Source van not found"))
            sourceName = srcVan.vanCode
            val srcVanStockMap = (currentVanStocks[sourceId] ?: emptyMap()).toMutableMap()
            val currentSrcStock = srcVanStockMap[productId] ?: BigDecimal.ZERO
            if (currentSrcStock < qty) {
                return Result.failure(IllegalStateException("Insufficient stock in ${srcVan.vanCode} for '${prod.name}'. Available: $currentSrcStock, requested: $qty"))
            }
            srcVanStockMap[productId] = currentSrcStock.subtract(qty)
            currentVanStocks[sourceId] = srcVanStockMap
            _vanStocks.value = currentVanStocks
        }

        val destinationVanName: String
        if (isReturnToWarehouse) {
            destinationVanName = "Central Warehouse"
            val updatedProducts = _products.value.toMutableList()
            val prodIdx = updatedProducts.indexOfFirst { it.productId == productId }
            if (prodIdx != -1) {
                updatedProducts[prodIdx] = prod.copy(stockQty = prod.stockQty.add(qty))
                _products.value = updatedProducts
                scope.launch(Dispatchers.IO) {
                    roomDb.productDao().updateStock(productId, updatedProducts[prodIdx].stockQty)
                }
            }
        } else {
            destinationVanName = "${destVan?.vanCode} (${destVan?.vanName})"
            val currentDestStock = destVanStockMap[productId] ?: BigDecimal.ZERO
            destVanStockMap[productId] = currentDestStock.add(qty)
            currentVanStocks[destinationVanId] = destVanStockMap
            _vanStocks.value = currentVanStocks
        }

        val transfer = StockTransfer(
            transferId = UUID.randomUUID().toString(),
            businessId = getBusinessId(),
            sourceType = sourceType.uppercase(),
            sourceId = sourceId,
            sourceName = sourceName,
            destinationVanId = destinationVanId,
            destinationVanName = destinationVanName,
            productId = productId,
            productName = prod.name,
            qty = qty,
            transferredBy = transferredBy,
            timestamp = getBahrainTimestampString(),
            notes = notes
        )

        _stockTransfers.value = listOf(transfer) + _stockTransfers.value
        recordAuditEvent(
            businessId = getBusinessId(),
            actorUserId = transferredBy,
            actorEmail = transferredBy,
            action = "STOCK_TRANSFERRED",
            targetType = "INVENTORY",
            targetId = transfer.transferId,
            details = "Transferred ${qty.stripTrailingZeros().toPlainString()} of ${prod.name} from $sourceName to $destinationVanName"
        )
        saveMultiTenantState()
        saveLocalDatabase()
        return Result.success(transfer)
    }

    fun getVanStock(vanId: String, productId: String): BigDecimal {
        return _vanStocks.value[vanId]?.get(productId) ?: BigDecimal.ZERO
    }

    suspend fun inviteEmployee(
        recipientEmail: String,
        role: String = User.ROLE_SALESPERSON,
        assignedVanId: String? = null,
        permissions: Set<String> = KanakPermissions.DEFAULT_SALESPERSON_PERMISSIONS,
        invitedBy: String
    ): Result<EmployeeInvitation> = mutationMutex.withLock {
        val trimmedEmail = recipientEmail.trim().lowercase()
        if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Please provide a valid recipient email"))
        }

        val assignedVan = _vans.value.find { it.vanId == assignedVanId }
        val randomDigits = (1000..9999).random()
        val inviteCode = "KNK-$randomDigits"

        val invitation = EmployeeInvitation(
            invitationId = UUID.randomUUID().toString(),
            businessId = getBusinessId(),
            businessName = getBusinessName(),
            recipientEmail = trimmedEmail,
            role = role,
            assignedVanId = assignedVanId,
            assignedVanCode = assignedVan?.vanCode,
            permissions = permissions,
            inviteCode = inviteCode,
            status = EmployeeInvitation.STATUS_PENDING,
            createdAt = getBahrainDateString(),
            expiresAt = "2026-11-30"
        )

        _pendingInvitations.value = listOf(invitation) + _pendingInvitations.value
        recordAuditEvent(
            businessId = getBusinessId(),
            actorUserId = invitedBy,
            actorEmail = invitedBy,
            action = "EMPLOYEE_INVITED",
            targetType = "INVITATION",
            targetId = invitation.invitationId,
            details = "Invited $trimmedEmail as $role to van ${assignedVan?.vanCode ?: "None"}"
        )
        saveMultiTenantState()
        return Result.success(invitation)
    }

    suspend fun resendInvitation(invitationId: String): Result<EmployeeInvitation> = mutationMutex.withLock {
        val index = _pendingInvitations.value.indexOfFirst { it.invitationId == invitationId }
        if (index == -1) return Result.failure(IllegalArgumentException("Invitation not found"))
        val inv = _pendingInvitations.value[index]
        return Result.success(inv)
    }

    suspend fun revokeInvitation(invitationId: String): Result<Unit> = mutationMutex.withLock {
        val index = _pendingInvitations.value.indexOfFirst { it.invitationId == invitationId }
        if (index == -1) return Result.failure(IllegalArgumentException("Invitation not found"))

        val inv = _pendingInvitations.value[index]
        val updated = _pendingInvitations.value.toMutableList()
        updated[index] = inv.copy(status = EmployeeInvitation.STATUS_REVOKED)
        _pendingInvitations.value = updated
        saveMultiTenantState()
        return Result.success(Unit)
    }

    suspend fun acceptInvitation(
        inviteCode: String,
        user: User
    ): Result<EmployeeMembership> = mutationMutex.withLock {
        val cleanCode = inviteCode.trim().uppercase()
        val index = _pendingInvitations.value.indexOfFirst {
            it.inviteCode.equals(cleanCode, ignoreCase = true) && it.status == EmployeeInvitation.STATUS_PENDING
        }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("Invalid or expired invitation code. Please check with your business owner."))
        }

        val invite = _pendingInvitations.value[index]
        val updatedInvites = _pendingInvitations.value.toMutableList()
        updatedInvites[index] = invite.copy(status = EmployeeInvitation.STATUS_ACCEPTED)
        _pendingInvitations.value = updatedInvites

        val membership = EmployeeMembership(
            membershipId = UUID.randomUUID().toString(),
            businessId = invite.businessId,
            businessName = invite.businessName,
            userId = user.userId,
            email = user.email,
            displayName = user.displayName,
            role = invite.role,
            assignedVanId = invite.assignedVanId,
            assignedVanCode = invite.assignedVanCode,
            permissions = invite.permissions,
            status = "ACTIVE",
            joinedAt = getBahrainDateString()
        )

        _teamMemberships.value = _teamMemberships.value + membership

        // If van was assigned, assign driver name to van
        if (invite.assignedVanId != null) {
            val vIdx = _vans.value.indexOfFirst { it.vanId == invite.assignedVanId }
            if (vIdx != -1) {
                val v = _vans.value[vIdx]
                val vList = _vans.value.toMutableList()
                vList[vIdx] = v.copy(
                    assignedSalespersonId = user.userId,
                    assignedSalespersonName = user.displayName,
                    status = Van.STATUS_ACTIVE
                )
                _vans.value = vList
            }
        }

        recordAuditEvent(
            businessId = invite.businessId,
            actorUserId = user.userId,
            actorEmail = user.email,
            action = "INVITATION_ACCEPTED",
            targetType = "MEMBERSHIP",
            targetId = membership.membershipId,
            details = "${user.email} joined ${invite.businessName} as ${invite.role}"
        )
        saveMultiTenantState()
        return Result.success(membership)
    }

    suspend fun updateMemberRoleAndVan(
        membershipId: String,
        newRole: String,
        newVanId: String?,
        newPermissions: Set<String>,
        updatedBy: String
    ): Result<EmployeeMembership> = mutationMutex.withLock {
        val index = _teamMemberships.value.indexOfFirst { it.membershipId == membershipId }
        if (index == -1) return Result.failure(IllegalArgumentException("Membership not found"))

        val current = _teamMemberships.value[index]
        val van = _vans.value.find { it.vanId == newVanId }
        val updated = current.copy(
            role = newRole,
            assignedVanId = newVanId,
            assignedVanCode = van?.vanCode,
            permissions = newPermissions
        )
        val list = _teamMemberships.value.toMutableList()
        list[index] = updated
        _teamMemberships.value = list

        recordAuditEvent(
            businessId = getBusinessId(),
            actorUserId = updatedBy,
            actorEmail = updatedBy,
            action = "MEMBER_UPDATED",
            targetType = "MEMBERSHIP",
            targetId = membershipId,
            details = "Updated ${current.email}: Role=$newRole, Van=${van?.vanCode ?: "None"}"
        )
        saveMultiTenantState()
        return Result.success(updated)
    }

    suspend fun toggleMemberStatus(
        membershipId: String,
        suspend: Boolean,
        updatedBy: String
    ): Result<EmployeeMembership> = mutationMutex.withLock {
        val index = _teamMemberships.value.indexOfFirst { it.membershipId == membershipId }
        if (index == -1) return Result.failure(IllegalArgumentException("Membership not found"))

        val current = _teamMemberships.value[index]
        val newStatus = if (suspend) "SUSPENDED" else "ACTIVE"
        val updated = current.copy(status = newStatus)
        val list = _teamMemberships.value.toMutableList()
        list[index] = updated
        _teamMemberships.value = list

        recordAuditEvent(
            businessId = getBusinessId(),
            actorUserId = updatedBy,
            actorEmail = updatedBy,
            action = if (suspend) "MEMBER_SUSPENDED" else "MEMBER_ACTIVATED",
            targetType = "MEMBERSHIP",
            targetId = membershipId,
            details = "${if (suspend) "Suspended" else "Activated"} access for ${current.email}"
        )
        saveMultiTenantState()
        return Result.success(updated)
    }

    suspend fun removeMember(
        membershipId: String,
        removedBy: String
    ): Result<Unit> = mutationMutex.withLock {
        val member = _teamMemberships.value.find { it.membershipId == membershipId }
            ?: return Result.failure(IllegalArgumentException("Member not found"))

        _teamMemberships.value = _teamMemberships.value.filter { it.membershipId != membershipId }
        recordAuditEvent(
            businessId = getBusinessId(),
            actorUserId = removedBy,
            actorEmail = removedBy,
            action = "MEMBER_REMOVED",
            targetType = "MEMBERSHIP",
            targetId = membershipId,
            details = "Removed membership for ${member.email}"
        )
        saveMultiTenantState()
        return Result.success(Unit)
    }

    fun getDailySalesSummary(
        vanIdFilter: String? = null,
        dateFilter: String? = null
    ): DailySalesSummaryData {
        val bahrainTz = TimeZone.getTimeZone("Asia/Bahrain")
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = bahrainTz }
        val targetDate = dateFilter ?: df.format(Date())

        val targetInvoices = _invoices.value.filter { inv ->
            !inv.isVoid && inv.date.startsWith(targetDate) && (vanIdFilter.isNullOrBlank() || inv.vanId == vanIdFilter)
        }

        var totalGross = BigDecimal.ZERO
        var totalCash = BigDecimal.ZERO
        var cashCount = 0
        var totalCredit = BigDecimal.ZERO
        var creditCount = 0
        var cashCollectedCredit = BigDecimal.ZERO
        var outstandingCredit = BigDecimal.ZERO
        var taxableSubtotal = BigDecimal.ZERO
        var vatCollected = BigDecimal.ZERO

        for (inv in targetInvoices) {
            totalGross = totalGross.add(inv.total)
            taxableSubtotal = taxableSubtotal.add(inv.subtotal)
            vatCollected = vatCollected.add(inv.vatTotal)

            if (!inv.isCredit) {
                totalCash = totalCash.add(inv.total)
                cashCount++
            } else {
                totalCredit = totalCredit.add(inv.total)
                creditCount++
                cashCollectedCredit = cashCollectedCredit.add(inv.amountPaid)
                outstandingCredit = outstandingCredit.add(inv.balanceDue)
            }
        }

        val vanObj = _vans.value.find { it.vanId == vanIdFilter }

        return DailySalesSummaryData(
            sessionDate = targetDate,
            vanId = vanIdFilter,
            vanCode = vanObj?.vanCode,
            totalGrossSales = BahrainFinancials.scaleAmount(totalGross),
            totalInvoicesCount = targetInvoices.size,
            totalCashSales = BahrainFinancials.scaleAmount(totalCash),
            cashInvoicesCount = cashCount,
            totalCreditSales = BahrainFinancials.scaleAmount(totalCredit),
            creditInvoicesCount = creditCount,
            cashCollectedOnCredit = BahrainFinancials.scaleAmount(cashCollectedCredit),
            totalOutstandingCredit = BahrainFinancials.scaleAmount(outstandingCredit),
            taxableSubtotal = BahrainFinancials.scaleAmount(taxableSubtotal),
            vatRate = getConfiguredVatRate(),
            vatAmountCollected = BahrainFinancials.scaleAmount(vatCollected),
            invoices = targetInvoices
        )
    }
}

data class Tuple6<A, B, C, D, E, F>(
    val a: A, val b: B, val c: C, val d: D, val e: E, val f: F
)

