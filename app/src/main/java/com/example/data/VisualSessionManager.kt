package com.example.data

import android.graphics.Bitmap
import android.util.Base64
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException

class VisualSessionManager(
    private val httpClient: OkHttpClient = HttpClientProvider.okHttpClient,
    private val onAudioReceived: (audioBytes: ByteArray, spokenText: String) -> Unit = { _, _ -> },
    private val onError: (String) -> Unit = {}
) {
    var apiUrl: String = "https://ayne-roan.vercel.app/api/see-and-describe"

    // Server-cached visual session ID for zero-upload follow-up turns
    var activeSessionId: String? = null
        private set

    // In-memory cache of the active camera frame and conversation history
    var lockedImageBase64: String? = null
        private set
    val conversationHistory = mutableListOf<ConversationTurn>()

    fun setLockedFrame(base64: String) {
        lockedImageBase64 = base64
    }

    fun setActiveSessionId(sessionId: String?) {
        activeSessionId = sessionId
    }

    fun clearSession() {
        activeSessionId = null
        lockedImageBase64 = null
        conversationHistory.clear()
    }

    /**
     * Volume+ Action: Fresh capture, clear history, describe surroundings
     */
    fun startNewSceneCapture(cameraBitmap: Bitmap, customVoice: String = "am-hamen") {
        conversationHistory.clear()
        activeSessionId = null
        lockedImageBase64 = compressBitmapToBase64(cameraBitmap)

        val payload = JSONObject().apply {
            put("imageBase64", lockedImageBase64)
            put("mode", "general")
            put("customVoice", customVoice)
            put("ttsProvider", "addis")
            put("includeAudio", true)
            put("history", JSONArray())
        }

        executeRequest(payload, isNewScene = true)
    }

    /**
     * Overload for when base64 is already captured
     */
    fun startNewScene(imageBase64: String, customVoice: String = "am-hamen") {
        conversationHistory.clear()
        activeSessionId = null
        lockedImageBase64 = imageBase64

        val payload = JSONObject().apply {
            put("imageBase64", lockedImageBase64)
            put("mode", "general")
            put("customVoice", customVoice)
            put("ttsProvider", "addis")
            put("includeAudio", true)
            put("history", JSONArray())
        }

        executeRequest(payload, isNewScene = true)
    }

    /**
     * Volume- Action: Follow-up question on the existing image without new capture
     * Turn 2+: Zero-Upload visual session caching (Sends ONLY sessionId, 0 image bytes!)
     */
    fun askFollowUpQuestion(
        userVoiceAudioBase64: String? = null,
        userTextQuery: String? = null,
        customVoice: String = "am-hamen"
    ) {
        val currentSessionId = activeSessionId
        val image = lockedImageBase64
        if (currentSessionId == null && image == null) {
            onError("No active scene in memory. Press Volume+ first to take a picture.")
            return
        }

        val payload = JSONObject().apply {
            // Zero-Upload: If sessionId is cached, send ONLY sessionId / chatId! 0 image bytes!
            if (currentSessionId != null) {
                put("sessionId", currentSessionId)
                put("chatId", currentSessionId)
            } else {
                put("imageBase64", image)
                val historyArray = JSONArray().apply {
                    conversationHistory.takeLast(6).forEach { turn ->
                        put(JSONObject().apply {
                            put("role", turn.role)
                            put("text", turn.text)
                        })
                    }
                }
                put("history", historyArray)
            }
            put("customVoice", customVoice)
            put("ttsProvider", "addis")
            put("includeAudio", true)
            if (!userVoiceAudioBase64.isNullOrEmpty()) put("audioBase64", userVoiceAudioBase64)
            if (!userTextQuery.isNullOrEmpty()) put("query", userTextQuery)
        }

        val promptText = userTextQuery ?: "Voice follow-up"
        executeRequest(payload, isNewScene = false, userQuestion = promptText)
    }

    private fun executeRequest(payload: JSONObject, isNewScene: Boolean, userQuestion: String? = null) {
        val request = Request.Builder()
            .url(apiUrl)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                if (!response.isSuccessful) {
                    onError("API error ${response.code}")
                    return
                }

                try {
                    val json = JSONObject(body)
                    // Cache sessionId / chatId for subsequent zero-upload follow-up turns
                    val sid = json.optString("sessionId").ifEmpty { json.optString("chatId") }.takeIf { it.isNotBlank() && it != "null" }
                    if (sid != null) {
                        activeSessionId = sid
                    }

                    val rawCleanSpeech = json.optString("cleanSpeech", "")
                    val rawAmharic = json.optString("amharic", "")
                    val rawField = json.optString("raw", "")
                    val amharic = rawCleanSpeech.cleanLabelPrefix("am").ifEmpty {
                        rawAmharic.cleanLabelPrefix("am").ifEmpty { rawField.cleanLabelPrefix("am") }
                    }
                    val transcribed = json.optString("transcribedQuery", "").takeIf { it.isNotBlank() }
                    val audioBase64 = json.optJSONObject("audio")?.optString("audioBase64")

                    // Update rolling history in memory
                    if (isNewScene) {
                        conversationHistory.add(ConversationTurn("user", "Describe my surroundings"))
                    } else {
                        val prompt = transcribed ?: userQuestion ?: "Voice follow-up"
                        conversationHistory.add(ConversationTurn("user", prompt))
                    }
                    if (amharic.isNotBlank()) {
                        conversationHistory.add(ConversationTurn("model", amharic))
                    }

                    // Pass audio bytes to player
                    if (!audioBase64.isNullOrEmpty()) {
                        val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
                        onAudioReceived(audioBytes, amharic)
                    }
                } catch (e: Exception) {
                    onError("Failed to parse response: ${e.message}")
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                onError("Network error: ${e.message}")
            }
        })
    }

    private fun compressBitmapToBase64(bitmap: Bitmap): String {
        val maxDim = 800
        val ratio = Math.min(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
        val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 65, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
