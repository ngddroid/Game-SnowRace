package com.ngstudio.snowrace.game.sim

import com.ngstudio.snowrace.core.clamp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Free-form shared bridge cells over one water gap (0.5 m grid).
 * Racers pack their snowball into cells; anyone can walk on solid cells.
 * When a builder reaches the far shore, their cells start crumbling shortly after.
 */
class BridgeGrid(
    val index: Int,
    val x0: Float,
    val z0: Float,
    val width: Float,
    val length: Float,
    val y0: Float,
    val y1: Float,
) {
    val cell = CELL
    val cols = ceil(width / cell).toInt()
    val rows = max(1, ceil(length / cell).toInt())
    val state = ByteArray(cols * rows)          // EMPTY / SOLID / CRUMBLING
    val owner = ByteArray(cols * rows)
    val placedAt = FloatArray(cols * rows)
    val crumbleAt = FloatArray(cols * rows) { Float.MAX_VALUE }
    var version = 0
        private set
    var solidCount = 0
        private set

    private val candIdx = IntArray(64)
    private val candDist = FloatArray(64)

    companion object {
        const val CELL = 0.5f
        const val EMPTY: Byte = 0
        const val SOLID: Byte = 1
        const val CRUMBLING: Byte = 2
        const val CRUMBLE_DELAY = 3.2f
        const val CRUMBLE_SHAKE = 0.55f
    }

    fun idx(c: Int, r: Int) = r * cols + c
    fun colOf(x: Float) = floor((x - x0) / cell).toInt()
    fun rowOf(z: Float) = floor((z - z0) / cell).toInt()
    fun cellX(c: Int) = x0 + (c + 0.5f) * cell
    fun cellZ(r: Int) = z0 + (r + 0.5f) * cell
    fun heightAt(z: Float): Float = y0 + (y1 - y0) * clamp((z - z0) / length, 0f, 1f)
    fun inside(x: Float, z: Float) = z >= z0 && z < z0 + length && x >= x0 && x < x0 + width

    fun isSolid(c: Int, r: Int): Boolean {
        if (c < 0 || r < 0 || c >= cols || r >= rows) return false
        return state[idx(c, r)] != EMPTY
    }

    /** True if a racer standing at x,z is supported (with a small tolerance). */
    fun solidAt(x: Float, z: Float, tol: Float = 0.2f): Boolean {
        if (z < z0 || z >= z0 + length) return true
        val c = colOf(x); val r = rowOf(z)
        if (isSolid(c, r)) return true
        if (tol <= 0f) return false
        return isSolid(colOf(x + tol), r) || isSolid(colOf(x - tol), r) ||
            isSolid(c, rowOf(z + tol)) || isSolid(c, rowOf(z - tol))
    }

    /**
     * Packs snow into empty cells under a disk (nearest cells first).
     * Returns the snow spent; each newly placed cell is reported through [onPlaced].
     */
    inline fun fillDisk(
        cx: Float, cz: Float, radius: Float, ownerId: Int, now: Float, available: Float, cost: Float,
        onPlaced: (c: Int, r: Int, n: Int) -> Unit,
    ): Float = fillDiskImpl(cx, cz, radius, ownerId, now, available, cost).also { spent ->
        val placed = lastPlacedCount
        for (k in 0 until placed) onPlaced(lastPlacedC[k], lastPlacedR[k], k)
    }

    val lastPlacedC = IntArray(64)
    val lastPlacedR = IntArray(64)
    var lastPlacedCount = 0

    fun fillDiskImpl(cx: Float, cz: Float, radius: Float, ownerId: Int, now: Float, available: Float, cost: Float): Float {
        lastPlacedCount = 0
        val c0 = max(0, colOf(cx - radius)); val c1 = min(cols - 1, colOf(cx + radius))
        val r0 = max(0, rowOf(cz - radius)); val r1 = min(rows - 1, rowOf(cz + radius))
        if (c0 > c1 || r0 > r1) return 0f
        var n = 0
        val rad2 = radius * radius
        for (r in r0..r1) for (c in c0..c1) {
            val i = idx(c, r)
            if (state[i] != EMPTY) continue
            val dx = cellX(c) - cx; val dz = cellZ(r) - cz
            val d2 = dx * dx + dz * dz
            if (d2 > rad2) continue
            if (n >= candIdx.size) break
            // insertion sort by distance
            var k = n
            while (k > 0 && candDist[k - 1] > d2) { candIdx[k] = candIdx[k - 1]; candDist[k] = candDist[k - 1]; k-- }
            candIdx[k] = i; candDist[k] = d2
            n++
        }
        var spent = 0f
        for (k in 0 until n) {
            if (available - spent < cost) break
            val i = candIdx[k]
            state[i] = SOLID
            owner[i] = ownerId.toByte()
            placedAt[i] = now
            crumbleAt[i] = Float.MAX_VALUE
            spent += cost
            solidCount++
            if (lastPlacedCount < lastPlacedC.size) {
                lastPlacedC[lastPlacedCount] = i % cols
                lastPlacedR[lastPlacedCount] = i / cols
                lastPlacedCount++
            }
        }
        if (spent > 0f) version++
        return spent
    }

    /** Schedules all cells of [ownerId] to crumble, oldest first. */
    fun scheduleCrumble(ownerId: Int, now: Float) {
        var changed = false
        for (i in state.indices) {
            if (state[i] == SOLID && owner[i].toInt() == ownerId && crumbleAt[i] == Float.MAX_VALUE) {
                val r = i / cols
                crumbleAt[i] = now + CRUMBLE_DELAY + r * 0.09f
                changed = true
            }
        }
        if (changed) version++
    }

    /** Advances crumbling; returns via callback each cell that just fell. */
    inline fun update(now: Float, onFall: (c: Int, r: Int) -> Unit) {
        for (i in state.indices) {
            val s = state[i]
            if (s == EMPTY) continue
            val t = crumbleAt[i]
            if (t == Float.MAX_VALUE) continue
            if (s == SOLID && now >= t - CRUMBLE_SHAKE) {
                state[i] = CRUMBLING; bump()
            } else if (s == CRUMBLING && now >= t) {
                state[i] = EMPTY
                crumbleAt[i] = Float.MAX_VALUE
                decSolid()
                bump()
                onFall(i % cols, i / cols)
            }
        }
    }

    fun bump() { version++ }
    fun decSolid() { solidCount-- }

    /** Number of rows not yet solid along the column at x (AI cost estimate). */
    fun missingRowsAt(x: Float): Int {
        val c = colOf(x)
        if (c < 0 || c >= cols) return rows
        var missing = 0
        for (r in 0 until rows) if (state[idx(c, r)] == EMPTY) missing++
        return missing
    }

    /** True when the column at x is solid end to end and not about to crumble. */
    fun columnComplete(x: Float, now: Float, safety: Float): Boolean {
        val c = colOf(x)
        if (c < 1 || c >= cols - 1) return false
        for (r in 0 until rows) {
            val i = idx(c, r)
            if (state[i] != SOLID) return false
            if (crumbleAt[i] != Float.MAX_VALUE && crumbleAt[i] - now < safety) return false
        }
        return true
    }

    /** Owner's partial bridge column (most solid rows), or NaN. */
    fun ownColumnX(ownerId: Int): Float {
        var best = -1; var bestCount = 0
        for (c in 0 until cols) {
            var count = 0
            for (r in 0 until rows) {
                val i = idx(c, r)
                if (state[i] == SOLID && owner[i].toInt() == ownerId) count++
            }
            if (count > bestCount) { bestCount = count; best = c }
        }
        return if (best < 0) Float.NaN else cellX(best)
    }
}
