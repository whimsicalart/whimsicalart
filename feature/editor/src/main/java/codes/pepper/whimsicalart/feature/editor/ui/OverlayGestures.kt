package codes.pepper.whimsicalart.feature.editor.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/** The operation being resolved against the expanded actionable areas. */
enum class OverlayGestureKind { TAP, GRAB }

/**
 * Which part of an overlay item's resize grab band a touch landed on, expressed
 * in the item's OWN rotated frame (top/left follow the item as it rotates).
 */
enum class OverlayResizeHandle {
    TOP_LEFT, TOP, TOP_RIGHT,
    LEFT, RIGHT,
    BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT
}

/**
 * Geometry of the currently-selected overlay item for one-finger corner/edge
 * resize: its real rendered bounds' centre and size (px, already including the
 * placement scale), its rotation, and the current uniform scale (so a drag can
 * compute the new scale from the movement ratio).
 */
data class OverlayResizeTarget(
    val id: String,
    val center: Offset,
    val size: Size,
    val rotationDegrees: Float,
    val scale: Float
)

/** The result of a corner/edge resize: the new uniform scale and centre. */
data class OverlayResizeResult(
    val scale: Float,
    val center: Offset
)

/**
 * Inner grab line of the resize band: 20% of the item's own dimension inside
 * its real bounds (the same inner fraction as Crop's frame).
 */
const val OVERLAY_RESIZE_INSIDE_FRACTION = 0.2f

/** Rotates [v] by [degrees] around the origin (screen orientation). */
internal fun rotateVector(v: Offset, degrees: Float): Offset {
    val rad = Math.toRadians(degrees.toDouble())
    val cos = kotlin.math.cos(rad).toFloat()
    val sin = kotlin.math.sin(rad).toFloat()
    return Offset(v.x * cos - v.y * sin, v.x * sin + v.y * cos)
}

/**
 * Decides whether a touch at [touchPx] lands in the resize grab band of
 * [target] (spec: effects_reference.md → Stickers/Text → *Resize grab band*).
 * The band runs from the **extended selection edge** — the item's real bounds
 * inflated outward by 50% of its size + [insetPx] (= the 48 dp actionable
 * inflation) — **inward**, down to a line **20% of the item's own dimension
 * inside its real bounds**. Touches in the band resize that corner/edge; touches
 * deeper inside (past the 20% line) fall through to `null`, meaning MOVE. All
 * geometry is evaluated in the item's own rotated frame, so the bands follow
 * the corner/edge handles as the item rotates. Corners take priority over edges.
 */
fun hitTestResizeBand(
    target: OverlayResizeTarget,
    touchPx: Offset,
    insetPx: Float
): OverlayResizeHandle? {
    val local = rotateVector(touchPx - target.center, -target.rotationDegrees)
    val halfW = target.size.width / 2f
    val halfH = target.size.height / 2f
    val edgeX = halfW + target.size.width * 0.5f + insetPx
    val edgeY = halfH + target.size.height * 0.5f + insetPx
    val innerX = halfW - target.size.width * OVERLAY_RESIZE_INSIDE_FRACTION
    val innerY = halfH - target.size.height * OVERLAY_RESIZE_INSIDE_FRACTION
    val onLeft = local.x >= -edgeX && local.x <= -innerX
    val onRight = local.x >= innerX && local.x <= edgeX
    val onTop = local.y >= -edgeY && local.y <= -innerY
    val onBottom = local.y >= innerY && local.y <= edgeY
    if (!onLeft && !onRight && !onTop && !onBottom) return null
    return when {
        onLeft && onTop -> OverlayResizeHandle.TOP_LEFT
        onRight && onTop -> OverlayResizeHandle.TOP_RIGHT
        onLeft && onBottom -> OverlayResizeHandle.BOTTOM_LEFT
        onRight && onBottom -> OverlayResizeHandle.BOTTOM_RIGHT
        onLeft -> OverlayResizeHandle.LEFT
        onRight -> OverlayResizeHandle.RIGHT
        onTop -> OverlayResizeHandle.TOP
        onBottom -> OverlayResizeHandle.BOTTOM
        else -> null
    }
}

