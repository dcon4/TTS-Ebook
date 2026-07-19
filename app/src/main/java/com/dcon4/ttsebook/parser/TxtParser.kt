package com.dcon4.ttsebook.parser

import com.dcon4.ttsebook.data.EbookBook
import com.dcon4.ttsebook.data.EbookChapter
import com.dcon4.ttsebook.data.EbookParser
import java.io.InputStream
import java.security.MessageDigest

class TxtParser : EbookParser {
    override fun parse(inputStream: InputStream, filePath: String): EbookBook {
        val text = inputStream.bufferedReader().readText()
        val title = filePath.substringAfterLast('/').removeSuffix(".txt")
        val paragraphs = text.split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val paragraphsPerChapter = 50
        val chapters = mutableListOf<EbookChapter>()
        paragraphs.chunked(paragraphsPerChapter).forEachIndexed { index, chunk ->
            val start = index * paragraphsPerChapter + 1
            val end = index * paragraphsPerChapter + chunk.size
            chapters.add(EbookChapter(index, "Section $start-$end", chunk.joinToString("\n\n")))
        }
        if (chapters.isEmpty()) {
            chapters.add(EbookChapter(0, title, text))
        }
        val hash = computeHash(text)
        return EbookBook(id = hash, title = title, author = "Unknown", chapters = chapters, contentHash = hash)
    }

    override fun supportsFormat(format: String): Boolean {
        return format.equals("txt", ignoreCase = true)
    }

    private fun computeHash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(text.take(65536).toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }.take(40)
    }
}
