package com.example.data

import org.json.JSONObject

/**
 * Removes label prefixes (e.g. "(አማርኛ)", "(Amharic):", "(English):", "(Oromo):", etc.),
 * unwraps nested JSON strings (e.g. {"amharic": "...", "english": "..."}, markdown ```json ... ```),
 * removes raw JSON artifacts, and extracts clean, human-readable speech text for the specified language.
 */
fun String.cleanLabelPrefix(preferredLanguage: String? = null): String {
    if (this.isBlank() || this.trim() == "null") return ""

    var s = this.trim()

    // 1. Strip markdown code blocks if wrapped in ``` or ```json
    s = s.replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*```$"), "")
        .trim()

    // 2. Identify candidate keys for target language
    val targetKeys = when (preferredLanguage?.lowercase()) {
        "om", "oromo", "afaan_oromoo" -> listOf("oromo", "afaanOromoo", "cleanSpeech", "speech", "text", "description", "amharic", "english")
        "en", "english" -> listOf("english", "englishTranslation", "translation", "cleanSpeech", "speech", "text", "description", "amharic", "oromo")
        "am", "amharic" -> listOf("amharic", "spokenAmharic", "cleanSpeech", "speech", "text", "description", "english", "oromo")
        else -> listOf("cleanSpeech", "spokenAmharic", "amharic", "oromo", "afaanOromoo", "text", "description", "english", "translation")
    }

    // 3. Try parsing as full JSONObject
    if (s.startsWith("{") && s.endsWith("}")) {
        try {
            val obj = JSONObject(s)
            for (key in targetKeys) {
                if (obj.has(key)) {
                    val candidate = obj.optText(key)
                    if (candidate.isNotBlank() && candidate != s) {
                        val cleaned = candidate.cleanLabelPrefix(preferredLanguage)
                        if (cleaned.isNotBlank()) return cleaned
                    }
                }
            }
            // If the JSON object contains no speech keys, return empty
            return ""
        } catch (_: Exception) {
            // Not a strict single JSON object, fall through to regex/partial extraction
        }
    }

    // 4. Handle partial / truncated JSON or JSON stubs
    if (s.contains("{") || s.contains("\":")) {
        // First check for incomplete / empty key stubs like `{"oromo": "` or `{"amharic": ""`
        val emptyStubPattern = Regex("""^\{?\s*"(?:oromo|afaanOromoo|amharic|spokenAmharic|english|cleanSpeech|text|description)"\s*:\s*"?\s*\}?$""", RegexOption.IGNORE_CASE)
        if (emptyStubPattern.matches(s.trim())) {
            return ""
        }

        // Regex extraction for candidate keys
        for (key in targetKeys) {
            val keyPattern = Regex(""""$key"\s*:\s*"((?:[^"\\]|\\.)*)"""", RegexOption.IGNORE_CASE)
            val match = keyPattern.find(s)
            if (match != null) {
                val rawVal = match.groupValues[1]
                val unescaped = unescapeJsonString(rawVal)
                if (unescaped.isNotBlank()) {
                    val cleaned = unescaped.cleanLabelPrefix(preferredLanguage)
                    if (cleaned.isNotBlank()) return cleaned
                }
            }
        }
    }

    // 5. Strip label prefixes like (አማርኛ), (Amharic), (English), (Oromo), etc.
    s = s
        .replace(Regex("""^\s*\(?\s*(?:አማርኛ|Amharic)\s*\)?\s*:?\s*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""^\s*\(?\s*(?:እንግሊዝኛ|English)\s*\)?\s*:?\s*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""^\s*\(?\s*(?:Afaan\s*Oromoo|Oromo)\s*\)?\s*:?\s*""", RegexOption.IGNORE_CASE), "")
        .trim()

    // 6. Clean residual JSON quotes or braces if leftover
    if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
        s = s.substring(1, s.length - 1).trim()
    }
    if (s.startsWith("{") && s.endsWith("}") && s.length >= 2) {
        s = s.substring(1, s.length - 1).trim()
    }

    return if (s == "null") "" else s
}

private fun unescapeJsonString(input: String): String {
    var s = input
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")

    if (s.contains("\\u")) {
        val unicodeRegex = Regex("""\\u([0-9a-fA-F]{4})""")
        s = unicodeRegex.replace(s) { matchResult ->
            val codePoint = matchResult.groupValues[1].toInt(16)
            codePoint.toChar().toString()
        }
    }
    return s
}

