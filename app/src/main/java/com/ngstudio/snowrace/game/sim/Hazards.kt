package com.ngstudio.snowrace.game.sim

import com.ngstudio.snowrace.core.TAU
import com.ngstudio.snowrace.core.smoothstep
import com.ngstudio.snowrace.level.HazardSpec
import com.ngstudio.snowrace.level.HazardType
import com.ngstudio.snowrace.level.PickupType
import com.ngstudio.snowrace.level.PropType
import kotlin.math.abs
import kotlin.math.sin

/** Static circular prop that blocks racers and balls. World coordinates. */
class Collider(val type: PropType, val x: Float, val z: Float, val r: Float, val y: Float, val rot: Float, val field: Int)

class Pickup(val type: PickupType, val x: Float, val z: Float, val y: Float, val field: Int) {
    var active = true
    var respawnAt = 0f
    var spin = 0f
}

/**
 * Runtime hazard. All coordinates are world-space. Motion is a pure function of time,
 * so the renderer and validator always agree on where things are.
 */
class Hazard(val spec: HazardSpec, val cx: Float, val cz: Float, val halfW: Float, val y: Float, val field: Int) {
    val type: HazardType = spec.type
    /** Current center x for moving hazards. */
    var x = cx
    var prevX = cx
    var vx = 0f
    /** 0..1 activation (wind / steam). */
    var active = 0f
    /** Steam warning puff 0..1. */
    var warn = 0f
    // thin ice
    var crack = 0f
    var brokenUntil = -1f
    val broken: Boolean get() = brokenTimer > 0f
    var brokenTimer = 0f
    var occupied = false
    // penguins
    val penguinX = FloatArray(spec.count.coerceAtLeast(1))
    var penguinDir = if (spec.speed >= 0f) 1f else -1f
    var rollAngle = 0f

    /** Sweep amplitude for logs / plows. */
    private val amp: Float = (halfW - spec.w * 0.5f - 0.6f).coerceAtLeast(0.5f)

    fun update(t: Float, dt: Float) {
        prevX = x
        when (type) {
            HazardType.LOG, HazardType.PLOW -> {
                // Ping-pong with eased turnarounds so the motion is readable.
                val travel = (2f * amp).coerceAtLeast(0.1f)
                val period = travel * 2f / abs(spec.speed).coerceAtLeast(0.3f)
                var u = ((t / period) + spec.phase) % 1f
                if (u < 0f) u += 1f
                val p = if (u < 0.5f) u * 2f else 2f - u * 2f
                val e = smoothstep(0f, 1f, p)
                x = cx - amp + 2f * amp * e
                vx = if (dt > 0f) (x - prevX) / dt else 0f
                rollAngle += vx * dt / 0.45f
            }
            HazardType.WIND -> {
                val u = ((t + spec.phase * spec.period) % spec.period) / spec.period
                active = smoothstep(0.0f, 0.1f, u) * (1f - smoothstep(0.5f, 0.6f, u))
            }
            HazardType.STEAM -> {
                val u = ((t + spec.phase * spec.period) % spec.period)
                warn = if (u < 0.9f) smoothstep(0f, 0.9f, u) else 0f
                active = if (u >= 0.9f && u < 2.4f) 1f - smoothstep(2.1f, 2.4f, u) else 0f
            }
            HazardType.PENGUINS -> {
                val span = halfW * 2f + 4f
                val spd = abs(spec.speed).coerceAtLeast(0.4f)
                for (i in penguinX.indices) {
                    var p = (t * spd + i * 0.95f + spec.phase * span) % span
                    if (p < 0) p += span
                    penguinX[i] = if (penguinDir > 0) -halfW - 2f + p else halfW + 2f - p
                }
            }
            HazardType.THIN_ICE -> {
                if (brokenTimer > 0f) {
                    brokenTimer -= dt
                    if (brokenTimer <= 0f) { crack = 0f }
                } else {
                    if (occupied) crack += dt / 1.1f else crack = (crack - dt * 0.25f).coerceAtLeast(0f)
                    if (crack >= 1f) { brokenTimer = 4.5f; crack = 1f }
                }
                occupied = false
            }
            else -> {}
        }
    }

    fun insideRect(px: Float, pz: Float, pad: Float = 0f): Boolean =
        abs(px - x) <= spec.w * 0.5f + pad && abs(pz - cz) <= spec.d * 0.5f + pad

    fun windDirX(): Float = if (spec.speed >= 0f) 1f else -1f

    fun steamWobble(t: Float): Float = sin(t * TAU * 3f) * 0.5f + 0.5f
}
