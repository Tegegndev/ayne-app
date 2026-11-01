package com.ayne.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Plays backend-generated permission clips, with Android TTS as an offline fallback. */
class PermissionPromptSpeaker(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build()
    private val cacheDirectory = File(appContext.filesDir, "permission-guidance")
    private val clips = mutableMapOf<String, File>()
    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingTts: (() -> Unit)? = null

    init {
        cacheDirectory.mkdirs()
        tts = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            pendingTts?.also { pending ->
                pendingTts = null
                pending()
            }
        }
    }

    /** Downloads the manifest and caches the six small clips without blocking the UI. */
    fun preload(baseUrl: String) {
        scope.launch {
            runCatching {
                val manifestUrl = "${baseUrl.trimEnd('/')}/api/v1/audio/permission-guidance/manifest"
                val request = Request.Builder().url(manifestUrl).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Guidance manifest HTTP ${response.code}")
                    val json = JSONObject(response.body?.string() ?: error("Empty guidance manifest"))
                    val supportedLanguages = json.optJSONArray("supportedLanguages") ?: return@use
                    val supportedMessages = json.optJSONArray("supportedMessages") ?: return@use
                    val manifestClips = json.optJSONArray("clips") ?: return@use
                    val languages = (0 until supportedLanguages.length()).map { supportedLanguages.getString(it) }.toSet()
                    val messages = (0 until supportedMessages.length()).map { supportedMessages.getString(it) }.toSet()
                    for (i in 0 until manifestClips.length()) {
                        val clip = manifestClips.optJSONObject(i) ?: continue
                        val language = clip.optString("language")
                        val message = clip.optString("message")
                        if (language !in setOf("en", "am", "om") || message !in setOf("camera", "microphone") ||
                            language !in languages || message !in messages
                        ) continue
                        val id = "$language/$message"
                        if (synchronized(clips) { id in clips }) continue

                        val version = json.optString("version", "1")
                        val digest = clip.optString("sha256").takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
                        val cached = File(cacheDirectory, "${version}_${language}_${message}_${digest ?: "clip"}.mp3")
                        if (cached.isFile && cached.length() > 0) {
                            synchronized(clips) { clips[id] = cached }
                            continue
                        }

                        val endpoint = clip.optString("endpoint")
                            .replace("{language}", language)
                            .replace("{message}", message)
                        val clipUrl = "${baseUrl.trimEnd('/')}/${endpoint.trimStart('/')}"
                        scope.launch { downloadClip(clipUrl, cached, digest, id) }
                    }
                }
            }.onFailure { Log.w(TAG, "Could not preload permission audio", it) }
        }
    }

    private fun downloadClip(url: String, destination: File, expectedSha256: String?, id: String) {
        runCatching {
            val request = Request.Builder().url(url).get().header("Accept", "audio/mpeg").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Permission clip HTTP ${response.code}")
                val body = response.body ?: error("Empty permission clip")
                val mimeType = body.contentType()?.toString()?.substringBefore(';')
                if (mimeType != "audio/mpeg") error("Unexpected permission clip type: $mimeType")
                val bytes = body.bytes()
                if (bytes.isEmpty()) error("Empty permission clip")
                if (expectedSha256 != null && sha256(bytes).equals(expectedSha256, ignoreCase = true).not()) {
                    error("Permission clip checksum mismatch")
                }
                val temporary = File(destination.parentFile, "${destination.name}.tmp")
                temporary.writeBytes(bytes)
                if (!temporary.renameTo(destination)) {
                    temporary.copyTo(destination, overwrite = true)
                    temporary.delete()
                }
                synchronized(clips) { clips[id] = destination }
            }
        }.onFailure { Log.w(TAG, "Could not cache permission clip $id", it) }
    }

    /** One-off spoken announcement with nothing to follow (first-launch picker). Device TTS only. */
    fun announce(text: String, languageCode: String) {
        speakFallback(text, languageCode) { }
    }

    /**
     * Immediately interrupts whatever is being spoken (TTS utterance, pending
     * speech, or a guidance clip). Called as soon as the user acts on a dialog
     * — e.g. picking a language on first launch must cut off the welcome
     * narration instead of letting it run to the end.
     */
    fun stop() {
        pendingTts = null
        stopAudio()
        runCatching { tts?.stop() }
    }

    /** Plays a cached clip, then launches Android's permission prompt; falls back to device TTS. */
    fun speakThen(
        text: String,
        languageCode: String,
        message: String,
        baseUrl: String,
        continueToPermission: () -> Unit
    ) {
        val clip = synchronized(clips) { clips["$languageCode/$message"] }
        if (clip != null && clip.isFile && clip.length() > 0) {
            playClip(clip, text, languageCode, continueToPermission)
        } else {
            // First-run/offline path: start preloading for next time, but never delay access.
            preload(baseUrl)
            speakFallback(text, languageCode, continueToPermission)
        }
    }

    private fun playClip(file: File, fallbackText: String, languageCode: String, onDone: () -> Unit) {
        val completed = AtomicBoolean(false)
        fun finish() {
            if (completed.compareAndSet(false, true)) onDone()
        }
        runCatching {
            stopAudio()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener { release(); mediaPlayer = null; finish() }
                setOnErrorListener { player, _, _ ->
                    player.release()
                    mediaPlayer = null
                    speakFallback(fallbackText, languageCode, ::finish)
                    true
                }
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        }.onFailure {
            mediaPlayer?.release()
            mediaPlayer = null
            speakFallback(fallbackText, languageCode, ::finish)
        }
    }

    private fun speakFallback(text: String, languageCode: String, onDone: () -> Unit) {
        val engine = tts
        if (engine == null) {
            onDone()
            return
        }
        if (!ttsReady) {
            pendingTts = {
                if (ttsReady) speakWith(engine, text, languageCode, onDone) else onDone()
            }
        } else {
            speakWith(engine, text, languageCode, onDone)
        }
    }

    private fun speakWith(engine: TextToSpeech, text: String, languageCode: String, onDone: () -> Unit) {
        val locale = Locale.forLanguageTag(languageCode)
        val status = engine.setLanguage(locale)
        if (status == TextToSpeech.LANG_MISSING_DATA || status == TextToSpeech.LANG_NOT_SUPPORTED) {
            // No voice for this language: still stop whatever is speaking so a
            // stale announcement (e.g. the first-launch welcome) cannot keep
            // talking after the user has already chosen.
            engine.stop()
            onDone()
            return
        }
        val completed = AtomicBoolean(false)
        fun finish() { if (completed.compareAndSet(false, true)) onDone() }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finish()
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish()
            override fun onError(utteranceId: String?, errorCode: Int) = finish()
        })
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "permission-${System.nanoTime()}") == TextToSpeech.ERROR) {
            finish()
        }
    }

    private fun stopAudio() {
        mediaPlayer?.runCatching { stop() }
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun shutdown() {
        stopAudio()
        pendingTts = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        scope.cancel()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "PermissionPromptAudio"
    }
}
