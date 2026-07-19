package com.dcon4.ttsebook.ui.screen

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dcon4.ttsebook.ui.viewmodel.ReaderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    initialChapterIndex: Int = -1,
    initialParagraphIndex: Int = -1,
    onNavigateToSearch: () -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val currentBook by viewModel.currentBook.collectAsState()
    val currentChapterIndex by viewModel.currentChapterIndex.collectAsState()
    val currentParagraphIndex by viewModel.currentParagraphIndex.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId, initialChapterIndex, initialParagraphIndex)
    }

    LaunchedEffect(currentParagraphIndex) {
        if (currentParagraphIndex >= 0) {
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
                        onClick = onNavigateToSearch,
                        modifier = Modifier.semantics { contentDescription = "Search" }
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.prevChapter() },
                            modifier = Modifier.semantics { contentDescription = "Previous chapter" }
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = null)
                        }
                        IconButton(
                            onClick = { viewModel.prevParagraph() },
                            modifier = Modifier.semantics { contentDescription = "Previous paragraph" }
                        ) {
                            Icon(Icons.Default.NavigateBefore, contentDescription = null)
                        }
                        FilledIconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier.size(56.dp).semantics {
                                contentDescription = if (isPlaying) "Pause" else "Play"
                            }
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.nextParagraph() },
                            modifier = Modifier.semantics { contentDescription = "Next paragraph" }
                        ) {
                            Icon(Icons.Default.NavigateNext, contentDescription = null)
                        }
                        IconButton(
                            onClick = { viewModel.nextChapter() },
                            modifier = Modifier.semantics { contentDescription = "Next chapter" }
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = null)
                        }
                    }
                    Text(
                        text = "Chapter ${currentChapterIndex + 1} Paragraph ${currentParagraphIndex + 1}/" +
                                (currentBook?.chapters?.getOrNull(currentChapterIndex)?.let {
                                    it.content.split(Regex("\\n\\s*\\n")).size
                                } ?: "0"),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    ) { padding ->
        val paragraphs = currentBook?.chapters?.getOrNull(currentChapterIndex)?.let {
            it.content.split(Regex("\\n\\s*\\n"))
                .map { p -> p.trim() }
                .filter { p -> p.isNotBlank() }
        } ?: emptyList()

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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(paragraphs) { index, paragraph ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (index == currentParagraphIndex) Modifier.semantics {
                                    contentDescription = "Current paragraph, double tap to play"
                                } else Modifier
                            ),
                        colors = if (index == currentParagraphIndex) {
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        } else {
                            CardDefaults.cardColors()
                        }
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
}
