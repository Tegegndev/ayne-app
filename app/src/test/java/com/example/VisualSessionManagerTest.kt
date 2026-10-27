package com.example

import com.example.data.ConversationTurn
import com.example.data.VisualSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VisualSessionManagerTest {

    @Test
    fun testStartNewSceneLocksImageAndClearsHistory() {
        val sessionManager = VisualSessionManager()
        sessionManager.setLockedFrame("initial_image_base64")
        sessionManager.conversationHistory.add(ConversationTurn("user", "Old query"))
        sessionManager.conversationHistory.add(ConversationTurn("model", "Old response"))

        assertEquals(2, sessionManager.conversationHistory.size)
        assertNotNull(sessionManager.lockedImageBase64)

        // Clear session
        sessionManager.clearSession()
        assertNull(sessionManager.lockedImageBase64)
        assertEquals(0, sessionManager.conversationHistory.size)
    }

    @Test
    fun testAskFollowUpRequiresLockedImage() {
        var errorMessage: String? = null
        val sessionManager = VisualSessionManager(
            onError = { errorMessage = it }
        )

        // Try asking follow up without locked image
        sessionManager.askFollowUpQuestion(userTextQuery = "What color is the cup?")

        assertEquals("No active scene in memory. Press Volume+ first to take a picture.", errorMessage)
    }

    @Test
    fun testConversationTurnDataModel() {
        val turn = ConversationTurn("user", "ስኒዎቹ ምን ቀለም ናቸው?")
        assertEquals("user", turn.role)
        assertEquals("ስኒዎቹ ምን ቀለም ናቸው?", turn.text)
    }
}
