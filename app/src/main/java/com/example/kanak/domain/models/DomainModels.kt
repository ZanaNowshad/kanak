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
    val lines: List<InvoiceLine> = emptyList(),
    val paymentType: String = PAYMENT_CASH,
    val paymentStatus: String = PAYMENT_STATUS_PAID,
    val amountPaid: BigDecimal = total,
    val vanId: String = "",
    val vanCode: String = "",
    val businessId: String = "",
    val syncStatus: String = SYNC_STATUS_PENDING,
    val idempotencyKey: String = invoiceId
) {
    companion object {
        const val STATUS_POSTED = "POSTED"
        const val STATUS_VOID = "VOID"

        const val PAYMENT_CASH = "CASH"
        const val PAYMENT_CREDIT = "CREDIT"

        const val PAYMENT_STATUS_PAID = "PAID"
        const val PAYMENT_STATUS_UNPAID = "UNPAID"
        const val PAYMENT_STATUS_PARTIAL = "PARTIALLY_PAID"

        const val SYNC_STATUS_PENDING = "PENDING"
        const val SYNC_STATUS_SYNCED = "SYNCED"
        const val SYNC_STATUS_FAILED = "FAILED"
        const val SYNC_STATUS_CONFLICT = "CONFLICT"
    }

    val isVoid: Boolean get() = status.equals(STATUS_VOID, ignoreCase = true)
    val isCredit: Boolean get() = paymentType.equals(PAYMENT_CREDIT, ignoreCase = true)
    val isPaid: Boolean get() = paymentStatus.equals(PAYMENT_STATUS_PAID, ignoreCase = true)
    val isSynced: Boolean get() = syncStatus.equals(SYNC_STATUS_SYNCED, ignoreCase = true)
    val isPendingSync: Boolean get() = syncStatus.equals(SYNC_STATUS_PENDING, ignoreCase = true)
    val isSyncConflict: Boolean get() = syncStatus.equals(SYNC_STATUS_CONFLICT, ignoreCase = true)
    val balanceDue: BigDecimal get() = if (isVoid) BigDecimal.ZERO else total.subtract(amountPaid).max(BigDecimal.ZERO)
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
 * Stable Permission Identifiers matching Section 7.1 of Production Spec.
 */
object KanakPermissions {
    const val PRODUCTS_READ = "products.read"
    const val PRODUCTS_CREATE = "products.create"
    const val PRODUCTS_UPDATE = "products.update"
    const val PRODUCTS_DELETE = "products.delete"
    const val PRODUCTS_CHANGE_PRICE = "products.change_price"

    const val CUSTOMERS_READ = "customers.read"
    const val CUSTOMERS_CREATE = "customers.create"
    const val CUSTOMERS_UPDATE = "customers.update"

    const val SUPPLIERS_READ = "suppliers.read"
    const val SUPPLIERS_CREATE = "suppliers.create"
    const val SUPPLIERS_UPDATE = "suppliers.update"

    const val SALES_CREATE = "sales.create"
    const val SALES_READ_OWN_VAN = "sales.read_own_van"
    const val SALES_READ_ALL = "sales.read_all"
    const val SALES_VOID = "sales.void"
    const val SALES_RETURN = "sales.return"

    const val PAYMENTS_RECORD = "payments.record"
    const val PAYMENTS_REFUND = "payments.refund"

    const val INVENTORY_READ_ASSIGNED = "inventory.read_assigned"
    const val INVENTORY_READ_ALL = "inventory.read_all"
    const val INVENTORY_RECEIVE = "inventory.receive"
    const val INVENTORY_TRANSFER = "inventory.transfer"
    const val INVENTORY_ADJUST = "inventory.adjust"

    const val PURCHASES_CREATE = "purchases.create"
    const val PURCHASES_REVERSE = "purchases.reverse"

    const val VANS_READ = "vans.read"
    const val VANS_CREATE = "vans.create"
    const val VANS_UPDATE = "vans.update"
    const val VANS_ASSIGN = "vans.assign"

    const val TEAM_INVITE = "team.invite"
    const val TEAM_REVOKE = "team.revoke"
    const val TEAM_ASSIGN = "team.assign"
    const val TEAM_MANAGE_PERMISSIONS = "team.manage_permissions"

    const val REPORTS_READ_ASSIGNED = "reports.read_assigned"
    const val REPORTS_READ_ALL = "reports.read_all"

    const val SETTINGS_READ = "settings.read"
    const val SETTINGS_UPDATE = "settings.update"

    const val LICENSES_READ = "licenses.read"

    val DEFAULT_SALESPERSON_PERMISSIONS = setOf(
        PRODUCTS_READ,
        CUSTOMERS_READ,
        CUSTOMERS_CREATE,
        CUSTOMERS_UPDATE,
        SALES_CREATE,
        SALES_READ_OWN_VAN,
        PAYMENTS_RECORD,
        INVENTORY_READ_ASSIGNED,
        REPORTS_READ_ASSIGNED
    )

    val DEFAULT_MANAGER_PERMISSIONS = DEFAULT_SALESPERSON_PERMISSIONS + setOf(
        PRODUCTS_CREATE,
        PRODUCTS_UPDATE,
        CUSTOMERS_READ,
        CUSTOMERS_CREATE,
        CUSTOMERS_UPDATE,
        SUPPLIERS_READ,
        SUPPLIERS_CREATE,
        SUPPLIERS_UPDATE,
        SALES_READ_ALL,
        INVENTORY_READ_ALL,
        INVENTORY_TRANSFER,
        INVENTORY_ADJUST,
        PURCHASES_CREATE,
        VANS_READ,
        REPORTS_READ_ALL
    )

    val ALL_PERMISSIONS = DEFAULT_MANAGER_PERMISSIONS + setOf(
        PRODUCTS_CHANGE_PRICE,
        PRODUCTS_DELETE,
        SALES_VOID,
        SALES_RETURN,
        PAYMENTS_REFUND,
        INVENTORY_RECEIVE,
        PURCHASES_REVERSE,
        VANS_CREATE,
        VANS_UPDATE,
        VANS_ASSIGN,
        TEAM_INVITE,
        TEAM_REVOKE,
        TEAM_ASSIGN,
        TEAM_MANAGE_PERMISSIONS,
        SETTINGS_READ,
        SETTINGS_UPDATE,
        LICENSES_READ
    )
}

/**
 * Tenant Business Entity.
 */
data class Business(
    val businessId: String = "biz_001",
    val name: String = "KANAK FMCG Wholesale",
    val crNumber: String = "123456-1",
    val vatNumber: String = "BH200012345600",
    val ownerEmail: String = "zanabalmuhamed@gmail.com",
    val ownerName: String = "Zanabal Mohamed",
    val phone: String = "+973 3305 0666",
    val address: String = "Building 844, Road 2415, Block 324, Manama, Bahrain",
    val status: String = STATUS_ACTIVE,
    val createdAt: String = "2026-11-01T00:00:00Z"
) {
    companion object {
        const val STATUS_PENDING_ACTIVATION = "PENDING_ACTIVATION"
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_SUSPENDED = "SUSPENDED"
    }

    val isActivated: Boolean get() = status.equals(STATUS_ACTIVE, ignoreCase = true)
}

/**
 * Van representation for Fleet Management.
 */
data class Van(
    val vanId: String = UUID.randomUUID().toString(),
    val businessId: String = "biz_001",
    val vanCode: String, // e.g. "VAN-001"
    val vanName: String, // e.g. "Toyota HiAce North"
    val registrationNo: String = "",
    val assignedSalespersonId: String? = null,
    val assignedSalespersonName: String? = null,
    val status: String = STATUS_ACTIVE, // ACTIVE, AVAILABLE, MAINTENANCE
    val notes: String = ""
) {
    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_AVAILABLE = "AVAILABLE"
        const val STATUS_MAINTENANCE = "MAINTENANCE"
    }
}

