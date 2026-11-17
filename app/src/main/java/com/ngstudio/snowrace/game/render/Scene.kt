package com.ngstudio.snowrace.game.render

import com.ngstudio.snowrace.core.Mat4
import com.ngstudio.snowrace.core.Quat

/** A renderable world that lives on the GL thread. */
interface Scene {
    fun update(dt: Float)
    fun render()
    fun release()
}

/** Small helpers to push instances with TRS transforms. */
object Inst {
    private val q = FloatArray(4)

    fun add(
        b: InstanceBatch, x: Float, y: Float, z: Float,
        yaw: Float = 0f, pitch: Float = 0f, roll: Float = 0f,
        sx: Float = 1f, sy: Float = 1f, sz: Float = 1f,
        color: Int = 0xFFFFFF, emissive: Float = 0f, sway: Float = 0f, outline: Float = 0f, gloss: Float = 0f, alpha: Float = 1f,
    ) {
        val o = b.next()
        Quat.euler(q, yaw, pitch, roll)
        Mat4.trs(b.data, o, x, y, z, q[0], q[1], q[2], q[3], sx, sy, sz)
        b.setStyle(o, color, emissive, sway, outline, gloss, alpha)
    }

    fun addQuat(
        b: InstanceBatch, x: Float, y: Float, z: Float, rot: FloatArray, s: Float,
        color: Int = 0xFFFFFF, emissive: Float = 0f, outline: Float = 0f, gloss: Float = 0f,
    ) {
        val o = b.next()
        Mat4.trs(b.data, o, x, y, z, rot[0], rot[1], rot[2], rot[3], s, s, s)
        b.setStyle(o, color, emissive, 0f, outline, gloss)
    }
}