/**
 * Checks whether any of the supplied text snippets contain raw JSON artifacts
 * that may interfere with voice generation or TTS.
 */
fun hasRawJsonArtifacts(vararg texts: String?): Boolean {
    return texts.any { text ->
        if (text.isNullOrBlank()) false
        else {
            val t = text.trim()
            t.startsWith("{") ||
                t.contains("```json", ignoreCase = true) ||
                t.contains("\"amharic\":", ignoreCase = true) ||
                t.contains("\"oromo\":", ignoreCase = true) ||
                t.contains("\"english\":", ignoreCase = true)
        }
    }
}

/**
 * Android's JSONObject.optString returns the literal string "null" when the value is
 * JSON null (e.g. the Afaan Oromoo endpoints return "amharic": null). This normalizes
 * JSON null and blank values to an empty string so fallback chains work correctly.
 */
fun JSONObject.optText(name: String): String {
    return optString(name, "").takeIf { it.isNotBlank() && it != "null" } ?: ""
}

data class ConversationTurn(
    val role: String,
    val text: String
)

data class VisionAudioData(
    val audioBase64: String?,
    val audioUrl: String?,
    val durationSeconds: Double?,
    val provider: String?,
    val voiceId: String?
)

data class VisionMetrics(
    val visionLatencyMs: Long?,
    val ttsLatencyMs: Long?,
    val totalLatencyMs: Long?
)

data class VisionDescribeResponse(
    val success: Boolean,
    val sessionId: String? = null,
    val transcribedQuery: String?,
    val amharic: String = "",
    val english: String = "",
    val cleanSpeech: String = "",
    val audio: VisionAudioData?,
    val metrics: VisionMetrics?,
    val oromo: String = "",
    val sceneType: String? = null
) {
    companion object {
        fun fromJson(jsonStr: String): VisionDescribeResponse {
            val root = JSONObject(jsonStr)
            val audioObj = root.optJSONObject("audio")
            val rootAudioUrl = root.optString("audioUrl").takeIf { it.isNotEmpty() && it != "null" }
            val rootAudioBase64 = root.optString("audioBase64").takeIf { it.isNotEmpty() && it != "null" }

            val audioData = if (audioObj != null) {
                VisionAudioData(
                    audioBase64 = audioObj.optString("audioBase64").takeIf { s -> s.isNotEmpty() },
                    audioUrl = audioObj.optString("audioUrl").takeIf { s -> s.isNotEmpty() },
                    durationSeconds = if (audioObj.has("durationSeconds")) audioObj.optDouble("durationSeconds") else null,
                    provider = audioObj.optString("provider").takeIf { s -> s.isNotEmpty() },
                    voiceId = audioObj.optString("voiceId").takeIf { s -> s.isNotEmpty() }
                )
            } else if (rootAudioUrl != null || rootAudioBase64 != null) {
                val b64 = rootAudioBase64 ?: if (rootAudioUrl!!.startsWith("data:audio") && rootAudioUrl.contains(",")) {
                    rootAudioUrl.substringAfter(",")
                } else {
                    rootAudioUrl
                }
                VisionAudioData(
                    audioBase64 = b64,
                    audioUrl = rootAudioUrl,
                    durationSeconds = null,
                    provider = root.optString("ttsProvider").ifEmpty { "addis" },
                    voiceId = root.optString("customVoice").ifEmpty { "om-ayyaantuu" }
                )
            } else null

            val metricsObj = root.optJSONObject("metrics")
            val metrics = metricsObj?.let {
                VisionMetrics(
                    visionLatencyMs = if (it.has("visionAndTranslationLatencyMs")) it.optLong("visionAndTranslationLatencyMs") else null,
                    ttsLatencyMs = if (it.has("ttsLatencyMs")) it.optLong("ttsLatencyMs") else null,
                    totalLatencyMs = if (it.has("totalLatencyMs")) it.optLong("totalLatencyMs") else null
                )
            }

            val rawField = root.optText("raw")
            val rawCleanSpeech = root.optText("cleanSpeech")
            val rawOromo = root.optText("oromo").ifEmpty {
                root.optText("afaanOromoo")
            }
            val rawAmharic = root.optText("amharic").ifEmpty {
                root.optText("cleanSpeech").ifEmpty {
                    root.optText("text").ifEmpty {
                        root.optText("description")
                    }
                }
            }
            val cleanAmharic = rawAmharic.cleanLabelPrefix("am").ifEmpty {
                rawField.cleanLabelPrefix("am")
            }
            val cleanOromo = rawOromo.cleanLabelPrefix("om").ifEmpty {
                rawField.cleanLabelPrefix("om")
            }
            val rawEnglish = root.optText("english").ifEmpty {
                root.optText("englishTranslation").ifEmpty {
                    root.optText("translation")
                }
            }
            val cleanEnglish = rawEnglish.cleanLabelPrefix("en").ifEmpty {
                rawField.cleanLabelPrefix("en")
            }
            val cleanSpeech = rawCleanSpeech.cleanLabelPrefix().ifEmpty {
                cleanOromo.ifEmpty { cleanAmharic.ifEmpty { cleanEnglish } }
            }
            val sessionId = root.optString("sessionId").ifEmpty { root.optString("chatId") }.takeIf { it.isNotBlank() && it != "null" }
            val sceneType = root.optString("sceneType").takeIf { it.isNotBlank() && it != "null" }

            return VisionDescribeResponse(
                success = root.optBoolean("success", true),
                sessionId = sessionId,
                transcribedQuery = root.optText("transcribedQuery").takeIf { it.isNotEmpty() },
                amharic = cleanAmharic,
                english = cleanEnglish,
                cleanSpeech = cleanSpeech,
                audio = audioData,
                metrics = metrics,
                oromo = cleanOromo,
                sceneType = sceneType
            )
        }
    }
}

