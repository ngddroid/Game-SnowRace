package com.ngstudio.snowrace.game.render

import com.ngstudio.snowrace.core.PI_F
import com.ngstudio.snowrace.core.TAU
import kotlin.math.cos
import kotlin.math.sin

/** Factories for every procedural prop mesh. All meshes are unit-ish sized and instanced. */
object Props {
    private const val WHITE = 0xFFFFFF
    private const val SNOW = 0xF4FAFF

    fun sphere(seg: Int = 20) = MeshBuilder().sphere(seg, (seg * 0.7f).toInt()).build()
    fun cone() = MeshBuilder().cone(14, flat = false).build()
    fun cylinder() = MeshBuilder().cylinder(16).build()
    fun torus() = MeshBuilder().torus(1f, 0.22f, 22, 8).build()
    fun roundedBox() = MeshBuilder().roundedBox(0.22f, 4).build()
    fun box() = MeshBuilder().box().build()

    /** Clumpy snowball: displaced icosphere. */
    fun snowball() = MeshBuilder().icosphere(3, noise = 0.035f, seed = 7).build(instanced = false)
    fun smoothBall() = MeshBuilder().sphere(28, 20).build(instanced = false)

    fun pine(): Mesh {
        val b = MeshBuilder()
        b.color(0x7A4E2D).at(0f, 0f, 0f, scx = 0.14f, scy = 0.55f, scz = 0.14f).cylinder(8)
        val tiers = listOf(floatArrayOf(0.35f, 0.78f, 0.95f), floatArrayOf(0.85f, 0.62f, 0.85f), floatArrayOf(1.3f, 0.45f, 0.8f))
        val greens = listOf(0x2E7D5B, 0x2B8A60, 0x34966A)
        tiers.forEachIndexed { i, t ->
            b.color(greens[i]).at(0f, t[0], 0f, yaw = i * 0.6f, scx = t[1], scy = t[2], scz = t[1]).cone(9, flat = true)
            b.color(SNOW).at(0f, t[0] + t[2] * 0.42f, 0f, yaw = i * 0.6f + 0.2f, scx = t[1] * 0.62f, scy = t[2] * 0.6f, scz = t[1] * 0.62f).cone(9, flat = true, base = false)
        }
        return b.build()
    }

    fun rock(seed: Int = 3): Mesh = MeshBuilder().icosphere(1, noise = 0.2f, seed = seed, flat = true) { _, ny, _, _ ->
        if (ny > 0.5f) 0xF2F8FF else MeshBuilder.shade(0x8C9BB5, 0.85f + ny * 0.2f)
    }.build()

    fun crystal(): Mesh {
        val b = MeshBuilder()
        val spikes = listOf(floatArrayOf(0f, 0f, 0f, 1f), floatArrayOf(0.22f, -0.5f, 0.35f, 0.7f), floatArrayOf(-0.2f, 0.4f, -0.3f, 0.6f))
        for (s in spikes) {
            b.color(0.62f, 0.95f, 1.12f).at(s[0], 0f, s[1] * 0.4f, roll = s[2], scx = 0.2f * s[3], scy = 0.9f * s[3], scz = 0.2f * s[3]).cylinder(6, caps = false)
            b.color(0.8f, 1.05f, 1.2f).at(s[0] + sin(-s[2]) * 0.9f * s[3], cos(s[2]) * 0.9f * s[3], s[1] * 0.4f, roll = s[2], scx = 0.2f * s[3], scy = 0.35f * s[3], scz = 0.2f * s[3]).cone(6, flat = true, base = false)
        }
        b.color(SNOW).at(0f, 0f, 0f, scx = 0.55f, scy = 0.12f, scz = 0.55f).sphere(10, 6)
        return b.build()
    }

