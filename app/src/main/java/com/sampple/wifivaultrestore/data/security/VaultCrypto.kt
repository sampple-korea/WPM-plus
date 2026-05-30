package com.sampple.wifivaultrestore.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.Key
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class VaultLockedException(message: String, cause: Throwable? = null) : Exception(message, cause)

class VaultCrypto {
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun encrypt(plainText: ByteArray): EncryptedBlob {
        val key = getOrCreateKey(CURRENT_KEY_ALIAS)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key)
        } catch (ex: android.security.keystore.UserNotAuthenticatedException) {
            throw VaultLockedException("User authentication is required to unlock the vault.", ex)
        }
        return EncryptedBlob(
            keyAlias = CURRENT_KEY_ALIAS,
            iv = cipher.iv,
            cipherText = cipher.doFinal(plainText),
        )
    }

    fun decrypt(blob: EncryptedBlob): ByteArray {
        val alias = blob.keyAlias.ifBlank { LEGACY_KEY_ALIAS }
        val key = getExistingKey(alias) ?: getOrCreateKey(CURRENT_KEY_ALIAS)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, blob.iv))
        } catch (ex: android.security.keystore.UserNotAuthenticatedException) {
            throw VaultLockedException("User authentication is required to unlock the vault.", ex)
        }
        return cipher.doFinal(blob.cipherText)
    }

    private fun getExistingKey(alias: String): SecretKey? {
        return keyStore.getKey(alias, null)?.asSecretKey()
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        getExistingKey(alias)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    private fun Key.asSecretKey(): SecretKey = this as SecretKey

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val GCM_TAG_BITS = 128
        private const val CURRENT_KEY_ALIAS = "wpm_plus_master_v2"
        private const val LEGACY_KEY_ALIAS = "wifi_vault_restore_master_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

data class EncryptedBlob(
    val keyAlias: String,
    val iv: ByteArray,
    val cipherText: ByteArray,
)
