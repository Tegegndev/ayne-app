package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import com.example.AyneUiState
import com.example.SupportedLanguage
import com.example.ui.theme.AyneDarkCard
import com.example.ui.theme.AyneOnYellow
import com.example.ui.theme.AynePureBlack
import com.example.ui.theme.AynePureWhite
import com.example.ui.theme.AyneVibrantYellow
import kotlin.math.abs
import kotlin.math.sin

/**
 * The voice assistant's state, derived purely from [AyneUiState].
 * The orb is a visual function of this state.
 */
sealed interface VoiceOrbState {
    /** Waiting for the user to start talking. */
    data object Idle : VoiceOrbState

    /** Microphone open. [level] is the smoothed mic amplitude, 0..1. */
    data class Listening(val heardSpeech: Boolean, val level: Float) : VoiceOrbState

    /** Request in flight, waiting for the server. */
    data object Thinking : VoiceOrbState

    /** Assistant audio is playing. */
    data object Speaking : VoiceOrbState
}

fun deriveVoiceOrbState(uiState: AyneUiState): VoiceOrbState = when {
    uiState.isAssistantSpeaking -> VoiceOrbState.Speaking
    uiState.isBusy -> VoiceOrbState.Thinking
    uiState.isRecordingVoice -> VoiceOrbState.Listening(uiState.heardSpeech, uiState.micLevel)
    else -> VoiceOrbState.Idle
}

private data class Ripple(
    val startNanos: Long,
    val strength: Float,
    val durationNanos: Long = 1_600_000_000L
)

/**
 * Full-screen voice-only Ask AI experience: a large amplitude-reactive ripple
 * orb replaces the old chat-panel UI. Tap the orb to talk; tap again (or
 * Volume−) to submit. Everything else — barge-in, VAD auto-submit, haptics —
 * keeps working exactly as before.
 */
@Composable
fun VoiceAskScreen(
    uiState: AyneUiState,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onMicTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val orbState = deriveVoiceOrbState(uiState)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AynePureBlack)
            .testTag("companion_mode_container")
    ) {
        // ---- Top row: close + new chat ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val exitDesc = when (uiState.selectedLanguage) {
                SupportedLanguage.ENGLISH -> "Close and return to camera"
                SupportedLanguage.AFAN_OROMOO -> "Cunee gara kaameraatti deebi'i"
                SupportedLanguage.AMHARIC -> "ዝጋት እና ወደ ካሜራ ተመለስ"
            }
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClose()
                },
                modifier = Modifier
                    .size(48.dp)
                    .border(1.dp, AyneVibrantYellow.copy(alpha = 0.4f), CircleShape)
                    .testTag("companion_exit_button")
                    .semantics { contentDescription = exitDesc }
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = AynePureWhite,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (uiState.askQuestionHistory.isNotEmpty()) {
                val clearDesc = when (uiState.selectedLanguage) {
                    SupportedLanguage.ENGLISH -> "Start a new conversation"
                    SupportedLanguage.AFAN_OROMOO -> "Haasawumsa haaraa jalqabi"
                    SupportedLanguage.AMHARIC -> "አዲስ ውይይት ጀምር"
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNewChat()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .border(1.dp, AyneVibrantYellow.copy(alpha = 0.4f), CircleShape)
                        .testTag("ask_question_clear_button")
                        .semantics { contentDescription = clearDesc }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = AyneVibrantYellow,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // ---- Center: orb + status ----
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RippleOrb(
                state = orbState,
                onTap = onMicTap,
                language = uiState.selectedLanguage
            )

            Spacer(modifier = Modifier.height(36.dp))

            val statusText = voiceStatusText(orbState, uiState)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = statusText,
                    color = AyneVibrantYellow,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                if (orbState is VoiceOrbState.Listening && !uiState.liveTranscription.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "“" + uiState.liveTranscription + "”",
                        color = AynePureWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 30.sp,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }

        // ---- Bottom hint ----
        val hint = when (uiState.selectedLanguage) {
            SupportedLanguage.ENGLISH -> "Volume + to capture · Volume − to talk"
            SupportedLanguage.AFAN_OROMOO -> "Volume + suuraa kaasuuf · Volume − dubbachuuf"
            SupportedLanguage.AMHARIC -> "Volume + ፎቶ ለማንሳት · Volume − ለመናገር"
        }
        Text(
            text = hint,
            color = AynePureWhite.copy(alpha = 0.45f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp)
        )
    }
}

