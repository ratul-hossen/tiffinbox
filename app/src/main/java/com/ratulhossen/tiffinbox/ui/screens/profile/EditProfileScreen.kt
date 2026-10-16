package com.ratulhossen.tiffinbox.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreChefRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import com.ratulhossen.tiffinbox.ui.components.PhoneAndWhatsappFields
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import com.ratulhossen.tiffinbox.ui.components.effectiveWhatsapp
import com.ratulhossen.tiffinbox.ui.screens.auth.SignupContactResult
import com.ratulhossen.tiffinbox.ui.screens.auth.validateSignupContacts
import com.ratulhossen.tiffinbox.ui.screens.roleselect.UserRole
import kotlinx.coroutines.launch

/**
 * Lets a customer or chef edit their own name, phone / WhatsApp numbers
 * (same "WhatsApp is the same number" checkbox as signup) and location;
 * chefs additionally edit kitchen name and bKash / Nagad numbers. Uses the
 * same validator as signup so the rules can't drift apart. Email is shown
 * read-only (changing a Firebase Auth email needs re-verification).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    onChangeLocation: (role: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uid = FirebaseAuthRepository.currentUser?.uid.orEmpty()

    var loaded by rememberSaveable { mutableStateOf(false) }
    var role by rememberSaveable { mutableStateOf(UserRole.CUSTOMER.name) }
    var email by rememberSaveable { mutableStateOf(FirebaseAuthRepository.currentUser?.email.orEmpty()) }
    var name by rememberSaveable { mutableStateOf("") }
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var whatsappNumber by rememberSaveable { mutableStateOf("") }
    var whatsappSameAsPhone by rememberSaveable { mutableStateOf(true) }
    var kitchenName by rememberSaveable { mutableStateOf("") }
    var bkashNumber by rememberSaveable { mutableStateOf("") }
    var nagadNumber by rememberSaveable { mutableStateOf("") }
    // Re-read every time this screen is shown, so it reflects a location
    // just changed on the location screen.
    var address by remember { mutableStateOf("") }
    var hasLocation by remember { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val isChef = role == UserRole.CHEF.name

    LaunchedEffect(uid) {
        val profile = FirestoreUserRepository.getProfile(uid) ?: return@LaunchedEffect
        address = profile.address
        hasLocation = profile.hasLocation
        if (loaded) return@LaunchedEffect
        role = profile.role.ifBlank { UserRole.CUSTOMER.name }
        if (profile.email.isNotBlank()) email = profile.email
        name = profile.name.ifBlank { FirebaseAuthRepository.currentUser?.displayName.orEmpty() }
        phoneNumber = profile.phoneNumber
        whatsappNumber = profile.whatsappNumber
        whatsappSameAsPhone = profile.whatsappNumber.isBlank() || profile.whatsappNumber == profile.phoneNumber
        if (role == UserRole.CHEF.name) {
            FirestoreCatalogRepository.getChef(uid)?.let { chef ->
                kitchenName = chef.kitchenName
                bkashNumber = chef.bkashNumber
                nagadNumber = chef.nagadNumber
                if (!hasLocation && chef.latitude != 0.0) hasLocation = true
            }
        }
        loaded = true
    }

    fun save() {
        message = null
        if (name.isBlank()) {
            isError = true
            message = context.getString(R.string.edit_profile_name_required)
            return
        }
        val contacts = when (
            val check = validateSignupContacts(
                isChef, phoneNumber, effectiveWhatsapp(phoneNumber, whatsappNumber, whatsappSameAsPhone), bkashNumber, nagadNumber
            )
        ) {
            is SignupContactResult.Invalid -> {
                isError = true
                message = context.getString(check.messageRes)
                return
            }
            is SignupContactResult.Valid -> check.contacts
        }
        isSaving = true
        scope.launch {
            var ok = FirestoreUserRepository.updateProfile(uid, name.trim(), contacts.phoneNumber, contacts.whatsappNumber)
            if (isChef) {
                ok = FirestoreChefRepository.updateContactInfo(
                    uid = uid,
                    ownerName = name.trim(),
                    kitchenName = kitchenName.trim().ifBlank { name.trim() },
                    phoneNumber = contacts.phoneNumber,
                    whatsappNumber = contacts.whatsappNumber,
                    bkashNumber = contacts.bkashNumber,
                    nagadNumber = contacts.nagadNumber
                ) && ok
            }
            FirebaseAuthRepository.updateDisplayName(name.trim())
            isSaving = false
            isError = !ok
            message = context.getString(if (ok) R.string.edit_profile_saved else R.string.edit_profile_save_failed)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.edit_profile_title)) },
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.login_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = email,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(stringResource(R.string.edit_profile_email_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            PhoneAndWhatsappFields(
                phoneNumber = phoneNumber,
                onPhoneNumberChange = { phoneNumber = it },
                whatsappNumber = whatsappNumber,
                onWhatsappNumberChange = { whatsappNumber = it },
                whatsappSameAsPhone = whatsappSameAsPhone,
                onWhatsappSameAsPhoneChange = { whatsappSameAsPhone = it }
            )

            if (isChef) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    stringResource(R.string.edit_profile_kitchen_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = kitchenName,
                    onValueChange = { kitchenName = it },
                    label = { Text(stringResource(R.string.login_kitchen_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.login_payment_number_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = bkashNumber,
                    onValueChange = { bkashNumber = it },
                    label = { Text(stringResource(R.string.login_bkash_number_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = nagadNumber,
                    onValueChange = { nagadNumber = it },
                    label = { Text(stringResource(R.string.login_nagad_number_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.edit_profile_location_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            when {
                                address.isNotBlank() -> address
                                hasLocation -> "✓"
                                else -> stringResource(R.string.edit_profile_location_none)
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    TextButton(onClick = { onChangeLocation(role) }) {
                        Text(stringResource(R.string.edit_profile_change_location))
                    }
                }
            }

            if (message != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    message.orEmpty(),
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            TiffinButton(
                onClick = { save() },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.edit_profile_save))
                }
            }
        }
    }
}
