package com.ngstudio.snowrace.data

import com.ngstudio.snowrace.game.RaceResult
import kotlin.math.max
import kotlin.math.min

/** Pure functions over [SaveData]: rewards, purchases, missions, achievements, progression. */
object GameRules {

    // ------------------------------------------------------------------ race results
    fun applyRace(s: SaveData, r: RaceResult, usedBoosters: Set<String>): SaveData {
        var progress = s.progress.copy(lastPlayed = r.level)
        if (r.cleared) {
            progress = progress.withStars(r.level, r.stars)
            if (r.level < MAX_LEVEL) {
                progress = when {
                    // normal progression frontier
                    r.level == progress.highestUnlocked -> progress.copy(highestUnlocked = r.level + 1)
                    // a level unlocked out of order (debug ad unlock): only open the one right after it
                    r.level > progress.highestUnlocked -> progress.copy(extraUnlocked = progress.extraUnlocked + (r.level + 1))
                    else -> progress
                }
            }
            if (r.boss) progress = progress.copy(bossesBeaten = progress.bossesBeaten + 1)
        }
        val boosters = s.economy.boosters.toMutableMap()
        for (b in usedBoosters) boosters[b] = max(0, (boosters[b] ?: 0) - 1)
        val won = r.rank == 1
        val streak = if (won) s.stats.winStreak + 1 else 0
        val stats = s.stats.copy(
            races = s.stats.races + 1,
            wins = s.stats.wins + if (won) 1 else 0,
            podiums = s.stats.podiums + if (r.cleared) 1 else 0,
            bonks = s.stats.bonks + r.bonks,
            bridgeBlocks = s.stats.bridgeBlocks + r.blocks,
            metersRolled = s.stats.metersRolled + r.meters,
            powerups = s.stats.powerups + r.pickups,
            winStreak = streak,
            bestStreak = max(s.stats.bestStreak, streak),
            coinsEarned = s.stats.coinsEarned + r.totalCoins,
            bossesBeaten = s.stats.bossesBeaten + if (r.boss && r.cleared) 1 else 0,
            playSeconds = s.stats.playSeconds + r.seconds,
        )
        var out = s.copy(
            progress = progress,
            economy = s.economy.copy(coins = s.economy.coins + r.totalCoins, boosters = boosters),
            stats = stats,
        )
        out = progressMissions(out) { m ->
            when (m.id) {
                "win" -> if (won) 1 else 0
                "podium" -> if (r.cleared) 1 else 0
                "bonk" -> r.bonks
                "blocks" -> r.blocks
                "roll" -> r.meters.toInt()
                "powerups" -> r.pickups
                "races" -> 1
                "finale" -> if (r.multiplier >= 3f) 1 else 0
                else -> 0
            }
        }
        return out
    }

    fun addCoins(s: SaveData, n: Int) = s.copy(economy = s.economy.copy(coins = s.economy.coins + n), stats = s.stats.copy(coinsEarned = s.stats.coinsEarned + max(0, n)))
    fun addGems(s: SaveData, n: Int) = s.copy(economy = s.economy.copy(gems = s.economy.gems + n))

    // ------------------------------------------------------------------ shop
    fun owns(s: SaveData, id: String) = id in s.economy.owned

    fun canAfford(s: SaveData, p: Price) = when (p.type) {
        PriceType.FREE -> true
        PriceType.COINS -> s.economy.coins >= p.amount
        PriceType.GEMS -> s.economy.gems >= p.amount
        PriceType.REWARD -> false
    }

    fun buy(s: SaveData, id: String, p: Price): SaveData? {
        if (owns(s, id) || !canAfford(s, p)) return null
        val e = s.economy
        val e2 = when (p.type) {
            PriceType.COINS -> e.copy(coins = e.coins - p.amount)
            PriceType.GEMS -> e.copy(gems = e.gems - p.amount)
            else -> e
        }
        return grant(s.copy(economy = e2), id)
    }

    fun grant(s: SaveData, id: String): SaveData =
        if (owns(s, id)) s else s.copy(economy = s.economy.copy(owned = s.economy.owned + id), stats = s.stats.copy(itemsUnlocked = s.stats.itemsUnlocked + 1))

    const val MYSTERY_BOX_PRICE = 1000

