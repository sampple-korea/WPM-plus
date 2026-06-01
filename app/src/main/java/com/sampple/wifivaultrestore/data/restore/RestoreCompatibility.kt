package com.sampple.wifivaultrestore.data.restore

import com.sampple.wifivaultrestore.data.SecurityType
import com.sampple.wifivaultrestore.data.WifiCredential

enum class RestoreSkipReason {
    BlankSsid,
    MissingPassword,
    UnsupportedEnterprise,
    UnsupportedWep,
    UnsupportedSecurity,
    InvalidPassphrase,
}

data class RestorePlanItem(
    val credential: WifiCredential,
    val supported: Boolean,
    val reason: RestoreSkipReason? = null,
)

data class RestorePlan(
    val items: List<RestorePlanItem>,
) {
    val supported: List<RestorePlanItem> = items.filter { it.supported }
    val skipped: List<RestorePlanItem> = items.filterNot { it.supported }
    val supportedCredentials: List<WifiCredential> = supported.map { it.credential }
}

object RestoreCompatibility {
    fun plan(credentials: List<WifiCredential>): RestorePlan {
        return RestorePlan(credentials.map(::evaluate))
    }

    fun evaluate(credential: WifiCredential): RestorePlanItem {
        val security = credential.security
        val password = credential.password.orEmpty()
        return when {
            credential.ssid.isBlank() -> credential.skipped(RestoreSkipReason.BlankSsid)
            security.contains(SecurityType.EAP) -> credential.skipped(RestoreSkipReason.UnsupportedEnterprise)
            security.contains(SecurityType.WEP) -> credential.skipped(RestoreSkipReason.UnsupportedWep)
            security.contains(SecurityType.OPEN) || security.contains(SecurityType.OWE) -> credential.supported()
            security.any { it == SecurityType.WPA2 || it == SecurityType.WPA3 } -> {
                when {
                    password.isBlank() -> credential.skipped(RestoreSkipReason.MissingPassword)
                    !password.isValidWpaPassphrase() -> credential.skipped(RestoreSkipReason.InvalidPassphrase)
                    else -> credential.supported()
                }
            }
            credential.hasPassword -> credential.skipped(RestoreSkipReason.UnsupportedSecurity)
            else -> credential.skipped(RestoreSkipReason.MissingPassword)
        }
    }

    private fun WifiCredential.supported(): RestorePlanItem = RestorePlanItem(this, supported = true)

    private fun WifiCredential.skipped(reason: RestoreSkipReason): RestorePlanItem =
        RestorePlanItem(this, supported = false, reason = reason)

    private fun String.isValidWpaPassphrase(): Boolean {
        return length in WPA_PASSPHRASE_RANGE && all { it.code in ASCII_PRINTABLE_RANGE }
    }

    private val WPA_PASSPHRASE_RANGE = 8..63
    private val ASCII_PRINTABLE_RANGE = 32..126
}
