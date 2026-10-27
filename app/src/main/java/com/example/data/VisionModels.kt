package com.example.data

import org.json.JSONObject

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

            return VisionDescribeResponse(
                success = root.optBoolean("success", true),
                transcribedQuery = root.optString("transcribedQuery").takeIf { it.isNotEmpty() },
                amharic = root.optString("amharic", ""),
                english = root.optString("english", ""),
                cleanSpeech = root.optString("cleanSpeech").takeIf { it.isNotEmpty() },
                audio = audioData,
                metrics = metrics
            )
        }
    }
}
