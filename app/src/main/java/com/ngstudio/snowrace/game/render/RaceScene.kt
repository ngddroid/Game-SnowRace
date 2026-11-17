package com.ngstudio.snowrace.game.render

import android.content.Context
import android.opengl.GLES30
import com.ngstudio.snowrace.audio.AudioEngine
import com.ngstudio.snowrace.audio.Haptic
import com.ngstudio.snowrace.audio.Haptics
import com.ngstudio.snowrace.audio.Sfx
import com.ngstudio.snowrace.core.Easing
import com.ngstudio.snowrace.core.Mat4
import com.ngstudio.snowrace.core.PI_F
import com.ngstudio.snowrace.core.Quat
import com.ngstudio.snowrace.core.Rng
import com.ngstudio.snowrace.core.TAU
import com.ngstudio.snowrace.core.clamp01
import com.ngstudio.snowrace.core.damp
import com.ngstudio.snowrace.core.lerp
import com.ngstudio.snowrace.data.Catalog
import com.ngstudio.snowrace.data.TrailShape
import com.ngstudio.snowrace.game.PopupKind
import com.ngstudio.snowrace.game.RaceController
import com.ngstudio.snowrace.game.UiEvent
import com.ngstudio.snowrace.game.sim.BridgeGrid
import com.ngstudio.snowrace.game.sim.Course
import com.ngstudio.snowrace.game.sim.Phase
import com.ngstudio.snowrace.game.sim.RState
import com.ngstudio.snowrace.game.sim.RaceSim
import com.ngstudio.snowrace.game.sim.Racer
import com.ngstudio.snowrace.game.sim.SegKind
import com.ngstudio.snowrace.game.sim.SimEvent
import com.ngstudio.snowrace.game.sim.SnowField
import com.ngstudio.snowrace.level.BossType
import com.ngstudio.snowrace.level.HazardType
import com.ngstudio.snowrace.level.LevelGenerator
import com.ngstudio.snowrace.level.PickupType
import com.ngstudio.snowrace.level.PropType
import com.ngstudio.snowrace.level.TimeOfDay
import com.ngstudio.snowrace.level.Weather
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Renders a race: level geometry, racers, FX, camera direction, and reacts to simulation events. */
class RaceScene(
    private val context: Context,
    private val res: RenderResources,
    private val ctrl: RaceController,
    private val audio: AudioEngine,
    private val haptics: Haptics,
) : Scene {
    private val sim: RaceSim = ctrl.sim
    private val spec = sim.spec
    private val course: Course = sim.course
    private val light = LightRig().apply { configure(spec.biome, spec.time, spec.weather) }
    private val cam = Camera()
    private val rng = Rng(spec.seed xor 0x1234L)
    private val halfW = course.halfW
    private val accent = spec.biome.accent

    // ---- static batches
    private val platform: InstanceBatch
    private val pines = InstanceBatch(Props.pine(), 96)
    private val rocks = InstanceBatch(Props.rock(5), 48)
    private val crystals = InstanceBatch(Props.crystal(), 24)
    private val snowmen = InstanceBatch(Props.snowman(), 16)
    private val lanterns = InstanceBatch(Props.lantern(), 24)
    private val candies = InstanceBatch(Props.candyCane(), 24)
    private val crates = InstanceBatch(Props.crate(), 16)
    private val barrels = InstanceBatch(Props.barrel(), 16)
    private val cabins = InstanceBatch(Props.cabin(), 8)
    private val mountains = InstanceBatch(Props.mountain(), 24)
    private val islands = InstanceBatch(Props.island(), 32)
    private val towers = InstanceBatch(Props.tower(), 12)
    private val flags = InstanceBatch(Props.flagPole(), 32)
    private val balloons = InstanceBatch(Props.balloon(), 24)
    private val steps = InstanceBatch(Props.step(), 8)
    private val boxes = InstanceBatch(Props.roundedBox(), 32)
    private val icicles = InstanceBatch(Props.icicle(), 512)
    private val markers = InstanceBatch(Props.marker(), 96)
    private val staticBatches: List<InstanceBatch>

    // ---- dynamic batches
    private val floes = InstanceBatch(Props.iceFloe(), 40)
    private val blocks = InstanceBatch(Props.block(), 512)
    private val logs = InstanceBatch(Props.log(), 8)
    private val plows = InstanceBatch(Props.plow(), 8)
    private val penguins = InstanceBatch(Props.penguin(), 24)
    private val bumpers = InstanceBatch(Props.bumper(), 12)
    private val vents = InstanceBatch(Props.steamVent(), 12)
    private val gifts = InstanceBatch(Props.gift(), 16)
    private val quads = InstanceBatch(Props.quad(), 64)
    private val bigSnow = InstanceBatch(Props.sphere(14), 64)
    private val dynamicBatches: List<InstanceBatch>

    private val floeData = ArrayList<FloatArray>() // x, z, scale, yaw, phase
    private val clouds = ArrayList<FloatArray>()   // x, y, z, size
    private val spectators = ArrayList<FloatArray>() // x, z, yaw, lookIndex, phase
    private val spectatorLooks = Catalog.rivalCharacters.shuffled(java.util.Random(spec.seed))

    // ---- terrain & water
    private val terrains: List<TerrainMesh>
    private val water: WaterPlane
    private val land: LandMask
    private val text: TextAtlas
    private val labelIndexOfRacer = IntArray(sim.racers.size)
    private val multLabel0: Int
    private val finishLabel: Int

    // ---- fx
    private val fx = ParticleSystem(if (res.tier == QualityTier.LOW) 900 else 1800)
    private val flakeCount = when (spec.weather) { Weather.CLEAR -> 70; Weather.SNOW -> 220; Weather.BLIZZARD -> 420 } / (if (res.tier == QualityTier.LOW) 2 else 1)
    private val flakes = FloatArray(flakeCount * 4)
    private val falling = ArrayList<FloatArray>() // x,y,z,vy,rot,color

    // ---- per racer render state
    private val pose = Pose()
    private val visR = FloatArray(sim.racers.size)
    private val blinkT = FloatArray(sim.racers.size) { rng.range(0.5f, 3f) }
    private val trailAcc = FloatArray(sim.racers.size)
    private val bumperHit = FloatArray(64)
    private val ballModel = FloatArray(16)
    private val tmpQ = FloatArray(4)
    private val scr = FloatArray(2)

    // ---- camera state
    private var camX = 0f; private var camY = 12f; private var camZ = -14f
    private var lookX = 0f; private var lookY = 0f; private var lookZ = 0f
    private var shake = 0f
    private var fov = 55f
    private var slowMo = 0f
    private var time = 0f
    private var rollStream = 0
    private var avalancheStream = 0
    private var landTimer = 0f
    private var lastBridgeVersion = -1
    private var playerFinishedFx = false
    private val basis = FloatArray(2)
    private val basis2 = FloatArray(2)

    init {
        // ---------------- platform slabs
        val md = MeshData()
        val packed = rgbToInt(light.snowPacked)
        val cliff = rgbToInt(light.cliff)
        fun slab(z0: Float, z1: Float, y: Float, x0: Float = -halfW - 0.12f, x1: Float = halfW + 0.12f, top: Int = packed) =
            addSlab(md, x0, x1, y, RaceSim.WATER_Y - 3f, z0, z1, top, MeshBuilder.shade(cliff, 1.18f), MeshBuilder.shade(cliff, 0.55f))
        slab(course.start.z0, course.start.z1, 0f)
        for (f in course.fields) slab(f.z0, f.z1, f.y0)
        slab(course.finish.z0, course.finish.z1, course.finishY)
        // finale stairs base and plaza
        val plazaZ0 = course.stepZ0(Course.STEPS) + Course.STEP_DEPTH
        slab(course.finale.z0, plazaZ0, course.finishY, -halfW * 0.85f, halfW * 0.85f)
        slab(plazaZ0, course.finale.z1 + 4f, course.finishY + Course.STEPS * Course.STEP_RISE)
        // snow lips along edges
        val lip = MeshBuilder(md)
        fun lipsFor(z0: Float, z1: Float, y: Float, front: Boolean, back: Boolean) {
            lip.color(0xF6FBFF)
            for (sx in listOf(-1f, 1f)) lip.at(sx * (halfW + 0.05f), y - 0.04f, z0, pitch = PI_F / 2, scx = 0.22f, scy = z1 - z0, scz = 0.2f).cylinder(10, caps = false)
            if (front) lip.at(-halfW - 0.1f, y - 0.04f, z0 + 0.02f, roll = -PI_F / 2, scx = 0.2f, scy = halfW * 2f + 0.2f, scz = 0.2f).cylinder(10, caps = false)
            if (back) lip.at(-halfW - 0.1f, y - 0.04f, z1 - 0.02f, roll = -PI_F / 2, scx = 0.2f, scy = halfW * 2f + 0.2f, scz = 0.2f).cylinder(10, caps = false)
            // organic snow mounds along the sides
            var z = z0 + 1f
            while (z < z1 - 1f) {
                val sx = if (rng.chance(0.5f)) -1f else 1f
                lip.at(sx * (halfW + 0.05f), y, z, scx = rng.range(0.3f, 0.5f), scy = rng.range(0.15f, 0.28f), scz = rng.range(0.4f, 0.8f)).sphere(10, 6)
                z += rng.range(1.5f, 3.5f)
            }
        }
        lipsFor(course.start.z0, course.start.z1, 0f, front = true, back = true)
        for (f in course.fields) lipsFor(f.z0, f.z1, f.y0, front = true, back = true)
        lipsFor(course.finish.z0, course.finish.z1, course.finishY, front = true, back = false)
        // checkered finish line
        val sq = 0.65f
        var cx = -halfW
        var k = 0
        while (cx < halfW - 0.01f) {
            for (row in 0..1) {
                lip.color(if ((k + row) % 2 == 0) 0x1B2A4A else 0xFFFFFF)
                    .at(cx + sq / 2f, course.finishY + 0.012f, course.finishLineZ - sq + row * sq + sq / 2f, scx = sq, scy = 1f, scz = sq).quadXZ()
            }
            cx += sq; k++
        }
        // start line
        lip.color(0xFFFFFF).at(0f, 0.012f, -0.7f, scx = halfW * 2f, scy = 1f, scz = 0.25f).quadXZ()
        platform = InstanceBatch(Mesh(md, true), 1)
        Inst.add(platform, 0f, 0f, 0f, outline = 0f)

        // ---------------- field props
        for (c in sim.colliders) {
            val y = c.y
            when (c.type) {
                PropType.PINE -> Inst.add(pines, c.x, y, c.z, yaw = c.rot, sx = c.r / 0.62f, sy = c.r / 0.62f, sz = c.r / 0.62f, sway = 1f, outline = 0f)
                PropType.ROCK -> Inst.add(rocks, c.x, y + c.r * 0.25f, c.z, yaw = c.rot, sx = c.r * 1.05f, sy = c.r * 0.8f, sz = c.r * 1.05f)
                PropType.CRYSTAL -> Inst.add(crystals, c.x, y, c.z, yaw = c.rot, sx = c.r / 0.5f, sy = c.r / 0.5f, sz = c.r / 0.5f, gloss = 1.4f, color = accentTint(0xFFFFFF))
                PropType.SNOWMAN -> Inst.add(snowmen, c.x, y, c.z, yaw = PI_F + rng.range(-0.4f, 0.4f), sx = c.r / 0.46f, sy = c.r / 0.46f, sz = c.r / 0.46f)
                PropType.LANTERN -> Inst.add(lanterns, c.x, y, c.z, yaw = c.rot)
                PropType.CANDY -> Inst.add(candies, c.x, y, c.z, yaw = c.rot, sx = c.r / 0.32f, sy = 1f, sz = c.r / 0.32f, gloss = 0.8f)
                PropType.CRATE -> Inst.add(crates, c.x, y, c.z, yaw = c.rot, sx = c.r * 1.45f, sy = c.r * 1.45f, sz = c.r * 1.45f)
                PropType.BARREL -> Inst.add(barrels, c.x, y, c.z, yaw = c.rot, sx = c.r / 0.42f, sy = 1.1f, sz = c.r / 0.42f)
            }
        }

        // ---------------- start / finish decor
        val gateZ = -0.35f
        for (sx in listOf(-1f, 1f)) Inst.add(towers, sx * (halfW + 0.35f), 0f, gateZ, sx = 0.85f, sy = 0.95f, sz = 0.85f)
        Inst.add(boxes, 0f, 3.55f, gateZ, sx = halfW * 2f + 1f, sy = 0.62f, sz = 0.25f, color = 0x5EC8FF, gloss = 0.3f)
        for (sx in listOf(-1f, 1f)) Inst.add(towers, sx * (halfW + 0.45f), course.finishY, course.finishLineZ)
        Inst.add(boxes, 0f, course.finishY + 3.55f, course.finishLineZ, sx = halfW * 2f + 1.4f, sy = 0.9f, sz = 0.3f, color = accent, gloss = 0.3f)
        val balloonColors = intArrayOf(0xFF4D8D, 0x3DF5C8, 0xFFC83D, 0x5EC8FF, 0xB46CFF)
        for (i in 0 until 10) {
            val sx = if (i % 2 == 0) -1f else 1f
            Inst.add(balloons, sx * (halfW + rng.range(0.6f, 1.6f)), course.finishY + rng.range(2.4f, 4.4f), course.finishLineZ + rng.range(-1.2f, 1.2f),
                sway = 1.5f, color = balloonColors[i % balloonColors.size], gloss = 0.9f)
        }
        // bunting poles along finish platform
        var fz = course.finish.z0 + 1f
        while (fz < course.finish.z1) {
            // left side only: the finale camera looks in from the right
            Inst.add(flags, -(halfW - 0.15f), course.finishY, fz, yaw = 0f, sway = 1.2f, color = balloonColors[rng.nextInt(balloonColors.size)])
            fz += 2.6f
        }
        // finale steps
        val stepColors = intArrayOf(0x5EC8FF, 0x3DF5C8, 0xFFC83D, 0xFF7A45, 0xFF4D8D)
        for (k2 in 1..Course.STEPS) {
            val top = course.finishY + k2 * Course.STEP_RISE
            Inst.add(steps, 0f, top - 1.5f, course.stepZ0(k2) + Course.STEP_DEPTH / 2f, sx = halfW * 1.7f, sy = 3f, sz = Course.STEP_DEPTH, color = stepColors[k2 - 1], gloss = 0.35f)
        }
        // plaza castle + trophy
        val plazaY = course.finishY + Course.STEPS * Course.STEP_RISE
        for (sx in listOf(-1f, 1f)) Inst.add(towers, sx * (halfW - 0.8f), plazaY, course.finale.z1 - 0.5f, sx = 1.25f, sy = 1.4f, sz = 1.25f)
        Inst.add(boxes, 0f, plazaY + 1.4f, course.finale.z1 + 0.3f, sx = halfW * 2f - 1.6f, sy = 2.8f, sz = 1f, color = 0xEAF6FF)
        Inst.add(boxes, 0f, plazaY + 2.95f, course.finale.z1 + 0.3f, sx = halfW * 2f - 1.2f, sy = 0.3f, sz = 1.2f, color = 0xFFFFFF)
        for (i in 0 until 6) Inst.add(flags, -halfW + 1.5f + i * (halfW * 2f - 3f) / 5f, plazaY + 3.1f, course.finale.z1 + 0.3f, sway = 1.2f, color = balloonColors[i % 5])

        // ---------------- environment: islands, pines, cabins, mountains, floes, clouds, spectators
        var z = course.start.z0 - 10f
        val endZ = course.finale.z1 + 25f
        while (z < endZ) {
            for (sx in listOf(-1f, 1f)) {
                if (rng.chance(0.25f)) continue
                val s = rng.range(2.5f, 5.5f)
                val ix = sx * (halfW + rng.range(5f, 16f) + s)
                val iz = z + rng.range(-3f, 3f)
                val top = RaceSim.WATER_Y + s * 0.42f
                Inst.add(islands, ix, RaceSim.WATER_Y - s * 0.3f, iz, yaw = rng.range(0f, TAU), sx = s, sy = s * 0.72f, sz = s * 0.9f)
                val n = rng.nextInt(1, 4)
                for (t in 0 until n) {
                    val a = rng.range(0f, TAU); val d = rng.range(0f, s * 0.45f)
                    val ts = rng.range(0.9f, 1.6f)
                    Inst.add(pines, ix + cos(a) * d, top - 0.2f, iz + sin(a) * d, yaw = a, sx = ts, sy = ts, sz = ts, sway = 1f)
                }
                if (s > 4.2f && rng.chance(0.35f)) Inst.add(cabins, ix, top - 0.15f, iz, yaw = if (sx > 0) -PI_F / 2 else PI_F / 2, sx = 0.8f, sy = 0.8f, sz = 0.8f)
                else if (rng.chance(0.3f)) Inst.add(snowmen, ix + rng.range(-1f, 1f), top - 0.1f, iz, yaw = if (sx > 0) -PI_F / 2 else PI_F / 2)
                if (spec.time != TimeOfDay.DAY && rng.chance(0.4f)) Inst.add(lanterns, ix - sx * s * 0.3f, top - 0.1f, iz)
            }
            z += rng.range(9f, 15f)
        }
        val midZ = (course.start.z0 + course.finale.z1) / 2f
        for (i in 0 until 16) {
            val a = i / 16f * TAU + rng.range(-0.15f, 0.15f)
            val r = rng.range(120f, 175f)
            val mz = midZ + sin(a) * r
            if (cos(a) * r > -40f && cos(a) * r < 40f && mz < course.start.z0 + 20f) continue // keep the view behind the camera clear
            val hgt = rng.range(26f, 55f)
            Inst.add(mountains, cos(a) * r, RaceSim.WATER_Y - 2f, mz, yaw = rng.range(0f, TAU), sx = hgt * rng.range(0.8f, 1.2f), sy = hgt, sz = hgt * rng.range(0.8f, 1.2f))
        }
        for (i in 0 until 28) {
            val sx = if (rng.chance(0.5f)) -1f else 1f
            floeData += floatArrayOf(sx * (halfW + rng.range(1.5f, 12f)), rng.range(course.start.z0 - 5f, course.finale.z1), rng.range(0.4f, 1.3f), rng.range(0f, TAU), rng.range(0f, TAU))
        }
        for (g in course.gaps) {
            // a few floes inside the gaps (outside the track) for life
            for (i in 0 until 2) floeData += floatArrayOf((if (rng.chance(0.5f)) -1f else 1f) * (halfW + rng.range(0.8f, 3f)), rng.range(g.z0 + 0.5f, g.z1 - 0.5f), rng.range(0.3f, 0.6f), rng.range(0f, TAU), rng.range(0f, TAU))
        }
        for (i in 0 until 14) clouds += floatArrayOf(rng.range(-140f, 140f), rng.range(28f, 60f), rng.range(course.start.z0, course.finale.z1 + 160f), rng.range(25f, 55f))
        for (i in 0 until 6) {
            val row = i % 2
            spectators += floatArrayOf(-(halfW - 0.7f - row * 0.95f), course.finish.z0 + 1.3f + (i / 2) * 2.2f + row * 0.9f, PI_F / 2 + 0.25f, i.toFloat(), rng.range(0f, 3f))
        }

        // icicles hanging under every platform edge that faces a gap, plus marker poles along the track sides
        fun iciclesAlong(z: Float, y: Float, facing: Float) {
            var x = -halfW + 0.2f
            while (x < halfW - 0.1f) {
                val len = rng.range(0.25f, 0.85f)
                Inst.add(icicles, x + rng.range(-0.1f, 0.1f), y - 0.12f, z + facing * 0.02f, sx = rng.range(0.07f, 0.12f), sy = len, sz = rng.range(0.07f, 0.12f), color = 0xCFEFFF, gloss = 1.6f, emissive = 0.12f)
                x += rng.range(0.35f, 0.75f)
            }
        }
        for ((gi, g) in course.gaps.withIndex()) {
            iciclesAlong(g.z0, course.fields[gi].y0, 1f)
            iciclesAlong(g.z1, g.y1, -1f)
        }
        val markerCols = intArrayOf(accent, 0xFFFFFF)
        for (f in course.fields) {
            var mz = f.z0 + 1.2f
            var k2 = 0
            while (mz < f.z1 - 0.8f) {
                for (sx in listOf(-1f, 1f)) Inst.add(markers, sx * (halfW + 0.05f), f.y0, mz, yaw = if (sx < 0) 0f else PI_F, sway = 0.6f, color = markerCols[k2 % 2], outline = 0f)
                mz += 3.2f; k2++
            }
        }
        staticBatches = listOf(platform, pines, rocks, crystals, snowmen, lanterns, candies, crates, barrels, cabins, mountains, islands, towers, flags, balloons, steps, boxes, icicles, markers)
        dynamicBatches = listOf(floes, blocks, logs, plows, penguins, bumpers, vents, gifts, quads, bigSnow)

        // ---------------- terrain, water, land mask
        terrains = sim.fields.map { TerrainMesh(it) }
        water = WaterPlane(-400f, 400f, course.start.z0 - 300f, course.finale.z1 + 400f)
        land = LandMask(-halfW - 12f, course.start.z0 - 12f, course.width + 24f, course.finale.z1 + 16f - (course.start.z0 - 12f)) { x, z ->
            val seg = course.locate(z)
            seg.kind != SegKind.GAP && z >= course.start.z0 && z <= course.finale.z1 + 4f && abs(x) <= halfW + 0.1f
        }
        for (f in floeData) land.stamp(f[0], f[1], f[2] * 0.9f)
        rebuildLand()

        // ---------------- labels
        val labels = ArrayList<Pair<String, Int>>()
        sim.racers.forEachIndexed { i, r -> labelIndexOfRacer[i] = labels.size; labels += r.name to if (r.isPlayer) 0xFFFFFFFF.toInt() else 0xFFEAF4FF.toInt() }
        multLabel0 = labels.size
        for (m in listOf("x1.5", "x2", "x3", "x4", "x5")) labels += m to 0xFFFFE27A.toInt()
        finishLabel = labels.size
        labels += "FINISH" to 0xFFFFFFFF.toInt()
        labels += "START" to 0xFFFFFFFF.toInt()
        text = TextAtlas(context, labels, highlightFirst = true)

        for (i in 0 until flakeCount) {
            flakes[i * 4] = rng.range(-14f, 14f); flakes[i * 4 + 1] = rng.range(0f, 14f); flakes[i * 4 + 2] = rng.range(-6f, 24f); flakes[i * 4 + 3] = rng.range(0f, TAU)
        }
        // initial camera: high above the finish looking back
        camX = halfW + 7f; camY = course.finishY + 9f; camZ = course.finishLineZ + 9f
        lookX = 0f; lookY = course.finishY + 1f; lookZ = course.finishLineZ - 6f
        Gl.check("RaceScene.init")
    }

    private fun accentTint(c: Int) = MeshBuilder.mix(c, accent, 0.25f)

    // =========================================================================== update
    override fun update(dt: Float) {
        val realDt = dt
        val simDt = if (slowMo > 0f) { slowMo -= realDt; realDt * 0.35f } else realDt
        time += realDt
        res.time = time
        // input uses the current camera ground basis
        cam.flatRight(basis); cam.flatForward(basis2)
        ctrl.applyInput(basis[0], basis[1], basis2[0], basis2[1])
        // fixed-ish sub-steps
        if (!ctrl.paused) {
            var remaining = simDt.coerceAtMost(0.05f)
            while (remaining > 1e-4f) {
                val step = min(remaining, 1f / 60f)
                ctrl.step(step)
                remaining -= step
                handleEvents()
            }
            fx.update(simDt) { x, z -> groundY(x, z) }
            updateFalling(simDt)
        }
        updateCamera(realDt)
        updateAudioLoops()
        landTimer -= realDt
        val v = sim.bridges.sumOf { it.version }
        if (v != lastBridgeVersion && landTimer <= 0f) { rebuildLand(); lastBridgeVersion = v; landTimer = 0.25f }
    }

    private fun rebuildLand() {
        land.beginRebuild()
        for (b in sim.bridges) for (r in 0 until b.rows) for (c in 0 until b.cols) {
            if (b.state[b.idx(c, r)] != BridgeGrid.EMPTY) land.mark(b.cellX(c), b.cellZ(r))
        }
        land.finishRebuild()
    }

    private fun groundY(x: Float, z: Float): Float {
        val seg = course.locate(z)
        return when (seg.kind) {
            SegKind.GAP -> if (sim.bridges[seg.index].solidAt(x, z, 0f)) sim.bridges[seg.index].heightAt(z) + RaceSim.BRIDGE_TOP else RaceSim.WATER_Y
            SegKind.FIELD -> if (abs(x) < halfW) sim.fields[seg.index].surfaceY(x, z) else RaceSim.WATER_Y
            else -> if (abs(x) < halfW) course.groundY(z) else RaceSim.WATER_Y
        }
    }

    private fun handleEvents() {
        val p = sim.player
        for (e in sim.events) {
            when (e) {
                is SimEvent.Countdown -> { audio.play(Sfx.COUNTDOWN); haptics.play(Haptic.TICK); ctrl.emit(UiEvent.Countdown(e.n)) }
                is SimEvent.Go -> { audio.play(Sfx.GO); haptics.play(Haptic.CLICK); ctrl.emit(UiEvent.Go) }
                is SimEvent.Bonk -> {
                    val near = isNear(e.x, e.z)
                    if (near) audio.play(Sfx.BONK, 0.9f, rng.range(0.9f, 1.15f))
                    burstStars(e.x, e.y, e.z, 7)
                    snowChunks(e.x, e.y - 0.4f, e.z, (8 + e.lost / 40f).toInt().coerceAtMost(26))
                    if (e.victim == 0) {
                        shake = max(shake, 0.55f); haptics.play(Haptic.BONK)
                        popup("Ouch!", e.x, e.y + 0.8f, e.z, PopupKind.BAD)
                    } else if (e.attacker == 0) {
                        shake = max(shake, 0.3f); haptics.play(Haptic.HEAVY)
                        popup(listOf("BONK!", "SMASH!", "POW!", "BOOM!").random(), e.x, e.y + 0.8f, e.z, PopupKind.GOOD)
                    }
                }
                is SimEvent.Splash -> {
                    if (isNear(e.x, e.z)) audio.play(Sfx.SPLASH, 0.9f, rng.range(0.9f, 1.1f))
                    splash(e.x, e.y, e.z, 1f)
                    if (e.racer == 0) { haptics.play(Haptic.SPLASH); popup("Splash!", e.x, e.y + 1.5f, e.z, PopupKind.BAD) }
                }
                is SimEvent.Block -> {
                    if (e.owner == 0) {
                        audio.play(Sfx.BRIDGE_BLOCK, 0.8f, 0.85f + (e.n % 24) * 0.025f)
                        if (e.n % 3 == 0) haptics.play(Haptic.TICK)
                    }
                    for (i in 0 until 3) fx.emit(e.x + rng.range(-0.25f, 0.25f), e.y + 0.2f, e.z + rng.range(-0.25f, 0.25f), rng.range(-0.6f, 0.6f), rng.range(0.6f, 1.6f), rng.range(-0.6f, 0.6f), 0.5f, 0.28f, 0.5f, 0xFFFFFFFF.toInt(), ParticleAtlas.PUFF, gravity = 1.5f, dragK = 2f)
                }
                is SimEvent.Crumble -> {
                    falling += floatArrayOf(e.x, e.y + 0.02f, e.z, 0f, rng.range(-1f, 1f), rng.range(-1f, 1f))
                    if (isNear(e.x, e.z)) audio.play(Sfx.CRUMBLE, 0.5f, rng.range(0.9f, 1.15f))
                }
                is SimEvent.BallLost -> {
                    snowChunks(e.x, e.y, e.z, 12)
                    for (i in 0 until 8) fx.emit(e.x, e.y, e.z, rng.range(-1.5f, 1.5f), rng.range(0.5f, 2f), rng.range(-1.5f, 1.5f), 0.7f, 0.5f, 1.1f, 0xFFFFFFFF.toInt(), ParticleAtlas.PUFF, dragK = 2.5f)
                    if (e.racer == 0) audio.play(Sfx.POP, 0.8f, 0.8f)
                }
                is SimEvent.Pickup -> {
                    val col = when (e.type) { PickupType.BOOST -> 0xFFFF7A45; PickupType.SHIELD -> 0xFF5EC8FF; PickupType.MAGNET -> 0xFFFF4D8D; PickupType.MITTEN -> 0xFF3DF5C8; PickupType.GIFT -> 0xFFFFC83D }.toInt()
                    sparkleBurst(e.x, e.y, e.z, col, 16)
                    if (e.racer == 0) {
                        if (e.type == PickupType.GIFT) { audio.play(Sfx.GIFT); audio.play(Sfx.COIN, 0.7f); popup("+10", e.x, e.y + 0.5f, e.z, PopupKind.COIN) }
                        else { audio.play(Sfx.POWERUP); popup(LevelGenerator.pickupTitle(e.type) + "!", e.x, e.y + 0.6f, e.z, PopupKind.GOOD) }
                        when (e.type) { PickupType.BOOST -> audio.play(Sfx.BOOST); PickupType.SHIELD -> audio.play(Sfx.SHIELD); else -> {} }
                        haptics.play(Haptic.CLICK)
                    }
                }
                is SimEvent.Finish -> {
                    if (e.racer == 0) {
                        audio.play(Sfx.CHEER); haptics.play(Haptic.SUCCESS)
                        confetti(0f, course.finishY + 3f, course.finishLineZ, 80)
                        if (e.rank == 1) slowMo = 0.9f
                        ctrl.emit(UiEvent.Banner(rankText(e.rank) + " place!", if (e.rank <= 3) "Now push for the finale!" else ""))
                    } else if (isNear(0f, course.finishLineZ)) audio.play(Sfx.WHOOSH, 0.4f)
                }
                is SimEvent.Step -> audio.play(if (e.left) Sfx.STEP1 else Sfx.STEP2, 0.6f, rng.range(0.9f, 1.1f))
                is SimEvent.Hazard -> {
                    when (e.type) {
                        HazardType.BUMPER -> { if (isNear(e.x, e.z)) audio.play(Sfx.POP, 1f, rng.range(0.7f, 0.85f)); markBumper(e.x, e.z) }
                        HazardType.THIN_ICE -> { audio.play(Sfx.ICE_CRACK); snowChunks(e.x, e.y + 0.2f, e.z, 10) }
                        HazardType.LOG -> { if (isNear(e.x, e.z)) audio.play(Sfx.BONK, 0.8f, 0.8f); burstStars(e.x, e.y, e.z, 5) }
                        else -> {}
                    }
                    if (e.racer == 0) { haptics.play(Haptic.BONK); shake = max(shake, 0.35f) }
                }
                is SimEvent.FinaleStep -> {
                    audio.play(Sfx.FINALE_STEP, 1f, 0.9f + e.step * 0.12f)
                    haptics.play(Haptic.CLICK)
                    sparkleBurst(e.x, e.y + 0.5f, e.z, 0xFFFFC83D.toInt(), 22)
                    ctrl.emit(UiEvent.FinaleStep(e.step, RaceSim.FINALE_MULT[e.step]))
                }
                is SimEvent.StageEnter -> if (e.racer == 0) {
                    audio.play(Sfx.WHOOSH)
                    if (e.stage < spec.stages.size) ctrl.emit(UiEvent.Banner("Stage ${e.stage + 1}/${spec.stages.size}"))
                    else ctrl.emit(UiEvent.Banner("Final sprint!", "Race to the finish!"))
                }
                is SimEvent.BossStart -> {
                    if (spec.boss == BossType.AVALANCHE) { ctrl.emit(UiEvent.Banner("AVALANCHE!", "Keep moving!")); shake = max(shake, 0.4f) }
                    else ctrl.emit(UiEvent.Banner("MEGA BALL!", "Dodge it!"))
                    audio.play(Sfx.ERROR, 0.6f, 0.7f)
                }
                is SimEvent.BossEnd -> { splash(0f, RaceSim.WATER_Y, course.fields[e.stage].z1 + 1f, 2.5f); if (isNear(0f, course.fields[e.stage].z1)) audio.play(Sfx.SPLASH, 1f, 0.7f) }
                is SimEvent.Caught -> {
                    snowChunks(e.x, e.y + 0.5f, e.z, 14)
                    if (e.racer == 0) { haptics.play(Haptic.BONK); shake = max(shake, 0.6f); audio.play(Sfx.BONK, 1f, 0.7f); popup("Buried!", e.x, e.y + 1.5f, e.z, PopupKind.BAD) }
                }
                is SimEvent.Bump -> { if (e.racer == 0) { audio.play(Sfx.POP, 0.7f, 1.2f); haptics.play(Haptic.TICK) } }
                is SimEvent.RaceLost -> { audio.play(Sfx.LOSE); ctrl.emit(UiEvent.RaceLost) }
                is SimEvent.RaceDone -> {
                    if (sim.player.finishRank in 1..3) { audio.play(Sfx.WIN); confetti(sim.player.x, sim.player.y + 2f, sim.player.z, 60) }
                }
            }
        }
        sim.events.clear()
        if (p.finished && !playerFinishedFx) {
            playerFinishedFx = true
        }
    }

    private fun rankText(r: Int) = when (r) { 1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"; else -> "${r}th" }

    private fun isNear(x: Float, z: Float): Boolean {
        val p = sim.player
        val dx = x - p.x; val dz = z - p.z
        return dx * dx + dz * dz < 26f * 26f
    }

    private fun popup(text: String, x: Float, y: Float, z: Float, kind: PopupKind) {
        if (cam.toScreen(x, y, z, res.width, res.height, scr)) ctrl.emit(UiEvent.Popup(text, scr[0] / res.width, scr[1] / res.height, kind))
    }

    private fun markBumper(x: Float, z: Float) {
        sim.hazards.forEachIndexed { i, h -> if (h.type == HazardType.BUMPER && abs(h.cx - x) < 0.1f && abs(h.cz - z) < 0.1f && i < bumperHit.size) bumperHit[i] = time }
    }

    // =========================================================================== FX presets
    private fun burstStars(x: Float, y: Float, z: Float, n: Int) {
        for (i in 0 until n) {
            val a = rng.range(0f, TAU)
            fx.emit(x, y, z, cos(a) * rng.range(1.5f, 3f), rng.range(1f, 3.5f), sin(a) * rng.range(1.5f, 3f), 0.7f, 0.35f, 0.15f, 0xFFFFE27A.toInt(), ParticleAtlas.STAR, gravity = 4f, dragK = 1.5f, rotation = rng.range(0f, TAU), rotSpeed = rng.range(-6f, 6f))
        }
    }

    private fun snowChunks(x: Float, y: Float, z: Float, n: Int) {
        for (i in 0 until n) {
            val a = rng.range(0f, TAU)
            fx.emit(x, y, z, cos(a) * rng.range(1f, 3.5f), rng.range(2f, 5f), sin(a) * rng.range(1f, 3.5f), rng.range(0.6f, 1.1f), rng.range(0.18f, 0.32f), 0.1f, 0xFFF4FAFF.toInt(), ParticleAtlas.CIRCLE, gravity = 11f, dragK = 0.5f)
        }
    }

    private fun sparkleBurst(x: Float, y: Float, z: Float, color: Int, n: Int) {
        for (i in 0 until n) {
            val a = rng.range(0f, TAU); val e = rng.range(-0.3f, 1f)
            fx.emit(x, y, z, cos(a) * 2.5f, e * 2.5f + 1f, sin(a) * 2.5f, rng.range(0.5f, 0.9f), rng.range(0.25f, 0.45f), 0.05f, color, ParticleAtlas.SPARKLE, dragK = 2.5f, additive = true, rotation = rng.range(0f, TAU))
        }
        fx.emit(x, y, z, 0f, 0f, 0f, 0.35f, 0.4f, 2.2f, color, ParticleAtlas.RING, additive = true)
    }

    private fun splash(x: Float, y: Float, z: Float, scale: Float) {
        for (i in 0 until (14 * scale).toInt()) {
            val a = rng.range(0f, TAU)
            fx.emit(x, y + 0.1f, z, cos(a) * rng.range(0.5f, 2f) * scale, rng.range(3f, 6f), sin(a) * rng.range(0.5f, 2f) * scale, rng.range(0.6f, 1f), rng.range(0.18f, 0.32f) * scale, 0.1f, 0xFFDDF6FF.toInt(), ParticleAtlas.DROP, gravity = 13f)
        }
        fx.emit(x, y + 0.03f, z, 0f, 0f, 0f, 1.1f, 0.5f * scale, 3.2f * scale, 0xFFFFFFFF.toInt(), ParticleAtlas.RING, ground = true)
        fx.emit(x, y + 0.03f, z, 0f, 0f, 0f, 1.4f, 0.4f * scale, 2.2f * scale, 0xCCFFFFFF.toInt(), ParticleAtlas.RING, ground = true)
    }

    private fun confetti(x: Float, y: Float, z: Float, n: Int) {
        val cols = intArrayOf(0xFFFF4D8D.toInt(), 0xFF3DF5C8.toInt(), 0xFFFFC83D.toInt(), 0xFF5EC8FF.toInt(), 0xFFB46CFF.toInt(), 0xFFFF7A45.toInt())
        for (i in 0 until n) {
            val a = rng.range(0f, TAU)
            fx.emit(x + rng.range(-1f, 1f), y, z, cos(a) * rng.range(1f, 5f), rng.range(4f, 9f), sin(a) * rng.range(1f, 5f), rng.range(1.6f, 2.6f), 0.22f, 0.2f, cols[i % cols.size], ParticleAtlas.SQUARE, gravity = 6f, dragK = 1.2f, rotation = rng.range(0f, TAU), rotSpeed = rng.range(-9f, 9f))
        }
    }

    private fun updateFalling(dt: Float) {
        val it = falling.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f[3] -= 14f * dt
            f[1] += f[3] * dt
            if (f[1] < RaceSim.WATER_Y) {
                if (isNear(f[0], f[2])) { splash(f[0], RaceSim.WATER_Y, f[2], 0.45f) }
                it.remove()
            }
        }
    }

    // =========================================================================== camera
    private fun updateCamera(dt: Float) {
        val p = sim.player
        val ballR = max(visR[0], p.ballR)
        when (sim.phase) {
            Phase.INTRO -> {
                val t = Easing.inOutSine(clamp01(sim.phaseT / sim.introDuration))
                val f0 = followPos(p, ballR); val fx0 = f0[0]; val fy0 = f0[1]; val fz0 = f0[2]
                val sx = halfW + 7f; val sy = course.finishY + 9f; val sz = course.finishLineZ + 9f
                camX = lerp(sx, fx0, t); camY = lerp(sy, fy0, t); camZ = lerp(sz, fz0, t)
                lookX = lerp(0f, p.x * 0.65f, t); lookY = lerp(course.finishY + 1f, p.y, t); lookZ = lerp(course.finishLineZ - 6f, p.z + 4f, t)
            }
            Phase.FINALE, Phase.DONE -> if (p.state == RState.FINALE || p.finished) {
                val tz = p.z + 1.5f
                camX = damp(camX, halfW + 4.5f, 2.2f, dt)
                camY = damp(camY, p.y + 4.2f, 2.2f, dt)
                camZ = damp(camZ, tz - 6.5f, 2.2f, dt)
                lookX = damp(lookX, 0f, 3f, dt); lookY = damp(lookY, p.y + 1.2f, 3f, dt); lookZ = damp(lookZ, tz + 1f, 3f, dt)
            }
            else -> {
                val f0 = followPos(p, ballR); val fx0 = f0[0]; val fy0 = f0[1]; val fz0 = f0[2]
                val k = if (sim.phase == Phase.COUNTDOWN) 3f else 5f
                camX = damp(camX, fx0, k, dt); camY = damp(camY, fy0, k, dt); camZ = damp(camZ, fz0, k * 1.3f, dt)
                lookX = damp(lookX, p.x * 0.65f, k, dt)
                lookY = damp(lookY, p.y + 0.8f, k, dt)
                lookZ = damp(lookZ, p.z + 3.9f + ballR * 0.6f, k * 1.3f, dt)
            }
        }
        if (sim.phase == Phase.FINALE && p.state != RState.FINALE && !p.finished) {
            val f0 = followPos(p, ballR); val fx0 = f0[0]; val fy0 = f0[1]; val fz0 = f0[2]
            camX = damp(camX, fx0, 4f, dt); camY = damp(camY, fy0, 4f, dt); camZ = damp(camZ, fz0, 4f, dt)
        }
        shake = damp(shake, 0f, 6f, dt)
        val avalancheRumble = if (spec.boss == BossType.AVALANCHE) sim.avState.indices.maxOfOrNull { i ->
            if (sim.avState[i] == 1 || sim.avState[i] == 2) clamp01(1f - abs(sim.avZ[i] - p.z) / 14f) * 0.25f else 0f
        } ?: 0f else 0f
        val s = shake + avalancheRumble
        val sx = sin(time * 47f) * s * 0.3f; val sy = cos(time * 53f) * s * 0.25f
        fov = damp(fov, if (p.boostT > 0f) 61f else 55f, 4f, dt)
        cam.fovDeg = fov
        cam.x = camX + sx; cam.y = camY + sy; cam.z = camZ
        cam.tx = lookX + sx * 0.5f; cam.ty = lookY; cam.tz = lookZ
        cam.aspect = res.width.toFloat() / res.height
        cam.far = 320f
        cam.update()
    }

    private val follow = FloatArray(3)

    /** Writes the follow-camera position into [follow] (no per-frame allocation). */
    private fun followPos(p: Racer, ballR: Float): FloatArray {
        val height = 7.3f + ballR * 1.2f
        val back = 9.9f + ballR * 1.5f
        follow[0] = p.x * 0.55f; follow[1] = p.y + height; follow[2] = p.z - back
        return follow
    }

    private fun updateAudioLoops() {
        val p = sim.player
        val pushing = p.state == RState.RUN && p.hasBall && p.speedNow > 0.5f && course.locate(p.ballZ).kind == SegKind.FIELD
        if (pushing && !ctrl.paused) {
            if (rollStream == 0) rollStream = audio.startLoop(Sfx.ROLL_LOOP, 0f)
            val vol = (min(1f, p.speedNow / 5f) * (0.35f + min(1f, p.ballR / 1.6f) * 0.5f)).coerceIn(0f, 0.9f)
            audio.setLoop(rollStream, vol, (0.85f + p.speedNow * 0.04f - p.ballR * 0.07f).coerceIn(0.6f, 1.3f))
        } else if (rollStream != 0) {
            audio.setLoop(rollStream, 0f, 1f)
        }
        val av = spec.boss == BossType.AVALANCHE && sim.avState.any { it == 2 } && !ctrl.paused
        if (av) {
            if (avalancheStream == 0) avalancheStream = audio.startLoop(Sfx.AVALANCHE_LOOP, 0f)
            var near = 0f
            for (i in sim.avState.indices) if (sim.avState[i] == 2) near = max(near, clamp01(1f - abs(sim.avZ[i] - p.z) / 20f))
            audio.setLoop(avalancheStream, (0.2f + near * 0.8f) * 0.7f, 0.9f)
        } else if (avalancheStream != 0) audio.setLoop(avalancheStream, 0f, 1f)
    }

    // =========================================================================== render
    override fun render() {
        val p = sim.player
        // ---- build dynamic instances
        for (b in dynamicBatches) b.clear()
        res.chars.clear()
        res.ballCasters.clear()
        fillDynamic()
        fillRacers()

        // ---- shadow pass
        res.shadow.update(light.sunDir[0], light.sunDir[1], light.sunDir[2], p.x * 0.6f, p.y, p.z + 6f, 19f)
        if (res.beginShadowPass()) {
            for (b in staticBatches) if (b !== mountains && b !== islands) b.draw()
            blocks.draw(); logs.draw(); plows.draw(); penguins.draw(); bumpers.draw(); gifts.draw(); bigSnow.draw()
            for (b in res.chars.all()) b.draw()
            res.ballCasters.draw()
            res.endShadowPass()
        }

        // ---- main pass
        GLES30.glViewport(0, 0, res.width, res.height)
        GLES30.glClearColor(light.fog[0], light.fog[1], light.fog[2], 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LESS)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glDisable(GLES30.GL_BLEND)

        res.common(res.lit, cam, light)
        for (b in staticBatches) b.draw()
        for (b in dynamicBatches) b.draw()
        for (b in res.chars.all()) b.draw()

        drawBalls()
        drawTerrain()
        drawWater()

        // outlines (characters + balls + key props)
        res.beginOutline(cam, light)
        for (b in res.chars.all()) b.draw()
        res.ballCasters.draw()
        res.endOutline()

        res.drawSky(cam, light)

        // ---- transparent: billboards
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        fillBillboards()
        res.beginBillboards(cam, true)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, res.particleAtlas.texture)
        res.ground.draw()
        res.beginBillboards(cam, false)
        res.bill.draw()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, res.iconAtlas.texture)
        res.iconBill.draw()
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, text.texture)
        res.labelBill.draw()
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
    }

    private fun fillDynamic() {
        val t = time
        // floes
        for (f in floeData) {
            val bob = sin(t * 1.3f + f[4]) * 0.05f
            Inst.add(floes, f[0], RaceSim.WATER_Y + 0.05f + bob, f[1], yaw = f[3] + sin(t * 0.2f + f[4]) * 0.1f, pitch = sin(t * 1.1f + f[4]) * 0.04f, sx = f[2], sy = 1f, sz = f[2])
        }
        // bridges
        for (b in sim.bridges) {
            for (r in 0 until b.rows) for (c in 0 until b.cols) {
                val i = b.idx(c, r)
                val st = b.state[i]
                if (st == BridgeGrid.EMPTY) continue
                val age = time - b.placedAt[i]
                var s = 1f
                if (age < 0.25f) s = Easing.outBack(clamp01(age / 0.25f))
                var x = b.cellX(c); val z = b.cellZ(r); var y = b.heightAt(z) + 0.02f
                var tint = 0xF2F8FF
                if (st == BridgeGrid.CRUMBLING) {
                    x += sin(t * 70f + i) * 0.04f; y += cos(t * 63f + i) * 0.02f
                    tint = 0xD9E6F5
                }
                val owner = b.owner[i].toInt()
                if (owner in sim.racers.indices) tint = MeshBuilder.mix(tint, sim.racers[owner].look.body, 0.1f)
                Inst.add(blocks, x, y - (1f - s) * 0.4f, z, sx = s, sy = s, sz = s, color = tint, gloss = 0.15f)
            }
        }
        for (f in falling) Inst.add(blocks, f[0], f[1], f[2], pitch = (f[3]) * 0.05f * f[4], roll = f[3] * 0.04f * f[5], color = 0xDDE8F5)
        // hazards
        sim.hazards.forEachIndexed { hi, h ->
            val y = h.y
            when (h.type) {
                HazardType.ICE -> Inst.add(quads, h.cx, y + 0.012f, h.cz, sx = h.spec.w, sy = 1f, sz = h.spec.d, color = 0xBFE9FF, gloss = 1.4f, emissive = 0.15f)
                HazardType.THIN_ICE -> {
                    val col = if (h.broken) 0x1A5C9E else MeshBuilder.mix(0xA8DDF5, 0xF0FAFF, h.crack)
                    Inst.add(quads, h.cx, y + 0.012f, h.cz, sx = h.spec.w, sy = 1f, sz = h.spec.d, color = col, gloss = if (h.broken) 0.6f else 1.2f, emissive = if (h.broken) 0.05f else 0.12f)
                    if (h.crack > 0.3f && !h.broken && rng.chance(0.2f)) fx.emit(h.cx + rng.range(-h.spec.w / 2, h.spec.w / 2), y + 0.05f, h.cz + rng.range(-h.spec.d / 2, h.spec.d / 2), 0f, 0.6f, 0f, 0.4f, 0.15f, 0.05f, 0xFFFFFFFF.toInt(), ParticleAtlas.SPARKLE, additive = true)
                }
                HazardType.LOG -> Inst.add(logs, h.x, y + 0.45f, h.cz, roll = -h.rollAngle, sx = 0.45f, sy = 0.45f, sz = h.spec.d, outline = 1f)
                HazardType.PLOW -> {
                    Inst.add(plows, h.x, y, h.cz, yaw = if (h.vx >= 0f) 0f else PI_F, outline = 0.6f, gloss = 0.4f)
                    if (abs(h.vx) > 0.3f && rng.chance(0.6f)) fx.emit(h.x + kotlin.math.sign(h.vx) * 1.1f, y + 0.3f, h.cz + rng.range(-0.7f, 0.7f), kotlin.math.sign(h.vx) * rng.range(1f, 3f), rng.range(1f, 3f), rng.range(-0.5f, 0.5f), 0.6f, 0.25f, 0.5f, 0xFFFFFFFF.toInt(), ParticleAtlas.PUFF, gravity = 4f)
                }
                HazardType.PENGUINS -> h.penguinX.forEachIndexed { i, px ->
                    if (abs(px) < halfW + 1.5f) Inst.add(penguins, px, y + abs(sin(t * 9f + i)) * 0.05f, h.cz, yaw = if (h.penguinDir > 0) PI_F / 2 else -PI_F / 2, roll = sin(t * 9f + i) * 0.14f, outline = 0.8f)
                }
                HazardType.BUMPER -> {
                    val since = time - (if (hi < bumperHit.size) bumperHit[hi] else -9f)
                    val sq = if (since < 0.45f) sin(since * 30f) * 0.18f * (1f - since / 0.45f) else 0f
                    val s = h.spec.w * 0.5f / 0.95f
                    Inst.add(bumpers, h.cx, y, h.cz, sx = s * (1f - sq * 0.5f), sy = s * (1f + sq), sz = s * (1f - sq * 0.5f), gloss = 0.7f, outline = 1f)
                }
                HazardType.STEAM -> {
                    Inst.add(vents, h.cx, y, h.cz, sx = h.spec.w * 0.5f, sy = 1f, sz = h.spec.w * 0.5f)
                    if (h.warn > 0.05f && rng.chance(0.25f)) fx.emit(h.cx + rng.range(-0.3f, 0.3f), y + 0.1f, h.cz + rng.range(-0.3f, 0.3f), 0f, 1.2f, 0f, 0.8f, 0.3f, 0.6f, 0xAAFFFFFF.toInt(), ParticleAtlas.PUFF)
                    if (h.active > 0.2f) for (k in 0 until 2) fx.emit(h.cx + rng.range(-0.4f, 0.4f), y + 0.2f, h.cz + rng.range(-0.4f, 0.4f), rng.range(-0.3f, 0.3f), rng.range(4f, 6f), rng.range(-0.3f, 0.3f), 1.0f, 0.6f, 1.8f, 0xDDFFFFFF.toInt(), ParticleAtlas.PUFF, dragK = 0.8f)
                }
                HazardType.WIND -> if (h.active > 0.1f && rng.chance(0.7f * h.active)) {
                    val dir = h.windDirX()
                    fx.emit(-dir * halfW, y + rng.range(0.2f, 1.6f), h.cz + rng.range(-h.spec.d / 2, h.spec.d / 2), dir * rng.range(8f, 11f), 0f, 0f, 1.2f, 0.9f, 0.9f, 0xCCFFFFFF.toInt(), ParticleAtlas.STREAK, additive = true)
                }
            }
        }
        // pickups (gifts as 3D boxes; others as icons in fillBillboards)
        for (pk in sim.pickups) if (pk.active && pk.type == PickupType.GIFT) {
            Inst.add(gifts, pk.x, pk.y + 0.25f + sin(time * 3f + pk.x) * 0.1f, pk.z, yaw = time * 1.5f + pk.z, sx = 1.2f, sy = 1.2f, sz = 1.2f, gloss = 0.5f, outline = 1f)
        }
        // bosses
        if (spec.boss == BossType.AVALANCHE) {
            for (i in sim.avState.indices) {
                if (sim.avState[i] == 0 || sim.avState[i] == 3) continue
                val seg = course.fields[i]
                val wz = if (sim.avState[i] == 1) seg.z0 - 3f - (1f - min(1f, time % 1f)) * 0f else sim.avZ[i]
                val n = 9
                for (k in 0 until n) {
                    val x = -halfW + (k + 0.5f) * (halfW * 2f / n)
                    val bob = sin(time * 6f + k) * 0.15f
                    val r = 1.0f + ((k * 37) % 5) * 0.12f
                    Inst.add(bigSnow, x, seg.y0 + r * 0.6f + bob, wz - 0.5f + ((k * 13) % 3) * 0.3f, yaw = time * 2f + k, pitch = time * 4f + k, sx = r, sy = r, sz = r, color = 0xF8FCFF, outline = 0f)
                    Inst.add(bigSnow, x + 0.4f, seg.y0 + r * 1.4f + bob, wz - 1.4f, sx = r * 0.9f, sy = r * 0.9f, sz = r * 0.9f, color = 0xEEF5FF)
                }
                if (sim.avState[i] == 2) for (k in 0 until 3) fx.emit(rng.range(-halfW, halfW), seg.y0 + rng.range(0.5f, 2.5f), wz + 0.3f, rng.range(-1f, 1f), rng.range(1f, 3f), rng.range(1f, 3f), 0.9f, 0.8f, 1.8f, 0xEEFFFFFF.toInt(), ParticleAtlas.PUFF, dragK = 1.5f)
            }
        }
    }

    private fun fillRacers() {
        val dt = 1f / 60f
        sim.racers.forEachIndexed { i, r ->
            visR[i] = damp(visR[i], if (r.hasBall && r.state != RState.FALL) r.ballR else 0f, 16f, dt)
            blinkT[i] -= dt
            if (blinkT[i] < -0.12f) blinkT[i] = rng.range(1.8f, 4.5f)
            val p = pose
            p.x = r.x; p.y = r.y; p.z = r.z; p.yaw = r.heading
            p.move = r.moveAmt; p.phase = r.stepPhase; p.pushing = r.pushing && visR[i] > 0.05f; p.ballR = visR[i]
            p.lean = 0.08f * r.moveAmt + (if (p.pushing) 0.1f else 0f)
            p.roll = 0f; p.squash = r.squash; p.jump = 0f; p.armsUp = 0f; p.wave = 0f
            p.blink = if (blinkT[i] < 0f) 0.12f else 1f
            p.mouth = 0f; p.scale = 1.12f; p.time = time + i * 1.7f; p.lookX = 0f; p.outline = 1f
            when (r.state) {
                RState.STUN -> {
                    val k = clamp01(1f - r.stateT / 1.05f)
                    p.roll = sin(r.stateT * 18f) * 0.35f * k
                    p.lean = -0.35f * k
                    p.jump = abs(sin(r.stateT * 9f)) * 0.15f * k
                    p.mouth = 1f; p.blink = 0.15f; p.armsUp = 0.7f * k
                }
                RState.FALL -> { p.armsUp = 1f; p.roll = r.stateT * 6f; p.mouth = 1f; p.y = r.y }
                RState.FINISHED -> {
                    if (r.moveAmt < 0.2f) { p.jump = abs(sin(time * 6.5f + i)) * 0.35f; p.armsUp = 1f; p.wave = 1f; p.mouth = 1f }
                }
                RState.FINALE -> if (r.finaleDone) { p.jump = abs(sin(time * 6.5f)) * 0.4f; p.armsUp = 1f; p.wave = 1f; p.mouth = 1f; p.pushing = false }
                RState.IDLE -> { p.lookX = sin(time * 0.7f + i) * 0.8f; p.squash = sin(time * 2.4f + i) * 0.015f }
                RState.RUN -> {}
            }
            if (r.dizzyT > 0f) p.blink = 0.15f
            res.rig.draw(r.look, p)
            // ball shadow caster + outline proxy
            if (visR[i] > 0.05f) {
                Inst.add(res.ballCasters, r.ballX, ballY(r, i), r.ballZ, sx = visR[i] * 1.01f, sy = visR[i] * 1.01f, sz = visR[i] * 1.01f, outline = 1.2f)
            }
            // fx: snow spray, trail
            if (r.state == RState.RUN && r.speedNow > 1f) {
                val seg = course.locate(r.ballZ)
                if (seg.kind == SegKind.FIELD && rng.chance(if (r.isPlayer) 0.7f else 0.25f)) {
                    val br = max(visR[i], 0.2f)
                    fx.emit(r.ballX + rng.range(-br, br) * 0.6f, r.y + 0.1f, r.ballZ - br * 0.6f, -sin(r.heading) * 1.2f + rng.range(-0.8f, 0.8f), rng.range(1f, 2.4f), -cos(r.heading) * 1.2f, 0.55f, 0.18f + br * 0.12f, 0.45f + br * 0.2f, 0xFFFFFFFF.toInt(), ParticleAtlas.PUFF, gravity = 3f, dragK = 2f)
                }
                val trail = r.trail
                if (trail.id != "none" || r.isPlayer) {
                    trailAcc[i] += trail.rate * dt * min(1f, r.speedNow / 4f)
                    while (trailAcc[i] >= 1f) {
                        trailAcc[i] -= 1f
                        val spr = when (trail.shape) {
                            TrailShape.CIRCLE -> ParticleAtlas.SOFT; TrailShape.STAR -> ParticleAtlas.STAR; TrailShape.HEART -> ParticleAtlas.HEART
                            TrailShape.FLAKE -> ParticleAtlas.FLAKE; TrailShape.SPARKLE -> ParticleAtlas.SPARKLE; TrailShape.NOTE -> ParticleAtlas.NOTE
                        }
                        val col = trail.colors[rng.nextInt(trail.colors.size)]
                        val br = max(visR[i], 0.3f)
                        fx.emit(r.ballX + rng.range(-br, br) * 0.7f, r.y + rng.range(0.2f, br * 1.6f), r.ballZ - br * 0.5f, rng.range(-0.4f, 0.4f), rng.range(0.3f, 1.2f), rng.range(-0.4f, 0.4f), rng.range(0.5f, 0.9f), rng.range(0.22f, 0.36f), 0.05f, col, spr, dragK = 1.5f, additive = trail.id != "none", rotation = rng.range(0f, TAU))
                    }
                }
            }
        }
        // spectators at the finish
        for (s in spectators) {
            val look = spectatorLooks[s[3].toInt() % spectatorLooks.size]
            val p = pose
            val excited = sim.player.z > course.finish.z0 - 12f
            p.x = s[0]; p.z = s[1]; p.y = course.finishY; p.yaw = s[2]
            p.move = 0f; p.phase = 0f; p.pushing = false; p.ballR = 0f; p.lean = 0f; p.roll = 0f; p.squash = 0f
            p.jump = if (excited) abs(sin(time * 6f + s[4])) * 0.3f else 0f
            p.armsUp = if (excited) 1f else 0f; p.wave = if (excited) 1f else 0.3f; p.blink = 1f; p.mouth = if (excited) 1f else 0f
            p.scale = 0.95f; p.time = time + s[4]; p.lookX = 0f; p.outline = 1f
            res.rig.draw(look, p)
        }
        // mega ball outline/shadow proxies
        if (spec.boss == BossType.MEGA_BALL) for (i in sim.megaActive.indices) if (sim.megaActive[i]) {
            Inst.add(res.ballCasters, sim.megaX[i], course.fields[i].y0 + RaceSim.MEGA_R, sim.megaZ[i], sx = RaceSim.MEGA_R, sy = RaceSim.MEGA_R, sz = RaceSim.MEGA_R, outline = 1.5f)
        }
    }

    private fun ballY(r: Racer, i: Int): Float = r.ballY - r.ballR + visR[i]

    private fun drawBalls() {
        val prog = res.ball
        res.common(prog, cam, light)
        sim.racers.forEachIndexed { i, r ->
            val vr = visR[i]
            if (vr <= 0.05f) return@forEachIndexed
            val def = r.ballLook
            drawBall(prog, r.ballX, ballY(r, i), r.ballZ, r.ballRot, vr, def.base, def.accent, def.pattern.ordinal, def.glow)
        }
        if (spec.boss == BossType.MEGA_BALL) for (i in sim.megaActive.indices) if (sim.megaActive[i]) {
            Quat.axisAngle(tmpQ, 0f, 0f, 1f, -sim.megaRot[i])
            drawBall(prog, sim.megaX[i], course.fields[i].y0 + RaceSim.MEGA_R, sim.megaZ[i], tmpQ, RaceSim.MEGA_R, 0xFFE9F3FF.toInt(), 0xFF9FB8D6.toInt(), 0, 0f)
        }
    }

    private fun drawBall(prog: ShaderProgram, x: Float, y: Float, z: Float, rot: FloatArray, r: Float, base: Int, accent: Int, pattern: Int, glow: Float) {
        Mat4.trs(ballModel, 0, x, y, z, rot[0], rot[1], rot[2], rot[3], r, r, r)
        prog.mat4("uModel", ballModel)
        prog.vec3("uBase", ((base shr 16) and 0xFF) / 255f, ((base shr 8) and 0xFF) / 255f, (base and 0xFF) / 255f)
        prog.vec3("uAccent", ((accent shr 16) and 0xFF) / 255f, ((accent shr 8) and 0xFF) / 255f, (accent and 0xFF) / 255f)
        prog.i("uPattern", pattern)
        prog.f("uGlow", glow)
        res.ballMesh.draw()
    }

    private fun drawTerrain() = TerrainMesh.drawAll(res, cam, light, terrains)

    private fun drawWater() = water.draw(res, cam, light, RaceSim.WATER_Y, land)

    private fun fillBillboards() {
        res.bill.clear(); res.ground.clear(); res.iconBill.clear(); res.labelBill.clear()
        val pa = res.particleAtlas
        val p = sim.player
        // blob shadows (low tier) + player ring
        if (!res.shadowEnabled) {
            for (i in sim.racers.indices) {
                val r = sim.racers[i]
                if (r.state == RState.FALL) continue
                res.ground.add(r.x, r.y + 0.03f, r.z, 0.9f, 0xFF1B2A4A.toInt(), 0.35f, pa.rects[ParticleAtlas.BLOB])
                if (visR[i] > 0.05f) res.ground.add(r.ballX, r.y + 0.03f, r.ballZ, visR[i] * 2.2f, 0xFF1B2A4A.toInt(), 0.35f, pa.rects[ParticleAtlas.BLOB])
            }
        }
        if (p.state != RState.FALL && !p.finished) {
            val pulse = 0.85f + sin(time * 5f) * 0.12f
            res.ground.add(p.x, p.y + 0.04f, p.z, 1.15f * pulse, 0xFFFF4D8D.toInt(), 0.75f, pa.rects[ParticleAtlas.RING])
        }
        fx.fill(pa, res.bill, res.ground)
        // snowfall around the camera target
        val fall = when (spec.weather) { Weather.BLIZZARD -> 3.2f; else -> 1.4f }
        val wind = if (spec.weather == Weather.BLIZZARD) 2.5f else 0.4f
        val dt = 1f / 60f
        for (i in 0 until flakeCount) {
            val o = i * 4
            flakes[o + 1] -= fall * dt
            flakes[o] += (wind + sin(time + flakes[o + 3]) * 0.5f) * dt
            if (flakes[o + 1] < -1f) { flakes[o + 1] = 14f; flakes[o] = rng.range(-14f, 14f) }
            if (flakes[o] > 14f) flakes[o] -= 28f
            val wx = lookX + flakes[o]; val wy = lookY + flakes[o + 1]; val wz = lookZ + flakes[o + 2] - 6f
            res.bill.add(wx, wy, wz, 0.09f + (i % 3) * 0.03f, 0xFFFFFFFF.toInt(), 0.85f, pa.rects[ParticleAtlas.SOFT])
        }
        // clouds
        for (c in clouds) res.bill.add(c[0] + sin(time * 0.02f + c[2]) * 6f, c[1], c[2], c[3], 0xFFFFFFFF.toInt(), if (light.night > 0f) 0.25f else 0.8f, pa.rects[ParticleAtlas.CLOUD])
        // shields / boosts / dizzy
        for (i in sim.racers.indices) {
            val r = sim.racers[i]
            if (r.shieldT > 0f && r.state != RState.FALL) {
                res.bill.add(r.x, r.y + 0.7f, r.z, 2.4f + sin(time * 6f) * 0.1f, 0xFF5EC8FF.toInt(), 0.35f, pa.rects[ParticleAtlas.GLOW], additive = true)
                res.bill.add(r.x, r.y + 0.7f, r.z, 2.2f, 0xFFBFEFFF.toInt(), 0.5f, pa.rects[ParticleAtlas.RING], rot = time, additive = true)
            }
            if (r.magnetT > 0f && visR[i] > 0.05f) {
                for (k in 0 until 3) {
                    val a = time * 4f + k * TAU / 3f
                    res.bill.add(r.ballX + cos(a) * visR[i] * 1.1f, ballY(r, i) + sin(a * 0.5f) * 0.3f, r.ballZ + sin(a) * visR[i] * 1.1f, 0.35f, 0xFFFF4D8D.toInt(), 0.9f, pa.rects[ParticleAtlas.SPARKLE], additive = true)
                }
            }
            if (r.boostT > 0f && r.speedNow > 1f && rng.chance(0.6f)) {
                fx.emit(r.x + rng.range(-0.6f, 0.6f), r.y + rng.range(0.3f, 1.2f), r.z, -sin(r.heading) * 6f, 0f, -cos(r.heading) * 6f, 0.25f, 0.8f, 0.5f, 0xFFFFE2B0.toInt(), ParticleAtlas.STREAK, additive = true, rotation = PI_F / 2)
            }
            if (r.dizzyT > 0f || r.state == RState.STUN) {
                for (k in 0 until 3) {
                    val a = time * 7f + k * TAU / 3f
                    res.bill.add(r.x + cos(a) * 0.35f, r.y + 1.45f, r.z + sin(a) * 0.35f, 0.26f, 0xFFFFE27A.toInt(), 1f, pa.rects[ParticleAtlas.STAR], rot = a)
                }
            }
        }
        // pickups: icon + glow
        for (pk in sim.pickups) {
            if (!pk.active || pk.type == PickupType.GIFT) continue
            val icon = when (pk.type) { PickupType.BOOST -> IconAtlas.BOOST; PickupType.SHIELD -> IconAtlas.SHIELD; PickupType.MAGNET -> IconAtlas.MAGNET; else -> IconAtlas.MITTEN }
            val bob = sin(time * 3f + pk.x) * 0.12f
            res.bill.add(pk.x, pk.y + 0.95f + bob, pk.z, 1.5f, 0xFFFFFFFF.toInt(), 0.55f, pa.rects[ParticleAtlas.GLOW], additive = true)
            res.ground.add(pk.x, pk.y + 0.05f, pk.z, 1.2f, 0xFFFFFFFF.toInt(), 0.6f, pa.rects[ParticleAtlas.RING], rot = time)
            res.iconBill.add(pk.x, pk.y + 0.95f + bob, pk.z, 0.95f, 0xFFFFFFFF.toInt(), 1f, res.iconAtlas.rects[icon])
        }
        // labels: racer names
        for (i in sim.racers.indices) {
            val r = sim.racers[i]
            if (r.state == RState.FALL && r.y < RaceSim.WATER_Y + 0.2f) continue
            val rect = text.rects[labelIndexOfRacer[i]]
            val dx = r.x - cam.x; val dz = r.z - cam.z; val dy = r.y - cam.y
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            val size = 0.36f * (dist / 15f).coerceIn(0.9f, 2.2f)
            res.labelBill.add(r.x, r.y + 1.75f, r.z, size, 0xFFFFFFFF.toInt(), if (r.isPlayer) 1f else 0.85f, rect)
            if (r.isPlayer && !r.finished) res.bill.add(r.x, r.y + 2.3f + sin(time * 5f) * 0.08f, r.z, 0.42f * (dist / 15f).coerceIn(0.9f, 2f), 0xFFFF4D8D.toInt(), 1f, pa.rects[ParticleAtlas.ARROW])
        }
        // multiplier labels on steps + finish
        for (k in 1..Course.STEPS) {
            val top = course.finishY + k * Course.STEP_RISE
            res.labelBill.add(0f, top + 0.6f, course.stepZ0(k) + 0.35f, 0.75f, 0xFFFFFFFF.toInt(), 1f, text.rects[multLabel0 + k - 1])
        }
        res.labelBill.add(0f, course.finishY + 3.55f, course.finishLineZ - 0.3f, 0.75f, 0xFFFFFFFF.toInt(), 1f, text.rects[finishLabel])
        if (sim.phase == Phase.INTRO || sim.phase == Phase.COUNTDOWN) res.labelBill.add(0f, 3.55f, -0.55f, 0.6f, 0xFFFFFFFF.toInt(), 1f, text.rects[finishLabel + 1])
    }

    override fun release() {
        if (rollStream != 0) audio.stopLoop(rollStream)
        if (avalancheStream != 0) audio.stopLoop(avalancheStream)
        for (t in terrains) t.release()
        land.release()
        TextureUtil.delete(text.texture)
    }

    companion object {
        fun rgbToInt(c: FloatArray): Int = ((c[0].coerceIn(0f, 1f) * 255).toInt() shl 16) or ((c[1].coerceIn(0f, 1f) * 255).toInt() shl 8) or (c[2].coerceIn(0f, 1f) * 255).toInt()

        /** Box slab with a flat top and vertically graded sides (icy cliffs into the water). */
        fun addSlab(md: MeshData, x0: Float, x1: Float, yTop: Float, yBot: Float, z0: Float, z1: Float, top: Int, sideTop: Int, sideBot: Int) {
            fun rgb(c: Int) = floatArrayOf(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)
            val t = rgb(top); val st = rgb(sideTop); val sb = rgb(sideBot)
            var a = md.vertex(x0, yTop, z0, 0f, 1f, 0f, t[0], t[1], t[2])
            md.vertex(x1, yTop, z0, 0f, 1f, 0f, t[0], t[1], t[2])
            md.vertex(x1, yTop, z1, 0f, 1f, 0f, t[0], t[1], t[2])
            md.vertex(x0, yTop, z1, 0f, 1f, 0f, t[0], t[1], t[2])
            md.quad(a, a + 3, a + 2, a + 1)
            // sides: (nx, nz, ax0, az0, ax1, az1)
            val sides = arrayOf(
                floatArrayOf(0f, -1f, x1, z0, x0, z0), floatArrayOf(0f, 1f, x0, z1, x1, z1),
                floatArrayOf(-1f, 0f, x0, z0, x0, z1), floatArrayOf(1f, 0f, x1, z1, x1, z0),
            )
            for (s in sides) {
                // mid band gives a nicer gradient: top light, mid, bottom dark
                val yMid = yTop - 0.9f
                a = md.vertex(s[2], yTop, s[3], s[0], 0f, s[1], st[0], st[1], st[2])
                md.vertex(s[4], yTop, s[5], s[0], 0f, s[1], st[0], st[1], st[2])
                val m0 = md.vertex(s[4], yMid, s[5], s[0], 0f, s[1], (st[0] + sb[0]) / 2, (st[1] + sb[1]) / 2, (st[2] + sb[2]) / 2)
                md.vertex(s[2], yMid, s[3], s[0], 0f, s[1], (st[0] + sb[0]) / 2, (st[1] + sb[1]) / 2, (st[2] + sb[2]) / 2)
                md.vertex(s[4], yBot, s[5], s[0], 0f, s[1], sb[0], sb[1], sb[2])
                md.vertex(s[2], yBot, s[3], s[0], 0f, s[1], sb[0], sb[1], sb[2])
                md.quad(a, a + 3, m0, a + 1)
                md.quad(a + 3, a + 5, a + 4, m0)
            }
        }
    }
}
