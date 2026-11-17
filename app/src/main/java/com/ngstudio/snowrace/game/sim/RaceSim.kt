package com.ngstudio.snowrace.game.sim

import com.ngstudio.snowrace.core.Quat
import com.ngstudio.snowrace.core.Rng
import com.ngstudio.snowrace.core.clamp
import com.ngstudio.snowrace.core.clamp01
import com.ngstudio.snowrace.core.damp
import com.ngstudio.snowrace.core.dampAngle
import com.ngstudio.snowrace.data.BallDef
import com.ngstudio.snowrace.data.BoosterIds
import com.ngstudio.snowrace.data.Catalog
import com.ngstudio.snowrace.data.CharacterDef
import com.ngstudio.snowrace.data.TrailDef
import com.ngstudio.snowrace.level.BossType
import com.ngstudio.snowrace.level.HazardType
import com.ngstudio.snowrace.level.LevelSpec
import com.ngstudio.snowrace.level.Personality
import com.ngstudio.snowrace.level.PickupType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

enum class Phase { INTRO, COUNTDOWN, RACE, FINALE, LOST, DONE }

class PlayerSetup(
    val name: String,
    val character: CharacterDef,
    val ball: BallDef,
    val trail: TrailDef,
    val boosters: Set<String> = emptySet(),
    /** When set, the player is driven by an AI brain with this skill (validation / debug autopilot). */
    val autopilotSkill: Float? = null,
)

class RaceSim(val spec: LevelSpec, setup: PlayerSetup, headless: Boolean = false) {
    val course = Course(spec)
    private val rng = Rng(spec.seed * 31L + 7L)
    val fields: List<SnowField>
    val bridges: List<BridgeGrid>
    val colliders = ArrayList<Collider>()
    val hazards = ArrayList<Hazard>()
    val pickups = ArrayList<Pickup>()
    val racers = ArrayList<Racer>()
    val player: Racer
    val events = ArrayList<SimEvent>(64)

    var time = 0f
        private set
    var raceTime = 0f
        private set
    var phase = Phase.INTRO
        private set
    var phaseT = 0f
        private set
    private var finishedCount = 0
    private var countdownShown = 4
    private var endTimer = -1f
    val introDuration = if (headless) 0f else 2.8f
    val assist = spec.assist

    // Bosses
    val avState = IntArray(spec.stages.size)
    val avZ = FloatArray(spec.stages.size)
    private val avT = FloatArray(spec.stages.size)
    val megaActive = BooleanArray(spec.stages.size)
    val megaX = FloatArray(spec.stages.size)
    val megaZ = FloatArray(spec.stages.size)
    val megaRot = FloatArray(spec.stages.size)
    private val megaT = FloatArray(spec.stages.size)

    private val tmpQ = FloatArray(4)
    private var rankTimer = 0f

    companion object {
        const val BASE_SPEED = 5.7f
        const val COST = 15f
        const val GAIN = 1f
        const val BRIDGE_RADIUS = 0.78f
        const val BRIDGE_TOP = 0.2f
        const val WATER_Y = -0.85f
        const val MEGA_R = 2.3f
        val FINALE_COST = floatArrayOf(40f, 90f, 150f, 230f, 330f)
        val FINALE_MULT = floatArrayOf(1f, 1.5f, 2f, 3f, 4f, 5f)
    }

    init {
        val halfW = course.halfW
        fields = course.fields.mapIndexed { i, seg -> SnowField(i, -halfW, seg.z0, course.width, seg.length, seg.y0) }
        bridges = course.gaps.mapIndexed { i, g -> BridgeGrid(i, -halfW, g.z0, course.width, g.length, g.y0, g.y1) }

        spec.stages.forEachIndexed { i, st ->
            val f = fields[i]
            val seg = course.fields[i]
            for (o in st.obstacles) {
                val wx = o.x; val wz = seg.z0 + o.z
                colliders += Collider(o.type, wx, wz, o.r, seg.y0, o.rot, i)
                f.blockCircle(wx, wz, o.r * 0.95f)
            }
            for (h in st.hazards) {
                val hz = Hazard(h, h.x, seg.z0 + h.z, halfW, seg.y0, i)
                hazards += hz
                when (h.type) {
                    HazardType.ICE, HazardType.THIN_ICE -> f.blockRect(h.x, seg.z0 + h.z, h.w, h.d)
                    HazardType.STEAM, HazardType.BUMPER -> f.blockCircle(h.x, seg.z0 + h.z, h.w * 0.5f)
                    else -> {}
                }
            }
            for (p in st.pickups) pickups += Pickup(p.type, p.x, seg.z0 + p.z, seg.y0, i)
            f.generate(st.snowStyle, st.snowDensity, rng)
        }

        // Racers on the start pad
        val total = spec.rivals.size + 1
        val spacing = min(1.9f, (course.width - 2f) / total)
        val playerSlot = total / 2
        val slots = (0 until total).map { -spacing * (total - 1) / 2f + it * spacing }
        player = Racer(0, true, setup.name, setup.character, setup.ball, setup.trail)
        racers += player
        var slot = 0
        spec.rivals.forEachIndexed { i, rv ->
            val r = Racer(i + 1, false, rv.name, Catalog.character(rv.character), Catalog.ball(rv.ball), Catalog.trail("none"))
            r.brain = AiBrain(r, rv.skill, rv.aggression, rv.personality, spec.seed * 101L + i * 7919L, assistMode = assist)
            racers += r
        }
        for (r in racers) {
            val s = if (r.isPlayer) playerSlot else { val v = if (slot == playerSlot) slot + 1 else slot; slot = v + 1; v }
            r.x = slots[s.coerceIn(0, total - 1)]
            r.z = -2.6f
            r.y = 0f
            r.heading = 0f
            r.snow = 6f
            r.respawnX = r.x; r.respawnZ = -2f
            placeBall(r, first = true)
        }
        setup.autopilotSkill?.let { sk ->
            player.brain = AiBrain(player, sk, 0.35f, Personality.BALANCED, spec.seed * 13L + 5L, assistMode = false, isAutopilot = true)
        }
        if (BoosterIds.BIG_START in setup.boosters) { player.snow = 420f; placeBall(player, first = true) }
        if (BoosterIds.TURBO_START in setup.boosters) player.boostT = -1f // armed: applied on GO
        if (BoosterIds.SHIELD_START in setup.boosters) player.shieldT = -1f
    }

