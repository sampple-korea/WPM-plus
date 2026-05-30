package com.sampple.wifivaultrestore.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class VaultLockedException(message: String, cause: Throwable? = null) : Exception(message, cause)

class VaultCrypto(private val context: Context) {
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun encrypt(plainText: ByteArray): EncryptedBlob {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key)
        } catch (ex: android.security.keystore.UserNotAuthenticatedException) {
            throw VaultLockedException("User authentication is required to unlock the vault.", ex)
        }
        return EncryptedBlob(
            keyAlias = KEY_ALIAS,
            iv = cipher.iv,
            cipherText = cipher.doFinal(plainText),
        )
    }

    fun decrypt(blob: EncryptedBlob): ByteArray {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, blob.iv))
        } catch (ex: android.security.keystore.UserNotAuthenticatedException) {
            throw VaultLockedException("User authentication is required to unlock the vault.", ex)
        }
        return cipher.doFinal(blob.cipherText)
    }

    private fun getOrCreateKey(): SecretKey {
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)

        if (context.isDeviceSecure()) {
            builder
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(
                    AUTH_VALID_SECONDS,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                )
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    private fun Context.isDeviceSecure(): Boolean {
        val manager = getSystemService(android.app.KeyguardManager::class.java)
        return manager?.isDeviceSecure == true
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AUTH_VALID_SECONDS = 300
        private const val GCM_TAG_BITS = 128
        private const val KEY_ALIAS = "wifi_vault_restore_master_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

data class EncryptedBlob(
    val keyAlias: String,
    val iv: ByteArray,
    val cipherText: ByteArray,
)
