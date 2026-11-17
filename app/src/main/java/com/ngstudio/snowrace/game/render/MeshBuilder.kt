package com.ngstudio.snowrace.game.render

import com.ngstudio.snowrace.core.TAU
import com.ngstudio.snowrace.core.hash2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Procedural mesh construction with a current transform (translate * rotate * scale) and color.
 * Normals are transformed exactly (R * n / S), so non-uniformly scaled spheres light correctly.
 * Colors above 1.0 are treated as self-illumination by the shaders (lamps, windows).
 */
class MeshBuilder(val d: MeshData = MeshData()) {
    private var tx = 0f; private var ty = 0f; private var tz = 0f
    private val rot = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f) // row-major 3x3
    private var sx = 1f; private var sy = 1f; private var sz = 1f
    private var cr = 1f; private var cg = 1f; private var cb = 1f

    fun color(c: Int, boost: Float = 1f): MeshBuilder {
        cr = ((c shr 16) and 0xFF) / 255f * boost; cg = ((c shr 8) and 0xFF) / 255f * boost; cb = (c and 0xFF) / 255f * boost
        return this
    }

    fun color(r: Float, g: Float, b: Float): MeshBuilder { cr = r; cg = g; cb = b; return this }

    /** Sets transform: position, rotation as yaw (Y), pitch (X), roll (Z) radians, then scale. */
    fun at(x: Float, y: Float, z: Float, yaw: Float = 0f, pitch: Float = 0f, roll: Float = 0f, scx: Float = 1f, scy: Float = 1f, scz: Float = 1f): MeshBuilder {
        tx = x; ty = y; tz = z
        sx = scx; sy = scy; sz = scz
        val cy = cos(yaw); val syw = sin(yaw)
        val cp = cos(pitch); val sp = sin(pitch)
        val cro = cos(roll); val sro = sin(roll)
        // R = Ry * Rx * Rz
        val r00 = cy * cro + syw * sp * sro; val r01 = -cy * sro + syw * sp * cro; val r02 = syw * cp
        val r10 = cp * sro;                   val r11 = cp * cro;                    val r12 = -sp
        val r20 = -syw * cro + cy * sp * sro; val r21 = syw * sro + cy * sp * cro;   val r22 = cy * cp
        rot[0] = r00; rot[1] = r01; rot[2] = r02
        rot[3] = r10; rot[4] = r11; rot[5] = r12
        rot[6] = r20; rot[7] = r21; rot[8] = r22
        return this
    }

    fun reset(): MeshBuilder = at(0f, 0f, 0f)

    private fun emit(px: Float, py: Float, pz: Float, nx: Float, ny: Float, nz: Float, r: Float = cr, g: Float = cg, b: Float = cb): Int {
        val lx = px * sx; val ly = py * sy; val lz = pz * sz
        val wx = rot[0] * lx + rot[1] * ly + rot[2] * lz + tx
        val wy = rot[3] * lx + rot[4] * ly + rot[5] * lz + ty
        val wz = rot[6] * lx + rot[7] * ly + rot[8] * lz + tz
        val qx = nx / sx; val qy = ny / sy; val qz = nz / sz
        var mx = rot[0] * qx + rot[1] * qy + rot[2] * qz
        var my = rot[3] * qx + rot[4] * qy + rot[5] * qz
        var mz = rot[6] * qx + rot[7] * qy + rot[8] * qz
        val l = sqrt(mx * mx + my * my + mz * mz)
        if (l > 1e-6f) { mx /= l; my /= l; mz /= l }
        return d.vertex(wx, wy, wz, mx, my, mz, r, g, b)
    }

    // ---------------------------------------------------------------- primitives (unit sized)

    /** UV sphere of radius 1. [shade] lets callers color by local normal. */
    fun sphere(segU: Int = 20, segV: Int = 14, shade: ((Float, Float, Float) -> Int)? = null): MeshBuilder {
        val base = d.vCount
        for (v in 0..segV) {
            val th = v.toFloat() / segV * Math.PI.toFloat()
            val y = cos(th); val rr = sin(th)
            for (u in 0..segU) {
                val ph = u.toFloat() / segU * TAU
                val x = rr * cos(ph); val z = rr * sin(ph)
                if (shade != null) { val c = shade(x, y, z); emit(x, y, z, x, y, z, ((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f) }
                else emit(x, y, z, x, y, z)
            }
        }
        val row = segU + 1
        for (v in 0 until segV) for (u in 0 until segU) {
            val a = base + v * row + u
            d.quad(a, a + 1, a + row + 1, a + row)
        }
        return this
    }

    /** Icosphere with optional radial noise and flat (faceted) shading. */
    fun icosphere(sub: Int, noise: Float = 0f, seed: Int = 0, flat: Boolean = false, shade: ((Float, Float, Float, Float) -> Int)? = null): MeshBuilder {
        val t = (1f + sqrt(5f)) / 2f
        val vs = ArrayList<FloatArray>()
        fun add(x: Float, y: Float, z: Float): Int { val l = sqrt(x * x + y * y + z * z); vs += floatArrayOf(x / l, y / l, z / l); return vs.size - 1 }
        add(-1f, t, 0f); add(1f, t, 0f); add(-1f, -t, 0f); add(1f, -t, 0f)
        add(0f, -1f, t); add(0f, 1f, t); add(0f, -1f, -t); add(0f, 1f, -t)
        add(t, 0f, -1f); add(t, 0f, 1f); add(-t, 0f, -1f); add(-t, 0f, 1f)
        var faces = listOf(
            intArrayOf(0, 11, 5), intArrayOf(0, 5, 1), intArrayOf(0, 1, 7), intArrayOf(0, 7, 10), intArrayOf(0, 10, 11),
            intArrayOf(1, 5, 9), intArrayOf(5, 11, 4), intArrayOf(11, 10, 2), intArrayOf(10, 7, 6), intArrayOf(7, 1, 8),
            intArrayOf(3, 9, 4), intArrayOf(3, 4, 2), intArrayOf(3, 2, 6), intArrayOf(3, 6, 8), intArrayOf(3, 8, 9),
            intArrayOf(4, 9, 5), intArrayOf(2, 4, 11), intArrayOf(6, 2, 10), intArrayOf(8, 6, 7), intArrayOf(9, 8, 1),
        )
        repeat(sub) {
            val cache = HashMap<Long, Int>()
            fun mid(a: Int, b: Int): Int {
                val key = if (a < b) (a.toLong() shl 32) or b.toLong() else (b.toLong() shl 32) or a.toLong()
                return cache.getOrPut(key) { val p = vs[a]; val q = vs[b]; add(p[0] + q[0], p[1] + q[1], p[2] + q[2]) }
            }
            val nf = ArrayList<IntArray>(faces.size * 4)
            for (f in faces) {
                val a = mid(f[0], f[1]); val b = mid(f[1], f[2]); val c = mid(f[2], f[0])
                nf += intArrayOf(f[0], a, c); nf += intArrayOf(f[1], b, a); nf += intArrayOf(f[2], c, b); nf += intArrayOf(a, b, c)
            }
            faces = nf
        }
        val rad = FloatArray(vs.size) { i ->
            if (noise == 0f) 1f else {
                val p = vs[i]
                val h = hash2((p[0] * 97f).toInt() + seed * 13, (p[1] * 89f + p[2] * 71f).toInt())
                1f + ((h and 0xFFFF) / 65535f - 0.5f) * 2f * noise
            }
        }
        if (flat) {
            for (f in faces) {
                val p = Array(3) { k -> val v = vs[f[k]]; floatArrayOf(v[0] * rad[f[k]], v[1] * rad[f[k]], v[2] * rad[f[k]]) }
                val ux = p[1][0] - p[0][0]; val uy = p[1][1] - p[0][1]; val uz = p[1][2] - p[0][2]
                val wx = p[2][0] - p[0][0]; val wy = p[2][1] - p[0][1]; val wz = p[2][2] - p[0][2]
                var nx = uy * wz - uz * wy; var ny = uz * wx - ux * wz; var nz = ux * wy - uy * wx
                val l = sqrt(nx * nx + ny * ny + nz * nz); nx /= l; ny /= l; nz /= l
                val cy = (p[0][1] + p[1][1] + p[2][1]) / 3f
                val c = shade?.invoke(nx, ny, nz, cy)
                val i0 = d.vCount
                for (k in 0 until 3) {
                    if (c != null) emit(p[k][0], p[k][1], p[k][2], nx, ny, nz, ((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)
                    else emit(p[k][0], p[k][1], p[k][2], nx, ny, nz)
                }
                d.tri(i0, i0 + 1, i0 + 2)
            }
        } else {
            val base = d.vCount
            for (i in vs.indices) {
                val v = vs[i]; val r = rad[i]
                val c = shade?.invoke(v[0], v[1], v[2], v[1])
                if (c != null) emit(v[0] * r, v[1] * r, v[2] * r, v[0], v[1], v[2], ((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)
                else emit(v[0] * r, v[1] * r, v[2] * r, v[0], v[1], v[2])
            }
            for (f in faces) d.tri(base + f[0], base + f[1], base + f[2])
        }
        return this
    }

    /** Axis-aligned box of size 1 centered at origin (flat normals). */
    fun box(): MeshBuilder {
        val n = arrayOf(
            floatArrayOf(1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f), floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, -1f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, -1f),
        )
        for (f in n) {
            // two tangent axes
            val ax = if (abs(f[1]) > 0.5f) floatArrayOf(1f, 0f, 0f) else floatArrayOf(0f, 1f, 0f)
            val bx = floatArrayOf(f[1] * ax[2] - f[2] * ax[1], f[2] * ax[0] - f[0] * ax[2], f[0] * ax[1] - f[1] * ax[0])
            val i0 = d.vCount
            for ((s, t) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)) {
                emit(
                    (f[0] + ax[0] * s + bx[0] * t) * 0.5f,
                    (f[1] + ax[1] * s + bx[1] * t) * 0.5f,
                    (f[2] + ax[2] * s + bx[2] * t) * 0.5f,
                    f[0], f[1], f[2],
                )
            }
            d.quad(i0, i0 + 1, i0 + 2, i0 + 3)
        }
        return this
    }

    /** Rounded box of size 1 (radius relative to unit size), smooth normals. */
    fun roundedBox(radius: Float = 0.15f, seg: Int = 6): MeshBuilder {
        val r = radius.coerceIn(0.01f, 0.5f)
        val inner = 0.5f - r
        val faces = arrayOf(
            intArrayOf(0, 1, 2, 1), intArrayOf(0, 1, 2, -1),
            intArrayOf(1, 2, 0, 1), intArrayOf(1, 2, 0, -1),
            intArrayOf(2, 0, 1, 1), intArrayOf(2, 0, 1, -1),
        )
        for (f in faces) {
            val base = d.vCount
            for (i in 0..seg) for (j in 0..seg) {
                val p = FloatArray(3)
                p[f[0]] = f[3] * 0.5f
                p[f[1]] = (i.toFloat() / seg - 0.5f)
                p[f[2]] = (j.toFloat() / seg - 0.5f)
                if (f[3] < 0) p[f[1]] = -p[f[1]]
                val c = floatArrayOf(p[0].coerceIn(-inner, inner), p[1].coerceIn(-inner, inner), p[2].coerceIn(-inner, inner))
                var nx = p[0] - c[0]; var ny = p[1] - c[1]; var nz = p[2] - c[2]
                val l = sqrt(nx * nx + ny * ny + nz * nz)
                if (l < 1e-6f) { nx = 0f; ny = 0f; nz = 0f; when (f[0]) { 0 -> nx = f[3].toFloat(); 1 -> ny = f[3].toFloat(); else -> nz = f[3].toFloat() } }
                else { nx /= l; ny /= l; nz /= l }
                emit(c[0] + nx * r, c[1] + ny * r, c[2] + nz * r, nx, ny, nz)
            }
            val row = seg + 1
            for (i in 0 until seg) for (j in 0 until seg) {
                val a = base + i * row + j
                d.quad(a, a + row, a + row + 1, a + 1)
            }
        }
        return this
    }

    /** Cylinder radius 1 from y=0..1. */
    fun cylinder(seg: Int = 16, caps: Boolean = true, flat: Boolean = false, topScale: Float = 1f, capColor: Int? = null): MeshBuilder {
        val base = d.vCount
        for (i in 0..seg) {
            val a = i.toFloat() / seg * TAU
            val x = cos(a); val z = sin(a)
            val slope = (1f - topScale)
            val nl = sqrt(1f + slope * slope)
            emit(x, 0f, z, x / nl, slope / nl, z / nl)
            emit(x * topScale, 1f, z * topScale, x / nl, slope / nl, z / nl)
        }
        for (i in 0 until seg) {
            val a = base + i * 2
            d.quad(a, a + 1, a + 3, a + 2)
        }
        if (flat) { /* smooth sides are fine for most props */ }
        if (caps) {
            val savedR = cr; val savedG = cg; val savedB = cb
            if (capColor != null) color(capColor)
            val top = emit(0f, 1f, 0f, 0f, 1f, 0f)
            val bot = emit(0f, 0f, 0f, 0f, -1f, 0f)
            val ring = d.vCount
            for (i in 0..seg) {
                val a = i.toFloat() / seg * TAU
                emit(cos(a) * topScale, 1f, sin(a) * topScale, 0f, 1f, 0f)
                emit(cos(a), 0f, sin(a), 0f, -1f, 0f)
            }
            for (i in 0 until seg) {
                d.tri(top, ring + (i + 1) * 2, ring + i * 2)
                d.tri(bot, ring + i * 2 + 1, ring + (i + 1) * 2 + 1)
            }
            cr = savedR; cg = savedG; cb = savedB
        }
        return this
    }

    /** Cone radius 1, height 1 (apex at y=1). Flat shading gives the low-poly look. */
    fun cone(seg: Int = 12, flat: Boolean = true, base: Boolean = true): MeshBuilder {
        for (i in 0 until seg) {
            val a0 = i.toFloat() / seg * TAU; val a1 = (i + 1).toFloat() / seg * TAU
            val x0 = cos(a0); val z0 = sin(a0); val x1 = cos(a1); val z1 = sin(a1)
            if (flat) {
                val am = (a0 + a1) / 2f
                val nx = cos(am); val nz = sin(am); val ny = 1f
                val l = sqrt(nx * nx + ny * ny + nz * nz)
                val i0 = d.vCount
                emit(x0, 0f, z0, nx / l, ny / l, nz / l)
                emit(0f, 1f, 0f, nx / l, ny / l, nz / l)
                emit(x1, 0f, z1, nx / l, ny / l, nz / l)
                d.tri(i0, i0 + 1, i0 + 2)
            } else {
                val l = sqrt(2f)
                val i0 = d.vCount
                emit(x0, 0f, z0, x0 / l, 1f / l, z0 / l)
                emit(0f, 1f, 0f, cos((a0 + a1) / 2) / l, 1f / l, sin((a0 + a1) / 2) / l)
                emit(x1, 0f, z1, x1 / l, 1f / l, z1 / l)
                d.tri(i0, i0 + 1, i0 + 2)
            }
        }
        if (base) {
            val c = emit(0f, 0f, 0f, 0f, -1f, 0f)
            val ring = d.vCount
            for (i in 0..seg) { val a = i.toFloat() / seg * TAU; emit(cos(a), 0f, sin(a), 0f, -1f, 0f) }
            for (i in 0 until seg) d.tri(c, ring + i, ring + i + 1)
        }
        return this
    }

    /** Torus in XZ plane; major radius R, minor radius r. */
    fun torus(R: Float = 1f, r: Float = 0.3f, segU: Int = 20, segV: Int = 8, arc: Float = TAU): MeshBuilder {
        val base = d.vCount
        for (u in 0..segU) {
            val a = u.toFloat() / segU * arc
            val ca = cos(a); val sa = sin(a)
            for (v in 0..segV) {
                val b = v.toFloat() / segV * TAU
                val cb2 = cos(b); val sb = sin(b)
                val nx = ca * cb2; val ny = sb; val nz = sa * cb2
                emit((R + r * cb2) * ca, r * sb, (R + r * cb2) * sa, nx, ny, nz)
            }
        }
        val row = segV + 1
        for (u in 0 until segU) for (v in 0 until segV) {
            val a = base + u * row + v
            d.quad(a, a + 1, a + row + 1, a + row)
        }
        return this
    }

    /** Flat double-sided triangle flag in XY plane (pole at x=0). */
    fun flagTri(): MeshBuilder {
        val i0 = d.vCount
        emit(0f, 0f, 0f, 0f, 0f, 1f); emit(1f, -0.25f, 0f, 0f, 0f, 1f); emit(0f, -0.5f, 0f, 0f, 0f, 1f)
        d.tri(i0, i0 + 2, i0 + 1)
        val i1 = d.vCount
        emit(0f, 0f, 0f, 0f, 0f, -1f); emit(1f, -0.25f, 0f, 0f, 0f, -1f); emit(0f, -0.5f, 0f, 0f, 0f, -1f)
        d.tri(i1, i1 + 1, i1 + 2)
        return this
    }

    /** Horizontal quad in XZ (size 1, centered), facing +Y. */
    fun quadXZ(): MeshBuilder {
        val i0 = d.vCount
        emit(-0.5f, 0f, -0.5f, 0f, 1f, 0f); emit(0.5f, 0f, -0.5f, 0f, 1f, 0f)
        emit(0.5f, 0f, 0.5f, 0f, 1f, 0f); emit(-0.5f, 0f, 0.5f, 0f, 1f, 0f)
        d.quad(i0, i0 + 3, i0 + 2, i0 + 1)
        return this
    }

    fun build(instanced: Boolean = true): Mesh = Mesh(d, instanced)

    companion object {
        fun mix(a: Int, b: Int, t: Float): Int {
            val k = t.coerceIn(0f, 1f)
            val r = (((a shr 16) and 0xFF) * (1 - k) + ((b shr 16) and 0xFF) * k).toInt()
            val g = (((a shr 8) and 0xFF) * (1 - k) + ((b shr 8) and 0xFF) * k).toInt()
            val bl = ((a and 0xFF) * (1 - k) + (b and 0xFF) * k).toInt()
            return (r shl 16) or (g shl 8) or bl
        }

        fun shade(c: Int, f: Float): Int {
            val r = min(255, max(0, (((c shr 16) and 0xFF) * f).toInt()))
            val g = min(255, max(0, (((c shr 8) and 0xFF) * f).toInt()))
            val b = min(255, max(0, ((c and 0xFF) * f).toInt()))
            return (r shl 16) or (g shl 8) or b
        }

        @Suppress("unused")
        private fun sgn(v: Float) = sign(v)
    }
}
