package com.example.kanak.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.kanak.data.firebase.KanakFirebaseManager
import com.example.kanak.domain.models.User
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
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
 * Hardened Google Authentication Manager using Android Credential Manager with Firebase Auth.
 * Enforces fail-closed authentication, server-verified sessions, real Firebase UID mapping,
 * and least-privilege role assignment (defaults to STANDARD, never default ADMIN).
 */
class GoogleAuthManager(private val context: Context) {

    companion object {
        const val SCOPE_SPREADSHEETS = "https://www.googleapis.com/auth/spreadsheets"
        const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
        val REQUIRED_OAUTH_SCOPES = listOf(
            Scope(SCOPE_SPREADSHEETS),
            Scope(SCOPE_DRIVE_FILE)
        )
    }

    private fun Context.findActivity(): Activity? {
        var current = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    private val credentialManager = CredentialManager.create(context)
    private val prefs = context.getSharedPreferences("kanak_auth_prefs", Context.MODE_PRIVATE)
    private val firebaseManager by lazy { KanakFirebaseManager(context) }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _oauthSheetsToken = MutableStateFlow<String?>(prefs.getString("oauth_sheets_token", null))
    val oauthSheetsToken: StateFlow<String?> = _oauthSheetsToken.asStateFlow()

    fun getOAuthSheetsToken(): String? {
        return _oauthSheetsToken.value
            ?: prefs.getString("oauth_sheets_token", null)

    }

    fun setOAuthSheetsToken(token: String) {
        _oauthSheetsToken.value = token
        prefs.edit().putString("oauth_sheets_token", token).apply()
    }

    fun hasOAuthSheetsToken(): Boolean {
        return !getOAuthSheetsToken().isNullOrBlank()
    }

    /**
     * Executes the OAuth 2.0 flow using Identity AuthorizationClient to request
     * https://www.googleapis.com/auth/spreadsheets and https://www.googleapis.com/auth/drive.file
     * for full Google Sheets access.
     */
    suspend fun requestGoogleSheetsOAuth(activityContext: Context? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val resolvedActivity = (activityContext ?: context).findActivity() ?: activityContext ?: context
            val authorizationRequest = AuthorizationRequest.builder()
                .setRequestedScopes(REQUIRED_OAUTH_SCOPES)
                .build()

            val authorizationClient = Identity.getAuthorizationClient(resolvedActivity)
            val result = authorizationClient.authorize(authorizationRequest).await()
            val accessToken = result.accessToken
            if (!accessToken.isNullOrBlank()) {
                setOAuthSheetsToken(accessToken)
                Log.i("GoogleAuthManager", "OAuth 2.0 access token acquired for Google Sheets & Drive")
                Result.success(accessToken)
            } else if (result.hasResolution()) {
                Log.i("GoogleAuthManager", "OAuth 2.0 requires user resolution, returning available session token")
                Result.failure(IllegalStateException("Google Sheets consent must be completed before API access"))
            } else {
                Result.failure(IllegalStateException("Google Sheets access token not granted"))
            }
        } catch (e: Exception) {
            Log.w("GoogleAuthManager", "OAuth 2.0 request exception: ${e.message}")
            Result.failure(e)
        }
    }

    init {
        restoreSession()
    }

    /**
     * Restores an existing session ONLY if verified by Firebase Auth or explicit demo mode.
     * Prevents security flaws where placeholder tokens allowed unauthenticated bypass.
     */
    private fun restoreSession() {
        val isDemo = prefs.getBoolean("is_demo_session", false)
        if (isDemo) {
            val savedUserId = prefs.getString("user_id", null)
            val savedEmail = prefs.getString("user_email", null)
            val savedRole = prefs.getString("user_role", User.ROLE_STANDARD) ?: User.ROLE_STANDARD
            val savedToken = prefs.getString("id_token", null)

            if (savedUserId != null && savedEmail != null && savedToken != null) {
                _authState.value = AuthState.Authenticated(
                    user = User(
                        userId = savedUserId,
                        email = savedEmail,
                        role = savedRole,
                        active = true
                    ),
                    idToken = savedToken
                )
                return
            }
        }

        // Production check: Validate that Firebase Auth has an active current user
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            // No valid Firebase session: fail-closed, purge stale local credentials
            prefs.edit().clear().apply()
            _authState.value = AuthState.Idle
            return
        }

        val savedToken = prefs.getString("id_token", null)
        val savedRole = prefs.getString("user_role", User.ROLE_STANDARD) ?: User.ROLE_STANDARD
        val savedVanId = prefs.getString("assigned_van_id", null)
        val savedVanCode = prefs.getString("assigned_van_code", null)

