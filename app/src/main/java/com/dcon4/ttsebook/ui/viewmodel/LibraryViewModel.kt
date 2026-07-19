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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    application: Application,
    private val bookRepository: BookRepository
) : AndroidViewModel(application) {

    val books: StateFlow<List<BookEntity>> = bookRepository.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _importResult = MutableStateFlow<Result<EbookBook>?>(null)
    val importResult: StateFlow<Result<EbookBook>?> = _importResult.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

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

    fun removeBook(bookId: String) {
        viewModelScope.launch {
            bookRepository.removeBook(bookId)
        }
    }

    fun loadBook(uriString: String): EbookBook? {
        return bookRepository.loadBook(uriString)
    }
}
