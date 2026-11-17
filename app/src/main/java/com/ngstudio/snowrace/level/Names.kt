package com.ngstudio.snowrace.level

object Names {
    val rivals = listOf(
        "Milo", "Juno", "Kiki", "Bramble", "Nori", "Pebble", "Tofu", "Ziggy", "Luna", "Biscuit",
        "Clover", "Puddle", "Waffles", "Nugget", "Maple", "Echo", "Pixel", "Sprocket", "Willow", "Cosmo",
        "Taffy", "Rumi", "Olive", "Fizz", "Pogo", "Saffi", "Toast", "Bean", "Comet", "Skye",
        "Flick", "Mango", "Peppa", "Rocco", "Sushi", "Tiki", "Velvet", "Wren", "Yoyo", "Zuzu",
        "Button", "Cinder", "Dumpling", "Ember", "Frodo", "Gizmo", "Honey", "Iggy", "Jelly", "Kobo",
        "Lumi", "Momo", "Noodle", "Opal", "Pippin", "Quill", "Radish", "Scout", "Truffle", "Umi",
    )

    private val adjectives = listOf(
        "Frosty", "Sparkly", "Snowy", "Chilly", "Breezy", "Glittering", "Whispering", "Crystal", "Misty", "Twinkling",
        "Fluffy", "Shimmering", "Icy", "Polar", "Starlit", "Moonlit", "Sunny", "Silent", "Merry", "Wobbly",
        "Bouncy", "Cozy", "Giant", "Tiny", "Swirly", "Dizzy", "Speedy", "Jolly", "Rumbling", "Glowing",
    )
    private val nouns = listOf(
        "Slopes", "Ridge", "Hollow", "Peaks", "Meadow", "Pass", "Summit", "Valley", "Drifts", "Crossing",
        "Banks", "Trail", "Canyon", "Glade", "Cove", "Shore", "Plateau", "Steps", "Hills", "Basin",
        "Lagoon", "Point", "Run", "Dunes", "Terrace", "Gorge", "Bay", "Heights", "Bend", "Fields",
    )

    fun levelName(seed: Long, biome: Biome, boss: BossType): String {
        if (boss == BossType.AVALANCHE) return "Avalanche Run"
        if (boss == BossType.MEGA_BALL) return "Mega Ball Mayhem"
        val a = adjectives[((seed ushr 7) % adjectives.size).toInt().let { if (it < 0) -it else it }]
        val n = nouns[((seed ushr 17) % nouns.size).toInt().let { if (it < 0) -it else it }]
        return "$a $n"
    }
}
