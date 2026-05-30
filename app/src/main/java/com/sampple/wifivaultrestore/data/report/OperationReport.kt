package com.sampple.wifivaultrestore.data.report

import org.json.JSONArray
import org.json.JSONObject

enum class OperationKind {
    Import,
    Extract,
    Restore,
}

data class OperationReport(
    val id: String,
    val kind: OperationKind,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val total: Int,
    val success: Int,
    val alreadyExists: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val notes: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("kind", kind.name)
        .put("startedAtMillis", startedAtMillis)
        .put("finishedAtMillis", finishedAtMillis)
        .put("total", total)
        .put("success", success)
        .put("alreadyExists", alreadyExists)
        .put("failed", failed)
        .put("skipped", skipped)
        .put("notes", JSONArray(notes))

    companion object {
        fun fromJson(json: JSONObject): OperationReport = OperationReport(
            id = json.optString("id"),
            kind = runCatching {
                OperationKind.valueOf(json.optString("kind", OperationKind.Import.name))
            }.getOrDefault(OperationKind.Import),
            startedAtMillis = json.optLong("startedAtMillis"),
            finishedAtMillis = json.optLong("finishedAtMillis"),
            total = json.optInt("total"),
            success = json.optInt("success"),
            alreadyExists = json.optInt("alreadyExists"),
            failed = json.optInt("failed"),
            skipped = json.optInt("skipped"),
            notes = buildList {
                val values = json.optJSONArray("notes") ?: return@buildList
                for (i in 0 until values.length()) {
                    add(values.optString(i))
                }
            },
        )
    }
}
