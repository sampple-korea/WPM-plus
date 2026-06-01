package com.sampple.wifivaultrestore.data.extract

import com.sampple.wifivaultrestore.data.CredentialSource
import com.sampple.wifivaultrestore.data.SecurityType
import com.sampple.wifivaultrestore.data.WifiCredential
import com.sampple.wifivaultrestore.data.parseSecuritySet
import com.sampple.wifivaultrestore.shizuku.PrivilegeMode
import com.sampple.wifivaultrestore.shizuku.ShizukuCommandRunner

class SystemWifiExtractor(
    private val commandRunner: ShizukuCommandRunner,
) {
    suspend fun extract(): ExtractionOutcome {
        val state = commandRunner.state()
        if (!state.running || !state.permissionGranted) {
            return ExtractionOutcome(
                mode = PrivilegeMode.Unavailable,
                credentials = emptyList(),
                notes = listOf("extract.shizuku_unavailable"),
                rawSourcesChecked = 0,
            )
        }

        val mode = state.mode
        val source = when (mode) {
            PrivilegeMode.Root -> CredentialSource.ShizukuRoot
            PrivilegeMode.Shell -> CredentialSource.ShizukuShell
            else -> CredentialSource.SystemDiagnostic
        }

        val notes = mutableListOf<String>()
        val credentials = linkedMapOf<String, WifiCredential>()
        var sourcesChecked = 0

        val fileResult = commandRunner.dumpWifiConfigFiles()
        if (fileResult.exitCode != 0 || fileResult.error.isNotBlank()) {
            notes += note("extract.config_read_failed", fileResult.error.ifBlank { "exit ${fileResult.exitCode}" })
        }
        val files = extractMarkedFiles(fileResult.output)
        sourcesChecked += CONFIG_PATH_COUNT
        if (files.isEmpty()) {
            notes += note("extract.no_readable_xml", mode.name)
        } else {
            files.forEach { (path, xml) ->
                val parsed = runCatching {
                    if (path.endsWith("wpa_supplicant.conf")) {
                        WpaSupplicantParser.parse(xml, source)
                    } else {
                        WifiConfigStoreParser.parse(xml, source)
                    }
                }
                    .onFailure { notes += note("extract.parse_failed", path, it.javaClass.simpleName) }
                    .getOrDefault(emptyList())
                parsed.forEach { credentials.mergeCredential(it) }
                notes += note("extract.parsed_path", parsed.size.toString(), path)
            }
        }

        val listNetworks = commandRunner.listWifiNetworks()
        if (listNetworks.exitCode != 0 || listNetworks.error.isNotBlank()) {
            notes += note("extract.cmd_list_failed", listNetworks.error.ifBlank { "exit ${listNetworks.exitCode}" })
        }
        sourcesChecked++
        parseCmdWifiListNetworks(listNetworks.output, source)
            .filterNot { credentials.containsKey(it.id) }
            .forEach { credentials[it.id] = it }
        if (listNetworks.output.contains("SecurityException", ignoreCase = true)) {
            notes += "extract.cmd_denied"
        }

        if (mode == PrivilegeMode.Shell && credentials.values.none { it.hasPassword }) {
            notes += "extract.shell_password_limited"
        }
        if (mode == PrivilegeMode.Root && credentials.values.none { it.hasPassword }) {
            notes += "extract.root_no_passwords"
        }

        return ExtractionOutcome(
            mode = mode,
            credentials = credentials.values.sortedBy { it.ssid.lowercase() },
            notes = notes,
            rawSourcesChecked = sourcesChecked,
        )
    }

    private fun MutableMap<String, WifiCredential>.mergeCredential(credential: WifiCredential) {
        val existing = this[credential.id]
        this[credential.id] = if (existing == null) {
            credential
        } else {
            credential.copy(
                password = credential.password ?: existing.password,
                note = credential.note ?: existing.note,
                createdAtMillis = existing.createdAtMillis,
            )
        }
    }

    private fun parseCmdWifiListNetworks(output: String, source: CredentialSource): List<WifiCredential> {
        val lines = output.lines().dropWhile { !it.trimStart().startsWith("Network Id") }.drop(1)
        return lines.mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@mapNotNull null
            val tokens = trimmed.split(Regex("\\s+"))
            if (tokens.size < 3) return@mapNotNull null
            val security = tokens.last()
            val ssid = tokens.drop(1).dropLast(1).joinToString(" ").trim()
            if (ssid.isBlank()) return@mapNotNull null
            WifiCredential.create(
                ssid = ssid,
                security = parseSecuritySet(security).ifEmpty { setOf(SecurityType.UNKNOWN) },
                password = null,
                source = source,
            )
        }.distinctBy { it.id }
    }

    private fun extractMarkedFiles(output: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val regex = Regex(
            "$MARKER_START(.+?)\\n(.*?)\\n$MARKER_END\\1",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        regex.findAll(output).forEach { match ->
            result[match.groupValues[1].trim()] = match.groupValues[2]
        }
        return result
    }

    companion object {
        private const val MARKER_START = "__WVR_FILE_START__"
        private const val MARKER_END = "__WVR_FILE_END__"

        private const val CONFIG_PATH_COUNT = 4

        fun note(code: String, vararg args: String): String = (listOf(code) + args.map(::sanitizeArg)).joinToString("|")

        private fun sanitizeArg(value: String): String = value.replace("|", "/").take(160)
    }
}
