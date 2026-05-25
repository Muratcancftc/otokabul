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
        playTone(context, ToneGenerator.TONE_PROP_ACK, 200, 90)
    }

    /** Teklif görüldü ama km düşük — kısa tik (ding değil). */
    fun playSkipTick(context: Context) {
        playTone(context, ToneGenerator.TONE_PROP_BEEP, 80, 70)
    }

    private fun playTone(context: Context, toneType: Int, durationMs: Int, volume: Int) {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, volume)
            tone.startTone(toneType, durationMs)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    tone.release()
                } catch (_: Exception) {
                }
            }, (durationMs + 50).toLong())
        } catch (_: Exception) {
            try {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, volume)
                tone.startTone(toneType, durationMs)
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        tone.release()
                    } catch (_: Exception) {
                    }
                }, (durationMs + 50).toLong())
            } catch (_: Exception) {
            }
        }
    }
}
