package com.skript.omnialbum.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skript.omnialbum.AppState
import com.skript.omnialbum.model.PhotoProvider
import com.skript.omnialbum.providers.ProviderRegistry
import kotlinx.coroutines.launch

private enum class AddMode { SINGLE, BULK }

private sealed interface BulkPhase {
    data object Adding : BulkPhase
    data object Succeeded : BulkPhase
    data class Failed(val error: String) : BulkPhase
    data object Unrecognized : BulkPhase
}

private data class BulkLineStatus(
    val url: String,
    val provider: PhotoProvider? = null,
    val phase: BulkPhase = BulkPhase.Adding
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAlbumSheet(
    appState: AppState,
    onDismiss: () -> Unit
) {
    val viewModel = appState.aggregator
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(AddMode.SINGLE) }
    var urlText by remember { mutableStateOf("") }
    var passwordText by remember { mutableStateOf("") }
    var isValidating by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var requiresPassword by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf<PhotoProvider?>(null) }
    var bulkText by remember { mutableStateOf("") }
    var bulkProcessing by remember { mutableStateOf(false) }
    var bulkStatuses by remember { mutableStateOf<List<BulkLineStatus>>(emptyList()) }
    val vmError by viewModel.errorMessage.collectAsState()

    fun addSingle() {
        val url = urlText.trim()
        val provider = selectedProvider ?: return
        if (url.isEmpty()) return
        isValidating = true
        localError = null
        scope.launch {
            val password = passwordText.ifEmpty { null }
            viewModel.addAlbum(url, provider, password)
            isValidating = false
            val error = viewModel.errorMessage.value
            if (error != null && isPasswordRequiredError(error)) {
                requiresPassword = true
                viewModel.clearError()
            } else if (error != null) {
                localError = error
            } else {
                onDismiss()
            }
        }
    }

    fun addAllBulk() {
        val lines = bulkText.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return
        bulkProcessing = true
        bulkStatuses = emptyList()
        scope.launch {
            for (line in lines) {
                val provider = ProviderRegistry.shared.detectProvider(line)
                if (provider == null) {
                    bulkStatuses = bulkStatuses + BulkLineStatus(line, phase = BulkPhase.Unrecognized)
                    continue
                }
                bulkStatuses = bulkStatuses + BulkLineStatus(line, provider, BulkPhase.Adding)
                viewModel.clearError()
                viewModel.addAlbum(line, provider)
                val error = viewModel.errorMessage.value
                val index = bulkStatuses.lastIndex
                bulkStatuses = bulkStatuses.toMutableList().also { list ->
                    list[index] = list[index].copy(
                        phase = if (error != null) BulkPhase.Failed(error) else BulkPhase.Succeeded
                    )
                }
                if (error != null) viewModel.clearError()
            }
            bulkProcessing = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Add album", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AddMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { mode = m },
                        shape = SegmentedButtonDefaults.itemShape(i, AddMode.entries.size)
                    ) {
                        Text(if (m == AddMode.SINGLE) "Single" else "Bulk Import")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (mode == AddMode.SINGLE) {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        selectedProvider = ProviderRegistry.shared.detectProvider(it.trim())
                        requiresPassword = false
                        localError = null
                    },
                    label = { Text("Share link") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !requiresPassword
                )
                Text(
                    selectedProvider?.linkFooter ?: "Paste a shared album link from any supported service.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(12.dp))

                Text("Service", style = MaterialTheme.typography.titleMedium)
                PhotoProvider.entries.forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProviderBadge(provider, 32)
                        Spacer(Modifier.size(12.dp))
                        Text(provider.displayName, modifier = Modifier.weight(1f))
                        if (selectedProvider == provider) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    androidx.compose.foundation.clickable {
                        selectedProvider = provider
                    }.let { /* clickable applied below via Modifier in Row */ }
                }

                if (selectedProvider == PhotoProvider.IMMICH && !requiresPassword) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordText,
                        onValueChange = { passwordText = it; localError = null },
                        label = { Text("Password (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (requiresPassword) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordText,
                        onValueChange = { passwordText = it; localError = null },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "This album is password protected. Enter the password to access it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (isValidating) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Validating link…")
                    }
                }
                if (localError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(localError ?: "", color = MaterialTheme.colorScheme.error)
                }
            } else {
                OutlinedTextField(
                    value = bulkText,
                    onValueChange = { bulkText = it },
                    label = { Text("Links") },
                    supportingText = { Text("Paste one link per line. Providers are auto-detected.") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !bulkProcessing
                )
                if (bulkStatuses.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Log", style = MaterialTheme.typography.titleMedium)
                    bulkStatuses.forEach { status ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            when (status.phase) {
                                BulkPhase.Adding -> CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                BulkPhase.Succeeded -> Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                is BulkPhase.Failed -> Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                BulkPhase.Unrecognized -> Icon(Icons.Default.Help, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.size(8.dp))
                            Column {
                                Text(status.url, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                val phaseText = when (val p = status.phase) {
                                    BulkPhase.Adding -> "Detected ${status.provider?.displayName ?: ""} — Adding…"
                                    BulkPhase.Succeeded -> "Detected ${status.provider?.displayName ?: ""} — Added"
                                    is BulkPhase.Failed -> "Detected ${status.provider?.displayName ?: ""} — ${p.error}"
                                    BulkPhase.Unrecognized -> "Unrecognized link"
                                }
                                Text(
                                    phaseText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (status.phase is BulkPhase.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(
                    onClick = { if (mode == AddMode.SINGLE) addSingle() else addAllBulk() },
                    enabled = when (mode) {
                        AddMode.SINGLE -> selectedProvider != null && urlText.isNotBlank() && !isValidating && (!requiresPassword || passwordText.isNotEmpty())
                        AddMode.BULK -> bulkText.isNotBlank() && !bulkProcessing
                    }
                ) {
                    Text(if (mode == AddMode.SINGLE) "Add" else "Add All")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun isPasswordRequiredError(error: String): Boolean {
    val e = error.lowercase()
    return e.contains("401") || e.contains("password") ||
        e.contains("unauthorized") || e.contains("authentication")
}
