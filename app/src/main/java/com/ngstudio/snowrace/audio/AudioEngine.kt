package com.ngstudio.snowrace.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import java.io.DataInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Base volumes balance perceived loudness (the WAVs are peak-normalized). */
enum class Sfx(val res: String, val minIntervalMs: Int = 0, val baseVolume: Float = 1f) {
    UI_TAP("sfx_ui_tap", 40, 0.8f),
    UI_BACK("sfx_ui_back", 40, 0.75f),
    UI_OPEN("sfx_ui_open", 60, 0.65f),
    UI_CLOSE("sfx_ui_close", 60, 0.65f),
    COIN("sfx_coin", 45, 0.7f),
    COIN_TICK("sfx_coin_tick", 28, 0.6f),
    STAR1("sfx_star1", 0, 0.9f), STAR2("sfx_star2", 0, 0.9f), STAR3("sfx_star3", 0, 0.85f),
    UNLOCK("sfx_unlock", 0, 0.85f),
    CHEST("sfx_chest_open", 0, 0.85f),
    WHEEL_TICK("sfx_wheel_tick", 25, 0.85f),
    WHEEL_WIN("sfx_wheel_win", 0, 0.85f),
    COUNTDOWN("sfx_countdown", 0, 0.45f),
    GO("sfx_go", 0, 0.7f),
    STEP1("sfx_step_snow1", 60, 0.75f),
    STEP2("sfx_step_snow2", 60, 0.75f),
    ROLL_LOOP("sfx_roll_loop"),
    BRIDGE_BLOCK("sfx_bridge_block", 45, 0.8f),
    SPLASH("sfx_splash", 80, 1f),
    BONK("sfx_bonk", 60, 1f),
    BOOST("sfx_boost", 0, 0.85f),
    SHIELD("sfx_shield", 0, 0.8f),
    POWERUP("sfx_powerup", 0, 0.65f),
    GIFT("sfx_gift", 0, 0.85f),
    AVALANCHE_LOOP("sfx_avalanche_loop", 0, 0.7f),
    CRUMBLE("sfx_crumble", 150, 0.55f),
    WIN("sfx_win_fanfare", 0, 0.85f),
    LOSE("sfx_lose", 0, 0.75f),
    ERROR("sfx_error", 120, 0.45f),
    WHOOSH("sfx_whoosh", 60, 0.75f),
    CHEER("sfx_cheer", 0, 0.85f),
    POP("sfx_pop", 30, 0.8f),
    ICE_CRACK("sfx_ice_crack", 120, 1f),
    FINALE_STEP("sfx_finale_step", 40, 0.9f),
    BONK_HEAVY("sfx_bonk_heavy", 150, 1f),
    BRIDGE_DONE("sfx_bridge_done", 300, 0.8f),
    FINALE_MAX("sfx_finale_max", 0, 0.9f),
}

enum class Music(val res: String) { MENU("music_menu"), RACE("music_race"), BOSS("music_boss") }

/**
 * Game audio: low-latency SFX via SoundPool, gapless looping music with crossfades via a
 * small software streamer on AudioTrack.
 */