    fun snowman(): Mesh {
        val b = MeshBuilder()
        b.color(SNOW).at(0f, 0.42f, 0f, scx = 0.46f, scy = 0.42f, scz = 0.46f).sphere(16, 10)
        b.at(0f, 1.0f, 0f, scx = 0.34f, scy = 0.32f, scz = 0.34f).sphere(16, 10)
        b.at(0f, 1.46f, 0f, scx = 0.25f, scy = 0.24f, scz = 0.25f).sphere(14, 10)
        b.color(0x262A3B).at(0f, 1.62f, 0f, scx = 0.26f, scy = 0.03f, scz = 0.26f).cylinder(14)
        b.at(0f, 1.63f, 0f, scx = 0.17f, scy = 0.26f, scz = 0.17f).cylinder(14)
        b.color(0xFF4D8D).at(0f, 1.66f, 0f, scx = 0.175f, scy = 0.05f, scz = 0.175f).cylinder(14, caps = false)
        b.color(0xFF8A2B).at(0f, 1.46f, 0.22f, pitch = PI_F / 2, scx = 0.045f, scy = 0.2f, scz = 0.045f).cone(8)
        b.color(0x1B2A4A)
        for (x in listOf(-0.09f, 0.09f)) b.at(x, 1.53f, 0.2f, scx = 0.035f, scy = 0.035f, scz = 0.03f).sphere(6, 4)
        for (y in listOf(0.92f, 1.06f)) b.at(0f, y, 0.33f, scx = 0.035f, scy = 0.035f, scz = 0.03f).sphere(6, 4)
        b.color(0x7A4E2D)
        b.at(0.3f, 1.05f, 0f, roll = -1.0f, scx = 0.025f, scy = 0.45f, scz = 0.025f).cylinder(5, caps = false)
        b.at(-0.3f, 1.05f, 0f, roll = 1.0f, scx = 0.025f, scy = 0.45f, scz = 0.025f).cylinder(5, caps = false)
        b.color(0xFF4D8D).at(0f, 1.24f, 0f, scx = 0.3f, scy = 0.3f, scz = 0.3f).torus(1f, 0.2f, 16, 6)
        return b.build()
    }

    fun lantern(): Mesh {
        val b = MeshBuilder()
        b.color(0x3B3F55).at(0f, 0f, 0f, scx = 0.06f, scy = 1.55f, scz = 0.06f).cylinder(8)
        b.at(0f, 0f, 0f, scx = 0.16f, scy = 0.12f, scz = 0.16f).cylinder(8)
        b.color(1.65f, 1.32f, 0.72f).at(0f, 1.72f, 0f, scx = 0.24f, scy = 0.3f, scz = 0.24f).roundedBox(0.2f, 3)
        b.color(0x3B3F55).at(0f, 1.86f, 0f, scx = 0.22f, scy = 0.18f, scz = 0.22f).cone(4, flat = true)
        b.color(SNOW).at(0f, 1.95f, 0f, scx = 0.12f, scy = 0.07f, scz = 0.12f).sphere(8, 5)
        return b.build()
    }

    fun candyCane(): Mesh {
        val b = MeshBuilder()
        val segs = 9
        for (i in 0 until segs) {
            b.color(if (i % 2 == 0) 0xFF3B5C else WHITE).at(0f, i * 0.18f, 0f, yaw = i * 0.4f, scx = 0.13f, scy = 0.18f, scz = 0.13f).cylinder(10, caps = i == 0)
        }
        b.color(0xFF3B5C).at(0.26f, segs * 0.18f, 0f, pitch = -PI_F / 2, scx = 0.26f, scy = 0.26f, scz = 0.26f).torus(1f, 0.5f, 12, 8, arc = PI_F)
        b.color(SNOW).at(0f, 0f, 0f, scx = 0.35f, scy = 0.12f, scz = 0.35f).sphere(10, 6)
        return b.build()
    }

    fun crate(): Mesh {
        val b = MeshBuilder()
        b.color(0xB07A45).at(0f, 0.5f, 0f).roundedBox(0.08f, 2)
        b.color(0x7A5230)
        b.at(0f, 0.08f, 0f, scx = 1.04f, scy = 0.13f, scz = 1.04f).roundedBox(0.2f, 2)
        b.at(0f, 0.92f, 0f, scx = 1.04f, scy = 0.13f, scz = 1.04f).roundedBox(0.2f, 2)
        b.color(SNOW).at(0f, 1.02f, 0f, scx = 0.95f, scy = 0.14f, scz = 0.95f).roundedBox(0.4f, 3)
        return b.build()
    }

