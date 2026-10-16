package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await

sealed class AuthResult {
    data class Success(val user: FirebaseUser) : AuthResult()
    data class Failure(val message: String) : AuthResult()
}

object FirebaseAuthRepository {

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser?
        get() = try {
            auth.currentUser
        } catch (e: Exception) {
            null
        }

    suspend fun signUp(name: String, email: String, password: String): AuthResult {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: return AuthResult.Failure("Account তৈরি হয়নি, আবার চেষ্টা করুন")

            val profileUpdate = UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build()
            user.updateProfile(profileUpdate).await()

            try {
                auth.useAppLanguage()
                user.sendEmailVerification().await()
            } catch (e: Exception) {
                // Non-fatal: the account still exists even if the verification
                // email couldn't be sent right now (e.g. offline).
            }

            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Failure(friendlyMessage(e))
        }
    }

    suspend fun signIn(email: String, password: String): AuthResult {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: return AuthResult.Failure("Login ব্যর্থ হয়েছে, আবার চেষ্টা করুন")
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Failure(friendlyMessage(e))
        }
    }

    /** Exchanges a Google ID token (from Credential Manager) for a real Firebase session. */
    suspend fun signInWithGoogleIdToken(idToken: String): AuthResult {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: return AuthResult.Failure("Google দিয়ে সাইন-ইন ব্যর্থ হয়েছে, আবার চেষ্টা করুন")
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Failure(friendlyMessage(e))
        }
    }

    /**
     * True when the signed-in account is an email/password account whose
     * address hasn't been confirmed yet (verification link not clicked).
     * Google accounts are already verified by Google, so they never need
     * this. Demo chef accounts seeded from admin-web use fake
     * @tiffinbox.app addresses that can't receive mail, so they're exempt.
     */
    fun needsEmailVerification(user: FirebaseUser? = currentUser): Boolean {
        if (user == null) return false
        val isPasswordAccount = user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }
        val isDemoAccount = user.email.orEmpty().endsWith("@tiffinbox.app")
        return isPasswordAccount && !user.isEmailVerified && !isDemoAccount
    }

    /** Re-fetches the user from Firebase (isEmailVerified is cached until reload) and reports whether it's verified now. */
    suspend fun reloadAndCheckEmailVerified(): Boolean {
        return try {
            currentUser?.reload()?.await()
            currentUser?.isEmailVerified == true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun resendVerificationEmail(): Boolean {
        return try {
            currentUser?.sendEmailVerification()?.await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** True only for an email/password account - a Google-only sign-in has no password to change. */
    val hasPasswordProvider: Boolean
        get() = currentUser?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } ?: false

    suspend fun changePassword(currentPassword: String, newPassword: String): AuthResult {
        val user = currentUser ?: return AuthResult.Failure("Login করা নেই")
        val email = user.email ?: return AuthResult.Failure("এই অ্যাকাউন্টের পাসওয়ার্ড পরিবর্তন করা যাবে না")
        return try {
            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).await()
            user.updatePassword(newPassword).await()
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Failure(friendlyMessage(e))
        }
    }

    /** Keeps the Firebase Auth display name (shown on Profile) in sync with Edit Profile. */
    suspend fun updateDisplayName(name: String) {
        try {
            currentUser?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build())?.await()
        } catch (e: Exception) {
            // Non-fatal: the Firestore name is still saved.
        }
    }

    fun signOut() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            // no-op: nothing to sign out of if Firebase was never configured
        }
    }

    private fun friendlyMessage(e: Exception): String {
        return if (e is IllegalStateException) {
            "Firebase এখনো সেটআপ করা হয়নি — google-services.json যোগ করুন"
        } else {
            e.message ?: "কিছু একটা ভুল হয়েছে, আবার চেষ্টা করুন"
        }
    }
}
