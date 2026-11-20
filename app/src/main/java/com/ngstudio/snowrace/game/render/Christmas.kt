package com.ngstudio.snowrace.game.render

import com.ngstudio.snowrace.core.PI_F
import com.ngstudio.snowrace.core.TAU
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Seasonal switch for the Christmas look of the race. */
object Holiday {
    /** Debug override (`adb shell am start ... --ez xmas true|false`); null follows the date. */
    @Volatile var force: Boolean? = null

    /**
     * Christmas decorations show from 15 November to 10 January (device date). To keep them all year, return true;
     * to turn them off, return false.
     */
    fun christmas(cal: Calendar = Calendar.getInstance()): Boolean {
        force?.let { return it }
        val m = cal.get(Calendar.MONTH)
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return (m == Calendar.NOVEMBER && d >= 15) || m == Calendar.DECEMBER || (m == Calendar.JANUARY && d <= 10)
    }
}

/** Christmas prop meshes (built once per GL context like every other prop). */
object XmasProps {
    private const val SNOW = 0xF4FAFF
    const val RED = 0xE8263F
    const val GREEN = 0x23A05A
    const val GOLD = 0xFFC83D
    private const val BLUE = 0x3D8BFF
    private const val SILVER = 0xDDE6F2

    /** Tier layout of [tree]: base height, radius, cone height. Shared with the twinkle lights placed on it. */
    val TREE_TIERS = arrayOf(floatArrayOf(0.3f, 0.95f, 0.95f), floatArrayOf(0.8f, 0.76f, 0.85f), floatArrayOf(1.25f, 0.56f, 0.75f), floatArrayOf(1.65f, 0.37f, 0.62f))
    const val TREE_TOP = 2.3f

    /** Decorated fir: four snowy tiers hung with baubles. The star and the twinkling lights are animated separately. */
    fun tree(): Mesh {
        val b = MeshBuilder()
        b.color(0x7A4E2D).at(0f, 0f, 0f, scx = 0.15f, scy = 0.5f, scz = 0.15f).cylinder(8)
        val greens = intArrayOf(0x1F7A4D, 0x23885A, 0x2A9663, 0x31A36C)
        val baubles = intArrayOf(RED, GOLD, BLUE, SILVER, RED)
        TREE_TIERS.forEachIndexed { i, t ->
            val (y, r, h) = Triple(t[0], t[1], t[2])
            b.color(greens[i]).at(0f, y, 0f, yaw = i * 0.5f, scx = r, scy = h, scz = r).cone(10, flat = true)
            b.color(SNOW).at(0f, y + h * 0.5f, 0f, yaw = i * 0.5f + 0.3f, scx = r * 0.5f, scy = h * 0.5f, scz = r * 0.5f).cone(10, flat = true, base = false)
            val n = if (i == 3) 3 else 5
            for (k in 0 until n) {
                val a = k * TAU / n + i * 0.7f
                val rr = r * 0.86f
                b.color(baubles[(k + i) % baubles.size]).at(cos(a) * rr, y + h * 0.1f, sin(a) * rr, scx = 0.075f, scy = 0.075f, scz = 0.075f).sphere(8, 6)
            }
        }
        return b.build()
    }

    /** Five-point gold star (flat, faces ±z). Its colour is above 1 so it glows. */
    fun star(): Mesh {
        val b = MeshBuilder()
        b.color(1.6f, 1.3f, 0.35f)
        for (i in 0 until 5) b.at(0f, 0f, 0f, roll = i * TAU / 5f, scx = 0.17f, scy = 0.46f, scz = 0.09f).cone(4, flat = true)
        b.at(0f, 0f, 0f, scx = 0.19f, scy = 0.19f, scz = 0.11f).sphere(8, 6)
        return b.build()
    }

