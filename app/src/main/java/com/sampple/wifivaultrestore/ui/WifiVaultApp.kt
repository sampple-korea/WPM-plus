package com.sampple.wifivaultrestore.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sampple.wifivaultrestore.R
import com.sampple.wifivaultrestore.data.CredentialSource
import com.sampple.wifivaultrestore.data.SecurityType
import com.sampple.wifivaultrestore.data.WifiCredential
import com.sampple.wifivaultrestore.data.report.OperationKind
import com.sampple.wifivaultrestore.data.report.OperationReport
import com.sampple.wifivaultrestore.data.restore.RestoreCompatibility
import com.sampple.wifivaultrestore.data.restore.RestorePlan
import com.sampple.wifivaultrestore.data.restore.RestoreSkipReason
import com.sampple.wifivaultrestore.data.restore.WifiRestoreIntentFactory
import com.sampple.wifivaultrestore.data.securityLabel
import com.sampple.wifivaultrestore.data.share.WifiShareFormatter
import com.sampple.wifivaultrestore.shizuku.PrivilegeMode
import com.sampple.wifivaultrestore.ui.model.AppState
import com.sampple.wifivaultrestore.ui.model.MainViewModel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

private data class PendingExport(
    val bytes: ByteArray,
    val message: String,
)

private data class SecurityChoice(
    val key: String,
    val security: Set<SecurityType>,
)

private enum class Destination(val labelRes: Int) {
    Vault(R.string.nav_vault),
    Add(R.string.nav_add),
    Restore(R.string.nav_restore),
    Activity(R.string.nav_activity),
}

private enum class CredentialFilter(val labelRes: Int) {
    All(R.string.filter_all),
    Restorable(R.string.filter_restorable),
    MissingPassword(R.string.filter_missing_password),
    Imported(R.string.filter_imported),
    Extracted(R.string.filter_extracted),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiVaultApp(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val importFailedMessage = stringResource(R.string.message_import_failed)
    val exportFailedMessage = stringResource(R.string.message_export_failed)
    val exportCompleteMessage = stringResource(R.string.message_export_complete)
    val passwordCopiedMessage = stringResource(R.string.message_password_copied)
    val crashClipboardLabel = stringResource(R.string.crash_clipboard_label)
    val crashCopiedMessage = stringResource(R.string.crash_copied_message)
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var qrDialogOpen by rememberSaveable { mutableStateOf(false) }
    var qrText by rememberSaveable { mutableStateOf("") }
    var manualDialogOpen by rememberSaveable { mutableStateOf(false) }
    var exportDialogOpen by rememberSaveable { mutableStateOf(false) }
    var importPassword by rememberSaveable { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf<PendingExport?>(null) }
    val destinations = Destination.entries

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.readDocumentBytes(uri)
            }.onSuccess { bytes ->
                if (bytes != null) viewModel.importBytes(uri.lastPathSegment, bytes)
            }.onFailure { error ->
                scope.launch {
                    snackbarHostState.showSnackbar(error.message ?: importFailedMessage)
                }
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val export = pendingExport
        pendingExport = null
        if (uri != null && export != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(export.bytes) }
                    ?: error(exportFailedMessage)
            }.onSuccess {
                scope.launch { snackbarHostState.showSnackbar(export.message) }
            }.onFailure { error ->
                scope.launch {
                    snackbarHostState.showSnackbar(error.message ?: exportFailedMessage)
                }
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val values = result.data?.getIntegerArrayListExtra(Settings.EXTRA_WIFI_NETWORK_RESULT_LIST)
        viewModel.onRestoreBatchResult(values)
    }