/**
 * Inventory stock balance allocated to a specific van.
 */
data class VanStock(
    val vanId: String,
    val productId: String,
    val stockQty: BigDecimal = BigDecimal.ZERO
)

/**
 * Atomic stock transfer between warehouse and van, or van to van.
 */
data class StockTransfer(
    val transferId: String = UUID.randomUUID().toString(),
    val businessId: String = "biz_001",
    val sourceType: String, // WAREHOUSE, VAN
    val sourceId: String, // "WAREHOUSE" or vanId
    val sourceName: String = "",
    val destinationVanId: String,
    val destinationVanName: String = "",
    val productId: String,
    val productName: String,
    val qty: BigDecimal,
    val transferredBy: String,
    val timestamp: String,
    val notes: String = ""
)

/**
 * Employee membership in a specific business.
 */
data class EmployeeMembership(
    val membershipId: String = UUID.randomUUID().toString(),
    val businessId: String = "biz_001",
    val businessName: String = "KANAK FMCG Wholesale",
    val userId: String,
    val email: String,
    val displayName: String,
    val role: String = User.ROLE_SALESPERSON,
    val assignedVanId: String? = null,
    val assignedVanCode: String? = null,
    val permissions: Set<String> = KanakPermissions.DEFAULT_SALESPERSON_PERMISSIONS,
    val status: String = "ACTIVE", // ACTIVE, SUSPENDED
    val joinedAt: String = ""
)

/**
 * Invitations sent by business owner to onboard new employees without separate licensing.
 */
