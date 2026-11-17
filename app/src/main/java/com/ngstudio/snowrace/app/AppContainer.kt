package com.ngstudio.snowrace.app

import android.app.Application
import android.content.Context
import com.ngstudio.snowrace.ads.AdConfig
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
import kotlinx.coroutines.launch

/** Manual dependency container living as long as the process. */
class AppContainer(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val save = SaveRepository(app)
    val audio = AudioEngine(app)
    val haptics = Haptics(app)
    val ads: AdProvider = AdConfig.create()
    val stage = Stage(this)
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

    fun tap() { audio.play(Sfx.UI_TAP); haptics.play(Haptic.TICK) }
    fun back() { audio.play(Sfx.UI_BACK); haptics.play(Haptic.TICK) }
}

/** Controls the persistent 3D layer behind the Compose UI. */
class Stage(private val c: AppContainer) {
    var surface: GameSurfaceView? = null
        private set
    var renderer: GameRenderer? = null
        private set
    private var rendering = true
    private var music: Music? = null

    fun attach(s: GameSurfaceView, r: GameRenderer) { surface = s; renderer = r }
    fun detach() { surface = null; renderer = null }

    fun showMenu(mode: MenuMode) {
        val d = c.save.data
        val level = LevelRepository.get(d.progress.highestUnlocked)
        val spec = SceneSpec.Menu(
            Catalog.character(d.profile.character), Catalog.ball(d.profile.ball), Catalog.trail(d.profile.trail),
            mode, level.biome, level.time,
        )
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
}

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as App).container
