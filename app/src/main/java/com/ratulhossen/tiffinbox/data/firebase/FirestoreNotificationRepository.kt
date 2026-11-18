package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.ratulhossen.tiffinbox.data.model.NotificationUi
import kotlinx.coroutines.tasks.await

/**
 * In-app notification history at notifications/{uid}/items, separate from
 * (and in addition to) the FCM push notifications NotificationHelper.kt
 * already shows - this is the list a user can scroll back through inside
 * the app. Free: a plain client-written Firestore subcollection, no Cloud
 * Functions involved.
 */
object FirestoreNotificationRepository {

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    fun addNotification(uid: String, title: String, body: String) {
        if (uid.isBlank()) return
        try {
            db.collection("notifications").document(uid).collection("items").add(
                hashMapOf(
                    "title" to title,
                    "body" to body,
                    "createdAt" to System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }

    suspend fun getNotifications(uid: String): List<NotificationUi> {
        return try {
            // Newest 100 - without the orderBy, limit(100) returned an arbitrary
            // 100 and new notifications could be missing once a user had more.
            db.collection("notifications").document(uid).collection("items")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    NotificationUi(
                        id = doc.id,
                        title = doc.getString("title") ?: return@mapNotNull null,
                        body = doc.getString("body") ?: "",
                        createdAt = doc.getLong("createdAt") ?: 0L
                    )
                }
                .sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
