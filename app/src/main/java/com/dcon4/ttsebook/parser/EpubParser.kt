package com.dcon4.ttsebook.parser

import com.dcon4.ttsebook.data.EbookBook
import com.dcon4.ttsebook.data.EbookChapter
import com.dcon4.ttsebook.data.EbookImage
import com.dcon4.ttsebook.data.EbookParser
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.domain.Resource
import nl.siegmann.epublib.epub.EpubReader
import java.io.InputStream
import java.net.URI
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
            val raw = try {
                resource.resource?.inputStream?.bufferedReader()?.readText() ?: ""
            } catch (e: Exception) {
                ""
            }
            val images = extractImages(raw, href, book)
            val content = stripHtml(raw)
            if (content.isNotBlank()) {
                chapters.add(EbookChapter(index, chapTitle, content, images))
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

    private fun extractImages(html: String, chapterHref: String, book: Book): List<EbookImage> {
        val images = mutableListOf<EbookImage>()
        val imgRegex = Regex("<img[^>]*>", RegexOption.IGNORE_CASE)
        val srcRegex = Regex("""src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val altRegex = Regex("""alt\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        val blockRegex = Regex("(?i)<(p|div|li|h[1-6]|section|article)[\\s>]")
        for (match in imgRegex.findAll(html)) {
            val src = srcRegex.find(match.value)?.groupValues?.get(1) ?: continue
            val resolved = resolveHref(chapterHref, src)
            val resource = findResource(book, resolved) ?: continue
            val bytes = try {
                resource.data
            } catch (e: Exception) {
                try {
                    resource.inputStream?.readBytes()
                } catch (e2: Exception) {
                    null
                }
            } ?: continue
            val anchor = blockRegex.findAll(html.substring(0, match.range.first)).count()
            val alt = altRegex.find(match.value)?.groupValues?.get(1)?.trim().orEmpty()
            val label = alt.ifEmpty { src.substringAfterLast('/') }
            val mime = try {
                resource.mediaType?.name
            } catch (e: Exception) {
                null
            }
            images.add(EbookImage(anchor, label, mime, bytes))
        }
        return images
    }

    private fun findResource(book: Book, href: String): Resource? {
        val name = href.substringAfterLast('/')
        val candidates = listOf(href, href.removePrefix("/"), name).distinct()
        for (candidate in candidates) {
            try {
                val resource = book.resources.getById(candidate)
                if (resource != null) return resource
            } catch (_: Exception) {}
        }
        return try {
            book.resources.resourceMap.values.firstOrNull {
                it.href == href || (name.isNotEmpty() && it.href.endsWith("/$name"))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveHref(base: String, src: String): String {
        val cleanBase = base.substringBefore('#')
        return try {
            URI.create(cleanBase).resolve(src).normalize().toString().removePrefix("/")
        } catch (_: Exception) {
            val dir = cleanBase.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
            (dir + src).removePrefix("/")
        }
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
