package com.sampple.wifivaultrestore.data.share

import com.sampple.wifivaultrestore.data.SecurityType
import com.sampple.wifivaultrestore.data.WifiCredential
import com.sampple.wifivaultrestore.data.canCarryVaultPassword

object WifiShareFormatter {
    fun qrPayload(credential: WifiCredential): String {
        val security = when {
            credential.security.contains(SecurityType.WEP) -> "WEP"
            credential.security.any { it == SecurityType.WPA2 || it == SecurityType.WPA3 || it == SecurityType.EAP } -> "WPA"
            credential.security.contains(SecurityType.OWE) -> "nopass"
            else -> "nopass"
        }
        return buildString {
            append("WIFI:")
            append("T:")
            append(escape(security))
            append(';')
            append("S:")
            append(escape(credential.ssid))
            append(';')
            credential.password?.takeIf { credential.security.canCarryVaultPassword() && it.isNotEmpty() }?.let { password ->
                append("P:")
                append(escape(password))
                append(';')
            }
            if (credential.hidden) {
                append("H:true;")
            }
            append(';')
        }
    }

    fun shareText(credential: WifiCredential): String {
        return buildString {
            appendLine(credential.ssid)
            appendLine(qrPayload(credential))
            if (credential.note != null) {
                appendLine()
                appendLine(credential.note)
            }
        }.trimEnd()
    }

    private fun escape(value: String): String {
        return buildString {
            value.forEach { char ->
                if (char in WIFI_QR_RESERVED) append('\\')
                append(char)
            }
        }
    }

    private val WIFI_QR_RESERVED = setOf('\\', ';', ',', ':', '"')
}
