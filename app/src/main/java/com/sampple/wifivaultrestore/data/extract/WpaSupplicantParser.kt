package com.sampple.wifivaultrestore.data.extract

import com.sampple.wifivaultrestore.data.CredentialSource
import com.sampple.wifivaultrestore.data.SecurityType
import com.sampple.wifivaultrestore.data.WifiCredential

object WpaSupplicantParser {
    fun parse(text: String, source: CredentialSource): List<WifiCredential> {
        val blocks = Regex("network=\\{(.*?)\\}", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .map { it.groupValues[1] }

        return blocks.mapNotNull { block ->
            val values = parseBlock(block)
            val ssid = values["ssid"]?.unquoteSupplicantValue() ?: return@mapNotNull null
            val psk = values["psk"]?.unquoteSupplicantValue()?.takeIf { it != "*" && it.isNotBlank() }
            val keyManagement = values["key_mgmt"].orEmpty()
            val security = when {
                keyManagement.contains("OWE", ignoreCase = true) -> setOf(SecurityType.OWE)
                keyManagement.contains("NONE", ignoreCase = true) -> setOf(SecurityType.OPEN)
                keyManagement.contains("SAE", ignoreCase = true) -> setOf(SecurityType.WPA3)
                keyManagement.contains("WPA", ignoreCase = true) || !psk.isNullOrBlank() -> setOf(SecurityType.WPA2)
                else -> setOf(SecurityType.UNKNOWN)
            }
            WifiCredential.create(
                ssid = ssid,
                security = security,
                password = psk,
                hidden = values["scan_ssid"] == "1",
                source = source,
            )
        }.distinctBy { it.id }.toList()
    }

    private fun parseBlock(block: String): Map<String, String> {
        return block.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
            .associate { line ->
                val key = line.substringBefore("=").trim()
                val value = line.substringAfter("=").trim()
                key to value
            }
    }

    private fun String.unquoteSupplicantValue(): String {
        val trimmed = trim()
        if (trimmed.length < 2 || trimmed.first() != '"' || trimmed.last() != '"') return trimmed
        return buildString {
            var escaped = false
            trimmed.substring(1, trimmed.length - 1).forEach { char ->
                if (escaped) {
                    append(char)
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else {
                    append(char)
                }
            }
            if (escaped) append('\\')
        }
    }
}
