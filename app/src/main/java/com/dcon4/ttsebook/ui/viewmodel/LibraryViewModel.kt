package com.dcon4.ttsebook.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dcon4.ttsebook.data.BookEntity
import com.dcon4.ttsebook.data.BookRepository
import com.dcon4.ttsebook.data.EbookBook
import com.dcon4.ttsebook.debug.DebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortMode { RECENT, TITLE, AUTHOR, FORMAT }

@HiltViewModel
class LibraryViewModel @Inject constructor(
    application: Application,
    private val bookRepository: BookRepository
) : AndroidViewModel(application) {

    private val allBooksFlow = bookRepository.getAllBooks()
    private val recentBooksFlow = bookRepository.getRecentBooks(5)

    private val _sortMode = MutableStateFlow(SortMode.RECENT)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    val recentBooks: StateFlow<List<BookEntity>> = recentBooksFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val books: StateFlow<List<BookEntity>> = combine(allBooksFlow, _sortMode) { books, sort ->
        when (sort) {
            SortMode.RECENT -> books.sortedByDescending { it.lastOpenedAt }
            SortMode.TITLE -> books.sortedBy { it.title.lowercase() }
            SortMode.AUTHOR -> books.sortedBy { it.author.lowercase() }
            SortMode.FORMAT -> books.sortedBy { it.format }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _importResult = MutableStateFlow<Result<EbookBook>?>(null)
    val importResult: StateFlow<Result<EbookBook>?> = _importResult.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _deleteConfirmBookId = MutableStateFlow<String?>(null)
    val deleteConfirmBookId: StateFlow<String?> = _deleteConfirmBookId.asStateFlow()

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = bookRepository.importBook(uri)
            _importResult.value = result
            _isImporting.value = false
        }
    }

    fun toggleFavorite(bookId: String) {
        viewModelScope.launch {
            bookRepository.toggleFavorite(bookId)
        }
    }

    fun requestDelete(bookId: String) {
        _deleteConfirmBookId.value = bookId
    }

    fun confirmDelete() {
        val bookId = _deleteConfirmBookId.value ?: return
        viewModelScope.launch {
            bookRepository.removeBook(bookId)
            _deleteConfirmBookId.value = null
        }
    }

    fun cancelDelete() {
        _deleteConfirmBookId.value = null
    }

    fun loadBook(uriString: String): EbookBook? {
        return bookRepository.loadBook(uriString)
    }
}
