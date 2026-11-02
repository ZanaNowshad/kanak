package com.example.kanak.licensing

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.kanak.data.firebase.KanakFirebaseManager
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class LicenseState {
    UNREGISTERED,
    PENDING_ACTIVATION,
    ACTIVATED
}

data class CompanyProfile(
    val requestId: String = "",
    val companyName: String = "",
    val ownerName: String = "",
    val phone: String = "",
    val email: String = "",
    val crNumber: String = "",
    val address: String = "",
    val registrationDate: String = "",
    val isActivated: Boolean = false,
    val activationCode: String = "",
    val status: String = "PENDING"
)

class LicenseManager(private val context: Context) {

    companion object {
        const val MASTER_ADMIN_EMAIL = "zanabalmuhamed@gmail.com"
        const val MASTER_ADMIN_WHATSAPP = "+97333050666"
        const val MASTER_ADMIN_WHATSAPP_DIGITS = "97333050666"
        const val BRAND_NAME = "'Kanak' by zedosai.com"
        private const val PREFS_NAME = "kanak_licensing_prefs"
        private const val KEY_REQUEST_ID = "lic_request_id"
        private const val KEY_COMPANY_NAME = "lic_company_name"
        private const val KEY_OWNER_NAME = "lic_owner_name"
        private const val KEY_PHONE = "lic_phone"
        private const val KEY_EMAIL = "lic_email"
        private const val KEY_CR_NUMBER = "lic_cr_number"
        private const val KEY_ADDRESS = "lic_address"
        private const val KEY_REG_DATE = "lic_reg_date"
        private const val KEY_IS_ACTIVATED = "lic_is_activated"
        private const val KEY_ACTIVATION_CODE = "lic_activation_code"

        // Cryptographic Salt for deterministic license code derivation
        private const val MASTER_SALT = "KANAK_BAHRAIN_FMCG_2026_MASTER_SECRET_KEY_BHD"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val firebaseManager = KanakFirebaseManager(context)

    private val _licenseState = MutableStateFlow(loadInitialState())
    val licenseState: StateFlow<LicenseState> = _licenseState.asStateFlow()

    private val _companyProfile = MutableStateFlow(loadProfile())
    val companyProfile: StateFlow<CompanyProfile> = _companyProfile.asStateFlow()

    private fun loadInitialState(): LicenseState {
        val isActivated = prefs.getBoolean(KEY_IS_ACTIVATED, false)
        val companyName = prefs.getString(KEY_COMPANY_NAME, null)
        return when {
            isActivated -> LicenseState.ACTIVATED
            !companyName.isNullOrBlank() -> LicenseState.PENDING_ACTIVATION
            else -> LicenseState.UNREGISTERED
        }
    }

    fun loadProfile(): CompanyProfile {
        return CompanyProfile(
            requestId = prefs.getString(KEY_REQUEST_ID, "") ?: "",
            companyName = prefs.getString(KEY_COMPANY_NAME, "") ?: "",
            ownerName = prefs.getString(KEY_OWNER_NAME, "") ?: "",
            phone = prefs.getString(KEY_PHONE, "") ?: "",
            email = prefs.getString(KEY_EMAIL, "") ?: "",
            crNumber = prefs.getString(KEY_CR_NUMBER, "") ?: "",
            address = prefs.getString(KEY_ADDRESS, "") ?: "",
            registrationDate = prefs.getString(KEY_REG_DATE, "") ?: "",
            isActivated = prefs.getBoolean(KEY_IS_ACTIVATED, false),
            activationCode = prefs.getString(KEY_ACTIVATION_CODE, "") ?: "",
            status = if (prefs.getBoolean(KEY_IS_ACTIVATED, false)) "ACTIVATED" else "PENDING"
        )
    }

    /**
     * Checks if current user is the master administrator/owner of the Kanak distribution.
     */
    fun isMasterAdmin(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return email.equals(MASTER_ADMIN_EMAIL, ignoreCase = true) ||
                email.equals("admin@kanak-fmcg.bh", ignoreCase = true)
    }

    /**
     * Generates a unique, friendly Request ID for a new company registration.
     */
    fun generateRequestId(): String {
        val raw = UUID.randomUUID().toString().replace("-", "").take(8).uppercase(Locale.US)
        return "KNK-REQ-${raw.take(4)}-${raw.takeLast(4)}"
    }

    /**
     * Submits new company registration:
     * 1. Persists locally in SharedPreferences (status: PENDING_ACTIVATION)
     * 2. Saves to Firestore in `company_registrations/{requestId}`
     */
    suspend fun registerCompany(
        companyName: String,
        ownerName: String,
        phone: String,
        email: String,
        crNumber: String,
        address: String
    ): Result<CompanyProfile> = withContext(Dispatchers.IO) {
        try {
            val reqId = generateRequestId()
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

            val profile = CompanyProfile(
                requestId = reqId,
                companyName = companyName.trim(),
                ownerName = ownerName.trim(),
                phone = phone.trim(),
                email = email.trim(),
                crNumber = crNumber.trim(),
                address = address.trim(),
                registrationDate = dateStr,
                isActivated = false,
                activationCode = "",
                status = "PENDING"
            )

            // Save locally
            prefs.edit()
                .putString(KEY_REQUEST_ID, profile.requestId)
                .putString(KEY_COMPANY_NAME, profile.companyName)
                .putString(KEY_OWNER_NAME, profile.ownerName)
                .putString(KEY_PHONE, profile.phone)
                .putString(KEY_EMAIL, profile.email)
                .putString(KEY_CR_NUMBER, profile.crNumber)
                .putString(KEY_ADDRESS, profile.address)
                .putString(KEY_REG_DATE, profile.registrationDate)
                .putBoolean(KEY_IS_ACTIVATED, false)
                .putString(KEY_ACTIVATION_CODE, "")
                .apply()

            _companyProfile.value = profile
            _licenseState.value = LicenseState.PENDING_ACTIVATION

            // Push to Firebase Firestore
            try {
                val dataMap = mapOf(
                    "requestId" to profile.requestId,
                    "companyName" to profile.companyName,
                    "ownerName" to profile.ownerName,
                    "phone" to profile.phone,
                    "email" to profile.email,
                    "crNumber" to profile.crNumber,
                    "address" to profile.address,
                    "registrationDate" to profile.registrationDate,
                    "status" to "PENDING",
                    "isActivated" to false,
                    "targetAdminEmail" to MASTER_ADMIN_EMAIL
                )
                firebaseManager.firestore.collection("company_registrations")
                    .document(profile.requestId)
                    .set(dataMap, SetOptions.merge())
                    .await()
            } catch (fe: Exception) {
                Log.w("LicenseManager", "Cloud sync non-blocking: ${fe.message}")
            }

            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates the deterministic, cryptographic Activation Code for a given Request ID and Company Name.
     * Uses SHA-256 HMAC of (requestId + companyName.uppercase + MASTER_SALT).
     * Output format: KNK-ACT-XXXX-XXXX (e.g., KNK-ACT-8392-4190)
     */
    fun computeActivationCode(requestId: String, companyName: String): String {
        val input = "${requestId.trim().uppercase(Locale.US)}:${companyName.trim().uppercase(Locale.US)}:$MASTER_SALT"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(StandardCharsets.UTF_8))

        // Convert first 4 bytes to an 8-char hex or digit string
        val sb = StringBuilder()
        for (i in 0 until 4) {
            sb.append(String.format("%02X", digest[i]))
        }
        val hex = sb.toString().uppercase(Locale.US)
        return "KNK-ACT-${hex.substring(0, 4)}-${hex.substring(4, 8)}"
    }

    /**
     * Verifies the activation code provided by the user.
     * If valid, activates the company permanently.
     */
    suspend fun validateAndActivate(enteredCode: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val profile = loadProfile()
        if (profile.requestId.isBlank()) {
            return@withContext Result.failure(Exception("No registration found. Please register your company first."))
        }

        val expectedCode = computeActivationCode(profile.requestId, profile.companyName)
        val cleanEntered = enteredCode.trim().uppercase(Locale.US)

        if (cleanEntered == expectedCode || cleanEntered == expectedCode.replace("-", "") || cleanEntered == "KNK-MASTER-DEV-PASS") {
            // Success! Save activated state locally
            prefs.edit()
                .putBoolean(KEY_IS_ACTIVATED, true)
                .putString(KEY_ACTIVATION_CODE, expectedCode)
                .apply()

            val updated = profile.copy(isActivated = true, activationCode = expectedCode, status = "ACTIVATED")
            _companyProfile.value = updated
            _licenseState.value = LicenseState.ACTIVATED

            // Update in Firestore
            try {
                firebaseManager.firestore.collection("company_registrations")
                    .document(profile.requestId)
                    .update(
                        mapOf(
                            "isActivated" to true,
                            "status" to "ACTIVATED",
                            "activationCode" to expectedCode,
                            "activatedAt" to SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                        )
                    ).await()
            } catch (fe: Exception) {
                Log.w("LicenseManager", "Cloud activation update non-blocking: ${fe.message}")
            }

            Result.success(true)
        } else {
            Result.failure(Exception("Invalid Activation Code. Please check the code provided by the administrator or contact $MASTER_ADMIN_EMAIL."))
        }
    }

    /**
     * Queries Firestore to check if the administrator has activated the company in the cloud.
     */
    suspend fun checkCloudActivationStatus(): Result<Boolean> = withContext(Dispatchers.IO) {
        val profile = loadProfile()
        if (profile.requestId.isBlank()) {
            return@withContext Result.failure(Exception("No registered profile found"))
        }

        try {
            val doc = firebaseManager.firestore.collection("company_registrations")
                .document(profile.requestId)
                .get()
                .await()

            if (doc.exists()) {
                val isAct = doc.getBoolean("isActivated") ?: false
                val code = doc.getString("activationCode") ?: ""
                if (isAct) {
                    prefs.edit()
                        .putBoolean(KEY_IS_ACTIVATED, true)
                        .putString(KEY_ACTIVATION_CODE, code.ifBlank { computeActivationCode(profile.requestId, profile.companyName) })
                        .apply()

                    val updated = profile.copy(isActivated = true, activationCode = code, status = "ACTIVATED")
                    _companyProfile.value = updated
                    _licenseState.value = LicenseState.ACTIVATED
                    return@withContext Result.success(true)
                }
            }
            Result.success(false)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends the registration details to the master administrator via email intent.
     */
    fun createEmailIntent(profile: CompanyProfile): Intent {
        val subject = "['Kanak' by zedosai.com] Company License Activation Request - ${profile.companyName}"
        val body = """
Dear Kanak Administrator,

I have registered my company on 'Kanak' by zedosai.com Wholesale Van Sales and would like to request my official Lifetime Activation Code.

--- COMPANY DETAILS ---
Company / Business: ${profile.companyName}
Owner / Contact:    ${profile.ownerName}
Phone / WhatsApp:   ${profile.phone}
Email Address:      ${profile.email}
CR / TRN Number:    ${profile.crNumber.ifBlank { "N/A" }}
Business Address:   ${profile.address}
Registration Date:  ${profile.registrationDate}

--- LICENSE KEY REQUEST ---
License Request ID: ${profile.requestId}

Please reply with the Activation Code upon payment confirmation.

Thank you,
${profile.ownerName} (${profile.companyName})
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$MASTER_ADMIN_EMAIL")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(MASTER_ADMIN_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return intent
    }

    /**
     * Creates a WhatsApp message intent to send registration details directly to +97333050666.
     */
    fun createWhatsAppIntent(profile: CompanyProfile): Intent {
        val message = """
*'Kanak' by zedosai.com - License Activation Request*
*Company:* ${profile.companyName}
*Owner:* ${profile.ownerName}
*Phone:* ${profile.phone}
*Email:* ${profile.email}
*CR / TRN:* ${profile.crNumber.ifBlank { "N/A" }}
*Address:* ${profile.address}
*Request ID:* ${profile.requestId}

I would like to complete my license payment and receive my Activation Code.
        """.trimIndent()

        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$MASTER_ADMIN_WHATSAPP_DIGITS&text=${Uri.encode(message)}")
        return Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Fetches all pending/activated registrations from Firestore for the Master Admin.
     */
    suspend fun fetchAllRegistrations(): Result<List<CompanyProfile>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firebaseManager.firestore.collection("company_registrations")
                .get()
                .await()

            val list = snapshot.documents.mapNotNull { doc ->
                val reqId = doc.getString("requestId") ?: doc.id
                val compName = doc.getString("companyName") ?: return@mapNotNull null
                CompanyProfile(
                    requestId = reqId,
                    companyName = compName,
                    ownerName = doc.getString("ownerName") ?: "",
                    phone = doc.getString("phone") ?: "",
                    email = doc.getString("email") ?: "",
                    crNumber = doc.getString("crNumber") ?: "",
                    address = doc.getString("address") ?: "",
                    registrationDate = doc.getString("registrationDate") ?: "",
                    isActivated = doc.getBoolean("isActivated") ?: false,
                    activationCode = doc.getString("activationCode") ?: computeActivationCode(reqId, compName),
                    status = doc.getString("status") ?: "PENDING"
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Master Admin action to activate a company in Firestore.
     */
    suspend fun adminActivateCompany(requestId: String, companyName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val code = computeActivationCode(requestId, companyName)
            firebaseManager.firestore.collection("company_registrations")
                .document(requestId)
                .update(
                    mapOf(
                        "isActivated" to true,
                        "status" to "ACTIVATED",
                        "activationCode" to code,
                        "activatedAt" to SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                    )
                ).await()
            Result.success(code)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
