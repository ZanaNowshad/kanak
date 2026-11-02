package com.example.kanak.data.repository

import android.content.Context
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

    private val _dashboardSummary = MutableStateFlow(DashboardSummary())
    val dashboardSummary: StateFlow<DashboardSummary> = _dashboardSummary.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    init {
        if (!loadLocalDatabase()) {
            seedInitialFmcgData()
            saveLocalDatabase()
        }
        recalculateDashboard()
    }

    fun getGatewayUrl(): String? {
        return prefs.getString("gateway_url", null)
    }

    fun setGatewayUrl(url: String?) {
        prefs.edit().putString("gateway_url", url?.trim()).apply()
    }

    fun getBusinessName(): String {
        return prefs.getString("biz_name", "KANAK FMCG WHOLESALE W.L.L.") ?: "KANAK FMCG WHOLESALE W.L.L."
    }

    fun setBusinessName(name: String) {
        prefs.edit().putString("biz_name", name.trim()).apply()
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
        preAssignedInvoiceId: String? = null
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
        val invoiceNumber = generateNextInvoiceNumber()
        val nowBahrain = getBahrainTimestampString()

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
            lines = invoiceLines
        )

        // Commit atomically to state
        _products.value = updatedProducts
        _invoices.value = listOf(invoice) + _invoices.value
        recalculateDashboard()

        // Sync with Google Sheets backend
        syncToGatewayIfConfigured("create_invoice", mapOf(
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
        ))

        // Cloud backup to Firebase Firestore
        scope.launch {
            try {
                firebaseManager.backupInvoiceToFirestore(invoice, createdBy)
            } catch (_: Exception) {
            }
        }

        return Result.success(invoice)
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
        val productSalesMap = mutableMapOf<String, Pair<BigDecimal, BigDecimal>>() // productId -> (qty, revenue)

        for (inv in postedToday) {
            todayTotal = todayTotal.add(inv.total)
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

        _dashboardSummary.value = DashboardSummary(
            todaySalesTotal = BahrainFinancials.scaleAmount(todayTotal),
            todayInvoiceCount = postedToday.size,
            topSellingProducts = topList
        )
        saveLocalDatabase()
    }

    private fun saveLocalDatabase() {
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
                        lines = lines
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

    private fun generateNextInvoiceNumber(): String {
        val count = _invoices.value.size + 1
        val year = Calendar.getInstance(TimeZone.getTimeZone("Asia/Bahrain")).get(Calendar.YEAR)
        return String.format(Locale.US, "INV-%d-%04d", year, count)
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
            val token = currentAuthToken ?: "device_bearer_token"
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
                val token = currentAuthToken ?: "device_bearer_token"
                val req = GatewayRequest(action = action, idToken = token, payload = payload)
                val resp = apiService.executeGatewayAction(gatewayUrl, req)
                if (!resp.isSuccessful) {
                    _lastSyncError.value = "Gateway HTTP ${resp.code()}"
                }
            } catch (e: Exception) {
                _lastSyncError.value = "Sync error: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /**
     * Seeds initial Bahrain FMCG wholesale inventory data with actual products,
     * realistic prices in BHD, and stock in van for instant ready-to-use testing.
     */
    private fun seedInitialFmcgData() {
        val p1 = Product(
            productId = "prod_001",
            barcode = "6281007000018",
            name = "Almarai Full Cream Fresh Milk 2L",
            costPrice = BigDecimal("0.850"),
            sellingPrice = BigDecimal("1.100"),
            vatRate = BigDecimal("0.10"),
            vatAmount = BigDecimal("0.100"),
            photoUrl = BingThumbnailUrlBuilder.buildDefaultProductImageUrl("6281007000018", "Almarai Full Cream Fresh Milk 2L"),
            stockQty = BigDecimal("45.000")
        )
        val p2 = Product(
            productId = "prod_002",
            barcode = "6291003001234",
            name = "Tiffany Marie Biscuits 12x100g",
            costPrice = BigDecimal("0.600"),
            sellingPrice = BigDecimal("0.800"),
            vatRate = BigDecimal("0.10"),
            vatAmount = BigDecimal("0.073"),
            photoUrl = BingThumbnailUrlBuilder.buildDefaultProductImageUrl("6291003001234", "Tiffany Marie Biscuits 12x100g"),
            stockQty = BigDecimal("60.000")
        )
        val p3 = Product(
            productId = "prod_003",
            barcode = "5740900000456",
            name = "Lurpak Salted Butter 200g",
            costPrice = BigDecimal("0.950"),
            sellingPrice = BigDecimal("1.250"),
            vatRate = BigDecimal("0.10"),
            vatAmount = BigDecimal("0.114"),
            photoUrl = BingThumbnailUrlBuilder.buildDefaultProductImageUrl("5740900000456", "Lurpak Salted Butter 200g"),
            stockQty = BigDecimal("30.000")
        )
        val p4 = Product(
            productId = "prod_004",
            barcode = "6281017000789",
            name = "Rani Float Mango Juice 240ml Can x24",
            costPrice = BigDecimal("3.200"),
            sellingPrice = BigDecimal("4.200"),
            vatRate = BigDecimal("0.10"),
            vatAmount = BigDecimal("0.382"),
            photoUrl = BingThumbnailUrlBuilder.buildDefaultProductImageUrl("6281017000789", "Rani Float Mango Juice 240ml Can x24"),
            stockQty = BigDecimal("20.000")
        )
        val p5 = Product(
            productId = "prod_005",
            barcode = "6084001001122",
            name = "Bahrain Water 500ml Pack of 24",
            costPrice = BigDecimal("0.800"),
            sellingPrice = BigDecimal("1.100"),
            vatRate = BigDecimal("0.00"), // Zero-rated basic water bottle pack
            vatAmount = BigDecimal("0.000"),
            photoUrl = BingThumbnailUrlBuilder.buildDefaultProductImageUrl("6084001001122", "Bahrain Water 500ml Pack of 24"),
            stockQty = BigDecimal("50.000")
        )
        _products.value = listOf(p1, p2, p3, p4, p5)

        val c1 = Customer(
            customerId = "cust_001",
            businessName = "Al-Hilal Cold Store",
            contact = "Mohammed Al-Saeed",
            address = "Shop 12, Gudaibiya, Manama",
            phone = "+973 3912 3456"
        )
        val c2 = Customer(
            customerId = "cust_002",
            businessName = "Bab Al-Bahrain Supermarket",
            contact = "Salman Khan",
            address = "Govt Ave, Manama Souq",
            phone = "+973 3876 5432"
        )
        val c3 = Customer(
            customerId = "cust_003",
            businessName = "Riffa Modern Grocery",
            contact = "Abdulla Isa",
            address = "Road 14, East Riffa",
            phone = "+973 3654 9870"
        )
        _customers.value = listOf(c1, c2, c3)

        val s1 = Supplier(
            supplierId = "sup_001",
            name = "Bahrain Food Distribution WLL",
            contact = "Ali Jassim (+973 1722 0000)"
        )
        val s2 = Supplier(
            supplierId = "sup_002",
            name = "Gulf Dairy Products Co.",
            contact = "Kareem Hassan (+973 1788 1122)"
        )
        _suppliers.value = listOf(s1, s2)
    }
}
