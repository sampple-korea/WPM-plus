package com.sampple.wifivaultrestore.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sampple.wifivaultrestore.R
import com.sampple.wifivaultrestore.data.SecurityType
import com.sampple.wifivaultrestore.data.WifiCredential
import com.sampple.wifivaultrestore.data.report.OperationKind
import com.sampple.wifivaultrestore.data.report.OperationReport
import com.sampple.wifivaultrestore.data.restore.WifiRestoreIntentFactory
import com.sampple.wifivaultrestore.data.securityLabel
import com.sampple.wifivaultrestore.shizuku.PrivilegeMode
import com.sampple.wifivaultrestore.ui.model.AppState
import com.sampple.wifivaultrestore.ui.model.MainViewModel
import kotlinx.coroutines.launch

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
    val destinations = Destination.entries

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                viewModel.importBytes(uri.lastPathSegment, bytes)
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
}

@Composable
private fun VaultPane(
    state: AppState,
    padding: PaddingValues,
    onImport: () -> Unit,
) {
    ScreenColumn(padding) {
        SectionHeader(stringResource(R.string.headline_vault), "${state.vault.credentials.size} networks")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onImport) { Text(stringResource(R.string.action_import)) }
            AssistChip(onClick = {}, label = { Text(stringResource(R.string.status_unlocked)) })
        }
        CredentialList(state.vault.credentials)
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = onRefresh, label = { Text(shizukuLabel(state)) })
            OutlinedButton(onClick = onRequestPermission) { Text(stringResource(R.string.action_request_shizuku)) }
        }
        Button(
            onClick = onExtract,
            enabled = state.shizuku.running && state.shizuku.permissionGranted,
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
        SectionHeader(stringResource(R.string.headline_restore), "${restorable.size} restorable networks")
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
        SectionHeader(stringResource(R.string.headline_reports), "${state.vault.reports.size} recent reports")
        LazyColumn {
            items(state.vault.reports, key = { it.id }) { report ->
                ReportRow(report)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CredentialList(credentials: List<WifiCredential>) {
    if (credentials.isEmpty()) {
        ListItem(headlineContent = { Text(stringResource(R.string.empty_vault)) })
        return
    }
    LazyColumn {
        items(credentials, key = { it.id }) { credential ->
            ListItem(
                leadingContent = {
                    Icon(
                        if (credential.hasPassword) Icons.Rounded.Key else Icons.Rounded.Security,
                        contentDescription = null,
                    )
                },
                headlineContent = { Text(credential.ssid) },
                supportingContent = {
                    Text("${securityLabel(credential.security)} · ${if (credential.hasPassword) "password saved" else "no password"}")
                },
            )
            HorizontalDivider()
        }
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
private fun ScreenColumn(
    padding: PaddingValues,
    content: @Composable Column.() -> Unit,
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
