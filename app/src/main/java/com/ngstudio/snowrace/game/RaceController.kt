package com.ngstudio.snowrace.game

import com.ngstudio.snowrace.game.sim.Phase
import com.ngstudio.snowrace.game.sim.PlayerSetup
import com.ngstudio.snowrace.game.sim.RaceSim
import com.ngstudio.snowrace.game.sim.SegKind
import com.ngstudio.snowrace.level.LevelSpec
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

data class RacerDot(val progress: Float, val color: Int, val isPlayer: Boolean, val finished: Boolean)

data class HudState(
    val phase: Phase = Phase.INTRO,
    val stage: Int = 1,
    val stages: Int = 1,
    val rank: Int = 1,
    val racers: Int = 1,
    val dots: List<RacerDot> = emptyList(),
    val bridgeRatio: Float = 0f,
    val bridgeReady: Boolean = false,
    val showBridgeMeter: Boolean = false,
    val snow: Int = 0,
    val boost: Float = 0f,
    val shield: Float = 0f,
    val magnet: Float = 0f,
    val finaleStep: Int = 0,
    val multiplier: Float = 1f,
    val giftCoins: Int = 0,
)

enum class PopupKind { GOOD, BAD, COIN, INFO, BIG }

sealed class UiEvent {
    data class Countdown(val n: Int) : UiEvent()
    data object Go : UiEvent()
    data class Popup(val text: String, val x: Float, val y: Float, val kind: PopupKind) : UiEvent()
    data class Banner(val text: String, val sub: String = "") : UiEvent()
    data class FinaleStep(val step: Int, val mult: Float) : UiEvent()
    data object RaceLost : UiEvent()
    data class Finished(val result: RaceResult) : UiEvent()
}

data class RaceResult(
    val level: Int,
    val rank: Int,
    val racers: Int,
    val cleared: Boolean,
    val stars: Int,
    val baseCoins: Int,
    val multiplier: Float,
    val giftCoins: Int,
    val totalCoins: Int,
    val bonks: Int,
    val blocks: Int,
    val meters: Float,
    val pickups: Int,
    val seconds: Int,
    val boss: Boolean,
)

/**
 * Owns one race: the simulation, the player's joystick input, HUD snapshots and the result.
 * [step] runs on the GL thread; HUD/events are published as flows for Compose.
 */
class RaceController(val spec: LevelSpec, val setup: PlayerSetup) {
    val sim = RaceSim(spec, setup)

    @Volatile var paused = false
    @Volatile var touchActive = false
    /** Joystick vector in screen space: x right, y up, magnitude 0..1 (already sensitivity scaled). */
    @Volatile var stickX = 0f
    @Volatile var stickY = 0f

    private var lastDirX = 0f
    private var lastDirZ = 1f

    private val _hud = MutableStateFlow(HudState(stages = spec.stages.size, racers = spec.rivals.size + 1))
    val hud: StateFlow<HudState> = _hud.asStateFlow()
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    @Volatile var result: RaceResult? = null
        private set
    private var hudTimer = 0f

    fun emit(e: UiEvent) { _events.tryEmit(e) }

    /** Converts the screen-space stick into a world direction using the camera's ground basis. */
    fun applyInput(rightX: Float, rightZ: Float, fwdX: Float, fwdZ: Float) {
        val p = sim.player
        if (p.brain != null) return // autopilot
        if (!touchActive) { p.inMag = 0f; return }
        val sx = stickX; val sy = stickY
        val len = sqrt(sx * sx + sy * sy)
        if (len < 0.08f) {
            // holding without dragging keeps going the way we were heading
            p.inX = lastDirX; p.inZ = lastDirZ; p.inMag = 1f
            return
        }
        val wx = rightX * sx + fwdX * sy
        val wz = rightZ * sx + fwdZ * sy
        val wl = sqrt(wx * wx + wz * wz).coerceAtLeast(1e-4f)
        lastDirX = wx / wl; lastDirZ = wz / wl
        p.inX = lastDirX; p.inZ = lastDirZ
        p.inMag = (0.35f + len * 0.9f).coerceAtMost(1f)
    }

    fun step(dt: Float) {
        if (paused || result != null && sim.phase == Phase.DONE) return
        sim.step(dt)
        hudTimer -= dt
        if (hudTimer <= 0f) { hudTimer = 0.08f; publishHud() }
        if (sim.phase == Phase.DONE && result == null) {
            val r = computeResult()
            result = r
            publishHud()
            emit(UiEvent.Finished(r))
        }
    }

    private fun publishHud() {
        val p = sim.player
        val c = sim.course
        val seg = c.locate(p.z)
        val stage = when (seg.kind) {
            SegKind.START -> 1
            SegKind.FIELD, SegKind.GAP -> seg.index + 1
            else -> spec.stages.size
        }
        val need = sim.bridgeNeedFor(p)
        val total = c.totalLength
        val dots = sim.racers.map {
            val prog = if (it.finishRank > 0) 1f else ((it.z - c.start.z0) / total).coerceIn(0f, 1f)
            RacerDot(prog, it.look.body, it.isPlayer, it.finishRank > 0)
        }
        _hud.value = HudState(
            phase = sim.phase,
            stage = stage,
            stages = spec.stages.size,
            rank = p.rank.coerceAtLeast(1),
            racers = sim.racers.size,
            dots = dots,
            bridgeRatio = need.ratio,
            bridgeReady = need.ready,
            showBridgeMeter = need.gap >= 0 && !p.finished,
            snow = p.snow.toInt(),
            boost = p.boostT.coerceAtLeast(0f),
            shield = p.shieldT.coerceAtLeast(0f),
            magnet = p.magnetT.coerceAtLeast(0f),
            finaleStep = p.finaleStep,
            multiplier = sim.multiplier(),
            giftCoins = p.giftCoins,
        )
    }

    private fun computeResult(): RaceResult {
        val p = sim.player
        val rank = if (p.finishRank > 0) p.finishRank else sim.racers.size
        val cleared = p.finishRank in 1..3
        val stars = when (p.finishRank) { 1 -> 3; 2 -> 2; 3 -> 1; else -> 0 }
        var base = when (rank) { 1 -> 60; 2 -> 40; 3 -> 25; else -> 10 }
        base += (spec.number / 25).coerceAtMost(40)
        if (spec.isBoss && cleared) base = (base * 1.5f).toInt()
        val mult = if (cleared) sim.multiplier() else 1f
        val total = (base * mult).toInt() + p.giftCoins
        return RaceResult(
            level = spec.number, rank = rank, racers = sim.racers.size, cleared = cleared, stars = stars,
            baseCoins = base, multiplier = mult, giftCoins = p.giftCoins, totalCoins = total,
            bonks = p.bonks, blocks = p.blocks, meters = p.metersRolled, pickups = p.pickups,
            seconds = sim.raceTime.toInt(), boss = spec.isBoss,
        )
    }
}
