package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
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
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class ProcessingFeedbackHelper(private val context: Context) {
    private var toneGenerator: ToneGenerator? = null
    private var audioTrack: AudioTrack? = null
    private var loopJob: Job? = null

    // Precomputed high-volume, resonant acoustic sonar pulse
    private val sampleRate = 44100
    private val pulseDurationMs = 200
    private val numSamples = (sampleRate * pulseDurationMs) / 1000
    private val pcmPulseBuffer = ShortArray(numSamples)

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
        // Build rich, loud acoustic pulse: 620Hz fundamental + 1240Hz warm harmonic with full dynamic range
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-t * 9.0) // Smooth decay envelope for clear resonance
            val fundamental = sin(2.0 * PI * 620.0 * t)
            val harmonic = 0.35 * sin(2.0 * PI * 1240.0 * t)
            val wave = (fundamental + harmonic) / 1.35
            val sample = (wave * envelope * Short.MAX_VALUE * 0.95).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            pcmPulseBuffer[i] = sample.toShort()
        }

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcmPulseBuffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build().apply {
                    write(pcmPulseBuffer, 0, pcmPulseBuffer.size)
                }
        } catch (e: Exception) {
            audioTrack = null
        }

        try {
            // ToneGenerator at 100% maximum volume for fallback
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            toneGenerator = null
        }
    }

    /**
     * Starts a prominent, big acoustic pulse and distinct haptic feedback
     * so blind and visually impaired users clearly know the app is actively working.
     */
    fun startWorkingFeedback(scope: CoroutineScope) {
        stopWorkingFeedback()

        // Immediate first pulse
        pulseAudioAndHaptic()

        loopJob = scope.launch(Dispatchers.Default) {
            try {
                while (isActive) {
                    delay(800)
                    if (isActive) {
                        pulseAudioAndHaptic()
                    }
                }
            } finally {
                try {
                    audioTrack?.stop()
                    toneGenerator?.stopTone()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    private fun pulseAudioAndHaptic() {
        var played = false

        // 1. Play high-volume acoustic sonar pulse via AudioTrack
        try {
            audioTrack?.let { track ->
                track.stop()
                track.reloadStaticData()
                track.play()
                played = true
            }
        } catch (e: Exception) {
            played = false
        }

        // 2. Fallback to full volume ToneGenerator if AudioTrack is unavailable
        if (!played) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 180)
            } catch (e: Exception) {
                // Ignore tone failure
            }
        }

        // 3. Robust distinct haptic feedback
        try {
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(55, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(55)
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
            audioTrack?.stop()
            toneGenerator?.stopTone()
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun playSuccessTone() {
        stopWorkingFeedback()
        try {
            // Loud high-frequency acknowledgment chime (volume 100)
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 220)
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun release() {
        stopWorkingFeedback()
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioTrack = null

        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            // Ignore
        }
        toneGenerator = null
    }
}
