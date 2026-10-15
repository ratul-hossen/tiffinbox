package com.ratulhossen.tiffinbox.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R

@Composable
fun PaymentMethodsScreen(onBack: () -> Unit) {
    StaticInfoScreen(title = stringResource(R.string.payment_methods_title), onBack = onBack) {
        PaymentMethodRow(stringResource(R.string.payment_method_cod), available = true)
        PaymentMethodRow(stringResource(R.string.payment_method_bkash), available = true)
        PaymentMethodRow(stringResource(R.string.payment_method_nagad), available = true)
        PaymentMethodRow(stringResource(R.string.payment_method_card), available = false)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            stringResource(R.string.payment_methods_card_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PaymentMethodRow(label: String, available: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (available) Icons.Filled.CheckCircle else Icons.Filled.Schedule,
            contentDescription = null,
            tint = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f))
        Text(
            if (available) stringResource(R.string.payment_methods_active) else stringResource(R.string.checkout_coming_soon_chip),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
