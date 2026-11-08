package com.example.kanak.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.kanak.domain.models.*
import java.math.BigDecimal

@Entity(
    tableName = "products",
    indices = [Index(value = ["barcode"], unique = false)]
)
data class ProductEntity(
    @PrimaryKey val productId: String,
    val barcode: String,
    val name: String,
    val costPrice: BigDecimal,
    val sellingPrice: BigDecimal,
    val vatRate: BigDecimal,
    val vatAmount: BigDecimal,
    val photoUrl: String?,
    val stockQty: BigDecimal
) {
    fun toDomain(): Product = Product(
        productId = productId,
        barcode = barcode,
        name = name,
        costPrice = costPrice,
        sellingPrice = sellingPrice,
        vatRate = vatRate,
        vatAmount = vatAmount,
        photoUrl = photoUrl,
        stockQty = stockQty
    )

    companion object {
        fun fromDomain(p: Product): ProductEntity = ProductEntity(
            productId = p.productId,
            barcode = p.barcode,
            name = p.name,
            costPrice = p.costPrice,
            sellingPrice = p.sellingPrice,
            vatRate = p.vatRate,
            vatAmount = p.vatAmount,
            photoUrl = p.photoUrl,
            stockQty = p.stockQty
        )
    }
}

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val customerId: String,
    val businessName: String,
    val contact: String,
    val address: String,
    val phone: String
) {
    fun toDomain(): Customer = Customer(
        customerId = customerId,
        businessName = businessName,
        contact = contact,
        address = address,
        phone = phone
    )

    companion object {
        fun fromDomain(c: Customer): CustomerEntity = CustomerEntity(
            customerId = c.customerId,
            businessName = c.businessName,
            contact = c.contact,
            address = c.address,
            phone = c.phone
        )
    }
}

@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey val supplierId: String,
    val name: String,
    val contact: String
) {
    fun toDomain(): Supplier = Supplier(
        supplierId = supplierId,
        name = name,
        contact = contact
    )

    companion object {
        fun fromDomain(s: Supplier): SupplierEntity = SupplierEntity(
            supplierId = s.supplierId,
            name = s.name,
            contact = s.contact
        )
    }
}

@Entity(
    tableName = "purchases",
    indices = [Index(value = ["productId"]), Index(value = ["supplierId"])]
)
data class PurchaseEntity(
    @PrimaryKey val purchaseId: String,
    val supplierId: String,
    val productId: String,
    val qty: BigDecimal,
    val cost: BigDecimal,
    val date: String,
    val createdBy: String,
    val isReversed: Boolean = false,
    val supplierName: String = "",
    val productName: String = ""
) {
    fun toDomain(): Purchase = Purchase(
        purchaseId = purchaseId,
        supplierId = supplierId,
        productId = productId,
        qty = qty,
        cost = cost,
        date = date,
        createdBy = createdBy,
        isReversed = isReversed,
        supplierName = supplierName,
        productName = productName
    )

    companion object {
        fun fromDomain(p: Purchase): PurchaseEntity = PurchaseEntity(
            purchaseId = p.purchaseId,
            supplierId = p.supplierId,
            productId = p.productId,
            qty = p.qty,
            cost = p.cost,
            date = p.date,
            createdBy = p.createdBy,
            isReversed = p.isReversed,
            supplierName = p.supplierName,
            productName = p.productName
        )
    }
}

@Entity(
    tableName = "invoices",
    indices = [Index(value = ["customerId"]), Index(value = ["date"]), Index(value = ["syncStatus"])]
)
data class InvoiceEntity(
    @PrimaryKey val invoiceId: String,
    val invoiceNumber: String,
    val customerId: String,
    val customerName: String,
    val date: String,
    val subtotal: BigDecimal,
    val vatTotal: BigDecimal,
    val total: BigDecimal,
    val status: String,
    val createdBy: String,
    val voidedAt: String?,
    val syncStatus: String = SYNC_STATUS_PENDING,
    val paymentType: String = Invoice.PAYMENT_CASH,
    val paymentStatus: String = Invoice.PAYMENT_STATUS_PAID,
    val amountPaid: BigDecimal = total,
    val createdAtTimestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val SYNC_STATUS_PENDING = "PENDING"
        const val SYNC_STATUS_SYNCED = "SYNCED"
        const val SYNC_STATUS_FAILED = "FAILED"

        fun fromDomain(inv: Invoice, syncStatus: String = SYNC_STATUS_PENDING): InvoiceEntity = InvoiceEntity(
            invoiceId = inv.invoiceId,
            invoiceNumber = inv.invoiceNumber,
            customerId = inv.customerId,
            customerName = inv.customerName,
            date = inv.date,
            subtotal = inv.subtotal,
            vatTotal = inv.vatTotal,
            total = inv.total,
            status = inv.status,
            createdBy = inv.createdBy,
            voidedAt = inv.voidedAt,
            syncStatus = syncStatus,
            paymentType = inv.paymentType,
            paymentStatus = inv.paymentStatus,
            amountPaid = inv.amountPaid
        )
    }
}

