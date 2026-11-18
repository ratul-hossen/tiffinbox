package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

data class ChatMessageUi(
    val id: String,
    val senderId: String,
    val text: String,
    val sentAt: Long,
    val isAdmin: Boolean = false,
    val type: String = "text", // "text", "voice", "payment_request"
    val audioBase64: String = "",
    val audioDurationSec: Int = 0,
    val paymentAmount: Int = 0,
    val paymentMethod: String = "bKash",
    val paymentNumber: String = "",
    val paymentFor: String = "",
    val paymentStatus: String = "pending", // "pending", "paid"
    val paymentTransactionId: String = "",
    val subscriptionId: String = "",
    /** Sent by the customer's AI agent through the MCP server. */
    val viaAgent: Boolean = false
)

data class ChatThreadUi(
    val chatId: String,
    val customerId: String,
    val chefId: String,
    val chefName: String,
    val lastMessage: String,
    val lastMessageAt: Long,
    /** Messages from the other side not opened yet (for whoever is looking at the list). */
    val unreadCount: Int = 0
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

    /**
     * Unread counters live on the thread doc: a customer's message bumps
     * unreadForChef, a chef's bumps unreadForCustomer, and opening the chat
     * sets your own back to 0. (Home's chat button used to show a green dot
     * that was always on, whether or not anything was unread.)
     */
    private const val UNREAD_FOR_CUSTOMER = "unreadForCustomer"
    private const val UNREAD_FOR_CHEF = "unreadForChef"

    private fun unreadFieldForReceiver(senderId: String, customerId: String): String =
        if (senderId == customerId) UNREAD_FOR_CHEF else UNREAD_FOR_CUSTOMER

    /** Marks a thread as read for the customer ([asChef] false) or the chef. */
    fun markRead(chatId: String, asChef: Boolean) {
        try {
            // update(), not set(merge): never create a half-empty thread doc here.
            db.collection("chats").document(chatId)
                .update(if (asChef) UNREAD_FOR_CHEF else UNREAD_FOR_CUSTOMER, 0)
        } catch (e: Exception) {
            // Thread doesn't exist yet / offline - nothing to clear.
        }
    }

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
            val threadData = hashMapOf<String, Any>(
                "customerId" to customerId,
                "chefId" to chefId,
                "chefName" to chefName,
                "lastMessage" to text,
                "lastMessageAt" to System.currentTimeMillis(),
                unreadFieldForReceiver(senderId, customerId) to FieldValue.increment(1)
            )
            chatRef.set(threadData, SetOptions.merge()).addOnSuccessListener {
                val messageData = hashMapOf(
                    "senderId" to senderId,
                    "text" to text,
                    "sentAt" to System.currentTimeMillis(),
                    "type" to "text"
                )
                chatRef.collection("messages").add(messageData)
            }
        } catch (e: Exception) {
            // Safe to ignore
        }
    }

    fun sendOrderPlacedMessage(
        customerId: String,
        chefId: String,
        chefName: String,
        orderId: String,
        itemsSummary: String,
        totalPrice: Int,
        isPreOrder: Boolean,
        paymentMethod: String
    ) {
        val chatId = chatId(customerId, chefId)
        val shortId = if (orderId.length > 6) orderId.takeLast(6).uppercase() else orderId.uppercase()
        val text = buildString {
            append(if (isPreOrder) "⏳ নতুন প্রি-অর্ডার প্লেস করা হয়েছে!" else "🍱 নতুন অর্ডার প্লেস করা হয়েছে!")
            append("\nঅর্ডার আইডি: #$shortId")
            append("\nখাবার: $itemsSummary")
            append("\nমোট মূল্য: ৳$totalPrice")
            append("\nপেমেন্ট: $paymentMethod")
        }
        sendMessage(
            chatId = chatId,
            customerId = customerId,
            chefId = chefId,
            chefName = chefName,
            senderId = customerId,
            text = text
        )
    }

    fun sendSubscriptionRequestedMessage(
        customerId: String,
        chefId: String,
        chefName: String,
        planName: String,
        mealsPerWeek: Int,
        mealType: String,
        totalPrice: Int,
        scheduleSummary: String,
        isCustomSchedule: Boolean
    ) {
        val chatId = chatId(customerId, chefId)
        val text = buildString {
            append("📅 নতুন মিল প্ল্যান সাবস্ক্রিপশন রিকোয়েস্ট!")
            append("\nপ্ল্যান: $planName (${if (isCustomSchedule) "কাস্টম শিডিউল" else "রেগুলার"})")
            append("\nমিল সংখ্যা: $mealsPerWeek meals/week")
            if (scheduleSummary.isNotBlank()) {
                append("\nশিডিউল: $scheduleSummary")
            } else {
                append("\nখাবারের সময়: $mealType")
            }
            append("\nমোট মূল্য: ৳$totalPrice")
        }
        sendMessage(
            chatId = chatId,
            customerId = customerId,
            chefId = chefId,
            chefName = chefName,
            senderId = customerId,
            text = text
        )
    }

    fun sendVoiceMessage(
        chatId: String,
        customerId: String,
        chefId: String,
        chefName: String,
        senderId: String,
        audioBase64: String,
        durationSec: Int
    ) {
        try {
            val chatRef = db.collection("chats").document(chatId)
            val threadData = hashMapOf<String, Any>(
                "customerId" to customerId,
                "chefId" to chefId,
                "chefName" to chefName,
                "lastMessage" to "🎤 Voice message ($durationSec s)",
                "lastMessageAt" to System.currentTimeMillis(),
                unreadFieldForReceiver(senderId, customerId) to FieldValue.increment(1)
            )
            chatRef.set(threadData, SetOptions.merge()).addOnSuccessListener {
                val messageData = hashMapOf(
                    "senderId" to senderId,
                    "text" to "🎤 Voice message",
                    "sentAt" to System.currentTimeMillis(),
                    "type" to "voice",
                    "audioBase64" to audioBase64,
                    "audioDurationSec" to durationSec
                )
                chatRef.collection("messages").add(messageData)
            }
        } catch (e: Exception) {
            // Safe to ignore
        }
    }

    fun sendPaymentRequest(
        chatId: String,
        customerId: String,
        chefId: String,
        chefName: String,
        senderId: String,
        amount: Int,
        paymentMethod: String,
        paymentNumber: String,
        paymentFor: String,
        subscriptionId: String = ""
    ) {
        try {
            val chatRef = db.collection("chats").document(chatId)
            val threadData = hashMapOf<String, Any>(
                "customerId" to customerId,
                "chefId" to chefId,
                "chefName" to chefName,
                "lastMessage" to "💳 Payment Request: ৳$amount",
                "lastMessageAt" to System.currentTimeMillis(),
                unreadFieldForReceiver(senderId, customerId) to FieldValue.increment(1)
            )
            chatRef.set(threadData, SetOptions.merge()).addOnSuccessListener {
                val messageData = hashMapOf(
                    "senderId" to senderId,
                    "text" to "Payment Request: ৳$amount for $paymentFor",
                    "sentAt" to System.currentTimeMillis(),
                    "type" to "payment_request",
                    "paymentAmount" to amount,
                    "paymentMethod" to paymentMethod,
                    "paymentNumber" to paymentNumber,
                    "paymentFor" to paymentFor,
                    "paymentStatus" to "pending",
                    "paymentTransactionId" to "",
                    "subscriptionId" to subscriptionId
                )
                chatRef.collection("messages").add(messageData)
            }
        } catch (e: Exception) {
            // Safe to ignore
        }
    }

    fun submitPaymentTransactionId(
        chatId: String,
        messageId: String,
        transactionId: String
    ) {
        try {
            db.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update("paymentTransactionId", transactionId)
        } catch (e: Exception) {
            // Safe to ignore
        }
    }

    suspend fun confirmPaymentReceived(
        chatId: String,
        messageId: String,
        subscriptionId: String,
        transactionId: String,
        paymentMethod: String
    ): Boolean {
        return try {
            db.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "paymentStatus" to "paid",
                        "paymentTransactionId" to transactionId
                    )
                ).await()
            if (subscriptionId.isNotBlank()) {
                FirestoreSubscriptionRepository.approveAndActivateSubscription(
                    subscriptionId = subscriptionId,
                    transactionId = transactionId,
                    paymentMethod = paymentMethod
                )
            }
            true
        } catch (e: Exception) {
            false
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
                        val text = doc.getString("text") ?: ""
                        val sentAt = doc.getLong("sentAt") ?: 0L
                        val isAdmin = doc.getBoolean("isAdmin") ?: false
                        val type = doc.getString("type") ?: "text"
                        val audioBase64 = doc.getString("audioBase64") ?: ""
                        val audioDurationSec = (doc.getLong("audioDurationSec") ?: 0L).toInt()
                        val paymentAmount = (doc.getLong("paymentAmount") ?: 0L).toInt()
                        val paymentMethod = doc.getString("paymentMethod") ?: "bKash"
                        val paymentNumber = doc.getString("paymentNumber") ?: ""
                        val paymentFor = doc.getString("paymentFor") ?: ""
                        val paymentStatus = doc.getString("paymentStatus") ?: "pending"
                        val paymentTransactionId = doc.getString("paymentTransactionId") ?: ""
                        val subscriptionId = doc.getString("subscriptionId") ?: ""
                        val viaAgent = doc.getBoolean("viaAgent") ?: false

                        ChatMessageUi(
                            id = doc.id,
                            senderId = senderId,
                            text = text,
                            sentAt = sentAt,
                            isAdmin = isAdmin,
                            type = type,
                            audioBase64 = audioBase64,
                            audioDurationSec = audioDurationSec,
                            paymentAmount = paymentAmount,
                            paymentMethod = paymentMethod,
                            paymentNumber = paymentNumber,
                            paymentFor = paymentFor,
                            paymentStatus = paymentStatus,
                            paymentTransactionId = paymentTransactionId,
                            subscriptionId = subscriptionId,
                            viaAgent = viaAgent
                        )
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
                        val unread = (doc.getLong(UNREAD_FOR_CHEF) ?: 0L).toInt()
                        ChatThreadUi(doc.id, customerId, chefId, chefName, lastMessage, lastMessageAt, unread)
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
                        val unread = (doc.getLong(UNREAD_FOR_CUSTOMER) ?: 0L).toInt()
                        ChatThreadUi(doc.id, customerId, chefId, chefName, lastMessage, lastMessageAt, unread)
                    }?.sortedByDescending { it.lastMessageAt } ?: emptyList()
                    onUpdate(threads)
                }
        } catch (e: Exception) {
            null
        }
    }
}
