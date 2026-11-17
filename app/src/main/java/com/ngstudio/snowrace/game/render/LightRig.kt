package com.ngstudio.snowrace.game.render

import com.ngstudio.snowrace.level.Biome
import com.ngstudio.snowrace.level.TimeOfDay
import com.ngstudio.snowrace.level.Weather
import kotlin.math.sqrt

/** All lighting / atmosphere constants for one scene, derived from biome + time of day. */
class LightRig {
    val sunDir = floatArrayOf(-0.45f, 0.8f, -0.4f)
    val sunColor = floatArrayOf(0.36f, 0.27f, 0.12f)
    val ambSky = floatArrayOf(0.7f, 0.79f, 0.96f)
    val ambGround = floatArrayOf(0.6f, 0.63f, 0.72f)
    val rim = floatArrayOf(0.55f, 0.7f, 0.95f)
    val fog = floatArrayOf(0.85f, 0.92f, 1f)
    var fogStart = 45f
    var fogEnd = 170f
    val skyTop = floatArrayOf(0.24f, 0.55f, 0.94f)
    val skyHorizon = floatArrayOf(0.75f, 0.9f, 1f)
    var night = 0f
    val snow = floatArrayOf(1f, 1f, 1f)
    val snowPacked = floatArrayOf(0.8f, 0.87f, 0.97f)
    val waterDeep = floatArrayOf(0.06f, 0.43f, 0.69f)
    val waterShallow = floatArrayOf(0.2f, 0.77f, 0.9f)
    val skyRefl = floatArrayOf(0.75f, 0.9f, 1f)
    val outline = floatArrayOf(0.11f, 0.16f, 0.29f)
    val cliff = floatArrayOf(0.5f, 0.64f, 0.79f)
    var saturation = 1.12f
    var contrast = 1.05f
    var brightness = 1.0f
    var vignette = 0.38f
    val gradeTint = floatArrayOf(1f, 1f, 1f)
    var sparkle = 1f
    var lanternGlow = 0f

    companion object {
        fun rgb(c: Int, out: FloatArray) {
            out[0] = ((c shr 16) and 0xFF) / 255f; out[1] = ((c shr 8) and 0xFF) / 255f; out[2] = (c and 0xFF) / 255f
        }

        private fun set(out: FloatArray, r: Float, g: Float, b: Float) { out[0] = r; out[1] = g; out[2] = b }
        private fun mix(out: FloatArray, a: FloatArray, b: FloatArray, t: Float) {
            for (i in 0..2) out[i] = a[i] + (b[i] - a[i]) * t
        }
    }

    fun configure(biome: Biome, time: TimeOfDay, weather: Weather) {
        val bTop = FloatArray(3).also { rgb(biome.skyTop, it) }
        val bHor = FloatArray(3).also { rgb(biome.skyHorizon, it) }
        val bFog = FloatArray(3).also { rgb(biome.fog, it) }
        rgb(biome.snow, snow)
        rgb(biome.snowShadow, snowPacked)
        // packed snow is lighter than the shadow color
        for (i in 0..2) snowPacked[i] = snowPacked[i] * 0.45f + 0.55f * snow[i] * 0.92f
        rgb(biome.waterDeep, waterDeep)
        rgb(biome.waterShallow, waterShallow)
        rgb(biome.cliff, cliff)
        night = 0f
        lanternGlow = 0f
        when (time) {
            TimeOfDay.DAY -> {
                setDir(-0.45f, 0.8f, -0.42f)
                set(sunColor, 0.36f, 0.27f, 0.12f)
                set(ambSky, 0.70f, 0.79f, 0.97f)
                set(ambGround, 0.60f, 0.63f, 0.73f)
                set(rim, 0.55f, 0.72f, 1.0f)
                for (i in 0..2) { skyTop[i] = bTop[i]; skyHorizon[i] = bHor[i]; fog[i] = bFog[i] }
                set(gradeTint, 1f, 1f, 1f)
                saturation = 1.12f; contrast = 1.05f; brightness = 1.0f
            }
            TimeOfDay.SUNSET -> {
                setDir(-0.75f, 0.42f, -0.25f)
                set(sunColor, 0.52f, 0.30f, 0.12f)
                set(ambSky, 0.62f, 0.58f, 0.82f)
                set(ambGround, 0.56f, 0.48f, 0.6f)
                set(rim, 1.0f, 0.62f, 0.5f)
                mix(skyTop, bTop, floatArrayOf(0.33f, 0.32f, 0.72f), 0.65f)
                mix(skyHorizon, bHor, floatArrayOf(1.0f, 0.68f, 0.52f), 0.7f)
                mix(fog, bFog, floatArrayOf(0.97f, 0.76f, 0.72f), 0.65f)
                set(gradeTint, 1.04f, 0.98f, 0.96f)
                saturation = 1.15f; contrast = 1.06f; brightness = 1.0f
                lanternGlow = 0.5f
            }
            TimeOfDay.NIGHT -> {
                setDir(-0.35f, 0.75f, 0.2f)
                set(sunColor, 0.26f, 0.31f, 0.42f)
                set(ambSky, 0.42f, 0.5f, 0.8f)
                set(ambGround, 0.32f, 0.36f, 0.55f)
                set(rim, 0.45f, 0.95f, 0.85f)
                mix(skyTop, bTop, floatArrayOf(0.05f, 0.07f, 0.2f), 0.85f)
                mix(skyHorizon, bHor, floatArrayOf(0.16f, 0.26f, 0.5f), 0.8f)
                mix(fog, bFog, floatArrayOf(0.17f, 0.23f, 0.43f), 0.82f)
                for (i in 0..2) { waterDeep[i] *= 0.55f; waterShallow[i] *= 0.65f }
                set(gradeTint, 0.96f, 1.0f, 1.06f)
                saturation = 1.1f; contrast = 1.08f; brightness = 1.05f
                night = 1f
                lanternGlow = 1f
            }
        }
        for (i in 0..2) skyRefl[i] = skyHorizon[i] * 0.7f + skyTop[i] * 0.3f
        when (weather) {
            Weather.CLEAR -> { fogStart = 55f; fogEnd = 190f }
            Weather.SNOW -> { fogStart = 40f; fogEnd = 160f }
            Weather.BLIZZARD -> { fogStart = 18f; fogEnd = 95f; saturation -= 0.08f }
        }
        sparkle = if (time == TimeOfDay.NIGHT) 0.6f else 1f
        // outline: deep navy, slightly tinted by sky
        set(outline, 0.1f + skyTop[0] * 0.05f, 0.14f + skyTop[1] * 0.05f, 0.27f + skyTop[2] * 0.05f)
    }

    private fun setDir(x: Float, y: Float, z: Float) {
        val l = sqrt(x * x + y * y + z * z)
        sunDir[0] = x / l; sunDir[1] = y / l; sunDir[2] = z / l
    }
}
