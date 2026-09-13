package codes.pepper.whimsicalart.feature.editor.ui.text

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect as AndroidRect
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import codes.pepper.whimsicalart.feature.editor.R
import codes.pepper.whimsicalart.feature.editor.domain.TextAlignment
import codes.pepper.whimsicalart.feature.editor.domain.TextBackgroundShape
import codes.pepper.whimsicalart.feature.editor.domain.TextFont
import codes.pepper.whimsicalart.feature.editor.domain.TextOutline
import codes.pepper.whimsicalart.feature.editor.domain.TextOverlayRenderer
import codes.pepper.whimsicalart.feature.editor.domain.TextStyleFlag
import codes.pepper.whimsicalart.feature.editor.domain.OutlinePass
import codes.pepper.whimsicalart.feature.editor.domain.ROUNDED_BACKGROUND_CORNER_FACTOR
import codes.pepper.whimsicalart.feature.editor.ui.OVERLAY_ACTION_INSET_DP
import codes.pepper.whimsicalart.feature.editor.ui.OverlayGestureKind
import codes.pepper.whimsicalart.feature.editor.ui.OverlayHitTarget
import codes.pepper.whimsicalart.feature.editor.ui.OverlayResizeTarget
import codes.pepper.whimsicalart.feature.editor.ui.overlayTransformGestures
import codes.pepper.whimsicalart.feature.editor.ui.resolveOverlayTarget
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Live, center-anchored editing layer for [TextOverlay]s. Each item is drawn at
 * `position − size/2` (centre anchor), scaled/rotated via graphicsLayer, and the
 * selected item is framed by the selection grid — which outlines exactly the
 * item's own bounds (it never grows).
 *
 * The selection frame always draws on the item's **outer** Box, so no background
 * shape (RECT / ROUNDED / OVAL / PILL clip) can shear it — the shape's clip and
 * fill live on an **inner** Box holding the text (spec: effects_reference.md →
 * Text → *Selection*: the grid `drawBehind` sits OUTSIDE the background clip).
 *
 * Gestures are resolved ONCE per gesture at the container level through
 * [resolveOverlayTarget] (spec: "Actionable area"): the touch targets span the
 * item's current size inflated outward by 50% of its size + 48 dp on every side;
 * an overlapping already-selected item keeps the grab/pinch; otherwise — and for
 * taps — the closest centre wins (exact ties → first added). When
 * [interactionEnabled] is false (eye ON) the gestures are inert and no grid is
 * drawn. The [draftOverlayId] is framed identically to [selectedOverlayId] so
 * the in-progress text gets selection handles while the user types.
 */
