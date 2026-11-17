package com.ngstudio.snowrace.level

import com.ngstudio.snowrace.core.Rng
import com.ngstudio.snowrace.core.clamp
import com.ngstudio.snowrace.data.Catalog
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Deterministic procedural levels 11..5000. Same (number, salt) -> identical level on every device.
 * The salt is chosen offline by the validator so every shipped level is proven solvable.
 */
object LevelGenerator {

    /** Level at which each hazard type first appears. */
    private val hazardUnlock = mapOf(
        HazardType.ICE to 6, HazardType.LOG to 7, HazardType.PENGUINS to 14, HazardType.PLOW to 18,
        HazardType.WIND to 22, HazardType.BUMPER to 26, HazardType.THIN_ICE to 32, HazardType.STEAM to 40,
    )
    private val pickupUnlock = mapOf(
        PickupType.BOOST to 5, PickupType.SHIELD to 8, PickupType.MAGNET to 12, PickupType.MITTEN to 9, PickupType.GIFT to 4,
    )

    fun difficulty(n: Int): Float {
        if (n <= 10) return 0.03f + n * 0.017f
        val base = 1f - exp(-(n - 10) / 230f)
        val block = (n - 1) % 10 // 0 = first after a boss (relief) ... 9 = boss
        val saw = if (block == 0) -0.07f else block / 9f * 0.09f
        return clamp(0.16f + base * 0.68f + saw, 0.1f, 0.9f)
    }

    fun biomeFor(n: Int): Biome = Biome.entries[((n - 1) / 25) % Biome.entries.size]

    fun timeFor(n: Int, biome: Biome): TimeOfDay {
        if (biome == Biome.AURORA_TUNDRA) return TimeOfDay.NIGHT
        val inBlock = (n - 1) % 25
        val cycle = ((n - 1) / 200) % 3
        val t = when {
            inBlock < 10 -> 0
            inBlock < 18 -> 1
            else -> 2
        }
        return TimeOfDay.entries[(t + cycle) % 3]
    }

