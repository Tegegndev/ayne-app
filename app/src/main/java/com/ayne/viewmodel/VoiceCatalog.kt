package com.ayne.viewmodel

/**
 * Single source of truth for the TTS voices the app offers, grouped by
 * language. The settings dialog renders only the list for the user's
 * selected language, and launch flows take [defaultVoiceFor] so a chosen
 * language always starts with a voice that can speak it.
 *
 * Voice IDs follow the provider catalog: Addis AI uses "am-*" / "om-*"
 * prefixes; English uses Gemini neural voice names.
 */
object VoiceCatalog {
    data class VoiceOption(val id: String, val label: String)

    val AMHARIC_VOICES = listOf(
        VoiceOption("am-hamen", "am-hamen · Addis Native Amharic, Female"),
        VoiceOption("am-simon", "am-simon · Addis Native Amharic, Male"),
        VoiceOption("am-yohanes-calm", "am-yohanes-calm · Addis Calm Narrative"),
        VoiceOption("am-tesfa", "am-tesfa · Addis Energetic Daily")
    )

    val OROMO_VOICES = listOf(
        VoiceOption("om-ayyaantuu", "om-ayyaantuu · Addis Native Oromo, Female"),
        VoiceOption("om-bikila", "om-bikila · Addis Native Oromo, Male"),
        VoiceOption("om-leensaa", "om-leensaa · Addis Native Oromo, Narrative"),
        VoiceOption("om-diriba", "om-diriba · Addis Native Oromo, Clear")
    )

    val ENGLISH_VOICES = listOf(
        VoiceOption("Zephyr", "Zephyr · Gemini Neural, Fast Clear"),
        VoiceOption("Aoede", "Aoede · Gemini Neural, Warm Reading"),
        VoiceOption("Puck", "Puck · Gemini Neural, Upbeat"),
        VoiceOption("Fenrir", "Fenrir · Gemini Neural, Calm")
    )

    /** Voices available for [language], in display order. */
    fun voicesFor(language: SupportedLanguage): List<VoiceOption> = when (language) {
        SupportedLanguage.AMHARIC -> AMHARIC_VOICES
        SupportedLanguage.AFAN_OROMOO -> OROMO_VOICES
        SupportedLanguage.ENGLISH -> ENGLISH_VOICES
    }

    /** Voice a language starts with when the user has not chosen one. */
    fun defaultVoiceFor(language: SupportedLanguage): String = when (language) {
        SupportedLanguage.AMHARIC -> "am-hamen"
        SupportedLanguage.AFAN_OROMOO -> "om-ayyaantuu"
        SupportedLanguage.ENGLISH -> "Zephyr"
    }

    /** True when [voiceId] belongs to [language]'s catalog. */
    fun belongsTo(voiceId: String, language: SupportedLanguage): Boolean =
        voicesFor(language).any { it.id == voiceId }
}