data class EmployeeInvitation(
    val invitationId: String = UUID.randomUUID().toString(),
    val businessId: String = "biz_001",
    val businessName: String = "KANAK FMCG Wholesale",
    val recipientEmail: String,
    val role: String = User.ROLE_SALESPERSON,
    val assignedVanId: String? = null,
    val assignedVanCode: String? = null,
    val permissions: Set<String> = KanakPermissions.DEFAULT_SALESPERSON_PERMISSIONS,
    val inviteCode: String, // e.g. "KNK-8294"
    val status: String = STATUS_PENDING, // PENDING, ACCEPTED, REVOKED, EXPIRED
    val createdAt: String,
    val expiresAt: String
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_ACCEPTED = "ACCEPTED"
        const val STATUS_REVOKED = "REVOKED"
        const val STATUS_EXPIRED = "EXPIRED"
    }
}

/**
 * Audit record for company-level and fleet changes.
 */
data class AuditRecord(
    val auditId: String = UUID.randomUUID().toString(),
    val businessId: String = "biz_001",
    val actorUserId: String,
    val actorEmail: String,
    val action: String,
    val targetType: String,
    val targetId: String,
    val details: String,
    val timestamp: String
)

/**
 * User representation matching Google Sheets 'Users' worksheet and business membership.
 */
data class User(
    val userId: String,
    val email: String,
    val role: String = ROLE_STANDARD,
    val active: Boolean = true,
    val businessId: String = "biz_001",
    val businessName: String = "KANAK FMCG Wholesale",
    val assignedVanId: String? = null,
    val assignedVanCode: String? = null,
    val permissions: Set<String> = emptySet(),
    val status: String = "ACTIVE"
) {
    companion object {
        const val ROLE_PLATFORM_ADMIN = "platform_admin"
        const val ROLE_OWNER = "owner"
        const val ROLE_MANAGER = "manager"
        const val ROLE_SALESPERSON = "salesperson"
        const val ROLE_ADMIN = "admin"
        const val ROLE_STANDARD = "standard"
    }

    val isPlatformAdmin: Boolean get() = role.equals(ROLE_PLATFORM_ADMIN, ignoreCase = true) ||
            email.trim().equals("zanabalmuhamed@gmail.com", ignoreCase = true) ||
            email.trim().equals("admin@kanak-fmcg.bh", ignoreCase = true)

    val isOwner: Boolean get() = isPlatformAdmin || role.equals(ROLE_OWNER, ignoreCase = true) || role.equals(ROLE_ADMIN, ignoreCase = true)
    val isManager: Boolean get() = isOwner || role.equals(ROLE_MANAGER, ignoreCase = true)
    val isSalesperson: Boolean get() = role.equals(ROLE_SALESPERSON, ignoreCase = true) || role.equals(ROLE_STANDARD, ignoreCase = true)

    val isAdmin: Boolean get() = isOwner

    fun hasPermission(permission: String): Boolean {
        if (isOwner) return true
        if (permissions.contains(permission)) return true
        return false
    }

    val displayName: String get() {
        if (email.trim().equals("zanabalmuhamed@gmail.com", ignoreCase = true)) {
            return "Zanabal Mohamed"
        }
        val namePart = email.substringBefore('@').replace('.', ' ').replace('_', ' ')
        return namePart.split(' ').filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            .ifBlank { "User" }
    }

    val initials: String get() {
        val parts = displayName.split(" ").filter { it.isNotBlank() }
        return if (parts.size >= 2) {
            "${parts[0].first()}${parts[1].first()}".uppercase()
        } else if (parts.isNotEmpty()) {
            parts[0].take(2).uppercase()
        } else {
            "ZM"
        }
    }
}

/**
 * Aggregated metrics for the Home Dashboard.
 */
data class DashboardSummary(
    val todaySalesTotal: BigDecimal = BigDecimal.ZERO,
    val todayVatTotal: BigDecimal = BigDecimal.ZERO,
    val todayInvoiceCount: Int = 0,
    val activeVansCount: Int = 0,
    val activeSalespeopleCount: Int = 0,
    val topSellingProducts: List<TopProductItem> = emptyList(),
    val vanPerformances: List<VanPerformanceItem> = emptyList()
)

data class VanPerformanceItem(
    val vanId: String,
    val vanCode: String,
    val vanName: String,
    val salespersonName: String,
    val todaySales: BigDecimal,
    val todayInvoicesCount: Int
)

data class TopProductItem(
    val productId: String,
    val productName: String,
    val qtySold: BigDecimal,
    val totalRevenue: BigDecimal
)

/**
 * Daily Sales Summary aggregate data for van route session and VAT compliance.
 */
data class DailySalesSummaryData(
    val sessionDate: String,
    val vanId: String?,
    val vanCode: String?,
    val totalGrossSales: BigDecimal,
    val totalInvoicesCount: Int,
    val totalCashSales: BigDecimal,
    val cashInvoicesCount: Int,
    val totalCreditSales: BigDecimal,
    val creditInvoicesCount: Int,
    val cashCollectedOnCredit: BigDecimal,
    val totalOutstandingCredit: BigDecimal,
    val taxableSubtotal: BigDecimal,
    val vatRate: BigDecimal = BigDecimal("0.10"),
    val vatAmountCollected: BigDecimal,
    val invoices: List<Invoice> = emptyList()
)
