package com.ngstudio.snowrace.data

/** Cosmetic catalog. Colors are ARGB ints so the renderer (GL) and UI can share them. */

enum class Rarity(val label: String, val color: Long) {
    COMMON("Common", 0xFF8FB8DE),
    RARE("Rare", 0xFF5EC8FF),
    EPIC("Epic", 0xFFB46CFF),
    LEGENDARY("Legendary", 0xFFFFC83D),
}

enum class Hat { BEANIE, TOPHAT, CROWN, CHEF, PIRATE, HELMET, HEADBAND, EARMUFFS, SANTA, NONE }
enum class Ears { NONE, ROUND, POINTY, TUFT }
enum class Extra { BEAK, ANTLERS, HORNS, UNICORN_HORN, CARROT_NOSE, EYE_PATCH, ANTENNA, PANDA_PATCHES, TAIL, CAPE, MANE, WHISKERS, FLUFF, BEARD, BELT }

enum class PriceType { FREE, COINS, GEMS, REWARD }

data class Price(val type: PriceType, val amount: Int = 0) {
    companion object {
        val FREE = Price(PriceType.FREE)
        fun coins(n: Int) = Price(PriceType.COINS, n)
        fun gems(n: Int) = Price(PriceType.GEMS, n)
        fun reward(note: Int = 0) = Price(PriceType.REWARD, note)
    }
}

data class CharacterDef(
    val id: String,
    val name: String,
    val rarity: Rarity,
    val price: Price,
    val body: Int,
    val belly: Int,
    val hat: Hat = Hat.BEANIE,
    val hatColor: Int = 0xFF3DF5C8.toInt(),
    val hatTrim: Int = 0xFFFFFFFF.toInt(),
    val pompom: Int = 0xFFFF4D8D.toInt(),
    val scarf: Int = 0xFFFF4D8D.toInt(),
    val scarfStripe: Int = 0xFFFFFFFF.toInt(),
    val mitten: Int = 0xFF3DF5C8.toInt(),
    val boots: Int = 0xFF1B2A4A.toInt(),
    val ears: Ears = Ears.NONE,
    val earColor: Int = body,
    val extras: Set<Extra> = emptySet(),
    val extraColor: Int = 0xFFFFC83D.toInt(),
    val unlockNote: String = "",
)

enum class BallPattern { PLAIN, STRIPES, SWIRL, DOTS, SPRINKLES, CHECKER, CRATERS, CRACKS, STARS, RAINBOW, GLITTER, BANDS }

data class BallDef(
    val id: String,
    val name: String,
    val rarity: Rarity,
    val price: Price,
    val base: Int,
    val accent: Int,
    val pattern: BallPattern,
    val glow: Float = 0f,
)

enum class TrailShape { CIRCLE, STAR, HEART, FLAKE, SPARKLE, NOTE }

data class TrailDef(
    val id: String,
    val name: String,
    val rarity: Rarity,
    val price: Price,
    val shape: TrailShape,
    val colors: List<Int>,
    val rate: Float = 18f,
)

object Catalog {
    private fun c(v: Long) = v.toInt()

