package codes.pepper.whimsicalart.feature.editor.ui.frames

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import codes.pepper.whimsicalart.core.designsystem.ColourPicker
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale
import codes.pepper.whimsicalart.feature.editor.domain.FrameGeometry
import codes.pepper.whimsicalart.feature.editor.domain.FrameLayer
import codes.pepper.whimsicalart.feature.editor.domain.FrameStyle
import codes.pepper.whimsicalart.feature.editor.domain.FrameTier
import codes.pepper.whimsicalart.feature.editor.ui.OnOffChipToggle

/**
 * The curve-distance slider span: −100%…+100%, the SIGN picks the direction
 * (negative = inward scoop, positive = outward bulge, `0` = straight) —
 * docs/effects_reference.md → *Frames*, "Custom (Curved) sliders".
 */
internal val CUSTOM_CURVE_DISTANCE_RANGE: ClosedFloatingPointRange<Float> = -1f..1f

/** The decorative-lines count range while the "Lines" toggle is on (docs:
 * *Frame lines* — 1–10; the off state of the toggle is the ONLY 0-lines state). */
internal val FRAME_LINES_RANGE: ClosedFloatingPointRange<Float> = 1f..10f

/** The decorative-lines spacing range (0–20% of the short image edge). */
internal val FRAME_LINE_DISTANCE_RANGE: ClosedFloatingPointRange<Float> = 0f..0.2f

/** The decorative-line width range (0–10% of the short image edge). */
internal val FRAME_LINE_THICKNESS_RANGE: ClosedFloatingPointRange<Float> = 0f..0.1f

/** Test tag on the frame colour picker so UI tests can assert its presence. */
internal const val FRAME_COLOUR_PICKER_TAG = "frame_colour_picker"

/**
 * The Frames controls (docs/effects_reference.md → *Frames*): two **labelled
 * sections** — **"Inner frame"** and **"Outer frame"** — each with its own
 * style chip row, per-style sliders, colour picker and decorative-line sliders.
 * A tier at **"None"** shows ONLY its style row (every other control of that
 * tier is hidden); the outer tier's style row **omits [FrameStyle.HEART]**.
 * Slider values stay in state so re-picking a style restores the last
 * thickness/colour.
 */
@Composable
fun FrameControls(
    innerSelectedStyle: FrameStyle?,
    innerThickness: Float,
    innerColor: Color,
    innerEdgeSize: Float,
    innerCustomAngle: Float,
    innerCustomCurveDistance: Float,
    innerCustomStrength: Float,
    innerCustomCount: Float,
    innerLines: Int,
    innerLinesEnabled: Boolean,
    innerLineDistance: Float,
    innerLineThickness: Float,
    outerSelectedStyle: FrameStyle?,
    outerThickness: Float,
    outerColor: Color,
    outerEdgeSize: Float,
    outerCustomAngle: Float,
    outerCustomCurveDistance: Float,
    outerCustomStrength: Float,
    outerCustomCount: Float,
    outerLines: Int,
    outerLinesEnabled: Boolean,
    outerLineDistance: Float,
    outerLineThickness: Float,
    onInnerStyleSelected: (FrameStyle?) -> Unit,
    onInnerThicknessChange: (Float) -> Unit,
    onInnerColorChange: (Color) -> Unit,
    onInnerEdgeSizeChange: (Float) -> Unit,
    onInnerCustomAngleChange: (Float) -> Unit,
    onInnerCustomCurveDistanceChange: (Float) -> Unit,
    onInnerCustomStrengthChange: (Float) -> Unit,
    onInnerCustomCountChange: (Float) -> Unit,
    onInnerLinesChange: (Int) -> Unit,
    onInnerLinesToggle: (Boolean) -> Unit,
    onInnerLineDistanceChange: (Float) -> Unit,
    onInnerLineThicknessChange: (Float) -> Unit,
    onOuterStyleSelected: (FrameStyle?) -> Unit,
    onOuterThicknessChange: (Float) -> Unit,
    onOuterColorChange: (Color) -> Unit,
    onOuterEdgeSizeChange: (Float) -> Unit,
    onOuterCustomAngleChange: (Float) -> Unit,
    onOuterCustomCurveDistanceChange: (Float) -> Unit,
    onOuterCustomStrengthChange: (Float) -> Unit,
    onOuterCustomCountChange: (Float) -> Unit,
    onOuterLinesChange: (Int) -> Unit,
    onOuterLinesToggle: (Boolean) -> Unit,
    onOuterLineDistanceChange: (Float) -> Unit,
    onOuterLineThicknessChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalUiScale.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp * s, vertical = 8.dp * s)
    ) {
        FrameTierControls(
            title = "Inner frame",
            selectedStyle = innerSelectedStyle,
            thickness = innerThickness,
            color = innerColor,
            edgeSize = innerEdgeSize,
            customAngle = innerCustomAngle,
            customCurveDistance = innerCustomCurveDistance,
            customStrength = innerCustomStrength,
            customCount = innerCustomCount,
            lines = innerLines,
            linesEnabled = innerLinesEnabled,
            lineDistance = innerLineDistance,
            lineThickness = innerLineThickness,
            includeHeart = true,
            onStyleSelected = onInnerStyleSelected,
            onThicknessChange = onInnerThicknessChange,
            onColorChange = onInnerColorChange,
            onEdgeSizeChange = onInnerEdgeSizeChange,
            onCustomAngleChange = onInnerCustomAngleChange,
            onCustomCurveDistanceChange = onInnerCustomCurveDistanceChange,
            onCustomStrengthChange = onInnerCustomStrengthChange,
            onCustomCountChange = onInnerCustomCountChange,
            onLinesChange = onInnerLinesChange,
            onLinesToggle = onInnerLinesToggle,
            onLineDistanceChange = onInnerLineDistanceChange,
            onLineThicknessChange = onInnerLineThicknessChange
        )
        FrameTierControls(
            title = "Outer frame",
            selectedStyle = outerSelectedStyle,
            thickness = outerThickness,
            color = outerColor,
            edgeSize = outerEdgeSize,
            customAngle = outerCustomAngle,
            customCurveDistance = outerCustomCurveDistance,
            customStrength = outerCustomStrength,
            customCount = outerCustomCount,
            lines = outerLines,
            linesEnabled = outerLinesEnabled,
            lineDistance = outerLineDistance,
            lineThickness = outerLineThickness,
            includeHeart = false,
            onStyleSelected = onOuterStyleSelected,
            onThicknessChange = onOuterThicknessChange,
            onColorChange = onOuterColorChange,
            onEdgeSizeChange = onOuterEdgeSizeChange,
            onCustomAngleChange = onOuterCustomAngleChange,
            onCustomCurveDistanceChange = onOuterCustomCurveDistanceChange,
            onCustomStrengthChange = onOuterCustomStrengthChange,
            onCustomCountChange = onOuterCustomCountChange,
            onLinesChange = onOuterLinesChange,
            onLinesToggle = onOuterLinesToggle,
            onLineDistanceChange = onOuterLineDistanceChange,
            onLineThicknessChange = onOuterLineThicknessChange
        )
    }
}

