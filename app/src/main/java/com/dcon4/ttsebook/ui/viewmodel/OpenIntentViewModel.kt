package com.dcon4.ttsebook.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.dcon4.ttsebook.data.BookRepository
import com.dcon4.ttsebook.data.EbookBook
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltViewModel
class OpenIntentViewModel @Inject constructor(
    private val bookRepository: BookRepository
) : ViewModel() {

    suspend fun importBook(uri: Uri): Result<EbookBook> =
        withContext(Dispatchers.IO) { bookRepository.importBook(uri) }
}
