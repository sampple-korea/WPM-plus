package com.sampple.wifivaultrestore.data.importer

import com.sampple.wifivaultrestore.data.VaultData
import com.sampple.wifivaultrestore.data.asSequence
import com.sampple.wifivaultrestore.data.toJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class ImportPasswordRequiredException : IllegalArgumentException(
    "This Wi-Fi vault export is encrypted. Enter the export password to import it.",
)

object VaultExportCodec {
    private const val PORTABLE_FORMAT = "wpm-plus"
    private const val ENCRYPTED_FORMAT = "wpm-plus.encrypted"
    private const val LEGACY_ENCRYPTED_FORMAT = "wifi-vault-restore.encrypted"
    private const val FORMAT_VERSION = 2
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun exportGzip(data: VaultData): ByteArray {
        return gzip(portableJson(data).toString(2).toByteArray(Charsets.UTF_8))
    }

    fun exportEncryptedGzip(data: VaultData, password: String): ByteArray {
        require(password.isNotBlank()) { "Export password must not be blank." }
        val salt = randomBytes(SALT_BYTES)
        val iv = randomBytes(IV_BYTES)
        val key = deriveKey(password, salt, ITERATIONS)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val cipherText = cipher.doFinal(exportGzip(data))
        return JSONObject()
            .put("format", ENCRYPTED_FORMAT)
            .put("formatVersion", FORMAT_VERSION)
            .put("payloadEncoding", "gzip+json")
            .put("cipher", CIPHER)
            .put("kdf", KDF)
            .put("iterations", ITERATIONS)
            .put("salt", salt.base64())
            .put("iv", iv.base64())
            .put("cipherText", cipherText.base64())
            .toString(2)
            .toByteArray(Charsets.UTF_8)
    }

    fun decodeEncryptedIfPresent(bytes: ByteArray, password: String?): VaultData? {
        val text = bytes.toString(Charsets.UTF_8).trimStart()
        if (!text.startsWith("{")) return null
        val envelope = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val format = envelope.optString("format")
        if (format != ENCRYPTED_FORMAT && format != LEGACY_ENCRYPTED_FORMAT) return null
        if (password.isNullOrBlank()) throw ImportPasswordRequiredException()

        val iterations = envelope.optInt("iterations", ITERATIONS)
        val salt = envelope.getString("salt").base64Decode()
        val iv = envelope.getString("iv").base64Decode()
        val cipherText = envelope.getString("cipherText").base64Decode()
        val key = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance(envelope.optString("cipher", CIPHER))
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val gzip = cipher.doFinal(cipherText)
        val json = gunzip(gzip).toString(Charsets.UTF_8)
        return VaultData.fromJson(JSONObject(json))
    }

    private fun portableJson(data: VaultData): JSONObject {
        return JSONObject()
            .put("format", PORTABLE_FORMAT)
            .put("formatVersion", FORMAT_VERSION)
            .put("exportedAtMillis", System.currentTimeMillis())
            .put("credentials", JSONArray(data.credentials.map { it.toJson() }))
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes) }
        return output.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): ByteArray {
        return GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KDF)
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun randomBytes(size: Int): ByteArray {
        return ByteArray(size).also { SecureRandom().nextBytes(it) }
    }

    private fun ByteArray.base64(): String = Base64.getEncoder().encodeToString(this)

    private fun String.base64Decode(): ByteArray = Base64.getDecoder().decode(this)
}
