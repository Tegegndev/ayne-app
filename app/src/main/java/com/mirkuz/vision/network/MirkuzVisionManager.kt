package com.mirkuz.vision.network

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Base64
import com.example.data.cleanLabelPrefix
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Production manager for Mirkuz Vision / Ayne assistive technology.
 *
 * Implements:
 * 1. Addis AI Realtime Streaming (SSE) for Read Text (ጽሑፍ አንብብ) with ~716ms first audio chunk.
 * 2. Zero-Upload Visual Session Caching for See & Describe (እይ እና ግለጽ) with ~120B follow-ups.
 * 3. 0ms-budget physical feedback and barge-in audio control.
 */
class MirkuzVisionManager(
    private val context: Context,
    private val serverBaseUrl: String,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    private var activeSessionId: String? = null
    private var mediaPlayer: MediaPlayer? = null
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun getActiveSessionId(): String? = activeSessionId

    fun setActiveSessionId(sessionId: String?) {
        activeSessionId = sessionId
    }

    // ========================================================
    // ZERO-LATENCY PHYSICAL FEEDBACK & BARGE-IN
    // ========================================================
    fun stopSpeechAndFeedback() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
        } catch (_: Exception) {}
    }

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(40)
            }
        } catch (_: Exception) {}
    }

    fun resetSession() {
        activeSessionId = null
    }

    // Alias for compatibility with backend developer guide
    fun stopSpeech() = stopSpeechAndFeedback()

    // ========================================================
    // 1. READ TEXT STREAMING (Addis AI Realtime Stream + SSE)
    // ========================================================
    fun readTextStream(
        compressedJpegBase64: String,
        onOcrReady: (signType: String, text: String, isCutOff: Boolean, guidance: String?) -> Unit,
        onFirstAudioPlay: () -> Unit,
        onError: (String) -> Unit
    ) {
        startReadTextStream(
            compressedJpegBase64 = compressedJpegBase64,
            userInstruction = null,
            onOcrReceived = { signType, _, text, isCutOff, guidance ->
                onOcrReady(signType, text, isCutOff, guidance)
            },
            onFirstAudioPlay = onFirstAudioPlay,
            onError = onError
        )
    }

    fun startReadTextStream(
        compressedJpegBase64: String,
        userInstruction: String? = null,
        onOcrReceived: (title: String, location: String, text: String, isCutOff: Boolean, guidance: String?) -> Unit,
        onFirstAudioPlay: () -> Unit,
        onError: (String) -> Unit
    ) {
        stopSpeechAndFeedback()
        triggerHaptic()

        val payload = JSONObject().apply {
            put("imageBase64", compressedJpegBase64)
            put("ttsProvider", "addis")
            put("customVoice", "am-hamen")
            put("preferredLanguage", "am")
            put("includeAudio", true)
            if (!userInstruction.isNullOrBlank()) {
                put("instruction", userInstruction)
            }
        }

        val request = Request.Builder()
            .url("${serverBaseUrl.trimEnd('/')}/api/read-text/stream")
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val tempAudioFile = File.createTempFile("mirkuz_read_stream", ".mp3", context.cacheDir)
        val audioOutputStream = FileOutputStream(tempAudioFile)
        var hasStartedAudioPlayback = false

        val sseFactory = EventSources.createFactory(okHttpClient)
        sseFactory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                when (type) {
                    "ocr" -> {
                        try {
                            val json = JSONObject(data)
                            // Save sessionId if returned
                            val sid = json.optString("sessionId").ifEmpty { json.optString("chatId") }
                            if (sid.isNotBlank() && sid != "null") {
                                activeSessionId = sid
                            }

                            val signType = json.optString("signType", "ምልክት").cleanPrefix()
                            val location = json.optString("locationContext", "").cleanPrefix()
                            val extracted = json.optString("extractedText", "").cleanPrefix()
                            val isCutOff = json.optBoolean("isCutOff", false)
                            val guidance = if (json.isNull("cameraGuidance")) {
                                null
                            } else {
                                json.optString("cameraGuidance").takeIf { it.isNotBlank() && it != "null" }?.cleanPrefix()
                            }

                            onOcrReceived(signType, location, extracted, isCutOff, guidance)
                        } catch (e: Exception) {
                            onError("Failed to parse OCR event: ${e.message}")
                        }
                    }
                    "audio_chunk" -> {
                        try {
                            val json = JSONObject(data)
                            val chunkStr = json.optString("chunk").ifEmpty {
                                json.optString("chunkBase64").ifEmpty {
                                    json.optString("audioBase64")
                                }
                            }
                            if (chunkStr.isNotEmpty()) {
                                val chunkBytes = Base64.decode(chunkStr, Base64.DEFAULT)
                                audioOutputStream.write(chunkBytes)
                                audioOutputStream.flush()

                                // Trigger playback on the first chunk (~716ms after OCR)
                                if (!hasStartedAudioPlayback) {
                                    hasStartedAudioPlayback = true
                                    onFirstAudioPlay()
                                    playAudioFile(tempAudioFile)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    "done" -> {
                        try {
                            audioOutputStream.close()
                        } catch (_: Exception) {}
                    }
                    "error" -> {
                        try {
                            audioOutputStream.close()
                        } catch (_: Exception) {}
                        val errMsg = try {
                            JSONObject(data).optString("error", "Stream error")
                        } catch (_: Exception) {
                            "Stream error: $data"
                        }
                        onError(errMsg)
                    }
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                try {
                    audioOutputStream.close()
                } catch (_: Exception) {}
                onError(t?.message ?: "SSE Connection failed")
            }
        })
    }

    // ========================================================
    // 2. SEE & DESCRIBE (Turn 1 Snapshot + Turn 2+ Zero-Upload Follow-Up)
    // ========================================================
    fun seeAndDescribe(
        newSnapshotBase64: String? = null,
        followUpQuestion: String? = null,
        preferredLanguage: String = "am",
        onSuccess: (spokenAmharic: String, englishReasoning: String) -> Unit,
        onError: (String) -> Unit
    ) {
        stopSpeechAndFeedback()
        triggerHaptic()

        // Check if user spoke a session reset keyword
        val cleanQuery = followUpQuestion?.trim()?.lowercase() ?: ""
        if (cleanQuery.contains("አዲስ ጀምር") || cleanQuery.contains("start over") || cleanQuery.contains("reset")) {
            resetSession()
            onSuccess("አዲስ ውይይት ተጀምሯል። እባክዎ አዲስ ፎቶ ያንሱ።", "Session reset.")
            return
        }

        val payload = JSONObject().apply {
            // TURN 2+: Send ONLY sessionId (ZERO image bytes!)
            if (activeSessionId != null && newSnapshotBase64 == null) {
                put("sessionId", activeSessionId)
                put("chatId", activeSessionId)
            } else if (newSnapshotBase64 != null) {
                // TURN 1: Initial photo upload
                put("imageBase64", newSnapshotBase64)
            }

            if (!followUpQuestion.isNullOrBlank()) {
                put("query", followUpQuestion)
            }
            put("preferredLanguage", preferredLanguage)
            put("customVoice", if (preferredLanguage == "en") "Zephyr" else "am-hamen")
            put("ttsProvider", if (preferredLanguage == "en") "gemini" else "addis")
            put("includeAudio", true)
        }

        val requestUrl = when (preferredLanguage) {
            "om" -> "${serverBaseUrl.trimEnd('/')}/api/see-and-describe/om"
            "am" -> "${serverBaseUrl.trimEnd('/')}/api/see-and-describe/am"
            else -> "${serverBaseUrl.trimEnd('/')}/api/see-and-describe"
        }

        val request = Request.Builder()
            .url(requestUrl)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: return onError("Empty response")
                try {
                    val json = JSONObject(bodyStr)

                    if (response.isSuccessful && json.optBoolean("success", true)) {
                        // Update sessionId (e.g. "vis_sess_8f92a10b4c")
                        val sid = json.optString("sessionId").ifEmpty { json.optString("chatId") }
                        if (sid.isNotBlank() && sid != "null") {
                            activeSessionId = sid
                        }

                        val amharicRaw = if (json.has("cleanSpeech")) {
                            json.optString("cleanSpeech")
                        } else {
                            json.optString("amharic").ifEmpty {
                                json.optString("description").ifEmpty {
                                    json.optString("text", "")
                                }
                            }
                        }
                        val amharicSpeech = amharicRaw.cleanPrefix(preferredLanguage)
                        val englishReasoning = json.optString("english").ifEmpty {
                            json.optString("englishTranslation").ifEmpty {
                                json.optString("translation", "")
                            }
                        }
                        val audioBase64 = json.optJSONObject("audio")?.optString("audioBase64")

                        if (!audioBase64.isNullOrEmpty()) {
                            playBase64Audio(audioBase64)
                        }

                        onSuccess(amharicSpeech, englishReasoning)
                    } else {
                        val errorMsg = json.optString("error", "Error processing request")
                        // If session expired, reset state so next click forces a snapshot
                        if (errorMsg.contains("Session expired", ignoreCase = true)) {
                            resetSession()
                        }
                        onError(errorMsg)
                    }
                } catch (e: Exception) {
                    onError("Failed to parse response: ${e.message}")
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                onError(e.message ?: "Network error")
            }
        })
    }

    // Overload matching backend report signature with audioBase64 in callback
    fun seeAndDescribe(
        newSnapshotBase64: String? = null,
        userQuery: String? = null,
        preferredLanguage: String = "am",
        onSuccess: (amharic: String, english: String, audioBase64: String?) -> Unit,
        onError: (String) -> Unit
    ) {
        stopSpeechAndFeedback()
        triggerHaptic()

        val cleanQuery = userQuery?.trim()?.lowercase() ?: ""
        if (cleanQuery.contains("አዲስ ጀምር") || cleanQuery.contains("start over") || cleanQuery.contains("reset")) {
            resetSession()
            onSuccess("አዲስ ውይይት ተጀምሯል። እባክዎ አዲስ ፎቶ ያንሱ።", "Session reset.", null)
            return
        }

        val payload = JSONObject().apply {
            if (activeSessionId != null && newSnapshotBase64 == null) {
                put("sessionId", activeSessionId)
                put("chatId", activeSessionId)
            } else if (newSnapshotBase64 != null) {
                put("imageBase64", newSnapshotBase64)
            }

            if (!userQuery.isNullOrBlank()) {
                put("query", userQuery)
            }
            put("preferredLanguage", preferredLanguage)
            put("customVoice", if (preferredLanguage == "en") "Zephyr" else "am-hamen")
            put("ttsProvider", if (preferredLanguage == "en") "gemini" else "addis")
            put("includeAudio", true)
        }

        val requestUrl = when (preferredLanguage) {
            "om" -> "${serverBaseUrl.trimEnd('/')}/api/see-and-describe/om"
            "am" -> "${serverBaseUrl.trimEnd('/')}/api/see-and-describe/am"
            else -> "${serverBaseUrl.trimEnd('/')}/api/see-and-describe"
        }

        val request = Request.Builder()
            .url(requestUrl)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: return onError("Empty response")
                try {
                    val json = JSONObject(bodyStr)

                    if (response.isSuccessful && json.optBoolean("success", true)) {
                        val sid = json.optString("sessionId").ifEmpty { json.optString("chatId") }
                        if (sid.isNotBlank() && sid != "null") {
                            activeSessionId = sid
                        }

                        val amharicRaw = if (json.has("cleanSpeech")) {
                            json.optString("cleanSpeech")
                        } else {
                            json.optString("amharic").ifEmpty {
                                json.optString("description").ifEmpty {
                                    json.optString("text", "")
                                }
                            }
                        }
                        val amharicSpeech = amharicRaw.cleanPrefix(preferredLanguage)
                        val englishReasoning = json.optString("english").ifEmpty {
                            json.optString("englishTranslation").ifEmpty {
                                json.optString("translation", "")
                            }
                        }
                        val audioBase64 = json.optJSONObject("audio")?.optString("audioBase64")

                        if (!audioBase64.isNullOrEmpty()) {
                            playBase64Audio(audioBase64)
                        }

                        onSuccess(amharicSpeech, englishReasoning, audioBase64)
                    } else {
                        val errorMsg = json.optString("error", "Error processing request")
                        if (errorMsg.contains("Session expired", ignoreCase = true)) {
                            resetSession()
                        }
                        onError(errorMsg)
                    }
                } catch (e: Exception) {
                    onError("Failed to parse response: ${e.message}")
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                onError(e.message ?: "Network error")
            }
        })
    }

    // ========================================================
    // AUDIO PLAYBACK HELPERS
    // ========================================================
    fun playAudioFile(file: File) {
        try {
            stopSpeechAndFeedback()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
        } catch (_: Exception) {}
    }

    fun playBase64Audio(base64Audio: String) {
        try {
            val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
            val tempFile = File.createTempFile("mirkuz_speech", ".mp3", context.cacheDir)
            FileOutputStream(tempFile).use { it.write(audioBytes) }
            playAudioFile(tempFile)
        } catch (_: Exception) {}
    }

    private fun String.cleanPrefix(preferredLanguage: String? = null): String {
        return this.cleanLabelPrefix(preferredLanguage)
    }
}

/**
 * Typealias for compatibility with backend report naming conventions.
 */
typealias MirkuzVisionRepository = MirkuzVisionManager

