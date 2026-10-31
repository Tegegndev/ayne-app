package com.ayne

import com.ayne.data.ConversationTurn
import com.ayne.data.VisionRepository
import com.ayne.data.VisualSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualSessionManagerTest {

    @Test
    fun testStartNewSceneLocksImageAndClearsHistoryAndActiveSession() {
        val sessionManager = VisualSessionManager()
        sessionManager.setLockedFrame("initial_image_base64")
        sessionManager.setActiveSessionId("session_12345")
        sessionManager.conversationHistory.add(ConversationTurn("user", "Old query"))
        sessionManager.conversationHistory.add(ConversationTurn("model", "Old response"))

        assertEquals(2, sessionManager.conversationHistory.size)
        assertNotNull(sessionManager.lockedImageBase64)
        assertEquals("session_12345", sessionManager.activeSessionId)

        // Clear session (Start over / "አዲስ ጀምር")
        sessionManager.clearSession()
        assertNull(sessionManager.lockedImageBase64)
        assertNull(sessionManager.activeSessionId)
        assertEquals(0, sessionManager.conversationHistory.size)
    }

    @Test
    fun testAskFollowUpRequiresLockedImageOrActiveSessionId() {
        var errorMessage: String? = null
        val sessionManager = VisualSessionManager(
            onError = { errorMessage = it }
        )

        // Try asking follow up without locked image or activeSessionId
        sessionManager.askFollowUpQuestion(userTextQuery = "What color is the cup?")

        assertEquals("No active scene in memory. Press Volume+ first to take a picture.", errorMessage)
    }

    @Test
    fun testConversationTurnDataModel() {
        val turn = ConversationTurn("user", "ስኒዎቹ ምን ቀለም ናቸው?")
        assertEquals("user", turn.role)
        assertEquals("ስኒዎቹ ምን ቀለም ናቸው?", turn.text)
    }

    @Test
    fun testVisionRepositorySessionManagement() {
        val repo = VisionRepository("https://test.ayne.ai")
        assertNull(repo.getActiveSessionId())

        repo.setActiveSessionId("test_session_abc")
        assertEquals("test_session_abc", repo.getActiveSessionId())

        repo.resetVisualSession()
        assertNull(repo.getActiveSessionId())
    }
}
