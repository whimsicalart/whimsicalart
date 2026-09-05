package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.Bitmap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BeautyGeometryContextTest {

    /** A context whose generator hands back a unique geometry per call (counted). */
    private class FakeContext : AutoCloseable {
        var calls = 0
        val context = DefaultBeautyGeometryContext(
            generator = BeautyGeometryGenerator { _: Bitmap ->
                calls++
                BeautyGeometry()
            }
        )
        override fun close() {}
    }

    @Test
    fun `empty list lazily generates and reuses the last geometry until stale`() {
        val fake = FakeContext()
        val image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val first = runBlocking { fake.context.geometryFor(image) }
        val second = runBlocking { fake.context.geometryFor(image) }
        // Same last geometry reused, no extra generation — ML runs once.
        assertSame(first, second)
        assertEquals(1, fake.calls)
        image.recycle()
    }

    @Test
    fun `markStale forces a fresh generation on next geometryFor`() {
        val fake = FakeContext()
        val image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val before = runBlocking { fake.context.geometryFor(image) }
        fake.context.markStale()
        val after = runBlocking { fake.context.geometryFor(image) }
        // Stale → new geometry generated (list pushes, stale cleared).
        assertEquals(false, before === after)
        assertEquals(2, fake.calls)
        assertEquals(after, fake.context.lastGeometry())
        image.recycle()
    }

    @Test
    fun `geometryFor returns last, markStale clears the stale flag after generating`() {
        val fake = FakeContext()
        val image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        runBlocking { fake.context.geometryFor(image) }
        fake.context.markStale()
        val regenerated = runBlocking { fake.context.geometryFor(image) }
        assertSame(regenerated, runBlocking { fake.context.geometryFor(image) })
        assertEquals(2, fake.calls)
        image.recycle()
    }

    @Test
    fun `geometryFor regenerates when the image dimensions change`() {
        val fake = FakeContext()
        // Same content at a different resolution (a preview fold vs a full-size
        // fold) must never reuse the other's pixel-space geometry.
        val preview = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val full = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
        val first = runBlocking { fake.context.geometryFor(preview) }
        val second = runBlocking { fake.context.geometryFor(full) }
        val third = runBlocking { fake.context.geometryFor(preview) }
        // Different dims → fresh generation each time; dims match again → reuse.
        assertEquals(false, first === second)
        assertEquals(false, first === third)
        assertEquals(false, second === third)
        assertEquals(3, fake.calls)
        preview.recycle()
        full.recycle()
    }

    @Test
    fun `geometryFor regenerates when the base instance changes even with equal dims`() {
        val fake = FakeContext()
        // The geometry track's "gap changed" signal: a deforming/occluding
        // effect upstream produces a NEW base instance of the same footprint, so
        // the previous geometry (traced from the OLD base) must not be reused.
        val before = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val after = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val first = runBlocking { fake.context.geometryFor(before) }
        val second = runBlocking { fake.context.geometryFor(after) }
        assertEquals(false, first === second)
        assertEquals(2, fake.calls)
        // The SAME instance at the same dims is reused (an unchanged segment).
        val third = runBlocking { fake.context.geometryFor(after) }
        assertSame(second, third)
        assertEquals(2, fake.calls)
        before.recycle()
        after.recycle()
    }
}
