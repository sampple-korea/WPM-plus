package com.sampple.wifivaultrestore.ui.model

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sampple.wifivaultrestore.data.WifiCredential
import com.sampple.wifivaultrestore.diagnostics.CrashReporter
import com.sampple.wifivaultrestore.data.extract.SystemWifiExtractor
import com.sampple.wifivaultrestore.data.extract.ShizukuWifiManagerReader
import com.sampple.wifivaultrestore.data.importer.ImportPasswordRequiredException
import com.sampple.wifivaultrestore.data.importer.VaultExportCodec
import com.sampple.wifivaultrestore.data.importer.WifiImportParser
import com.sampple.wifivaultrestore.data.report.OperationKind
import com.sampple.wifivaultrestore.data.report.OperationReport
import com.sampple.wifivaultrestore.data.restore.RestoreSession
import com.sampple.wifivaultrestore.data.security.WifiVaultRepository
import com.sampple.wifivaultrestore.shizuku.ShizukuCommandRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WifiVaultRepository(application)
    private val shizuku = ShizukuCommandRunner(application)
    private val extractor = SystemWifiExtractor(shizuku, ShizukuWifiManagerReader(application))

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        CrashReporter.consumePendingReport(application)?.let { report ->
            _state.update { it.copy(pendingCrashReport = report) }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { repository.load() }
                .onSuccess { vault ->
                    _state.update {
                        it.copy(
                            vault = vault,
                            loading = false,
                            shizuku = shizuku.state(),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(loading = false, message = error.message ?: "Vault could not be opened.")
                    }
                }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun requestShizukuPermission() {
        shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        _state.update { it.copy(shizuku = shizuku.state()) }
    }

    fun refreshShizukuState() {
        _state.update { it.copy(shizuku = shizuku.state()) }
    }

    fun importBytes(fileName: String?, bytes: ByteArray, password: String? = null) {
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            _state.update { it.copy(busy = true) }
            runCatching {
                val outcome = WifiImportParser.parse(fileName, bytes, password)
                val vault = repository.upsertCredentials(outcome.credentials)
                val report = OperationReport(
                    id = UUID.randomUUID().toString(),
                    kind = OperationKind.Import,
                    startedAtMillis = started,
                    finishedAtMillis = System.currentTimeMillis(),
                    total = outcome.importedCount + outcome.skippedCount,
                    success = outcome.importedCount,
                    skipped = outcome.skippedCount,
                    notes = outcome.skipped.take(12).map { "${it.index}: ${it.reason}" },
                )
                repository.appendReport(report)
                vault.copy(reports = listOf(report) + vault.reports)
            }.onSuccess { vault ->
                _state.update {
                    it.copy(
                        vault = vault,
                        busy = false,
                        pendingImportBytes = null,
                        pendingImportFileName = null,
                        message = "Imported ${vault.credentials.size} total vault entries.",
                    )
                }
            }.onFailure { error ->
                if (error is ImportPasswordRequiredException) {
                    _state.update {
                        it.copy(
                            busy = false,
                            pendingImportBytes = bytes,
                            pendingImportFileName = fileName,
                            message = error.message,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            busy = false,
                            pendingImportBytes = null,
                            pendingImportFileName = null,
                            message = error.message ?: "Import failed.",
                        )
                    }
                }
            }
        }
    }

    fun importPendingEncrypted(password: String) {
        val bytes = state.value.pendingImportBytes ?: return
        importBytes(state.value.pendingImportFileName, bytes, password)
    }

    fun cancelPendingImportPassword() {
        _state.update { it.copy(pendingImportBytes = null, pendingImportFileName = null) }
    }

    fun clearPendingCrashReport() {
        _state.update { it.copy(pendingCrashReport = null) }
    }

    fun importText(text: String) {
        importBytes("pasted-wifi-qr.txt", text.toByteArray(Charsets.UTF_8))
    }

    fun updateNote(credentialId: String, note: String?) {
        viewModelScope.launch {
            runCatching { repository.updateNote(credentialId, note) }
                .onSuccess { vault ->
                    _state.update { it.copy(vault = vault, message = "Note saved.") }
                }
                .onFailure { error ->
                    _state.update { it.copy(message = error.message ?: "Could not save note.") }
                }
        }
    }

    fun exportVault(encrypt: Boolean, password: String?): ByteArray {
        val vault = state.value.vault
        return if (encrypt) {
            VaultExportCodec.exportEncryptedGzip(vault, password.orEmpty())
        } else {
            VaultExportCodec.exportGzip(vault)
        }
    }

    fun extractSystem() {
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            _state.update { it.copy(busy = true) }
            runCatching {
                val outcome = extractor.extract()
                val vault = repository.upsertCredentials(outcome.credentials)
                val report = OperationReport(
                    id = UUID.randomUUID().toString(),
                    kind = OperationKind.Extract,
                    startedAtMillis = started,
                    finishedAtMillis = System.currentTimeMillis(),
                    total = outcome.credentials.size,
                    success = outcome.withPasswords,
                    skipped = outcome.withoutPasswords,
                    notes = outcome.notes,
                )
                repository.appendReport(report)
                Pair(outcome, vault.copy(reports = listOf(report) + vault.reports))
            }.onSuccess { (outcome, vault) ->
                _state.update {
                    it.copy(
                        vault = vault,
                        busy = false,
                        lastExtraction = outcome,
                        shizuku = shizuku.state(),
                        message = "Extracted ${outcome.credentials.size} entries; ${outcome.withPasswords} include passwords.",
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        busy = false,
                        shizuku = shizuku.state(),
                        message = error.message ?: "Extraction failed.",
                    )
                }
            }
        }
    }

    fun startRestore(credentials: List<WifiCredential> = state.value.vault.credentials) {
        val restorable = credentials.filter { it.canRestore }
        val skipped = credentials.size - restorable.size
        _state.update {
            it.copy(
                restoreSession = RestoreSession(
                    id = UUID.randomUUID().toString(),
                    queue = restorable,
                    skipped = skipped,
                ).nextBatch(),
                message = if (restorable.isEmpty()) "No restorable networks." else null,
            )
        }
    }

    fun onRestoreBatchResult(results: List<Int>?) {
        val current = state.value.restoreSession ?: return
        var success = 0
        var alreadyExists = 0
        var failed = 0
        if (results == null) {
            failed = current.activeBatch?.items?.size ?: 0
        } else {
            results.forEach { result ->
                when (result) {
                    Settings.ADD_WIFI_RESULT_SUCCESS -> success++
                    Settings.ADD_WIFI_RESULT_ALREADY_EXISTS -> alreadyExists++
                    else -> failed++
                }
            }
        }

        val updated = current.completeActiveBatch(success, alreadyExists, failed).nextBatch()
        _state.update { it.copy(restoreSession = updated) }

        if (updated.done) {
            viewModelScope.launch {
                val report = OperationReport(
                    id = updated.id,
                    kind = OperationKind.Restore,
                    startedAtMillis = updated.startedAtMillis,
                    finishedAtMillis = System.currentTimeMillis(),
                    total = updated.total + updated.skipped,
                    success = updated.success,
                    alreadyExists = updated.alreadyExists,
                    failed = updated.failed,
                    skipped = updated.skipped,
                    notes = listOf("Restored with Android ACTION_WIFI_ADD_NETWORKS in batches of 5."),
                )
                val vault = repository.appendReport(report)
                _state.update {
                    it.copy(
                        vault = vault,
                        restoreSession = updated,
                        message = "Restore complete: ${updated.success} saved, ${updated.alreadyExists} already existed, ${updated.failed} failed.",
                    )
                }
            }
        }
    }

    companion object {
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 520
    }
}