    val characters: List<CharacterDef> = listOf(
        CharacterDef("pip", "Pip", Rarity.COMMON, Price.FREE, c(0xFFFF7A45), c(0xFFFFD2B8)),
        CharacterDef("bubbles", "Bubbles", Rarity.COMMON, Price.coins(600), c(0xFF5EC8FF), c(0xFFD9F3FF), hatColor = c(0xFFFFC83D), pompom = c(0xFFFF4D8D), scarf = c(0xFFFF5A5A), mitten = c(0xFFFFC83D)),
        // id stays "mochi" so existing saves and level rival lists keep working
        CharacterDef("mochi", "Lil' Santa", Rarity.COMMON, Price.coins(600), c(0xFFD9263A), c(0xFFFFFFFF), hat = Hat.SANTA, hatColor = c(0xFFD9263A), hatTrim = c(0xFFFFFFFF), pompom = c(0xFFFFFFFF), scarf = c(0xFFFFFFFF), scarfStripe = c(0xFFFFFFFF), mitten = c(0xFFFFFFFF), boots = c(0xFF1E1E26), extras = setOf(Extra.BEARD, Extra.BELT), extraColor = c(0xFFFFFFFF)),
        CharacterDef("minty", "Minty", Rarity.COMMON, Price.coins(600), c(0xFF4FE3B5), c(0xFFD8FFF1), hatColor = c(0xFF7B5CFF), pompom = c(0xFFFFC83D), scarf = c(0xFF7B5CFF), mitten = c(0xFF7B5CFF)),
        CharacterDef("sunny", "Sunny", Rarity.COMMON, Price.coins(700), c(0xFFFFD34D), c(0xFFFFF4C9), hatColor = c(0xFFFF7A45), pompom = c(0xFFFFFFFF), scarf = c(0xFF3DB2FF), mitten = c(0xFFFF7A45)),
        CharacterDef("grape", "Grape", Rarity.COMMON, Price.coins(700), c(0xFF9A6BFF), c(0xFFE6DBFF), hatColor = c(0xFF3DF5C8), pompom = c(0xFFFFC83D), scarf = c(0xFFFFC83D), mitten = c(0xFF3DF5C8)),
        CharacterDef("waddles", "Waddles", Rarity.RARE, Price.coins(1800), c(0xFF26304F), c(0xFFFFFFFF), hatColor = c(0xFFFF5A5A), pompom = c(0xFFFFFFFF), scarf = c(0xFFFF5A5A), mitten = c(0xFF26304F), boots = c(0xFFFFA733), extras = setOf(Extra.BEAK), extraColor = c(0xFFFFA733)),
        CharacterDef("bjorn", "Bjorn", Rarity.RARE, Price.coins(1800), c(0xFFF4F8FF), c(0xFFFFFFFF), hat = Hat.NONE, scarf = c(0xFF3D7BFF), scarfStripe = c(0xFFFFC83D), mitten = c(0xFFF4F8FF), ears = Ears.ROUND, earColor = c(0xFFF4F8FF), boots = c(0xFF3D7BFF)),
        CharacterDef("ember", "Ember", Rarity.RARE, Price.coins(1800), c(0xFFFF8A2B), c(0xFFFFF1E0), hat = Hat.NONE, scarf = c(0xFF2FB38A), mitten = c(0xFF3A2A2A), ears = Ears.POINTY, earColor = c(0xFFFF8A2B), extras = setOf(Extra.TAIL, Extra.WHISKERS), extraColor = c(0xFFFFF1E0)),
        CharacterDef("sprout", "Sprout", Rarity.RARE, Price.coins(2000), c(0xFFB0794A), c(0xFFF2DCC0), hat = Hat.NONE, scarf = c(0xFFFF4040), scarfStripe = c(0xFF2DBE60), mitten = c(0xFF7A4E2D), ears = Ears.ROUND, earColor = c(0xFFB0794A), extras = setOf(Extra.ANTLERS), extraColor = c(0xFF7A4E2D)),
        CharacterDef("bao", "Bao", Rarity.RARE, Price.coins(2000), c(0xFFFFFFFF), c(0xFFFFFFFF), hat = Hat.NONE, scarf = c(0xFF2DBE60), mitten = c(0xFF262626), boots = c(0xFF262626), ears = Ears.ROUND, earColor = c(0xFF262626), extras = setOf(Extra.PANDA_PATCHES), extraColor = c(0xFF262626)),
        CharacterDef("koda", "Koda", Rarity.RARE, Price.coins(2200), c(0xFF8C9BB0), c(0xFFFFFFFF), hat = Hat.NONE, scarf = c(0xFF3D7BFF), mitten = c(0xFF8C9BB0), ears = Ears.POINTY, earColor = c(0xFF6B7A90), extras = setOf(Extra.TAIL), extraColor = c(0xFFFFFFFF)),
        CharacterDef("hopper", "Hopper", Rarity.RARE, Price.coins(2200), c(0xFF6BD64A), c(0xFFE8FFD0), hat = Hat.EARMUFFS, hatColor = c(0xFFFF4D8D), scarf = c(0xFFFFC83D), mitten = c(0xFF6BD64A)),
        CharacterDef("shade", "Shade", Rarity.RARE, Price.coins(2500), c(0xFF2A2D3A), c(0xFF3A3F52), hat = Hat.HEADBAND, hatColor = c(0xFFFF3B5C), scarf = c(0xFF2A2D3A), scarfStripe = c(0xFFFF3B5C), mitten = c(0xFF2A2D3A)),
        CharacterDef("hoot", "Hoot", Rarity.EPIC, Price.coins(4000), c(0xFF9C6B45), c(0xFFF3DDBF), hat = Hat.NONE, scarf = c(0xFF7B5CFF), mitten = c(0xFF9C6B45), ears = Ears.TUFT, earColor = c(0xFF7A5133), extras = setOf(Extra.BEAK), extraColor = c(0xFFFFB627)),
        CharacterDef("yuki", "Yuki", Rarity.EPIC, Price.coins(4500), c(0xFFF2FBFF), c(0xFFBFE7FF), hat = Hat.NONE, scarf = c(0xFF3DB2FF), mitten = c(0xFFBFE7FF), extras = setOf(Extra.HORNS, Extra.FLUFF), extraColor = c(0xFF9FB8D6)),
        CharacterDef("bolt", "Bolt", Rarity.EPIC, Price.coins(4500), c(0xFFB9C6D6), c(0xFF8193A8), hat = Hat.NONE, scarf = c(0xFFFFC83D), scarfStripe = c(0xFF1B2A4A), mitten = c(0xFF5D6E84), boots = c(0xFF5D6E84), extras = setOf(Extra.ANTENNA), extraColor = c(0xFFFF4D8D)),
        CharacterDef("nova", "Nova", Rarity.EPIC, Price.coins(5000), c(0xFFFFFFFF), c(0xFFE3E9F2), hat = Hat.HELMET, hatColor = c(0xFF5EC8FF), scarf = c(0xFFFF7A45), mitten = c(0xFFFF7A45), boots = c(0xFF8193A8)),
        CharacterDef("floe", "Captain Floe", Rarity.EPIC, Price.coins(5000), c(0xFF4A90E2), c(0xFFDDEEFF), hat = Hat.PIRATE, hatColor = c(0xFF1F1F2E), pompom = c(0xFFFFFFFF), scarf = c(0xFFE83A3A), mitten = c(0xFF1F1F2E), extras = setOf(Extra.EYE_PATCH), extraColor = c(0xFF1F1F2E)),
        CharacterDef("pudding", "Chef Pudding", Rarity.EPIC, Price.coins(5000), c(0xFFFFE0A3), c(0xFFFFF6E0), hat = Hat.CHEF, hatColor = c(0xFFFFFFFF), scarf = c(0xFFE83A3A), scarfStripe = c(0xFFFFFFFF), mitten = c(0xFFFFFFFF)),
        CharacterDef("frostking", "Frost King", Rarity.LEGENDARY, Price.gems(60), c(0xFF7FD6FF), c(0xFFE0F7FF), hat = Hat.CROWN, hatColor = c(0xFFFFC83D), pompom = c(0xFFFF4D8D), scarf = c(0xFFB0183F), scarfStripe = c(0xFFFFC83D), mitten = c(0xFFFFC83D), extras = setOf(Extra.CAPE), extraColor = c(0xFFB0183F)),
        CharacterDef("glacier", "Glacier", Rarity.LEGENDARY, Price.gems(70), c(0xFF6FE7F2), c(0xFFD7FBFF), hat = Hat.NONE, scarf = c(0xFF2E2A7A), mitten = c(0xFF2E9FB3), extras = setOf(Extra.HORNS, Extra.TAIL), extraColor = c(0xFFFFFFFF)),
        CharacterDef("sparkle", "Sparkle", Rarity.LEGENDARY, Price.gems(70), c(0xFFFFFFFF), c(0xFFFFF0FB), hat = Hat.NONE, scarf = c(0xFFB46CFF), scarfStripe = c(0xFF3DF5C8), mitten = c(0xFFFFB3E6), extras = setOf(Extra.UNICORN_HORN, Extra.MANE), extraColor = c(0xFFFF7ACB)),
        CharacterDef("flurry", "Flurry", Rarity.LEGENDARY, Price.reward(), c(0xFFFFFFFF), c(0xFFFFFFFF), hat = Hat.TOPHAT, hatColor = c(0xFF262A3B), pompom = c(0xFFFF4D8D), scarf = c(0xFFFF4D8D), mitten = c(0xFF6B4A2E), boots = c(0xFF262A3B), extras = setOf(Extra.CARROT_NOSE), extraColor = c(0xFFFF8A2B), unlockNote = "Day 7 login reward"),
    )