private fun voiceStatusText(state: VoiceOrbState, uiState: AyneUiState): String {
    return when (state) {
        is VoiceOrbState.Idle -> when (uiState.selectedLanguage) {
            SupportedLanguage.ENGLISH -> "Tap to speak"
            SupportedLanguage.AFAN_OROMOO -> "Dubbachuuf tuqi"
            SupportedLanguage.AMHARIC -> "ለመናገር ይንኩ"
        }
        is VoiceOrbState.Listening -> when (uiState.selectedLanguage) {
            SupportedLanguage.ENGLISH -> "Listening…"
            SupportedLanguage.AFAN_OROMOO -> "Dhaggeeffachaa jira…"
            SupportedLanguage.AMHARIC -> "እያዳመጥኩ ነው…"
        }
        is VoiceOrbState.Thinking -> uiState.busyMessage?.takeIf { it.isNotBlank() } ?: when (uiState.selectedLanguage) {
            SupportedLanguage.ENGLISH -> "Thinking…"
            SupportedLanguage.AFAN_OROMOO -> "Yaadaa jira…"
            SupportedLanguage.AMHARIC -> "እያሰብኩ ነው…"
        }
        is VoiceOrbState.Speaking -> when (uiState.selectedLanguage) {
            SupportedLanguage.ENGLISH -> "Speaking…"
            SupportedLanguage.AFAN_OROMOO -> "Ibsaa jira…"
            SupportedLanguage.AMHARIC -> "እያነገርኩ ነው…"
        }
    }
}

private fun voiceOrbDescription(state: VoiceOrbState, language: SupportedLanguage): String {
    return when (state) {
        is VoiceOrbState.Idle -> when (language) {
            SupportedLanguage.ENGLISH -> "Ask. Tap to speak your question."
            SupportedLanguage.AFAN_OROMOO -> "Gaafadhu. Dubbachuuf tuqi."
            SupportedLanguage.AMHARIC -> "ጠይቅ። ለመናገር ይንኩ።"
        }
        is VoiceOrbState.Listening -> when (language) {
            SupportedLanguage.ENGLISH -> "Listening. Tap to submit, or press Volume minus."
            SupportedLanguage.AFAN_OROMOO -> "Dhaggeeffachaa jira. Erguuf tuqi ykn Volume hir'isi."
            SupportedLanguage.AMHARIC -> "እያዳመጥኩ ነው። ለመላክ ይንኩ ወይም Volume ቀንስ ይጫኑ።"
        }
        is VoiceOrbState.Thinking -> when (language) {
            SupportedLanguage.ENGLISH -> "Thinking, please wait."
            SupportedLanguage.AFAN_OROMOO -> "Yaadaa jira, eegi."
            SupportedLanguage.AMHARIC -> "እያሰብኩ ነው፣ ትንሽ ይቆዩ።"
        }
        is VoiceOrbState.Speaking -> when (language) {
            SupportedLanguage.ENGLISH -> "Speaking. Tap to interrupt."
            SupportedLanguage.AFAN_OROMOO -> "Dubbannee jira. Dhaabuuf tuqi."
            SupportedLanguage.AMHARIC -> "እያነገርኩ ነው። ለማቋረጥ ይንኩ።"
        }
    }
}

/**
 * The big circle: a glowing core disc surrounded by ripple rings.
 *
 * - Idle:      slow breathing + a lazy ripple every ~2.4s
 * - Listening: ripple cadence and ring strength follow the live mic level
 * - Thinking:  ripples stop, a rotating arc sweeps the core
 * - Speaking:  rhythmic ripples pulse in time with the assistant's voice
 */
