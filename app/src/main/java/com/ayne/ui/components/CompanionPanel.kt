package com.ayne.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ayne.viewmodel.AyneAction
import com.ayne.viewmodel.AyneUiState

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
