package com.example.ui

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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.AyneAction
import com.example.AyneActionCard
import com.example.AyneTopBar
import com.example.SupportedLanguage
import com.example.ui.theme.*

/**
 * Clean UI screens for English and Afaan Oromoo matching the Amharic version.
 * High-contrast WCAG AAA design, large touch targets, zero clutter.
 */

@Composable
fun EnglishMockScreen(
    isFrontCamera: Boolean,
    onAction: (AyneAction) -> Unit,
    onLanguageSelected: (SupportedLanguage) -> Unit,
    onFlipCamera: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()
    var showMockResult by remember { mutableStateOf(true) }

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
                isFrontCamera = isFrontCamera,
                onLanguageSelected = onLanguageSelected,
                onFlipCamera = onFlipCamera,
                onSettingsClick = onSettingsClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Result Card (Matches Amharic Version exactly)
            if (showMockResult) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .border(1.dp, AyneVibrantYellow.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .semantics {
                            contentDescription = "Last answer: On the table there is a white coffee cup, a dark red notebook, and a 100 Birr banknote next to a set of keys."
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
                                text = "Surroundings",
                                color = AyneVibrantYellow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showMockResult = false
                                },
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

                        Text(
                            text = "On the table in front of you, there is a white coffee cup, a dark red notebook, and a 100 Birr banknote next to a set of keys.",
                            color = AynePureWhite,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Secondary Translation (Amharic)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AynePureBlack,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "(አማርኛ) በጠረጴዛው ላይ ባለ ነጭ የቡና ስኒ፣ ደማቅ ቀይ ማስታወሻ ደብተር እና የ100 ብር ኖት ከመክፈቻ ቁልፍ አጠገብ አለ።",
                                color = AynePureWhite.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Audio indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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

            // 3. Three Stacked Action Cards (Exactly like Amharic Version)
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
                        showMockResult = true
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
                        showMockResult = true
                    }
                )

                AyneActionCard(
                    icon = Icons.Filled.Mic,
                    title = "Ask Question",
                    subtitle = "Ask about the captured scene",
                    contentDescription = "Ask question. Ask about the captured scene button",
                    testTag = "card_ask_question_en",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMockResult = true
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. Bottom Controls (Exactly like Amharic Version)
        CleanLanguageBottomControls(
            buttonLabel = "Repeat Last Answer",
            volumeUpHint = "Press Volume+ to take a photo",
            volumeDownHint = "Press Volume− to ask follow-up",
            onRepeatClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showMockResult = true
            }
        )
    }
}

@Composable
fun AfaanOromooMockScreen(
    isFrontCamera: Boolean,
    onAction: (AyneAction) -> Unit,
    onLanguageSelected: (SupportedLanguage) -> Unit,
    onFlipCamera: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()
    var showMockResult by remember { mutableStateOf(true) }

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
                isFrontCamera = isFrontCamera,
                onLanguageSelected = onLanguageSelected,
                onFlipCamera = onFlipCamera,
                onSettingsClick = onSettingsClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Result Card (Matches Amharic Version exactly)
            if (showMockResult) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .border(1.dp, AyneVibrantYellow.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .semantics {
                            contentDescription = "Deebii: Minjaala irra kubbayyaa bunaa adii, dabtara nootii bifa diimaa dukkanaawaa fi maallaqa qarshii 100 banaa qulfii cinaa jiru argina."
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
                                text = "Naannoo",
                                color = AyneVibrantYellow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showMockResult = false
                                },
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

                        Text(
                            text = "Minjaala irra kubbayyaa bunaa adii, dabtara nootii bifa diimaa dukkanaawaa fi maallaqa qarshii 100 banaa qulfii cinaa jiru argina.",
                            color = AynePureWhite,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Secondary Translation (English)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AynePureBlack,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "(English) On the table in front of you, there is a white coffee cup, a dark red notebook, and a 100 Birr banknote.",
                                color = AynePureWhite.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Audio indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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

            // 3. Three Stacked Action Cards (Exactly like Amharic Version)
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
                        showMockResult = true
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
                        showMockResult = true
                    }
                )

                AyneActionCard(
                    icon = Icons.Filled.Mic,
                    title = "Gaaffii Gaafadhu",
                    subtitle = "Suuraa irratti sagaleen gaafadhu",
                    contentDescription = "Gaaffii gaafadhu. Suuraa irratti gaafachuuf tuqi",
                    testTag = "card_ask_question_om",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMockResult = true
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. Bottom Controls (Exactly like Amharic Version)
        CleanLanguageBottomControls(
            buttonLabel = "Deebii Irra Deebi'i",
            volumeUpHint = "Volume+ suuraa kaasuuf",
            volumeDownHint = "Volume− gaaffii dabalataa gaafachuuf",
            onRepeatClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showMockResult = true
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
        EnglishMockScreen(
            isFrontCamera = false,
            onAction = {},
            onLanguageSelected = {},
            onFlipCamera = {},
            onSettingsClick = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
fun AfaanOromooMockScreenPreview() {
    AyneTheme {
        AfaanOromooMockScreen(
            isFrontCamera = false,
            onAction = {},
            onLanguageSelected = {},
            onFlipCamera = {},
            onSettingsClick = {}
        )
    }
}
