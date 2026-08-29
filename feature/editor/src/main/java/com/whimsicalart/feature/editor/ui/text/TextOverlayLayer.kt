package com.whimsicalart.feature.editor.ui.text

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TextOverlayLayer(
    overlays: List<TextOverlay>,
    onMove: (String, Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        overlays.forEach { overlay ->
            var backgroundModifier: Modifier = Modifier
            when (overlay.backgroundShape) {
                TextBackgroundShape.NONE -> {}
                TextBackgroundShape.ROUNDED -> backgroundModifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(overlay.backgroundColor.copy(alpha = 0.75f))
                TextBackgroundShape.PILL -> backgroundModifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(overlay.backgroundColor.copy(alpha = 0.75f))
                TextBackgroundShape.OVAL -> backgroundModifier = Modifier
                    .clip(CircleShape)
                    .background(overlay.backgroundColor.copy(alpha = 0.75f))
            }

            // Re-read the latest position inside the drag coroutine (keyed on
            // the stable overlay id) and convert the px drag delta to the dp
            // space the overlay position lives in, so a dragged text never
            // jumps or flies away after recomposition.
            val currentOverlay by rememberUpdatedState(overlay)
            Box(
                modifier = Modifier
                    .offset(
                        x = overlay.position.x.dp,
                        y = overlay.position.y.dp
                    )
                    .pointerInput(overlay.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val position = currentOverlay.position
                            onMove(
                                overlay.id,
                                with(density) {
                                    Offset(
                                        position.x + dragAmount.x.toDp().value,
                                        position.y + dragAmount.y.toDp().value
                                    )
                                }
                            )
                        }
                    }
                    .then(backgroundModifier)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = overlay.text,
                    color = overlay.color,
                    fontSize = overlay.fontSize.sp,
                    fontStyle = overlay.fontStyle.toComposeStyle(),
                    fontWeight = if (overlay.fontStyle == FontStyle.BOLD ||
                        overlay.fontStyle == FontStyle.BOLD_ITALIC
                    ) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },
                    textAlign = when (overlay.alignment) {
                        TextAlignment.LEFT -> TextAlign.Left
                        TextAlignment.CENTER -> TextAlign.Center
                        TextAlignment.RIGHT -> TextAlign.Right
                    },
                    style = TextStyle(
                        shadow = if (overlay.hasShadow) {
                            androidx.compose.ui.graphics.Shadow(
                                color = overlay.shadowColor,
                                offset = Offset(2f, 2f),
                                blurRadius = overlay.shadowRadius
                            )
                        } else {
                            null
                        }
                    )
                )
            }
        }
    }
}

private fun FontStyle.toComposeStyle(): androidx.compose.ui.text.font.FontStyle {
    return when (this) {
        com.whimsicalart.feature.editor.ui.text.FontStyle.NORMAL,
        com.whimsicalart.feature.editor.ui.text.FontStyle.BOLD -> {
            androidx.compose.ui.text.font.FontStyle.Normal
        }
        com.whimsicalart.feature.editor.ui.text.FontStyle.ITALIC,
        com.whimsicalart.feature.editor.ui.text.FontStyle.BOLD_ITALIC -> {
            androidx.compose.ui.text.font.FontStyle.Italic
        }
    }
}