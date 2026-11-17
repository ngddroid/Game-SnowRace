package com.ngstudio.snowrace.game.render

import com.ngstudio.snowrace.core.Mat4
import com.ngstudio.snowrace.core.PI_F
import com.ngstudio.snowrace.core.Quat
import com.ngstudio.snowrace.data.CharacterDef
import com.ngstudio.snowrace.data.Ears
import com.ngstudio.snowrace.data.Extra
import com.ngstudio.snowrace.data.Hat
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

enum class PartMesh { SPHERE, CONE, CYL, TORUS, BOX }

enum class Role { STATIC, EYE, PUPIL, SHINE, MOUTH, SCARF1, SCARF2, CAPE, TAIL, POMPOM, HEAD }

class PartT(
    val mesh: PartMesh,
    val role: Role,
    val px: Float, val py: Float, val pz: Float,
    val yaw: Float, val pitch: Float, val roll: Float,
    val sx: Float, val sy: Float, val sz: Float,
    val color: Int,
    val emissive: Float = 0f,
    val outline: Float = 1f,
    val gloss: Float = 0.12f,
    val side: Float = 0f,
)

/** Animation inputs for one character instance. */
class Pose {
    var x = 0f; var y = 0f; var z = 0f; var yaw = 0f
    var move = 0f
    var phase = 0f
    var pushing = false
    var ballR = 0f
    var lean = 0f
    var roll = 0f
    var squash = 0f
    var jump = 0f
    var armsUp = 0f
    var wave = 0f
    var blink = 1f
    var mouth = 0f
    var scale = 1f
    var time = 0f
    var lookX = 0f
    var outline = 1f
}

class CharBatches(
    val sphere: InstanceBatch,
    val cone: InstanceBatch,
    val cyl: InstanceBatch,
    val torus: InstanceBatch,
    val box: InstanceBatch,
) {
    private val list = listOf(sphere, cone, cyl, torus, box)
    fun all(): List<InstanceBatch> = list
    fun clear() { sphere.clear(); cone.clear(); cyl.clear(); torus.clear(); box.clear() }
    fun of(m: PartMesh) = when (m) {
        PartMesh.SPHERE -> sphere; PartMesh.CONE -> cone; PartMesh.CYL -> cyl; PartMesh.TORUS -> torus; PartMesh.BOX -> box
    }
}

/**
 * "Snowpal" characters: chibi creatures with a big expressive head, a puffy winter jacket,
 * real arms and legs (sleeves, mittens, trousers, boots), a scarf and a hat. Built from
 * primitives and animated procedurally every frame: run cycle with opposite arm swing,
 * pushing pose that reaches the snowball, blinking, celebrations and secondary motion.
 */
class CharacterRig(private val batches: CharBatches) {
    private val cache = HashMap<String, List<PartT>>()
    private val root = FloatArray(16)
    private val local = FloatArray(16)
    private val out = FloatArray(16)
    private val q = FloatArray(4)

    companion object {
        const val HEAD_Y = 1.0f
        private const val SKIN_GLOSS = 0.16f

        fun shade(c: Int, f: Float): Int = MeshBuilder.shade(c, f)

        /** Kid characters get a skin tone and hair colour (a diverse cast); animals keep fur faces. */
        private val kidLooks: Map<String, Pair<Int, Int>> = mapOf(
            "pip" to (0xFFDCC0 to 0x6B3E1F),
            "bubbles" to (0x8D5524 to 0x1B1B1B),
            "mochi" to (0xF1C27D to 0xE8B04A),
            "minty" to (0xC68642 to 0x2B1A10),
            "sunny" to (0xFFDBAC to 0xB5532A),
            "grape" to (0xE0AC69 to 0x3A2414),
            "hopper" to (0xF1C27D to 0x2B1A10),
            "shade" to (0xC68642 to 0x111111),
            "nova" to (0xFFDCC0 to 0x6B3E1F),
            "floe" to (0x8D5524 to 0x1B1B1B),
            "pudding" to (0xE0AC69 to 0xE8B04A),
            "frostking" to (0xF1C27D to 0xD9D9D9),
        )

        fun kidLook(d: CharacterDef): Pair<Int, Int>? = kidLooks[d.id]
    }

