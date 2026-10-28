package com.example

import com.example.data.AskQuestionAnswerEvent
import com.example.data.ReadTextOcrEvent
import com.example.data.VisionDescribeResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for the Afaan Oromoo endpoint payloads, which return
 * "amharic": null (JSON null) instead of a string. Android's JSONObject.optString
 * returns the literal string "null" for JSON null values — the parsers must
 * normalize that to an empty string so the om fallback chains work.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OromoEndpointParsingTest {

    @Test
    fun testVisionDescribeOromoPayloadWithNullAmharic() {
        // Shape returned by POST /api/see-and-describe with preferredLanguage: "om"
        val json = """
            {
                "success": true,
                "sessionId": "vis_sess_abc123",
                "oromo": "Suuraan kun magariisa qofa agarsiisa.",
                "amharic": null,
                "english": "This image only shows green.",
                "cleanSpeech": "Suuraan kun magariisa qofa agarsiisa."
            }
        """.trimIndent()

        val response = VisionDescribeResponse.fromJson(json)
        assertEquals("vis_sess_abc123", response.sessionId)
        assertEquals("Suuraan kun magariisa qofa agarsiisa.", response.oromo)
        assertEquals("This image only shows green.", response.english)
        // amharic is JSON null in the om payload; the parser falls back to cleanSpeech
        // (the om spoken text) and must never surface the literal string "null".
        assertEquals("Suuraan kun magariisa qofa agarsiisa.", response.amharic)
        assertTrue(response.amharic != "null")
    }

    @Test
    fun testReadTextOcrOromoPayloadWithNullAmharic() {
        // Shape of the "ocr" SSE event from POST /api/read-text/stream with preferredLanguage: "om"
        val json = """
            {
                "success": true,
                "sessionId": "vis_sess_def456",
                "signType": "Barreeffamni hin jiru (No Text Visible)",
                "documentType": "Barreeffamni hin jiru (No Text Visible)",
                "locationContext": "Fuuldura kee",
                "extractedText": "Barreeffamni hin jiru.",
                "oromo": "Fakkii kana keessatti barreeffamni hin mul'atu.",
                "amharic": null,
                "english": "No text visible in this image.",
                "isBlurry": false,
                "isCutOff": false,
                "isLegible": false
            }
        """.trimIndent()

        val event = ReadTextOcrEvent.fromJson(json)
        assertEquals("vis_sess_def456", event.sessionId)
        assertEquals("Fakkii kana keessatti barreeffamni hin mul'atu.", event.oromo)
        assertEquals("No text visible in this image.", event.english)
        assertEquals("", event.amharic) // must NOT be the literal string "null"

        val response = event.toReadTextResponse()
        assertEquals("Fakkii kana keessatti barreeffamni hin mul'atu.", response.oromo)
        assertEquals("", response.amharic)
    }

    @Test
    fun testAskQuestionAnswerOromoPayloadWithNullAmharic() {
        // Shape of the "answer" SSE event from POST /api/ask-question/om/stream
        val json = """
            {
                "sessionId": "vis_sess_ghi789",
                "oromo": "Ammas wanti addaa mul'atu hin jiru.",
                "amharic": null,
                "english": "There is still nothing specific visible.",
                "language": "om",
                "visionLatencyMs": 5795
            }
        """.trimIndent()

        val event = AskQuestionAnswerEvent.fromJson(json)
        assertEquals("vis_sess_ghi789", event.sessionId)
        assertEquals("Ammas wanti addaa mul'atu hin jiru.", event.oromo)
        assertEquals("There is still nothing specific visible.", event.english)
        assertEquals("", event.amharic) // must NOT be the literal string "null"
    }
}
