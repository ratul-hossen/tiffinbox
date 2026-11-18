package com.ratulhossen.tiffinbox.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import com.ratulhossen.tiffinbox.R

/**
 * Email field tagged for autofill, so the phone's password manager (Google
 * Password Manager, Samsung Pass, ...) can offer the saved account.
 */
@Composable
fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isNewAccount: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.login_email_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = modifier.semantics {
            contentType = if (isNewAccount) ContentType.NewUsername else ContentType.Username
        }
    )
}

/**
 * Password field with a show/hide eye button, tagged for autofill so a saved
 * password can be filled in and a new one offered for saving.
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.login_password_label),
    isNewPassword: Boolean = false
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.password_hide else R.string.password_show
                    )
                )
            }
        },
        modifier = modifier.semantics {
            contentType = if (isNewPassword) ContentType.NewPassword else ContentType.Password
        }
    )
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Asks the phone's password manager to save this email + password (the
 * "Save password?" sheet). Best effort: if the user declines, or the device
 * has no password provider, nothing happens and login carries on.
 */
suspend fun offerToSavePassword(context: Context, email: String, password: String) {
    val activity = context.findActivity() ?: return
    if (email.isBlank() || password.isBlank()) return
    try {
        CredentialManager.create(activity).createCredential(activity, CreatePasswordRequest(email, password))
    } catch (e: Exception) {
        Log.i("PasswordSave", "Password not saved: ${e.javaClass.simpleName}")
    }
}
