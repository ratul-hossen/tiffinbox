package com.ratulhossen.tiffinbox.data.firebase

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

object FirebaseStorageRepository {

    /** Returns the uploaded screenshot's public download URL, or null on failure. */
    suspend fun uploadBkashScreenshot(customerId: String, imageUri: Uri): String? {
        return try {
            val ref = FirebaseStorage.getInstance().reference
                .child("bkash_proofs/$customerId/${System.currentTimeMillis()}.jpg")
            ref.putFile(imageUri).await()
            ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            null
        }
    }
}
