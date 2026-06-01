package com.sampple.wifivaultrestore.data.importer

import com.sampple.wifivaultrestore.data.CredentialSource
import com.sampple.wifivaultrestore.data.SecurityType
import com.sampple.wifivaultrestore.data.VaultData
import com.sampple.wifivaultrestore.data.WifiCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            ssid,security,password,hidden,autojoin,note
            "Cafe, Main",wpa2,cafepass,false,true,"second floor"
        """.trimIndent()

        val result = WifiImportParser.parseText(csv)

        assertEquals(1, result.importedCount)
        assertEquals("Cafe, Main", result.credentials.single().ssid)
        assertEquals("second floor", result.credentials.single().note)
    }

    @Test
    fun parsesPlainCsvLargerThanEncryptedImportLimit() {
        val note = "a".repeat(8 * 1024 * 1024 + 64)
        val csv = "ssid,security,password,note\nLarge,WPA2,secretpass,$note"

        val result = WifiImportParser.parse("large.csv", csv.toByteArray(Charsets.UTF_8))

        assertEquals(1, result.importedCount)
        assertEquals("Large", result.credentials.single().ssid)
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

    @Test
    fun dropsPasswordsFromPasswordlessSecurityImports() {
        val json = """{"ssid":"Guest","security":"OPEN","password":"legacy-open-secret"}"""
        val csv = "ssid,security,password\nEnhanced,OWE,legacy-owe-secret"
        val qr = "WIFI:T:nopass;S:Lobby;P:legacy-qr-secret;;"

        assertNull(WifiImportParser.parseText(json).credentials.single().password)
        assertNull(WifiImportParser.parseText(csv).credentials.single().password)
        assertNull(WifiImportParser.parseText(qr).credentials.single().password)
    }

    @Test
    fun roundTripsPortableVaultGzip() {
        val credential = WifiCredential.create(
            ssid = "Lab",
            security = setOf(SecurityType.WPA2),
            password = "labpass123",
            note = "rack room",
        )

        val bytes = VaultExportCodec.exportGzip(VaultData(credentials = listOf(credential)))
        val result = WifiImportParser.parse("vault.wpmv.json.gz", bytes)

        assertEquals(1, result.importedCount)
        assertEquals("rack room", result.credentials.single().note)
        assertEquals("labpass123", result.credentials.single().password)
    }

    @Test
    fun roundTripsEncryptedVaultExport() {
        val credential = WifiCredential.create(
            ssid = "Private",
            security = setOf(SecurityType.WPA3),
            password = "privatepass123",
        )

        val bytes = VaultExportCodec.exportEncryptedGzip(
            data = VaultData(credentials = listOf(credential)),
            password = "export-password",
        )
        val result = WifiImportParser.parse("vault.wpmv.json", bytes, "export-password")

        assertEquals(1, result.importedCount)
        assertEquals("Private", result.credentials.single().ssid)
    }

    private fun gzip(text: String): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return output.toByteArray()
    }
}
