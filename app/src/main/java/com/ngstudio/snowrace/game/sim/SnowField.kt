package com.ngstudio.snowrace.game.sim

import com.ngstudio.snowrace.core.Rng
import com.ngstudio.snowrace.core.clamp
import com.ngstudio.snowrace.core.smoothstep
import com.ngstudio.snowrace.level.SnowStyle
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Deformable snow layer for one stage. Each 0.25 m cell stores snow depth
 * (0 = packed/carved, 1 = fresh, up to 1.4 for berms pushed up beside grooves).
 * The same grid drives gameplay (how much snow a ball collects) and the GPU heightmap.
 */
class SnowField(
    val index: Int,
    val x0: Float,
    val z0: Float,
    val width: Float,
    val length: Float,
    val baseY: Float,
) {
    val cell = CELL
    val cols = ceil(width / cell).toInt()
    val rows = ceil(length / cell).toInt()
    val depth = FloatArray(cols * rows)
    private val carvedAt = FloatArray(cols * rows) { -1000f }
    val blocked = BooleanArray(cols * rows)

    /** Rows changed since last GPU upload (inclusive range); -1 when clean. */
    var dirtyMin = 0
    var dirtyMax = rows - 1
    private var regenAcc = 0f
    var initialTotal = 0f
        private set

    companion object {
        const val CELL = 0.25f
        const val SNOW_H = 0.2f
        const val MAX_DEPTH = 1.4f
    }

    fun idx(c: Int, r: Int) = r * cols + c
    fun colOf(x: Float) = floor((x - x0) / cell).toInt()
    fun rowOf(z: Float) = floor((z - z0) / cell).toInt()
    fun contains(x: Float, z: Float) = x >= x0 && x < x0 + width && z >= z0 && z < z0 + length

    fun markDirty(r0: Int, r1: Int) {
        val a = clamp(r0, 0, rows - 1)
        val b = clamp(r1, 0, rows - 1)
        if (dirtyMin < 0) { dirtyMin = a; dirtyMax = b } else { dirtyMin = min(dirtyMin, a); dirtyMax = max(dirtyMax, b) }
    }

    fun clearDirty() { dirtyMin = -1; dirtyMax = -1 }

    /** Fills the field according to a style. Blocked cells (props, ice) stay empty. */
    fun generate(style: SnowStyle, density: Float, rng: Rng) {
        val seedA = rng.range(0f, 100f)
        val seedB = rng.range(0f, 100f)
        val islands = Array(7) { floatArrayOf(rng.range(0.15f, 0.85f), rng.range(0.15f, 0.85f), rng.range(0.12f, 0.22f)) }
        var total = 0f
        for (r in 0 until rows) {
            val v = (r + 0.5f) / rows
            for (c in 0 until cols) {
                val u = (c + 0.5f) / cols
                val i = idx(c, r)
                if (blocked[i]) { depth[i] = 0f; continue }
                val n = noise(u * 6f + seedA, v * 9f + seedB)
                var d = when (style) {
                    SnowStyle.UNIFORM -> 0.92f + 0.08f * n
                    SnowStyle.PATCHES -> smoothstep(0.32f, 0.55f, n) * 0.85f + 0.25f
                    SnowStyle.STRIPES -> 0.35f + 0.65f * smoothstep(-0.2f, 0.4f, sin(u * 18.85f + n * 2f))
                    SnowStyle.ISLANDS -> {
                        var m = 0.18f
                        for (isl in islands) {
                            val dx = (u - isl[0]) * width
                            val dz = (v - isl[1]) * length
                            val dist = sqrt(dx * dx + dz * dz) / (isl[2] * (width + length) * 0.5f)
                            m = max(m, 1f - smoothstep(0.7f, 1.05f, dist))
                        }
                        m
                    }
                    SnowStyle.EDGES -> 0.35f + 0.65f * smoothstep(0.18f, 0.42f, abs(u - 0.5f))
                    SnowStyle.CENTER -> 0.35f + 0.65f * (1f - smoothstep(0.18f, 0.4f, abs(u - 0.5f)))
                }
                d = clamp(d * density, 0f, 1f)
                // soft edge falloff at the field front/back borders
                val edge = smoothstep(0f, 0.035f, v) * smoothstep(0f, 0.03f, 1f - v)
                d *= 0.35f + 0.65f * edge
                depth[i] = d
                total += d
            }
        }
        initialTotal = total
        dirtyMin = 0; dirtyMax = rows - 1
    }

    private fun noise(x: Float, y: Float): Float {
        // cheap smooth value noise (deterministic)
        val xi = floor(x).toInt(); val yi = floor(y).toInt()
        val xf = x - xi; val yf = y - yi
        val u = xf * xf * (3 - 2 * xf); val v = yf * yf * (3 - 2 * yf)
        fun h(a: Int, b: Int): Float = ((com.ngstudio.snowrace.core.hash2(a, b) and 0xFFFF) / 65535f)
        val a = h(xi, yi); val b = h(xi + 1, yi); val c = h(xi, yi + 1); val d = h(xi + 1, yi + 1)
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
    }

    fun blockCircle(cx: Float, cz: Float, r: Float) {
        val c0 = colOf(cx - r); val c1 = colOf(cx + r)
        val r0 = rowOf(cz - r); val r1 = rowOf(cz + r)
        for (rr in max(0, r0)..min(rows - 1, r1)) for (cc in max(0, c0)..min(cols - 1, c1)) {
            val px = x0 + (cc + 0.5f) * cell - cx
            val pz = z0 + (rr + 0.5f) * cell - cz
            if (px * px + pz * pz <= r * r) { blocked[idx(cc, rr)] = true; depth[idx(cc, rr)] = 0f }
        }
    }

    fun blockRect(cx: Float, cz: Float, w: Float, d: Float) {
        val c0 = colOf(cx - w / 2); val c1 = colOf(cx + w / 2)
        val r0 = rowOf(cz - d / 2); val r1 = rowOf(cz + d / 2)
        for (rr in max(0, r0)..min(rows - 1, r1)) for (cc in max(0, c0)..min(cols - 1, c1)) {
            blocked[idx(cc, rr)] = true; depth[idx(cc, rr)] = 0f
        }
    }

    /** Bilinear depth sample (for feet/ball height). */
    fun depthAt(x: Float, z: Float): Float {
        val fx = (x - x0) / cell - 0.5f
        val fz = (z - z0) / cell - 0.5f
        val c = floor(fx).toInt(); val r = floor(fz).toInt()
        val tx = fx - c; val tz = fz - r
        fun d(cc: Int, rr: Int): Float = depth[idx(clamp(cc, 0, cols - 1), clamp(rr, 0, rows - 1))]
        val a = d(c, r); val b = d(c + 1, r); val e = d(c, r + 1); val f = d(c + 1, r + 1)
        return (a + (b - a) * tx) * (1 - tz) + (e + (f - e) * tx) * tz
    }

    fun surfaceY(x: Float, z: Float): Float = baseY + depthAt(x, z) * SNOW_H

    /**
     * Collects snow inside a circle; carved cells become grooves and snow is pushed into
     * side berms perpendicular to [mx],[mz] (normalized motion dir). Returns depth units collected.
     */
    fun collect(cx: Float, cz: Float, radius: Float, now: Float, mx: Float, mz: Float): Float {
        val outer = radius + 0.38f
        val c0 = max(0, colOf(cx - outer)); val c1 = min(cols - 1, colOf(cx + outer))
        val r0 = max(0, rowOf(cz - outer)); val r1 = min(rows - 1, rowOf(cz + outer))
        if (c0 > c1 || r0 > r1) return 0f
        var got = 0f
        val rad2 = radius * radius
        val out2 = outer * outer
        for (rr in r0..r1) {
            val pz = z0 + (rr + 0.5f) * cell - cz
            for (cc in c0..c1) {
                val i = rr * cols + cc
                if (blocked[i]) continue
                val px = x0 + (cc + 0.5f) * cell - cx
                val d2 = px * px + pz * pz
                if (d2 <= rad2) {
                    val dv = depth[i]
                    if (dv > 0.02f) {
                        got += dv
                        depth[i] = 0f
                        carvedAt[i] = now
                    }
                } else if (d2 <= out2) {
                    // berm: only to the sides of motion
                    val inv = 1f / sqrt(d2)
                    val side = abs(px * mz - pz * mx) * inv
                    if (side > 0.55f && depth[i] > 0.3f) {
                        depth[i] = min(MAX_DEPTH, depth[i] + 0.05f)
                    }
                }
            }
        }
        if (got > 0f) markDirty(r0, r1)
        return got
    }

    /** Wipes snow (plow, mega-ball) without returning it. */
    fun wipe(cx: Float, cz: Float, w: Float, d: Float, now: Float) {
        val c0 = max(0, colOf(cx - w / 2)); val c1 = min(cols - 1, colOf(cx + w / 2))
        val r0 = max(0, rowOf(cz - d / 2)); val r1 = min(rows - 1, rowOf(cz + d / 2))
        if (c0 > c1 || r0 > r1) return
        var changed = false
        for (rr in r0..r1) for (cc in c0..c1) {
            val i = rr * cols + cc
            if (depth[i] > 0.05f) { depth[i] = 0f; carvedAt[i] = now; changed = true }
        }
        if (changed) markDirty(r0, r1)
    }

    /** Refills rows below z with fresh snow (avalanche deposit). */
    fun refillRows(zFrom: Float, zTo: Float, amount: Float) {
        val r0 = max(0, rowOf(zFrom)); val r1 = min(rows - 1, rowOf(zTo))
        if (r0 > r1) return
        for (rr in r0..r1) for (cc in 0 until cols) {
            val i = rr * cols + cc
            if (!blocked[i]) depth[i] = max(depth[i], amount)
        }
        markDirty(r0, r1)
    }

    /** Slow regrowth of carved snow so no stage can ever run completely dry. */
    fun regen(dt: Float, now: Float) {
        regenAcc += dt
        if (regenAcc < 0.5f) return
        val step = regenAcc * 0.028f
        regenAcc = 0f
        var minR = -1; var maxR = -1
        for (r in 0 until rows) {
            val base = r * cols
            var rowChanged = false
            for (c in 0 until cols) {
                val i = base + c
                if (!blocked[i] && depth[i] < 0.9f && now - carvedAt[i] > 7f) {
                    depth[i] = min(0.9f, depth[i] + step)
                    rowChanged = true
                }
            }
            if (rowChanged) { if (minR < 0) minR = r; maxR = r }
        }
        if (minR >= 0) markDirty(minR, maxR)
    }

    /** Average depth around a point sampled on a coarse lattice (for AI target picking). */
    fun densityAround(cx: Float, cz: Float, r: Float): Float {
        var sum = 0f; var n = 0
        var dz = -r
        while (dz <= r) {
            var dx = -r
            while (dx <= r) {
                val x = cx + dx; val z = cz + dz
                if (contains(x, z)) {
                    sum += depth[idx(colOf(x), rowOf(z))]
                }
                n++
                dx += 0.5f
            }
            dz += 0.5f
        }
        return if (n == 0) 0f else sum / n
    }

    fun remaining(): Float {
        var s = 0f
        for (d in depth) s += d
        return s
    }
}