    fun generate(n: Int, salt: Int = 0): LevelSpec {
        if (n <= 10) return Handcrafted.level(n)
        val seed = n.toLong() * 2_654_435_761L + salt * 97_531L + 12_345L
        val rng = Rng(seed)
        val ease = salt * 0.05f // retries get a little gentler
        val d = clamp(difficulty(n) - ease, 0.08f, 0.9f)
        val biome = biomeFor(n)
        val time = timeFor(n, biome)
        val boss = when {
            n % 10 != 0 -> BossType.NONE
            (n / 10) % 2 == 1 -> BossType.AVALANCHE
            else -> BossType.MEGA_BALL
        }
        val weather = when {
            d > 0.55f && rng.chance(0.15f) -> Weather.BLIZZARD
            rng.chance(0.55f) -> Weather.SNOW
            else -> Weather.CLEAR
        }
        val stageCount = when {
            boss != BossType.NONE -> if (d > 0.5f) 3 else 2
            n <= 30 -> 2
            d < 0.35f -> if (rng.chance(0.5f)) 2 else 3
            d < 0.62f -> 3
            else -> if (rng.chance(0.55f)) 3 else 4
        }
        val width = (12.5f + rng.range(0f, 3f) + d * 1.5f).roundTo(0.5f)
        val halfW = width / 2f
        val rivalCount = clamp((3 + d * 4.6f + rng.range(-0.4f, 0.6f)).roundToInt(), 3, 7)

        val hazardPool = biome.hazards.filter { (hazardUnlock[it] ?: 1) <= n }
        val pickupPool = PickupType.entries.filter { it != PickupType.GIFT && (pickupUnlock[it] ?: 1) <= n }

        val stages = ArrayList<StageSpec>()
        for (s in 0 until stageCount) {
            val late = s.toFloat() / max(1, stageCount - 1)
            val length = (17f + d * 9f + rng.range(0f, 6f) + late * 2f).roundTo(0.5f)
            val gap = clamp(3.4f + d * 4.0f + rng.range(-0.5f, 0.8f) + late * 0.4f, 3.2f, 8f).roundTo(0.1f)
            val style = when {
                d < 0.25f -> rng.pick(listOf(SnowStyle.UNIFORM, SnowStyle.UNIFORM, SnowStyle.PATCHES, SnowStyle.CENTER))
                else -> rng.pick(SnowStyle.entries)
            }
            val density = clamp(1.05f - d * 0.12f + rng.range(-0.05f, 0.05f), 0.85f, 1f)

            val placed = ArrayList<FloatArray>() // x, z, radius of everything placed (for spacing)
            val bands = ArrayList<FloatArray>()  // z, half-depth of full-width sweepers
            val hazards = ArrayList<HazardSpec>()
            val hazardCount = if (boss != BossType.NONE) min(1, hazardPool.size) else clamp((d * 3.2f + rng.range(-0.5f, 0.8f)).toInt(), 0, min(3, hazardPool.size))
            val zones = zoneSlots(length, hazardCount, rng)
            for (k in 0 until hazardCount) {
                val type = rng.pick(hazardPool)
                val z = zones[k]
                val h = makeHazard(type, z, halfW, d, rng)
                hazards += h
                if (hazardRadius(h) <= 0f) bands += floatArrayOf(h.z, h.d * 0.5f + 1.4f)
                else placed += floatArrayOf(h.x, h.z, hazardRadius(h))
            }

            val obstacles = ArrayList<ObstacleSpec>()
            val obstacleCount = clamp((2 + d * 5f + rng.range(-1f, 2f)).toInt(), 1, 7)
            var tries = 0
            while (obstacles.size < obstacleCount && tries < 60) {
                tries++
                val type = rng.pick(biome.props)
                val r = when (type) {
                    PropType.PINE -> rng.range(0.5f, 0.75f)
                    PropType.ROCK -> rng.range(0.45f, 0.85f)
                    PropType.CRYSTAL -> rng.range(0.4f, 0.7f)
                    PropType.LANTERN -> 0.3f
                    PropType.SNOWMAN -> 0.5f
                    PropType.CANDY -> rng.range(0.35f, 0.6f)
                    PropType.CRATE -> 0.6f
                    PropType.BARREL -> 0.45f
                }
                val x = rng.range(-halfW + 1.2f, halfW - 1.2f)
                val z = rng.range(3f, length - 3.2f)
                if (placed.any { dist(it[0], it[1], x, z) < it[2] + r + 2.1f }) continue
                if (bands.any { abs(it[0] - z) < it[1] + r }) continue
                obstacles += ObstacleSpec(type, x, z, r, rng.range(0f, 6.28f))
                placed += floatArrayOf(x, z, r)
            }

            val pickups = ArrayList<PickupSpec>()
            val powerCount = if (pickupPool.isEmpty()) 0 else (if (rng.chance(0.35f + d * 0.3f)) 2 else 1)
            repeat(powerCount) {
                for (attempt in 0 until 20) {
                    val x = rng.range(-halfW + 1.5f, halfW - 1.5f)
                    val z = rng.range(2.5f, length - 3f)
                    if (placed.any { dist(it[0], it[1], x, z) < it[2] + 1.6f }) continue
                    if (bands.any { abs(it[0] - z) < it[1] }) continue
                    pickups += PickupSpec(rng.pick(pickupPool), x, z)
                    placed += floatArrayOf(x, z, 0.5f)
                    break
                }
            }
            repeat(rng.nextInt(1, 3)) {
                for (attempt in 0 until 20) {
                    val x = rng.range(-halfW + 1.2f, halfW - 1.2f)
                    val z = rng.range(2f, length - 2.5f)
                    if (placed.any { dist(it[0], it[1], x, z) < it[2] + 1.3f }) continue
                    pickups += PickupSpec(PickupType.GIFT, x, z)
                    placed += floatArrayOf(x, z, 0.4f)
                    break
                }
            }
            stages += StageSpec(length, gap, style, density, obstacles, hazards, pickups, rise = rng.range(0.4f, 0.9f).roundTo(0.1f))
        }

        val rivals = ArrayList<RivalSpec>()
        val names = Names.rivals.toMutableList().also { rng.shuffle(it) }
        val looks = Catalog.rivalCharacters.map { it.id }.filter { it != "pip" }.toMutableList().also { rng.shuffle(it) }
        val balls = Catalog.balls.map { it.id }
        for (i in 0 until rivalCount) {
            val skill = clamp(0.25f + d * 0.55f + rng.range(-0.12f, 0.1f), 0.2f, 0.85f)
            val aggression = clamp(0.05f + d * 0.45f + rng.range(-0.1f, 0.12f), 0f, 0.7f)
            val personality = rng.pick(Personality.entries)
            rivals += RivalSpec(names[i % names.size], looks[i % looks.size], balls[rng.nextInt(min(balls.size, 6 + (d * 14).toInt()))], skill, aggression, personality)
        }

        val bossSpeed = when (boss) {
            BossType.AVALANCHE -> 2.1f + d * 1.5f
            BossType.MEGA_BALL -> 2.8f + d * 2.2f
            BossType.NONE -> 0f
        }

        return LevelSpec(
            number = n,
            seed = seed,
            name = Names.levelName(seed, biome, boss),
            biome = biome,
            time = time,
            weather = weather,
            width = width,
            stages = stages,
            rivals = rivals,
            boss = boss,
            bossSpeed = bossSpeed,
            difficulty = d,
            assist = false,
            tutorial = 0,
            newFeature = newFeatureAt(n),
        )
    }

