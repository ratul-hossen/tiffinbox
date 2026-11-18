package com.ratulhossen.tiffinbox.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.ratulhossen.tiffinbox.ui.theme.BlackRedBrand
import com.ratulhossen.tiffinbox.ui.theme.GreenBrand
import com.ratulhossen.tiffinbox.ui.theme.OceanBrand
import com.ratulhossen.tiffinbox.ui.theme.SunsetBrand
import com.ratulhossen.tiffinbox.ui.theme.ThemeMode
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.ui.theme.ThemePalette
import com.ratulhossen.tiffinbox.ui.theme.ThemePreferences
import androidx.compose.material3.TopAppBarDefaults

private data class SettingsMenuItem(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
private fun ThemePalette.label(): String = when (this) {
    ThemePalette.BLACK_RED -> stringResource(R.string.settings_theme_black_red)
    ThemePalette.SUNSET_ORANGE -> stringResource(R.string.settings_theme_sunset)
    ThemePalette.OCEAN_BLUE -> stringResource(R.string.settings_theme_ocean)
    ThemePalette.CLASSIC_GREEN -> stringResource(R.string.settings_theme_classic_green)
    ThemePalette.DYNAMIC -> stringResource(R.string.settings_theme_dynamic)
}

/** Preview swatch for each palette, independent of the currently active one. */
private fun ThemePalette.swatch(): Brush = when (this) {
    ThemePalette.BLACK_RED -> Brush.linearGradient(listOf(BlackRedBrand.gradientStart, Color(0xFF111111)))
    ThemePalette.SUNSET_ORANGE -> Brush.linearGradient(listOf(SunsetBrand.gradientStart, SunsetBrand.gradientEnd))
    ThemePalette.OCEAN_BLUE -> Brush.linearGradient(listOf(OceanBrand.gradientStart, OceanBrand.gradientEnd))
    ThemePalette.CLASSIC_GREEN -> Brush.linearGradient(listOf(GreenBrand.gradientStart, GreenBrand.gradientEnd))
    ThemePalette.DYNAMIC -> Brush.sweepGradient(
        listOf(Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFCC00), Color(0xFF34C759), Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF3B30))
    )
}

@Composable
private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> stringResource(R.string.settings_mode_system)
    ThemeMode.LIGHT -> stringResource(R.string.settings_mode_light)
    ThemeMode.DARK -> stringResource(R.string.settings_mode_dark)
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
    val selectedMode = ThemePreferences.mode
    val menuItems = listOf(
        SettingsMenuItem(Icons.Filled.Lock, stringResource(R.string.settings_menu_change_password), onChangePasswordClick),
        SettingsMenuItem(Icons.Filled.Payment, stringResource(R.string.settings_menu_payment_methods), onPaymentMethodsClick),
        SettingsMenuItem(Icons.Filled.HelpOutline, stringResource(R.string.settings_menu_help_support), onHelpSupportClick),
        SettingsMenuItem(Icons.Filled.Description, stringResource(R.string.settings_menu_terms), onTermsClick),
        SettingsMenuItem(Icons.Filled.PrivacyTip, stringResource(R.string.settings_menu_privacy_policy), onPrivacyPolicyClick)
    )

    Scaffold(
        // Transparent so the app-wide AnimatedGradientBackground shows through.
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
                val isSelected = selectedPalette == palette
                Surface(
                    onClick = { ThemePreferences.setPalette(context, palette) },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    shadowElevation = if (isSelected) 6.dp else 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(palette.swatch())
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(palette.label(), fontWeight = FontWeight.SemiBold)
                            if (palette == ThemePalette.DYNAMIC) {
                                Text(
                                    stringResource(R.string.settings_theme_dynamic_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(stringResource(R.string.settings_mode_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    val isSelected = selectedMode == mode
                    Surface(
                        onClick = { ThemePreferences.setMode(context, mode) },
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation = if (isSelected) 3.dp else 0.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            mode.label(),
                            textAlign = TextAlign.Center,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
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

            // Which build this is - testers can quote it when reporting a problem.
            val versionName = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                    .getOrNull().orEmpty()
            }
            if (versionName.isNotBlank()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    stringResource(R.string.settings_app_version, versionName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
