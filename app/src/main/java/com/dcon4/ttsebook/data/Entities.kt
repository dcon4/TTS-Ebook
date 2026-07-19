package com.dcon4.ttsebook.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val filePath: String,
    val contentHash: String,
    val format: String,
    val isFavorite: Boolean = false,
    val lastOpenedAt: Long = 0,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "positions")
data class PositionEntity(
    @PrimaryKey val bookId: String,
    val chapterIndex: Int = 0,
    val paragraphIndex: Int = 0,
    val characterOffset: Int = 0,
    val chapterTitle: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)
