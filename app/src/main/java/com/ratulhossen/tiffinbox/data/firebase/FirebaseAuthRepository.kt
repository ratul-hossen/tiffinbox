package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
