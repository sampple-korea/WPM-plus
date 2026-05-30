package com.sampple.wifivaultrestore.data.importer

import com.sampple.wifivaultrestore.data.CredentialSource
import com.sampple.wifivaultrestore.data.SecurityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class WifiImportParserTest {
    @Test
    fun parsesSamsungQuickShareGzip() {
        val json = """
            [
              {"ssid":"Office","security":["WPA2","WPA3"],"password":"secretpass","hidden":false,"autojoin":true,"private":false},
              {"ssid":"Guest","security":["OPEN","OWE"],"password":"","hidden":false,"autojoin":true,"private":false}
            ]
        """.trimIndent()

        val result = WifiImportParser.parse("WiFi.json.gz", gzip(json))

        assertEquals(2, result.importedCount)
        assertEquals(CredentialSource.QuickShare, result.credentials.first().source)
        assertTrue(result.credentials.first().security.contains(SecurityType.WPA2))
    }

    @Test
    fun parsesCsvWithQuotedSsid() {
        val csv = """
            ssid,security,password,hidden,autojoin
            "Cafe, Main",wpa2,cafepass,false,true
        """.trimIndent()

        val result = WifiImportParser.parseText(csv)

        assertEquals(1, result.importedCount)
        assertEquals("Cafe, Main", result.credentials.single().ssid)
    }

    @Test
    fun parsesEscapedWifiQr() {
        val qr = """WIFI:T:WPA;S:Cafe\;Main;P:pa\:ss;H:true;;"""

        val result = WifiImportParser.parseText(qr)

        assertEquals(1, result.importedCount)
        assertEquals("Cafe;Main", result.credentials.single().ssid)
        assertEquals("pa:ss", result.credentials.single().password)
        assertTrue(result.credentials.single().hidden)
    }

    private fun gzip(text: String): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return output.toByteArray()
    }
}
