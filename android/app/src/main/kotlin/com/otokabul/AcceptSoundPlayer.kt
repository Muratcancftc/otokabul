package com.otokabul

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * Yolculuk kabul edildiğinde kısa "ding" sesi çalar.
 * Atlama durumunda ses çalmaz.
 */
object AcceptSoundPlayer {

    private const val DING_DURATION_MS = 180

    fun playDing(context: Context) {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, DING_DURATION_MS)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    tone.release()
                } catch (_: Exception) {
                }
            }, (DING_DURATION_MS + 40).toLong())
        } catch (_: Exception) {
            // Ses kapalı veya cihaz desteklemiyorsa sessiz devam
        }
    }
}
