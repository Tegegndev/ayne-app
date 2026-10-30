package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.data.AddisRealtimeClient
import com.example.data.ConversationTurn
import com.example.data.cleanLabelPrefix
import com.example.data.hasRawJsonArtifacts
import com.example.data.ReadTextResponse
import com.example.data.VisionApiService
import com.example.data.VisionDescribeResponse
import com.example.ui.AfaanOromooMockScreen
import com.example.ui.EnglishMockScreen
import com.example.ui.VoiceAskScreen
import com.example.ui.theme.AyneDarkCard
import com.example.ui.theme.AyneOnYellow
import com.example.ui.theme.AynePureBlack
import com.example.ui.theme.AynePureWhite
import com.example.ui.theme.AyneTheme
import com.example.ui.theme.AyneVibrantYellow
import com.example.util.AudioPlaybackHelper
import com.example.util.CameraCaptureHelper
import com.example.util.Pcm16Decoder
import com.example.util.ProcessingFeedbackHelper
import com.example.util.VoiceRecorderHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ==========================================
// Domain & State Models
// ==========================================

enum class SupportedLanguage(val displayName: String, val code: String) {
    AMHARIC("አማርኛ", "am"),
    AFAN_OROMOO("Afaan Oromoo", "om"),
    ENGLISH("English", "en")
}

data class AyneUiState(
    val selectedLanguage: SupportedLanguage = SupportedLanguage.AMHARIC,
    val lastAnswer: String? = null,
    val lastEnglishAnswer: String? = null,
    val lastAudioBase64: String? = null,
    val activeSessionId: String? = null,
    val isBusy: Boolean = false,
    val busyMessage: String? = null,
    val isFrontCamera: Boolean = false,
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiEndpoint: String = DEFAULT_ENDPOINT,
    val readTextEndpoint: String = DEFAULT_READ_TEXT_ENDPOINT,
    val askQuestionEndpoint: String = DEFAULT_ASK_QUESTION_ENDPOINT,
    val customVoice: String = "am-hamen",
    val lastResponse: VisionDescribeResponse? = null,
    val lastReadResponse: ReadTextResponse? = null,
    val lockedImageBase64: String? = null,
    val lastCapturedBase64: String? = null,
    val conversationHistory: List<ConversationTurn> = emptyList(),
    val isRecordingVoice: Boolean = false,
    val heardSpeech: Boolean = false,
    val liveTranscription: String? = null,
    val micLevel: Float = 0f,
    val isAssistantSpeaking: Boolean = false,
    val isCompanionMode: Boolean = false,
    val companionGreeting: String? = null,
    val isAskQuestionOpen: Boolean = false,
    val askQuestionHistory: List<ConversationTurn> = emptyList(),
    val askQuestionSessionId: String? = null,
    val askQuestionLastAnswer: String? = null
) {
    companion object {
        fun buildVisionUrl(base: String, language: String? = null): String {
            val root = "${base.trim().trimEnd('/')}/api/see-and-describe"
            return when (language?.lowercase()) {
                "om", "oromo" -> "$root/om"
                "am", "amharic" -> "$root/am"
                else -> root
            }
        }

        fun buildReadTextUrl(base: String, language: String? = null): String {
            val root = "${base.trim().trimEnd('/')}/api/read-text"
            return when (language?.lowercase()) {
                "om", "oromo" -> "$root/om"
                "am", "amharic" -> "$root/am"
                else -> root
            }
        }

        fun buildAskQuestionUrl(base: String, language: String? = null): String {
            val root = "${base.trim().trimEnd('/')}/api/ask-question"
            return when (language?.lowercase()) {
                "om", "oromo" -> "$root/om"
                "am", "amharic" -> "$root/am"
                else -> root
            }
        }

        val DEFAULT_BASE_URL: String by lazy {
            try {
                val url = BuildConfig.API_BASE_URL
                if (!url.isNullOrBlank()) url.trim().trimEnd('/')
                else "https://ayne-roan.vercel.app"
            } catch (e: Throwable) {
                "https://ayne-roan.vercel.app"
            }
        }

        val DEFAULT_ENDPOINT: String by lazy {
            try {
                val custom = BuildConfig.VISION_API_URL
                if (!custom.isNullOrBlank() && !custom.contains("foul-crematorium")) {
                    custom.trim()
                } else {
                    buildVisionUrl(DEFAULT_BASE_URL)
                }
            } catch (e: Throwable) {
                buildVisionUrl(DEFAULT_BASE_URL)
            }
        }

        val DEFAULT_READ_TEXT_ENDPOINT: String by lazy {
            try {
                val custom = BuildConfig.READ_TEXT_API_URL
                if (!custom.isNullOrBlank() && !custom.contains("foul-crematorium")) {
                    custom.trim()
                } else {
                    buildReadTextUrl(DEFAULT_BASE_URL)
                }
            } catch (e: Throwable) {
                buildReadTextUrl(DEFAULT_BASE_URL)
            }
        }

        val DEFAULT_ASK_QUESTION_ENDPOINT: String by lazy {
            try {
                val custom = BuildConfig.ASK_QUESTION_API_URL
                if (!custom.isNullOrBlank() && !custom.contains("foul-crematorium")) {
                    custom.trim()
                } else {
                    buildAskQuestionUrl(DEFAULT_BASE_URL)
                }
            } catch (e: Throwable) {
                buildAskQuestionUrl(DEFAULT_BASE_URL)
            }
        }
    }
}

sealed interface AyneAction {
    data class DescribeSurroundings(val frameBase64: String) : AyneAction
    data class ReadText(val frameBase64: String) : AyneAction
    data object StartVoiceRecording : AyneAction
    data object StopAndSubmitVoiceRecording : AyneAction
    data object CancelVoiceRecording : AyneAction
    data class AskFollowUp(val voiceBase64: String? = null, val textQuery: String? = null) : AyneAction
    data object ClearSession : AyneAction
    data object DismissResult : AyneAction
    data object AskQuestion : AyneAction
    data class OpenAskQuestion(val frameBase64: String? = null) : AyneAction
    data object CloseAskQuestion : AyneAction
    data class SubmitAskQuestion(val query: String) : AyneAction
    data object ClearAskQuestionHistory : AyneAction
    data object EnterCompanionMode : AyneAction
    data object ExitCompanionMode : AyneAction
    data class AskCompanionQuestion(val query: String) : AyneAction
    data object RepeatLastAnswer : AyneAction
    data class SelectLanguage(val language: SupportedLanguage) : AyneAction
    data object FlipCamera : AyneAction
    data class UpdateEndpoints(
        val baseUrl: String? = null,
        val visionUrl: String,
        val readTextUrl: String,
        val askQuestionUrl: String? = null
    ) : AyneAction
    data class UpdateVoice(val voiceId: String) : AyneAction
    data object VolumeCaptureTriggered : AyneAction
    data object VolumeFollowUpTriggered : AyneAction
}

fun generateCompanionFallbackAnswer(query: String?, language: SupportedLanguage): String {
    val q = query?.lowercase() ?: ""
    return when {
        q.contains("ቀልድ") || q.contains("joke") || q.contains("qoosaa") -> {
            when (language) {
                SupportedLanguage.ENGLISH -> "Why don't scientists trust atoms? Because they make up everything! 😄 What else would you like to hear?"
                SupportedLanguage.AFAN_OROMOO -> "Namichi tokko mana kuusaa daldalaa dhaqee 'falaasaa balali'u qabduu?' jennaan daldalaan 'eeyyee garuu boba'aan waan dhumateef lafa irra deema' jedheen! 😄"
                SupportedLanguage.AMHARIC -> "አንድ ሰውዬ ወደ ሱቅ ሄዶ 'የሚበር ምንጣፍ አለ?' ሲል ሻጩ 'አዎ አለ ግን የነዳጅ ዋጋ ስለጨመረ በእግር ነው የሚሄደው' አለው! 😂 ሌላ ቀልድ ወይም መረጃ ይፈልጋሉ?"
            }
        }
        q.contains("እውነታ") || q.contains("fact") || q.contains("aja'ib") -> {
            when (language) {
                SupportedLanguage.ENGLISH -> "Fun Fact: Ethiopia is the birthplace of Coffee Arabica and follows a unique 13-month calendar with 12 months of 30 days and one month of 5 or 6 days! ☕"
                SupportedLanguage.AFAN_OROMOO -> "Waan nama ajaa'ibu: Itoophiyaan biyya addunyaa irratti ji'a 13 qabdu isa tokkicha! Akkasumas madda bunaati. ☕"
                SupportedLanguage.AMHARIC -> "አስደናቂ እውነታ፦ ኢትዮጵያ በዓለም ላይ የራሷ የሆነ 13 ወራት ያላት ብቸኛ ሀገር ናት! በተጨማሪም የዓለማችን ምርጥ የቡና መገኛ የካፋ ምድር ናት። ☕"
            }
        }
        q.contains("ዜና") || q.contains("news") || q.contains("oduu") -> {
            when (language) {
                SupportedLanguage.ENGLISH -> "Today's Update: Advancements in AI assistive vision are empowering visually impaired individuals around the globe. I'm right here as your companion! 📰"
                SupportedLanguage.AFAN_OROMOO -> "Oduu Har'aa: Saayinsiin fi teeknooloojiin AI qaro-dhabeeyyii gargaaruuf daran guddataa jira. Anis yeroo hunda si cinaa jira! 📰"
                SupportedLanguage.AMHARIC -> "የዛሬ የቴክኖሎጂና ዕለታዊ ዜና፦ በዓለም ዙሪያ አዳዲስ የሰው ሰራሽ አስተዋይ (AI) ፈጠራዎች ለዓይነ ስውራን ድጋፍ በመስጠት ላይ ይገኛሉ። እኔም በማንኛውም ሰዓት ከጎንዎ ነኝ! 📰"
            }
        }
        q.contains("ምክር") || q.contains("advice") || q.contains("gorsa") -> {
            when (language) {
                SupportedLanguage.ENGLISH -> "Daily Wisdom: 'A stitch in time saves nine.' Take a deep breath, stay confident, and believe in your steps today! ☀️"
                SupportedLanguage.AFAN_OROMOO -> "Gorsa Guyyaa: Yeroo hunda ofitti amani, carraaqqiin bu'aa gaarii fida! ☀️"
                SupportedLanguage.AMHARIC -> "የዕለቱ ምክር፦ 'ድር ቢያብር አንበሳ ያስር።' ሁልጊዜ በራስ መተማመን ይኑርዎት፤ ዛሬ የተሻለ ቀን እንዲሆንልዎ እመኛለሁ! ☀️"
            }
        }
        else -> {
            when (language) {
                SupportedLanguage.ENGLISH -> "I'm your AI companion! You can chat with me about jokes, daily news, fascinating facts, or any question on your mind."
                SupportedLanguage.AFAN_OROMOO -> "Ani gargaaraa keeti! Qoosaa, oduu haaraa, fi gaaffii kamiyyuu na gaafachuu dandeessa."
                SupportedLanguage.AMHARIC -> "እኔ ረዳትዎ ነኝ! ስለ ቀልድ፣ አስደናቂ እውነታዎች፣ ዜና ወይም ማንኛውም ጥያቄ አብረን ማውራት እንችላለን።"
            }
        }
    }
}

// ==========================================
// ViewModel
// ==========================================