    /** One light bulb. Bright white base so the instance tint picks its colour and the excess above 1 glows. */
    fun bulb(): Mesh = MeshBuilder().color(1.35f, 1.35f, 1.35f).at(0f, 0f, 0f, scx = 1f, scy = 1.25f, scz = 1f).sphere(6, 4).build()

    /** Holly wreath facing ±z with red berries and a bow at the bottom. */
    fun wreath(): Mesh {
        val b = MeshBuilder()
        b.color(0x1F7A4D).at(0f, 0f, 0f, pitch = PI_F / 2, scx = 1f, scy = 1f, scz = 1f).torus(1f, 0.32f, 18, 6)
        b.color(0x2E9A62)
        for (i in 0 until 9) {
            val a = i * TAU / 9f
            b.at(cos(a) * 1.05f, sin(a) * 1.05f, 0f, roll = a, scx = 0.32f, scy = 0.18f, scz = 0.2f).sphere(6, 4)
        }
        b.color(RED)
        for (i in 0 until 6) {
            val a = i * TAU / 6f + 0.3f
            b.at(cos(a) * 0.95f, sin(a) * 0.95f, 0.25f, scx = 0.11f, scy = 0.11f, scz = 0.11f).sphere(6, 4)
        }
        b.at(-0.28f, -1.0f, 0.22f, roll = 0.5f, scx = 0.28f, scy = 0.2f, scz = 0.12f).sphere(8, 6)
        b.at(0.28f, -1.0f, 0.22f, roll = -0.5f, scx = 0.28f, scy = 0.2f, scz = 0.12f).sphere(8, 6)
        b.at(-0.16f, -1.38f, 0.2f, roll = -0.25f, scx = 0.08f, scy = 0.32f, scz = 0.05f).box()
        b.at(0.16f, -1.38f, 0.2f, roll = 0.25f, scx = 0.08f, scy = 0.32f, scz = 0.05f).box()
        b.color(0xB81D33).at(0f, -1.0f, 0.24f, scx = 0.11f, scy = 0.11f, scz = 0.11f).sphere(6, 4)
        return b.build()
    }

    /** A little pile of wrapped presents. */
    fun giftPile(): Mesh {
        val b = MeshBuilder()
        fun gift(x: Float, y0: Float, z: Float, s: Float, h: Float, yaw: Float, box: Int, ribbon: Int) {
            b.color(box).at(x, y0 + h / 2f, z, yaw = yaw, scx = s, scy = h, scz = s).roundedBox(0.12f, 2)
            b.color(ribbon)
            b.at(x, y0 + h / 2f, z, yaw = yaw, scx = s * 0.24f, scy = h * 1.04f, scz = s * 1.04f).box()
            b.at(x, y0 + h / 2f, z, yaw = yaw, scx = s * 1.04f, scy = h * 1.04f, scz = s * 0.24f).box()
            for (sd in intArrayOf(-1, 1)) {
                b.at(x + sd * s * 0.17f, y0 + h + s * 0.08f, z, yaw = yaw, roll = -sd * 0.6f, scx = s * 0.18f, scy = s * 0.2f, scz = s * 0.18f).torus(1f, 0.4f, 10, 5)
            }
        }
        gift(0f, 0f, 0f, 0.7f, 0.6f, 0.2f, RED, GOLD)
        gift(0.66f, 0f, 0.18f, 0.5f, 0.42f, -0.4f, GREEN, RED)
        gift(-0.58f, 0f, 0.26f, 0.46f, 0.56f, 0.5f, BLUE, SILVER)
        gift(0.04f, 0.6f, -0.02f, 0.42f, 0.36f, -0.3f, 0xF6F2EA, RED)
        b.color(SNOW).at(0f, 0f, 0.05f, scx = 1.05f, scy = 0.1f, scz = 0.8f).sphere(10, 6)
        return b.build()
    }

