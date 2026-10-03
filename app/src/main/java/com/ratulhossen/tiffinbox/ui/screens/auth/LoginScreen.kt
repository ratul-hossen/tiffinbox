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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.AuthResult
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreChefRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import com.ratulhossen.tiffinbox.ui.screens.roleselect.UserRole
import kotlinx.coroutines.launch

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
fun LoginScreen(role: String, onLoginSuccess: (resolvedRole: String) -> Unit) {
    var isLoginMode by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var kitchenName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val emailPasswordRequiredMessage = stringResource(R.string.login_email_password_required)
    val kitchenNameRequiredMessage = stringResource(R.string.login_kitchen_name_required)
    val googleSignInFailedMessage = stringResource(R.string.login_google_signin_failed)
    val googleSignInCancelledMessage = stringResource(R.string.login_google_signin_cancelled)
    val isChefRole = role == UserRole.CHEF.name

    fun submit() {
        errorMessage = null
        if (email.isBlank() || password.isBlank()) {
            errorMessage = emailPasswordRequiredMessage
            return
        }
        if (!isLoginMode && isChefRole && kitchenName.isBlank()) {
            errorMessage = kitchenNameRequiredMessage
            return
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
                    val resolvedRole = if (isLoginMode) {
                        val storedRole = FirestoreUserRepository.getRole(uid) ?: role
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
                        storedRole
                    } else {
                        FirestoreUserRepository.upsertProfile(
                            uid = uid,
                            name = name.trim(),
                            email = email.trim(),
                            role = role
                        )
                        if (isChefRole) {
                            FirestoreChefRepository.createChefProfile(
                                uid = uid,
                                kitchenName = kitchenName.trim(),
                                ownerName = name.trim()
                            )
                        }
                        role
                    }
                    FirestoreUserRepository.registerFcmToken(uid)
                    isLoading = false
                    onLoginSuccess(resolvedRole)
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
                        val existingRole = FirestoreUserRepository.getRole(uid)
                        val resolvedRole = if (existingRole != null) {
                            if (existingRole == UserRole.CHEF.name && !FirestoreChefRepository.chefProfileExists(uid)) {
                                val displayName = googleIdTokenCredential.displayName
                                    ?: authResult.user.displayName.orEmpty()
                                FirestoreChefRepository.createChefProfile(
                                    uid = uid,
                                    kitchenName = if (displayName.isNotBlank()) "$displayName's Kitchen" else "My Kitchen",
                                    ownerName = displayName
                                )
                            }
                            existingRole
                        } else {
                            val displayName = googleIdTokenCredential.displayName
                                ?: authResult.user.displayName.orEmpty()
                            FirestoreUserRepository.upsertProfile(
                                uid = uid,
                                name = displayName,
                                email = authResult.user.email.orEmpty(),
                                role = role
                            )
                            if (isChefRole) {
                                FirestoreChefRepository.createChefProfile(
                                    uid = uid,
                                    kitchenName = if (displayName.isNotBlank()) "$displayName's Kitchen" else "My Kitchen",
                                    ownerName = displayName
                                )
                            }
                            role
                        }
                        FirestoreUserRepository.registerFcmToken(uid)
                        isLoading = false
                        onLoginSuccess(resolvedRole)
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
                isLoading = false
                errorMessage = googleSignInFailedMessage
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
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
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.login_email_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.login_password_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

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
            onClick = {
                isLoginMode = !isLoginMode
                errorMessage = null
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isLoginMode) stringResource(R.string.login_toggle_to_signup)
                else stringResource(R.string.login_toggle_to_login)
            )
        }
    }
}
