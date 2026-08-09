package com.dcon4.ttsebook.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.dcon4.ttsebook.debug.DebugLogger
import com.dcon4.ttsebook.parser.EpubParser
import com.dcon4.ttsebook.parser.HtmlParser
import com.dcon4.ttsebook.parser.PdfParser
import com.dcon4.ttsebook.parser.TxtParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val positionDao: PositionDao,
    private val bookmarkDao: BookmarkDao
) {
    private val parsers = listOf(
        EpubParser(), PdfParser(), TxtParser(), HtmlParser()
    )

    companion object {
        private const val TAG = "BookRepository"
    }

    fun getAllBooks(): Flow<List<BookEntity>> = bookDao.getAllBooks()
    fun getFavoriteBooks(): Flow<List<BookEntity>> = bookDao.getFavoriteBooks()
    fun getRecentBooks(limit: Int = 20): Flow<List<BookEntity>> = bookDao.getRecentBooks(limit)

    suspend fun getBook(bookId: String): BookEntity? = bookDao.getBook(bookId)
    suspend fun getPosition(bookId: String): PositionEntity? = positionDao.getPosition(bookId)
    suspend fun getBookmarks(bookId: String): List<BookmarkEntity> {
        var list = emptyList<BookmarkEntity>()
        bookmarkDao.getBookmarks(bookId).collect { list = it }
        return list
    }

    fun getBookmarksFlow(bookId: String): Flow<List<BookmarkEntity>> = bookmarkDao.getBookmarks(bookId)

    suspend fun importBook(uri: Uri): Result<EbookBook> {
        return withContext(Dispatchers.IO) {
            try {
                val path = uri.path ?: uri.toString()
                DebugLogger.log(TAG, "Import start: $path")
                var format = detectFormat(path)
                if (!parsers.any { it.supportsFormat(format) }) {
                    format = detectFormatFromMime(context.contentResolver.getType(uri)) ?: format
                }
                val parser = parsers.find { it.supportsFormat(format) }
                    ?: return@withContext Result.failure(Exception("Unsupported format: $format"))
                val booksDir = File(context.filesDir, "books")
                booksDir.mkdirs()
                val tmpFile = File(booksDir, "import-${System.currentTimeMillis()}.tmp")
                try {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: return@withContext Result.failure(Exception("Cannot open file"))
                    val t0 = System.currentTimeMillis()
                    var copied = 0L
                    input.use { inp ->
                        tmpFile.outputStream().use { out ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = inp.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                copied += n
                            }
                        }
                    }
                    DebugLogger.log(TAG, "Import streamed $copied bytes in ${System.currentTimeMillis() - t0}ms format=$format")
                    val t1 = System.currentTimeMillis()
                    val ebook = parser.parse(tmpFile, path)
                    DebugLogger.log(TAG, "Import parsed in ${System.currentTimeMillis() - t1}ms chapters=${ebook.chapters.size} title=${ebook.title}")
                    val internalFile = File(booksDir, "${ebook.id}.$format")
                    if (internalFile.exists()) {
                        tmpFile.delete()
                    } else {
                        if (!tmpFile.renameTo(internalFile)) {
                            tmpFile.copyTo(internalFile, overwrite = true)
                            tmpFile.delete()
                        }
                    }
                    val existing = bookDao.getBook(ebook.id)
                    if (existing == null) {
                        bookDao.upsertBook(
                            BookEntity(
                                id = ebook.id,
                                title = ebook.title,
                                author = ebook.author,
                                filePath = internalFile.absolutePath,
                                contentHash = ebook.contentHash,
                                format = format,
                                lastOpenedAt = System.currentTimeMillis()
                            )
                        )
                    } else {
                        bookDao.upsertBook(existing.copy(lastOpenedAt = System.currentTimeMillis()))
                    }
                    DebugLogger.log(TAG, "Import complete: ${internalFile.name}")
                    Result.success(ebook)
                } finally {
                    if (tmpFile.exists()) tmpFile.delete()
                }
            } catch (e: Throwable) {
                DebugLogger.logException(TAG, "Import failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun removeBook(bookId: String) {
        bookDao.deleteBook(bookId)
        positionDao.deletePosition(bookId)
    }

    suspend fun renderPdfPage(filePath: String, pageNumber: Int, scale: Float): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) return@withContext null
                val doc = PDDocument.load(file)
                try {
                    if (pageNumber < 1 || pageNumber > doc.numberOfPages) return@withContext null
                    val renderer = PDFRenderer(doc)
                    renderer.renderImage(pageNumber - 1, scale)
                } finally {
                    doc.close()
                }
            } catch (e: Throwable) {
                DebugLogger.logException(TAG, "renderPdfPage failed page=$pageNumber", e)
                null
            }
        }
    }

    suspend fun toggleFavorite(bookId: String) {
        val book = bookDao.getBook(bookId) ?: return
        bookDao.setFavorite(bookId, !book.isFavorite)
    }

    suspend fun savePosition(bookId: String, chapterIndex: Int, paragraphIndex: Int, chapterTitle: String) {
        positionDao.upsertPosition(
            PositionEntity(
                bookId = bookId,
                chapterIndex = chapterIndex,
                paragraphIndex = paragraphIndex,
                chapterTitle = chapterTitle
            )
        )
        bookDao.upsertBook(
            bookDao.getBook(bookId)?.copy(lastOpenedAt = System.currentTimeMillis()) ?: return
        )
    }

    suspend fun addBookmark(bookId: String, chapterIndex: Int, paragraphIndex: Int, label: String) {
        bookmarkDao.addBookmark(
            BookmarkEntity(
                bookId = bookId,
                chapterIndex = chapterIndex,
                paragraphIndex = paragraphIndex,
                label = label
            )
        )
    }

    suspend fun removeBookmark(bookmarkId: Long) {
        bookmarkDao.removeBookmark(bookmarkId)
    }

    fun loadBook(filePath: String): EbookBook? {
        return try {
            val format = detectFormat(filePath)
            val parser = parsers.find { it.supportsFormat(format) } ?: return null
            val file = File(filePath)
            if (file.exists()) {
                parser.parse(file, filePath)
            } else {
                val uri = Uri.parse(filePath)
                val input = context.contentResolver.openInputStream(uri) ?: return null
                val tmpFile = File(context.cacheDir, "load-${System.currentTimeMillis()}.$format")
                try {
                    input.use { inp ->
                        tmpFile.outputStream().use { out ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = inp.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                            }
                        }
                    }
                    parser.parse(tmpFile, filePath)
                } finally {
                    tmpFile.delete()
                }
            }
        } catch (e: Throwable) {
            DebugLogger.logException(TAG, "Load book failed", e)
            null
        }
    }

    suspend fun loadEpubImageBytes(filePath: String, href: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) return@withContext null
                val zipFile = java.util.zip.ZipFile(file)
                try {
                    val name = href.removePrefix("/")
                    var entry = zipFile.getEntry(name)
                    if (entry == null) {
                        val entries = zipFile.entries()
                        while (entries.hasMoreElements()) {
                            val e = entries.nextElement()
                            if (e.name == name || e.name.endsWith("/$name")) {
                                entry = e
                                break
                            }
                        }
                    }
                    val found = entry ?: return@withContext null
                    zipFile.getInputStream(found).use { it.readBytes() }
                } finally {
                    zipFile.close()
                }
            } catch (e: Throwable) {
                DebugLogger.logException(TAG, "loadEpubImageBytes failed href=$href", e)
                null
            }
        }
    }

    private fun detectFormat(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "epub" -> "epub"
            "pdf" -> "pdf"
            "txt" -> "txt"
            "html", "htm", "mhtml", "xhtml" -> "html"
            else -> ext
        }
    }

    private fun detectFormatFromMime(mimeType: String?): String? {
        return when (mimeType?.lowercase()?.trim()) {
            "application/epub+zip" -> "epub"
            "application/pdf" -> "pdf"
            "text/plain" -> "txt"
            "text/html", "application/xhtml+xml" -> "html"
            else -> null
        }
    }
}
