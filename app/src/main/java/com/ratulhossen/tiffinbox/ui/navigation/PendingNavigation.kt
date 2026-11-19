package com.ratulhossen.tiffinbox.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Where a tapped phone notification wants to go. MainActivity sets it from
 * the launching intent (onCreate / onNewIntent); the customer MainScreen
 * consumes it once and selects that tab.
 */
object PendingNavigation {
    /** Intent extra set by InAppNotificationWatcher. */
    const val EXTRA_OPEN_TAB = "open_tab"
    const val TAB_ORDERS = "orders"

    var openTab by mutableStateOf<String?>(null)
        private set

    fun request(tab: String?) {
        if (!tab.isNullOrBlank()) openTab = tab
    }

    fun consume() {
        openTab = null
    }
}
