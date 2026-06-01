package com.sampple.wifivaultrestore.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertNull
import org.junit.Test

class VaultDataTest {
    @Test
    fun dropsLegacyPasswordsFromPasswordlessCredentials() {
        val vault = VaultData.fromJson(
            JSONObject()
                .put(
                    "credentials",
                    JSONArray().put(
                        JSONObject()
                            .put("id", "legacy-open")
                            .put("ssid", "Guest")
                            .put("security", JSONArray().put("OPEN"))
                            .put("password", "legacy-open-secret"),
                    ),
                ),
        )

        assertNull(vault.credentials.single().password)
    }
}
