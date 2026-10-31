package com.ayne

import com.ayne.data.AskQuestionAnswerEvent
import com.ayne.data.ReadTextOcrEvent
import com.ayne.data.VisionDescribeResponse
import com.ayne.data.cleanLabelPrefix
import com.ayne.data.hasRawJsonArtifacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JsonResponseHandlingTest {

    @Test
    fun testCleanLabelPrefixUnwrapsRawJsonAmharic() {
        val rawJson = """{"amharic": "ከፊት ለፊትዎ የስልክ ስክሪን ይታያል፤ በትልልቅ ቢጫ ቁልፎች የተዘጋጀ ነው።", "english": "Directly in front is a phone screen."}"""
        val cleaned = rawJson.cleanLabelPrefix("am")
        assertEquals("ከፊት ለፊትዎ የስልክ ስክሪን ይታያል፤ በትልልቅ ቢጫ ቁልፎች የተዘጋጀ ነው።", cleaned)
    }

    @Test
    fun testCleanLabelPrefixUnwrapsMarkdownFencedJson() {
        val markdownJson = """
            ```json
            {
              "amharic": "ከፊት ለፊትዎ የስልክ ስክሪን ይታያል።",
              "english": "In front of you is a screen."
            }
            ```
        """.trimIndent()
        val cleaned = markdownJson.cleanLabelPrefix("am")
        assertEquals("ከፊት ለፊትዎ የስልክ ስክሪን ይታያል።", cleaned)
    }

    @Test
    fun testCleanLabelPrefixWithTruncatedOrEmptyStubReturnsEmpty() {
        // When server LLM response is cut off at the opening key stub
        val stubOromo = "{\n  \"oromo\": \""
        val stubAmharic = "{\n  \"amharic\": \""
        val emptyStub = "{\"amharic\": \"\"}"

        assertEquals("", stubOromo.cleanLabelPrefix("om"))
        assertEquals("", stubAmharic.cleanLabelPrefix("am"))
        assertEquals("", emptyStub.cleanLabelPrefix("am"))
    }

    @Test
    fun testCleanLabelPrefixUnwrapsOromoAndEnglish() {
        val json = """{"oromo":"Fuuldura kee iskiriinii moobaayilaatu mul'ata.","english":"Directly in front is a phone screen."}"""
        val omClean = json.cleanLabelPrefix("om")
        val enClean = json.cleanLabelPrefix("en")

        assertEquals("Fuuldura kee iskiriinii moobaayilaatu mul'ata.", omClean)
        assertEquals("Directly in front is a phone screen.", enClean)
    }

    @Test
    fun testVisionDescribeResponseUnwrapsNestedJsonInFields() {
        // Real-world scenario observed from live backend: the text fields contain raw JSON strings
        val jsonPayload = """
            {
                "success": true,
                "sessionId": "vis_sess_live_123",
                "amharic": "{\"amharic\":\"ከፊት ለፊትዎ የስልክ ስክሪን ይታያል።\",\"english\":\"In front is a screen.\"}",
                "english": "{\"amharic\":\"ከፊት ለፊትዎ የስልክ ስክሪን ይታያል።\",\"english\":\"In front is a screen.\"}",
                "cleanSpeech": "{\"amharic\":\"ከፊት ለፊትዎ የስልክ ስክሪን ይታያል።\",\"english\":\"In front is a screen.\"}"
            }
        """.trimIndent()

        val response = VisionDescribeResponse.fromJson(jsonPayload)
        assertEquals("vis_sess_live_123", response.sessionId)
        assertEquals("ከፊት ለፊትዎ የስልክ ስክሪን ይታያል።", response.amharic)
        assertEquals("In front is a screen.", response.english)
        assertFalse(response.amharic.contains("{"))
        assertFalse(response.english.contains("{"))
    }

    @Test
    fun testVisionDescribeResponseWhenOromoPayloadContainsJson() {
        val jsonPayload = """
            {
                "success": true,
                "sessionId": "vis_sess_om_456",
                "oromo": "{\"oromo\":\"Fuuldura kee sa'aatii 12 irratti iskiriinii moobaayiliitu mul'ata.\",\"english\":\"Directly in front is a phone screen.\"}",
                "english": "{\"oromo\":\"Fuuldura kee sa'aatii 12 irratti iskiriinii moobaayiliitu mul'ata.\",\"english\":\"Directly in front is a phone screen.\"}",
                "cleanSpeech": "{\"oromo\":\"Fuuldura kee sa'aatii 12 irratti iskiriinii moobaayiliitu mul'ata.\",\"english\":\"Directly in front is a phone screen.\"}"
            }
        """.trimIndent()

        val response = VisionDescribeResponse.fromJson(jsonPayload)
        assertEquals("vis_sess_om_456", response.sessionId)
        assertEquals("Fuuldura kee sa'aatii 12 irratti iskiriinii moobaayiliitu mul'ata.", response.oromo)
        assertEquals("Directly in front is a phone screen.", response.english)
        assertFalse(response.oromo.contains("{"))
        assertFalse(response.english.contains("{"))
    }

    @Test
    fun testAskQuestionAnswerEventUnwrapsJsonString() {
        val answerJson = """
            {
                "sessionId": "vis_sess_q_789",
                "amharic": "በፊትህ ጥቁር እና ቢጫ ቀለም ያለው ስክሪን ይታያል።",
                "english": "{ \"amharic\": \"በፊትህ ጥቁር እና ቢጫ ቀለም ያለው ስክሪን ይታያል።\", \"english\": \"In front of you is a screen.\" }"
            }
        """.trimIndent()

        val event = AskQuestionAnswerEvent.fromJson(answerJson)
        assertEquals("በፊትህ ጥቁር እና ቢጫ ቀለም ያለው ስክሪን ይታያል።", event.amharic)
        assertEquals("In front of you is a screen.", event.english)
        assertFalse(event.english.contains("{"))
    }

    @Test
    fun testHasRawJsonArtifacts() {
        assertTrue(hasRawJsonArtifacts("""{"amharic": "ሰላም"}"""))
        assertTrue(hasRawJsonArtifacts("{\n  \"oromo\": \""))
        assertTrue(hasRawJsonArtifacts("```json\n{\"text\": \"hello\"}\n```"))
        assertFalse(hasRawJsonArtifacts("ከፊት ለፊትዎ የስልክ ስክሪን ይታያል።"))
        assertFalse(hasRawJsonArtifacts("Fuuldura kee iskiriinii moobaayilaatu mul'ata."))
        assertFalse(hasRawJsonArtifacts("Directly in front of you is a door."))
        assertFalse(hasRawJsonArtifacts(null, ""))
    }
}
