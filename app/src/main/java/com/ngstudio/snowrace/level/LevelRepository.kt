package com.ngstudio.snowrace.level

import com.ngstudio.snowrace.data.MAX_LEVEL

/**
 * Entry point for level data. Levels 1-10 are handcrafted; 11-5000 are generated with the
 * validated salt from `assets/levels/salts.bin` (one byte per level, produced by the
 * full validation test run).
 */
object LevelRepository {
    @Volatile private var salts: ByteArray = ByteArray(0)
    private val cache = object : LinkedHashMap<Int, LevelSpec>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, LevelSpec>?): Boolean = size > 12
    }

    fun installSalts(bytes: ByteArray) { salts = bytes }

    fun saltFor(n: Int): Int = if (n - 1 in salts.indices) salts[n - 1].toInt() and 0xFF else 0

    fun get(n: Int): LevelSpec {
        val level = n.coerceIn(1, MAX_LEVEL)
        synchronized(cache) {
            cache[level]?.let { return it }
            val spec = if (level <= 10) Handcrafted.level(level) else LevelGenerator.generate(level, saltFor(level))
            cache[level] = spec
            return spec
        }
    }
}
