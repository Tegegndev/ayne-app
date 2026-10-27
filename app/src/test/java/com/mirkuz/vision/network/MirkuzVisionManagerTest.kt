package com.mirkuz.vision.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MirkuzVisionManagerTest {

    private lateinit var context: Context
    private var lastRecordedRequestBody: String? = null
    private var requestCount: Int = 0
    private var manager: MirkuzVisionManager? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        lastRecordedRequestBody = null
        requestCount = 0
    }

    @After
    fun tearDown() {
        manager?.stopSpeechAndFeedback()
    }

    private fun createManager(responseProvider: (requestBody: String) -> Pair<String, String>): MirkuzVisionManager {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestCount++
                val request = chain.request()
                val buffer = Buffer()
                request.body?.writeTo(buffer)
                val bodyStr = buffer.readUtf8()
                lastRecordedRequestBody = bodyStr

                val (body, contentType) = responseProvider(bodyStr)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody(contentType.toMediaType()))
                    .build()
            }
            .build()

        val mgr = MirkuzVisionManager(
            context = context,
            serverBaseUrl = "https://mock.mirkuz.vision",
            okHttpClient = client
        )
        manager = mgr
        return mgr
    }

    @Test
    fun testSessionResetOnKeywords() {
        val mgr = createManager { _ ->
            Pair("{}", "application/json")
        }
        mgr.setActiveSessionId("vis_sess_123")
        assertEquals("vis_sess_123", mgr.getActiveSessionId())

        var successAmharic: String? = null
        var successEnglish: String? = null

        mgr.seeAndDescribe(
            newSnapshotBase64 = null,
            followUpQuestion = "አዲስ ጀምር",
            onSuccess = { amharic, english ->
                successAmharic = amharic
                successEnglish = english
            },
            onError = { fail("Should not fail on reset") }
        )

        assertNull("Session ID should be cleared on 'አዲስ ጀምር'", mgr.getActiveSessionId())
        assertEquals("አዲስ ውይይት ተጀምሯል። እባክዎ አዲስ ፎቶ ያንሱ።", successAmharic)
        assertEquals("Session reset.", successEnglish)
        assertEquals(0, requestCount)
    }

    @Test
    fun testTurn1SnapshotSetsSessionId() {
        val mockResponseBody = """
            {
                "success": true,
                "sessionId": "vis_sess_abc123",
                "chatId": "vis_sess_abc123",
                "amharic": "(አማርኛ) በጠረጴዛው ላይ ባለ አረንጓዴ ቀለም የብር ኖት አለ።",
                "english": "On the table there is a green banknote."
            }
        """.trimIndent()

        val mgr = createManager { _ ->
            Pair(mockResponseBody, "application/json")
        }

        val latch = CountDownLatch(1)
        var spokenAmharic: String? = null

        mgr.seeAndDescribe(
            newSnapshotBase64 = "base64_image_bytes",
            followUpQuestion = null,
            onSuccess = { amharic, _ ->
                spokenAmharic = amharic
                latch.countDown()
            },
            onError = { error ->
                fail("Request failed: $error")
            }
        )

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        val body = lastRecordedRequestBody ?: ""

        assertTrue(body.contains("imageBase64"))
        assertFalse(body.contains("\"sessionId\""))
        assertEquals("vis_sess_abc123", mgr.getActiveSessionId())
        assertEquals("በጠረጴዛው ላይ ባለ አረንጓዴ ቀለም የብር ኖት አለ።", spokenAmharic)
    }

    @Test
    fun testTurn2FollowUpSendsOnlySessionIdZeroImageBytes() {
        val mockResponseBody = """
            {
                "success": true,
                "sessionId": "vis_sess_abc123",
                "amharic": "የስኒው ቀለም ነጭ ነው።",
                "english": "The cup is white."
            }
        """.trimIndent()

        val mgr = createManager { _ ->
            Pair(mockResponseBody, "application/json")
        }
        mgr.setActiveSessionId("vis_sess_abc123")

        val latch = CountDownLatch(1)
        var resultAmharic: String? = null

        mgr.seeAndDescribe(
            newSnapshotBase64 = null,
            followUpQuestion = "የስኒው ቀለም ምን ይመስላል?",
            onSuccess = { amharic, _ ->
                resultAmharic = amharic
                latch.countDown()
            },
            onError = { error ->
                fail("Request failed: $error")
            }
        )

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        val body = lastRecordedRequestBody ?: ""

        assertTrue(body.contains("\"sessionId\":\"vis_sess_abc123\""))
        assertTrue(body.contains("\"query\":\"የስኒው ቀለም ምን ይመስላል?\""))
        assertFalse("Follow-up should NEVER include imageBase64!", body.contains("imageBase64"))
        assertEquals("የስኒው ቀለም ነጭ ነው።", resultAmharic)
    }

    @Test
    fun testReadTextStreamSseReceivesOcrEvent() {
        val ssePayload = "event: ocr\n" +
                "data: {\"sessionId\":\"vis_sess_ocr\",\"signType\":\"የመንገድ ምልክት\",\"locationContext\":\"በ12 ሰዓት\",\"extractedText\":\"ቦሌ መንገድ\",\"isCutOff\":false,\"cameraGuidance\":null}\n\n" +
                "event: done\n" +
                "data: {\"chunks\":0}\n\n"

        val mgr = createManager { _ ->
            Pair(ssePayload, "text/event-stream")
        }

        val latch = CountDownLatch(1)
        var receivedTitle: String? = null
        var receivedLocation: String? = null
        var receivedText: String? = null
        var receivedCutOff = true

        mgr.startReadTextStream(
            compressedJpegBase64 = "sample_jpeg_base64",
            userInstruction = null,
            onOcrReceived = { title, location, text, isCutOff, _ ->
                receivedTitle = title
                receivedLocation = location
                receivedText = text
                receivedCutOff = isCutOff
                latch.countDown()
            },
            onFirstAudioPlay = {},
            onError = { error ->
                fail("Stream error: $error")
            }
        )

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertEquals("የመንገድ ምልክት", receivedTitle)
        assertEquals("በ12 ሰዓት", receivedLocation)
        assertEquals("ቦሌ መንገድ", receivedText)
        assertFalse(receivedCutOff)
        assertEquals("vis_sess_ocr", mgr.getActiveSessionId())
    }

    @Test
    fun testReadTextStreamDirectionalCoachingWhenCutOff() {
        val ssePayload = "event: ocr\n" +
                "data: {\"signType\":\"Prescription\",\"locationContext\":\"በመድሃኒቱ ላይ\",\"extractedText\":\"500mg...\",\"isCutOff\":true,\"cameraGuidance\":\"ጽሑፉ በቀኝ በኩል ተቆርጧል፤ እባክዎ ካሜራውን ትንሽ ወደ ቀኝ ያዙሩትና እንደገና ያንሱ\"}\n\n" +
                "event: done\n" +
                "data: {}\n\n"

        val mgr = createManager { _ ->
            Pair(ssePayload, "text/event-stream")
        }

        val latch = CountDownLatch(1)
        var guidanceReceived: String? = null
        var isCutOffReceived = false

        mgr.startReadTextStream(
            compressedJpegBase64 = "sample_jpeg_base64",
            onOcrReceived = { _, _, _, isCutOff, guidance ->
                isCutOffReceived = isCutOff
                guidanceReceived = guidance
                latch.countDown()
            },
            onFirstAudioPlay = {},
            onError = { error ->
                fail("Stream error: $error")
            }
        )

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertTrue(isCutOffReceived)
        assertEquals("ጽሑፉ በቀኝ በኩል ተቆርጧል፤ እባክዎ ካሜራውን ትንሽ ወደ ቀኝ ያዙሩትና እንደገና ያንሱ", guidanceReceived)
    }

    @Test
    fun testBargeInStopsAudioSafely() {
        val mgr = createManager { _ -> Pair("{}", "application/json") }
        mgr.stopSpeechAndFeedback()
        mgr.resetSession()
        assertNull(mgr.getActiveSessionId())
    }
}
