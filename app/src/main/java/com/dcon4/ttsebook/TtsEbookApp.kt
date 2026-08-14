package com.dcon4.ttsebook

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.dcon4.ttsebook.data.PronunciationRepository
import com.dcon4.ttsebook.debug.DebugLogger
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val Application.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@HiltAndroidApp
class TtsEbookApp : Application() {

    @Inject
    lateinit var pronunciationRepository: PronunciationRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        DebugLogger.init(this)
        DebugLogger.verboseEnabled = getSharedPreferences("ttsebook_settings", Context.MODE_PRIVATE)
            .getBoolean("verbose_logging", true)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = java.io.StringWriter()
                val pw = java.io.PrintWriter(sw)
                throwable.printStackTrace(pw)
                DebugLogger.log("TtsEbookApp", "Uncaught ${throwable.javaClass.simpleName} on ${thread.name}: ${throwable.message}")
                DebugLogger.log("TtsEbookApp", sw.toString().take(2000))
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
        PDFBoxResourceLoader.init(this)
        appScope.launch { pronunciationRepository.reload() }
        DebugLogger.verbose("TtsEbookApp", "Application onCreate")
    }
}
