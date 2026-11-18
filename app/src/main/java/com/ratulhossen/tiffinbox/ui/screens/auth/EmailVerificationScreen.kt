package com.ratulhossen.tiffinbox.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import kotlinx.coroutines.launch
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import com.ratulhossen.tiffinbox.ui.components.CirclePhoto

/**
 * Blocks an email/password account until its address is confirmed via the
 * link Firebase emails at signup (free on the Spark plan, unlike SMS). Shown
 * right after signup and again on login / app reopen while still unverified
 * - see FirebaseAuthRepository.needsEmailVerification().
 */
@Composable
fun EmailVerificationScreen(onVerified: () -> Unit, onLogout: () -> Unit) {
    val email = FirebaseAuthRepository.currentUser?.email.orEmpty()
    var isChecking by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Edge-to-edge: keep content clear of the status bar, the phone's
            // back/home buttons and the keyboard.
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CirclePhoto(resId = R.drawable.email_verify, size = 150.dp)
        Spacer(modifier = Modifier.height(20.dp))
        Text(stringResource(R.string.email_verification_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.email_verification_body, email),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            stringResource(R.string.email_verification_spam_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontSize = 12.sp
        )

        if (statusMessage != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                statusMessage.orEmpty(),
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        TiffinButton(
            onClick = {
                isChecking = true
                statusMessage = null
                scope.launch {
                    val verified = FirebaseAuthRepository.reloadAndCheckEmailVerified()
                    isChecking = false
                    if (verified) {
                        onVerified()
                    } else {
                        isError = true
                        statusMessage = context.getString(R.string.email_verification_not_yet)
                    }
                }
            },
            enabled = !isChecking && !isResending,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(stringResource(R.string.email_verification_check_button))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                isResending = true
                statusMessage = null
                scope.launch {
                    val sent = FirebaseAuthRepository.resendVerificationEmail()
                    isResending = false
                    isError = !sent
                    statusMessage = context.getString(
                        if (sent) R.string.email_verification_resent else R.string.email_verification_resend_failed
                    )
                }
            },
            enabled = !isChecking && !isResending,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(stringResource(R.string.email_verification_resend_button))
        }

        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.email_verification_logout_button))
        }
    }
}
