package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentLinkedQueue

class AudioPlaybackHelper {
    private var mediaPlayer: MediaPlayer? = null
    private var lastAudioFile: File? = null
    private val chunkQueue = ConcurrentLinkedQueue<File>()
    @Volatile
    private var isPlayingQueue = false

    val isPlaying: Boolean
        get() = try {
            mediaPlayer?.isPlaying == true || isPlayingQueue
        } catch (e: Exception) {
            false
        }

    /**
     * Enqueue a real-time audio chunk for immediate sequential playback.
     * Starts playback as soon as Chunk #1 arrives.
     */
    @Synchronized
    fun enqueueAudioChunk(
        context: Context,
        rawBase64Chunk: String,
        format: String = "mp3",
        onError: ((String) -> Unit)? = null
    ) {
        try {
            val cleanBase64 = if (rawBase64Chunk.contains(",")) {
                rawBase64Chunk.substringAfter(",")
            } else {
                rawBase64Chunk
            }
            if (cleanBase64.isBlank()) return

            val audioBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val ext = if (format.equals("wav", ignoreCase = true)) ".wav" else ".mp3"
            val tempChunkFile = File.createTempFile("mirkuz_chunk_", ext, context.cacheDir).apply {
                deleteOnExit()
            }
            FileOutputStream(tempChunkFile).use { fos ->
                fos.write(audioBytes)
                fos.flush()
            }

            chunkQueue.add(tempChunkFile)

            if (!isPlayingQueue) {
                playNextChunkInQueue(onError)
            }
        } catch (e: Exception) {
            onError?.invoke(e.localizedMessage ?: "Failed to process audio chunk")
        }
    }

    @Synchronized
    private fun playNextChunkInQueue(onError: ((String) -> Unit)? = null) {
        val nextFile = chunkQueue.poll()
        if (nextFile == null) {
            isPlayingQueue = false
            return
        }

        isPlayingQueue = true
        try {
            releasePlayer()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .build()
                )
                setDataSource(nextFile.absolutePath)
                setOnCompletionListener {
                    try {
                        nextFile.delete()
                    } catch (e: Exception) {
                        // Ignore
                    }
                    releasePlayer()
                    playNextChunkInQueue(onError)
                }
                setOnErrorListener { _, what, extra ->
                    try {
                        nextFile.delete()
                    } catch (e: Exception) {
                        // Ignore
                    }
                    releasePlayer()
                    onError?.invoke("MediaPlayer error in stream chunk: $what, extra: $extra")
                    playNextChunkInQueue(onError)
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            try {
                nextFile.delete()
            } catch (ignored: Exception) {}
            releasePlayer()
            onError?.invoke(e.localizedMessage ?: "Failed to play audio chunk")
            playNextChunkInQueue(onError)
        }
    }

    /**
     * Stores the consolidated audio from all chunks for replay functionality.
     */
    fun setLastConsolidatedAudio(context: Context, rawBase64Audio: String) {
        try {
            val cleanBase64 = if (rawBase64Audio.contains(",")) {
                rawBase64Audio.substringAfter(",")
            } else {
                rawBase64Audio
            }
            if (cleanBase64.isBlank()) return

            val audioBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val fullFile = File.createTempFile("mirkuz_speech_full_", ".mp3", context.cacheDir).apply {
                deleteOnExit()
            }
            FileOutputStream(fullFile).use { fos ->
                fos.write(audioBytes)
                fos.flush()
            }
            lastAudioFile = fullFile
        } catch (e: Exception) {
            // Ignore
        }
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
            val tempFile = File.createTempFile("mirkuz_speech_", ".mp3", context.cacheDir).apply {
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
            stop()
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

    @Synchronized
    fun stop() {
        isPlayingQueue = false
        while (chunkQueue.isNotEmpty()) {
            try {
                chunkQueue.poll()?.delete()
            } catch (e: Exception) {}
        }
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
