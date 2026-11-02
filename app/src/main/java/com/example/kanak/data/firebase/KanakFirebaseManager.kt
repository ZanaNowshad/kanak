package com.example.kanak.data.firebase

import android.content.Context
import android.util.Log
import com.example.R
import com.example.kanak.domain.models.Invoice
import com.example.kanak.domain.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

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
    suspend fun backupInvoiceToFirestore(invoice: Invoice, userId: String): Result<Unit> {
        return try {
            val invoiceMap = mapOf(
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
                "userId" to userId
            )
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
}