    val activeBatch = state.restoreSession?.activeBatch
    LaunchedEffect(activeBatch?.id) {
        if (activeBatch != null) {
            val intent: Intent = WifiRestoreIntentFactory.buildIntent(activeBatch.items)
            restoreLauncher.launch(intent)
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = viewModel::refresh, enabled = !state.busy) {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_refresh))
                        }
                    },
                )
                if (state.loading || state.busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        icon = { Icon(iconFor(destination), contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        when (destinations[selectedIndex]) {
            Destination.Vault -> VaultPane(
                state = state,
                padding = padding,
                onAdd = { selectedIndex = destinations.indexOf(Destination.Add) },
                onExport = { exportDialogOpen = true },
                onPasswordCopied = {
                    scope.launch { snackbarHostState.showSnackbar(passwordCopiedMessage) }
                },
                onShare = context::shareCredential,
                onUpdateNote = viewModel::updateNote,
                onEdit = viewModel::updateCredential,
                onDelete = viewModel::deleteCredential,
            )
            Destination.Add -> AddPane(
                state = state,
                padding = padding,
                onManual = { manualDialogOpen = true },
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onPasteQr = { qrDialogOpen = true },
                onRefresh = viewModel::refreshShizukuState,
                onRequestPermission = viewModel::requestShizukuPermission,
                onExtract = viewModel::extractSystem,
            )
            Destination.Restore -> RestorePane(
                state = state,
                padding = padding,
                onRestore = viewModel::startRestore,
            )
            Destination.Activity -> ActivityPane(state = state, padding = padding)
        }
    }

    if (manualDialogOpen) {
        ManualCredentialDialog(
            onDismiss = { manualDialogOpen = false },
            onSave = { ssid, security, password, hidden, note ->
                viewModel.saveManualCredential(ssid, security, password, hidden, note)
                manualDialogOpen = false
            },
        )
    }

    if (qrDialogOpen) {
        AlertDialog(
            onDismissRequest = { qrDialogOpen = false },
            title = { Text(stringResource(R.string.qr_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = qrText,
                    onValueChange = { qrText = it },
                    label = { Text(stringResource(R.string.qr_dialog_label)) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importText(qrText)
                        qrText = ""
                        qrDialogOpen = false
                    },
                    enabled = qrText.isNotBlank(),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { qrDialogOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (exportDialogOpen) {
        ExportVaultDialog(
            onDismiss = { exportDialogOpen = false },
            onConfirm = { encrypted, password ->
                runCatching {
                    val bytes = viewModel.exportVault(encrypted, password)
                    val fileName = if (encrypted) "wpm-plus-vault-encrypted.wpmv.json" else "wpm-plus-vault.wpmv.json.gz"
                    pendingExport = PendingExport(bytes, exportCompleteMessage)
                    exportLauncher.launch(fileName)
                    exportDialogOpen = false
                }.onFailure { error ->
                    scope.launch {
                        snackbarHostState.showSnackbar(error.message ?: exportFailedMessage)
                    }
                }
            },
        )
    }

    if (state.pendingImportBytes != null) {
        AlertDialog(
            onDismissRequest = {
                importPassword = ""
                viewModel.cancelPendingImportPassword()
            },
            title = { Text(stringResource(R.string.import_password_title)) },
            text = {
                OutlinedTextField(
                    value = importPassword,
                    onValueChange = { importPassword = it },
                    label = { Text(stringResource(R.string.export_password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    enabled = importPassword.isNotBlank(),
                    onClick = {
                        viewModel.importPendingEncrypted(importPassword)
                        importPassword = ""
                    },
                ) {
                    Text(stringResource(R.string.action_import))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        importPassword = ""
                        viewModel.cancelPendingImportPassword()
                    },
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    state.pendingCrashReport?.let { report ->
        AlertDialog(
            onDismissRequest = viewModel::clearPendingCrashReport,
            title = { Text(stringResource(R.string.crash_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.crash_dialog_body))
                    Text(stringResource(R.string.crash_report_hidden_notice))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        context.copySensitiveText(
                            label = crashClipboardLabel,
                            value = report,
                        )
                        viewModel.clearPendingCrashReport()
                        scope.launch {
                            snackbarHostState.showSnackbar(crashCopiedMessage)
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_copy_log))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::clearPendingCrashReport) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun VaultPane(
    state: AppState,
    padding: PaddingValues,
    onAdd: () -> Unit,
    onExport: () -> Unit,
    onPasswordCopied: () -> Unit,
    onShare: (WifiCredential) -> Unit,
    onUpdateNote: (String, String?) -> Unit,
    onEdit: (String, String, Set<SecurityType>, String?, Boolean, String?) -> Unit,
    onDelete: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(CredentialFilter.All) }
    val plan = remember(state.vault.credentials) { RestoreCompatibility.plan(state.vault.credentials) }
    val filtered = remember(state.vault.credentials, query, filter, plan) {
        state.vault.credentials
            .filter { credential -> credential.matchesFilter(filter, plan) }
            .filter { credential ->
                val needle = query.trim().lowercase()
                needle.isBlank() ||
                    credential.ssid.lowercase().contains(needle) ||
                    credential.note.orEmpty().lowercase().contains(needle) ||
                    securityLabel(credential.security).lowercase().contains(needle)
            }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                title = stringResource(R.string.headline_vault),
                subtitle = stringResource(R.string.status_unlocked),
            )
        }
        item { SummaryGrid(plan = plan) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                label = { Text(stringResource(R.string.search_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            FilterRow(
                values = CredentialFilter.entries,
                selected = filter,
                label = { stringResource(it.labelRes) },
                onSelected = { filter = it },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onExport,
                    enabled = state.vault.credentials.isNotEmpty() && !state.busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_export))
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                if (state.vault.credentials.isEmpty()) {
                    EmptyVaultCard(onAdd = onAdd, enabled = !state.busy)
                } else {
                    ListItem(headlineContent = { Text(stringResource(R.string.empty_filtered)) })
                }
            }
        } else {
            items(filtered, key = { it.id }) { credential ->
                CredentialRow(
                    credential = credential,
                    onPasswordCopied = onPasswordCopied,
                    onShare = onShare,
                    onUpdateNote = onUpdateNote,
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EmptyVaultCard(
    onAdd: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.empty_vault), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.empty_vault_body))
            Button(onClick = onAdd, enabled = enabled) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.headline_add))
            }
        }
    }
}

@Composable
private fun AddPane(
    state: AppState,
    padding: PaddingValues,
    onManual: () -> Unit,
    onImport: () -> Unit,
    onPasteQr: () -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onExtract: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                title = stringResource(R.string.headline_add),
                subtitle = stringResource(R.string.extract_note),
            )
        }
        item {
            ActionPanel(
                title = stringResource(R.string.action_add_manual),
                body = stringResource(R.string.add_manual_body),
                icon = Icons.Rounded.Add,
                onClick = onManual,
                enabled = !state.busy,
            )
        }
        item {
            ActionPanel(
                title = stringResource(R.string.action_import),
                body = stringResource(R.string.add_import_body),
                icon = Icons.Rounded.UploadFile,
                onClick = onImport,
                enabled = !state.busy,
            )
        }
        item {
            ActionPanel(
                title = stringResource(R.string.action_paste_qr),
                body = stringResource(R.string.add_qr_body),
                icon = Icons.Rounded.ContentCopy,
                onClick = onPasteQr,
                enabled = !state.busy,
            )
        }
        item {
            ActionPanel(
                title = stringResource(R.string.action_extract_system),
                body = stringResource(R.string.add_extract_body),
                icon = Icons.Rounded.FileDownload,
                onClick = onExtract,
                enabled = state.shizuku.running && state.shizuku.permissionGranted && !state.busy,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AssistChip(onClick = onRefresh, enabled = !state.busy, label = { Text(shizukuLabel(state)) })
                OutlinedButton(onClick = onRequestPermission, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_request_shizuku))
                }
            }
        }
        state.lastExtraction?.let { outcome ->
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.last_extraction_title)) },
                    supportingContent = {
                        Column {
                            Text(stringResource(R.string.extraction_summary, outcome.credentials.size))
                            Text(stringResource(R.string.extraction_password_summary, outcome.withPasswords, outcome.withoutPasswords))
                        }
                    },
                )
            }
            items(outcome.notes.take(5)) { note ->
                ListItem(headlineContent = { Text(localizedNote(note)) })
            }
        }
    }
}

