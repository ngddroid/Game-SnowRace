package com.ngstudio.snowrace.game.render

import android.opengl.GLES30
import com.ngstudio.snowrace.core.Rng
import java.nio.FloatBuffer
import kotlin.math.min

/** Instanced camera-facing (or ground-aligned) textured quads. */
class BillboardBatch(initial: Int = 256) {
    private val vao = Gl.genVao()
    private val quadVbo = Gl.genBuffer()
    private val instVbo = Gl.genBuffer()
    private val ibo = Gl.genBuffer()
    var data = FloatArray(initial * FLOATS)
    var count = 0
    private var buf: FloatBuffer = Gl.floatBuffer(initial * FLOATS)

    companion object { const val FLOATS = 14 }

    init {
        GLES30.glBindVertexArray(vao)
        val q = Gl.floatBuffer(12)
        q.put(floatArrayOf(-0.5f, -0.5f, 0f, 0.5f, -0.5f, 0f, 0.5f, 0.5f, 0f, -0.5f, 0.5f, 0f)).position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 48, q, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        val ib = Gl.shortBuffer(6); ib.put(shortArrayOf(0, 1, 2, 0, 2, 3)).position(0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, 12, ib, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instVbo)
        val s = FLOATS * 4
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, s, 0); GLES30.glVertexAttribDivisor(1, 1)
        GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, s, 16); GLES30.glVertexAttribDivisor(2, 1)
        GLES30.glEnableVertexAttribArray(3); GLES30.glVertexAttribPointer(3, 4, GLES30.GL_FLOAT, false, s, 32); GLES30.glVertexAttribDivisor(3, 1)
        GLES30.glEnableVertexAttribArray(4); GLES30.glVertexAttribPointer(4, 2, GLES30.GL_FLOAT, false, s, 48); GLES30.glVertexAttribDivisor(4, 1)
        GLES30.glBindVertexArray(0)
    }

    fun clear() { count = 0 }

    /** Adds a quad. [color] is ARGB; it is premultiplied here. [additive] writes alpha 0 for additive look. */
    fun add(x: Float, y: Float, z: Float, size: Float, color: Int, alpha: Float, uv: UvRect, rot: Float = 0f, aspect: Float = uv.aspect, additive: Boolean = false) {
        if (alpha <= 0.003f || size <= 0f) return
        if ((count + 1) * FLOATS > data.size) data = data.copyOf(data.size * 2)
        val o = count * FLOATS
        val a = alpha * (((color ushr 24) and 0xFF) / 255f)
        data[o] = x; data[o + 1] = y; data[o + 2] = z; data[o + 3] = size
        data[o + 4] = ((color shr 16) and 0xFF) / 255f * a
        data[o + 5] = ((color shr 8) and 0xFF) / 255f * a
        data[o + 6] = (color and 0xFF) / 255f * a
        data[o + 7] = if (additive) 0f else a
        data[o + 8] = uv.u0; data[o + 9] = uv.v0; data[o + 10] = uv.u1; data[o + 11] = uv.v1
        data[o + 12] = rot; data[o + 13] = aspect
        count++
    }

    fun draw() {
        if (count == 0) return
        val need = count * FLOATS
        if (buf.capacity() < need) buf = Gl.floatBuffer(data.size)
        buf.position(0); buf.put(data, 0, need); buf.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, buf.capacity() * 4, null, GLES30.GL_STREAM_DRAW)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, need * 4, buf)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElementsInstanced(GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_SHORT, 0, count)
    }
}

/** CPU particle simulation (struct of arrays). */
class ParticleSystem(val capacity: Int) {
    private val x = FloatArray(capacity); private val y = FloatArray(capacity); private val z = FloatArray(capacity)
    private val vx = FloatArray(capacity); private val vy = FloatArray(capacity); private val vz = FloatArray(capacity)
    private val life = FloatArray(capacity); private val maxLife = FloatArray(capacity)
    private val s0 = FloatArray(capacity); private val s1 = FloatArray(capacity)
    private val color = IntArray(capacity)
    private val rot = FloatArray(capacity); private val rotV = FloatArray(capacity)
    private val sprite = IntArray(capacity)
    private val grav = FloatArray(capacity); private val drag = FloatArray(capacity)
    private val flags = IntArray(capacity) // 1 = additive, 2 = ground-aligned, 4 = fade-in
    var count = 0
        private set
    val rng = Rng(424242)

    fun emit(
        px: Float, py: Float, pz: Float, pvx: Float, pvy: Float, pvz: Float,
        lifeS: Float, size0: Float, size1: Float, argb: Int, spr: Int,
        gravity: Float = 0f, dragK: Float = 0f, additive: Boolean = false, ground: Boolean = false,
        rotation: Float = 0f, rotSpeed: Float = 0f,
    ) {
        val i = if (count < capacity) count++ else rng.nextInt(capacity)
        x[i] = px; y[i] = py; z[i] = pz; vx[i] = pvx; vy[i] = pvy; vz[i] = pvz
        life[i] = 0f; maxLife[i] = lifeS; s0[i] = size0; s1[i] = size1; color[i] = argb
        sprite[i] = spr; grav[i] = gravity; drag[i] = dragK; rot[i] = rotation; rotV[i] = rotSpeed
        flags[i] = (if (additive) 1 else 0) or (if (ground) 2 else 0)
    }

    fun update(dt: Float, floorY: (Float, Float) -> Float = { _, _ -> -100f }) {
        var i = 0
        while (i < count) {
            life[i] += dt
            if (life[i] >= maxLife[i]) {
                val last = count - 1
                if (i != last) copy(last, i)
                count--
                continue
            }
            vy[i] -= grav[i] * dt
            val k = 1f - min(1f, drag[i] * dt)
            vx[i] *= k; vy[i] *= k; vz[i] *= k
            x[i] += vx[i] * dt; y[i] += vy[i] * dt; z[i] += vz[i] * dt
            rot[i] += rotV[i] * dt
            if (grav[i] > 0f) {
                val fy = floorY(x[i], z[i])
                if (y[i] < fy) { y[i] = fy; vy[i] = -vy[i] * 0.25f; vx[i] *= 0.6f; vz[i] *= 0.6f }
            }
            i++
        }
    }

    private fun copy(from: Int, to: Int) {
        x[to] = x[from]; y[to] = y[from]; z[to] = z[from]; vx[to] = vx[from]; vy[to] = vy[from]; vz[to] = vz[from]
        life[to] = life[from]; maxLife[to] = maxLife[from]; s0[to] = s0[from]; s1[to] = s1[from]; color[to] = color[from]
        rot[to] = rot[from]; rotV[to] = rotV[from]; sprite[to] = sprite[from]; grav[to] = grav[from]; drag[to] = drag[from]; flags[to] = flags[from]
    }

    fun clear() { count = 0 }

    fun fill(atlas: ParticleAtlas, bill: BillboardBatch, ground: BillboardBatch) {
        for (i in 0 until count) {
            val t = life[i] / maxLife[i]
            val size = s0[i] + (s1[i] - s0[i]) * t
            val fadeIn = min(1f, life[i] * 12f)
            val alpha = fadeIn * (if (t > 0.6f) (1f - t) / 0.4f else 1f)
            val target = if ((flags[i] and 2) != 0) ground else bill
            target.add(x[i], y[i], z[i], size, color[i], alpha, atlas.rects[sprite[i]], rot[i], 1f, (flags[i] and 1) != 0)
        }
    }
}