/**
 * Computes the new uniform scale + centre when [handle] is dragged to
 * [touchPx]. Unlike Crop (free aspect) a sticker/text **always scales
 * proportionally** from the opposite corner: the scale factor is the ratio of
 * the finger's distance to that anchor vs. the dragged corner's original
 * distance (edges scale along their axis and the orthogonal axis follows), so
 * the item never distorts. Everything is done in world coordinates; because
 * rotation is a rigid transform the distances (and therefore the ratio) are
 * identical in the item's own frame. The result is clamped to
 * [minScale]..[maxScale], keeping the anchor point fixed.
 */
fun resizeFromHandle(
    target: OverlayResizeTarget,
    handle: OverlayResizeHandle,
    touchPx: Offset,
    minScale: Float,
    maxScale: Float
): OverlayResizeResult {
    if (target.size.width <= 0f || target.size.height <= 0f) {
        return OverlayResizeResult(target.scale, target.center)
    }
    val sx = when (handle) {
        OverlayResizeHandle.LEFT, OverlayResizeHandle.TOP_LEFT, OverlayResizeHandle.BOTTOM_LEFT -> -1f
        OverlayResizeHandle.RIGHT, OverlayResizeHandle.TOP_RIGHT, OverlayResizeHandle.BOTTOM_RIGHT -> 1f
        else -> 0f
    }
    val sy = when (handle) {
        OverlayResizeHandle.TOP, OverlayResizeHandle.TOP_LEFT, OverlayResizeHandle.TOP_RIGHT -> -1f
        OverlayResizeHandle.BOTTOM, OverlayResizeHandle.BOTTOM_LEFT, OverlayResizeHandle.BOTTOM_RIGHT -> 1f
        else -> 0f
    }
    val dragOffset = rotateVector(
        Offset(sx * target.size.width / 2f, sy * target.size.height / 2f),
        target.rotationDegrees
    )
    val anchor = target.center - dragOffset
    val draggedCornerDistance = (target.center + dragOffset - anchor).getDistance()
    if (draggedCornerDistance <= 1e-4f) {
        return OverlayResizeResult(target.scale, target.center)
    }
    val scale = (target.scale * (touchPx - anchor).getDistance() / draggedCornerDistance)
        .coerceIn(minScale, maxScale)
    val m = scale / target.scale
    return OverlayResizeResult(scale, anchor + (target.center - anchor) * m)
}

/**
 * Geometry of an overlay item that participates in touch resolution: its real
 * rendered bounds' centre and size (px), plus its [order] in the item list.
 */
data class OverlayHitTarget(
    val id: String,
    val center: Offset,
    val size: Size,
    val order: Int
)

/** Flat finger-inset (~48 dp, converted to px by the caller). */
const val OVERLAY_ACTION_INSET_DP = 48f

/**
 * The ACTIONABLE area of [target]: its real bounds inflated outward by **50% of
 * its current size + 48 dp** on every side (so small items still give a
 * fat-finger-friendly target and the target grows with the item). This is the
 * touch area only — selection grids keep outlining exactly the item's bounds.
 */
fun expandActionableArea(target: OverlayHitTarget, insetPx: Float): Rect {
    val dx = target.size.width * 0.5f + insetPx
    val dy = target.size.height * 0.5f + insetPx
    val halfW = target.size.width / 2f
    val halfH = target.size.height / 2f
    return Rect(
        left = target.center.x - halfW - dx,
        top = target.center.y - halfH - dy,
        right = target.center.x + halfW + dx,
        bottom = target.center.y + halfH + dy
    )
}

