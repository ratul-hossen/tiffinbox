package com.ratulhossen.tiffinbox.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.AuthResult
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import kotlinx.coroutines.launch
import com.ratulhossen.tiffinbox.ui.components.TiffinButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val hasPassword = FirebaseAuthRepository.hasPasswordProvider
    val mismatchMessage = stringResource(R.string.change_password_mismatch)
    val tooShortMessage = stringResource(R.string.change_password_too_short)
    val successText = stringResource(R.string.change_password_success)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.change_password_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(20.dp)) {
            if (!hasPassword) {
                Text(
                    stringResource(R.string.change_password_google_only),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            OutlinedTextField(
                value = currentPassword,
                onValueChange = { currentPassword = it },
                label = { Text(stringResource(R.string.change_password_current_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text(stringResource(R.string.change_password_new_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text(stringResource(R.string.change_password_confirm_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (successMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(successMessage.orEmpty(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(20.dp))
            TiffinButton(
                onClick = {
                    errorMessage = null
                    successMessage = null
                    if (newPassword.length < 6) {
                        errorMessage = tooShortMessage
                        return@TiffinButton
                    }
                    if (newPassword != confirmPassword) {
                        errorMessage = mismatchMessage
                        return@TiffinButton
                    }
                    isLoading = true
                    scope.launch {
                        val result = FirebaseAuthRepository.changePassword(currentPassword, newPassword)
                        isLoading = false
                        when (result) {
                            is AuthResult.Success -> {
                                successMessage = successText
                                currentPassword = ""
                                newPassword = ""
                                confirmPassword = ""
                            }
                            is AuthResult.Failure -> errorMessage = result.message
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.change_password_submit_button))
                }
            }
        }
    }
}
