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
import androidx.compose.material.icons.filled.*
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
import com.dcon4.ttsebook.BuildConfig
import com.dcon4.ttsebook.data.BookEntity
import com.dcon4.ttsebook.debug.DebugLogger
import com.dcon4.ttsebook.ui.viewmodel.LibraryViewModel
import com.dcon4.ttsebook.ui.viewmodel.SortMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookSelected: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val books by viewModel.books.collectAsState()
    val recentBooks by viewModel.recentBooks.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val deleteConfirmBookId by viewModel.deleteConfirmBookId.collectAsState()
    val context = LocalContext.current

    var showDebugDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }

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
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TTS Ebook") },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.semantics { contentDescription = "Sort books" }
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        viewModel.setSortMode(mode)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortMode == mode) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { showDebugDialog = true },
                        modifier = Modifier.semantics { contentDescription = "Share debug log" }
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null)
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.semantics { contentDescription = "Settings" }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
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
                    if (recentBooks.isNotEmpty()) {
                        item {
                            Text(
                                text = "Recent",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(recentBooks, key = { "recent_${it.id}" }) { book ->
                            BookListItem(
                                book = book,
                                dateFormatter = dateFormatter,
                                onClick = { onBookSelected(book.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(book.id) },
                                onDelete = { viewModel.requestDelete(book.id) }
                            )
                        }
                        item {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            Text(
                                text = "All Books",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    items(books, key = { it.id }) { book ->
                        BookListItem(
                            book = book,
                            dateFormatter = dateFormatter,
                            onClick = { onBookSelected(book.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(book.id) },
                            onDelete = { viewModel.requestDelete(book.id) }
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
                    val file = DebugLogger.getLogFile()?.takeIf { it.exists() }
                    if (file != null) {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${BuildConfig.APPLICATION_ID}.debug.fileprovider",
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
                    val file = DebugLogger.getLogFile()?.takeIf { it.exists() }
                    if (file != null) {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${BuildConfig.APPLICATION_ID}.debug.fileprovider",
                            file
                        )
                        val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
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

    if (deleteConfirmBookId != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("Remove Book") },
            text = { Text("Are you sure you want to remove this book from your library? The book file will be deleted.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BookListItem(
    book: BookEntity,
    dateFormatter: SimpleDateFormat,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = book.format.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row {
                    Text(
                        text = dateFormatter.format(Date(book.lastOpenedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
