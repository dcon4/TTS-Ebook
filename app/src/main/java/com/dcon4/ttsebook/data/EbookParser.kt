package com.dcon4.ttsebook.data

import java.io.File

data class EbookImage(
    val anchorParagraphIndex: Int,
    val label: String,
    val mimeType: String?,
    val href: String
)

data class ChapterLink(
    val start: Int,
    val end: Int,
    val label: String,
    val href: String,
    val chapterIndex: Int? = null
)

data class EbookChapter(
    val index: Int,
    val title: String,
    val content: String,
    val images: List<EbookImage> = emptyList(),
    val links: List<ChapterLink> = emptyList(),
    val pageNumber: Int? = null
)

data class EbookBook(
    val id: String,
    val title: String,
    val author: String,
    val chapters: List<EbookChapter>,
    val contentHash: String
)

interface EbookParser {
    fun parse(file: File, displayPath: String): EbookBook
    fun supportsFormat(format: String): Boolean
}
