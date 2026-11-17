package com.ngstudio.snowrace.game.sim

import com.ngstudio.snowrace.core.Rng
import com.ngstudio.snowrace.core.TAU
import com.ngstudio.snowrace.level.HazardType
import com.ngstudio.snowrace.level.Personality
import com.ngstudio.snowrace.level.PickupType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rival AI. Collects snow on promising patches, decides when its ball is big enough to bridge,
 * picks a crossing point (own partial bridge, other racers' leftovers, or a free lane), may bonk
 * smaller racers, hitchhikes on intact bridges, and avoids hazards. Also used as the autopilot.
 */
class AiBrain(
    val me: Racer,
    val skill: Float,
    val aggression: Float,
    val personality: Personality,
    seed: Long,
    private val assistMode: Boolean,
    val isAutopilot: Boolean = false,
) {
    private enum class Mode { COLLECT, BRIDGE, ATTACK }

    private val rng = Rng(seed)
    private var mode = Mode.COLLECT
    private var tx = 0f
    private var tz = 0f
    private var retarget = 0f
    private var bridgeX = Float.NaN
    private var bridgeGap = -1
    private var hitch = false
    private var attackId = -1
    private var attackT = 0f
    private var attackCd = 3f + rng.range(0f, 3f)
    private var stuckClock = 0f
    private var stuckX = 0f
    private var stuckZ = 0f
    private var detourT = 0f
    private val wobblePhase = rng.range(0f, TAU)
    private val margin: Float = when (personality) {
        Personality.COLLECTOR -> 1.42f
        Personality.RUSHER -> 1.06f
        Personality.BULLY -> 1.2f
        Personality.BALANCED -> 1.16f
    } + (if (assistMode && !isAutopilot) 0.3f else 0f) - skill * 0.04f
    private val aggro: Float = (aggression + if (personality == Personality.BULLY) 0.25f else 0f).coerceIn(0f, 1f)
    private var stageClock = 0f

    fun think(sim: RaceSim, dt: Float) {
        if (me.state != RState.RUN) { me.inMag = 0f; return }
        attackCd -= dt
        retarget -= dt
        detourT -= dt
        val c = sim.course
        val seg = c.locate(me.z)
        var mag = 1f
        when (seg.kind) {
            SegKind.START -> { tx = me.x * 0.85f; tz = c.fields[0].z0 + 3f; mode = Mode.COLLECT }
            SegKind.FIELD -> fieldLogic(sim, seg.index, dt)
            SegKind.GAP -> gapLogic(sim, seg.index)
            SegKind.FINISH, SegKind.FINALE -> { tx = me.x * 0.6f; tz = c.finishLineZ + 3f }
        }

        var dx = tx - me.x
        var dz = tz - me.z
        val d = sqrt(dx * dx + dz * dz)
        if (d > 1e-3f) { dx /= d; dz /= d } else { dx = 0f; dz = 1f }

        if (mode == Mode.COLLECT && seg.kind == SegKind.FIELD) {
            val w = sin(sim.time * 1.25f + wobblePhase) * 0.38f
            val cw = cos(w); val sw = sin(w)
            val nx = dx * cw - dz * sw
            val nz = dx * sw + dz * cw
            dx = nx; dz = nz
        }

        // Hazard & bully avoidance
        var ax = 0f; var az = 0f
        val awareness = 0.35f + 0.65f * skill
        for (h in sim.hazards) {
            val hz = h.cz - me.z
            if (hz > 5f || hz < -3f) continue
            when (h.type) {
                HazardType.LOG, HazardType.PLOW -> {
                    val band = h.spec.d * 0.5f + 0.6f
                    val ahead = hz > -band && hz < band + 2.2f
                    val rel = me.x - h.x
                    val approaching = h.vx * rel > 0f || abs(rel) < h.spec.w * 0.5f + 0.9f
                    if (ahead && abs(rel) < h.spec.w * 0.5f + 2.6f && approaching) {
                        if (abs(hz) < band) { dz = 1f; dx *= 0.3f } // already in its lane: dash through
                        else if (rng.nextFloat() < awareness) mag = min(mag, 0.12f)
                    }
                }
                HazardType.BUMPER, HazardType.STEAM -> {
                    if (h.type == HazardType.STEAM && h.active < 0.1f && h.warn < 0.2f) continue
                    val rx = me.x - h.cx; val rz = me.z - h.cz
                    val dd = sqrt(rx * rx + rz * rz)
                    val R = h.spec.w * 0.5f + 1.6f + me.ballR
                    if (dd < R && dd > 1e-3f) { val k = (R - dd) / R * 1.6f * awareness; ax += rx / dd * k; az += rz / dd * k }
                }
                HazardType.THIN_ICE -> {
                    if (h.insideRect(me.x + dx * 1.2f, me.z + dz * 1.2f, 0.6f) && skill > 0.35f) {
                        val rx = me.x - h.x
                        ax += (if (rx >= 0f) 1f else -1f) * 1.2f * awareness
                    }
                }
                else -> {}
            }
        }
        for (o in sim.racers) {
            if (o === me || !o.hasBall || o.state != RState.RUN) continue
            if (me.hasBall && o.ballR < me.ballR * 1.2f) continue
            if (o.isPlayer && assistMode && !isAutopilot) continue
            val rx = me.x - o.ballX; val rz = me.z - o.ballZ
            val dd = sqrt(rx * rx + rz * rz)
            val R = o.ballR + 2.8f
            if (dd < R && dd > 1e-3f) { val k = (R - dd) / R * 1.3f * awareness; ax += rx / dd * k; az += rz / dd * k * 0.5f }
        }
        dx += ax; dz += az

        // Stuck detection: if barely moving while trying, pick a fresh detour.
        stuckClock += dt
        if (stuckClock > 1.4f) {
            val moved = sqrt((me.x - stuckX) * (me.x - stuckX) + (me.z - stuckZ) * (me.z - stuckZ))
            if (moved < 0.45f && mag > 0.5f) {
                detourT = 0.8f
                retarget = 0f
                tx = me.x + rng.range(-3f, 3f); tz = me.z + rng.range(-1.5f, 2.5f)
            }
            stuckClock = 0f; stuckX = me.x; stuckZ = me.z
        }
        if (detourT > 0f) {
            val ddx = tx - me.x; val ddz = tz - me.z
            val dd = sqrt(ddx * ddx + ddz * ddz).coerceAtLeast(1e-3f)
            dx = ddx / dd; dz = ddz / dd
        }

        me.inX = dx
        me.inZ = dz
        me.inMag = mag
    }

    private fun fieldLogic(sim: RaceSim, fi: Int, dt: Float) {
        val c = sim.course
        val b = sim.bridges[fi]
        val gap = c.gaps[fi]
        if (bridgeGap != fi) { bridgeGap = fi; bridgeX = Float.NaN; mode = Mode.COLLECT; hitch = false; stageClock = 0f }
        stageClock += dt
        // Time pressure: the longer we linger, the smaller the safety margin we insist on.
        val effMargin = max(1.0f, margin - stageClock * 0.012f)

        // Hitchhike on an intact bridge?
        if (mode != Mode.BRIDGE && !assistMode && !isAutopilot && skill > 0.3f && personality != Personality.COLLECTOR) {
            val eta = (gap.z0 - me.z).coerceAtLeast(0f) / 5f + gap.length / 5f
            var bestX = Float.NaN; var bestD = 7f
            var x = -c.halfW + 1f
            while (x < c.halfW - 1f) {
                val dd = abs(x - me.x)
                if (dd < bestD && b.columnComplete(x, sim.time, eta + 1.2f)) { bestD = dd; bestX = x }
                x += 0.5f
            }
            if (!bestX.isNaN() && rng.nextFloat() < 0.04f + skill * 0.06f) { mode = Mode.BRIDGE; hitch = true; bridgeX = bestX }
        }

        // Bonk opportunities
        if (mode == Mode.COLLECT && aggro > 0.05f && attackCd <= 0f && me.hasBall) {
            var victim: Racer? = null; var bestD = 7f
            for (o in sim.racers) {
                if (o === me || o.state != RState.RUN || o.finished) continue
                if (o.isPlayer && assistMode) continue
                if (o.shieldT > 0f || o.invuln > 0f) continue
                if (o.hasBall && me.ballR <= o.ballR * 1.3f) continue
                val dd = sqrt((o.x - me.ballX) * (o.x - me.ballX) + (o.z - me.ballZ) * (o.z - me.ballZ))
                if (dd < bestD) { bestD = dd; victim = o }
            }
            if (victim != null && rng.nextFloat() < aggro * 0.35f) {
                mode = Mode.ATTACK; attackId = victim.id; attackT = 1.5f + aggro
                attackCd = 7f + rng.range(0f, 5f)
            } else attackCd = 1.5f
        }

        when (mode) {
            Mode.ATTACK -> {
                val v = sim.racers.firstOrNull { it.id == attackId }
                attackT -= dt
                if (v == null || attackT <= 0f || v.state != RState.RUN || v.invuln > 0f || !me.hasBall) { mode = Mode.COLLECT; return }
                tx = v.x + v.vx * 0.35f; tz = v.z + v.vz * 0.35f
            }
            Mode.COLLECT -> {
                val x = chooseBridgeX(sim, b)
                val need = b.missingRowsAt(x) * 3.1f * RaceSim.COST
                if (need <= 0f || me.snow >= need * effMargin) { mode = Mode.BRIDGE; bridgeX = x; hitch = need <= 0f; approach(gap.z0, gap.z1) }
                else collectTarget(sim, fi)
            }
            Mode.BRIDGE -> {
                val need = b.missingRowsAt(bridgeX) * 3.1f * RaceSim.COST
                if (hitch && !b.columnComplete(bridgeX, sim.time, 1.0f) && need > me.snow) { hitch = false; mode = Mode.COLLECT; collectTarget(sim, fi); return }
                if (!hitch && me.snow < need * 0.8f && me.z < gap.z0 - 0.4f) { mode = Mode.COLLECT; collectTarget(sim, fi); return }
                approach(gap.z0, gap.z1)
            }
        }
    }

    private fun approach(z0: Float, z1: Float) {
        val off = Racer.BODY_R + me.ballR * 0.92f
        if (me.z < z0 - 2.4f - off) { tx = bridgeX; tz = z0 - 0.4f }
        else if (abs(me.x - bridgeX) > 0.45f && me.z < z0 - off * 0.6f) { tx = bridgeX; tz = me.z + 0.5f }
        else { tx = bridgeX; tz = z1 + 2.5f }
    }

    private fun gapLogic(sim: RaceSim, gi: Int) {
        val b = sim.bridges[gi]
        val g = sim.course.gaps[gi]
        if (bridgeGap != gi) { bridgeGap = gi; if (bridgeX.isNaN()) bridgeX = me.x }
        val cx = if (bridgeX.isNaN()) me.x else bridgeX
        if (me.hasBall) {
            tx = cx; tz = g.z1 + 2.5f
            return
        }
        val aheadOk = me.z + 0.75f >= g.z1 || b.solidAt(me.x, me.z + 0.75f, 0.2f)
        if (aheadOk) { tx = me.x; tz = g.z1 + 2.5f }
        else {
            // dead end: walk back to collect more snow
            tx = me.x; tz = g.z0 - 3f
            mode = Mode.COLLECT
            hitch = false
            bridgeX = b.ownColumnX(me.id).let { if (it.isNaN()) bridgeX else it }
        }
    }

    private fun chooseBridgeX(sim: RaceSim, b: BridgeGrid): Float {
        if (!bridgeX.isNaN()) return bridgeX
        val own = b.ownColumnX(me.id)
        if (!own.isNaN()) return own
        val c = sim.course
        var best = me.ballX.coerceIn(-c.halfW + 1.2f, c.halfW - 1.2f)
        var bestScore = Float.MAX_VALUE
        var x = -c.halfW + 1.2f
        while (x <= c.halfW - 1.2f) {
            var score = b.missingRowsAt(x) * 1f + abs(x - me.x) * 0.35f
            for (o in sim.racers) if (o !== me && abs(o.x - x) < 1.4f && o.z > b.z0 - 4f) score += 1.5f
            if (score < bestScore) { bestScore = score; best = x }
            x += 0.5f
        }
        return best
    }

    private fun collectTarget(sim: RaceSim, fi: Int) {
        val f = sim.fields[fi]
        val c = sim.course
        val reached = (tx - me.x) * (tx - me.x) + (tz - me.z) * (tz - me.z) < 1.4f * 1.4f
        if (retarget > 0f && !reached && f.contains(tx, tz) && f.densityAround(tx, tz, 0.8f) > 0.22f) return
        retarget = 0.7f + rng.range(0f, 1.1f) * (1.3f - skill)
        // power-ups nearby
        if (skill > 0.4f) {
            for (p in sim.pickups) {
                if (!p.active || p.field != fi) continue
                if (p.type == PickupType.GIFT && !me.isPlayer) continue
                val dd = sqrt((p.x - me.x) * (p.x - me.x) + (p.z - me.z) * (p.z - me.z))
                if (dd < 4.5f && rng.nextFloat() < skill) { tx = p.x; tz = p.z; return }
            }
        }
        var bestScore = -1e9f
        var bx = me.x; var bz = me.z + 2f
        val zMin = f.z0 + 0.8f
        val zMax = f.z0 + f.length - 1.0f
        repeat(10) {
            val cx = rng.range(-c.halfW + 1f, c.halfW - 1f)
            val cz = rng.range(zMin, max(zMin + 0.5f, zMax))
            val dens = f.densityAround(cx, cz, 1.2f)
            val dist = sqrt((cx - me.x) * (cx - me.x) + (cz - me.z) * (cz - me.z))
            var score = dens * 3f - dist * 0.11f + (if (cz > me.z) 0.15f else 0f)
            for (h in sim.hazards) {
                if (h.field != fi) continue
                if (h.type == HazardType.ICE || h.type == HazardType.THIN_ICE) { if (h.insideRect(cx, cz, 0.8f)) score -= 1.5f }
                else if (abs(cz - h.cz) < 1.8f) score -= 0.6f * skill
            }
            score += rng.range(-1f, 1f) * (1f - skill) * 0.6f
            if (score > bestScore) { bestScore = score; bx = cx; bz = cz }
        }
        tx = bx; tz = bz
    }
}
