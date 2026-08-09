package com.dcon4.ttsebook.parser

import com.dcon4.ttsebook.data.EbookBook
import com.dcon4.ttsebook.data.EbookChapter
import com.dcon4.ttsebook.data.EbookParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.security.MessageDigest

class PdfParser : EbookParser {
    override fun parse(inputStream: InputStream, filePath: String): EbookBook {
        val doc = PDDocument.load(inputStream)
        val title = filePath.substringAfterLast('/').removeSuffix(".pdf")
        val numPages = doc.numberOfPages
        val stripper = PDFTextStripper()
        stripper.sortByPosition = true

        val rawPages = (1..numPages).map { p ->
            stripper.startPage = p
            stripper.endPage = p
            stripper.getText(doc)
        }

        val repeatedLines = findRepeatedLines(rawPages)

        val chapters = mutableListOf<EbookChapter>()
        var index = 0
        rawPages.forEachIndexed { i, raw ->
            val cleaned = cleanPageText(raw, repeatedLines)
            if (cleaned.isNotBlank()) {
                chapters.add(
                    EbookChapter(
                        index = index,
                        title = "Page ${i + 1}",
                        content = cleaned,
                        pageNumber = i + 1
                    )
                )
                index++
            }
        }
        doc.close()
        val hash = computeHash(chapters)
        return EbookBook(id = hash, title = title, author = "Unknown", chapters = chapters, contentHash = hash)
    }

    override fun supportsFormat(format: String): Boolean {
        return format.equals("pdf", ignoreCase = true)
    }

    private fun findRepeatedLines(rawPages: List<String>): Set<String> {
        val pageCount = rawPages.size
        if (pageCount < 2) return emptySet()
        val frequency = HashMap<String, Int>()
        rawPages.forEach { raw ->
            raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toSet().forEach { line ->
                frequency[line] = (frequency[line] ?: 0) + 1
            }
        }
        return frequency
            .filter { it.value >= 2 && it.value * 100 / pageCount >= 30 }
            .keys
    }

    private fun cleanPageText(raw: String, repeatedLines: Set<String>): String {
        val kept = mutableListOf<String>()
        for (rawLine in raw.split("\n")) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                kept.add("")
                continue
            }
            if (line.matches(Regex("\\d{1,4}"))) continue
            if (line in repeatedLines) continue
            kept.add(line)
        }
        val sb = StringBuilder()
        for (line in kept) {
            if (line.isEmpty()) {
                sb.append('\n')
                continue
            }
            if (sb.isEmpty()) {
                sb.append(line)
                continue
            }
            val lastChar = sb.last()
            if (lastChar == '-') {
                sb.deleteCharAt(sb.length - 1)
                sb.append(line)
            } else if (lastChar != '\n' && !lastChar.isWhitespace()) {
                sb.append(' ').append(line)
            } else {
                sb.append(line)
            }
        }
        return sb.toString().trim()
    }

    private fun computeHash(chapters: List<EbookChapter>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        chapters.take(3).forEach { chapter ->
            digest.update(chapter.content.take(4096).toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(40)
    }
}
