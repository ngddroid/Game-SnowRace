package com.ngstudio.snowrace.data

import com.ngstudio.snowrace.game.RaceResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRulesTest {
    private fun result(level: Int, rank: Int, coins: Int = 100) = RaceResult(
        level = level, rank = rank, racers = 5, cleared = rank <= 3, stars = when (rank) { 1 -> 3; 2 -> 2; 3 -> 1; else -> 0 },
        baseCoins = coins, multiplier = 1f, giftCoins = 0, totalCoins = coins, bonks = 2, blocks = 30, meters = 40f, pickups = 1, seconds = 30, boss = false,
    )

    @Test
    fun freshInstallOnlyLevelOneUnlocked() {
        val s = SaveData()
        assertTrue(s.progress.isUnlocked(1))
        assertFalse(s.progress.isUnlocked(2))
        assertFalse(s.progress.isUnlocked(5000))
    }

    @Test
    fun clearingFrontierUnlocksNext() {
        var s = SaveData()
        s = GameRules.applyRace(s, result(1, 1), emptySet())
        assertTrue(s.progress.isUnlocked(2))
        assertEquals(3, s.progress.starsFor(1))
        assertEquals(250, s.economy.coins) // 150 start + 100
        // losing does not unlock
        val lost = GameRules.applyRace(s, result(2, 5), emptySet())
        assertFalse(lost.progress.isUnlocked(3))
    }

    @Test
    fun adUnlockedLevelOnlyOpensItsSuccessor() {
        var s = SaveData()
        s = s.copy(progress = s.progress.copy(extraUnlocked = setOf(57)))
        s = GameRules.applyRace(s, result(57, 2), emptySet())
        assertTrue(s.progress.isUnlocked(58))
        assertFalse(s.progress.isUnlocked(20))
        assertEquals(1, s.progress.highestUnlocked)
    }

    @Test
    fun starsNeverDecrease() {
        var s = SaveData()
        s = GameRules.applyRace(s, result(1, 1), emptySet())
        s = GameRules.applyRace(s, result(1, 3), emptySet())
        assertEquals(3, s.progress.starsFor(1))
    }

    @Test
    fun purchasesRespectBalance() {
        val s = SaveData(economy = Economy(coins = 500))
        assertNull(GameRules.buy(s, "bubbles", Price.coins(600)))
        val rich = s.copy(economy = s.economy.copy(coins = 700))
        val bought = GameRules.buy(rich, "bubbles", Price.coins(600))
        assertNotNull(bought)
        assertEquals(100, bought!!.economy.coins)
        assertTrue("bubbles" in bought.economy.owned)
        assertNull(GameRules.buy(bought, "bubbles", Price.coins(600))) // already owned
    }

    @Test
    fun loginCalendarClaimsOncePerDayAndResetsAfterGap() {
        var s = SaveData()
        val (s1, r1) = GameRules.claimLogin(s, 100)!!
        assertEquals(100, r1.coins)
        assertNull(GameRules.claimLogin(s1, 100))
        val (s2, r2) = GameRules.claimLogin(s1, 101)!!
        assertEquals(150, r2.coins)
        val (_, r3) = GameRules.claimLogin(s2, 105)!! // missed days -> back to day 1
        assertEquals(100, r3.coins)
        s = s2
        assertEquals(2, s.rewards.loginIndex)
    }

    @Test
    fun wheelPickIsAlwaysValid() {
        for (seed in 0L until 2000L) {
            val i = GameRules.pickWheel(seed * 7919)
            assertTrue(i in GameRules.wheel.indices)
        }
    }

    @Test
    fun missionsProgressAndClaim() {
        var s = GameRules.refreshMissions(SaveData(), 10)
        assertEquals(3, s.rewards.missions.size)
        repeat(10) { s = GameRules.applyRace(s, result(1, 1), emptySet()) }
        val ready = s.rewards.missions.indexOfFirst { it.progress >= it.target }
        if (ready >= 0) {
            val before = s.economy.coins
            val after = GameRules.claimMission(s, ready)!!
            assertTrue(after.economy.coins > before)
            assertNull(GameRules.claimMission(after, ready))
        }
    }
}