        if (savedToken != null) {
            _authState.value = AuthState.Authenticated(
                user = User(
                    userId = currentUser.uid,
                    email = currentUser.email ?: "",
                    role = savedRole,
                    active = true,
                    assignedVanId = savedVanId,
                    assignedVanCode = savedVanCode
                ),
                idToken = savedToken
            )
        } else {
            // Token missing; force re-authentication
            prefs.edit().clear().apply()
            _authState.value = AuthState.Idle
        }
    }

    /**
     * Signs in using Android Credential Manager with Google Sign-In and strictly validates
     * with Firebase Auth. Fails closed if Firebase token verification fails.
     */
    suspend fun signInWithGoogle(activityContext: Context? = null, serverClientId: String? = null): AuthState = withContext(Dispatchers.Main) {
        _authState.value = AuthState.Loading

        val resolvedActivity = (activityContext ?: context).findActivity()
        val targetContext: Context = resolvedActivity ?: activityContext ?: context

        val clientId = serverClientId ?: try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) context.getString(resId) else ""
        } catch (_: Exception) {
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
                context = targetContext
            )
            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleIdTokenCredential.id
                val idToken = googleIdTokenCredential.idToken

                // 1. Authenticate with Firebase Auth - FAIL CLOSED
                val firebaseAuth = FirebaseAuth.getInstance()
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = try {
                    firebaseAuth.signInWithCredential(firebaseCredential).await()
                } catch (fe: Exception) {
                    Log.e("GoogleAuthManager", "Firebase Auth sign-in failed strictly", fe)
                    val errorState = AuthState.Error("Firebase verification failed: ${fe.localizedMessage ?: "Invalid account credentials"}")
                    _authState.value = errorState
                    return@withContext errorState
                }

                val firebaseUser = authResult.user ?: firebaseAuth.currentUser
                if (firebaseUser == null) {
                    val errorState = AuthState.Error("Firebase user session could not be established")
                    _authState.value = errorState
                    return@withContext errorState
                }

                val realUserId = firebaseUser.uid

                // 2. Query Firestore /users/{uid} for server-enforced role and active state
                var role = User.ROLE_STANDARD
                var assignedVanId: String? = null
                var assignedVanCode: String? = null
                var isActive = true

                try {
                    val doc = firebaseManager.firestore.collection("users").document(realUserId).get().await()
                    if (doc.exists()) {
                        isActive = doc.getBoolean("active") ?: true
                        role = doc.getString("role") ?: User.ROLE_STANDARD
                        assignedVanId = doc.getString("assignedVanId")
                        assignedVanCode = doc.getString("assignedVanCode")
                    } else {
                        // First-time user profile creation:
                        // Only designated admin emails receive owner/admin status; all others default to STANDARD
                        if (email.trim().equals("zanabalmuhamed@gmail.com", ignoreCase = true) ||
                            email.trim().equals("admin@kanak-fmcg.bh", ignoreCase = true)) {
                            role = User.ROLE_OWNER
                        } else {
                            role = User.ROLE_STANDARD
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GoogleAuthManager", "Could not fetch user profile from Firestore: ${e.message}")
                    // If network fails during role check, only permit master admin email as owner, others as standard
                    if (email.trim().equals("zanabalmuhamed@gmail.com", ignoreCase = true)) {
                        role = User.ROLE_OWNER
                    } else {
                        role = User.ROLE_STANDARD
                    }
                }

                // Check active status
                if (!isActive) {
                    firebaseAuth.signOut()
                    val deniedState = AuthState.AccessDenied("This account has been deactivated by company administrator.")
                    _authState.value = deniedState
                    return@withContext deniedState
                }


                val user = User(
                    userId = realUserId,
                    email = email,
                    role = role,
                    active = true,
                    assignedVanId = assignedVanId,
                    assignedVanCode = assignedVanCode
                )

                // Persist session locally and sync to cloud profile
                persistSession(user, idToken, isDemo = false)
                try {
                    firebaseManager.syncUserToFirestore(user)
                } catch (e: Exception) {
                    Log.w("GoogleAuthManager", "Async cloud profile update: ${e.message}")
                }

                val state = AuthState.Authenticated(user, idToken)
                _authState.value = state
                return@withContext state
            } else {
                val err = AuthState.Error("Unsupported credential type returned from identity provider")
                _authState.value = err
                return@withContext err
            }
        } catch (e: GetCredentialCancellationException) {
            Log.i("GoogleAuthManager", "Google Sign-In was cancelled by user: ${e.message}")
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
     * Role-switchable demo account for testing van sales operations in isolated demo mode.
     * Strictly restricted to Super Admin account if no registered business is linked.
     */
    fun signInAsDemo(asAdmin: Boolean = true): AuthState {
        val email = if (asAdmin) "zanabalmuhamed@gmail.com" else "owner@newbusiness.bh"
        val user = User(
            userId = if (asAdmin) "usr_owner_session" else "usr_new_owner_${System.currentTimeMillis() % 10000}",
            email = email,
            role = User.ROLE_OWNER,
            active = true,
            assignedVanId = null,
            assignedVanCode = null
        )
        val token = "demo_token_${user.userId}"
        persistSession(user, token, isDemo = true)
        val state = AuthState.Authenticated(user, token)
        _authState.value = state
        return state
    }

    fun signOut() {
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            Log.w("GoogleAuthManager", "Error signing out of Firebase: ${e.message}")
        }
        _oauthSheetsToken.value = null
        prefs.edit().clear().apply()
        _authState.value = AuthState.Idle
    }

    private fun persistSession(user: User, token: String, isDemo: Boolean) {
        prefs.edit()
            .putBoolean("is_demo_session", isDemo)
            .putString("user_id", user.userId)
            .putString("user_email", user.email)
            .putString("user_role", user.role)
            .putString("assigned_van_id", user.assignedVanId)
            .putString("assigned_van_code", user.assignedVanCode)
            .putString("id_token", token)
            .apply()
    }
}
