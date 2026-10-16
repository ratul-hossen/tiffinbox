package com.ratulhossen.tiffinbox.ui.screens.profile

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository

private data class ProfileMenuItem(val icon: ImageVector, val label: String, val onClick: () -> Unit = {})

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onSubscriptionsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPaymentMethodClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onEditProfileClick: () -> Unit = {},
    onMyOrdersClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {}
) {
    val fallbackName = stringResource(R.string.profile_demo_user_name)
    val user = FirebaseAuthRepository.currentUser
    val displayName = user?.displayName?.takeIf { it.isNotBlank() } ?: fallbackName
    val subtitle = user?.email ?: stringResource(R.string.profile_demo_phone)

    val menuItems = listOf(
        ProfileMenuItem(Icons.Filled.Edit, stringResource(R.string.edit_profile_menu), onEditProfileClick),
        ProfileMenuItem(Icons.Filled.Receipt, stringResource(R.string.profile_menu_my_orders), onMyOrdersClick),
        ProfileMenuItem(Icons.Filled.DateRange, stringResource(R.string.profile_menu_my_subscriptions), onSubscriptionsClick),
        ProfileMenuItem(Icons.Filled.Favorite, stringResource(R.string.profile_menu_favorites), onFavoritesClick),
        // Address lives on the Edit Profile screen (with a "Change location" button).
        ProfileMenuItem(Icons.Filled.LocationOn, stringResource(R.string.profile_menu_address_management), onEditProfileClick),
        ProfileMenuItem(Icons.Filled.Payment, stringResource(R.string.profile_menu_payment_method), onPaymentMethodClick),
        ProfileMenuItem(Icons.Filled.Notifications, stringResource(R.string.profile_menu_notifications), onNotificationsClick),
        ProfileMenuItem(Icons.Filled.Settings, stringResource(R.string.profile_menu_settings), onSettingsClick)
    )

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEditProfileClick)
                .padding(20.dp),
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
                Text(displayName, style = MaterialTheme.typography.titleLarge)
                Text(
                    subtitle,
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
                contentDescription = stringResource(R.string.action_logout),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                stringResource(R.string.action_logout),
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        }

    }
}
