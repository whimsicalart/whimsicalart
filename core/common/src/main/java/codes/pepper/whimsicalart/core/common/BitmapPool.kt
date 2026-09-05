package codes.pepper.whimsicalart.core.common

import android.graphics.Bitmap
import android.graphics.Color
import android.util.LruCache

/**
 * Cooperative pool for reusable [Bitmap]s of a fixed output size.
 *
 * Bitmaps held by the pool are live, mutable and idle (not recycled). Callers
 * requesting a [get] receive a blank, cleared bitmap of the requested size that
 * they must fully redraw before use. When a bitmap is no longer needed, return it
 * with [put] so it can be reused on a later pass, avoiding a fresh native
 * allocation. [clear] (or LRU eviction) recycles pooled bitmaps to release native
 * memory.
 *
 * Capacity is bounded both by a maximum pooled entry count and an optional
 * configured cache size, so long-lived pins in undo history or saved results are
 * never pooled accidentally by callers.
 */
object BitmapPool {

    private const val DEFAULT_TARGET_POOL_SIZE = 2
    private const val MAX_POOLED_BITMAPS = 2

    private var targetPoolSize = DEFAULT_TARGET_POOL_SIZE

    private val pool = object : LruCache<String, Bitmap>(MAX_POOLED_BITMAPS) {
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            if (oldValue != newValue) {
                oldValue.recycle()
            }
        }
    }

    private val lock = Any()

    fun configureTargetPoolSize(size: Int) {
        synchronized(lock) {
            targetPoolSize = size.coerceAtLeast(1)
        }
    }

    /**
     * Returns a reusable, cleared [Bitmap] of the given size and config, or null
     * when no matching entry is cached (caller should allocate a fresh bitmap).
     */
    fun get(width: Int, height: Int, config: Bitmap.Config): Bitmap? {
        synchronized(lock) {
            val key = keyOf(width, height, config)
            val bitmap = pool.remove(key) ?: return null
            bitmap.eraseColor(Color.TRANSPARENT)
            return bitmap
        }
    }

    /**
     * Returns [bitmap] to the pool. The bitmap must be mutable, not recycled, and
     * no longer referenced by the caller. Incompatible bitmaps are recycled and
     * not cached.
     */
    fun put(bitmap: Bitmap) {
        val config = bitmap.config
        if (bitmap.isRecycled || !bitmap.isMutable || config == Bitmap.Config.HARDWARE) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
        if (config == null) {
            bitmap.recycle()
            return
        }
        val key = keyOf(bitmap.width, bitmap.height, config)
        synchronized(lock) {
            if (pool.size() >= targetPoolSize) {
                bitmap.recycle()
                return
            }
            bitmap.eraseColor(Color.TRANSPARENT)
            pool.put(key, bitmap)
        }
    }

    /** Recycles and clears all pooled bitmaps, releasing native memory. */
    fun clear() {
        synchronized(lock) {
            pool.evictAll()
        }
    }

    fun size(): Int = pool.size()

    private fun keyOf(
        width: Int,
        height: Int,
        config: Bitmap.Config
    ): String = "$width x $height x ${config.name}"
}
