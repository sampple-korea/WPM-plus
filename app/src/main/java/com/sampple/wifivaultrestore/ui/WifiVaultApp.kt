package com.sampple.wifivaultrestore.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sampple.wifivaultrestore.R
import com.sampple.wifivaultrestore.data.WifiCredential
import com.sampple.wifivaultrestore.data.report.OperationKind
import com.sampple.wifivaultrestore.data.report.OperationReport
import com.sampple.wifivaultrestore.data.restore.WifiRestoreIntentFactory
import com.sampple.wifivaultrestore.data.securityLabel
import com.sampple.wifivaultrestore.shizuku.PrivilegeMode
import com.sampple.wifivaultrestore.ui.model.AppState
import com.sampple.wifivaultrestore.ui.model.MainViewModel
import kotlinx.coroutines.launch

private data class PendingExport(
    val bytes: ByteArray,
    val message: String,
)

private enum class Destination(val labelRes: Int) {
    Vault(R.string.nav_vault),
    Extract(R.string.nav_extract),
    Restore(R.string.nav_restore),
    Reports(R.string.nav_reports),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiVaultApp(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedIndex by remember { mutableIntStateOf(0) }
    var qrDialogOpen by remember { mutableStateOf(false) }
    var qrText by remember { mutableStateOf("") }
    var exportDialogOpen by remember { mutableStateOf(false) }
    var importPassword by remember { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf<PendingExport?>(null) }
    val destinations = Destination.entries

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                viewModel.importBytes(uri.lastPathSegment, bytes)
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
                    ?: error(context.getString(R.string.message_export_failed))
            }.onSuccess {
                scope.launch { snackbarHostState.showSnackbar(export.message) }
            }.onFailure { error ->
                scope.launch {
                    snackbarHostState.showSnackbar(error.message ?: context.getString(R.string.message_export_failed))
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
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                    }
                },
            )
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
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onExport = { exportDialogOpen = true },
                onPasteQr = { qrDialogOpen = true },
                onPasswordCopied = {
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.message_password_copied)) }
                },
                onUpdateNote = viewModel::updateNote,
            )
            Destination.Extract -> ExtractPane(
                state = state,
                padding = padding,
                onRefresh = viewModel::refreshShizukuState,
                onRequestPermission = viewModel::requestShizukuPermission,
                onExtract = viewModel::extractSystem,
            )
            Destination.Restore -> RestorePane(
                state = state,
                padding = padding,
                onRestore = viewModel::startRestore,
            )
            Destination.Reports -> ReportsPane(state = state, padding = padding)
        }
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
                    pendingExport = PendingExport(bytes, context.getString(R.string.message_export_complete))
                    exportLauncher.launch(fileName)
                    exportDialogOpen = false
                }.onFailure { error ->
                    scope.launch {
                        snackbarHostState.showSnackbar(error.message ?: context.getString(R.string.message_export_failed))
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
                    OutlinedTextField(
                        value = report.take(4000),
                        onValueChange = {},
                        readOnly = true,
                        minLines = 6,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        context.copySensitiveText(
                            label = context.getString(R.string.crash_clipboard_label),
                            value = report,
                        )
                        viewModel.clearPendingCrashReport()
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.crash_copied_message))
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
    onImport: () -> Unit,
    onExport: () -> Unit,
    onPasteQr: () -> Unit,
    onPasswordCopied: () -> Unit,
    onUpdateNote: (String, String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(state.vault.credentials, query) {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) {
            state.vault.credentials
        } else {
            state.vault.credentials.filter { credential ->
                credential.ssid.lowercase().contains(needle) ||
                    credential.note.orEmpty().lowercase().contains(needle) ||
                    securityLabel(credential.security).lowercase().contains(needle)
            }
        }
    }
    ScreenColumn(padding) {
        SectionHeader(stringResource(R.string.headline_vault), stringResource(R.string.vault_count, state.vault.credentials.size))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            label = { Text(stringResource(R.string.search_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_import))
                }
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_export))
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(onClick = onPasteQr, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_paste_qr))
                }
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.status_unlocked)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        CredentialList(
            credentials = filtered,
            onPasswordCopied = onPasswordCopied,
            onUpdateNote = onUpdateNote,
        )
    }
}

