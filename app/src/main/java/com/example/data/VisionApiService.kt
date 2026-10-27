package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class VisionApiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    /**
     * Dedicated Spatial Surroundings Describer (Single-pass)
     * POST /api/see-and-describe
     */
    suspend fun seeAndDescribe(
        endpointUrl: String,
        imageBase64: String,
        audioBase64: String? = null,
        query: String? = null,
        history: List<ConversationTurn> = emptyList(),
        customVoice: String = "am-hamen",
        mode: String = "general",
        includeAudio: Boolean = true
    ): Result<VisionDescribeResponse> = withContext(Dispatchers.IO) {
        try {
            val jsonPayload = JSONObject().apply {
                put("imageBase64", imageBase64)
                if (!audioBase64.isNullOrBlank()) {
                    put("audioBase64", audioBase64)
                }
                if (!query.isNullOrBlank()) {
                    put("query", query)
                }
                put("customVoice", customVoice)
                put("mode", mode)
                put("includeAudio", includeAudio)

                val historyArray = org.json.JSONArray()
                history.forEach { turn ->
                    historyArray.put(JSONObject().apply {
                        put("role", turn.role)
                        put("text", turn.text)
                    })
                }
                put("history", historyArray)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonPayload.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(endpointUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                val errorMsg = "API returned HTTP ${response.code}: ${responseBody?.take(150) ?: "Empty response"}"
                return@withContext Result.failure(IOException(errorMsg))
            }

            val parsed = VisionDescribeResponse.fromJson(responseBody)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ultra-Low Latency Streaming (SSE)
     * POST /api/see-and-describe/stream
     * Events:
     * 1. event: vision -> amharic, english, visionLatencyMs (~500ms)
     * 2. event: audio_chunk -> chunk, format
     * 3. event: done
     */
    suspend fun seeAndDescribeStream(
        baseEndpointUrl: String,
        imageBase64: String,
        audioBase64: String? = null,
        query: String? = null,
        history: List<ConversationTurn> = emptyList(),
        customVoice: String = "am-hamen",
        mode: String = "general",
        onVision: (StreamVisionEvent) -> Unit,
        onAudioChunk: (StreamAudioChunkEvent) -> Unit,
        onDone: () -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val streamUrl = if (baseEndpointUrl.endsWith("/stream")) {
                baseEndpointUrl
            } else if (baseEndpointUrl.endsWith("/see-and-describe")) {
                "$baseEndpointUrl/stream"
            } else {
                "${baseEndpointUrl.trimEnd('/')}/api/see-and-describe/stream"
            }

            val jsonPayload = JSONObject().apply {
                put("imageBase64", imageBase64)
                if (!audioBase64.isNullOrBlank()) {
                    put("audioBase64", audioBase64)
                }
                if (!query.isNullOrBlank()) {
                    put("query", query)
                }
                put("customVoice", customVoice)
                put("mode", mode)
                put("includeAudio", true)

                val historyArray = org.json.JSONArray()
                history.forEach { turn ->
                    historyArray.put(JSONObject().apply {
                        put("role", turn.role)
                        put("text", turn.text)
                    })
                }
                put("history", historyArray)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonPayload.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(streamUrl)
                .post(requestBody)
                .addHeader("Accept", "text/event-stream")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("Stream HTTP ${response.code}"))
            }

            val inputStream = response.body?.byteStream()
                ?: return@withContext Result.failure(IOException("Empty stream body"))

            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                var currentEvent: String? = null
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line?.trim() ?: continue
                    if (currentLine.isEmpty()) continue

                    if (currentLine.startsWith("event:")) {
                        currentEvent = currentLine.removePrefix("event:").trim()
                    } else if (currentLine.startsWith("data:")) {
                        val dataStr = currentLine.removePrefix("data:").trim()
                        if (dataStr == "[DONE]" || currentEvent == "done") {
                            onDone()
                            break
                        }

                        when (currentEvent) {
                            "vision" -> {
                                try {
                                    val obj = JSONObject(dataStr)
                                    val amharic = obj.optString("amharic", "")
                                    val english = obj.optString("english", "")
                                    val latency = if (obj.has("visionLatencyMs")) obj.optLong("visionLatencyMs") else null
                                    onVision(StreamVisionEvent(amharic, english, latency))
                                } catch (e: Exception) {
                                    // Ignore parse error on malformed chunk
                                }
                            }
                            "audio_chunk" -> {
                                try {
                                    val obj = JSONObject(dataStr)
                                    val chunk = obj.optString("chunk", "")
                                    val format = obj.optString("format", "mp3")
                                    if (chunk.isNotEmpty()) {
                                        onAudioChunk(StreamAudioChunkEvent(chunk, format))
                                    }
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            }
                            else -> {
                                // Fallback: some servers send vision directly without named event
                                try {
                                    val obj = JSONObject(dataStr)
                                    if (obj.has("amharic")) {
                                        val amharic = obj.optString("amharic", "")
                                        val english = obj.optString("english", "")
                                        val latency = if (obj.has("visionLatencyMs")) obj.optLong("visionLatencyMs") else null
                                        onVision(StreamVisionEvent(amharic, english, latency))
                                    }
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            }
                        }
                    }
                }
            }
            onDone()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Optical Reader for Environmental Signs & Documents
     * POST /api/read-text (Alias: /api/read-text-and-signs)
     */
    suspend fun readText(
        endpointUrl: String,
        imageBase64: String,
        query: String? = null,
        audioBase64: String? = null,
        customVoice: String = "am-hamen",
        ttsProvider: String = "addis",
        preferredLanguage: String = "am",
        includeAudio: Boolean = true
    ): Result<ReadTextResponse> = withContext(Dispatchers.IO) {
        try {
            val jsonPayload = JSONObject().apply {
                put("imageBase64", imageBase64)
                if (!query.isNullOrBlank()) {
                    put("query", query)
                }
                if (!audioBase64.isNullOrBlank()) {
                    put("audioBase64", audioBase64)
                }
                put("customVoice", customVoice)
                put("ttsProvider", ttsProvider)
                put("preferredLanguage", preferredLanguage)
                put("includeAudio", includeAudio)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonPayload.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(endpointUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                val errorMsg = "API returned HTTP ${response.code}: ${responseBody?.take(150) ?: "Empty response"}"
                return@withContext Result.failure(IOException(errorMsg))
            }

            val parsed = ReadTextResponse.fromJson(responseBody)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Optical Reader Real-time Stream (SSE)
     * POST /api/read-text/stream (Alias: /api/read-text-and-signs/stream)
     * Events:
     * 1. event: ocr -> instant document card, locationContext, extracted text, isCutOff (~2.5s)
     * 2. event: audio_chunk -> real-time Addis AI base64 MP3 chunks (~3.4s for Chunk #1)
     * 3. event: done -> stream finished
     * 4. event: error -> error payload
     */
    suspend fun readTextStream(
        baseEndpointUrl: String,
        imageBase64: String,
        query: String? = null,
        audioBase64: String? = null,
        customVoice: String = "am-hamen",
        ttsProvider: String = "addis",
        preferredLanguage: String = "am",
        onOcr: (ReadTextOcrEvent) -> Unit,
        onAudioChunk: (StreamAudioChunkEvent) -> Unit,
        onDone: () -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val streamUrl = if (baseEndpointUrl.endsWith("/stream")) {
                baseEndpointUrl
            } else if (baseEndpointUrl.endsWith("/read-text") || baseEndpointUrl.endsWith("/read-text-and-signs")) {
                "$baseEndpointUrl/stream"
            } else {
                "${baseEndpointUrl.trimEnd('/')}/api/read-text/stream"
            }

            val jsonPayload = JSONObject().apply {
                put("imageBase64", imageBase64)
                if (!query.isNullOrBlank()) {
                    put("query", query)
                }
                if (!audioBase64.isNullOrBlank()) {
                    put("audioBase64", audioBase64)
                }
                put("customVoice", customVoice)
                put("ttsProvider", ttsProvider)
                put("preferredLanguage", preferredLanguage)
                put("includeAudio", true)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonPayload.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(streamUrl)
                .post(requestBody)
                .addHeader("Accept", "text/event-stream")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("ReadText stream HTTP ${response.code}"))
            }

            val inputStream = response.body?.byteStream()
                ?: return@withContext Result.failure(IOException("Empty stream body"))

            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                var currentEvent: String? = null
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line?.trim() ?: continue
                    if (currentLine.isEmpty()) continue

                    if (currentLine.startsWith("event:")) {
                        currentEvent = currentLine.removePrefix("event:").trim()
                    } else if (currentLine.startsWith("data:")) {
                        val dataStr = currentLine.removePrefix("data:").trim()
                        if (dataStr == "[DONE]" || currentEvent == "done") {
                            onDone()
                            break
                        }

                        when (currentEvent) {
                            "ocr" -> {
                                try {
                                    val ocrEvent = ReadTextOcrEvent.fromJson(dataStr)
                                    onOcr(ocrEvent)
                                } catch (e: Exception) {
                                    // Ignore parse error
                                }
                            }
                            "audio_chunk" -> {
                                try {
                                    val obj = JSONObject(dataStr)
                                    val chunk = obj.optString("chunk").ifEmpty {
                                        obj.optString("chunkBase64").ifEmpty {
                                            obj.optString("audioBase64")
                                        }
                                    }
                                    val format = obj.optString("format", "mp3")
                                    if (chunk.isNotEmpty()) {
                                        onAudioChunk(StreamAudioChunkEvent(chunk, format))
                                    }
                                } catch (e: Exception) {
                                    // Ignore parse error
                                }
                            }
                            "error" -> {
                                try {
                                    val obj = JSONObject(dataStr)
                                    val errMsg = obj.optString("error", obj.optString("message", "Stream error"))
                                    return@withContext Result.failure(IOException(errMsg))
                                } catch (e: Exception) {
                                    return@withContext Result.failure(IOException("Stream error: $dataStr"))
                                }
                            }
                            else -> {
                                try {
                                    val obj = JSONObject(dataStr)
                                    if (obj.has("extractedText") || obj.has("spokenAmharic") || obj.has("signType") || obj.has("amharic")) {
                                        val ocrEvent = ReadTextOcrEvent.fromJson(dataStr)
                                        onOcr(ocrEvent)
                                    } else if (obj.has("chunk") || obj.has("chunkBase64")) {
                                        val chunk = obj.optString("chunk", obj.optString("chunkBase64"))
                                        val format = obj.optString("format", "mp3")
                                        if (chunk.isNotEmpty()) {
                                            onAudioChunk(StreamAudioChunkEvent(chunk, format))
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            }
                        }
                    }
                }
            }
            onDone()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
