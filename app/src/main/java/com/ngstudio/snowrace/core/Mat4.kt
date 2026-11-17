package com.ngstudio.snowrace.core

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Column-major 4x4 matrix helpers operating on FloatArray(16) (OpenGL layout). */
object Mat4 {
    fun new(): FloatArray = FloatArray(16).also { identity(it) }

    fun identity(m: FloatArray) {
        for (i in 0 until 16) m[i] = 0f
        m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
    }

    fun copy(src: FloatArray, dst: FloatArray) { System.arraycopy(src, 0, dst, 0, 16) }

    /** out = a * b (out may not alias a or b). */
    fun mul(out: FloatArray, a: FloatArray, b: FloatArray) {
        for (c in 0 until 4) {
            val b0 = b[c * 4]; val b1 = b[c * 4 + 1]; val b2 = b[c * 4 + 2]; val b3 = b[c * 4 + 3]
            out[c * 4] = a[0] * b0 + a[4] * b1 + a[8] * b2 + a[12] * b3
            out[c * 4 + 1] = a[1] * b0 + a[5] * b1 + a[9] * b2 + a[13] * b3
            out[c * 4 + 2] = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3
            out[c * 4 + 3] = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3
        }
    }

    fun perspective(m: FloatArray, fovYRad: Float, aspect: Float, near: Float, far: Float) {
        val f = 1f / tan(fovYRad / 2f)
        for (i in 0 until 16) m[i] = 0f
        m[0] = f / aspect
        m[5] = f
        m[10] = (far + near) / (near - far)
        m[11] = -1f
        m[14] = 2f * far * near / (near - far)
    }

    fun ortho(m: FloatArray, l: Float, r: Float, b: Float, t: Float, n: Float, f: Float) {
        for (i in 0 until 16) m[i] = 0f
        m[0] = 2f / (r - l)
        m[5] = 2f / (t - b)
        m[10] = -2f / (f - n)
        m[12] = -(r + l) / (r - l)
        m[13] = -(t + b) / (t - b)
        m[14] = -(f + n) / (f - n)
        m[15] = 1f
    }

    fun lookAt(m: FloatArray, ex: Float, ey: Float, ez: Float, cx: Float, cy: Float, cz: Float, ux: Float, uy: Float, uz: Float) {
        var fx = cx - ex; var fy = cy - ey; var fz = cz - ez
        val fl = 1f / sqrt(fx * fx + fy * fy + fz * fz)
        fx *= fl; fy *= fl; fz *= fl
        var sx = fy * uz - fz * uy
        var sy = fz * ux - fx * uz
        var sz = fx * uy - fy * ux
        val sl = 1f / sqrt(sx * sx + sy * sy + sz * sz)
        sx *= sl; sy *= sl; sz *= sl
        val vx = sy * fz - sz * fy
        val vy = sz * fx - sx * fz
        val vz = sx * fy - sy * fx
        m[0] = sx; m[1] = vx; m[2] = -fx; m[3] = 0f
        m[4] = sy; m[5] = vy; m[6] = -fy; m[7] = 0f
        m[8] = sz; m[9] = vz; m[10] = -fz; m[11] = 0f
        m[12] = -(sx * ex + sy * ey + sz * ez)
        m[13] = -(vx * ex + vy * ey + vz * ez)
        m[14] = fx * ex + fy * ey + fz * ez
        m[15] = 1f
    }

    /** Compose translation * rotation(quaternion) * scale into m. */
    fun trs(
        m: FloatArray, off: Int,
        tx: Float, ty: Float, tz: Float,
        qx: Float, qy: Float, qz: Float, qw: Float,
        sx: Float, sy: Float, sz: Float,
    ) {
        val xx = qx * qx; val yy = qy * qy; val zz = qz * qz
        val xy = qx * qy; val xz = qx * qz; val yz = qy * qz
        val wx = qw * qx; val wy = qw * qy; val wz = qw * qz
        m[off + 0] = (1f - 2f * (yy + zz)) * sx
        m[off + 1] = (2f * (xy + wz)) * sx
        m[off + 2] = (2f * (xz - wy)) * sx
        m[off + 3] = 0f
        m[off + 4] = (2f * (xy - wz)) * sy
        m[off + 5] = (1f - 2f * (xx + zz)) * sy
        m[off + 6] = (2f * (yz + wx)) * sy
        m[off + 7] = 0f
        m[off + 8] = (2f * (xz + wy)) * sz
        m[off + 9] = (2f * (yz - wx)) * sz
        m[off + 10] = (1f - 2f * (xx + yy)) * sz
        m[off + 11] = 0f
        m[off + 12] = tx
        m[off + 13] = ty
        m[off + 14] = tz
        m[off + 15] = 1f
    }

    /** General inverse. Returns false if singular. */
    fun invert(out: FloatArray, m: FloatArray): Boolean {
        val inv = FloatArray(16)
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]
        var det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]
        if (det == 0f) return false
        det = 1f / det
        for (i in 0 until 16) out[i] = inv[i] * det
        return true
    }

    /** Projects world point through viewProj; writes NDC x,y,z,w into out4. */
    fun project(vp: FloatArray, x: Float, y: Float, z: Float, out4: FloatArray) {
        out4[0] = vp[0] * x + vp[4] * y + vp[8] * z + vp[12]
        out4[1] = vp[1] * x + vp[5] * y + vp[9] * z + vp[13]
        out4[2] = vp[2] * x + vp[6] * y + vp[10] * z + vp[14]
        out4[3] = vp[3] * x + vp[7] * y + vp[11] * z + vp[15]
    }
}

/** Quaternion helpers writing into FloatArray(4) = (x,y,z,w). */
object Quat {
    fun identity(q: FloatArray) { q[0] = 0f; q[1] = 0f; q[2] = 0f; q[3] = 1f }

    fun axisAngle(q: FloatArray, ax: Float, ay: Float, az: Float, angle: Float) {
        val h = angle * 0.5f
        val s = sin(h)
        q[0] = ax * s; q[1] = ay * s; q[2] = az * s; q[3] = cos(h)
    }

    /** out = a * b */
    fun mul(out: FloatArray, a: FloatArray, b: FloatArray) {
        val x = a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1]
        val y = a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0]
        val z = a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3]
        val w = a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]
        out[0] = x; out[1] = y; out[2] = z; out[3] = w
    }

    fun normalize(q: FloatArray) {
        val l = sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
        if (l > 0f) { q[0] /= l; q[1] /= l; q[2] /= l; q[3] /= l }
    }

    /** Yaw (Y) then pitch (X) then roll (Z) in local space: q = qYaw * qPitch * qRoll. */
    fun euler(q: FloatArray, yaw: Float, pitch: Float, roll: Float) {
        val cy = cos(yaw * 0.5f); val sy = sin(yaw * 0.5f)
        val cp = cos(pitch * 0.5f); val sp = sin(pitch * 0.5f)
        val cr = cos(roll * 0.5f); val sr = sin(roll * 0.5f)
        // qYaw*qPitch
        val x1 = cy * sp
        val y1 = sy * cp
        val z1 = -sy * sp
        val w1 = cy * cp
        // * qRoll (0,0,sr,cr)
        q[0] = w1 * 0f + x1 * cr + y1 * sr - z1 * 0f
        q[1] = w1 * 0f - x1 * sr + y1 * cr + z1 * 0f
        q[2] = w1 * sr + x1 * 0f - y1 * 0f + z1 * cr
        q[3] = w1 * cr - x1 * 0f - y1 * 0f - z1 * sr
    }
}
