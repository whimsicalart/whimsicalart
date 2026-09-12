package codes.pepper.whimsicalart.feature.editor.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shared inter-finger twist measurement used by the overlay gestures and the
 * Transform tool (docs/effects_reference.md → *Shared placement-overlay
 * gestures*). Returns the change in the angle of the two-pointer vector vs. the
 * previous event for the SAME pointer-id pair, wrapped into `[-π, π]`; the
 * first event for a pair yields `0f` (a re-grab never phantoms a rotation).
 *
 * Pure Kotlin + Compose geometry — runs in plain JUnit.
 */
class InterFingerTwistTest {

    private val toRad = PI.toFloat() / 180f
    private val center = Offset(200f, 200f)

    /** Two pointer positions whose connecting vector points at [angleDegrees]. */
    private fun pairAt(angleDegrees: Float, radius: Float = 100f): Pair<Offset, Offset> {
        val rad = angleDegrees * toRad
        val dir = Offset(cos(rad), sin(rad))
        return center - dir * radius to center + dir * radius
    }

    @Test
    fun `inter-finger delta is the angle change of the two-pointer vector`() {
        val twist = InterFingerTwist()
        val (p0a, p1a) = pairAt(0f)
        val (p0b, p1b) = pairAt(30f)
        // First event for the pair establishes the reference.
        assertEquals(0f, twist.sample(p0a, p1a, 1L, 2L), 1e-6f)
        // Second event: the vector turned 30°.
        assertEquals(
            30f * toRad,
            twist.sample(p0b, p1b, 1L, 2L),
            1e-4f
        )
        // A steady finger pair returns the steady per-event delta.
        val (p0c, p1c) = pairAt(60f)
        assertEquals(
            30f * toRad,
            twist.sample(p0c, p1c, 1L, 2L),
            1e-4f
        )
    }

    @Test
    fun `crossing the plus-minus 180 seam returns the short way around`() {
        val twist = InterFingerTwist()
        val (p0a, p1a) = pairAt(179f)
        twist.sample(p0a, p1a, 7L, 9L)
        // A -179° follow-up is +2° past the seam, NOT a -358° snap.
        val (p0b, p1b) = pairAt(-179f)
        val delta = twist.sample(p0b, p1b, 7L, 9L)
        assertEquals(2f * toRad, delta, 1e-4f)
    }

    @Test
    fun `a pointer-set change resets the reference (no phantom rotation)`() {
        val twist = InterFingerTwist()
        val (p0a, p1a) = pairAt(0f)
        val (p0b, p1b) = pairAt(30f)
        twist.sample(p0a, p1a, 1L, 2L)
        assertEquals(30f * toRad, twist.sample(p0b, p1b, 1L, 2L), 1e-4f)
        // A NEW finger (id 3) replaces id 2: the first event of the new pair must
        // report 0°, even though its vector is at 45°.
        val (p0c, p1c) = pairAt(45f)
        assertEquals(0f, twist.sample(p0c, p1c, 1L, 3L), 1e-6f)
        // The rebuilt reference then measures its own movement.
        val (p0d, p1d) = pairAt(60f)
        assertEquals(15f * toRad, twist.sample(p0d, p1d, 1L, 3L), 1e-4f)
    }

    @Test
    fun `reset clears the reference for a fresh gesture`() {
        val twist = InterFingerTwist()
        val (p0a, p1a) = pairAt(0f)
        val (p0b, p1b) = pairAt(30f)
        twist.sample(p0a, p1a, 1L, 2L)
        assertEquals(30f * toRad, twist.sample(p0b, p1b, 1L, 2L), 1e-4f)
        twist.reset()
        // Without a reference the same 30° vector must not replay as rotation.
        assertEquals(0f, twist.sample(p0b, p1b, 1L, 2L), 1e-6f)
    }
}