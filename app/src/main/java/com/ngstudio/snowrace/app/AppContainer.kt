package com.ngstudio.snowrace.app

import android.app.Application
import android.content.Context
import com.ngstudio.snowrace.ads.AdConfig
import com.ngstudio.snowrace.analytics.Telemetry
import com.ngstudio.snowrace.notify.Notifications
import com.ngstudio.snowrace.notify.Push
import com.ngstudio.snowrace.notify.Reminders
import com.ngstudio.snowrace.update.AppUpdater
import com.ngstudio.snowrace.ads.AdProvider
import com.ngstudio.snowrace.audio.AudioEngine
import com.ngstudio.snowrace.audio.Haptic
import com.ngstudio.snowrace.audio.Haptics
import com.ngstudio.snowrace.audio.Music
import com.ngstudio.snowrace.audio.Sfx
import com.ngstudio.snowrace.data.Catalog
import com.ngstudio.snowrace.data.SaveRepository
import com.ngstudio.snowrace.data.Settings
import com.ngstudio.snowrace.game.render.GameRenderer
import com.ngstudio.snowrace.game.render.GameSurfaceView
import com.ngstudio.snowrace.game.render.MenuMode
import com.ngstudio.snowrace.game.render.SceneSpec
import com.ngstudio.snowrace.game.RaceController
import com.ngstudio.snowrace.level.LevelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Manual dependency container living as long as the process. */
class AppContainer(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val save = SaveRepository(app)
    val audio = AudioEngine(app)
    val haptics = Haptics(app)
    val ads: AdProvider = AdConfig.create()
    val stage = Stage(this)
    val updater = AppUpdater(app)
    /** Home tab a tapped notification asked for (home, levels, shop, rewards, profile); the main screen consumes it. */
    val openTab = MutableStateFlow<String?>(null)
    @Volatile var levelSaltsLoaded = false

    init {
        applySettings(save.data.settings)
        CoroutineScope(Dispatchers.IO).launch {
            audio.loadAll()
            try {
                val bytes = app.assets.open("levels/salts.bin").use { it.readBytes() }
                LevelRepository.installSalts(bytes)
            } catch (_: Throwable) { }
            levelSaltsLoaded = true
        }
    }

    fun applySettings(s: Settings) {
        audio.sfxEnabled = s.sfx
        audio.sfxVolume = s.sfxVolume
        audio.setMusicEnabled(s.music)
        audio.setMusicVolume(s.musicVolume)
        haptics.enabled = s.haptics
    }

    /** "Share usage & crash data" toggle. */
    fun setShareData(on: Boolean) {
        save.update { it.copy(settings = it.settings.copy(shareData = on)) }
        Telemetry.setConsent(on)
    }

    /** Notifications toggle (local reminders + push). The system permission is handled by the caller. */
    fun setNotifications(on: Boolean) {
        save.update { it.copy(settings = it.settings.copy(notifications = on)) }
        if (!on) Reminders.cancel(app)
        Push.setEnabled(on, changed = true)
    }

    fun tap() { audio.play(Sfx.UI_TAP); haptics.play(Haptic.TICK) }
    fun back() { audio.play(Sfx.UI_BACK); haptics.play(Haptic.TICK) }
}

/** Text shown on the loading cover while the 3D scene is swapped behind it. */
data class LoadingCover(val title: String = "", val subtitle: String = "")

/** Controls the persistent 3D layer behind the Compose UI. */
class Stage(private val c: AppContainer) {
    var surface: GameSurfaceView? = null
        private set
    var renderer: GameRenderer? = null
        private set
    private var rendering = true
    private var music: Music? = null

    private val _cover = MutableStateFlow<LoadingCover?>(null)
    /** Non-null while the loading cover should be up. */
    val cover: StateFlow<LoadingCover?> = _cover.asStateFlow()
    private var coverSeq = 0
    /** True while the 3D layer shows (or is building) a race. */
    var raceShown = false
        private set

    /**
     * Swaps the 3D scene behind an opaque cover: the cover fades in first, then [swap] queues the GL build
     * (which blocks the GL thread for a moment), and the cover lifts once the new scene has drawn its first frame.
     * The player never sees a frozen frame or the old scene popping into the new one.
     */
    suspend fun swapScene(cover: LoadingCover = LoadingCover(), swap: () -> Unit) {
        val token = ++coverSeq
        try {
            _cover.value = cover
            delay(COVER_FADE_IN_MS + 30L)
            swap()
            renderer?.let { r -> withTimeoutOrNull(6000L) { r.building.first { it == 0 } } }
            delay(90L) // let the first frames of the new scene reach the screen
        } finally {
            if (coverSeq == token) _cover.value = null
        }
    }

    fun attach(s: GameSurfaceView, r: GameRenderer) { surface = s; renderer = r }
    fun detach() { surface = null; renderer = null }

    fun showMenu(mode: MenuMode) {
        val d = c.save.data
        val level = LevelRepository.get(d.progress.highestUnlocked)
        val spec = SceneSpec.Menu(
            Catalog.character(d.profile.character), Catalog.ball(d.profile.ball), Catalog.trail(d.profile.trail),
            mode, level.biome, level.time,
        )
        raceShown = false
        renderer?.updateMenu(spec)
        setRendering(true)
    }

    /** Preview a different look (shop) without equipping it. */
    fun preview(characterId: String?, ballId: String?, trailId: String?, mode: MenuMode = MenuMode.SHOP) {
        val d = c.save.data
        val level = LevelRepository.get(d.progress.highestUnlocked)
        renderer?.updateMenu(
            SceneSpec.Menu(
                Catalog.character(characterId ?: d.profile.character), Catalog.ball(ballId ?: d.profile.ball), Catalog.trail(trailId ?: d.profile.trail),
                mode, level.biome, level.time,
            )
        )
        setRendering(true)
    }

    fun showRace(controller: RaceController) {
        raceShown = true
        renderer?.show(SceneSpec.Race(controller))
        setRendering(true)
    }

    fun setRendering(on: Boolean) {
        if (rendering == on) return
        rendering = on
        surface?.renderMode = if (on) android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY else android.opengl.GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    fun drag(dYaw: Float) = renderer?.menuDrag(dYaw)
    fun wave() = renderer?.menuWave()

    fun playMusic(m: Music?) {
        if (music == m) return
        music = m
        c.audio.playMusic(m)
    }

    fun currentMusic() = music

    companion object {
        const val COVER_FADE_IN_MS = 170
        const val COVER_FADE_OUT_MS = 380
    }
}

class App : Application() {
    /** Built on first use, so a background start (a reminder or push in a fresh process) doesn't load the game. */
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        val s = SaveRepository.peek(this)?.settings ?: Settings()
        Telemetry.init(this, s.shareData)
        Notifications.createChannels(this)
        Push.setEnabled(s.notifications, changed = false)
        // debug builds print the push token at start (logcat tag "Push") for Firebase's "Send test message"
        if (com.ngstudio.snowrace.BuildConfig.DEBUG && s.notifications) Push.logToken()
    }
}

val Context.container: AppContainer get() = (applicationContext as App).container
