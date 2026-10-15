package com.ratulhossen.tiffinbox.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.ui.theme.ThemePalette
import com.ratulhossen.tiffinbox.ui.theme.ThemePreferences

private data class SettingsMenuItem(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
private fun ThemePalette.label(): String = when (this) {
    ThemePalette.BLACK_RED -> stringResource(R.string.settings_theme_black_red)
    ThemePalette.CLASSIC_GREEN -> stringResource(R.string.settings_theme_classic_green)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangePasswordClick: () -> Unit = {},
    onPaymentMethodsClick: () -> Unit = {},
    onHelpSupportClick: () -> Unit = {},
    onTermsClick: () -> Unit = {},
    onPrivacyPolicyClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val selectedPalette = ThemePreferences.current
    val menuItems = listOf(
        SettingsMenuItem(Icons.Filled.Lock, stringResource(R.string.settings_menu_change_password), onChangePasswordClick),
        SettingsMenuItem(Icons.Filled.Payment, stringResource(R.string.settings_menu_payment_methods), onPaymentMethodsClick),
        SettingsMenuItem(Icons.Filled.HelpOutline, stringResource(R.string.settings_menu_help_support), onHelpSupportClick),
        SettingsMenuItem(Icons.Filled.Description, stringResource(R.string.settings_menu_terms), onTermsClick),
        SettingsMenuItem(Icons.Filled.PrivacyTip, stringResource(R.string.settings_menu_privacy_policy), onPrivacyPolicyClick)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(stringResource(R.string.settings_theme_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_theme_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            ThemePalette.entries.forEach { palette ->
                Surface(
                    onClick = { ThemePreferences.setPalette(context, palette) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (selectedPalette == palette) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPalette == palette,
                            onClick = { ThemePreferences.setPalette(context, palette) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(palette.label(), modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            HorizontalDivider()
            menuItems.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = item.onClick)
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(item.icon, contentDescription = item.label, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(item.label, modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