/**
 * Resolves which item a touch at [pointerPx] targets, per the spec rules
 * (docs/effects_reference.md → Stickers/Text → "Actionable area"):
 * - Candidates are items whose EXPANDED actionable area contains the touch.
 * - For [OverlayGestureKind.GRAB] (drag/move + pinch zoom/rotate) the currently
 *   selected item wins whenever it is among the candidates — an item being
 *   edited is never lost beneath another overlapping one.
 * - Every other case ([OverlayGestureKind.TAP], or a grab with no candidate
 *   selected) targets the item whose **centre is closest** to the touch point,
 *   regardless of list or z order.
 * - Only when two or more centres are **exactly equal** (the same point, and it
 *   is the closest) does list insertion order decide: the earliest added wins.
 */
fun resolveOverlayTarget(
    pointerPx: Offset,
    targets: List<OverlayHitTarget>,
    insetPx: Float,
    selectedId: String?,
    kind: OverlayGestureKind
): String? {
    val candidates = targets.filter { expandActionableArea(it, insetPx).contains(pointerPx) }
    if (candidates.isEmpty()) return null
    if (kind == OverlayGestureKind.GRAB) {
        val selected = candidates.firstOrNull { it.id == selectedId }
        if (selected != null) return selected.id
    }
    return candidates.minWith(
        compareBy<OverlayHitTarget> { distanceSq(it.center, pointerPx) }.thenBy { it.order }
    ).id
}

private fun distanceSq(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy
}

/** Wraps an angular difference into `[-π, π]` so crossing the ±π seam never spikes. */
internal fun wrapAngleDiff(diffInRadians: Float): Float {
    val tau = 2f * PI.toFloat()
    var d = diffInRadians
    while (d > PI.toFloat()) d -= tau
    while (d < -PI.toFloat()) d += tau
    return d
}

/**
 * Measures the **inter-finger twist** — the change in the angle of the vector
 * between two pressed pointers (spec: effects_reference.md → *Shared
 * placement-overlay gestures*). The built-in `calculateRotation()` averages each
 * pointer's OWN path turning, which inflates the reported angle while the
 * fingers scale or drift (a zoom-only pinch keeps reading as a twist); only the
 * inter-finger angle is the real rotation. Feed it the two pointer positions +
 * ids on each gesture event; it returns the wrapped angular delta (radians) vs.
 * the previous event for the SAME pointer-id pair, holding the reference so
 * crossing the ±π seam never spikes. A change of pointer pair resets the
 * reference (a re-grab never phantoms a rotation).
 *
 * Shared by the overlay gestures and the Transform tool so both turn exactly
 * with the fingers and never diverge.
 */
class InterFingerTwist {
    private var previousAngle: Float? = null
    private var previousIds: List<Long>? = null

    /**
     * The wrapped angular delta (radians) between [firstPosition]/[firstId] and
     * [secondPosition]/[secondId] vs. the previous event for the same pair, or
     * `0f` on the first event a pointer pair is seen.
     */
    fun sample(
        firstPosition: Offset,
        secondPosition: Offset,
        firstId: Long,
        secondId: Long
    ): Float {
        val angle = atan2(
            secondPosition.y - firstPosition.y,
            secondPosition.x - firstPosition.x
        )
        val currentIds = listOf(firstId, secondId).sorted()
        val previous = if (previousIds == currentIds) previousAngle else null
        previousAngle = angle
        previousIds = currentIds
        return if (previous == null) 0f else wrapAngleDiff(angle - previous)
    }

    /** Clears the reference so the next event starts a fresh measurement. */
    fun reset() {
        previousAngle = null
        previousIds = null
    }
}

/** Degrees of cumulative twist below which rotation is ignored (spec dead zone). */
const val ROTATION_DEAD_ZONE_DEGREES = 10f

/** Number of recent angular deltas averaged for the smoothed rotation. */
const val ROTATION_SMOOTHING_WINDOW = 3

