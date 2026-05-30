package com.sampple.wifivaultrestore.data

import java.security.MessageDigest
import java.util.Locale

enum class SecurityType {
    OPEN,
    OWE,
    WPA2,
    WPA3,
    WEP,
    EAP,
    UNKNOWN,
}

data class WifiCredential(
    val id: String,
    val ssid: String,
    val security: Set<SecurityType>,
    val password: String?,
    val hidden: Boolean = false,
    val autoJoin: Boolean = true,
    val note: String? = null,
    val source: CredentialSource = CredentialSource.Manual,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = createdAtMillis,
) {
    val hasPassword: Boolean
        get() = !password.isNullOrEmpty()

    val canRestore: Boolean
        get() = security.any { it == SecurityType.OPEN || it == SecurityType.OWE } || hasPassword

    val redactedSummary: String
        get() = buildString {
            append(ssid)
            append(" / ")
            append(securityLabel(security))
            append(" / ")
            append(if (hasPassword) "password saved" else "no password")
            if (hidden) append(" / hidden")
        }

    companion object {
        fun create(
            ssid: String,
            security: Set<SecurityType>,
            password: String?,
            hidden: Boolean = false,
            autoJoin: Boolean = true,
            note: String? = null,
            source: CredentialSource = CredentialSource.Manual,
            nowMillis: Long = System.currentTimeMillis(),
        ): WifiCredential {
            val normalizedSecurity = if (security.isEmpty()) setOf(SecurityType.UNKNOWN) else security
            return WifiCredential(
                id = stableId(ssid, normalizedSecurity, hidden),
                ssid = ssid,
                security = normalizedSecurity,
                password = password?.takeIf { it.isNotEmpty() },
                hidden = hidden,
                autoJoin = autoJoin,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                source = source,
                createdAtMillis = nowMillis,
                updatedAtMillis = nowMillis,
            )
        }

        fun stableId(ssid: String, security: Set<SecurityType>, hidden: Boolean): String {
            val key = buildString {
                append(ssid.trim())
                append('|')
                append(security.map { it.name }.sorted().joinToString("+"))
                append('|')
                append(hidden)
            }
            return MessageDigest.getInstance("SHA-256")
                .digest(key.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .take(24)
        }
    }
}

enum class CredentialSource {
    Manual,
    QuickShare,
    Json,
    Csv,
    WifiQr,
    ShizukuShell,
    ShizukuRoot,
    RootFile,
    SystemDiagnostic,
}

fun securityLabel(security: Set<SecurityType>): String {
    val ordered = security.sortedBy { it.ordinal }.filter { it != SecurityType.UNKNOWN }
    return if (ordered.isEmpty()) {
        SecurityType.UNKNOWN.name
    } else {
        ordered.joinToString("/") { it.name.uppercase(Locale.US) }
    }
}

fun parseSecurityToken(raw: String): SecurityType {
    val token = raw.trim().uppercase(Locale.US)
    return when {
        token.isEmpty() -> SecurityType.UNKNOWN
        token == "OPEN" || token == "NONE" || token == "NOPASS" -> SecurityType.OPEN
        token == "OWE" -> SecurityType.OWE
        token == "WPA" || token == "WPA2" || token == "WPA-PSK" || token == "PSK" -> SecurityType.WPA2
        token == "SAE" || token == "WPA3" -> SecurityType.WPA3
        token == "WEP" -> SecurityType.WEP
        token.contains("EAP") || token.contains("ENTERPRISE") -> SecurityType.EAP
        token.contains("WPA3") || token.contains("SAE") -> SecurityType.WPA3
        token.contains("WPA") || token.contains("PSK") -> SecurityType.WPA2
        else -> SecurityType.UNKNOWN
    }
}

fun parseSecuritySet(raw: String): Set<SecurityType> {
    return raw
        .split(',', '/', '+', '|', ';', ' ')
        .map(::parseSecurityToken)
        .filter { it != SecurityType.UNKNOWN }
        .toSet()
        .ifEmpty { setOf(SecurityType.UNKNOWN) }
}
