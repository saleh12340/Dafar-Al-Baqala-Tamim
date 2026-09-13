package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.ui.HomeScreen
import com.example.ui.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AccountingViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: AccountingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showSplash by remember { mutableStateOf(true) }

                    // Request runtime permissions on first launch
                    val permissionsLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { /* Handled gracefully by the system and settings UI */ }

                    LaunchedEffect(Unit) {
                        val permissionsToRequest = mutableListOf<String>()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_CONNECT)
                            permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_SCAN)
                        } else {
                            permissionsToRequest.add(android.Manifest.permission.BLUETOOTH)
                            permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_ADMIN)
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_IMAGES)
                        } else {
                            permissionsToRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        }

                        permissionsLauncher.launch(permissionsToRequest.toTypedArray())
                    }

                    if (showSplash) {
                        SplashScreen(onTimeout = { showSplash = false })
                    } else {
                        HomeScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
