package com.sampple.wifivaultrestore.data.restore

import android.content.Intent
import android.net.wifi.WifiNetworkSuggestion
import android.os.Bundle
import android.os.Parcelable
import android.provider.Settings
import com.sampple.wifivaultrestore.data.SecurityType
import com.sampple.wifivaultrestore.data.WifiCredential

internal enum class RestoreNetworkKind {
    Wpa3,
    Wpa2,
    EnhancedOpen,
    Open,
}

object WifiRestoreIntentFactory {
    fun buildIntent(credentials: List<WifiCredential>): Intent {
        val suggestions = credentials.mapNotNull { credential ->
            if (RestoreCompatibility.evaluate(credential).supported) {
                credential.toSuggestionOrNull()
            } else {
                null
            }
        }
        val bundle = Bundle().apply {
            putParcelableArrayList(
                Settings.EXTRA_WIFI_NETWORK_LIST,
                ArrayList<Parcelable>(suggestions),
            )
        }
        return Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putExtras(bundle)
    }

    fun WifiCredential.toSuggestionOrNull(): WifiNetworkSuggestion? {
        if (!RestoreCompatibility.evaluate(this).supported) return null

        val builder = WifiNetworkSuggestion.Builder()
            .setSsid(ssid)
            .setIsHiddenSsid(hidden)
            .setIsInitialAutojoinEnabled(autoJoin)

        return try {
            when (restoreNetworkKind(credential = this)) {
                RestoreNetworkKind.Wpa3 -> {
                    builder.setWpa3Passphrase(password.orEmpty())
                    builder.setCredentialSharedWithUser(true)
                }
                RestoreNetworkKind.Wpa2 -> {
                    builder.setWpa2Passphrase(password.orEmpty())
                    builder.setCredentialSharedWithUser(true)
                }
                RestoreNetworkKind.EnhancedOpen -> {
                    builder.setIsEnhancedOpen(true)
                }
                RestoreNetworkKind.Open -> Unit
                null -> return null
            }
            builder.build()
        } catch (_: RuntimeException) {
            null
        }
    }

    internal fun restoreNetworkKind(credential: WifiCredential): RestoreNetworkKind? {
        if (!RestoreCompatibility.evaluate(credential).supported) return null
        val security = credential.security
        val passwordValue = credential.password
        return when {
            security.contains(SecurityType.WPA3) &&
                !security.contains(SecurityType.WPA2) &&
                !passwordValue.isNullOrBlank() -> RestoreNetworkKind.Wpa3
            security.any { it == SecurityType.WPA2 || it == SecurityType.WPA3 } &&
                !passwordValue.isNullOrBlank() -> RestoreNetworkKind.Wpa2
            security.contains(SecurityType.OWE) -> RestoreNetworkKind.EnhancedOpen
            security.contains(SecurityType.OPEN) || passwordValue.isNullOrBlank() -> RestoreNetworkKind.Open
            else -> null
        }
    }
}