/**
 * Pinch-twist stabilisation (spec: effects_reference.md → *Shared
 * placement-overlay gestures*). Feed each gesture event's raw angular delta (in
 * radians); the filter returns the **smoothed** delta to apply:
 * - **Dead zone**: angular jitter is ignored until the CUMULATIVE twist of the
 *   gesture exceeds ~10° (deliberately generous), so a pinch the user intends as
 *   zoom-only virtually never rotates the item even as the fingers drift while
 *   scaling.
 * - **Smoothing**: once past the dead zone, the applied delta is the **average
 *   of the recent angular deltas** (a small sliding window), so a trembling
 *   twist produces a steady rotation and letting go mid-turn never snaps.
 * - **1:1 with the fingers**: the total applied rotation is clamped to the
 *   gesture's net twist minus the swallowed dead zone — the item can never turn
 *   more than the fingers did (a noisy twist can under-run, never over-run).
 */
class RotationTwistFilter(
    private val deadZoneDegrees: Float = ROTATION_DEAD_ZONE_DEGREES,
    private val windowSize: Int = ROTATION_SMOOTHING_WINDOW
) {
    private val deltas = ArrayDeque<Float>()
    private var cumulativeRadians = 0f
    private var appliedRadians = 0f
    private var locked = false
    private var lockSign = 1f

    /** Returns the smoothed rotation delta (radians) to apply for [changeRadians], or `0f`. */
    fun sample(changeRadians: Float): Float {
        if (changeRadians == 0f) return 0f
        cumulativeRadians += changeRadians
        deltas.addLast(changeRadians)
        while (deltas.size > windowSize) deltas.removeFirst()
        val deadZoneRadians = deadZoneDegrees * (PI.toFloat() / 180f)
        if (!locked) {
            if (abs(cumulativeRadians) > deadZoneRadians) {
                locked = true
                lockSign = if (cumulativeRadians > 0f) 1f else -1f
            } else {
                return 0f
            }
        }
        // The gesture's rotation target = the fingers' net twist minus the once-
        // swallowed dead zone (in the locked direction). The applied rotation
        // converges to this and NEVER crosses it, so the item turns exactly with
        // the fingers — never several times more.
        val target = cumulativeRadians - lockSign * deadZoneRadians
        val remaining = target - appliedRadians
        val smoothed = deltas.average().toFloat()
        // Use the smoothed delta only when it moves TOWARD the target and fits
        // within the remaining budget; otherwise step exactly to the target. This
        // keeps rotation smooth without ever overshooting the fingers' twist.
        val step = if (smoothed * remaining > 0f && abs(smoothed) <= abs(remaining)) {
            smoothed
        } else {
            remaining
        }
        appliedRadians += step
        return step
    }

    /** Resets the filter for a new gesture. */
    fun reset() {
        deltas.clear()
        cumulativeRadians = 0f
        appliedRadians = 0f
        locked = false
        lockSign = 1f
    }
}

/**
 * Drag / pinch / pinch-twist / corner-resize detector that mirrors
 * `detectTransformGestures` but resolves the gesture's target through
 * [resolveOverlayTarget] ONCE on the very first touch (the same rule as grab,
 * so the resolve may happen before touch slop) and keeps it for the whole
 * gesture: overlapping expanded areas follow the spec's selection /
 * centre-closeness rules instead of z-order, its resize band is evaluated per
 * that resolved id, and a moving/pinching gesture never switches items
 * mid-flight.
 *
 * The gesture **target resolves once** at the very first touch, using the same
 * rule as grab ([resolveOverlayTarget] with GRAB): the selected item wins when
 * the touch is inside its expanded actionable area, otherwise the smallest
 * candidate whose centre is nearest. A one-finger touch that lands inside the
 * resize grab band of **that resolved target** ([resizeTarget] is asked per-id)
 * starts a **corner/edge resize** (spec: effects_reference.md → Stickers/Text →
 * *Selection*): the uniform scale about the opposite corner tracks the finger —
 * so dragging the corner of an *unselected* item resizes it on the FIRST drag,
 * while a touch inside the selected item's expanded area always stays on it.
 * If a second finger joins, the gesture hands control back to the
 * move/pinch/twist transformer locked to the same resolved target.
 *
 * The rotation reported to [onGesture] is the **true finger twist** — the
 * change in the angle of the vector between the two pointers (a zoom-only pinch,
 * whose individual fingers shear along curved paths, measures ≈0°), then
 * **stabilised** by a [RotationTwistFilter] (dead zone + sliding-window average,
 * 1:1 with the net twist — a trembling twist stays steady and never over-rotates).
 */
