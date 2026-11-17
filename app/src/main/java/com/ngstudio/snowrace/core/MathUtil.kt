package com.ngstudio.snowrace.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

const val PI_F = PI.toFloat()
const val TAU = (PI * 2.0).toFloat()
const val DEG2RAD = (PI / 180.0).toFloat()

fun clamp(v: Float, lo: Float, hi: Float): Float = if (v < lo) lo else if (v > hi) hi else v
fun clamp(v: Int, lo: Int, hi: Int): Int = if (v < lo) lo else if (v > hi) hi else v
fun clamp01(v: Float): Float = clamp(v, 0f, 1f)
fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
fun invLerp(a: Float, b: Float, v: Float): Float = if (b == a) 0f else (v - a) / (b - a)
fun remap(v: Float, a0: Float, a1: Float, b0: Float, b1: Float): Float = lerp(b0, b1, clamp01(invLerp(a0, a1, v)))

fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = clamp01((x - e0) / (e1 - e0))
    return t * t * (3f - 2f * t)
}

/** Frame-rate independent exponential smoothing. */
fun damp(current: Float, target: Float, lambda: Float, dt: Float): Float =
    lerp(current, target, 1f - exp(-lambda * dt))

fun wrapAngle(a: Float): Float {
    var r = a
    while (r > PI_F) r -= TAU
    while (r < -PI_F) r += TAU
    return r
}

fun angleDiff(from: Float, to: Float): Float = wrapAngle(to - from)

fun dampAngle(current: Float, target: Float, lambda: Float, dt: Float): Float =
    current + angleDiff(current, target) * (1f - exp(-lambda * dt))

fun moveTowards(current: Float, target: Float, maxDelta: Float): Float =
    if (abs(target - current) <= maxDelta) target else current + kotlin.math.sign(target - current) * maxDelta

fun length2(x: Float, z: Float): Float = sqrt(x * x + z * z)
fun headingOf(x: Float, z: Float): Float = atan2(x, z)

/** Integer hash (deterministic, platform independent). */
fun hashInt(x0: Int): Int {
    var x = x0
    x = ((x ushr 16) xor x) * 0x45d9f3b
    x = ((x ushr 16) xor x) * 0x45d9f3b
    x = (x ushr 16) xor x
    return x
}

fun hash2(a: Int, b: Int): Int = hashInt(a * 73856093 xor hashInt(b * 19349663))
fun hashFloat(a: Int, b: Int = 0): Float = (hash2(a, b) and 0xFFFFFF) / 16777215f

object Easing {
    fun outCubic(t: Float): Float { val p = 1f - t; return 1f - p * p * p }
    fun inCubic(t: Float): Float = t * t * t
    fun inOutSine(t: Float): Float = -(cos(PI_F * t) - 1f) / 2f
    fun outQuad(t: Float): Float = 1f - (1f - t) * (1f - t)
    fun outBack(t: Float, s: Float = 1.70158f): Float {
        val p = t - 1f
        return p * p * ((s + 1f) * p + s) + 1f
    }
    fun outElastic(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        val c4 = TAU / 3f
        return 2f.pow(-10f * t) * sin((t * 10f - 0.75f) * c4) + 1f
    }
    fun outBounce(t0: Float): Float {
        var t = t0
        val n1 = 7.5625f
        val d1 = 2.75f
        return when {
            t < 1f / d1 -> n1 * t * t
            t < 2f / d1 -> { t -= 1.5f / d1; n1 * t * t + 0.75f }
            t < 2.5f / d1 -> { t -= 2.25f / d1; n1 * t * t + 0.9375f }
            else -> { t -= 2.625f / d1; n1 * t * t + 0.984375f }
        }
    }
}
