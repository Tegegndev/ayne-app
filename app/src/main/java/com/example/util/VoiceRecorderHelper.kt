package com.example.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import java.io.File

class VoiceRecorderHelper(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var isCurrentlyRecording = false

    val isRecording: Boolean
        get() = isCurrentlyRecording

    fun startRecording(): Boolean {
        if (isCurrentlyRecording) return false

        return try {
            val tempFile = File.createTempFile("mirkuz_mic_", ".m4a", context.cacheDir)
            currentOutputFile = tempFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(32000)
                setOutputFile(tempFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            isCurrentlyRecording = true
            true
        } catch (e: Exception) {
            cleanup()
            false
        }
    }

    /**
     * Stops the active recording, reads the audio bytes, and returns a Base64 string.
     */
    fun stopRecording(): String? {
        if (!isCurrentlyRecording) return null

        return try {
            try {
                mediaRecorder?.stop()
            } catch (e: Exception) {
                // Audio might be too short or stopped abruptly
            } finally {
                mediaRecorder?.release()
                mediaRecorder = null
                isCurrentlyRecording = false
            }

            val file = currentOutputFile
            if (file != null && file.exists() && file.length() > 0) {
                val bytes = file.readBytes()
                file.delete()
                currentOutputFile = null
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else {
                cleanup()
                null
            }
        } catch (e: Exception) {
            cleanup()
            null
        }
    }

    fun cancelRecording() {
        try {
            if (isCurrentlyRecording) {
                mediaRecorder?.stop()
            }
        } catch (ignored: Exception) {
        } finally {
            cleanup()
        }
    }

    private fun cleanup() {
        try {
            mediaRecorder?.release()
        } catch (ignored: Exception) {
        }
        mediaRecorder = null
        isCurrentlyRecording = false
        try {
            currentOutputFile?.delete()
        } catch (ignored: Exception) {
        }
        currentOutputFile = null
    }
}
