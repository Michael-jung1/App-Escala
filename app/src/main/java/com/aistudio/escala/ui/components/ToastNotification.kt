package com.aistudio.escala.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

enum class ToastType {
    SUCCESS,
    ERROR,
    INFO
}

@Composable
fun ToastNotification(
    message: String,
    visible: Boolean,
    type: ToastType = ToastType.INFO,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .zIndex(100f)
    ) {
        val (bgContainer, iconTint, icon) = when (type) {
            ToastType.SUCCESS -> Triple(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.primary,
                Icons.Default.CheckCircle
            )
            ToastType.ERROR -> Triple(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.error,
                Icons.Default.ErrorOutline
            )
            ToastType.INFO -> Triple(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
                Icons.Default.Info
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .testTag("app_toast_notification"),
            shape = RoundedCornerShape(12.dp),
            color = bgContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = message,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f)
                )
            }
        }
    }
}
