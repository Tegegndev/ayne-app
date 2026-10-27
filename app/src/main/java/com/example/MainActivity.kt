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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.example.ui.theme.AyneOnYellow
import com.example.ui.theme.AynePureBlack
import com.example.ui.theme.AynePureWhite
import com.example.ui.theme.AyneTheme
import com.example.ui.theme.AyneVibrantYellow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    val batteryPercentage: Int = 92,
    val isBusy: Boolean = false
)

sealed interface AyneAction {
    data object DescribeSurroundings : AyneAction
    data object ReadText : AyneAction
    data object AskQuestion : AyneAction
    data object RepeatLastAnswer : AyneAction
    data class SelectLanguage(val language: SupportedLanguage) : AyneAction
    data object VolumeCapture : AyneAction
    data object VolumeFollowUp : AyneAction
}

class AyneViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AyneUiState())
    val uiState: StateFlow<AyneUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    fun showMessage(msg: String) {
        _userMessage.tryEmit(msg)
    }

    fun onAction(action: AyneAction) {
        when (action) {
            is AyneAction.DescribeSurroundings -> {
                _userMessage.tryEmit("Camera active: Capturing surroundings... (Coming soon — backend not connected)")
            }
            is AyneAction.ReadText -> {
                _userMessage.tryEmit("Camera active: Scanning text... (Coming soon — backend not connected)")
            }
            is AyneAction.AskQuestion -> {
                _userMessage.tryEmit("Microphone active: Listening for voice input... (Coming soon — backend not connected)")
            }
            is AyneAction.RepeatLastAnswer -> {
                val answer = _uiState.value.lastAnswer
                if (answer.isNullOrBlank()) {
                    _userMessage.tryEmit("No answer yet")
                } else {
                    _userMessage.tryEmit(answer)
                }
            }
            is AyneAction.SelectLanguage -> {
                _uiState.update { it.copy(selectedLanguage = action.language) }
                _userMessage.tryEmit("Language: ${action.language.displayName}")
            }
            is AyneAction.VolumeCapture -> {
                _userMessage.tryEmit("Volume ▲ Capture: Ready to scan (Coming soon — backend not connected)")
            }
            is AyneAction.VolumeFollowUp -> {
                _userMessage.tryEmit("Volume ▼ Follow-up: Ready to listen (Coming soon — backend not connected)")
            }
        }
    }
}

// ==========================================
// Main Activity
// ==========================================

class MainActivity : ComponentActivity() {
    private val viewModel: AyneViewModel by viewModels()

    // Key event triggers for volume shortcuts
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
                onVolumeCaptureTriggered?.invoke() ?: viewModel.onAction(AyneAction.VolumeCapture)
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                onVolumeFollowUpTriggered?.invoke() ?: viewModel.onAction(AyneAction.VolumeFollowUp)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}

// ==========================================
// Screen Composables with Runtime Permissions
// ==========================================

@Composable
fun AyneAppScreen(
    viewModel: AyneViewModel,
    modifier: Modifier = Modifier,
    onRegisterVolumeHandlers: (((() -> Unit), (() -> Unit)) -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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

    // Helper functions to verify permission before triggering actions
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

    // Register volume key shortcuts with runtime permission gating
    LaunchedEffect(onRegisterVolumeHandlers) {
        onRegisterVolumeHandlers?.invoke(
            { requireCameraPermission { viewModel.onAction(AyneAction.VolumeCapture) } },
            { requireMicPermission { viewModel.onAction(AyneAction.VolumeFollowUp) } }
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
            onAction = viewModel::onAction,
            onRequestCameraAction = { onGranted -> requireCameraPermission(onGranted) },
            onRequestMicAction = { onGranted -> requireMicPermission(onGranted) },
            modifier = Modifier.padding(innerPadding)
        )

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

@Composable
fun AyneScreenContent(
    uiState: AyneUiState,
    onAction: (AyneAction) -> Unit,
    onRequestCameraAction: (() -> Unit) -> Unit,
    onRequestMicAction: (() -> Unit) -> Unit,
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
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Three Stacked Action Cards
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: Describe Surroundings (requires CAMERA)
            AyneActionCard(
                icon = Icons.Filled.PhotoCamera,
                title = stringResource(R.string.describe_surroundings_title),
                subtitle = stringResource(R.string.describe_surroundings_amharic),
                contentDescription = stringResource(R.string.describe_surroundings_desc),
                testTag = "card_describe_surroundings",
                onClick = {
                    onRequestCameraAction {
                        onAction(AyneAction.DescribeSurroundings)
                    }
                }
            )

            // Card 2: Read Text (requires CAMERA)
            AyneActionCard(
                icon = Icons.Filled.Description,
                title = stringResource(R.string.read_text_title),
                subtitle = stringResource(R.string.read_text_amharic),
                contentDescription = stringResource(R.string.read_text_desc),
                testTag = "card_read_text",
                onClick = {
                    onRequestCameraAction {
                        onAction(AyneAction.ReadText)
                    }
                }
            )

            // Card 3: Ask Question (requires RECORD_AUDIO)
            AyneActionCard(
                icon = Icons.Filled.Mic,
                title = stringResource(R.string.ask_question_title),
                subtitle = stringResource(R.string.ask_question_amharic),
                contentDescription = stringResource(R.string.ask_question_desc),
                testTag = "card_ask_question",
                onClick = {
                    onRequestMicAction {
                        onAction(AyneAction.AskQuestion)
                    }
                }
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
    modifier: Modifier = Modifier
) {
    val logoDesc = stringResource(R.string.logo_desc)
    val batteryDesc = stringResource(R.string.battery_status_desc, batteryPercentage)

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

            Spacer(modifier = Modifier.width(10.dp))

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

        // Right side: Signal & Battery
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
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "4G",
                color = AynePureWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.BatteryFull,
                contentDescription = null,
                tint = AynePureWhite,
                modifier = Modifier.size(20.dp)
            )
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
            .padding(horizontal = 4.dp),
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
                fontSize = 13.sp,
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
                    .padding(horizontal = 3.dp, vertical = 6.dp)
                    .semantics {
                        role = Role.RadioButton
                        contentDescription = "${language.displayName}${if (isSelected) ", selected" else ""}"
                    }
            )

            if (index < languages.size - 1) {
                Text(
                    text = " | ",
                    color = AyneVibrantYellow,
                    fontSize = 13.sp,
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
// Previews
// ==========================================

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
fun AyneAppScreenPreview() {
    AyneTheme {
        AyneScreenContent(
            uiState = AyneUiState(),
            onAction = {},
            onRequestCameraAction = {},
            onRequestMicAction = {}
        )
    }
}