    /** Random not-yet-owned common/rare item, or null when nothing is left. */
    fun mysteryPick(s: SaveData, seed: Long): String? {
        val pool = Catalog.characters.filter { (it.rarity == Rarity.COMMON || it.rarity == Rarity.RARE) && it.price.type == PriceType.COINS && !owns(s, it.id) }.map { it.id } +
            Catalog.balls.filter { (it.rarity == Rarity.COMMON || it.rarity == Rarity.RARE) && it.price.type == PriceType.COINS && !owns(s, it.id) }.map { it.id } +
            Catalog.trails.filter { (it.rarity == Rarity.COMMON || it.rarity == Rarity.RARE) && it.price.type == PriceType.COINS && !owns(s, it.id) }.map { it.id }
        if (pool.isEmpty()) return null
        return pool[((seed % pool.size + pool.size) % pool.size).toInt()]
    }

    const val BOOSTER_PRICE = 150

    // ------------------------------------------------------------------ daily login
    data class LoginReward(val coins: Int = 0, val gems: Int = 0, val item: String? = null, val label: String)

    val loginRewards = listOf(
        LoginReward(coins = 100, label = "100"),
        LoginReward(coins = 150, label = "150"),
        LoginReward(gems = 5, label = "5"),
        LoginReward(coins = 250, label = "250"),
        LoginReward(gems = 10, label = "10"),
        LoginReward(coins = 400, label = "400"),
        LoginReward(item = "flurry", gems = 0, label = "Flurry"),
    )

    fun loginClaimable(s: SaveData, today: Long) = s.rewards.lastLoginClaimDay < today

    fun claimLogin(s: SaveData, today: Long): Pair<SaveData, LoginReward>? {
        if (!loginClaimable(s, today)) return null
        // streak resets if a day was missed
        val idx = if (s.rewards.lastLoginClaimDay == today - 1) s.rewards.loginIndex % 7 else 0
        val reward = loginRewards[idx]
        var out = s
        out = addCoins(out, reward.coins)
        out = addGems(out, reward.gems)
        var shown = reward
        if (reward.item != null) {
            out = if (owns(out, reward.item)) { shown = LoginReward(gems = 25, label = "25"); addGems(out, 25) } else grant(out, reward.item)
        }
        out = out.copy(rewards = out.rewards.copy(loginIndex = (idx + 1) % 7, lastLoginClaimDay = today))
        return out to shown
    }

    fun loginDisplayIndex(s: SaveData, today: Long): Int =
        if (s.rewards.lastLoginClaimDay == today) (s.rewards.loginIndex + 6) % 7
        else if (s.rewards.lastLoginClaimDay == today - 1) s.rewards.loginIndex % 7 else 0

    // ------------------------------------------------------------------ lucky wheel
    data class WheelSlice(val label: String, val coins: Int = 0, val gems: Int = 0, val booster: String? = null, val color: Long, val weight: Int)

    val wheel = listOf(
        WheelSlice("50", coins = 50, color = 0xFF5EC8FF, weight = 22),
        WheelSlice("5", gems = 5, color = 0xFFFF4D8D, weight = 10),
        WheelSlice("100", coins = 100, color = 0xFF3DF5C8, weight = 20),
        WheelSlice("Boost", booster = BoosterIds.TURBO_START, color = 0xFFFF7A45, weight = 10),
        WheelSlice("200", coins = 200, color = 0xFFFFC83D, weight = 14),
        WheelSlice("15", gems = 15, color = 0xFFB46CFF, weight = 4),
        WheelSlice("Big", booster = BoosterIds.BIG_START, color = 0xFF5EC8FF, weight = 12),
        WheelSlice("500", coins = 500, color = 0xFFFF4D8D, weight = 8),
    )

    fun wheelFree(s: SaveData, today: Long) = s.rewards.wheelFreeDay < today
    const val WHEEL_AD_SPINS = 3
    fun wheelAdSpinsLeft(s: SaveData, today: Long) = if (s.rewards.wheelAdDay < today) WHEEL_AD_SPINS else WHEEL_AD_SPINS - s.rewards.wheelAdSpinsToday

    fun pickWheel(seed: Long): Int {
        val total = wheel.sumOf { it.weight }
        var r = ((seed % total) + total) % total
        for ((i, s) in wheel.withIndex()) { if (r < s.weight) return i; r -= s.weight }
        return 0
    }

