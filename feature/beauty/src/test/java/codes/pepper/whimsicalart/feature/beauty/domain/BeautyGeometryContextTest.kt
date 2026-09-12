package codes.pepper.whimsicalart.feature.beauty.domain

import android.graphics.Bitmap
import android.graphics.Path
import codes.pepper.whimsicalart.feature.beauty.detection.FaceDetectionResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BeautyGeometryContextTest {

    /**
     * A context whose generator records WHICH fields each generate was asked for
     * and returns a partial geometry populated only for the requested fields —
     * so the per-field lazy contract is observable (each requested field yields a
     * fresh instance; the merge path carries the earlier instances through).
     */
    private class FakeContext {
        var calls = 0
        val requested = mutableListOf<Set<GeometryField>>()
        val context = DefaultBeautyGeometryContext(
            generator = BeautyGeometryGenerator { _: Bitmap, _: Int?, needed: Set<GeometryField> ->
                calls++
                requested += needed
                BeautyGeometry(
                    faceResult = if (GeometryField.FACE in needed) {
                        FaceDetectionResult(faces = emptyList(), imageWidth = 1, imageHeight = 1)
                    } else {
                        null
                    },
                    skinPath = if (GeometryField.SKIN in needed) Path() else null,
                    hairPath = if (GeometryField.HAIR in needed) Path() else null,
                    subjectMask = if (GeometryField.SUBJECT_MASK in needed) {
                        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                    } else {
                        null
                    }
                )
            }
        )
    }

    @Test
    fun `full resolve lazily generates once and reuses the last geometry until stale`() {
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
        // Stale → new geometry generated (stale cleared).
        assertEquals(false, before === after)
        assertEquals(2, fake.calls)
        assertEquals(after, fake.context.lastGeometry())
        image.recycle()
    }

    @Test
    fun `geometryFor returns last and cleared stale stays cleared after generating`() {
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

    @Test
    fun `per-field request resolves only the wanted field and never the others`() {
        val fake = FakeContext()
        val image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val geometry = runBlocking { fake.context.geometryFor(image, null, setOf(GeometryField.FACE)) }
        // A face-only effect list requests ONLY FACE — the hair/skin segmenters
        // never run for it.
        assertEquals(listOf(setOf(GeometryField.FACE)), fake.requested)
        assertTrue(geometry.faceResult != null)
        assertEquals(null, geometry.hairPath)
        assertEquals(null, geometry.skinPath)
        image.recycle()
    }

    @Test
    fun `adding a missing field computes only that field against the same base and merges`() {
        val fake = FakeContext()
        val image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val first = runBlocking {
            fake.context.geometryFor(image, null, setOf(GeometryField.FACE, GeometryField.SKIN))
        }
        val face = first.faceResult
        val skin = first.skinPath
        val second = runBlocking {
            fake.context.geometryFor(image, null, setOf(GeometryField.FACE, GeometryField.SKIN, GeometryField.HAIR))
        }
        // Same base + extra field → ONLY HAIR re-resolved (no full re-resolve);
        // the existing face result and skin path are carried through.
        assertEquals(2, fake.calls)
        assertEquals(
            listOf(setOf(GeometryField.FACE, GeometryField.SKIN), setOf(GeometryField.HAIR)),
            fake.requested
        )
        assertSame(face, second.faceResult)
        assertSame(skin, second.skinPath)
        assertTrue(second.hairPath != null)
        image.recycle()
    }

    @Test
    fun `SUBJECT_MASK resolves on demand and is reused for a same-base mask-only request`() {
        val fake = FakeContext()
        val image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val mask = runBlocking {
            fake.context.geometryFor(image, null, setOf(GeometryField.FACE, GeometryField.SUBJECT_MASK))
        }.subjectMask
        assertTrue(mask != null)
        // Re-entering the background tool after an unchanged stack: the retained
        // geometry already carries the mask — no re-segmentation.
        val again = runBlocking {
            fake.context.geometryFor(image, null, setOf(GeometryField.SUBJECT_MASK))
        }
        assertSame(mask, again.subjectMask)
        assertEquals(1, fake.calls)
        image.recycle()
        mask!!.recycle()
    }

    @Test
    fun `empty needed fields resolve no ML stages and yield an empty geometry`() {
        val fake = FakeContext()
        val image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val pen = runBlocking { fake.context.geometryFor(image, null, emptySet()) }
        // The manual pen requests no geometry at all — nothing ML runs, the
        // result is an empty value the pen simply does not consume.
        assertEquals(1, fake.calls)
        assertEquals(listOf(emptySet<GeometryField>()), fake.requested)
        assertEquals(null, pen.faceResult)
        assertEquals(null, pen.hairPath)
        assertEquals(null, pen.skinPath)
        assertEquals(null, pen.subjectMask)
        image.recycle()
    }

    @Test
    fun `markStale never recycles the caller-owned base bitmap`() {
        val fake = FakeContext()
        val image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        runBlocking { fake.context.geometryFor(image, null, setOf(GeometryField.FACE)) }
        fake.context.markStale()
        // The base is geometry-track/caller-owned — the context only drops its
        // reference (the owner, under geometryTrackLock, does the recycling), so
        // a concurrent fold never observes a recycled retained base.
        assertEquals(false, image.isRecycled)
        image.recycle()
    }

    @Test
    fun `flatten settles the retained base but keeps the last geometry for re-edit reuse`() {
        val fake = FakeContext()
        val image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val before = runBlocking { fake.context.geometryFor(image, null, setOf(GeometryField.FACE)) }
        fake.context.flatten()
        // The merged layer re-uses the last geometry on re-edit...
        assertSame(before, fake.context.lastGeometry())
        // ...but the retained base was released: a next request on the SAME base
        // instance can no longer incrementally resolve — it regenerates.
        val after = runBlocking { fake.context.geometryFor(image, null, setOf(GeometryField.FACE)) }
        assertEquals(2, fake.calls)
        assertEquals(false, before === after)
        image.recycle()
    }
}