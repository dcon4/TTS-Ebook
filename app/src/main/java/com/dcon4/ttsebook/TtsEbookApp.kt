package com.dcon4.ttsebook

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.dcon4.ttsebook.debug.DebugLogger
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

private val Application.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@HiltAndroidApp
class TtsEbookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugLogger.init(this)
        DebugLogger.verboseEnabled = getSharedPreferences("ttsebook_settings", Context.MODE_PRIVATE)
            .getBoolean("verbose_logging", true)
        PDFBoxResourceLoader.init(this)
        DebugLogger.verbose("TtsEbookApp", "Application onCreate")
    }
}
