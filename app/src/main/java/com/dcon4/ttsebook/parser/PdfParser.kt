package com.dcon4.ttsebook.parser

import com.dcon4.ttsebook.data.EbookBook
import com.dcon4.ttsebook.data.EbookChapter
import com.dcon4.ttsebook.data.EbookParser
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.security.MessageDigest

class PdfParser : EbookParser {
    override fun parse(inputStream: InputStream, filePath: String): EbookBook {
        val doc = PDDocument.load(inputStream)
        val title = filePath.substringAfterLast('/').removeSuffix(".pdf")
        val numPages = doc.numberOfPages
        val chapters = mutableListOf<EbookChapter>()
        val stripper = PDFTextStripper()
        stripper.sortByPosition = true
        val pageGroupSize = 10
        var chapterIndex = 0
        var pageStart = 1
        while (pageStart <= numPages) {
            val pageEnd = minOf(pageStart + pageGroupSize - 1, numPages)
            stripper.startPage = pageStart
            stripper.endPage = pageEnd
            val text = stripper.getText(doc).trim()
            if (text.isNotBlank()) {
                chapters.add(EbookChapter(chapterIndex, "Pages $pageStart-$pageEnd", text))
                chapterIndex++
            }
            pageStart = pageEnd + 1
        }
        doc.close()
        val hash = computeHash(chapters)
        return EbookBook(id = hash, title = title, author = "Unknown", chapters = chapters, contentHash = hash)
    }

    override fun supportsFormat(format: String): Boolean {
        return format.equals("pdf", ignoreCase = true)
    }

    private fun computeHash(chapters: List<EbookChapter>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        chapters.take(3).forEach { chapter ->
            digest.update(chapter.content.take(4096).toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(40)
    }
}
