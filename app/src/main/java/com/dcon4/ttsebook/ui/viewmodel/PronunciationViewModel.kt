package com.dcon4.ttsebook.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dcon4.ttsebook.data.PronunciationEntity
import com.dcon4.ttsebook.data.PronunciationRepository
import com.dcon4.ttsebook.debug.DebugLogger
import com.dcon4.ttsebook.playback.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PronunciationViewModel @Inject constructor(
    application: Application,
    private val repository: PronunciationRepository,
    private val ttsManager: TtsManager
) : AndroidViewModel(application) {

    private val _entries = MutableStateFlow<List<PronunciationEntity>>(emptyList())
    val entries: StateFlow<List<PronunciationEntity>> = _entries.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAll().collect { list ->
                _entries.value = list
            }
        }
    }

    fun addEntry(word: String, replacement: String) {
        val w = word.trim()
        val r = replacement.trim()
        if (w.isEmpty() || r.isEmpty()) {
            _error.value = "Both fields are required"
            return
        }
        viewModelScope.launch {
            repository.add(w, r)
            _error.value = null
        }
    }

    fun removeEntry(id: Long) {
        viewModelScope.launch {
            repository.remove(id)
        }
    }

    fun testPronunciation(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        _testing.value = true
        if (ttsManager.ttsReady) {
            ttsManager.speak(t, "pron_test")
            _testing.value = false
            DebugLogger.log("PronunciationViewModel", "Test pronunciation: $t")
        } else {
            ttsManager.initTts { success ->
                if (success) ttsManager.speak(t, "pron_test")
                _testing.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}