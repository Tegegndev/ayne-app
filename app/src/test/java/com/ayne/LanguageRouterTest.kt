package com.ayne

import com.ayne.data.endpoints.EndpointPath
import com.ayne.data.endpoints.LanguageRouter
import com.ayne.data.endpoints.Variant
import com.ayne.data.endpoints.resolve
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageRouterTest {

    private data class RoutingTestCase(
        val baseUrl: String,
        val language: String,
        val path: EndpointPath,
        val variant: Variant,
        val expected: String
    )

    @Test
    fun testLanguageRoutingMatrix() {
        val testCases = listOf(
            // am inserts /am
            RoutingTestCase(
                baseUrl = "https://api.example.com",
                language = "am",
                path = EndpointPath.SEE_AND_DESCRIBE,
                variant = Variant.PLAIN,
                expected = "https://api.example.com/api/see-and-describe/am"
            ),
            // om inserts /om
            RoutingTestCase(
                baseUrl = "https://api.example.com",
                language = "om",
                path = EndpointPath.READ_TEXT,
                variant = Variant.PLAIN,
                expected = "https://api.example.com/api/read-text/om"
            ),
            // en omits language segment
            RoutingTestCase(
                baseUrl = "https://api.example.com",
                language = "en",
                path = EndpointPath.ASK_QUESTION,
                variant = Variant.PLAIN,
                expected = "https://api.example.com/api/ask-question"
            ),
            // Unknown language omits language segment
            RoutingTestCase(
                baseUrl = "https://api.example.com",
                language = "fr",
                path = EndpointPath.SEE_AND_DESCRIBE,
                variant = Variant.PLAIN,
                expected = "https://api.example.com/api/see-and-describe"
            ),
            // Base URL with a trailing slash is stripped cleanly
            RoutingTestCase(
                baseUrl = "https://api.example.com/",
                language = "am",
                path = EndpointPath.SEE_AND_DESCRIBE,
                variant = Variant.PLAIN,
                expected = "https://api.example.com/api/see-and-describe/am"
            ),
            // Base URL already containing the language: same language is idempotent
            RoutingTestCase(
                baseUrl = "https://api.example.com/api/see-and-describe/am",
                language = "am",
                path = EndpointPath.SEE_AND_DESCRIBE,
                variant = Variant.PLAIN,
                expected = "https://api.example.com/api/see-and-describe/am"
            ),
            // Base URL already containing a language is re-routed to new language
            RoutingTestCase(
                baseUrl = "https://api.example.com/api/see-and-describe/am",
                language = "om",
                path = EndpointPath.SEE_AND_DESCRIBE,
                variant = Variant.PLAIN,
                expected = "https://api.example.com/api/see-and-describe/om"
            ),
            // Already-streaming URL: same language and stream variant is idempotent
            RoutingTestCase(
                baseUrl = "https://api.example.com/api/see-and-describe/am/stream",
                language = "am",
                path = EndpointPath.SEE_AND_DESCRIBE,
                variant = Variant.STREAM,
                expected = "https://api.example.com/api/see-and-describe/am/stream"
            ),
            // Already-streaming URL re-routed to English stream
            RoutingTestCase(
                baseUrl = "https://api.example.com/api/see-and-describe/am/stream",
                language = "en",
                path = EndpointPath.SEE_AND_DESCRIBE,
                variant = Variant.STREAM,
                expected = "https://api.example.com/api/see-and-describe/stream"
            ),
            // AUDIO_STREAM variant
            RoutingTestCase(
                baseUrl = "https://api.example.com",
                language = "om",
                path = EndpointPath.READ_TEXT_AND_SIGNS,
                variant = Variant.AUDIO_STREAM,
                expected = "https://api.example.com/api/read-text-and-signs/om/audio-stream"
            )
        )

        for (case in testCases) {
            val actualFromFunction = resolve(case.baseUrl, case.language, case.path, case.variant)
            val actualFromObject = LanguageRouter.resolve(case.baseUrl, case.language, case.path, case.variant)
            assertEquals("Failed for case $case", case.expected, actualFromFunction)
            assertEquals("Failed for object call $case", case.expected, actualFromObject)
        }
    }
}
