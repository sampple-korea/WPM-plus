package com.sampple.wifivaultrestore.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReporterTest {
    @Test
    fun redactsKeyValueJsonAndWifiQrSecrets() {
        val report = """
            password: cafe secret value
            passphrase=my secret value
            {"psk":"json-secret"}
            {"password":"json pass","psk":"json psk"}
            WIFI:T:WPA;S:Cafe;P:qr-secret;H:false;;
            WIFI:T:WPA;S:Escaped;P:pa\;ss;H:false;;
            WIFI:P:first-field-secret;S:Cafe;;
        """.trimIndent()

        val redacted = CrashReporter.redactSecretsForReport(report)

        assertFalse(redacted.contains("cafe-secret"))
        assertFalse(redacted.contains("cafe secret value"))
        assertFalse(redacted.contains("my secret value"))
        assertFalse(redacted.contains("json-secret"))
        assertFalse(redacted.contains("json pass"))
        assertFalse(redacted.contains("json psk"))
        assertFalse(redacted.contains("qr-secret"))
        assertFalse(redacted.contains("pa\\;ss"))
        assertFalse(redacted.contains("ss;H:false"))
        assertFalse(redacted.contains("first-field-secret"))
        assertTrue(redacted.contains("password: [redacted]"))
        assertTrue(redacted.contains("passphrase=[redacted]"))
        assertTrue(redacted.contains("\"psk\":\"[redacted]\""))
        assertTrue(redacted.contains("\"password\":\"[redacted]\""))
        assertTrue(redacted.contains("P:[redacted]"))
    }
}
