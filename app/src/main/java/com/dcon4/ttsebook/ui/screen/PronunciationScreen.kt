package com.dcon4.ttsebook.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dcon4.ttsebook.ui.viewmodel.PronunciationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PronunciationScreen(
    onBack: () -> Unit,
    viewModel: PronunciationViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val error by viewModel.error.collectAsState()
    val focusManager = LocalFocusManager.current

    var word by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom Pronunciations") },
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
                Text(
                    "Add a word or phrase and how it should be pronounced. The replacement is what gets read aloud.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = word,
                            onValueChange = { word = it },
                            label = { Text("Word or phrase") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Word or phrase to replace" },
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = replacement,
                            onValueChange = { replacement = it },
                            label = { Text("Replacement text") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Replacement pronunciation text" },
                            singleLine = true
                        )
                        if (error != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = error ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.testPronunciation(replacement)
                                    viewModel.clearError()
                                },
                                enabled = replacement.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Test pronunciation")
                            }
                            Button(
                                onClick = {
                                    viewModel.addEntry(word, replacement)
                                    if (error == null) {
                                        word = ""
                                        replacement = ""
                                        focusManager.clearFocus()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Add")
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Entries (${entries.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (entries.isEmpty()) {
                item {
                    Text(
                        "No entries yet. Add a word that is mispronounced and how it should sound instead.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(entries, key = { it.id }) { entry ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(entry.word, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Reads as: ${entry.replacement}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Match case for ${entry.word}" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = entry.matchCase,
                                onCheckedChange = { viewModel.setMatchCase(entry.id, it) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Match case",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "(replace only when letter case matches exactly)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.testPronunciation(entry.replacement) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Test")
                            }
                            OutlinedButton(
                                onClick = { viewModel.removeEntry(entry.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = "Delete entry for ${entry.word}" }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}