    private fun zoneSlots(length: Float, count: Int, rng: Rng): List<Float> {
        if (count <= 0) return emptyList()
        // Hazards live in evenly spaced bands, away from the field start (spawn/landing) and the shore.
        val start = 4f; val end = length - 4f
        val band = (end - start) / count
        return (0 until count).map { start + band * (it + 0.5f) + rng.range(-band * 0.2f, band * 0.2f) }
    }

    private fun makeHazard(type: HazardType, z: Float, halfW: Float, d: Float, rng: Rng): HazardSpec = when (type) {
        HazardType.ICE -> HazardSpec(type, rng.range(-halfW * 0.5f, halfW * 0.5f), z, rng.range(3.5f, 6.5f), rng.range(2.5f, 4f))
        HazardType.LOG -> HazardSpec(type, 0f, z, 1f, rng.range(2.4f, 3.4f), speed = (1.6f + d * 2.2f) * rng.sign(), phase = rng.nextFloat())
        HazardType.PLOW -> HazardSpec(type, 0f, z, 2.3f, 1.5f, speed = (1.3f + d * 1.6f) * rng.sign(), phase = rng.nextFloat())
        HazardType.PENGUINS -> HazardSpec(type, 0f, z, 0.7f, 0.7f, speed = (0.8f + d * 0.8f) * rng.sign(), phase = rng.nextFloat(), count = rng.nextInt(3, 5))
        HazardType.THIN_ICE -> HazardSpec(type, rng.range(-halfW * 0.45f, halfW * 0.45f), z, rng.range(3f, 4.5f), rng.range(2.4f, 3.4f))
        HazardType.WIND -> HazardSpec(type, 0f, z, halfW * 2f, rng.range(3f, 4.5f), speed = rng.sign(), period = rng.range(4f, 6f), phase = rng.nextFloat())
        HazardType.BUMPER -> HazardSpec(type, rng.range(-halfW * 0.6f, halfW * 0.6f), z, rng.range(1.2f, 1.7f), 1f)
        HazardType.STEAM -> HazardSpec(type, rng.range(-halfW * 0.6f, halfW * 0.6f), z, rng.range(1.8f, 2.6f), 1f, period = rng.range(3.6f, 5f), phase = rng.nextFloat())
    }

    private fun hazardRadius(h: HazardSpec): Float = when (h.type) {
        HazardType.LOG, HazardType.PLOW, HazardType.PENGUINS, HazardType.WIND -> 0f // full-width sweepers handled by z bands
        HazardType.ICE, HazardType.THIN_ICE -> sqrt(h.w * h.w + h.d * h.d) * 0.5f
        else -> h.w * 0.5f
    }

    fun newFeatureAt(n: Int): String? {
        hazardUnlock.entries.firstOrNull { it.value == n }?.let { return "New hazard: ${hazardTitle(it.key)}" }
        pickupUnlock.entries.firstOrNull { it.value == n }?.let { return "New power-up: ${pickupTitle(it.key)}" }
        if (n % 25 == 1 && n > 1) return "New biome: ${biomeFor(n).title}"
        return null
    }

    fun hazardTitle(t: HazardType) = when (t) {
        HazardType.ICE -> "Slippery Ice"
        HazardType.LOG -> "Rolling Logs"
        HazardType.PLOW -> "Snow Plow"
        HazardType.PENGUINS -> "Penguin Parade"
        HazardType.THIN_ICE -> "Thin Ice"
        HazardType.WIND -> "Wind Gusts"
        HazardType.BUMPER -> "Bouncy Bumpers"
        HazardType.STEAM -> "Steam Vents"
    }

    fun pickupTitle(t: PickupType) = when (t) {
        PickupType.BOOST -> "Sled Boost"
        PickupType.SHIELD -> "Frost Shield"
        PickupType.MAGNET -> "Snow Magnet"
        PickupType.MITTEN -> "Mega Mitten"
        PickupType.GIFT -> "Gift Box"
    }

    private fun dist(ax: Float, az: Float, bx: Float, bz: Float): Float = sqrt((ax - bx) * (ax - bx) + (az - bz) * (az - bz))
    private fun Float.roundTo(step: Float): Float = (this / step).roundToInt() * step
}
