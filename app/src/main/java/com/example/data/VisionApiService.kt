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
     * Supports Turn 1 (with image) and Turn 2+ (Zero-Upload with sessionId only)
     */
    suspend fun seeAndDescribe(
        endpointUrl: String,
        imageBase64: String? = null,
        sessionId: String? = null,
        audioBase64: String? = null,
        query: String? = null,
        history: List<ConversationTurn> = emptyList(),
        customVoice: String = "am-hamen",
        ttsProvider: String = "addis",
        preferredLanguage: String = "am",
        mode: String = "general",
        includeAudio: Boolean = true
    ): Result<VisionDescribeResponse> = withContext(Dispatchers.IO) {
        try {
            val jsonPayload = JSONObject().apply {
                if (!sessionId.isNullOrBlank() && imageBase64.isNullOrBlank()) {
                    // Turn 2+: Zero-Upload follow-up! Send ONLY sessionId / chatId
                    put("sessionId", sessionId)
                    put("chatId", sessionId)
                } else if (!imageBase64.isNullOrBlank()) {
                    put("imageBase64", imageBase64)
                    if (!sessionId.isNullOrBlank()) {
                        put("sessionId", sessionId)
                        put("chatId", sessionId)
                    }
                } else if (!sessionId.isNullOrBlank()) {
                    put("sessionId", sessionId)
                    put("chatId", sessionId)
                }

                if (!audioBase64.isNullOrBlank()) {
                    put("audioBase64", audioBase64)
                }
                if (!query.isNullOrBlank()) {
                    put("query", query)
                }
                put("customVoice", customVoice)
                put("ttsProvider", ttsProvider)
                put("preferredLanguage", preferredLanguage)
                put("mode", mode)
                put("includeAudio", includeAudio)

                // Only send client history if there is NO active sessionId.
                // When sessionId is present, the server ALREADY caches the visual session and prior conversation.
                // Sending client history alongside sessionId causes duplicate turns on the server,
                // bloating LLM context and degrading response latency after 2+ turns.
                if (sessionId.isNullOrBlank() && history.isNotEmpty()) {
                    val historyArray = org.json.JSONArray()
                    history.takeLast(6).forEach { turn ->
                        historyArray.put(JSONObject().apply {
                            put("role", turn.role)
                            put("text", turn.text)
                        })
                    }
                    put("history", historyArray)
                }
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
     * 1. event: vision -> amharic, english, visionLatencyMs, sessionId
     * 2. event: audio_chunk -> chunk, format
     * 3. event: done
     */
    suspend fun seeAndDescribeStream(
        baseEndpointUrl: String,
        imageBase64: String? = null,
        sessionId: String? = null,
        audioBase64: String? = null,
        query: String? = null,
        history: List<ConversationTurn> = emptyList(),
        customVoice: String = "am-hamen",
        ttsProvider: String = "addis",
        preferredLanguage: String = "am",
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
                if (!sessionId.isNullOrBlank() && imageBase64.isNullOrBlank()) {
                    // Turn 2+: Zero-Upload follow-up! Send ONLY sessionId / chatId
                    put("sessionId", sessionId)
                    put("chatId", sessionId)
                } else if (!imageBase64.isNullOrBlank()) {
                    put("imageBase64", imageBase64)
                    if (!sessionId.isNullOrBlank()) {
                        put("sessionId", sessionId)
                        put("chatId", sessionId)
                    }
                } else if (!sessionId.isNullOrBlank()) {
                    put("sessionId", sessionId)
                    put("chatId", sessionId)
                }

                if (!audioBase64.isNullOrBlank()) {
                    put("audioBase64", audioBase64)
                }
                if (!query.isNullOrBlank()) {
                    put("query", query)
                }
                put("customVoice", customVoice)
                put("ttsProvider", ttsProvider)
                put("preferredLanguage", preferredLanguage)
                put("mode", mode)
                put("includeAudio", true)

                // Only send client history if there is NO active sessionId
                if (sessionId.isNullOrBlank() && history.isNotEmpty()) {
                    val historyArray = org.json.JSONArray()
                    history.takeLast(6).forEach { turn ->
                        historyArray.put(JSONObject().apply {
                            put("role", turn.role)
                            put("text", turn.text)
                        })
                    }
                    put("history", historyArray)
                }
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
                                    val rawField = obj.optText("raw")
                                    val rawAmharic = obj.optText("amharic")
                                    val amharic = rawAmharic.cleanLabelPrefix("am").ifEmpty { rawField.cleanLabelPrefix("am") }
                                    val rawOromo = obj.optText("oromo").ifEmpty { obj.optText("afaanOromoo") }
                                    val oromo = rawOromo.cleanLabelPrefix("om").ifEmpty { rawField.cleanLabelPrefix("om") }
                                    val rawEnglish = obj.optText("english")
                                    val english = rawEnglish.cleanLabelPrefix("en").ifEmpty { rawField.cleanLabelPrefix("en") }
                                    val latency = if (obj.has("visionLatencyMs")) obj.optLong("visionLatencyMs") else null
                                    val sid = obj.optString("sessionId").takeIf { it.isNotBlank() && it != "null" }
                                    onVision(StreamVisionEvent(amharic = amharic, english = english, oromo = oromo, visionLatencyMs = latency, sessionId = sid))
                                } catch (e: Exception) {
                                    // Ignore parse error on malformed chunk
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
                                    // Ignore
                                }
                            }
                            else -> {
                                // Fallback: some servers send vision directly without named event
                                try {
                                    val obj = JSONObject(dataStr)
                                    if (obj.has("amharic") || obj.has("oromo") || obj.has("english") || obj.has("raw")) {
                                        val rawField = obj.optText("raw")
                                        val rawAmharic = obj.optText("amharic")
                                        val amharic = rawAmharic.cleanLabelPrefix("am").ifEmpty { rawField.cleanLabelPrefix("am") }
                                        val rawOromo = obj.optText("oromo").ifEmpty { obj.optText("afaanOromoo") }
                                        val oromo = rawOromo.cleanLabelPrefix("om").ifEmpty { rawField.cleanLabelPrefix("om") }
                                        val rawEnglish = obj.optText("english")
                                        val english = rawEnglish.cleanLabelPrefix("en").ifEmpty { rawField.cleanLabelPrefix("en") }
                                        val latency = if (obj.has("visionLatencyMs")) obj.optLong("visionLatencyMs") else null
                                        val sid = obj.optString("sessionId").takeIf { it.isNotBlank() && it != "null" }
                                        onVision(StreamVisionEvent(amharic = amharic, english = english, oromo = oromo, visionLatencyMs = latency, sessionId = sid))
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
     * 2. event: audio_chunk -> real-time Addis AI base64 MP3 chunks (~716ms for Chunk #1)
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

    /**
     * Dedicated Real-Time Visual Q&A Stream (SSE)
     * POST /api/ask-question/stream
     * Events:
     * 1. event: transcription -> user's transcribed Amharic/English voice query (~300ms)
     * 2. event: answer -> Gemini visual reasoning answer (~700ms)
     * 3. event: audio_chunk -> streaming Addis AI MP3 chunks (~1.0s – 1.2s for first chunk)
     * 4. event: done -> turn count, total latency (~1.8s)
     * 5. event: error -> error code and message (e.g. SESSION_EXPIRED, INADMISSIBLE_AUDIO)
     */
    suspend fun askQuestionStream(
        baseEndpointUrl: String,
        sessionId: String?,
        audioBase64: String? = null,
        query: String? = null,
        imageBase64: String? = null,
        customVoice: String = "am-hamen",
        ttsProvider: String = "addis",
        preferredLanguage: String = "am",
        includeAudio: Boolean = true,
        onTranscription: (AskQuestionTranscriptionEvent) -> Unit,
        onAnswer: (AskQuestionAnswerEvent) -> Unit,
        onAudioChunk: (StreamAudioChunkEvent) -> Unit,
        onErrorEvent: (code: String, message: String) -> Unit,
        onDone: () -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val streamUrl = if (preferredLanguage == "om") {
                if (baseEndpointUrl.endsWith("/stream")) {
                    baseEndpointUrl
                } else if (baseEndpointUrl.endsWith("/ask-question/om")) {
                    "$baseEndpointUrl/stream"
                } else if (baseEndpointUrl.endsWith("/ask-question")) {
                    "$baseEndpointUrl/om/stream"
                } else if (baseEndpointUrl.endsWith("/see-and-describe")) {
                    baseEndpointUrl.replace("see-and-describe", "ask-question/om") + "/stream"
                } else {
                    "${baseEndpointUrl.trimEnd('/')}/api/ask-question/om/stream"
                }
            } else {
                if (baseEndpointUrl.endsWith("/stream")) {
                    baseEndpointUrl
                } else if (baseEndpointUrl.endsWith("/ask-question")) {
                    "$baseEndpointUrl/stream"
                } else if (baseEndpointUrl.endsWith("/see-and-describe")) {
                    baseEndpointUrl.replace("see-and-describe", "ask-question") + "/stream"
                } else {
                    "${baseEndpointUrl.trimEnd('/')}/api/ask-question/stream"
                }
            }

            val jsonPayload = JSONObject().apply {
                if (!sessionId.isNullOrBlank()) {
                    put("sessionId", sessionId)
                }
                if (!audioBase64.isNullOrBlank()) {
                    put("audioBase64", audioBase64)
                }
                if (!query.isNullOrBlank()) {
                    put("query", query)
                }
                if (!imageBase64.isNullOrBlank()) {
                    put("imageBase64", imageBase64)
                }
                put("customVoice", customVoice)
                put("ttsProvider", ttsProvider)
                put("preferredLanguage", preferredLanguage)
                put("includeAudio", includeAudio)
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
                val errBody = response.body?.string().orEmpty()
                try {
                    val errJson = JSONObject(errBody)
                    val code = errJson.optString("code", "HTTP_${response.code}")
                    val msg = errJson.optString("message", errBody)
                    onErrorEvent(code, msg)
                } catch (e: Exception) {
                    if (response.code == 404) {
                        val msg = if (preferredLanguage == "om") {
                            "Yeroon suuraa darbeera. Maaloo suuraa haaraa kaasaa."
                        } else {
                            "Session expired in server cache."
                        }
                        onErrorEvent("SESSION_EXPIRED", msg)
                    } else if (response.code == 422) {
                        val msg = if (preferredLanguage == "om") {
                            "Sagaleen keessan hin dhaga'amne; maaloo lammata dubbadhaa."
                        } else {
                            "ድምፅዎ አልተሰማም፤ እባክዎ እንደገና ይናገሩ"
                        }
                        onErrorEvent("INADMISSIBLE_AUDIO", msg)
                    } else {
                        onErrorEvent("HTTP_${response.code}", errBody.ifEmpty { "HTTP ${response.code}" })
                    }
                }
                return@withContext Result.failure(IOException("AskQuestion stream HTTP ${response.code}"))
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
                            "transcription" -> {
                                try {
                                    val ev = AskQuestionTranscriptionEvent.fromJson(dataStr)
                                    onTranscription(ev)
                                } catch (e: Exception) {
                                    // Ignore parse error
                                }
                            }
                            "answer" -> {
                                try {
                                    val ev = AskQuestionAnswerEvent.fromJson(dataStr)
                                    onAnswer(ev)
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
                                    // Ignore
                                }
                            }
                            "error" -> {
                                try {
                                    val obj = JSONObject(dataStr)
                                    val code = obj.optString("code", "ERROR")
                                    val msg = obj.optString("message", obj.optString("error", dataStr))
                                    onErrorEvent(code, msg)
                                } catch (e: Exception) {
                                    onErrorEvent("ERROR", dataStr)
                                }
                            }
                            else -> {
                                try {
                                    val obj = JSONObject(dataStr)
                                    if (obj.has("transcribedQuery")) {
                                        onTranscription(AskQuestionTranscriptionEvent.fromJson(dataStr))
                                    } else if (obj.has("amharic") || obj.has("english")) {
                                        onAnswer(AskQuestionAnswerEvent.fromJson(dataStr))
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

    /**
     * Dedicated Buffered Fallback Visual Q&A (JSON)
     * POST /api/ask-question
     */
    suspend fun askQuestion(
        endpointUrl: String,
        sessionId: String?,
        audioBase64: String? = null,
        query: String? = null,
        imageBase64: String? = null,
        customVoice: String = "am-hamen",
        ttsProvider: String = "addis",
        preferredLanguage: String = "am",
        includeAudio: Boolean = true
    ): Result<VisionDescribeResponse> = withContext(Dispatchers.IO) {
        try {
            val resolvedUrl = if (preferredLanguage == "om") {
                if (endpointUrl.endsWith("/stream")) {
                    endpointUrl.removeSuffix("/stream")
                } else if (endpointUrl.endsWith("/ask-question/om")) {
                    endpointUrl
                } else if (endpointUrl.endsWith("/ask-question")) {
                    "$endpointUrl/om"
                } else if (endpointUrl.endsWith("/see-and-describe")) {
                    endpointUrl.replace("see-and-describe", "ask-question/om")
                } else {
                    "${endpointUrl.trimEnd('/')}/api/ask-question/om"
                }
            } else {
                if (endpointUrl.endsWith("/stream")) {
                    endpointUrl.removeSuffix("/stream")
                } else if (endpointUrl.endsWith("/ask-question")) {
                    endpointUrl
                } else if (endpointUrl.endsWith("/see-and-describe")) {
                    endpointUrl.replace("see-and-describe", "ask-question")
                } else {
                    "${endpointUrl.trimEnd('/')}/api/ask-question"
                }
            }

            val jsonPayload = JSONObject().apply {
                if (!sessionId.isNullOrBlank()) {
                    put("sessionId", sessionId)
                }
                if (!audioBase64.isNullOrBlank()) {
                    put("audioBase64", audioBase64)
                }
                if (!query.isNullOrBlank()) {
                    put("query", query)
                }
                if (!imageBase64.isNullOrBlank()) {
                    put("imageBase64", imageBase64)
                }
                put("customVoice", customVoice)
                put("ttsProvider", ttsProvider)
                put("preferredLanguage", preferredLanguage)
                put("includeAudio", includeAudio)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonPayload.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(resolvedUrl)
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
     * Standalone Speech Synthesis (POST /api/tts/synthesize)
     * Synthesizes plain text into spoken base64 MP3 audio using Addis AI neural voices.
     */
    suspend fun synthesizeTts(
        baseUrl: String,
        text: String,
        customVoice: String = "am-hamen",
        ttsProvider: String = "addis",
        language: String = "am"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "${baseUrl.trimEnd('/')}/api/tts/synthesize"
            val jsonPayload = JSONObject().apply {
                put("text", text)
                put("voiceId", customVoice)
                put("customVoice", customVoice)
                put("language", language)
                put("preferredLanguage", language)
                put("provider", ttsProvider)
                put("ttsProvider", ttsProvider)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonPayload.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) {
                return@withContext Result.failure(IOException("TTS HTTP ${response.code}: $body"))
            }

            val root = JSONObject(body)
            val b64 = root.optString("audioBase64").ifEmpty {
                val url = root.optString("audioUrl")
                if (url.startsWith("data:audio") && url.contains(",")) {
                    url.substringAfter(",")
                } else {
                    url
                }
            }
            if (b64.isNotBlank()) {
                Result.success(b64)
            } else {
                Result.failure(IOException("No audio payload returned from TTS"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
