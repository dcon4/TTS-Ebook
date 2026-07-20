package com.dcon4.ttsebook.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import com.dcon4.ttsebook.MainActivity
import com.dcon4.ttsebook.R
import com.dcon4.ttsebook.data.EbookChapter
import com.dcon4.ttsebook.data.PositionEntity
import com.dcon4.ttsebook.data.PositionDao
import com.dcon4.ttsebook.data.BookmarkDao
import com.dcon4.ttsebook.data.BookmarkEntity
import com.dcon4.ttsebook.debug.DebugLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TtsPlaybackService : Service() {

    companion object {
        const val ACTION_PLAY = "com.dcon4.ttsebook.action.PLAY"
        const val ACTION_PAUSE = "com.dcon4.ttsebook.action.PAUSE"
        const val ACTION_NEXT = "com.dcon4.ttsebook.action.NEXT"
        const val ACTION_PREVIOUS = "com.dcon4.ttsebook.action.PREVIOUS"
        const val ACTION_NEXT_CHAPTER = "com.dcon4.ttsebook.action.NEXT_CHAPTER"
        const val ACTION_PREV_CHAPTER = "com.dcon4.ttsebook.action.PREV_CHAPTER"
        const val ACTION_JUMP_TO = "com.dcon4.ttsebook.action.JUMP_TO"
        const val ACTION_BOOKMARK = "com.dcon4.ttsebook.action.BOOKMARK"
        const val ACTION_STOP = "com.dcon4.ttsebook.action.STOP"
        const val ACTION_POSITION_CHANGED = "com.dcon4.ttsebook.action.POSITION_CHANGED"
        const val EXTRA_CHAPTER_INDEX = "chapterIndex"
        const val EXTRA_PARAGRAPH_INDEX = "paragraphIndex"
        const val EXTRA_PARAGRAPH_COUNT = "paragraphCount"
        const val EXTRA_IS_PLAYING = "isPlaying"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "TtsPlaybackService"

        fun playIntent(context: Context): Intent = Intent(context, TtsPlaybackService::class.java).setAction(ACTION_PLAY)
        fun pauseIntent(context: Context): Intent = Intent(context, TtsPlaybackService::class.java).setAction(ACTION_PAUSE)
        fun nextIntent(context: Context): Intent = Intent(context, TtsPlaybackService::class.java).setAction(ACTION_NEXT)
        fun prevIntent(context: Context): Intent = Intent(context, TtsPlaybackService::class.java).setAction(ACTION_PREVIOUS)
        fun nextChapterIntent(context: Context): Intent = Intent(context, TtsPlaybackService::class.java).setAction(ACTION_NEXT_CHAPTER)
        fun prevChapterIntent(context: Context): Intent = Intent(context, TtsPlaybackService::class.java).setAction(ACTION_PREV_CHAPTER)
        fun jumpToIntent(context: Context, chapterIndex: Int, paragraphIndex: Int): Intent =
            Intent(context, TtsPlaybackService::class.java).setAction(ACTION_JUMP_TO)
                .putExtra(EXTRA_CHAPTER_INDEX, chapterIndex)
                .putExtra(EXTRA_PARAGRAPH_INDEX, paragraphIndex)
        fun bookmarkIntent(context: Context): Intent = Intent(context, TtsPlaybackService::class.java).setAction(ACTION_BOOKMARK)
        fun stopIntent(context: Context): Intent = Intent(context, TtsPlaybackService::class.java).setAction(ACTION_STOP)
    }

    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var bookRepository: com.dcon4.ttsebook.data.BookRepository
    @Inject lateinit var positionDao: PositionDao
    @Inject lateinit var bookmarkDao: BookmarkDao

    private lateinit var notificationManager: NotificationManager
    private lateinit var mediaSession: MediaSessionCompat
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    var isPlaying = false
        private set
    var chapters: List<EbookChapter> = emptyList()
        private set
    var currentChapterIndex = 0
        private set
    var currentParagraphIndex = 0
        private set
    var paragraphs: List<String> = emptyList()
        private set
    var bookId: String = ""
        private set
    var bookTitle: String = ""
        private set

    private var hasPendingChunks = false
    private var ttsInitPending = false

    override fun onCreate() {
        super.onCreate()
        DebugLogger.log(TAG, "Service onCreate")
        notificationManager = getSystemService(NotificationManager::class.java)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, TAG).apply {
            setCallback(MediaSessionCallback())
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            isActive = true
        }
        ttsManager.onUtteranceDone = { utteranceId ->
            handleUtteranceDone(utteranceId)
        }
    }

    private var loadingNotificationBuilt = false

    private fun buildLoadingNotification(): Notification {
        return NotificationCompat.Builder(this, "ttsebook_playback")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("TTS Ebook")
            .setContentText("Loading...")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                if (!loadingNotificationBuilt) {
                    try {
                        startForeground(NOTIFICATION_ID, buildLoadingNotification())
                    } catch (e: Exception) {
                        DebugLogger.log(TAG, "Loading notification failed: ${e.message}")
                    }
                    loadingNotificationBuilt = true
                }
                val bookIdExtra = intent.getStringExtra("bookId")
                if (bookIdExtra != null && bookIdExtra != bookId) {
                    GlobalScope.launch(CoroutineExceptionHandler { _, e ->
                        DebugLogger.logException(TAG, "Play coroutine failed", e)
                    }) {
                        try {
                            val entity = bookRepository.getBook(bookIdExtra) ?: return@launch
                            val bookEbook = withContext(Dispatchers.IO) {
                                bookRepository.loadBook(entity.filePath)
                            } ?: return@launch
                            chapters = bookEbook.chapters
                            this@TtsPlaybackService.bookId = bookEbook.id
                            this@TtsPlaybackService.bookTitle = bookEbook.title
                            this@TtsPlaybackService.currentChapterIndex = intent.getIntExtra("startChapter", 0)
                                .coerceIn(0, chapters.lastIndex.coerceAtLeast(0))
                            loadChapterParagraphs()
                            this@TtsPlaybackService.currentParagraphIndex = intent.getIntExtra("startParagraph", 0)
                                .coerceIn(0, paragraphs.lastIndex.coerceAtLeast(0))
                            resume()
                        } catch (e: Throwable) {
                            DebugLogger.logException(TAG, "Play action failed", e)
                        }
                    }
                } else {
                    resume()
                }
            }
            ACTION_PAUSE -> pause()
            ACTION_NEXT -> nextParagraph()
            ACTION_PREVIOUS -> prevParagraph()
            ACTION_NEXT_CHAPTER -> nextChapter()
            ACTION_PREV_CHAPTER -> prevChapter()
            ACTION_JUMP_TO -> {
                val ci = intent?.getIntExtra(EXTRA_CHAPTER_INDEX, 0) ?: 0
                val pi = intent?.getIntExtra(EXTRA_PARAGRAPH_INDEX, 0) ?: 0
                jumpTo(ci, pi)
            }
            ACTION_BOOKMARK -> addBookmark()
            ACTION_STOP -> stopSelf()
            else -> {
                if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
                    mediaSession.controller.dispatchMediaButtonEvent(intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT))
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        DebugLogger.log(TAG, "Service onDestroy")
        ttsManager.stop()
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }

    fun setBook(bookId: String, title: String, chapters: List<EbookChapter>, startChapter: Int, startParagraph: Int) {
        this.bookId = bookId
        this.bookTitle = title
        this.chapters = chapters
        this.currentChapterIndex = startChapter.coerceIn(0, chapters.lastIndex)
        loadChapterParagraphs()
        currentParagraphIndex = startParagraph.coerceIn(0, paragraphs.lastIndex)
        broadcastPosition()
        DebugLogger.log(TAG, "Set book: $title chapter=$currentChapterIndex para=$currentParagraphIndex")
    }

    fun play() {
        if (!ttsManager.ttsReady) {
            ttsManager.initTts { success ->
                if (success) speakCurrent()
            }
        } else {
            speakCurrent()
        }
    }

    fun resume() {
        if (paragraphs.isEmpty() || currentParagraphIndex >= paragraphs.size) return
        requestAudioFocus()
        isPlaying = true
        if (!ttsManager.ttsReady && !ttsInitPending) {
            ttsInitPending = true
            ttsManager.initTts { success ->
                ttsInitPending = false
                if (success) speakCurrent() else isPlaying = false
            }
        } else if (ttsManager.ttsReady) {
            speakCurrent()
        }
        broadcastPosition()
        updateNotification()
        updateMediaSession()
    }

    fun pause() {
        isPlaying = false
        ttsManager.stop()
        abandonAudioFocus()
        broadcastPosition()
        updateNotification()
        updateMediaSession()
        savePosition()
    }

    fun togglePlayPause() {
        if (isPlaying) pause() else resume()
    }

    fun nextParagraph() {
        if (currentParagraphIndex < paragraphs.lastIndex) {
            currentParagraphIndex++
            if (isPlaying) speakCurrent()
            broadcastPosition()
            updateNotification()
            updateMediaSession()
            savePosition()
        } else if (currentChapterIndex < chapters.lastIndex) {
            currentChapterIndex++
            loadChapterParagraphs()
            currentParagraphIndex = 0
            if (isPlaying) speakCurrent()
            broadcastPosition()
            updateNotification()
            updateMediaSession()
            savePosition()
        }
    }

    fun prevParagraph() {
        if (currentParagraphIndex > 0) {
            currentParagraphIndex--
            if (isPlaying) speakCurrent()
            broadcastPosition()
            updateNotification()
            updateMediaSession()
            savePosition()
        } else if (currentChapterIndex > 0) {
            currentChapterIndex--
            loadChapterParagraphs()
            currentParagraphIndex = paragraphs.lastIndex
            if (isPlaying) speakCurrent()
            broadcastPosition()
            updateNotification()
            updateMediaSession()
            savePosition()
        }
    }

    fun nextChapter() {
        if (currentChapterIndex < chapters.lastIndex) {
            currentChapterIndex++
            loadChapterParagraphs()
            currentParagraphIndex = 0
            if (isPlaying) speakCurrent()
            broadcastPosition()
            updateNotification()
            updateMediaSession()
            savePosition()
        }
    }

    fun prevChapter() {
        if (currentChapterIndex > 0) {
            currentChapterIndex--
            loadChapterParagraphs()
            currentParagraphIndex = 0
            if (isPlaying) speakCurrent()
            broadcastPosition()
            updateNotification()
            updateMediaSession()
            savePosition()
        }
    }

    fun jumpTo(chapterIndex: Int, paragraphIndex: Int) {
        currentChapterIndex = chapterIndex.coerceIn(0, chapters.lastIndex)
        loadChapterParagraphs()
        currentParagraphIndex = paragraphIndex.coerceIn(0, paragraphs.lastIndex)
        if (isPlaying) speakCurrent()
        broadcastPosition()
        updateNotification()
        updateMediaSession()
        savePosition()
    }

    private fun speakCurrent() {
        if (paragraphs.isEmpty() || currentParagraphIndex >= paragraphs.size) {
            pause()
            return
        }
        val text = paragraphs[currentParagraphIndex]
        if (text.isBlank()) {
            nextParagraph()
            return
        }
        ttsManager.speakChunked(text, "book_${bookId}_ch${currentChapterIndex}_p${currentParagraphIndex}")
        hasPendingChunks = true
        updateMediaSession()
    }

    private fun loadChapterParagraphs() {
        paragraphs = if (chapters.isNotEmpty() && currentChapterIndex in chapters.indices) {
            val chapter = chapters[currentChapterIndex]
            ttsManager.splitParagraphs(chapter.content)
        } else {
            emptyList()
        }
        DebugLogger.log(TAG, "Loaded chapter $currentChapterIndex: ${paragraphs.size} paragraphs")
    }

    private fun handleUtteranceDone(utteranceId: String) {
        val pattern = Regex("book_.+_ch(\\d+)_p(\\d+)_chunk_(\\d+)")
        val match = pattern.find(utteranceId)
        if (match != null) {
            val chapIndex = match.groupValues[1].toIntOrNull() ?: -1
            val paraIndex = match.groupValues[2].toIntOrNull() ?: -1
            val chunkIndex = match.groupValues[3].toIntOrNull() ?: 0
            if (chapIndex != currentChapterIndex || paraIndex != currentParagraphIndex) {
                return
            }
            val chunkCount = ttsManager.chunkText(paragraphs.getOrElse(currentParagraphIndex) { "" }).size
            if (chunkIndex >= chunkCount - 1) {
                autoAdvance()
            }
        }
    }

    private fun autoAdvance() {
        if (currentParagraphIndex < paragraphs.lastIndex) {
            currentParagraphIndex++
            if (isPlaying) speakCurrent()
            savePosition()
        } else if (currentChapterIndex < chapters.lastIndex) {
            currentChapterIndex++
            loadChapterParagraphs()
            currentParagraphIndex = 0
            if (isPlaying) speakCurrent()
            savePosition()
        } else {
            pause()
        }
        broadcastPosition()
        updateNotification()
        updateMediaSession()
    }

    private fun broadcastPosition() {
        sendBroadcast(Intent(ACTION_POSITION_CHANGED).apply {
            setPackage(packageName)
            putExtra(EXTRA_CHAPTER_INDEX, currentChapterIndex)
            putExtra(EXTRA_PARAGRAPH_INDEX, currentParagraphIndex)
            putExtra(EXTRA_PARAGRAPH_COUNT, paragraphs.size)
            putExtra(EXTRA_IS_PLAYING, isPlaying)
        })
    }

    private fun savePosition() {
        if (bookId.isBlank()) return
        val chapterTitle = chapters.getOrNull(currentChapterIndex)?.title ?: ""
        GlobalScope.launch {
            positionDao.upsertPosition(
                PositionEntity(
                    bookId = bookId,
                    chapterIndex = currentChapterIndex,
                    paragraphIndex = currentParagraphIndex,
                    chapterTitle = chapterTitle
                )
            )
        }
    }

    private fun addBookmark() {
        if (bookId.isBlank() || chapters.isEmpty()) return
        val chapterTitle = chapters.getOrNull(currentChapterIndex)?.title ?: "Chapter ${currentChapterIndex + 1}"
        val label = "$bookTitle - $chapterTitle (paragraph ${currentParagraphIndex + 1})"
        GlobalScope.launch {
            bookmarkDao.addBookmark(
                BookmarkEntity(
                    bookId = bookId,
                    chapterIndex = currentChapterIndex,
                    paragraphIndex = currentParagraphIndex,
                    label = label
                )
            )
        }
        DebugLogger.log(TAG, "Bookmark added: $label")
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .build()
            audioFocusRequest = focusRequest
            audioManager?.requestAudioFocus(focusRequest)
        } else {
            audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "ttsebook_playback",
            "Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "TTS Ebook playback controls"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY

        val prevChapterIntent = PendingIntent.getService(
            this, 0, prevChapterIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val prevParaIntent = PendingIntent.getService(
            this, 1, prevIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPausePending = PendingIntent.getService(
            this, 2, Intent(this, TtsPlaybackService::class.java).setAction(playPauseAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextParaIntent = PendingIntent.getService(
            this, 3, nextIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextChapterPending = PendingIntent.getService(
            this, 4, nextChapterIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val bookmarkPending = PendingIntent.getService(
            this, 5, bookmarkIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val chapterTitle = chapters.getOrNull(currentChapterIndex)?.title ?: ""
        val contentText = if (isPlaying) {
            "Chapter $chapterTitle - Paragraph ${currentParagraphIndex + 1}/${paragraphs.size}"
        } else {
            "Paused"
        }

        val notification = NotificationCompat.Builder(this, "ttsebook_playback")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(bookTitle.ifBlank { "TTS Ebook" })
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2, 3, 4)
            )
            .addAction(android.R.drawable.ic_media_previous, "Prev Chap", prevChapterIntent)
            .addAction(android.R.drawable.ic_media_previous, "Prev", prevParaIntent)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPausePending)
            .addAction(android.R.drawable.ic_media_next, "Next", nextParaIntent)
            .addAction(android.R.drawable.ic_media_next, "Next Chap", nextChapterPending)
            .addAction(android.R.drawable.ic_menu_add, "Bookmark", bookmarkPending)
            .build()

        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            DebugLogger.log(TAG, "startForeground failed: ${e.message}")
        }
    }

    private fun updateMediaSession() {
        val chapterTitle = chapters.getOrNull(currentChapterIndex)?.title ?: ""
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, chapterTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, bookTitle)
                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, (currentParagraphIndex + 1).toLong())
                .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, paragraphs.size.toLong())
                .build()
        )
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, 0L, 1.0f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_STOP
                )
                .build()
        )
    }

    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() { resume() }
        override fun onPause() { pause() }
        override fun onSkipToNext() { nextParagraph() }
        override fun onSkipToPrevious() { prevParagraph() }
        override fun onStop() { pause() }
    }
}
