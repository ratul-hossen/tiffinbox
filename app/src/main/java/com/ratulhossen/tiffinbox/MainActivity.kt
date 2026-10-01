package com.ratulhossen.tiffinbox

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.notifications.ensureNotificationChannel
import com.ratulhossen.tiffinbox.ui.navigation.TiffinNavGraph
import com.ratulhossen.tiffinbox.ui.theme.TiffinBoxTheme
import com.ratulhossen.tiffinbox.util.rememberIsOnline

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
            Column {
                val isOnline = rememberIsOnline()
                AnimatedVisibility(visible = !isOnline) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            "ইন্টারনেট সংযোগ নেই — কিছু তথ্য পুরনো হতে পারে",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    TiffinNavGraph()
                }
            }
        }
    }
}
