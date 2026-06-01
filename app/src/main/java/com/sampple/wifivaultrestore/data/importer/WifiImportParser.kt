package com.sampple.wifivaultrestore.data.importer

import com.sampple.wifivaultrestore.data.CredentialSource
import com.sampple.wifivaultrestore.data.SecurityType
import com.sampple.wifivaultrestore.data.WifiCredential
import com.sampple.wifivaultrestore.data.asSequence
import com.sampple.wifivaultrestore.data.parseSecuritySet
import com.sampple.wifivaultrestore.data.parseSecurityToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

data class ImportOutcome(
    val credentials: List<WifiCredential>,
    val skipped: List<ImportSkip>,
) {
    val importedCount: Int = credentials.size
    val skippedCount: Int = skipped.size
}

data class ImportSkip(
    val index: Int,
    val reason: String,
)

object WifiImportParser {
    private const val MAX_IMPORT_BYTES = 16 * 1024 * 1024
    private const val MAX_DECOMPRESSED_BYTES = 16 * 1024 * 1024

    fun parse(fileName: String?, bytes: ByteArray, password: String? = null): ImportOutcome {
        require(bytes.size <= MAX_IMPORT_BYTES) { "Import file is too large." }
        VaultExportCodec.decodeEncryptedIfPresent(bytes, password)?.let { vault ->
            return ImportOutcome(vault.credentials.distinctBy { it.id }, emptyList())
        }
        val plain = maybeGunzip(fileName, bytes).toString(Charsets.UTF_8).trim()
        return when {
            plain.startsWith("[") || plain.startsWith("{") -> parseJson(plain)
            plain.startsWith("WIFI:", ignoreCase = true) -> parseWifiQrLines(plain)
            else -> parseCsv(plain)
        }
    }

