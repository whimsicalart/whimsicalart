package codes.pepper.whimsicalart.feature.editor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage

/**
 * The current zoom/pan transform of the editor's photo viewport. Kept as a
 * single shared state (hoisted into [EditorScreen]) so the tap-and-hold compare
 * overlay can render the original at exactly the same position/scale as the
 * live preview (matching zoom, pan, rotation and flips).
 */
class ViewportTransform {
    var scale by mutableFloatStateOf(1f)
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)

    /** Resets pan/zoom to the default (image fits, no offset). */
    fun reset() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }
}

@Composable
fun ZoomableImage(
    model: Any,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    rotation: Float = 0f,
    flipHorizontal: Boolean = false,
    flipVertical: Boolean = false,
    colorFilter: ColorFilter? = null,
    bitmapOverride: ImageBitmap? = null,
    transform: ViewportTransform = remember { ViewportTransform() },
    panZoomEnabled: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (panZoomEnabled) {
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            transform.scale = (transform.scale * zoom).coerceIn(0.5f, 5f)
                            transform.offsetX += pan.x
                            transform.offsetY += pan.y
                        }
                    }
                } else {
                    Modifier
                }
            )
            .graphicsLayer {
                this.scaleX = transform.scale * if (flipHorizontal) -1f else 1f
                this.scaleY = transform.scale * if (flipVertical) -1f else 1f
                rotationZ = rotation
                translationX = transform.offsetX
                translationY = transform.offsetY
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmapOverride != null) {
            Image(
                bitmap = bitmapOverride,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize()
            )
        } else {
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
}
