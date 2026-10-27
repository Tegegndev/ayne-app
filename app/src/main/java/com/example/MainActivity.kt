package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import com.example.data.ConversationTurn
import com.example.data.ReadTextResponse
import com.example.data.VisionApiService
import com.example.data.VisionDescribeResponse
import com.example.ui.theme.AyneDarkCard
import com.example.ui.theme.AyneOnYellow
import com.example.ui.theme.AynePureBlack
import com.example.ui.theme.AynePureWhite
import com.example.ui.theme.AyneTheme
import com.example.ui.theme.AyneVibrantYellow
import com.example.util.AudioPlaybackHelper
import com.example.util.CameraCaptureHelper
import com.example.util.ProcessingFeedbackHelper
import com.example.util.VoiceRecorderHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val isBusy: Boolean = false,
    val busyMessage: String? = null,
    val isFrontCamera: Boolean = false,
    val apiEndpoint: String = DEFAULT_ENDPOINT,
    val readTextEndpoint: String = DEFAULT_READ_TEXT_ENDPOINT,
    val customVoice: String = "am-hamen",
    val lastResponse: VisionDescribeResponse? = null,
    val lastReadResponse: ReadTextResponse? = null,
    val lockedImageBase64: String? = null,
    val conversationHistory: List<ConversationTurn> = emptyList(),
    val isRecordingVoice: Boolean = false
) {
    companion object {
        val DEFAULT_ENDPOINT: String by lazy {
            try {
                BuildConfig.VISION_API_URL.takeIf { it.isNotBlank() }
                    ?: "https://foul-crematorium-pjp6vpw46q65h665r-3000.app.github.dev/api/see-and-describe"
            } catch (e: Throwable) {
                "https://foul-crematorium-pjp6vpw46q65h665r-3000.app.github.dev/api/see-and-describe"
            }
        }

        val DEFAULT_READ_TEXT_ENDPOINT: String by lazy {
            try {
                val vision = BuildConfig.VISION_API_URL
                if (vision.contains("see-and-describe")) {
                    vision.replace("see-and-describe", "read-text")
                } else if (vision.isNotBlank()) {
                    "$vision/api/read-text"
                } else {
                    "https://foul-crematorium-pjp6vpw46q65h665r-3000.app.github.dev/api/read-text"
                }
            } catch (e: Throwable) {
                "https://foul-crematorium-pjp6vpw46q65h665r-3000.app.github.dev/api/read-text"
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
    data object AskQuestion : AyneAction
    data object RepeatLastAnswer : AyneAction
    data class SelectLanguage(val language: SupportedLanguage) : AyneAction
    data object FlipCamera : AyneAction
    data class UpdateEndpoints(val visionUrl: String, val readTextUrl: String) : AyneAction
    data class UpdateVoice(val voiceId: String) : AyneAction
    data object VolumeCaptureTriggered : AyneAction
    data object VolumeFollowUpTriggered : AyneAction
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
    private var recordingJob: Job? = null

    private val _uiState = MutableStateFlow(AyneUiState())
    val uiState: StateFlow<AyneUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

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
        if (_uiState.value.isRecordingVoice) {
            cancelVoiceRecording()
        }
    }

    fun playTapEarcon() {
        processingFeedbackHelper?.playInstantTapEarcon()
    }

    fun startVoiceRecording(context: Context) {
        val state = _uiState.value
        if (state.lockedImageBase64 == null) {
            val noSceneMsg = context.getString(R.string.no_scene_in_memory)
            _userMessage.tryEmit(noSceneMsg)
            playTapEarcon()
            return
        }

        bargeIn()
        playTapEarcon()

        if (voiceRecorderHelper == null) {
            voiceRecorderHelper = VoiceRecorderHelper(context.applicationContext)
        }

        val started = voiceRecorderHelper?.startRecording() == true
        if (started) {
            _uiState.update { it.copy(isRecordingVoice = true) }
            _userMessage.tryEmit(context.getString(R.string.recording_voice_prompt))

            recordingJob?.cancel()
            recordingJob = viewModelScope.launch {
                delay(8000) // auto-stop after 8 seconds if user doesn't tap again
                if (_uiState.value.isRecordingVoice) {
                    stopAndSubmitVoiceRecording(context)
                }
            }
        } else {
            // Simulator or recording init fallback
            executeFollowUpRequest(
                context = context,
                voiceBase64 = null,
                textQuery = "ስኒዎቹ ምን ቀለም ናቸው?"
            )
        }
    }

    fun stopAndSubmitVoiceRecording(context: Context) {
        recordingJob?.cancel()
        recordingJob = null

        if (!_uiState.value.isRecordingVoice) return

        playTapEarcon()
        _uiState.update { it.copy(isRecordingVoice = false) }

        val voiceBase64 = voiceRecorderHelper?.stopRecording()
        executeFollowUpRequest(
            context = context,
            voiceBase64 = voiceBase64,
            textQuery = if (voiceBase64.isNullOrBlank()) "ስኒዎቹ ምን ቀለም ናቸው?" else null
        )
    }

    fun cancelVoiceRecording() {
        recordingJob?.cancel()
        recordingJob = null
        voiceRecorderHelper?.cancelRecording()
        _uiState.update { it.copy(isRecordingVoice = false) }
    }

    fun onAction(context: Context, action: AyneAction) {
        bargeIn()
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
            is AyneAction.ClearSession -> {
                _uiState.update {
                    it.copy(
                        lockedImageBase64 = null,
                        conversationHistory = emptyList(),
                        isRecordingVoice = false
                    )
                }
                _userMessage.tryEmit("Scene cleared. Ready for fresh capture.")
            }
            is AyneAction.AskQuestion -> {
                if (_uiState.value.isRecordingVoice) {
                    stopAndSubmitVoiceRecording(context)
                } else if (_uiState.value.lockedImageBase64 == null) {
                    _userMessage.tryEmit(context.getString(R.string.no_scene_in_memory))
                } else {
                    startVoiceRecording(context)
                }
            }
            is AyneAction.RepeatLastAnswer -> {
                repeatLastAnswer(context)
            }
            is AyneAction.SelectLanguage -> {
                _uiState.update { it.copy(selectedLanguage = action.language) }
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
                val cleanedVision = action.visionUrl.trim()
                val cleanedRead = action.readTextUrl.trim()
                _uiState.update {
                    it.copy(
                        apiEndpoint = cleanedVision.ifBlank { it.apiEndpoint },
                        readTextEndpoint = cleanedRead.ifBlank { it.readTextEndpoint }
                    )
                }
                _userMessage.tryEmit("API Endpoints updated")
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
                    stopAndSubmitVoiceRecording(context)
                } else if (_uiState.value.lockedImageBase64 == null) {
                    _userMessage.tryEmit(context.getString(R.string.no_scene_in_memory))
                } else {
                    startVoiceRecording(context)
                }
            }
        }
    }

    /**
     * Volume+ / Fresh capture:
     * 1. Snaps camera frame.
     * 2. Resets conversation history (history.clear()).
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

        // Reset history and lock image
        _uiState.update {
            it.copy(
                isBusy = true,
                busyMessage = progressMsg,
                lockedImageBase64 = frameBase64,
                conversationHistory = emptyList(),
                isRecordingVoice = false
            )
        }
        _userMessage.tryEmit(progressMsg)
        processingFeedbackHelper?.startWorkingFeedback(viewModelScope)

        viewModelScope.launch {
            val endpoint = state.apiEndpoint
            val voice = state.customVoice
            var receivedVision = false
            val audioChunks = StringBuilder()

            // 1. Attempt ultra-low latency SSE streaming (~500ms vision arrival)
            val streamResult = visionApiService.seeAndDescribeStream(
                baseEndpointUrl = endpoint,
                imageBase64 = frameBase64,
                audioBase64 = null,
                query = null,
                history = emptyList(),
                customVoice = voice,
                mode = "general",
                onVision = { visionEvent ->
                    receivedVision = true
                    processingFeedbackHelper?.playSuccessTone()
                    val chosenAnswer = if (state.selectedLanguage == SupportedLanguage.ENGLISH && visionEvent.english.isNotBlank()) {
                        visionEvent.english
                    } else {
                        visionEvent.amharic.ifBlank { visionEvent.english }
                    }

                    val initialHistory = listOf(
                        ConversationTurn("user", "Describe my surroundings"),
                        ConversationTurn("model", chosenAnswer)
                    )

                    _uiState.update {
                        it.copy(
                            lastAnswer = chosenAnswer,
                            lastEnglishAnswer = visionEvent.english,
                            conversationHistory = initialHistory,
                            lockedImageBase64 = frameBase64,
                            busyMessage = null
                        )
                    }
                    _userMessage.tryEmit(chosenAnswer)
                },
                onAudioChunk = { chunkEvent ->
                    audioChunks.append(chunkEvent.chunkBase64)
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
                        audioPlaybackHelper.playBase64Audio(
                            context = context,
                            rawBase64Audio = fullAudio,
                            onError = { err ->
                                _userMessage.tryEmit("Audio error: $err")
                            }
                        )
                    }
                }
            )

            // 2. If streaming failed, fallback to single-pass seeAndDescribe
            if (streamResult.isFailure && !receivedVision) {
                val singleResult = visionApiService.seeAndDescribe(
                    endpointUrl = endpoint,
                    imageBase64 = frameBase64,
                    audioBase64 = null,
                    query = null,
                    history = emptyList(),
                    customVoice = voice,
                    mode = "general",
                    includeAudio = true
                )

                singleResult.onSuccess { response ->
                    processingFeedbackHelper?.playSuccessTone()
                    val chosenAnswer = if (state.selectedLanguage == SupportedLanguage.ENGLISH && response.english.isNotBlank()) {
                        response.english
                    } else {
                        response.amharic.ifBlank { response.english }
                    }

                    val initialHistory = listOf(
                        ConversationTurn("user", "Describe my surroundings"),
                        ConversationTurn("model", chosenAnswer)
                    )

                    _uiState.update {
                        it.copy(
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
                    if (!rawAudio.isNullOrBlank()) {
                        audioPlaybackHelper.playBase64Audio(
                            context = context,
                            rawBase64Audio = rawAudio,
                            onError = { err ->
                                _userMessage.tryEmit("Audio error: $err")
                            }
                        )
                    } else {
                        _userMessage.tryEmit(chosenAnswer)
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
     * 1. Does NOT take a new picture! Reuses lockedImageBase64.
     * 2. Sends user voice/query + rolling conversationHistory to the backend.
     * 3. Speaks answer aloud and appends turn to history.
     */
    fun executeFollowUpRequest(
        context: Context,
        voiceBase64: String?,
        textQuery: String?
    ) {
        val state = _uiState.value
        val lockedImage = state.lockedImageBase64
        if (lockedImage == null) {
            val noSceneMsg = context.getString(R.string.no_scene_in_memory)
            _userMessage.tryEmit(noSceneMsg)
            return
        }

        if (state.isBusy) return

        val progressMsg = context.getString(R.string.analyzing_followup)
        _uiState.update {
            it.copy(
                isBusy = true,
                busyMessage = progressMsg
            )
        }
        _userMessage.tryEmit(progressMsg)
        processingFeedbackHelper?.startWorkingFeedback(viewModelScope)

        viewModelScope.launch {
            val endpoint = state.apiEndpoint
            val voice = state.customVoice
            var receivedVision = false
            val audioChunks = StringBuilder()

            // 1. Attempt streaming SSE follow-up
            val streamResult = visionApiService.seeAndDescribeStream(
                baseEndpointUrl = endpoint,
                imageBase64 = lockedImage, // REUSES locked image!
                audioBase64 = voiceBase64,
                query = textQuery,
                history = state.conversationHistory, // PASSES conversation history!
                customVoice = voice,
                mode = "general",
                onVision = { visionEvent ->
                    receivedVision = true
                    processingFeedbackHelper?.playSuccessTone()
                    val chosenAnswer = if (state.selectedLanguage == SupportedLanguage.ENGLISH && visionEvent.english.isNotBlank()) {
                        visionEvent.english
                    } else {
                        visionEvent.amharic.ifBlank { visionEvent.english }
                    }

                    val prompt = textQuery ?: "Voice question"
                    val updatedHistory = state.conversationHistory + listOf(
                        ConversationTurn("user", prompt),
                        ConversationTurn("model", chosenAnswer)
                    )

                    _uiState.update {
                        it.copy(
                            lastAnswer = chosenAnswer,
                            lastEnglishAnswer = visionEvent.english,
                            conversationHistory = updatedHistory,
                            busyMessage = null
                        )
                    }
                    _userMessage.tryEmit(chosenAnswer)
                },
                onAudioChunk = { chunkEvent ->
                    audioChunks.append(chunkEvent.chunkBase64)
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
                        audioPlaybackHelper.playBase64Audio(
                            context = context,
                            rawBase64Audio = fullAudio,
                            onError = { err ->
                                _userMessage.tryEmit("Audio error: $err")
                            }
                        )
                    }
                }
            )

            // 2. Single-pass fallback if streaming fails
            if (streamResult.isFailure && !receivedVision) {
                val singleResult = visionApiService.seeAndDescribe(
                    endpointUrl = endpoint,
                    imageBase64 = lockedImage, // REUSES locked image!
                    audioBase64 = voiceBase64,
                    query = textQuery,
                    history = state.conversationHistory, // PASSES conversation history!
                    customVoice = voice,
                    mode = "general",
                    includeAudio = true
                )

                singleResult.onSuccess { response ->
                    processingFeedbackHelper?.playSuccessTone()
                    val chosenAnswer = if (state.selectedLanguage == SupportedLanguage.ENGLISH && response.english.isNotBlank()) {
                        response.english
                    } else {
                        response.amharic.ifBlank { response.english }
                    }

                    val userTurn = response.transcribedQuery?.takeIf { it.isNotBlank() } ?: textQuery ?: "Voice question"
                    val updatedHistory = state.conversationHistory + listOf(
                        ConversationTurn("user", userTurn),
                        ConversationTurn("model", chosenAnswer)
                    )

                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            busyMessage = null,
                            lastAnswer = chosenAnswer,
                            lastEnglishAnswer = response.english,
                            lastAudioBase64 = response.audio?.audioBase64,
                            lastResponse = response,
                            lastReadResponse = null,
                            conversationHistory = updatedHistory
                        )
                    }

                    val rawAudio = response.audio?.audioBase64
                    if (!rawAudio.isNullOrBlank()) {
                        audioPlaybackHelper.playBase64Audio(
                            context = context,
                            rawBase64Audio = rawAudio,
                            onError = { err ->
                                _userMessage.tryEmit("Audio error: $err")
                            }
                        )
                    } else {
                        _userMessage.tryEmit(chosenAnswer)
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
                busyMessage = progressMsg
            )
        }
        _userMessage.tryEmit(progressMsg)
        processingFeedbackHelper?.startWorkingFeedback(viewModelScope)

        viewModelScope.launch {
            val endpoint = state.readTextEndpoint
            val voice = state.customVoice
            val prefLang = if (state.selectedLanguage == SupportedLanguage.ENGLISH) "en" else "am"

            val result = visionApiService.readText(
                endpointUrl = endpoint,
                imageBase64 = frameBase64,
                query = null,
                audioBase64 = null,
                customVoice = voice,
                ttsProvider = if (prefLang == "en") "gemini" else "addis",
                preferredLanguage = prefLang,
                includeAudio = true
            )

            result.onSuccess { response ->
                processingFeedbackHelper?.playSuccessTone()
                val chosenAnswer = if (prefLang == "en" && response.english.isNotBlank()) {
                    response.english
                } else {
                    response.amharic.ifBlank { response.english }
                }

                val guidanceMsg = when {
                    !response.cameraGuidance.isNullOrBlank() -> response.cameraGuidance
                    response.isBlurry == true -> "ምስሉ ደብዛዛ ነው፤ እባክዎ ካሜራውን ሳያንቀሳቅሱ ያቆዩት"
                    response.isLegible == false -> "ጽሑፉ በግልጽ አይታይም፤ እባክዎ ካሜራውን ወደ ጽሑፉ ያቅርቡ"
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
                if (!rawAudio.isNullOrBlank()) {
                    audioPlaybackHelper.playBase64Audio(
                        context = context,
                        rawBase64Audio = rawAudio,
                        onError = { err ->
                            _userMessage.tryEmit("Audio error: $err")
                        }
                    )
                } else {
                    _userMessage.tryEmit(chosenAnswer)
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyDown(keyCode, event)

        // Stop any currently playing speech immediately on ANY volume key (Barge-In)
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            viewModel.bargeIn()
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
                    onVolumeFollowUpTriggered?.invoke()
                        ?: viewModel.onAction(this, AyneAction.VolumeFollowUpTriggered)
                    return true
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
        viewModel.bargeIn()
        if (uiState.isRecordingVoice) {
            viewModel.stopAndSubmitVoiceRecording(context)
        } else {
            if (uiState.lockedImageBase64 == null) {
                viewModel.showMessage(context.getString(R.string.no_scene_in_memory))
                viewModel.playTapEarcon()
            } else {
                requireMicPermission {
                    viewModel.startVoiceRecording(context)
                }
            }
        }
    }

    LaunchedEffect(onRegisterVolumeHandlers, uiState.isRecordingVoice, uiState.lockedImageBase64) {
        onRegisterVolumeHandlers?.invoke(
            { triggerDescribeSurroundings() },
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

        if (showSettingsDialog) {
            VisionSettingsDialog(
                currentEndpoint = uiState.apiEndpoint,
                currentReadTextEndpoint = uiState.readTextEndpoint,
                currentVoice = uiState.customVoice,
                onDismiss = { showSettingsDialog = false },
                onSave = { newVisionUrl, newReadUrl, newVoice ->
                    showSettingsDialog = false
                    viewModel.onAction(context, AyneAction.UpdateEndpoints(newVisionUrl, newReadUrl))
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
        if (readResp != null && !uiState.isBusy) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .border(2.dp, AyneVibrantYellow, RoundedCornerShape(14.dp))
                    .semantics {
                        contentDescription = "Read Text Result. ${readResp.amharic.ifBlank { readResp.english }}"
                    },
                color = AyneDarkCard,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Sign / Document Classification Badge
                    if (!readResp.signType.isNullOrBlank() || !readResp.documentType.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = AyneVibrantYellow,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = readResp.signType ?: readResp.documentType ?: "",
                                color = AyneVibrantYellow,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Spatial Location Context
                    if (!readResp.locationContext.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = AynePureWhite,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = readResp.locationContext,
                                color = AynePureWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Spoken Reading Text
                    Text(
                        text = uiState.lastAnswer ?: readResp.amharic,
                        color = AynePureWhite,
                        fontSize = 17.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Extracted Verbatim Text (if different)
                    if (!readResp.extractedText.isNullOrBlank() && readResp.extractedText != readResp.amharic) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AynePureBlack,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = stringResource(R.string.extracted_text_label),
                                    color = AyneVibrantYellow,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = readResp.extractedText,
                                    color = AynePureWhite.copy(alpha = 0.9f),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }

                    // Camera Guidance alert if cut off or blurry
                    if (!readResp.cameraGuidance.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AyneVibrantYellow.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = AyneVibrantYellow,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = readResp.cameraGuidance,
                                color = AyneVibrantYellow,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        } else if (!uiState.lastAnswer.isNullOrBlank() && !uiState.isBusy) {
            // General description answer card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .border(1.dp, AyneVibrantYellow.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .semantics {
                        contentDescription = "Last answer: ${uiState.lastAnswer}"
                    },
                color = AyneDarkCard,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = uiState.lastAnswer ?: "",
                        color = AynePureWhite,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (!uiState.lastEnglishAnswer.isNullOrBlank() && uiState.selectedLanguage == SupportedLanguage.AMHARIC) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = uiState.lastEnglishAnswer ?: "",
                            color = AynePureWhite.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // Scene Locked Status Indicator
        if (uiState.lockedImageBase64 != null && !uiState.isBusy) {
            val followUpCount = uiState.conversationHistory.count { it.role == "user" && it.text != "Describe my surroundings" }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .border(1.5.dp, AyneVibrantYellow, RoundedCornerShape(12.dp))
                    .semantics {
                        contentDescription = "Active scene locked in memory. $followUpCount follow-up questions asked."
                    },
                color = AyneDarkCard,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            tint = AyneVibrantYellow,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (followUpCount > 0) stringResource(R.string.scene_locked_badge, followUpCount)
                                   else stringResource(R.string.scene_locked_initial),
                            color = AyneVibrantYellow,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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

        // Voice Recording Active Banner
        if (uiState.isRecordingVoice) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clickable { onAskQuestionClick() }
                    .border(2.dp, AyneVibrantYellow, RoundedCornerShape(12.dp))
                    .semantics {
                        role = Role.Button
                        contentDescription = "Microphone recording active. Tap to finish recording and ask follow-up question."
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
                            .background(AyneVibrantYellow),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = null,
                            tint = AyneOnYellow,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.recording_voice_prompt),
                        color = AyneVibrantYellow,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // 2. Three Stacked Action Cards
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

            // Card 3: Ask Question (voice mode / visual follow-up)
            val askSubtitle = when {
                uiState.isRecordingVoice -> "Recording… Tap to submit · ለመጨረስ ይጫኑ"
                uiState.lockedImageBase64 != null -> "Ask follow-up about scene · በተያዘው ምስል ላይ ጠይቅ"
                else -> stringResource(R.string.ask_question_amharic) + " (Take photo first)"
            }
            AyneActionCard(
                icon = Icons.Filled.Mic,
                title = stringResource(R.string.ask_question_title),
                subtitle = askSubtitle,
                contentDescription = stringResource(R.string.ask_question_desc),
                testTag = "card_ask_question",
                onClick = onAskQuestionClick
            )
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
    currentEndpoint: String,
    currentReadTextEndpoint: String,
    currentVoice: String,
    onDismiss: () -> Unit,
    onSave: (newVisionUrl: String, newReadTextUrl: String, newVoice: String) -> Unit
) {
    var visionUrlText by remember { mutableStateOf(currentEndpoint) }
    var readTextUrlText by remember { mutableStateOf(currentReadTextEndpoint) }
    var selectedVoice by remember { mutableStateOf(currentVoice) }

    val voices = listOf(
        "am-hamen" to "am-hamen (Addis Native Amharic, Female)",
        "am-simon" to "am-simon (Addis Native Amharic, Male)",
        "am-yohanes-calm" to "am-yohanes-calm (Addis Calm Narrative)",
        "am-tesfa" to "am-tesfa (Addis Energetic Daily)",
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
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("read_text_endpoint_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

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
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(visionUrlText, readTextUrlText, selectedVoice) },
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