    // ------------------------------------------------------------------------------------------
    fun step(dt: Float) {
        time += dt
        phaseT += dt
        when (phase) {
            Phase.INTRO -> if (phaseT >= introDuration) {
                phase = Phase.COUNTDOWN; phaseT = 0f
            }
            Phase.COUNTDOWN -> {
                val n = 3 - floor(phaseT).toInt()
                if (n in 1..3 && n < countdownShown) { countdownShown = n; events += SimEvent.Countdown(n) }
                if (phaseT >= 3f || introDuration == 0f) {
                    phase = Phase.RACE; phaseT = 0f
                    events += SimEvent.Go
                    for (r in racers) { r.state = RState.RUN; r.stateT = 0f }
                    if (player.boostT < 0f) player.boostT = 3.5f
                    if (player.shieldT < 0f) player.shieldT = 8f
                }
            }
            else -> {}
        }

        for (h in hazards) h.update(time, dt)
        if (phase == Phase.RACE || phase == Phase.FINALE || phase == Phase.LOST || phase == Phase.DONE) {
            raceTime += dt
            updateBoss(dt)
            for (b in bridges) {
                b.update(time) { c, r ->
                    events += SimEvent.Crumble(b.cellX(c), b.heightAt(b.cellZ(r)), b.cellZ(r))
                }
            }
            for (f in fields) f.regen(dt, time)
            director()
            for (r in racers) r.brain?.think(this, dt)
            for (r in racers) updateRacer(r, dt)
            collisions()
            updatePickups(dt)
            rankTimer -= dt
            if (rankTimer <= 0f) { updateRanks(); rankTimer = 0.2f }
            checkEnd(dt)
        } else {
            for (r in racers) { r.moveAmt = damp(r.moveAmt, 0f, 6f, dt); placeBall(r, first = false) }
        }
    }

    // ------------------------------------------------------------------------------------------
    private fun director() {
        val p = player
        for (r in racers) {
            if (r.isPlayer) { r.speedMul = 1f; r.gainMul = 1f; continue }
            val sk = r.brain?.skill ?: 0.5f
            var mul = 0.8f + 0.17f * sk
            if (assist) {
                val lead = r.z - p.z
                mul *= when {
                    p.finished -> 1f
                    lead > -1.5f -> 0.5f
                    lead > -6f -> 0.72f
                    else -> 0.9f
                }
                r.gainMul = 0.72f
                r.lockFinish = !p.finished
            } else {
                val lead = r.z - p.z
                if (!p.finished) {
                    if (lead > 14f) mul *= 0.88f else if (lead < -18f) mul *= 1.05f
                }
                r.gainMul = 0.9f + 0.15f * sk
                r.lockFinish = false
            }
            r.speedMul = mul
        }
    }

