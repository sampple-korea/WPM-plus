package com.sampple.wifivaultrestore.data

import com.sampple.wifivaultrestore.data.report.OperationReport
import org.json.JSONArray
import org.json.JSONObject

data class VaultData(
    val credentials: List<WifiCredential> = emptyList(),
    val reports: List<OperationReport> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("version", 1)
        .put("credentials", JSONArray(credentials.map { it.toJson() }))
        .put("reports", JSONArray(reports.map { it.toJson() }))

    companion object {
        fun fromJson(json: JSONObject): VaultData {
            val credentials = json.optJSONArray("credentials").asSequence()
                .mapNotNull { it as? JSONObject }
                .map(WifiCredentialJson::fromJson)
                .toList()
            val reports = json.optJSONArray("reports").asSequence()
                .mapNotNull { it as? JSONObject }
                .map(OperationReport.Companion::fromJson)
                .toList()
            return VaultData(credentials = credentials, reports = reports)
        }
    }
}

private object WifiCredentialJson {
    fun fromJson(json: JSONObject): WifiCredential {
        val security = json.optJSONArray("security").asSequence()
            .mapNotNull { it as? String }
            .map(::parseSecurityToken)
            .toSet()
            .ifEmpty { setOf(SecurityType.UNKNOWN) }
        return WifiCredential(
            id = json.optString("id"),
            ssid = json.optString("ssid"),
            security = security,
            password = json.optString("password").takeIf { security.canCarryVaultPassword() && it.isNotEmpty() },
            hidden = json.optBoolean("hidden", false),
            autoJoin = json.optBoolean("autoJoin", true),
            note = json.optString("note", json.optString("notes", "")).takeIf { it.isNotBlank() },
            source = runCatching {
                CredentialSource.valueOf(json.optString("source", CredentialSource.Manual.name))
            }.getOrDefault(CredentialSource.Manual),
            createdAtMillis = json.optLong("createdAtMillis", System.currentTimeMillis()),
            updatedAtMillis = json.optLong("updatedAtMillis", System.currentTimeMillis()),
        )
    }
}

fun WifiCredential.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("ssid", ssid)
    .put("security", JSONArray(security.map { it.name }))
    .put("password", password ?: "")
    .put("hidden", hidden)
    .put("autoJoin", autoJoin)
    .put("note", note ?: "")
    .put("source", source.name)
    .put("createdAtMillis", createdAtMillis)
    .put("updatedAtMillis", updatedAtMillis)

fun JSONArray?.asSequence(): Sequence<Any?> = sequence {
    if (this@asSequence == null) return@sequence
    for (i in 0 until length()) {
        yield(opt(i))
    }
}