    val balls: List<BallDef> = listOf(
        BallDef("classic", "Classic Snow", Rarity.COMMON, Price.FREE, c(0xFFFFFFFF), c(0xFFDDEBFF), BallPattern.PLAIN),
        BallDef("frost", "Frost Glitter", Rarity.COMMON, Price.coins(500), c(0xFFE6F6FF), c(0xFF8FD8FF), BallPattern.GLITTER),
        BallDef("candy", "Candy Cane", Rarity.COMMON, Price.coins(600), c(0xFFFFFFFF), c(0xFFFF3B5C), BallPattern.STRIPES),
        BallDef("mint", "Mint Swirl", Rarity.COMMON, Price.coins(600), c(0xFFF2FFFA), c(0xFF3DF5C8), BallPattern.SWIRL),
        BallDef("polka", "Polka Pop", Rarity.COMMON, Price.coins(700), c(0xFFFFF4FA), c(0xFFFF4D8D), BallPattern.DOTS),
        BallDef("sprinkle", "Sprinkle Donut", Rarity.RARE, Price.coins(1500), c(0xFFFFB3D1), c(0xFFFFFFFF), BallPattern.SPRINKLES),
        BallDef("choco", "Choco Chip", Rarity.RARE, Price.coins(1500), c(0xFF8A5A3B), c(0xFFFFF0D6), BallPattern.SPRINKLES),
        BallDef("melon", "Watermelon", Rarity.RARE, Price.coins(1600), c(0xFF6FD66A), c(0xFF2E8B3C), BallPattern.BANDS),
        BallDef("moon", "Moon Rock", Rarity.RARE, Price.coins(1800), c(0xFFC9CED8), c(0xFF8C93A3), BallPattern.CRATERS),
        BallDef("disco", "Disco Ice", Rarity.RARE, Price.coins(2000), c(0xFFE8EEF7), c(0xFF9FB3CC), BallPattern.CHECKER, glow = 0.15f),
        BallDef("zebra", "Zebra", Rarity.RARE, Price.coins(2000), c(0xFFFFFFFF), c(0xFF1F1F2E), BallPattern.STRIPES),
        BallDef("lava", "Lava Core", Rarity.EPIC, Price.coins(3800), c(0xFF2B1B24), c(0xFFFF6A1A), BallPattern.CRACKS, glow = 0.6f),
        BallDef("galaxy", "Galaxy", Rarity.EPIC, Price.coins(4200), c(0xFF2A1F5C), c(0xFFFFFFFF), BallPattern.STARS, glow = 0.25f),
        BallDef("rainbow", "Rainbow", Rarity.EPIC, Price.coins(4200), c(0xFFFFFFFF), c(0xFFFFFFFF), BallPattern.RAINBOW),
        BallDef("emerald", "Emerald", Rarity.EPIC, Price.coins(4500), c(0xFF19C27A), c(0xFF9CFFD8), BallPattern.GLITTER, glow = 0.2f),
        BallDef("bubblegum", "Bubblegum", Rarity.EPIC, Price.coins(4500), c(0xFFFF8AD8), c(0xFFFFFFFF), BallPattern.SWIRL),
        BallDef("sunball", "Sunball", Rarity.LEGENDARY, Price.gems(50), c(0xFFFFC83D), c(0xFFFF7A1A), BallPattern.SWIRL, glow = 0.7f),
        BallDef("golden", "Golden", Rarity.LEGENDARY, Price.gems(60), c(0xFFFFD45E), c(0xFFFFF3B0), BallPattern.GLITTER, glow = 0.35f),
        BallDef("aurora", "Aurora", Rarity.LEGENDARY, Price.gems(60), c(0xFF1E1B4B), c(0xFF3DF5C8), BallPattern.BANDS, glow = 0.5f),
        BallDef("crystal", "Crystal", Rarity.LEGENDARY, Price.reward(), c(0xFFCFF4FF), c(0xFFFFFFFF), BallPattern.CHECKER, glow = 0.4f, ),
    )