@Composable
private fun FrameTierControls(
    title: String,
    selectedStyle: FrameStyle?,
    thickness: Float,
    color: Color,
    edgeSize: Float,
    customAngle: Float,
    customCurveDistance: Float,
    customStrength: Float,
    customCount: Float,
    lines: Int,
    linesEnabled: Boolean,
    lineDistance: Float,
    lineThickness: Float,
    includeHeart: Boolean,
    onStyleSelected: (FrameStyle?) -> Unit,
    onThicknessChange: (Float) -> Unit,
    onColorChange: (Color) -> Unit,
    onEdgeSizeChange: (Float) -> Unit,
    onCustomAngleChange: (Float) -> Unit,
    onCustomCurveDistanceChange: (Float) -> Unit,
    onCustomStrengthChange: (Float) -> Unit,
    onCustomCountChange: (Float) -> Unit,
    onLinesChange: (Int) -> Unit,
    onLinesToggle: (Boolean) -> Unit,
    onLineDistanceChange: (Float) -> Unit,
    onLineThicknessChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val s = LocalUiScale.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp * s),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = MaterialTheme.typography.titleSmall.fontSize * s
                )
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp * s)
        ) {
            FrameStyleChip("None", selected = selectedStyle == null, onClick = { onStyleSelected(null) })
            FrameStyle.entries.forEach { style ->
                if (!includeHeart && style == FrameStyle.HEART) return@forEach
                FrameStyleChip(
                    style.displayName,
                    selected = selectedStyle == style,
                    onClick = { onStyleSelected(style) }
                )
            }
        }
        // A tier at None shows ONLY its style row — nothing else.
        if (selectedStyle != null) {
            if (selectedStyle.hasEdgeSize) {
                LabeledSlider(
                    label = "Edge Size",
                    value = edgeSize,
                    valueRange = 0f..1f,
                    display = percentDisplay(),
                    onValueChange = onEdgeSizeChange
                )
            }
            if (selectedStyle.isCustom) {
                LabeledSlider(
                    label = "Angle",
                    value = customAngle,
                    valueRange = -180f..180f,
                    display = degreesDisplay(),
                    onValueChange = onCustomAngleChange
                )
                LabeledSlider(
                    label = "Curve Distance",
                    value = customCurveDistance,
                    valueRange = CUSTOM_CURVE_DISTANCE_RANGE,
                    display = percentDisplay(),
                    onValueChange = onCustomCurveDistanceChange
                )
                LabeledSlider(
                    label = "Strength",
                    value = customStrength,
                    valueRange = -1f..1f,
                    display = percentDisplay(),
                    onValueChange = onCustomStrengthChange
                )
                LabeledSlider(
                    label = "Count",
                    value = customCount,
                    valueRange = 1f..10f,
                    display = { "${"%.1f".format(it)}" },
                    onValueChange = onCustomCountChange
                )
            }
            LabeledSlider(
                label = "Thickness",
                value = thickness,
                valueRange = 0f..1f,
                display = percentDisplay(),
                onValueChange = onThicknessChange
            )
            OnOffChipToggle("Lines", linesEnabled) { onLinesToggle(!linesEnabled) }
            if (linesEnabled) {
                LabeledSlider(
                    label = "Lines",
                    value = lines.toFloat().coerceIn(FRAME_LINES_RANGE),
                    valueRange = FRAME_LINES_RANGE,
                    display = { "${it.toInt()}/10" },
                    onValueChange = { onLinesChange(it.toInt()) }
                )
                LabeledSlider(
                    label = "Line Distance",
                    value = lineDistance,
                    valueRange = FRAME_LINE_DISTANCE_RANGE,
                    display = percentDisplay(),
                    onValueChange = onLineDistanceChange
                )
                LabeledSlider(
                    label = "Line Thickness",
                    value = lineThickness,
                    valueRange = FRAME_LINE_THICKNESS_RANGE,
                    display = percentDisplay(),
                    onValueChange = onLineThicknessChange
                )
            }
            ColourPicker(
                label = "",
                selected = color,
                onColorChange = onColorChange,
                showAlpha = true,
                modifier = Modifier.testTag(FRAME_COLOUR_PICKER_TAG)
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp * LocalUiScale.current),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = MaterialTheme.typography.labelMedium.fontSize * LocalUiScale.current
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = display(value),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = MaterialTheme.typography.labelMedium.fontSize * LocalUiScale.current
            )
        )
    }
    Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
}

