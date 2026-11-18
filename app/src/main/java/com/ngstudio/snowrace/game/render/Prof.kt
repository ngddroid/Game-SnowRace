package com.ngstudio.snowrace.game.render

import android.os.SystemClock
import android.util.Log
import com.ngstudio.snowrace.BuildConfig

/** Debug-only section timer: logs the worst time per section once a second (tag FrostyProf). */
object Prof {
    private val names = arrayOf("sim", "events", "fx", "land", "cam", "fill", "shadow", "main", "bb")
    const val SIM = 0; const val EVENTS = 1; const val FX = 2; const val LAND = 3; const val CAM = 4
    const val FILL = 5; const val SHADOW = 6; const val MAIN = 7; const val BB = 8
    private val worst = LongArray(names.size)
    private var clock = 0L
    private var t0 = 0L

    fun start() { if (BuildConfig.DEBUG) t0 = SystemClock.elapsedRealtimeNanos() }

    fun end(slot: Int) {
        if (!BuildConfig.DEBUG) return
        val d = SystemClock.elapsedRealtimeNanos() - t0
        if (d > worst[slot]) worst[slot] = d
        t0 = SystemClock.elapsedRealtimeNanos()
    }

    fun tick() {
        if (!BuildConfig.DEBUG) return
        val now = SystemClock.elapsedRealtimeNanos()
        if (now - clock < 1_000_000_000L) return
        clock = now
        val sb = StringBuilder()
        for (i in names.indices) { sb.append(names[i]).append('=').append(worst[i] / 100_000 / 10f).append(' '); worst[i] = 0 }
        Log.i("FrostyProf", sb.toString())
    }
}
