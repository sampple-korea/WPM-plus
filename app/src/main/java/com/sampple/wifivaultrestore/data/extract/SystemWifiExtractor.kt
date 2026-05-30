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
                notes = listOf("Shizuku is not running or permission has not been granted."),
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

        val fileResult = commandRunner.run(WIFI_CONFIG_DUMP_COMMAND)
        val files = extractMarkedFiles(fileResult.output)
        sourcesChecked += CONFIG_PATHS.size
        if (files.isEmpty()) {
            notes += "No readable Wi‑Fi config XML files were returned for ${mode.name}."
        } else {
            files.forEach { (path, xml) ->
                val parsed = runCatching {
                    if (path.endsWith("wpa_supplicant.conf")) {
                        WpaSupplicantParser.parse(xml, source)
                    } else {
                        WifiConfigStoreParser.parse(xml, source)
                    }
                }
                    .onFailure { notes += "Could not parse $path: ${it.javaClass.simpleName}" }
                    .getOrDefault(emptyList())
                parsed.forEach { credentials[it.id] = it }
                notes += "Parsed ${parsed.size} network entries from $path."
            }
        }

        val listNetworks = commandRunner.run("cmd wifi list-networks 2>&1")
        sourcesChecked++
        parseCmdWifiListNetworks(listNetworks.output, source)
            .filterNot { credentials.containsKey(it.id) }
            .forEach { credentials[it.id] = it }
        if (listNetworks.output.contains("SecurityException", ignoreCase = true)) {
            notes += "cmd wifi list-networks was denied by the platform."
        }

        if (mode == PrivilegeMode.Shell && credentials.values.none { it.hasPassword }) {
            notes += "ADB/Shizuku shell mode usually cannot read PSK values on production builds. Use Shizuku root/Sui for full password extraction."
        }
        if (mode == PrivilegeMode.Root && credentials.values.none { it.hasPassword }) {
            notes += "Root mode was available, but no readable PreSharedKey values were found in known config files."
        }

        return ExtractionOutcome(
            mode = mode,
            credentials = credentials.values.sortedBy { it.ssid.lowercase() },
            notes = notes,
            rawSourcesChecked = sourcesChecked,
        )
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

        private val CONFIG_PATHS = listOf(
            "/data/misc/apexdata/com.android.wifi/WifiConfigStore.xml",
            "/data/misc/apexdata/com.android.wifi/WifiConfigStoreSoftAp.xml",
            "/data/misc/wifi/WifiConfigStore.xml",
            "/data/misc/wifi/wpa_supplicant.conf",
        )

        private val WIFI_CONFIG_DUMP_COMMAND = buildString {
            append("for p in ")
            append(CONFIG_PATHS.joinToString(" ") { "'$it'" })
            append("; do ")
            append("if [ -r \"\$p\" ]; then ")
            append("echo $MARKER_START\$p; cat \"\$p\"; echo $MARKER_END\$p; ")
            append("else echo __WVR_UNREADABLE__\$p; fi; ")
            append("done")
        }
    }
}
