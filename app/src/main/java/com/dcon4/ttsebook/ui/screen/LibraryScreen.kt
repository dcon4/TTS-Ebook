package com.dcon4.ttsebook.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dcon4.ttsebook.R
import com.dcon4.ttsebook.data.BookEntity
import com.dcon4.ttsebook.ui.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookSelected: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val books by viewModel.books.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    val context = LocalContext.current

    var showDebugDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importBook(it) }
    }

    LaunchedEffect(importResult) {
        importResult?.let { result ->
            result.onSuccess { ebook ->
                onBookSelected(ebook.id)
            }
            viewModel
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TTS Ebook") },
                actions = {
                    IconButton(
                        onClick = { showDebugDialog = true },
                        modifier = Modifier.semantics { contentDescription = "Share debug log" }
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.semantics { contentDescription = "Add book" }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isImporting) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (books.isEmpty()) {
                Text(
                    text = "No books found. Tap the + button to add a book.",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(books, key = { it.id }) { book ->
                        BookListItem(
                            book = book,
                            onClick = { onBookSelected(book.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(book.id) },
                            onDelete = { viewModel.removeBook(book.id) }
                        )
                    }
                }
            }
        }
    }

    if (showDebugDialog) {
        AlertDialog(
            onDismissRequest = { showDebugDialog = false },
            title = { Text("Debug Log") },
            text = { Text("Share the debug log file with another app, or open an email with the subject and file pre-filled.") },
            confirmButton = {
                TextButton(onClick = {
                    val file = com.dcon4.ttsebook.debug.DebugLogger.getLogFile()?.takeIf { it.exists() }
                    if (file != null) {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${com.dcon4.ttsebook.BuildConfig.APPLICATION_ID}.debug.fileprovider",
                            file
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share debug log"))
                    }
                    showDebugDialog = false
                }) { Text("Share") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val file = com.dcon4.ttsebook.debug.DebugLogger.getLogFile()?.takeIf { it.exists() }
                    if (file != null) {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${com.dcon4.ttsebook.BuildConfig.APPLICATION_ID}.debug.fileprovider",
                            file
                        )
                        val dateTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "message/rfc822"
                            putExtra(Intent.EXTRA_SUBJECT, "TTS Ebook debug log - $dateTime")
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Email debug log"))
                    }
                    showDebugDialog = false
                }) { Text("Email") }
            }
        )
    }
}

@Composable
private fun BookListItem(
    book: BookEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${book.format.uppercase()} - ${if (book.isFavorite) "Favorited" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.semantics {
                    contentDescription = if (book.isFavorite) "Remove from favorites" else "Add to favorites"
                }
            ) {
                Icon(
                    if (book.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = if (book.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.semantics { contentDescription = "Remove book" }
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
            }
        }
    }
}
