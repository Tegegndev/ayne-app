package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AyneOnYellow
import com.example.ui.theme.AynePureBlack
import com.example.ui.theme.AynePureWhite
import com.example.ui.theme.AyneVibrantYellow

@Composable
fun VisionSettingsDialog(
    currentVoice: String,
    currentImageMaxDimension: Int,
    currentImageJpegQuality: Int,
    onDismiss: () -> Unit,
    onSave: (newVoice: String, newImageMaxDimension: Int, newImageJpegQuality: Int) -> Unit
) {
    var selectedVoice by remember { mutableStateOf(currentVoice) }
    var imageMaxDimension by remember { mutableFloatStateOf(currentImageMaxDimension.toFloat()) }
    var imageJpegQuality by remember { mutableFloatStateOf(currentImageJpegQuality.toFloat()) }

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

                Spacer(modifier = Modifier.height(14.dp))

                // Image compression: max dimension (resolution)
                Text(
                    text = "Image resolution: ${imageMaxDimension.toInt()}px (longest side)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AyneVibrantYellow
                )
                Slider(
                    value = imageMaxDimension,
                    onValueChange = { imageMaxDimension = it },
                    valueRange = 480f..1600f,
                    steps = 6, // discrete stops: 480, 640, 800, 960, 1120, 1280, 1440, 1600
                    colors = SliderDefaults.colors(
                        thumbColor = AyneVibrantYellow,
                        activeTrackColor = AyneVibrantYellow,
                        inactiveTrackColor = AynePureWhite.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("image_resolution_slider")
                )
                Text(
                    text = "Lower = faster uploads (default 800px). Higher = more detail for small text.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AynePureWhite.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Image compression: JPEG quality
                Text(
                    text = "JPEG quality: ${imageJpegQuality.toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AyneVibrantYellow
                )
                Slider(
                    value = imageJpegQuality,
                    onValueChange = { imageJpegQuality = it },
                    valueRange = 40f..95f,
                    colors = SliderDefaults.colors(
                        thumbColor = AyneVibrantYellow,
                        activeTrackColor = AyneVibrantYellow,
                        inactiveTrackColor = AynePureWhite.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("image_quality_slider")
                )
                Text(
                    text = "Lower = smaller/faster (default 72%). Higher = sharper photos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AynePureWhite.copy(alpha = 0.7f)
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
                        imageMaxDimension = 800f
                        imageJpegQuality = 72f
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
                onClick = { onSave(selectedVoice, imageMaxDimension.toInt(), imageJpegQuality.toInt()) },
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
