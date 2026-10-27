package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
}
