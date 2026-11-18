package com.ngstudio.snowrace.game.render

import android.opengl.GLES30
import com.ngstudio.snowrace.game.sim.SnowField
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** GPU side of a [SnowField]: a vertex grid displaced by an R8 heightmap updated row-by-row. */
class TerrainMesh(val field: SnowField) {
    private val vao = Gl.genVao()
    private val vbo = Gl.genBuffer()
    private val ibo = Gl.genBuffer()
    private val count: Int
    private val intType: Boolean
    val tex: Int
    private val buf: ByteBuffer = Gl.byteBuffer(field.cols * field.rows)

    init {
        val nx = field.cols + 2; val nz = field.rows + 2
        val verts = Gl.floatBuffer(nx * nz * 3)
        for (j in 0 until nz) for (i in 0 until nx) {
            val u = when (i) { 0 -> 0f; nx - 1 -> 1f; else -> (i - 0.5f) / field.cols }
            val v = when (j) { 0 -> 0f; nz - 1 -> 1f; else -> (j - 0.5f) / field.rows }
            val border = if (i == 0 || j == 0 || i == nx - 1 || j == nz - 1) 1f else 0f
            verts.put(u); verts.put(border); verts.put(v)
        }
        verts.position(0)
        val idxCount = (nx - 1) * (nz - 1) * 6
        intType = nx * nz > 65535
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, nx * nz * 12, verts, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        if (intType) {
            val ib = Gl.intBuffer(idxCount)
            for (j in 0 until nz - 1) for (i in 0 until nx - 1) {
                val a = j * nx + i
                ib.put(a); ib.put(a + nx); ib.put(a + 1); ib.put(a + 1); ib.put(a + nx); ib.put(a + nx + 1)
            }
            ib.position(0)
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, idxCount * 4, ib, GLES30.GL_STATIC_DRAW)
        } else {
            val ib = Gl.shortBuffer(idxCount)
            for (j in 0 until nz - 1) for (i in 0 until nx - 1) {
                val a = j * nx + i
                ib.put(a.toShort()); ib.put((a + nx).toShort()); ib.put((a + 1).toShort())
                ib.put((a + 1).toShort()); ib.put((a + nx).toShort()); ib.put((a + nx + 1).toShort())
            }
            ib.position(0)
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, idxCount * 2, ib, GLES30.GL_STATIC_DRAW)
        }
        GLES30.glBindVertexArray(0)
        count = idxCount
        for (k in 0 until field.cols * field.rows) buf.put(k, toByte(field.depth[k]))
        buf.position(0)
        tex = TextureUtil.r8(field.cols, field.rows, buf)
        field.clearDirty()
    }

    private fun toByte(d: Float): Byte = ((d / SnowField.MAX_DEPTH).coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()

    fun draw(prog: ShaderProgram) {
        if (field.dirtyMin >= 0) {
            val r0 = field.dirtyMin; val r1 = field.dirtyMax
            val n = (r1 - r0 + 1) * field.cols
            val base = r0 * field.cols
            for (k in 0 until n) buf.put(k, toByte(field.depth[base + k]))
            buf.position(0)
            TextureUtil.updateR8Rows(tex, field.cols, r0, r1 - r0 + 1, buf)
            field.clearDirty()
        }
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        prog.vec4("uField", field.x0, field.z0, field.width, field.length)
        prog.f("uBaseY", field.baseY)
        prog.vec2("uTexel", 1f / field.cols, 1f / field.rows)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, count, if (intType) GLES30.GL_UNSIGNED_INT else GLES30.GL_UNSIGNED_SHORT, 0)
    }

    fun release() {
        TextureUtil.delete(tex)
        GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        GLES30.glDeleteBuffers(2, intArrayOf(vbo, ibo), 0)
    }

    companion object {
        fun drawAll(res: RenderResources, cam: Camera, light: LightRig, terrains: List<TerrainMesh>) {
            val prog = res.terrain
            res.common(prog, cam, light)
            prog.vec3("uSnowColor", light.snow)
            prog.vec3("uSnowPacked", light.snowPacked)
            prog.f("uSparkle", light.sparkle)
            // trail walls drawn a bit taller than the sim's snow depth so grooves read crisply
            prog.f("uHScale", SnowField.MAX_DEPTH * SnowField.SNOW_H * 1.45f)
            prog.i("uHeight", 0)
            for (t in terrains) t.draw(prog)
        }
    }
}

/** Large water quad. */
class WaterPlane(x0: Float, x1: Float, z0: Float, z1: Float) {
    private val vao = Gl.genVao()
    private val vbo = Gl.genBuffer()
    private val ibo = Gl.genBuffer()

    init {
        val v = Gl.floatBuffer(12)
        v.put(floatArrayOf(x0, 0f, z0, x1, 0f, z0, x1, 0f, z1, x0, 0f, z1)).position(0)
        val i = Gl.shortBuffer(6); i.put(shortArrayOf(0, 3, 2, 0, 2, 1)).position(0)
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 48, v, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, 12, i, GLES30.GL_STATIC_DRAW)
        GLES30.glBindVertexArray(0)
    }

    fun draw(res: RenderResources, cam: Camera, light: LightRig, waterY: Float, mask: LandMask) {
        val prog = res.water
        res.common(prog, cam, light)
        prog.f("uWaterY", waterY)
        prog.vec3("uDeep", light.waterDeep)
        prog.vec3("uShallow", light.waterShallow)
        prog.vec3("uSkyRefl", light.skyRefl)
        prog.vec4("uLandRect", mask.x0, mask.z0, mask.w, mask.l)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, res.causticsTex)
        prog.i("uCaustics", 2)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mask.tex)
        prog.i("uLand", 0)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_SHORT, 0)
    }

    fun release() {
        GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        GLES30.glDeleteBuffers(2, intArrayOf(vbo, ibo), 0)
    }
}