class AudioEngine(private val context: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(14)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()

    private val ids = ConcurrentHashMap<Sfx, Int>()
    private val loaded = ConcurrentHashMap.newKeySet<Int>()
    private val lastPlayed = ConcurrentHashMap<Sfx, Long>()
    @Volatile var sfxEnabled = true
    @Volatile var sfxVolume = 0.9f
    @Volatile private var loadedCount = 0
    val totalSfx = Sfx.entries.size
    val loadProgress: Float get() = if (totalSfx == 0) 1f else loadedCount.toFloat() / totalSfx

    private val music = MusicStreamer(context)

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded.add(sampleId)
            loadedCount++
        }
    }

    @SuppressLint("DiscouragedApi")
    fun loadAll() {
        for (s in Sfx.entries) {
            val resId = context.resources.getIdentifier(s.res, "raw", context.packageName)
            if (resId != 0) {
                ids[s] = pool.load(context, resId, 1)
            } else {
                loadedCount++
                Log.w("AudioEngine", "Missing sound ${s.res}")
            }
        }
    }

    // SoundPool calls can block for several ms (internal locks, AudioTrack rate changes), which showed up as
    // frame hitches when they ran on the GL thread. Every pool call now goes to one ordered worker thread.
    private val sfxThread = Executors.newSingleThreadExecutor { r -> Thread(r, "Sfx").apply { isDaemon = true } }
    /** Loop handle -> SoundPool stream id (filled in by the worker once the loop has started). */
    private val loopStreams = ConcurrentHashMap<Int, Int>()
    /** Loop handle -> last volume / rate sent (touched only by the caller thread). */
    private val loopSent = HashMap<Int, FloatArray>()
    private val nextLoop = AtomicInteger(1)

    /** Plays a one-shot without blocking the caller. */
    fun play(s: Sfx, volume: Float = 1f, pitch: Float = 1f, pan: Float = 0f) {
        if (!sfxEnabled) return
        val id = ids[s] ?: return
        if (id !in loaded) return
        if (s.minIntervalMs > 0) {
            val now = SystemClock.uptimeMillis()
            val last = lastPlayed[s] ?: 0L
            if (now - last < s.minIntervalMs) return
            lastPlayed[s] = now
        }
        val v = (volume * s.baseVolume * sfxVolume).coerceIn(0f, 1f)
        val l = v * min(1f, 1f - pan)
        val r = v * min(1f, 1f + pan)
        val rate = pitch.coerceIn(0.5f, 2f)
        sfxThread.execute { pool.play(id, l, r, 1, 0, rate) }
    }

    /** Starts a looping sound; returns a handle for [setLoop] / [stopLoop] (0 if the sound is not loaded yet). */
    fun startLoop(s: Sfx, volume: Float = 0f, pitch: Float = 1f): Int {
        val id = ids[s] ?: return 0
        if (id !in loaded) return 0
        val v = if (sfxEnabled) volume * s.baseVolume * sfxVolume else 0f
        val rate = pitch.coerceIn(0.5f, 2f)
        val h = nextLoop.getAndIncrement()
        synchronized(loopSent) { loopSent[h] = floatArrayOf(v, rate) }
        sfxThread.execute { loopStreams[h] = pool.play(id, v, v, 2, -1, rate) }
        return h
    }

    /** Sends volume / rate only when they change audibly (it used to be two SoundPool calls every frame). */
    fun setLoop(handle: Int, volume: Float, pitch: Float) {
        if (handle == 0) return
        val v = if (sfxEnabled) (volume * sfxVolume).coerceIn(0f, 1f) else 0f
        val rate = pitch.coerceIn(0.5f, 2f)
        val sent = synchronized(loopSent) { loopSent[handle] } ?: return
        val sendVol = abs(sent[0] - v) >= 0.02f || (v == 0f && sent[0] != 0f)
        val sendRate = abs(sent[1] - rate) >= 0.015f
        if (!sendVol && !sendRate) return
        if (sendVol) sent[0] = v
        if (sendRate) sent[1] = rate
        sfxThread.execute {
            val sid = loopStreams[handle] ?: return@execute
            if (sendVol) pool.setVolume(sid, v, v)
            if (sendRate) pool.setRate(sid, rate)
        }
    }

    fun stopLoop(handle: Int) {
        if (handle == 0) return
        synchronized(loopSent) { loopSent.remove(handle) }
        sfxThread.execute { loopStreams.remove(handle)?.let { pool.stop(it) } }
    }

    fun stopAllLoops() { sfxThread.execute { pool.autoPause(); pool.autoResume() } }

    // ---- Music -----------------------------------------------------------------------------
    fun playMusic(m: Music?) = music.play(m)
    fun setMusicEnabled(enabled: Boolean) = music.setEnabled(enabled)
    fun setMusicVolume(v: Float) { music.volume = v }
    fun duckMusic(amount: Float, ms: Long) = music.duck(amount, ms)

    fun onPause() { sfxThread.execute { pool.autoPause() }; music.pause() }
    fun onResume() { sfxThread.execute { pool.autoResume() }; music.resume() }
}

/** Streams 16-bit mono PCM WAV loops with seamless looping and crossfades. */
private class MusicStreamer(private val context: Context) {
    private val sampleRate = 22050
    private val cache = HashMap<Music, ShortArray>()

    @Volatile var volume = 0.65f
    @Volatile private var enabled = true
    @Volatile private var paused = false
    @Volatile private var target: Music? = null
    @Volatile private var duckUntil = 0L
    @Volatile private var duckAmount = 1f

