package codes.pepper.whimsicalart.feature.editor.domain.matting

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class BackgroundReplacerTest {

    private fun solid(w: Int, h: Int, color: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test
    fun should_fill_background_region_with_background_image() {
        val source = solid(8, 8, Color.RED)
        val background = solid(8, 8, Color.BLUE)
        val mask = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            setPixel(1, 1, Color.WHITE)
        }

        val result = BackgroundReplacer.composite(source, mask, background)

        assertEquals("fully-background pixel shows the replacement", Color.BLUE, result.getPixel(0, 0))
    }

    @Test
    fun should_keep_subject_pixels_untouched() {
        val source = solid(8, 8, Color.RED)
        val background = solid(8, 8, Color.BLUE)
        val mask = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            setPixel(1, 1, Color.WHITE)
        }

        val result = BackgroundReplacer.composite(source, mask, background)

        assertEquals("subject pixel keeps the original", Color.RED, result.getPixel(1, 1))
    }

    @Test
    fun should_cover_scale_to_exact_target_size() {
        val background = solid(100, 50, Color.BLUE)

        val result = BackgroundReplacer.coverScale(background, 50, 50)

        assertEquals(50, result.width)
        assertEquals(50, result.height)
    }

    @Test
    fun coverScale_is_memoized_for_an_unchanged_background_and_size() {
        val background = solid(100, 50, Color.BLUE)

        val a = BackgroundReplacer.coverScale(background, 50, 50)
        val b = BackgroundReplacer.coverScale(background, 50, 50)

        assertSame(
            "an identical (background, w, h) cover request must be served from the cache, not rescaled",
            a,
            b
        )
        assertEquals(50, a.width)
        assertEquals(50, a.height)
    }

    @Test
    fun coverScale_recomputes_for_a_different_target_size() {
        val background = solid(100, 50, Color.BLUE)

        val a = BackgroundReplacer.coverScale(background, 50, 50)
        val c = BackgroundReplacer.coverScale(background, 25, 25)

        assertNotSame("a different target size must recompute", a, c)
        assertEquals(25, c.width)
        assertEquals(25, c.height)
    }

    @Test
    fun coverScale_recomputes_for_a_different_background() {
        val blue = solid(100, 50, Color.BLUE)
        val green = solid(100, 50, Color.GREEN)

        val a = BackgroundReplacer.coverScale(blue, 50, 50)
        val b = BackgroundReplacer.coverScale(green, 50, 50)

        assertNotSame("a different background must recompute despite equal dimensions", a, b)
        assertEquals(
            "the green cover must carry the green pixels",
            Color.GREEN,
            b.getPixel(0, 0)
        )
    }

    @Test
    fun coverScale_cache_entry_survives_acid_after_place() {
        val source = solid(8, 8, Color.RED)
        val background = solid(8, 8, Color.BLUE)
        val mask = solid(8, 8, Color.TRANSPARENT)

        // Two placements with an intervening cache hit prove the cached cover is
        // NOT recycled after the first place() returns (the old code recycled it
        // per call, which would break a shared cache entry).
        val first = BackgroundReplacer.place(source, mask, background, BackgroundReplacer.PlacementTransform())
        val cached = BackgroundReplacer.coverScale(background, 8, 8)
        assertEquals("cached cover is still drawable after place()", Color.BLUE, cached.getPixel(0, 0))
        first.recycle()
    }

    @Test
    fun should_support_smaller_background_than_source() {
        val source = solid(100, 100, Color.RED)
        val background = solid(20, 10, Color.BLUE)
        val mask = solid(100, 100, Color.TRANSPARENT)

        val result = BackgroundReplacer.composite(source, mask, background)

        assertEquals(100, result.width)
        assertEquals(100, result.height)
        assertEquals("up-scaled cover background fills even corners", Color.BLUE, result.getPixel(3, 3))
    }

    // --- place(): the placement (pan / scale / rotate) compositor -------------

    @Test
    fun place_default_transform_matches_plain_composite() {
        val source = solid(8, 8, Color.RED)
        val background = solid(8, 8, Color.BLUE)
        val mask = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            setPixel(4, 4, Color.WHITE)
        }

        val result = BackgroundReplacer.place(
            source, mask, background, BackgroundReplacer.PlacementTransform()
        )

        assertEquals(8, result.width)
        assertEquals(8, result.height)
        assertEquals("background region shows the replacement", Color.BLUE, result.getPixel(0, 0))
        assertEquals("subject pixel keeps the original", Color.RED, result.getPixel(4, 4))
    }

    @Test
    fun place_scaled_down_leaves_uncovered_canvas_transparent() {
        val source = solid(8, 8, Color.RED)
        val background = solid(8, 8, Color.BLUE)
        val mask = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            setPixel(4, 4, Color.WHITE)
        }

        val result = BackgroundReplacer.place(
            source, mask, background,
            BackgroundReplacer.PlacementTransform(scale = 0.5f, freeScaleUp = false)
        )

        assertEquals(8, result.width)
        assertEquals(8, result.height)
        // The background stays a static cover-fill over the whole source area, so
        // a scaled-down subject leaves NO transparent gaps — the cover fills in.
        assertEquals("the static background cover fills the non-subject area", Color.BLUE, result.getPixel(1, 1))
        assertEquals("the scaled subject keeps its pixels at the centre", Color.RED, result.getPixel(4, 4))
    }

    @Test
    fun place_scaled_up_grows_the_canvas_to_fit() {
        val source = solid(8, 8, Color.RED)
        val background = solid(8, 8, Color.BLUE)
        val mask = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            setPixel(4, 4, Color.WHITE)
        }

        val result = BackgroundReplacer.place(
            source, mask, background,
            BackgroundReplacer.PlacementTransform(scale = 2f, freeScaleUp = true)
        )

        assertEquals("committed canvas grows to 2x to fit the transformed subject", 16, result.width)
        assertEquals(16, result.height)
        // Overflow beyond the original source area (which the static cover does
        // not reach) is transparent — the 2x subject is centred in the grown box.
        assertEquals(
            "overflow beyond the original canvas is transparent",
            0,
            Color.alpha(result.getPixel(0, 0))
        )
        // The subject's centre pixel lands at the centre of the grown canvas.
        assertEquals("the grown subject keeps its pixels at the canvas centre", Color.RED, result.getPixel(8, 8))
    }

    @Test
    fun place_rotation_grows_canvas_and_leaves_corners_transparent() {
        val source = solid(8, 8, Color.RED)
        val background = solid(8, 8, Color.BLUE)
        val mask = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            setPixel(4, 4, Color.WHITE)
        }

        val result = BackgroundReplacer.place(
            source, mask, background,
            BackgroundReplacer.PlacementTransform(scale = 1f, rotationDegrees = 45f, freeScaleUp = true)
        )

        // 8·(cos45 + sin45) ≈ 11.31 → union with the source → ceiling of 12.
        assertEquals(12, result.width)
        assertEquals(12, result.height)
        assertEquals(
            "the rotated subject's bounding box leaves the output corner transparent",
            0,
            Color.alpha(result.getPixel(0, 0))
        )
        assertEquals("the rotated subject's centre stays at the canvas centre", Color.RED, result.getPixel(6, 6))
    }

    @Test
    fun place_pan_moves_the_subject_while_the_background_stays() {
        val source = solid(8, 8, Color.RED)
        val background = solid(8, 8, Color.BLUE)
        val mask = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            setPixel(4, 4, Color.WHITE)
        }

        // panX = 0.25 moves the destination centre from x=4 to x=6, so the
        // subject pixel at (4,4) slides to (6,4); the backdrop stays static.
        val result = BackgroundReplacer.place(
            source, mask, background,
            BackgroundReplacer.PlacementTransform(scale = 1f, panX = 0.25f, panY = 0f, freeScaleUp = true)
        )

        assertEquals("panned subject pixel translates with the pan", Color.RED, result.getPixel(6, 4))
        assertEquals(
            "the spot the subject left shows the static background cover",
            Color.BLUE,
            result.getPixel(4, 4)
        )
        assertEquals("the untouched far side remains the background", Color.BLUE, result.getPixel(0, 0))
    }

    @Test
    fun place_transform_with_smaller_mask_never_reads_out_of_bounds() {
        // Regression for the full-size fold swallowing reports: the mask (and its
        // soft-edge band) live at ML resolution — smaller than the folded base.
        // The transform path used to index the edge band with SOURCE-space
        // coordinates (`edge[sj * maskW + si]`), which threw an
        // ArrayIndexOutOfBoundsException once the projected point passed the mask
        // edge, and requestFullFold's runCatching parked the canvas on the
        // preview image forever. Sampling must map source → mask like the fast
        // path and the blur processor.
        val source = solid(8, 8, Color.RED)
        val background = solid(8, 8, Color.BLUE)
        val mask = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }

        val result = BackgroundReplacer.place(
            source, mask, background,
            BackgroundReplacer.PlacementTransform(scale = 1f, panX = 0.25f, panY = 0f, freeScaleUp = true),
            softEdgeWidth = SoftEdgeBand.DEFAULT_WIDTH
        )

        assertEquals("a projected point inside the source but past the mask maps to the mark", Color.RED, result.getPixel(6, 4))
        assertEquals("unmarked canvas takes the static background cover", Color.BLUE, result.getPixel(0, 0))
    }

    @Test
    fun place_negative_fractional_projection_never_throws() {
        // Regression for the full-size fold swallowing reports (second OOB
        // hazard): a grown/rotated placement's edge destination pixels project
        // just OUTSIDE the source — e.g. `su` lands in `[-1, 0)`. Guarding on
        // `su.toInt() in 0 until w` is not enough: `(-0.5f).toInt()` truncates to
        // 0, the guard passes, and [sampleSubject]'s `floor(-0.5)` → -1 reads
        // `sourcePx[y * srcW - 1]` → OOB. The guard must be FLOAT-based so any
        // negative fraction stays out. Full-size folds keep the capped ML mask
        // smaller than the base, so the mask/edge arrays live at mask resolution
        // too and must be indexed through the same source → mask mapping.
        val source = solid(200, 150, Color.RED)
        val background = solid(200, 150, Color.BLUE)
        val mask = Bitmap.createBitmap(100, 75, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }

        val result = BackgroundReplacer.place(
            source, mask, background,
            BackgroundReplacer.PlacementTransform(scale = 1f, rotationDegrees = 15f, freeScaleUp = true),
            softEdgeWidth = 4
        )

        assertTrue("a rotated placement grows the canvas", result.width > 200)
        assertTrue("the grown canvas is red inside the source region", Color.red(result.getPixel(100, 75)) in 0..255)
    }

    @Test
    fun place_rotation_turns_the_subject_keeping_the_backdrop_axis_aligned() {
        val source = solid(8, 8, Color.RED)
        val background = solid(8, 8, Color.BLUE)
        val mask = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            // Mark both the centre and an off-centre subject pixel so the 180°
            // rotation (about the canvas centre) is provably the subject turning.
            setPixel(4, 4, Color.WHITE)
            setPixel(6, 2, Color.WHITE)
        }

        val result = BackgroundReplacer.place(
            source, mask, background,
            BackgroundReplacer.PlacementTransform(scale = 1f, rotationDegrees = 180f, freeScaleUp = true)
        )

        assertEquals(8, result.width)
        assertEquals(8, result.height)
        // (6,2) about the centre (4,4) rotated 180° maps exactly to (2,6).
        assertEquals("the subject point (6,2) rotates to (2,6)", Color.RED, result.getPixel(2, 6))
        assertEquals(
            "the original subject location is now the static background cover",
            Color.BLUE,
            result.getPixel(6, 2)
        )
        assertEquals("the centred subject stays put", Color.RED, result.getPixel(4, 4))
    }

    @Test
    fun placed_canvas_size_stays_put_for_a_within_fit_placement() {
        assertEquals(8 to 8, BackgroundReplacer.placedCanvasSize(8, 8, 1f, 0f))
        assertEquals(8 to 4, BackgroundReplacer.placedCanvasSize(8, 4, 1f, 0f))
        assertEquals(8 to 8, BackgroundReplacer.placedCanvasSize(8, 8, 0.5f, 0f))
    }


    @Test
    fun should_fade_the_replace_a_few_pixels_inward_from_the_boundary() {
        val source = solid(16, 8, Color.BLACK)
        val background = solid(16, 8, Color.BLUE)
        val mask = Bitmap.createBitmap(16, 8, Bitmap.Config.ARGB_8888).apply {
            val px = IntArray(16 * 8)
            for (y in 0 until 8) {
                for (x in 0 until 16) {
                    px[y * 16 + x] = if (x < 8) Color.WHITE else Color.TRANSPARENT
                }
            }
            setPixels(px, 0, 16, 0, 0, 16, 8)
        }

        val result = BackgroundReplacer.composite(source, mask, background, softEdgeWidth = 4)

        val blue = { x: Int -> Color.blue(result.getPixel(x, 4)) }
        assertEquals("outside the mask is the pure backdrop", 255, blue(8))
        assertEquals("subject core stays the pure source", 0, blue(3))
        assertTrue(
            "the band carries a monotonic source<-backdrop ramp (b7..b4=${(4..7).map(blue)})",
            blue(7) > blue(6) && blue(6) > blue(5) && blue(5) > blue(4) && blue(4) > 0
        )
    }

    @Test
    fun place_default_fades_inward_like_composite() {
        val source = solid(16, 8, Color.BLACK)
        val background = solid(16, 8, Color.BLUE)
        val mask = Bitmap.createBitmap(16, 8, Bitmap.Config.ARGB_8888).apply {
            val px = IntArray(16 * 8)
            for (y in 0 until 8) {
                for (x in 0 until 16) {
                    px[y * 16 + x] = if (x < 8) Color.WHITE else Color.TRANSPARENT
                }
            }
            setPixels(px, 0, 16, 0, 0, 16, 8)
        }

        val result = BackgroundReplacer.place(
            source,
            mask,
            background,
            BackgroundReplacer.PlacementTransform(),
            softEdgeWidth = 4
        )

        assertEquals("outside the mask is the pure backdrop", Color.BLUE, result.getPixel(8, 4))
        assertEquals("subject core stays the pure source", Color.BLACK, result.getPixel(3, 4))
        assertTrue("band pixel is a mix, not pure", Color.blue(result.getPixel(6, 4)) in 1..254)
    }

    // --- size independence: a full-res mask on a preview-res bitmap must match
    //     a mask at the bitmap's own resolution -------------------------------

    /** A [w]×[w] transparent mask with a single white subject pixel at [sx],[sy]. */
    private fun pointMask(w: Int, sx: Int, sy: Int): Bitmap =
        Bitmap.createBitmap(w, w, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            setPixel(sx, sy, Color.WHITE)
        }

    @Test
    fun composite_is_size_independent_between_matching_and_full_res_masks() {
        val source = solid(4, 4, Color.RED)
        val background = solid(4, 4, Color.BLUE)
        // Subject at source (1,1): in a 4×4 mask that is (1,1); in an 8×8 mask it
        // is (1*8/4, 1*8/4) = (2,2).
        val matching = pointMask(4, 1, 1)
        val fullRes = pointMask(8, 2, 2)

        val a = BackgroundReplacer.composite(source, matching, background)
        val b = BackgroundReplacer.composite(source, fullRes, background)

        for (y in 0 until 4) for (x in 0 until 4) {
            assertEquals("pixel ($x,$y) matches across mask resolutions", a.getPixel(x, y), b.getPixel(x, y))
        }
        assertEquals("subject pixel stays", Color.RED, a.getPixel(1, 1))
        assertEquals("background pixel is replaced", Color.BLUE, a.getPixel(0, 0))
    }

    @Test
    fun place_fast_path_is_size_independent_for_full_res_mask() {
        val source = solid(4, 4, Color.RED)
        val background = solid(4, 4, Color.BLUE)
        val matching = pointMask(4, 1, 1)
        val fullRes = pointMask(8, 2, 2)

        val a = BackgroundReplacer.place(
            source, matching, background, BackgroundReplacer.PlacementTransform()
        )
        val b = BackgroundReplacer.place(
            source, fullRes, background, BackgroundReplacer.PlacementTransform()
        )

        for (y in 0 until 4) for (x in 0 until 4) {
            assertEquals("pixel ($x,$y) matches across mask resolutions", a.getPixel(x, y), b.getPixel(x, y))
        }
        assertEquals("subject pixel stays", Color.RED, a.getPixel(1, 1))
        assertEquals("background pixel is replaced", Color.BLUE, a.getPixel(0, 0))
    }

    @Test
    fun place_pan_is_size_independent_for_full_res_mask() {
        val source = solid(4, 4, Color.RED)
        val background = solid(4, 4, Color.BLUE)
        val matching = pointMask(4, 1, 1)
        val fullRes = pointMask(8, 2, 2)
        val transform = BackgroundReplacer.PlacementTransform(scale = 1f, panX = 0.25f, freeScaleUp = true)

        val a = BackgroundReplacer.place(source, matching, background, transform)
        val b = BackgroundReplacer.place(source, fullRes, background, transform)

        for (y in 0 until 4) for (x in 0 until 4) {
            assertEquals("panned pixel ($x,$y) matches across mask resolutions", a.getPixel(x, y), b.getPixel(x, y))
        }
    }

    @Test
    fun composite_leaves_no_original_background_outside_the_mask() {
        val sourceColor = Color.RED
        val replacementColor = Color.GREEN
        val source = solid(8, 8, sourceColor)
        val background = solid(8, 8, replacementColor)
        val mask = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            for (y in 0..1) for (x in 0..1) setPixel(x, y, Color.WHITE)
        }

        val result = BackgroundReplacer.composite(source, mask, background)

        for (y in 0 until 8) for (x in 0 until 8) {
            val isSubject = x <= 1 && y <= 1
            if (isSubject) {
                assertEquals("subject pixel keeps the original", sourceColor, result.getPixel(x, y))
            } else {
                assertEquals(
                    "outside the mask the original background is fully gone",
                    replacementColor,
                    result.getPixel(x, y)
                )
            }
        }
    }

    @Test
    fun composite_feathers_the_boundary_band() {
        val source = solid(8, 8, Color.RED)
        val background = solid(8, 8, Color.BLUE)
        val mask = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            // Boundary alpha in the 110..255 band must alpha-blend, not hard-switch.
            setPixel(4, 4, Color.argb(180, 255, 255, 255))
        }

        val result = BackgroundReplacer.composite(source, mask, background)

        val px = result.getPixel(4, 4)
        assertTrue("feathered pixel is not the pure subject", px != Color.RED)
        assertTrue("feathered pixel is not the pure background", px != Color.BLUE)
        assertEquals("a fully-background pixel is replaced outright", Color.BLUE, result.getPixel(0, 0))
    }
}
