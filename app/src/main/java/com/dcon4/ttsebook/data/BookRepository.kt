package com.dcon4.ttsebook.data

import android.content.Context
import android.net.Uri
import com.dcon4.ttsebook.debug.DebugLogger
import com.dcon4.ttsebook.parser.EpubParser
import com.dcon4.ttsebook.parser.HtmlParser
import com.dcon4.ttsebook.parser.PdfParser
import com.dcon4.ttsebook.parser.TxtParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
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
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("Cannot open file"))
            val path = uri.path ?: uri.toString()
            val format = detectFormat(path)
            val parser = parsers.find { it.supportsFormat(format) }
                ?: return Result.failure(Exception("Unsupported format: $format"))
            val ebook = parser.parse(inputStream, path)
            inputStream.close()
            val internalFile = File(context.filesDir, "books/${ebook.id}.$format")
            internalFile.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { src ->
                internalFile.outputStream().use { dst -> src.copyTo(dst) }
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
            Result.success(ebook)
        } catch (e: Exception) {
            DebugLogger.logException(TAG, "Import failed", e)
            Result.failure(e)
        }
    }

    suspend fun removeBook(bookId: String) {
        bookDao.deleteBook(bookId)
        positionDao.deletePosition(bookId)
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
            val file = File(filePath)
            if (file.exists()) {
                val inputStream = file.inputStream()
                val format = detectFormat(filePath)
                val parser = parsers.find { it.supportsFormat(format) } ?: return null
                val ebook = parser.parse(inputStream, filePath)
                inputStream.close()
                ebook
            } else {
                val uri = Uri.parse(filePath)
                val inputStream = context.contentResolver.openInputStream(uri) ?: return null
                val format = detectFormat(filePath)
                val parser = parsers.find { it.supportsFormat(format) } ?: return null
                val ebook = parser.parse(inputStream, filePath)
                inputStream.close()
                ebook
            }
        } catch (e: Throwable) {
            DebugLogger.logException(TAG, "Load book failed", e)
            null
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
}
