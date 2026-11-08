package com.example.kanak.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProductsFlow(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProducts(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE productId = :productId LIMIT 1")
    suspend fun getProductById(productId: String): ProductEntity?

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Query("UPDATE products SET stockQty = :newStock WHERE productId = :productId")
    suspend fun updateStock(productId: String, newStock: BigDecimal)

    @Query("DELETE FROM products WHERE productId = :productId")
    suspend fun deleteById(productId: String)

    @Query("DELETE FROM products")
    suspend fun clearAll()
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY businessName ASC")
    fun getAllCustomersFlow(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY businessName ASC")
    suspend fun getAllCustomers(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE customerId = :customerId LIMIT 1")
    suspend fun getCustomerById(customerId: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(customer: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(customers: List<CustomerEntity>)

    @Query("DELETE FROM customers WHERE customerId = :customerId")
    suspend fun deleteById(customerId: String)

    @Query("DELETE FROM customers")
    suspend fun clearAll()
}

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun getAllSuppliersFlow(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    suspend fun getAllSuppliers(): List<SupplierEntity>

    @Query("SELECT * FROM suppliers WHERE supplierId = :supplierId LIMIT 1")
    suspend fun getSupplierById(supplierId: String): SupplierEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(supplier: SupplierEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(suppliers: List<SupplierEntity>)

    @Query("DELETE FROM suppliers WHERE supplierId = :supplierId")
    suspend fun deleteById(supplierId: String)

    @Query("DELETE FROM suppliers")
    suspend fun clearAll()
}

@Dao
interface PurchaseDao {
    @Query("SELECT * FROM purchases ORDER BY date DESC")
    fun getAllPurchasesFlow(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases ORDER BY date DESC")
    suspend fun getAllPurchases(): List<PurchaseEntity>

    @Query("SELECT * FROM purchases WHERE purchaseId = :purchaseId LIMIT 1")
    suspend fun getPurchaseById(purchaseId: String): PurchaseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(purchase: PurchaseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(purchases: List<PurchaseEntity>)

    @Query("DELETE FROM purchases WHERE purchaseId = :purchaseId")
    suspend fun deleteById(purchaseId: String)

    @Query("DELETE FROM purchases")
    suspend fun clearAll()
}

@Dao
interface InvoiceDao {
    @Transaction
    @Query("SELECT * FROM invoices ORDER BY date DESC")
    fun getAllInvoicesWithLinesFlow(): Flow<List<InvoiceWithLines>>

    @Transaction
    @Query("SELECT * FROM invoices ORDER BY date DESC")
    suspend fun getAllInvoicesWithLines(): List<InvoiceWithLines>

    @Transaction
    @Query("SELECT * FROM invoices WHERE invoiceId = :invoiceId LIMIT 1")
    suspend fun getInvoiceWithLinesById(invoiceId: String): InvoiceWithLines?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLines(lines: List<InvoiceLineEntity>)

    @Transaction
    suspend fun insertFullInvoice(invoice: InvoiceEntity, lines: List<InvoiceLineEntity>) {
        insertInvoice(invoice)
        insertLines(lines)
    }

    @Query("UPDATE invoices SET syncStatus = :syncStatus WHERE invoiceId = :invoiceId")
    suspend fun updateSyncStatus(invoiceId: String, syncStatus: String)

    @Query("UPDATE invoices SET status = :status, voidedAt = :voidedAt WHERE invoiceId = :invoiceId")
    suspend fun updateVoidStatus(invoiceId: String, status: String, voidedAt: String)

    @Query("UPDATE invoices SET paymentStatus = :paymentStatus, amountPaid = :amountPaid WHERE invoiceId = :invoiceId")
    suspend fun updatePayment(invoiceId: String, paymentStatus: String, amountPaid: BigDecimal)

    @Query("SELECT * FROM invoices WHERE syncStatus = :syncStatus ORDER BY createdAtTimestamp ASC")
    suspend fun getInvoicesBySyncStatus(syncStatus: String): List<InvoiceEntity>

    @Query("DELETE FROM invoice_lines WHERE invoiceId = :invoiceId")
    suspend fun deleteLinesForInvoice(invoiceId: String)

    @Query("DELETE FROM invoices WHERE invoiceId = :invoiceId")
    suspend fun deleteInvoice(invoiceId: String)

    @Query("DELETE FROM invoices")
    suspend fun clearAllInvoices()

    @Query("DELETE FROM invoice_lines")
    suspend fun clearAllLines()
}

@Dao
interface StockAdjustmentDao {
    @Query("SELECT * FROM stock_adjustments ORDER BY date DESC")
    fun getAllAdjustmentsFlow(): Flow<List<StockAdjustmentEntity>>

    @Query("SELECT * FROM stock_adjustments ORDER BY date DESC")
    suspend fun getAllAdjustments(): List<StockAdjustmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(adjustment: StockAdjustmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(adjustments: List<StockAdjustmentEntity>)

    @Query("DELETE FROM stock_adjustments")
    suspend fun clearAll()
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE status = :status ORDER BY timestamp ASC")
    suspend fun getPendingItems(status: String = SyncQueueEntity.STATUS_PENDING): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = :status")
    fun getPendingCountFlow(status: String = SyncQueueEntity.STATUS_PENDING): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueEntity): Long

    @Update
    suspend fun update(item: SyncQueueEntity)

    @Query("UPDATE sync_queue SET status = :status, lastError = :error, retryCount = retryCount + 1 WHERE queueId = :queueId")
    suspend fun markFailed(queueId: Long, error: String?, status: String = SyncQueueEntity.STATUS_FAILED)

    @Query("DELETE FROM sync_queue WHERE queueId = :queueId")
    suspend fun deleteById(queueId: Long)

    @Query("DELETE FROM sync_queue WHERE status = :status")
    suspend fun clearCompleted(status: String = SyncQueueEntity.STATUS_COMPLETED)

    @Query("DELETE FROM sync_queue")
    suspend fun clearAll()
}

@Dao
interface StockLedgerDao {
    @Query("SELECT * FROM stock_ledger ORDER BY timestamp DESC")
    fun getAllLedgerEntriesFlow(): Flow<List<StockLedgerEntity>>

    @Query("SELECT * FROM stock_ledger WHERE productId = :productId ORDER BY timestamp DESC")
    suspend fun getLedgerForProduct(productId: String): List<StockLedgerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: StockLedgerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<StockLedgerEntity>)

    @Query("DELETE FROM stock_ledger")
    suspend fun clearAll()
}

@Dao
interface DeviceStockAllowanceDao {
    @Query("SELECT * FROM device_stock_allowances WHERE deviceId = :deviceId AND vanId = :vanId")
    fun getAllowancesForDeviceFlow(deviceId: String, vanId: String): Flow<List<DeviceStockAllowanceEntity>>

    @Query("SELECT * FROM device_stock_allowances WHERE deviceId = :deviceId AND productId = :productId LIMIT 1")
    suspend fun getAllowanceForProduct(deviceId: String, productId: String): DeviceStockAllowanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(allowance: DeviceStockAllowanceEntity)

    @Query("DELETE FROM device_stock_allowances WHERE expiresAtTimestamp < :nowTimestamp")
    suspend fun purgeExpiredAllowances(nowTimestamp: Long = System.currentTimeMillis())
}