    fun barrel(): Mesh {
        val b = MeshBuilder()
        b.color(0x9A6238).at(0f, 0f, 0f, scx = 0.42f, scy = 1f, scz = 0.42f).cylinder(14, capColor = 0xC08A55)
        b.color(0x5B6578)
        for (y in listOf(0.18f, 0.82f)) b.at(0f, y, 0f, scx = 0.43f, scy = 0.2f, scz = 0.43f).torus(1f, 0.12f, 18, 5)
        b.color(SNOW).at(0f, 1.0f, 0f, scx = 0.4f, scy = 0.09f, scz = 0.4f).sphere(12, 6)
        return b.build()
    }

    fun cabin(): Mesh {
        val b = MeshBuilder()
        b.color(0x9C6B45).at(0f, 1f, 0f, scx = 3f, scy = 2f, scz = 2.6f).roundedBox(0.05f, 2)
        b.color(0x5C3A22).at(0f, 0.65f, 1.31f, scx = 0.7f, scy = 1.3f, scz = 0.06f).box()
        b.color(1.55f, 1.25f, 0.62f)
        for (x in listOf(-0.95f, 0.95f)) b.at(x, 1.2f, 1.31f, scx = 0.55f, scy = 0.5f, scz = 0.05f).box()
        b.color(0x6B4A30)
        b.at(-0.85f, 2.45f, 0f, roll = 0.62f, scx = 2.1f, scy = 0.22f, scz = 3.0f).box()
        b.at(0.85f, 2.45f, 0f, roll = -0.62f, scx = 2.1f, scy = 0.22f, scz = 3.0f).box()
        b.color(SNOW)
        b.at(-0.87f, 2.6f, 0f, roll = 0.62f, scx = 2.1f, scy = 0.2f, scz = 3.1f).roundedBox(0.3f, 3)
        b.at(0.87f, 2.6f, 0f, roll = -0.62f, scx = 2.1f, scy = 0.2f, scz = 3.1f).roundedBox(0.3f, 3)
        b.color(0x7C8496).at(0.9f, 3.1f, -0.5f, scx = 0.4f, scy = 0.9f, scz = 0.4f).box()
        b.color(SNOW).at(0.9f, 3.58f, -0.5f, scx = 0.46f, scy = 0.15f, scz = 0.46f).roundedBox(0.4f, 3)
        return b.build()
    }

    fun log(): Mesh = MeshBuilder().color(0x7A4E2D).at(0f, 0f, -0.5f, pitch = PI_F / 2).cylinder(14, capColor = 0xE0B880).build()

    /** Snow plow driving along X with blades on both ends. */
    fun plow(): Mesh {
        val b = MeshBuilder()
        b.color(0xFFC83D).at(0f, 0.72f, 0f, scx = 1.5f, scy = 0.8f, scz = 1.2f).roundedBox(0.15f, 3)
        b.color(0xFFB020).at(-0.2f, 1.38f, 0f, scx = 0.8f, scy = 0.62f, scz = 1.0f).roundedBox(0.2f, 3)
        b.color(0x2A3A6A)
        for (sz in listOf(-1f, 1f)) b.at(-0.2f, 1.42f, sz * 0.51f, scx = 0.6f, scy = 0.36f, scz = 0.04f).box()
        b.color(0x9AA6B8)
        for (sx in listOf(-1f, 1f)) b.at(sx * 1.0f, 0.48f, 0f, scx = 0.14f, scy = 0.7f, scz = 1.5f).roundedBox(0.3f, 2)
        b.color(0x26283A)
        for (sx in listOf(-0.5f, 0.5f)) for (sz in listOf(-1f, 1f)) {
            b.at(sx, 0.3f, sz * 0.5f, pitch = PI_F / 2, scx = 0.3f, scy = 0.2f, scz = 0.3f).cylinder(12)
        }
        b.color(1.6f, 0.75f, 0.3f).at(-0.2f, 1.78f, 0f, scx = 0.12f, scy = 0.12f, scz = 0.12f).sphere(8, 6)
        b.color(SNOW).at(-0.2f, 1.74f, 0f, scx = 0.5f, scy = 0.08f, scz = 0.6f).roundedBox(0.4f, 2)
        return b.build()
    }