    // ------------------------------------------------------------------------------------------
    private fun updateRacer(r: Racer, dt: Float) {
        r.stateT += dt
        if (r.invuln > 0f) r.invuln -= dt
        if (r.boostT > 0f) r.boostT -= dt
        if (r.shieldT > 0f) r.shieldT -= dt
        if (r.magnetT > 0f) r.magnetT -= dt
        if (r.dizzyT > 0f) r.dizzyT -= dt
        r.squash = damp(r.squash, 0f, 8f, dt)
        when (r.state) {
            RState.RUN -> moveRunner(r, dt)
            RState.STUN -> {
                r.inMag = 0f
                r.vx = damp(r.vx, 0f, 6f, dt); r.vz = damp(r.vz, 0f, 6f, dt)
                r.x += (r.vx + r.knockVx) * dt
                r.z += (r.vz + r.knockVz) * dt
                r.knockVx = damp(r.knockVx, 0f, 3.5f, dt)
                r.knockVz = damp(r.knockVz, 0f, 3.5f, dt)
                constrain(r)
                placeBall(r, false)
                ground(r)
                if (r.state == RState.STUN && r.stateT >= 1.05f) { r.state = RState.RUN; r.stateT = 0f; r.invuln = max(r.invuln, 1.1f) }
                r.moveAmt = damp(r.moveAmt, 0f, 6f, dt)
            }
            RState.FALL -> {
                val t = r.stateT
                val prevY = r.y
                r.y = r.airY + 1.6f * t - 10f * t * t
                if (prevY > WATER_Y && r.y <= WATER_Y) events += SimEvent.Splash(r.x, WATER_Y, r.z, r.id)
                if (r.y < WATER_Y - 1.2f) r.y = WATER_Y - 1.2f
                if (t >= 1.35f) respawn(r)
            }
            RState.FINISHED -> {
                val dx = r.celebrateX - r.x; val dz = r.celebrateZ - r.z
                val d = sqrt(dx * dx + dz * dz)
                if (d > 0.3f) {
                    val s = min(3.2f, d * 2f) * dt
                    r.x += dx / d * s; r.z += dz / d * s
                    r.heading = dampAngle(r.heading, atan2(dx, dz), 8f, dt)
                    r.moveAmt = damp(r.moveAmt, 0.6f, 8f, dt)
                } else {
                    r.heading = dampAngle(r.heading, atan2(-r.x * 0.3f, -1f), 4f, dt)
                    r.moveAmt = damp(r.moveAmt, 0f, 6f, dt)
                }
                r.stepPhase += r.moveAmt * dt * 9f
                r.y = course.groundY(r.z)
                placeBall(r, false)
            }
            RState.FINALE -> updateFinale(r, dt)
            RState.IDLE -> {}
        }
    }

    private fun moveRunner(r: Racer, dt: Float) {
        var ix = r.inX; var iz = r.inZ
        var mag = clamp01(r.inMag)
        val len = sqrt(ix * ix + iz * iz)
        if (len > 1e-4f) { ix /= len; iz /= len } else mag = 0f
        if (mag > 0.04f) r.heading = dampAngle(r.heading, atan2(ix, iz), if (r.onIce) 6f else 13f, dt)

        val sizeK = clamp01((r.ballR - 0.3f) / 2.0f)
        var spd = BASE_SPEED * r.speedMul * (1f - 0.28f * sizeK) * mag
        if (r.boostT > 0f) spd *= 1.5f
        if (!r.hasBall) spd *= 1.06f
        val tvx = sin(r.heading) * spd
        val tvz = cos(r.heading) * spd
        val accel = if (r.onIce) 1.5f else 11f
        r.vx = damp(r.vx, tvx, accel, dt)
        r.vz = damp(r.vz, tvz, accel, dt)

        val ox = r.x; val oz = r.z
        r.x += (r.vx + r.knockVx) * dt
        r.z += (r.vz + r.knockVz) * dt
        r.knockVx = damp(r.knockVx, 0f, 4f, dt)
        r.knockVz = damp(r.knockVz, 0f, 4f, dt)

        r.onIce = false
        affectHazards(r, dt)
        if (r.state != RState.RUN) return

        if (r.lockFinish && r.z > course.finishLineZ - 2.4f) { r.z = course.finishLineZ - 2.4f; r.vz = 0f }
        constrain(r)
        placeBall(r, false)
        ground(r)
        if (r.state != RState.RUN) return
        collectSnow(r)

        val moved = sqrt((r.x - ox) * (r.x - ox) + (r.z - oz) * (r.z - oz))
        r.speedNow = if (dt > 0f) moved / dt else 0f
        r.moveAmt = damp(r.moveAmt, clamp01(r.speedNow / 4.5f), 10f, dt)
        r.pushing = r.hasBall && r.speedNow > 0.3f
        if (r.hasBall) r.metersRolled += moved
        val before = r.stepPhase
        r.stepPhase += moved * 1.6f
        if (r.isPlayer && floor(before / 3.14159f) != floor(r.stepPhase / 3.14159f)) {
            events += SimEvent.Step(r.id, (floor(r.stepPhase / 3.14159f).toInt() and 1) == 0)
        }

        // Crossed a gap? Start crumbling this racer's bridge cells behind them.
        for (g in course.gaps.indices) {
            val gap = course.gaps[g]
            if (r.z > gap.z1 + 0.6f && r.progress < gap.z1 + 0.6f) {
                bridges[g].scheduleCrumble(r.id, time)
                events += SimEvent.StageEnter(r.id, g + 1)
            }
        }
        r.progress = max(r.progress, r.z)

        if (r.z >= course.finishLineZ) finish(r)
    }

    private fun constrain(r: Racer) {
        val lim = course.halfW - 0.32f
        r.x = clamp(r.x, -lim, lim)
        if (r.z < course.start.z0 + 0.5f) r.z = course.start.z0 + 0.5f
        val zMax = course.finale.z1 - 1f
        if (r.z > zMax) r.z = zMax
        // props
        for (c in colliders) {
            val dz = r.z - c.z
            if (dz > 4f || dz < -4f) continue
            val dx = r.x - c.x
            val d2 = dx * dx + dz * dz
            val minD = c.r + Racer.BODY_R
            if (d2 < minD * minD && d2 > 1e-6f) {
                val d = sqrt(d2)
                val push = (minD - d) / d
                r.x += dx * push; r.z += dz * push
            }
        }
    }

