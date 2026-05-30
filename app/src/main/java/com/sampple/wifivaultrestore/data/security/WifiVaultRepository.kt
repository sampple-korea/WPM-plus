package com.sampple.wifivaultrestore.data.security

import android.content.Context
import android.util.Base64
import com.sampple.wifivaultrestore.data.VaultData
import com.sampple.wifivaultrestore.data.WifiCredential
import com.sampple.wifivaultrestore.data.report.OperationReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class WifiVaultRepository(context: Context) {
    private val appContext = context.applicationContext
    private val crypto = VaultCrypto()
    private val vaultFile: File = File(appContext.filesDir, "wifi-vault.enc.json")

    suspend fun load(): VaultData = withContext(Dispatchers.IO) {
        if (!vaultFile.exists()) return@withContext VaultData()
        runCatching {
            val envelope = JSONObject(vaultFile.readText(Charsets.UTF_8))
            val blob = EncryptedBlob(
                keyAlias = envelope.optString("keyAlias"),
                iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP),
                cipherText = Base64.decode(envelope.getString("cipherText"), Base64.NO_WRAP),
            )
            val plain = crypto.decrypt(blob)
            VaultData.fromJson(JSONObject(String(plain, Charsets.UTF_8)))
        }.getOrElse { error ->
            if (error is VaultLockedException) {
                archiveLockedVault()
                VaultData()
            } else {
                throw error
            }
        }
    }

    suspend fun replace(data: VaultData) = withContext(Dispatchers.IO) {
        val blob = crypto.encrypt(data.toJson().toString().toByteArray(Charsets.UTF_8))
        val envelope = JSONObject()
            .put("version", 1)
            .put("keyAlias", blob.keyAlias)
            .put("iv", Base64.encodeToString(blob.iv, Base64.NO_WRAP))
            .put("cipherText", Base64.encodeToString(blob.cipherText, Base64.NO_WRAP))
        vaultFile.writeText(envelope.toString(), Charsets.UTF_8)
    }

    suspend fun upsertCredentials(newCredentials: List<WifiCredential>): VaultData {
        val current = load()
        val byId = current.credentials.associateBy { it.id }.toMutableMap()
        newCredentials.forEach { credential ->
            val existing = byId[credential.id]
            byId[credential.id] = if (existing == null) {
                credential
            } else {
                credential.copy(
                    password = credential.password ?: existing.password,
                    note = credential.note ?: existing.note,
                    createdAtMillis = existing.createdAtMillis,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }
        }
        val updated = current.copy(credentials = byId.values.sortedBy { it.ssid.lowercase() })
        replace(updated)
        return updated
    }

    suspend fun updateNote(credentialId: String, note: String?): VaultData {
        val current = load()
        val updated = current.copy(
            credentials = current.credentials.map { credential ->
                if (credential.id == credentialId) {
                    credential.copy(
                        note = note?.trim()?.takeIf { it.isNotEmpty() },
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                } else {
                    credential
                }
            },
        )
        replace(updated)
        return updated
    }

    suspend fun appendReport(report: OperationReport): VaultData {
        val current = load()
        val updated = current.copy(reports = (listOf(report) + current.reports).take(100))
        replace(updated)
        return updated
    }

    private fun archiveLockedVault() {
        val backup = File(
            appContext.filesDir,
            "wifi-vault.locked.${System.currentTimeMillis()}.enc.json",
        )
        runCatching { vaultFile.copyTo(backup, overwrite = false) }
        runCatching { vaultFile.delete() }
    }
}
