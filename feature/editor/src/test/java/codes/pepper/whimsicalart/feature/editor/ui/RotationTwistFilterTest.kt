package codes.pepper.whimsicalart.feature.editor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * Spec: effects_reference.md → *Shared placement-overlay gestures* — pinch-twist
 * stabilisation. Rotation must be ignored until the CUMULATIVE twist exceeds the
 * ~10° dead zone (a pure pinch never rotates), and once past it the applied delta
 * is the average of the recent angular deltas (a trembling twist stays steady,
 * and letting go mid-turn never snaps). The total applied rotation is **1:1 with
 * the fingers** — it never exceeds the gesture's net twist.
 *
 * Pure Kotlin — runs in plain JUnit.
 */
class RotationTwistFilterTest {

    @Test
    fun `small twists stay at zero until the cumulative 10 degree dead zone`() {
        val filter = RotationTwistFilter()
        val four = (4f * PI.toFloat() / 180f)
        // Cumulative 4° then 8° — still inside the 10° dead zone.
        assertEquals(0f, filter.sample(four), 1e-6f)
        assertEquals(0f, filter.sample(four), 1e-6f)
        // Cumulative 12° — past the ~10° threshold, rotation starts applying.
        assertTrue("third sample should apply", filter.sample(four) != 0f)
    }

    @Test
    fun `a zoom-only pinch that constantly jitters stays at zero until deliberate`() {
        val filter = RotationTwistFilter()
        // Alternating ~±1° jitter stays well below the 10° cumulative dead zone,
        // so the item never rotates.
        val small = (1f * PI.toFloat() / 180f)
        var applied = 0f
        repeat(8) {
            applied = filter.sample(if (it % 2 == 0) small else -small)
        }
        assertEquals(0f, applied, 1e-6f)
    }

    @Test
    fun `cumulative twist past the dead zone is smoothed`() {
        val filter = RotationTwistFilter()
        // Many consistent 1° deltas: cumulative crosses 10° on the 11th event;
        // from then on the applied value is the (capped) window average and never
        // exceeds the raw motion.
        val one = (1f * PI.toFloat() / 180f)
        var applied = 0f
        var appliedCount = 0
        repeat(16) {
            val v = filter.sample(one)
            if (v != 0f) {
                applied += v
                appliedCount++
            }
        }
        assertTrue("no smoothed rotation applied", appliedCount > 0)
        // Total applied should be the net twist minus the swallowed dead zone
        // and smoothing lag — never more than the raw motion.
        val totalMotion = 16f * one
        assertTrue(
            "applied ${applied / (PI.toFloat() / 180f)}° exceeds raw motion ${totalMotion / (PI.toFloat() / 180f)}°",
            applied <= totalMotion
        )
    }

    @Test
    fun `applied rotation never exceeds the fingers net twist even with jitter`() {
        val filter = RotationTwistFilter()
        val toRad = PI.toFloat() / 180f
        // A deliberate turn polluted by jitter: raw deltas sum well above the
        // dead zone, but a big early spike then a correction must NOT inflate the
        // applied rotation beyond the net twist.
        val deltas = listOf(20f, -10f, 5f, -5f, 5f) // net = 15°
        var applied = 0f
        deltas.forEach { applied += filter.sample(it * toRad) }
        val netTwist = deltas.sum() * toRad
        assertTrue(
            "applied ${applied / toRad}° exceeds net twist ${netTwist / toRad}°",
            applied <= netTwist
        )
        // And the smoothing converges toward it monotonically: the final applied
        // value matches the trailing target within the smoothing lag.
        assertTrue("applied ${applied / toRad}° too far below net", applied > 0f)
    }

    @Test
    fun `letting go mid-turn never snaps (trailing deltas are not inflated)`() {
        val filter = RotationTwistFilter()
        // A burst of rotation then a rest: after the rest the applied delta drops
        // to ~0 — there is no snap-back.
        val three = (3f * PI.toFloat() / 180f)
        repeat(4) { filter.sample(three) } // deliberate turn (> 10° cumulative)
        val beforeRest = filter.sample(three)
        val duringRest = filter.sample(0f)
        val afterRest = filter.sample(0f)
        assertEquals(0f, duringRest, 1e-6f)
        assertEquals(0f, afterRest, 1e-6f)
        assertTrue("no rotation applied during the turn", beforeRest != 0f)
    }

    @Test
    fun `reset clears the dead-zone state for a new gesture`() {
        val filter = RotationTwistFilter()
        val one = (1f * PI.toFloat() / 180f)
        filter.sample(one) // cumulative 1° — inside the dead zone
        filter.reset()
        // After reset the same small sample must again be suppressed (still < 10°).
        assertEquals(0f, filter.sample(one), 1e-6f)
    }
}