    fun placeBall(r: Racer, first: Boolean) {
        val br = r.ballR
        r.ballVisR = if (first) br else r.ballVisR + (br - r.ballVisR) * 0.35f
        val off = Racer.BODY_R + max(br, 0.2f) * 0.92f
        var bx = r.x + sin(r.heading) * off
        var bz = r.z + cos(r.heading) * off
        if (r.hasBall) {
            val lim = course.halfW - br * 0.55f
            if (bx > lim) { val d = bx - lim; bx -= d; r.x -= d }
            if (bx < -lim) { val d = -lim - bx; bx += d; r.x += d }
            for (c in colliders) {
                val dz = bz - c.z
                if (dz > 5f || dz < -5f) continue
                val dx = bx - c.x
                val d2 = dx * dx + dz * dz
                val minD = c.r + br * 0.9f
                if (d2 < minD * minD && d2 > 1e-6f) {
                    val d = sqrt(d2)
                    val push = (minD - d) / d
                    bx += dx * push; bz += dz * push
                    r.x += dx * push; r.z += dz * push
                }
            }
        }
        if (!first && r.hasBall) {
            val mdx = bx - r.ballX; val mdz = bz - r.ballZ
            val d = sqrt(mdx * mdx + mdz * mdz)
            if (d > 1e-4f && d < 1.5f) {
                Quat.axisAngle(tmpQ, mdz / d, 0f, -mdx / d, d / max(br, 0.2f))
                Quat.mul(r.ballRot, tmpQ, r.ballRot)
                Quat.normalize(r.ballRot)
            }
        }
        r.ballX = bx; r.ballZ = bz
        val seg = course.locate(bz)
        r.ballY = when (seg.kind) {
            SegKind.FIELD -> fields[seg.index].baseY + br * 0.96f
            SegKind.GAP -> bridges[seg.index].heightAt(bz) + BRIDGE_TOP + br * 0.97f
            SegKind.FINALE -> course.finaleStepY(bz) + br
            else -> course.groundY(bz) + br
        }
    }

    private fun ground(r: Racer) {
        val seg = course.locate(r.z)
        when (seg.kind) {
            SegKind.GAP -> {
                val b = bridges[seg.index]
                if (!b.solidAt(r.x, r.z)) { startFall(r, seg.z0 - 1.3f); return }
                r.y = b.heightAt(r.z) + BRIDGE_TOP
            }
            SegKind.FIELD -> {
                val f = fields[seg.index]
                r.y = f.baseY + f.depthAt(r.x, r.z) * SnowField.SNOW_H * 0.55f
            }
            else -> r.y = course.groundY(r.z)
        }
        // Ball over water: pack it into the bridge.
        if (r.hasBall && r.state == RState.RUN) {
            val bseg = course.locate(r.ballZ)
            if (bseg.kind == SegKind.GAP) {
                val b = bridges[bseg.index]
                val before = r.blocks
                val spent = b.fillDisk(r.ballX, r.ballZ, BRIDGE_RADIUS, r.id, time, r.snow, COST) { c, rr, n ->
                    events += SimEvent.Block(b.cellX(c), b.heightAt(b.cellZ(rr)), b.cellZ(rr), r.id, before + n)
                }
                r.blocks += b.lastPlacedCount
                r.snow -= spent
                if (!b.solidAt(r.ballX, r.ballZ, 0f) && r.snow < COST) {
                    events += SimEvent.BallLost(r.ballX, r.ballY, r.ballZ, r.id)
                    r.snow = 0f
                }
            }
        }
    }

    private fun collectSnow(r: Racer) {
        if (r.speedNow < 0.35f) return
        val cx: Float; val cz: Float; var rad: Float
        if (r.hasBall) { cx = r.ballX; cz = r.ballZ; rad = 0.42f + 0.5f * r.ballR }
        else { cx = r.x + sin(r.heading) * 0.35f; cz = r.z + cos(r.heading) * 0.35f; rad = 0.4f }
        val seg = course.locate(cz)
        if (seg.kind != SegKind.FIELD) return
        val magnet = r.magnetT > 0f
        if (magnet) rad *= 1.45f
        val got = fields[seg.index].collect(cx, cz, rad, time, sin(r.heading), cos(r.heading))
        if (got > 0f) r.snow += got * GAIN * r.gainMul * (if (magnet) 1.35f else 1f)
    }

    private fun startFall(r: Racer, respawnZ: Float) {
        if (r.state == RState.FALL) return
        r.state = RState.FALL
        r.stateT = 0f
        r.airY = r.y
        r.falls++
        r.respawnZ = respawnZ
        r.respawnX = clamp(r.x, -course.halfW + 1f, course.halfW - 1f)
        if (r.hasBall && !(assist && r.isPlayer)) {
            events += SimEvent.BallLost(r.ballX, r.ballY, r.ballZ, r.id)
            r.snow = 0f
        }
    }

