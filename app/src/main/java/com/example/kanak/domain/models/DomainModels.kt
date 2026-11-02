package com.example.kanak.domain.models

import java.math.BigDecimal
import java.util.UUID

/**
 * Authoritative Product entity matching Google Sheets 'Products' worksheet.
 */
data class Product(
    val productId: String = UUID.randomUUID().toString(),
    val barcode: String,
    val name: String,
    val costPrice: BigDecimal,
    val sellingPrice: BigDecimal,
    val vatRate: BigDecimal = BigDecimal("0.10"),
    val vatAmount: BigDecimal,
    val photoUrl: String? = null,
    val stockQty: BigDecimal = BigDecimal.ZERO
)

/**
 * Authoritative Customer entity matching Google Sheets 'Customers' worksheet.
 */
data class Customer(
    val customerId: String = UUID.randomUUID().toString(),
    val businessName: String,
    val contact: String = "",
    val address: String = "",
    val phone: String = ""
)

/**
 * Authoritative Supplier entity matching Google Sheets 'Suppliers' worksheet.
 */
data class Supplier(
    val supplierId: String = UUID.randomUUID().toString(),
    val name: String,
    val contact: String = ""
)

/**
 * Authoritative Purchase entity matching Google Sheets 'Purchases' worksheet.
 * Admin-only inventory increase.
 */
data class Purchase(
    val purchaseId: String = UUID.randomUUID().toString(),
    val supplierId: String,
    val productId: String,
    val qty: BigDecimal,
    val cost: BigDecimal,
    val date: String,
    val createdBy: String,
    val supplierName: String = "",
    val productName: String = "",
    val isReversed: Boolean = false
)

/**
 * Authoritative Invoice entity matching Google Sheets 'Invoices' worksheet.
 */
data class Invoice(
    val invoiceId: String = UUID.randomUUID().toString(),
    val invoiceNumber: String,
    val customerId: String,
    val customerName: String,
    val date: String,
    val subtotal: BigDecimal,
    val vatTotal: BigDecimal,
    val total: BigDecimal,
    val status: String = STATUS_POSTED,
    val createdBy: String,
    val voidedAt: String? = null,
    val lines: List<InvoiceLine> = emptyList()
) {
    companion object {
        const val STATUS_POSTED = "POSTED"
        const val STATUS_VOID = "VOID"
    }

    val isVoid: Boolean get() = status.equals(STATUS_VOID, ignoreCase = true)
}

/**
 * Authoritative Invoice Line entity matching Google Sheets 'InvoiceLines' worksheet.
 */
data class InvoiceLine(
    val lineId: String = UUID.randomUUID().toString(),
    val invoiceId: String,
    val productId: String,
    val productName: String,
    val qty: BigDecimal,
    val unitPrice: BigDecimal,
    val vatRate: BigDecimal,
    val vatAmount: BigDecimal,
    val lineTotal: BigDecimal
)

/**
 * Authoritative Stock Adjustment entity matching Google Sheets 'StockAdjustments' worksheet.
 */
data class StockAdjustment(
    val adjustmentId: String = UUID.randomUUID().toString(),
    val productId: String,
    val deltaQty: BigDecimal,
    val reason: String,
    val date: String,
    val createdBy: String,
    val productName: String = ""
)

/**
 * User representation matching Google Sheets 'Users' worksheet.
 */
data class User(
    val userId: String,
    val email: String,
    val role: String = ROLE_STANDARD,
    val active: Boolean = true
) {
    companion object {
        const val ROLE_ADMIN = "admin"
        const val ROLE_STANDARD = "standard"
    }

    val isAdmin: Boolean get() = role.equals(ROLE_ADMIN, ignoreCase = true)

    val displayName: String get() {
        val namePart = email.substringBefore('@').replace('.', ' ')
        return namePart.split(' ').filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            .ifBlank { "Ahmed Hussain" }
    }
}

/**
 * Aggregated metrics for the Home Dashboard.
 */
data class DashboardSummary(
    val todaySalesTotal: BigDecimal = BigDecimal.ZERO,
    val todayInvoiceCount: Int = 0,
    val topSellingProducts: List<TopProductItem> = emptyList()
)

data class TopProductItem(
    val productId: String,
    val productName: String,
    val qtySold: BigDecimal,
    val totalRevenue: BigDecimal
)
