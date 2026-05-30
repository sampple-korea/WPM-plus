package com.sampple.wifivaultrestore.data.extract

import com.sampple.wifivaultrestore.data.CredentialSource
import com.sampple.wifivaultrestore.data.SecurityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WpaSupplicantParserTest {
    @Test
    fun parsesSupplicantNetworks() {
        val conf = """
            network={
                ssid="Office"
                psk="secretpass"
                key_mgmt=WPA-PSK
                scan_ssid=1
            }
            network={
                ssid="Guest"
                key_mgmt=NONE
            }
        """.trimIndent()

        val result = WpaSupplicantParser.parse(conf, CredentialSource.RootFile)

        assertEquals(2, result.size)
        assertEquals("secretpass", result.first { it.ssid == "Office" }.password)
        assertTrue(result.first { it.ssid == "Office" }.hidden)
        assertTrue(result.first { it.ssid == "Guest" }.security.contains(SecurityType.OPEN))
    }
}