    private var thread: Thread? = null
    private val lock = Object()

    fun play(m: Music?) {
        target = m
        ensureThread()
    }

    fun setEnabled(e: Boolean) { enabled = e; ensureThread() }
    fun pause() { paused = true }
    fun resume() { paused = false; synchronized(lock) { lock.notifyAll() }; ensureThread() }
    fun duck(amount: Float, ms: Long) { duckAmount = amount; duckUntil = SystemClock.uptimeMillis() + ms }

    @SuppressLint("DiscouragedApi")
    private fun load(m: Music): ShortArray? {
        cache[m]?.let { return it }
        val resId = context.resources.getIdentifier(m.res, "raw", context.packageName)
        if (resId == 0) return null
        return try {
            context.resources.openRawResource(resId).use { raw ->
                val input = DataInputStream(raw.buffered())
                val header = ByteArray(12)
                input.readFully(header)
                var data: ShortArray? = null
                while (data == null) {
                    val idBytes = ByteArray(4)
                    input.readFully(idBytes)
                    val size = Integer.reverseBytes(input.readInt())
                    val id = String(idBytes)
                    if (id == "data") {
                        val bytes = ByteArray(size)
                        input.readFully(bytes)
                        val out = ShortArray(size / 2)
                        for (i in out.indices) {
                            out[i] = ((bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 0xFF)).toShort()
                        }
                        data = out
                    } else {
                        input.skipBytes(size + (size and 1))
                    }
                }
                cache[m] = data
                data
            }
        } catch (t: Throwable) {
            Log.e("Music", "Failed to load ${m.res}", t)
            null
        }
    }

    private fun ensureThread() {
        synchronized(lock) {
            if (thread?.isAlive == true) { lock.notifyAll(); return }
            thread = Thread({ runLoop() }, "MusicStreamer").also {
                it.priority = Thread.MAX_PRIORITY - 1
                it.start()
            }
        }
    }

    private fun runLoop() {
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(max(minBuf * 2, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()

        val chunk = 1024
        val out = ShortArray(chunk)
        var current: Music? = null
        var curData: ShortArray? = null
        var curPos = 0
        var prevData: ShortArray? = null
        var prevPos = 0
        var fade = 1f // 0..1 crossfade progress toward current
        val fadeStep = chunk.toFloat() / (sampleRate * 1.2f)
        var gainSmooth = 0f
        var idleChunks = 0

        try {
            while (true) {
                if (paused || !enabled || (target == null && current == null && prevData == null)) {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
                    if (!enabled || target == null) {
                        // fade state reset when fully stopped
                        if (!enabled) { current = null; curData = null; prevData = null }
                    }
                    synchronized(lock) { lock.wait(250) }
                    idleChunks++
                    if (idleChunks > 120 && !enabled) break // exit after ~30s idle while disabled
                    continue
                }
                idleChunks = 0
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()

                val want = target
                if (want != current) {
                    prevData = curData
                    prevPos = curPos
                    current = want
                    curData = want?.let { load(it) }
                    curPos = 0
                    fade = 0f
                }

                val now = SystemClock.uptimeMillis()
                val duck = if (now < duckUntil) duckAmount else 1f
                val targetGain = volume * duck
                for (i in 0 until chunk) {
                    gainSmooth += (targetGain - gainSmooth) * 0.0015f
                    var s = 0f
                    val cd = curData
                    if (cd != null && cd.isNotEmpty()) {
                        s += cd[curPos] * fade
                        curPos++; if (curPos >= cd.size) curPos = 0
                    }
                    val pd = prevData
                    if (pd != null && pd.isNotEmpty() && fade < 1f) {
                        s += pd[prevPos] * (1f - fade)
                        prevPos++; if (prevPos >= pd.size) prevPos = 0
                    }
                    val v = (s * gainSmooth).toInt()
                    out[i] = v.coerceIn(-32768, 32767).toShort()
                }
                if (fade < 1f) {
                    fade = min(1f, fade + fadeStep)
                    if (fade >= 1f) prevData = null
                }
                if (curData == null && prevData == null) {
                    current = null
                }
                track.write(out, 0, chunk)
            }
        } catch (_: InterruptedException) {
        } finally {
            try { track.stop() } catch (_: Throwable) {}
            track.release()
            synchronized(lock) { thread = null }
        }
    }
}