    fun applyWheel(s: SaveData, index: Int, today: Long, viaAd: Boolean): SaveData {
        val w = wheel[index]
        var out = addGems(addCoins(s, w.coins), w.gems)
        if (w.booster != null) {
            val b = out.economy.boosters.toMutableMap(); b[w.booster] = (b[w.booster] ?: 0) + 1
            out = out.copy(economy = out.economy.copy(boosters = b))
        }
        val rw = out.rewards
        out = out.copy(
            rewards = if (viaAd) rw.copy(wheelAdDay = today, wheelAdSpinsToday = (if (rw.wheelAdDay < today) 0 else rw.wheelAdSpinsToday) + 1)
            else rw.copy(wheelFreeDay = today),
            stats = out.stats.copy(wheelSpins = out.stats.wheelSpins + 1),
        )
        return progressMissions(out) { if (it.id == "spin") 1 else 0 }
    }

    // ------------------------------------------------------------------ missions
    data class MissionDef(val id: String, val title: String, val targets: List<Int>, val reward: Int, val gems: Int = 0)

    val missionPool = listOf(
        MissionDef("win", "Win %d races", listOf(1, 2, 3), 120, 2),
        MissionDef("podium", "Finish top 3 in %d races", listOf(2, 3, 5), 100),
        MissionDef("bonk", "Bonk %d rivals", listOf(3, 5, 8), 100, 1),
        MissionDef("blocks", "Build %d bridge blocks", listOf(80, 150, 250), 90),
        MissionDef("roll", "Roll snow for %d m", listOf(250, 500, 900), 90),
        MissionDef("powerups", "Grab %d power-ups", listOf(2, 4, 6), 80),
        MissionDef("races", "Play %d races", listOf(3, 5, 8), 70),
        MissionDef("finale", "Reach x3 or more on the finale %d times", listOf(1, 2), 150, 3),
        MissionDef("spin", "Spin the Lucky Wheel", listOf(1), 60),
    )

    fun missionTitle(m: MissionState): String {
        val def = missionPool.firstOrNull { it.id == m.id } ?: return m.id
        if (!def.title.contains("%d")) return def.title
        var t = def.title.format(m.target)
        if (m.target == 1) {
            t = t.replace(" 1 times", " once").replace(" races", " race").replace(" rivals", " rival")
                .replace(" power-ups", " power-up").replace(" blocks", " block")
        }
        return t
    }

    fun refreshMissions(s: SaveData, today: Long): SaveData {
        if (s.rewards.missionsDay == today && s.rewards.missions.isNotEmpty()) return s
        val rng = java.util.Random(today * 7919L + s.profile.createdEpochDay)
        val picks = missionPool.shuffled(rng).take(3)
        val tier = min(2, (s.progress.highestUnlocked / 30))
        val missions = picks.map { d ->
            val target = d.targets[min(tier, d.targets.size - 1)]
            MissionState(d.id, target, 0, d.reward + tier * 30, d.gems)
        }
        return s.copy(rewards = s.rewards.copy(missionsDay = today, missions = missions))
    }

    inline fun progressMissions(s: SaveData, delta: (MissionState) -> Int): SaveData {
        if (s.rewards.missions.isEmpty()) return s
        val ms = s.rewards.missions.map { m -> if (m.claimed) m else m.copy(progress = min(m.target, m.progress + delta(m))) }
        return s.copy(rewards = s.rewards.copy(missions = ms))
    }

    fun claimMission(s: SaveData, index: Int): SaveData? {
        val m = s.rewards.missions.getOrNull(index) ?: return null
        if (m.claimed || m.progress < m.target) return null
        val ms = s.rewards.missions.toMutableList(); ms[index] = m.copy(claimed = true)
        return addGems(addCoins(s, m.reward), m.rewardGems).let { it.copy(rewards = it.rewards.copy(missions = ms)) }
    }

    fun missionsReady(s: SaveData) = s.rewards.missions.count { !it.claimed && it.progress >= it.target }

    // ------------------------------------------------------------------ milestone chests
    fun milestoneReady(s: SaveData): Int? {
        val cleared = s.progress.highestUnlocked - 1
        var m = 10
        while (m <= cleared) { if (m !in s.rewards.milestonesClaimed) return m; m += 10 }
        return null
    }

    fun milestoneReward(m: Int): Pair<Int, Int> = (200 + m * 5).coerceAtMost(2000) to (if (m % 50 == 0) 15 else 3)

    fun claimMilestone(s: SaveData, m: Int): SaveData {
        val (c, g) = milestoneReward(m)
        return addGems(addCoins(s, c), g).let { it.copy(rewards = it.rewards.copy(milestonesClaimed = it.rewards.milestonesClaimed + m)) }
    }

    // ------------------------------------------------------------------ achievements
    data class Achievement(val id: String, val title: String, val desc: String, val target: Int, val reward: Int, val gems: Int, val metric: (SaveData) -> Int)

