package com.ratulhossen.tiffinbox.ui.screens.auth

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import com.ratulhossen.tiffinbox.data.firebase.OtpSendResult
import com.ratulhossen.tiffinbox.data.firebase.PhoneVerificationRepository

/**
 * [phoneNumber] is the normalized "880XXXXXXXXXX" form (no '+', see
 * util/PhoneNumber.kt) — kept '+'-free so it's safe to embed directly in a
 * navigation route segment; the '+' is only added back when calling the
 * Firebase Phone Auth API.
 */
@Composable
fun PhoneVerificationScreen(phoneNumber: String, onVerified: () -> Unit) {
    val context = LocalContext.current
    var verificationId by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(true) }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val sendFailedMessage = stringResource(R.string.phone_verification_send_failed)
    val codeRequiredMessage = stringResource(R.string.phone_verification_code_required)

    fun onVerificationSucceeded() {
        FirebaseAuthRepository.currentUser?.uid?.let { FirestoreUserRepository.markPhoneVerified(it) }
        onVerified()
    }

    fun sendOtp() {
        isSending = true
        errorMessage = null
        val activity = context as? Activity
        if (activity == null) {
            isSending = false
            errorMessage = sendFailedMessage
            return
        }
        PhoneVerificationRepository.sendOtp(activity, "+$phoneNumber") { result ->
            isSending = false
            when (result) {
                is OtpSendResult.CodeSent -> verificationId = result.verificationId
                is OtpSendResult.AutoVerified -> {
                    isVerifying = true
                    PhoneVerificationRepository.applyCredential(result.credential) { success, message ->
                        isVerifying = false
                        if (success) onVerificationSucceeded() else errorMessage = message
                    }
                }
                is OtpSendResult.Failed -> errorMessage = result.message
            }
        }
    }

    LaunchedEffect(phoneNumber) { sendOtp() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.phone_verification_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(R.string.phone_verification_subtitle, "+$phoneNumber"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(28.dp))

        if (isSending && verificationId == null) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        } else {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                label = { Text(stringResource(R.string.phone_verification_code_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                val id = verificationId
                if (code.length < 6 || id == null) {
                    errorMessage = codeRequiredMessage
                    return@Button
                }
                isVerifying = true
                errorMessage = null
                PhoneVerificationRepository.confirmCode(id, code) { success, message ->
                    isVerifying = false
                    if (success) onVerificationSucceeded() else errorMessage = message
                }
            },
            enabled = !isSending && !isVerifying && verificationId != null,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (isVerifying) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(stringResource(R.string.phone_verification_verify_button))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = { sendOtp() },
            enabled = !isSending && !isVerifying,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.phone_verification_resend_button))
        }
    }
}
