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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
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
    val batteryPercentage: Int = 92,
    val isBusy: Boolean = false,
    val busyMessage: String? = null,
    val apiEndpoint: String = DEFAULT_ENDPOINT,
    val customVoice: String = "am-hamen",
    val lastResponse: VisionDescribeResponse? = null
) {
    companion object {
        val DEFAULT_ENDPOINT: String by lazy {
            try {
                BuildConfig.VISION_API_URL.takeIf { it.isNotBlank() }
                    ?: "https://api.mirkuz.ai/api/see-and-describe"
            } catch (e: Throwable) {
                "https://api.mirkuz.ai/api/see-and-describe"
            }
        }
    }
}

sealed interface AyneAction {
    data class DescribeSurroundings(val frameBase64: String) : AyneAction
    data class ReadText(val frameBase64: String) : AyneAction
    data object AskQuestion : AyneAction
    data object RepeatLastAnswer : AyneAction
    data class SelectLanguage(val language: SupportedLanguage) : AyneAction
    data class UpdateApiEndpoint(val newUrl: String) : AyneAction
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

    private val _uiState = MutableStateFlow(AyneUiState())
    val uiState: StateFlow<AyneUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    fun showMessage(msg: String) {
        _userMessage.tryEmit(msg)
    }

    fun onAction(context: Context, action: AyneAction) {
        when (action) {
            is AyneAction.DescribeSurroundings -> {
                executeVisionRequest(context, action.frameBase64, mode = "general")
            }
            is AyneAction.ReadText -> {
                executeVisionRequest(context, action.frameBase64, mode = "read_text")
            }
            is AyneAction.AskQuestion -> {
                _userMessage.tryEmit("Microphone active: Speak your question... (Coming soon)")
            }
            is AyneAction.RepeatLastAnswer -> {
                repeatLastAnswer(context)
            }
            is AyneAction.SelectLanguage -> {
                _uiState.update { it.copy(selectedLanguage = action.language) }
                _userMessage.tryEmit("Language: ${action.language.displayName}")
            }
            is AyneAction.UpdateApiEndpoint -> {
                val cleaned = action.newUrl.trim()
                if (cleaned.isNotBlank()) {
                    _uiState.update { it.copy(apiEndpoint = cleaned) }
                    _userMessage.tryEmit("API Endpoint updated")
                }
            }
            is AyneAction.UpdateVoice -> {
                _uiState.update { it.copy(customVoice = action.voiceId) }
                _userMessage.tryEmit("Voice set to ${action.voiceId}")
            }
            is AyneAction.VolumeCaptureTriggered -> {
                _userMessage.tryEmit("Volume ▲: Capturing surroundings...")
            }
            is AyneAction.VolumeFollowUpTriggered -> {
                _userMessage.tryEmit("Volume ▼: Ready for question...")
            }
        }
    }

