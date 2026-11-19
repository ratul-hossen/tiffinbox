package com.ratulhossen.tiffinbox.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ratulhossen.tiffinbox.MainActivity
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.RoleCache
import com.ratulhossen.tiffinbox.ui.navigation.PendingNavigation

/**
 * Shows a phone notification for every new entry in the signed-in user's
 * notifications/{uid}/items list (order placed, accepted, cooking, on the
 * way, delivered, new order for a chef ...).
 *
 * Why this exists: those entries were only written to Firestore, and the
 * FCM push half needs a server (Cloud Functions = paid Blaze plan), so no
 * notification ever appeared on the phone. This listener is free: it runs
 * while the app's process is alive (open or in the background). When the
 * app has been fully closed, the entries still wait in the in-app
 * Notifications screen.
 */
object InAppNotificationWatcher {
    private const val PREFS = "notification_watcher"

    private var registration: ListenerRegistration? = null
    private var watchingUid: String? = null

    fun start(context: Context, uid: String) {
        if (uid == watchingUid && registration != null) return
        stop()
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // First run on this phone for this account: start from "now" so an old
        // history doesn't arrive as a burst of notifications.
        if (!prefs.contains(uid)) prefs.edit().putLong(uid, System.currentTimeMillis()).apply()
        val since = prefs.getLong(uid, System.currentTimeMillis())
        watchingUid = uid
        registration = try {
            FirebaseFirestore.getInstance()
                .collection("notifications").document(uid).collection("items")
                .whereGreaterThan("createdAt", since)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot == null) return@addSnapshotListener
                    var newest = prefs.getLong(uid, since)
                    snapshot.documentChanges
                        .filter { it.type == DocumentChange.Type.ADDED }
                        .map { it.document }
                        .sortedBy { it.getLong("createdAt") ?: 0L }
                        .forEach { doc ->
                            val createdAt = doc.getLong("createdAt") ?: return@forEach
                            if (createdAt <= newest) return@forEach
                            newest = createdAt
                            val title = doc.getString("title") ?: appContext.getString(R.string.app_name)
                            val body = doc.getString("body").orEmpty()
                            show(
                                appContext,
                                id = doc.id.hashCode(),
                                title = title,
                                body = body,
                                // Customers land on the Orders tab for anything about an order.
                                openTab = if (!RoleCache.read(appContext, uid).equals("CHEF", ignoreCase = true) && isAboutAnOrder(title, body))
                                    PendingNavigation.TAB_ORDERS else null
                            )
                        }
                    prefs.edit().putLong(uid, newest).apply()
                }
        } catch (e: Exception) {
            null
        }
    }

    fun stop() {
        registration?.remove()
        registration = null
        watchingUid = null
    }

    private val ORDER_WORDS = listOf("order", "refund", "payment", "অর্ডার", "রিফান্ড", "পেমেন্ট")

    private fun isAboutAnOrder(title: String, body: String): Boolean {
        val text = "$title $body".lowercase()
        return ORDER_WORDS.any { it in text }
    }

    private fun show(context: Context, id: Int, title: String, body: String, openTab: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        ensureNotificationChannel(context)
        // SINGLE_TOP + CLEAR_TOP: a running app gets onNewIntent instead of a second activity.
        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (openTab != null) launch.putExtra(PendingNavigation.EXTRA_OPEN_TAB, openTab)
        val openApp = PendingIntent.getActivity(
            context,
            // One PendingIntent per notification, so each keeps its own extras.
            id,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, ORDER_UPDATES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(id, notification)
    }
}
