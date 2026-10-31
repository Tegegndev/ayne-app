package com.ayne

import com.ayne.data.AddisRealtimeEvent
import com.ayne.data.AddisRealtimeSseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the Addis AI Realtime Mode B SSE line parser against the exact
 * event shapes observed on the production stream (2026-10-30 verification).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddisRealtimeSseParserTest {

    private fun parseAll(script: String): List<AddisRealtimeEvent> {
        val parser = AddisRealtimeSseParser()
        return script.lines().mapNotNull { parser.feed(it) }
    }

    @Test
    fun `full production turn parses into the documented event lifecycle`() {
        val script = """
            event: ready
            data: {"model":"አሌፍ-1.2-realtime-audio","ready":true}

            event: transcript
            data: {"role":"user","text":"ሰላም","finished":true}

            event: transcript
            data: {"role":"assistant","text":"እንኳን ደህና መጡ!","finished":true}

            event: audio
            data: {"data":"AAAbAP/zZMQA","sample_rate":24000,"format":"mp3","chunkIndex":1}

            event: audio
            data: {"data":"4e66676k","sample_rate":24000,"format":"mp3","chunkIndex":2}

            event: turn_complete
            data: {"finish_reason":"completed","format":"mp3","duration_seconds":0.65}

            event: done
            data: {"finished":true}
        """.trimIndent()

        val events = parseAll(script)

        assertEquals(7, events.size)
        assertTrue(events[0] is AddisRealtimeEvent.Ready)
        assertEquals("አሌፍ-1.2-realtime-audio", (events[0] as AddisRealtimeEvent.Ready).model)

        val userTranscript = events[1] as AddisRealtimeEvent.Transcript
        assertEquals("user", userTranscript.role)
        assertEquals("ሰላም", userTranscript.text)
        assertTrue(!userTranscript.noSpeechDetected)

        val assistantTranscript = events[2] as AddisRealtimeEvent.Transcript
        assertEquals("assistant", assistantTranscript.role)

        val audio1 = events[3] as AddisRealtimeEvent.Audio
        assertEquals("mp3", audio1.format)
        assertEquals("AAAbAP/zZMQA", audio1.base64Data)

        assertTrue(events[4] is AddisRealtimeEvent.Audio)
        assertTrue((events[5] as AddisRealtimeEvent.TurnComplete).rawJson.contains("finish_reason"))
        assertEquals(AddisRealtimeEvent.Done, events[6])
    }

    @Test
    fun `noSpeechDetected flag is surfaced for silent input`() {
        val script = """
            event: ready
            data: {"model":"m","ready":true}

            event: transcript
            data: {"role":"user","text":"","finished":true,"noSpeechDetected":true}
        """.trimIndent()

        val events = parseAll(script)
        val transcript = events[1] as AddisRealtimeEvent.Transcript
        assertTrue(transcript.noSpeechDetected)
        assertEquals("", transcript.text)
    }

    @Test
    fun `audio events with blank data are dropped`() {
        val script = """
            event: audio
            data: {"data":"","sample_rate":24000,"format":"mp3","chunkIndex":7}
        """.trimIndent()

        assertTrue(parseAll(script).isEmpty())
    }

    @Test
    fun `malformed json data lines are ignored without crashing`() {
        val parser = AddisRealtimeSseParser()
        assertNull(parser.feed("event: audio"))
        assertNull(parser.feed("data: not-json-at-all {{{"))
        assertNull(parser.feed(""))
        assertNull(parser.feed(": keep-alive comment"))
        assertNull(parser.feed("event: unknown_event"))
        assertNull(parser.feed("data: {}"))
    }

    @Test
    fun `no-space event and data prefixes still parse`() {
        val script = """
            event:transcript
            data:{"role":"user","text":"ሁላም","finished":true}
        """.trimIndent()

        val events = parseAll(script)
        assertEquals(1, events.size)
        assertEquals("ሁላም", (events[0] as AddisRealtimeEvent.Transcript).text)
    }

    @Test
    fun `parser keeps listening across events without reset issues`() {
        val parser = AddisRealtimeSseParser()
        assertNull(parser.feed("event: ready"))
        assertTrue(parser.feed("data: {\"ready\":true}") is AddisRealtimeEvent.Ready)
        // Next event line arrives for the following event.
        assertNull(parser.feed("event: done"))
        assertEquals(AddisRealtimeEvent.Done, parser.feed("data: {\"finished\":true}"))
    }
}
