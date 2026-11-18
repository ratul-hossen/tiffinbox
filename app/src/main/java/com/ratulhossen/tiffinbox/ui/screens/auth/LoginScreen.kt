package com.ratulhossen.tiffinbox.ui.screens.auth

import android.util.Log
import androidx.compose.foundation.background
import com.ratulhossen.tiffinbox.ui.theme.brandGradient
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.RoleCache
import com.ratulhossen.tiffinbox.data.firebase.AuthResult
import com.ratulhossen.tiffinbox.data.firebase.RoleLookup
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreChefRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import com.ratulhossen.tiffinbox.ui.components.EmailField
import com.ratulhossen.tiffinbox.ui.components.KitchenTypeDropdown
import com.ratulhossen.tiffinbox.ui.components.PasswordField
import com.ratulhossen.tiffinbox.ui.components.offerToSavePassword
import com.ratulhossen.tiffinbox.ui.components.PhoneAndWhatsappFields
import com.ratulhossen.tiffinbox.ui.components.effectiveWhatsapp
import com.ratulhossen.tiffinbox.ui.screens.roleselect.UserRole
import kotlinx.coroutines.launch
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import com.ratulhossen.tiffinbox.ui.components.FullBleedPhoto
import com.ratulhossen.tiffinbox.ui.components.bottomScrim

/**
 * Looks up the OAuth web client id that the Google Services Gradle plugin
 * auto-generates (as R.string.default_web_client_id) once a real
 * google-services.json is present. Looked up by name via the resources
 * table — rather than referenced as R.string.default_web_client_id
 * directly — so this file still compiles in a checkout that hasn't added
 * google-services.json yet; it simply returns null and the Google button
 * reports that it isn't configured instead of crashing the build.
 */
private fun defaultWebClientIdOrNull(context: android.content.Context): String? {
    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    return if (resId != 0) context.getString(resId) else null
}