@Composable
private fun ExtractPane(
    state: AppState,
    padding: PaddingValues,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onExtract: () -> Unit,
) {
    ScreenColumn(padding) {
        SectionHeader(stringResource(R.string.headline_extract), stringResource(R.string.extract_note))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = onRefresh, label = { Text(shizukuLabel(state)) })
            OutlinedButton(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_request_shizuku))
            }
        }
        Button(
            onClick = onExtract,
            enabled = state.shizuku.running && state.shizuku.permissionGranted,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_extract_system))
        }
        state.lastExtraction?.let { outcome ->
            ListItem(
                headlineContent = { Text("${outcome.credentials.size} entries extracted") },
                supportingContent = {
                    Text("${outcome.withPasswords} with passwords, ${outcome.withoutPasswords} without passwords")
                },
            )
            outcome.notes.take(5).forEach { note ->
                ListItem(headlineContent = { Text(note) })
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
    ScreenColumn(padding) {
        val restorable = state.vault.credentials.filter { it.canRestore }
        SectionHeader(stringResource(R.string.headline_restore), stringResource(R.string.restorable_count, restorable.size))
        Button(
            onClick = { onRestore(restorable) },
            enabled = restorable.isNotEmpty() && state.restoreSession?.activeBatch == null,
        ) {
            Text(stringResource(R.string.action_restore_selected))
        }
        state.restoreSession?.let { session ->
            val progress = if (session.total == 0) 0f else session.submitted.toFloat() / session.total.toFloat()
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            ListItem(
                headlineContent = { Text("${session.submitted} / ${session.total} submitted") },
                supportingContent = {
                    Text("${session.success} saved, ${session.alreadyExists} existing, ${session.failed} failed, ${session.skipped} skipped")
                },
            )
        }
        CredentialList(restorable.take(30))
    }
}

@Composable
private fun ReportsPane(state: AppState, padding: PaddingValues) {
    ScreenColumn(padding) {
        SectionHeader(stringResource(R.string.headline_reports), stringResource(R.string.report_count, state.vault.reports.size))
        LazyColumn {
            items(state.vault.reports, key = { it.id }) { report ->
                ReportRow(report)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CredentialList(
    credentials: List<WifiCredential>,
    onPasswordCopied: (() -> Unit)? = null,
    onUpdateNote: ((String, String?) -> Unit)? = null,
) {
    if (credentials.isEmpty()) {
        ListItem(headlineContent = { Text(stringResource(R.string.empty_vault)) })
        return
    }
    LazyColumn {
        items(credentials, key = { it.id }) { credential ->
            CredentialRow(
                credential = credential,
                onPasswordCopied = onPasswordCopied,
                onUpdateNote = onUpdateNote,
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun CredentialRow(
    credential: WifiCredential,
    onPasswordCopied: (() -> Unit)?,
    onUpdateNote: ((String, String?) -> Unit)?,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var revealPassword by remember { mutableStateOf(false) }
    var noteDialogOpen by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable { expanded = !expanded },
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
                        credential.source.name,
                    ).joinToString(" · "),
                )
                credential.note?.let { Text(it) }
                if (expanded && credential.hasPassword) {
                    Text(
                        text = if (revealPassword) credential.password.orEmpty() else "••••••••",
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (credential.hasPassword) {
                    IconButton(onClick = { revealPassword = !revealPassword }) {
                        Icon(
                            if (revealPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null,
                        )
                    }
                    IconButton(
                        onClick = {
                            context.copySensitivePassword(credential)
                            onPasswordCopied?.invoke()
                        },
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    }
                }
                if (onUpdateNote != null) {
                    IconButton(onClick = { noteDialogOpen = true }) {
                        Icon(Icons.Rounded.EditNote, contentDescription = null)
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
}

@Composable
private fun ReportRow(report: OperationReport) {
    val kind = when (report.kind) {
        OperationKind.Import -> "Import"
        OperationKind.Extract -> "Extract"
        OperationKind.Restore -> "Restore"
    }
    ListItem(
        headlineContent = { Text(kind) },
        supportingContent = {
            Text("${report.success} success · ${report.alreadyExists} existing · ${report.failed} failed · ${report.skipped} skipped")
        },
    )
}

@Composable
private fun ExportVaultDialog(
    onDismiss: () -> Unit,
    onConfirm: (Boolean, String?) -> Unit,
) {
    var encrypted by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(
                        checked = encrypted,
                        onCheckedChange = { encrypted = it },
                    )
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
    var note by remember(credential.id) { mutableStateOf(credential.note.orEmpty()) }
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
private fun ScreenColumn(
    padding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Surface(tonalElevation = 2.dp, shape = androidx.compose.material3.MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle)
        }
    }
}

@Composable
private fun shizukuLabel(state: AppState): String {
    return when {
        !state.shizuku.running -> stringResource(R.string.shizuku_not_running)
        state.shizuku.mode == PrivilegeMode.Root -> stringResource(R.string.shizuku_root)
        state.shizuku.mode == PrivilegeMode.Shell -> stringResource(R.string.shizuku_shell)
        state.shizuku.permissionGranted -> "Shizuku uid ${state.shizuku.uid}"
        else -> "Shizuku permission required"
    }
}

private fun iconFor(destination: Destination) = when (destination) {
    Destination.Vault -> Icons.Rounded.Security
    Destination.Extract -> Icons.Rounded.FileDownload
    Destination.Restore -> Icons.Rounded.Restore
    Destination.Reports -> Icons.Rounded.Assessment
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
