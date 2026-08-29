package com.whimsicalart.feature.editor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale

@Composable
fun CompareOverlay(
    originalBitmap: Bitmap,
    editedBitmap: Bitmap,
    isComparing: Boolean,
    rotation: Float = 0f,
    flipHorizontal: Boolean = false,
    flipVertical: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (isComparing) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
                .graphicsLayer {
                    scaleX = scale * if (flipHorizontal) -1f else 1f
                    scaleY = scale * if (flipVertical) -1f else 1f
                    rotationZ = rotation
                    translationX = offsetX
                    translationY = offsetY
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = originalBitmap.asImageBitmap(),
                contentDescription = "Original photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
                .graphicsLayer {
                    scaleX = scale * if (flipHorizontal) -1f else 1f
                    scaleY = scale * if (flipVertical) -1f else 1f
                    rotationZ = rotation
                    translationX = offsetX
                    translationY = offsetY
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = editedBitmap.asImageBitmap(),
                contentDescription = "Edited photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
