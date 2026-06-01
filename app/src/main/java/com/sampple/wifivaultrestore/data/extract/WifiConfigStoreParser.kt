package com.sampple.wifivaultrestore.data.extract

import com.sampple.wifivaultrestore.data.CredentialSource
import com.sampple.wifivaultrestore.data.SecurityType
import com.sampple.wifivaultrestore.data.WifiCredential
import com.sampple.wifivaultrestore.data.parseSecuritySet
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

object WifiConfigStoreParser {
    fun parse(xml: String, source: CredentialSource): List<WifiCredential> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isIgnoringComments = true
            isCoalescing = true
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

        val networks = document.getElementsByTagName("Network")
        return buildList {
            for (i in 0 until networks.length) {
                val network = networks.item(i) as? Element ?: continue
                parseNetwork(network, source)?.let(::add)
            }
        }.distinctBy { it.id }
    }

    private fun parseNetwork(network: Element, source: CredentialSource): WifiCredential? {
        val values = mutableMapOf<String, String>()
        val elements = network.getElementsByTagName("*")
        for (i in 0 until elements.length) {
            val element = elements.item(i) as? Element ?: continue
            val name = element.getAttribute("name").takeIf { it.isNotBlank() } ?: continue
            val value = when (element.tagName) {
                "string" -> element.textContent.orEmpty()
                "boolean", "int", "long" -> element.getAttribute("value")
                else -> element.textContent.orEmpty().takeIf { it.isNotBlank() } ?: continue
            }
            values[name] = value
        }

        val ssid = values["SSID"]?.unquoteWifiValue()
            ?: values["ConfigKey"]?.extractSsidFromConfigKey()
            ?: return null
        if (ssid.isBlank()) return null

        val preSharedKey = values["PreSharedKey"]?.unquoteWifiValue()?.takeIf { it != "*" && it.isNotBlank() }
        val keyManagement = listOfNotNull(
            values["AllowedKeyMgmt"],
            values["KeyMgmt"],
            values["ConfigKey"],
            values["SecurityParams"],
        ).joinToString(" ")
        val security = inferSecurity(keyManagement, preSharedKey)

        return WifiCredential.create(
            ssid = ssid,
            security = security,
            password = preSharedKey,
            hidden = values["HiddenSSID"].toBooleanCompat(),
            autoJoin = values["AutoJoinEnabled"].toBooleanCompat(default = true),
            source = source,
        )
    }

    private fun inferSecurity(raw: String, preSharedKey: String?): Set<SecurityType> {
        val parsed = parseSecuritySet(raw)
            .filter { it != SecurityType.UNKNOWN }
            .toSet()
        return when {
            parsed.isNotEmpty() -> parsed
            !preSharedKey.isNullOrBlank() -> setOf(SecurityType.WPA2)
            else -> setOf(SecurityType.OPEN)
        }
    }

    private fun String.extractSsidFromConfigKey(): String? {
        val start = indexOf('"')
        if (start < 0) return null
        val end = indexOf('"', startIndex = start + 1)
        if (end <= start) return null
        return substring(start + 1, end)
    }

    private fun String.unquoteWifiValue(): String {
        val trimmed = trim()
        return if (trimmed.length >= 2 && trimmed.first() == '"' && trimmed.last() == '"') {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
    }

    private fun String?.toBooleanCompat(default: Boolean = false): Boolean {
        return when (this?.trim()?.lowercase()) {
            "true", "1", "yes", "y", "on" -> true
            "false", "0", "no", "n", "off" -> false
            else -> default
        }
    }
}
