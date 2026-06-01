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
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class VaultLoadResult(
    val data: VaultData,
    val lockedVaultBackupName: String? = null,
)

class WifiVaultRepository(context: Context) {
    private val appContext = context.applicationContext
    private val crypto = VaultCrypto()
    private val vaultFile: File = File(appContext.filesDir, "wifi-vault.enc.json")

    suspend fun load(): VaultData = loadWithStatus().data

    suspend fun loadWithStatus(): VaultLoadResult = withContext(Dispatchers.IO) {
        if (!vaultFile.exists()) return@withContext VaultLoadResult(VaultData())
        runCatching {
            val envelope = JSONObject(vaultFile.readText(Charsets.UTF_8))
            val blob = EncryptedBlob(
                keyAlias = envelope.optString("keyAlias"),
                iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP),
                cipherText = Base64.decode(envelope.getString("cipherText"), Base64.NO_WRAP),
            )
            val plain = crypto.decrypt(blob)
            VaultData.fromJson(JSONObject(String(plain, Charsets.UTF_8)))
                .let(::VaultLoadResult)
        }.getOrElse { error ->
            if (error is VaultLockedException) {
                val backup = runCatching { archiveLockedVault(vaultFile, appContext.filesDir) }
                    .getOrElse { throw error }
                VaultLoadResult(
                    data = VaultData(),
                    lockedVaultBackupName = backup?.name,
                )
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
        writeAtomically(envelope.toString().toByteArray(Charsets.UTF_8))
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

    suspend fun replaceCredential(originalId: String, replacement: WifiCredential): VaultData {
        val current = load()
        val updatedCredentials = current.credentials
            .filterNot { it.id == originalId || it.id == replacement.id }
            .plus(replacement)
            .sortedBy { it.ssid.lowercase() }
        val updated = current.copy(credentials = updatedCredentials)
        replace(updated)
        return updated
    }

    suspend fun deleteCredential(credentialId: String): VaultData {
        val current = load()
        val updated = current.copy(credentials = current.credentials.filterNot { it.id == credentialId })
        replace(updated)
        return updated
    }

    suspend fun appendReport(report: OperationReport): VaultData {
        val current = load()
        val updated = current.copy(reports = (listOf(report) + current.reports).take(100))
        replace(updated)
        return updated
    }

    private fun writeAtomically(bytes: ByteArray) {
        val temp = File(appContext.filesDir, "${vaultFile.name}.tmp")
        FileOutputStream(temp).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        runCatching {
            Files.move(
                temp.toPath(),
                vaultFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.getOrElse { error ->
            if (error !is AtomicMoveNotSupportedException) throw error
            Files.move(temp.toPath(), vaultFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        internal fun archiveLockedVault(
            vaultFile: File,
            filesDir: File,
            timestampMillis: Long = System.currentTimeMillis(),
        ): File? {
            if (!vaultFile.exists()) return null

            val backup = lockedVaultBackupFile(filesDir, timestampMillis)
            return runCatching {
                Files.move(vaultFile.toPath(), backup.toPath(), StandardCopyOption.ATOMIC_MOVE)
                backup
            }.recoverCatching { error ->
                if (error !is AtomicMoveNotSupportedException) throw error
                Files.move(vaultFile.toPath(), backup.toPath())
                backup
            }.getOrElse {
                vaultFile.copyTo(backup, overwrite = false)
                if (!vaultFile.delete()) Files.delete(vaultFile.toPath())
                backup
            }
        }

        private fun lockedVaultBackupFile(filesDir: File, timestampMillis: Long): File {
            val baseName = "wifi-vault.locked.$timestampMillis.enc.json"
            var backup = File(filesDir, baseName)
            var suffix = 1
            while (backup.exists()) {
                backup = File(filesDir, "wifi-vault.locked.$timestampMillis.$suffix.enc.json")
                suffix += 1
            }
            return backup
        }
    }
}
