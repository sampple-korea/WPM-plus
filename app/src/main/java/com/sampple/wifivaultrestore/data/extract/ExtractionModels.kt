package com.sampple.wifivaultrestore.data.extract

import com.sampple.wifivaultrestore.data.WifiCredential
import com.sampple.wifivaultrestore.shizuku.PrivilegeMode

data class ExtractionOutcome(
    val mode: PrivilegeMode,
    val credentials: List<WifiCredential>,
    val notes: List<String>,
    val rawSourcesChecked: Int,
) {
    val withPasswords: Int = credentials.count { it.hasPassword }
    val withoutPasswords: Int = credentials.size - withPasswords
}
