package com.ratulhossen.tiffinbox.data

import android.content.Context

/**
 * Last known role ("CHEF" / "CUSTOMER") per account, kept on the phone.
 *
 * The role lives in Firestore (users/{uid}.role). When that read fails on a
 * slow or offline start, the app used to fall back to "customer", so a chef
 * reopening the app landed on the customer home and looked logged out. This
 * keeps the last confirmed role so the right home opens anyway.
 */
object RoleCache {
    private const val PREFS = "role_cache"

    fun read(context: Context, uid: String?): String? =
        uid?.let { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(it, null) }

    fun write(context: Context, uid: String, role: String) {
        if (uid.isBlank() || role.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(uid, role).apply()
    }
}