    /** Snowman in a Santa hat and a green scarf. */
    fun santaSnowman(): Mesh {
        val b = MeshBuilder()
        b.color(SNOW).at(0f, 0.42f, 0f, scx = 0.46f, scy = 0.42f, scz = 0.46f).sphere(16, 10)
        b.at(0f, 1.0f, 0f, scx = 0.34f, scy = 0.32f, scz = 0.34f).sphere(16, 10)
        b.at(0f, 1.46f, 0f, scx = 0.25f, scy = 0.24f, scz = 0.25f).sphere(14, 10)
        // Santa hat: red cone leaning to one side, white fur brim and pompom
        val lean = -0.45f
        b.color(RED).at(0f, 1.6f, 0f, roll = lean, scx = 0.24f, scy = 0.52f, scz = 0.24f).cone(12, flat = false)
        b.color(0xFFFFFF).at(0f, 1.6f, 0f, scx = 0.27f, scy = 0.27f, scz = 0.27f).torus(1f, 0.28f, 14, 6)
        b.at(-sin(lean) * 0.5f, 1.6f + cos(lean) * 0.5f, 0f, scx = 0.08f, scy = 0.08f, scz = 0.08f).sphere(8, 6)
        b.color(0xFF8A2B).at(0f, 1.46f, 0.22f, pitch = PI_F / 2, scx = 0.045f, scy = 0.2f, scz = 0.045f).cone(8)
        b.color(0x1B2A4A)
        for (x in floatArrayOf(-0.09f, 0.09f)) b.at(x, 1.53f, 0.2f, scx = 0.035f, scy = 0.035f, scz = 0.03f).sphere(6, 4)
        b.color(RED)
        for (y in floatArrayOf(0.92f, 1.06f)) b.at(0f, y, 0.33f, scx = 0.04f, scy = 0.04f, scz = 0.03f).sphere(6, 4)
        b.color(0x7A4E2D)
        b.at(0.3f, 1.05f, 0f, roll = -1.0f, scx = 0.025f, scy = 0.45f, scz = 0.025f).cylinder(5, caps = false)
        b.at(-0.3f, 1.05f, 0f, roll = 1.0f, scx = 0.025f, scy = 0.45f, scz = 0.025f).cylinder(5, caps = false)
        b.color(GREEN).at(0f, 1.24f, 0f, scx = 0.3f, scy = 0.3f, scz = 0.3f).torus(1f, 0.2f, 16, 6)
        b.at(0.12f, 1.1f, 0.24f, roll = 0.2f, scx = 0.07f, scy = 0.2f, scz = 0.04f).box()
        return b.build()
    }
}

/**
 * Christmas dressing for one race: decorated trees with twinkling lights and spinning stars, chasing string lights
 * along both edges of the track, presents, wreaths and a big star over the finish. The static pieces go into the
 * scene's static batches; [fill] animates the lights and stars each frame, only near the camera.
 */
internal class ChristmasDecor(res: RenderResources, low: Boolean) {
    val trees = res.prop("xmas.tree")
    val gifts = res.prop("xmas.gifts")
    val wreaths = res.prop("xmas.wreath")
    private val bulbs = res.prop("xmas.bulb")
    private val stars = res.prop("xmas.star")
    val staticBatches get() = listOf(trees, gifts, wreaths)
    val dynamicBatches get() = listOf(bulbs, stars)

    private val lightSpacing = if (low) 1.3f else 0.8f
    private val treeLights = !low

    // x, y, z, size, colour index, phase, mode (0 = chasing string, 1 = random twinkle)
    private var lights = FloatArray(7 * 256)
    private var lightCount = 0
    // x, y, z, size, phase
    private val starData = ArrayList<FloatArray>()

    private fun light(x: Float, y: Float, z: Float, size: Float, color: Int, phase: Float, mode: Int) {
        if ((lightCount + 1) * 7 > lights.size) lights = lights.copyOf(lights.size * 2)
        val o = lightCount * 7
        lights[o] = x; lights[o + 1] = y; lights[o + 2] = z; lights[o + 3] = size
        lights[o + 4] = color.toFloat(); lights[o + 5] = phase; lights[o + 6] = mode.toFloat()
        lightCount++
    }