    private fun respawn(r: Racer) {
        r.state = RState.RUN
        r.stateT = 0f
        r.x = r.respawnX
        r.z = r.respawnZ
        r.vx = 0f; r.vz = 0f; r.knockVx = 0f; r.knockVz = 0f
        r.heading = 0f
        r.invuln = 1.6f
        r.y = course.groundY(r.z)
        placeBall(r, true)
        ground(r)
    }

    private fun stun(r: Racer, fromX: Float, fromZ: Float, force: Float, lose: Float, attacker: Int) {
        if (r.state != RState.RUN) return
        if (r.invuln > 0f || r.shieldT > 0f) return
        var dx = r.x - fromX; var dz = r.z - fromZ
        val d = sqrt(dx * dx + dz * dz)
        if (d > 1e-3f) { dx /= d; dz /= d } else { dx = 0f; dz = -1f }
        r.knockVx = dx * force; r.knockVz = dz * force
        val keep = if (assist && r.isPlayer) 1f else 1f - lose
        val lost = r.snow * (1f - keep)
        r.snow *= keep
        r.state = RState.STUN
        r.stateT = 0f
        r.invuln = 2.2f
        r.dizzyT = 1.2f
        r.timesBonked++
        r.lastBonker = attacker
        events += SimEvent.Bonk(r.x, r.y + 0.9f, r.z, attacker, r.id, lost)
    }

    private fun canBonk(victim: Racer): Boolean {
        if (phase != Phase.RACE && phase != Phase.LOST) return false
        if (victim.finished) return false
        if (assist && victim.isPlayer) return false
        return victim.state == RState.RUN && victim.invuln <= 0f && victim.shieldT <= 0f
    }

    private fun collisions() {
        val n = racers.size
        for (i in 0 until n) {
            val a = racers[i]
            if (a.state == RState.FALL || a.finished) continue
            for (j in i + 1 until n) {
                val b = racers[j]
                if (b.state == RState.FALL || b.finished) continue
                // body-body
                var dx = b.x - a.x; var dz = b.z - a.z
                var d2 = dx * dx + dz * dz
                val minBody = Racer.BODY_R * 2f
                if (d2 < minBody * minBody && d2 > 1e-6f) {
                    val d = sqrt(d2); val p = (minBody - d) * 0.5f / d
                    a.x -= dx * p; a.z -= dz * p; b.x += dx * p; b.z += dz * p
                }
                // ball-ball
                if (a.hasBall && b.hasBall) {
                    dx = b.ballX - a.ballX; dz = b.ballZ - a.ballZ
                    d2 = dx * dx + dz * dz
                    val ra = a.ballR; val rb = b.ballR
                    val minD = ra + rb
                    if (d2 < minD * minD && d2 > 1e-6f) {
                        when {
                            ra > rb * 1.22f && canBonk(b) -> { stun(b, a.ballX, a.ballZ, 5.2f, 0.45f, a.id); a.bonks++ }
                            rb > ra * 1.22f && canBonk(a) -> { stun(a, b.ballX, b.ballZ, 5.2f, 0.45f, b.id); b.bonks++ }
                            else -> {
                                val d = sqrt(d2); val p = (minD - d) * 0.5f / d
                                a.x -= dx * p; a.z -= dz * p; b.x += dx * p; b.z += dz * p
                                a.ballX -= dx * p; a.ballZ -= dz * p; b.ballX += dx * p; b.ballZ += dz * p
                            }
                        }
                    }
                }
                ballHitsBody(a, b)
                ballHitsBody(b, a)
            }
        }
    }

    private fun ballHitsBody(att: Racer, vic: Racer) {
        if (!att.hasBall || vic.state == RState.FALL) return
        val dx = vic.x - att.ballX; val dz = vic.z - att.ballZ
        val d2 = dx * dx + dz * dz
        val minD = att.ballR + Racer.BODY_R
        if (d2 >= minD * minD || d2 < 1e-6f) return
        val bigger = !vic.hasBall || att.ballR > vic.ballR * 1.22f
        if (bigger && canBonk(vic) && att.state == RState.RUN) {
            stun(vic, att.ballX, att.ballZ, 5.6f, 0.45f, att.id)
            att.bonks++
        } else {
            val d = sqrt(d2); val p = (minD - d) / d
            vic.x += dx * p * 0.8f; vic.z += dz * p * 0.8f
            att.x -= dx * p * 0.2f; att.z -= dz * p * 0.2f
        }
    }

