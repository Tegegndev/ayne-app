package com.example
 
import com.example.data.ReadTextOcrEvent
import com.example.data.ReadTextResponse
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
@Config(sdk = [36])
class ReadTextStreamTest {
 
     @Test
     fun testReadTextOcrEventParsing() {
         val ocrJson = """
             {
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
         assertEquals("ፋርማሲ ክፍት ነው", response.amharic)
         assertEquals("Pharmacy is open", response.english)
         assertEquals(2480L, response.metrics?.visionLatencyMs)
     }
 
     @Test
     fun testReadTextOcrEventWithCutOffSafety() {
         val cutOffJson = """
             {
                 "signType": "Prescription / Dosage",
                 "documentType": "Medical Prescription",
                 "locationContext": "በወረቀቱ አናት ላይ",
                 "extractedText": "1 ኪኒን በ...",
                 "spokenAmharic": "ጽሑፉ በከፊል ተቆርጧል፤ እባክዎ ካሜራውን ትንሽ ወደ ኋላ ያርቁት",
                 "amharic": "",
                 "english": "Text partially cut off",
                 "isCutOff": true,
                 "isBlurry": false,
                 "isLegible": false,
                 "cameraGuidance": "ጽሑፉ በከፊል ተቆርጧል፤ እባክዎ ካሜራውን ትንሽ ወደ ኋላ ያርቁት",
                 "ocrLatencyMs": 2100
             }
         """.trimIndent()
 
         val event = ReadTextOcrEvent.fromJson(cutOffJson)
         assertTrue(event.isCutOff)
         assertFalse(event.isLegible)
         assertNotNull(event.cameraGuidance)
         assertEquals("Prescription / Dosage", event.signType)
 
         val response = event.toReadTextResponse()
         assertTrue(response.isCutOff)
         assertEquals("ጽሑፉ በከፊል ተቆርጧል፤ እባክዎ ካሜራውን ትንሽ ወደ ኋላ ያርቁት", response.amharic)
     }
 
     @Test
     fun testReadTextResponseFromJsonWithSpokenAmharicAndCutOff() {
         val restJson = """
             {
                 "success": true,
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
         // Preferred spokenAmharic transliteration for native speech
         assertEquals("ቦሌ ሮድ", response.amharic)
         assertEquals("Transit Notice", response.signType)
         assertFalse(response.isCutOff)
     }
 }