@Composable
private fun RestorePane(
    state: AppState,
    padding: PaddingValues,
    onRestore: (List<WifiCredential>) -> Unit,
) {
    val plan = remember(state.vault.credentials) { RestoreCompatibility.plan(state.vault.credentials) }
    var selectedIds by rememberSaveable(state.vault.credentials.map { it.id }.joinToString("|")) {
        mutableStateOf(plan.supportedCredentials.map { it.id })
    }
    val selectedCredentials = remember(plan, selectedIds) {
        plan.supportedCredentials.filter { it.id in selectedIds }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                title = stringResource(R.string.restore_review_title),
                subtitle = stringResource(R.string.restore_review_body),
            )
        }
        item { SummaryGrid(plan = plan) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.restore_selected_count, selectedIds.size)) },
                )
                OutlinedButton(
                    onClick = { selectedIds = plan.supportedCredentials.map { it.id } },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.restore_select_all))
                }
                OutlinedButton(onClick = { selectedIds = emptyList() }) {
                    Text(stringResource(R.string.restore_clear_selection))
                }
            }
        }
        item {
            Button(
                onClick = { onRestore(selectedCredentials) },
                enabled = selectedCredentials.isNotEmpty() && state.restoreSession?.activeBatch == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Restore, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_restore_selected))
            }
        }
        state.restoreSession?.let { session ->
            item {
                val progress = if (session.total == 0) 0f else session.submitted.toFloat() / session.total.toFloat()
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                ListItem(
                    headlineContent = { Text(stringResource(R.string.restore_progress_title, session.submitted, session.total)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.restore_progress_body,
                                session.success,
                                session.alreadyExists,
                                session.failed,
                                session.skipped,
                            ),
                        )
                    },
                )
            }
        }
        if (plan.skipped.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.restore_skipped_title),
                    subtitle = stringResource(R.string.summary_skipped, plan.skipped.size),
                )
            }
            items(plan.skipped.take(12), key = { "skip-${it.credential.id}" }) { item ->
                ListItem(
                    headlineContent = { Text(item.credential.ssid.ifBlank { stringResource(R.string.skip_blank_ssid) }) },
                    supportingContent = { Text(skipReasonLabel(item.reason)) },
                )
                HorizontalDivider()
            }
        }
        if (plan.supportedCredentials.isEmpty()) {
            item { ListItem(headlineContent = { Text(stringResource(R.string.empty_vault)) }) }
        } else {
            items(plan.supportedCredentials, key = { it.id }) { credential ->
                SelectableRestoreRow(
                    credential = credential,
                    selected = credential.id in selectedIds,
                    onSelectedChange = { selected ->
                        selectedIds = if (selected) {
                            (selectedIds + credential.id).distinct()
                        } else {
                            selectedIds - credential.id
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ActivityPane(state: AppState, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                title = stringResource(R.string.headline_activity),
                subtitle = stringResource(R.string.report_count, state.vault.reports.size),
            )
        }
        item { PrivacySummaryCard() }
        if (state.vault.reports.isEmpty()) {
            item { ListItem(headlineContent = { Text(stringResource(R.string.empty_activity)) }) }
        } else {
            items(state.vault.reports, key = { it.id }) { report ->
                ReportRow(report)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PrivacySummaryCard() {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.privacy_summary_title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.privacy_summary_body))
        }
    }
}

@Composable
private fun SummaryGrid(plan: RestorePlan) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        MetricTile(stringResource(R.string.summary_total, plan.items.size), Modifier.weight(1f))
        MetricTile(stringResource(R.string.summary_passwords, plan.items.count { it.credential.hasPassword }), Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        MetricTile(stringResource(R.string.summary_restorable, plan.supported.size), Modifier.weight(1f))
        MetricTile(stringResource(R.string.summary_skipped, plan.skipped.size), Modifier.weight(1f))
    }
}

@Composable
private fun MetricTile(text: String, modifier: Modifier = Modifier) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun ActionPanel(
    title: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.55f)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(14.dp),
        ) {
            Icon(icon, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(body)
            }
        }
    }
}