    fun parseText(text: String): ImportOutcome {
        val trimmed = text.trim()
        return if (trimmed.startsWith("WIFI:", ignoreCase = true)) {
            parseWifiQrLines(trimmed)
        } else if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            parseJson(trimmed)
        } else {
            parseCsv(trimmed)
        }
    }

    private fun parseJson(text: String): ImportOutcome {
        val rootArray = when {
            text.startsWith("[") -> JSONArray(text)
            else -> JSONObject(text).let { root ->
                root.optJSONArray("networks")
                    ?: root.optJSONArray("credentials")
                    ?: JSONArray().put(root)
            }
        }

        val credentials = mutableListOf<WifiCredential>()
        val skipped = mutableListOf<ImportSkip>()
        rootArray.asSequence().forEachIndexed { index, raw ->
            val json = raw as? JSONObject
            if (json == null) {
                skipped += ImportSkip(index, "import.json_item_not_object")
                return@forEachIndexed
            }
            val credential = parseJsonCredential(json)
            if (credential == null) {
                skipped += ImportSkip(index, "import.missing_ssid_or_security")
            } else {
                credentials += credential
            }
        }
        return ImportOutcome(credentials.distinctBy { it.id }, skipped)
    }

    private fun parseJsonCredential(json: JSONObject): WifiCredential? {
        val ssid = json.optString("ssid", json.optString("SSID")).trimControl()
        if (ssid.isBlank()) return null

        val security = when (val securityArray = json.optJSONArray("security")) {
            null -> parseSecuritySet(json.optString("security", json.optString("type", "")))
            else -> securityArray.asSequence()
                .mapNotNull { it as? String }
                .map(::parseSecurityToken)
                .filter { it != SecurityType.UNKNOWN }
                .toSet()
                .ifEmpty { setOf(SecurityType.UNKNOWN) }
        }
        val password = json.optString("password", json.optString("passphrase", "")).takeIf { it.isNotEmpty() }
        return WifiCredential.create(
            ssid = ssid,
            security = security,
            password = password,
            hidden = json.optBoolean("hidden", json.optBoolean("hiddenSsid", false)),
            autoJoin = json.optBoolean("autojoin", json.optBoolean("autoJoin", true)),
            note = parseNote(json),
            source = if (json.has("private")) CredentialSource.QuickShare else CredentialSource.Json,
        )
    }

    private fun parseCsv(text: String): ImportOutcome {
        val rows = Csv.readRows(text)
        if (rows.isEmpty()) return ImportOutcome(emptyList(), listOf(ImportSkip(0, "import.empty_csv")))

        val header = rows.first().map { it.trim().lowercase() }
        val hasHeader = header.any { it in setOf("ssid", "security", "password") }
        val dataRows = if (hasHeader) rows.drop(1) else rows
        val ssidIndex = if (hasHeader) header.indexOf("ssid") else 0
        val securityIndex = if (hasHeader) header.indexOf("security").takeIf { it >= 0 } ?: 1 else 1
        val passwordIndex = if (hasHeader) header.indexOf("password").takeIf { it >= 0 } ?: 2 else 2
        val hiddenIndex = if (hasHeader) header.indexOf("hidden") else 3
        val autoJoinIndex = if (hasHeader) {
            header.indexOf("autojoin").takeIf { it >= 0 } ?: header.indexOf("auto_join")
        } else 4
        val noteIndex = if (hasHeader) {
            header.indexOf("note").takeIf { it >= 0 } ?: header.indexOf("notes")
        } else 5

        val credentials = mutableListOf<WifiCredential>()
        val skipped = mutableListOf<ImportSkip>()
        dataRows.forEachIndexed { rowIndex, row ->
            val ssid = row.getOrNull(ssidIndex).orEmpty().trimControl()
            if (ssid.isBlank()) {
                skipped += ImportSkip(rowIndex, "import.missing_ssid")
                return@forEachIndexed
            }
            credentials += WifiCredential.create(
                ssid = ssid,
                security = parseSecuritySet(row.getOrNull(securityIndex).orEmpty()),
                password = row.getOrNull(passwordIndex).orEmpty().ifBlank { null },
                hidden = row.getOrNull(hiddenIndex).toBooleanCompat(),
                autoJoin = row.getOrNull(autoJoinIndex).toBooleanCompat(default = true),
                note = row.getOrNull(noteIndex).orEmpty().ifBlank { null },
                source = CredentialSource.Csv,
            )
        }
        return ImportOutcome(credentials.distinctBy { it.id }, skipped)
    }

    private fun parseNote(json: JSONObject): String? {
        val direct = json.optString("note", "").takeIf { it.isNotBlank() }
        if (direct != null) return direct.trim()
        val notesString = json.optString("notes", "").takeIf { it.isNotBlank() }
        if (notesString != null) return notesString.trim()
        return json.optJSONArray("notes")?.asSequence()
            ?.mapNotNull { it as? String }
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString("\n")
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseWifiQrLines(text: String): ImportOutcome {
        val blocks = text
            .lines()
            .map { it.trim() }
            .filter { it.startsWith("WIFI:", ignoreCase = true) }
        val credentials = mutableListOf<WifiCredential>()
        val skipped = mutableListOf<ImportSkip>()
        blocks.forEachIndexed { index, block ->
            val fields = parseWifiQrFields(block)
            val ssid = fields["S"].orEmpty().trimControl()
            if (ssid.isBlank()) {
                skipped += ImportSkip(index, "import.missing_qr_ssid")
                return@forEachIndexed
            }
            val security = parseSecuritySet(fields["T"].orEmpty())
            credentials += WifiCredential.create(
                ssid = ssid,
                security = if (security == setOf(SecurityType.UNKNOWN)) setOf(SecurityType.OPEN) else security,
                password = fields["P"].orEmpty().ifBlank { null },
                hidden = fields["H"].toBooleanCompat(),
                source = CredentialSource.WifiQr,
            )
        }
        return ImportOutcome(credentials.distinctBy { it.id }, skipped)
    }

    private fun maybeGunzip(fileName: String?, bytes: ByteArray): ByteArray {
        val gzip = fileName?.endsWith(".gz", ignoreCase = true) == true ||
            bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
        if (!gzip) return bytes
        return GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            val output = mutableListOf<ByteArray>()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                require(total <= MAX_DECOMPRESSED_BYTES) { "Import file expands beyond the supported size." }
                output += buffer.copyOf(read)
            }
            ByteArray(total).also { target ->
                var offset = 0
                output.forEach { chunk ->
                    chunk.copyInto(target, destinationOffset = offset)
                    offset += chunk.size
                }
            }
        }
    }

    private fun parseWifiQrFields(block: String): Map<String, String> {
        val body = block.removePrefix("WIFI:").removeSuffix(";;")
        val fields = mutableMapOf<String, String>()
        val parts = splitEscaped(body, ';')
        parts.forEach { part ->
            val keyValue = splitEscaped(part, ':', limit = 2)
            if (keyValue.size == 2) {
                fields[keyValue[0].uppercase()] = unescapeWifiQr(keyValue[1])
            }
        }
        return fields
    }

    private fun splitEscaped(value: String, separator: Char, limit: Int = Int.MAX_VALUE): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        for (char in value) {
            when {
                escaped -> {
                    current.append('\\')
                    current.append(char)
                    escaped = false
                }
                char == '\\' -> escaped = true
                char == separator && result.size < limit - 1 -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        if (escaped) current.append('\\')
        result += current.toString()
        return result
    }

    private fun unescapeWifiQr(value: String): String = buildString {
        var escaped = false
        value.forEach { char ->
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

private fun String?.toBooleanCompat(default: Boolean = false): Boolean {
    return when (this?.trim()?.lowercase()) {
        "true", "1", "yes", "y", "on" -> true
        "false", "0", "no", "n", "off" -> false
        else -> default
    }
}

private fun String.trimControl(): String = filter { it >= ' ' }.trim()
