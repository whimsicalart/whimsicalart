package codes.pepper.whimsicalart.feature.editor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import kotlin.math.PI
import kotlin.math.abs

/**
 * The current zoom/pan transform of the editor's photo viewport. Kept as a
 * single shared state (hoisted into [EditorScreen]) so the tap-and-hold compare
 * overlay can render the original at exactly the same position/scale as the
 * live preview (matching zoom, pan, rotation and flips).
 */
class ViewportTransform {
    // docs/effect_stack.md → Scale terms: `canvasZoom` — the user's viewport
    // magnification relative to the fitted letterbox image (1.0 = fitted).
    var canvasZoom by mutableFloatStateOf(1f)
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)

    /** Resets pan/zoom to the default (image fits, no offset). */
    fun reset() {
        canvasZoom = 1f
        offsetX = 0f
        offsetY = 0f
    }

    /** Overwrites this transform with [other]'s canvasZoom and offsets. */
    fun copyFrom(other: ViewportTransform) {
        canvasZoom = other.canvasZoom
        offsetX = other.offsetX
        offsetY = other.offsetY
    }
}

/**
 * Maps a two-finger twist delta (degrees, accumulating on the current value)
 * onto the current rotation (degrees) with a `[0, 360)` wrap, so the transform
 * twist and the free-rotate slider always describe the same angle
 * (docs/effects_reference.md → Transform → *Twist gesture*).
 */
internal fun rotatedByDelta(currentDegrees: Float, deltaDegrees: Float): Float =
    ((currentDegrees + deltaDegrees) % 360f + 360f) % 360f

/**
 * The rotation the canvas layer must show. Over a displayed composite the
 * committed rotation is already baked into its pixels — BUT only the amount the
 * LAST published fold stamped ([compositeBakedRotation]); the transform toolbar
 * (slider / CW-CCW) commits a new angle instantly while the re-fold chews on it,
 * so the unbaked remainder `(rotation − baked)` must be restored on top to give
 * the slider the same live feedback as the twist. The twist's transient
 * [liveRotationDegrees] adds on either base. Outside a composite the plain
 * committed + live rotation applies.
 */
internal fun displayRotation(
    useCompositePreview: Boolean,
    committedRotation: Float,
    bakedRotation: Float,
    liveRotationDegrees: Float,
    eyeEnabled: Boolean
): Float = when {
    useCompositePreview -> (committedRotation - bakedRotation) + liveRotationDegrees
    eyeEnabled -> 0f
    else -> committedRotation + liveRotationDegrees
}

/** Flip mirrors of [displayRotation]: over a composite only the UNBAKED flip
 * (current ⊕ stamped) is shown, otherwise the committed flip. */
internal fun displayFlip(
    useCompositePreview: Boolean,
    committed: Boolean,
    baked: Boolean,
    eyeEnabled: Boolean
): Boolean = when {
    useCompositePreview -> committed != baked
    eyeEnabled -> false
    else -> committed
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
    panZoomEnabled: Boolean = true,
    twistEnabled: Boolean = false,
    onTwistDegrees: ((Float) -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (panZoomEnabled || twistEnabled) {
                    Modifier.pointerInput(panZoomEnabled, twistEnabled) {
                        // Mirrors the platform detectTransformGestures loop (same
                        // touch-slop gating, pan/zoom accumulation and event
                        // consumption) but replaces the rotation source with the
                        // TRUE inter-finger twist (the change in the angle of the
                        // two-pointer vector, tracked per pointer-id pair and
                        // wrapped across ±π) so a zoom-only pinch measures ≈0°,
                        // stabilised by a RotationTwistFilter (dead zone +
                        // sliding-window average, 1:1 with the fingers' net
                        // twist) — exactly as the shared placement-overlay
                        // gestures do (docs/effects_reference.md → *Shared
                        // placement-overlay gestures*).
                        val rotationFilter = RotationTwistFilter()
                        awaitEachGesture {
                            val touchSlop = viewConfiguration.touchSlop
                            var rotation = 0f
                            var zoom = 1f
                            var pan = Offset.Zero
                            var pastTouchSlop = false
                            rotationFilter.reset()
                            // Fresh per gesture (like the overlay gestures), so a
                            // re-grab never replays a phantom rotation.
                            val twist = InterFingerTwist()
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val canceled = event.changes.any { it.isConsumed }
                                if (!canceled) {
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val pressed = event.changes.filter { it.pressed }
                                    val rotationChange = if (pressed.size == 2) {
                                        twist.sample(
                                            pressed[0].position, pressed[1].position,
                                            pressed[0].id.value, pressed[1].id.value
                                        )
                                    } else {
                                        twist.reset()
                                        event.calculateRotation()
                                    }
                                    if (!pastTouchSlop) {
                                        zoom *= zoomChange
                                        rotation += rotationChange
                                        pan += panChange
                                        val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                        val zoomMotion = abs(1 - zoom) * centroidSize
                                        val rotationMotion = abs(rotation * PI.toFloat() * centroidSize / 180f)
                                        val panMotion = pan.getDistance()
                                        if (zoomMotion > touchSlop || rotationMotion > touchSlop || panMotion > touchSlop) {
                                            pastTouchSlop = true
                                        }
                                    }
                                    if (pastTouchSlop) {
                                        if (panZoomEnabled) {
                                            transform.canvasZoom = (transform.canvasZoom * zoomChange).coerceIn(0.5f, 5f)
                                            transform.offsetX += panChange.x
                                            transform.offsetY += panChange.y
                                        }
                                        if (twistEnabled) {
                                            val applied = rotationFilter.sample(rotationChange)
                                            if (applied != 0f) {
                                                onTwistDegrees?.invoke(applied * 180f / PI.toFloat())
                                            }
                                        }
                                    }
                                    event.changes.forEach { if (it.position != it.previousPosition) it.consume() }
                                }
                            } while (!canceled && event.changes.any { it.pressed })
                        }
                    }
                } else {
                    Modifier
                }
            )
            .graphicsLayer {
                this.scaleX = transform.canvasZoom * if (flipHorizontal) -1f else 1f
                this.scaleY = transform.canvasZoom * if (flipVertical) -1f else 1f
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
