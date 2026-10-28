package com.example.data

import org.json.JSONObject

/**
 * Removes annoying "(አማርኛ)" and "(Amharic)" label prefixes from backend responses.
 */
fun String.cleanLabelPrefix(): String {
    return this
        .replace(Regex("^\\s*\\([\\s]*(?:አማርኛ|Amharic)[\\s]*\\)[\\s]*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^\\s*(?:አማርኛ|Amharic)\\s*:\\s*", RegexOption.IGNORE_CASE), "")
        .trim()
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
    val amharic: String,
    val english: String,
    val cleanSpeech: String?,
    val audio: VisionAudioData?,
    val metrics: VisionMetrics?
) {
    companion object {
        fun fromJson(jsonStr: String): VisionDescribeResponse {
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
                    visionLatencyMs = if (it.has("visionAndTranslationLatencyMs")) it.optLong("visionAndTranslationLatencyMs") else null,
                    ttsLatencyMs = if (it.has("ttsLatencyMs")) it.optLong("ttsLatencyMs") else null,
                    totalLatencyMs = if (it.has("totalLatencyMs")) it.optLong("totalLatencyMs") else null
                )
            }

            val rawAmharic = root.optString("amharic").ifEmpty {
                root.optString("cleanSpeech").ifEmpty {
                    root.optString("text").ifEmpty {
                        root.optString("description", "")
                    }
                }
            }
            val cleanAmharic = rawAmharic.cleanLabelPrefix()
            val cleanSpeech = root.optString("cleanSpeech").takeIf { it.isNotEmpty() }?.cleanLabelPrefix()
            val rawEnglish = root.optString("english").ifEmpty {
                root.optString("englishTranslation").ifEmpty {
                    root.optString("translation", "")
                }
            }
            val sessionId = root.optString("sessionId").ifEmpty { root.optString("chatId") }.takeIf { it.isNotBlank() && it != "null" }

            return VisionDescribeResponse(
                success = root.optBoolean("success", true),
                sessionId = sessionId,
                transcribedQuery = root.optString("transcribedQuery").takeIf { it.isNotEmpty() },
                amharic = cleanAmharic,
                english = rawEnglish,
                cleanSpeech = cleanSpeech,
                audio = audioData,
                metrics = metrics
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
    val amharic: String,
    val english: String,
    val cleanSpeech: String?,
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

            val spokenAmharic = root.optString("spokenAmharic").takeIf { it.isNotEmpty() && it != "null" }?.cleanLabelPrefix()
            val rawAmharic = spokenAmharic ?: root.optString("amharic", "")
            val resolvedAmharic = rawAmharic.cleanLabelPrefix()
            val cleanSpeech = root.optString("cleanSpeech").takeIf { it.isNotEmpty() && it != "null" }?.cleanLabelPrefix()
            val sessionId = root.optString("sessionId").takeIf { it.isNotBlank() && it != "null" }

            return ReadTextResponse(
                success = root.optBoolean("success", true),
                sessionId = sessionId,
                transcribedQuery = root.optString("transcribedQuery").takeIf { it.isNotEmpty() },
                signType = root.optString("signType").takeIf { it.isNotEmpty() && it != "null" },
                documentType = root.optString("documentType").takeIf { it.isNotEmpty() && it != "null" },
                locationContext = root.optString("locationContext").takeIf { it.isNotEmpty() && it != "null" },
                extractedText = root.optString("extractedText").takeIf { it.isNotEmpty() && it != "null" },
                amharic = resolvedAmharic,
                english = root.optString("english", ""),
                cleanSpeech = cleanSpeech,
                isLegible = root.optBoolean("isLegible", true),
                isBlurry = root.optBoolean("isBlurry", false),
                isCutOff = root.optBoolean("isCutOff", false),
                cameraGuidance = root.optString("cameraGuidance").takeIf { it.isNotEmpty() && it != "null" },
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
    val amharic: String,
    val english: String,
    val visionLatencyMs: Long?,
    val sessionId: String? = null
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
    val amharic: String,
    val english: String,
    val cleanSpeech: String?,
    val isCutOff: Boolean,
    val isBlurry: Boolean,
    val isLegible: Boolean,
    val cameraGuidance: String?,
    val ocrLatencyMs: Long?
) {
    fun toReadTextResponse(): ReadTextResponse {
        val resolvedAmharic = (spokenAmharic?.takeIf { it.isNotEmpty() }
            ?: amharic.takeIf { it.isNotEmpty() }
            ?: cleanSpeech.orEmpty()).cleanLabelPrefix()
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
            val spokenAmharic = root.optString("spokenAmharic").takeIf { it.isNotEmpty() && it != "null" }?.cleanLabelPrefix()
            val rawAmharic = root.optString("amharic", "")
            val cleanAmharic = rawAmharic.cleanLabelPrefix()
            val cleanSpeech = root.optString("cleanSpeech").takeIf { it.isNotEmpty() && it != "null" }?.cleanLabelPrefix()
            val sessionId = root.optString("sessionId").takeIf { it.isNotBlank() && it != "null" }

            return ReadTextOcrEvent(
                sessionId = sessionId,
                signType = root.optString("signType").takeIf { it.isNotEmpty() && it != "null" },
                documentType = root.optString("documentType").takeIf { it.isNotEmpty() && it != "null" },
                locationContext = root.optString("locationContext").takeIf { it.isNotEmpty() && it != "null" },
                extractedText = root.optString("extractedText").takeIf { it.isNotEmpty() && it != "null" },
                spokenAmharic = spokenAmharic,
                amharic = cleanAmharic,
                english = root.optString("english", ""),
                cleanSpeech = cleanSpeech,
                isCutOff = root.optBoolean("isCutOff", false),
                isBlurry = root.optBoolean("isBlurry", false),
                isLegible = root.optBoolean("isLegible", true),
                cameraGuidance = root.optString("cameraGuidance").takeIf { it.isNotEmpty() && it != "null" },
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
                transcribedQuery = root.optString("transcribedQuery", ""),
                detectedLanguage = root.optString("detectedLanguage").takeIf { it.isNotEmpty() },
                sttLatencyMs = if (root.has("sttLatencyMs")) root.optLong("sttLatencyMs") else null
            )
        }
    }
}

data class AskQuestionAnswerEvent(
    val sessionId: String?,
    val amharic: String,
    val english: String,
    val visionLatencyMs: Long?
) {
    companion object {
        fun fromJson(jsonStr: String): AskQuestionAnswerEvent {
            val root = JSONObject(jsonStr)
            val rawAmharic = root.optString("amharic").ifEmpty {
                root.optString("cleanSpeech").ifEmpty { root.optString("text", "") }
            }
            return AskQuestionAnswerEvent(
                sessionId = root.optString("sessionId").takeIf { it.isNotBlank() && it != "null" },
                amharic = rawAmharic.cleanLabelPrefix(),
                english = root.optString("english", ""),
                visionLatencyMs = if (root.has("visionLatencyMs")) root.optLong("visionLatencyMs") else null
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
