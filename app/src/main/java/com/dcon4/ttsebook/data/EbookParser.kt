package com.dcon4.ttsebook.data

import java.io.InputStream

data class EbookImage(
    val anchorParagraphIndex: Int,
    val label: String,
    val mimeType: String?,
    val bytes: ByteArray
)

data class EbookChapter(
    val index: Int,
    val title: String,
    val content: String,
    val images: List<EbookImage> = emptyList(),
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
    fun parse(inputStream: InputStream, filePath: String): EbookBook
    fun supportsFormat(format: String): Boolean
}