    fun penguin(): Mesh {
        val b = MeshBuilder()
        b.color(0x26304F).at(0f, 0.38f, 0f, scx = 0.26f, scy = 0.38f, scz = 0.24f).sphere(14, 10)
        b.color(WHITE).at(0f, 0.34f, 0.12f, scx = 0.19f, scy = 0.28f, scz = 0.13f).sphere(12, 8)
        b.color(0xFFA733).at(0f, 0.55f, 0.22f, pitch = PI_F / 2, scx = 0.05f, scy = 0.12f, scz = 0.04f).cone(8)
        for (x in listOf(-0.08f, 0.08f)) {
            b.color(WHITE).at(x, 0.63f, 0.18f, scx = 0.05f, scy = 0.06f, scz = 0.03f).sphere(6, 4)
            b.color(0x111111).at(x, 0.63f, 0.205f, scx = 0.028f, scy = 0.035f, scz = 0.02f).sphere(6, 4)
        }
        b.color(0xFFA733)
        for (x in listOf(-0.1f, 0.1f)) b.at(x, 0.03f, 0.08f, scx = 0.08f, scy = 0.03f, scz = 0.1f).sphere(8, 4)
        b.color(0x26304F)
        for (x in listOf(-1f, 1f)) b.at(x * 0.25f, 0.36f, 0f, roll = x * 0.3f, scx = 0.05f, scy = 0.2f, scz = 0.12f).sphere(8, 6)
        return b.build()
    }

    fun bumper(): Mesh {
        val b = MeshBuilder()
        b.color(0xFFF1DC).at(0f, 0f, 0f, scx = 0.32f, scy = 0.55f, scz = 0.32f).cylinder(12)
        b.color(0xFF4D8D).at(0f, 0.55f, 0f, scx = 0.95f, scy = 0.55f, scz = 0.95f).sphere(18, 10)
        b.color(0xFFF1DC).at(0f, 0.55f, 0f, scx = 0.93f, scy = 0.1f, scz = 0.93f).cylinder(18, caps = true)
        b.color(WHITE)
        for (i in 0 until 7) {
            val a = i / 7f * TAU
            val r = if (i == 0) 0f else 0.55f
            val y = if (i == 0) 1.08f else 0.92f
            b.at(cos(a) * r, y, sin(a) * r, scx = 0.13f, scy = 0.07f, scz = 0.13f).sphere(8, 5)
        }
        return b.build()
    }

    fun steamVent(): Mesh {
        val b = MeshBuilder()
        b.color(0x6F7890).at(0f, 0.05f, 0f, scx = 0.9f, scy = 0.5f, scz = 0.9f).torus(1f, 0.3f, 14, 6)
        b.color(0x23404F).at(0f, 0.0f, 0f, scx = 0.85f, scy = 0.04f, scz = 0.85f).cylinder(14)
        b.color(0.4f, 0.75f, 0.85f).at(0f, 0.05f, 0f, scx = 0.6f, scy = 0.02f, scz = 0.6f).cylinder(14)
        return b.build()
    }

    fun iceFloe(): Mesh = MeshBuilder().color(0xBFE6F5).at(0f, -0.2f, 0f, scx = 1f, scy = 0.32f, scz = 1f).cylinder(6, capColor = 0xF6FCFF).build()

    fun mountain(): Mesh {
        val b = MeshBuilder()
        b.color(0x6F82A6).at(0f, 0f, 0f).cone(8, flat = true)
        b.color(0xF4F8FF).at(0f, 0.5f, 0f, yaw = 0.2f, scx = 0.52f, scy = 0.52f, scz = 0.52f).cone(8, flat = true, base = false)
        b.color(0x8496B8).at(0.45f, 0f, 0.2f, yaw = 1f, scx = 0.55f, scy = 0.6f, scz = 0.55f).cone(7, flat = true)
        b.color(0xF4F8FF).at(0.45f, 0.33f, 0.2f, yaw = 1.2f, scx = 0.24f, scy = 0.27f, scz = 0.24f).cone(7, flat = true, base = false)
        return b.build()
    }

    fun island(): Mesh = MeshBuilder().icosphere(2, noise = 0.12f, seed = 11, flat = true) { _, ny, _, _ ->
        if (ny > 0.25f) 0xF4F9FF else MeshBuilder.shade(0x7F93B5, 0.85f + ny * 0.3f)
    }.build()

