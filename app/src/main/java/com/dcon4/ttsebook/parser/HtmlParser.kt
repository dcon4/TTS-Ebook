package com.dcon4.ttsebook.parser

import com.dcon4.ttsebook.data.EbookBook
import com.dcon4.ttsebook.data.EbookChapter
import com.dcon4.ttsebook.data.EbookParser
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import java.io.InputStream
import java.security.MessageDigest

class HtmlParser : EbookParser {
    override fun parse(inputStream: InputStream, filePath: String): EbookBook {
        val html = inputStream.bufferedReader().readText()
        val doc = Jsoup.parse(html)
        val title = doc.title().ifBlank {
            filePath.substringAfterLast('/').removeSuffix(".html").removeSuffix(".htm")
        }
        val headings = doc.select("h1, h2, h3")
        if (headings.isNotEmpty()) {
            val chapters = mutableListOf<EbookChapter>()
            var index = 0
            for (heading in headings) {
                val chapTitle = heading.text().ifBlank { "Chapter ${index + 1}" }
                val content = mutableListOf<String>()
                var elem = heading.nextElementSibling()
                while (elem != null && elem.tagName() !in listOf("h1", "h2", "h3")) {
                    val text = Jsoup.clean(elem.outerHtml(), Safelist.none())
                    if (text.isNotBlank()) content.add(text)
                    elem = elem.nextElementSibling()
                }
                if (content.isNotEmpty()) {
                    chapters.add(EbookChapter(index, chapTitle, content.joinToString("\n\n")))
                    index++
                }
            }
            if (chapters.isNotEmpty()) {
                val hash = computeHash(chapters)
                return EbookBook(id = hash, title = title, author = "Unknown", chapters = chapters, contentHash = hash)
            }
        }
        val bodyText = Jsoup.clean(html, Safelist.none())
            .replace(Regex("\\s+"), " ")
            .trim()
        val paragraphs = bodyText.split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 20 }
        val paragraphsPerChapter = 50
        val chapters = mutableListOf<EbookChapter>()
        paragraphs.chunked(paragraphsPerChapter).forEachIndexed { index, chunk ->
            val start = index * paragraphsPerChapter + 1
            val end = index * paragraphsPerChapter + chunk.size
            chapters.add(EbookChapter(index, "Section $start-$end", chunk.joinToString("\n\n")))
        }
        if (chapters.isEmpty()) {
            chapters.add(EbookChapter(0, title, bodyText))
        }
        val hash = computeHash(chapters)
        return EbookBook(id = hash, title = title, author = "Unknown", chapters = chapters, contentHash = hash)
    }

    override fun supportsFormat(format: String): Boolean {
        return format in listOf("html", "htm", "mhtml", "xhtml")
    }

    private fun computeHash(chapters: List<EbookChapter>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        chapters.take(3).forEach { chapter ->
            digest.update(chapter.content.take(4096).toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(40)
    }
}
