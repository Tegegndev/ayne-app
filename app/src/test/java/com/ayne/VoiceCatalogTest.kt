package com.ayne

import com.ayne.viewmodel.SupportedLanguage
import com.ayne.viewmodel.VoiceCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCatalogTest {

    @Test
    fun `every language has a non-empty voice list`() {
        SupportedLanguage.entries.forEach { language ->
            val voices = VoiceCatalog.voicesFor(language)
            assertTrue("No voices for $language", voices.isNotEmpty())
        }
    }

    @Test
    fun `each language default belongs to its own catalog`() {
        SupportedLanguage.entries.forEach { language ->
            val default = VoiceCatalog.defaultVoiceFor(language)
            assertTrue(
                "Default voice '$default' is not in ${language} catalog",
                VoiceCatalog.belongsTo(default, language)
            )
        }
    }

    @Test
    fun `voice ids are unique across the whole catalog`() {
        val all = SupportedLanguage.entries.flatMap { VoiceCatalog.voicesFor(it) }.map { it.id }
        assertEquals("Duplicate voice ids: ${all.groupBy { it }.filterValues { it.size > 1 }.keys}",
            all.size, all.toSet().size)
    }

    @Test
    fun `a voice from one language does not belong to another`() {
        val amharicVoice = VoiceCatalog.defaultVoiceFor(SupportedLanguage.AMHARIC)
        assertFalse(VoiceCatalog.belongsTo(amharicVoice, SupportedLanguage.AFAN_OROMOO))
        assertFalse(VoiceCatalog.belongsTo(amharicVoice, SupportedLanguage.ENGLISH))
    }

    @Test
    fun `saved amharic voice survives restore check but oromo voice does not`() {
        assertTrue(VoiceCatalog.belongsTo("am-simon", SupportedLanguage.AMHARIC))
        assertFalse(VoiceCatalog.belongsTo("om-bikila", SupportedLanguage.AMHARIC))
    }
}
