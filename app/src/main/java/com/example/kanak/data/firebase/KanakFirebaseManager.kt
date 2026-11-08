package com.example.kanak.data.firebase

import android.content.Context
import android.util.Log
import com.example.R
import com.example.kanak.domain.financial.BahrainFinancials
import com.example.kanak.domain.models.Invoice
import com.example.kanak.domain.models.InvoiceLine
import com.example.kanak.domain.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.util.UUID

/**
 * Manages Firebase Firestore and Firebase Auth integration for Kanak.
 * Provides cloud backup of invoices, fleet synchronization, and cloud profile persistence.
 * Strict Invariant: Always accesses Firestore via named database ID from firebase_applet_config.xml.
 */
class KanakFirebaseManager(private val context: Context) {

    private val dbId: String by lazy {
        context.getString(R.string.firestore_database_id)
    }

    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(dbId)
    }

    val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    /**
     * Persists or updates the authenticated user profile in the /users/{userId} collection.
     */
    suspend fun syncUserToFirestore(user: User): Result<Unit> {
        return try {
            val userMap = mapOf(
                "userId" to user.userId,
                "email" to user.email,
                "role" to user.role,
                "active" to user.active
            )
            firestore.collection("users")
                .document(user.userId)
                .set(userMap, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("KanakFirebaseManager", "Failed to sync user to Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Backs up a posted or voided van sales invoice to the cloud in /invoices/{invoiceId}.
     */
    suspend fun backupInvoiceToFirestore(invoice: Invoice, userId: String, isSyncedToSheets: Boolean = false): Result<Unit> {
        return try {
            val linesData = invoice.lines.map { line ->
                mapOf(
                    "lineId" to line.lineId,
                    "productId" to line.productId,
                    "productName" to line.productName,
                    "qty" to line.qty.toPlainString(),
                    "unitPrice" to line.unitPrice.toPlainString(),
                    "vatRate" to line.vatRate.toPlainString(),
                    "vatAmount" to line.vatAmount.toPlainString(),
                    "lineTotal" to line.lineTotal.toPlainString()
                )
            }

            val invoiceMap = mutableMapOf<String, Any?>(
                "invoiceId" to invoice.invoiceId,
                "invoiceNumber" to invoice.invoiceNumber,
                "customerId" to invoice.customerId,
                "customerName" to invoice.customerName,
                "date" to invoice.date,
                "subtotal" to invoice.subtotal.toPlainString(),
                "vatTotal" to invoice.vatTotal.toPlainString(),
                "total" to invoice.total.toPlainString(),
                "status" to invoice.status,
                "createdBy" to invoice.createdBy,
                "userId" to userId,
                "vanId" to invoice.vanId,
                "vanCode" to invoice.vanCode,
                "businessId" to invoice.businessId,
                "syncedToSheets" to isSyncedToSheets,
                "paymentType" to invoice.paymentType,
                "paymentStatus" to invoice.paymentStatus,
                "amountPaid" to invoice.amountPaid.toPlainString(),
                "lines" to linesData
            )
            if (invoice.voidedAt != null) {
                invoiceMap["voidedAt"] = invoice.voidedAt
            }

            firestore.collection("invoices")
                .document(invoice.invoiceId)
                .set(invoiceMap, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("KanakFirebaseManager", "Failed to backup invoice to Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Fetches completed sales records (status == POSTED) from Firestore that require
     * synchronization to the authoritative Google Sheets document.
     */
    suspend fun getCompletedSalesForSheetsSync(): Result<List<Invoice>> {
        return try {
            val querySnapshot = firestore.collection("invoices")
                .whereEqualTo("status", Invoice.STATUS_POSTED)
                .get()
                .await()

            val invoices = querySnapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val syncedToSheets = data["syncedToSheets"] as? Boolean ?: false
                if (syncedToSheets) return@mapNotNull null

                val invId = data["invoiceId"] as? String ?: doc.id
                @Suppress("UNCHECKED_CAST")
                val rawLines = (data["lines"] as? List<Map<String, Any?>>) ?: emptyList()
                val lines = rawLines.map { lMap ->
                    InvoiceLine(
                        lineId = lMap["lineId"]?.toString() ?: UUID.randomUUID().toString(),
                        invoiceId = invId,
                        productId = lMap["productId"]?.toString() ?: "",
                        productName = lMap["productName"]?.toString() ?: "",
                        qty = BahrainFinancials.parseBhd(lMap["qty"]?.toString() ?: "0"),
                        unitPrice = BahrainFinancials.parseBhd(lMap["unitPrice"]?.toString() ?: "0"),
                        vatRate = BahrainFinancials.parseBhd(lMap["vatRate"]?.toString() ?: "0.10"),
                        vatAmount = BahrainFinancials.parseBhd(lMap["vatAmount"]?.toString() ?: "0"),
                        lineTotal = BahrainFinancials.parseBhd(lMap["lineTotal"]?.toString() ?: "0")
                    )
                }

                val totalBhd = BahrainFinancials.parseBhd(data["total"]?.toString() ?: "0")
                val paymentType = data["paymentType"] as? String ?: Invoice.PAYMENT_CASH
                val paymentStatus = data["paymentStatus"] as? String ?: if (paymentType == Invoice.PAYMENT_CREDIT) Invoice.PAYMENT_STATUS_UNPAID else Invoice.PAYMENT_STATUS_PAID
                val amountPaid = if (data.containsKey("amountPaid")) {
                    BahrainFinancials.parseBhd(data["amountPaid"]?.toString() ?: "0")
                } else {
                    if (paymentStatus == Invoice.PAYMENT_STATUS_PAID) totalBhd else BigDecimal.ZERO
                }

                Invoice(
                    invoiceId = invId,
                    invoiceNumber = data["invoiceNumber"] as? String ?: "INV",
                    customerId = data["customerId"] as? String ?: "",
                    customerName = data["customerName"] as? String ?: "",
                    date = data["date"] as? String ?: "",
                    subtotal = BahrainFinancials.parseBhd(data["subtotal"]?.toString() ?: "0"),
                    vatTotal = BahrainFinancials.parseBhd(data["vatTotal"]?.toString() ?: "0"),
                    total = totalBhd,
                    status = data["status"] as? String ?: Invoice.STATUS_POSTED,
                    createdBy = data["createdBy"] as? String ?: "",
                    voidedAt = data["voidedAt"] as? String,
                    lines = lines,
                    paymentType = paymentType,
                    paymentStatus = paymentStatus,
                    amountPaid = amountPaid,
                    vanId = data["vanId"] as? String ?: "",
                    vanCode = data["vanCode"] as? String ?: "",
                    businessId = data["businessId"] as? String ?: "",
                    syncStatus = if (data["syncedToSheets"] as? Boolean == true) Invoice.SYNC_STATUS_SYNCED else Invoice.SYNC_STATUS_PENDING
                )
            }
            Result.success(invoices)
        } catch (e: Exception) {
            Log.w("KanakFirebaseManager", "Failed to fetch sales for sync: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Marks an invoice in Firestore as successfully pushed to Google Sheets.
     */
    suspend fun markSaleSyncedToSheets(invoiceId: String): Result<Unit> {
        return try {
            val updateMap = mapOf(
                "syncedToSheets" to true,
                "sheetsSyncedAt" to System.currentTimeMillis().toString()
            )
            firestore.collection("invoices")
                .document(invoiceId)
                .set(updateMap, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("KanakFirebaseManager", "Failed to mark invoice synced to Sheets: ${e.message}")
            Result.failure(e)
        }
    }
}
