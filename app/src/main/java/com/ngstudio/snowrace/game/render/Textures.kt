package com.ngstudio.snowrace.game.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLUtils
import androidx.core.content.res.ResourcesCompat
import com.ngstudio.snowrace.R
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin

object TextureUtil {
    fun fromBitmap(bmp: Bitmap, mipmap: Boolean = true, repeat: Boolean = false): Int {
        val id = Gl.genTexture()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, if (mipmap) GLES30.GL_LINEAR_MIPMAP_LINEAR else GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        val wrap = if (repeat) GLES30.GL_REPEAT else GLES30.GL_CLAMP_TO_EDGE
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrap)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrap)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        if (mipmap) GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        return id
    }

    /** Single-channel texture (R8), linear filtered, clamped. */
    fun r8(width: Int, height: Int, data: ByteBuffer?): Int {
        val id = Gl.genTexture()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, width, height, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, data)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return id
    }

    fun updateR8Rows(id: Int, width: Int, row0: Int, rows: Int, data: ByteBuffer) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, row0, width, rows, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, data)
    }

    fun delete(id: Int) { if (id != 0) GLES30.glDeleteTextures(1, intArrayOf(id), 0) }
}

/** UV rectangle inside an atlas (u0,v0 top-left; u1,v1 bottom-right) plus aspect ratio (w/h). */
class UvRect(val u0: Float, val v0: Float, val u1: Float, val v1: Float, val aspect: Float = 1f)

/** Procedurally drawn particle sprites (white shapes, tinted per particle). */
class ParticleAtlas {
    val texture: Int
    private val cells = 4
    val rects: Array<UvRect>

    companion object {
        const val SOFT = 0; const val STAR = 1; const val HEART = 2; const val FLAKE = 3
        const val SPARKLE = 4; const val NOTE = 5; const val RING = 6; const val PUFF = 7
        const val SQUARE = 8; const val STREAK = 9; const val CLOUD = 10; const val BLOB = 11
        const val DROP = 12; const val GLOW = 13; const val ARROW = 14; const val CIRCLE = 15
    }

