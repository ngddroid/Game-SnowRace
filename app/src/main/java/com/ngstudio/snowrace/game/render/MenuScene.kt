package com.ngstudio.snowrace.game.render

import android.opengl.GLES30
import com.ngstudio.snowrace.audio.AudioEngine
import com.ngstudio.snowrace.audio.Sfx
import com.ngstudio.snowrace.core.Easing
import com.ngstudio.snowrace.core.Mat4
import com.ngstudio.snowrace.core.PI_F
import com.ngstudio.snowrace.core.Quat
import com.ngstudio.snowrace.core.Rng
import com.ngstudio.snowrace.core.TAU
import com.ngstudio.snowrace.core.clamp01
import com.ngstudio.snowrace.core.damp
import com.ngstudio.snowrace.data.BallDef
import com.ngstudio.snowrace.data.Catalog
import com.ngstudio.snowrace.data.CharacterDef
import com.ngstudio.snowrace.data.TrailDef
import com.ngstudio.snowrace.data.TrailShape
import com.ngstudio.snowrace.game.sim.BridgeGrid
import com.ngstudio.snowrace.game.sim.Racer
import com.ngstudio.snowrace.game.sim.SnowField
import com.ngstudio.snowrace.level.Biome
import com.ngstudio.snowrace.level.SnowStyle
import com.ngstudio.snowrace.level.TimeOfDay
import com.ngstudio.snowrace.level.Weather
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class MenuMode { HOME, SHOP, PROFILE, ONBOARD_ROLL, ONBOARD_BRIDGE, ONBOARD_BONK, ONBOARD_COLLECT, BACKDROP }