class AyneViewModel(
    private val visionApiService: VisionApiService = VisionApiService(),
    private val audioPlaybackHelper: AudioPlaybackHelper = AudioPlaybackHelper()
) : ViewModel() {
    private var processingFeedbackHelper: ProcessingFeedbackHelper? = null
    private var voiceRecorderHelper: VoiceRecorderHelper? = null
    private var realtimeClient: AddisRealtimeClient? = null
    private var recordingJob: Job? = null

    /**
     * The streaming network job responsible for the current spoken answer
     * (describe / follow-up / read-text). Cancelled on barge-in so an
     * interrupted answer STOPS streaming instead of re-enqueueing later audio
     * chunks and talking over (or into) whatever the user does next.
     */
    private var activeApiJob: Job? = null

    /**
     * Cancels the in-flight answer stream (if any) so no further audio chunks
     * or state updates from that request can arrive. Safe to call anytime.
     */
    private fun cancelActiveApiJob() {
        activeApiJob?.cancel()
        activeApiJob = null
    }
    private var activeSessionId: String? = null

    /**
     * Fresh CameraX frame provider, wired from the UI layer.
     * Used by the silence fallback to freeze the current preview frame
     * when the user says nothing within the VAD timeout window.
     */
    private var frameProvider: (suspend () -> String)? = null

    fun setFrameProvider(provider: (suspend () -> String)?) {
        frameProvider = provider
    }

    // VAD tuning: mic amplitude threshold + hands-free timers
    private val vadSpeechThreshold = 4500          // maxAmplitude is 0..32767
    private val vadPollIntervalMs = 100L
    private val vadSilenceAutoSubmitMs = 1800L     // 1.8s silence after speech → submit
    private val vadNoSpeechTimeoutMs = 6000L       // 6s timeout if no speech at all
    private val vadMaxRecordingMs = 25_000L        // safety cap

    // Silence fallback query sent when the user says nothing
    private val silenceFallbackQuery = "ትዕይንቱን ግለጽልኝ"

    private val _uiState = MutableStateFlow(AyneUiState())
    val uiState: StateFlow<AyneUiState> = _uiState.asStateFlow()

    fun getActiveSessionId(): String? = activeSessionId

    fun isSpeaking(): Boolean = audioPlaybackHelper.isPlaying

    fun resetVisualSession() {
        activeSessionId = null
        _uiState.update { it.copy(activeSessionId = null) }
    }

    private val _userMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    init {
        // Mirror the audio player's start/stop into UI state so the voice orb
        // can animate the "assistant is speaking" phase.
        audioPlaybackHelper.onPlaybackStateChanged = { speaking ->
            _uiState.update { it.copy(isAssistantSpeaking = speaking) }
        }
    }

    fun initFeedbackHelper(context: Context) {
        if (processingFeedbackHelper == null) {
            processingFeedbackHelper = ProcessingFeedbackHelper(context.applicationContext)
        }
        if (voiceRecorderHelper == null) {
            voiceRecorderHelper = VoiceRecorderHelper(context.applicationContext)
        }
    }

    fun showMessage(msg: String) {
        _userMessage.tryEmit(msg)
    }

    fun bargeIn() {
        audioPlaybackHelper.stop()
        processingFeedbackHelper?.stopWorkingFeedback()
        cancelActiveApiJob()  // a higher-priority request replaces the streaming answer
        realtimeClient?.cancel()  // abort any in-flight Mode B voice turn
        if (_uiState.value.isRecordingVoice) {
            cancelVoiceRecording()
        }
    }

    /**
     * Playback-only interruption used by hardware volume key presses: silences
     * the speaker and aborts any in-flight Mode B turn (whose answer would
     * otherwise come back and talk over the user's next action), but leaves
     * any active voice recording untouched, so the key-up can submit the take.
     */
    fun stopPlaybackOnly() {
        audioPlaybackHelper.stop()
        processingFeedbackHelper?.stopWorkingFeedback()
        cancelActiveApiJob()  // interrupted answer must not keep streaming
        realtimeClient?.cancel()
    }

    fun playTapEarcon() {
        processingFeedbackHelper?.playInstantTapEarcon()
    }

    fun openAskQuestion(context: Context, capturedFrame: String? = null) {
        bargeIn()
        val greeting = when (_uiState.value.selectedLanguage) {
            SupportedLanguage.ENGLISH -> "Hello! How can I help you? Press Volume up to take a photo, or Volume down to ask your question."
            SupportedLanguage.AFAN_OROMOO -> "Akkam! Maal si gargaaru? Suuraa kaasuuf Volume ol dhiibi, gaaffii gaafachuuf Volume gadi dhiibi."
            SupportedLanguage.AMHARIC -> "ሰላም! ምን ልርዳዎት? ፎቶ ለማንሳት የድምፅ መጨመሪያውን፣ ጥያቄ ለመጠየቅ የድምፅ መቀነሻውን ይጫኑ።"
        }
        _uiState.update {
            it.copy(
                isCompanionMode = true,
                isAskQuestionOpen = true,
                lockedImageBase64 = capturedFrame ?: it.lockedImageBase64,
                lastCapturedBase64 = capturedFrame ?: it.lastCapturedBase64,
                companionGreeting = greeting,
                lastAnswer = greeting,
                lastEnglishAnswer = if (it.selectedLanguage == SupportedLanguage.ENGLISH) greeting else null,
                lastReadResponse = null,
                isRecordingVoice = false,
                micLevel = 0f,
                liveTranscription = null
            )
        }
        processingFeedbackHelper?.playSuccessTone()
        _userMessage.tryEmit(greeting)
        synthesizeAndPlayTtsFallback(context, greeting, _uiState.value)
    }

    /**
     * Follow-up entry: opens the Ask voice screen WITHOUT a fresh greeting and
     * goes straight into listening, so a Volume− press on the home screen
     * starts the next question of the current session immediately. Keeps the
     * locked image, previous ask turns, and the old greeting intact.
     */
    fun openAskFollowUp(context: Context) {
        bargeIn()
        _uiState.update {
            it.copy(
                isCompanionMode = true,
                isAskQuestionOpen = true
            )
        }
        startVoiceRecording(context)
    }

    /**
     * Stores a freshly captured frame without restarting the Ask Question
     * conversation (greeting and history stay intact). Any previous
     * image/session binding is cleared so the new photo is uploaded with the
     * next question.
     */
    fun setLockedFrame(frameBase64: String?) {
        if (frameBase64.isNullOrBlank()) return
        activeSessionId = null
        _uiState.update {
            it.copy(
                lockedImageBase64 = frameBase64,
                lastCapturedBase64 = frameBase64,
                activeSessionId = null,
                askQuestionSessionId = null
            )
        }
    }

    fun enterCompanionMode(context: Context, capturedFrame: String? = null) {
        openAskQuestion(context, capturedFrame)
    }

    fun exitCompanionMode() {
        bargeIn()
        _uiState.update { it.copy(isCompanionMode = false, isAskQuestionOpen = false, isBusy = false) }
        playTapEarcon()
        _userMessage.tryEmit("Switched to Vision mode")
    }

    fun clearAskQuestionSession(context: Context) {
        bargeIn()
        _uiState.update {
            it.copy(
                askQuestionHistory = emptyList(),
                askQuestionSessionId = null,
                askQuestionLastAnswer = null,
                isBusy = false
            )
        }
        playTapEarcon()
        val resetMsg = when (_uiState.value.selectedLanguage) {
            SupportedLanguage.ENGLISH -> "Started a new question session"
            SupportedLanguage.AFAN_OROMOO -> "Marii gaaffii haaraa eegalteerta"
            SupportedLanguage.AMHARIC -> "አዲስ የጥያቄ ውይይት ተጀምሯል"
        }
        _userMessage.tryEmit(resetMsg)
    }

    fun startVoiceRecording(context: Context) {
        bargeIn()
        playTapEarcon()

        if (voiceRecorderHelper == null) {
            voiceRecorderHelper = VoiceRecorderHelper(context.applicationContext)
        }

        val started = voiceRecorderHelper?.startRecording() == true
        if (started) {
            _uiState.update { it.copy(isRecordingVoice = true, heardSpeech = false, liveTranscription = null, micLevel = 0f) }
            _userMessage.tryEmit(context.getString(R.string.recording_voice_prompt))

            // Hands-free VAD loop:
            // - Speech onset → immediate Barge-In (mute any playing audio)
            // - 600ms continuous silence after speech → auto-submit
            // - 3s with no speech at all → freeze camera frame + describe surroundings
            recordingJob?.cancel()
            recordingJob = viewModelScope.launch {
                var hasSpoken = false
                var silenceMs = 0L
                var listenedMs = 0L

                while (isActive && _uiState.value.isRecordingVoice && listenedMs < vadMaxRecordingMs) {
                    delay(vadPollIntervalMs)
                    listenedMs += vadPollIntervalMs

                    val amplitude = voiceRecorderHelper?.currentAmplitude ?: 0

                    // Publish a smoothed 0..1 mic level for the voice orb ripple animation.
                    // Fast attack, gentle release (~15% decay per 100ms tick).
                    val normalizedLevel = (amplitude / 10000f).coerceIn(0.06f, 1f)
                    _uiState.update { prev ->
                        prev.copy(micLevel = if (normalizedLevel > prev.micLevel) {
                            normalizedLevel
                        } else {
                            prev.micLevel * 0.85f
                        })
                    }

                    if (amplitude >= vadSpeechThreshold) {
                        if (!hasSpoken) {
                            // Barge-In: user started speaking — immediately mute speaker
                            hasSpoken = true
                            audioPlaybackHelper.stop()
                            processingFeedbackHelper?.stopWorkingFeedback()
                            _uiState.update { it.copy(heardSpeech = true) }
                        }
                        silenceMs = 0L
                    } else if (hasSpoken) {
                        silenceMs += vadPollIntervalMs
                        if (silenceMs >= vadSilenceAutoSubmitMs) break
                    }
                }

                if (!_uiState.value.isRecordingVoice) return@launch // cancelled externally

                if (hasSpoken) {
                    stopAndSubmitVoiceRecording(context)
                } else if (_uiState.value.isAskQuestionOpen) {
                    cancelVoiceRecording()
                    val prompt = when (_uiState.value.selectedLanguage) {
                        SupportedLanguage.ENGLISH -> "I didn't hear your question, please tap the mic to speak."
                        SupportedLanguage.AFAN_OROMOO -> "Gaaffii kee hin dhageenye, maaloo irra deebi'ii dubbadhu."
                        SupportedLanguage.AMHARIC -> "ጥያቄዎን አልሰማሁም፤ እባክዎ ማይክሮፎኑን ተጭነው እንደገና ይናገሩ።"
                    }
                    _userMessage.tryEmit(prompt)
                } else {
                    // Silence fallback: no speech detected in vision mode → stop mic, describe current view
                    cancelVoiceRecording()
                    submitSilenceFallback(context)
                }
            }
        } else {
            if (!_uiState.value.isAskQuestionOpen) {
                // Recording init failed (e.g. emulator without mic) → describe surroundings
                viewModelScope.launch { submitSilenceFallback(context) }
            }
        }
    }

    /**
     * Silence Fallback: freezes the live CameraX preview frame and asks the
     * server to describe the surroundings ("ትዕይንቱን ግለጽልኝ") when the user says nothing.
     */
    private suspend fun submitSilenceFallback(context: Context) {
        if (_uiState.value.isBusy) return

        val freshFrame = try {
            frameProvider?.invoke()
        } catch (e: Throwable) {
            null
        }

        if (freshFrame != null) {
            _uiState.update {
                it.copy(
                    lockedImageBase64 = freshFrame,
                    lastCapturedBase64 = freshFrame,
                    liveTranscription = null
                )
            }
        }

        val notice = when (_uiState.value.selectedLanguage) {
            SupportedLanguage.ENGLISH -> "No speech detected — describing what I see…"
            SupportedLanguage.AFAN_OROMOO -> "Sagaleen dhaga'ameera — waanan argu ibsa…"
            SupportedLanguage.AMHARIC -> "ድምፅ አልተሰማም — የያዝኩትን እገልጻለሁ…"
        }
        _userMessage.tryEmit(notice)

        executeFollowUpRequest(
            context = context,
            voiceBase64 = null,
            textQuery = silenceFallbackQuery,
            freshImageBase64 = freshFrame
        )
    }

    fun stopAndSubmitVoiceRecording(context: Context) {
        recordingJob?.cancel()
        recordingJob = null

        if (!_uiState.value.isRecordingVoice) return

        playTapEarcon()
        _uiState.update { it.copy(isRecordingVoice = false, micLevel = 0f) }

        val voiceBase64 = voiceRecorderHelper?.stopRecording()
        if (voiceBase64.isNullOrBlank()) {
            // No usable audio captured → describe the current surroundings instead
            viewModelScope.launch { submitSilenceFallback(context) }
            return
        }
        if (_uiState.value.isAskQuestionOpen) {
            // Companion mode: use the realtime Mode B voice turn (Addis AI
            // STT + conversational reply + streamed speech), falling back to
            // the session-based ask-question endpoint on any failure.
            startRealtimeTurn(context, voiceBase64)
        } else {
            executeFollowUpRequest(
                context = context,
                voiceBase64 = voiceBase64,
                textQuery = null
            )
        }
    }

    fun cancelVoiceRecording() {
        recordingJob?.cancel()
        recordingJob = null
        voiceRecorderHelper?.cancelRecording()
        // A cancelled recording can orphan an in-flight realtime turn (no
        // callback will fire after cancel()), so unstick the orb.
        _uiState.update { it.copy(isRecordingVoice = false, heardSpeech = false, micLevel = 0f, isBusy = false) }
    }

    /**
     * Realtime Mode B voice turn: decodes the recorded clip to 16 kHz mono
     * PCM16, POSTs it to /api/addis-realtime/stream, accumulates the streamed
     * MP3 speech, and plays it when the turn completes. Recent conversation
     * turns ride along in systemPrompt so follow-ups keep their context.
     * Any failure falls back to the session-based ask-question endpoint.
     */
    private fun startRealtimeTurn(context: Context, voiceBase64: String) {
        val language = _uiState.value.selectedLanguage.code
        val voice = if (language == "om") "om-abdi" else "am-hamen"
        // Follow-up context: prefer the ask-turn history; on the very first
        // realtime follow-up after a fresh describe, fall back to the rolling
        // vision conversation so the scene description still informs the model.
        val historyTurns = _uiState.value.askQuestionHistory.ifEmpty {
            _uiState.value.conversationHistory
        }
        val recentHistory = historyTurns.takeLast(6)
        val systemPrompt = if (recentHistory.isEmpty()) {
            null
        } else {
            "Previous conversation turns, oldest first. Continue naturally as the assistant:\n" +
                recentHistory.joinToString("\n") { "${it.role}: ${it.text}" }
        }

        _uiState.update { it.copy(isBusy = true, liveTranscription = null) } // orb → Thinking

        val client = realtimeClient ?: AddisRealtimeClient().also { realtimeClient = it }

        viewModelScope.launch {
            // 1. Capture the CURRENT camera view: every question carries the
            //    freshest image so visual questions stay context-aware. Used by
            //    the session-based fallback today, and by Mode B once the
            //    gateway forwards it upstream.
            val freshFrame = try {
                frameProvider?.invoke()
            } catch (_: Throwable) {
                null
            }
            if (freshFrame != null) {
                _uiState.update {
                    it.copy(lockedImageBase64 = freshFrame, lastCapturedBase64 = freshFrame)
                }
            }

            // 2. Decode AAC/M4A → 16 kHz mono PCM16 (off the main thread).
            val pcm16 = withContext(Dispatchers.Default) {
                Pcm16Decoder.decodeTo16kMonoPcm16(context, voiceBase64)
            }
            if (pcm16 == null) {
                _uiState.update { it.copy(isBusy = false) }
                executeFollowUpRequest(
                    context,
                    voiceBase64 = voiceBase64,
                    textQuery = null,
                    freshImageBase64 = freshFrame
                )
                return@launch
            }
            val audioBase64 = android.util.Base64.encodeToString(pcm16, android.util.Base64.NO_WRAP)

            // 3. Stream the turn.
            client.onComplete = { userText, assistantText, mp3Base64 ->
                viewModelScope.launch {
                    val turns = buildList {
                        if (!userText.isNullOrBlank()) add(ConversationTurn("user", userText))
                        if (!assistantText.isNullOrBlank()) add(ConversationTurn("assistant", assistantText))
                    }
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            askQuestionHistory = it.askQuestionHistory + turns,
                            askQuestionLastAnswer = assistantText ?: it.askQuestionLastAnswer,
                            lastAnswer = assistantText ?: it.lastAnswer,
                            liveTranscription = null
                        )
                    }
                    if (!mp3Base64.isNullOrBlank()) {
                        // Playback flips the orb to Speaking via the playback listener;
                        // also records lastAudioFile so "Repeat last answer" works.
                        audioPlaybackHelper.playBase64Audio(context, mp3Base64)
                    } else if (_uiState.value.isAskQuestionOpen) {
                        executeFollowUpRequest(
                            context,
                            voiceBase64 = voiceBase64,
                            textQuery = null,
                            freshImageBase64 = freshFrame
                        )
                    }
                }
            }
            client.onError = { message ->
                viewModelScope.launch {
                    _uiState.update { it.copy(isBusy = false) }
                    showMessage("Realtime voice unavailable ($message) — using standard mode")
                    executeFollowUpRequest(
                        context,
                        voiceBase64 = voiceBase64,
                        textQuery = null,
                        freshImageBase64 = freshFrame
                    )
                }
            }
            client.sendVoiceTurn(
                audioBase64 = audioBase64,
                language = language,
                voice = voice,
                systemPrompt = systemPrompt,
                image = freshFrame
            )
        }
    }

    fun onAction(context: Context, action: AyneAction) {
        // Don't barge-in when tapping Ask while recording — that tap means "submit the take"
        val isSubmitRecordingTap = action is AyneAction.AskQuestion && _uiState.value.isRecordingVoice
        if (!isSubmitRecordingTap) {
            bargeIn()
        }
        playTapEarcon()

        when (action) {
            is AyneAction.DescribeSurroundings -> {
                executeVisionRequest(context, action.frameBase64)
            }
            is AyneAction.ReadText -> {
                executeReadTextRequest(context, action.frameBase64)
            }
            is AyneAction.StartVoiceRecording -> {
                startVoiceRecording(context)
            }
            is AyneAction.StopAndSubmitVoiceRecording -> {
                stopAndSubmitVoiceRecording(context)
            }
            is AyneAction.CancelVoiceRecording -> {
                cancelVoiceRecording()
            }
            is AyneAction.AskFollowUp -> {
                executeFollowUpRequest(context, action.voiceBase64, action.textQuery)
            }
            is AyneAction.DismissResult -> {
                _uiState.update {
                    it.copy(
                        lastReadResponse = null,
                        lastResponse = null,
                        lastAnswer = null,
                        lastEnglishAnswer = null,
                        liveTranscription = null
                    )
                }
            }
            is AyneAction.ClearSession -> {
                resetVisualSession()
                _uiState.update {
                    it.copy(
                        activeSessionId = null,
                        lockedImageBase64 = null,
                        lastCapturedBase64 = null,
                        lastReadResponse = null,
                        lastResponse = null,
                        lastAnswer = null,
                        lastEnglishAnswer = null,
                        conversationHistory = emptyList(),
                        isRecordingVoice = false,
                        heardSpeech = false,
                        micLevel = 0f,
                        liveTranscription = null
                    )
                }
                _userMessage.tryEmit("Scene cleared. Ready for fresh capture.")
            }
            is AyneAction.AskQuestion -> {
                if (_uiState.value.isRecordingVoice) {
                    stopAndSubmitVoiceRecording(context)
                } else if (!_uiState.value.isCompanionMode && !_uiState.value.isAskQuestionOpen) {
                    enterCompanionMode(context)
                } else {
                    startVoiceRecording(context)
                }
            }
            is AyneAction.OpenAskQuestion -> {
                openAskQuestion(context, action.frameBase64)
            }
            is AyneAction.EnterCompanionMode -> {
                openAskQuestion(context)
            }
            is AyneAction.CloseAskQuestion, is AyneAction.ExitCompanionMode -> {
                exitCompanionMode()
            }
            is AyneAction.ClearAskQuestionHistory -> {
                clearAskQuestionSession(context)
            }
            is AyneAction.SubmitAskQuestion -> {
                executeFollowUpRequest(context, voiceBase64 = null, textQuery = action.query)
            }
            is AyneAction.AskCompanionQuestion -> {
                executeFollowUpRequest(context, voiceBase64 = null, textQuery = action.query)
            }
            is AyneAction.RepeatLastAnswer -> {
                repeatLastAnswer(context)
            }
            is AyneAction.SelectLanguage -> {
                val newVoice = when (action.language) {
                    SupportedLanguage.AFAN_OROMOO -> {
                        if (!_uiState.value.customVoice.startsWith("om-")) "om-ayyaantuu" else _uiState.value.customVoice
                    }
                    SupportedLanguage.AMHARIC -> {
                        if (_uiState.value.customVoice.startsWith("om-")) "am-hamen" else _uiState.value.customVoice
                    }
                    SupportedLanguage.ENGLISH -> _uiState.value.customVoice
                }
                _uiState.update { it.copy(selectedLanguage = action.language, customVoice = newVoice) }
                _userMessage.tryEmit("Language: ${action.language.displayName}")
            }
            is AyneAction.FlipCamera -> {
                val newIsFront = !_uiState.value.isFrontCamera
                _uiState.update { it.copy(isFrontCamera = newIsFront) }
                val feedback = if (newIsFront) {
                    context.getString(R.string.camera_switched_front)
                } else {
                    context.getString(R.string.camera_switched_back)
                }
                _userMessage.tryEmit(feedback)
            }
            is AyneAction.UpdateEndpoints -> {
                val cleanedBase = action.baseUrl?.trim()?.trimEnd('/').orEmpty()
                val cleanedVision = action.visionUrl.trim()
                val cleanedRead = action.readTextUrl.trim()
                val cleanedAsk = action.askQuestionUrl?.trim().orEmpty()
                _uiState.update {
                    val newBase = cleanedBase.ifBlank { it.baseUrl }
                    it.copy(
                        baseUrl = newBase,
                        apiEndpoint = cleanedVision.ifBlank { it.apiEndpoint },
                        readTextEndpoint = cleanedRead.ifBlank { it.readTextEndpoint },
                        askQuestionEndpoint = cleanedAsk.ifBlank { it.askQuestionEndpoint }
                    )
                }
                _userMessage.tryEmit("API Configuration updated")
            }
            is AyneAction.UpdateVoice -> {
                _uiState.update { it.copy(customVoice = action.voiceId) }
                _userMessage.tryEmit("Voice set to ${action.voiceId}")
            }
            is AyneAction.VolumeCaptureTriggered -> {
                _userMessage.tryEmit("Volume ▲: Capturing fresh surroundings...")
            }
            is AyneAction.VolumeFollowUpTriggered -> {
                if (_uiState.value.isRecordingVoice) {
                    // While already recording (ask tab), Volume− submits the take.
                    stopAndSubmitVoiceRecording(context)
                } else if (_uiState.value.isAskQuestionOpen) {
                    // Inside the ask tab, not yet recording → start recording.
                    startVoiceRecording(context)
                } else {
                    // Anywhere else (home / vision): Volume− is SEE & DESCRIBE.
                    // It never starts recordings or opens ask sessions here;
                    // its old silence-fallback describe path was exactly the
                    // "Volume− messes with describe" bug.
                    viewModelScope.launch {
                        val frame = try {
                            frameProvider?.invoke()
                        } catch (_: Throwable) {
                            null
                        }
                        if (frame != null) {
                            executeVisionRequest(context, frame)
                        } else {
                            _userMessage.tryEmit(context.getString(R.string.no_scene_in_memory))
                        }
                    }
                }
            }
        }
    }

    /**
     * Volume+ / Fresh capture:
     * 1. Snaps camera frame.
     * 2. Resets conversation history (history.clear()) & activeSessionId.
     * 3. Locks image in memory as lockedImageBase64.
     * 4. Describes surroundings out loud and updates rolling history.
     */
    private fun executeVisionRequest(
        context: Context,
        frameBase64: String
    ) {
        if (_uiState.value.isBusy) return

        val state = _uiState.value
        val progressMsg = context.getString(R.string.analyzing_surroundings)

        // Reset history, activeSessionId, lock image, and clear previous optical readings
        activeSessionId = null
        _uiState.update {
            it.copy(
                activeSessionId = null,
                isBusy = true,
                busyMessage = progressMsg,
                lockedImageBase64 = frameBase64,
                lastCapturedBase64 = frameBase64,
                lastReadResponse = null,
                lastResponse = null,
                conversationHistory = emptyList(),
                isRecordingVoice = false,
                micLevel = 0f
            )
        }
        processingFeedbackHelper?.startWorkingFeedback(viewModelScope)

        activeApiJob = viewModelScope.launch {
            val prefLang = when (state.selectedLanguage) {
                SupportedLanguage.ENGLISH -> "en"
                SupportedLanguage.AFAN_OROMOO -> "om"
                SupportedLanguage.AMHARIC -> "am"
            }
            val endpoint = when (prefLang) {
                "om" -> "${state.baseUrl.trimEnd('/')}/api/see-and-describe/om"
                "am" -> "${state.baseUrl.trimEnd('/')}/api/see-and-describe/am"
                else -> "${state.baseUrl.trimEnd('/')}/api/see-and-describe"
            }
            val voice = if (state.selectedLanguage == SupportedLanguage.AFAN_OROMOO && !state.customVoice.startsWith("om-")) {
                "om-ayyaantuu"
            } else if (state.selectedLanguage == SupportedLanguage.AMHARIC && state.customVoice.startsWith("om-")) {
                "am-hamen"
            } else {
                state.customVoice
            }
            var receivedVision = false
            val audioChunks = StringBuilder()

            // 1. Attempt ultra-low latency SSE streaming (~500ms vision arrival)
            val streamResult = visionApiService.seeAndDescribeStream(
                baseEndpointUrl = endpoint,
                imageBase64 = frameBase64,
                sessionId = null,
                audioBase64 = null,
                query = null,
                history = emptyList(),
                customVoice = voice,
                ttsProvider = "addis",
                preferredLanguage = prefLang,
                mode = "general",
                onVision = { visionEvent ->
                    receivedVision = true
                    processingFeedbackHelper?.stopWorkingFeedback()
                    processingFeedbackHelper?.playSuccessTone()
                    if (!visionEvent.sessionId.isNullOrBlank()) {
                        activeSessionId = visionEvent.sessionId
                    }
                    val chosenAnswer = visionEvent.cleanSpeech.ifBlank {
                        when (state.selectedLanguage) {
                            SupportedLanguage.ENGLISH -> visionEvent.english.ifBlank { visionEvent.amharic }
                            SupportedLanguage.AFAN_OROMOO -> visionEvent.oromo.ifBlank { visionEvent.english }
                            SupportedLanguage.AMHARIC -> visionEvent.amharic.ifBlank { visionEvent.english }
                        }
                    }.cleanLabelPrefix(prefLang)

                    val initialHistory = listOf(
                        ConversationTurn("user", "Describe my surroundings"),
                        ConversationTurn("model", chosenAnswer)
                    )

                    _uiState.update {
                        it.copy(
                            activeSessionId = activeSessionId,
                            lastAnswer = chosenAnswer,
                            lastEnglishAnswer = visionEvent.english,
                            lastReadResponse = null,
                            conversationHistory = initialHistory,
                            lockedImageBase64 = frameBase64,
                            busyMessage = null
                        )
                    }
                },
                onAudioChunk = { chunkEvent ->
                    if (!_uiState.value.isRecordingVoice) {
                        audioChunks.append(chunkEvent.chunkBase64)
                        audioPlaybackHelper.enqueueAudioChunk(
                            context = context,
                            rawBase64Chunk = chunkEvent.chunkBase64,
                            format = chunkEvent.format,
                            onError = { err ->
                                _userMessage.tryEmit("Audio error: $err")
                            }
                        )
                    }
                    // else: defense in depth — an interrupted stream (or one racing
                    // a just-started recording) must never feed the speaker,
                    // otherwise the mic hears the old answer instead of the user.
                },
                onDone = {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            busyMessage = null
                        )
                    }
                    val fullAudio = audioChunks.toString()
                    if (fullAudio.isNotBlank()) {
                        _uiState.update { it.copy(lastAudioBase64 = fullAudio) }
                        audioPlaybackHelper.setLastConsolidatedAudio(context, fullAudio)
                    } else {
                        val answerToSpeak = _uiState.value.lastAnswer
                        if (!answerToSpeak.isNullOrBlank()) {
                            synthesizeAndPlayTtsFallback(context, answerToSpeak, _uiState.value)
                        }
                    }
                }
            )

            // 2. If streaming failed, fallback to single-pass seeAndDescribe
            if (streamResult.isFailure && !receivedVision) {
                val singleResult = visionApiService.seeAndDescribe(
                    endpointUrl = endpoint,
                    imageBase64 = frameBase64,
                    sessionId = null,
                    audioBase64 = null,
                    query = null,
                    history = emptyList(),
                    customVoice = voice,
                    ttsProvider = "addis",
                    preferredLanguage = prefLang,
                    mode = "general",
                    includeAudio = true
                )

                singleResult.onSuccess { response ->
                    processingFeedbackHelper?.stopWorkingFeedback()
                    processingFeedbackHelper?.playSuccessTone()
                    if (!response.sessionId.isNullOrBlank()) {
                        activeSessionId = response.sessionId
                    }
                    val chosenAnswer = response.cleanSpeech.ifBlank {
                        when (state.selectedLanguage) {
                            SupportedLanguage.ENGLISH -> response.english.ifBlank { response.amharic }
                            SupportedLanguage.AFAN_OROMOO -> response.oromo.ifBlank { response.english }
                            SupportedLanguage.AMHARIC -> response.amharic.ifBlank { response.english }
                        }
                    }.cleanLabelPrefix(prefLang)

                    val initialHistory = listOf(
                        ConversationTurn("user", "Describe my surroundings"),
                        ConversationTurn("model", chosenAnswer)
                    )

                    _uiState.update {
                        it.copy(
                            activeSessionId = activeSessionId,
                            isBusy = false,
                            busyMessage = null,
                            lastAnswer = chosenAnswer,
                            lastEnglishAnswer = response.english,
                            lastAudioBase64 = response.audio?.audioBase64,
                            lastResponse = response,
                            lastReadResponse = null,
                            lockedImageBase64 = frameBase64,
                            conversationHistory = initialHistory
                        )
                    }

                    val rawAudio = response.audio?.audioBase64
                    val serverHadJson = hasRawJsonArtifacts(response.amharic, response.oromo, response.english, response.cleanSpeech) ||
                        (response.audio?.durationSeconds != null && response.audio.durationSeconds < 0.6 && chosenAnswer.length > 25)

                    if (!rawAudio.isNullOrBlank() && !serverHadJson) {
                        audioPlaybackHelper.playBase64Audio(
                            context = context,
                            rawBase64Audio = rawAudio,
                            onError = { err ->
                                _userMessage.tryEmit("Audio error: $err")
                            }
                        )
                    } else if (chosenAnswer.isNotBlank()) {
                        synthesizeAndPlayTtsFallback(context, chosenAnswer, state)
                    }
                }.onFailure { error ->
                    processingFeedbackHelper?.stopWorkingFeedback()
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            busyMessage = null
                        )
                    }
                    val errMsg = error.localizedMessage ?: "Network error connecting to $endpoint"
                    _userMessage.tryEmit("Error: $errMsg")
                }
            } else if (streamResult.isFailure && receivedVision) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        busyMessage = null
                    )
                }
            }
        }
    }

    /**
     * Volume- / Multi-turn Visual Follow-up:
     * Turn 2+: Zero-Upload Visual Session Caching (sessionId):
     * - Sends ONLY sessionId & query — 0 image bytes are uploaded!
     * - Reuses server-cached image frame.
     * - Total follow-up response time cut to ~2.8s.
     */
    fun executeFollowUpRequest(
        context: Context,
        voiceBase64: String?,
        textQuery: String?,
        freshImageBase64: String? = null
    ) {
        val state = _uiState.value
        val sid = state.askQuestionSessionId ?: activeSessionId ?: state.activeSessionId
        val lockedImage = freshImageBase64 ?: state.lockedImageBase64

        // Check for session reset command (e.g. "አዲስ ጀምር" / "start over")
        val isResetCommand = textQuery?.contains("አዲስ ጀምር") == true ||
                textQuery?.contains("start over", ignoreCase = true) == true
        if (isResetCommand) {
            clearAskQuestionSession(context)
            return
        }

        if (state.isBusy) return

        val progressMsg = context.getString(R.string.analyzing_followup)
        _uiState.update {
            it.copy(
                isBusy = true,
                busyMessage = progressMsg,
                lastReadResponse = null
            )
        }
        processingFeedbackHelper?.startWorkingFeedback(viewModelScope)

        activeApiJob = viewModelScope.launch {
            val prefLang = when (state.selectedLanguage) {
                SupportedLanguage.ENGLISH -> "en"
                SupportedLanguage.AFAN_OROMOO -> "om"
                SupportedLanguage.AMHARIC -> "am"
            }
            val askEndpoint = when (prefLang) {
                "om" -> "${state.baseUrl.trimEnd('/')}/api/ask-question/om"
                "am" -> "${state.baseUrl.trimEnd('/')}/api/ask-question/am"
                else -> "${state.baseUrl.trimEnd('/')}/api/ask-question"
            }
            val voice = if (state.selectedLanguage == SupportedLanguage.AFAN_OROMOO && !state.customVoice.startsWith("om-")) {
                "om-ayyaantuu"
            } else if (state.selectedLanguage == SupportedLanguage.AMHARIC && state.customVoice.startsWith("om-")) {
                "am-hamen"
            } else {
                state.customVoice
            }
            val sendSessionId = sid

            var receivedAnswer = false
            var currentTranscribedQuery: String? = textQuery
            val audioChunks = StringBuilder()

            // 1. Primary: Dedicated Real-Time SSE Stream (POST /api/ask-question/{lang}/stream)
            val streamResult = visionApiService.askQuestionStream(
                baseEndpointUrl = askEndpoint,
                sessionId = sendSessionId,
                audioBase64 = voiceBase64,
                query = textQuery,
                imageBase64 = if (sendSessionId == null) lockedImage else null,
                customVoice = voice,
                ttsProvider = "addis",
                preferredLanguage = prefLang,
                includeAudio = true,
                onTranscription = { transEv ->
                    val queryText = transEv.transcribedQuery.ifBlank { textQuery ?: "" }
                    currentTranscribedQuery = queryText
                    if (queryText.isNotBlank()) {
                        val transMsg = when (prefLang) {
                            "en" -> "Question: \"$queryText\""
                            "om" -> "Gaaffii: \"$queryText\""
                            else -> "ጥያቄ፡ \"$queryText\""
                        }
                        _uiState.update { it.copy(busyMessage = transMsg, liveTranscription = queryText) }
                        _userMessage.tryEmit(transMsg)
                    }
                },
                onAnswer = { ansEv ->
                    receivedAnswer = true
                    processingFeedbackHelper?.stopWorkingFeedback()
                    processingFeedbackHelper?.playSuccessTone()
                    val newSid = ansEv.sessionId?.takeIf { it.isNotBlank() } ?: sendSessionId
                    val chosenAnswer = ansEv.cleanSpeech.ifBlank {
                        when (state.selectedLanguage) {
                            SupportedLanguage.ENGLISH -> ansEv.english.ifBlank { ansEv.amharic }
                            SupportedLanguage.AFAN_OROMOO -> ansEv.oromo.ifBlank { ansEv.english }
                            SupportedLanguage.AMHARIC -> ansEv.amharic.ifBlank { ansEv.english }
                        }
                    }.cleanLabelPrefix(prefLang)

                    val userTurn = currentTranscribedQuery?.takeIf { it.isNotBlank() } ?: textQuery ?: "Question"
                    val updatedAskHistory = (state.askQuestionHistory + listOf(
                        ConversationTurn("user", userTurn),
                        ConversationTurn("model", chosenAnswer)
                    )).takeLast(20)

                    _uiState.update {
                        it.copy(
                            askQuestionSessionId = newSid,
                            askQuestionHistory = updatedAskHistory,
                            askQuestionLastAnswer = chosenAnswer,
                            lastAnswer = chosenAnswer,
                            lastEnglishAnswer = ansEv.english,
                            lastReadResponse = null,
                            busyMessage = null
                        )
                    }
                },
                onAudioChunk = { chunkEv ->
                    if (!_uiState.value.isRecordingVoice) {
                        audioChunks.append(chunkEv.chunkBase64)
                        // Progressive streaming MP3 playback through speaker (<1.2s first word)
                        audioPlaybackHelper.enqueueAudioChunk(
                            context = context,
                            rawBase64Chunk = chunkEv.chunkBase64,
                            format = chunkEv.format,
                            onError = { err ->
                                _userMessage.tryEmit("Audio error: $err")
                            }
                        )
                    }
                    // else: never feed the speaker while the user is recording —
                    // the mic would hear the old answer instead of their question.
                },
                onErrorEvent = { code, message ->
                    processingFeedbackHelper?.stopWorkingFeedback()
                    processingFeedbackHelper?.playInstantTapEarcon()
                    if (code == "SESSION_EXPIRED") {
                        _uiState.update {
                            it.copy(
                                askQuestionSessionId = null,
                                isBusy = false,
                                busyMessage = null
                            )
                        }
                        val prompt = if (state.selectedLanguage == SupportedLanguage.AFAN_OROMOO) {
                            context.getString(R.string.session_expired_prompt_om)
                        } else {
                            context.getString(R.string.session_expired_prompt)
                        }
                        _userMessage.tryEmit(prompt)
                    } else if (code == "INADMISSIBLE_AUDIO") {
                        _uiState.update {
                            it.copy(
                                isBusy = false,
                                busyMessage = null
                            )
                        }
                        val prompt = if (state.selectedLanguage == SupportedLanguage.AFAN_OROMOO) {
                            context.getString(R.string.inadmissible_audio_prompt_om)
                        } else if (message.isNotBlank() && message != "INADMISSIBLE_AUDIO") {
                            message
                        } else {
                            context.getString(R.string.inadmissible_audio_prompt)
                        }
                        _userMessage.tryEmit(prompt)
                    } else {
                        _uiState.update {
                            it.copy(
                                isBusy = false,
                                busyMessage = null
                            )
                        }
                        _userMessage.tryEmit("Error ($code): $message")
                    }
                },
                onDone = {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            busyMessage = null
                        )
                    }
                    val fullAudio = audioChunks.toString()
                    if (fullAudio.isNotBlank()) {
                        _uiState.update { it.copy(lastAudioBase64 = fullAudio) }
                        audioPlaybackHelper.setLastConsolidatedAudio(context, fullAudio)
                    } else {
                        val answerToSpeak = _uiState.value.askQuestionLastAnswer ?: _uiState.value.lastAnswer
                        if (!answerToSpeak.isNullOrBlank()) {
                            synthesizeAndPlayTtsFallback(context, answerToSpeak, _uiState.value)
                        }
                    }
                }
            )

            // 2. Fallback to buffered JSON (POST /api/ask-question or /api/ask-question/om or /api/ask-question/am)
            if (streamResult.isFailure && !receivedAnswer) {
                val bufferedResult = visionApiService.askQuestion(
                    endpointUrl = askEndpoint,
                    sessionId = sendSessionId,
                    audioBase64 = voiceBase64,
                    query = textQuery,
                    imageBase64 = if (sendSessionId == null) lockedImage else null,
                    customVoice = voice,
                    ttsProvider = "addis",
                    preferredLanguage = prefLang,
                    includeAudio = true
                )

                bufferedResult.onSuccess { response ->
                    processingFeedbackHelper?.stopWorkingFeedback()
                    processingFeedbackHelper?.playSuccessTone()
                    val newSid = response.sessionId?.takeIf { it.isNotBlank() } ?: sendSessionId
                    val chosenAnswer = response.cleanSpeech.ifBlank {
                        when (state.selectedLanguage) {
                            SupportedLanguage.ENGLISH -> response.english.ifBlank { response.amharic }
                            SupportedLanguage.AFAN_OROMOO -> response.oromo.ifBlank { response.english }
                            SupportedLanguage.AMHARIC -> response.amharic.ifBlank { response.english }
                        }
                    }.cleanLabelPrefix(prefLang)

                    val userTurn = response.transcribedQuery?.takeIf { it.isNotBlank() } ?: currentTranscribedQuery ?: textQuery ?: "Question"
                    val updatedAskHistory = (state.askQuestionHistory + listOf(
                        ConversationTurn("user", userTurn),
                        ConversationTurn("model", chosenAnswer)
                    )).takeLast(20)

                    _uiState.update {
                        it.copy(
                            askQuestionSessionId = newSid,
                            askQuestionHistory = updatedAskHistory,
                            askQuestionLastAnswer = chosenAnswer,
                            isBusy = false,
                            busyMessage = null,
                            lastAnswer = chosenAnswer,
                            lastEnglishAnswer = response.english,
                            lastAudioBase64 = response.audio?.audioBase64,
                            lastResponse = response,
                            lastReadResponse = null,
                            liveTranscription = response.transcribedQuery?.takeIf { q -> q.isNotBlank() } ?: it.liveTranscription
                        )
                    }

                    val rawAudio = response.audio?.audioBase64
                    val serverHadJson = hasRawJsonArtifacts(response.amharic, response.oromo, response.english, response.cleanSpeech) ||
                        (response.audio?.durationSeconds != null && response.audio.durationSeconds < 0.6 && chosenAnswer.length > 25)

                    if (!rawAudio.isNullOrBlank() && !serverHadJson) {
                        audioPlaybackHelper.playBase64Audio(
                            context = context,
                            rawBase64Audio = rawAudio,
                            onError = { err ->
                                _userMessage.tryEmit("Audio error: $err")
                            }
                        )
                    } else if (chosenAnswer.isNotBlank()) {
                        synthesizeAndPlayTtsFallback(context, chosenAnswer, state)
                    }
                }.onFailure { error ->
                    processingFeedbackHelper?.stopWorkingFeedback()
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            busyMessage = null
                        )
                    }
                    val errorPrompt = when (state.selectedLanguage) {
                        SupportedLanguage.ENGLISH -> "Unable to process question. Please check your internet connection."
                        SupportedLanguage.AFAN_OROMOO -> "Gaaffii kee deebisuun hin danda'amne. Maaloo intarneetii kee mirkaneeffadhu."
                        SupportedLanguage.AMHARIC -> "ጥያቄዎን ማስተናገድ አልተቻለም። እባክዎ የበይነመረብ ግንኙነትዎን ያረጋግጡ።"
                    }
                    _userMessage.tryEmit(errorPrompt)
                }
            } else if (streamResult.isFailure && receivedAnswer) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        busyMessage = null
                    )
                }
            }
        }
    }

    private fun executeReadTextRequest(
        context: Context,
        frameBase64: String
    ) {
        if (_uiState.value.isBusy) return

        val state = _uiState.value
        val progressMsg = context.getString(R.string.reading_text_progress)

        _uiState.update {
            it.copy(
                isBusy = true,
                busyMessage = progressMsg,
                lastCapturedBase64 = frameBase64,
                lastReadResponse = null
            )
        }
        processingFeedbackHelper?.startWorkingFeedback(viewModelScope)

        activeApiJob = viewModelScope.launch {
            val prefLang = when (state.selectedLanguage) {
                SupportedLanguage.ENGLISH -> "en"
                SupportedLanguage.AFAN_OROMOO -> "om"
                SupportedLanguage.AMHARIC -> "am"
            }
            val endpoint = when (prefLang) {
                "om" -> "${state.baseUrl.trimEnd('/')}/api/read-text/om"
                "am" -> "${state.baseUrl.trimEnd('/')}/api/read-text/am"
                else -> "${state.baseUrl.trimEnd('/')}/api/read-text"
            }
            val voice = state.customVoice
            val ttsProvider = if (prefLang == "en") "gemini" else "addis"

            var receivedOcr = false
            val audioChunks = StringBuilder()

            // 1. Connect to Realtime Addis AI Streaming SSE endpoint
            val streamResult = visionApiService.readTextStream(
                baseEndpointUrl = endpoint,
                imageBase64 = frameBase64,
                query = null,
                audioBase64 = null,
                customVoice = voice,
                ttsProvider = ttsProvider,
                preferredLanguage = prefLang,
                onOcr = { ocrEvent ->
                    receivedOcr = true
                    // Immediate acoustic feedback: stop pulsing tone and chime success
                    processingFeedbackHelper?.stopWorkingFeedback()
                    processingFeedbackHelper?.playSuccessTone()

                    val response = ocrEvent.toReadTextResponse()
                    val chosenAnswer = response.cleanSpeech.ifBlank {
                        when {
                            prefLang == "en" && response.english.isNotBlank() -> response.english
                            prefLang == "om" && response.oromo.isNotBlank() -> response.oromo
                            else -> response.amharic.ifBlank { response.english }
                        }
                    }.cleanLabelPrefix(prefLang)

                    val guidanceMsg = when {
                        !response.cameraGuidance.isNullOrBlank() -> response.cameraGuidance
                        response.isCutOff -> context.getString(R.string.text_cut_off_warning)
                        response.isBlurry -> context.getString(R.string.text_blurry_warning)
                        !response.isLegible -> "ጽሑፉ በግልጽ አይታይም፤ እባክዎ ካሜራውን ወደ ጽሑፉ ያቅርቡ"
                        else -> null
                    }
                    if (!guidanceMsg.isNullOrBlank()) {
                        _userMessage.tryEmit(guidanceMsg)
                    }

                    _uiState.update {
                        it.copy(
                            lastAnswer = chosenAnswer,
                            lastEnglishAnswer = response.english,
                            lastReadResponse = response,
                            lastResponse = null,
                            busyMessage = null
                        )
                    }
                },
                onAudioChunk = { chunkEvent ->
                    if (!_uiState.value.isRecordingVoice) {
                        audioChunks.append(chunkEvent.chunkBase64)
                        // Play audio chunk through speaker right away (~700ms after OCR)
                        audioPlaybackHelper.enqueueAudioChunk(
                            context = context,
                            rawBase64Chunk = chunkEvent.chunkBase64,
                            format = chunkEvent.format,
                            onError = { err ->
                                _userMessage.tryEmit("Audio error: $err")
                            }
                        )
                    }
                    // else: never feed the speaker while the user is recording.
                },
                onDone = {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            busyMessage = null
                        )
                    }
                    val fullAudio = audioChunks.toString()
                    if (fullAudio.isNotBlank()) {
                        _uiState.update { it.copy(lastAudioBase64 = fullAudio) }
                        audioPlaybackHelper.setLastConsolidatedAudio(context, fullAudio)
                    } else {
                        val answerToSpeak = _uiState.value.lastAnswer
                        if (!answerToSpeak.isNullOrBlank()) {
                            synthesizeAndPlayTtsFallback(context, answerToSpeak, _uiState.value)
                        }
                    }
                }
            )

            // 2. If streaming failed before OCR, fallback to single-pass buffered REST
            if (streamResult.isFailure && !receivedOcr) {
                val singleResult = visionApiService.readText(
                    endpointUrl = endpoint,
                    imageBase64 = frameBase64,
                    query = null,
                    audioBase64 = null,
                    customVoice = voice,
                    ttsProvider = ttsProvider,
                    preferredLanguage = prefLang,
                    includeAudio = true
                )

                singleResult.onSuccess { response ->
                    processingFeedbackHelper?.stopWorkingFeedback()
                    processingFeedbackHelper?.playSuccessTone()
                    val chosenAnswer = response.cleanSpeech.ifBlank {
                        when {
                            prefLang == "en" && response.english.isNotBlank() -> response.english
                            prefLang == "om" && response.oromo.isNotBlank() -> response.oromo
                            else -> response.amharic.ifBlank { response.english }
                        }
                    }.cleanLabelPrefix(prefLang)

                    val guidanceMsg = when {
                        !response.cameraGuidance.isNullOrBlank() -> response.cameraGuidance
                        response.isCutOff -> context.getString(R.string.text_cut_off_warning)
                        response.isBlurry -> context.getString(R.string.text_blurry_warning)
                        !response.isLegible -> "ጽሑፉ በግልጽ አይታይም፤ እባክዎ ካሜራውን ወደ ጽሑፉ ያቅርቡ"
                        else -> null
                    }
                    if (!guidanceMsg.isNullOrBlank()) {
                        _userMessage.tryEmit(guidanceMsg)
                    }

                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            busyMessage = null,
                            lastAnswer = chosenAnswer,
                            lastEnglishAnswer = response.english,
                            lastAudioBase64 = response.audio?.audioBase64,
                            lastReadResponse = response,
                            lastResponse = null
                        )
                    }

                    val rawAudio = response.audio?.audioBase64
                    val serverHadJson = hasRawJsonArtifacts(response.amharic, response.oromo, response.english, response.cleanSpeech) ||
                        (response.audio?.durationSeconds != null && response.audio.durationSeconds < 0.6 && chosenAnswer.length > 25)

                    if (!rawAudio.isNullOrBlank() && !serverHadJson) {
                        audioPlaybackHelper.playBase64Audio(
                            context = context,
                            rawBase64Audio = rawAudio,
                            onError = { err ->
                                _userMessage.tryEmit("Audio error: $err")
                            }
                        )
                    } else if (chosenAnswer.isNotBlank()) {
                        synthesizeAndPlayTtsFallback(context, chosenAnswer, state)
                    }
                }.onFailure { error ->
                    processingFeedbackHelper?.stopWorkingFeedback()
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            busyMessage = null
                        )
                    }
                    val errMsg = error.localizedMessage ?: "Network error connecting to $endpoint"
                    _userMessage.tryEmit("Error: $errMsg")
                }
            } else if (streamResult.isFailure && receivedOcr) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        busyMessage = null
                    )
                }
            }
        }
    }

    private fun synthesizeAndPlayTtsFallback(
        context: Context,
        text: String,
        state: AyneUiState
    ) {
        val prefLang = when (state.selectedLanguage) {
            SupportedLanguage.ENGLISH -> "en"
            SupportedLanguage.AFAN_OROMOO -> "om"
            SupportedLanguage.AMHARIC -> "am"
        }
        val cleanText = text.cleanLabelPrefix(prefLang)
        if (cleanText.isBlank()) return

        val voice = if (state.selectedLanguage == SupportedLanguage.AFAN_OROMOO && !state.customVoice.startsWith("om-")) {
            "om-ayyaantuu"
        } else if (state.selectedLanguage == SupportedLanguage.AMHARIC && state.customVoice.startsWith("om-")) {
            "am-hamen"
        } else if (state.selectedLanguage == SupportedLanguage.ENGLISH) {
            "Zephyr"
        } else {
            state.customVoice
        }
        val provider = if (prefLang == "en") "gemini" else "addis"

        viewModelScope.launch {
            val ttsResult = visionApiService.synthesizeTts(
                baseUrl = state.baseUrl,
                text = cleanText,
                customVoice = voice,
                ttsProvider = provider,
                language = prefLang
            )
            ttsResult.onSuccess { audioBase64 ->
                _uiState.update { it.copy(lastAudioBase64 = audioBase64) }
                audioPlaybackHelper.playBase64Audio(
                    context = context,
                    rawBase64Audio = audioBase64,
                    onError = { err ->
                        _userMessage.tryEmit("Audio error: $err")
                    }
                )
            }
        }
    }

    private fun repeatLastAnswer(context: Context) {
        val state = _uiState.value
        if (state.lastAnswer.isNullOrBlank()) {
            _userMessage.tryEmit(context.getString(R.string.snackbar_no_answer))
            return
        }

        val replayed = audioPlaybackHelper.replayLastAudio(
            onError = {
                _userMessage.tryEmit(state.lastAnswer)
            }
        )

        if (!replayed) {
            _userMessage.tryEmit(state.lastAnswer)
        }
    }

    override fun onCleared() {
        super.onCleared()
        processingFeedbackHelper?.release()
        audioPlaybackHelper.stop()
    }
}

