package com.ayne.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayne.BuildConfig
import com.ayne.R
import com.ayne.data.AddisRealtimeClient
import com.ayne.data.ConversationTurn
import com.ayne.data.ReadTextResponse
import com.ayne.data.VisionApiService
import com.ayne.data.VisionDescribeResponse
import com.ayne.data.cleanLabelPrefix
import com.ayne.data.hasRawJsonArtifacts
import com.ayne.data.endpoints.EndpointPath
import com.ayne.data.endpoints.LanguageRouter
import com.ayne.data.endpoints.Variant
import com.ayne.domain.generateCompanionFallbackAnswer
import com.ayne.util.AudioPlaybackHelper
import com.ayne.util.CameraCaptureHelper
import com.ayne.util.Pcm16Decoder
import com.ayne.util.ProcessingFeedbackHelper
import com.ayne.util.VoiceRecorderHelper
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

enum class SupportedLanguage(val displayName: String, val code: String) {
    AMHARIC("አማርኛ", "am"),
    AFAN_OROMOO("Afaan Oromoo", "om"),
    ENGLISH("English", "en")
}

data class AyneUiState(
    val selectedLanguage: SupportedLanguage = SupportedLanguage.AMHARIC,
    val isFirstLaunch: Boolean = false,
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
    val imageMaxDimension: Int = 800,
    val imageJpegQuality: Int = 72,
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
        fun buildVisionUrl(base: String, language: String? = null): String =
            LanguageRouter.resolve(base, language ?: "en", EndpointPath.SEE_AND_DESCRIBE, Variant.PLAIN)

        fun buildReadTextUrl(base: String, language: String? = null): String =
            LanguageRouter.resolve(base, language ?: "en", EndpointPath.READ_TEXT, Variant.PLAIN)

        fun buildAskQuestionUrl(base: String, language: String? = null): String =
            LanguageRouter.resolve(base, language ?: "en", EndpointPath.ASK_QUESTION, Variant.PLAIN)

        val DEFAULT_BASE_URL: String by lazy {
            BuildConfig.API_BASE_URL.trim().trimEnd('/')
        }

        val DEFAULT_ENDPOINT: String by lazy {
            buildVisionUrl(DEFAULT_BASE_URL)
        }

        val DEFAULT_READ_TEXT_ENDPOINT: String by lazy {
            buildReadTextUrl(DEFAULT_BASE_URL)
        }

        val DEFAULT_ASK_QUESTION_ENDPOINT: String by lazy {
            buildAskQuestionUrl(DEFAULT_BASE_URL)
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
    data class UpdateVoice(val voiceId: String) : AyneAction
    data class UpdateImageCompression(
        val maxDimension: Int,
        val jpegQuality: Int
    ) : AyneAction
    data object VolumeCaptureTriggered : AyneAction
    data object VolumeFollowUpTriggered : AyneAction
}


class AyneViewModel(
    private val visionApiService: VisionApiService = VisionApiService(),
    private val audioPlaybackHelper: AudioPlaybackHelper = AudioPlaybackHelper()
) : ViewModel() {
    private var processingFeedbackHelper: ProcessingFeedbackHelper? = null
    private var voiceRecorderHelper: VoiceRecorderHelper? = null
    private var realtimeClient: AddisRealtimeClient? = null
    private var recordingJob: Job? = null

    /**
     * Hands-free Ask conversation: a pending "listen again" scheduled after an
     * answer finishes playing. Cancelled when the Ask page closes.
     */
    private var conversationLoopJob: Job? = null

    /** Consecutive no-speech cycles inside the Ask conversation. */
    private var silentCycles = 0

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
        // Keep the capture helper's live compression settings in sync with
        // state defaults (user edits flow through UpdateImageCompression).
        CameraCaptureHelper.compressionMaxDimension = _uiState.value.imageMaxDimension
        CameraCaptureHelper.compressionJpegQuality = _uiState.value.imageJpegQuality
    }

    fun initFeedbackHelper(context: Context) {
        if (processingFeedbackHelper == null) {
            processingFeedbackHelper = ProcessingFeedbackHelper(context.applicationContext)
        }
        if (voiceRecorderHelper == null) {
            voiceRecorderHelper = VoiceRecorderHelper(context.applicationContext)
        }
    }

    /**
     * Restores the saved language + voice before the first composition so the
     * launch permission narration (and every screen) starts in the user's
     * language. Called once, synchronously, from MainActivity.onCreate.
     * When nothing is saved yet (fresh install) the default stays Amharic and
     * [AyneUiState.isFirstLaunch] is set so the UI can offer the guided
     * first-launch language picker.
     */
    fun initLanguagePreference(context: Context) {
        val prefs = context.getSharedPreferences(LANGUAGE_PREFS_FILE, Context.MODE_PRIVATE)
        val savedCode = prefs.getString(LANGUAGE_PREFS_KEY_LANGUAGE, null)
        if (savedCode == null) {
            _uiState.update {
                it.copy(
                    isFirstLaunch = true,
                    selectedLanguage = SupportedLanguage.AMHARIC,
                    customVoice = VoiceCatalog.defaultVoiceFor(SupportedLanguage.AMHARIC)
                )
            }
            return
        }
        val language = SupportedLanguage.entries.firstOrNull { it.code == savedCode }
            ?: SupportedLanguage.AMHARIC
        _uiState.update { it.copy(selectedLanguage = language, customVoice = restoreVoice(language, prefs)) }
    }

    /** Keeps the customVoice consistent with its language rules (am-* / om-*, English free). */
    private fun restoreVoice(language: SupportedLanguage, prefs: android.content.SharedPreferences): String {
        val savedVoice = prefs.getString(LANGUAGE_PREFS_KEY_VOICE, null)
        return if (savedVoice != null && VoiceCatalog.belongsTo(savedVoice, language)) {
            savedVoice
        } else {
            VoiceCatalog.defaultVoiceFor(language)
        }
    }

    /** Persists the current language + voice whenever the user changes either. */
    private fun saveLanguagePreference(context: Context) {
        val state = _uiState.value
        context.getSharedPreferences(LANGUAGE_PREFS_FILE, Context.MODE_PRIVATE).edit()
            .putString(LANGUAGE_PREFS_KEY_LANGUAGE, state.selectedLanguage.code)
            .putString(LANGUAGE_PREFS_KEY_VOICE, state.customVoice)
            .putBoolean(LANGUAGE_PREFS_KEY_CHOSEN, true)
            .apply()
    }

    private companion object {
        const val LANGUAGE_PREFS_FILE = "ayne_settings"
        const val LANGUAGE_PREFS_KEY_LANGUAGE = "language"
        const val LANGUAGE_PREFS_KEY_VOICE = "voice"
        const val LANGUAGE_PREFS_KEY_CHOSEN = "language_chosen"

        /** Pause before re-entering listening after a spoken answer. */
        const val RELISTEN_DELAY_MS = 800L

        /** Silent cycles before the Ask conversation pauses itself. */
        const val MAX_SILENT_CYCLES = 2
    }

    private fun mapFailureToUserMessage(context: Context, error: Throwable?): String {
        return when {
            error is java.io.IOException -> context.getString(R.string.error_network)
            error != null && error.message?.contains("HTTP 5") == true -> context.getString(R.string.error_bad_response)
            else -> context.getString(R.string.error_bad_response)
        }
    }

    private fun handleAudioPlaybackError(context: Context, detail: String? = null) {
        val message = context.getString(R.string.error_audio_playback)
        _userMessage.tryEmit(message)
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
        silentCycles = 0
        val greeting = when (_uiState.value.selectedLanguage) {
            SupportedLanguage.ENGLISH -> "Hello! I'm listening — ask me anything."
            SupportedLanguage.AFAN_OROMOO -> "Akkam! Nan dhaggeeffadha — waan fedhe natti himi."
            SupportedLanguage.AMHARIC -> "ሰላም! እያዳመጥኩ ነው — ምንም ይጠይቁኝ።"
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
        silentCycles = 0
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
        conversationLoopJob?.cancel()
        conversationLoopJob = null
        silentCycles = 0
        _uiState.update { it.copy(isCompanionMode = false, isAskQuestionOpen = false, isBusy = false) }
        playTapEarcon()
        val exitMsg = when (_uiState.value.selectedLanguage) {
            SupportedLanguage.ENGLISH -> "Conversation ended"
            SupportedLanguage.AFAN_OROMOO -> "Hir'a haasawumsaa xumure"
            SupportedLanguage.AMHARIC -> "ውይይቱ ተጠናቋል"
        }
        _userMessage.tryEmit(exitMsg)
    }

    /**
     * Hands-free Ask conversation: after an answer finishes playing (or the
     * greeting), re-enter listening so the user can keep talking without any
     * tap or key press. No-ops unless the Ask page is open, and the guard
     * re-checks state after the delay so closing the page always stops the
     * loop.
     */
    private fun scheduleHandsFreeRelisten(context: Context, delayMs: Long = RELISTEN_DELAY_MS) {
        if (!_uiState.value.isAskQuestionOpen) return
        conversationLoopJob?.cancel()
        conversationLoopJob = viewModelScope.launch {
            delay(delayMs)
            val s = _uiState.value
            if (s.isAskQuestionOpen && !s.isRecordingVoice && !s.isBusy && !s.isAssistantSpeaking) {
                startVoiceRecording(context)
            }
        }
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

                    if (listenedMs >= 150L && amplitude >= vadSpeechThreshold) {
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
                    silentCycles = 0
                    stopAndSubmitVoiceRecording(context)
                } else if (_uiState.value.isAskQuestionOpen) {
                    // Hands-free conversation: nudge and re-listen instead of
                    // dead-ending; pause the loop after repeated silence.
                    cancelVoiceRecording()
                    silentCycles++
                    if (silentCycles < MAX_SILENT_CYCLES) {
                        val prompt = when (_uiState.value.selectedLanguage) {
                            SupportedLanguage.ENGLISH -> "I'm listening…"
                            SupportedLanguage.AFAN_OROMOO -> "Nan dhaggeeffadha…"
                            SupportedLanguage.AMHARIC -> "እያዳመጥኩ ነው…"
                        }
                        _userMessage.tryEmit(prompt)
                        scheduleHandsFreeRelisten(context, delayMs = 600)
                    } else {
                        silentCycles = 0
                        val pause = when (_uiState.value.selectedLanguage) {
                            SupportedLanguage.ENGLISH -> "Pausing the conversation — tap the circle when you're ready."
                            SupportedLanguage.AFAN_OROMOO -> "Hir'a baafe — yeroo qopheessu goobee tuqi."
                            SupportedLanguage.AMHARIC -> "ውይይቱ ለጊዜ ቆሟል — ሲዘጋጁ ክቡኑን ይንኩ።"
                        }
                        _userMessage.tryEmit(pause)
                    }
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

        // This scene already has a spoken description → do NOT describe it
        // again. Re-describing on every silent Volume− press made the
        // assistant repeat the same description over and over. Ask the user
        // to actually speak instead.
        if (_uiState.value.conversationHistory.isNotEmpty()) {
            val prompt = when (_uiState.value.selectedLanguage) {
                SupportedLanguage.ENGLISH -> "I didn't hear your question — press Volume− and speak after the tone."
                SupportedLanguage.AFAN_OROMOO -> "Gaaffii kee hin dhageenye — Volume gadi dhiibi erga sagalee booda dubbachiisi."
                SupportedLanguage.AMHARIC -> "ጥያቄዎን አልሰማሁም — ድምፁን ከሰሙ በኋላ በድምፅ መቀነሻው ተጭነው ይናገሩ።"
            }
            _userMessage.tryEmit(prompt)
            return
        }

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

        // Show what the server heard as soon as the user transcript arrives —
        // otherwise Ask mode gives zero feedback about the recognized speech.
        client.onUserTranscript = { text, noSpeechDetected ->
            if (!noSpeechDetected && text.isNotBlank()) {
                _uiState.update { it.copy(liveTranscription = text) }
            }
        }

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
                        if (!assistantText.isNullOrBlank()) add(ConversationTurn("model", assistantText))
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
                        audioPlaybackHelper.playBase64Audio(
                            context,
                            mp3Base64,
                            onComplete = { scheduleHandsFreeRelisten(context) }
                        )
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
                    SupportedLanguage.ENGLISH -> {
                        if (!VoiceCatalog.belongsTo(_uiState.value.customVoice, SupportedLanguage.ENGLISH)) {
                            VoiceCatalog.defaultVoiceFor(SupportedLanguage.ENGLISH)
                        } else {
                            _uiState.value.customVoice
                        }
                    }
                }
                _uiState.update { it.copy(selectedLanguage = action.language, customVoice = newVoice) }
                saveLanguagePreference(context)
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
            is AyneAction.UpdateVoice -> {
                _uiState.update { it.copy(customVoice = action.voiceId) }
                saveLanguagePreference(context)
                _userMessage.tryEmit("Voice set to ${action.voiceId}")
            }
            is AyneAction.UpdateImageCompression -> {
                val maxDim = action.maxDimension.coerceIn(320, 1600)
                val quality = action.jpegQuality.coerceIn(30, 95)
                CameraCaptureHelper.compressionMaxDimension = maxDim
                CameraCaptureHelper.compressionJpegQuality = quality
                _uiState.update {
                    it.copy(imageMaxDimension = maxDim, imageJpegQuality = quality)
                }
                _userMessage.tryEmit("Image quality: ${maxDim}px max, $quality% JPEG")
            }
            is AyneAction.VolumeCaptureTriggered -> {
                _userMessage.tryEmit("Volume ▲: Capturing fresh surroundings...")
            }
            is AyneAction.VolumeFollowUpTriggered -> {
                if (_uiState.value.isRecordingVoice) {
                    // While already recording, Volume− submits the take.
                    stopAndSubmitVoiceRecording(context)
                } else if (_uiState.value.isAskQuestionOpen) {
                    // Inside the ask tab, not yet recording → start recording.
                    startVoiceRecording(context)
                } else if (_uiState.value.lockedImageBase64 == null &&
                    _uiState.value.activeSessionId == null && activeSessionId == null
                ) {
                    // Nothing described/captured yet — nothing to follow up on.
                    _userMessage.tryEmit(context.getString(R.string.no_scene_in_memory))
                } else {
                    // Home / vision screen: record a follow-up question about
                    // the current scene + describe history (original Volume−
                    // job). The old streaming-into-mic bug is fixed upstream
                    // (cancelActiveApiJob), so describing cannot pollute the mic.
                    startVoiceRecording(context)
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

        // Reset history, activeSessionId, lock image, and clear previous optical readings.
        // The Ask session id/history MUST die here too: a fresh capture is a new
        // scene, and a surviving askQuestionSessionId makes executeFollowUpRequest
        // send sessionId without an image — the server then answers from ITS
        // cached photo of the OLD Ask scene (the "recognition answers from a
        // stale cache" bug), until the session finally expires with a nag.
        activeSessionId = null
        _uiState.update {
            it.copy(
                activeSessionId = null,
                askQuestionSessionId = null,
                askQuestionHistory = emptyList(),
                askQuestionLastAnswer = null,
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
            val endpoint = LanguageRouter.resolve(
                baseUrl = state.baseUrl,
                language = prefLang,
                path = EndpointPath.SEE_AND_DESCRIBE,
                variant = Variant.PLAIN
            )
            val voice = if (state.selectedLanguage == SupportedLanguage.AFAN_OROMOO && !state.customVoice.startsWith("om-")) {
                "om-ayyaantuu"
            } else if (state.selectedLanguage == SupportedLanguage.AMHARIC && state.customVoice.startsWith("om-")) {
                "am-hamen"
            } else {
                state.customVoice
            }
            var receivedVision = false
            var spokenThisTurn: String? = null
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
                    spokenThisTurn = chosenAnswer

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
                                handleAudioPlaybackError(context, err)
                            }
                        )
                    }
                    // else: defense in depth — an interrupted stream (or one racing
                    // a just-started recording) must never feed the speaker,
                    // otherwise the mic hears the old answer instead of the user.
                },
                onDone = {
                    audioPlaybackHelper.finishStream(context)
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
                    } else if (receivedVision && !spokenThisTurn.isNullOrBlank()) {
                        // Stream gave text but no audio → synthesize THIS turn's answer.
                        synthesizeAndPlayTtsFallback(context, spokenThisTurn!!, _uiState.value)
                    } else if (!receivedVision) {
                        // Nothing arrived this turn: never fall back to speaking a
                        // PREVIOUS answer — that made the assistant repeat itself.
                        _userMessage.tryEmit(context.getString(R.string.snackbar_no_answer))
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
                                handleAudioPlaybackError(context, err)
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
                    val errMsg = mapFailureToUserMessage(context, error)
                    _userMessage.tryEmit(errMsg)
                }
            } else if (streamResult.isFailure && receivedVision) {
                processingFeedbackHelper?.stopWorkingFeedback()
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        busyMessage = null
                    )
                }
                if (audioChunks.isEmpty() && !spokenThisTurn.isNullOrBlank()) {
                    synthesizeAndPlayTtsFallback(context, spokenThisTurn!!, _uiState.value)
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
            val askEndpoint = LanguageRouter.resolve(
                baseUrl = state.baseUrl,
                language = prefLang,
                path = EndpointPath.ASK_QUESTION,
                variant = Variant.PLAIN
            )
            val voice = if (state.selectedLanguage == SupportedLanguage.AFAN_OROMOO && !state.customVoice.startsWith("om-")) {
                "om-ayyaantuu"
            } else if (state.selectedLanguage == SupportedLanguage.AMHARIC && state.customVoice.startsWith("om-")) {
                "am-hamen"
            } else {
                state.customVoice
            }
            val sendSessionId = sid

            var receivedAnswer = false
            var spokenThisTurn: String? = null
            var currentTranscribedQuery: String? = textQuery
            var sessionExpired = false
            val audioChunks = StringBuilder()

            // 1. Primary: Dedicated Real-Time SSE Stream (POST /api/ask-question/{lang}/stream).
            // A local function so an EXPIRED visual session can be retried once
            // with the locally stored photo — the server forgets its session
            // photo after a short TTL, which is what produced the endless
            // "Visual session expired / photo expired" nag.
            suspend fun runAskStream(sessionId: String?, imageBase64: String?): Result<Unit> =
                visionApiService.askQuestionStream(
                baseEndpointUrl = askEndpoint,
                sessionId = sessionId,
                audioBase64 = voiceBase64,
                query = textQuery,
                imageBase64 = imageBase64,
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
                    // Persist only a FRESH session id — falling back to the
                    // (possibly expired) sendSessionId here is what kept
                    // re-seeding the dead session.
                    val newSid = ansEv.sessionId?.takeIf { it.isNotBlank() }
                    val chosenAnswer = ansEv.cleanSpeech.ifBlank {
                        when (state.selectedLanguage) {
                            SupportedLanguage.ENGLISH -> ansEv.english.ifBlank { ansEv.amharic }
                            SupportedLanguage.AFAN_OROMOO -> ansEv.oromo.ifBlank { ansEv.english }
                            SupportedLanguage.AMHARIC -> ansEv.amharic.ifBlank { ansEv.english }
                        }
                    }.cleanLabelPrefix(prefLang)
                    spokenThisTurn = chosenAnswer

                    val userTurn = currentTranscribedQuery?.takeIf { it.isNotBlank() } ?: textQuery ?: "Question"
                    val updatedAskHistory = (state.askQuestionHistory + listOf(
                        ConversationTurn("user", userTurn),
                        ConversationTurn("model", chosenAnswer)
                    )).takeLast(20)

                    _uiState.update {
                        it.copy(
                            askQuestionSessionId = newSid ?: it.askQuestionSessionId,
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
                                handleAudioPlaybackError(context, err)
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
                        // Stale visual session: purge EVERY stale id (the old
                        // code kept activeSessionId alive and re-sent it, so
                        // the error repeated forever) and let the post-stream
                        // self-heal below retry from the local photo.
                        sessionExpired = true
                        activeSessionId = null
                        _uiState.update {
                            it.copy(
                                askQuestionSessionId = null,
                                activeSessionId = null,
                                isBusy = false,
                                busyMessage = null
                            )
                        }
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
                        // Re-enter listening so one unheard take doesn't dead-end
                        // the hands-free conversation loop.
                        if (_uiState.value.isAskQuestionOpen) {
                            scheduleHandsFreeRelisten(context)
                        }
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
                    // An expired-session pass ends silently here; the self-heal
                    // retry below owns the outcome instead.
                    if (!(sessionExpired && !receivedAnswer)) {
                        audioPlaybackHelper.finishStream(context)
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
                        } else if (receivedAnswer && !spokenThisTurn.isNullOrBlank()) {
                            synthesizeAndPlayTtsFallback(context, spokenThisTurn!!, _uiState.value)
                        } else if (!receivedAnswer) {
                            // No answer for THIS question — never speak the previous
                            // turn's answer again (the "AI repeats itself" bug).
                            _userMessage.tryEmit(context.getString(R.string.snackbar_no_answer))
                        }
                    }
                }
            )

            val streamResult = runAskStream(
                sessionId = sendSessionId,
                imageBase64 = if (sendSessionId == null) lockedImage else null
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
                            onComplete = { scheduleHandsFreeRelisten(context) },
                            onError = { err ->
                                handleAudioPlaybackError(context, err)
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
                processingFeedbackHelper?.stopWorkingFeedback()
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        busyMessage = null
                    )
                }
                if (audioChunks.isEmpty() && !spokenThisTurn.isNullOrBlank()) {
                    synthesizeAndPlayTtsFallback(context, spokenThisTurn!!, _uiState.value)
                }
            }

            // 3. Self-heal an expired visual session: the question never got
            // answered, we still hold the photo locally, and every stale
            // session id is purged — so answer from OUR photo with a fresh
            // session instead of telling the user "photo expired".
            if (sessionExpired && !receivedAnswer) {
                if (!lockedImage.isNullOrBlank()) {
                    audioChunks.clear()
                    _uiState.update { it.copy(isBusy = true, busyMessage = progressMsg) }
                    processingFeedbackHelper?.startWorkingFeedback(viewModelScope)
                    runAskStream(sessionId = null, imageBase64 = lockedImage)
                } else {
                    // Nothing to recover with — a new photo is genuinely needed.
                    val prompt = if (state.selectedLanguage == SupportedLanguage.AFAN_OROMOO) {
                        context.getString(R.string.session_expired_prompt_om)
                    } else {
                        context.getString(R.string.session_expired_prompt)
                    }
                    _userMessage.tryEmit(prompt)
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
            val endpoint = LanguageRouter.resolve(
                baseUrl = state.baseUrl,
                language = prefLang,
                path = EndpointPath.READ_TEXT,
                variant = Variant.PLAIN
            )
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
                                handleAudioPlaybackError(context, err)
                            }
                        )
                    }
                    // else: never feed the speaker while the user is recording.
                },
                onDone = {
                    audioPlaybackHelper.finishStream(context)
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
                                handleAudioPlaybackError(context, err)
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
                    val errMsg = mapFailureToUserMessage(context, error)
                    _userMessage.tryEmit(errMsg)
                }
            } else if (streamResult.isFailure && receivedOcr) {
                processingFeedbackHelper?.stopWorkingFeedback()
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        busyMessage = null
                    )
                }
                if (audioChunks.isEmpty()) {
                    val answerToSpeak = _uiState.value.lastAnswer
                    if (!answerToSpeak.isNullOrBlank()) {
                        synthesizeAndPlayTtsFallback(context, answerToSpeak, _uiState.value)
                    }
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
                    onComplete = { scheduleHandsFreeRelisten(context) },
                    onError = { err ->
                        handleAudioPlaybackError(context, err)
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
