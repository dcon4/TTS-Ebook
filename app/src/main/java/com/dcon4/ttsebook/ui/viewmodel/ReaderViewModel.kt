package com.dcon4.ttsebook.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dcon4.ttsebook.data.BookEntity
import com.dcon4.ttsebook.data.BookRepository
import com.dcon4.ttsebook.data.BookmarkEntity
import com.dcon4.ttsebook.data.EbookBook
import com.dcon4.ttsebook.debug.DebugLogger
import com.dcon4.ttsebook.playback.TtsPlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    application: Application,
    private val bookRepository: BookRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ReaderViewModel"
    }

    private val _currentBook = MutableStateFlow<EbookBook?>(null)
    val currentBook: StateFlow<EbookBook?> = _currentBook.asStateFlow()

    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

    private val _currentParagraphIndex = MutableStateFlow(0)
    val currentParagraphIndex: StateFlow<Int> = _currentParagraphIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkEntity>> = _bookmarks.asStateFlow()

    var bookEntity: BookEntity? = null
        private set

    fun loadBook(bookId: String, initialChapterIndex: Int = -1, initialParagraphIndex: Int = -1) {
        viewModelScope.launch {
            val entity = bookRepository.getBook(bookId) ?: return@launch
            bookEntity = entity
            val ebook = bookRepository.loadBook(entity.filePath) ?: return@launch
            _currentBook.value = ebook
            if (initialChapterIndex >= 0 && initialParagraphIndex >= 0) {
                _currentChapterIndex.value = initialChapterIndex
                _currentParagraphIndex.value = initialParagraphIndex
            } else {
                val pos = bookRepository.getPosition(bookId)
                _currentChapterIndex.value = pos?.chapterIndex ?: 0
                _currentParagraphIndex.value = pos?.paragraphIndex ?: 0
            }
            val intent = Intent(getApplication(), TtsPlaybackService::class.java).apply {
                action = TtsPlaybackService.ACTION_PLAY
                putExtra("bookId", ebook.id)
                putExtra("bookTitle", ebook.title)
                putExtra("startChapter", _currentChapterIndex.value)
                putExtra("startParagraph", _currentParagraphIndex.value)
            }
            getApplication<Application>().startForegroundService(intent)
        }
    }

    fun play() {
        getApplication<Application>().startForegroundService(
            TtsPlaybackService.playIntent(getApplication())
        )
        _isPlaying.value = true
    }

    fun pause() {
        getApplication<Application>().startService(
            TtsPlaybackService.pauseIntent(getApplication())
        )
        _isPlaying.value = false
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun nextParagraph() {
        getApplication<Application>().startService(
            TtsPlaybackService.nextIntent(getApplication())
        )
        val ci = _currentChapterIndex.value
        val pi = _currentParagraphIndex.value + 1
        val chapter = _currentBook.value?.chapters?.getOrNull(ci)
        val paraCount = chapter?.content?.split(Regex("\\n\\s*\\n"))
            ?.map { it.trim() }?.filter { it.isNotBlank() }?.size ?: 0
        if (pi < paraCount) {
            _currentParagraphIndex.value = pi
        } else {
            _currentChapterIndex.value = ci + 1
            _currentParagraphIndex.value = 0
        }
    }

    fun prevParagraph() {
        getApplication<Application>().startService(
            TtsPlaybackService.prevIntent(getApplication())
        )
        if (_currentParagraphIndex.value > 0) {
            _currentParagraphIndex.value = _currentParagraphIndex.value - 1
        } else if (_currentChapterIndex.value > 0) {
            _currentChapterIndex.value = _currentChapterIndex.value - 1
            val chapter = _currentBook.value?.chapters?.getOrNull(_currentChapterIndex.value)
            val paraCount = chapter?.content?.split(Regex("\\n\\s*\\n"))
                ?.map { it.trim() }?.filter { it.isNotBlank() }?.size ?: 0
            _currentParagraphIndex.value = (paraCount - 1).coerceAtLeast(0)
        }
    }

    fun nextChapter() {
        getApplication<Application>().startService(
            TtsPlaybackService.nextChapterIntent(getApplication())
        )
        if (_currentChapterIndex.value < (_currentBook.value?.chapters?.size ?: 1) - 1) {
            _currentChapterIndex.value = _currentChapterIndex.value + 1
            _currentParagraphIndex.value = 0
        }
    }

    fun prevChapter() {
        getApplication<Application>().startService(
            TtsPlaybackService.prevChapterIntent(getApplication())
        )
        if (_currentChapterIndex.value > 0) {
            _currentChapterIndex.value = _currentChapterIndex.value - 1
            _currentParagraphIndex.value = 0
        }
    }

    fun jumpTo(chapterIndex: Int, paragraphIndex: Int) {
        _currentChapterIndex.value = chapterIndex
        _currentParagraphIndex.value = paragraphIndex
        val intent = Intent(getApplication(), TtsPlaybackService::class.java).apply {
            action = TtsPlaybackService.ACTION_PLAY
            putExtra("bookId", _currentBook.value?.id ?: "")
            putExtra("bookTitle", _currentBook.value?.title ?: "")
            putExtra("startChapter", chapterIndex)
            putExtra("startParagraph", paragraphIndex)
        }
        getApplication<Application>().startForegroundService(intent)
    }

    fun addBookmark() {
        val book = _currentBook.value ?: return
        val ci = _currentChapterIndex.value
        val pi = _currentParagraphIndex.value
        val chapter = book.chapters.getOrNull(ci)
        val label = "${chapter?.title ?: "Chapter ${ci + 1}"} - Paragraph ${pi + 1}"
        viewModelScope.launch {
            bookRepository.addBookmark(book.id, ci, pi, label)
        }
    }

    fun getCurrentParagraphs(): List<String> {
        val book = _currentBook.value ?: return emptyList()
        val chapter = book.chapters.getOrNull(_currentChapterIndex.value) ?: return emptyList()
        return chapter.content.split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}
