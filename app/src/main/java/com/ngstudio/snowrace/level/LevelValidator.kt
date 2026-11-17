package com.ngstudio.snowrace.level

import com.ngstudio.snowrace.data.Catalog
import com.ngstudio.snowrace.game.sim.BridgeGrid
import com.ngstudio.snowrace.game.sim.Phase
import com.ngstudio.snowrace.game.sim.PlayerSetup
import com.ngstudio.snowrace.game.sim.RaceSim
import kotlin.math.ceil
import kotlin.math.sqrt

data class ValidationReport(
    val level: Int,
    val ok: Boolean,
    val reasons: List<String>,
    val autopilotRank: Int,
    val raceTime: Float,
)

/**
 * Proves a level is fair and solvable:
 *  1. enough reachable snow on every stage to bridge its gap (with racers competing),
 *  2. an open path from every field start to its shore,
 *  3. hazards/props never crowd spawn or landing zones and never overlap,
 *  4. a headless autopilot actually finishes on the podium (levels 1-10: wins with a casual skill).
 */
object LevelValidator {

    fun staticCheck(spec: LevelSpec): List<String> {
        val reasons = ArrayList<String>()
        val sim = RaceSim(spec, setup(null), headless = true)
        val racers = spec.rivals.size + 1
        spec.stages.forEachIndexed { i, st ->
            val rows = ceil(st.gap / BridgeGrid.CELL).toInt()
            val need = rows * 3.1f * RaceSim.COST
            val have = sim.fields[i].initialTotal * RaceSim.GAIN
            val required = need * (1.8f + 0.25f * (racers - 1))
            if (have < required) reasons += "stage ${i + 1}: snow ${have.toInt()} < required ${required.toInt()}"
            if (!pathOpen(spec, i)) reasons += "stage ${i + 1}: no open path to shore"
            for (h in st.hazards) {
                if (h.z < 3f || h.z > st.length - 3f) reasons += "stage ${i + 1}: hazard ${h.type} too close to spawn/shore"
            }
            for (a in st.obstacles.indices) {
                val o = st.obstacles[a]
                if (o.z < 2f || o.z > st.length - 2f) reasons += "stage ${i + 1}: prop too close to edge"
                if (kotlin.math.abs(o.x) > spec.width / 2f - 0.8f) reasons += "stage ${i + 1}: prop out of bounds"
                for (b in a + 1 until st.obstacles.size) {
                    val p = st.obstacles[b]
                    val d = sqrt((o.x - p.x) * (o.x - p.x) + (o.z - p.z) * (o.z - p.z))
                    if (d < o.r + p.r + 0.5f) reasons += "stage ${i + 1}: props overlap"
                }
            }
        }
        return reasons
    }

    /** BFS over a 0.5 m grid: is there a >= 2 m wide opening from field start to the shore? */
    fun pathOpen(spec: LevelSpec, stage: Int): Boolean {
        val st = spec.stages[stage]
        val cell = 0.5f
        val cols = ceil(spec.width / cell).toInt()
        val rows = ceil(st.length / cell).toInt()
        val blocked = BooleanArray(cols * rows)
        val halfW = spec.width / 2f
        for (r in 0 until rows) for (c in 0 until cols) {
            val x = -halfW + (c + 0.5f) * cell
            val z = (r + 0.5f) * cell
            var b = false
            for (o in st.obstacles) if ((x - o.x) * (x - o.x) + (z - o.z) * (z - o.z) < (o.r + 0.45f) * (o.r + 0.45f)) b = true
            for (h in st.hazards) {
                if (h.type == HazardType.BUMPER || h.type == HazardType.STEAM) {
                    val R = h.w * 0.5f + 0.3f
                    if ((x - h.x) * (x - h.x) + (z - h.z) * (z - h.z) < R * R) b = true
                }
            }
            blocked[r * cols + c] = b
        }
        val seen = BooleanArray(cols * rows)
        val queue = ArrayDeque<Int>()
        for (c in 0 until cols) if (!blocked[c]) { seen[c] = true; queue.add(c) }
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val r = i / cols; val c = i % cols
            val n = intArrayOf(i - 1, i + 1, i - cols, i + cols)
            val ok = booleanArrayOf(c > 0, c < cols - 1, r > 0, r < rows - 1)
            for (k in 0 until 4) if (ok[k] && !blocked[n[k]] && !seen[n[k]]) { seen[n[k]] = true; queue.add(n[k]) }
        }
        var reach = 0
        for (c in 0 until cols) if (seen[(rows - 1) * cols + c]) reach++
        return reach >= 4
    }

    fun simulate(spec: LevelSpec, skill: Float, dt: Float = 1f / 30f, maxTime: Float = 260f): Pair<Int, Float> {
        val sim = RaceSim(spec, setup(skill), headless = true)
        var t = 0f
        while (t < maxTime && sim.phase != Phase.DONE) {
            sim.step(dt)
            sim.events.clear()
            t += dt
        }
        val rank = if (sim.player.finishRank > 0) sim.player.finishRank else 99
        return rank to sim.raceTime
    }

    fun validate(spec: LevelSpec): ValidationReport {
        val reasons = ArrayList(staticCheck(spec))
        val casual = spec.number <= 10
        val (rank, time) = simulate(spec, if (casual) 0.5f else 0.85f)
        if (casual) {
            if (rank != 1) reasons += "casual autopilot rank $rank (must win)"
        } else if (rank > 3) reasons += "autopilot rank $rank (needs podium)"
        if (time > 240f) reasons += "race too long (${time.toInt()}s)"
        return ValidationReport(spec.number, reasons.isEmpty(), reasons, rank, time)
    }

    private fun setup(skill: Float?) = PlayerSetup(
        name = "Bot",
        character = Catalog.character("pip"),
        ball = Catalog.ball("classic"),
        trail = Catalog.trail("none"),
        autopilotSkill = skill,
    )
}