@Composable
private fun CredentialRow(
    credential: WifiCredential,
    onPasswordCopied: (() -> Unit)?,
    onShare: ((WifiCredential) -> Unit)?,
    onUpdateNote: ((String, String?) -> Unit)?,
    onEdit: ((String, String, Set<SecurityType>, String?, Boolean, String?) -> Unit)?,
    onDelete: ((String) -> Unit)?,
) {
    val context = LocalContext.current
    var detailsExpanded by rememberSaveable(credential.id) { mutableStateOf(false) }
    var actionsOpen by rememberSaveable(credential.id) { mutableStateOf(false) }
    var revealPassword by rememberSaveable(credential.id) { mutableStateOf(false) }
    var noteDialogOpen by rememberSaveable(credential.id) { mutableStateOf(false) }
    var editDialogOpen by rememberSaveable(credential.id) { mutableStateOf(false) }
    var deleteDialogOpen by rememberSaveable(credential.id) { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable { detailsExpanded = !detailsExpanded },
        leadingContent = {
            Icon(
                if (credential.hasPassword) Icons.Rounded.Key else Icons.Rounded.Security,
                contentDescription = null,
            )
        },
        headlineContent = { Text(credential.ssid) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    listOf(
                        securityLabel(credential.security),
                        if (credential.hasPassword) stringResource(R.string.password_saved) else stringResource(R.string.password_missing),
                        sourceLabel(credential.source),
                    ).joinToString(" · "),
                )
                val flags = buildList {
                    if (credential.hidden) add(stringResource(R.string.credential_hidden))
                    if (credential.autoJoin) add(stringResource(R.string.credential_autojoin))
                }
                if (flags.isNotEmpty()) Text(flags.joinToString(" · "))
                credential.note?.let { Text(it) }
                if (detailsExpanded && credential.hasPassword) {
                    Text(
                        text = if (revealPassword) credential.password.orEmpty() else "••••••••",
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { actionsOpen = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.action_more_network, credential.ssid),
                    )
                }
                DropdownMenu(
                    expanded = actionsOpen,
                    onDismissRequest = { actionsOpen = false },
                ) {
                    if (credential.hasPassword) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (revealPassword) R.string.action_hide_password else R.string.action_reveal_password,
                                    ),
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (revealPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                detailsExpanded = true
                                revealPassword = !revealPassword
                                actionsOpen = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_copy_password)) },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                            onClick = {
                                context.copySensitivePassword(credential)
                                onPasswordCopied?.invoke()
                                actionsOpen = false
                            },
                        )
                    }
                    if (onShare != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                            onClick = {
                                onShare(credential)
                                actionsOpen = false
                            },
                        )
                    }
                    if (onUpdateNote != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit_note)) },
                            leadingIcon = { Icon(Icons.Rounded.EditNote, contentDescription = null) },
                            onClick = {
                                noteDialogOpen = true
                                actionsOpen = false
                            },
                        )
                    }
                    if (onEdit != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = {
                                editDialogOpen = true
                                actionsOpen = false
                            },
                        )
                    }
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = {
                                deleteDialogOpen = true
                                actionsOpen = false
                            },
                        )
                    }
                }
            }
        },
    )

    if (noteDialogOpen && onUpdateNote != null) {
        NoteDialog(
            credential = credential,
            onDismiss = { noteDialogOpen = false },
            onSave = { note ->
                onUpdateNote(credential.id, note)
                noteDialogOpen = false
            },
        )
    }
    if (editDialogOpen && onEdit != null) {
        ManualCredentialDialog(
            credential = credential,
            title = stringResource(R.string.edit_dialog_title),
            onDismiss = { editDialogOpen = false },
            onSave = { ssid, security, password, hidden, note ->
                onEdit(credential.id, ssid, security, password, hidden, note)
                editDialogOpen = false
            },
        )
    }
    if (deleteDialogOpen && onDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteDialogOpen = false },
            title = { Text(stringResource(R.string.delete_dialog_title, credential.ssid)) },
            text = { Text(stringResource(R.string.delete_dialog_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(credential.id)
                        deleteDialogOpen = false
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteDialogOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SelectableRestoreRow(
    credential: WifiCredential,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier.toggleable(
            value = selected,
            role = Role.Checkbox,
            onValueChange = onSelectedChange,
        ),
        leadingContent = {
            Checkbox(checked = selected, onCheckedChange = null)
        },
        headlineContent = { Text(credential.ssid) },
        supportingContent = {
            Text(
                listOf(
                    securityLabel(credential.security),
                    sourceLabel(credential.source),
                ).joinToString(" · "),
            )
        },
    )
}

@Composable
private fun ReportRow(report: OperationReport) {
    val kind = when (report.kind) {
        OperationKind.Import -> stringResource(R.string.report_import)
        OperationKind.Extract -> stringResource(R.string.report_extract)
        OperationKind.Restore -> stringResource(R.string.report_restore)
    }
    ListItem(
        headlineContent = { Text(kind) },
        supportingContent = {
            Column {
                Text(
                    stringResource(
                        R.string.report_summary,
                        report.success,
                        report.alreadyExists,
                        report.failed,
                        report.skipped,
                    ),
                )
                report.notes.take(2).forEach { Text(localizedNote(it)) }
            }
        },
    )
}

@Composable
private fun ManualCredentialDialog(
    credential: WifiCredential? = null,
    title: String = stringResource(R.string.manual_dialog_title),
    onDismiss: () -> Unit,
    onSave: (String, Set<SecurityType>, String?, Boolean, String?) -> Unit,
) {
    val initialSecurity = credential?.security?.takeIf { it.isNotEmpty() } ?: setOf(SecurityType.WPA2)
    val securityChoices = remember(credential?.id) {
        val base = listOf(
            setOf(SecurityType.WPA2),
            setOf(SecurityType.WPA3),
            setOf(SecurityType.OPEN),
            setOf(SecurityType.OWE),
        )
        (listOf(initialSecurity) + base)
            .distinctBy(::securityKey)
            .map { SecurityChoice(securityKey(it), it) }
    }
    var ssid by rememberSaveable(credential?.id) { mutableStateOf(credential?.ssid.orEmpty()) }
    var securityKey by rememberSaveable(credential?.id) { mutableStateOf(securityKey(initialSecurity)) }
    var password by rememberSaveable(credential?.id) { mutableStateOf(credential?.password.orEmpty()) }
    var hidden by rememberSaveable(credential?.id) { mutableStateOf(credential?.hidden ?: false) }
    var note by rememberSaveable(credential?.id) { mutableStateOf(credential?.note.orEmpty()) }
    val selectedSecurity = securityChoices.firstOrNull { it.key == securityKey } ?: securityChoices.first()
    val security = selectedSecurity.security
    val passwordRequired = security.any { it == SecurityType.WPA2 || it == SecurityType.WPA3 }
    val showPassword = passwordRequired || credential?.hasPassword == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text(stringResource(R.string.manual_ssid_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.manual_security_label), fontWeight = FontWeight.Medium)
                FilterRow(
                    values = securityChoices,
                    selected = selectedSecurity,
                    label = { securityLabel(it.security) },
                    onSelected = { securityKey = it.key },
                )
                if (showPassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.manual_password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = hidden,
                            role = Role.Checkbox,
                            onValueChange = { hidden = it },
                        )
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(checked = hidden, onCheckedChange = null)
                    Text(stringResource(R.string.manual_hidden_label))
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.note_label)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = ssid.isNotBlank() && (!passwordRequired || password.isNotBlank()),
                onClick = {
                    onSave(
                        ssid,
                        security,
                        password.takeIf { showPassword && it.isNotBlank() },
                        hidden,
                        note.takeIf { it.isNotBlank() },
                    )
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun ExportVaultDialog(
    onDismiss: () -> Unit,
    onConfirm: (Boolean, String?) -> Unit,
) {
    var encrypted by rememberSaveable { mutableStateOf(true) }
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = encrypted,
                            role = Role.Checkbox,
                            onValueChange = { encrypted = it },
                        )
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(checked = encrypted, onCheckedChange = null)
                    Column {
                        Text(stringResource(R.string.export_encrypt_label), fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.export_encrypt_supporting))
                    }
                }
                if (encrypted) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.export_password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !encrypted || password.isNotBlank(),
                onClick = { onConfirm(encrypted, password) },
            ) {
                Text(stringResource(R.string.action_export))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun NoteDialog(
    credential: WifiCredential,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    var note by rememberSaveable(credential.id) { mutableStateOf(credential.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note_dialog_title, credential.ssid)) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.note_label)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onSave(note.ifBlank { null }) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun <T> FilterRow(
    values: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(values) { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelected(value) },
                label = { Text(label(value)) },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun shizukuLabel(state: AppState): String {
    return when {
        !state.shizuku.running -> stringResource(R.string.shizuku_not_running)
        state.shizuku.mode == PrivilegeMode.Root -> stringResource(R.string.shizuku_root)
        state.shizuku.mode == PrivilegeMode.Shell -> stringResource(R.string.shizuku_shell)
        state.shizuku.permissionGranted -> stringResource(R.string.shizuku_other, state.shizuku.uid ?: -1)
        else -> stringResource(R.string.shizuku_permission_required)
    }
}

@Composable
private fun sourceLabel(source: CredentialSource): String {
    return when (source) {
        CredentialSource.Manual -> stringResource(R.string.source_manual)
        CredentialSource.QuickShare -> stringResource(R.string.source_quick_share)
        CredentialSource.Json -> stringResource(R.string.source_json)
        CredentialSource.Csv -> stringResource(R.string.source_csv)
        CredentialSource.WifiQr -> stringResource(R.string.source_wifi_qr)
        CredentialSource.ShizukuShell -> stringResource(R.string.source_shizuku_shell)
        CredentialSource.ShizukuRoot -> stringResource(R.string.source_shizuku_root)
        CredentialSource.RootFile -> stringResource(R.string.source_root_file)
        CredentialSource.SystemDiagnostic -> stringResource(R.string.source_system_diagnostic)
    }
}

@Composable
private fun skipReasonLabel(reason: RestoreSkipReason?): String {
    return when (reason) {
        RestoreSkipReason.BlankSsid -> stringResource(R.string.skip_blank_ssid)
        RestoreSkipReason.MissingPassword -> stringResource(R.string.skip_missing_password)
        RestoreSkipReason.UnsupportedEnterprise -> stringResource(R.string.skip_unsupported_enterprise)
        RestoreSkipReason.UnsupportedWep -> stringResource(R.string.skip_unsupported_wep)
        RestoreSkipReason.UnsupportedSecurity -> stringResource(R.string.skip_unsupported_security)
        RestoreSkipReason.InvalidPassphrase -> stringResource(R.string.skip_invalid_passphrase)
        null -> stringResource(R.string.skip_unsupported_security)
    }
}

@Composable
private fun localizedNote(raw: String): String {
    val parts = raw.split("|")
    val code = parts.firstOrNull().orEmpty()
    fun arg(index: Int): String = parts.getOrNull(index).orEmpty()
    return when (code) {
        "extract.shizuku_unavailable" -> stringResource(R.string.note_extract_shizuku_unavailable)
        "extract.config_read_failed" -> stringResource(R.string.note_extract_config_read_failed, arg(1))
        "extract.no_readable_xml" -> stringResource(R.string.note_extract_no_readable_xml, arg(1))
        "extract.parse_failed" -> stringResource(R.string.note_extract_parse_failed, arg(1), arg(2))
        "extract.parsed_path" -> stringResource(R.string.note_extract_parsed_path, arg(1), arg(2))
        "extract.cmd_list_failed" -> stringResource(R.string.note_extract_cmd_list_failed, arg(1))
        "extract.cmd_denied" -> stringResource(R.string.note_extract_cmd_denied)
        "extract.shell_password_limited" -> stringResource(R.string.note_extract_shell_password_limited)
        "extract.root_no_passwords" -> stringResource(R.string.note_extract_root_no_passwords)
        "import.skip" -> {
            val reason = localizedNote(arg(2))
            stringResource(R.string.note_import_skip, arg(1), reason)
        }
        "import.json_item_not_object" -> stringResource(R.string.note_import_json_item_not_object)
        "import.missing_ssid_or_security" -> stringResource(R.string.note_import_missing_ssid_or_security)
        "import.empty_csv" -> stringResource(R.string.note_import_empty_csv)
        "import.missing_ssid" -> stringResource(R.string.note_import_missing_ssid)
        "import.missing_qr_ssid" -> stringResource(R.string.note_import_missing_qr_ssid)
        "restore.android_batches" -> stringResource(R.string.note_restore_android_batches)
        else -> raw
    }
}

private fun WifiCredential.matchesFilter(filter: CredentialFilter, plan: RestorePlan): Boolean {
    return when (filter) {
        CredentialFilter.All -> true
        CredentialFilter.Restorable -> plan.supported.any { it.credential.id == id }
        CredentialFilter.MissingPassword -> !hasPassword
        CredentialFilter.Imported -> source in setOf(CredentialSource.QuickShare, CredentialSource.Json, CredentialSource.Csv, CredentialSource.WifiQr)
        CredentialFilter.Extracted -> source in setOf(CredentialSource.ShizukuShell, CredentialSource.ShizukuRoot, CredentialSource.RootFile, CredentialSource.SystemDiagnostic)
    }
}

private fun securityKey(security: Set<SecurityType>): String {
    return security.sortedBy { it.ordinal }.joinToString("+") { it.name }
}

private fun iconFor(destination: Destination) = when (destination) {
    Destination.Vault -> Icons.Rounded.Security
    Destination.Add -> Icons.Rounded.Add
    Destination.Restore -> Icons.Rounded.Restore
    Destination.Activity -> Icons.Rounded.Assessment
}

private fun Context.copySensitivePassword(credential: WifiCredential) {
    copySensitiveText(credential.ssid, credential.password.orEmpty())
}

private fun Context.copySensitiveText(label: String, value: String) {
    val clip = ClipData.newPlainText(label, value)
    clip.description.extras = PersistableBundle().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        } else {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(clip)
}

private fun Context.shareCredential(credential: WifiCredential) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, WifiShareFormatter.shareText(credential))
    startActivity(Intent.createChooser(intent, getString(R.string.message_share_sheet_title)))
}

private fun Context.readDocumentBytes(uri: Uri): ByteArray? {
    return contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            require(total <= MAX_IMPORT_BYTES) { getString(R.string.message_import_too_large) }
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }
}

private const val MAX_IMPORT_BYTES = 16 * 1024 * 1024
