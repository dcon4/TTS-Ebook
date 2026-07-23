package com.dcon4.ttsebook.ui.screen

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dcon4.ttsebook.BuildConfig
import com.dcon4.ttsebook.debug.DebugLogger
import com.dcon4.ttsebook.ui.viewmodel.ReaderViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    initialChapterIndex: Int = -1,
    initialParagraphIndex: Int = -1,
    onNavigateToSearch: () -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val currentBook by viewModel.currentBook.collectAsState()
    val currentChapterIndex by viewModel.currentChapterIndex.collectAsState()
    val currentParagraphIndex by viewModel.currentParagraphIndex.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val paragraphs by viewModel.paragraphs.collectAsState()
    val paragraphCount by viewModel.paragraphCount.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    var showDebugDialog by remember { mutableStateOf(false) }
    var showChapterDialog by remember { mutableStateOf(false) }

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId, initialChapterIndex, initialParagraphIndex)
    }

    LaunchedEffect(currentParagraphIndex, paragraphs) {
        if (currentParagraphIndex in paragraphs.indices) {
            listState.animateScrollToItem(currentParagraphIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentBook?.title ?: "Reader",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = currentBook?.chapters?.getOrNull(currentChapterIndex)?.title
                                ?: "Chapter ${currentChapterIndex + 1}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.semantics { contentDescription = "Settings" }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    }
                    IconButton(
                        onClick = { showDebugDialog = true },
                        modifier = Modifier.semantics { contentDescription = "Share debug log" }
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null)
                    }
                    IconButton(
                        onClick = onNavigateToSearch,
                        modifier = Modifier.semantics { contentDescription = "Search" }
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                    IconButton(
                        onClick = { showChapterDialog = true },
                        modifier = Modifier.semantics { contentDescription = "Table of contents" }
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null)
                    }
                    IconButton(
                        onClick = { viewModel.addBookmark() },
                        modifier = Modifier.semantics { contentDescription = "Add bookmark" }
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                    }
                    IconButton(
                        onClick = onNavigateToBookmarks,
                        modifier = Modifier.semantics { contentDescription = "Bookmarks" }
                    ) {
                        Icon(Icons.Default.Bookmarks, contentDescription = null)
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.prevChapter() },
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { contentDescription = "Previous chapter" }
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = null, modifier = Modifier.size(28.dp))
                        }
                        IconButton(
                            onClick = { viewModel.prevParagraph() },
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { contentDescription = "Previous paragraph" }
                        ) {
                            Icon(Icons.Default.NavigateBefore, contentDescription = null, modifier = Modifier.size(28.dp))
                        }
                        FilledIconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier
                                .size(64.dp)
                                .semantics {
                                    contentDescription = if (isPlaying) "Pause" else "Play"
                                }
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.nextParagraph() },
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { contentDescription = "Next paragraph" }
                        ) {
                            Icon(Icons.Default.NavigateNext, contentDescription = null, modifier = Modifier.size(28.dp))
                        }
                        IconButton(
                            onClick = { viewModel.nextChapter() },
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { contentDescription = "Next chapter" }
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(28.dp))
                        }
                    }
                    Text(
                        text = "Chapter ${currentChapterIndex + 1} Sentence ${currentParagraphIndex + 1}/$paragraphCount",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    ) { padding ->
        if (paragraphs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No content available", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(paragraphs) { index, paragraph ->
                    Surface(
                        color = if (index == currentParagraphIndex)
                            MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = paragraph,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                lineHeight = 28.sp
                            )
                        )
                    }
                }
            }
        }
    }

    if (showChapterDialog) {
        AlertDialog(
            onDismissRequest = { showChapterDialog = false },
            title = { Text("Chapters") },
            text = {
                if (chapters.isEmpty()) {
                    Text("No chapters available")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        itemsIndexed(chapters) { index, chapter ->
                            Surface(
                                color = if (index == currentChapterIndex)
                                    MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                onClick = {
                                    viewModel.jumpTo(index, 0)
                                    showChapterDialog = false
                                }
                            ) {
                                Text(
                                    text = chapter.title.ifBlank { "Chapter ${index + 1}" },
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChapterDialog = false }) {
                    Text("Close")
                }
            }
        )
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
}
