package codes.pepper.whimsicalart.feature.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import codes.pepper.whimsicalart.feature.editor.domain.matting.BackgroundReplacer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Screen-space geometry of the placement grid — the replaccement image's real
 * scaled / panned / rotated bounds, projected exactly as the committed renderer
 * (`BackgroundReplacer.place`) draws them, so the grid always IS the image's
 * true bounds and can never drift from what is displayed.
 */
internal data class PlacementGridGeom(
    val center: Offset,
    val halfW: Float,
    val halfH: Float,
    val rotationDegrees: Float,
    val scale: Float,
    /** Screen pixels corresponding to one canvas unit along each axis. */
    val pxPerCanvasX: Float,
    val pxPerCanvasY: Float
)

/**
 * Projects the placement ([scale], [panX]/[panY], [rotationDegrees] — the SAME
 * `PlacementTransform` the committed renderer uses) onto the [canvasFitSize]
 * preview box as its on-screen bounds, anchored at [originX]/[originY] — the
 * fitted photo rect's top-left inside the full canvas box ([ImageCanvasFrame.
 * offsetX/offsetY]). The draw AND the gesture hit-test must project against the
 * SAME anchored frame, otherwise the hit-test grid sits half-a-letterbox away
 * from the drawn grid (the resize bands drift toward the drawn middle). Mirrors
 * `BackgroundReplacer.placedCanvasSize` for the grown output and `place`'s
 * centre = canvas-centre + pan·canvas.
 */
internal fun placementGridGeom(
    canvasSize: Size,
    canvasFitSize: Size,
    scale: Float,
    panX: Float,
    panY: Float,
    rotationDegrees: Float,
    originX: Float = 0f,
    originY: Float = 0f
): PlacementGridGeom? {
    val cw = canvasSize.width
    val ch = canvasSize.height
    if (cw <= 0f || ch <= 0f) return null
    val (outW, outH) = BackgroundReplacer.placedCanvasSize(
        cw.toInt().coerceAtLeast(1), ch.toInt().coerceAtLeast(1), scale, rotationDegrees
    )
    val outWf = outW.toFloat()
    val outHf = outH.toFloat()
    if (canvasFitSize.width <= 0f || canvasFitSize.height <= 0f) return null
    val fit = min(canvasFitSize.width / outWf, canvasFitSize.height / outHf)
    val outWpx = outWf * fit
    val outHpx = outHf * fit
    val outLeft = (canvasFitSize.width - outWpx) / 2f + originX
    val outTop = (canvasFitSize.height - outHpx) / 2f + originY
    val canvasLeft = outLeft + (outWf - cw) / 2f * fit
    val canvasTop = outTop + (outHf - ch) / 2f * fit
    return PlacementGridGeom(
        center = Offset(
            canvasLeft + cw * fit / 2f + panX * cw * fit,
            canvasTop + ch * fit / 2f + panY * ch * fit
        ),
        halfW = cw * scale * fit / 2f,
        halfH = ch * scale * fit / 2f,
        rotationDegrees = rotationDegrees,
        scale = scale,
        pxPerCanvasX = cw * fit,
        pxPerCanvasY = ch * fit
    )
}

/** The resize-band target a touch on the placement grid is tested against. */
internal fun placementResizeTarget(grid: PlacementGridGeom): OverlayResizeTarget =
    OverlayResizeTarget(
        id = "placement",
        center = grid.center,
        size = Size(grid.halfW * 2f, grid.halfH * 2f),
        rotationDegrees = grid.rotationDegrees,
        scale = grid.scale
    )

/**
 * Resizes the placement proportionally when [handle] is dragged to [touchPx]:
 * reuses the stickers/text corner-edge rule (`resizeFromHandle` — uniform scale
 * about the opposite corner, so the item never distorts), clamped to
 * [minScale]..[maxScale] where [maxScale] is `1×` unless [maxScale] unlocks
 * scale-up (the "Free scale up" switch). Returns `null` when the grid is
 * degenerate.
 */
internal fun resizePlacementBand(
    grid: PlacementGridGeom,
    handle: OverlayResizeHandle,
    touchPx: Offset,
    minScale: Float,
    maxScale: Float
): OverlayResizeResult? {
    if (grid.pxPerCanvasX <= 0f || grid.pxPerCanvasY <= 0f) return null
    return resizeFromHandle(placementResizeTarget(grid), handle, touchPx, minScale, maxScale)
}

/**
 * The pan (in canvas units) the grid must absorb so a band drag keeps its
 * proportion-resize anchor fixed: the resize's new centre relative to the
 * current one, converted from screen px to canvas units.
 */
internal fun resizePanDelta(grid: PlacementGridGeom, resize: OverlayResizeResult): Offset =
    Offset(
        (resize.center.x - grid.center.x) / grid.pxPerCanvasX,
        (resize.center.y - grid.center.y) / grid.pxPerCanvasY
    )