    fun parts(def: CharacterDef): List<PartT> = cache.getOrPut(def.id) { build(def) }

    private fun build(d: CharacterDef): List<PartT> {
        val p = ArrayList<PartT>()
        fun s(role: Role, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, color: Int, em: Float = 0f, ol: Float = 1f, gloss: Float = 0.12f, side: Float = 0f) =
            p.add(PartT(PartMesh.SPHERE, role, x, y, z, 0f, 0f, 0f, sx, sy, sz, color, em, ol, gloss, side))
        fun part(mesh: PartMesh, role: Role, x: Float, y: Float, z: Float, yaw: Float, pitch: Float, roll: Float, sx: Float, sy: Float, sz: Float, color: Int, em: Float = 0f, ol: Float = 1f, gloss: Float = 0.12f) =
            p.add(PartT(mesh, role, x, y, z, yaw, pitch, roll, sx, sy, sz, color, em, ol, gloss))

        val kid = kidLook(d)
        val skin = kid?.first ?: d.body
        val isAnimalFace = kid == null && (Extra.BEAK in d.extras || d.ears != Ears.NONE) && d.belly != d.body

        // ---- torso: puffy jacket + belly panel + hips
        s(Role.STATIC, 0f, 0.6f, 0f, 0.215f, 0.235f, 0.185f, d.body, gloss = 0.2f)
        s(Role.STATIC, 0f, 0.575f, 0.105f, 0.145f, 0.155f, 0.095f, d.belly, ol = 0f, gloss = 0.2f)
        s(Role.STATIC, 0f, 0.41f, 0f, 0.18f, 0.11f, 0.155f, pants(d), gloss = 0.1f)
        if (Extra.FLUFF in d.extras) {
            for ((x, z) in listOf(0.2f to 0.06f, -0.2f to 0.06f, 0.16f to -0.14f, -0.16f to -0.14f, 0f to -0.19f)) s(Role.STATIC, x, 0.5f, z, 0.12f, 0.11f, 0.12f, d.belly, ol = 0.6f)
        }

        // ---- head
        s(Role.HEAD, 0f, HEAD_Y, 0.02f, 0.29f, 0.27f, 0.27f, skin, gloss = SKIN_GLOSS)
        if (kid != null) {
            // hair: a cap sitting slightly behind the face shows at the sides, back and top
            s(Role.HEAD, 0f, HEAD_Y + 0.05f, -0.045f, 0.295f, 0.25f, 0.26f, kid.second, gloss = 0.3f)
            // soft tufts at the nape and sides so it reads as hair, not a helmet
            for ((tx, ty, tz) in listOf(Triple(-0.17f, HEAD_Y - 0.06f, -0.13f), Triple(0f, HEAD_Y - 0.1f, -0.2f), Triple(0.17f, HEAD_Y - 0.06f, -0.13f), Triple(-0.25f, HEAD_Y + 0.02f, 0.02f), Triple(0.25f, HEAD_Y + 0.02f, 0.02f))) {
                s(Role.HEAD, tx, ty, tz, 0.085f, 0.1f, 0.08f, shade(kid.second, 1.12f), ol = 0f, gloss = 0.3f)
            }
        }
        if (isAnimalFace) s(Role.HEAD, 0f, HEAD_Y - 0.07f, 0.15f, 0.19f, 0.15f, 0.13f, d.belly, ol = 0f, gloss = SKIN_GLOSS)
        if (Extra.PANDA_PATCHES in d.extras) {
            for (x in listOf(-0.105f, 0.105f)) s(Role.HEAD, x, HEAD_Y - 0.015f, 0.222f, 0.1f, 0.115f, 0.055f, d.extraColor, ol = 0f)
        }
        for (sd in listOf(-1f, 1f)) {
            s(Role.EYE, sd * 0.105f, HEAD_Y - 0.01f, 0.236f, 0.07f, 0.084f, 0.05f, 0xFFFFFF, ol = 0f, side = sd)
            s(Role.PUPIL, sd * 0.105f, HEAD_Y - 0.015f, 0.27f, 0.048f, 0.062f, 0.028f, 0x1B2A4A, ol = 0f, gloss = 0.6f, side = sd)
            s(Role.SHINE, sd * 0.105f + 0.022f, HEAD_Y + 0.013f, 0.292f, 0.018f, 0.018f, 0.01f, 0xFFFFFF, em = 1f, ol = 0f, side = sd)
            s(Role.HEAD, sd * 0.18f, HEAD_Y - 0.075f, 0.2f, 0.055f, 0.034f, 0.03f, 0xFF8FA8, em = 0.35f, ol = 0f)
        }
        if (Extra.EYE_PATCH in d.extras) {
            s(Role.HEAD, 0.105f, HEAD_Y - 0.01f, 0.28f, 0.095f, 0.105f, 0.03f, d.extraColor, ol = 0f)
            part(PartMesh.TORUS, Role.HEAD, 0f, HEAD_Y + 0.08f, 0f, 0f, 0f, 0.45f, 0.3f, 0.12f, 0.29f, d.extraColor, ol = 0f)
        }
        if (Extra.BEAK !in d.extras && Extra.CARROT_NOSE !in d.extras) s(Role.HEAD, 0f, HEAD_Y - 0.05f, 0.27f, 0.03f, 0.024f, 0.02f, shade(skin, 0.86f), ol = 0f)
        s(Role.MOUTH, 0f, HEAD_Y - 0.1f, 0.25f, 0.045f, 0.024f, 0.02f, 0x6B1E3A, ol = 0f)

        // ---- scarf
        part(PartMesh.TORUS, Role.STATIC, 0f, 0.8f, 0.0f, 0f, 0f, 0f, 0.205f, 0.3f, 0.195f, d.scarf, gloss = 0.05f)
        part(PartMesh.BOX, Role.SCARF1, 0.1f, 0.72f, -0.18f, 0f, 0f, 0f, 0.11f, 0.19f, 0.05f, d.scarf, ol = 0.8f)
        part(PartMesh.BOX, Role.SCARF2, 0.11f, 0.55f, -0.22f, 0f, 0f, 0f, 0.1f, 0.15f, 0.045f, d.scarfStripe, ol = 0.8f)

        // ---- ears
        val earY = HEAD_Y + 0.2f
        when (d.ears) {
            Ears.ROUND -> for (sd in listOf(-1f, 1f)) {
                s(Role.STATIC, sd * 0.21f, earY, -0.02f, 0.085f, 0.085f, 0.065f, d.earColor)
                s(Role.STATIC, sd * 0.21f, earY, 0.025f, 0.05f, 0.05f, 0.025f, 0xFFB3C6, ol = 0f)
            }
            Ears.POINTY -> for (sd in listOf(-1f, 1f)) {
                part(PartMesh.CONE, Role.STATIC, sd * 0.17f, earY - 0.05f, -0.02f, 0f, 0f, -sd * 0.38f, 0.085f, 0.22f, 0.06f, d.earColor)
                part(PartMesh.CONE, Role.STATIC, sd * 0.17f, earY - 0.03f, 0.01f, 0f, 0f, -sd * 0.38f, 0.048f, 0.14f, 0.035f, 0xFFF1E6, ol = 0f)
            }
            Ears.TUFT -> for (sd in listOf(-1f, 1f)) part(PartMesh.CONE, Role.STATIC, sd * 0.2f, earY - 0.03f, -0.02f, 0f, 0f, -sd * 0.62f, 0.065f, 0.17f, 0.045f, d.earColor)
            Ears.NONE -> {}
        }

        // ---- hats (head top is at HEAD_Y + 0.27)
        val top = HEAD_Y + 0.27f
        when (d.hat) {
            Hat.BEANIE -> {
                s(Role.STATIC, 0f, HEAD_Y + 0.16f, -0.015f, 0.285f, 0.205f, 0.28f, d.hatColor, gloss = 0.05f)
                part(PartMesh.TORUS, Role.STATIC, 0f, HEAD_Y + 0.13f, -0.005f, 0f, 0f, 0f, 0.27f, 0.22f, 0.265f, d.hatTrim, gloss = 0.05f)
                s(Role.POMPOM, 0f, HEAD_Y + 0.4f, -0.03f, 0.085f, 0.085f, 0.085f, d.pompom, gloss = 0.05f)
            }
            Hat.TOPHAT -> {
                part(PartMesh.CYL, Role.STATIC, 0f, top - 0.06f, 0f, 0f, 0f, 0f, 0.3f, 0.035f, 0.3f, d.hatColor, gloss = 0.4f)
                part(PartMesh.CYL, Role.STATIC, 0f, top - 0.04f, 0f, 0f, 0f, 0f, 0.19f, 0.32f, 0.19f, d.hatColor, gloss = 0.4f)
                part(PartMesh.CYL, Role.STATIC, 0f, top - 0.01f, 0f, 0f, 0f, 0f, 0.195f, 0.07f, 0.195f, d.pompom, ol = 0f)
            }
            Hat.CROWN -> {
                part(PartMesh.CYL, Role.STATIC, 0f, top - 0.07f, 0f, 0f, 0f, 0f, 0.21f, 0.13f, 0.21f, d.hatColor, gloss = 1.2f)
                for (i in 0 until 5) {
                    val a = i / 5f * 2f * PI_F
                    part(PartMesh.CONE, Role.STATIC, cos(a) * 0.18f, top + 0.05f, sin(a) * 0.18f, 0f, 0f, 0f, 0.05f, 0.12f, 0.05f, d.hatColor, gloss = 1.2f, ol = 0.6f)
                }
                s(Role.STATIC, 0f, top, 0.21f, 0.042f, 0.042f, 0.03f, d.pompom, em = 0.5f, ol = 0f, gloss = 1f)
            }
            Hat.CHEF -> {
                part(PartMesh.CYL, Role.STATIC, 0f, top - 0.1f, 0f, 0f, 0f, 0f, 0.23f, 0.16f, 0.23f, d.hatColor)
                s(Role.STATIC, 0f, top + 0.15f, 0f, 0.28f, 0.19f, 0.28f, d.hatColor)
                s(Role.STATIC, 0.14f, top + 0.12f, 0.05f, 0.16f, 0.14f, 0.16f, d.hatColor)
                s(Role.STATIC, -0.14f, top + 0.12f, -0.03f, 0.16f, 0.14f, 0.16f, d.hatColor)
            }
            Hat.PIRATE -> {
                s(Role.STATIC, 0f, top - 0.03f, 0f, 0.37f, 0.11f, 0.24f, d.hatColor, gloss = 0.1f)
                s(Role.STATIC, 0f, top + 0.04f, 0f, 0.23f, 0.11f, 0.19f, d.hatColor, gloss = 0.1f)
                s(Role.STATIC, 0f, top + 0.01f, 0.2f, 0.045f, 0.045f, 0.02f, d.pompom, em = 0.3f, ol = 0f)
            }
            Hat.HELMET -> {
                s(Role.STATIC, 0f, HEAD_Y + 0.16f, -0.02f, 0.32f, 0.23f, 0.31f, 0xFFFFFF, gloss = 0.8f)
                part(PartMesh.TORUS, Role.STATIC, 0f, HEAD_Y + 0.13f, -0.01f, 0f, 0f, 0f, 0.28f, 0.24f, 0.275f, d.hatColor, gloss = 0.6f)
                part(PartMesh.CYL, Role.STATIC, 0.17f, top + 0.03f, -0.05f, 0f, 0f, -0.3f, 0.015f, 0.16f, 0.015f, 0x8193A8, ol = 0f)
                s(Role.STATIC, 0.22f, top + 0.18f, -0.05f, 0.04f, 0.04f, 0.04f, 0xFF4D8D, em = 0.8f, ol = 0f)
            }
            Hat.HEADBAND -> {
                part(PartMesh.TORUS, Role.STATIC, 0f, HEAD_Y + 0.13f, 0f, 0f, 0f, 0f, 0.265f, 0.2f, 0.255f, d.hatColor)
                part(PartMesh.BOX, Role.SCARF1, 0.06f, HEAD_Y + 0.08f, -0.28f, 0f, 0f, 0.4f, 0.06f, 0.2f, 0.03f, d.hatColor, ol = 0.6f)
                part(PartMesh.BOX, Role.SCARF2, -0.04f, HEAD_Y + 0.06f, -0.28f, 0f, 0f, -0.3f, 0.06f, 0.18f, 0.03f, d.hatColor, ol = 0.6f)
            }
            Hat.EARMUFFS -> {
                part(PartMesh.TORUS, Role.STATIC, 0f, HEAD_Y + 0.05f, 0f, 0f, PI_F / 2, PI_F / 2, 0.31f, 0.2f, 0.31f, d.hatTrim)
                for (sd in listOf(-1f, 1f)) s(Role.STATIC, sd * 0.3f, HEAD_Y + 0.02f, 0f, 0.09f, 0.11f, 0.1f, d.hatColor)
            }
            Hat.NONE -> {}
        }

        // ---- extras
        for (e in d.extras) when (e) {
            Extra.BEAK -> part(PartMesh.CONE, Role.STATIC, 0f, HEAD_Y - 0.04f, 0.26f, 0f, PI_F / 2, 0f, 0.065f, 0.14f, 0.05f, d.extraColor)
            Extra.CARROT_NOSE -> part(PartMesh.CONE, Role.STATIC, 0f, HEAD_Y - 0.01f, 0.26f, 0f, PI_F / 2, 0f, 0.045f, 0.2f, 0.045f, d.extraColor)
            Extra.ANTLERS -> for (sd in listOf(-1f, 1f)) {
                part(PartMesh.CYL, Role.STATIC, sd * 0.13f, HEAD_Y + 0.18f, -0.03f, 0f, 0f, -sd * 0.42f, 0.028f, 0.28f, 0.028f, d.extraColor)
                part(PartMesh.CYL, Role.STATIC, sd * 0.22f, HEAD_Y + 0.32f, -0.03f, 0f, 0f, -sd * 1.15f, 0.024f, 0.14f, 0.024f, d.extraColor)
                part(PartMesh.CYL, Role.STATIC, sd * 0.23f, HEAD_Y + 0.36f, -0.03f, 0f, 0f, sd * 0.1f, 0.02f, 0.13f, 0.02f, d.extraColor)
            }
            Extra.HORNS -> for (sd in listOf(-1f, 1f)) part(PartMesh.CONE, Role.STATIC, sd * 0.18f, HEAD_Y + 0.17f, 0f, 0f, 0f, -sd * 0.55f, 0.065f, 0.23f, 0.065f, d.extraColor, gloss = 0.4f)
            Extra.UNICORN_HORN -> part(PartMesh.CONE, Role.STATIC, 0f, HEAD_Y + 0.22f, 0.12f, 0f, 0.45f, 0f, 0.055f, 0.3f, 0.055f, 0xFFE27A, em = 0.2f, gloss = 1f)
            Extra.ANTENNA -> {
                part(PartMesh.CYL, Role.STATIC, 0f, HEAD_Y + 0.25f, 0f, 0f, 0f, 0f, 0.015f, 0.24f, 0.015f, 0x8193A8, ol = 0f)
                s(Role.POMPOM, 0f, HEAD_Y + 0.5f, 0f, 0.05f, 0.05f, 0.05f, d.extraColor, em = 0.8f, ol = 0.5f)
            }
            Extra.TAIL -> {
                p.add(PartT(PartMesh.SPHERE, Role.TAIL, 0f, 0.42f, -0.22f, 0f, -0.6f, 0f, 0.11f, 0.11f, 0.2f, d.body))
                p.add(PartT(PartMesh.SPHERE, Role.TAIL, 0f, 0.55f, -0.36f, 0f, -0.6f, 0f, 0.08f, 0.08f, 0.1f, d.extraColor))
            }
            Extra.CAPE -> part(PartMesh.BOX, Role.CAPE, 0f, 0.6f, -0.2f, 0f, -0.12f, 0f, 0.42f, 0.5f, 0.04f, d.extraColor, ol = 0.8f)
            Extra.MANE -> {
                s(Role.STATIC, 0f, HEAD_Y + 0.2f, -0.17f, 0.11f, 0.11f, 0.11f, d.extraColor)
                s(Role.STATIC, 0.02f, HEAD_Y + 0.08f, -0.25f, 0.11f, 0.11f, 0.1f, 0xB46CFF)
                s(Role.STATIC, -0.01f, HEAD_Y - 0.05f, -0.26f, 0.1f, 0.1f, 0.09f, d.extraColor)
                s(Role.STATIC, 0.06f, HEAD_Y + 0.25f, 0.05f, 0.08f, 0.07f, 0.08f, 0xB46CFF)
            }
            Extra.WHISKERS, Extra.FLUFF, Extra.PANDA_PATCHES, Extra.EYE_PATCH -> {}
        }
        return p
    }

