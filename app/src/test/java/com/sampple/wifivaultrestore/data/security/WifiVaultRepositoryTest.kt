package com.sampple.wifivaultrestore.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WifiVaultRepositoryTest {
    @Test
    fun archiveLockedVaultMovesActiveFileToTimestampedBackup() {
        val filesDir = Files.createTempDirectory("wpm-vault-repository").toFile()
        try {
            val vaultFile = File(filesDir, "wifi-vault.enc.json").apply {
                writeText("locked-payload", Charsets.UTF_8)
            }

            val backup = WifiVaultRepository.archiveLockedVault(
                vaultFile = vaultFile,
                filesDir = filesDir,
                timestampMillis = 1234L,
            )

            assertEquals(File(filesDir, "wifi-vault.locked.1234.enc.json"), backup)
            assertFalse(vaultFile.exists())
            assertTrue(backup?.exists() == true)
            assertEquals("locked-payload", backup?.readText(Charsets.UTF_8))
        } finally {
            filesDir.deleteRecursively()
        }
    }
}
