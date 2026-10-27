package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ProcessingFeedbackHelper(private val context: Context) {
    private var toneGenerator: ToneGenerator? = null
    private var loopJob: Job? = null

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 65)
        } catch (e: Exception) {
            toneGenerator = null
        }
    }

    /**
     * Starts a rhythmic acoustic pulse and subtle haptic feedback
     * so blind and visually impaired users know the app is actively working.
     */
    fun startWorkingFeedback(scope: CoroutineScope) {
        stopWorkingFeedback()

        // Immediate first pulse
        pulseAudioAndHaptic()

        loopJob = scope.launch(Dispatchers.Default) {
            try {
                while (isActive) {
                    delay(850)
                    if (isActive) {
                        pulseAudioAndHaptic()
                    }
                }
            } finally {
                try {
                    toneGenerator?.stopTone()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    private fun pulseAudioAndHaptic() {
        try {
            // Soft pleasant accessibility prompt tone (100ms)
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
        } catch (e: Exception) {
            // Ignore tone failure
        }

        try {
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(35)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore vibration failure
        }
    }

    fun stopWorkingFeedback() {
        loopJob?.cancel()
        loopJob = null
        try {
            toneGenerator?.stopTone()
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun playSuccessTone() {
        stopWorkingFeedback()
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun release() {
        stopWorkingFeedback()
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            // Ignore
        }
        toneGenerator = null
    }
}
