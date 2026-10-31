package com.example.data.endpoints

enum class EndpointPath(val segment: String) {
    SEE_AND_DESCRIBE("see-and-describe"),
    READ_TEXT("read-text"),
    ASK_QUESTION("ask-question"),
    READ_TEXT_AND_SIGNS("read-text-and-signs")
}

enum class Variant(val suffix: String) {
    PLAIN(""),
    STREAM("/stream"),
    AUDIO_STREAM("/audio-stream")
}

object LanguageRouter {
    fun resolve(
        baseUrl: String,
        language: String,
        path: EndpointPath,
        variant: Variant = Variant.PLAIN
    ): String = com.example.data.endpoints.resolve(baseUrl, language, path, variant)
}

fun resolve(
    baseUrl: String,
    language: String,
    path: EndpointPath,
    variant: Variant = Variant.PLAIN
): String {
    val trimmed = baseUrl.trim()
    if (trimmed.isEmpty()) return ""

    val normalizedLanguage = when (language.lowercase().trim()) {
        "am", "amharic" -> "am"
        "om", "oromo" -> "om"
        else -> ""
    }

    val expectedLanguageSegment = if (normalizedLanguage.isNotEmpty()) "/$normalizedLanguage" else ""
    val expectedTail = "/api/${path.segment}$expectedLanguageSegment${variant.suffix}"

    if (trimmed == expectedTail || trimmed.endsWith(expectedTail)) {
        return trimmed
    }

    var root = trimmed.trimEnd('/')

    for (v in Variant.entries) {
        if (v.suffix.isNotEmpty() && root.endsWith(v.suffix)) {
            root = root.removeSuffix(v.suffix)
            break
        }
    }

    val knownLanguages = listOf("/am", "/om", "/en")
    for (lang in knownLanguages) {
        if (root.endsWith(lang)) {
            root = root.removeSuffix(lang)
            break
        }
    }

    for (ep in EndpointPath.entries) {
        if (root.endsWith("/${ep.segment}")) {
            root = root.removeSuffix("/${ep.segment}")
            break
        }
    }

    if (root.endsWith("/api")) {
        root = root.removeSuffix("/api")
    }

    val finalRoot = root.trimEnd('/')
    return "$finalRoot$expectedTail"
}
