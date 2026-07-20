package com.dcon4.ttsebook.debug

import android.content.Context
import android.util.Log
import com.dcon4.ttsebook.BuildConfig
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {
    private var logFile: File? = null
    private var writer: FileWriter? = null
    var verboseEnabled = true

    fun init(context: Context) {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val file = File(context.filesDir, "ttsebook-debug.log.$dateStr.txt")
        logFile = file
        writer = FileWriter(file, true)
        writer?.write("===== Session start =====\n")
        writer?.flush()
        verbose("DebugLogger", "Logger initialized (build ${BuildConfig.VERSION_CODE})")
    }

    fun log(tag: String, message: String) {
        val line = "${timestamp()} [$tag] $message"
        writer?.write("$line\n")
        writer?.flush()
        Log.d(tag, message)
    }

    fun verbose(tag: String, message: String) {
        if (!verboseEnabled) return
        val line = "${timestamp()} [$tag] [VERBOSE] $message"
        writer?.write("$line\n")
        writer?.flush()
        Log.v(tag, message)
    }

    fun logException(tag: String, message: String, throwable: Throwable) {
        log(tag, "$message: ${throwable.message}")
        Log.e(tag, message, throwable)
    }

    fun getLogFile(): File? {
        return logFile?.takeIf { it.exists() }
    }

    fun clearLog() {
        logFile?.let {
            if (it.exists()) it.writeText("")
        }
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
    }
}
