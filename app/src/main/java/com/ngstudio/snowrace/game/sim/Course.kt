package com.ngstudio.snowrace.game.sim

import com.ngstudio.snowrace.level.LevelSpec

enum class SegKind { START, FIELD, GAP, FINISH, FINALE }

class Segment(
    val kind: SegKind,
    val index: Int,
    val z0: Float,
    val z1: Float,
    val y0: Float,
    val y1: Float,
) {
    val length: Float get() = z1 - z0
    fun yAt(z: Float): Float = if (z1 <= z0) y0 else y0 + (y1 - y0) * ((z - z0) / (z1 - z0)).coerceIn(0f, 1f)
}

/**
 * World-space layout of a level along +Z:
 * START pad -> (FIELD i -> GAP i) x stages -> FINISH platform -> FINALE stairs.
 */
class Course(val spec: LevelSpec) {
    val width: Float = spec.width
    val halfW: Float = width / 2f
    val segments = ArrayList<Segment>()
    val fields = ArrayList<Segment>()
    val gaps = ArrayList<Segment>()
    val start: Segment
    val finish: Segment
    val finale: Segment
    val finishLineZ: Float
    val finishY: Float

    companion object {
        const val START_LEN = 6f
        const val STEP_DEPTH = 1.7f
        const val STEP_RISE = 0.42f
        const val STEPS = 5
    }

    init {
        var z = -START_LEN
        var y = 0f
        start = Segment(SegKind.START, 0, z, 0f, 0f, 0f)
        segments += start
        z = 0f
        spec.stages.forEachIndexed { i, st ->
            val f = Segment(SegKind.FIELD, i, z, z + st.length, y, y)
            fields += f; segments += f
            z += st.length
            val g = Segment(SegKind.GAP, i, z, z + st.gap, y, y + st.rise)
            gaps += g; segments += g
            z += st.gap
            y += st.rise
        }
        finish = Segment(SegKind.FINISH, 0, z, z + spec.finishLength, y, y)
        segments += finish
        finishLineZ = z + 3.2f
        finishY = y
        z += spec.finishLength
        finale = Segment(SegKind.FINALE, 0, z, z + STEPS * STEP_DEPTH + 6f, y, y + STEPS * STEP_RISE)
        segments += finale
    }

    val totalLength: Float get() = finishLineZ - start.z0

    fun locate(z: Float): Segment {
        if (z < segments[0].z0) return segments[0]
        for (s in segments) if (z >= s.z0 && z < s.z1) return s
        return segments.last()
    }

    /** Ground height for non-gap segments (gaps depend on bridges). */
    fun groundY(z: Float): Float {
        val s = locate(z)
        return when (s.kind) {
            SegKind.FINALE -> finaleStepY(z)
            SegKind.GAP -> s.yAt(z)
            else -> s.y0
        }
    }

    fun finaleStepIndex(z: Float): Int {
        val d = z - finale.z0
        if (d < 0f) return 0
        return (d / STEP_DEPTH).toInt().plus(1).coerceAtMost(STEPS)
    }

    fun finaleStepY(z: Float): Float = finishY + finaleStepIndex(z) * STEP_RISE

    fun stepZ0(k: Int): Float = finale.z0 + (k - 1) * STEP_DEPTH
}
