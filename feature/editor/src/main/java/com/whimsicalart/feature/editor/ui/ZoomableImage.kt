package com.whimsicalart.feature.editor.ui

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage

@Composable
fun ZoomableImage(
    model: Any,
    contentDescription: String?,
    rotation: Float = 0f,
    flipHorizontal: Boolean = false,
    flipVertical: Boolean = false,
    colorFilter: ColorFilter? = null,
    modifier: Modifier = Modifier
) {
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
        SubcomposeAsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter,
            loading = {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
