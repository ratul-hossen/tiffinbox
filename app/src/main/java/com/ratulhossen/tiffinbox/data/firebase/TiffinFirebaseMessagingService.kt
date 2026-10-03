package com.ratulhossen.tiffinbox.data.firebase

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.notifications.ORDER_UPDATES_CHANNEL_ID

/**
 * Receives FCM pushes and shows a local notification. There's no backend
 * sender wired up yet (that needs a Cloud Function or similar triggered on
 * order-status changes) — this only handles the client-side half: token
 * registration and rendering whatever arrives. Test pushes can be sent from
 * Firebase Console -> Cloud Messaging -> "Send test message" to a token
 * logged from onNewToken.
 */
class TiffinFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val uid = FirebaseAuthRepository.currentUser?.uid ?: return
        FirestoreUserRepository.saveFcmToken(uid, token)
    }

    @SuppressLint("MissingPermission")
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: "TiffinBox"
        val body = message.notification?.body ?: return

        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return

        val notification = NotificationCompat.Builder(this, ORDER_UPDATES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(System.currentTimeMillis().toInt(), notification)
    }
}
