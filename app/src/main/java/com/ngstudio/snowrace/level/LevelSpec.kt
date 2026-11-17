package com.ngstudio.snowrace.level

/** Pure data describing a level. Generated deterministically or loaded from handcrafted JSON. */

enum class TimeOfDay { DAY, SUNSET, NIGHT }
enum class Weather { CLEAR, SNOW, BLIZZARD }
enum class BossType { NONE, AVALANCHE, MEGA_BALL }
enum class HazardType { ICE, LOG, PLOW, PENGUINS, THIN_ICE, WIND, BUMPER, STEAM }
enum class PickupType { BOOST, SHIELD, MAGNET, MITTEN, GIFT }
enum class PropType { ROCK, PINE, SNOWMAN, CRATE, LANTERN, CANDY, CRYSTAL, BARREL }
enum class Personality { BALANCED, COLLECTOR, RUSHER, BULLY }

/** Snow layout styles: how fresh snow is distributed on a field. */
enum class SnowStyle { UNIFORM, PATCHES, STRIPES, ISLANDS, EDGES, CENTER }

data class ObstacleSpec(val type: PropType, val x: Float, val z: Float, val r: Float, val rot: Float = 0f)

/**
 * Hazard placed in a field. x,z are relative to the field (x centered on track, z from field start).
 * w,d = size (meaning depends on type); speed/period/phase drive motion or activation cycles.
 */
data class HazardSpec(
    val type: HazardType,
    val x: Float,
    val z: Float,
    val w: Float,
    val d: Float,
    val speed: Float = 1f,
    val period: Float = 4f,
    val phase: Float = 0f,
    val count: Int = 1,
)

data class PickupSpec(val type: PickupType, val x: Float, val z: Float)

data class StageSpec(
    val length: Float,
    val gap: Float,
    val snowStyle: SnowStyle = SnowStyle.UNIFORM,
    val snowDensity: Float = 1f,
    val obstacles: List<ObstacleSpec> = emptyList(),
    val hazards: List<HazardSpec> = emptyList(),
    val pickups: List<PickupSpec> = emptyList(),
    /** Height gained by the next platform (bridges slope up across the gap). */
    val rise: Float = 0.6f,
)

data class RivalSpec(
    val name: String,
    val character: String,
    val ball: String,
    val skill: Float,
    val aggression: Float,
    val personality: Personality,
)

data class LevelSpec(
    val number: Int,
    val seed: Long,
    val name: String,
    val biome: Biome,
    val time: TimeOfDay,
    val weather: Weather,
    val width: Float,
    val stages: List<StageSpec>,
    val finishLength: Float = 10f,
    val rivals: List<RivalSpec>,
    val boss: BossType = BossType.NONE,
    val bossSpeed: Float = 0f,
    val difficulty: Float,
    /** Win guarantee (levels 1-10): rubber-banded rivals, no bonks on the player. */
    val assist: Boolean = false,
    /** Tutorial id shown during this level (0 = none). */
    val tutorial: Int = 0,
    val newFeature: String? = null,
) {
    val isBoss: Boolean get() = boss != BossType.NONE
}

/** Visual biome with its palette. Colors are 0xRRGGBB ints. */
enum class Biome(
    val title: String,
    val skyTop: Int,
    val skyHorizon: Int,
    val fog: Int,
    val snow: Int,
    val snowShadow: Int,
    val waterDeep: Int,
    val waterShallow: Int,
    val cliff: Int,
    val accent: Int,
    val props: List<PropType>,
    val hazards: List<HazardType>,
) {
    ALPINE_VILLAGE(
        "Alpine Village", 0x3E8EF0, 0xBFE6FF, 0xD6EEFF, 0xFFFFFF, 0x9CC2EE, 0x0F6FB0, 0x37C6E8, 0x7FA3C9, 0xFF4D8D,
        listOf(PropType.PINE, PropType.ROCK, PropType.LANTERN, PropType.SNOWMAN), listOf(HazardType.ICE, HazardType.LOG, HazardType.PENGUINS, HazardType.PLOW)
    ),
    FROZEN_LAKE(
        "Frozen Lake", 0x4C9BEA, 0xD3F0FF, 0xE2F4FF, 0xF9FDFF, 0xA4C8F0, 0x1A5C9E, 0x5ED8F0, 0x8CB4D8, 0x3DF5C8,
        listOf(PropType.ROCK, PropType.PINE, PropType.CRYSTAL), listOf(HazardType.THIN_ICE, HazardType.ICE, HazardType.WIND, HazardType.PENGUINS)
    ),
    PINE_VALLEY(
        "Pine Valley", 0x3A86E0, 0xC4ECFF, 0xD0EBFA, 0xFFFFFF, 0x98BEE6, 0x0E6A9C, 0x2FBBD6, 0x7E98B5, 0xFFC83D,
        listOf(PropType.PINE, PropType.PINE, PropType.ROCK, PropType.BARREL), listOf(HazardType.LOG, HazardType.PLOW, HazardType.ICE, HazardType.BUMPER)
    ),
    CANDY_PEAKS(
        "Candy Peaks", 0xFF8FC8, 0xFFE0F0, 0xFFE6F3, 0xFFF7FB, 0xE8B4D6, 0x7A3FD0, 0xC58CFF, 0xD99AC4, 0xFF4D8D,
        listOf(PropType.CANDY, PropType.CANDY, PropType.SNOWMAN), listOf(HazardType.BUMPER, HazardType.ICE, HazardType.WIND, HazardType.LOG)
    ),
    ICE_CAVES(
        "Ice Caves", 0x2B4FA8, 0x8FD0F5, 0xA9D8F2, 0xEAF7FF, 0x86B0DE, 0x0B3F7A, 0x2AA6D9, 0x5F86B8, 0x5EC8FF,
        listOf(PropType.CRYSTAL, PropType.CRYSTAL, PropType.ROCK), listOf(HazardType.THIN_ICE, HazardType.BUMPER, HazardType.LOG, HazardType.ICE)
    ),
    AURORA_TUNDRA(
        "Aurora Tundra", 0x23246E, 0x6FA8E8, 0x8BB5E6, 0xF2F8FF, 0x8FA6D8, 0x10306E, 0x2A8FC8, 0x6E7FB0, 0x3DF5C8,
        listOf(PropType.ROCK, PropType.LANTERN, PropType.PINE), listOf(HazardType.WIND, HazardType.PENGUINS, HazardType.PLOW, HazardType.THIN_ICE)
    ),
    GLACIER_HARBOR(
        "Glacier Harbor", 0x3F92E6, 0xCDEBFF, 0xDBF0FF, 0xFFFFFF, 0x9DC3EC, 0x0A5A9A, 0x29B5E0, 0x7B9CC0, 0xFF7A45,
        listOf(PropType.CRATE, PropType.BARREL, PropType.LANTERN), listOf(HazardType.PLOW, HazardType.PENGUINS, HazardType.ICE, HazardType.LOG)
    ),
    STEAM_SPRINGS(
        "Steam Springs", 0x4A8FD8, 0xD8EEFA, 0xE4F1F8, 0xFFFFFF, 0xA5C4E2, 0x127A8A, 0x46D4C8, 0x8A8FA8, 0xFF7A45,
        listOf(PropType.ROCK, PropType.ROCK, PropType.LANTERN), listOf(HazardType.STEAM, HazardType.BUMPER, HazardType.THIN_ICE, HazardType.ICE)
    );
}
