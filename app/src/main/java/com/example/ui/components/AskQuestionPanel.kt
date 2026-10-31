package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.ui.VoiceAskScreen
import com.example.viewmodel.AyneAction
import com.example.viewmodel.AyneUiState

@Composable
fun AyneAskQuestionPanel(
    uiState: AyneUiState,
    onAction: (AyneAction) -> Unit,
    onSpeakClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    VoiceAskScreen(
        uiState = uiState,
        onClose = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onAction(AyneAction.ExitCompanionMode)
        },
        onNewChat = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onAction(AyneAction.ClearAskQuestionHistory)
        },
        onMicTap = onSpeakClick,
        modifier = modifier
    )
}
