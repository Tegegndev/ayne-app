package com.ayne.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayne.ui.components.AyneActionCard
import com.ayne.ui.components.AyneCompanionPanel
import com.ayne.ui.components.AyneTopBar
import com.ayne.viewmodel.AyneAction
import com.ayne.viewmodel.AyneUiState
import com.ayne.viewmodel.SupportedLanguage
import com.ayne.ui.theme.*

/**
 * Functional, high-contrast WCAG AAA UI screens for English and Afaan Oromoo.
 * Fully wired to live ViewModel actions, audio playback, camera capture, and multi-turn visual follow-ups.
 */

@Composable
fun EnglishMockScreen(
    uiState: AyneUiState = AyneUiState(selectedLanguage = SupportedLanguage.ENGLISH),
    onAction: (AyneAction) -> Unit = {},
    onDescribeClick: () -> Unit = {},
    onReadTextClick: () -> Unit = {},
    onAskQuestionClick: () -> Unit = {},
    onRepeatClick: () -> Unit = {},
    onLanguageSelected: (SupportedLanguage) -> Unit = {},
    onFlipCamera: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onHelpClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AynePureBlack)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // 1. Top Bar
            AyneTopBar(
                selectedLanguage = SupportedLanguage.ENGLISH,
                isFrontCamera = uiState.isFrontCamera,
                onLanguageSelected = onLanguageSelected,
                onFlipCamera = onFlipCamera,
                onSettingsClick = onSettingsClick,
                onHelpClick = onHelpClick
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Active Busy / Analysis Indicator
            AnimatedVisibility(
                visible = uiState.isBusy,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .border(2.dp, AyneVibrantYellow, RoundedCornerShape(12.dp))
                        .semantics {
                            contentDescription = uiState.busyMessage ?: "Analyzing"
                        },
                    color = AyneDarkCard,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = AyneVibrantYellow,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Working tone active",
                            tint = AyneVibrantYellow,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.busyMessage ?: "Analyzing scene… Please hold steady",
                            color = AyneVibrantYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // 3. Active Voice Recording Indicator Banner
            if (uiState.isRecordingVoice) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable { onAskQuestionClick() }
                        .border(2.dp, AyneVibrantYellow, RoundedCornerShape(12.dp))
                        .semantics {
                            role = Role.Button
                            contentDescription = if (uiState.liveTranscription != null) {
                                "Heard: ${uiState.liveTranscription}. Tap to submit."
                            } else {
                                "Microphone listening. Speak your question; it submits automatically when you pause."
                            }
                        }
                        .testTag("card_recording_voice_active_en"),
                    color = AyneDarkCard,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (uiState.heardSpeech) AynePureWhite else AyneVibrantYellow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = null,
                                tint = if (uiState.heardSpeech) AynePureBlack else AyneOnYellow,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (uiState.heardSpeech) "Heard you… Submitting when you pause"
                                       else "Listening… Speak your question. It submits automatically when you pause.",
                                color = AyneVibrantYellow,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 18.sp
                            )
                            if (!uiState.liveTranscription.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "You said: ${uiState.liveTranscription}",
                                    color = AynePureWhite,
                                    fontSize = 14.sp,
                                    lineHeight = 19.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // 4. Optical Read Result (when available)
            val readResp = uiState.lastReadResponse
            if (readResp != null && !uiState.isBusy) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .border(2.dp, AyneVibrantYellow, RoundedCornerShape(12.dp))
                        .semantics {
                            contentDescription = "Read Text Result: ${readResp.english.ifBlank { readResp.amharic }}"
                        },
                    color = AyneDarkCard,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = readResp.signType ?: "Document / Sign",
                                color = AyneVibrantYellow,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { onAction(AyneAction.DismissResult) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss result",
                                    tint = AynePureWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (!readResp.locationContext.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = AynePureWhite.copy(alpha = 0.8f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = readResp.locationContext,
                                    color = AynePureWhite.copy(alpha = 0.85f),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (readResp.english.isNotBlank()) readResp.english else readResp.amharic,
                            color = AynePureWhite,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (readResp.isCutOff || readResp.isBlurry) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AyneVibrantYellow.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .border(1.dp, AyneVibrantYellow, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = AyneVibrantYellow,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = readResp.cameraGuidance ?: if (readResp.isCutOff) "Text is cut off. Please move camera back." else "Image is blurry. Please hold steady.",
                                    color = AyneVibrantYellow,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            } else if (!uiState.lastAnswer.isNullOrBlank() && !uiState.isBusy) {
                // 5. Visual Description / Q&A Result Card
                val displayAnswer = uiState.lastEnglishAnswer?.takeIf { it.isNotBlank() } ?: uiState.lastAnswer ?: ""
                val secondaryAmharic = if (uiState.lastEnglishAnswer?.isNotBlank() == true) uiState.lastAnswer else null
                val hasTranscript = !uiState.liveTranscription.isNullOrBlank()

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .border(1.dp, AyneVibrantYellow.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .semantics {
                            contentDescription = if (hasTranscript) {
                                "You said: ${uiState.liveTranscription}. Answer: $displayAnswer"
                            } else {
                                "Last answer: $displayAnswer"
                            }
                        },
                    color = AyneDarkCard,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (hasTranscript) "Question & Answer" else "Surroundings",
                                color = AyneVibrantYellow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { onAction(AyneAction.DismissResult) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss result",
                                    tint = AynePureWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // What the user said (server-side speech-to-text)
                        if (hasTranscript) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AynePureBlack, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = null,
                                    tint = AyneVibrantYellow,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "You said: ${uiState.liveTranscription}",
                                    color = AynePureWhite.copy(alpha = 0.9f),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        Text(
                            text = displayAnswer,
                            color = AynePureWhite,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (!secondaryAmharic.isNullOrBlank() && secondaryAmharic != displayAnswer) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AynePureBlack,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = secondaryAmharic,
                                    color = AynePureWhite.copy(alpha = 0.85f),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Audio indicator / Tap to replay
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onRepeatClick()
                                }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(AyneVibrantYellow),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Play spoken audio",
                                    tint = AyneOnYellow,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Spoken audio ready · Tap to replay",
                                color = AyneVibrantYellow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 6. Three Stacked Action Cards (Wired directly to active camera & API)
            if (uiState.isCompanionMode) {
                AyneCompanionPanel(
                    uiState = uiState,
                    onAction = onAction,
                    onSpeakClick = onAskQuestionClick
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AyneActionCard(
                        icon = Icons.Filled.PhotoCamera,
                        title = "Describe Surroundings",
                        subtitle = "Explore what's around you",
                        contentDescription = "Describe surroundings. Explore what is around you button",
                        testTag = "card_describe_surroundings_en",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDescribeClick()
                        }
                    )

                    AyneActionCard(
                        icon = Icons.Filled.Description,
                        title = "Read Text",
                        subtitle = "Read signs, documents & labels",
                        contentDescription = "Read text. Read signs, documents and labels button",
                        testTag = "card_read_text_en",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onReadTextClick()
                        }
                    )

                    val askSubtitle = when {
                        uiState.isRecordingVoice -> "Listening… Auto-submits when you pause"
                        else -> "Visual Q&A · Tap to ask or press Volume−"
                    }

                    AyneActionCard(
                        icon = Icons.Filled.QuestionAnswer,
                        title = "Ask Question",
                        subtitle = askSubtitle,
                        contentDescription = "Ask question. Tap to ask or press Volume minus.",
                        testTag = "card_ask_question_en",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAskQuestionClick()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 7. Bottom Controls
        CleanLanguageBottomControls(
            buttonLabel = "Repeat Last Answer",
            volumeUpHint = "Press Volume+ to take a photo",
            volumeDownHint = "Press Volume− to ask follow-up",
            onRepeatClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onRepeatClick()
            }
        )
    }
}

@Composable
fun AfaanOromooMockScreen(
    uiState: AyneUiState = AyneUiState(selectedLanguage = SupportedLanguage.AFAN_OROMOO),
    onAction: (AyneAction) -> Unit = {},
    onDescribeClick: () -> Unit = {},
    onReadTextClick: () -> Unit = {},
    onAskQuestionClick: () -> Unit = {},
    onRepeatClick: () -> Unit = {},
    onLanguageSelected: (SupportedLanguage) -> Unit = {},
    onFlipCamera: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onHelpClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AynePureBlack)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // 1. Top Bar
            AyneTopBar(
                selectedLanguage = SupportedLanguage.AFAN_OROMOO,
                isFrontCamera = uiState.isFrontCamera,
                onLanguageSelected = onLanguageSelected,
                onFlipCamera = onFlipCamera,
                onSettingsClick = onSettingsClick,
                onHelpClick = onHelpClick
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Active Busy / Analysis Indicator
            AnimatedVisibility(
                visible = uiState.isBusy,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .border(2.dp, AyneVibrantYellow, RoundedCornerShape(12.dp))
                        .semantics {
                            contentDescription = uiState.busyMessage ?: "Xiinxalaa jira"
                        },
                    color = AyneDarkCard,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = AyneVibrantYellow,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Sagalee xiinxallaa",
                            tint = AyneVibrantYellow,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.busyMessage ?: "Naannoo xiinxalaa jira… Suuraa qabadhaa",
                            color = AyneVibrantYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // 3. Active Voice Recording Indicator Banner
            if (uiState.isRecordingVoice) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable { onAskQuestionClick() }
                        .border(2.dp, AyneVibrantYellow, RoundedCornerShape(12.dp))
                        .semantics {
                            role = Role.Button
                            contentDescription = if (uiState.liveTranscription != null) {
                                "Kan dhaggeeffame: ${uiState.liveTranscription}. Galchiuf tuqi."
                            } else {
                                "Maayikirofoonni dhaggeeffachaa jira. Gaaffii dubbadhu; yeroo dhaabdu ofumaan ni ergama."
                            }
                        }
                        .testTag("card_recording_voice_active_om"),
                    color = AyneDarkCard,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (uiState.heardSpeech) AynePureWhite else AyneVibrantYellow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = null,
                                tint = if (uiState.heardSpeech) AynePureBlack else AyneOnYellow,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (uiState.heardSpeech) "Si dhaga'een… Yeroo dhaabdu ergaa"
                                       else "Dhaggeeffachaa jira… Gaaffii keessan dubbadhaa. Yeroo dhaabdu ofumaan ni ergama.",
                                color = AyneVibrantYellow,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 18.sp
                            )
                            if (!uiState.liveTranscription.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Atsi jedheete: ${uiState.liveTranscription}",
                                    color = AynePureWhite,
                                    fontSize = 14.sp,
                                    lineHeight = 19.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // 4. Optical Read Result (when available)
            val readResp = uiState.lastReadResponse
            if (readResp != null && !uiState.isBusy) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .border(2.dp, AyneVibrantYellow, RoundedCornerShape(12.dp))
                        .semantics {
                            contentDescription = "Bu'aa barruu: ${readResp.oromo.ifBlank { readResp.amharic }.ifBlank { readResp.english }}"
                        },
                    color = AyneDarkCard,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = readResp.signType ?: "Asxaa / Waraqaa",
                                color = AyneVibrantYellow,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { onAction(AyneAction.DismissResult) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Cufi",
                                    tint = AynePureWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (!readResp.locationContext.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = AynePureWhite.copy(alpha = 0.8f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = readResp.locationContext,
                                    color = AynePureWhite.copy(alpha = 0.85f),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = readResp.oromo.ifBlank { readResp.amharic }.ifBlank { readResp.english },
                            color = AynePureWhite,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (readResp.isCutOff || readResp.isBlurry) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AyneVibrantYellow.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .border(1.dp, AyneVibrantYellow, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = AyneVibrantYellow,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = readResp.cameraGuidance ?: "Barruun murameera. Kaameeraa gara duubatti siqsaa.",
                                    color = AyneVibrantYellow,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            } else if (!uiState.lastAnswer.isNullOrBlank() && !uiState.isBusy) {
                // 5. Visual Description / Q&A Result Card
                val hasTranscript = !uiState.liveTranscription.isNullOrBlank()

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .border(1.dp, AyneVibrantYellow.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .semantics {
                            contentDescription = if (hasTranscript) {
                                "Atsi jedheete: ${uiState.liveTranscription}. Deebii: ${uiState.lastAnswer}"
                            } else {
                                "Deebii: ${uiState.lastAnswer}"
                            }
                        },
                    color = AyneDarkCard,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (hasTranscript) "Gaaffii fi Deebii" else "Naannoo",
                                color = AyneVibrantYellow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { onAction(AyneAction.DismissResult) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Cufi",
                                    tint = AynePureWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // What the user said (server-side speech-to-text)
                        if (hasTranscript) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AynePureBlack, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = null,
                                    tint = AyneVibrantYellow,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Atsi jedheete: ${uiState.liveTranscription}",
                                    color = AynePureWhite.copy(alpha = 0.9f),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        Text(
                            text = uiState.lastAnswer ?: "",
                            color = AynePureWhite,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (!uiState.lastEnglishAnswer.isNullOrBlank() && uiState.lastEnglishAnswer != uiState.lastAnswer) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AynePureBlack,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = uiState.lastEnglishAnswer ?: "",
                                    color = AynePureWhite.copy(alpha = 0.85f),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Audio indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onRepeatClick()
                                }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(AyneVibrantYellow),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Sagalee dhaggeeffadhu",
                                    tint = AyneOnYellow,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sagaleen qophaa'eera · Irra deebi'i",
                                color = AyneVibrantYellow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 6. Three Stacked Action Cards (Wired directly to active camera & API)
            if (uiState.isCompanionMode) {
                AyneCompanionPanel(
                    uiState = uiState,
                    onAction = onAction,
                    onSpeakClick = onAskQuestionClick
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AyneActionCard(
                        icon = Icons.Filled.PhotoCamera,
                        title = "Naannoo Ibsi",
                        subtitle = "Wanta naannoo keetti jiru bari",
                        contentDescription = "Naannoo ibsi. Wanta naannoo keetti jiru baruuf tuqi",
                        testTag = "card_describe_surroundings_om",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDescribeClick()
                        }
                    )

                    AyneActionCard(
                        icon = Icons.Filled.Description,
                        title = "Barruu Dubbisi",
                        subtitle = "Asxaa, qoricha fi barruulee dubbisi",
                        contentDescription = "Barruu dubbisi. Asxaa fi barruulee dubbisuuf tuqi",
                        testTag = "card_read_text_om",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onReadTextClick()
                        }
                    )

                    val askSubtitle = when {
                        uiState.isRecordingVoice -> "Dhaggeeffachaa jira… Dhaabi"
                        else -> "Gaaffii fi qorannoo · Tuqi ykn Volume− dhiibi"
                    }

                    AyneActionCard(
                        icon = Icons.Filled.QuestionAnswer,
                        title = "Gaaffii Gaafadhu",
                        subtitle = askSubtitle,
                        contentDescription = "Gaaffii gaafadhu. Tuqi ykn Volume minus dhiibi",
                        testTag = "card_ask_question_om",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAskQuestionClick()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 7. Bottom Controls
        CleanLanguageBottomControls(
            buttonLabel = "Deebii Irra Deebi'i",
            volumeUpHint = "Volume+ suuraa kaasuuf",
            volumeDownHint = "Volume− gaaffii dabalataa gaafachuuf",
            onRepeatClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onRepeatClick()
            }
        )
    }
}

// ==========================================
// Reusable Bottom Controls (Matches AyneBottomControls)
// ==========================================

@Composable
private fun CleanLanguageBottomControls(
    buttonLabel: String,
    volumeUpHint: String,
    volumeDownHint: String,
    onRepeatClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onRepeatClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = buttonLabel
                },
            colors = ButtonDefaults.buttonColors(
                containerColor = AyneVibrantYellow,
                contentColor = AyneOnYellow
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = AyneOnYellow,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = buttonLabel,
                style = MaterialTheme.typography.titleLarge,
                color = AyneOnYellow,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = volumeUpHint,
            style = MaterialTheme.typography.bodySmall,
            color = AynePureWhite.copy(alpha = 0.7f),
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = volumeDownHint,
            style = MaterialTheme.typography.bodySmall,
            color = AynePureWhite.copy(alpha = 0.7f),
            fontSize = 11.sp
        )
    }
}

// ==========================================
// Previews
// ==========================================

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
fun EnglishMockScreenPreview() {
    AyneTheme {
        EnglishMockScreen()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
fun AfaanOromooMockScreenPreview() {
    AyneTheme {
        AfaanOromooMockScreen()
    }
}
