package com.example

import com.example.data.AskQuestionAnswerEvent
import com.example.data.AskQuestionDoneEvent
import com.example.data.AskQuestionTranscriptionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AskQuestionStreamTest {

    @Test
    fun testAskQuestionTranscriptionEventParsing() {
        val json = """
            {
                "transcribedQuery": "ስኒዎቹ ምን ቀለም ናቸው?",
                "detectedLanguage": "am",
                "sttLatencyMs": 312
            }
        """.trimIndent()

        val event = AskQuestionTranscriptionEvent.fromJson(json)
        assertEquals("ስኒዎቹ ምን ቀለም ናቸው?", event.transcribedQuery)
        assertEquals("am", event.detectedLanguage)
        assertEquals(312L, event.sttLatencyMs)
    }

    @Test
    fun testAskQuestionAnswerEventParsingWithPrefixStripping() {
        val json = """
            {
                "sessionId": "sess_89f1a23b_45c1",
                "amharic": "(አማርኛ) በቀኝ በኩል ያሉት ትናንሽ ስኒዎች ነጭ ቀለም ናቸው።",
                "english": "The two small cups on your right are white.",
                "visionLatencyMs": 680
            }
        """.trimIndent()

        val event = AskQuestionAnswerEvent.fromJson(json)
        assertEquals("sess_89f1a23b_45c1", event.sessionId)
        assertEquals("በቀኝ በኩል ያሉት ትናንሽ ስኒዎች ነጭ ቀለም ናቸው።", event.amharic)
        assertEquals("The two small cups on your right are white.", event.english)
        assertEquals(680L, event.visionLatencyMs)
    }

    @Test
    fun testAskQuestionDoneEventParsing() {
        val json = """
            {
                "sessionId": "sess_89f1a23b_45c1",
                "turnCount": 3,
                "totalLatencyMs": 1820
            }
        """.trimIndent()

        val event = AskQuestionDoneEvent.fromJson(json)
        assertEquals("sess_89f1a23b_45c1", event.sessionId)
        assertEquals(3, event.turnCount)
        assertEquals(1820L, event.totalLatencyMs)
    }

    @Test
    fun testAskQuestionEndpointDerivation() {
        val state = AyneUiState()
        assertNotNull(state.baseUrl)
        assertTrue(state.baseUrl.contains("https://ayne-roan.vercel.app"))
        assertNotNull(state.askQuestionEndpoint)
        assertTrue(state.askQuestionEndpoint.contains("ask-question"))
        assertTrue(state.apiEndpoint.contains("see-and-describe"))
        assertTrue(state.readTextEndpoint.contains("read-text"))
        assertEquals("am-hamen", state.customVoice)
        assertNull(state.activeSessionId)
    }

    @Test
    fun testBuildUrlsFromBaseUrl() {
        val customBase = "https://my-custom-vision.app/"
        assertEquals("https://my-custom-vision.app/api/see-and-describe", AyneUiState.buildVisionUrl(customBase))
        assertEquals("https://my-custom-vision.app/api/read-text", AyneUiState.buildReadTextUrl(customBase))
        assertEquals("https://my-custom-vision.app/api/ask-question", AyneUiState.buildAskQuestionUrl(customBase))
    }

    @Test
    fun testAskQuestionAnswerEventParsingAfaanOromoo() {
        val json = """
            {
                "sessionId": "sess_89f1a23b_45c1",
                "oromo": "Kubbaan keelloon minjaala irra jira.",
                "english": "A yellow ball is on the table.",
                "language": "om",
                "visionLatencyMs": 680
            }
        """.trimIndent()

        val event = AskQuestionAnswerEvent.fromJson(json)
        assertEquals("sess_89f1a23b_45c1", event.sessionId)
        assertEquals("Kubbaan keelloon minjaala irra jira.", event.oromo)
        assertEquals("A yellow ball is on the table.", event.english)
        assertEquals("om", event.language)
        assertEquals(680L, event.visionLatencyMs)
    }

    @Test
    fun testVisionDescribeResponseParsingAfaanOromoo() {
        val json = """
            {
                "sessionId": "sess_89f1a23b_45c1",
                "oromo": "Fuuldura kee sa'aatii 12 irratti balballi banamaa jira. Gara mirgaa minjaalli jira.",
                "english": "At 12 o'clock in front of you, an open door is present. To your right is a table.",
                "cleanSpeech": "Fuuldura kee sa'aatii 12 irratti balballi banamaa jira. Gara mirgaa minjaalli jira.",
                "audioUrl": "data:audio/mp3;base64,//uQxAAAAAA",
                "sceneType": "indoor"
            }
        """.trimIndent()

        val response = com.example.data.VisionDescribeResponse.fromJson(json)
        assertEquals("sess_89f1a23b_45c1", response.sessionId)
        assertEquals("Fuuldura kee sa'aatii 12 irratti balballi banamaa jira. Gara mirgaa minjaalli jira.", response.oromo)
        assertEquals("indoor", response.sceneType)
        assertNotNull(response.audio)
        assertEquals("//uQxAAAAAA", response.audio?.audioBase64)
    }
}
