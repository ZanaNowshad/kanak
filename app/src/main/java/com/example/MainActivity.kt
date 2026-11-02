package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.kanak.auth.GoogleAuthManager
import com.example.kanak.data.repository.KanakRepository
import com.example.kanak.printing.BluetoothPrinterManager
import com.example.kanak.ui.navigation.KanakApp
import com.example.ui.theme.KanakBackground
import com.example.ui.theme.KanakTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: KanakRepository
    private lateinit var authManager: GoogleAuthManager
    private lateinit var printerManager: BluetoothPrinterManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = KanakRepository(applicationContext)
        authManager = GoogleAuthManager(applicationContext)
        printerManager = BluetoothPrinterManager(applicationContext)

        setContent {
            KanakTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = KanakBackground
                ) {
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