/**
 * Optical Reader Response for /api/read-text & /api/read-text-and-signs
 */
data class ReadTextResponse(
    val success: Boolean,
    val sessionId: String? = null,
    val transcribedQuery: String?,
    val signType: String?,
    val documentType: String?,
    val locationContext: String?,
    val extractedText: String?,
    val amharic: String = "",
    val english: String = "",
    val oromo: String = "",
    val cleanSpeech: String = "",
    val isLegible: Boolean,
    val isBlurry: Boolean,
    val isCutOff: Boolean = false,
    val cameraGuidance: String?,
    val audio: VisionAudioData?,
    val metrics: VisionMetrics?
) {
    companion object {
        fun fromJson(jsonStr: String): ReadTextResponse {
            val root = JSONObject(jsonStr)
            val audioObj = root.optJSONObject("audio")
            val audioData = audioObj?.let {
                VisionAudioData(
                    audioBase64 = it.optString("audioBase64").takeIf { s -> s.isNotEmpty() },
                    audioUrl = it.optString("audioUrl").takeIf { s -> s.isNotEmpty() },
                    durationSeconds = if (it.has("durationSeconds")) it.optDouble("durationSeconds") else null,
                    provider = it.optString("provider").takeIf { s -> s.isNotEmpty() },
                    voiceId = it.optString("voiceId").takeIf { s -> s.isNotEmpty() }
                )
            }

            val metricsObj = root.optJSONObject("metrics")
            val metrics = metricsObj?.let {
                VisionMetrics(
                    visionLatencyMs = if (it.has("ocrLatencyMs")) it.optLong("ocrLatencyMs") else null,
                    ttsLatencyMs = if (it.has("ttsLatencyMs")) it.optLong("ttsLatencyMs") else null,
                    totalLatencyMs = if (it.has("totalLatencyMs")) it.optLong("totalLatencyMs") else null
                )
            }

            val rawField = root.optText("raw")
            val rawCleanSpeech = root.optText("cleanSpeech")
            val spokenAmharic = root.optText("spokenAmharic").takeIf { it.isNotEmpty() }?.cleanLabelPrefix("am")
            val rawAmharic = spokenAmharic ?: root.optText("amharic")
            val resolvedAmharic = rawAmharic.cleanLabelPrefix("am").ifEmpty {
                rawField.cleanLabelPrefix("am")
            }
            val sessionId = root.optString("sessionId").takeIf { it.isNotBlank() && it != "null" }
            val rawOromo = root.optText("oromo").ifEmpty {
                root.optText("afaanOromoo")
            }
            val resolvedOromo = rawOromo.cleanLabelPrefix("om").ifEmpty {
                rawField.cleanLabelPrefix("om")
            }
            val resolvedEnglish = root.optText("english").cleanLabelPrefix("en").ifEmpty {
                rawField.cleanLabelPrefix("en")
            }
            val cleanSpeech = rawCleanSpeech.cleanLabelPrefix().ifEmpty {
                resolvedOromo.ifEmpty { resolvedAmharic.ifEmpty { resolvedEnglish } }
            }

            return ReadTextResponse(
                success = root.optBoolean("success", true),
                sessionId = sessionId,
                transcribedQuery = root.optString("transcribedQuery").takeIf { it.isNotEmpty() },
                signType = root.optString("signType").takeIf { it.isNotEmpty() && it != "null" }?.cleanLabelPrefix(),
                documentType = root.optString("documentType").takeIf { it.isNotEmpty() && it != "null" }?.cleanLabelPrefix(),
                locationContext = root.optString("locationContext").takeIf { it.isNotEmpty() && it != "null" }?.cleanLabelPrefix(),
                extractedText = root.optString("extractedText").takeIf { it.isNotEmpty() && it != "null" }?.cleanLabelPrefix(),
                amharic = resolvedAmharic,
                english = resolvedEnglish,
                oromo = resolvedOromo,
                cleanSpeech = cleanSpeech,
                isLegible = root.optBoolean("isLegible", true),
                isBlurry = root.optBoolean("isBlurry", false),
                isCutOff = root.optBoolean("isCutOff", false),
                cameraGuidance = root.optString("cameraGuidance").takeIf { it.isNotEmpty() && it != "null" }?.cleanLabelPrefix(),
                audio = audioData,
                metrics = metrics
            )
        }
    }
}

