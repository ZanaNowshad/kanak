package com.example.kanak.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kanak.auth.AuthState
import com.example.kanak.auth.GoogleAuthManager
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/** Google Sign-In only; first-time business setup is shown after authentication. */
@Composable
fun AuthScreen(
    authManager: GoogleAuthManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by authManager.authState.collectAsState()
    val scope = rememberCoroutineScope()
    Surface(color = KanakBackground, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.AccountCircle, contentDescription = null,
                tint = KanakPrimaryBlue, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(16.dp))
            Text("KANAK", style = MaterialTheme.typography.headlineLarge,
                color = KanakPrimaryBlue, fontWeight = FontWeight.Bold)
            Text("Sign in to manage your business", color = KanakSecondaryText)
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { scope.launch { authManager.signInWithGoogle(context) } },
                enabled = state !is AuthState.Loading,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("google_sign_in_button"),
                colors = ButtonDefaults.buttonColors(containerColor = KanakPrimaryBlue)
            ) {
                if (state is AuthState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp),
                        color = Color.White, strokeWidth = 2.dp)
                } else Text("Continue with Google", fontWeight = FontWeight.SemiBold)
            }
            when (val current = state) {
                is AuthState.Error -> Text(current.message,
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
                is AuthState.AccessDenied -> Text(current.reason,
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
                else -> Unit
            }
        }
    }
}
