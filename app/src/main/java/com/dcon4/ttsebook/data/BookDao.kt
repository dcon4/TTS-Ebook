package com.dcon4.ttsebook.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE isFavorite = 1 ORDER BY title")
    fun getFavoriteBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBook(bookId: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBook(book: BookEntity)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBook(bookId: String)

    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun getRecentBooks(limit: Int = 20): Flow<List<BookEntity>>

    @Query("UPDATE books SET isFavorite = :favorite WHERE id = :bookId")
    suspend fun setFavorite(bookId: String, favorite: Boolean)
}

@Dao
interface PositionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPosition(position: PositionEntity)

    @Query("SELECT * FROM positions WHERE bookId = :bookId")
    suspend fun getPosition(bookId: String): PositionEntity?

    @Query("DELETE FROM positions WHERE bookId = :bookId")
    suspend fun deletePosition(bookId: String)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY chapterIndex, paragraphIndex")
    fun getBookmarks(bookId: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun removeBookmark(bookmarkId: Long)
}