    // ------------------------------------------------------------------------------------------
    private fun affectHazards(r: Racer, dt: Float) {
        for (h in hazards) {
            val dzc = r.z - h.cz
            if (dzc > 7f || dzc < -7f) continue
            when (h.type) {
                HazardType.ICE -> if (h.insideRect(r.x, r.z)) r.onIce = true
                HazardType.LOG -> {
                    val halfLen = h.spec.d * 0.5f
                    if (abs(r.z - h.cz) < halfLen + 0.2f && abs(r.x - h.x) < 0.45f + Racer.BODY_R) {
                        if (r.invuln <= 0f && r.shieldT <= 0f && !(assist && r.isPlayer && r.stateT < 0f)) {
                            val before = r.state
                            stunHazard(r, h.x, sign(h.vx).let { if (it == 0f) sign(r.x - h.x) else it } * 5f, -1.5f, if (assist && r.isPlayer) 0f else 0.4f)
                            if (before != r.state) events += SimEvent.Hazard(h.type, r.x, r.y + 0.8f, r.z, r.id)
                        } else {
                            r.x = h.x + sign(r.x - h.x).let { if (it == 0f) 1f else it } * (0.46f + Racer.BODY_R)
                        }
                    }
                    if (r.hasBall && abs(r.ballZ - h.cz) < halfLen + r.ballR * 0.5f && abs(r.ballX - h.x) < 0.45f + r.ballR) {
                        val push = h.vx * dt
                        r.x += push
                    }
                }
                HazardType.PLOW -> {
                    val hw = h.spec.w * 0.5f; val hd = h.spec.d * 0.5f
                    fields.getOrNull(h.field)?.wipe(h.x, h.cz, h.spec.w, h.spec.d + 0.3f, time)
                    if (abs(r.z - h.cz) < hd + Racer.BODY_R && abs(r.x - h.x) < hw + Racer.BODY_R) {
                        val dir = if (abs(h.vx) > 0.05f) sign(h.vx) else sign(r.x - h.x).let { if (it == 0f) 1f else it }
                        r.x = h.x + dir * (hw + Racer.BODY_R + 0.05f)
                        r.knockVx = dir * 2.5f
                        if (r.isPlayer && r.invuln <= 0f) { events += SimEvent.Bump(r.x, r.y, r.z, r.id); r.invuln = 0.5f }
                    }
                }
                HazardType.PENGUINS -> {
                    for (px in h.penguinX) {
                        val dx = r.x - px; val dz = r.z - h.cz
                        if (dx * dx + dz * dz < 0.68f * 0.68f && r.invuln <= 0f) {
                            r.knockVz = -3.2f; r.knockVx = sign(dx) * 1.5f
                            r.invuln = 0.6f
                            events += SimEvent.Bump(r.x, r.y + 0.6f, r.z, r.id)
                        }
                        if (r.hasBall) {
                            val bdx = r.ballX - px; val bdz = r.ballZ - h.cz
                            val m = r.ballR + 0.3f
                            if (bdx * bdx + bdz * bdz < m * m) { r.z -= 2.5f * dt; r.knockVz = min(r.knockVz, -1.2f) }
                        }
                    }
                }
                HazardType.THIN_ICE -> {
                    if (h.insideRect(r.x, r.z)) {
                        h.occupied = true
                        if (h.broken) {
                            startFall(r, h.cz - h.spec.d * 0.5f - 1.0f)
                            events += SimEvent.Hazard(h.type, r.x, r.y, r.z, r.id)
                            return
                        }
                    }
                }
                HazardType.WIND -> if (h.active > 0.01f && h.insideRect(r.x, r.z)) {
                    r.x += h.windDirX() * 2.8f * h.active * dt
                }
                HazardType.BUMPER -> {
                    val R = h.spec.w * 0.5f
                    var dx = r.x - h.cx; var dz = r.z - h.cz
                    var d = sqrt(dx * dx + dz * dz)
                    if (d < R + Racer.BODY_R && d > 1e-3f) {
                        r.knockVx = dx / d * 7f; r.knockVz = dz / d * 7f
                        r.x = h.cx + dx / d * (R + Racer.BODY_R)
                        r.z = h.cz + dz / d * (R + Racer.BODY_R)
                        events += SimEvent.Hazard(h.type, h.cx, h.y + 0.6f, h.cz, r.id)
                    }
                    if (r.hasBall) {
                        dx = r.ballX - h.cx; dz = r.ballZ - h.cz
                        d = sqrt(dx * dx + dz * dz)
                        if (d < R + r.ballR * 0.9f && d > 1e-3f) {
                            r.knockVx = dx / d * 6f; r.knockVz = dz / d * 6f
                            events += SimEvent.Hazard(h.type, h.cx, h.y + 0.6f, h.cz, r.id)
                        }
                    }
                }
                HazardType.STEAM -> if (h.active > 0.3f && r.hasBall) {
                    val R = h.spec.w * 0.5f
                    val dx = r.ballX - h.cx; val dz = r.ballZ - h.cz
                    if (dx * dx + dz * dz < (R + r.ballR * 0.4f) * (R + r.ballR * 0.4f)) {
                        r.snow -= (if (assist && r.isPlayer) 70f else 240f) * dt
                    }
                }
            }
        }
    }

    private fun stunHazard(r: Racer, fromX: Float, kx: Float, kz: Float, lose: Float) {
        if (r.state != RState.RUN || r.invuln > 0f || r.shieldT > 0f) return
        r.snow *= (1f - lose)
        r.knockVx = kx; r.knockVz = kz
        r.state = RState.STUN; r.stateT = 0.2f
        r.invuln = 2f; r.dizzyT = 1f
        r.timesBonked++
    }

