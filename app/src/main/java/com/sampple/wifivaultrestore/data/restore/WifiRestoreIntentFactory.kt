package com.sampple.wifivaultrestore.data.restore

import android.content.Intent
import android.net.wifi.WifiNetworkSuggestion
import android.os.Bundle
import android.os.Parcelable
import android.provider.Settings
import com.sampple.wifivaultrestore.data.SecurityType
import com.sampple.wifivaultrestore.data.WifiCredential

object WifiRestoreIntentFactory {
    fun buildIntent(credentials: List<WifiCredential>): Intent {
        val suggestions = credentials.mapNotNull { it.toSuggestionOrNull() }
        val bundle = Bundle().apply {
            putParcelableArrayList(
                Settings.EXTRA_WIFI_NETWORK_LIST,
                ArrayList<Parcelable>(suggestions),
            )
        }
        return Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putExtras(bundle)
    }

    fun WifiCredential.toSuggestionOrNull(): WifiNetworkSuggestion? {
        if (!canRestore || ssid.isBlank()) return null

        val builder = WifiNetworkSuggestion.Builder()
            .setSsid(ssid)
            .setIsHiddenSsid(hidden)
            .setIsInitialAutojoinEnabled(autoJoin)

        return try {
            val passwordValue = password
            when {
                security.contains(SecurityType.WPA3) && !security.contains(SecurityType.WPA2) && !passwordValue.isNullOrBlank() -> {
                    builder.setWpa3Passphrase(passwordValue)
                    builder.setCredentialSharedWithUser(true)
                }
                security.any { it == SecurityType.WPA2 || it == SecurityType.WPA3 || it == SecurityType.WEP } && !passwordValue.isNullOrBlank() -> {
                    builder.setWpa2Passphrase(passwordValue)
                    builder.setCredentialSharedWithUser(true)
                }
                security.contains(SecurityType.OWE) && !security.contains(SecurityType.OPEN) -> {
                    builder.setIsEnhancedOpen(true)
                }
                security.contains(SecurityType.OPEN) || passwordValue.isNullOrBlank() -> Unit
                else -> return null
            }
            builder.build()
        } catch (_: RuntimeException) {
            null
        }
    }
}