// ==========================================
// Main Activity
// ==========================================

class MainActivity : ComponentActivity() {
    private val viewModel: AyneViewModel by viewModels()

    private var onVolumeCaptureTriggered: (() -> Unit)? = null
    private var onVolumeFollowUpTriggered: (() -> Unit)? = null

    private var volumeUpPressTime = 0L
    private var volumeDownPressTime = 0L
    private var isVolumeUpLongPress = false
    private var isVolumeDownLongPress = false
    private var lastVolumeDownReleaseTime = 0L
    private val volumeDownHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingVolumeDownRunnable: Runnable? = null

    fun registerVolumeHandlers(onCapture: () -> Unit, onFollowUp: () -> Unit) {
        onVolumeCaptureTriggered = onCapture
        onVolumeFollowUpTriggered = onFollowUp
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AyneTheme {
                AyneAppScreen(
                    viewModel = viewModel,
                    onRegisterVolumeHandlers = ::registerVolumeHandlers
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pendingVolumeDownRunnable?.let { volumeDownHandler.removeCallbacks(it) }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        // Screen Tap Barge-In: Immediately stop any currently playing speech on physical touch
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            if (viewModel.isSpeaking()) {
                viewModel.bargeIn()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyDown(keyCode, event)

        // Stop any currently playing speech immediately on ANY volume key
        // (speaker barge-in) — but do NOT kill an active voice recording:
        // a Volume− press while recording means "submit this take".
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            viewModel.stopPlaybackOnly()
        }

        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event.repeatCount > 0) {
                    isVolumeUpLongPress = true
                    return super.onKeyDown(keyCode, event)
                }
                volumeUpPressTime = System.currentTimeMillis()
                isVolumeUpLongPress = false
                event.startTracking()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event.repeatCount > 0) {
                    isVolumeDownLongPress = true
                    return super.onKeyDown(keyCode, event)
                }
                volumeDownPressTime = System.currentTimeMillis()
                isVolumeDownLongPress = false
                event.startTracking()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                isVolumeUpLongPress = true
                return super.onKeyLongPress(keyCode, event)
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                isVolumeDownLongPress = true
                return super.onKeyLongPress(keyCode, event)
            }
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!isVolumeUpLongPress && (System.currentTimeMillis() - volumeUpPressTime < 600)) {
                    onVolumeCaptureTriggered?.invoke()
                        ?: viewModel.onAction(this, AyneAction.VolumeCaptureTriggered)
                    return true
                }
                return super.onKeyUp(keyCode, event)
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!isVolumeDownLongPress && (System.currentTimeMillis() - volumeDownPressTime < 600)) {
                    // While a voice question is recording, Volume− ALWAYS submits
                    // the take immediately — no double-tap interpretation, so the
                    // user's recorded question can never be lost or replayed over.
                    if (viewModel.uiState.value.isRecordingVoice) {
                        viewModel.stopAndSubmitVoiceRecording(this@MainActivity)
                        return true
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastVolumeDownReleaseTime < 350) {
                        // Double-tap on Volume-: Instant memory replay of last spoken audio buffer with 0 network calls!
                        pendingVolumeDownRunnable?.let { volumeDownHandler.removeCallbacks(it) }
                        pendingVolumeDownRunnable = null
                        lastVolumeDownReleaseTime = 0L
                        viewModel.onAction(this@MainActivity, AyneAction.RepeatLastAnswer)
                        return true
                    } else {
                        lastVolumeDownReleaseTime = now
                        val runnable = Runnable {
                            onVolumeFollowUpTriggered?.invoke()
                                ?: viewModel.onAction(this@MainActivity, AyneAction.VolumeFollowUpTriggered)
                            pendingVolumeDownRunnable = null
                        }
                        pendingVolumeDownRunnable = runnable
                        volumeDownHandler.postDelayed(runnable, 350)
                        return true
                    }
                }
                return super.onKeyUp(keyCode, event)
            }
        }
        return super.onKeyUp(keyCode, event)
    }
}

