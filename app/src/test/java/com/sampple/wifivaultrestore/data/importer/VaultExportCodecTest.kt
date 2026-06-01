package com.sampple.wifivaultrestore.data.importer

import org.json.JSONObject
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64

class VaultExportCodecTest {
    @Test
    fun ignoresLargePlaintextBeforeEncryptedSizeLimit() {
        val plaintext = ByteArray(9 * 1024 * 1024) { 'a'.code.toByte() }

        assertNull(VaultExportCodec.decodeEncryptedIfPresent(plaintext, null))
    }

    @Test
    fun rejectsEncryptedExportsWithUnsupportedIterations() {
        val envelope = JSONObject()
            .put("format", "wpm-plus.encrypted")
            .put("formatVersion", 2)
            .put("payloadEncoding", "gzip+json")
            .put("cipher", "AES/GCM/NoPadding")
            .put("kdf", "PBKDF2WithHmacSHA256")
            .put("iterations", 10)
            .put("salt", ByteArray(16).base64())
            .put("iv", ByteArray(12).base64())
            .put("cipherText", byteArrayOf(1, 2, 3).base64())

        assertThrows(IllegalArgumentException::class.java) {
            VaultExportCodec.decodeEncryptedIfPresent(
                envelope.toString().toByteArray(Charsets.UTF_8),
                "password",
            )
        }
    }

    @Test
    fun rejectsEncryptedExportsWithUnsupportedCipher() {
        val envelope = JSONObject()
            .put("format", "wpm-plus.encrypted")
            .put("cipher", "AES/CBC/PKCS5Padding")
            .put("kdf", "PBKDF2WithHmacSHA256")
            .put("iterations", 210_000)
            .put("salt", ByteArray(16).base64())
            .put("iv", ByteArray(12).base64())
            .put("cipherText", byteArrayOf(1, 2, 3).base64())

        assertThrows(IllegalArgumentException::class.java) {
            VaultExportCodec.decodeEncryptedIfPresent(
                envelope.toString().toByteArray(Charsets.UTF_8),
                "password",
            )
        }
    }

    private fun ByteArray.base64(): String = Base64.getEncoder().encodeToString(this)
}
