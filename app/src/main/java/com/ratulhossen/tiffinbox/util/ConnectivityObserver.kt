package com.ratulhossen.tiffinbox.util

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** True while the device has an active network with internet capability. */
@Composable
fun rememberIsOnline(): Boolean {
    val context = LocalContext.current
    var isOnline by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val activeCapabilities = connectivityManager?.activeNetwork
            ?.let { connectivityManager.getNetworkCapabilities(it) }
        isOnline = activeCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                isOnline = false
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                isOnline = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        connectivityManager?.registerDefaultNetworkCallback(callback)
        onDispose { connectivityManager?.unregisterNetworkCallback(callback) }
    }

    return isOnline
}