private fun percentDisplay(): (Float) -> String = { "${(it * 100).toInt()}%" }

private fun degreesDisplay(): (Float) -> String = { "${it.toInt()}°" }

@Composable
private fun FrameStyleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = MaterialTheme.typography.labelMedium.fontSize * LocalUiScale.current
                )
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

/**
 * Draws the frame layers over the edit preview using the exact committed
 * geometry (the same [FrameGeometry] the committed pass consumes) so the
 * overlay matches the exported result. The canvas is the photo rect ENLARGED by
 * [outerExtentPx] on each side: the photo sits at `(outerExtentPx,
 * outerExtentPx)` and the outer tier's ring draws AROUND it (clipped to the
 * viewport, per docs/effects_reference.md → *Outer-frame dimensions*). The
 * inner band is the photo-rect gap to the interior window; the outer band the
 * photo-rect gap to the outer boundary contour.
 */
@Composable
fun FramePreview(
    inner: FrameTier,
    outer: FrameTier,
    outerExtentPx: Float,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        if (outerExtentPx < 0.5f && !inner.enabled) return@Canvas
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val px = outerExtentPx.coerceAtLeast(0f)
        val pw = w - 2 * px
        val ph = h - 2 * px
        if (pw < 1f || ph < 1f) return@Canvas
        val short = minOf(pw, ph)
        if (inner.enabled) {
            val style = inner.style ?: return@Canvas
            val t = (inner.thickness * short / 2f).coerceIn(0f, short / 2f)
            if (t >= 0.5f) {
                val g = FrameGeometry(
                    style, pw.toInt(), ph.toInt(), t,
                    edgeSize = inner.edgeSize,
                    customAngle = inner.customAngle,
                    customCurveDistance = inner.customCurveDistance,
                    customStrength = inner.customStrength,
                    customCount = inner.customCount,
                    offsetX = px.toInt(),
                    offsetY = px.toInt()
                )
                val band = Path().apply {
                    addRect(Rect(px, px, px + pw, px + ph))
                    addPolygon(g.polygon)
                    fillType = PathFillType.EvenOdd
                }
                drawPath(band, Color(inner.color))
            }
        }
        if (outer.enabled) {
            val style = outer.style ?: return@Canvas
            val t = (outer.thickness * short / 2f).coerceIn(0f, short / 2f)
            if (t >= 0.5f) {
                val g = FrameGeometry(
                    style, w.toInt(), h.toInt(), t,
                    edgeSize = outer.edgeSize,
                    customAngle = outer.customAngle,
                    customCurveDistance = outer.customCurveDistance,
                    customStrength = outer.customStrength,
                    customCount = outer.customCount,
                    outward = true,
                    offsetX = px.toInt(),
                    offsetY = px.toInt()
                )
                val ring = Path().apply {
                    addRect(Rect(px, px, px + pw, px + ph))
                    addPolygon(g.polygon)
                    fillType = PathFillType.EvenOdd
                }
                drawPath(ring, Color(outer.color))
            }
        }
    }
}

/**
 * The outer tier's preview extent in px for a fitted photo of [fitW]×[fitH] —
 * the amount the interactive frame preview grows around the photo.
 */
internal fun outerPreviewExtent(outer: FrameTier, fitW: Int, fitH: Int): Float =
    if (outer.enabled) {
        FrameLayer(outer = outer).outerExtentPx(fitW, fitH).coerceAtLeast(0).toFloat()
    } else {
        0f
    }

private fun Path.addPolygon(pts: FloatArray) {
    if (pts.size < 4) return
    moveTo(pts[0], pts[1])
    var i = 2
    while (i < pts.size) {
        lineTo(pts[i], pts[i + 1])
        i += 2
    }
    close()
}