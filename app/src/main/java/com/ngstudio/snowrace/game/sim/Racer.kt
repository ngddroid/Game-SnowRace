package com.ngstudio.snowrace.game.sim

import com.ngstudio.snowrace.data.BallDef
import com.ngstudio.snowrace.data.CharacterDef
import com.ngstudio.snowrace.data.TrailDef
import kotlin.math.sqrt

enum class RState { IDLE, RUN, STUN, FALL, FINISHED, FINALE }

class Racer(
    val id: Int,
    val isPlayer: Boolean,
    val name: String,
    val look: CharacterDef,
    val ballLook: BallDef,
    val trail: TrailDef,
) {
    // Kinematics
    var x = 0f
    var z = 0f
    var y = 0f
    var vx = 0f
    var vz = 0f
    var heading = 0f
    var knockVx = 0f
    var knockVz = 0f
    var vy = 0f
    var airY = 0f

    // Snowball
    var snow = 0f
        set(v) { field = v.coerceIn(0f, MAX_SNOW) }
    val hasBall: Boolean get() = snow >= 0.5f
    val ballR: Float get() = radiusFor(snow)
    var ballX = 0f
    var ballZ = 0f
    var ballY = 0f
    val ballRot = floatArrayOf(0f, 0f, 0f, 1f)
    var ballVisR = 0f

    // State
    var state = RState.IDLE
    var stateT = 0f
    var invuln = 0f
    var boostT = 0f
    var shieldT = 0f
    var magnetT = 0f
    var onIce = false
    var speedMul = 1f
    var gainMul = 1f
    var lockFinish = false

    // Control
    var inX = 0f
    var inZ = 1f
    var inMag = 0f

    // Race bookkeeping
    var finishRank = 0
    var finishTime = 0f
    var rank = 0
    var progress = 0f
    var respawnZ = 0f
    var respawnX = 0f
    var finaleStep = 0
    var finaleDone = false
    var celebrateX = 0f
    var celebrateZ = 0f
    var lastBonker = -1

    // Animation
    var stepPhase = 0f
    var moveAmt = 0f
    var speedNow = 0f
    var pushing = false
    var dizzyT = 0f
    var squash = 0f

    // Stats (mostly for the player)
    var metersRolled = 0f
    var bonks = 0
    var blocks = 0
    var pickups = 0
    var giftCoins = 0
    var falls = 0
    var timesBonked = 0

    var brain: AiBrain? = null

    val running: Boolean get() = state == RState.RUN
    val finished: Boolean get() = state == RState.FINISHED || state == RState.FINALE

    companion object {
        const val MAX_SNOW = 2600f
        const val BODY_R = 0.34f
        fun radiusFor(snow: Float): Float = if (snow < 0.5f) 0f else 0.26f + 0.042f * sqrt(snow)
    }
}

sealed class SimEvent {
    data class Countdown(val n: Int) : SimEvent()
    data object Go : SimEvent()
    data class Bonk(val x: Float, val y: Float, val z: Float, val attacker: Int, val victim: Int, val lost: Float) : SimEvent()
    data class Splash(val x: Float, val y: Float, val z: Float, val racer: Int) : SimEvent()
    data class Block(val x: Float, val y: Float, val z: Float, val owner: Int, val n: Int) : SimEvent()
    data class Crumble(val x: Float, val y: Float, val z: Float) : SimEvent()
    data class BallLost(val x: Float, val y: Float, val z: Float, val racer: Int) : SimEvent()
    data class Pickup(val type: com.ngstudio.snowrace.level.PickupType, val x: Float, val y: Float, val z: Float, val racer: Int) : SimEvent()
    data class Finish(val racer: Int, val rank: Int) : SimEvent()
    data class Step(val racer: Int, val left: Boolean) : SimEvent()
    data class Hazard(val type: com.ngstudio.snowrace.level.HazardType, val x: Float, val y: Float, val z: Float, val racer: Int) : SimEvent()
    data class FinaleStep(val step: Int, val x: Float, val y: Float, val z: Float) : SimEvent()
    data class StageEnter(val racer: Int, val stage: Int) : SimEvent()
    data class BossStart(val stage: Int) : SimEvent()
    data class BossEnd(val stage: Int) : SimEvent()
    data class Caught(val racer: Int, val x: Float, val y: Float, val z: Float) : SimEvent()
    data class Bump(val x: Float, val y: Float, val z: Float, val racer: Int) : SimEvent()
    data object RaceLost : SimEvent()
    data object RaceDone : SimEvent()
}
