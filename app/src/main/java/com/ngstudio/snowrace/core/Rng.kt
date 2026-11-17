package com.ngstudio.snowrace.core

/**
 * Small, fast, deterministic PRNG (SplitMix64 seeding + xorshift64*).
 * Identical sequences on every device/JVM, which keeps generated levels stable.
 */
class Rng(seed: Long) {
    private var state: Long

    init {
        var z = seed + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        z = z xor (z ushr 31)
        state = if (z == 0L) 0x9E3779B97F4A7C15uL.toLong() else z
    }

    fun nextLong(): Long {
        var x = state
        x = x xor (x ushr 12)
        x = x xor (x shl 25)
        x = x xor (x ushr 27)
        state = x
        return x * 0x2545F4914F6CDD1DL
    }

    fun nextInt(bound: Int): Int {
        if (bound <= 1) return 0
        val r = (nextLong() ushr 33).toInt()
        return (r.toLong() * bound ushr 31).toInt().coerceIn(0, bound - 1)
    }

    fun nextInt(from: Int, untilInclusive: Int): Int = from + nextInt(untilInclusive - from + 1)

    fun nextFloat(): Float = (nextLong() ushr 40).toFloat() / (1L shl 24).toFloat()

    fun range(a: Float, b: Float): Float = a + (b - a) * nextFloat()

    fun chance(p: Float): Boolean = nextFloat() < p

    fun sign(): Float = if (nextFloat() < 0.5f) -1f else 1f

    fun <T> pick(list: List<T>): T = list[nextInt(list.size)]

    fun <T> shuffle(list: MutableList<T>) {
        for (i in list.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val t = list[i]; list[i] = list[j]; list[j] = t
        }
    }

    /** Gaussian-ish value in [-1,1] (sum of uniforms). */
    fun soft(): Float = (nextFloat() + nextFloat() + nextFloat()) / 1.5f - 1f
}
