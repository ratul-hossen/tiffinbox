package com.ratulhossen.tiffinbox

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ratulhossen.tiffinbox.notifications.ensureNotificationChannel
import com.ratulhossen.tiffinbox.ui.navigation.TiffinNavGraph
import com.ratulhossen.tiffinbox.ui.theme.TiffinBoxTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureNotificationChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            TiffinBoxApp()
        }
    }
}

@Composable
fun TiffinBoxApp() {
    TiffinBoxTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            TiffinNavGraph()
        }
    }
}
