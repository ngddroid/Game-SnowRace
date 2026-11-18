package com.ngstudio.snowrace.game.render

import android.opengl.GLES30
import java.nio.FloatBuffer

/**
 * CPU-side mesh data: interleaved position(3) normal(3) color(3) + int indices.
 * Uploaded to a VAO with an optional per-instance attribute buffer.
 */
class MeshData {
    var verts = FloatArray(1024)
    var vCount = 0
    var idx = IntArray(1024)
    var iCount = 0

    fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, r: Float, g: Float, b: Float): Int {
        if ((vCount + 1) * STRIDE > verts.size) verts = verts.copyOf(verts.size * 2)
        val o = vCount * STRIDE
        verts[o] = x; verts[o + 1] = y; verts[o + 2] = z
        verts[o + 3] = nx; verts[o + 4] = ny; verts[o + 5] = nz
        verts[o + 6] = r; verts[o + 7] = g; verts[o + 8] = b
        return vCount++
    }

    fun tri(a: Int, b: Int, c: Int) {
        if (iCount + 3 > idx.size) idx = idx.copyOf(idx.size * 2)
        idx[iCount++] = a; idx[iCount++] = b; idx[iCount++] = c
    }

    fun quad(a: Int, b: Int, c: Int, d: Int) { tri(a, b, c); tri(a, c, d) }

    companion object { const val STRIDE = 9 }
}

class Mesh(data: MeshData, instanced: Boolean) {
    val vao = Gl.genVao()
    private val vbo = Gl.genBuffer()
    private val ibo = Gl.genBuffer()
    val indexCount = data.iCount
    private val useInt = data.vCount > 65535
    var instanceVbo = 0
        private set

    init {
        GLES30.glBindVertexArray(vao)
        val vb = Gl.floatBuffer(data.vCount * MeshData.STRIDE)
        vb.put(data.verts, 0, data.vCount * MeshData.STRIDE).position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.vCount * MeshData.STRIDE * 4, vb, GLES30.GL_STATIC_DRAW)
        val stride = MeshData.STRIDE * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, stride, 24)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        if (useInt) {
            val ib = Gl.intBuffer(data.iCount); ib.put(data.idx, 0, data.iCount).position(0)
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, data.iCount * 4, ib, GLES30.GL_STATIC_DRAW)
        } else {
            val ib = Gl.shortBuffer(data.iCount)
            for (k in 0 until data.iCount) ib.put(data.idx[k].toShort())
            ib.position(0)
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, data.iCount * 2, ib, GLES30.GL_STATIC_DRAW)
        }

        if (instanced) {
            instanceVbo = Gl.genBuffer()
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo)
            val istride = INSTANCE_FLOATS * 4
            for (c in 0 until 4) {
                GLES30.glEnableVertexAttribArray(3 + c)
                GLES30.glVertexAttribPointer(3 + c, 4, GLES30.GL_FLOAT, false, istride, c * 16)
                GLES30.glVertexAttribDivisor(3 + c, 1)
            }
            GLES30.glEnableVertexAttribArray(7)
            GLES30.glVertexAttribPointer(7, 4, GLES30.GL_FLOAT, false, istride, 64)
            GLES30.glVertexAttribDivisor(7, 1)
            GLES30.glEnableVertexAttribArray(8)
            GLES30.glVertexAttribPointer(8, 4, GLES30.GL_FLOAT, false, istride, 80)
            GLES30.glVertexAttribDivisor(8, 1)
        }
        GLES30.glBindVertexArray(0)
    }

    fun draw() {
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, if (useInt) GLES30.GL_UNSIGNED_INT else GLES30.GL_UNSIGNED_SHORT, 0)
    }

    fun drawInstanced(count: Int) {
        if (count <= 0) return
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElementsInstanced(GLES30.GL_TRIANGLES, indexCount, if (useInt) GLES30.GL_UNSIGNED_INT else GLES30.GL_UNSIGNED_SHORT, 0, count)
    }

    /** Frees the GPU buffers (call when a per-level mesh is no longer used). */
    fun release() {
        GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        val ids = if (instanceVbo != 0) intArrayOf(vbo, ibo, instanceVbo) else intArrayOf(vbo, ibo)
        GLES30.glDeleteBuffers(ids.size, ids, 0)
    }

    fun uploadInstances(buf: FloatBuffer, count: Int, capacityBytes: Int) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, capacityBytes, null, GLES30.GL_STREAM_DRAW)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, count * INSTANCE_FLOATS * 4, buf)
    }

    companion object {
        /** mat4 (16) + tint vec4 (4) + params vec4 (4: emissive, sway, outline flag, gloss) */
        const val INSTANCE_FLOATS = 24
    }
}

/** Collects instances of one mesh for a frame, then uploads and draws them in one call. */
class InstanceBatch(val mesh: Mesh, initial: Int = 64) {
    var data = FloatArray(initial * Mesh.INSTANCE_FLOATS)
    var count = 0
    private var buf: FloatBuffer = Gl.floatBuffer(initial * Mesh.INSTANCE_FLOATS)
    private var uploaded = -1

    fun clear() { count = 0; uploaded = -1 }

    /** Reserves a slot and returns its float offset (matrix at +0, tint at +16, params at +20). */
    fun next(): Int {
        if ((count + 1) * Mesh.INSTANCE_FLOATS > data.size) data = data.copyOf(data.size * 2)
        val o = count * Mesh.INSTANCE_FLOATS
        count++
        return o
    }

    fun setStyle(o: Int, color: Int, emissive: Float = 0f, sway: Float = 0f, outline: Float = 1f, gloss: Float = 0f, alpha: Float = 1f) {
        data[o + 16] = ((color shr 16) and 0xFF) / 255f
        data[o + 17] = ((color shr 8) and 0xFF) / 255f
        data[o + 18] = (color and 0xFF) / 255f
        data[o + 19] = alpha
        data[o + 20] = emissive
        data[o + 21] = sway
        data[o + 22] = outline
        data[o + 23] = gloss
    }

    fun setTint(o: Int, r: Float, g: Float, b: Float) { data[o + 16] = r; data[o + 17] = g; data[o + 18] = b; data[o + 19] = 1f }

    fun upload() {
        if (uploaded == count) return
        val need = count * Mesh.INSTANCE_FLOATS
        if (buf.capacity() < need) buf = Gl.floatBuffer(data.size)
        buf.position(0)
        buf.put(data, 0, need)
        buf.position(0)
        mesh.uploadInstances(buf, count, buf.capacity() * 4)
        uploaded = count
    }

    fun draw() {
        if (count == 0) return
        upload()
        mesh.drawInstanced(count)
    }
}