@Composable
fun RippleOrb(
    state: VoiceOrbState,
    onTap: () -> Unit,
    language: SupportedLanguage,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val ripples = remember { mutableStateListOf<Ripple>() }

    // Drive a per-frame redraw so ripples animate smoothly.
    var frameNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameNanos = it }
        }
    }

    // Smoothed mic level for the listening phase.
    val animLevel = remember { Animatable(0f) }
    LaunchedEffect(state) {
        when (val s = state) {
            is VoiceOrbState.Listening -> animLevel.animateTo(s.level, tween(90, easing = LinearEasing))
            else -> animLevel.animateTo(0f, tween(300))
        }
    }

    // Emit ripples on a state-driven cadence.
    LaunchedEffect(state) {
        while (true) {
            // Prune finished ripples.
            val now = System.nanoTime()
            ripples.removeAll { now - it.startNanos > it.durationNanos }

            when (val s = state) {
                is VoiceOrbState.Listening -> {
                    val strength = 0.35f + 0.6f * s.level.coerceIn(0f, 1f)
                    ripples.add(Ripple(startNanos = System.nanoTime(), strength = strength))
                    val intervalMs = (700L - 420L * s.level.coerceIn(0f, 1f)).toLong()
                    kotlinx.coroutines.delay(intervalMs)
                }
                is VoiceOrbState.Speaking -> {
                    ripples.add(Ripple(startNanos = System.nanoTime(), strength = 0.6f))
                    kotlinx.coroutines.delay(520L)
                }
                is VoiceOrbState.Idle -> {
                    ripples.add(Ripple(startNanos = System.nanoTime(), strength = 0.45f))
                    kotlinx.coroutines.delay(2400L)
                }
                is VoiceOrbState.Thinking -> {
                    kotlinx.coroutines.delay(200L) // no ripples while thinking
                }
            }
        }
    }

    // Rotating arc for the thinking phase.
    val thinkRotation = rememberInfiniteTransition(label = "thinking")
    val rotation by thinkRotation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "rotation"
    )

    val description = voiceOrbDescription(state, language)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier.size(300.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("companion_mic_button")
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = description
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTap()
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val coreBaseRadius = size.minDimension * 0.18f          // ~54dp at 300dp
            val maxRippleRadius = size.minDimension * 0.46f         // ~138dp
            val density = size.minDimension / 300.dp.toPx()         // scale strokes with the box

            // ---- Ripples ----
            val now = System.nanoTime()
            for (ripple in ripples) {
                val progress = ((now - ripple.startNanos).toFloat() / ripple.durationNanos)
                    .coerceIn(0f, 1f)
                val radius = coreBaseRadius + (maxRippleRadius - coreBaseRadius) * progress
                val fade = (1f - progress) * (1f - progress)
                drawCircle(
                    color = AyneVibrantYellow.copy(alpha = 0.55f * fade * ripple.strength),
                    radius = radius,
                    center = center,
                    style = Stroke(width = (2.5f + 3f * ripple.strength) * density)
                )
            }

            // ---- Core breathing / pulsing scale ----
            val tSec = (frameNanos / 1_000_000L) / 1000f
            val level = animLevel.value.coerceIn(0f, 1f)
            val coreScale = when (state) {
                is VoiceOrbState.Idle -> 1f + 0.025f * sin(tSec * (2f * Math.PI.toFloat() / 2.6f))
                is VoiceOrbState.Listening -> 1f + 0.05f + 0.16f * level
                is VoiceOrbState.Thinking -> 1f + 0.03f * sin(tSec * (2f * Math.PI.toFloat() / 0.9f))
                is VoiceOrbState.Speaking -> 1f + 0.06f * abs(sin(tSec * (2f * Math.PI.toFloat() / 0.56f)))
            }
            val coreRadius = coreBaseRadius * coreScale

            // ---- Core glow + disc ----
            val coreColor = when (state) {
                is VoiceOrbState.Listening -> if (state.heardSpeech) AynePureWhite else AyneVibrantYellow
                is VoiceOrbState.Thinking -> AyneDarkCard
                else -> AyneVibrantYellow
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = 0.35f),
                        coreColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = coreRadius * 1.7f
                ),
                radius = coreRadius * 1.7f,
                center = center
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(coreColor, coreColor.copy(alpha = 0.85f)),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )

            // ---- Thinking arc ----
            if (state is VoiceOrbState.Thinking) {
                drawArc(
                    color = AyneVibrantYellow,
                    startAngle = rotation,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(center.x - coreRadius - 18.dp.toPx(), center.y - coreRadius - 18.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(
                        (coreRadius + 18.dp.toPx()) * 2f,
                        (coreRadius + 18.dp.toPx()) * 2f
                    ),
                    style = Stroke(width = 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
        }

        // ---- Center icon ----
        val iconTint = when (state) {
            is VoiceOrbState.Thinking -> AyneVibrantYellow.copy(alpha = 0.6f)
            else -> AyneOnYellow
        }
        Icon(
            imageVector = if (state is VoiceOrbState.Listening) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(56.dp)
        )
    }
}
