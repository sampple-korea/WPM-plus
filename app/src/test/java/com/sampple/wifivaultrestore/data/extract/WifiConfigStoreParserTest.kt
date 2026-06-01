package com.sampple.wifivaultrestore.data.extract

import com.sampple.wifivaultrestore.data.CredentialSource
import com.sampple.wifivaultrestore.data.SecurityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiConfigStoreParserTest {
    @Test
    fun parsesWifiConfigStoreNetwork() {
        val xml = """
            <WifiConfigStoreData>
              <NetworkList>
                <Network>
                  <WifiConfiguration>
                    <string name="ConfigKey">&quot;Office&quot;WPA_PSK</string>
                    <string name="SSID">&quot;Office&quot;</string>
                    <string name="PreSharedKey">&quot;secretpass&quot;</string>
                    <boolean name="HiddenSSID" value="true" />
                  </WifiConfiguration>
                </Network>
              </NetworkList>
            </WifiConfigStoreData>
        """.trimIndent()

        val result = WifiConfigStoreParser.parse(xml, CredentialSource.ShizukuRoot)

        assertEquals(1, result.size)
        assertEquals("Office", result.single().ssid)
        assertEquals("secretpass", result.single().password)
        assertTrue(result.single().security.contains(SecurityType.WPA2))
        assertTrue(result.single().hidden)
    }

    @Test
    fun parsesEnhancedOpenConfigKey() {
        val xml = """
            <WifiConfigStoreData>
              <NetworkList>
                <Network>
                  <WifiConfiguration>
                    <string name="ConfigKey">&quot;Cafe&quot;SECURITY_TYPE_OWE</string>
                    <string name="SSID">&quot;Cafe&quot;</string>
                  </WifiConfiguration>
                </Network>
              </NetworkList>
            </WifiConfigStoreData>
        """.trimIndent()

        val result = WifiConfigStoreParser.parse(xml, CredentialSource.ShizukuRoot)

        assertEquals(1, result.size)
        assertEquals("Cafe", result.single().ssid)
        assertTrue(result.single().security.contains(SecurityType.OWE))
    }
}
