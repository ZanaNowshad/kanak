package com.example.kanak.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.example.kanak.domain.models.User
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Authenticated(val user: User, val idToken: String) : AuthState()
    data class Error(val message: String) : AuthState()
    data class AccessDenied(val reason: String = "Your account is not authorized to access Kanak.") : AuthState()
    object Unauthorized : AuthState()
}

/**
 * Google Authentication Manager using modern Android Credential Manager with Google Sign-In and Firebase Auth.
 * Supports Google account sign-in, token retrieval, session restoration, and demo sign-in for zero-setup local operation.
 */
class GoogleAuthManager(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)
    private val prefs = context.getSharedPreferences("kanak_auth_prefs", Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val savedUserId = prefs.getString("user_id", null)
        val savedEmail = prefs.getString("user_email", null)
        val savedRole = prefs.getString("user_role", User.ROLE_ADMIN) ?: User.ROLE_ADMIN
        val savedToken = prefs.getString("id_token", "default_valid_token") ?: "default_valid_token"

        if (savedUserId != null && savedEmail != null) {
            _authState.value = AuthState.Authenticated(
                user = User(
                    userId = savedUserId,
                    email = savedEmail,
                    role = savedRole,
                    active = true
                ),
                idToken = savedToken
            )
        }
    }

    /**
     * Signs in using Android Credential Manager with Google Sign-In Option and links with Firebase Auth.
     */
    suspend fun signInWithGoogle(serverClientId: String? = null): AuthState = withContext(Dispatchers.IO) {
        _authState.value = AuthState.Loading

        val clientId = serverClientId ?: try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) context.getString(resId) else ""
        } catch (e: Exception) {
            ""
        }

        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        try {
            val result: GetCredentialResponse = credentialManager.getCredential(
                request = request,
                context = context
            )
            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleIdTokenCredential.id
                val idToken = googleIdTokenCredential.idToken
                val sub = googleIdTokenCredential.id.hashCode().toString() // subject identifier

                // Authenticate with Firebase Auth
                try {
                    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                    FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
                } catch (fe: Exception) {
                    android.util.Log.w("GoogleAuthManager", "Firebase Auth sign-in non-blocking: ${fe.message}")
                }

                val user = User(
                    userId = sub,
                    email = email,
                    role = User.ROLE_ADMIN, // Default to admin for initial device operator
                    active = true
                )

                persistSession(user, idToken)
                val state = AuthState.Authenticated(user, idToken)
                _authState.value = state
                return@withContext state
            } else {
                val err = AuthState.Error("Unsupported credential type returned")
                _authState.value = err
                return@withContext err
            }
        } catch (e: GetCredentialCancellationException) {
            android.util.Log.w("GoogleAuthManager", "Google Sign-In was cancelled: ${e.message}")
            val state = AuthState.Idle
            _authState.value = state
            return@withContext state
        } catch (e: GetCredentialException) {
            val errState = AuthState.Error("Google Sign-In failed: ${e.message ?: "Authentication error"}")
            _authState.value = errState
            return@withContext errState
        } catch (e: Exception) {
            val errState = AuthState.Error("Sign-In failed: ${e.message ?: "Connection error"}")
            _authState.value = errState
            return@withContext errState
        }
    }

    /**
     * Provides a fast, secure role-switchable demo account for testing van sales operations.
     */
    fun signInAsDemo(asAdmin: Boolean = true): AuthState {
        val user = User(
            userId = if (asAdmin) "usr_admin_bahrain_001" else "usr_sales_operator_002",
            email = if (asAdmin) "admin@kanak-fmcg.bh" else "driver.ali@kanak-fmcg.bh",
            role = if (asAdmin) User.ROLE_ADMIN else User.ROLE_STANDARD,
            active = true
        )
        val token = "demo_token_${user.userId}"
        persistSession(user, token)
        val state = AuthState.Authenticated(user, token)
        _authState.value = state
        return state
    }

    fun signOut() {
        prefs.edit().clear().apply()
        _authState.value = AuthState.Idle
    }

    private fun persistSession(user: User, token: String) {
        prefs.edit()
            .putString("user_id", user.userId)
            .putString("user_email", user.email)
            .putString("user_role", user.role)
            .putString("id_token", token)
            .apply()
    }
}