    init {
        val size = 512
        val c = size / cells
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun cx(i: Int) = (i % cells) * c + c / 2f
        fun cy(i: Int) = (i / cells) * c + c / 2f
        fun radial(i: Int, r: Float, inner: Int, outer: Int) {
            p.shader = RadialGradient(cx(i), cy(i), r, intArrayOf(inner, outer), null, Shader.TileMode.CLAMP)
            cv.drawCircle(cx(i), cy(i), r, p)
            p.shader = null
        }
        // SOFT
        radial(SOFT, c * 0.48f, Color.WHITE, Color.TRANSPARENT)
        // STAR
        p.color = Color.WHITE
        cv.drawPath(starPath(cx(STAR), cy(STAR), c * 0.44f, c * 0.2f, 5), p)
        // HEART
        cv.drawPath(heartPath(cx(HEART), cy(HEART), c * 0.4f), p)
        // FLAKE
        p.strokeWidth = c * 0.06f; p.strokeCap = Paint.Cap.ROUND; p.style = Paint.Style.STROKE
        for (k in 0 until 6) {
            val a = k * Math.PI / 3
            val ex = cx(FLAKE) + cos(a).toFloat() * c * 0.4f; val ey = cy(FLAKE) + sin(a).toFloat() * c * 0.4f
            cv.drawLine(cx(FLAKE), cy(FLAKE), ex, ey, p)
            val mx = cx(FLAKE) + cos(a).toFloat() * c * 0.24f; val my = cy(FLAKE) + sin(a).toFloat() * c * 0.24f
            for (s in listOf(-0.6, 0.6)) {
                cv.drawLine(mx, my, mx + cos(a + s).toFloat() * c * 0.12f, my + sin(a + s).toFloat() * c * 0.12f, p)
            }
        }
        p.style = Paint.Style.FILL
        // SPARKLE (4-point star with glow)
        radial(SPARKLE, c * 0.3f, Color.argb(160, 255, 255, 255), Color.TRANSPARENT)
        cv.drawPath(starPath(cx(SPARKLE), cy(SPARKLE), c * 0.46f, c * 0.07f, 4), p)
        // NOTE
        p.color = Color.WHITE
        cv.drawOval(cx(NOTE) - c * 0.26f, cy(NOTE) + c * 0.08f, cx(NOTE) + c * 0.02f, cy(NOTE) + c * 0.3f, p)
        cv.drawRect(cx(NOTE) - c * 0.03f, cy(NOTE) - c * 0.36f, cx(NOTE) + c * 0.03f, cy(NOTE) + c * 0.2f, p)
        cv.drawRect(cx(NOTE) - c * 0.03f, cy(NOTE) - c * 0.36f, cx(NOTE) + c * 0.22f, cy(NOTE) - c * 0.26f, p)
        // RING
        p.style = Paint.Style.STROKE; p.strokeWidth = c * 0.07f
        cv.drawCircle(cx(RING), cy(RING), c * 0.38f, p)
        p.style = Paint.Style.FILL
        // PUFF
        for ((dx, dy, r) in listOf(Triple(-0.14f, 0.06f, 0.24f), Triple(0.13f, 0.05f, 0.25f), Triple(0f, -0.1f, 0.27f), Triple(0f, 0.12f, 0.22f))) {
            p.shader = RadialGradient(cx(PUFF) + dx * c, cy(PUFF) + dy * c, r * c, intArrayOf(Color.argb(235, 255, 255, 255), Color.argb(0, 255, 255, 255)), floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP)
            cv.drawCircle(cx(PUFF) + dx * c, cy(PUFF) + dy * c, r * c, p)
        }
        p.shader = null
        // SQUARE (confetti)
        p.color = Color.WHITE
        cv.drawRoundRect(cx(SQUARE) - c * 0.3f, cy(SQUARE) - c * 0.2f, cx(SQUARE) + c * 0.3f, cy(SQUARE) + c * 0.2f, c * 0.06f, c * 0.06f, p)
        // STREAK
        p.shader = LinearGradient(cx(STREAK) - c * 0.45f, 0f, cx(STREAK) + c * 0.45f, 0f, intArrayOf(Color.TRANSPARENT, Color.WHITE, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        cv.drawRoundRect(cx(STREAK) - c * 0.45f, cy(STREAK) - c * 0.05f, cx(STREAK) + c * 0.45f, cy(STREAK) + c * 0.05f, c * 0.05f, c * 0.05f, p)
        p.shader = null
        // CLOUD
        for ((dx, dy, r) in listOf(Triple(-0.2f, 0.08f, 0.2f), Triple(0.18f, 0.08f, 0.22f), Triple(-0.02f, -0.04f, 0.27f), Triple(0.05f, 0.12f, 0.2f))) {
            p.shader = RadialGradient(cx(CLOUD) + dx * c, cy(CLOUD) + dy * c, r * c, intArrayOf(Color.argb(255, 255, 255, 255), Color.argb(0, 255, 255, 255)), floatArrayOf(0.65f, 1f), Shader.TileMode.CLAMP)
            cv.drawCircle(cx(CLOUD) + dx * c, cy(CLOUD) + dy * c, r * c, p)
        }
        p.shader = null
        // BLOB (shadow)
        radial(BLOB, c * 0.48f, Color.argb(255, 255, 255, 255), Color.argb(0, 255, 255, 255))
        // DROP
        p.color = Color.WHITE
        val drop = Path()
        drop.moveTo(cx(DROP), cy(DROP) - c * 0.4f)
        drop.quadTo(cx(DROP) + c * 0.3f, cy(DROP) + c * 0.05f, cx(DROP), cy(DROP) + c * 0.32f)
        drop.quadTo(cx(DROP) - c * 0.3f, cy(DROP) + c * 0.05f, cx(DROP), cy(DROP) - c * 0.4f)
        cv.drawPath(drop, p)
        // GLOW
        p.shader = RadialGradient(cx(GLOW), cy(GLOW), c * 0.5f, intArrayOf(Color.argb(255, 255, 255, 255), Color.argb(90, 255, 255, 255), Color.argb(0, 255, 255, 255)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx(GLOW), cy(GLOW), c * 0.5f, p)
        p.shader = null
        // ARROW (pointing down)
        val ar = Path()
        ar.moveTo(cx(ARROW) - c * 0.3f, cy(ARROW) - c * 0.2f)
        ar.lineTo(cx(ARROW) + c * 0.3f, cy(ARROW) - c * 0.2f)
        ar.lineTo(cx(ARROW), cy(ARROW) + c * 0.3f)
        ar.close()
        cv.drawPath(ar, p)
        // CIRCLE (hard disc)
        cv.drawCircle(cx(CIRCLE), cy(CIRCLE), c * 0.44f, p)

        texture = TextureUtil.fromBitmap(bmp)
        bmp.recycle()
        val inset = 1f / size
        rects = Array(cells * cells) { i ->
            val u = (i % cells).toFloat() / cells; val v = (i / cells).toFloat() / cells
            UvRect(u + inset, v + inset, u + 1f / cells - inset, v + 1f / cells - inset)
        }
    }

    private fun starPath(x: Float, y: Float, outer: Float, inner: Float, points: Int): Path {
        val path = Path()
        for (k in 0 until points * 2) {
            val r = if (k % 2 == 0) outer else inner
            val a = Math.PI * k / points - Math.PI / 2
            val px = x + cos(a).toFloat() * r; val py = y + sin(a).toFloat() * r
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        return path
    }

    private fun heartPath(x: Float, y: Float, s: Float): Path {
        val path = Path()
        path.moveTo(x, y + s * 0.85f)
        path.cubicTo(x - s * 1.3f, y - s * 0.1f, x - s * 0.6f, y - s * 1.05f, x, y - s * 0.4f)
        path.cubicTo(x + s * 0.6f, y - s * 1.05f, x + s * 1.3f, y - s * 0.1f, x, y + s * 0.85f)
        path.close()
        return path
    }
}

/** Game UI icons (power-ups etc.) packed into one texture for in-world billboards. */
class IconAtlas(context: Context) {
    val texture: Int
    val rects: Array<UvRect>

    companion object {
        const val BOOST = 0; const val SHIELD = 1; const val MAGNET = 2; const val MITTEN = 3
        const val GIFT = 4; const val CROWN = 5; const val SNOWFLAKE = 6; const val BONK = 7
        private val names = listOf("ui_boost", "ui_shield", "ui_magnet", "ui_mitten", "ui_gift", "ui_crown", "ui_snowflake", "ui_bonk")
    }

    init {
        val cell = 256
        val bmp = Bitmap.createBitmap(cell * 4, cell * 2, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        names.forEachIndexed { i, n ->
            val x = (i % 4) * cell; val y = (i / 4) * cell
            @Suppress("DiscouragedApi")
            val id = context.resources.getIdentifier(n, "drawable", context.packageName)
            val icon = if (id != 0) BitmapFactory.decodeResource(context.resources, id, BitmapFactory.Options().apply { inScaled = false }) else null
            if (icon != null) {
                cv.drawBitmap(icon, null, android.graphics.Rect(x + 8, y + 8, x + cell - 8, y + cell - 8), p)
                icon.recycle()
            } else {
                p.color = listOf(0xFFFF7A45, 0xFF5EC8FF, 0xFFFF4D8D, 0xFF3DF5C8, 0xFFFF5A5A, 0xFFFFC83D, 0xFFFFFFFF, 0xFFFFC83D)[i].toInt()
                cv.drawCircle(x + cell / 2f, y + cell / 2f, cell * 0.38f, p)
            }
        }
        texture = TextureUtil.fromBitmap(bmp)
        bmp.recycle()
        val inset = 1f / 1024f
        rects = Array(8) { i -> UvRect((i % 4) / 4f + inset, (i / 4) / 2f + inset, (i % 4 + 1) / 4f - inset, (i / 4 + 1) / 2f - inset) }
    }
}

/** Text labels (racer names, multipliers) rendered with the game font into a texture. */
class TextAtlas(context: Context, labels: List<Pair<String, Int>>, highlightFirst: Boolean) {
    val texture: Int
    val rects: Array<UvRect>

    init {
        val w = 1024
        val rowH = 72
        val font: Typeface = try { ResourcesCompat.getFont(context, R.font.lilita_one) ?: Typeface.DEFAULT_BOLD } catch (_: Throwable) { Typeface.DEFAULT_BOLD }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textSize = 54f; textAlign = Paint.Align.LEFT }
        val stroke = Paint(fill).apply { style = Paint.Style.STROKE; strokeWidth = 10f; strokeJoin = Paint.Join.ROUND; color = 0xFF1B2A4A.toInt() }
        // simple shelf packing
        val placed = ArrayList<IntArray>()
        var x = 0; var y = 0
        for ((text, _) in labels) {
            val tw = (fill.measureText(text) + 28).toInt().coerceAtMost(w)
            if (x + tw > w) { x = 0; y += rowH }
            placed += intArrayOf(x, y, tw)
            x += tw
        }
        var h = 64
        while (h < y + rowH) h *= 2
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        labels.forEachIndexed { i, (text, color) ->
            val (px, py, _) = placed[i].let { Triple(it[0], it[1], it[2]) }
            val baseline = py + rowH * 0.74f
            if (highlightFirst && i == 0) stroke.color = 0xFFFF4D8D.toInt() else stroke.color = 0xFF1B2A4A.toInt()
            cv.drawText(text, px + 14f, baseline, stroke)
            fill.color = color
            cv.drawText(text, px + 14f, baseline, fill)
        }
        texture = TextureUtil.fromBitmap(bmp)
        bmp.recycle()
        rects = Array(labels.size) { i ->
            val p = placed[i]
            UvRect(p[0].toFloat() / w, p[1].toFloat() / h, (p[0] + p[2]).toFloat() / w, (p[1] + rowH).toFloat() / h, p[2].toFloat() / rowH)
        }
    }
}