/**
 * Low-res land/water mask (blurred) for shallow-water color and shore foam.
 * [baseSolid] fills static land once; [extra] adds dynamic land (bridges) each rebuild.
 */
class LandMask(val x0: Float, val z0: Float, val w: Float, val l: Float, private val cell: Float = 0.5f, baseSolid: (Float, Float) -> Boolean) {
    private val cols = ceil(w / cell).toInt()
    private val rows = ceil(l / cell).toInt()
    private val base = FloatArray(cols * rows)
    private val cur = FloatArray(cols * rows)
    private val tmp = FloatArray(cols * rows)
    private val bytes: ByteBuffer = Gl.byteBuffer(cols * rows)
    val tex: Int
    // async rebuild state: the worker fills [bytes]; the GL thread uploads when [ready]
    @Volatile private var ready = false
    private var busy = false
    private var marks = FloatArray(512)

    init {
        for (r in 0 until rows) {
            val z = z0 + (r + 0.5f) * cell
            for (c in 0 until cols) if (baseSolid(x0 + (c + 0.5f) * cell, z)) base[r * cols + c] = 1f
        }
        tex = TextureUtil.r8(cols, rows, null)
    }

    fun stamp(cx: Float, cz: Float, rad: Float) {
        val c0 = ((cx - rad - x0) / cell).toInt(); val c1 = ((cx + rad - x0) / cell).toInt()
        val r0 = ((cz - rad - z0) / cell).toInt(); val r1 = ((cz + rad - z0) / cell).toInt()
        for (r in max(0, r0)..min(rows - 1, r1)) for (c in max(0, c0)..min(cols - 1, c1)) {
            val dx = x0 + (c + 0.5f) * cell - cx; val dz = z0 + (r + 0.5f) * cell - cz
            if (dx * dx + dz * dz <= rad * rad) base[r * cols + c] = 1f
        }
    }

    inline fun rebuild(extra: (mark: (Float, Float) -> Unit) -> Unit) {
        beginRebuild()
        extra { x, z -> mark(x, z) }
        finishRebuild()
    }

    fun beginRebuild() { System.arraycopy(base, 0, cur, 0, cur.size) }

    fun mark(x: Float, z: Float) {
        val c = ((x - x0) / cell).toInt(); val r = ((z - z0) / cell).toInt()
        if (c in 0 until cols && r in 0 until rows) cur[r * cols + c] = 1f
    }

    fun finishRebuild() {
        blur(cur, tmp); blur(cur, tmp)
        for (i in cur.indices) bytes.put(i, (cur[i].coerceIn(0f, 1f) * 255f).toInt().toByte())
        bytes.position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, cols, rows, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, bytes)
    }

    private fun blur(a: FloatArray, t: FloatArray) {
        val rad = 2
        val n = (2 * rad + 1).toFloat()
        for (r in 0 until rows) {
            var acc = 0f
            for (k in -rad..rad) acc += a[r * cols + k.coerceIn(0, cols - 1)]
            for (c in 0 until cols) {
                t[r * cols + c] = acc / n
                acc -= a[r * cols + (c - rad).coerceIn(0, cols - 1)]
                acc += a[r * cols + (c + rad + 1).coerceIn(0, cols - 1)]
            }
        }
        for (c in 0 until cols) {
            var acc = 0f
            for (k in -rad..rad) acc += t[k.coerceIn(0, rows - 1) * cols + c]
            for (r in 0 until rows) {
                a[r * cols + c] = acc / n
                acc -= t[(r - rad).coerceIn(0, rows - 1) * cols + c]
                acc += t[(r + rad + 1).coerceIn(0, rows - 1) * cols + c]
            }
        }
    }

    /**
     * Rebuilds the mask off the render thread. [fill] writes x,z pairs of extra land (bridge cells)
     * into the array it is given and returns the pair count. Returns false while a rebuild is in flight.
     */
    fun requestAsync(fill: (FloatArray) -> Int): Boolean {
        if (busy) return false
        var need = 0
        // grow the scratch array until the snapshot fits
        while (true) {
            need = fill(marks)
            if (need >= 0) break
            marks = FloatArray(marks.size * 2)
        }
        val snapshot = marks.copyOf(need * 2)
        busy = true
        WORKER.execute {
            System.arraycopy(base, 0, cur, 0, cur.size)
            for (k in 0 until need) mark(snapshot[k * 2], snapshot[k * 2 + 1])
            blur(cur, tmp); blur(cur, tmp)
            for (i in cur.indices) bytes.put(i, (cur[i].coerceIn(0f, 1f) * 255f).toInt().toByte())
            ready = true
        }
        return true
    }

    /** Call once per frame on the GL thread: uploads a finished async rebuild. */
    fun uploadIfReady() {
        if (!ready) return
        bytes.position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, cols, rows, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, bytes)
        ready = false
        busy = false
    }

    fun release() { TextureUtil.delete(tex) }

    companion object {
        private val WORKER = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "LandMask").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 2 }
        }
    }
}
