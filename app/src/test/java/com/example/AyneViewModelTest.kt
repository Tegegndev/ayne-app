package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AyneViewModelTest {

    @Test
    fun testInitialStateAndActiveSessionId() {
        val viewModel = AyneViewModel()
        assertNull(viewModel.getActiveSessionId())
        assertNull(viewModel.uiState.value.activeSessionId)
        assertFalse(viewModel.isSpeaking())
    }

    @Test
    fun testResetVisualSessionClearsActiveSessionId() {
        val viewModel = AyneViewModel()
        viewModel.resetVisualSession()
        assertNull(viewModel.getActiveSessionId())
        assertNull(viewModel.uiState.value.activeSessionId)
    }

    @Test
    fun testBargeInStopsAudioAndSpeakingState() {
        val viewModel = AyneViewModel()
        viewModel.bargeIn()
        assertFalse(viewModel.isSpeaking())
    }

    @Test
    fun testUpdateEndpointsActionWithBaseUrl() {
        val viewModel = AyneViewModel()
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val customBase = "https://custom-server.app"
        viewModel.onAction(
            context,
            AyneAction.UpdateEndpoints(
                baseUrl = customBase,
                visionUrl = "$customBase/api/see-and-describe",
                readTextUrl = "$customBase/api/read-text",
                askQuestionUrl = "$customBase/api/ask-question"
            )
        )
        val state = viewModel.uiState.value
        assertEquals("https://custom-server.app", state.baseUrl)
        assertEquals("https://custom-server.app/api/see-and-describe", state.apiEndpoint)
        assertEquals("https://custom-server.app/api/read-text", state.readTextEndpoint)
        assertEquals("https://custom-server.app/api/ask-question", state.askQuestionEndpoint)
    }

    @Test
    fun testEnterCompanionModeGreetsUser() {
        val viewModel = AyneViewModel()
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        assertFalse(viewModel.uiState.value.isCompanionMode)

        viewModel.onAction(context, AyneAction.EnterCompanionMode)

        val state = viewModel.uiState.value
        org.junit.Assert.assertTrue(state.isCompanionMode)
        org.junit.Assert.assertNotNull(state.companionGreeting)
        org.junit.Assert.assertTrue(state.companionGreeting!!.contains("ሰላም") || state.companionGreeting!!.contains("ረዳት"))
        assertEquals(state.companionGreeting, state.lastAnswer)
    }

    @Test
    fun testExitCompanionMode() {
        val viewModel = AyneViewModel()
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        viewModel.onAction(context, AyneAction.EnterCompanionMode)
        org.junit.Assert.assertTrue(viewModel.uiState.value.isCompanionMode)

        viewModel.onAction(context, AyneAction.ExitCompanionMode)
        assertFalse(viewModel.uiState.value.isCompanionMode)
    }

    @Test
    fun testAskQuestionEntersCompanionModeWhenInactive() {
        val viewModel = AyneViewModel()
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        assertFalse(viewModel.uiState.value.isCompanionMode)

        // When user taps Ask Question, it should enter Companion mode and greet the user
        viewModel.onAction(context, AyneAction.AskQuestion)
        org.junit.Assert.assertTrue(viewModel.uiState.value.isCompanionMode)
        org.junit.Assert.assertNotNull(viewModel.uiState.value.companionGreeting)
    }

    @Test
    fun testGenerateCompanionFallbackAnswers() {
        val jokeAm = generateCompanionFallbackAnswer("ቀልድ ንገረኝ", SupportedLanguage.AMHARIC)
        org.junit.Assert.assertTrue(jokeAm.contains("ሰውዬ") || jokeAm.contains("ምንጣፍ"))

        val jokeEn = generateCompanionFallbackAnswer("Tell me a funny joke", SupportedLanguage.ENGLISH)
        org.junit.Assert.assertTrue(jokeEn.contains("atoms"))

        val factAm = generateCompanionFallbackAnswer("አስደናቂ እውነታ", SupportedLanguage.AMHARIC)
        org.junit.Assert.assertTrue(factAm.contains("ኢትዮጵያ") || factAm.contains("ቡና"))

        val newsEn = generateCompanionFallbackAnswer("Latest news", SupportedLanguage.ENGLISH)
        org.junit.Assert.assertTrue(newsEn.contains("AI") || newsEn.contains("Update"))

        val jokeOm = generateCompanionFallbackAnswer("qoosaa natti himi", SupportedLanguage.AFAN_OROMOO)
        org.junit.Assert.assertTrue(jokeOm.contains("Namichi") || jokeOm.contains("falaasaa"))
    }

    @Test
    fun testSelectAfaanOromooLanguageDefaultsToAyyaantuuVoice() {
        val viewModel = AyneViewModel()
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        assertEquals(SupportedLanguage.AMHARIC, viewModel.uiState.value.selectedLanguage)
        assertEquals("am-hamen", viewModel.uiState.value.customVoice)

        viewModel.onAction(context, AyneAction.SelectLanguage(SupportedLanguage.AFAN_OROMOO))

        val state = viewModel.uiState.value
        assertEquals(SupportedLanguage.AFAN_OROMOO, state.selectedLanguage)
        assertEquals("om-ayyaantuu", state.customVoice)
    }

    @Test
    fun testStandaloneAskQuestionStateAndReset() {
        val viewModel = AyneViewModel()
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        assertFalse(viewModel.uiState.value.isAskQuestionOpen)
        assertTrue(viewModel.uiState.value.askQuestionHistory.isEmpty())

        viewModel.onAction(context, AyneAction.OpenAskQuestion())
        assertTrue(viewModel.uiState.value.isAskQuestionOpen)

        viewModel.onAction(context, AyneAction.ClearAskQuestionHistory)
        assertTrue(viewModel.uiState.value.askQuestionHistory.isEmpty())
        assertNull(viewModel.uiState.value.askQuestionSessionId)

        viewModel.onAction(context, AyneAction.CloseAskQuestion)
        assertFalse(viewModel.uiState.value.isAskQuestionOpen)
    }
}
