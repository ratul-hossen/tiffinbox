package com.ratulhossen.tiffinbox.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R

/**
 * Phone number + "WhatsApp is the same number" checkbox (checked by
 * default, since that's the common case in Bangladesh) + a WhatsApp field
 * that only appears when the box is unticked. Use [effectiveWhatsapp] to
 * get the number to validate/save.
 */
@Composable
fun PhoneAndWhatsappFields(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    whatsappNumber: String,
    onWhatsappNumberChange: (String) -> Unit,
    whatsappSameAsPhone: Boolean,
    onWhatsappSameAsPhoneChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = onPhoneNumberChange,
            label = { Text(stringResource(R.string.login_phone_number_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onWhatsappSameAsPhoneChange(!whatsappSameAsPhone) }
        ) {
            Checkbox(checked = whatsappSameAsPhone, onCheckedChange = onWhatsappSameAsPhoneChange)
            Text(
                stringResource(R.string.whatsapp_same_as_phone),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (!whatsappSameAsPhone) {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = whatsappNumber,
                onValueChange = onWhatsappNumberChange,
                label = { Text(stringResource(R.string.login_whatsapp_number_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** The WhatsApp number to actually validate/save, given the checkbox state. */
fun effectiveWhatsapp(phoneNumber: String, whatsappNumber: String, sameAsPhone: Boolean): String =
    if (sameAsPhone) phoneNumber else whatsappNumber
