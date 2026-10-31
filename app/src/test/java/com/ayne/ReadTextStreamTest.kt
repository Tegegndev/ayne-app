package com.ayne

import com.ayne.data.ReadTextOcrEvent
import com.ayne.data.ReadTextResponse
import com.ayne.data.cleanLabelPrefix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadTextStreamTest {

    @Test
    fun testReadTextOcrEventParsing() {
        val ocrJson = """
            {
                "sessionId": "sess_read_123",
                "signType": "Storefront / Environmental Sign",
                "documentType": "Storefront",
                "locationContext": "በበሩ ላይ በ12 ሰዓት",
                "extractedText": "ፋርማሲ ክፍት ነው",
                "spokenAmharic": "ፋርማሲ ክፍት ነው",
                "amharic": "ፋርማሲ ክፍት ነው",
                "english": "Pharmacy is open",
                "isCutOff": false,
                "isBlurry": false,
                "isLegible": true,
                "cameraGuidance": null,
                "ocrLatencyMs": 2480
            }
        """.trimIndent()

        val event = ReadTextOcrEvent.fromJson(ocrJson)
        assertEquals("sess_read_123", event.sessionId)
        assertEquals("Storefront / Environmental Sign", event.signType)
        assertEquals("Storefront", event.documentType)
        assertEquals("በበሩ ላይ በ12 ሰዓት", event.locationContext)
        assertEquals("ፋርማሲ ክፍት ነው", event.extractedText)
        assertEquals("ፋርማሲ ክፍት ነው", event.spokenAmharic)
        assertFalse(event.isCutOff)
        assertFalse(event.isBlurry)
        assertTrue(event.isLegible)
        assertNull(event.cameraGuidance)
        assertEquals(2480L, event.ocrLatencyMs)

        val response = event.toReadTextResponse()
        assertTrue(response.success)
        assertEquals("sess_read_123", response.sessionId)
        assertEquals("ፋርማሲ ክፍት ነው", response.amharic)
        assertEquals("Pharmacy is open", response.english)
        assertEquals(2480L, response.metrics?.visionLatencyMs)
    }

    @Test
    fun testReadTextOcrEventWithCutOffSafetyDirectionalCoaching() {
        val cutOffJson = """
            {
                "signType": "Prescription / Dosage",
                "documentType": "Medical Prescription",
                "locationContext": "በወረቀቱ አናት ላይ",
                "extractedText": "1 ኪኒን በቀን...",
                "spokenAmharic": "ጽሑፉ በቀኝ በኩል ተቆርጧል፤ እባክዎ ካሜራውን ትንሽ ወደ ቀኝ ያዙሩትና እንደገና ያንሱ",
                "amharic": "",
                "english": "Text partially cut off on right side",
                "isCutOff": true,
                "isBlurry": false,
                "isLegible": false,
                "cameraGuidance": "ጽሑፉ በቀኝ በኩል ተቆርጧል፤ እባክዎ ካሜራውን ትንሽ ወደ ቀኝ ያዙሩትና እንደገና ያንሱ",
                "ocrLatencyMs": 2100
            }
        """.trimIndent()

        val event = ReadTextOcrEvent.fromJson(cutOffJson)
        assertTrue(event.isCutOff)
        assertFalse(event.isLegible)
        assertNotNull(event.cameraGuidance)
        assertEquals("Prescription / Dosage", event.signType)
        assertEquals("ጽሑፉ በቀኝ በኩል ተቆርጧል፤ እባክዎ ካሜራውን ትንሽ ወደ ቀኝ ያዙሩትና እንደገና ያንሱ", event.cameraGuidance)

        val response = event.toReadTextResponse()
        assertTrue(response.isCutOff)
        assertEquals("ጽሑፉ በቀኝ በኩል ተቆርጧል፤ እባክዎ ካሜራውን ትንሽ ወደ ቀኝ ያዙሩትና እንደገና ያንሱ", response.amharic)
    }

    @Test
    fun testAmharicPrefixEradication() {
        // Annoying (አማርኛ) and (Amharic) label prefixes must be eradicated
        val prefixedAmharic = "(አማርኛ) በጠረጴዛው ላይ ባለ አረንጓዴ ቀለም የብር ኖት አለ።"
        val prefixedEnglish = "(Amharic) በበሩ ላይ የተለጠፈ ማስታወቂያ አለ።"
        val colonPrefixed = "አማርኛ: ፋርማሲ ክፍት ነው"

        assertEquals("በጠረጴዛው ላይ ባለ አረንጓዴ ቀለም የብር ኖት አለ።", prefixedAmharic.cleanLabelPrefix())
        assertEquals("በበሩ ላይ የተለጠፈ ማስታወቂያ አለ።", prefixedEnglish.cleanLabelPrefix())
        assertEquals("ፋርማሲ ክፍት ነው", colonPrefixed.cleanLabelPrefix())

        // Test in ReadTextResponse.fromJson
        val jsonWithPrefix = """
            {
                "success": true,
                "spokenAmharic": "(አማርኛ) 1 ኪኒን በጠዋት",
                "amharic": "(አማርኛ) 1 ኪኒን በጠዋት",
                "english": "1 pill in the morning",
                "isCutOff": false,
                "isBlurry": false,
                "isLegible": true
            }
        """.trimIndent()

        val response = ReadTextResponse.fromJson(jsonWithPrefix)
        assertEquals("1 ኪኒን በጠዋት", response.amharic)
    }

    @Test
    fun testReadTextResponseFromJsonWithSpokenAmharicAndCutOff() {
        val restJson = """
            {
                "success": true,
                "sessionId": "sess_rest_999",
                "signType": "Transit Notice",
                "locationContext": "በቀኝ በኩል በሚገኝ ምሰሶ ላይ",
                "extractedText": "ቦሌ መንገድ",
                "spokenAmharic": "ቦሌ ሮድ",
                "amharic": "ቦሌ መንገድ",
                "english": "Bole Road",
                "isCutOff": false,
                "isBlurry": false,
                "isLegible": true
            }
        """.trimIndent()

        val response = ReadTextResponse.fromJson(restJson)
        assertTrue(response.success)
        assertEquals("sess_rest_999", response.sessionId)
        assertEquals("ቦሌ ሮድ", response.amharic)
        assertEquals("Transit Notice", response.signType)
        assertFalse(response.isCutOff)
    }
}
