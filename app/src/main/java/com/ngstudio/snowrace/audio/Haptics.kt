package com.ngstudio.snowrace.audio

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class Haptic { TICK, CLICK, HEAVY, SUCCESS, BONK, SPLASH, ERROR }

class Haptics(context: Context) {
    @Volatile var enabled = true
    private var last = 0L

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val hasAmplitude = vibrator?.hasAmplitudeControl() == true

    fun play(h: Haptic) {
        val v = vibrator ?: return
        if (!enabled || !v.hasVibrator()) return
        val now = SystemClock.uptimeMillis()
        if (h == Haptic.TICK && now - last < 35) return
        last = now
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val effect = when (h) {
                    Haptic.TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    Haptic.CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    Haptic.HEAVY -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    Haptic.SUCCESS -> wave(longArrayOf(0, 30, 60, 45), intArrayOf(0, 120, 0, 200))
                    Haptic.BONK -> wave(longArrayOf(0, 18, 30, 40), intArrayOf(0, 255, 0, 140))
                    Haptic.SPLASH -> wave(longArrayOf(0, 60, 40, 30), intArrayOf(0, 90, 0, 50))
                    Haptic.ERROR -> wave(longArrayOf(0, 40, 50, 40), intArrayOf(0, 150, 0, 150))
                }
                v.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= 26) {
                val ms = when (h) { Haptic.TICK -> 8L; Haptic.CLICK -> 14L; Haptic.HEAVY, Haptic.BONK -> 32L; else -> 24L }
                v.vibrate(VibrationEffect.createOneShot(ms, if (hasAmplitude) 160 else VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(if (h == Haptic.TICK) 8L else 20L)
            }
        } catch (_: Throwable) {
        }
    }

    private fun wave(timings: LongArray, amps: IntArray): VibrationEffect =
        if (hasAmplitude) VibrationEffect.createWaveform(timings, amps, -1)
        else VibrationEffect.createWaveform(timings, -1)
}