@Entity(
    tableName = "invoice_lines",
    indices = [Index(value = ["invoiceId"]), Index(value = ["productId"])],
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["invoiceId"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class InvoiceLineEntity(
    @PrimaryKey val lineId: String,
    val invoiceId: String,
    val productId: String,
    val productName: String,
    val qty: BigDecimal,
    val unitPrice: BigDecimal,
    val vatRate: BigDecimal,
    val vatAmount: BigDecimal,
    val lineTotal: BigDecimal
) {
    fun toDomain(): InvoiceLine = InvoiceLine(
        lineId = lineId,
        invoiceId = invoiceId,
        productId = productId,
        productName = productName,
        qty = qty,
        unitPrice = unitPrice,
        vatRate = vatRate,
        vatAmount = vatAmount,
        lineTotal = lineTotal
    )

    companion object {
        fun fromDomain(line: InvoiceLine): InvoiceLineEntity = InvoiceLineEntity(
            lineId = line.lineId,
            invoiceId = line.invoiceId,
            productId = line.productId,
            productName = line.productName,
            qty = line.qty,
            unitPrice = line.unitPrice,
            vatRate = line.vatRate,
            vatAmount = line.vatAmount,
            lineTotal = line.lineTotal
        )
    }
}

data class InvoiceWithLines(
    @Embedded val invoice: InvoiceEntity,
    @Relation(
        parentColumn = "invoiceId",
        entityColumn = "invoiceId"
    )
    val lines: List<InvoiceLineEntity>
) {
    fun toDomain(): Invoice = Invoice(
        invoiceId = invoice.invoiceId,
        invoiceNumber = invoice.invoiceNumber,
        customerId = invoice.customerId,
        customerName = invoice.customerName,
        date = invoice.date,
        subtotal = invoice.subtotal,
        vatTotal = invoice.vatTotal,
        total = invoice.total,
        status = invoice.status,
        createdBy = invoice.createdBy,
        voidedAt = invoice.voidedAt,
        lines = lines.map { it.toDomain() },
        paymentType = invoice.paymentType,
        paymentStatus = invoice.paymentStatus,
        amountPaid = invoice.amountPaid,
        syncStatus = invoice.syncStatus
    )
}

@Entity(
    tableName = "stock_adjustments",
    indices = [Index(value = ["productId"])]
)
data class StockAdjustmentEntity(
    @PrimaryKey val adjustmentId: String,
    val productId: String,
    val deltaQty: BigDecimal,
    val reason: String,
    val date: String,
    val createdBy: String,
    val productName: String = ""
) {
    fun toDomain(): StockAdjustment = StockAdjustment(
        adjustmentId = adjustmentId,
        productId = productId,
        deltaQty = deltaQty,
        reason = reason,
        date = date,
        createdBy = createdBy,
        productName = productName
    )

    companion object {
        fun fromDomain(sa: StockAdjustment): StockAdjustmentEntity = StockAdjustmentEntity(
            adjustmentId = sa.adjustmentId,
            productId = sa.productId,
            deltaQty = sa.deltaQty,
            reason = sa.reason,
            date = sa.date,
            createdBy = sa.createdBy,
            productName = sa.productName
        )
    }
}

@Entity(
    tableName = "sync_queue",
    indices = [Index(value = ["status"]), Index(value = ["timestamp"])]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val queueId: Long = 0,
    val action: String,
    val payloadJson: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = STATUS_PENDING,
    val retryCount: Int = 0,
    val lastError: String? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_PROCESSING = "PROCESSING"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_COMPLETED = "COMPLETED"
    }
}

@Entity(
    tableName = "stock_ledger",
    indices = [Index(value = ["productId"]), Index(value = ["timestamp"])]
)
data class StockLedgerEntity(
    @PrimaryKey val ledgerId: String,
    val productId: String,
    val productName: String,
    val changeQty: BigDecimal,
    val movementType: String, // SALE, PURCHASE, RETURN, ADJUSTMENT, VAN_TRANSFER
    val referenceId: String,  // Invoice ID, Purchase ID, etc.
    val locationId: String = "WAREHOUSE_MAIN",
    val createdBy: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "device_stock_allowances",
    indices = [Index(value = ["deviceId"]), Index(value = ["productId"])]
)
data class DeviceStockAllowanceEntity(
    @PrimaryKey val allowanceId: String,
    val deviceId: String,
    val vanId: String,
    val productId: String,
    val maxOfflineQty: BigDecimal,
    val reservedOfflineQty: BigDecimal,
    val expiresAtTimestamp: Long
)

