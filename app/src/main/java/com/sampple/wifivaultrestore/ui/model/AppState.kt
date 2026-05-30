package com.sampple.wifivaultrestore.ui.model

import com.sampple.wifivaultrestore.data.VaultData
import com.sampple.wifivaultrestore.data.extract.ExtractionOutcome
import com.sampple.wifivaultrestore.data.restore.RestoreSession
import com.sampple.wifivaultrestore.shizuku.ShizukuState

data class AppState(
    val vault: VaultData = VaultData(),
    val loading: Boolean = true,
    val message: String? = null,
    val shizuku: ShizukuState = ShizukuState(running = false, permissionGranted = false),
    val lastExtraction: ExtractionOutcome? = null,
    val restoreSession: RestoreSession? = null,
)