    private fun pants(d: CharacterDef): Int = if (Extra.PANDA_PATCHES in d.extras) d.extraColor else shade(d.body, 0.78f)
    private fun sleeves(d: CharacterDef): Int = if (Extra.PANDA_PATCHES in d.extras) d.extraColor else d.body

    /** Emits all parts of a character into the instance batches. */
    fun draw(def: CharacterDef, pose: Pose) {
        val sc = pose.scale
        val sq = pose.squash
        val move = pose.move
        val ph = pose.phase
        val bob = abs(sin(ph)) * 0.045f * move
        Quat.euler(q, pose.yaw, pose.lean, pose.roll)
        Mat4.trs(root, 0, pose.x, pose.y + pose.jump + bob, pose.z, q[0], q[1], q[2], q[3], sc * (1f - sq * 0.45f), sc * (1f + sq), sc * (1f - sq * 0.45f))
        val t = pose.time

        // ---------------- static + secondary-motion parts
        for (pt in parts(def)) {
            var x = pt.px; var y = pt.py; var z = pt.pz
            var yaw = pt.yaw; var pitch = pt.pitch; var roll = pt.roll
            var sx = pt.sx; var sy = pt.sy; var sz = pt.sz
            when (pt.role) {
                Role.EYE -> { sy *= pose.blink }
                Role.PUPIL -> { sy *= pose.blink; x += pose.lookX * 0.022f }
                Role.SHINE -> { if (pose.blink < 0.4f) { sx = 0f; sy = 0f; sz = 0f }; x += pose.lookX * 0.022f }
                Role.MOUTH -> { sy *= 1f + pose.mouth * 1.6f; sx *= 1f + pose.mouth * 0.35f }
                Role.SCARF1 -> { pitch = -(0.15f + move * 0.85f) + sin(t * 9f) * 0.1f * move; z -= move * 0.08f; y += move * 0.05f }
                Role.SCARF2 -> { pitch = -(0.2f + move * 1.1f) + sin(t * 9f + 0.8f) * 0.14f * move; z -= move * 0.22f; y += move * 0.14f }
                Role.CAPE -> { pitch = -0.12f - move * 0.6f + sin(t * 7f) * 0.06f * move; z -= move * 0.1f; y += move * 0.06f }
                Role.TAIL -> { yaw = sin(t * 8f) * 0.35f * (0.35f + move) }
                Role.POMPOM -> { y += abs(sin(ph + 0.6f)) * 0.025f * move; z -= move * 0.03f }
                Role.HEAD, Role.STATIC -> {}
            }
            if (sx == 0f) continue
            emit(pt.mesh, x, y, z, yaw, pitch, roll, sx, sy, sz, pt.color, pt.emissive, pt.outline * pose.outline, pt.gloss)
        }

        // ---------------- legs (trousers + boots)
        val pantsColor = pants(def)
        for (sd in floatArrayOf(-1f, 1f)) {
            val lp = ph + if (sd < 0f) 0f else PI_F
            val ax = sd * 0.1f
            val az = 0.03f + sin(lp) * 0.17f * move
            val ay = 0.1f + max(0f, cos(lp)) * 0.08f * move
            limb(sd * 0.09f, 0.39f, 0f, ax, ay, az, 0.072f, pantsColor, pose.outline)
            emit(PartMesh.SPHERE, ax, ay - 0.03f, az + 0.04f, 0f, sin(lp) * 0.3f * move, 0f, 0.085f, 0.068f, 0.13f, def.boots, 0f, pose.outline, 0.3f)
        }

        // ---------------- arms (sleeves + mittens)
        val sleeveColor = sleeves(def)
        val r = pose.ballR
        var handY = 0.6f
        var handZ = 0.3f
        if (pose.pushing && r > 0f) {
            handY = (r * 1.0f + 0.12f).coerceIn(0.3f, 0.62f)
            val cz = 0.34f + r * 0.92f
            val dy = r - handY
            val half = sqrt(max(0f, r * r - dy * dy))
            handZ = (cz - half - 0.03f).coerceIn(0.2f, 0.9f)
        }
        for (sd in floatArrayOf(-1f, 1f)) {
            val armPhase = ph + if (sd < 0f) PI_F else 0f
            var hx = sd * 0.29f
            var hy = 0.43f + abs(cos(ph)) * 0.02f * move
            var hz = 0.03f + sin(armPhase) * 0.16f * move
            if (pose.armsUp > 0.01f) {
                val k = pose.armsUp
                val wv = if (sd > 0f) pose.wave else pose.wave * 0.4f
                hx = lerp(hx, sd * 0.34f + sin(t * 13f + sd) * 0.06f * wv, k)
                hy = lerp(hy, 1.25f + abs(sin(t * 9f + sd)) * 0.05f * wv, k)
                hz = lerp(hz, 0.08f, k)
            } else if (pose.pushing) {
                hx = sd * 0.17f; hy = handY; hz = handZ
            } else if (pose.wave > 0.01f && sd > 0f) {
                hx = lerp(hx, 0.34f, pose.wave); hy = lerp(hy, 1.18f + sin(t * 14f) * 0.07f, pose.wave); hz = lerp(hz, 0.14f, pose.wave)
            }
            limb(sd * 0.19f, 0.74f, 0f, hx, hy, hz, 0.064f, sleeveColor, pose.outline)
            emit(PartMesh.SPHERE, hx, hy, hz, 0f, 0f, 0f, 0.082f, 0.082f, 0.082f, def.mitten, 0f, pose.outline, 0.12f)
        }
    }

