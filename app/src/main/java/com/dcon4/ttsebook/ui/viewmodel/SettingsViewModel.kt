package com.dcon4.ttsebook.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dcon4.ttsebook.BuildConfig
import com.dcon4.ttsebook.debug.DebugLogger
import com.dcon4.ttsebook.playback.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val ttsManager: TtsManager
) : AndroidViewModel(application) {

    private val _engines = MutableStateFlow<List<TextToSpeech.EngineInfo>>(emptyList())
    val engines: StateFlow<List<TextToSpeech.EngineInfo>> = _engines.asStateFlow()

    private val _voices = MutableStateFlow<List<Voice>>(emptyList())
    val voices: StateFlow<List<Voice>> = _voices.asStateFlow()

    private val _selectedEngine = MutableStateFlow<String?>(null)
    val selectedEngine: StateFlow<String?> = _selectedEngine.asStateFlow()

    private val _selectedVoice = MutableStateFlow<String?>(null)
    val selectedVoice: StateFlow<String?> = _selectedVoice.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _fontSize = MutableStateFlow(16)
    val fontSize: StateFlow<Int> = _fontSize.asStateFlow()

    init {
        loadTtsSettings()
    }

    private fun loadTtsSettings() {
        if (!ttsManager.ttsReady) {
            ttsManager.initTts { refreshVoices() }
        } else {
            refreshVoices()
        }
        _selectedEngine.value = ttsManager.enginePackage
        _selectedVoice.value = ttsManager.voiceName
        _speechRate.value = ttsManager.speechRate
    }

    fun refreshVoices() {
        _engines.value = ttsManager.getAvailableEngines()
        _voices.value = ttsManager.getAvailableVoices()
    }

    fun selectEngine(engine: String?) {
        _selectedEngine.value = engine
        ttsManager.reinitWithEngine(engine)
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            _voices.value = ttsManager.getAvailableVoices()
        }
    }

    fun selectVoice(voice: String?) {
        _selectedVoice.value = voice
        ttsManager.setVoice(voice)
    }

    fun setSpeechRate(rate: Float) {
        _speechRate.value = rate
        ttsManager.setSpeechRate(rate)
    }

    fun setFontSize(size: Int) {
        _fontSize.value = size
    }

    fun getShareLogIntent(): Intent? {
        val file = DebugLogger.getLogFile()?.takeIf { it.exists() } ?: return null
        val uri = FileProvider.getUriForFile(
            getApplication(),
            "${BuildConfig.APPLICATION_ID}.debug.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun getEmailLogIntent(): Intent? {
        val file = DebugLogger.getLogFile()?.takeIf { it.exists() } ?: return null
        val uri = FileProvider.getUriForFile(
            getApplication(),
            "${BuildConfig.APPLICATION_ID}.debug.fileprovider",
            file
        )
        val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        return Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_SUBJECT, "TTS Ebook debug log - $dateTime")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
