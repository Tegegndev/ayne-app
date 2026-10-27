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

/**
 * Audio and Haptic feedback helper for blind and visually impaired users.
 * Uses pleasant, gentle assistant chimes instead of awkward or jarring camera shutter sounds.
 */
class ProcessingFeedbackHelper(private val context: Context) {
    private var toneGenerator: ToneGenerator? = null
    private var workingPulseTrack: AudioTrack? = null
    private var tapChimeTrack: AudioTrack? = null
    private var loopJob: Job? = null

    private val sampleRate = 44100

    // 1. Pleasant, gentle dual-tone assistant tap chime (~120ms)
    // Plays a soft, warm ascending harmonic chime (E5 -> B5)
    private val tapDurationMs = 120
    private val tapNumSamples = (sampleRate * tapDurationMs) / 1000
    private val pcmTapBuffer = ShortArray(tapNumSamples)

    // 2. High-volume, resonant acoustic sonar pulse for working/analyzing state (~200ms)
    private val pulseDurationMs = 200
    private val pulseNumSamples = (sampleRate * pulseDurationMs) / 1000
    private val pcmPulseBuffer = ShortArray(pulseNumSamples)

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
        // --- Synthesize Pleasant Tap Chime ---
        // Warm, friendly two-tone assistant earcon (659Hz E5 blending into 988Hz B5)
        for (i in 0 until tapNumSamples) {
            val t = i.toDouble() / sampleRate
            val sample = if (t < 0.045) {
                // First soft bell note (659Hz) with smooth fade-in
                val attack = (t / 0.008).coerceAtMost(1.0)
                val decay = exp(-t * 22.0)
                val wave = sin(2.0 * PI * 659.25 * t) + 0.3 * sin(2.0 * PI * 1318.5 * t)
                (wave * attack * decay * Short.MAX_VALUE * 0.75).toInt()
            } else {
                // Second bright, cheerful bell note (987.7Hz)
                val t2 = t - 0.045
                val attack = (t2 / 0.006).coerceAtMost(1.0)
                val decay = exp(-t2 * 25.0)
                val wave = sin(2.0 * PI * 987.77 * t2) + 0.25 * sin(2.0 * PI * 1975.5 * t2)
                (wave * attack * decay * Short.MAX_VALUE * 0.75).toInt()
            }
            pcmTapBuffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        try {
            tapChimeTrack = AudioTrack.Builder()
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
                .setBufferSizeInBytes(pcmTapBuffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build().apply {
                    write(pcmTapBuffer, 0, pcmTapBuffer.size)
                }
        } catch (e: Exception) {
            tapChimeTrack = null
        }

        // --- Synthesize Working Sonar Pulse ---
        for (i in 0 until pulseNumSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-t * 9.0) // Smooth decay envelope for clear resonance
            val fundamental = sin(2.0 * PI * 620.0 * t)
            val harmonic = 0.35 * sin(2.0 * PI * 1240.0 * t)
            val wave = (fundamental + harmonic) / 1.35
            val sample = (wave * envelope * Short.MAX_VALUE * 0.95).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            pcmPulseBuffer[i] = sample.toShort()
        }

        try {
            workingPulseTrack = AudioTrack.Builder()
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
            workingPulseTrack = null
        }

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
        } catch (e: Exception) {
            toneGenerator = null
        }
    }

    /**
     * Pleasant, gentle tap earcon and haptic feedback triggered immediately on button tap (0ms)
     * so blind users have zero-latency sensory feedback without any jarring or creepy shutter sounds.
     */
    fun playInstantTapEarcon() {
        var played = false
        try {
            tapChimeTrack?.let { track ->
                track.stop()
                track.reloadStaticData()
                track.play()
                played = true
            }
        } catch (e: Exception) {
            played = false
        }

        if (!played) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
            } catch (e: Exception) {
                // Ignore tone failure
            }
        }

        try {
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(40)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore vibration failure
        }
    }

    /**
     * Starts a prominent acoustic pulse and distinct haptic feedback
     * so blind and visually impaired users clearly know the app is actively working.
     */
    fun startWorkingFeedback(scope: CoroutineScope) {
        stopWorkingFeedback()

        // Immediate first pulse
        pulseAudioAndHaptic()

        loopJob = scope.launch(Dispatchers.Default) {
            try {
                while (isActive) {
                    delay(1500) // Calm, rhythmic 1.5s interval
                    if (isActive) {
                        pulseAudioAndHaptic()
                    }
                }
            } finally {
                try {
                    workingPulseTrack?.stop()
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
            workingPulseTrack?.let { track ->
                track.stop()
                track.reloadStaticData()
                track.play()
                played = true
            }
        } catch (e: Exception) {
            played = false
        }

        // 2. Fallback to tone generator if AudioTrack is unavailable
        if (!played) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 150)
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
            workingPulseTrack?.stop()
            toneGenerator?.stopTone()
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun playSuccessTone() {
        stopWorkingFeedback()
        try {
            // Pleasant high-frequency acknowledgment chime
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 200)
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun release() {
        stopWorkingFeedback()
        try {
            workingPulseTrack?.release()
        } catch (e: Exception) {
            // Ignore
        }
        workingPulseTrack = null

        try {
            tapChimeTrack?.release()
        } catch (e: Exception) {
            // Ignore
        }
        tapChimeTrack = null

        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            // Ignore
        }
        toneGenerator = null
    }
}
