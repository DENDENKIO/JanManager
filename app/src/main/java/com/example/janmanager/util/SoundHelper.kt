package com.example.janmanager.util

import android.media.AudioManager
import android.media.ToneGenerator

object SoundHelper {
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * スキャン成功時にビープ音を再生する
     */
    fun playSuccessBeep() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
    }

    /**
     * リソースを解放する
     */
    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