    private fun executeVisionRequest(
        context: Context,
        frameBase64: String,
        mode: String
    ) {
        if (_uiState.value.isBusy) return

        val state = _uiState.value
        val progressMsg = if (mode == "read_text") {
            context.getString(R.string.reading_text_progress)
        } else {
            context.getString(R.string.analyzing_surroundings)
        }

        _uiState.update {
            it.copy(
                isBusy = true,
                busyMessage = progressMsg
            )
        }
        _userMessage.tryEmit(progressMsg)

        viewModelScope.launch {
            val endpoint = state.apiEndpoint
            val voice = state.customVoice

            val result = visionApiService.seeAndDescribe(
                endpointUrl = endpoint,
                imageBase64 = frameBase64,
                audioBase64 = null, // Silent 1-tap capture
                customVoice = voice,
                mode = mode,
                includeAudio = true
            )

            result.onSuccess { response ->
                val chosenAnswer = if (state.selectedLanguage == SupportedLanguage.ENGLISH && response.english.isNotBlank()) {
                    response.english
                } else {
                    response.amharic.ifBlank { response.english }
                }

                _uiState.update {
                    it.copy(
                        isBusy = false,
                        busyMessage = null,
                        lastAnswer = chosenAnswer,
                        lastEnglishAnswer = response.english,
                        lastAudioBase64 = response.audio?.audioBase64,
                        lastResponse = response
                    )
                }

                // Play synthesized speech if returned
                val rawAudio = response.audio?.audioBase64
                if (!rawAudio.isNullOrBlank()) {
                    audioPlaybackHelper.playBase64Audio(
                        context = context,
                        rawBase64Audio = rawAudio,
                        onComplete = {
                            // Completed playback
                        },
                        onError = { err ->
                            _userMessage.tryEmit("Audio error: $err")
                        }
                    )
                } else {
                    _userMessage.tryEmit(chosenAnswer)
                }
            }.onFailure { error ->
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
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                onVolumeCaptureTriggered?.invoke()
                    ?: viewModel.onAction(this, AyneAction.VolumeCaptureTriggered)
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                onVolumeFollowUpTriggered?.invoke()
                    ?: viewModel.onAction(this, AyneAction.VolumeFollowUpTriggered)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
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

    // CameraX instance
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    // Bind CameraX on start
    LaunchedEffect(lifecycleOwner) {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            if (cameraProvider.hasCamera(cameraSelector)) {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)
            }
        } catch (e: Exception) {
            // Emulators or headless environments gracefully fallback
        }
    }

    // Settings dialog state
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Rationale dialog state
    var pendingPermissionDialog by remember { mutableStateOf<String?>(null) }
    var pendingPermissionMessage by remember { mutableStateOf("") }

    // Camera permission launcher
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

    // Audio/Mic permission launcher
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

    // Action execution helpers
    fun triggerDescribeSurroundings() {
        requireCameraPermission {
            coroutineScope.launch {
                val frame = CameraCaptureHelper.captureFrame(context, imageCapture)
                viewModel.onAction(context, AyneAction.DescribeSurroundings(frame))
            }
        }
    }

    fun triggerReadText() {
        requireCameraPermission {
            coroutineScope.launch {
                val frame = CameraCaptureHelper.captureFrame(context, imageCapture)
                viewModel.onAction(context, AyneAction.ReadText(frame))
            }
        }
    }

    // Register volume key shortcuts
    LaunchedEffect(onRegisterVolumeHandlers) {
        onRegisterVolumeHandlers?.invoke(
            { triggerDescribeSurroundings() },
            { requireMicPermission { viewModel.onAction(context, AyneAction.AskQuestion) } }
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
            onAskQuestionClick = {
                requireMicPermission {
                    viewModel.onAction(context, AyneAction.AskQuestion)
                }
            },
            onSettingsClick = { showSettingsDialog = true },
            modifier = Modifier.padding(innerPadding)
        )

        // Vision API Settings Dialog (Env Var + Endpoint Customizer)
        if (showSettingsDialog) {
            VisionSettingsDialog(
                currentEndpoint = uiState.apiEndpoint,
                currentVoice = uiState.customVoice,
                onDismiss = { showSettingsDialog = false },
                onSave = { newUrl, newVoice ->
                    showSettingsDialog = false
                    viewModel.onAction(context, AyneAction.UpdateApiEndpoint(newUrl))
                    viewModel.onAction(context, AyneAction.UpdateVoice(newVoice))
                }
            )
        }

        // Accessible High-Contrast Permission Rationale Dialog
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
            batteryPercentage = uiState.batteryPercentage,
            onLanguageSelected = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onAction(AyneAction.SelectLanguage(it))
            },
            onSettingsClick = onSettingsClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Progress / Analysis Indicator banner for visual & TalkBack awareness
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
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = uiState.busyMessage ?: stringResource(R.string.analyzing_surroundings),
                        color = AyneVibrantYellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // Last answer text card (if available) for visual inspection / accessibility
        if (!uiState.lastAnswer.isNullOrBlank() && !uiState.isBusy) {
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

        // 2. Three Stacked Action Cards
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: Describe Surroundings (Mirkuz Vision see-and-describe API)
            AyneActionCard(
                icon = Icons.Filled.PhotoCamera,
                title = stringResource(R.string.describe_surroundings_title),
                subtitle = stringResource(R.string.describe_surroundings_amharic),
                contentDescription = stringResource(R.string.describe_surroundings_desc),
                testTag = "card_describe_surroundings",
                onClick = onDescribeClick
            )

            // Card 2: Read Text (mode: read_text)
            AyneActionCard(
                icon = Icons.Filled.Description,
                title = stringResource(R.string.read_text_title),
                subtitle = stringResource(R.string.read_text_amharic),
                contentDescription = stringResource(R.string.read_text_desc),
                testTag = "card_read_text",
                onClick = onReadTextClick
            )

            // Card 3: Ask Question (voice mode)
            AyneActionCard(
                icon = Icons.Filled.Mic,
                title = stringResource(R.string.ask_question_title),
                subtitle = stringResource(R.string.ask_question_amharic),
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
    batteryPercentage: Int,
    onLanguageSelected: (SupportedLanguage) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val logoDesc = stringResource(R.string.logo_desc)
    val batteryDesc = stringResource(R.string.battery_status_desc, batteryPercentage)
    val settingsDesc = stringResource(R.string.settings_button_desc)

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
            // Yellow circular badge with walking person
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AyneVibrantYellow)
                    .semantics { contentDescription = logoDesc },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.DirectionsWalk,
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

        // Right side: Settings gear + Signal & Battery
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(36.dp)
                    .semantics { contentDescription = settingsDesc }
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = AyneVibrantYellow,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics {
                    contentDescription = batteryDesc
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.NetworkCell,
                    contentDescription = null,
                    tint = AynePureWhite,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Filled.BatteryFull,
                    contentDescription = null,
                    tint = AynePureWhite,
                    modifier = Modifier.size(18.dp)
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
            // Large 48dp vibrant yellow Material icon
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AyneVibrantYellow,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(24.dp))

            // Title and Subtitle
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
        // Solid vibrant yellow repeat button
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
                    imageVector = Icons.Filled.VolumeUp,
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

        // Centered hint line in white with yellow triangles:
        // "Volume ▲ capture · Volume ▼ follow-up"
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
    currentVoice: String,
    onDismiss: () -> Unit,
    onSave: (newEndpoint: String, newVoice: String) -> Unit
) {
    var endpointText by remember { mutableStateOf(currentEndpoint) }
    var selectedVoice by remember { mutableStateOf(currentVoice) }

    val voices = listOf(
        "am-hamen" to "am-hamen (Addis Native Amharic, Female)",
        "am-simon" to "am-simon (Addis Native Amharic, Male)",
        "Zephyr" to "Zephyr (Gemini Neural, Fast Clear)",
        "Puck" to "Puck (Gemini Neural, Upbeat)",
        "Aoede" to "Aoede (Gemini Neural, Warm)",
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

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = endpointText,
                    onValueChange = { endpointText = it },
                    label = { Text("VISION_API_URL", color = AyneVibrantYellow) },
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
                        .testTag("api_endpoint_input")
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
                            .padding(vertical = 6.dp)
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
                onClick = { onSave(endpointText, selectedVoice) },
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
            onSettingsClick = {}
        )
    }
}
