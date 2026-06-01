package com.sampple.wifivaultrestore.data.share

import com.sampple.wifivaultrestore.data.CredentialSource
import com.sampple.wifivaultrestore.data.SecurityType
import com.sampple.wifivaultrestore.data.WifiCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiShareFormatterTest {
    @Test
    fun createsEscapedWifiQrPayload() {
        val credential = WifiCredential.create(
            ssid = "Office:East",
            security = setOf(SecurityType.WPA2),
            password = "pa;ss,word",
            source = CredentialSource.Json,
        )

        assertEquals(
            "WIFI:T:WPA;S:Office\\:East;P:pa\\;ss\\,word;;",
            WifiShareFormatter.qrPayload(credential),
        )
    }

    @Test
    fun includesNoteInShareTextWithoutChangingQrPayload() {
        val credential = WifiCredential.create(
            ssid = "Guest",
            security = setOf(SecurityType.OPEN),
            password = null,
            note = "Lobby network",
            source = CredentialSource.Json,
        )

        val text = WifiShareFormatter.shareText(credential)

        assertTrue(text.contains("WIFI:T:nopass;S:Guest;;"))
        assertTrue(text.contains("Lobby network"))
    }

    @Test
    fun doesNotShareLegacyPasswordsForPasswordlessSecurity() {
        val credential = WifiCredential(
            id = "legacy-open",
            ssid = "Guest",
            security = setOf(SecurityType.OPEN),
            password = "legacy-open-secret",
        )

        val payload = WifiShareFormatter.qrPayload(credential)

        assertEquals("WIFI:T:nopass;S:Guest;;", payload)
        assertFalse(payload.contains("legacy-open-secret"))
        assertFalse(payload.contains("P:"))
    }
}