/** Live 3D diorama behind menus: hero on a podium, shop preview and onboarding vignettes. */
class MenuScene(
    private val res: RenderResources,
    private val audio: AudioEngine?,
    biome: Biome,
    time: TimeOfDay,
) : Scene {
    @Volatile var look: CharacterDef = Catalog.character("pip")
    @Volatile var ball: BallDef = Catalog.ball("classic")
    @Volatile var trail: TrailDef = Catalog.trail("none")
    @Volatile var mode: MenuMode = MenuMode.HOME
    @Volatile var dragYaw = 0f
    @Volatile private var waveAt = -10f

    private val light = LightRig().apply { configure(biome, time, if (time == TimeOfDay.NIGHT) Weather.CLEAR else Weather.SNOW) }
    private val cam = Camera()
    private val rng = Rng(77)
    private var t = 0f
    private var modeT = 0f
    private var lastMode = MenuMode.HOME

    private val island = InstanceBatch(buildIsland(), 1)
    private val pines = InstanceBatch(Props.pine(), 24)
    private val snowmen = InstanceBatch(Props.snowman(), 4)
    private val lanterns = InstanceBatch(Props.lantern(), 6)
    private val gifts = InstanceBatch(Props.gift(), 6)
    private val mountains = InstanceBatch(Props.mountain(), 12)
    private val podium = InstanceBatch(buildPodium(), 1)
    private val floes = InstanceBatch(Props.iceFloe(), 16)
    private val slabs = InstanceBatch(buildBridgeSlabs(), 1)
    private val blocks = InstanceBatch(Props.block(), 128)
    private val statics = listOf(island, pines, snowmen, lanterns, gifts, mountains, slabs)
    private val floeData = ArrayList<FloatArray>()

    private val water = WaterPlane(-300f, 300f, -300f, 300f)
    private val mask: LandMask
    private val fx = ParticleSystem(600)
    private val flakes = FloatArray(160 * 4)
    private val pose = Pose()
    private val model = FloatArray(16)
    private val q = FloatArray(4)
    private val ballRot = floatArrayOf(0f, 0f, 0f, 1f)
    private val ballRot2 = floatArrayOf(0f, 0f, 0f, 1f)

    // onboarding props
    private val rollField = SnowField(0, -4f, -4f, 8f, 8f, 0.02f)
    private val rollTerrain: TerrainMesh
    private var rollSnow = 8f
    private var rollAngle = 0f
    private var prevBallX = 0f
    private var prevBallZ = 0f
    private val bridge = BridgeGrid(0, BRIDGE_X - 2.5f, -1f, 5f, 3f, 0f, 0f)
    private var bridgeSnow = 0f
    private var lastCycle = -1

    companion object {
        const val WATER_Y = -0.85f
        const val BRIDGE_X = 40f
        val ISLAND_R = 6.2f
    }

    init {
        for (i in 0 until 9) {
            val a = PI_F + (i / 8f) * PI_F + rng.range(-0.12f, 0.12f) // back half
            val r = rng.range(4.2f, 5.6f)
            val s = rng.range(0.9f, 1.5f)
            Inst.add(pines, cos(a) * r, 0f, sin(a) * r, yaw = rng.range(0f, TAU), sx = s, sy = s, sz = s, sway = 1f)
        }
        for (i in 0 until 6) {
            val a = rng.range(-PI_F, 0f); val r = rng.range(18f, 30f)
            Inst.add(pines, cos(a) * r, WATER_Y + 0.3f, sin(a) * r - 6f, yaw = rng.range(0f, TAU), sx = 1.8f, sy = 1.8f, sz = 1.8f, sway = 1f)
        }
        Inst.add(snowmen, -3.4f, 0f, -1.2f, yaw = 0.5f)
        Inst.add(lanterns, 2.9f, 0f, -0.9f)
        Inst.add(lanterns, -2.4f, 0f, 1.8f)
        Inst.add(gifts, 2.0f, 0f, 1.4f, yaw = 0.4f, gloss = 0.5f)
        Inst.add(gifts, 2.45f, 0f, 1.0f, yaw = -0.3f, sx = 0.7f, sy = 0.7f, sz = 0.7f, color = 0xB46CFF, gloss = 0.5f)
        for (i in 0 until 10) {
            val a = PI_F + i / 9f * PI_F
            val r = rng.range(90f, 140f); val h = rng.range(25f, 50f)
            Inst.add(mountains, cos(a) * r, WATER_Y - 2f, sin(a) * r, yaw = rng.range(0f, TAU), sx = h, sy = h, sz = h)
        }
        Inst.add(island, 0f, 0f, 0f)
        Inst.add(podium, 0f, 0f, 0f)
        Inst.add(slabs, 0f, 0f, 0f)
        for (i in 0 until 12) {
            val a = rng.range(0f, TAU); val r = rng.range(8f, 20f)
            floeData += floatArrayOf(cos(a) * r, sin(a) * r, rng.range(0.4f, 1.2f), rng.range(0f, TAU), rng.range(0f, TAU))
        }
        mask = LandMask(-24f, -24f, 48f, 48f, 0.5f) { x, z -> x * x + z * z < ISLAND_R * ISLAND_R }
        for (f in floeData) mask.stamp(f[0], f[1], f[2] * 0.9f)
        mask.beginRebuild(); mask.finishRebuild()
        rollField.generate(SnowStyle.UNIFORM, 1f, Rng(5))
        rollTerrain = TerrainMesh(rollField)
        for (i in 0 until 160) {
            flakes[i * 4] = rng.range(-8f, 8f); flakes[i * 4 + 1] = rng.range(0f, 8f); flakes[i * 4 + 2] = rng.range(-6f, 6f); flakes[i * 4 + 3] = rng.range(0f, TAU)
        }
        cam.x = 0f; cam.y = 2f; cam.z = 6f
    }

    fun wave() { waveAt = t }

    private fun heroMode() = mode == MenuMode.HOME || mode == MenuMode.SHOP || mode == MenuMode.PROFILE || mode == MenuMode.BACKDROP

    private fun buildIsland(): Mesh {
        val b = MeshBuilder()
        b.color(0x8FB4DA).at(0f, -3.2f, 0f, scx = ISLAND_R, scy = 3.2f, scz = ISLAND_R).cylinder(36, capColor = 0xF4FAFF)
        b.color(0xF6FBFF).at(0f, -0.02f, 0f, scx = ISLAND_R, scy = 0.9f, scz = ISLAND_R).torus(1f, 0.22f, 40, 8)
        val r = Rng(3)
        for (i in 0 until 14) {
            val a = i / 14f * TAU
            b.at(cos(a) * ISLAND_R, 0f, sin(a) * ISLAND_R, scx = r.range(0.4f, 0.8f), scy = r.range(0.2f, 0.35f), scz = r.range(0.4f, 0.8f)).sphere(10, 6)
        }
        return b.build()
    }

    private fun buildPodium(): Mesh {
        val b = MeshBuilder()
        b.color(0xEAF6FF).at(0f, 0f, 0f, scx = 1.05f, scy = 0.28f, scz = 1.05f).cylinder(28, capColor = 0xFFFFFF)
        b.color(0xFFC83D).at(0f, 0.27f, 0f, scx = 1.06f, scy = 0.4f, scz = 1.06f).torus(1f, 0.08f, 32, 6)
        b.color(0xFF4D8D).at(0f, 0.04f, 0f, scx = 1.08f, scy = 0.5f, scz = 1.08f).torus(1f, 0.07f, 32, 6)
        return b.build()
    }

    private fun buildBridgeSlabs(): Mesh {
        val md = MeshData()
        RaceScene.addSlab(md, BRIDGE_X - 2.5f, BRIDGE_X + 2.5f, 0f, WATER_Y - 3f, -8f, -1f, 0xF0F6FF, 0xA9C6E6, 0x5F7FA8)
        RaceScene.addSlab(md, BRIDGE_X - 2.5f, BRIDGE_X + 2.5f, 0f, WATER_Y - 3f, 2f, 9f, 0xF0F6FF, 0xA9C6E6, 0x5F7FA8)
        return Mesh(md, true)
    }

    // ===================================================================== update
    override fun update(dt: Float) {
        t += dt
        res.time = t
        if (mode != lastMode) { lastMode = mode; modeT = 0f; resetOnboarding() } else modeT += dt
        fx.update(dt) { x, z -> if (x * x + z * z < ISLAND_R * ISLAND_R) 0f else WATER_Y }
        when (mode) {
            MenuMode.ONBOARD_ROLL -> updateRoll(dt)
            MenuMode.ONBOARD_BRIDGE -> updateBridge(dt)
            else -> {}
        }
        updateCamera(dt)
    }

    private fun resetOnboarding() {
        rollField.generate(SnowStyle.UNIFORM, 1f, Rng(5))
        rollSnow = 8f; rollAngle = 0f
        for (i in bridge.state.indices) { bridge.state[i] = BridgeGrid.EMPTY }
        bridgeSnow = 0f; lastCycle = -1
    }

    private fun updateRoll(dt: Float) {
        val cycle = 7.5f
        if (modeT > cycle) { modeT = 0f; rollField.generate(SnowStyle.UNIFORM, 1f, Rng(5)); rollSnow = 8f; rollAngle = 0f }
        rollAngle += dt * 1.05f
        val r = rollRadius()
        val path = 1.5f
        val bx = cos(rollAngle) * path; val bz = sin(rollAngle) * path
        val got = rollField.collect(bx, bz, 0.42f + 0.5f * r, t, -sin(rollAngle), cos(rollAngle))
        rollSnow += got
        rollBall(ballRot, bx, bz, r)
        if (rng.chance(0.6f)) fx.emit(bx, 0.1f, bz, rng.range(-0.6f, 0.6f), rng.range(1f, 2f), rng.range(-0.6f, 0.6f), 0.5f, 0.2f, 0.45f, 0xFFFFFFFF.toInt(), ParticleAtlas.PUFF, gravity = 3f, dragK = 2f)
    }

    private fun rollRadius() = Racer.radiusFor(rollSnow * 0.35f).coerceIn(0.3f, 0.8f)

    private fun rollBall(rot: FloatArray, bx: Float, bz: Float, r: Float) {
        val dx = bx - prevBallX; val dz = bz - prevBallZ
        val d = sqrt(dx * dx + dz * dz)
        if (d > 1e-4f && d < 1f) {
            Quat.axisAngle(q, dz / d, 0f, -dx / d, d / max(0.2f, r))
            Quat.mul(rot, q, rot); Quat.normalize(rot)
        }
        prevBallX = bx; prevBallZ = bz
    }

    private fun updateBridge(dt: Float) {
        val cycle = 6.5f
        val c = (modeT / cycle).toInt()
        if (c != lastCycle) {
            lastCycle = c
            for (i in bridge.state.indices) bridge.state[i] = BridgeGrid.EMPTY
            bridgeSnow = 420f
        }
        val u = (modeT % cycle)
        val z = -5.5f + min(u, 5f) * 2.2f
        val r = Racer.radiusFor(bridgeSnow)
        val ballZ = z + Racer.BODY_R + max(r, 0.2f) * 0.92f
        if (ballZ > -1f && ballZ < 2f && bridgeSnow > 0f) {
            val placedBefore = bridge.solidCount
            val spent = bridge.fillDiskImpl(BRIDGE_X, ballZ, 0.78f, 0, t, bridgeSnow, 15f)
            bridgeSnow -= spent
            if (bridge.solidCount > placedBefore) {
                audio?.play(Sfx.BRIDGE_BLOCK, 0.35f, 0.9f + bridge.solidCount * 0.01f)
                fx.emit(BRIDGE_X + rng.range(-0.4f, 0.4f), 0.2f, ballZ, 0f, 1.2f, 0f, 0.5f, 0.3f, 0.5f, 0xFFFFFFFF.toInt(), ParticleAtlas.PUFF, gravity = 1.5f, dragK = 2f)
            }
        }
        rollBall(ballRot2, BRIDGE_X, ballZ, r)
    }

    private fun updateCamera(dt: Float) {
        var cx = 0f; var cy = 2f; var cz = 5.6f; var lx = 0f; var ly = 1f; var lz = 0f
        when (mode) {
            MenuMode.HOME -> { cx = 0f; cy = 1.7f; cz = 5.6f; lx = 0f; ly = 0.42f; lz = 0f }
            MenuMode.SHOP, MenuMode.PROFILE -> { cx = 0.3f; cy = 1.25f; cz = 6.2f; lx = 0.3f; ly = -0.55f; lz = 0f }
            MenuMode.ONBOARD_ROLL -> { cx = 0f; cy = 7.6f; cz = 7.0f; lx = 0f; ly = 0f; lz = 2.6f }
            MenuMode.ONBOARD_BRIDGE -> { cx = BRIDGE_X + 6.5f; cy = 5.5f; cz = -4.5f; lx = BRIDGE_X; ly = 0f; lz = 0.8f }
            MenuMode.ONBOARD_BONK -> { cx = 2.8f; cy = 4.6f; cz = -8.6f; lx = 0f; ly = 0f; lz = -2.2f }
            MenuMode.ONBOARD_COLLECT -> { cx = 0f; cy = 2.5f; cz = 8.6f; lx = 0f; ly = 0.75f; lz = 0f }
            MenuMode.BACKDROP -> { cx = sin(t * 0.08f) * 7f; cy = 4f; cz = cos(t * 0.08f) * 7f; lx = 0f; ly = 0.8f; lz = 0f }
        }
        val k = if (modeT < 0.05f && (mode == MenuMode.ONBOARD_BRIDGE)) 100f else 4f
        cam.x = damp(cam.x, cx, k, dt); cam.y = damp(cam.y, cy, k, dt); cam.z = damp(cam.z, cz, k, dt)
        cam.tx = damp(cam.tx, lx, k, dt); cam.ty = damp(cam.ty, ly, k, dt); cam.tz = damp(cam.tz, lz, k, dt)
        cam.fovDeg = damp(cam.fovDeg, if (heroMode()) 44f else 60f, k, dt)
        cam.aspect = res.width.toFloat() / max(1, res.height)
        cam.far = 260f
        cam.update()
    }

    // ===================================================================== render
    override fun render() {
        res.chars.clear(); res.ballCasters.clear(); blocks.clear(); floes.clear()
        for (f in floeData) Inst.add(floes, f[0], WATER_Y + 0.05f + sin(t * 1.3f + f[4]) * 0.05f, f[1], yaw = f[3] + t * 0.03f, sx = f[2], sy = 1f, sz = f[2])
        val balls = ArrayList<FloatArray>() // x,y,z,r,ballIndex(0=main,1=rival,..), rotIndex
        val ballDefs = ArrayList<BallDef>()
        val rots = ArrayList<FloatArray>()
        fun ballAt(x: Float, y: Float, z: Float, r: Float, def: BallDef, rot: FloatArray) {
            balls += floatArrayOf(x, y, z, r); ballDefs += def; rots += rot
            Inst.add(res.ballCasters, x, y, z, sx = r, sy = r, sz = r, outline = 1.2f)
        }
        fillCharacters(::ballAt)
        if (mode == MenuMode.ONBOARD_BRIDGE) fillBridge()

        res.shadow.update(light.sunDir[0], light.sunDir[1], light.sunDir[2], cam.tx, 0f, cam.tz, 9f)
        if (res.beginShadowPass()) {
            for (b in statics) if (b !== mountains && b !== island) b.draw()
            if (heroMode()) podium.draw()
            blocks.draw()
            for (b in res.chars.all()) b.draw()
            res.ballCasters.draw()
            res.endShadowPass()
        }
        GLES30.glViewport(0, 0, res.width, res.height)
        GLES30.glClearColor(light.fog[0], light.fog[1], light.fog[2], 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glDisable(GLES30.GL_BLEND)
        res.common(res.lit, cam, light)
        for (b in statics) b.draw()
        if (heroMode()) podium.draw()
        floes.draw(); blocks.draw()
        for (b in res.chars.all()) b.draw()
        // balls
        res.common(res.ball, cam, light)
        for (i in balls.indices) {
            val b = balls[i]; val d = ballDefs[i]; val rot = rots[i]
            Mat4.trs(model, 0, b[0], b[1], b[2], rot[0], rot[1], rot[2], rot[3], b[3], b[3], b[3])
            res.ball.mat4("uModel", model)
            res.ball.vec3("uBase", ((d.base shr 16) and 0xFF) / 255f, ((d.base shr 8) and 0xFF) / 255f, (d.base and 0xFF) / 255f)
            res.ball.vec3("uAccent", ((d.accent shr 16) and 0xFF) / 255f, ((d.accent shr 8) and 0xFF) / 255f, (d.accent and 0xFF) / 255f)
            res.ball.i("uPattern", d.pattern.ordinal)
            res.ball.f("uGlow", d.glow)
            res.ballMesh.draw()
        }
        if (mode == MenuMode.ONBOARD_ROLL) TerrainMesh.drawAll(res, cam, light, listOf(rollTerrain))
        water.draw(res, cam, light, WATER_Y, mask)
        res.beginOutline(cam, light)
        for (b in res.chars.all()) b.draw()
        res.ballCasters.draw()
        res.endOutline()
        res.drawSky(cam, light)

        // billboards
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        res.bill.clear(); res.ground.clear()
        val pa = res.particleAtlas
        fx.fill(pa, res.bill, res.ground)
        val dt = 1f / 60f
        for (i in 0 until 160) {
            val o = i * 4
            flakes[o + 1] -= 0.9f * dt
            flakes[o] += (0.25f + sin(t + flakes[o + 3]) * 0.3f) * dt
            if (flakes[o + 1] < -0.5f) { flakes[o + 1] = 8f; flakes[o] = rng.range(-8f, 8f) }
            if (flakes[o] > 8f) flakes[o] -= 16f
            res.bill.add(cam.tx + flakes[o], flakes[o + 1], cam.tz + flakes[o + 2], 0.06f + (i % 3) * 0.025f, 0xFFFFFFFF.toInt(), 0.9f, pa.rects[ParticleAtlas.SOFT])
        }
        if (mode == MenuMode.HOME || mode == MenuMode.SHOP || mode == MenuMode.PROFILE) {
            // soft glow behind the hero
            res.bill.add(0f, 0.9f, -0.6f, 3.4f, 0xFFFFFFFF.toInt(), 0.25f, pa.rects[ParticleAtlas.GLOW], additive = true)
        }
        res.beginBillboards(cam, true)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, pa.texture)
        res.ground.draw()
        res.beginBillboards(cam, false)
        res.bill.draw()
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
    }

    private fun basePose(p: Pose, x: Float, y: Float, z: Float, yaw: Float, seed: Float) {
        p.x = x; p.y = y; p.z = z; p.yaw = yaw
        p.move = 0f; p.phase = 0f; p.pushing = false; p.ballR = 0f; p.lean = 0f; p.roll = 0f
        p.squash = sin(t * 2.3f + seed) * 0.018f
        p.jump = 0f; p.armsUp = 0f; p.wave = 0f
        val bl = (t + seed * 1.37f) % 3.6f
        p.blink = if (bl < 0.12f) 0.12f else 1f
        p.mouth = 0f; p.scale = 1f; p.time = t + seed; p.lookX = sin(t * 0.5f + seed) * 0.6f; p.outline = 1f
    }

    private fun fillCharacters(ballAt: (Float, Float, Float, Float, BallDef, FloatArray) -> Unit) {
        val p = pose
        when (mode) {
            MenuMode.HOME, MenuMode.SHOP, MenuMode.PROFILE, MenuMode.BACKDROP -> {
                val since = t - waveAt
                val yaw = if (mode == MenuMode.HOME) sin(t * 0.35f) * 0.25f + dragYaw else dragYaw + t * 0.25f * (if (mode == MenuMode.SHOP) 0f else 0f)
                basePose(p, 0f, 0.28f, 0f, yaw, 0f)
                if (since < 1.6f) {
                    p.wave = 1f; p.mouth = 1f
                    p.jump = if (since < 0.5f) sin(since / 0.5f * PI_F) * 0.35f else 0f
                    p.squash = if (since > 0.45f && since < 0.65f) -0.12f * sin((since - 0.45f) / 0.2f * PI_F) else p.squash
                } else if ((t % 9f) < 1.2f) { p.wave = 0.8f; p.mouth = 0.5f }
                res.rig.draw(look, p)
                // ball beside the hero
                val br = 0.45f
                Quat.euler(ballRot, t * 0.2f, 0f, 0f)
                ballAt(1.25f, br, 0.35f, br, ball, ballRot)
                // trail sparkle preview
                if (trail.id != "none" && rng.chance(0.35f)) {
                    val col = trail.colors[rng.nextInt(trail.colors.size)]
                    fx.emit(1.25f + rng.range(-0.4f, 0.4f), rng.range(0.3f, 1.1f), 0.35f + rng.range(-0.3f, 0.3f), 0f, rng.range(0.3f, 0.8f), 0f, 0.9f, 0.22f, 0.05f, col, spriteOf(trail.shape), additive = true, rotation = rng.range(0f, TAU))
                }
            }
            MenuMode.ONBOARD_ROLL -> {
                val r = rollRadius()
                val a = rollAngle
                val path = 1.5f
                val off = Racer.BODY_R + r * 0.92f
                val heading = atan2(-sin(a), cos(a)) // tangent of the circle (counter clockwise)
                val bx = cos(a) * path; val bz = sin(a) * path
                val cx = bx - sin(heading) * off; val cz = bz - cos(heading) * off
                basePose(p, cx, 0.02f, cz, heading, 0f)
                p.move = 1f; p.phase = t * 9f; p.pushing = true; p.ballR = r; p.lean = 0.2f
                res.rig.draw(look, p)
                ballAt(bx, r, bz, r, ball, ballRot)
            }
            MenuMode.ONBOARD_BRIDGE -> {
                val u = modeT % 6.5f
                val z = -5.5f + min(u, 5f) * 2.2f
                val r = Racer.radiusFor(bridgeSnow)
                basePose(p, BRIDGE_X, 0.2f.takeIf { z > -1f && z < 2f } ?: 0f, z, 0f, 0f)
                val moving = u < 5f
                p.move = if (moving) 1f else 0f; p.phase = t * 9f; p.pushing = moving && bridgeSnow > 1f; p.ballR = r; p.lean = if (moving) 0.2f else 0f
                if (!moving) { p.armsUp = 1f; p.wave = 1f; p.jump = abs(sin(t * 6f)) * 0.3f; p.mouth = 1f }
                res.rig.draw(look, p)
                if (bridgeSnow > 1f) {
                    val bz = z + Racer.BODY_R + max(r, 0.2f) * 0.92f
                    ballAt(BRIDGE_X, r + (if (bz > -1f && bz < 2f) 0.2f else 0f), bz, r, ball, ballRot2)
                }
            }
            MenuMode.ONBOARD_BONK -> {
                val cycle = 4.6f
                val u = modeT % cycle
                // hero with a big ball comes from the back towards the camera
                val big = 0.95f
                val hz = -5.2f + u * 1.9f
                basePose(p, 0.15f, 0f, hz, 0f, 0f)
                p.move = 1f; p.phase = t * 9f; p.pushing = true; p.ballR = big; p.lean = 0.2f
                res.rig.draw(look, p)
                val bz = hz + Racer.BODY_R + big * 0.92f
                Quat.axisAngle(q, 1f, 0f, 0f, hz / big)
                ballAt(0.15f, big, bz, big, ball, q.copyOf())
                // rival with a tiny ball walks the other way and gets bonked
                val rival = Catalog.character("bubbles")
                val hitT = 2.0f
                val rp = Pose()
                if (u < hitT) {
                    val rz = 2.6f - u * 1.0f
                    basePose(rp, 0f, 0f, rz, PI_F, 3f)
                    rp.move = 1f; rp.phase = t * 9f; rp.pushing = true; rp.ballR = 0.3f
                    res.rig.draw(rival, rp)
                    Quat.axisAngle(q, 1f, 0f, 0f, -rz / 0.3f)
                    ballAt(0f, 0.3f, rz - Racer.BODY_R - 0.28f, 0.3f, Catalog.ball("candy"), q.copyOf())
                } else {
                    val k = u - hitT
                    if (k < 0.05f && modeT > 0.2f) {
                        audio?.play(Sfx.BONK, 0.7f)
                        for (i in 0 until 9) fx.emit(0f, 1.1f, 0.6f, rng.range(-2f, 2f), rng.range(1f, 3f), rng.range(-1f, 2f), 0.7f, 0.3f, 0.12f, 0xFFFFE27A.toInt(), ParticleAtlas.STAR, gravity = 4f, rotSpeed = 5f)
                    }
                    val kk = min(k, 1.2f)
                    val rz = 0.6f + kk * 2.2f
                    basePose(rp, 0.3f + kk * 0.6f, abs(sin(kk / 1.2f * PI_F)) * 0.7f, rz, PI_F, 3f)
                    rp.roll = k * 9f * clamp01(1f - k / 1.6f); rp.armsUp = 1f; rp.mouth = 1f; rp.blink = 0.15f
                    res.rig.draw(rival, rp)
                    for (s2 in 0 until 3) {
                        val a = t * 7f + s2 * TAU / 3f
                        fx.emit(0.3f + kk * 0.6f + cos(a) * 0.35f, 1.45f, rz + sin(a) * 0.35f, 0f, 0f, 0f, 0.05f, 0.22f, 0.22f, 0xFFFFE27A.toInt(), ParticleAtlas.STAR)
                    }
                }
            }
            MenuMode.ONBOARD_COLLECT -> {
                val ids = listOf("waddles", "ember", look.id, "sparkle", "frostking")
                val balls = listOf("candy", "galaxy", ball.id, "rainbow", "golden")
                ids.forEachIndexed { i, id ->
                    val x = -1.9f + i * 0.95f
                    val z = -abs(x) * 0.45f
                    val phase = (t * 1.6f - i * 0.35f) % 3f
                    basePose(p, x, 0f, z, -x * 0.12f, i.toFloat())
                    if (phase < 0.6f) { p.jump = sin(phase / 0.6f * PI_F) * 0.45f; p.armsUp = 1f; p.mouth = 1f }
                    res.rig.draw(Catalog.character(id), p)
                    val br = 0.22f
                    val bq = FloatArray(4); Quat.euler(bq, t + i, 0f, 0f)
                    ballAt(x + 0.3f, br, z + 0.55f, br, Catalog.ball(balls[i]), bq)
                }
            }
        }
    }

    private fun fillBridge() {
        for (r in 0 until bridge.rows) for (c in 0 until bridge.cols) {
            val i = bridge.idx(c, r)
            if (bridge.state[i] == BridgeGrid.EMPTY) continue
            val age = t - bridge.placedAt[i]
            val s = if (age < 0.25f) Easing.outBack(clamp01(age / 0.25f)) else 1f
            Inst.add(blocks, bridge.cellX(c), 0.02f - (1f - s) * 0.4f, bridge.cellZ(r), sx = s, sy = s, sz = s, color = 0xF2F8FF, gloss = 0.15f)
        }
    }

    private fun spriteOf(s: TrailShape) = when (s) {
        TrailShape.CIRCLE -> ParticleAtlas.SOFT; TrailShape.STAR -> ParticleAtlas.STAR; TrailShape.HEART -> ParticleAtlas.HEART
        TrailShape.FLAKE -> ParticleAtlas.FLAKE; TrailShape.SPARKLE -> ParticleAtlas.SPARKLE; TrailShape.NOTE -> ParticleAtlas.NOTE
    }

    override fun release() {
        rollTerrain.release()
        mask.release()
    }
}
