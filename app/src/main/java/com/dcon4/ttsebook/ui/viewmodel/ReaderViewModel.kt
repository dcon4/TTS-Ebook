package com.dcon4.ttsebook.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

data class ChapterImageUi(
    val anchorParagraphIndex: Int,
    val bitmap: Bitmap,
    val label: String
)

data class LinkSpanUi(
    val start: Int,
    val end: Int,
    val href: String,
    val targetChapterIndex: Int
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    application: Application,
    private val bookRepository: BookRepository,
    private val pronunciationRepository: com.dcon4.ttsebook.data.PronunciationRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ReaderViewModel"
        private const val PREFS_NAME = "ttsebook_settings"
        private const val KEY_SHOW_PDF_PAGES = "show_pdf_pages"
        private const val KEY_SHOW_EMBEDDED_IMAGES = "show_embedded_images"
        private const val PDF_RENDER_SCALE = 1.5f
        private const val PDF_PAGE_CACHE_SIZE = 3
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

    private val _paragraphLinks = MutableStateFlow<List<List<LinkSpanUi>>>(emptyList())
    val paragraphLinks: StateFlow<List<List<LinkSpanUi>>> = _paragraphLinks.asStateFlow()

    private val _paragraphCount = MutableStateFlow(0)
    val paragraphCount: StateFlow<Int> = _paragraphCount.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkEntity>> = _bookmarks.asStateFlow()

    private val _chapters = MutableStateFlow<List<EbookChapter>>(emptyList())
    val chapters: StateFlow<List<EbookChapter>> = _chapters.asStateFlow()

    private val _bookFormat = MutableStateFlow<String?>(null)
    val bookFormat: StateFlow<String?> = _bookFormat.asStateFlow()

    private val _pdfPageNumber = MutableStateFlow(0)
    val pdfPageNumber: StateFlow<Int> = _pdfPageNumber.asStateFlow()

    private val _pdfPageBitmap = MutableStateFlow<Bitmap?>(null)
    val pdfPageBitmap: StateFlow<Bitmap?> = _pdfPageBitmap.asStateFlow()

    private val _chapterImages = MutableStateFlow<List<ChapterImageUi>>(emptyList())
    val chapterImages: StateFlow<List<ChapterImageUi>> = _chapterImages.asStateFlow()

    private var showPdfPages = true
    private var showEmbeddedImages = true
    private var lastHandledChapter = -1
    private val pdfPageCache = LinkedHashMap<Int, Bitmap>()

    var bookEntity: BookEntity? = null
        private set

    private fun sentenceStartOffsets(text: String): List<Int> {
        val starts = mutableListOf(0)
        for (m in Regex("(?<=[.!?])\\s+").findAll(text)) {
            starts.add(m.range.last + 1)
        }
        return starts
    }

    private fun computeChapterTextAndLinks(book: EbookBook?, chapterIndex: Int): Pair<List<String>, List<List<LinkSpanUi>>> {
        val chapter = book?.chapters?.getOrNull(chapterIndex) ?: return emptyList() to emptyList()
        val text = pronunciationRepository.applyTo(chapter.content)
        val starts = sentenceStartOffsets(text)
        val sentences = mutableListOf<String>()
        val ranges = mutableListOf<Pair<Int, Int>>()
        starts.forEachIndexed { i, s ->
            val e = if (i + 1 < starts.size) starts[i + 1] else text.length
            val sentence = text.substring(s, e).trim()
            if (sentence.isNotBlank()) {
                sentences.add(sentence)
                ranges.add(s to e)
            }
        }
        val perSentence = MutableList(sentences.size) { mutableListOf<LinkSpanUi>() }
        if (chapter.links.isNotEmpty() && ranges.isNotEmpty()) {
            var cursor = 0
            for (link in chapter.links) {
                if (link.label.isEmpty() || link.start >= link.end) continue
                val idx = text.indexOf(link.label, cursor)
                if (idx < 0) continue
                cursor = idx + link.label.length
                val sentenceIndex = ranges.indexOfFirst { idx >= it.first && idx < it.second }
                if (sentenceIndex < 0) continue
                val sentenceStart = ranges[sentenceIndex].first
                val sentenceLen = sentences[sentenceIndex].length
                val relStart = (idx - sentenceStart).coerceIn(0, sentenceLen)
                val relEnd = (relStart + link.label.length).coerceAtMost(sentenceLen)
                if (relEnd > relStart) {
                    perSentence[sentenceIndex].add(
                        LinkSpanUi(relStart, relEnd, link.href, link.chapterIndex ?: -1)
                    )
                }
            }
        }
        return sentences to perSentence
    }

    fun updateParagraphs() {
        val book = _currentBook.value
        val ci = _currentChapterIndex.value
        viewModelScope.launch(Dispatchers.Default) {
            val (list, links) = computeChapterTextAndLinks(book, ci)
            _paragraphs.value = list
            _paragraphCount.value = list.size
            _paragraphLinks.value = links
        }
        handleChapterDisplay(ci)
    }

    private fun handleChapterDisplay(chapterIndex: Int) {
        if (chapterIndex == lastHandledChapter) return
        lastHandledChapter = chapterIndex
        val book = _currentBook.value ?: return
        val chapter = book.chapters.getOrNull(chapterIndex) ?: return
        if (_bookFormat.value == "pdf") {
            _chapterImages.value = emptyList()
            val pageNumber = chapter.pageNumber ?: return
            _pdfPageNumber.value = pageNumber
            val cached = pdfPageCache[pageNumber]
            if (cached != null) {
                _pdfPageBitmap.value = cached
                return
            }
            _pdfPageBitmap.value = null
            val filePath = bookEntity?.filePath ?: return
            viewModelScope.launch {
                val bmp = if (showPdfPages) {
                    bookRepository.renderPdfPage(filePath, pageNumber, PDF_RENDER_SCALE)
                } else {
                    null
                }
                if (bmp != null && _currentChapterIndex.value == chapterIndex) {
                    pdfPageCache[pageNumber] = bmp
                    if (pdfPageCache.size > PDF_PAGE_CACHE_SIZE) {
                        val oldest = pdfPageCache.keys.firstOrNull()
                        if (oldest != null) pdfPageCache.remove(oldest)
                    }
                    _pdfPageBitmap.value = bmp
                }
            }
        } else {
            _pdfPageBitmap.value = null
            _pdfPageNumber.value = 0
            if (!showEmbeddedImages || chapter.images.isEmpty()) {
                _chapterImages.value = emptyList()
                return
            }
            _chapterImages.value = emptyList()
            viewModelScope.launch {
                val filePath = bookEntity?.filePath ?: return@launch
                val decoded = chapter.images.mapNotNull { img ->
                    try {
                        val bytes = bookRepository.loadEpubImageBytes(filePath, img.href)
                        if (bytes == null) {
                            null
                        } else {
                            val bmp = withContext(Dispatchers.Default) {
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }
                            if (bmp != null) {
                                ChapterImageUi(img.anchorParagraphIndex, bmp, img.label)
                            } else {
                                null
                            }
                        }
                    } catch (_: Throwable) {
                        null
                    }
                }
                if (_currentChapterIndex.value == chapterIndex) {
                    _chapterImages.value = decoded
                }
            }
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
            val prefs = getApplication<Application>()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            showPdfPages = prefs.getBoolean(KEY_SHOW_PDF_PAGES, true)
            showEmbeddedImages = prefs.getBoolean(KEY_SHOW_EMBEDDED_IMAGES, true)
            lastHandledChapter = -1
            _pdfPageBitmap.value = null
            _pdfPageNumber.value = 0
            _chapterImages.value = emptyList()
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
                _bookFormat.value = entity.format
                if (initialChapterIndex >= 0 && initialParagraphIndex >= 0) {
                    _currentChapterIndex.value = initialChapterIndex
                    _currentParagraphIndex.value = initialParagraphIndex
                } else {
                    val pos = bookRepository.getPosition(bookId)
                    _currentChapterIndex.value = pos?.chapterIndex ?: 0
                    _currentParagraphIndex.value = pos?.paragraphIndex ?: 0
                }
                val list = withContext(Dispatchers.Default) {
                    computeChapterTextAndLinks(ebook, _currentChapterIndex.value)
                }
                val t5 = System.currentTimeMillis()
                DebugLogger.log(TAG, "loadBook: computeParagraphs ${t5-t4}ms size=${list.first.size}")
                _paragraphs.value = list.first
                _paragraphLinks.value = list.second
                _paragraphCount.value = list.first.size
                handleChapterDisplay(_currentChapterIndex.value)
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
                if (initialChapterIndex >= 0 && initialParagraphIndex >= 0) {
                    DebugLogger.log(TAG, "loadBook: jump to ch=$initialChapterIndex p=$initialParagraphIndex")
                    getApplication<Application>().startService(
                        TtsPlaybackService.jumpToIntent(getApplication(), initialChapterIndex, initialParagraphIndex)
                    )
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

    fun tapToSpeak(paragraphIndex: Int) {
        val total = _paragraphs.value.size
        if (paragraphIndex < 0 || paragraphIndex >= total) return
        val ci = _currentChapterIndex.value
        DebugLogger.log(TAG, "Tap to speak: ch=$ci p=$paragraphIndex")
        _currentParagraphIndex.value = paragraphIndex
        val app = getApplication<Application>()
        app.startService(TtsPlaybackService.playIntent(app))
        app.startService(TtsPlaybackService.jumpToIntent(app, ci, paragraphIndex))
    }

    fun openLink(href: String, targetChapterIndex: Int) {
        val app = getApplication<Application>()
        if (targetChapterIndex >= 0) {
            if (targetChapterIndex == _currentChapterIndex.value) {
                DebugLogger.log(TAG, "Same-chapter link tapped (anchor, not yet supported): $href")
            } else {
                DebugLogger.log(TAG, "Internal link tapped: ch=$targetChapterIndex href=$href")
                jumpTo(targetChapterIndex, 0)
            }
        } else {
            DebugLogger.log(TAG, "External link tapped: $href")
            val scheme = href.substringBefore(':').lowercase()
            try {
                if (scheme in setOf("http", "https", "mailto", "tel")) {
                    val action = if (scheme == "mailto") Intent.ACTION_SENDTO else Intent.ACTION_VIEW
                    val intent = Intent(action, android.net.Uri.parse(href))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    app.startActivity(intent)
                } else {
                    DebugLogger.log(TAG, "Unsupported link scheme: $scheme")
                }
            } catch (e: Exception) {
                DebugLogger.logException(TAG, "Failed to open link", e)
            }
        }
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
        return chapter.content.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}
