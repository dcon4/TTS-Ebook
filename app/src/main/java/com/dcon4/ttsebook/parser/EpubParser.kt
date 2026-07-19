package com.dcon4.ttsebook.parser

import com.dcon4.ttsebook.data.EbookBook
import com.dcon4.ttsebook.data.EbookChapter
import com.dcon4.ttsebook.data.EbookParser
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.epub.EpubReader
import java.io.InputStream
import java.security.MessageDigest

class EpubParser : EbookParser {
    override fun parse(inputStream: InputStream, filePath: String): EbookBook {
        val book = EpubReader().readEpub(inputStream)
        val title = book.title ?: filePath.substringAfterLast('/').removeSuffix(".epub")
        val author = book.metadata.authors.firstOrNull()?.let {
            "${it.firstname} ${it.lastname}"
        }?.trim() ?: "Unknown"
        val chapters = mutableListOf<EbookChapter>()
        var index = 0
        for (resource in book.tableOfContents?.tocReferences ?: emptyList()) {
            val href = resource.resource?.href ?: continue
            val chapTitle = resource.title ?: "Chapter ${index + 1}"
            val content = try {
                val raw = resource.resource?.inputStream?.bufferedReader()?.readText() ?: ""
                stripHtml(raw)
            } catch (e: Exception) {
                ""
            }
            if (content.isNotBlank()) {
                chapters.add(EbookChapter(index, chapTitle, content))
                index++
            }
        }
        if (chapters.isEmpty()) {
            val allContent = buildString {
                for (resource in book.contents) {
                    try {
                        val text = stripHtml(resource.inputStream.bufferedReader().readText())
                        append("$text\n\n")
                    } catch (_: Exception) {}
                }
            }
            chapters.add(EbookChapter(0, title, allContent))
        }
        val hash = computeHash(chapters)
        return EbookBook(
            id = hash,
            title = title,
            author = author,
            chapters = chapters,
            contentHash = hash
        )
    }

    override fun supportsFormat(format: String): Boolean {
        return format.equals("epub", ignoreCase = true)
    }

    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&[a-zA-Z]+;"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun computeHash(chapters: List<EbookChapter>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        chapters.take(3).forEach { chapter ->
            digest.update(chapter.content.take(4096).toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(40)
    }
}
