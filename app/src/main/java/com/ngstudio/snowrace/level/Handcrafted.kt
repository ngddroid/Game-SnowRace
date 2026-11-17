package com.ngstudio.snowrace.level

/**
 * Levels 1-10: hand-tuned onboarding curve. All of them run in assist mode
 * (rubber-banded rivals that never bonk the player and never finish first),
 * so a player who keeps moving always wins.
 */
object Handcrafted {
    private fun rivals(n: Int, skill: Float, vararg looks: String): List<RivalSpec> {
        val names = listOf("Milo", "Juno", "Kiki", "Bramble", "Nori", "Pebble")
        val balls = listOf("classic", "frost", "candy", "mint", "polka", "classic")
        return (0 until n).map {
            RivalSpec(names[it], looks.getOrElse(it) { "bubbles" }, balls[it], skill + it * 0.03f, 0.05f, Personality.COLLECTOR)
        }
    }

    fun level(n: Int): LevelSpec = when (n) {
        1 -> LevelSpec(
            number = 1, seed = 1001, name = "First Roll", biome = Biome.ALPINE_VILLAGE, time = TimeOfDay.DAY, weather = Weather.SNOW,
            width = 13f,
            stages = listOf(
                StageSpec(length = 22f, gap = 3.5f, snowStyle = SnowStyle.UNIFORM, snowDensity = 1f, rise = 0.6f,
                    obstacles = listOf(ObstacleSpec(PropType.PINE, -5.0f, 9f, 0.6f), ObstacleSpec(PropType.SNOWMAN, 4.9f, 14f, 0.5f))),
            ),
            rivals = rivals(3, 0.2f, "bubbles", "mochi", "minty"),
            difficulty = 0.03f, assist = true, tutorial = 1,
        )
        2 -> LevelSpec(
            number = 2, seed = 1002, name = "Two Shores", biome = Biome.ALPINE_VILLAGE, time = TimeOfDay.DAY, weather = Weather.CLEAR,
            width = 13f,
            stages = listOf(
                StageSpec(20f, 3.8f, SnowStyle.UNIFORM, 1f, obstacles = listOf(ObstacleSpec(PropType.PINE, 4.8f, 8f, 0.6f), ObstacleSpec(PropType.ROCK, -4.4f, 13f, 0.55f))),
                StageSpec(22f, 4.4f, SnowStyle.PATCHES, 1f, obstacles = listOf(ObstacleSpec(PropType.LANTERN, -5.0f, 6f, 0.35f), ObstacleSpec(PropType.LANTERN, 5.0f, 6f, 0.35f))),
            ),
            rivals = rivals(3, 0.22f, "sunny", "grape", "bubbles"),
            difficulty = 0.05f, assist = true, tutorial = 2,
        )
        3 -> LevelSpec(
            number = 3, seed = 1003, name = "Bonk!", biome = Biome.PINE_VALLEY, time = TimeOfDay.DAY, weather = Weather.SNOW,
            width = 13f,
            stages = listOf(
                StageSpec(22f, 4f, SnowStyle.UNIFORM, 1f, obstacles = listOf(ObstacleSpec(PropType.PINE, -4.8f, 5f, 0.6f), ObstacleSpec(PropType.PINE, 4.8f, 15f, 0.6f))),
                StageSpec(22f, 4.6f, SnowStyle.CENTER, 1f, obstacles = listOf(ObstacleSpec(PropType.ROCK, 0.0f, 7f, 0.7f))),
            ),
            rivals = rivals(3, 0.24f, "mochi", "minty", "sunny"),
            difficulty = 0.07f, assist = true, tutorial = 3,
        )
        4 -> LevelSpec(
            number = 4, seed = 1004, name = "Gift Hunt", biome = Biome.ALPINE_VILLAGE, time = TimeOfDay.SUNSET, weather = Weather.CLEAR,
            width = 13.5f,
            stages = listOf(
                StageSpec(22f, 4.2f, SnowStyle.PATCHES, 1f,
                    pickups = listOf(PickupSpec(PickupType.GIFT, -3.6f, 8f), PickupSpec(PickupType.GIFT, 3.6f, 14f)),
                    obstacles = listOf(ObstacleSpec(PropType.SNOWMAN, 0.0f, 11f, 0.5f))),
                StageSpec(24f, 4.8f, SnowStyle.ISLANDS, 1f,
                    pickups = listOf(PickupSpec(PickupType.GIFT, 0.0f, 6f), PickupSpec(PickupType.GIFT, -4.0f, 16f), PickupSpec(PickupType.GIFT, 4.0f, 18f))),
            ),
            rivals = rivals(4, 0.25f, "grape", "bubbles", "mochi", "minty"),
            difficulty = 0.09f, assist = true, tutorial = 4,
        )
        5 -> LevelSpec(
            number = 5, seed = 1005, name = "Sled Boost", biome = Biome.FROZEN_LAKE, time = TimeOfDay.DAY, weather = Weather.SNOW,
            width = 13.5f,
            stages = listOf(
                StageSpec(22f, 4.6f, SnowStyle.STRIPES, 1f, pickups = listOf(PickupSpec(PickupType.BOOST, 0.0f, 4f), PickupSpec(PickupType.GIFT, 4.0f, 15f)),
                    obstacles = listOf(ObstacleSpec(PropType.ROCK, -4.5f, 12f, 0.6f))),
                StageSpec(24f, 5f, SnowStyle.UNIFORM, 1f, pickups = listOf(PickupSpec(PickupType.BOOST, -3.2f, 8f))),
            ),
            rivals = rivals(4, 0.26f, "sunny", "grape", "bubbles", "mochi"),
            difficulty = 0.11f, assist = true, tutorial = 5,
        )
        6 -> LevelSpec(
            number = 6, seed = 1006, name = "Slippery Steps", biome = Biome.FROZEN_LAKE, time = TimeOfDay.SUNSET, weather = Weather.CLEAR,
            width = 13.5f,
            stages = listOf(
                StageSpec(24f, 4.6f, SnowStyle.UNIFORM, 1f,
                    hazards = listOf(HazardSpec(HazardType.ICE, 0.0f, 12f, 6f, 3.5f))),
                StageSpec(24f, 5f, SnowStyle.EDGES, 1f,
                    hazards = listOf(HazardSpec(HazardType.ICE, -3.2f, 9f, 4f, 3f), HazardSpec(HazardType.ICE, 3.2f, 16f, 4f, 3f)),
                    pickups = listOf(PickupSpec(PickupType.BOOST, 0.0f, 4f))),
            ),
            rivals = rivals(4, 0.28f, "minty", "sunny", "grape", "bubbles"),
            difficulty = 0.13f, assist = true, tutorial = 6,
        )
        7 -> LevelSpec(
            number = 7, seed = 1007, name = "Log Roll", biome = Biome.PINE_VALLEY, time = TimeOfDay.DAY, weather = Weather.SNOW,
            width = 13.5f,
            stages = listOf(
                StageSpec(24f, 4.8f, SnowStyle.PATCHES, 1f,
                    hazards = listOf(HazardSpec(HazardType.LOG, 0.0f, 12f, 1f, 3f, speed = 1.6f))),
                StageSpec(26f, 5f, SnowStyle.UNIFORM, 1f,
                    hazards = listOf(HazardSpec(HazardType.LOG, 0.0f, 10f, 1f, 3f, speed = 1.8f, phase = 0.5f)),
                    pickups = listOf(PickupSpec(PickupType.GIFT, 4.0f, 18f))),
            ),
            rivals = rivals(4, 0.3f, "bubbles", "mochi", "minty", "sunny"),
            difficulty = 0.15f, assist = true, tutorial = 7,
        )
        8 -> LevelSpec(
            number = 8, seed = 1008, name = "Shield Up", biome = Biome.GLACIER_HARBOR, time = TimeOfDay.DAY, weather = Weather.CLEAR,
            width = 13.5f,
            stages = listOf(
                StageSpec(24f, 5f, SnowStyle.ISLANDS, 1f,
                    pickups = listOf(PickupSpec(PickupType.SHIELD, 0.0f, 5f)),
                    obstacles = listOf(ObstacleSpec(PropType.CRATE, -4.4f, 10f, 0.6f), ObstacleSpec(PropType.BARREL, 4.4f, 15f, 0.5f))),
                StageSpec(24f, 5.2f, SnowStyle.UNIFORM, 1f,
                    hazards = listOf(HazardSpec(HazardType.LOG, 0.0f, 13f, 1f, 3f, speed = 2f)),
                    pickups = listOf(PickupSpec(PickupType.SHIELD, 2.4f, 6f), PickupSpec(PickupType.GIFT, -4.0f, 18f))),
            ),
            rivals = rivals(4, 0.32f, "grape", "minty", "sunny", "bubbles"),
            difficulty = 0.17f, assist = true, tutorial = 8,
        )
        9 -> LevelSpec(
            number = 9, seed = 1009, name = "Three Peaks", biome = Biome.ALPINE_VILLAGE, time = TimeOfDay.SUNSET, weather = Weather.SNOW,
            width = 13.5f,
            stages = listOf(
                StageSpec(20f, 4.4f, SnowStyle.UNIFORM, 1f, pickups = listOf(PickupSpec(PickupType.BOOST, 0.0f, 4f))),
                StageSpec(22f, 4.8f, SnowStyle.PATCHES, 1f, hazards = listOf(HazardSpec(HazardType.ICE, 0.0f, 11f, 5f, 3f))),
                StageSpec(22f, 5f, SnowStyle.UNIFORM, 1f, pickups = listOf(PickupSpec(PickupType.MITTEN, 0.0f, 8f), PickupSpec(PickupType.GIFT, 4.0f, 16f))),
            ),
            rivals = rivals(4, 0.33f, "mochi", "bubbles", "grape", "minty"),
            difficulty = 0.19f, assist = true, tutorial = 9,
        )
        else -> LevelSpec(
            number = 10, seed = 1010, name = "Avalanche!", biome = Biome.PINE_VALLEY, time = TimeOfDay.DAY, weather = Weather.SNOW,
            width = 13.5f,
            stages = listOf(
                StageSpec(24f, 4.4f, SnowStyle.UNIFORM, 1f, pickups = listOf(PickupSpec(PickupType.BOOST, 0.0f, 6f))),
                StageSpec(24f, 4.6f, SnowStyle.UNIFORM, 1f, pickups = listOf(PickupSpec(PickupType.SHIELD, 0.0f, 5f))),
            ),
            rivals = rivals(4, 0.33f, "sunny", "minty", "mochi", "grape"),
            boss = BossType.AVALANCHE, bossSpeed = 1.9f,
            difficulty = 0.2f, assist = true, tutorial = 10,
        )
    }
}