    /** A decorated tree of height scale [s] standing at ground level [y]. */
    fun tree(x: Float, y: Float, z: Float, s: Float, phase: Float) {
        Inst.add(trees, x, y, z, sx = s, sy = s, sz = s, gloss = 0.25f)
        starData += floatArrayOf(x, y + XmasProps.TREE_TOP * s, z, 0.62f * s, phase)
        if (!treeLights) return
        // little lights between the baubles on the lower three tiers
        for (i in 0..2) {
            val t = XmasProps.TREE_TIERS[i]
            for (k in 0 until 4) {
                val a = k * TAU / 4f + i * 0.9f + 0.4f
                val rr = t[1] * 0.62f
                light(x + cos(a) * rr * s, y + (t[0] + t[2] * 0.35f) * s, z + sin(a) * rr * s, 0.05f * s, (k + i) % 4, phase + k * 1.7f + i, 1)
            }
        }
    }

    /** A row of string lights from [z0] to [z1] at height [y]; the glow chases along the row. */
    fun lightRow(x: Float, y: Float, z0: Float, z1: Float) {
        var z = z0
        var i = 0
        while (z <= z1) {
            light(x, y + 0.03f, z, 0.1f, i % 4, z * 1.1f, 0)
            z += lightSpacing; i++
        }
    }

    fun gifts(x: Float, y: Float, z: Float, s: Float, yaw: Float) = Inst.add(gifts, x, y, z, yaw = yaw, sx = s, sy = s, sz = s, gloss = 0.35f)

    /** A wreath facing the approaching camera (−z). */
    fun wreath(x: Float, y: Float, z: Float, s: Float) = Inst.add(wreaths, x, y, z, yaw = PI_F, sx = s, sy = s, sz = s, gloss = 0.2f)

    fun bigStar(x: Float, y: Float, z: Float, s: Float) { starData += floatArrayOf(x, y, z, s, 0f) }

    /** Animates the lights and stars within [range] of the camera. */
    fun fill(t: Float, camZ: Float, range: Float = 95f) {
        for (i in 0 until lightCount) {
            val o = i * 7
            val z = lights[o + 2]
            if (abs(z - camZ) > range) continue
            val on = if (lights[o + 6] == 0f) {
                val w = 0.5f + 0.5f * sin(t * 4.2f - lights[o + 5]) // a wave of light running along the string
                w * w
            } else {
                val w = 0.5f + 0.5f * sin(t * 2.3f + lights[o + 5])
                w * w * w
            }
            val c = BULB_COLORS[lights[o + 4].toInt()]
            val s = lights[o + 3]
            Inst.add(bulbs, lights[o], lights[o + 1], z, sx = s, sy = s, sz = s, color = MeshBuilder.mix(MeshBuilder.mix(c, 0x1B2A4A, 0.6f), c, on), emissive = 0.35f + 0.65f * on)
        }
        for (st in starData) {
            if (abs(st[2] - camZ) > range + 40f) continue
            val pulse = 0.5f + 0.5f * sin(t * 2.6f + st[4])
            val sc = st[3] * (1f + 0.08f * pulse)
            Inst.add(stars, st[0], st[1] + sin(t * 1.6f + st[4]) * 0.05f * st[3], st[2], yaw = t * 1.3f + st[4], sx = sc, sy = sc, sz = sc, emissive = 0.4f + 0.4f * pulse)
        }
    }

    companion object {
        private val BULB_COLORS = intArrayOf(0xFF3344, 0x33E066, 0xFFD040, 0x46A6FF)
        /** Bunting, balloons and canopy colours for the Christmas look. */
        val PALETTE = intArrayOf(XmasProps.RED, XmasProps.GREEN, XmasProps.GOLD, 0xF4F8FF, 0xC0182F)
    }
}
