package codes.pepper.whimsicalart.feature.editor.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Transform tool's pinch-twist path, composed exactly as wired in
 * `ZoomableImage`: real two-finger positions → [InterFingerTwist] (the TRUE
 * inter-finger delta) → [RotationTwistFilter] → the degrees fed to
 * `applyTwistDelta`. The gesture stays **1:1 with the fingers**: the applied
 * rotation never exceeds the fingers' net twist, and a zoom-only pinch — whose
 * fingers travel curved paths — measures ≈0° and never rotates the photo.
 * (Spec: docs/effects_reference.md → *Shared placement-overlay gestures*.)
 *
 * Pure Kotlin + Compose geometry — runs in plain JUnit.
 */
class TransformTwistFilterTest {

    private val toRad = PI.toFloat() / 180f
    private val center = Offset(200f, 200f)

    /** Two pointer positions whose connecting vector points at [angleDegrees]. */
    private fun pairAt(angleDegrees: Float, radius: Float = 100f): Pair<Offset, Offset> {
        val rad = angleDegrees * toRad
        val dir = Offset(cos(rad), sin(rad))
        return center - dir * radius to center + dir * radius
    }

    /**
     * Total raw inter-finger twist and total applied rotation (degrees) for a
     * two-finger trajectory whose vector angle takes [angleDegrees] each step
     * (with an optional growing finger separation).
     */
    private fun simulate(
        angleStepsDegrees: List<Float>,
        startRadius: Float = 100f,
        radiusStep: Float = 0f
    ): Pair<Float, Float> {
        val twist = InterFingerTwist()
        val filter = RotationTwistFilter()
        var radius = startRadius
        var raw = 0f
        var applied = 0f
        angleStepsDegrees.forEach { angle ->
            val (p0, p1) = pairAt(angle, radius)
            val delta = twist.sample(p0, p1, 1L, 2L)
            raw += delta
            applied += filter.sample(delta)
            radius += radiusStep
        }
        return raw / toRad to applied / toRad
    }

    @Test
    fun `a zoom-only pinch applies zero rotation`() {
        // Fingers slip apart along a STRAIGHT line (vector angle pinned at 0°,
        // separation growing 50px → 250px): the inter-finger measurement reads
        // ≈0° total, so the filter applies 0° — the photo never turns while the
        // user just zooms.
        val (raw, applied) = simulate(
            angleStepsDegrees = List(12) { 0f },
            startRadius = 50f,
            radiusStep = 20f
        )
        assertEquals("inter-finger twist read as rotation", 0f, raw, 1e-4f)
        assertEquals("zoom-only pinch rotated the photo", 0f, applied, 1e-6f)
    }

    @Test
    fun `cumulative twist past the dead zone is smoothed through the filter`() {
        // A deliberate 1°/step turn past the 10° dead zone: rotation starts
        // applying, and the total never exceeds the raw motion.
        val (raw, applied) = simulate((0..20).map { it.toFloat() })
        assertTrue("no rotation applied", applied > 0f)
        assertTrue("applied $applied° exceeds raw $raw°", applied <= raw)
    }

    @Test
    fun `applied rotation never exceeds the fingers net twist even with jitter`() {
        // A deliberate turn polluted by jitter: raw deltas net +30°, but a big
        // spike then a correction must NOT inflate the applied rotation beyond
        // the fingers' net twist.
        val (raw, applied) = simulate(listOf(0f, 20f, 5f, 15f, 12f, 3f, 30f))
        assertEquals(30f, raw, 1e-4f)
        assertTrue("applied $applied° exceeds net $raw°", applied <= raw)
        assertTrue("no rotation applied", applied > 0f)
    }

    @Test
    fun `a re-grab after letting go never replays phantom rotation`() {
        // Twist 30°, then re-grab with a NEW finger id at the same positions:
        // the fresh reference reads 0° and the second gesture only adds its own
        // movement — the total never exceeds the fingers' net twist.
        var angle = 0f
        val firstGesture = simulate((1..5).map { angle += 6f; angle })
        val secondGesture = simulate(
            (1..5).map { angle += 6f; angle }.mapIndexed { i, a -> a },
            startRadius = 100f
        )
        assertTrue("first gesture applied nothing", firstGesture.second > 0f)
        assertTrue(
            "re-grab replayed phantom rotation (${secondGesture.second}°)",
            secondGesture.second <= secondGesture.first
        )
        val pairIds = listOf(1L, 2L)
        val twist = InterFingerTwist()
        twist.sample(pairAt(0f).first, pairAt(0f).second, pairIds[0], pairIds[1])
        // A pointer-set change mid-gesture resets the reference (covered in
        // InterFingerTwistTest); here the re-grab must read 0°.
        assertEquals(0f, twist.sample(
            pairAt(30f).first, pairAt(30f).second, 1L, 4L
        ) / toRad, 1e-4f)
    }
}