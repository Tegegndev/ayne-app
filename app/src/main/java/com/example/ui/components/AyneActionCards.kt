package com.example.ui.components

import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AynePureBlack
import com.example.ui.theme.AynePureWhite
import com.example.ui.theme.AyneVibrantYellow

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
