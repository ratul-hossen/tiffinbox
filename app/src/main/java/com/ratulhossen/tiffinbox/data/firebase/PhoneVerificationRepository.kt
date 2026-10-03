package com.ratulhossen.tiffinbox.data.firebase

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

sealed class OtpSendResult {
    data class CodeSent(val verificationId: String) : OtpSendResult()
    data class AutoVerified(val credential: PhoneAuthCredential) : OtpSendResult()
    data class Failed(val message: String) : OtpSendResult()
}

/**
 * Verifies a phone number belongs to the already-signed-in Firebase user via
 * a real SMS OTP (Firebase Phone Auth), then attaches it to that account
 * with FirebaseUser.updatePhoneNumber. This only confirms ownership of the
 * number — it doesn't change how the user signs in (still email/password or
 * Google); the account's primary identity is untouched.
 */
object PhoneVerificationRepository {

    fun sendOtp(activity: Activity, e164Phone: String, onResult: (OtpSendResult) -> Unit) {
        val options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber(e164Phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    onResult(OtpSendResult.AutoVerified(credential))
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    onResult(OtpSendResult.Failed(e.message ?: "ফোন ভেরিফিকেশন ব্যর্থ হয়েছে"))
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    onResult(OtpSendResult.CodeSent(verificationId))
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun confirmCode(verificationId: String, code: String, onResult: (success: Boolean, error: String?) -> Unit) {
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        applyCredential(credential, onResult)
    }

    fun applyCredential(credential: PhoneAuthCredential, onResult: (success: Boolean, error: String?) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onResult(false, "সাইন-ইন করা নেই")
            return
        }
        user.updatePhoneNumber(credential)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { onResult(false, it.message) }
    }
}
