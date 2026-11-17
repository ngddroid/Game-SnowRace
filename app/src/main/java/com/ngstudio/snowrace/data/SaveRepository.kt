package com.ngstudio.snowrace.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Single JSON save file written atomically. All game state lives in one immutable [SaveData]
 * snapshot exposed as a StateFlow, so Compose screens recompose automatically.
 */
class SaveRepository(context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
    private val file = AtomicFile(File(context.filesDir, "save.json"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null
    private val lock = Any()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SaveData> = _state.asStateFlow()
    val data: SaveData get() = _state.value

    private fun load(): SaveData = try {
        if (file.baseFile.exists()) {
            val text = file.readFully().decodeToString()
            json.decodeFromString(SaveData.serializer(), text)
        } else {
            SaveData(profile = Profile(createdEpochDay = TimeUtil.today()))
        }
    } catch (t: Throwable) {
        Log.e("SaveRepository", "Save load failed, starting fresh", t)
        SaveData(profile = Profile(createdEpochDay = TimeUtil.today()))
    }

    fun update(block: (SaveData) -> SaveData) {
        _state.update(block)
        scheduleSave()
    }

    private fun scheduleSave() {
        pending?.cancel()
        pending = scope.launch {
            delay(350)
            writeNow()
        }
    }

    fun flush() {
        pending?.cancel()
        scope.launch { writeNow() }
    }

    private fun writeNow() {
        synchronized(lock) {
            val text = json.encodeToString(SaveData.serializer(), _state.value)
            var out: java.io.FileOutputStream? = null
            try {
                out = file.startWrite()
                out.write(text.encodeToByteArray())
                file.finishWrite(out)
            } catch (t: Throwable) {
                if (out != null) file.failWrite(out)
                Log.e("SaveRepository", "Save failed", t)
            }
        }
    }

    fun resetAll() {
        _state.value = SaveData(profile = Profile(createdEpochDay = TimeUtil.today(), onboardingDone = true, name = data.profile.name))
        scheduleSave()
    }
}

object TimeUtil {
    fun today(): Long {
        val tz = java.util.TimeZone.getDefault()
        val now = System.currentTimeMillis()
        return (now + tz.getOffset(now)) / 86_400_000L
    }

    fun msUntilTomorrow(): Long {
        val tz = java.util.TimeZone.getDefault()
        val now = System.currentTimeMillis()
        val local = now + tz.getOffset(now)
        return 86_400_000L - (local % 86_400_000L)
    }

    fun formatCountdown(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%dh %02dm".format(h, m) else "%02d:%02d".format(m, sec)
    }
}