// ==========================================
// Screen Composables
// ==========================================

@Composable
fun AyneAppScreen(
    viewModel: AyneViewModel,
    modifier: Modifier = Modifier,
    onRegisterVolumeHandlers: (((() -> Unit), (() -> Unit)) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(context) {
        viewModel.initFeedbackHelper(context)
    }

    // CameraX instance
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val cameraSelector = if (uiState.isFrontCamera) {
        CameraSelector.DEFAULT_FRONT_CAMERA
    } else {
        CameraSelector.DEFAULT_BACK_CAMERA
    }

    LaunchedEffect(lifecycleOwner, uiState.isFrontCamera) {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val cameraProvider = cameraProviderFuture.get()
            if (cameraProvider.hasCamera(cameraSelector)) {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)
            }
        } catch (e: Exception) {
            // Emulators gracefully fallback
        }
    }

    // Provide live frames to the ViewModel for the VAD silence fallback (freeze current view)
    LaunchedEffect(imageCapture) {
        viewModel.setFrameProvider { CameraCaptureHelper.captureFrame(context, imageCapture) }
    }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var pendingPermissionDialog by remember { mutableStateOf<String?>(null) }
    var pendingPermissionMessage by remember { mutableStateOf("") }

    var onCameraGrantedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.showMessage(context.getString(R.string.camera_permission_granted))
            onCameraGrantedAction?.invoke()
        } else {
            pendingPermissionDialog = Manifest.permission.CAMERA
            pendingPermissionMessage = context.getString(R.string.camera_permission_denied)
            viewModel.showMessage(context.getString(R.string.camera_permission_denied))
        }
        onCameraGrantedAction = null
    }

    var onMicGrantedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.showMessage(context.getString(R.string.mic_permission_granted))
            onMicGrantedAction?.invoke()
        } else {
            pendingPermissionDialog = Manifest.permission.RECORD_AUDIO
            pendingPermissionMessage = context.getString(R.string.mic_permission_denied)
            viewModel.showMessage(context.getString(R.string.mic_permission_denied))
        }
        onMicGrantedAction = null
    }

    fun requireCameraPermission(onGranted: () -> Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            onGranted()
        } else {
            onCameraGrantedAction = onGranted
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun requireMicPermission(onGranted: () -> Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            onGranted()
        } else {
            onMicGrantedAction = onGranted
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun triggerDescribeSurroundings() {
        viewModel.bargeIn()
        viewModel.playTapEarcon()
        requireCameraPermission {
            coroutineScope.launch {
                val frame = CameraCaptureHelper.captureFrame(context, imageCapture)
                viewModel.onAction(context, AyneAction.DescribeSurroundings(frame))
            }
        }
    }

    fun captureAskQuestionScene() {
        // Stop the speaker only: capturing a photo must never destroy a voice
        // question the user is still recording.
        viewModel.stopPlaybackOnly()
        viewModel.playTapEarcon()
        requireCameraPermission {
            coroutineScope.launch {
                val frame = try {
                    CameraCaptureHelper.captureFrame(context, imageCapture)
                } catch (_: Throwable) {
                    null
                }
                if (frame != null) {
                    viewModel.setLockedFrame(frame)
                    val capturedMsg = when (uiState.selectedLanguage) {
                        SupportedLanguage.ENGLISH -> "Photo captured. Press Volume− to ask."
                        SupportedLanguage.AFAN_OROMOO -> "Suuraan kaafame. Gaafachuuf Volume gadi dhiibi."
                        SupportedLanguage.AMHARIC -> "ፎቶ ተነስቷል። ለመጠየቅ የድምፅ መቀነሻውን ይጫኑ።"
                    }
                    viewModel.showMessage(capturedMsg)
                }
            }
        }
    }

    /**
     * Volume+ handler. On the Ask Question page it only takes a photo for the
     * upcoming question; everywhere else it keeps the "describe surroundings"
     * behavior.
     */
    fun triggerVolumeCapture() {
        if (uiState.isAskQuestionOpen || uiState.isCompanionMode) {
            captureAskQuestionScene()
        } else {
            triggerDescribeSurroundings()
        }
    }

    fun triggerReadText() {
        viewModel.bargeIn()
        viewModel.playTapEarcon()
        requireCameraPermission {
            coroutineScope.launch {
                val frame = CameraCaptureHelper.captureFrame(context, imageCapture)
                viewModel.onAction(context, AyneAction.ReadText(frame))
            }
        }
    }

    fun triggerVolumeFollowUp() {
        if (uiState.isRecordingVoice) {
            // Already recording: submit what was heard so far!
            viewModel.stopAndSubmitVoiceRecording(context)
        } else if (uiState.isAskQuestionOpen || uiState.isCompanionMode) {
            // Already on Ask Question page: Volume− only records the question.
            // The photo is taken separately with Volume+.
            viewModel.bargeIn()
            requireMicPermission {
                viewModel.startVoiceRecording(context)
            }
        } else {
            // On main screen: Volume− is SEE & DESCRIBE — capture a frame and
            // describe it aloud, exactly like the describe button. Volume−
            // never opens or creates an ask-question session.
            triggerDescribeSurroundings()
        }
    }

    LaunchedEffect(onRegisterVolumeHandlers, uiState.isRecordingVoice, uiState.lockedImageBase64, uiState.activeSessionId, uiState.isCompanionMode, uiState.isAskQuestionOpen) {
        onRegisterVolumeHandlers?.invoke(
            { triggerVolumeCapture() },
            { triggerVolumeFollowUp() }
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.userMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AynePureBlack,
        contentColor = AynePureWhite,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .padding(16.dp)
                    .testTag("snackbar_host")
            ) { data ->
                Snackbar(
                    containerColor = AyneVibrantYellow,
                    contentColor = AyneOnYellow,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = data.visuals.message,
                        style = MaterialTheme.typography.titleMedium,
                        color = AyneOnYellow,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { innerPadding ->
        if (uiState.isCompanionMode) {
            // Full-screen voice-only Ask AI: ripple orb takes over the whole screen.
            VoiceAskScreen(
                uiState = uiState,
                onClose = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.onAction(context, AyneAction.ExitCompanionMode)
                },
                onNewChat = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.onAction(context, AyneAction.ClearAskQuestionHistory)
                },
                onMicTap = { triggerVolumeFollowUp() },
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            when (uiState.selectedLanguage) {
                SupportedLanguage.ENGLISH -> {
                    EnglishMockScreen(
                        uiState = uiState,
                        onAction = { action -> viewModel.onAction(context, action) },
                        onDescribeClick = { triggerDescribeSurroundings() },
                        onReadTextClick = { triggerReadText() },
                        onAskQuestionClick = { triggerVolumeFollowUp() },
                        onRepeatClick = { viewModel.onAction(context, AyneAction.RepeatLastAnswer) },
                        onLanguageSelected = { lang -> viewModel.onAction(context, AyneAction.SelectLanguage(lang)) },
                        onFlipCamera = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.onAction(context, AyneAction.FlipCamera)
                        },
                        onSettingsClick = { showSettingsDialog = true },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                SupportedLanguage.AFAN_OROMOO -> {
                    AfaanOromooMockScreen(
                        uiState = uiState,
                        onAction = { action -> viewModel.onAction(context, action) },
                        onDescribeClick = { triggerDescribeSurroundings() },
                        onReadTextClick = { triggerReadText() },
                        onAskQuestionClick = { triggerVolumeFollowUp() },
                        onRepeatClick = { viewModel.onAction(context, AyneAction.RepeatLastAnswer) },
                        onLanguageSelected = { lang -> viewModel.onAction(context, AyneAction.SelectLanguage(lang)) },
                        onFlipCamera = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.onAction(context, AyneAction.FlipCamera)
                        },
                        onSettingsClick = { showSettingsDialog = true },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                SupportedLanguage.AMHARIC -> {
                    AyneScreenContent(
                        uiState = uiState,
                        onAction = { action -> viewModel.onAction(context, action) },
                        onDescribeClick = { triggerDescribeSurroundings() },
                        onReadTextClick = { triggerReadText() },
                        onAskQuestionClick = { triggerVolumeFollowUp() },
                        onFlipCamera = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.onAction(context, AyneAction.FlipCamera)
                        },
                        onSettingsClick = { showSettingsDialog = true },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        if (showSettingsDialog) {
            VisionSettingsDialog(
                currentBaseUrl = uiState.baseUrl,
                currentEndpoint = uiState.apiEndpoint,
                currentReadTextEndpoint = uiState.readTextEndpoint,
                currentAskQuestionEndpoint = uiState.askQuestionEndpoint,
                currentVoice = uiState.customVoice,
                onDismiss = { showSettingsDialog = false },
                onSave = { newBaseUrl, newVisionUrl, newReadUrl, newAskUrl, newVoice ->
                    showSettingsDialog = false
                    viewModel.onAction(context, AyneAction.UpdateEndpoints(newBaseUrl, newVisionUrl, newReadUrl, newAskUrl))
                    viewModel.onAction(context, AyneAction.UpdateVoice(newVoice))
                }
            )
        }

        if (pendingPermissionDialog != null) {
            AlertDialog(
                onDismissRequest = { pendingPermissionDialog = null },
                shape = RoundedCornerShape(16.dp),
                containerColor = AynePureBlack,
                titleContentColor = AyneVibrantYellow,
                textContentColor = AynePureWhite,
                modifier = Modifier
                    .border(2.dp, AyneVibrantYellow, RoundedCornerShape(16.dp))
                    .testTag("permission_rationale_dialog"),
                title = {
                    Text(
                        text = stringResource(R.string.permission_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AyneVibrantYellow
                    )
                },
                text = {
                    Text(
                        text = pendingPermissionMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = AynePureWhite,
                        lineHeight = 24.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingPermissionDialog = null
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AyneVibrantYellow,
                            contentColor = AyneOnYellow
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("open_settings_button")
                    ) {
                        Text(
                            text = stringResource(R.string.permission_dialog_open_settings),
                            fontWeight = FontWeight.Bold,
                            color = AyneOnYellow
                        )
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { pendingPermissionDialog = null },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("dismiss_permission_dialog_button")
                    ) {
                        Text(
                            text = stringResource(R.string.permission_dialog_dismiss),
                            color = AynePureWhite,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            )
        }
    }
}

// ==========================================
// Main Screen Content
// ==========================================

@Composable
fun AyneScreenContent(
    uiState: AyneUiState,
    onAction: (AyneAction) -> Unit,
    onDescribeClick: () -> Unit,
    onReadTextClick: () -> Unit,
    onAskQuestionClick: () -> Unit,
    onFlipCamera: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AynePureBlack)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Top Bar
        AyneTopBar(
            selectedLanguage = uiState.selectedLanguage,
            isFrontCamera = uiState.isFrontCamera,
            onLanguageSelected = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onAction(AyneAction.SelectLanguage(it))
            },
            onFlipCamera = onFlipCamera,
            onSettingsClick = onSettingsClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Progress / Analysis Indicator banner
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
                    modifier = Modifier.padding(16.dp),
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
                        contentDescription = stringResource(R.string.working_sound_active),
                        tint = AyneVibrantYellow,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = uiState.busyMessage ?: stringResource(R.string.analyzing_surroundings),
                        color = AyneVibrantYellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // Detailed optical reading card (when available)
        val readResp = uiState.lastReadResponse
        var showExtractedText by remember(readResp) { mutableStateOf(false) }

        if (readResp != null && !uiState.isBusy) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .border(2.dp, AyneVibrantYellow, RoundedCornerShape(14.dp))
                    .semantics {
                        contentDescription = "Read Text Result. ${readResp.amharic.ifBlank { readResp.english }}"
                    },
                color = AyneDarkCard,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Header: Type badge & Dismiss button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Description,
                                contentDescription = null,
                                tint = AyneVibrantYellow,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = readResp.signType ?: readResp.documentType ?: stringResource(R.string.read_text_title),
                                color = AyneVibrantYellow,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = { onAction(AyneAction.DismissResult) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.dismiss_result_desc),
                                tint = AynePureWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Spatial Location Context
                    if (!readResp.locationContext.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = AynePureWhite.copy(alpha = 0.8f),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = readResp.locationContext,
                                color = AynePureWhite.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Spoken Reading Text (clean and scroll-constrained)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 110.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = uiState.lastAnswer ?: readResp.amharic,
                            color = AynePureWhite,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Extracted Verbatim Text (Collapsible drawer to prevent screen flooding)
                    if (!readResp.extractedText.isNullOrBlank() && readResp.extractedText != readResp.amharic) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showExtractedText = !showExtractedText },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AyneVibrantYellow),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = AyneVibrantYellow
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (showExtractedText) stringResource(R.string.hide_verbatim_text)
                                           else stringResource(R.string.view_verbatim_text),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AyneVibrantYellow
                                )
                            }
                        }

                        if (showExtractedText) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AynePureBlack,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 90.dp)
                                    .border(1.dp, AyneVibrantYellow.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = readResp.extractedText,
                                        color = AynePureWhite.copy(alpha = 0.9f),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    // Camera Guidance alert if cut off or blurry
                    val guidanceAlert = when {
                        !readResp.cameraGuidance.isNullOrBlank() -> readResp.cameraGuidance
                        readResp.isCutOff -> stringResource(R.string.text_cut_off_warning)
                        readResp.isBlurry -> stringResource(R.string.text_blurry_warning)
                        else -> null
                    }
                    if (!guidanceAlert.isNullOrBlank()) {
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
                                text = guidanceAlert,
                                color = AyneVibrantYellow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        } else if (!uiState.lastAnswer.isNullOrBlank() && !uiState.isBusy) {
            // General description answer card with dismiss button and bounded height
            val hasTranscript = !uiState.liveTranscription.isNullOrBlank()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .border(1.dp, AyneVibrantYellow.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .semantics {
                        contentDescription = if (hasTranscript) {
                            "You said: ${uiState.liveTranscription}. Answer: ${uiState.lastAnswer}"
                        } else {
                            "Last answer: ${uiState.lastAnswer}"
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
                            text = if (hasTranscript) {
                                stringResource(R.string.question_answer_title)
                            } else {
                                stringResource(R.string.surroundings_title)
                            },
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
                                contentDescription = stringResource(R.string.dismiss_result_desc),
                                tint = AynePureWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    // What the user said (server-side speech-to-text of the mic audio)
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
                                text = stringResource(R.string.you_said_transcript, uiState.liveTranscription ?: ""),
                                color = AynePureWhite.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 110.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = uiState.lastAnswer ?: "",
                            color = AynePureWhite,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (!uiState.lastEnglishAnswer.isNullOrBlank() && uiState.selectedLanguage == SupportedLanguage.AMHARIC) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = uiState.lastEnglishAnswer ?: "",
                            color = AynePureWhite.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Captured Frame Debug Preview & Scene Status Indicator
        val previewBase64 = uiState.lockedImageBase64 ?: uiState.lastCapturedBase64
        val previewBitmap: Bitmap? = remember(previewBase64) {
            previewBase64?.let { b64 ->
                try {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (e: Throwable) {
                    null
                }
            }
        }
        var showFullPreviewDialog by remember { mutableStateOf(false) }

        if (previewBitmap != null && !uiState.isBusy) {
            val isLocked = uiState.lockedImageBase64 != null || uiState.activeSessionId != null
            val hasSession = uiState.activeSessionId != null
            val followUpCount = uiState.conversationHistory.count { it.role == "user" && it.text != "Describe my surroundings" }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .border(1.5.dp, AyneVibrantYellow, RoundedCornerShape(12.dp))
                    .semantics {
                        contentDescription = "Captured camera frame debug preview. ${previewBitmap.width} by ${previewBitmap.height} pixels. Tap to inspect full image."
                    },
                color = AyneDarkCard,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Clickable thumbnail with frame info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable { showFullPreviewDialog = true }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.5.dp, AyneVibrantYellow, RoundedCornerShape(8.dp))
                                .background(AynePureBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.debug_preview_desc),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoCamera,
                                    contentDescription = null,
                                    tint = AyneVibrantYellow,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when {
                                        hasSession && followUpCount > 0 -> "Zero-Upload Session · $followUpCount follow-up${if (followUpCount > 1) "s" else ""}"
                                        hasSession -> "Zero-Upload Session Active · Turn 2 ready"
                                        isLocked && followUpCount > 0 -> stringResource(R.string.scene_locked_badge, followUpCount)
                                        isLocked -> stringResource(R.string.scene_locked_initial)
                                        else -> stringResource(R.string.debug_preview_title)
                                    },
                                    color = AyneVibrantYellow,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    hasSession -> "Cached server-side · 0 image bytes on follow-up"
                                    else -> "${previewBitmap.width}×${previewBitmap.height} · ${stringResource(R.string.debug_preview_tap_to_enlarge)}"
                                },
                                color = AynePureWhite.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (isLocked) {
                        OutlinedButton(
                            onClick = {
                                onAction(AyneAction.ClearSession)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AynePureWhite),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("button_clear_scene")
                        ) {
                            Text(
                                text = stringResource(R.string.clear_scene_button),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AynePureWhite
                            )
                        }
                    }
                }
            }
        }

        // Enlarged full debug preview dialog
        if (showFullPreviewDialog && previewBitmap != null) {
            AlertDialog(
                onDismissRequest = { showFullPreviewDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.debug_dialog_title),
                            color = AyneVibrantYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        IconButton(
                            onClick = { showFullPreviewDialog = false }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.debug_dialog_close),
                                tint = AynePureWhite
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AynePureBlack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp)
                                .border(1.5.dp, AyneVibrantYellow, RoundedCornerShape(10.dp))
                        ) {
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.debug_preview_desc),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Resolution: ${previewBitmap.width} × ${previewBitmap.height} px • Compression: JPEG 65%",
                            color = AynePureWhite.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        val b64Len = previewBase64?.length ?: 0
                        val approxBytes = (b64Len * 3) / 4
                        Text(
                            text = "Payload size: ~${approxBytes / 1024} KB • Model Vision Ready",
                            color = AyneVibrantYellow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showFullPreviewDialog = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AyneVibrantYellow,
                            contentColor = AynePureBlack
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.debug_dialog_close),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                containerColor = AyneDarkCard,
                textContentColor = AynePureWhite
            )
        }

        // Voice Recording Active Banner (mic icon + live VAD state / transcription)
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
                    .testTag("card_recording_voice_active"),
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
                            text = if (uiState.heardSpeech) {
                                stringResource(R.string.recording_voice_heard_speech)
                            } else {
                                stringResource(R.string.recording_voice_prompt)
                            },
                            color = AyneVibrantYellow,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 18.sp
                        )
                        if (!uiState.liveTranscription.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.you_said_transcript, uiState.liveTranscription ?: ""),
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

        // 2. Main Content: Either Companion Mode or Action Cards
        if (uiState.isCompanionMode) {
            AyneCompanionPanel(
                uiState = uiState,
                onAction = onAction,
                onSpeakClick = onAskQuestionClick
            )
        } else {
            // Three Stacked Action Cards
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card 1: Describe Surroundings (POST /api/see-and-describe)
                AyneActionCard(
                    icon = Icons.Filled.PhotoCamera,
                    title = stringResource(R.string.describe_surroundings_title),
                    subtitle = stringResource(R.string.describe_surroundings_amharic),
                    contentDescription = stringResource(R.string.describe_surroundings_desc),
                    testTag = "card_describe_surroundings",
                    onClick = onDescribeClick
                )

                // Card 2: Read Text (POST /api/read-text)
                AyneActionCard(
                    icon = Icons.Filled.Description,
                    title = stringResource(R.string.read_text_title),
                    subtitle = stringResource(R.string.read_text_amharic),
                    contentDescription = stringResource(R.string.read_text_desc),
                    testTag = "card_read_text",
                    onClick = onReadTextClick
                )

                // Card 3: Ask Question (POST /api/ask-question)
                val askSubtitle = when (uiState.selectedLanguage) {
                    SupportedLanguage.ENGLISH -> "Visual Q&A · Tap to ask or press Volume−"
                    SupportedLanguage.AFAN_OROMOO -> "Gaaffii fi qorannoo · Tuqi ykn Volume− dhiibi"
                    SupportedLanguage.AMHARIC -> "የቀጥታ ጥያቄና መልስ · ለመጠየቅ ይጫኑ ወይም Volume−"
                }
                AyneActionCard(
                    icon = Icons.Filled.QuestionAnswer,
                    title = stringResource(R.string.ask_question_title),
                    subtitle = askSubtitle,
                    contentDescription = stringResource(R.string.ask_question_desc),
                    testTag = "card_ask_question",
                    onClick = onAskQuestionClick
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Bottom Controls: Repeat Button & Volume Hint
        AyneBottomControls(
            onRepeatClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onAction(AyneAction.RepeatLastAnswer)
            }
        )
    }
}

// ==========================================
// Ask Question Panel Component (Standalone Q&A)
// ==========================================

@Composable
fun AyneAskQuestionPanel(
    uiState: AyneUiState,
    onAction: (AyneAction) -> Unit,
    onSpeakClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Voice-only full-screen redesign: the old chat-panel UI was replaced by
    // the immersive ripple-orb experience (see ui/VoiceAskScreen.kt).
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

// Backward-compatibility alias for screens importing AyneCompanionPanel
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

// ==========================================
// Top Bar Component
// ==========================================

@Composable
fun AyneTopBar(
    selectedLanguage: SupportedLanguage,
    isFrontCamera: Boolean,
    onLanguageSelected: (SupportedLanguage) -> Unit,
    onFlipCamera: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val logoDesc = stringResource(R.string.logo_desc)
    val settingsDesc = stringResource(R.string.settings_button_desc)
    val cameraSideName = if (isFrontCamera) {
        stringResource(R.string.camera_front)
    } else {
        stringResource(R.string.camera_back)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag("top_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Logo & App Name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics {
                heading()
            }
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AyneVibrantYellow)
                    .semantics { contentDescription = logoDesc },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = null,
                    tint = AyneOnYellow,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = stringResource(R.string.app_name),
                color = AyneVibrantYellow,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
        }

        // Language Selector: አማርኛ | Afaan Oromoo | English
        LanguageSelector(
            selectedLanguage = selectedLanguage,
            onLanguageSelected = onLanguageSelected
        )

        // Right side: Camera Flip & Settings buttons
        Row(verticalAlignment = Alignment.CenterVertically) {
            val flipDesc = stringResource(R.string.flip_camera_desc, cameraSideName)
            IconButton(
                onClick = onFlipCamera,
                modifier = Modifier
                    .size(44.dp)
                    .semantics {
                        contentDescription = flipDesc
                    }
                    .testTag("flip_camera_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Cameraswitch,
                    contentDescription = null,
                    tint = if (isFrontCamera) AynePureWhite else AyneVibrantYellow,
                    modifier = Modifier.size(26.dp)
                )
            }

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(44.dp)
                    .semantics { contentDescription = settingsDesc }
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = AyneVibrantYellow,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
fun LanguageSelector(
    selectedLanguage: SupportedLanguage,
    onLanguageSelected: (SupportedLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    val languages = SupportedLanguage.entries

    Row(
        modifier = modifier
            .testTag("language_selector")
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        languages.forEachIndexed { index, language ->
            val isSelected = language == selectedLanguage
            val textColor = if (isSelected) AyneVibrantYellow else AynePureWhite
            val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium

            Text(
                text = language.displayName,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = fontWeight,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = AyneVibrantYellow),
                        role = Role.RadioButton,
                        onClickLabel = "Switch language to ${language.displayName}"
                    ) {
                        onLanguageSelected(language)
                    }
                    .padding(horizontal = 2.dp, vertical = 6.dp)
                    .semantics {
                        role = Role.RadioButton
                        contentDescription = "${language.displayName}${if (isSelected) ", selected" else ""}"
                    }
            )

            if (index < languages.size - 1) {
                Text(
                    text = " | ",
                    color = AyneVibrantYellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ==========================================
// Action Card Component
// ==========================================

@Composable
fun AyneActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp)
            .border(
                width = 2.dp,
                color = AyneVibrantYellow,
                shape = RoundedCornerShape(16.dp)
            )
            .testTag(testTag)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        shape = RoundedCornerShape(16.dp),
        color = AynePureBlack,
        contentColor = AynePureWhite
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AyneVibrantYellow,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(24.dp))

            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = AynePureWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    lineHeight = 28.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AynePureWhite,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

// ==========================================
// Bottom Controls Component
// ==========================================

@Composable
fun AyneBottomControls(
    onRepeatClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repeatDesc = stringResource(R.string.repeat_last_answer_desc)
    val captureText = stringResource(R.string.volume_hint_capture)
    val followupText = stringResource(R.string.volume_hint_followup)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onRepeatClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .testTag("button_repeat_last_answer")
                .semantics {
                    role = Role.Button
                    contentDescription = repeatDesc
                },
            colors = ButtonDefaults.buttonColors(
                containerColor = AyneVibrantYellow,
                contentColor = AyneOnYellow
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = AyneOnYellow,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.repeat_last_answer),
                    style = MaterialTheme.typography.labelLarge,
                    color = AyneOnYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .semantics {
                    contentDescription = "Hardware volume key shortcut hint: Volume up for capture, Volume down for follow-up"
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Volume ",
                color = AynePureWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "▲",
                color = AyneVibrantYellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = " $captureText · Volume ",
                color = AynePureWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "▼",
                color = AyneVibrantYellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = " $followupText",
                color = AynePureWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ==========================================
// Settings Dialog
// ==========================================

@Composable
fun VisionSettingsDialog(
    currentBaseUrl: String,
    currentEndpoint: String,
    currentReadTextEndpoint: String,
    currentAskQuestionEndpoint: String,
    currentVoice: String,
    onDismiss: () -> Unit,
    onSave: (newBaseUrl: String, newVisionUrl: String, newReadTextUrl: String, newAskQuestionUrl: String, newVoice: String) -> Unit
) {
    var baseUrlText by remember { mutableStateOf(currentBaseUrl) }
    var visionUrlText by remember { mutableStateOf(currentEndpoint) }
    var readTextUrlText by remember { mutableStateOf(currentReadTextEndpoint) }
    var askQuestionUrlText by remember { mutableStateOf(currentAskQuestionEndpoint) }
    var selectedVoice by remember { mutableStateOf(currentVoice) }

    val voices = listOf(
        "am-hamen" to "am-hamen (Addis Native Amharic, Female)",
        "am-simon" to "am-simon (Addis Native Amharic, Male)",
        "am-yohanes-calm" to "am-yohanes-calm (Addis Calm Narrative)",
        "am-tesfa" to "am-tesfa (Addis Energetic Daily)",
        "om-ayyaantuu" to "om-ayyaantuu (Addis Native Oromo, Female)",
        "om-bikila" to "om-bikila (Addis Native Oromo, Male)",
        "om-leensaa" to "om-leensaa (Addis Native Oromo, Narrative)",
        "om-diriba" to "om-diriba (Addis Native Oromo, Clear)",
        "Zephyr" to "Zephyr (Gemini Neural, Fast Clear)",
        "Aoede" to "Aoede (Gemini Neural, Warm Reading)",
        "Puck" to "Puck (Gemini Neural, Upbeat)",
        "Fenrir" to "Fenrir (Gemini Neural, Calm)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = AynePureBlack,
        titleContentColor = AyneVibrantYellow,
        textContentColor = AynePureWhite,
        modifier = Modifier
            .border(2.dp, AyneVibrantYellow, RoundedCornerShape(16.dp))
            .testTag("vision_settings_dialog"),
        title = {
            Text(
                text = stringResource(R.string.settings_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AyneVibrantYellow
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.settings_dialog_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AynePureWhite
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Base URL
                OutlinedTextField(
                    value = baseUrlText,
                    onValueChange = { baseUrlText = it },
                    label = { Text(stringResource(R.string.base_url_label), color = AyneVibrantYellow) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AyneVibrantYellow,
                        unfocusedBorderColor = AynePureWhite,
                        focusedTextColor = AynePureWhite,
                        unfocusedTextColor = AynePureWhite,
                        cursorColor = AyneVibrantYellow
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("base_url_input")
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = {
                        val trimmed = baseUrlText.trim()
                        if (trimmed.isNotBlank()) {
                            visionUrlText = AyneUiState.buildVisionUrl(trimmed)
                            readTextUrlText = AyneUiState.buildReadTextUrl(trimmed)
                            askQuestionUrlText = AyneUiState.buildAskQuestionUrl(trimmed)
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("apply_base_url_button")
                ) {
                    Text(
                        text = stringResource(R.string.apply_base_url_button),
                        color = AyneVibrantYellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // See and Describe URL
                OutlinedTextField(
                    value = visionUrlText,
                    onValueChange = { visionUrlText = it },
                    label = { Text(stringResource(R.string.vision_endpoint_label), color = AyneVibrantYellow) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AyneVibrantYellow,
                        unfocusedBorderColor = AynePureWhite,
                        focusedTextColor = AynePureWhite,
                        unfocusedTextColor = AynePureWhite,
                        cursorColor = AyneVibrantYellow
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_endpoint_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Read Text & Signs URL
                OutlinedTextField(
                    value = readTextUrlText,
                    onValueChange = { readTextUrlText = it },
                    label = { Text(stringResource(R.string.read_text_endpoint_label), color = AyneVibrantYellow) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AyneVibrantYellow,
                        unfocusedBorderColor = AynePureWhite,
                        focusedTextColor = AynePureWhite,
                        unfocusedTextColor = AynePureWhite,
                        cursorColor = AyneVibrantYellow
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("read_text_endpoint_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Ask Question Real-time Stream URL
                OutlinedTextField(
                    value = askQuestionUrlText,
                    onValueChange = { askQuestionUrlText = it },
                    label = { Text(stringResource(R.string.ask_question_endpoint_label), color = AyneVibrantYellow) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AyneVibrantYellow,
                        unfocusedBorderColor = AynePureWhite,
                        focusedTextColor = AynePureWhite,
                        unfocusedTextColor = AynePureWhite,
                        cursorColor = AyneVibrantYellow
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ask_question_endpoint_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.voice_selector_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AyneVibrantYellow
                )

                Spacer(modifier = Modifier.height(6.dp))

                voices.forEach { (voiceId, label) ->
                    val isChosen = voiceId == selectedVoice
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedVoice = voiceId }
                            .padding(vertical = 5.dp)
                    ) {
                        Text(
                            text = if (isChosen) "● " else "○ ",
                            color = if (isChosen) AyneVibrantYellow else AynePureWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = label,
                            color = if (isChosen) AyneVibrantYellow else AynePureWhite,
                            fontSize = 13.sp,
                            fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        baseUrlText = AyneUiState.DEFAULT_BASE_URL
                        visionUrlText = AyneUiState.DEFAULT_ENDPOINT
                        readTextUrlText = AyneUiState.DEFAULT_READ_TEXT_ENDPOINT
                        askQuestionUrlText = AyneUiState.DEFAULT_ASK_QUESTION_ENDPOINT
                        selectedVoice = "am-hamen"
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("reset_default_button")
                ) {
                    Text(
                        text = stringResource(R.string.reset_default),
                        color = AyneVibrantYellow
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(baseUrlText, visionUrlText, readTextUrlText, askQuestionUrlText, selectedVoice) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AyneVibrantYellow,
                    contentColor = AyneOnYellow
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("save_settings_button")
            ) {
                Text(
                    text = stringResource(R.string.save_button),
                    fontWeight = FontWeight.Bold,
                    color = AyneOnYellow
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("cancel_settings_button")
            ) {
                Text(
                    text = stringResource(R.string.cancel_button),
                    color = AynePureWhite
                )
            }
        }
    )
}

// ==========================================
// Previews
// ==========================================

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
fun AyneAppScreenPreview() {
    AyneTheme {
        AyneScreenContent(
            uiState = AyneUiState(
                lastAnswer = "በ12 ሰዓት ከፊት ለፊትዎ ጠረጴዛው ላይ ባለ አረንጓዴ ቀለም የኢትዮጵያ መቶ ብር ኖት አለ።"
            ),
            onAction = {},
            onDescribeClick = {},
            onReadTextClick = {},
            onAskQuestionClick = {},
            onFlipCamera = {},
            onSettingsClick = {}
        )
    }
}
