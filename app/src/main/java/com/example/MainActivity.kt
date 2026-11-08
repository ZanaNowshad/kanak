package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.kanak.auth.GoogleAuthManager
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.printing.BluetoothPrinterManager
import com.example.kanak.ui.navigation.KanakApp
import com.example.ui.theme.KanakTheme

/**
 * Main Activity for Kanak FMCG Wholesale Van Sales Application.
 */
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val context = this
      val repository = remember { KanakRepository(context) }
      val authManager = remember { GoogleAuthManager(context) }
      val printerManager = remember { BluetoothPrinterManager(context) }

      KanakTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          KanakApp(
            repository = repository,
            authManager = authManager,
            printerManager = printerManager
          )
        }
      }
    }
  }
}
