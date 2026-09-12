package codes.pepper.whimsicalart.feature.editor.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import androidx.core.content.ContextCompat
import codes.pepper.whimsicalart.feature.stickers.domain.StickerItem
import codes.pepper.whimsicalart.feature.stickers.domain.StickerLineStyle
import codes.pepper.whimsicalart.feature.stickers.ui.DEFAULT_LINE_COLOUR
import java.util.LinkedHashMap
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Committed-pass renderer for the Stickers tool (docs/effects_reference.md →
 * *Stickers*), extracted from [BitmapRenderer] so each overlay drawing family
 * owns its raster logic. One transform block for every sticker kind
 * (rotate/flip about the box centre, `translate` + positioned drawBitmap rather
 * than a Matrix — identical output on-device, and the rotate/translate form is
 * what rasterizes deterministically on every backend, including the Robolectric
 * software canvas used by the parity tests). Draw math is unchanged from
 * `BitmapRenderer.drawStickers`.
 */
internal object StickerOverlayRenderer {

    internal fun drawStickers(context: Context, canvas: Bitmap, stickers: List<StickerItem>) {
        if (stickers.isEmpty()) return
        val bitmap = canvas
        val draw = android.graphics.Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (s in stickers) {
            val w = (s.widthFrac * bitmap.width).roundToInt().coerceAtLeast(1)
            val h = (s.heightFrac * bitmap.height).roundToInt().coerceAtLeast(1)
            val cx = s.x * bitmap.width
            val cy = s.y * bitmap.height
            val left = cx - w / 2f
            val top = cy - h / 2f
            paint.alpha = (s.opacity * 255f).roundToInt().coerceIn(0, 255)
            paint.colorFilter = s.tintColor?.let { PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN) }

            val emojiText = s.emojiText
            val source: Bitmap
            var drawX = 0f
            var drawY = 0f
            // Decode and scale the sticker ART once per (sticker, box size): a
            // fold iterates the same placements over and over, and for gallery
            // stickers that meant re-decoding the content URI + resampling on
            // EVERY fold. Emoji glyphs and drawable/photo sources also rasterised
            // from scratch each fold. The art bitmap depends only on the sticker
            // identity and the box size (tint/opacity/rotation/flip are applied at
            // draw time below), so unchanged stickers recomposite the cached art.
            val cacheKey = "${s.stickerId}|$w|$h"
            val cachedArt = stickerArtCache.remove(cacheKey)
            if (cachedArt != null) {
                stickerArtCache[cacheKey] = cachedArt
                source = cachedArt.bitmap
                drawX = (w - source.width) / 2f
                drawY = (h - source.height) / 2f
            } else {
                if (emojiText != null) {
                    // Shared glyph renderer: the exact same Paint path and fit math
                    // used by the live preview, so the emoji fills its sticker square
                    // and is never cropped on any side, identically in both
                    // (spec: "Emoji rendering"). The glyph bitmap is a square of side
                    // w (the sticker keeps square glyph cells even in a w×h box) and
                    // is centred in the box.
                    val sidePx = w.coerceAtLeast(1)
                    source = renderEmojiGlyph(emojiText, sidePx)
                    drawX = (w - sidePx) / 2f
                    drawY = (h - sidePx) / 2f
                } else if (s.imageUri != null) {
                    // Gallery sticker: the picked photo is decoded from its content URI
                    // (bounded to the sticker's own dimensions, two-phase sample like
                    // the editor's raw decode, EXIF-oriented) and resampled into the
                    // sticker box with a PROPORTIONAL FIT of the source's own ratio —
                    // the box carries the intrinsic aspect (spec "Gallery stickers keep
                    // the original image's aspect ratio"), and a fit also guarantees no
                    // distortion or centre-crop even if that ratio drifts a rounding
                    // pixel. Non-custom-colour, so no tint.
                    val uriBitmap = runCatching {
                        Uri.parse(s.imageUri)
                    }.getOrNull()?.let { uri ->
                        decodeBoundedOriented(context.contentResolver, uri, maxDim = maxOf(w, h))
                    } ?: continue
                    val fit = min(w.toFloat() / uriBitmap.width, h.toFloat() / uriBitmap.height)
                    val sw = (uriBitmap.width * fit).roundToInt().coerceAtLeast(1)
                    val sh = (uriBitmap.height * fit).roundToInt().coerceAtLeast(1)
                    source = if (sw != uriBitmap.width || sh != uriBitmap.height) {
                        val scaled = Bitmap.createScaledBitmap(uriBitmap, sw, sh, true)
                        if (scaled !== uriBitmap) uriBitmap.recycle()
                        scaled
                    } else {
                        uriBitmap
                    }
                    drawX = (w - sw) / 2f
                    drawY = (h - sh) / 2f
                } else {
                    val drawable = s.drawableRes?.let { ContextCompat.getDrawable(context, it) } ?: continue
                    source = asBitmap(drawable, w, h)
                }
                stickerDecodeCount++
                putStickerArt(cacheKey, source)
            }
            // The living cache entry for `source` (hit: the refreshed hit; miss:
            // the freshly added one), so derived artefacts cached alongside the
            // art share its key and lifetime.
            val art = cachedArt ?: stickerArtCache[cacheKey]

            draw.save()
            draw.translate(left, top)
            draw.rotate(s.rotationDegrees, w / 2f, h / 2f)
            if (s.isFlipped) draw.scale(-1f, 1f, w / 2f, h / 2f)
            draw.drawBitmap(source, drawX, drawY, paint)
            // The silhouette line (colour-enabled stickers; guard cleanly for any
            // sticker that carries one): stroked along the art's alpha contour in
            // the SAME rotation/flip frame as the fill, fill first then line. A
            // null colour resolves to black (the default line colour); a None style or
            // zero width draws nothing.
            if (s.lineStyle != StickerLineStyle.NONE) {
                val lineColor = s.lineColor ?: DEFAULT_LINE_COLOUR
                val lineWidth = s.lineThickness.coerceIn(0f, 1f) * min(w, h)
                val linePaint = lineColor.let { stickerLinePaint(s.lineStyle, lineWidth, it, paint.alpha) }
                if (linePaint != null) {
                    // The contour depends only on the art identity + box size (the
                    // same key that gates the art bitmap), so it is cached with the
                    // art and traced at most once per key — not re-scanned on every
                    // fold (STICKER_CONTOUR_MAX_DIM lattice walk per draw).
                    val contour = art?.silhouettePath ?: stickerSilhouettePath(source, source.width, source.height).also {
                        art?.silhouettePath = it
                        stickerContourCount++
                    }
                    draw.save()
                    draw.translate(drawX, drawY)
                    draw.drawPath(contour, linePaint)
                    draw.restore()
                }
            }
            draw.restore()
        }
        paint.colorFilter = null
    }

    internal fun asBitmap(drawable: android.graphics.drawable.Drawable, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bmp
    }

    /*
     * Bounded LRU of decoded+scaled sticker art bitmaps, keyed by
     * `stickerId|boxW|boxH`. Same identity + box size reuses the art; any
     * placement change that grows/shrinks the box (or resets the sticker) is a
     * fresh key and re-decodes.
     */
    private class CachedStickerArt(val bitmap: Bitmap, val bytes: Int) {
        /**
         * Lazily-traced silhouette contour ([stickerSilhouettePath] at the art's
         * own size), cached alongside the art because the contour depends on
         * exactly the same inputs as the art key (art identity + box size).
         * Computed once on the first line-style draw and reused on later folds.
         */
        var silhouettePath: Path? = null
    }

    private const val MAX_CACHE_BYTES = 16 * 1024 * 1024

    private val stickerArtCache = LinkedHashMap<String, CachedStickerArt>(32, 0.75f, true)

    private var stickerCacheBytes = 0L

    /** Sticker-art decodes/rasters since the last [clearStickerCache]; internal for tests. */
    internal var stickerDecodeCount: Int = 0
        private set

    /** Silhouette-contour traces ([stickerSilhouettePath]) fired by [drawStickers]; internal for tests. */
    internal var stickerContourCount: Int = 0
        private set

    private fun putStickerArt(key: String, bitmap: Bitmap) {
        stickerArtCache[key] = CachedStickerArt(bitmap, bitmap.byteCount)
        stickerCacheBytes += bitmap.byteCount
        while (stickerCacheBytes > MAX_CACHE_BYTES && stickerArtCache.isNotEmpty()) {
            val eldest = stickerArtCache.entries.iterator().next()
            stickerCacheBytes -= eldest.value.bytes
            eldest.value.bitmap.recycle()
            stickerArtCache.remove(eldest.key)
        }
    }

    /** Drops every cached sticker art (frees the bitmaps); internal for tests. */
    internal fun clearStickerCache() {
        for (entry in stickerArtCache.values) entry.bitmap.recycle()
        stickerArtCache.clear()
        stickerCacheBytes = 0L
        stickerDecodeCount = 0
        stickerContourCount = 0
    }
}