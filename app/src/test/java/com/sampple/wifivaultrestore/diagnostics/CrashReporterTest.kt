package com.sampple.wifivaultrestore.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReporterTest {
    @Test
    fun redactsKeyValueJsonAndWifiQrSecrets() {
        val report = """
            password: cafe-secret
            {"psk":"json-secret"}
            WIFI:T:WPA;S:Cafe;P:qr-secret;H:false;;
        """.trimIndent()

        val redacted = CrashReporter.redactSecretsForReport(report)

        assertFalse(redacted.contains("cafe-secret"))
        assertFalse(redacted.contains("json-secret"))
        assertFalse(redacted.contains("qr-secret"))
        assertTrue(redacted.contains("password=[redacted]"))
        assertTrue(redacted.contains("psk=[redacted]"))
        assertTrue(redacted.contains("P:[redacted]"))
    }
}
