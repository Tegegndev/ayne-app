package com.example.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.theme.AyneDarkCard
import com.example.ui.theme.AyneOnYellow
import com.example.ui.theme.AynePureBlack
import com.example.ui.theme.AynePureWhite
import com.example.ui.theme.AyneVibrantYellow
import com.example.viewmodel.SupportedLanguage

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

/**
 * First-launch language picker shown once, voiced by the launcher's TTS
 * announcement. Full-screen welcome art as the background; buttons carry
 * English content descriptions with position hints because TalkBack cannot
 * read Ethiopic script out loud.
 */
@Composable
fun FirstLaunchLanguagePickerDialog(
    onLanguageChosen: (SupportedLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AynePureBlack)
                .testTag("language_picker_dialog")
        ) {
            Image(
                painter = painterResource(id = R.drawable.welcome_picker_background),
                contentDescription = null, // decorative: voiced by the TTS announcement instead
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Light scrim so text and buttons stay readable over the artwork.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AynePureBlack.copy(alpha = 0.25f))
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ቋንቋ ይምረጡ",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AyneVibrantYellow
                )
                Text(
                    text = "Choose your language",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = AynePureWhite
                )

                // The illustration sits between the titles and the buttons;
                // the weighing spacer pushes the buttons into the artwork's
                // quieter rippled area near the bottom.
                Spacer(modifier = Modifier.weight(1f))

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FirstLaunchLanguageButton(
                        label = "አማርኛ",
                        hint = "Amharic, first button",
                        tag = "language_button_am",
                        onChosen = { onLanguageChosen(SupportedLanguage.AMHARIC) }
                    )
                    FirstLaunchLanguageButton(
                        label = "Afaan Oromoo",
                        hint = "Afaan Oromoo, middle button",
                        tag = "language_button_om",
                        onChosen = { onLanguageChosen(SupportedLanguage.AFAN_OROMOO) }
                    )
                    FirstLaunchLanguageButton(
                        label = "English",
                        hint = "English, last button",
                        tag = "language_button_en",
                        onChosen = { onLanguageChosen(SupportedLanguage.ENGLISH) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FirstLaunchLanguageButton(
    label: String,
    hint: String,
    tag: String,
    onChosen: () -> Unit
) {
    Button(
        onClick = onChosen,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AyneDarkCard,
            contentColor = AynePureWhite
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .border(1.dp, AyneVibrantYellow, RoundedCornerShape(12.dp))
            .testTag(tag)
            .semantics { contentDescription = hint }
    ) {
        Text(
            text = label,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
