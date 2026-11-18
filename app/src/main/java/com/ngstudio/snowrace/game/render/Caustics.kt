package com.ngstudio.snowrace.game.render

import android.opengl.GLES30
import kotlin.math.sqrt

/**
 * Bakes a tileable Worley "cell edge" texture used for the bright caustic lines on the water.
 * Mipmapped, so the pattern stays clean (no shimmer) in the distance.
 */
object Caustics {
    private const val SIZE = 256
    private const val CELLS = 8

    fun bake(): Int {
        val rnd = java.util.Random(4242)
        val px = FloatArray(CELLS * CELLS)
        val py = FloatArray(CELLS * CELLS)
        for (i in 0 until CELLS * CELLS) { px[i] = 0.1f + rnd.nextFloat() * 0.8f; py[i] = 0.1f + rnd.nextFloat() * 0.8f }
        val bytes = Gl.byteBuffer(SIZE * SIZE)
        val scale = CELLS.toFloat() / SIZE
        for (y in 0 until SIZE) {
            val v = (y + 0.5f) * scale
            val cy = v.toInt()
            for (x in 0 until SIZE) {
                val u = (x + 0.5f) * scale
                val cx = u.toInt()
                var d1 = 99f; var d2 = 99f
                for (oy in -1..1) for (ox in -1..1) {
                    val gx = cx + ox; val gy = cy + oy
                    val wx = ((gx % CELLS) + CELLS) % CELLS
                    val wy = ((gy % CELLS) + CELLS) % CELLS
                    val k = wy * CELLS + wx
                    val dx = gx + px[k] - u; val dy = gy + py[k] - v
                    val d = dx * dx + dy * dy
                    if (d < d1) { d2 = d1; d1 = d } else if (d < d2) d2 = d
                }
                val edge = sqrt(d2) - sqrt(d1)
                val t = (edge / 0.11f).coerceIn(0f, 1f)
                val line = 1f - t * t * (3f - 2f * t)
                bytes.put(y * SIZE + x, (line * 255f + 0.5f).toInt().toByte())
            }
        }
        bytes.position(0)
        val id = Gl.genTexture()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, SIZE, SIZE, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, bytes)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        return id
    }
}
