package com.ratulhossen.tiffinbox.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreChefRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import com.ratulhossen.tiffinbox.ui.screens.roleselect.UserRole
import com.ratulhossen.tiffinbox.util.normalizeBangladeshPhone
import kotlinx.coroutines.launch

/**
 * Shown right after a brand-new Google sign-up (an existing account signing
 * back in with Google skips this entirely). Google gives a name/email/photo
 * but nothing else, so this collects the same WhatsApp number (both roles)
 * and bKash number + kitchen name (chef) the email/password signup form
 * already requires, then hands off to the same phone-verification and
 * location-setup steps so both signup paths end up equivalent.
 */
@Composable
fun CompleteGoogleProfileScreen(uid: String, role: String, onCompleted: (normalizedPhone: String) -> Unit) {
    val isChefRole = role == UserRole.CHEF.name
    val user = FirebaseAuthRepository.currentUser
    val name = user?.displayName.orEmpty()
    val email = user?.email.orEmpty()

    var kitchenName by remember { mutableStateOf("") }
    var whatsappNumber by remember { mutableStateOf("") }
    var bkashNumber by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val kitchenNameRequiredMessage = stringResource(R.string.login_kitchen_name_required)
    val whatsappRequiredMessage = stringResource(R.string.login_whatsapp_number_required)
    val whatsappInvalidMessage = stringResource(R.string.login_whatsapp_number_invalid)
    val bkashRequiredMessage = stringResource(R.string.login_bkash_number_required)

    fun submit() {
        errorMessage = null
        if (isChefRole && kitchenName.isBlank()) {
            errorMessage = kitchenNameRequiredMessage
            return
        }
        if (whatsappNumber.isBlank()) {
            errorMessage = whatsappRequiredMessage
            return
        }
        val normalizedPhone = normalizeBangladeshPhone(whatsappNumber)
        if (normalizedPhone == null) {
            errorMessage = whatsappInvalidMessage
            return
        }
        if (isChefRole && bkashNumber.isBlank()) {
            errorMessage = bkashRequiredMessage
            return
        }

        isLoading = true
        scope.launch {
            FirestoreUserRepository.upsertProfile(
                uid = uid,
                name = name,
                email = email,
                role = role,
                whatsappNumber = normalizedPhone
            )
            if (isChefRole) {
                FirestoreChefRepository.createChefProfile(
                    uid = uid,
                    kitchenName = kitchenName.trim(),
                    ownerName = name,
                    whatsappNumber = normalizedPhone,
                    bkashNumber = bkashNumber.trim()
                )
            }
            FirestoreUserRepository.registerFcmToken(uid)
            isLoading = false
            onCompleted(normalizedPhone)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.complete_google_profile_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(R.string.complete_google_profile_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(28.dp))

        if (isChefRole) {
            OutlinedTextField(
                value = kitchenName,
                onValueChange = { kitchenName = it },
                label = { Text(stringResource(R.string.login_kitchen_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = whatsappNumber,
            onValueChange = { whatsappNumber = it },
            label = { Text(stringResource(R.string.login_whatsapp_number_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (isChefRole) {
            OutlinedTextField(
                value = bkashNumber,
                onValueChange = { bkashNumber = it },
                label = { Text(stringResource(R.string.login_bkash_number_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { submit() },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(stringResource(R.string.complete_google_profile_continue_button))
            }
        }
    }
}
