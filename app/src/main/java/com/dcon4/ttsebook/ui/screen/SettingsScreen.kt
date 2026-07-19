package com.dcon4.ttsebook.ui.screen

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dcon4.ttsebook.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val engines by viewModel.engines.collectAsState()
    val voices by viewModel.voices.collectAsState()
    val selectedEngine by viewModel.selectedEngine.collectAsState()
    val selectedVoice by viewModel.selectedVoice.collectAsState()
    val speechRate by viewModel.speechRate.collectAsState()
    val verboseEnabled by viewModel.verboseEnabled.collectAsState()

    var showDebugDialog by remember { mutableStateOf(false) }
    var showEngineDialog by remember { mutableStateOf(false) }
    var showVoiceDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Back" }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("TTS Settings", style = MaterialTheme.typography.titleMedium)
            }

            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("TTS Engine", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = selectedEngine ?: "System default",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { showEngineDialog = true }) {
                            Text("Change engine")
                        }
                    }
                }
            }

            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("TTS Voice", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = selectedVoice ?: "System default",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { showVoiceDialog = true }) {
                            Text("Change voice")
                        }
                    }
                }
            }

            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Reading Speed", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = speechRate,
                            onValueChange = { viewModel.setSpeechRate(it) },
                            valueRange = 0.25f..2.0f,
                            steps = 6,
                            modifier = Modifier.semantics {
                                contentDescription = "Reading speed: ${String.format("%.2f", speechRate)}"
                            }
                        )
                        Text(
                            text = "${String.format("%.2f", speechRate)}x",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Text("Debug", style = MaterialTheme.typography.titleMedium)
            }

            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Verbose Logging", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = if (verboseEnabled) "Enabled" else "Disabled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = verboseEnabled,
                            onCheckedChange = { viewModel.toggleVerboseLogging() },
                            modifier = Modifier.semantics {
                                contentDescription = "Toggle verbose logging"
                            }
                        )
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { showDebugDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Share debug log")
                }
            }
        }
    }

    if (showEngineDialog) {
        AlertDialog(
            onDismissRequest = { showEngineDialog = false },
            title = { Text("Select TTS Engine") },
            text = {
                LazyColumn {
                    items(engines) { engine ->
                        TextButton(
                            onClick = {
                                viewModel.selectEngine(engine.name)
                                showEngineDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(engine.label.ifBlank { engine.name })
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showVoiceDialog) {
        AlertDialog(
            onDismissRequest = { showVoiceDialog = false },
            title = { Text("Select TTS Voice") },
            text = {
                LazyColumn {
                    items(voices) { voice ->
                        TextButton(
                            onClick = {
                                viewModel.selectVoice(voice.name)
                                showVoiceDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(voice.name)
                                Text(
                                    text = "${voice.locale.displayName}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showDebugDialog) {
        AlertDialog(
            onDismissRequest = { showDebugDialog = false },
            title = { Text("Debug Log") },
            text = { Text("Share the debug log file with another app, or open an email with the subject and file pre-filled.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.getShareLogIntent()?.let { intent ->
                        context.startActivity(Intent.createChooser(intent, "Share debug log"))
                    }
                    showDebugDialog = false
                }) { Text("Share") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.getEmailLogIntent()?.let { intent ->
                        context.startActivity(Intent.createChooser(intent, "Email debug log"))
                    }
                    showDebugDialog = false
                }) { Text("Email") }
            }
        )
    }
}
