package com.dcon4.ttsebook.ui.viewmodel

import android.graphics.Bitmap

object ZoomImageHolder {
    var bitmap: Bitmap? = null
        private set

    fun set(b: Bitmap) {
        bitmap = b
    }

    fun clear() {
        bitmap = null
    }
}
