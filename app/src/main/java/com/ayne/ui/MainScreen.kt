package com.ayne.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ayne.R
import com.ayne.data.ConversationTurn
import com.ayne.data.ReadTextResponse
import com.ayne.data.VisionDescribeResponse
import com.ayne.data.cleanLabelPrefix
import com.ayne.ui.components.AyneActionCard
import com.ayne.ui.components.AyneAskQuestionPanel
import com.ayne.ui.components.AyneBottomControls
import com.ayne.ui.components.AyneCompanionPanel
import com.ayne.ui.components.AyneTopBar
import com.ayne.ui.components.FirstLaunchLanguagePickerDialog
import com.ayne.ui.components.LanguageSelector
import com.ayne.ui.components.VisionSettingsDialog
import com.ayne.ui.theme.AyneDarkCard
import com.ayne.ui.theme.AyneOnYellow
import com.ayne.ui.theme.AynePureBlack
import com.ayne.ui.theme.AynePureWhite
import com.ayne.ui.theme.AyneTheme
import com.ayne.ui.theme.AyneVibrantYellow
import com.ayne.util.CameraCaptureHelper
import com.ayne.util.PermissionPromptSpeaker
import com.ayne.viewmodel.AyneAction
import com.ayne.viewmodel.AyneUiState
import com.ayne.viewmodel.AyneViewModel
import com.ayne.viewmodel.SupportedLanguage
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine


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
    val permissionPromptSpeaker = remember(context) { PermissionPromptSpeaker(context) }

    fun permissionRationale(camera: Boolean): String {
        val resource = when (uiState.selectedLanguage) {
            SupportedLanguage.AMHARIC -> if (camera) R.string.camera_permission_rationale_am else R.string.mic_permission_rationale_am
            SupportedLanguage.AFAN_OROMOO -> if (camera) R.string.camera_permission_rationale_om else R.string.mic_permission_rationale_om
            SupportedLanguage.ENGLISH -> if (camera) R.string.camera_permission_rationale_en else R.string.mic_permission_rationale_en
        }
        return context.getString(resource)
    }

    DisposableEffect(permissionPromptSpeaker) {
        onDispose { permissionPromptSpeaker.shutdown() }
    }

    LaunchedEffect(context) {
        viewModel.initFeedbackHelper(context)
    }

    LaunchedEffect(uiState.baseUrl) {
        permissionPromptSpeaker.preload(uiState.baseUrl)
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
    var showFirstLaunchPicker by rememberSaveable { mutableStateOf(false) }
    // True once the first-launch picker has been resolved (pick or defer). The
    // home content stays hidden until then so the homepage never flashes
    // behind the startup dialogs on a brand-new install.
    var firstLaunchResolved by rememberSaveable { mutableStateOf(false) }
    val firstLaunchPending = uiState.isFirstLaunch && !firstLaunchResolved

    // Launch-time permission flow: scheduled continuation of the request that is
    // currently waiting for its system dialog result (null when no launch flow is active).
    var launchCameraContinuation by remember { mutableStateOf<CancellableContinuation<Boolean>?>(null) }
    var launchMicContinuation by remember { mutableStateOf<CancellableContinuation<Boolean>?>(null) }

    var onCameraGrantedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        launchCameraContinuation?.resumeWith(Result.success(isGranted))
        launchCameraContinuation = null
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
        launchMicContinuation?.resumeWith(Result.success(isGranted))
        launchMicContinuation = null
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
            permissionPromptSpeaker.speakThen(
                permissionRationale(camera = true),
                uiState.selectedLanguage.code,
                "camera",
                uiState.baseUrl
            ) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
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
            permissionPromptSpeaker.speakThen(
                permissionRationale(camera = false),
                uiState.selectedLanguage.code,
                "microphone",
                uiState.baseUrl
            ) {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    /**
     * Launch-time flow: on a first launch the language choice comes first, so
     * the permission narration that follows is spoken in the user's language.
     * Missing permissions are then asked one after another, each narrated by
     * the permission-guidance audio. Already granted permissions are never
     * re-asked, so normal starts stay silent.
     */
    LaunchedEffect(Unit) {
        // Let the first frame render and the audio helpers settle before speaking.
        delay(600)

        // First launch: guide the user through language selection once. TalkBack
        // cannot read Ethiopic script, so the dialog is described via device TTS
        // using English position hints.
        if (uiState.isFirstLaunch) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            showFirstLaunchPicker = true
            permissionPromptSpeaker.announce(
                context.getString(R.string.language_picker_announcement),
                "en"
            )
            // Wait until the user picks a language (closing happens on pick,
            // back-press defers the choice and re-opens the picker next launch).
            snapshotFlow { showFirstLaunchPicker }.first { it == false }
        }

        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCamera && hasMic) return@LaunchedEffect

        if (!hasCamera) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val cameraGranted = suspendCancellableCoroutine { continuation ->
                launchCameraContinuation = continuation
                permissionPromptSpeaker.speakThen(
                    permissionRationale(camera = true),
                    uiState.selectedLanguage.code,
                    "camera",
                    uiState.baseUrl
                ) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
            if (!cameraGranted) {
                // The launcher callback already raised the spoken "open settings"
                // dialog. Let the user resolve it before asking for the next
                // permission, so dialogs do not stack on top of each other.
                snapshotFlow { pendingPermissionDialog }.first { it == null }
            }
        }

        if (!hasMic) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            suspendCancellableCoroutine<Boolean> { continuation ->
                launchMicContinuation = continuation
                permissionPromptSpeaker.speakThen(
                    permissionRationale(camera = false),
                    uiState.selectedLanguage.code,
                    "microphone",
                    uiState.baseUrl
                ) {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
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
            // On main screen: Volume− RECORDS a follow-up question about the
            // current scene (old behavior from the initial voice commits).
            // The submitted clip goes to the session-based follow-up with the
            // locked image + describe history, so it continues the see-and-
            // describe conversation. Silence still falls back to describe.
            viewModel.bargeIn()
            requireMicPermission {
                viewModel.startVoiceRecording(context)
            }
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
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = if (uiState.isCompanionMode) 64.dp else 16.dp
                )
                    .testTag("snackbar_host")
            ) { data ->
                if (uiState.isCompanionMode) {
                    // Compact, quiet toast on the fullscreen voice screen —
                    // the loud yellow bar covered the orb and fought with the
                    // bottom hint for attention.
                    Snackbar(
                        containerColor = AyneDarkCard.copy(alpha = 0.95f),
                        contentColor = AynePureWhite,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = data.visuals.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AynePureWhite,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
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
        }
    ) { innerPadding ->
        if (firstLaunchPending) {
            // First launch still in progress (language not picked yet): keep the
            // screen black so the homepage cannot flash behind the dialogs.
            Box(modifier = Modifier
                .fillMaxSize()
                .background(AynePureBlack))
        } else if (uiState.isCompanionMode) {
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
                currentVoice = uiState.customVoice,
                currentImageMaxDimension = uiState.imageMaxDimension,
                currentImageJpegQuality = uiState.imageJpegQuality,
                onDismiss = { showSettingsDialog = false },
                onSave = { newVoice, newMaxDimension, newJpegQuality ->
                    showSettingsDialog = false
                    viewModel.onAction(context, AyneAction.UpdateVoice(newVoice))
                    viewModel.onAction(
                        context,
                        AyneAction.UpdateImageCompression(newMaxDimension, newJpegQuality)
                    )
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

        if (showFirstLaunchPicker) {
            FirstLaunchLanguagePickerDialog(
                onLanguageChosen = { language ->
                    showFirstLaunchPicker = false
                    firstLaunchResolved = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.onAction(context, AyneAction.SelectLanguage(language))
                    // Confirm in the chosen language (device TTS, best effort).
                    permissionPromptSpeaker.announce(language.displayName, language.code)
                },
                onDismiss = {
                    showFirstLaunchPicker = false
                    firstLaunchResolved = true
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