/**
 * Interactive placement grid for the replacement background (the Background
 * tool's Replace-with-image — i.e. Category B placement — state).
 *
 * Draws the bounds of the placement (the scaled / panned / rotated replacement
 * image, normalized against [canvasSize], the source's rotated pixel size)
 * over the preview and owns the canvas gestures while it is active: a
 * single-finger drag **inside the grid body** pans the placement, a one-finger
 * drag starting in a **corner/edge grab band** (the same extended-edge + 20%
 * inner band as Crop / stickers / text — see [hitTestResizeBand]) **resizes it
 * proportionally** (uniform scale from the opposite corner, emitting [onScale]
 * so the Size slider mirrors it), a two-finger pinch scales it
 * **proportionally** (uniform scale, down to 0.2× by default, up to 2× with
 * [freeScaleUp]), and the pinch's twist rotates it. The grid is a control
 * affordance — never the source of a committed crop (the effect emits the
 * subject composited over the transformed placement).
 */
@Composable
fun BackgroundPlacementOverlay(
    canvasSize: Size,
    canvasFitSize: Size,
    frameOffsetX: Float = 0f,
    frameOffsetY: Float = 0f,
    initialScale: Float,
    initialPanX: Float,
    initialPanY: Float,
    initialRotationDegrees: Float,
    freeScaleUp: Boolean,
    onScale: (Float) -> Unit,
    onPan: (Float, Float) -> Unit,
    onRotate: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val scaleLimit = if (freeScaleUp) 2f else 1f
    // Gestures update the LOCAL state (and echo to the VM); the VM echoes back,
    // which re-keys the remember and re-syncs the local mirrors to the same value.
    var scale by remember(initialScale, initialPanX, initialPanY, initialRotationDegrees, freeScaleUp) {
        mutableStateOf(initialScale)
    }
    var panX by remember(initialScale, initialPanX, initialPanY, initialRotationDegrees, freeScaleUp) {
        mutableStateOf(initialPanX)
    }
    var panY by remember(initialScale, initialPanX, initialPanY, initialRotationDegrees, freeScaleUp) {
        mutableStateOf(initialPanY)
    }
    var rotationDegrees by remember(initialScale, initialPanX, initialPanY, initialRotationDegrees, freeScaleUp) {
        mutableStateOf(initialRotationDegrees)
    }

    // The 48 dp fat-finger inflation the corner/edge grab band extends to
    // (same actionable inset as stickers/text).
    val insetPx = with(LocalDensity.current) { OVERLAY_ACTION_INSET_DP.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(canvasSize, canvasFitSize, frameOffsetX, frameOffsetY, freeScaleUp, insetPx) {
                val cw = canvasSize.width
                val ch = canvasSize.height
                if (cw <= 0f || ch <= 0f) return@pointerInput
                // Screen pixels that correspond to one canvas unit at the preview's
                // fit scale (the placement is normalized against [canvasSize]).
                val cwPx = canvasFitSize.width
                val chPx = canvasFitSize.height
                if (cwPx <= 0f || chPx <= 0f) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val startY = down.position.y
                    // Snapshot the placement at gesture start so single-finger drags
                    // follow the pointer absolutely (delta from start), avoiding
                    // accumulated clamp jitter across events.
                    val startPanX = panX
                    val startPanY = panY
                    // The corner/edge resize grab band (extended edge → 20% inner
                    // line) is evaluated against the CURRENT grid geometry, so a
                    // drag starting on a corner/edge resizes instead of panning.
                    val grid = placementGridGeom(
                        canvasSize, canvasFitSize, scale, panX, panY, rotationDegrees,
                        originX = frameOffsetX, originY = frameOffsetY
                    )
                    var resizeHandle = grid?.let {
                        hitTestResizeBand(placementResizeTarget(it), down.position, insetPx)
                    }
                    var lastSpan = 0f
                    var lastAngle = 0f
                    var sawPinch = false
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed }) break
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            // Two-finger pinch: scale proportionally about the
                            // placement centre, and rotate by the twist of the
                            // pointer line. A band drag that gains a second finger
                            // hands control back to the pinch (no jump — deltas
                            // only from this point on).
                            sawPinch = true
                            resizeHandle = null
                            val p0 = pressed[0].position
                            val p1 = pressed[1].position
                            val span = hypot(p1.x - p0.x, p1.y - p0.y)
                            val angle = atan2(p1.y - p0.y, p1.x - p0.x)
                            if (lastSpan > 0f) {
                                val zoom = span / lastSpan
                                if (abs(zoom - 1f) >= 1e-4f) {
                                    scale = (scale * zoom).coerceIn(0.2f, scaleLimit)
                                    onScale(scale)
                                }
                            }
                            if (lastSpan > 0f) {
                                val delta = ((angle - lastAngle + PI) % (2.0 * PI)) - PI
                                if (abs(delta) >= 1e-4) {
                                    rotationDegrees += (delta * 180f / PI).toFloat()
                                    onRotate(rotationDegrees)
                                }
                            }
                            lastSpan = span
                            lastAngle = angle
                        } else if (pressed.isNotEmpty()) {
                            if (resizeHandle != null) {
                                // Corner/edge resize: track the finger against the
                                // live grid, emitting the scale (Size slider follows)
                                // and the pan that keeps the opposite corner fixed.
                                val cur = placementGridGeom(
                                    canvasSize, canvasFitSize, scale, panX, panY, rotationDegrees,
                                    originX = frameOffsetX, originY = frameOffsetY
                                )
                                if (cur != null) {
                                    val resized = resizePlacementBand(
                                        cur, resizeHandle!!, pressed[0].position, 0.2f, scaleLimit
                                    )
                                    if (resized != null) {
                                        val panDelta = resizePanDelta(cur, resized)
                                        val newScale = resized.scale
                                        val newPanX = (panX + panDelta.x).coerceIn(-0.5f, 0.5f)
                                        val newPanY = (panY + panDelta.y).coerceIn(-0.5f, 0.5f)
                                        if (newScale != scale || newPanX != panX || newPanY != panY) {
                                            scale = newScale
                                            panX = newPanX
                                            panY = newPanY
                                            onScale(scale)
                                            onPan(panX, panY)
                                        }
                                    }
                                }
                            } else if (!sawPinch) {
                                // Single-finger drag pans the placement (a pinch that
                                // ended mid-gesture never falls through to a pan).
                                val pointer = pressed[0]
                                if (cwPx > 0f && chPx > 0f) {
                                    panX = (startPanX + (pointer.position.x - startX) / cwPx)
                                        .coerceIn(-0.5f, 0.5f)
                                    panY = (startPanY + (pointer.position.y - startY) / chPx)
                                        .coerceIn(-0.5f, 0.5f)
                                    onPan(panX, panY)
                                }
                            }
                        }
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val viewSize = this.size
            // The grid is drawn at the SAME anchored frame the gesture hit-test
            // uses (the fitted photo rect, offset by imageFrame's letterbox
            // origin), so what you grab is exactly what you see.
            val grid = placementGridGeom(
                canvasSize, canvasFitSize, scale, panX, panY, rotationDegrees,
                originX = frameOffsetX, originY = frameOffsetY
            )
            if (grid == null) return@Canvas
            val centerX = grid.center.x
            val centerY = grid.center.y
            val halfW = grid.halfW
            val halfH = grid.halfH
            val rad = grid.rotationDegrees * PI.toFloat() / 180f
            val cosA = cos(rad)
            val sinA = sin(rad)

            fun corner(x: Float, y: Float): Offset = Offset(
                centerX + x * cosA - y * sinA,
                centerY + x * sinA + y * cosA
            )
            val corners = listOf(
                corner(-halfW, -halfH),
                corner(halfW, -halfH),
                corner(halfW, halfH),
                corner(-halfW, halfH)
            )

            // Dim the canvas outside the placement bounds so the grid's extent reads.
            val film = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, viewSize))
            }
            val billboard = Path().apply {
                moveTo(corners[0].x, corners[0].y)
                lineTo(corners[1].x, corners[1].y)
                lineTo(corners[2].x, corners[2].y)
                lineTo(corners[3].x, corners[3].y)
                close()
            }
            film.addPath(billboard)
            drawPath(film, color = Color.Black.copy(alpha = 0.35f))

            // Grid border (the placement bounds).
            drawPath(billboard, color = Color.White, style = Stroke(width = 2f))

            // Rule-of-thirds guides.
            if (halfW > 0f && halfH > 0f) {
                for (i in 1..2) {
                    val fx = -halfW + 2f * halfW * i / 3f
                    val fy = -halfH + 2f * halfH * i / 3f
                    drawLine(
                        start = corner(fx, -halfH), end = corner(fx, halfH),
                        color = Color.White.copy(alpha = 0.5f), strokeWidth = 1f
                    )
                    drawLine(
                        start = corner(-halfW, fy), end = corner(halfW, fy),
                        color = Color.White.copy(alpha = 0.5f), strokeWidth = 1f
                    )
                }
            }
            // Centre marker so rotation/pan anchoring reads.
            drawLine(
                start = corner(-halfW * 0.1f, 0f), end = corner(halfW * 0.1f, 0f),
                color = Color.White.copy(alpha = 0.6f), strokeWidth = 1f
            )
            drawLine(
                start = corner(0f, -halfH * 0.1f), end = corner(0f, halfH * 0.1f),
                color = Color.White.copy(alpha = 0.6f), strokeWidth = 1f
            )
        }
    }
}