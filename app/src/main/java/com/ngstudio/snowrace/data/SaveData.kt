package com.ngstudio.snowrace.data

import kotlinx.serialization.Serializable

const val MAX_LEVEL = 5000

@Serializable
data class SaveData(
    val version: Int = 1,
    val settings: Settings = Settings(),
    val profile: Profile = Profile(),
    val economy: Economy = Economy(),
    val progress: Progress = Progress(),
    val rewards: RewardsState = RewardsState(),
    val stats: Stats = Stats(),
    /** achievementId -> claimed (true once the reward was collected). */
    val achievementsClaimed: Set<String> = emptySet(),
)

@Serializable
data class Settings(
    val music: Boolean = true,
    val musicVolume: Float = 0.65f,
    val sfx: Boolean = true,
    val sfxVolume: Float = 0.9f,
    val haptics: Boolean = true,
    /** 0 = Auto, 1 = Low, 2 = Medium, 3 = High */
    val quality: Int = 0,
    val sensitivity: Float = 1f,
    val leftHanded: Boolean = false,
    val showFps: Boolean = false,
    /** Daily-gift reminders and push news (the system permission is asked separately on Android 13+). */
    val notifications: Boolean = true,
    /** The in-game "allow notifications?" prompt was already shown once. */
    val notifPrompted: Boolean = false,
    /** Send anonymous usage analytics and crash reports (Firebase). */
    val shareData: Boolean = true,
)

@Serializable
data class Profile(
    val name: String = "Player",
    val onboardingDone: Boolean = false,
    val character: String = "pip",
    val ball: String = "classic",
    val trail: String = "none",
    val createdEpochDay: Long = 0,
)

@Serializable
data class Economy(
    val coins: Int = 150,
    val gems: Int = 5,
    val owned: Set<String> = setOf("pip", "classic", "none"),
    val boosters: Map<String, Int> = mapOf(BoosterIds.BIG_START to 1),
)

object BoosterIds {
    const val BIG_START = "big_start"
    const val TURBO_START = "turbo_start"
    const val SHIELD_START = "shield_start"
    val all = listOf(BIG_START, TURBO_START, SHIELD_START)
}

@Serializable
data class Progress(
    /** Highest level reachable through normal progression (level 1 at install). */
    val highestUnlocked: Int = 1,
    /** Levels unlocked out of order (debug "watch ad" unlocks). */
    val extraUnlocked: Set<Int> = emptySet(),
    /** One char per level: '0'..'3' stars. Index 0 = level 1. */
    val stars: String = "",
    val lastPlayed: Int = 1,
    val bossesBeaten: Int = 0,
) {
    fun starsFor(level: Int): Int {
        val i = level - 1
        return if (i in stars.indices) stars[i] - '0' else 0
    }

    fun isUnlocked(level: Int): Boolean = level <= highestUnlocked || level in extraUnlocked

    fun totalStars(): Int = stars.sumOf { it - '0' }

    fun withStars(level: Int, value: Int): Progress {
        val i = level - 1
        val sb = StringBuilder(stars)
        while (sb.length <= i) sb.append('0')
        val old = sb[i] - '0'
        if (value > old) sb.setCharAt(i, ('0' + value))
        return copy(stars = sb.toString())
    }
}

@Serializable
data class MissionState(
    val id: String,
    val target: Int,
    val progress: Int = 0,
    val reward: Int,
    val rewardGems: Int = 0,
    val claimed: Boolean = false,
)

@Serializable
data class RewardsState(
    /** Next login-calendar slot to claim, 0..6. */
    val loginIndex: Int = 0,
    val lastLoginClaimDay: Long = -1,
    val wheelFreeDay: Long = -1,
    val wheelAdSpinsToday: Int = 0,
    val wheelAdDay: Long = -1,
    val missionsDay: Long = -1,
    val missions: List<MissionState> = emptyList(),
    val milestonesClaimed: Set<Int> = emptySet(),
)

@Serializable
data class Stats(
    val races: Int = 0,
    val wins: Int = 0,
    val podiums: Int = 0,
    val bonks: Int = 0,
    val bridgeBlocks: Int = 0,
    val metersRolled: Float = 0f,
    val powerups: Int = 0,
    val gifts: Int = 0,
    val winStreak: Int = 0,
    val bestStreak: Int = 0,
    val coinsEarned: Int = 0,
    val itemsUnlocked: Int = 0,
    val bossesBeaten: Int = 0,
    val wheelSpins: Int = 0,
    val playSeconds: Int = 0,
)