@Composable
fun TextOverlayLayer(
    overlays: List<TextOverlay>,
    selectedOverlayId: String? = null,
    draftOverlayId: String? = null,
    onMove: (String, Offset) -> Unit,
    onScale: (String, Float) -> Unit,
    onRotate: (String, Float) -> Unit,
    onResize: (String, Float, Offset) -> Unit,
    onSelect: (String) -> Unit,
    interactionEnabled: Boolean = true,
    sourceBitmap: Bitmap? = null,
    blurFrame: TextBlurFrame? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val insetPx = with(density) { OVERLAY_ACTION_INSET_DP.dp.toPx() }

    // Single measurer shared across overlays — identical style semantics guarantees
    // the measured layout aligns with the Text composable's fill glyphs.
    val textMeasurer = rememberTextMeasurer()

    // Measured real bounds (px) of each item, keyed by id. The actionable area
    // derives from these; the grid still outlines exactly these bounds.
    var sizes by remember(overlays.map { it.id }) { mutableStateOf<Map<String, Size>>(emptyMap()) }

    val currentOverlays by rememberUpdatedState(overlays)
    val currentSizes by rememberUpdatedState(sizes)
    val currentSelectedId by rememberUpdatedState(selectedOverlayId)

    val targets = remember(currentOverlays, currentSizes, density) {
        currentOverlays.mapIndexedNotNull { index, overlay ->
            val size = currentSizes[overlay.id] ?: return@mapIndexedNotNull null
            OverlayHitTarget(
                id = overlay.id,
                center = with(density) {
                    Offset(overlay.position.x.dp.toPx(), overlay.position.y.dp.toPx())
                },
                size = Size(
                    size.width * overlay.scale.coerceAtLeast(0.1f),
                    size.height * overlay.scale.coerceAtLeast(0.1f)
                ),
                order = index
            )
        }
    }
    val currentTargets by rememberUpdatedState(targets)

// Live resize geometry for any requested id: that text item's real rendered
// bounds + rotation + uniform scale, read fresh on every gesture event. The
// target may be an UNSELECTED item — the gesture resolved it with the grab rule.
val currentResizeTarget: (id: String) -> OverlayResizeTarget? = { id ->
    val target = currentTargets.firstOrNull { it.id == id }
    val overlay = currentOverlays.firstOrNull { it.id == id }
    if (target != null && overlay != null) {
        OverlayResizeTarget(
            id = id,
            center = target.center,
            size = target.size,
            rotationDegrees = overlay.rotation,
            scale = overlay.scale.coerceAtLeast(0.1f)
        )
    } else {
        null
    }
}

    Box(modifier = modifier.fillMaxSize()) {
        // Gesture hit-testing lives on the full-canvas container so expanded,
        // overlapping actionable areas resolve exactly as the spec requires —
        // per-item boxes would otherwise hit-test by z-order only.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(overlays.map { it.id }, selectedOverlayId, interactionEnabled, insetPx) {
                    if (!interactionEnabled) return@pointerInput
                    detectTapGestures(onTap = { pos ->
                        resolveOverlayTarget(
                            pos, currentTargets, insetPx, currentSelectedId, OverlayGestureKind.TAP
                        )?.let(onSelect)
                    })
                }
                .pointerInput(overlays.map { it.id }, selectedOverlayId, interactionEnabled, insetPx) {
                    if (interactionEnabled) {
                        overlayTransformGestures(
                            hitTargets = { currentTargets },
                            insetPx = insetPx,
                            selectedId = { currentSelectedId },
                            resizeTarget = currentResizeTarget,
                            onGesture = { id, pan, zoom, rotation ->
                                val current = currentOverlays.firstOrNull { it.id == id } ?: return@overlayTransformGestures
                                val move = with(density) {
                                    Offset(pan.x.toDp().value, pan.y.toDp().value)
                                }
                                onMove(id, current.position + move)
                                onScale(id, (current.scale * zoom).coerceIn(0.3f, 5f))
                                onRotate(id, current.rotation + Math.toDegrees(rotation.toDouble()).toFloat())
                            },
                            onResize = { id, scale, center ->
                                // Corner/edge resize: a new uniform scale about the
                                // opposite corner; the re-anchored centre comes back
                                // in dp for the overlay model.
                                val position = with(density) {
                                    Offset(center.x.toDp().value, center.y.toDp().value)
                                }
                                onResize(id, scale, position)
                            }
                        )
                    }
                }
        ) {}

        overlays.forEach { overlay ->
            val selected = interactionEnabled && (overlay.id == selectedOverlayId || overlay.id == draftOverlayId)
            val measured = sizes[overlay.id] ?: Size.Zero

            Box(
                modifier = Modifier
                    .offset(
                        x = overlay.position.x.dp - with(density) { (measured.width / 2f).toDp() },
                        y = overlay.position.y.dp - with(density) { (measured.height / 2f).toDp() }
                    )
                    .graphicsLayer {
                        scaleX = overlay.scale
                        scaleY = overlay.scale
                        rotationZ = overlay.rotation
                    }
                    .onSizeChanged {
                        sizes = sizes + (overlay.id to Size(it.width.toFloat(), it.height.toFloat()))
                    }
                    // The selection frame lives on the OUTER box and draws over its
                    // FULL bounds, so a background shape's clip/fill (applied on the
                    // INNER box below) can never shear the rectangular grid.
                    .then(
                        if (selected) {
                            Modifier
                                .drawBehind {
                                    drawRect(color = Color.White, style = Stroke(width = 2f))
                                    TextSelectionGrid.anchors(size).forEach { c ->
                                        drawCircle(
                                            color = Color(0xFF3D84FF),
                                            radius = TextSelectionGrid.HANDLE_RADIUS,
                                            center = c
                                        )
                                        drawCircle(
                                            color = Color.White,
                                            radius = TextSelectionGrid.HANDLE_RADIUS * 0.45f,
                                            center = c
                                        )
                                    }
                                }
                        } else {
                            Modifier
                        }
                    )
            ) {
                // The background fill (with its RECT / ROUNDED / OVAL / PILL clip)
                // is confined to the inner box holding the text; it never touches
                // the selection frame or its handles. BLUR is the exception: it
                // needs the REAL photo pixels under the block, so it reads them
                // from the preview bitmap and paints a pre-rendered feathered blur
                // oval behind the text (see buildTextBlurPiece).
                var blurHolder by remember(overlay.id) { mutableStateOf<TextBlurPiece?>(null) }
                // The cache key is the ACTUAL single-rule inputs the piece
                // consumes (see [textBlurPieceKey] and [buildTextBlurPiece] →
                // `toTextItem` → `scaledBlurCoverRect`), NOT the compose box's
                // `measured` size. The reported bug: an edit that re-lays-out
                // into the SAME box size (font family change, a style flag with
                // equal line counts, a rewrap that keeps the block bounds) left
                // every old key equal, so the OLD pre-blurred window was drawn
                // until an unrelated key (position/scale) happened to move.
                // `measured` stays only as the "laid out" gate key, so the piece
                // is born once the item has sized itself.
                val blurKey = textBlurPieceKey(overlay)
                val blurPiece = remember(
                    blurKey,
                    measured,
                    sourceBitmap,
                    blurFrame,
                    density
                ) {
                    val piece = if (
                        overlay.backgroundShape == TextBackgroundShape.BLUR &&
                        sourceBitmap != null &&
                        blurFrame != null &&
                        overlay.backgroundBlur > 0f &&
                        measured != Size.Zero
                    ) {
                        buildTextBlurPiece(context, sourceBitmap, overlay, measured, blurFrame, density)
                    } else {
                        null
                    }
                    if (blurHolder != null && blurHolder !== piece) {
                        blurHolder?.bitmap?.recycle()
                    }
                    blurHolder = piece
                    piece
                }
                // Only the overlay id scope: during preview recomputation the OLD
                // piece is recycled above, and this disposal fires only when the
                // item truly leaves composition — at which point it recycles the
                // still-live current piece.
                DisposableEffect(overlay.id) {
                    onDispose { blurHolder?.bitmap?.recycle() }
                }
                Box(
                    modifier = Modifier
                        .then(backgroundModifier(overlay))
                        .then(
                            if (blurPiece != null) {
                                // Mirrors the committed draw: the pre-blurred
                                // window is painted centred on the box at its
                                // CANVAS-px size (source px × canvas-per-source),
                                // and the box's graphicsLayer applies the item's
                                // rotationZ + scale about its centre — the same
                                // transform the committed rasteriser applies
                                // about the block centre.
                                val dst = blurDstPx(blurPiece, overlay.scale)
                                Modifier.drawBehind {
                                    drawImage(
                                        image = blurPiece.image,
                                        srcOffset = IntOffset.Zero,
                                        srcSize = IntSize(blurPiece.image.width, blurPiece.image.height),
                                        dstOffset = IntOffset(
                                            ((size.width - dst.width) / 2f).roundToInt(),
                                            ((size.height - dst.height) / 2f).roundToInt()
                                        ),
                                        dstSize = dst
                                    )
                                }
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Text is drawn via the Text composable (for accessibility),
                    // but when hasOutline is on the measured layout is also drawn
                    // once with a Stroke + blur halo BEHIND the fill glyphs,
                    // exactly mirroring the committed rasteriser's stroke-first
                    // pass (BitmapRenderer.kt:713-725).
                    val style = remember(overlay) { overlayTextStyle(overlay) }
                    val textLayout = remember(overlay.text, style, textMeasurer) {
                        textMeasurer.measure(AnnotatedString(overlay.text), style)
                    }
                    // A TRANSLUCENT fill must never show its shadow through the
                    // glyph (spec: effects_reference.md → *Text* → "The shadow is
                    // hollowed out of the glyph like the outline"). The shadow is
                    // then drawn separately in drawBehind (isolated layer + clear,
                    // mirroring the committed rasteriser's negative-mask pass), so
                    // the Text fill itself carries NO shadow — the translucent
                    // glyph reveals the image instead. An OPAQUE fill keeps the
                    // style-embedded shadow (the opaque glyph hides what is under
                    // it); that path is unchanged.
                    val fillStyle = remember(overlay) {
                        if (overlay.usesHollowShadow) {
                            style.copy(shadow = null)
                        } else {
                            style
                        }
                    }
                    Text(
                        text = overlay.text,
                        color = overlay.color,
                        fontSize = overlay.fontSize.sp,
                        fontFamily = overlay.fontFamily.toComposeFont(),
                        fontWeight = if (TextStyleFlag.BOLD in overlay.style) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                        fontStyle = if (TextStyleFlag.ITALIC in overlay.style) {
                            FontStyle.Italic
                        } else {
                            FontStyle.Normal
                        },
                        textAlign = overlay.alignment.toTextAlign(),
                        textDecoration = textDecorationOf(overlay),
                        style = fillStyle,
                        modifier = Modifier.drawBehind {
                            if (overlay.hasOutline) {
                                // Mirrors the committed outward-only outline
                                // (BitmapRenderer.drawTexts): each feathered pass is
                                // drawn at DOUBLE width into an offscreen layer, then
                                // the glyph fill shape is cleared out of that layer —
                                // so only the outward band (the full thickness the
                                // user set) survives. Nothing of the outline shows
                                // inside the glyph, even under a translucent fill.
                                drawIntoCanvas { canvas ->
                                    canvas.saveLayer(Rect(Offset.Zero, size), androidx.compose.ui.graphics.Paint())
                                }
                                textOutlinePreviewParams(
                                    overlay.outlineSize,
                                    overlay.outlineBlur,
                                    size.width,
                                    density
                                ).forEach { pass ->
                                    drawText(
                                        textLayoutResult = textLayout,
                                        color = overlay.outlineColor,
                                        alpha = pass.alpha,
                                        drawStyle = Stroke(width = pass.strokeWidthPx * 2f)
                                    )
                                }
                                drawText(
                                    textLayoutResult = textLayout,
                                    color = Color.Black,
                                    drawStyle = Fill,
                                    blendMode = BlendMode.Clear
                                )
                                drawIntoCanvas { canvas -> canvas.restore() }
                            }
                            if (overlay.usesHollowShadow) {
                                // Hollow shadow for a translucent fill: the shadowed
                                // glyph is drawn into an isolated layer at FULL
                                // opacity (its own style already carries the shadow),
                                // then the glyph fill shape is cleared out of the
                                // layer — the same negative-mask pass used for the
                                // outline above. Only the shadow's EXTERNAL band (the
                                // offset silhouette outside the glyph) survives; the
                                // translucent fill drawn by the Text composable on top
                                // reveals the image through the glyph instead of the
                                // shadow. A fully transparent fill (alpha 0) shows the
                                // shadow only as the external hollow silhouette.
                                drawIntoCanvas { canvas ->
                                    canvas.saveLayer(Rect(Offset.Zero, size), androidx.compose.ui.graphics.Paint())
                                }
                                drawText(
                                    textLayoutResult = textLayout,
                                    color = overlay.shadowColor,
                                    alpha = 1f,
                                    drawStyle = Fill
                                )
                                drawText(
                                    textLayoutResult = textLayout,
                                    color = Color.Black,
                                    drawStyle = Fill,
                                    blendMode = BlendMode.Clear
                                )
                                drawIntoCanvas { canvas -> canvas.restore() }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun backgroundModifier(overlay: TextOverlay): Modifier {
    return when (overlay.backgroundShape) {
        TextBackgroundShape.NONE -> Modifier
        TextBackgroundShape.RECT -> Modifier.background(overlay.backgroundColor)
        TextBackgroundShape.ROUNDED -> Modifier
            // The corner follows the SAME rule the committed rasteriser uses —
            // 25% of the text size — so preview and commit agree at any font size
            // and the corner scales with the block under the layer's graphicsLayer.
            .clip(RoundedCornerShape((overlay.fontSize * ROUNDED_BACKGROUND_CORNER_FACTOR).dp))
            .background(overlay.backgroundColor)
        TextBackgroundShape.OVAL -> Modifier
            .clip(CircleShape)
            .background(overlay.backgroundColor)
        TextBackgroundShape.PILL -> Modifier
            .clip(RoundedCornerShape(50))
            .background(overlay.backgroundColor)
        // No opaque fill: the feathered blur oval is drawn behind the text by
        // TextOverlayLayer using the photo pixels under the block.
        TextBackgroundShape.BLUR -> Modifier
    }
}

/**
 * Pure geometry of the text item's selection grid — the frame and its four
 * corner handles ALWAYS span the item's OUTER bounds (0..[size]), independently
 * of the `TextBackgroundShape`: the shape's clip/fill is confined to an inner
 * box (see [backgroundModifier]), so no shape can shear or inset the frame.
 * Kept as a pure helper so the invariant is testable across every shape.
 */
internal object TextSelectionGrid {
    const val HANDLE_RADIUS = 10f

    /** The four outer-box corners the frame stroke + handles are drawn at. */
    fun anchors(size: Size): List<Offset> = listOf(
        Offset.Zero,
        Offset(size.width, 0f),
        Offset(0f, size.height),
        Offset(size.width, size.height)
    )
}

/**
 * Preview-only outline drawing parameters — mirrors the committed path exactly.
 * The [OutlinePass] list is the SAME outward-feather band stack the committed
 * rasteriser produces via [codes.pepper.whimsicalart.feature.editor.domain.TextOutline.featherPasses],
 * only the stroke width is converted from the panel's dp value.
 */
internal fun textOutlinePreviewParams(
    outlineSizeDp: Float,
    outlineBlur: Float,
    blockWidthPx: Float,
    density: Density
): List<OutlinePass> {
    val strokePx = with(density) { outlineSizeDp.dp.toPx() }.coerceAtLeast(0.5f)
    return TextOutline(blur = outlineBlur).featherPasses(strokePx)
}

/**
 * Builds a single [TextStyle] matching exactly the per-param values applied to
 * the [Text] composable, so the measured layout aligns 1:1 with the fill glyphs.
 */
private fun overlayTextStyle(overlay: TextOverlay): TextStyle = TextStyle(
    color = overlay.color,
    fontSize = overlay.fontSize.sp,
    fontFamily = overlay.fontFamily.toComposeFont(),
    fontWeight = if (TextStyleFlag.BOLD in overlay.style) FontWeight.Bold else FontWeight.Normal,
    fontStyle = if (TextStyleFlag.ITALIC in overlay.style) FontStyle.Italic else FontStyle.Normal,
    textAlign = overlay.alignment.toTextAlign(),
    textDecoration = textDecorationOf(overlay),
    shadow = if (overlay.hasShadow) {
        Shadow(
            color = overlay.shadowColor,
            offset = shadowOffset(overlay),
            blurRadius = (overlay.shadowDistance * 0.25f).coerceAtLeast(0.5f)
        )
    } else {
        null
    }
)

private fun textDecorationOf(overlay: TextOverlay): TextDecoration? {
    val hasUnderline = TextStyleFlag.UNDERLINE in overlay.style
    val hasStrikethrough = TextStyleFlag.STRIKETHROUGH in overlay.style
    if (!hasUnderline && !hasStrikethrough) return null
    return TextDecoration.combine(buildList {
        if (hasUnderline) add(TextDecoration.Underline)
        if (hasStrikethrough) add(TextDecoration.LineThrough)
    })
}

private fun shadowOffset(overlay: TextOverlay): Offset {
    val rad = Math.toRadians(overlay.shadowAngle.toDouble())
    val dx = kotlin.math.cos(rad).toFloat() * overlay.shadowDistance
    val dy = kotlin.math.sin(rad).toFloat() * overlay.shadowDistance
    return Offset(dx, dy)
}

private fun TextFont.toComposeFont(): FontFamily {
    return when (this) {
        TextFont.DEFAULT -> FontFamily.Default
        TextFont.SERIF -> SerifBundledFonts
        TextFont.MONOSPACE -> MonospaceBundledFonts
        TextFont.SANS_SERIF -> FontFamily.SansSerif
        TextFont.HANDWRITING -> HandwritingBundledFonts
    }
}

// Bundled OFL/Apache-licensed fonts (Noto Serif, Droid Sans Mono, Dancing Script).
// Resolving these on the device's own generic families is NOT deterministic —
// OEMs (e.g. Motorola with a custom font pack) map every generic family onto a
// single installed font, so Serif/Monospace/Handwriting would render identical.
private val SerifBundledFonts = FontFamily(
    Font(R.font.serif_regular),
    Font(R.font.serif_bold, weight = FontWeight.Bold),
    Font(R.font.serif_italic, style = FontStyle.Italic),
    Font(R.font.serif_bold_italic, weight = FontWeight.Bold, style = FontStyle.Italic)
)
private val MonospaceBundledFonts = FontFamily(Font(R.font.monospace_regular))
private val HandwritingBundledFonts = FontFamily(
    Font(R.font.handwriting_regular),
    Font(R.font.handwriting_bold, weight = FontWeight.Bold)
)

private fun TextAlignment.toTextAlign(): TextAlign {
    return when (this) {
        TextAlignment.LEFT -> TextAlign.Left
        TextAlignment.CENTER -> TextAlign.Center
        TextAlignment.RIGHT -> TextAlign.Right
        // Compose has no justified text alignment — the rasterizer applies the
        // real justification; the preview shows left-aligned justified blocks.
        TextAlignment.JUSTIFIED -> TextAlign.Start
    }
}

/**
 * PX-space location of the visible (fitted) image rect inside the editor box —
 * the same frame [codes.pepper.whimsicalart.feature.editor.ui.text.TextOverlay.toTextItem]
 * normalises against, so the blur oval overlaps exactly the pixels the photo
 * shows under the text block. Produced by the caller from the editor's
 * `ImageCanvasFrame` (whose offset/size are already in px).
 */
data class TextBlurFrame(
    val offsetXPx: Float,
    val offsetYPx: Float,
    val widthPx: Float,
    val heightPx: Float
)

/**
 * On-screen (canvas-px) size a [TextBlurPiece] must be painted at in the item's
 * BOX-local space. The piece is `rw×rh` SOURCE px of the preview bitmap; the
 * preview bitmap is shown inside the canvas frame (`blurFrame`), so one source
 * px spans `canvasPerSourceX` canvas px and the on-screen window is
 * `image.width * canvasPerSourceX` canvas px. Dividing by the item's own
 * [overlayScale] yields the size in the pre-graphicsLayer box coordinates the
 * `drawBehind` paints in — the box's graphicsLayer re-applies that scale, so
 * the oval lands at exactly the canvas-px window size. (Regression: the draw
 * previously skipped the canvas-per-source factor, painting the oval at
 * `image.width / scale` — `scaleX` times too small when the preview bitmap is
 * smaller than the fitted frame, which is the normal preview case → the blur
 * was invisible or clipped behind the glyphs during a live edit.)
 */
internal fun blurDstPx(piece: TextBlurPiece, overlayScale: Float): IntSize {
    val scale = overlayScale.coerceAtLeast(0.1f)
    return IntSize(
        (piece.image.width * piece.canvasPerSourceX / scale).roundToInt(),
        (piece.image.height * piece.canvasPerSourceY / scale).roundToInt()
    )
}

/** A pre-rendered feathered blur oval region (bitmap + its source window). */
data class TextBlurPiece(
    val bitmap: Bitmap,
    val image: ImageBitmap,
    val src: AndroidRect,
    /** Canvas-px per source-px, i.e. `blurFrame.widthPx / sourceBitmap.width`. */
    val canvasPerSourceX: Float,
    /** Canvas-px per source-px, i.e. `blurFrame.heightPx / sourceBitmap.height`. */
    val canvasPerSourceY: Float
)

/**
 * The preview bitmap the LIVE text-blur piece must read the photo pixels from,
 * in display priority: the folded [compositePreview] (what the eye-ON / stacked
 * canvas actually shows), else the [sharpenedPreview] look pass, else the
 * ORIENTED BASE decode — the plain-photo state where neither a stack fold nor a
 * look pass has produced a bitmap, and the canvas simply shows the Coil decode
 * of the uri (whose content the oriented base matches exactly). WITHOUT this
 * base fallback the live source is null on any untouched photo, the
 * `sourceBitmap != null` gate in the blur-piece `remember` stays false, and the
 * BLUR background never renders live (only at commit) — the reported bug.
 */
internal fun resolveLiveTextBlurSource(
    compositePreview: Bitmap?,
    sharpenedPreview: Bitmap?,
    baseBitmap: Bitmap?
): Bitmap? = compositePreview ?: sharpenedPreview ?: baseBitmap

/**
 * The live blur-piece CACHE KEY — the actual single-rule inputs the piece
 * consumes: [TextOverlay.toTextItem] → [TextOverlayRenderer.scaledBlurCoverRect]
 * → `measureTextBlock` (text, fontSize, fontFamily, the bold/italic/underline/
 * strikethrough flags in [TextOverlay.style], alignment) plus the per-item
 * geometry (scale, position, rotation) and the blur intensity. It deliberately
 * EXCLUDES the compose box's `measured` size — a text edit that re-lays-out into
 * the same box must still rebuild, or a stale pre-blurred window is drawn (the
 * reported bug) — and it excludes metrics the cover rule does NOT consume:
 * shadow, outline and background colour. Rotation IS in the key: the shared
 * blur core now re-samples the photo content under the ROTATED footprint
 * ([TextOverlayRenderer.blurRegionOval]), so a rotation delta changes which
 * photo pixels the oval blurs and must rebuild the piece — mirroring the spec
 * (effects_reference.md → *Text* → *Background blur semantics*: ANY footprint
 * change regenerates the cached background). This matches how the committed
 * rasteriser recomputes the committed oval for the same [TextItem] block key.
 */
internal fun textBlurPieceKey(overlay: TextOverlay): String = listOf(
    overlay.id,
    overlay.backgroundShape,
    overlay.backgroundBlur,
    overlay.scale,
    overlay.rotation,
    overlay.position.x,
    overlay.position.y,
    overlay.text,
    overlay.fontSize,
    overlay.fontFamily,
    overlay.style.sorted().joinToString(","),
    overlay.alignment
).joinToString("|")

/**
 * Previews the committed BLUR background for one text item: decides the bitmap
 * window under the padded block (inflated by the feather band) using the same
 * normalised mapping the committed rasteriser applies, then runs the SHARED
 * [TextOverlayRenderer.blurRegionOval] so the preview and the saved photo are
 * pixel-identical. `overlay.position` is in dp; [blurFrame] is in px.
 *
 * The window is THE SHARED SINGLE RULE, not the live box: [measured] is only a
 * "the text has laid out" gate (never zero during a live edit), because the
 * oval's centre, padding (0.2/0.18 × text size) and block height must come
 * from [TextOverlayRenderer.scaledBlurCoverRect] exactly as the committed
 * rasteriser computes them — the SCALED cover is the commit's oval (scale baked
 * into the geometry, see that function) — otherwise the preview oval carries the
 * compose box's own 12/8 dp padding and layout width, painting a visibly
 * DIFFERENT (and at small sizes an effectively invisible) oval from the saved
 * photo. Mapping the scaled cover (in the fitted frame's px) into the preview
 * SOURCE px by [scaleX]/[scaleY] lands the oval on the same pixels the photo
 * shows, for any pinch-scale and rotation.
 */
internal fun buildTextBlurPiece(
    context: Context,
    sourceBitmap: Bitmap,
    overlay: TextOverlay,
    measured: Size,
    blurFrame: TextBlurFrame,
    density: Density
): TextBlurPiece? {
    if (measured == Size.Zero) return null
    val frameW = blurFrame.widthPx
    val frameH = blurFrame.heightPx
    if (frameW <= 0f || frameH <= 0f) return null
    val scaleX = sourceBitmap.width / frameW
    val scaleY = sourceBitmap.height / frameH
    // Normalize the live overlay against the SAME fitted frame the committed
    // item is normalized against, then reuse the committed scaled-cover rule
    // verbatim. The live piece window covers the SCALED cover (the commit's
    // oval) extended by the feather band — the box-local paint then divides it
    // back out (blurDstPx) and the item's graphicsLayer re-applies the same
    // scale, landing on the identical oval the commit bakes.
    val item = overlay.toTextItem(density, frameW, frameH, blurFrame.offsetXPx, blurFrame.offsetYPx)
    val scaled = TextOverlayRenderer.scaledBlurCoverRect(context, item, frameW, frameH)
    if (scaled.width() <= 0f || scaled.height() <= 0f) return null
    val inner = RectF(
        scaled.left * scaleX,
        scaled.top * scaleY,
        scaled.right * scaleX,
        scaled.bottom * scaleY
    )
    val blockH = scaled.height() * scaleY
    val radius = (overlay.backgroundBlur * blockH).coerceAtLeast(0.5f)
    val feather = (blockH * 0.08f).coerceIn(3f, 32f)
    val bw = sourceBitmap.width
    val bh = sourceBitmap.height
    val sx = floor(inner.left - feather).toInt().coerceIn(0, bw - 1)
    val sy = floor(inner.top - feather).toInt().coerceIn(0, bh - 1)
    val ex = ceil(inner.right + feather).toInt().coerceIn(sx + 1, bw)
    val ey = ceil(inner.bottom + feather).toInt().coerceIn(sy + 1, bh)
    val rw = ex - sx
    val rh = ey - sy
    if (rw < 1 || rh < 1) return null
    val blended = TextOverlayRenderer.blurRegionOval(
        sourceBitmap, sx, sy, rw, rh,
        // [overlay.rotation] is forwarded so the shared core re-samples the
        // photo under the ROTATED oval footprint (the box's graphicsLayer
        // rotates the piece back into place); the window stays the UNROTATED
        // scaled cover, whose rotation is exactly the rotated cover's footprint.
        inner, radius, feather,
        overlay.rotation
    )
    return TextBlurPiece(
        bitmap = blended,
        image = blended.asImageBitmap(),
        src = AndroidRect(sx, sy, ex, ey),
        // The window lives in SOURCE px; the preview bitmap is drawn into the
        // canvas frame at `blurFrame`, so each source px covers widthPx/source.width
        // CANVAS px (see blurDstPx).
        canvasPerSourceX = blurFrame.widthPx / sourceBitmap.width,
        canvasPerSourceY = blurFrame.heightPx / sourceBitmap.height
    )
}