    fun block(): Mesh = MeshBuilder().color(0xF2F8FF).at(0f, 0f, 0f, scx = 0.5f, scy = 0.36f, scz = 0.5f).roundedBox(0.2f, 3).build()

    fun step(): Mesh = MeshBuilder().color(WHITE).roundedBox(0.08f, 3).build()

    fun gift(): Mesh {
        val b = MeshBuilder()
        b.color(0xFF4D6D).at(0f, 0.25f, 0f, scx = 0.5f, scy = 0.5f, scz = 0.5f).roundedBox(0.12f, 2)
        b.color(0xFFC83D)
        b.at(0f, 0.25f, 0f, scx = 0.14f, scy = 0.53f, scz = 0.53f).box()
        b.at(0f, 0.25f, 0f, scx = 0.53f, scy = 0.53f, scz = 0.14f).box()
        b.at(-0.09f, 0.55f, 0f, roll = 0.6f, scx = 0.1f, scy = 0.12f, scz = 0.1f).torus(1f, 0.4f, 10, 5)
        b.at(0.09f, 0.55f, 0f, roll = -0.6f, scx = 0.1f, scy = 0.12f, scz = 0.1f).torus(1f, 0.4f, 10, 5)
        return b.build()
    }

    fun flagPole(): Mesh {
        val b = MeshBuilder()
        b.color(0xDDE6F2).at(0f, 0f, 0f, scx = 0.05f, scy = 2.4f, scz = 0.05f).cylinder(6)
        b.color(0xFFC83D).at(0f, 2.42f, 0f, scx = 0.08f, scy = 0.08f, scz = 0.08f).sphere(6, 4)
        b.color(WHITE).at(0.03f, 2.35f, 0f, scx = 0.9f, scy = 1.0f, scz = 1f).flagTri()
        return b.build()
    }

    fun tower(): Mesh {
        val b = MeshBuilder()
        b.color(0xEAF6FF).at(0f, 0f, 0f, scx = 0.55f, scy = 3.4f, scz = 0.55f).cylinder(14, capColor = 0xFFFFFF)
        b.color(0xB9D9F0).at(0f, 3.4f, 0f, scx = 0.62f, scy = 0.25f, scz = 0.62f).cylinder(14)
        b.color(0xFF4D8D).at(0f, 3.65f, 0f, scx = 0.7f, scy = 1.1f, scz = 0.7f).cone(12, flat = true)
        b.color(SNOW).at(0f, 3.6f, 0f, scx = 0.72f, scy = 0.12f, scz = 0.72f).torus(1f, 0.35f, 14, 5)
        b.color(1.4f, 1.25f, 0.6f).at(0f, 2.2f, 0.52f, scx = 0.22f, scy = 0.38f, scz = 0.06f).roundedBox(0.3f, 2)
        return b.build()
    }

    fun balloon(): Mesh {
        val b = MeshBuilder()
        b.color(WHITE).at(0f, 1.0f, 0f, scx = 0.32f, scy = 0.4f, scz = 0.32f).sphere(14, 10)
        b.color(0xDDE6F2).at(0f, 0f, 0f, scx = 0.01f, scy = 0.62f, scz = 0.01f).cylinder(4, caps = false)
        return b.build()
    }

    fun quad(): Mesh = MeshBuilder().color(WHITE).quadXZ().build()

    /** Icicle hanging down from y=0 (unit length, scaled per instance). */
    fun icicle(): Mesh = MeshBuilder().color(WHITE).at(0f, 0f, 0f, pitch = PI_F).cone(6, flat = true, base = true).build()

    /** Short snowy edge marker pole with a little pennant. */
    fun marker(): Mesh {
        val b = MeshBuilder()
        b.color(0xFFFFFF).at(0f, 0f, 0f, scx = 0.045f, scy = 1.15f, scz = 0.045f).cylinder(6)
        b.color(SNOW).at(0f, 1.17f, 0f, scx = 0.08f, scy = 0.07f, scz = 0.08f).sphere(8, 5)
        b.color(WHITE).at(0.02f, 1.1f, 0f, scx = 0.42f, scy = 0.5f, scz = 1f).flagTri()
        return b.build()
    }
}