/**
 * SSE Real-time Streaming Events
 */
data class StreamVisionEvent(
    val amharic: String = "",
    val english: String = "",
    val oromo: String = "",
    val visionLatencyMs: Long?,
    val sessionId: String? = null,
    val cleanSpeech: String = ""
)

data class StreamAudioChunkEvent(
    val chunkBase64: String,
    val format: String = "mp3"
)

/**
 * OCR Metadata event from POST /api/read-text/stream
 * Emitted immediately after single-pass OCR completes (~2.5s)
 */
data class ReadTextOcrEvent(
    val sessionId: String? = null,
    val signType: String?,
    val documentType: String?,
    val locationContext: String?,
    val extractedText: String?,
    val spokenAmharic: String?,
    val amharic: String = "",
    val english: String = "",
    val oromo: String = "",
    val cleanSpeech: String = "",
    val isCutOff: Boolean,
    val isBlurry: Boolean,
    val isLegible: Boolean,
    val cameraGuidance: String?,
    val ocrLatencyMs: Long?
) {
    fun toReadTextResponse(): ReadTextResponse {
        val resolvedAmharic = (spokenAmharic?.takeIf { it.isNotEmpty() }
            ?: amharic.takeIf { it.isNotEmpty() }
            ?: "").cleanLabelPrefix()
        return ReadTextResponse(
            success = true,
            sessionId = sessionId,
            transcribedQuery = null,
            signType = signType,
            documentType = documentType,
            locationContext = locationContext,
            extractedText = extractedText,
            amharic = resolvedAmharic,
            english = english,
            oromo = oromo,
            cleanSpeech = cleanSpeech,
            isLegible = isLegible,
            isBlurry = isBlurry,
            isCutOff = isCutOff,
            cameraGuidance = cameraGuidance,
            audio = null,
            metrics = ocrLatencyMs?.let { VisionMetrics(visionLatencyMs = it, ttsLatencyMs = null, totalLatencyMs = null) }
        )
    }

    companion object {
        fun fromJson(jsonStr: String): ReadTextOcrEvent {
            val root = JSONObject(jsonStr)
            val rawField = root.optText("raw")
            val rawCleanSpeech = root.optText("cleanSpeech")
            val spokenAmharic = root.optText("spokenAmharic").takeIf { it.isNotEmpty() }?.cleanLabelPrefix("am")
            val rawAmharic = root.optText("amharic")
            val cleanAmharic = rawAmharic.cleanLabelPrefix("am").ifEmpty {
                rawField.cleanLabelPrefix("am")
            }
            val sessionId = root.optString("sessionId").takeIf { it.isNotBlank() && it != "null" }
            val rawOromo = root.optText("oromo").ifEmpty {
                root.optText("afaanOromoo")
            }
            val cleanOromo = rawOromo.cleanLabelPrefix("om").ifEmpty {
                rawField.cleanLabelPrefix("om")
            }
            val cleanEnglish = root.optText("english").cleanLabelPrefix("en").ifEmpty {
                rawField.cleanLabelPrefix("en")
            }
            val cleanSpeech = rawCleanSpeech.cleanLabelPrefix().ifEmpty {
                cleanOromo.ifEmpty { cleanAmharic.ifEmpty { cleanEnglish } }
            }

            return ReadTextOcrEvent(
                sessionId = sessionId,
                signType = root.optString("signType").takeIf { it.isNotEmpty() && it != "null" }?.cleanLabelPrefix(),
                documentType = root.optString("documentType").takeIf { it.isNotEmpty() && it != "null" }?.cleanLabelPrefix(),
                locationContext = root.optString("locationContext").takeIf { it.isNotEmpty() && it != "null" }?.cleanLabelPrefix(),
                extractedText = root.optString("extractedText").takeIf { it.isNotEmpty() && it != "null" }?.cleanLabelPrefix(),
                spokenAmharic = spokenAmharic,
                amharic = cleanAmharic,
                english = cleanEnglish,
                oromo = cleanOromo,
                cleanSpeech = cleanSpeech,
                isCutOff = root.optBoolean("isCutOff", false),
                isBlurry = root.optBoolean("isBlurry", false),
                isLegible = root.optBoolean("isLegible", true),
                cameraGuidance = root.optString("cameraGuidance").takeIf { it.isNotEmpty() && it != "null" }?.cleanLabelPrefix(),
                ocrLatencyMs = if (root.has("ocrLatencyMs")) root.optLong("ocrLatencyMs") else null
            )
        }
    }
}

