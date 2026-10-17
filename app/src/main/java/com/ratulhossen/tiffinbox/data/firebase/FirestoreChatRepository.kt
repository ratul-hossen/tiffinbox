package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

data class ChatMessageUi(
    val id: String,
    val senderId: String,
    val text: String,
    val sentAt: Long,
    val isAdmin: Boolean = false
)

data class ChatThreadUi(
    val chatId: String,
    val customerId: String,
    val chefId: String,
    val chefName: String,
    val lastMessage: String,
    val lastMessageAt: Long
)

/**
 * The app has one shared login for both roles (no separate chef accounts
 * yet), so a chef's own replies can't use their Firebase Auth uid - that
 * would collide with whichever uid is signed in on the device. Chef
 * replies use the synthetic sender id "chef:<chefId>" instead, so a
 * conversation always has exactly two distinct sender ids no matter who
 * is operating the device during a demo.
 */
object FirestoreChatRepository {

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    fun chatId(customerId: String, chefId: String): String = "${customerId}_$chefId"

    fun chefSenderId(chefId: String): String = "chef:$chefId"

    fun sendMessage(
        chatId: String,
        customerId: String,
        chefId: String,
        chefName: String,
        senderId: String,
        text: String
    ) {
        try {
            val chatRef = db.collection("chats").document(chatId)
            val threadData = hashMapOf(
                "customerId" to customerId,
                "chefId" to chefId,
                "chefName" to chefName,
                "lastMessage" to text,
                "lastMessageAt" to System.currentTimeMillis()
            )
            // প্রথমে parent chat document তৈরি/update করো, তারপরই message add করো।
            // এটা না করলে chat document না থাকলে Firestore rules messages subcollection
            // এ access deny করে (get(parent).data.customerId == uid check fail করে)।
            chatRef.set(threadData, SetOptions.merge()).addOnSuccessListener {
                val messageData = hashMapOf(
                    "senderId" to senderId,
                    "text" to text,
                    "sentAt" to System.currentTimeMillis()
                )
                chatRef.collection("messages").add(messageData)
            }.addOnFailureListener {
                // Fallback: offline বা permission error — silently ignore
            }
        } catch (e: Exception) {
            // Firebase not configured yet, or offline — safe to ignore.
        }
    }

    fun listenMessages(chatId: String, onUpdate: (List<ChatMessageUi>) -> Unit): ListenerRegistration? {
        return try {
            db.collection("chats").document(chatId)
                .collection("messages")
                .orderBy("sentAt", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, _ ->
                    val messages = snapshot?.documents?.mapNotNull { doc ->
                        val senderId = doc.getString("senderId") ?: return@mapNotNull null
                        val text = doc.getString("text") ?: return@mapNotNull null
                        val sentAt = doc.getLong("sentAt") ?: 0L
                        val isAdmin = doc.getBoolean("isAdmin") ?: false
                        ChatMessageUi(doc.id, senderId, text, sentAt, isAdmin)
                    } ?: emptyList()
                    onUpdate(messages)
                }
        } catch (e: Exception) {
            null
        }
    }

    fun listenChefThreads(chefId: String, onUpdate: (List<ChatThreadUi>) -> Unit): ListenerRegistration? {
        return try {
            db.collection("chats")
                .whereEqualTo("chefId", chefId)
                .addSnapshotListener { snapshot, _ ->
                    val threads = snapshot?.documents?.mapNotNull { doc ->
                        val customerId = doc.getString("customerId") ?: return@mapNotNull null
                        val chefName = doc.getString("chefName") ?: ""
                        val lastMessage = doc.getString("lastMessage") ?: ""
                        val lastMessageAt = doc.getLong("lastMessageAt") ?: 0L
                        ChatThreadUi(doc.id, customerId, chefId, chefName, lastMessage, lastMessageAt)
                    }?.sortedByDescending { it.lastMessageAt } ?: emptyList()
                    onUpdate(threads)
                }
        } catch (e: Exception) {
            null
        }
    }

    fun listenCustomerThreads(customerId: String, onUpdate: (List<ChatThreadUi>) -> Unit): ListenerRegistration? {
        return try {
            db.collection("chats")
                .whereEqualTo("customerId", customerId)
                .addSnapshotListener { snapshot, _ ->
                    val threads = snapshot?.documents?.mapNotNull { doc ->
                        val chefId = doc.getString("chefId") ?: return@mapNotNull null
                        val chefName = doc.getString("chefName") ?: ""
                        val lastMessage = doc.getString("lastMessage") ?: ""
                        val lastMessageAt = doc.getLong("lastMessageAt") ?: 0L
                        ChatThreadUi(doc.id, customerId, chefId, chefName, lastMessage, lastMessageAt)
                    }?.sortedByDescending { it.lastMessageAt } ?: emptyList()
                    onUpdate(threads)
                }
        } catch (e: Exception) {
            null
        }
    }
}