    // ------------------------------------------------------------------------------------------
    private fun updateBoss(dt: Float) {
        when (spec.boss) {
            BossType.AVALANCHE -> for (i in fields.indices) {
                val seg = course.fields[i]
                when (avState[i]) {
                    0 -> if (racers.any { it.z > seg.z0 + 2f && it.z < seg.z1 }) {
                        avState[i] = 1; avT[i] = 0f; avZ[i] = seg.z0 - 3f
                        events += SimEvent.BossStart(i)
                    }
                    1 -> { avT[i] += dt; if (avT[i] >= (if (assist) 3.2f else 2.2f)) avState[i] = 2 }
                    2 -> {
                        val prev = avZ[i]
                        avZ[i] += spec.bossSpeed * dt
                        fields[i].refillRows(prev - 2.5f, avZ[i] - 2.5f, 0.85f)
                        for (r in racers) {
                            if (r.state != RState.RUN || r.finished) continue
                            if (r.z < avZ[i] + 0.35f && r.z > seg.z0 - 4f && r.z < seg.z1 + 0.5f && r.invuln <= 0f) {
                                val keep = if (assist && r.isPlayer) 1f else 0.5f
                                r.snow *= keep
                                r.z = avZ[i] + 1.3f
                                r.knockVz = 3f
                                r.state = RState.STUN; r.stateT = 0.1f; r.invuln = 1.8f; r.dizzyT = 0.9f
                                events += SimEvent.Caught(r.id, r.x, r.y, r.z)
                            }
                        }
                        if (avZ[i] >= seg.z1 + 0.6f) { avState[i] = 3; events += SimEvent.BossEnd(i) }
                    }
                }
            }
            BossType.MEGA_BALL -> for (i in fields.indices) {
                val seg = course.fields[i]
                if (!megaActive[i]) {
                    if (racers.any { it.z > seg.z0 + 1f && it.z < seg.z1 }) { megaActive[i] = true; events += SimEvent.BossStart(i) }
                    megaX[i] = -course.halfW + MEGA_R
                    megaZ[i] = seg.z0 + seg.length * 0.5f
                    continue
                }
                megaT[i] += dt
                val amp = course.halfW - MEGA_R * 0.8f
                val spd = spec.bossSpeed.coerceAtLeast(1.5f) * (if (assist) 0.6f else 1f)
                val period = 4f * amp / spd
                val u = (megaT[i] / period) % 1f
                val p = if (u < 0.5f) u * 2f else 2f - u * 2f
                val nx = -amp + 2f * amp * (p * p * (3f - 2f * p))
                megaRot[i] += (nx - megaX[i]) / MEGA_R
                megaX[i] = nx
                megaZ[i] = seg.z0 + seg.length * (0.5f + 0.22f * sin(megaT[i] * 0.33f + i))
                fields[i].wipe(megaX[i], megaZ[i], MEGA_R * 0.7f, 1.0f, time)
                for (r in racers) {
                    if (r.state != RState.RUN) continue
                    var dx = r.x - megaX[i]; var dz = r.z - megaZ[i]
                    val hitBody = dx * dx + dz * dz < (MEGA_R + Racer.BODY_R) * (MEGA_R + Racer.BODY_R)
                    dx = r.ballX - megaX[i]; dz = r.ballZ - megaZ[i]
                    val hitBall = r.hasBall && dx * dx + dz * dz < (MEGA_R + r.ballR) * (MEGA_R + r.ballR)
                    if (hitBody || hitBall) {
                        if (canBonk(r)) stun(r, megaX[i], megaZ[i], 6.5f, 0.35f, -2)
                        else if (r.invuln <= 0f) { r.knockVx = sign(r.x - megaX[i]) * 5f; r.knockVz = -2f; r.invuln = 0.6f }
                    }
                }
            }
            BossType.NONE -> {}
        }
    }

    // ------------------------------------------------------------------------------------------
    private fun updatePickups(dt: Float) {
        for (p in pickups) {
            p.spin += dt
            if (!p.active) {
                if (p.type != PickupType.GIFT && time >= p.respawnAt) p.active = true
                continue
            }
            for (r in racers) {
                if (r.state != RState.RUN) continue
                var dx = r.x - p.x; var dz = r.z - p.z
                var hit = dx * dx + dz * dz < 0.9f * 0.9f
                if (!hit && r.hasBall) {
                    dx = r.ballX - p.x; dz = r.ballZ - p.z
                    val m = r.ballR + 0.4f
                    hit = dx * dx + dz * dz < m * m
                }
                if (!hit) continue
                p.active = false
                p.respawnAt = time + 14f
                when (p.type) {
                    PickupType.BOOST -> r.boostT = 3.2f
                    PickupType.SHIELD -> r.shieldT = 6f
                    PickupType.MAGNET -> r.magnetT = 7f
                    PickupType.MITTEN -> r.snow += max(150f, r.snow * 0.4f)
                    PickupType.GIFT -> r.giftCoins += 10
                }
                r.pickups++
                events += SimEvent.Pickup(p.type, p.x, p.y + 0.8f, p.z, r.id)
                break
            }
        }
    }

