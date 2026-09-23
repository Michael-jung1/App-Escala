package com.aistudio.escala.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.escala.ui.theme.getChurchColor

@Composable
fun ChurchBadge(
    churchName: String,
    modifier: Modifier = Modifier,
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
) {
    val churchColor = getChurchColor(churchName, isDark = isDark)
    Box(
        modifier = modifier
            .background(churchColor.background, RoundedCornerShape(100.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = churchName,
            color = churchColor.text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ChurchDotIndicator(
    churchName: String,
    modifier: Modifier = Modifier,
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
) {
    val churchColor = getChurchColor(churchName, isDark = isDark)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(churchColor.text, CircleShape)
        )
        Text(
            text = churchName,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

