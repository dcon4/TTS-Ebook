package com.dcon4.ttsebook.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dcon4.ttsebook.data.BookRepository
import com.dcon4.ttsebook.data.EbookChapter
import com.dcon4.ttsebook.search.BooleanSearchEngine
import com.dcon4.ttsebook.search.SearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val bookRepository: BookRepository
) : ViewModel() {

    private val searchEngine = BooleanSearchEngine()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var chapters: List<EbookChapter> = emptyList()

    fun loadBook(bookId: String) {
        viewModelScope.launch {
            val entity = bookRepository.getBook(bookId) ?: return@launch
            val ebook = bookRepository.loadBook(entity.filePath) ?: return@launch
            chapters = ebook.chapters
        }
    }

    fun search(query: String) {
        _query.value = query
        if (query.isBlank() || chapters.isEmpty()) {
            _results.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            _results.value = searchEngine.search(query, chapters)
            _isSearching.value = false
        }
    }
}
