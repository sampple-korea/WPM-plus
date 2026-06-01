package com.sampple.wifivaultrestore.data.security

import com.sampple.wifivaultrestore.data.SecurityType
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

    @Test
    fun passwordCarryingSecurityPolicyOnlyKeepsSecretsForPasswordNetworks() {
        assertTrue(setOf(SecurityType.WPA2).canCarryVaultPassword())
        assertTrue(setOf(SecurityType.WPA3).canCarryVaultPassword())
        assertTrue(setOf(SecurityType.EAP).canCarryVaultPassword())
        assertTrue(setOf(SecurityType.WEP).canCarryVaultPassword())
        assertFalse(setOf(SecurityType.OPEN).canCarryVaultPassword())
        assertFalse(setOf(SecurityType.OWE).canCarryVaultPassword())
        assertFalse(setOf(SecurityType.OPEN, SecurityType.OWE).canCarryVaultPassword())
    }
}
