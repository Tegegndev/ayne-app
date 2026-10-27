package com.example.data

import android.media.MediaPlayer
import android.util.Base64
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

class MirkuzVisionRepository(
    private val serverBaseUrl: String,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    private var activeSessionId: String? = null
    private var currentPlayer: MediaPlayer? = null

    fun getActiveSessionId(): String? = activeSessionId

    fun setActiveSessionId(sessionId: String?) {
        activeSessionId = sessionId
    }

    fun stopCurrentSpeech() {
        try {
            currentPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            currentPlayer = null
        } catch (_: Exception) {}
    }

    // ==========================================
    // 1. READ TEXT STREAMING (SSE + Addis AI Realtime)
    // ==========================================
    fun readTextStream(
        jpegBase64: String,
        onOcrMeta: (JSONObject) -> Unit,
        onFirstAudioPlay: () -> Unit,
        onError: (String) -> Unit
    ) {
        stopCurrentSpeech()

        val json = JSONObject().apply {
            put("imageBase64", jpegBase64)
            put("ttsProvider", "addis")
            put("customVoice", "am-hamen")
            put("preferredLanguage", "am")
            put("includeAudio", true)
        }

        val request = Request.Builder()
            .url("${serverBaseUrl.trimEnd('/')}/api/read-text/stream")
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val tempAudioFile = File.createTempFile("mirkuz_read_stream", ".mp3")
        val audioStream = FileOutputStream(tempAudioFile)
        var playbackStarted = false

        val sseFactory = EventSources.createFactory(okHttpClient)
        sseFactory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                when (type) {
                    "ocr" -> {
                        try {
                            val ocrData = JSONObject(data)
                            onOcrMeta(ocrData)
                        } catch (e: Exception) {
                            onError("Failed to parse OCR event: ${e.message}")
                        }
                    }
                    "audio_chunk" -> {
                        try {
                            val chunkObj = JSONObject(data)
                            val chunkStr = chunkObj.optString("chunk").ifEmpty {
                                chunkObj.optString("chunkBase64").ifEmpty {
                                    chunkObj.optString("audioBase64")
                                }
                            }
                            if (chunkStr.isNotEmpty()) {
                                val audioBytes = Base64.decode(chunkStr, Base64.DEFAULT)
                                audioStream.write(audioBytes)
                                audioStream.flush()

                                // Start speaking on the first chunk (~716ms after OCR)
                                if (!playbackStarted) {
                                    playbackStarted = true
                                    onFirstAudioPlay()
                                    playFile(tempAudioFile)
                                }
                            }
                        } catch (e: Exception) {
                            // Non-fatal chunk error
                        }
                    }
                    "done" -> {
                        try {
                            audioStream.close()
                        } catch (_: Exception) {}
                    }
                    "error" -> {
                        try {
                            audioStream.close()
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
                    audioStream.close()
                } catch (_: Exception) {}
                onError(t?.message ?: "SSE Connection failed")
            }
        })
    }

    // ==========================================
    // 2. SEE & DESCRIBE WITH ZERO-UPLOAD FOLLOW-UP
    // ==========================================
    fun seeAndDescribe(
        newImageBase64: String? = null,
        userQuestion: String? = null,
        preferredLanguage: String = "am",
        onSuccess: (amharic: String, audioBase64: String?) -> Unit,
        onError: (String) -> Unit
    ) {
        stopCurrentSpeech()

        val json = JSONObject().apply {
            // If we have an active session and no new image was captured, send ONLY sessionId / chatId!
            if (activeSessionId != null && newImageBase64 == null) {
                put("sessionId", activeSessionId)
                put("chatId", activeSessionId)
            } else if (newImageBase64 != null) {
                put("imageBase64", newImageBase64)
            }
            if (!userQuestion.isNullOrBlank()) {
                put("query", userQuestion)
            }
            put("preferredLanguage", preferredLanguage)
            put("customVoice", if (preferredLanguage == "en") "Zephyr" else "am-hamen")
            put("ttsProvider", if (preferredLanguage == "en") "gemini" else "addis")
            put("includeAudio", true)
        }

        val request = Request.Builder()
            .url("${serverBaseUrl.trimEnd('/')}/api/see-and-describe")
            .header("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: return onError("Empty response")
                try {
                    val resObj = JSONObject(bodyStr)
                    if (resObj.optBoolean("success", true)) {
                        // Cache sessionId / chatId for subsequent follow-up turns
                        val sid = resObj.optString("sessionId").ifEmpty { resObj.optString("chatId") }.takeIf { it.isNotBlank() && it != "null" }
                        if (sid != null) {
                            activeSessionId = sid
                        }

                        val amharicText = resObj.optString("amharic").cleanLabelPrefix()
                        val audioBase64 = resObj.optJSONObject("audio")?.optString("audioBase64")

                        if (!audioBase64.isNullOrEmpty()) {
                            playBase64(audioBase64)
                        }
                        onSuccess(amharicText, audioBase64)
                    } else {
                        onError(resObj.optString("error", "Request failed"))
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

    fun resetVisualSession() {
        activeSessionId = null
    }

    private fun playFile(file: File) {
        try {
            stopCurrentSpeech()
            currentPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
        } catch (_: Exception) {}
    }

    private fun playBase64(b64: String) {
        try {
            val audioBytes = Base64.decode(b64, Base64.DEFAULT)
            val tempFile = File.createTempFile("mirkuz_speech", ".mp3")
            FileOutputStream(tempFile).use { it.write(audioBytes) }
            playFile(tempFile)
        } catch (_: Exception) {}
    }
}