    val trails: List<TrailDef> = listOf(
        TrailDef("none", "Snow Dust", Rarity.COMMON, Price.FREE, TrailShape.CIRCLE, listOf(c(0xFFFFFFFF)), rate = 8f),
        TrailDef("sparkle", "Sparkle", Rarity.COMMON, Price.coins(800), TrailShape.SPARKLE, listOf(c(0xFFFFFFFF), c(0xFFBFEFFF))),
        TrailDef("flakes", "Snowflakes", Rarity.COMMON, Price.coins(900), TrailShape.FLAKE, listOf(c(0xFFFFFFFF), c(0xFF9FDFFF))),
        TrailDef("hearts", "Hearts", Rarity.RARE, Price.coins(1600), TrailShape.HEART, listOf(c(0xFFFF4D8D), c(0xFFFF9EC4))),
        TrailDef("stars", "Starlight", Rarity.RARE, Price.coins(1800), TrailShape.STAR, listOf(c(0xFFFFC83D), c(0xFFFFF0A0))),
        TrailDef("bubbles", "Bubbles", Rarity.RARE, Price.coins(1800), TrailShape.CIRCLE, listOf(c(0xFF8FE3FF), c(0xFFDFF6FF))),
        TrailDef("confetti", "Confetti", Rarity.EPIC, Price.coins(3500), TrailShape.SPARKLE, listOf(c(0xFFFF4D8D), c(0xFF3DF5C8), c(0xFFFFC83D), c(0xFF7B5CFF)), rate = 26f),
        TrailDef("notes", "Melody", Rarity.EPIC, Price.coins(3500), TrailShape.NOTE, listOf(c(0xFF7B5CFF), c(0xFFFF4D8D), c(0xFF3DB2FF))),
        TrailDef("rainbow", "Rainbow", Rarity.EPIC, Price.coins(4000), TrailShape.CIRCLE, listOf(c(0xFFFF4D4D), c(0xFFFFA64D), c(0xFFFFE14D), c(0xFF4DFF88), c(0xFF4DB8FF), c(0xFF9A4DFF)), rate = 30f),
        TrailDef("ember", "Embers", Rarity.EPIC, Price.coins(4000), TrailShape.CIRCLE, listOf(c(0xFFFF6A1A), c(0xFFFFC83D)), rate = 24f),
        TrailDef("aurora", "Aurora", Rarity.LEGENDARY, Price.gems(45), TrailShape.SPARKLE, listOf(c(0xFF3DF5C8), c(0xFF7B5CFF), c(0xFFFF4D8D)), rate = 30f),
        TrailDef("golden", "Golden Glow", Rarity.LEGENDARY, Price.gems(50), TrailShape.STAR, listOf(c(0xFFFFD45E), c(0xFFFFFFFF)), rate = 28f),
    )

    private val charMap = characters.associateBy { it.id }
    private val ballMap = balls.associateBy { it.id }
    private val trailMap = trails.associateBy { it.id }

    fun character(id: String): CharacterDef = charMap[id] ?: characters[0]
    fun ball(id: String): BallDef = ballMap[id] ?: balls[0]
    fun trail(id: String): TrailDef = trailMap[id] ?: trails[0]

    /** Rival appearance pool (excludes reward-only items). */
    val rivalCharacters: List<CharacterDef> = characters.filter { it.price.type != PriceType.REWARD }
}
