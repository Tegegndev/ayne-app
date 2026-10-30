package com.example.data

import org.json.JSONObject

/**
 * Events emitted by the Addis AI Realtime "Mode B" SSE stream
 * (POST /api/addis-realtime/stream, text/event-stream).
 */
sealed class AddisRealtimeEvent {
    data class Ready(val model: String) : AddisRealtimeEvent()

    /**
     * Live transcription. [role] is "user" or "assistant"; [noSpeechDetected]
     * is true when the server heard nothing usable from our audio.
     */
    data class Transcript(
        val role: String,
        val text: String,
        val noSpeechDetected: Boolean
    ) : AddisRealtimeEvent()

    /** One base64-encoded MP3 chunk of the assistant's speech. */
    data class Audio(
        val base64Data: String,
        val format: String
    ) : AddisRealtimeEvent()

    /** Turn finished; [rawJson] carries finish reason and usage metadata. */
    data class TurnComplete(val rawJson: String) : AddisRealtimeEvent()

    /** Terminal event of the stream. */
    data object Done : AddisRealtimeEvent()
}

/**
 * Incremental line parser for the Mode B Server-Sent Events stream.
 * Feed each line as it arrives; returns an [AddisRealtimeEvent] whenever a
 * data line completes, null otherwise. Pure Kotlin so it can be unit-tested
 * on the JVM (with Robolectric for org.json).
 */
class AddisRealtimeSseParser {
    private var currentEvent: String = ""

    fun feed(line: String): AddisRealtimeEvent? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("event:")) {
            currentEvent = trimmed.removePrefix("event:").trim()
            return null
        }
        if (trimmed.startsWith("data:")) {
            val payload = trimmed.removePrefix("data:").trim()
            return parseData(currentEvent, payload)
        }
        // Comments / keep-alives / unknown fields are ignored.
        return null
    }

    companion object {
        fun parseData(event: String, payload: String): AddisRealtimeEvent? {
            if (payload.isEmpty() || payload == "[DONE]") return null
            val json = try {
                JSONObject(payload)
            } catch (_: Exception) {
                return null
            }
            return when (event) {
                "ready" -> AddisRealtimeEvent.Ready(json.optString("model"))
                "transcript" -> AddisRealtimeEvent.Transcript(
                    role = json.optString("role"),
                    text = json.optString("text"),
                    noSpeechDetected = json.optBoolean("noSpeechDetected", false)
                )
                "audio" -> {
                    val data = json.optString("data")
                    if (data.isBlank()) null
                    else AddisRealtimeEvent.Audio(
                        base64Data = data,
                        format = json.optString("format", "mp3")
                    )
                }
                "turn_complete" -> AddisRealtimeEvent.TurnComplete(payload)
                "done" -> AddisRealtimeEvent.Done
                else -> null
            }
        }
    }
}
