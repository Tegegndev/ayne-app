package com.ayne.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayne.R
import com.ayne.ui.theme.AyneOnYellow
import com.ayne.ui.theme.AynePureWhite
import com.ayne.ui.theme.AyneVibrantYellow

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