suspend fun PointerInputScope.overlayTransformGestures(
    hitTargets: () -> List<OverlayHitTarget>,
    insetPx: Float,
    selectedId: () -> String?,
    resizeTarget: (id: String) -> OverlayResizeTarget?,
    minScale: Float = 0.3f,
    maxScale: Float = 5f,
    onGesture: (targetId: String, pan: Offset, zoom: Float, rotation: Float) -> Unit,
    onResize: (targetId: String, scale: Float, center: Offset) -> Unit
) {
    val touchSlop = viewConfiguration.touchSlop
    val rotationFilter = RotationTwistFilter()
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false)
        rotationFilter.reset()
        // The gesture's target resolves ONCE with the grab rule (the selected
        // item wins when the touch is inside its expanded actionable area,
        // otherwise the candidate whose centre is nearest), so the whole
        // gesture — resize-band check, move, pinch and twist — stays locked to
        // the same item from the very first touch.
        val resolvedId = resolveOverlayTarget(
            first.position, hitTargets(), insetPx, selectedId(), OverlayGestureKind.GRAB
        )
        // Last event's inter-finger twist tracker (a re-grab via a new pointer
        // set resets the reference internally, so it never produces a phantom
        // rotation).
        val twist = InterFingerTwist()
        var rotation = 0f
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        var targetId: String? = resolvedId
        // A one-finger touch inside the RESOLVED target's resize band locks the
        // gesture into corner/edge resize for as long as one pointer is down.
        // (A second finger landing later flips the gesture back to pinch/twist.)
        var resizing = false
        var resizeHandle: OverlayResizeHandle? = null
        var resizeId: String? = null
        if (resolvedId != null) {
            val resolved = resizeTarget(resolvedId)
            if (resolved != null) {
                val handle = hitTestResizeBand(resolved, first.position, insetPx)
                if (handle != null) {
                    resizing = true
                    resizeHandle = handle
                    resizeId = resolvedId
                }
            }
        }
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                if (resizing) {
                    // A second finger means the user actually pinches: hand the
                    // rest of the gesture back to the transform path (no jump —
                    // only deltas from this point apply).
                    if (event.changes.size > 1) {
                        resizing = false
                        resizeHandle = null
                        targetId = resizeId
                        pastTouchSlop = true
                        rotation = 0f
                        zoom = 1f
                        pan = Offset.Zero
                    } else {
                        val cur = resizeTarget(resizeId!!)
                        if (cur != null) {
                            val update = resizeFromHandle(
                                cur, resizeHandle!!, event.changes.first().position, minScale, maxScale
                            )
                            if (abs(update.scale - cur.scale) > 1e-5f ||
                                (update.center - cur.center).getDistance() > 1e-5f
                            ) {
                                onResize(cur.id, update.scale, update.center)
                            }
                        }
                    }
                } else {
                    // True finger twist: the change in the angle of the vector
                    // between the two pressed pointers. The built-in
                    // calculateRotation() averages each pointer's OWN path
                    // turning, which inflates the reported angle while the
                    // fingers scale or drift (a zoom-only pinch keeps reading as
                    // a twist). Only the inter-finger angle is the real rotation,
                    // so it is used whenever exactly two pointers are down;
                    // any other pointer count falls back to calculateRotation().
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
                    val zoomChange = event.calculateZoom()
                    val panChange = event.calculatePan()
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
                    val id = if (pastTouchSlop) targetId else null
                    val appliedRotation = rotationFilter.sample(rotationChange)
                    if (id != null && (appliedRotation != 0f || zoomChange != 1f || panChange != Offset.Zero)) {
                        onGesture(id, panChange, zoomChange, appliedRotation)
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}