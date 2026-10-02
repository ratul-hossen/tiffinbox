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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.AuthResult
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(role: String, onLoginSuccess: () -> Unit) {
    var isLoginMode by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val emailPasswordRequiredMessage = stringResource(R.string.login_email_password_required)

    fun submit() {
        errorMessage = null
        if (email.isBlank() || password.isBlank()) {
            errorMessage = emailPasswordRequiredMessage
            return
        }
        isLoading = true
        scope.launch {
            val result = if (isLoginMode) {
                FirebaseAuthRepository.signIn(email.trim(), password)
            } else {
                FirebaseAuthRepository.signUp(name.trim(), email.trim(), password)
            }
            isLoading = false
            when (result) {
                is AuthResult.Success -> {
                    if (!isLoginMode) {
                        FirestoreUserRepository.upsertProfile(
                            uid = result.user.uid,
                            name = name.trim(),
                            email = email.trim(),
                            role = role
                        )
                    }
                    FirestoreUserRepository.registerFcmToken(result.user.uid)
                    onLoginSuccess()
                }
                is AuthResult.Failure -> errorMessage = result.message
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
            onClick = onLoginSuccess,
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
