package com.dcon4.ttsebook.playback

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.dcon4.ttsebook.debug.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MAX_UTTERANCE_LENGTH = 4000
        private const val TAG = "TtsManager"
    }

    var tts: TextToSpeech? = null
        private set
    var ttsReady = false
        private set

    var enginePackage: String? = null
        private set
    var voiceName: String? = null
        private set
    var speechRate: Float = 1.0f
        private set

    private var onReadyListener: (() -> Unit)? = null
    var onUtteranceDone: ((String) -> Unit)? = null

    fun initTts(listener: ((Boolean) -> Unit)? = null) {
        val savedEngine = enginePackage
        tts = TextToSpeech(context, TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.language = Locale.US
                applyVoicePreference()
                tts?.setSpeechRate(speechRate)
                warmUp()
                DebugLogger.log(TAG, "TTS initialized successfully")
                listener?.invoke(true)
                onReadyListener?.invoke()
            } else {
                DebugLogger.logException(TAG, "TTS init failed", RuntimeException("status=$status"))
                listener?.invoke(false)
            }
        }, savedEngine)

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId != null) {
                    onUtteranceDone?.invoke(utteranceId)
                }
            }
            override fun onError(utteranceId: String?) {
                DebugLogger.log(TAG, "TTS error on utterance: $utteranceId")
            }
        })
    }

    fun reinitWithEngine(engine: String?) {
        tts?.stop()
        tts?.shutdown()
        ttsReady = false
        enginePackage = engine
        tts = TextToSpeech(context, TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.language = Locale.US
                applyVoicePreference()
                tts?.setSpeechRate(speechRate)
                warmUp()
                DebugLogger.log(TAG, "Re-initialized with engine: $engine")
            } else if (engine != null) {
                DebugLogger.log(TAG, "Engine $engine failed, retrying default")
                reinitWithEngine(null)
            }
        }, engine)
    }

    fun setVoice(name: String?) {
        voiceName = name
        applyVoicePreference()
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate
        if (ttsReady) {
            tts?.setSpeechRate(rate)
        }
    }

    fun getAvailableEngines(): List<TextToSpeech.EngineInfo> {
        return tts?.engines ?: emptyList()
    }

    fun getAvailableVoices(): List<Voice> {
        return tts?.voices?.filter { it.locale.language == "en" }?.sortedBy { it.name } ?: emptyList()
    }

    fun speak(text: String, utteranceId: String): Int {
        if (!ttsReady) return TextToSpeech.ERROR
        return tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) ?: TextToSpeech.ERROR
    }

    fun speakChunked(text: String, baseId: String, onChunkDone: ((String) -> Unit)? = null): Int {
        if (!ttsReady) return TextToSpeech.ERROR
        val chunks = chunkText(text)
        var result = TextToSpeech.SUCCESS
        chunks.forEachIndexed { index, chunk ->
            val utteranceId = "${baseId}_chunk_$index"
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val res = tts?.speak(chunk, queueMode, null, utteranceId) ?: TextToSpeech.ERROR
            if (res == TextToSpeech.ERROR) result = TextToSpeech.ERROR
        }
        return result
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    fun isSpeaking(): Boolean {
        return tts?.isSpeaking ?: false
    }

    private fun applyVoicePreference() {
        val name = voiceName ?: return
        val voice = tts?.voices?.find { it.name == name }
        if (voice != null) {
            tts?.voice = voice
            DebugLogger.log(TAG, "Applied voice: $name")
        } else {
            DebugLogger.log(TAG, "Voice $name not available, clearing preference")
            voiceName = null
        }
    }

    private fun warmUp() {
        speak("", "warmup")
    }

    fun chunkText(text: String): List<String> {
        if (text.length <= MAX_UTTERANCE_LENGTH) return listOf(text)
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = minOf(start + MAX_UTTERANCE_LENGTH, text.length)
            if (end >= text.length) {
                chunks.add(text.substring(start))
                break
            }
            val breakAt = findBreakPoint(text, start, end)
            chunks.add(text.substring(start, breakAt))
            start = breakAt
        }
        return chunks
    }

    private fun findBreakPoint(text: String, start: Int, maxEnd: Int): Int {
        val searchEnd = (maxEnd * 0.8).toInt()
        var breakAt = maxEnd
        for (c in listOf('\n', '.', '!', '?')) {
            val idx = text.lastIndexOf(c, searchEnd.coerceAtMost(maxEnd - 1))
            if (idx > start) {
                breakAt = idx + 1
                break
            }
        }
        return breakAt.coerceIn(start + 1, maxEnd)
    }

    fun splitParagraphs(text: String): List<String> {
        return text.split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    fun splitSentences(text: String): List<String> {
        return text.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}