@Composable
fun LoginScreen(
    role: String,
    onLoginSuccess: (resolvedRole: String) -> Unit,
    onSignupComplete: (uid: String, role: String) -> Unit,
    onGoogleNeedsProfile: (uid: String, role: String) -> Unit
) {
    var isLoginMode by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var kitchenName by remember { mutableStateOf("") }
    var kitchenType by remember { mutableStateOf("") }
    var yearsOfExperience by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var whatsappNumber by remember { mutableStateOf("") }
    var whatsappSameAsPhone by remember { mutableStateOf(true) }
    var bkashNumber by remember { mutableStateOf("") }
    var nagadNumber by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Only chef signup uses the 3-step stepper (Personal Info -> Kitchen Info -> Confirm);
    // login and customer signup stay on the single-screen form below.
    var step by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val emailPasswordRequiredMessage = stringResource(R.string.login_email_password_required)
    val kitchenNameRequiredMessage = stringResource(R.string.login_kitchen_name_required)
    val googleSignInFailedMessage = stringResource(R.string.login_google_signin_failed)
    val googleSignInCancelledMessage = stringResource(R.string.login_google_signin_cancelled)
    val googleNetworkErrorMessage = stringResource(R.string.login_google_network_error)
    val isChefRole = role == UserRole.CHEF.name

    fun submit() {
        errorMessage = null
        if (email.isBlank() || password.isBlank()) {
            errorMessage = emailPasswordRequiredMessage
            return
        }
        var contacts: SignupContacts? = null
        if (!isLoginMode) {
            if (isChefRole && kitchenName.isBlank()) {
                errorMessage = kitchenNameRequiredMessage
                return
            }
            when (val check = validateSignupContacts(
                    isChefRole, phoneNumber, effectiveWhatsapp(phoneNumber, whatsappNumber, whatsappSameAsPhone), bkashNumber, nagadNumber
                )) {
                is SignupContactResult.Invalid -> {
                    errorMessage = context.getString(check.messageRes)
                    return
                }
                is SignupContactResult.Valid -> contacts = check.contacts
            }
        }
        isLoading = true
        scope.launch {
            val result = if (isLoginMode) {
                FirebaseAuthRepository.signIn(email.trim(), password)
            } else {
                FirebaseAuthRepository.signUp(name.trim(), email.trim(), password)
            }
            when (result) {
                is AuthResult.Success -> {
                    val uid = result.user.uid
                    if (isLoginMode) {
                        val storedRole = FirestoreUserRepository.getRole(uid)
                            ?: RoleCache.read(context, uid) ?: role
                        if (storedRole == UserRole.CHEF.name && !FirestoreChefRepository.chefProfileExists(uid)) {
                            // Self-heal: an account created before chef-profile
                            // creation existed (or whose profile write failed)
                            // gets a minimal one now, instead of silently
                            // acting like chef "chef1" forever.
                            FirestoreChefRepository.createChefProfile(
                                uid = uid,
                                kitchenName = result.user.displayName?.let { "$it's Kitchen" } ?: "My Kitchen",
                                ownerName = result.user.displayName.orEmpty()
                            )
                        }
                        FirestoreUserRepository.registerFcmToken(uid)
                        RoleCache.write(context, uid, storedRole)
                        isLoading = false
                        offerToSavePassword(context, email.trim(), password)
                        onLoginSuccess(storedRole)
                    } else {
                        FirestoreUserRepository.upsertProfile(
                            uid = uid,
                            name = name.trim(),
                            email = email.trim(),
                            role = role,
                            phoneNumber = contacts?.phoneNumber.orEmpty(),
                            whatsappNumber = contacts?.whatsappNumber.orEmpty()
                        )
                        if (isChefRole) {
                            FirestoreChefRepository.createChefProfile(
                                uid = uid,
                                kitchenName = kitchenName.trim(),
                                ownerName = name.trim(),
                                phoneNumber = contacts?.phoneNumber.orEmpty(),
                                whatsappNumber = contacts?.whatsappNumber.orEmpty(),
                                bkashNumber = contacts?.bkashNumber.orEmpty(),
                                nagadNumber = contacts?.nagadNumber.orEmpty(),
                                kitchenType = kitchenType.trim(),
                                yearsOfExperience = yearsOfExperience.toIntOrNull() ?: 0
                            )
                        }
                        FirestoreUserRepository.registerFcmToken(uid)
                        RoleCache.write(context, uid, role)
                        isLoading = false
                        offerToSavePassword(context, email.trim(), password)
                        onSignupComplete(uid, role)
                    }
                }
                is AuthResult.Failure -> {
                    isLoading = false
                    errorMessage = result.message
                }
            }
        }
    }

    fun submitGoogle() {
        errorMessage = null
        val webClientId = defaultWebClientIdOrNull(context)
        if (webClientId == null) {
            // No default_web_client_id resource - google-services.json has
            // no web OAuth client, which means Firebase Console's Google
            // Sign-In provider isn't enabled and/or this build's signing
            // SHA-1 isn't registered there yet. Not a code bug; see
            // admin-web/README.md section 0 for the Console steps.
            Log.e("GoogleSignIn", "default_web_client_id missing - Google Sign-In not configured in Firebase Console for this app/SHA-1")
            errorMessage = googleSignInFailedMessage
            return
        }
        isLoading = true
        scope.launch {
            try {
                val option = GetSignInWithGoogleOption.Builder(webClientId).build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val response = CredentialManager.create(context).getCredential(context, request)
                val credential = response.credential

                if (credential !is CustomCredential ||
                    credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    isLoading = false
                    errorMessage = googleSignInFailedMessage
                    return@launch
                }

                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authResult = FirebaseAuthRepository.signInWithGoogleIdToken(googleIdTokenCredential.idToken)

                when (authResult) {
                    is AuthResult.Success -> {
                        val uid = authResult.user.uid
                        val lookup = FirestoreUserRepository.lookupRole(uid)
                        val existingRole = when (lookup) {
                            is RoleLookup.Found -> lookup.role
                            RoleLookup.NotFound -> null
                            // Couldn't read the profile (offline / slow network): use the
                            // role saved on this phone, else stop instead of treating an
                            // existing account as a brand-new sign-up.
                            RoleLookup.Failed -> RoleCache.read(context, uid) ?: run {
                                FirebaseAuthRepository.signOut()
                                isLoading = false
                                errorMessage = googleNetworkErrorMessage
                                return@launch
                            }
                        }
                        if (existingRole != null && existingRole != role) {
                            // e.g. a customer's Google account tapped "Chef": it used to open
                            // the customer home silently, which looked like chef login failing.
                            FirebaseAuthRepository.signOut()
                            isLoading = false
                            errorMessage = context.getString(
                                R.string.login_google_role_mismatch,
                                context.getString(
                                    if (existingRole == UserRole.CHEF.name) R.string.role_name_chef
                                    else R.string.role_name_customer
                                )
                            )
                            return@launch
                        }
                        if (existingRole != null) {
                            RoleCache.write(context, uid, existingRole)
                            if (existingRole == UserRole.CHEF.name && !FirestoreChefRepository.chefProfileExists(uid)) {
                                val displayName = googleIdTokenCredential.displayName
                                    ?: authResult.user.displayName.orEmpty()
                                FirestoreChefRepository.createChefProfile(
                                    uid = uid,
                                    kitchenName = if (displayName.isNotBlank()) "$displayName's Kitchen" else "My Kitchen",
                                    ownerName = displayName
                                )
                            }
                            FirestoreUserRepository.registerFcmToken(uid)
                            isLoading = false
                            onLoginSuccess(existingRole)
                        } else {
                            // Brand-new Google sign-up: still needs the same
                            // phone/WhatsApp/bKash-or-Nagad numbers and
                            // location setup an email/password signup goes
                            // through - CompleteGoogleProfileScreen collects
                            // the former, then hands off to location setup.
                            isLoading = false
                            onGoogleNeedsProfile(uid, role)
                        }
                    }
                    is AuthResult.Failure -> {
                        isLoading = false
                        errorMessage = authResult.message
                    }
                }
            } catch (e: GetCredentialCancellationException) {
                isLoading = false
                errorMessage = googleSignInCancelledMessage
            } catch (e: Exception) {
                // The user-facing message stays generic on purpose, but log
                // the real exception - this is almost always a Firebase
                // Console config issue (Google provider not enabled, or the
                // signing keystore's SHA-1 isn't registered, so
                // google-services.json has no web client id / the Credential
                // Manager call fails) rather than a code bug, and the exact
                // exception type/message narrows down which.
                Log.e("GoogleSignIn", "Google sign-in failed", e)
                isLoading = false
                errorMessage = googleSignInFailedMessage
            }
        }
    }

    fun toggleMode() {
        isLoginMode = !isLoginMode
        errorMessage = null
        step = 0
    }

    if (!isLoginMode && isChefRole) {
        ChefSignupStepper(
            step = step,
            onStepChange = { step = it },
            name = name, onNameChange = { name = it },
            email = email, onEmailChange = { email = it },
            password = password, onPasswordChange = { password = it },
            kitchenName = kitchenName, onKitchenNameChange = { kitchenName = it },
            kitchenType = kitchenType, onKitchenTypeChange = { kitchenType = it },
            yearsOfExperience = yearsOfExperience, onYearsOfExperienceChange = { yearsOfExperience = it },
            phoneNumber = phoneNumber, onPhoneNumberChange = { phoneNumber = it },
            whatsappNumber = whatsappNumber, onWhatsappNumberChange = { whatsappNumber = it },
            whatsappSameAsPhone = whatsappSameAsPhone, onWhatsappSameAsPhoneChange = { whatsappSameAsPhone = it },
            bkashNumber = bkashNumber, onBkashNumberChange = { bkashNumber = it },
            nagadNumber = nagadNumber, onNagadNumberChange = { nagadNumber = it },
            isLoading = isLoading,
            errorMessage = errorMessage,
            onSubmit = { submit() },
            onToggleToLogin = { toggleMode() }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Edge-to-edge: keep content clear of the status bar, the phone's
            // back/home buttons and the keyboard.
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        // Food photo header (res/drawable-nodpi/login_header.jpg) with the
        // gradient app logo tucked into its bottom-left corner.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .shadow(12.dp, MaterialTheme.shapes.large)
                .clip(MaterialTheme.shapes.large)
        ) {
            FullBleedPhoto(
                resId = R.drawable.login_header,
                modifier = Modifier.fillMaxSize(),
                scrim = bottomScrim(0.6f)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
                    .size(56.dp)
                    .shadow(10.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(brandGradient()),
                contentAlignment = Alignment.Center
            ) {
                Text("🍱", fontSize = 28.sp)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            if (isLoginMode) stringResource(R.string.login_welcome_back) else stringResource(R.string.login_create_account_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(R.string.login_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(28.dp))

        if (!isLoginMode) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.login_name_label)) },
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
            Spacer(modifier = Modifier.height(12.dp))
        }

        EmailField(
            value = email,
            onValueChange = { email = it },
            isNewAccount = !isLoginMode,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        PasswordField(
            value = password,
            onValueChange = { password = it },
            isNewPassword = !isLoginMode,
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
        TiffinButton(
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
                Text(if (isLoginMode) stringResource(R.string.login_button) else stringResource(R.string.signup_button))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { submitGoogle() },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(stringResource(R.string.login_continue_with_google_button))
        }

        Spacer(modifier = Modifier.height(20.dp))
        TextButton(
            onClick = { toggleMode() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isLoginMode) stringResource(R.string.login_toggle_to_signup)
                else stringResource(R.string.login_toggle_to_login)
            )
        }
    }
}

/**
 * Chef-only signup flow, split into 3 steps (Personal Info -> Kitchen Info ->
 * Confirm) matching the reference mockups. The real Firebase submission and
 * all required-field validation still lives in [LoginScreen]'s submit() -
 * this only gates which step is showing and does light per-step validation
 * for the Next button.
 */
@Composable
private fun ChefSignupStepper(
    step: Int,
    onStepChange: (Int) -> Unit,
    name: String, onNameChange: (String) -> Unit,
    email: String, onEmailChange: (String) -> Unit,
    password: String, onPasswordChange: (String) -> Unit,
    kitchenName: String, onKitchenNameChange: (String) -> Unit,
    kitchenType: String, onKitchenTypeChange: (String) -> Unit,
    yearsOfExperience: String, onYearsOfExperienceChange: (String) -> Unit,
    phoneNumber: String, onPhoneNumberChange: (String) -> Unit,
    whatsappNumber: String, onWhatsappNumberChange: (String) -> Unit,
    whatsappSameAsPhone: Boolean, onWhatsappSameAsPhoneChange: (Boolean) -> Unit,
    bkashNumber: String, onBkashNumberChange: (String) -> Unit,
    nagadNumber: String, onNagadNumberChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onSubmit: () -> Unit,
    onToggleToLogin: () -> Unit
) {
    val stepTitles = listOf(
        stringResource(R.string.login_step_personal_info_title),
        stringResource(R.string.login_step_kitchen_info_title),
        stringResource(R.string.login_step_confirm_title)
    )
    val canProceed = when (step) {
        0 -> name.isNotBlank() && email.isNotBlank() && password.isNotBlank() && phoneNumber.isNotBlank() &&
            (whatsappSameAsPhone || whatsappNumber.isNotBlank())
        1 -> kitchenName.isNotBlank()
        else -> bkashNumber.isNotBlank() || nagadNumber.isNotBlank()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Edge-to-edge: keep content clear of the status bar, the phone's
            // back/home buttons and the keyboard.
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(stringResource(R.string.login_create_account_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            stepTitles.forEachIndexed { index, title ->
                val isDone = index < step
                val isCurrent = index == step
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            if (isDone || isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${index + 1}",
                        color = if (isDone || isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (index != stepTitles.lastIndex) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(stepTitles[step], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))

        when (step) {
            0 -> {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.login_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                PhoneAndWhatsappFields(
                    phoneNumber = phoneNumber,
                    onPhoneNumberChange = onPhoneNumberChange,
                    whatsappNumber = whatsappNumber,
                    onWhatsappNumberChange = onWhatsappNumberChange,
                    whatsappSameAsPhone = whatsappSameAsPhone,
                    onWhatsappSameAsPhoneChange = onWhatsappSameAsPhoneChange
                )
                Spacer(modifier = Modifier.height(12.dp))
                EmailField(
                    value = email,
                    onValueChange = onEmailChange,
                    isNewAccount = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                PasswordField(
                    value = password,
                    onValueChange = onPasswordChange,
                    isNewPassword = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            1 -> {
                OutlinedTextField(
                    value = kitchenName,
                    onValueChange = onKitchenNameChange,
                    label = { Text(stringResource(R.string.login_kitchen_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                KitchenTypeDropdown(
                    value = kitchenType,
                    onValueChange = onKitchenTypeChange,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = yearsOfExperience,
                    onValueChange = { v -> onYearsOfExperienceChange(v.filter { it.isDigit() }) },
                    label = { Text(stringResource(R.string.login_years_experience_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> {
                Text(
                    stringResource(R.string.login_payment_number_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = bkashNumber,
                    onValueChange = onBkashNumberChange,
                    label = { Text(stringResource(R.string.login_bkash_number_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = nagadNumber,
                    onValueChange = onNagadNumberChange,
                    label = { Text(stringResource(R.string.login_nagad_number_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step > 0) {
                OutlinedButton(
                    onClick = { onStepChange(step - 1) },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Text(stringResource(R.string.login_step_back_button))
                }
            }
            TiffinButton(
                onClick = {
                    if (step < stepTitles.lastIndex) onStepChange(step + 1) else onSubmit()
                },
                enabled = !isLoading && canProceed,
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        if (step < stepTitles.lastIndex) stringResource(R.string.login_step_next_button)
                        else stringResource(R.string.signup_button)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onToggleToLogin, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.login_toggle_to_login))
        }
    }
}
