package com.dcon4.ttsebook.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dcon4.ttsebook.data.BookEntity
import com.dcon4.ttsebook.data.BookRepository
import com.dcon4.ttsebook.data.BookmarkEntity
import com.dcon4.ttsebook.data.EbookBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.dcon4.ttsebook.data.EbookChapter
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

    private val _paragraphs = MutableStateFlow<List<String>>(emptyList())
    val paragraphs: StateFlow<List<String>> = _paragraphs.asStateFlow()

    private val _paragraphCount = MutableStateFlow(0)
    val paragraphCount: StateFlow<Int> = _paragraphCount.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkEntity>> = _bookmarks.asStateFlow()

    private val _chapters = MutableStateFlow<List<EbookChapter>>(emptyList())
    val chapters: StateFlow<List<EbookChapter>> = _chapters.asStateFlow()

    var bookEntity: BookEntity? = null
        private set

    private fun computeParagraphs(book: EbookBook?, chapterIndex: Int): List<String> {
        val chapter = book?.chapters?.getOrNull(chapterIndex) ?: return emptyList()
        return chapter.content.split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    fun updateParagraphs() {
        val book = _currentBook.value
        val ci = _currentChapterIndex.value
        viewModelScope.launch(Dispatchers.Default) {
            val list = computeParagraphs(book, ci)
            _paragraphs.value = list
            _paragraphCount.value = list.size
        }
    }

    private val positionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TtsPlaybackService.ACTION_POSITION_CHANGED) {
                _currentChapterIndex.value = intent.getIntExtra(TtsPlaybackService.EXTRA_CHAPTER_INDEX, 0)
                _currentParagraphIndex.value = intent.getIntExtra(TtsPlaybackService.EXTRA_PARAGRAPH_INDEX, 0)
                _isPlaying.value = intent.getBooleanExtra(TtsPlaybackService.EXTRA_IS_PLAYING, false)
                updateParagraphs()
            }
        }
    }

    private var receiverRegistered = false

    private fun registerPositionReceiver() {
        if (!receiverRegistered) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getApplication<Application>().registerReceiver(
                    positionReceiver,
                    IntentFilter(TtsPlaybackService.ACTION_POSITION_CHANGED),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                getApplication<Application>().registerReceiver(
                    positionReceiver,
                    IntentFilter(TtsPlaybackService.ACTION_POSITION_CHANGED)
                )
            }
            receiverRegistered = true
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (receiverRegistered) {
            try {
                getApplication<Application>().unregisterReceiver(positionReceiver)
            } catch (_: Exception) { }
            receiverRegistered = false
        }
    }

    fun loadBook(bookId: String, initialChapterIndex: Int = -1, initialParagraphIndex: Int = -1) {
        viewModelScope.launch {
            DebugLogger.log(TAG, "loadBook: start bookId=$bookId")
            val t0 = System.currentTimeMillis()
            try {
                val entity = bookRepository.getBook(bookId) ?: run {
                    DebugLogger.log(TAG, "loadBook: getBook returned null"); return@launch
                }
                bookEntity = entity
                val t1 = System.currentTimeMillis()
                DebugLogger.log(TAG, "loadBook: getBook ${t1-t0}ms")
                registerPositionReceiver()
                val t2 = System.currentTimeMillis()
                DebugLogger.log(TAG, "loadBook: registerReceiver ${t2-t1}ms")
                bookEntity = entity
                val t3 = System.currentTimeMillis()
                DebugLogger.log(TAG, "loadBook: getBook ${t3-t2}ms")
                val ebook = withContext(Dispatchers.IO) {
                    bookRepository.loadBook(entity.filePath)
                } ?: run {
                    DebugLogger.log(TAG, "loadBook: loadBook returned null"); return@launch
                }
                val t4 = System.currentTimeMillis()
                DebugLogger.log(TAG, "loadBook: loadBook ${t4-t3}ms chapters=${ebook.chapters.size}")
                _currentBook.value = ebook
                _chapters.value = ebook.chapters
                if (initialChapterIndex >= 0 && initialParagraphIndex >= 0) {
                    _currentChapterIndex.value = initialChapterIndex
                    _currentParagraphIndex.value = initialParagraphIndex
                } else {
                    val pos = bookRepository.getPosition(bookId)
                    _currentChapterIndex.value = pos?.chapterIndex ?: 0
                    _currentParagraphIndex.value = pos?.paragraphIndex ?: 0
                }
                val list = withContext(Dispatchers.Default) {
                    computeParagraphs(ebook, _currentChapterIndex.value)
                }
                val t5 = System.currentTimeMillis()
                DebugLogger.log(TAG, "loadBook: computeParagraphs ${t5-t4}ms size=${list.size}")
                _paragraphs.value = list
                _paragraphCount.value = list.size
                val intent = Intent(getApplication(), TtsPlaybackService::class.java).apply {
                    action = TtsPlaybackService.ACTION_PLAY
                    putExtra("bookId", ebook.id)
                    putExtra("bookTitle", ebook.title)
                    putExtra("startChapter", _currentChapterIndex.value)
                    putExtra("startParagraph", _currentParagraphIndex.value)
                }
                val canUseForeground = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                val t6 = System.currentTimeMillis()
                DebugLogger.log(TAG, "loadBook: service start ${t6-t0}ms total")
                if (canUseForeground) {
                    getApplication<Application>().startForegroundService(intent)
                } else {
                    getApplication<Application>().startService(intent)
                }
            } catch (e: Throwable) {
                DebugLogger.logException(TAG, "loadBook failed", e)
            }
        }
    }

    fun play() {
        getApplication<Application>().startForegroundService(
            TtsPlaybackService.playIntent(getApplication())
        )
    }

    fun pause() {
        getApplication<Application>().startService(
            TtsPlaybackService.pauseIntent(getApplication())
        )
    }

    fun togglePlayPause() {
        getApplication<Application>().startService(
            if (_isPlaying.value) TtsPlaybackService.pauseIntent(getApplication())
            else TtsPlaybackService.playIntent(getApplication())
        )
    }

    fun nextParagraph() {
        getApplication<Application>().startService(
            TtsPlaybackService.nextIntent(getApplication())
        )
    }

    fun prevParagraph() {
        getApplication<Application>().startService(
            TtsPlaybackService.prevIntent(getApplication())
        )
    }

    fun nextChapter() {
        getApplication<Application>().startService(
            TtsPlaybackService.nextChapterIntent(getApplication())
        )
    }

    fun prevChapter() {
        getApplication<Application>().startService(
            TtsPlaybackService.prevChapterIntent(getApplication())
        )
    }

    fun jumpTo(chapterIndex: Int, paragraphIndex: Int) {
        getApplication<Application>().startService(
            TtsPlaybackService.jumpToIntent(getApplication(), chapterIndex, paragraphIndex)
        )
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
