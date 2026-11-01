package com.ayne.data

import android.media.AudioAttributes
import android.util.Base64
import com.ayne.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Client for the Addis AI Realtime "Mode B" voice turn:
 * one HTTP POST with the user's question (audio or text) and a
 * Server-Sent Events response streaming transcripts and MP3 speech chunks.
 *
 * Contract (verified in production 2026-10-30; history/image support 2026-11-01):
 *   request : {"audio": <base64 PCM16 16kHz mono> | "text": "...", "language": "am"|"om",
 *              "voice": "am-hamen"|"om-abdi", "history": [{"role","text"}...],
 *              "image": <base64 JPEG>}
 *   stream  : ready → transcript(user) → transcript(assistant) → audio* → turn_complete → done
 *   barge-in: cancel the HTTP request ([cancel]); the server aborts upstream work on close.
 */
class AddisRealtimeClient(
    private val streamUrl: String = DEFAULT_STREAM_URL,
    httpClient: OkHttpClient? = null
) {
    /** Set once the server confirms the session (after "ready"). */
    var onReady: (() -> Unit)? = null

    /**
     * Fires with the transcription of what the user said, so the UI can show
     * what the server heard ("live transcription"). [noSpeechDetected] is true
     * when the server heard nothing usable — callers should not treat the
     * (blank) text as a real turn.
     */
    var onUserTranscript: ((text: String, noSpeechDetected: Boolean) -> Unit)? = null

    /** Fires with the assistant's textual answer, when the stream includes one. */
    var onAssistantTranscript: ((String) -> Unit)? = null

    /**
     * Fires when the turn ends. [mp3Base64] is the full assistant speech
     * (aggregated MP3, base64) or null when the turn produced no audio.
     * [userText]/[assistantText] carry the turn's transcripts for history.
     */
    var onComplete: ((userText: String?, assistantText: String?, mp3Base64: String?) -> Unit)? = null

    /** Fires on network/protocol failure. Not fired after a deliberate [cancel]. */
    var onError: ((message: String) -> Unit)? = null

    private val httpClient: OkHttpClient = httpClient ?: OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)   // server fails the stream at 15s
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private val callLock = Any()
    private var activeCall: Call? = null
    private val cancelled = AtomicBoolean(false)

    /**
     * Starts a voice turn. Safe to call from any thread; callbacks arrive on
     * an OkHttp worker thread — hop to the main thread for UI work.
     *
     * [history] carries recent conversation turns (oldest first) so follow-ups
     * keep their context; the server prepends them to the LLM prompt and
     * normalizes "assistant" roles to "model".
     */
    fun sendVoiceTurn(
        audioBase64: String,
        language: String,
        voice: String? = null,
        history: List<ConversationTurn>? = null,
        image: String? = null
    ) {
        cancel() // any in-flight turn is dead the moment a new one starts
        cancelled.set(false)

        val payload = JSONObject().apply {
            put("audio", audioBase64)
            put("language", language)
            if (!voice.isNullOrBlank()) put("voice", voice)
            if (!history.isNullOrEmpty()) {
                put("history", org.json.JSONArray().apply {
                    history.forEach { turn ->
                        put(JSONObject().apply {
                            put("role", turn.role)
                            put("text", turn.text)
                        })
                    }
                })
            }
            // Current camera frame so visual questions have context.
            if (!image.isNullOrBlank()) put("image", image)
        }
        val request = Request.Builder()
            .url(streamUrl)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val call = httpClient.newCall(request)
        synchronized(callLock) { activeCall = call }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cancelled.get()) return
                onError?.invoke(e.message ?: "Realtime stream connection failed")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (cancelled.get()) return
                    if (!resp.isSuccessful) {
                        onError?.invoke("Realtime stream HTTP ${resp.code}")
                        return
                    }
                    handleStream(resp)
                }
            }
        })
    }

    /** Aborts the active turn. Cancels are silent: no error/fallback fires. */
    fun cancel() {
        cancelled.set(true)
        synchronized(callLock) {
            try {
                activeCall?.cancel()
            } catch (_: Exception) {
            }
            activeCall = null
        }
    }

    private fun handleStream(response: Response) {
        val parser = AddisRealtimeSseParser()
        val mp3Buffer = java.io.ByteArrayOutputStream()
        var userText: String? = null
        var assistantText: String? = null

        try {
            val source = response.body?.source()
                ?: throw IOException("Realtime stream returned an empty body")
            while (true) {
                val line = source.readUtf8Line() ?: break
                when (val event = parser.feed(line)) {
                    is AddisRealtimeEvent.Ready -> onReady?.invoke()
                    is AddisRealtimeEvent.Transcript -> {
                        if (event.role == "user") {
                            userText = event.text
                            onUserTranscript?.invoke(event.text, event.noSpeechDetected)
                        } else {
                            assistantText = event.text
                            onAssistantTranscript?.invoke(event.text)
                        }
                    }
                    is AddisRealtimeEvent.Audio -> {
                        val bytes = try {
                            Base64.decode(event.base64Data, Base64.DEFAULT)
                        } catch (_: IllegalArgumentException) {
                            ByteArray(0)
                        }
                        if (bytes.isNotEmpty()) mp3Buffer.write(bytes)
                    }
                    is AddisRealtimeEvent.TurnComplete -> Unit
                    is AddisRealtimeEvent.Done -> break
                    null -> Unit
                }
            }
        } catch (e: Exception) {
            if (cancelled.get()) return
            onError?.invoke(e.message ?: "Realtime stream interrupted")
            return
        }

        if (cancelled.get()) return
        val mp3Base64 = if (mp3Buffer.size() > 0) {
            Base64.encodeToString(mp3Buffer.toByteArray(), Base64.NO_WRAP)
        } else {
            null
        }
        onComplete?.invoke(userText, assistantText, mp3Base64)
    }

    companion object {
        val DEFAULT_STREAM_URL: String =
            BuildConfig.API_BASE_URL.trim().trimEnd('/') + "/api/addis-realtime/stream"
    }
}
