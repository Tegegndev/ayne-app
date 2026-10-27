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
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val onAudioReceived: (audioBytes: ByteArray, spokenText: String) -> Unit = { _, _ -> },
    private val onError: (String) -> Unit = {}
) {
    var apiUrl: String = "https://foul-crematorium-pjp6vpw46q65h665r-3000.app.github.dev/api/see-and-describe"

    // In-memory cache of the active camera frame and conversation history
    var lockedImageBase64: String? = null
        private set
    val conversationHistory = mutableListOf<ConversationTurn>()

    fun setLockedFrame(base64: String) {
        lockedImageBase64 = base64
    }

    fun clearSession() {
        lockedImageBase64 = null
        conversationHistory.clear()
    }

    /**
     * Volume+ Action: Fresh capture, clear history, describe surroundings
     */
    fun startNewSceneCapture(cameraBitmap: Bitmap, customVoice: String = "am-hamen") {
        conversationHistory.clear()
        lockedImageBase64 = compressBitmapToBase64(cameraBitmap)

        val payload = JSONObject().apply {
            put("imageBase64", lockedImageBase64)
            put("mode", "general")
            put("customVoice", customVoice)
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
        lockedImageBase64 = imageBase64

        val payload = JSONObject().apply {
            put("imageBase64", lockedImageBase64)
            put("mode", "general")
            put("customVoice", customVoice)
            put("includeAudio", true)
            put("history", JSONArray())
        }

        executeRequest(payload, isNewScene = true)
    }

    /**
     * Volume- Action: Follow-up question on the existing image without new capture
     */
    fun askFollowUpQuestion(
        userVoiceAudioBase64: String? = null,
        userTextQuery: String? = null,
        customVoice: String = "am-hamen"
    ) {
        val image = lockedImageBase64
        if (image == null) {
            onError("No active scene in memory. Press Volume+ first to take a picture.")
            return
        }

        val historyArray = JSONArray().apply {
            conversationHistory.forEach { turn ->
                put(JSONObject().apply {
                    put("role", turn.role)
                    put("text", turn.text)
                })
            }
        }

        val payload = JSONObject().apply {
            put("imageBase64", image) // REUSES existing image!
            put("history", historyArray)
            put("customVoice", customVoice)
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
                    val amharic = json.optString("amharic", "")
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
