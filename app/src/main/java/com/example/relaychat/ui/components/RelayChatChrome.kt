package com.example.relaychat.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun RelayChatBackdrop(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val darkTheme = colors.isDarkTheme()
    val topColor = if (darkTheme) {
        lerp(colors.background, colors.surface, 0.45f)
    } else {
        Color(0xFFF9FBFF)
    }
    val bottomColor = if (darkTheme) {
        Color(0xFF0B1522)
    } else {
        Color(0xFFE6EEF9)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        topColor,
                        colors.background,
                        bottomColor,
                    )
                )
            )
    ) {
    }
}

@Composable
fun RelayGlassCard(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val darkTheme = colors.isDarkTheme()
    val cardShape = RoundedCornerShape(8.dp)
    val borderColor = if (darkTheme) {
        colors.outline.copy(alpha = 0.48f)
    } else {
        Color.White.copy(alpha = 0.88f)
    }
    val cardColor = if (darkTheme) {
        colors.surface.copy(alpha = 0.86f)
    } else {
        colors.surface.copy(alpha = 0.92f)
    }
    val overlayBrush = Brush.linearGradient(
        colors = listOf(
            if (darkTheme) {
                Color.White.copy(alpha = 0.07f)
            } else {
                Color.White.copy(alpha = 0.76f)
            },
            accent.copy(alpha = if (darkTheme) 0.10f else 0.05f),
            Color.Transparent,
        ),
        start = Offset.Zero,
        end = Offset(1080f, 1080f),
    )

    Surface(
        modifier = modifier.shadow(
            elevation = if (darkTheme) 8.dp else 10.dp,
            shape = cardShape,
            ambientColor = accent.copy(alpha = if (darkTheme) 0.10f else 0.06f),
            spotColor = accent.copy(alpha = if (darkTheme) 0.10f else 0.06f),
        ),
        shape = cardShape,
        color = cardColor,
        contentColor = colors.onSurface,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(
            modifier = Modifier
                .background(cardColor)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(overlayBrush)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        if (darkTheme) {
                            Color.White.copy(alpha = 0.08f)
                        } else {
                            Color.White.copy(alpha = 0.94f)
                        }
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.24f),
                                accent.copy(alpha = 0.05f),
                                Color.Transparent,
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
fun RelaySectionEyebrow(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun RelayInfoPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    highlight: Color = MaterialTheme.colorScheme.secondary,
) {
    val colors = MaterialTheme.colorScheme
    val darkTheme = colors.isDarkTheme()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .border(
                width = 1.dp,
                color = highlight.copy(alpha = if (darkTheme) 0.34f else 0.22f),
                shape = RoundedCornerShape(999.dp),
            )
            .background(highlight.copy(alpha = if (darkTheme) 0.22f else 0.11f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = highlight,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface,
        )
    }
}

@Composable
fun relayOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = relayTextFieldContainerColor(focused = true, enabled = true),
    unfocusedContainerColor = relayTextFieldContainerColor(focused = false, enabled = true),
    disabledContainerColor = relayTextFieldContainerColor(focused = false, enabled = false),
    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.48f),
    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
)

@Composable
fun RelayEmptyStateCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    RelayGlassCard(
        modifier = modifier,
        accent = MaterialTheme.colorScheme.secondary,
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 24.dp),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun relayTextFieldContainerColor(
    focused: Boolean,
    enabled: Boolean,
): Color {
    val colors = MaterialTheme.colorScheme
    val darkTheme = colors.isDarkTheme()

    return when {
        !enabled && darkTheme -> colors.surfaceVariant.copy(alpha = 0.34f)
        !enabled -> Color.White.copy(alpha = 0.42f)
        focused && darkTheme -> colors.surfaceVariant.copy(alpha = 0.72f)
        focused -> Color.White.copy(alpha = 0.90f)
        darkTheme -> colors.surfaceVariant.copy(alpha = 0.58f)
        else -> Color.White.copy(alpha = 0.78f)
    }
}

private fun ColorScheme.isDarkTheme(): Boolean = background.luminance() < 0.5f
