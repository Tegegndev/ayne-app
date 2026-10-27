package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

class AudioPlaybackHelper {
    private var mediaPlayer: MediaPlayer? = null
    private var lastAudioFile: File? = null

    val isPlaying: Boolean
        get() = try {
            mediaPlayer?.isPlaying == true
        } catch (e: Exception) {
            false
        }

    fun playBase64Audio(
        context: Context,
        rawBase64Audio: String,
        onComplete: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        try {
            stop()

            val cleanBase64 = if (rawBase64Audio.contains(",")) {
                rawBase64Audio.substringAfter(",")
            } else {
                rawBase64Audio
            }

            val audioBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val tempFile = File.createTempFile("mirkuz_speech_", ".wav", context.cacheDir).apply {
                deleteOnExit()
            }

            FileOutputStream(tempFile).use { fos ->
                fos.write(audioBytes)
                fos.flush()
            }
            lastAudioFile = tempFile

            playFile(tempFile, onComplete, onError)
        } catch (e: Exception) {
            onError?.invoke(e.localizedMessage ?: "Audio playback error")
        }
    }

    fun replayLastAudio(
        onComplete: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ): Boolean {
        val file = lastAudioFile
        if (file != null && file.exists() && file.length() > 0) {
            playFile(file, onComplete, onError)
            return true
        }
        return false
    }

    private fun playFile(
        file: File,
        onComplete: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        try {
            stop()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    onComplete?.invoke()
                    releasePlayer()
                }
                setOnErrorListener { _, what, extra ->
                    onError?.invoke("MediaPlayer error: $what, extra: $extra")
                    releasePlayer()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            onError?.invoke(e.localizedMessage ?: "Failed to initialize media player")
            releasePlayer()
        }
    }

    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            // Ignore stop errors
        } finally {
            mediaPlayer = null
        }
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore
        } finally {
            mediaPlayer = null
        }
    }
}
