package com.skript.omnialbum.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skript.omnialbum.AppState
import com.skript.omnialbum.platform.SettingsStore
import com.skript.omnialbum.platform.shareCsvFile
import com.skript.omnialbum.platform.pickCsvFile
import com.skript.omnialbum.util.Csv
import kotlinx.coroutines.launch

private const val KEY_WIFI_ONLY_UPLOAD = "wifiOnlyUpload"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appState: AppState,
    modifier: Modifier = Modifier
) {
    val viewModel = appState.aggregator
    val albums by viewModel.albums.collectAsState()
    val scope = rememberCoroutineScope()

    var wifiOnly by remember { mutableStateOf(SettingsStore.getBool(KEY_WIFI_ONLY_UPLOAD, false)) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var dialogTitle by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Uploads", style = MaterialTheme.typography.titleMedium)
            ListItem(
                headlineContent = { Text("Wi-Fi Only Uploads") },
                supportingContent = { Text("Pause uploads when on cellular data.") },
                leadingContent = { Icon(Icons.Default.Info, null) },
                trailingContent = {
                    Switch(
                        checked = wifiOnly,
                        onCheckedChange = {
                            wifiOnly = it
                            SettingsStore.setBool(KEY_WIFI_ONLY_UPLOAD, it)
                        }
                    )
                }
            )

            Text("Data", style = MaterialTheme.typography.titleMedium)
            ListItem(
                headlineContent = { Text("Export Albums to CSV") },
                leadingContent = { Icon(Icons.Default.Upload, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (albums.isEmpty()) {
                            dialogTitle = "Export"
                            dialogMessage = "No albums to export"
                        } else {
                            shareCsvFile(Csv.exportFileName(), Csv.export(albums))
                        }
                    }
            )
            ListItem(
                headlineContent = { Text("Import Albums from CSV") },
                leadingContent = { Icon(Icons.Default.Download, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        pickCsvFile { content ->
                            if (content == null) return@pickCsvFile
                            val links = Csv.parseImport(content)
                            if (links.isEmpty()) {
                                dialogTitle = "Import Error"
                                dialogMessage = "No valid share links found in the CSV file"
                            } else {
                                scope.launch {
                                    var added = 0
                                    for (link in links) {
                                        val before = viewModel.albums.value.size
                                        viewModel.addAlbum(link.url, link.provider)
                                        if (viewModel.albums.value.size > before) added++
                                    }
                                    dialogTitle = "Import Successful"
                                    dialogMessage = "Successfully imported $added album${if (added == 1) "" else "s"}."
                                }
                            }
                        }
                    }
            )

            Text("About", style = MaterialTheme.typography.titleMedium)
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("1.0 (Compose Multiplatform)") },
                leadingContent = { Icon(Icons.Default.Info, null) }
            )
            ListItem(
                headlineContent = { Text("OmniAlbum") },
                supportingContent = { Text("Browse shared photo albums from multiple self-hosted services — all in one place.") }
            )
        }
    }

    if (dialogMessage != null) {
        AlertDialog(
            onDismissRequest = { dialogMessage = null },
            title = { Text(dialogTitle) },
            text = { Text(dialogMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { dialogMessage = null }) { Text("OK") }
            }
        )
    }
}
