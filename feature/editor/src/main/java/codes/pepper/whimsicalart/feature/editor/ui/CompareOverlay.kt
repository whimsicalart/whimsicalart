package codes.pepper.whimsicalart.feature.editor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale

/**
 * Show the pristine original (or the edited) photo for tap-and-hold compare.
 * It renders the bitmap with the SAME viewport transform (zoom/pan/rotation/
 * flips) as the live preview so the revealed photo is pixel-aligned with the
 * image the user is looking at — panning/zooming the preview keeps the compare
 * frame in place.
 */
@Composable
fun CompareOverlay(
    originalBitmap: Bitmap,
    editedBitmap: Bitmap,
    isComparing: Boolean,
    modifier: Modifier = Modifier,
    rotation: Float = 0f,
    flipHorizontal: Boolean = false,
    flipVertical: Boolean = false,
    transform: ViewportTransform
) {
    val bitmap = if (isComparing) originalBitmap else editedBitmap
    // While comparing (revealing the plain original) the photo is shown
    // COMPLETELY UNTRANSFORMED: no rotation and no flips, per the "hold to
    // compare shows the pristine image" rule. Only the viewport's position/scale
    // are kept so the original lines up with the preview the user is looking at.
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = transform.scale * (if (flipHorizontal && !isComparing) -1f else 1f)
                scaleY = transform.scale * (if (flipVertical && !isComparing) -1f else 1f)
                rotationZ = if (isComparing) 0f else rotation
                translationX = transform.offsetX
                translationY = transform.offsetY
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = if (isComparing) "Original photo" else "Edited photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}
