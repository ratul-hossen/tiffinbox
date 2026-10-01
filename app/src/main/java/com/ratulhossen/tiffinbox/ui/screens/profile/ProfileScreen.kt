package com.ratulhossen.tiffinbox.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.data.firebase.FirestoreSeedRepository

private data class ProfileMenuItem(val icon: ImageVector, val label: String, val onClick: () -> Unit = {})

@Composable
fun ProfileScreen(onLogout: () -> Unit, onSubscriptionsClick: () -> Unit) {
    var seedStatus by remember { mutableStateOf<String?>(null) }

    val menuItems = listOf(
        ProfileMenuItem(Icons.Filled.Receipt, "আমার অর্ডার"),
        ProfileMenuItem(Icons.Filled.DateRange, "আমার Subscriptions", onSubscriptionsClick),
        ProfileMenuItem(Icons.Filled.Favorite, "Favorites"),
        ProfileMenuItem(Icons.Filled.LocationOn, "ঠিকানা ব্যবস্থাপনা"),
        ProfileMenuItem(Icons.Filled.Payment, "Payment পদ্ধতি"),
        ProfileMenuItem(Icons.Filled.Notifications, "Notifications"),
        ProfileMenuItem(Icons.Filled.Settings, "Settings")
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("👤", fontSize = 28.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Rafi Ahmed", style = MaterialTheme.typography.titleLarge)
                Text(
                    "+880 1XXXXXXXXX",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider()

        menuItems.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { item.onClick() }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onLogout)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Logout,
                contentDescription = "Logout",
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                "Logout",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        HorizontalDivider()
        TextButton(
            onClick = {
                seedStatus = "পাঠানো হচ্ছে..."
                FirestoreSeedRepository.seedSampleData { result ->
                    seedStatus = when (result) {
                        is FirestoreSeedRepository.SeedResult.Success -> "✅ Sample chef/meal Firestore-এ পাঠানো হয়েছে"
                        is FirestoreSeedRepository.SeedResult.Failure -> "❌ ${result.message}"
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("Developer: Sample data Firestore-এ পাঠান", style = MaterialTheme.typography.labelSmall)
        }
        if (seedStatus != null) {
            Text(
                seedStatus.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
    }
}