/**
 * Real-time Streaming Events for POST /api/ask-question/stream
 */
data class AskQuestionTranscriptionEvent(
    val transcribedQuery: String,
    val detectedLanguage: String?,
    val sttLatencyMs: Long?
) {
    companion object {
        fun fromJson(jsonStr: String): AskQuestionTranscriptionEvent {
            val root = JSONObject(jsonStr)
            return AskQuestionTranscriptionEvent(
                transcribedQuery = root.optText("transcribedQuery"),
                detectedLanguage = root.optString("detectedLanguage").takeIf { it.isNotEmpty() },
                sttLatencyMs = if (root.has("sttLatencyMs")) root.optLong("sttLatencyMs") else null
            )
        }
    }
}

data class AskQuestionAnswerEvent(
    val sessionId: String?,
    val amharic: String = "",
    val english: String = "",
    val oromo: String = "",
    val language: String? = null,
    val visionLatencyMs: Long?,
    val cleanSpeech: String = ""
) {
    companion object {
        fun fromJson(jsonStr: String): AskQuestionAnswerEvent {
            val root = JSONObject(jsonStr)
            val rawField = root.optText("raw")
            val rawCleanSpeech = root.optText("cleanSpeech")
            val rawAmharic = root.optText("amharic").ifEmpty {
                root.optText("cleanSpeech").ifEmpty { root.optText("text") }
            }
            val rawOromo = root.optText("oromo").ifEmpty {
                root.optText("afaanOromoo")
            }
            val cleanAmharic = rawAmharic.cleanLabelPrefix("am").ifEmpty {
                rawField.cleanLabelPrefix("am")
            }
            val cleanOromo = rawOromo.cleanLabelPrefix("om").ifEmpty {
                rawField.cleanLabelPrefix("om")
            }
            val cleanEnglish = root.optText("english").cleanLabelPrefix("en").ifEmpty {
                rawField.cleanLabelPrefix("en")
            }
            val cleanSpeech = rawCleanSpeech.cleanLabelPrefix().ifEmpty {
                cleanOromo.ifEmpty { cleanAmharic.ifEmpty { cleanEnglish } }
            }

            return AskQuestionAnswerEvent(
                sessionId = root.optString("sessionId").takeIf { it.isNotBlank() && it != "null" },
                amharic = cleanAmharic,
                english = cleanEnglish,
                oromo = cleanOromo,
                language = root.optString("language").takeIf { it.isNotEmpty() },
                visionLatencyMs = if (root.has("visionLatencyMs")) root.optLong("visionLatencyMs") else null,
                cleanSpeech = cleanSpeech
            )
        }
    }
}

data class AskQuestionDoneEvent(
    val sessionId: String?,
    val turnCount: Int?,
    val totalLatencyMs: Long?
) {
    companion object {
        fun fromJson(jsonStr: String): AskQuestionDoneEvent {
            val root = JSONObject(jsonStr)
            return AskQuestionDoneEvent(
                sessionId = root.optString("sessionId").takeIf { it.isNotBlank() && it != "null" },
                turnCount = if (root.has("turnCount")) root.optInt("turnCount") else null,
                totalLatencyMs = if (root.has("totalLatencyMs")) root.optLong("totalLatencyMs") else null
            )
        }
    }
}