    /** A soft capsule-like limb (stretched sphere) between two local-space points. */
    private fun limb(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, thick: Float, color: Int, outline: Float) {
        var dx = bx - ax; var dy = by - ay; var dz = bz - az
        val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-4f)
        dx /= len; dy /= len; dz /= len
        // quaternion rotating +Y onto (dx,dy,dz)
        var qx = dz; var qy = 0f; var qz = -dx; var qw = 1f + dy
        if (qw < 1e-4f) { qx = 1f; qy = 0f; qz = 0f; qw = 0f }
        val l = sqrt(qx * qx + qy * qy + qz * qz + qw * qw)
        qx /= l; qy /= l; qz /= l; qw /= l
        val batch = batches.sphere
        Mat4.trs(local, 0, (ax + bx) * 0.5f, (ay + by) * 0.5f, (az + bz) * 0.5f, qx, qy, qz, qw, thick, len * 0.5f + thick * 0.55f, thick)
        Mat4.mul(out, root, local)
        val o = batch.next()
        System.arraycopy(out, 0, batch.data, o, 16)
        batch.setStyle(o, color, outline = outline, gloss = 0.18f)
    }

    private fun emit(mesh: PartMesh, x: Float, y: Float, z: Float, yaw: Float, pitch: Float, roll: Float, sx: Float, sy: Float, sz: Float, color: Int, emissive: Float, outline: Float, gloss: Float) {
        val batch = batches.of(mesh)
        Quat.euler(q, yaw, pitch, roll)
        Mat4.trs(local, 0, x, y, z, q[0], q[1], q[2], q[3], sx, sy, sz)
        Mat4.mul(out, root, local)
        val o = batch.next()
        System.arraycopy(out, 0, batch.data, o, 16)
        batch.setStyle(o, color, emissive = emissive, outline = outline, gloss = gloss)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