    private val rankBuf = ArrayList<Racer>()
    private fun updateRanks() {
        rankBuf.clear(); rankBuf.addAll(racers)
        rankBuf.sortWith { a, b ->
            val pa = if (a.finishRank > 0) 100000f - a.finishRank else a.z
            val pb = if (b.finishRank > 0) 100000f - b.finishRank else b.z
            pb.compareTo(pa)
        }
        rankBuf.forEachIndexed { i, r -> r.rank = i + 1 }
    }

    private fun finish(r: Racer) {
        if (r.finished) return
        finishedCount++
        r.finishRank = finishedCount
        r.finishTime = raceTime
        r.rank = finishedCount
        events += SimEvent.Finish(r.id, finishedCount)
        if (r.isPlayer) {
            r.state = RState.FINALE
            r.stateT = 0f
            r.boostT = 0f
            if (phase == Phase.RACE || phase == Phase.LOST) { phase = Phase.FINALE; phaseT = 0f }
        } else {
            r.state = RState.FINISHED
            val side = if (finishedCount % 2 == 0) 1f else -1f
            r.celebrateX = side * (1.6f + (finishedCount / 2) * 1.5f).coerceAtMost(course.halfW - 1f)
            r.celebrateZ = course.finishLineZ + 3.4f + (finishedCount % 3) * 0.7f
        }
    }

    private fun updateFinale(r: Racer, dt: Float) {
        r.inMag = 0f
        if (!r.finaleDone) {
            r.heading = dampAngle(r.heading, 0f, 8f, dt)
            val speed = 3.6f
            r.x = damp(r.x, 0f, 2.5f, dt)
            r.z += speed * dt
            r.moveAmt = damp(r.moveAmt, 0.8f, 8f, dt)
            r.stepPhase += speed * dt * 1.6f
            r.pushing = r.hasBall
            placeBall(r, false)
            val next = r.finaleStep + 1
            if (next > Course.STEPS) {
                r.finaleDone = true
            } else {
                val stairZ = course.stepZ0(next)
                val front = if (r.hasBall) r.ballZ + r.ballR * 0.3f else r.z + 0.4f
                if (front >= stairZ) {
                    val cost = FINALE_COST[next - 1]
                    if (r.hasBall && r.snow >= cost) {
                        r.snow -= cost
                        r.finaleStep = next
                        events += SimEvent.FinaleStep(next, r.ballX, course.finishY + next * Course.STEP_RISE, stairZ)
                        if (next == Course.STEPS) r.finaleDone = true
                    } else {
                        r.finaleDone = true
                        if (r.hasBall) events += SimEvent.BallLost(r.ballX, r.ballY, r.ballZ, r.id)
                    }
                }
            }
            if (r.finaleDone) r.stateT = 0f
        } else {
            r.moveAmt = damp(r.moveAmt, 0f, 6f, dt)
            r.heading = dampAngle(r.heading, 3.14159f, 3f, dt)
        }
        // Stay on the last step that was paid for.
        val limitZ = if (r.finaleStep == 0) course.finale.z0 - 0.35f
        else course.stepZ0(r.finaleStep) + Course.STEP_DEPTH - 0.35f
        if (r.z > limitZ && (r.finaleDone || !r.hasBall)) r.z = limitZ
        r.y = course.finaleStepY(r.z)
        if (r.hasBall) placeBall(r, false)
        if (r.finaleDone && r.stateT > 1.6f && phase != Phase.DONE) {
            phase = Phase.DONE
            events += SimEvent.RaceDone
        }
    }

    private fun checkEnd(dt: Float) {
        if (phase == Phase.RACE && !assist && !player.finished) {
            val othersDone = racers.count { !it.isPlayer && it.finishRank > 0 }
            if (othersDone >= 3) {
                phase = Phase.LOST; phaseT = 0f
                events += SimEvent.RaceLost
                endTimer = 2.2f
            }
        }
        if (phase == Phase.LOST) {
            endTimer -= dt
            if (endTimer <= 0f) { phase = Phase.DONE; events += SimEvent.RaceDone }
        }
    }

    // ------------------------------------------------------------------------------------------
    /** Snow the player still needs to fully bridge the next gap (0 if ready). */
    fun bridgeNeedFor(r: Racer): BridgeNeed {
        val seg = course.locate(r.z)
        val gi = when (seg.kind) {
            SegKind.FIELD, SegKind.GAP -> seg.index
            SegKind.START -> 0
            else -> return BridgeNeed(-1, 0f, 0f)
        }
        if (gi >= bridges.size) return BridgeNeed(-1, 0f, 0f)
        val b = bridges[gi]
        val x = if (seg.kind == SegKind.GAP) r.x else r.ballX
        val rows = b.missingRowsAt(x)
        val need = rows * 3.1f * COST
        return BridgeNeed(gi, need, r.snow)
    }

    fun multiplier(): Float = FINALE_MULT[player.finaleStep.coerceIn(0, Course.STEPS)]

    val playerWon: Boolean get() = player.finishRank in 1..3
}

data class BridgeNeed(val gap: Int, val need: Float, val have: Float) {
    val ratio: Float get() = if (need <= 0f) 1f else (have / need).coerceIn(0f, 1.5f)
    val ready: Boolean get() = gap >= 0 && have >= need
}
