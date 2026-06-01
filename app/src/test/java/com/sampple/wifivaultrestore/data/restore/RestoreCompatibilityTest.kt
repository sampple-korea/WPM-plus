package com.sampple.wifivaultrestore.data.restore

import com.sampple.wifivaultrestore.data.CredentialSource
import com.sampple.wifivaultrestore.data.SecurityType
import com.sampple.wifivaultrestore.data.WifiCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreCompatibilityTest {
    @Test
    fun acceptsOpenAndValidWpaNetworks() {
        val open = credential("Guest", setOf(SecurityType.OPEN), null)
        val wpa = credential("Office", setOf(SecurityType.WPA2), "secretpass")

        val plan = RestoreCompatibility.plan(listOf(open, wpa))

        assertEquals(2, plan.supported.size)
        assertTrue(plan.skipped.isEmpty())
    }

    @Test
    fun rejectsUnsupportedOrInvalidNetworksWithReasons() {
        val enterprise = credential("Corp", setOf(SecurityType.EAP), "identity-password")
        val wep = credential("Legacy", setOf(SecurityType.WEP), "abcde")
        val shortWpa = credential("Short", setOf(SecurityType.WPA2), "short")
        val missing = credential("Missing", setOf(SecurityType.WPA2), null)

        val plan = RestoreCompatibility.plan(listOf(enterprise, wep, shortWpa, missing))

        assertTrue(plan.supported.isEmpty())
        assertEquals(
            listOf(
                RestoreSkipReason.UnsupportedEnterprise,
                RestoreSkipReason.UnsupportedWep,
                RestoreSkipReason.InvalidPassphrase,
                RestoreSkipReason.MissingPassword,
            ),
            plan.skipped.map { it.reason },
        )
    }

    @Test
    fun canRestoreMatchesModernAndroidSuggestionSupport() {
        assertFalse(credential("Corp", setOf(SecurityType.EAP), "password").canRestore)
        assertFalse(credential("Legacy", setOf(SecurityType.WEP), "password").canRestore)
        assertFalse(credential("Office", setOf(SecurityType.WPA2), null).canRestore)
        assertFalse(credential("Short", setOf(SecurityType.WPA2), "short").canRestore)
        assertTrue(credential("Office", setOf(SecurityType.WPA2), "secretpass").canRestore)
    }

    private fun credential(
        ssid: String,
        security: Set<SecurityType>,
        password: String?,
    ): WifiCredential = WifiCredential.create(
        ssid = ssid,
        security = security,
        password = password,
        source = CredentialSource.Json,
    )
}
