package com.sampple.wifivaultrestore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sampple.wifivaultrestore.R

private enum class Destination(val labelRes: Int) {
    Vault(R.string.nav_vault),
    Extract(R.string.nav_extract),
    Restore(R.string.nav_restore),
    Reports(R.string.nav_reports),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiVaultApp() {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val destinations = Destination.entries

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
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
            Destination.Vault -> PlaceholderPane(
                padding,
                R.string.headline_vault,
                R.string.empty_vault,
            )
            Destination.Extract -> PlaceholderPane(
                padding,
                R.string.headline_extract,
                R.string.extract_note,
            )
            Destination.Restore -> PlaceholderPane(
                padding,
                R.string.headline_restore,
                R.string.status_ready,
            )
            Destination.Reports -> PlaceholderPane(
                padding,
                R.string.headline_reports,
                R.string.status_ready,
            )
        }
    }
}

@Composable
private fun PlaceholderPane(
    padding: PaddingValues,
    titleRes: Int,
    bodyRes: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            tonalElevation = 2.dp,
            shape = androidx.compose.material3.MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(titleRes)) },
                supportingContent = { Text(stringResource(bodyRes)) },
            )
        }
        Row(Modifier.fillMaxWidth()) {}
    }
}

private fun iconFor(destination: Destination) = when (destination) {
    Destination.Vault -> Icons.Rounded.Security
    Destination.Extract -> Icons.Rounded.FileDownload
    Destination.Restore -> Icons.Rounded.Restore
    Destination.Reports -> Icons.Rounded.Assessment
}
