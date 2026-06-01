package com.sampple.wifivaultrestore.ui.model

import android.app.Application
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sampple.wifivaultrestore.R
import com.sampple.wifivaultrestore.data.CredentialSource
import com.sampple.wifivaultrestore.data.SecurityType
import com.sampple.wifivaultrestore.data.WifiCredential
import com.sampple.wifivaultrestore.diagnostics.CrashReporter
import com.sampple.wifivaultrestore.data.extract.SystemWifiExtractor
import com.sampple.wifivaultrestore.data.extract.ShizukuWifiManagerReader
import com.sampple.wifivaultrestore.data.importer.ImportPasswordRequiredException
import com.sampple.wifivaultrestore.data.importer.VaultExportCodec
import com.sampple.wifivaultrestore.data.importer.WifiImportParser
import com.sampple.wifivaultrestore.data.report.OperationKind
import com.sampple.wifivaultrestore.data.report.OperationReport
import com.sampple.wifivaultrestore.data.restore.RestoreCompatibility
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
                    _state.update { it.copy(loading = false, message = error.message ?: string(R.string.message_vault_open_failed)) }
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
                        message = string(R.string.message_import_success, vault.credentials.size),
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
                            message = error.message ?: string(R.string.message_import_failed),
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

    fun saveManualCredential(
        ssid: String,
        security: SecurityType,
        password: String?,
        hidden: Boolean,
        note: String?,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching {
                val credential = WifiCredential.create(
                    ssid = ssid.trim(),
                    security = setOf(security),
                    password = password?.takeIf { it.isNotBlank() },
                    hidden = hidden,
                    note = note,
                    source = CredentialSource.Manual,
                )
                repository.upsertCredentials(listOf(credential))
            }.onSuccess { vault ->
                _state.update { it.copy(vault = vault, busy = false, message = string(R.string.message_network_saved)) }
            }.onFailure { error ->
                _state.update { it.copy(busy = false, message = error.message ?: string(R.string.message_network_save_failed)) }
            }
        }
    }

    fun updateNote(credentialId: String, note: String?) {
        viewModelScope.launch {
            runCatching { repository.updateNote(credentialId, note) }
                .onSuccess { vault ->
                    _state.update { it.copy(vault = vault, message = string(R.string.message_note_saved)) }
                }
                .onFailure { error ->
                    _state.update { it.copy(message = error.message ?: string(R.string.message_note_failed)) }
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
                        message = string(R.string.message_extract_success, outcome.credentials.size, outcome.withPasswords),
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        busy = false,
                        shizuku = shizuku.state(),
                        message = error.message ?: string(R.string.message_extract_failed),
                    )
                }
            }
        }
    }

    fun startRestore(credentials: List<WifiCredential> = state.value.vault.credentials) {
        val plan = RestoreCompatibility.plan(credentials)
        val restorable = plan.supportedCredentials
        val skipped = plan.skipped.size
        _state.update {
            it.copy(
                restorePlan = plan,
                restoreSession = RestoreSession(
                    id = UUID.randomUUID().toString(),
                    queue = restorable,
                    skipped = skipped,
                ).nextBatch(),
                message = if (restorable.isEmpty()) string(R.string.message_no_restorable) else null,
            )
        }
    }

    fun onRestoreBatchResult(results: List<Int>?) {
        val current = state.value.restoreSession ?: return
        val batchSize = current.activeBatch?.items?.size ?: 0
        var success = 0
        var alreadyExists = 0
        var failed = 0
        if (results == null) {
            failed = batchSize
        } else {
            results.forEach { result ->
                when (result) {
                    Settings.ADD_WIFI_RESULT_SUCCESS -> success++
                    Settings.ADD_WIFI_RESULT_ALREADY_EXISTS -> alreadyExists++
                    else -> failed++
                }
            }
            failed += (batchSize - results.size).coerceAtLeast(0)
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
                    notes = listOf(string(R.string.restore_report_note)),
                )
                val vault = repository.appendReport(report)
                _state.update {
                    it.copy(
                        vault = vault,
                        restoreSession = updated,
                        message = string(
                            R.string.message_restore_complete,
                            updated.success,
                            updated.alreadyExists,
                            updated.failed,
                        ),
                    )
                }
            }
        }
    }

    companion object {
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 520
    }

    private fun string(@StringRes id: Int, vararg args: Any): String {
        return if (args.isEmpty()) {
            getApplication<Application>().getString(id)
        } else {
            getApplication<Application>().getString(id, *args)
        }
    }
}
