package com.ratulhossen.tiffinbox.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R

@Composable
fun HelpSupportScreen(onBack: () -> Unit) {
    StaticInfoScreen(title = stringResource(R.string.help_support_title), onBack = onBack) {
        Text(stringResource(R.string.help_support_faq_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        Text(stringResource(R.string.help_support_faq_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.help_support_contact_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        Text(stringResource(R.string.help_support_contact_body), style = MaterialTheme.typography.bodyMedium)
    }
}
