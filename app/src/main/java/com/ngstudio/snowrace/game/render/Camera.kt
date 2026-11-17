package com.ngstudio.snowrace.game.render

import com.ngstudio.snowrace.core.DEG2RAD
import com.ngstudio.snowrace.core.Mat4
import kotlin.math.sqrt

class Camera {
    var x = 0f; var y = 10f; var z = -10f
    var tx = 0f; var ty = 0f; var tz = 0f
    var fovDeg = 52f
    var aspect = 0.5f
    var near = 0.3f
    var far = 260f
    val view = FloatArray(16)
    val proj = FloatArray(16)
    val viewProj = FloatArray(16)
    val invViewProj = FloatArray(16)
    val right = FloatArray(3)
    val up = FloatArray(3)
    val fwd = FloatArray(3)

    fun update() {
        Mat4.lookAt(view, x, y, z, tx, ty, tz, 0f, 1f, 0f)
        Mat4.perspective(proj, fovDeg * DEG2RAD, aspect, near, far)
        Mat4.mul(viewProj, proj, view)
        Mat4.invert(invViewProj, viewProj)
        // camera basis from view matrix rows
        right[0] = view[0]; right[1] = view[4]; right[2] = view[8]
        up[0] = view[1]; up[1] = view[5]; up[2] = view[9]
        fwd[0] = -view[2]; fwd[1] = -view[6]; fwd[2] = -view[10]
    }

    /** Ground-plane forward (normalized XZ). */
    fun flatForward(out: FloatArray) {
        val l = sqrt(fwd[0] * fwd[0] + fwd[2] * fwd[2]).coerceAtLeast(1e-4f)
        out[0] = fwd[0] / l; out[1] = fwd[2] / l
    }

    fun flatRight(out: FloatArray) {
        val l = sqrt(right[0] * right[0] + right[2] * right[2]).coerceAtLeast(1e-4f)
        out[0] = right[0] / l; out[1] = right[2] / l
    }

    private val tmp = FloatArray(4)

    /** World -> screen pixels. Returns false if behind the camera. */
    fun toScreen(wx: Float, wy: Float, wz: Float, width: Int, height: Int, out: FloatArray): Boolean {
        Mat4.project(viewProj, wx, wy, wz, tmp)
        if (tmp[3] <= 0.01f) return false
        out[0] = (tmp[0] / tmp[3] * 0.5f + 0.5f) * width
        out[1] = (1f - (tmp[1] / tmp[3] * 0.5f + 0.5f)) * height
        return true
    }
}
