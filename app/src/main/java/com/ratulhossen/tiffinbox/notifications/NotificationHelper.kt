package com.ratulhossen.tiffinbox.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

const val ORDER_UPDATES_CHANNEL_ID = "order_updates"

/** Creates the notification channel. Safe to call repeatedly — creating an existing channel is a no-op. */
fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val channel = NotificationChannel(
        ORDER_UPDATES_CHANNEL_ID,
        "Order Updates",
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = "অর্ডার placed, accepted, cooking, delivered status আপডেট"
    }
    val manager = context.getSystemService(NotificationManager::class.java)
    manager?.createNotificationChannel(channel)
}
