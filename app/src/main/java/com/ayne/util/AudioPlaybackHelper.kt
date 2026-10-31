package com.ayne.util

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

    /** True once any chunk of the current queue actually started playing. */
    @Volatile
    private var queuePlayedAny = false

    /** Consecutive unplayable chunks in a row (reset on every successful play). */
    @Volatile
    private var consecutiveChunkFailures = 0

    /**
     * Notified whenever audio playback starts or fully stops, so the UI can
     * reflect the "assistant is speaking" state (e.g. the voice orb).
     */
    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    @Volatile
    private var lastNotifiedPlaying = false

    val isPlaying: Boolean
        get() = try {
            mediaPlayer?.isPlaying == true || isPlayingQueue
        } catch (e: Exception) {
            false
        }

    private fun notifyPlaybackState(playing: Boolean) {
        if (playing != lastNotifiedPlaying) {
            lastNotifiedPlaying = playing
            onPlaybackStateChanged?.invoke(playing)
        }
    }

    /**
     * Enqueue a real-time audio chunk for immediate sequential playback.
     * Starts playback as soon as Chunk #1 arrives (~716ms after OCR).
     */
    @Synchronized
    fun enqueueAudioChunk(
        context: Context,
        rawBase64Chunk: String,
        format: String = "mp3",
        onFirstChunkPlay: (() -> Unit)? = null,
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
            if (audioBytes.isEmpty()) {
                // Streaming servers occasionally emit empty segments; nothing
                // playable here and nothing worth surfacing to the user.
                return
            }
            val ext = if (format.equals("wav", ignoreCase = true)) ".wav" else ".mp3"
            val tempChunkFile = TempAudioStore.createTempAudioFile(context, "ayne_chunk_", ext).apply {
                deleteOnExit()
            }
            FileOutputStream(tempChunkFile).use { fos ->
                fos.write(audioBytes)
                fos.flush()
            }

            chunkQueue.add(tempChunkFile)

            if (!isPlayingQueue) {
                onFirstChunkPlay?.invoke()
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
            notifyPlaybackState(false)
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
                    onChunkFailure(onError, "MediaPlayer error in stream chunk: $what, extra: $extra")
                    playNextChunkInQueue(onError)
                    true
                }
                prepare()
                start()
                // This chunk parsed and started: the stream is healthy.
                queuePlayedAny = true
                consecutiveChunkFailures = 0
                notifyPlaybackState(true)
            }
        } catch (e: Exception) {
            try {
                nextFile.delete()
            } catch (ignored: Exception) {
                // Deliberate: ignore error when deleting unplayable temporary audio chunk file
            }
            releasePlayer()
            // Streamed TTS arrives as arbitrary byte slices: a chunk can be a
            // header fragment or a split MP3 frame that MediaPlayer cannot
            // parse on its own, so prepare() throws. That chunk is skipped and
            // the next one usually plays fine — only report a failure when
            // playback is genuinely dead (nothing played and nothing left).
            onChunkFailure(
                onError,
                e.localizedMessage ?: "Failed to play audio chunk"
            )
            playNextChunkInQueue(onError)
        }
    }

    /**
     * Report an audio failure only if it actually broke playback: silent for
     * ordinary mid-stream segment skips, loud once it's clear nothing is
     * playing and nothing is left.
     */
    private fun onChunkFailure(onError: ((String) -> Unit)?, message: String) {
        consecutiveChunkFailures++
        val queueEmpty = chunkQueue.isEmpty()
        if (!queuePlayedAny && queueEmpty && consecutiveChunkFailures >= 2) {
            onError?.invoke("Audio stream unplayable ($message)")
        } else if (!queuePlayedAny && queueEmpty) {
            onError?.invoke("Skipped unplayable audio segment ($message)")
        }
        // Otherwise: a later chunk will play — don't scare the user.
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
            val fullFile = TempAudioStore.createTempAudioFile(context, "ayne_speech_full_", ".mp3").apply {
                deleteOnExit()
            }
            FileOutputStream(fullFile).use { fos ->
                fos.write(audioBytes)
                fos.flush()
            }
            lastAudioFile = fullFile
        } catch (e: Exception) {
            android.util.Log.e("AudioPlaybackHelper", "Failed to cache consolidated audio: ${e.message}", e)
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
            if (audioBytes.isEmpty()) {
                onError?.invoke("No audio data received from server")
                return
            }
            val tempFile = TempAudioStore.createTempAudioFile(context, "ayne_speech_", ".mp3").apply {
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
                    notifyPlaybackState(false)
                }
                setOnErrorListener { _, what, extra ->
                    onError?.invoke("MediaPlayer error: $what, extra: $extra")
                    releasePlayer()
                    notifyPlaybackState(false)
                    true
                }
                prepare()
                start()
                notifyPlaybackState(true)
            }
        } catch (e: Exception) {
            onError?.invoke(e.localizedMessage ?: "Failed to initialize media player")
            releasePlayer()
            notifyPlaybackState(false)
        }
    }

    @Synchronized
    fun stop() {
        isPlayingQueue = false
        queuePlayedAny = false
        consecutiveChunkFailures = 0
        while (chunkQueue.isNotEmpty()) {
            try {
                chunkQueue.poll()?.delete()
            } catch (e: Exception) {
                // Deliberate: ignore error deleting discarded temporary audio chunk
            }
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
            notifyPlaybackState(false)
        }
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            android.util.Log.e("AudioPlaybackHelper", "Failed to cache consolidated audio: ${e.message}", e)
        } finally {
            mediaPlayer = null
        }
    }
}
