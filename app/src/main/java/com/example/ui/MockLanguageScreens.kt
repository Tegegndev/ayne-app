package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.AyneAction
import com.example.AyneTopBar
import com.example.SupportedLanguage
import com.example.ui.theme.*

/**
 * Senior-level UI/UX Mock Screens for English and Afaan Oromoo.
 * Built strictly according to the UX Peak System:
 * - 8pt spatial grid rhythm
 * - Concentric radii (R_outer = R_inner + padding)
 * - Thumb-zone ergonomics (48dp+ hit targets, sticky primary actions)
 * - High-contrast accessibility (WCAG AAA compliant pure black & vibrant yellow)
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

    var activeMockTab by remember { mutableStateOf(0) } // 0: Scene Description, 1: Read Text
    var isSimulatedAudioPlaying by remember { mutableStateOf(false) }
    var userFeedbackMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AynePureBlack)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // 1. Top Bar with Language Selector
            AyneTopBar(
                selectedLanguage = SupportedLanguage.ENGLISH,
                isFrontCamera = isFrontCamera,
                onLanguageSelected = onLanguageSelected,
                onFlipCamera = onFlipCamera,
                onSettingsClick = onSettingsClick
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Mock Preview Mode Header Badge
            MockModeBanner(
                languageName = "English",
                ttsEngine = "Gemini Neural Voice (Zephyr)",
                badgeColor = AyneVibrantYellow
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Tab Switcher between Simulated Features
            MockFeatureTabRow(
                selectedIndex = activeMockTab,
                tabTitles = listOf("Scene Description", "Read Text & OCR"),
                onTabSelected = { index ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    activeMockTab = index
                    isSimulatedAudioPlaying = false
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Interactive Mock Response Cards
            if (activeMockTab == 0) {
                MockEnglishSceneCard(
                    isPlaying = isSimulatedAudioPlaying,
                    onToggleAudio = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isSimulatedAudioPlaying = !isSimulatedAudioPlaying
                    },
                    onPromptChipClick = { prompt ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        userFeedbackMessage = "Simulated follow-up: \"$prompt\""
                    }
                )
            } else {
                MockEnglishReadTextCard(
                    isPlaying = isSimulatedAudioPlaying,
                    onToggleAudio = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isSimulatedAudioPlaying = !isSimulatedAudioPlaying
                    }
                )
            }

            AnimatedVisibility(
                visible = userFeedbackMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                userFeedbackMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = AyneDarkCard,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AyneVibrantYellow, RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = AyneVibrantYellow,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = msg,
                                color = AynePureWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Thumb-Zone Action Cards (English Mock)
            Text(
                text = "Assistive Actions (English Preview)",
                color = AyneVibrantYellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MockThumbActionCard(
                    icon = Icons.Filled.PhotoCamera,
                    title = "Describe Surroundings",
                    subtitle = "Instant snapshot · Multi-turn zero-upload vision",
                    badge = "Turn 1 (~2.8s)",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        activeMockTab = 0
                        userFeedbackMessage = "Captured new frame (Mock). Scene updated."
                    }
                )

                MockThumbActionCard(
                    icon = Icons.Filled.Description,
                    title = "Read Text & Signs",
                    subtitle = "Optical reader · Prescriptions, signs & directional guidance",
                    badge = "SSE Stream (~716ms)",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        activeMockTab = 1
                        userFeedbackMessage = "Read Text streaming simulated. Safe directional coaching active."
                    }
                )

                MockThumbActionCard(
                    icon = Icons.Filled.Mic,
                    title = "Ask Follow-Up Question",
                    subtitle = "Zero-upload voice query · Sends only 120 bytes over network",
                    badge = "0 Upload Bytes",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        userFeedbackMessage = "Listening for English follow-up question…"
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 6. Bottom Sticky Repeat CTA
        MockBottomCtaBar(
            buttonLabel = "Repeat Spoken Description",
            hintText = "Volume+ to snap · Volume− to ask follow-up",
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                isSimulatedAudioPlaying = true
                userFeedbackMessage = "Repeating last English description…"
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

    var activeMockTab by remember { mutableStateOf(0) } // 0: Ibsa Naannoo, 1: Barruu Dubbisi
    var isSimulatedAudioPlaying by remember { mutableStateOf(false) }
    var userFeedbackMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AynePureBlack)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // 1. Top Bar with Language Selector
            AyneTopBar(
                selectedLanguage = SupportedLanguage.AFAN_OROMOO,
                isFrontCamera = isFrontCamera,
                onLanguageSelected = onLanguageSelected,
                onFlipCamera = onFlipCamera,
                onSettingsClick = onSettingsClick
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Mock Preview Mode Header Badge
            MockModeBanner(
                languageName = "Afaan Oromoo",
                ttsEngine = "Sagalee Oromoo (Beta Neural Model)",
                badgeColor = AyneVibrantYellow
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Tab Switcher
            MockFeatureTabRow(
                selectedIndex = activeMockTab,
                tabTitles = listOf("Ibsa Naannoo", "Barruu & Asxaa"),
                onTabSelected = { index ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    activeMockTab = index
                    isSimulatedAudioPlaying = false
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Interactive Mock Response Cards (Afaan Oromoo)
            if (activeMockTab == 0) {
                MockOromoSceneCard(
                    isPlaying = isSimulatedAudioPlaying,
                    onToggleAudio = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isSimulatedAudioPlaying = !isSimulatedAudioPlaying
                    },
                    onPromptChipClick = { prompt ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        userFeedbackMessage = "Gaaffii: \"$prompt\""
                    }
                )
            } else {
                MockOromoReadTextCard(
                    isPlaying = isSimulatedAudioPlaying,
                    onToggleAudio = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isSimulatedAudioPlaying = !isSimulatedAudioPlaying
                    }
                )
            }

            AnimatedVisibility(
                visible = userFeedbackMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                userFeedbackMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = AyneDarkCard,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AyneVibrantYellow, RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = AyneVibrantYellow,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = msg,
                                color = AynePureWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Thumb-Zone Action Cards (Afaan Oromoo)
            Text(
                text = "Tajaajila Mirkuz (Afaan Oromoo Preview)",
                color = AyneVibrantYellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MockThumbActionCard(
                    icon = Icons.Filled.PhotoCamera,
                    title = "Naannoo Ibsi",
                    subtitle = "Suuraa kaasuun wanta naannoo jiru sagaleen dhageeffadhu",
                    badge = "Turn 1 (~2.8s)",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        activeMockTab = 0
                        userFeedbackMessage = "Suuraan haaraan kaafameera. Ibsi qophaa'eera."
                    }
                )

                MockThumbActionCard(
                    icon = Icons.Filled.Description,
                    title = "Barruu & Asxaa Dubbisi",
                    subtitle = "Qoricha, mallattoowwanii fi waraqaalee ifatti dubbisi",
                    badge = "SSE Stream (~716ms)",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        activeMockTab = 1
                        userFeedbackMessage = "Barruun dubbifamaa jira. Gorsi kaameraa qophaa'eera."
                    }
                )

                MockThumbActionCard(
                    icon = Icons.Filled.Mic,
                    title = "Gaaffii Dabalataa",
                    subtitle = "Suuraa duraan kaafame irratti sagaleen gaaffii gaafadhu",
                    badge = "Zero-Upload (0 Byte)",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        userFeedbackMessage = "Dhaggeeffachaa jira… Gaaffii kee gaafadhu."
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 6. Bottom Sticky Repeat CTA
        MockBottomCtaBar(
            buttonLabel = "Deebii Irra Deebi'i",
            hintText = "Volume+ suuraa kaasuuf · Volume− gaaffii gaafachuuf",
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                isSimulatedAudioPlaying = true
                userFeedbackMessage = "Deebiin duraa irra deebi'amee dubbifamaa jira…"
            }
        )
    }
}

// ==========================================
// SUB-COMPONENTS & MOCK CARDS
// ==========================================

@Composable
private fun MockModeBanner(
    languageName: String,
    ttsEngine: String,
    badgeColor: Color
) {
    Surface(
        color = AyneDarkCard,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "MOCK PREVIEW · $languageName".uppercase(),
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = ttsEngine,
                        color = AynePureWhite.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            Surface(
                color = badgeColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "UI Mock",
                    color = badgeColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun MockFeatureTabRow(
    selectedIndex: Int,
    tabTitles: List<String>,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        color = AyneDarkCard,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AyneVibrantYellow.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabTitles.forEachIndexed { index, title ->
                val isSelected = selectedIndex == index
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) AyneVibrantYellow else Color.Transparent,
                    animationSpec = tween(150),
                    label = "tab_bg"
                )
                val textColor = if (isSelected) AynePureBlack else AynePureWhite

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bgColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = AyneVibrantYellow),
                            role = Role.Tab
                        ) { onTabSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MockEnglishSceneCard(
    isPlaying: Boolean,
    onToggleAudio: () -> Unit,
    onPromptChipClick: (String) -> Unit
) {
    Surface(
        color = AyneDarkCard,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, AyneVibrantYellow.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .semantics { heading() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        tint = AyneVibrantYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Surroundings Description",
                        color = AyneVibrantYellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = AyneVibrantYellow.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Turn 2 · Zero-Upload",
                        color = AyneVibrantYellow,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main English Description Text
            Text(
                text = "On the dining table there is a hot cup of coffee with steam rising, a dark red notepad, and a green 100 Birr banknote resting near a set of house keys.",
                color = AynePureWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Subdued Secondary Translation (Amharic)
            Surface(
                color = AynePureBlack,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AyneVibrantYellow.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "Dual-Language Reference (አማርኛ):",
                        color = AyneVibrantYellow.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "በመመገቢያ ጠረጴዛው ላይ ትኩስ ቡና፣ ደማቅ ቀይ ማስታወሻ ደብተር እና የ100 ብር ኖት ከመክፈቻ ቁልፍ አጠገብ አለ።",
                        color = AynePureWhite.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Simulated Audio Player Bar
            MockAudioPlayerBar(
                isPlaying = isPlaying,
                voiceTitle = "Gemini Neural Voice (Zephyr · English)",
                duration = "0:04 / 0:08",
                onToggleAudio = onToggleAudio
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Follow-up Prompt Chips
            Text(
                text = "Quick Follow-Up Questions (Tap to simulate):",
                color = AyneVibrantYellow.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("What color is the cup?", "Are keys near the edge?").forEach { prompt ->
                    Surface(
                        color = AynePureBlack,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, AyneVibrantYellow.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .clickable { onPromptChipClick(prompt) }
                    ) {
                        Text(
                            text = prompt,
                            color = AynePureWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MockEnglishReadTextCard(
    isPlaying: Boolean,
    onToggleAudio: () -> Unit
) {
    Surface(
        color = AyneDarkCard,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, AyneVibrantYellow.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .semantics { heading() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = null,
                        tint = AyneVibrantYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Read Text · Medical Prescription",
                        color = AyneVibrantYellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = AyneVibrantYellow.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "SSE ~716ms First Audio",
                        color = AyneVibrantYellow,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Extracted verbatim text
            Surface(
                color = AynePureBlack,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AyneVibrantYellow.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Verbatim Extracted Text:",
                        color = AyneVibrantYellow,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"Paracetamol 500mg — Take 1 tablet every 8 hours with water after meals...\"",
                        color = AynePureWhite,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Directional Coaching Alert (Zero-Guessing Safety Demonstration)
            Surface(
                color = AyneVibrantYellow.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AyneVibrantYellow, RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = AyneVibrantYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Directional Coaching (Zero-Guess Safety)",
                            color = AyneVibrantYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Text is cut off on the right. Tilt camera slightly right and retake.",
                            color = AynePureWhite,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Simulated Audio Player Bar
            MockAudioPlayerBar(
                isPlaying = isPlaying,
                voiceTitle = "Addis AI Realtime Stream ( am-hamen / English Mode )",
                duration = "0:02 / 0:06",
                onToggleAudio = onToggleAudio
            )
        }
    }
}

@Composable
private fun MockOromoSceneCard(
    isPlaying: Boolean,
    onToggleAudio: () -> Unit,
    onPromptChipClick: (String) -> Unit
) {
    Surface(
        color = AyneDarkCard,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, AyneVibrantYellow.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .semantics { heading() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        tint = AyneVibrantYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ibsa Naannoo (Afaan Oromoo)",
                        color = AyneVibrantYellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = AyneVibrantYellow.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Turn 2 · Zero-Upload",
                        color = AyneVibrantYellow,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Afaan Oromoo Description Text
            Text(
                text = "Minjaala nyaataa irra kubbayyaa bunaa aaran irraa ba'aa jiru, nootii barruu bifa diimaa dukkanaawaa fi maallaqa qarshii 100 magariisa banaa qulfii cinaa jiru argina.",
                color = AynePureWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Subdued Secondary Translation (English)
            Surface(
                color = AynePureBlack,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AyneVibrantYellow.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "Hiika Ingilizii (English Reference):",
                        color = AyneVibrantYellow.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "On the dining table there is a hot cup of coffee with steam rising, a dark red notepad, and a 100 Birr banknote.",
                        color = AynePureWhite.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Simulated Audio Player Bar
            MockAudioPlayerBar(
                isPlaying = isPlaying,
                voiceTitle = "Sagalee Oromoo (Beta Neural Model)",
                duration = "0:04 / 0:09",
                onToggleAudio = onToggleAudio
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Follow-up Prompt Chips (Afaan Oromoo)
            Text(
                text = "Gaaffii Dabalataa (Tuqiitii yaali):",
                color = AyneVibrantYellow.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Bifti kubbayyaa maali?", "Qulfichi daangaa irra jiraa?").forEach { prompt ->
                    Surface(
                        color = AynePureBlack,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, AyneVibrantYellow.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .clickable { onPromptChipClick(prompt) }
                    ) {
                        Text(
                            text = prompt,
                            color = AynePureWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MockOromoReadTextCard(
    isPlaying: Boolean,
    onToggleAudio: () -> Unit
) {
    Surface(
        color = AyneDarkCard,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, AyneVibrantYellow.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .semantics { heading() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = null,
                        tint = AyneVibrantYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Barruu Dubbisi · Ajaja Qorichaa",
                        color = AyneVibrantYellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = AyneVibrantYellow.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "SSE Stream ~716ms",
                        color = AyneVibrantYellow,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Extracted verbatim text
            Surface(
                color = AynePureBlack,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AyneVibrantYellow.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Barruu Kaameraan Argitee Dubbiste:",
                        color = AyneVibrantYellow,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"Paracetamol 500mg — Kiniinii 1 sa'aatii 8n 8tti bishaanin nyaata booda fudhadhu...\"",
                        color = AynePureWhite,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Directional Coaching Alert (Oromo)
            Surface(
                color = AyneVibrantYellow.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AyneVibrantYellow, RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = AyneVibrantYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Gorsa Kaameraa (Of-Eeggannoo)",
                            color = AyneVibrantYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Barruun mirgaan citeera; kaameraa gara mirgaatti xiqqoo naannessi.",
                            color = AynePureWhite,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Simulated Audio Player Bar
            MockAudioPlayerBar(
                isPlaying = isPlaying,
                voiceTitle = "Sagalee Oromoo (Realtime Audio Stream)",
                duration = "0:02 / 0:07",
                onToggleAudio = onToggleAudio
            )
        }
    }
}

@Composable
private fun MockAudioPlayerBar(
    isPlaying: Boolean,
    voiceTitle: String,
    duration: String,
    onToggleAudio: () -> Unit
) {
    Surface(
        color = AynePureBlack,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AyneVibrantYellow.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(AyneVibrantYellow)
                        .clickable(role = Role.Button, onClick = onToggleAudio),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Close else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isPlaying) "Stop audio" else "Play spoken audio",
                        tint = AyneOnYellow,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = if (isPlaying) "Playing audio…" else "Spoken Audio Available",
                        color = AyneVibrantYellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = voiceTitle,
                        color = AynePureWhite.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }

            Text(
                text = duration,
                color = AynePureWhite.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MockThumbActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit
) {
    Surface(
        color = AyneDarkCard,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .border(1.5.dp, AyneVibrantYellow.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = AyneVibrantYellow),
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                role = Role.Button
                contentDescription = "$title. $subtitle"
            }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(AyneVibrantYellow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AyneOnYellow,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = title,
                        color = AynePureWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = AyneVibrantYellow,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Surface(
                color = AyneVibrantYellow.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = badge,
                    color = AyneVibrantYellow,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun MockBottomCtaBar(
    buttonLabel: String,
    hintText: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = AyneVibrantYellow,
                contentColor = AyneOnYellow
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = buttonLabel
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = AyneOnYellow,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = buttonLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = hintText,
            color = AynePureWhite.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
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

