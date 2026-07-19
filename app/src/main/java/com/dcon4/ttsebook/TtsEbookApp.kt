package com.dcon4.ttsebook

import android.app.Application
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
        PDFBoxResourceLoader.init(this)
        DebugLogger.verbose("TtsEbookApp", "Application onCreate")
    }
}
