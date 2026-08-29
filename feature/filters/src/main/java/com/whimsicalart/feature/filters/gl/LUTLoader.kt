package com.whimsicalart.feature.filters.gl

import android.content.Context
import android.graphics.BitmapFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

data class LUT3D(
    val size: Int,
    val data: FloatBuffer,
    val textureId: Int
)

class LUTLoader(private val context: Context) {

    fun loadLUT(resourceId: Int, size: Int = 64): LUT3D {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeResource(context.resources, resourceId, options)

        val width = bitmap.width
        val height = bitmap.height

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()

        val totalEntries = size * size * size
        val buffer = ByteBuffer.allocateDirect(totalEntries * 3 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        var index = 0
        for (z in 0 until size) {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val pixelIndex = (y * width) + x + (z * height * width)
                    if (pixelIndex < pixels.size) {
                        val pixel = pixels[pixelIndex]
                        val r = ((pixel shr 16) and 0xFF) / 255.0f
                        val g = ((pixel shr 8) and 0xFF) / 255.0f
                        val b = (pixel and 0xFF) / 255.0f
                        buffer.put(index++, r)
                        buffer.put(index++, g)
                        buffer.put(index++, b)
                    }
                }
            }
        }

        buffer.position(0)

        val textureIds = IntArray(1)
        android.opengl.GLES30.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]

        android.opengl.GLES30.glBindTexture(android.opengl.GLES30.GL_TEXTURE_3D, textureId)
        android.opengl.GLES30.glTexParameteri(
            android.opengl.GLES30.GL_TEXTURE_3D,
            android.opengl.GLES30.GL_TEXTURE_MIN_FILTER,
            android.opengl.GLES30.GL_LINEAR
        )
        android.opengl.GLES30.glTexParameteri(
            android.opengl.GLES30.GL_TEXTURE_3D,
            android.opengl.GLES30.GL_TEXTURE_MAG_FILTER,
            android.opengl.GLES30.GL_LINEAR
        )
        android.opengl.GLES30.glTexParameteri(
            android.opengl.GLES30.GL_TEXTURE_3D,
            android.opengl.GLES30.GL_TEXTURE_WRAP_S,
            android.opengl.GLES30.GL_CLAMP_TO_EDGE
        )
        android.opengl.GLES30.glTexParameteri(
            android.opengl.GLES30.GL_TEXTURE_3D,
            android.opengl.GLES30.GL_TEXTURE_WRAP_T,
            android.opengl.GLES30.GL_CLAMP_TO_EDGE
        )
        android.opengl.GLES30.glTexParameteri(
            android.opengl.GLES30.GL_TEXTURE_3D,
            android.opengl.GLES30.GL_TEXTURE_WRAP_R,
            android.opengl.GLES30.GL_CLAMP_TO_EDGE
        )

        android.opengl.GLES30.glTexImage3D(
            android.opengl.GLES30.GL_TEXTURE_3D, 0, android.opengl.GLES30.GL_RGB,
            size, size, size, 0,
            android.opengl.GLES30.GL_RGB, android.opengl.GLES30.GL_FLOAT, buffer
        )

        return LUT3D(
            size = size,
            data = buffer,
            textureId = textureId
        )
    }

    companion object {
        private const val LUT_SIZE = 64

        fun trilinearInterpolate(
            lut: LUT3D,
            r: Float,
            g: Float,
            b: Float
        ): Triple<Float, Float, Float> {
            val x = (r * (lut.size - 1)).coerceIn(0f, (lut.size - 1).toFloat())
            val y = (g * (lut.size - 1)).coerceIn(0f, (lut.size - 1).toFloat())
            val z = (b * (lut.size - 1)).coerceIn(0f, (lut.size - 1).toFloat())

            val x0 = x.toInt()
            val y0 = y.toInt()
            val z0 = z.toInt()
            val x1 = (x0 + 1).coerceAtMost(lut.size - 1)
            val y1 = (y0 + 1).coerceAtMost(lut.size - 1)
            val z1 = (z0 + 1).coerceAtMost(lut.size - 1)

            val fx = x - x0
            val fy = y - y0
            val fz = z - z0

            val getLUT = { lx: Int, ly: Int, lz: Int ->
                val idx = (lz * lut.size * lut.size + ly * lut.size + lx) * 3
                Triple(
                    lut.data.get(idx),
                    lut.data.get(idx + 1),
                    lut.data.get(idx + 2)
                )
            }

            val c000 = getLUT(x0, y0, z0)
            val c001 = getLUT(x0, y0, z1)
            val c010 = getLUT(x0, y1, z0)
            val c011 = getLUT(x0, y1, z1)
            val c100 = getLUT(x1, y0, z0)
            val c101 = getLUT(x1, y0, z1)
            val c110 = getLUT(x1, y1, z0)
            val c111 = getLUT(x1, y1, z1)

            val rResult = lerp(
                lerp(lerp(c000.first, c100.first, fx), lerp(c010.first, c110.first, fx), fy),
                lerp(lerp(c001.first, c101.first, fx), lerp(c011.first, c111.first, fx), fy),
                fz
            )

            val gResult = lerp(
                lerp(lerp(c000.second, c100.second, fx), lerp(c010.second, c110.second, fx), fy),
                lerp(lerp(c001.second, c101.second, fx), lerp(c011.second, c111.second, fx), fy),
                fz
            )

            val bResult = lerp(
                lerp(lerp(c000.third, c100.third, fx), lerp(c010.third, c110.third, fx), fy),
                lerp(lerp(c001.third, c101.third, fx), lerp(c011.third, c111.third, fx), fy),
                fz
            )

            return Triple(rResult, gResult, bResult)
        }

        private fun lerp(a: Float, b: Float, t: Float): Float {
            return a + (b - a) * t
        }
    }
}
