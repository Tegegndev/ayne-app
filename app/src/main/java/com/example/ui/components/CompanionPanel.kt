package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.viewmodel.AyneAction
import com.example.viewmodel.AyneUiState

@Composable
fun AyneCompanionPanel(
    uiState: AyneUiState,
    onAction: (AyneAction) -> Unit,
    onSpeakClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AyneAskQuestionPanel(
        uiState = uiState,
        onAction = onAction,
        onSpeakClick = onSpeakClick,
        modifier = modifier
    )
}
