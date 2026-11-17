package com.ngstudio.snowrace.level

import com.ngstudio.snowrace.data.MAX_LEVEL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Proves every shipped level is solvable and fair.
 *  - Fast sample (always): ~300 levels incl. all boss types and every biome.
 *  - Full run (-DfullLevelValidation=true): all 5,000 levels; writes assets/levels/salts.bin
 *    (one byte per level = the first generator salt that passes validation).
 */
class LevelValidationTest {

    private fun firstValidSalt(n: Int): Pair<Int, ValidationReport> {
        var last: ValidationReport? = null
        for (salt in 0 until MAX_SALT) {
            val r = LevelValidator.validate(LevelGenerator.generate(n, salt))
            if (r.ok) return salt to r
            last = r
        }
        return -1 to last!!
    }

    @Test
    fun handcraftedLevelsAlwaysWinnable() {
        for (n in 1..10) {
            val spec = Handcrafted.level(n)
            assertTrue("static checks for $n: ${LevelValidator.staticCheck(spec)}", LevelValidator.staticCheck(spec).isEmpty())
            val (rank, _) = LevelValidator.simulate(spec, 0.5f)
            assertEquals("level $n must be won by a casual player", 1, rank)
        }
    }

    @Test
    fun sampledGeneratedLevelsAreValid() {
        val levels = (11..MAX_LEVEL step 17).toList() + (20..MAX_LEVEL step 170).toList()
        val failed = levels.filter { firstValidSalt(it).first < 0 }
        assertTrue("levels with no valid salt: $failed", failed.isEmpty())
    }

    @Test
    fun deterministicGeneration() {
        for (n in listOf(11, 99, 250, 1000, 2500, 4999, 5000)) {
            assertEquals(LevelGenerator.generate(n, 0), LevelGenerator.generate(n, 0))
            assertEquals(LevelGenerator.generate(n, 3), LevelGenerator.generate(n, 3))
        }
    }

    @Test
    fun fullValidationWritesSaltTable() {
        assumeTrue(System.getProperty("fullLevelValidation") == "true")
        val salts = ByteArray(MAX_LEVEL)
        val hist = IntArray(MAX_SALT + 1)
        var totalTime = 0f
        val failures = ArrayList<String>()
        val t0 = System.currentTimeMillis()
        for (n in 11..MAX_LEVEL) {
            val (salt, rep) = firstValidSalt(n)
            if (salt < 0) failures += "L$n ${rep.reasons}" else { salts[n - 1] = salt.toByte(); hist[salt]++; totalTime += rep.raceTime }
        }
        val out = listOf(File("src/main/assets/levels/salts.bin"), File("app/src/main/assets/levels/salts.bin")).first { it.parentFile?.exists() == true }
        out.writeBytes(salts)
        println("Validated ${MAX_LEVEL - 10} levels in ${(System.currentTimeMillis() - t0) / 1000}s; salt histogram=${hist.toList()}; avg race ${"%.1f".format(totalTime / (MAX_LEVEL - 10))}s; wrote ${out.absolutePath}")
        assertTrue("levels without a valid layout: $failures", failures.isEmpty())
    }

    companion object { const val MAX_SALT = 24 }
}