    val achievements: List<Achievement> = listOf(
        Achievement("first_win", "First Victory", "Win your first race", 1, 100, 2) { it.stats.wins },
        Achievement("wins_10", "Champion", "Win 10 races", 10, 300, 5) { it.stats.wins },
        Achievement("wins_50", "Snow Legend", "Win 50 races", 50, 1000, 15) { it.stats.wins },
        Achievement("wins_200", "Frost Royalty", "Win 200 races", 200, 3000, 40) { it.stats.wins },
        Achievement("level_10", "Getting Started", "Reach level 10", 10, 150, 3) { it.progress.highestUnlocked },
        Achievement("level_25", "Explorer", "Reach level 25", 25, 300, 5) { it.progress.highestUnlocked },
        Achievement("level_50", "Adventurer", "Reach level 50", 50, 600, 8) { it.progress.highestUnlocked },
        Achievement("level_100", "Globetrotter", "Reach level 100", 100, 1200, 15) { it.progress.highestUnlocked },
        Achievement("level_250", "Peak Seeker", "Reach level 250", 250, 2500, 25) { it.progress.highestUnlocked },
        Achievement("level_500", "Summit Master", "Reach level 500", 500, 5000, 50) { it.progress.highestUnlocked },
        Achievement("bonk_10", "Bonk Rookie", "Bonk 10 rivals", 10, 120, 2) { it.stats.bonks },
        Achievement("bonk_100", "Bonk Boss", "Bonk 100 rivals", 100, 600, 8) { it.stats.bonks },
        Achievement("bonk_500", "Unstoppable", "Bonk 500 rivals", 500, 2000, 20) { it.stats.bonks },
        Achievement("blocks_500", "Builder", "Place 500 bridge blocks", 500, 200, 3) { it.stats.bridgeBlocks },
        Achievement("blocks_5000", "Architect", "Place 5,000 bridge blocks", 5000, 1000, 10) { it.stats.bridgeBlocks },
        Achievement("roll_1000", "Rolling Along", "Roll snow for 1 km", 1000, 200, 3) { it.stats.metersRolled.toInt() },
        Achievement("roll_20000", "Snow Plow", "Roll snow for 20 km", 20000, 1500, 15) { it.stats.metersRolled.toInt() },
        Achievement("streak_3", "Hat Trick", "Win 3 races in a row", 3, 250, 4) { it.stats.bestStreak },
        Achievement("streak_10", "On Fire", "Win 10 races in a row", 10, 1500, 20) { it.stats.bestStreak },
        Achievement("boss_1", "Boss Buster", "Beat a boss level", 1, 300, 5) { it.stats.bossesBeaten },
        Achievement("boss_10", "Boss Hunter", "Beat 10 boss levels", 10, 1200, 15) { it.stats.bossesBeaten },
        Achievement("items_5", "Collector", "Unlock 5 items", 5, 200, 3) { it.stats.itemsUnlocked },
        Achievement("items_20", "Curator", "Unlock 20 items", 20, 1000, 12) { it.stats.itemsUnlocked },
        Achievement("stars_30", "Star Gazer", "Earn 30 stars", 30, 300, 5) { it.progress.totalStars() },
        Achievement("stars_300", "Constellation", "Earn 300 stars", 300, 2000, 20) { it.progress.totalStars() },
        Achievement("powerups_25", "Power Player", "Grab 25 power-ups", 25, 250, 4) { it.stats.powerups },
        Achievement("races_100", "Dedicated", "Play 100 races", 100, 800, 10) { it.stats.races },
        Achievement("spins_10", "Lucky Spinner", "Spin the wheel 10 times", 10, 200, 3) { it.stats.wheelSpins },
        Achievement("coins_10000", "Piggy Bank", "Earn 10,000 coins", 10000, 500, 8) { it.stats.coinsEarned },
        Achievement("coins_100000", "Treasure Hoard", "Earn 100,000 coins", 100000, 3000, 30) { it.stats.coinsEarned },
    )

    fun achievementsReady(s: SaveData) = achievements.count { it.id !in s.achievementsClaimed && it.metric(s) >= it.target }

    fun claimAchievement(s: SaveData, id: String): SaveData? {
        val a = achievements.firstOrNull { it.id == id } ?: return null
        if (id in s.achievementsClaimed || a.metric(s) < a.target) return null
        return addGems(addCoins(s, a.reward), a.gems).copy(achievementsClaimed = s.achievementsClaimed + id)
    }
}
