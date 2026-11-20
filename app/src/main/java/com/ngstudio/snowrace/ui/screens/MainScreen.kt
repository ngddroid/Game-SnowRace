package com.ngstudio.snowrace.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.snowrace.R
import com.ngstudio.snowrace.analytics.Telemetry
import com.ngstudio.snowrace.data.BoosterIds
import com.ngstudio.snowrace.data.Catalog
import com.ngstudio.snowrace.data.GameRules
import com.ngstudio.snowrace.data.SaveData
import com.ngstudio.snowrace.data.TimeUtil
import com.ngstudio.snowrace.level.BossType
import com.ngstudio.snowrace.level.LevelRepository
import com.ngstudio.snowrace.ui.LocalNav
import com.ngstudio.snowrace.ui.Screen
import com.ngstudio.snowrace.ui.Tab
import com.ngstudio.snowrace.ui.components.BodyText
import com.ngstudio.snowrace.ui.components.Buttons
import com.ngstudio.snowrace.ui.components.CurrencyPill
import com.ngstudio.snowrace.ui.components.GameButton
import com.ngstudio.snowrace.ui.components.LocalContainer
import com.ngstudio.snowrace.ui.components.OutlinedText
import com.ngstudio.snowrace.ui.components.RoundIconButton
import com.ngstudio.snowrace.ui.components.UiIcon
import com.ngstudio.snowrace.ui.components.pressable
import com.ngstudio.snowrace.ui.theme.Palette
import com.ngstudio.snowrace.ui.theme.Type

@Composable
fun MainScreen() {
    val nav = LocalNav.current
    val c = LocalContainer.current
    val save by c.save.state.collectAsState()
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = nav.tab,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
            label = "tabs",
            modifier = Modifier.fillMaxSize(),
        ) { tab ->
            when (tab) {
                Tab.HOME -> HomeTab(save)
                Tab.LEVELS -> LevelsTab(save)
                Tab.SHOP -> ShopTab(save)
                Tab.REWARDS -> RewardsTab(save)
                Tab.PROFILE -> ProfileTab(save)
            }
        }
        BottomBar(save, Modifier.align(Alignment.BottomCenter))
    }
}

// ================================================================================== top bar
@Composable
fun TopBar(save: SaveData, modifier: Modifier = Modifier, showGear: Boolean = true) {
    val nav = LocalNav.current
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.pressable { nav.tab = Tab.PROFILE }.clip(RoundedCornerShape(24.dp)).background(Color(0x66141235)).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(save.profile.character, 44.dp)
            Spacer(Modifier.width(6.dp))
            Column {
                OutlinedText(save.profile.name, style = Type.Label.copy(fontSize = 14.sp), outlineWidth = 3.dp, align = TextAlign.Start)
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Palette.Berry).padding(horizontal = 6.dp)) {
                    BodyText("LV ${save.progress.highestUnlocked}", color = Color.White, style = Type.Small.copy(fontSize = 11.sp))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        CurrencyPill(R.drawable.ui_coin, save.economy.coins, onPlus = { nav.tab = Tab.REWARDS })
        Spacer(Modifier.width(4.dp))
        CurrencyPill(R.drawable.ui_gem, save.economy.gems)
        if (showGear) {
            Spacer(Modifier.width(2.dp))
            Box(Modifier.size(44.dp).pressable { nav.push(Screen.Settings) }, contentAlignment = Alignment.Center) { UiIcon(R.drawable.ui_gear, 40.dp) }
        }
    }
}

@Composable
fun Avatar(characterId: String, size: androidx.compose.ui.unit.Dp) {
    val def = Catalog.character(characterId)
    val kid = com.ngstudio.snowrace.game.render.CharacterRig.kidLook(def)
    val skin = Color(0xFF000000 or (kid?.first ?: def.body).toLong())
    val hair = kid?.let { Color(0xFF000000 or it.second.toLong()) }
    val suit = Color(0xFF000000 or def.body.toLong())
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFFBFE9FF), Color(0xFF7FC4FF)))).border(3.dp, Palette.Navy, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.toPx()
            // snowsuit shoulders
            drawOval(suit, topLeft = Offset(w * 0.12f, w * 0.78f), size = Size(w * 0.76f, w * 0.5f))
            // scarf
            drawRoundRect(Color(0xFF000000 or def.scarf.toLong()), topLeft = Offset(w * 0.22f, w * 0.72f), size = Size(w * 0.56f, w * 0.12f), cornerRadius = CornerRadius(w * 0.06f))
            // hair behind the face
            if (hair != null) drawOval(hair, topLeft = Offset(w * 0.14f, w * 0.2f), size = Size(w * 0.72f, w * 0.62f))
            // face
            drawOval(skin, topLeft = Offset(w * 0.2f, w * 0.24f), size = Size(w * 0.6f, w * 0.54f))
            val eyeY = w * 0.52f
            for (sx in listOf(-1f, 1f)) {
                drawOval(Color.White, topLeft = Offset(w / 2 + sx * w * 0.12f - w * 0.065f, eyeY - w * 0.08f), size = Size(w * 0.13f, w * 0.16f))
                drawOval(Color(0xFF1B2A4A), topLeft = Offset(w / 2 + sx * w * 0.12f - w * 0.045f, eyeY - w * 0.05f), size = Size(w * 0.09f, w * 0.11f))
                drawCircle(Color.White, radius = w * 0.02f, center = Offset(w / 2 + sx * w * 0.12f + w * 0.015f, eyeY - w * 0.025f))
                drawOval(Color(0x88FF8FA8), topLeft = Offset(w / 2 + sx * w * 0.2f - w * 0.05f, w * 0.6f), size = Size(w * 0.1f, w * 0.06f))
            }
            if (com.ngstudio.snowrace.data.Extra.BEARD in def.extras) {
                val beard = Color(0xFF000000 or def.extraColor.toLong())
                drawOval(beard, topLeft = Offset(w * 0.27f, w * 0.64f), size = Size(w * 0.46f, w * 0.26f))
                for (sx in listOf(-1f, 1f)) drawOval(beard, topLeft = Offset(w / 2 + sx * w * 0.07f - w * 0.075f, w * 0.6f), size = Size(w * 0.15f, w * 0.06f))
            }
            drawOval(Color(0xFF6B1E3A), topLeft = Offset(w * 0.46f, w * 0.66f), size = Size(w * 0.08f, w * 0.045f))
            // hat (beanie / other hats share the hat colour)
            if (def.hat == com.ngstudio.snowrace.data.Hat.SANTA) {
                val red = Color(0xFF000000 or def.hatColor.toLong())
                val tip = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.3f, w * 0.14f); lineTo(w * 0.86f, w * 0.06f); lineTo(w * 0.78f, w * 0.3f); close()
                }
                drawOval(red, topLeft = Offset(w * 0.16f, w * 0.04f), size = Size(w * 0.68f, w * 0.4f))
                drawPath(tip, red)
                drawRoundRect(Color(0xFF000000 or def.hatTrim.toLong()), topLeft = Offset(w * 0.15f, w * 0.29f), size = Size(w * 0.7f, w * 0.13f), cornerRadius = CornerRadius(w * 0.065f))
                drawCircle(Color(0xFF000000 or def.pompom.toLong()), radius = w * 0.07f, center = Offset(w * 0.86f, w * 0.08f))
            } else if (def.hat != com.ngstudio.snowrace.data.Hat.NONE) {
                drawOval(Color(0xFF000000 or def.hatColor.toLong()), topLeft = Offset(w * 0.16f, w * 0.02f), size = Size(w * 0.68f, w * 0.42f))
                drawRoundRect(Color(0xFF000000 or def.hatTrim.toLong()), topLeft = Offset(w * 0.17f, w * 0.3f), size = Size(w * 0.66f, w * 0.1f), cornerRadius = CornerRadius(w * 0.05f))
            } else if (def.ears != com.ngstudio.snowrace.data.Ears.NONE) {
                for (sx in listOf(-1f, 1f)) drawCircle(Color(0xFF000000 or def.earColor.toLong()), radius = w * 0.1f, center = Offset(w / 2 + sx * w * 0.24f, w * 0.26f))
            }
        }
    }
}

// ================================================================================== bottom bar
@Composable
fun BottomBar(save: SaveData, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val today = TimeUtil.today()
    val rewardsBadge = (if (GameRules.loginClaimable(save, today)) 1 else 0) + (if (GameRules.wheelFree(save, today)) 1 else 0) + GameRules.missionsReady(save) + (if (GameRules.milestoneReady(save) != null) 1 else 0)
    val profileBadge = GameRules.achievementsReady(save)
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp).height(78.dp)) {
        Canvas(Modifier.fillMaxWidth().height(66.dp).align(Alignment.BottomCenter)) {
            val r = CornerRadius(28.dp.toPx())
            drawRoundRect(Color(0x66000000), topLeft = Offset(0f, 5f), size = size, cornerRadius = r)
            drawRoundRect(Palette.Navy, size = size, cornerRadius = r)
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF3B37A0), Color(0xFF26236A))), topLeft = Offset(4f, 4f), size = Size(size.width - 8f, size.height - 8f), cornerRadius = r)
            drawRoundRect(Color(0x22FFFFFF), topLeft = Offset(16f, 8f), size = Size(size.width - 32f, size.height * 0.3f), cornerRadius = CornerRadius(20f))
        }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
            BarItem(Tab.SHOP, R.drawable.ui_shop, "Shop", 0)
            BarItem(Tab.REWARDS, R.drawable.ui_gift, "Rewards", rewardsBadge)
            BarItem(Tab.HOME, R.drawable.ui_home, "Home", 0, center = true)
            BarItem(Tab.LEVELS, R.drawable.ui_map, "Levels", 0)
            BarItem(Tab.PROFILE, R.drawable.ui_profile, "Profile", profileBadge)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BarItem(tab: Tab, icon: Int, label: String, badge: Int, center: Boolean = false) {
    val nav = LocalNav.current
    val selected = nav.tab == tab
    val lift by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "bar")
    Box(Modifier.weight(1f).fillMaxSize().pressable { nav.tab = tab }, contentAlignment = Alignment.BottomCenter) {
        if (center) {
            Box(
                Modifier.offset(y = (-10).dp - 6.dp * lift).size(70.dp).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF7CF06A), Color(0xFF2FC25B))))
                    .border(4.dp, Palette.Navy, CircleShape),
                contentAlignment = Alignment.Center,
            ) { UiIcon(icon, 50.dp) }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-6).dp - 8.dp * lift)) {
                Box {
                    UiIcon(icon, 40.dp + 8.dp * lift)
                    if (badge > 0) com.ngstudio.snowrace.ui.components.Badge(badge, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp))
                }
                OutlinedText(label, style = Type.Small.copy(fontSize = 11.sp), outlineWidth = 3.dp, color = if (selected) Palette.Gold else Color.White)
            }
        }
    }
}

// ================================================================================== home tab
@Composable
fun HomeTab(save: SaveData) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    val level = save.progress.highestUnlocked
    val spec = remember(level) { LevelRepository.get(level) }
    val today = TimeUtil.today()
    LaunchedEffect(Unit) {
        c.updater.check() // once per launch; never during a race
        Telemetry.userProps(save.progress.highestUnlocked, listOf("auto", "low", "medium", "high").getOrElse(save.settings.quality) { "auto" })
    }
    Box(Modifier.fillMaxSize()) {
        // touch layer for the 3D hero (tap = wave, drag = rotate)
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { c.stage.wave(); c.audio.play(com.ngstudio.snowrace.audio.Sfx.POP, 0.6f, 1.2f) } }
                .pointerInput(Unit) { detectDragGestures { _, d -> c.stage.drag(d.x * 0.012f) } },
        )
        Box(Modifier.fillMaxWidth().height(170.dp).background(Brush.verticalGradient(listOf(Color(0x991E1B4B), Color.Transparent))))
        Box(Modifier.fillMaxWidth().height(330.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC1E1B4B)))))
        Column(Modifier.fillMaxSize()) {
            TopBar(save)
            val inf = rememberInfiniteTransition(label = "logo")
            val bob by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "lb")
            UiIcon(R.drawable.logo_frosty, 190.dp, Modifier.align(Alignment.CenterHorizontally).graphicsLayer { translationY = bob * 5f; rotationZ = bob * 1.2f })
            Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    RoundIconButton(R.drawable.ui_calendar, { nav.rewardsFocus = 0; nav.tab = Tab.REWARDS }, badge = if (GameRules.loginClaimable(save, today)) 1 else 0, label = "Daily")
                    RoundIconButton(R.drawable.ui_wheel, { nav.rewardsFocus = 1; nav.tab = Tab.REWARDS }, badge = if (GameRules.wheelFree(save, today)) 1 else 0, label = "Spin")
                    RoundIconButton(R.drawable.ui_mission, { nav.rewardsFocus = 2; nav.tab = Tab.REWARDS }, badge = GameRules.missionsReady(save), label = "Missions")
                }
                Spacer(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    RoundIconButton(R.drawable.ui_shop, { nav.tab = Tab.SHOP }, label = "Shop")
                    RoundIconButton(R.drawable.ui_trophy, { nav.tab = Tab.PROFILE }, badge = GameRules.achievementsReady(save), label = "Trophies")
                    val milestone = GameRules.milestoneReady(save)
                    if (milestone != null) RoundIconButton(R.drawable.ui_chest, { nav.rewardsFocus = 3; nav.tab = Tab.REWARDS }, badge = 1, label = "Chest")
                }
            }
            UpdateReadyBanner(Modifier.fillMaxWidth())
            LevelCard(level, spec.name, spec.biome.title, spec.stages.size, spec.boss, spec.newFeature)
            Spacer(Modifier.height(8.dp))
            BoosterRow(save)
            Spacer(Modifier.height(10.dp))
            GameButton(
                "PLAY", onClick = { nav.startRace(level, nav.boosters) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 46.dp), height = 66.dp,
                colors = Buttons.Green, icon = R.drawable.ui_play, shine = true,
                textStyle = Type.Title,
            )
            Spacer(Modifier.height(98.dp))
        }
    }
}

@Composable
private fun LevelCard(level: Int, name: String, biome: String, stages: Int, boss: BossType, feature: String?) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (boss != BossType.NONE) {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Brush.horizontalGradient(listOf(Color(0xFFFF3B5C), Palette.Coral))).border(2.dp, Palette.Navy, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 2.dp)) {
                OutlinedText(if (boss == BossType.AVALANCHE) "BOSS: AVALANCHE" else "BOSS: MEGA BALL", style = Type.Small, outlineWidth = 2.dp)
            }
            Spacer(Modifier.height(4.dp))
        }
        OutlinedText("LEVEL $level", style = Type.Title.copy(fontSize = 34.sp), brush = Brush.verticalGradient(listOf(Color.White, Palette.Ice)))
        OutlinedText("$name · $biome", style = Type.Label, outlineWidth = 3.dp, color = Palette.Ice)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until stages) {
                Box(Modifier.padding(horizontal = 3.dp).size(14.dp).clip(CircleShape).background(Palette.Mint).border(2.dp, Palette.Navy, CircleShape))
                if (i < stages - 1) Box(Modifier.width(14.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xAAFFFFFF)))
            }
            Spacer(Modifier.width(6.dp))
            UiIcon(R.drawable.ui_flag, 22.dp)
        }
        if (feature != null) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xCC1B2A4A)).border(2.dp, Palette.Gold, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                BodyText("✨ $feature", color = Palette.Gold, style = Type.Small)
            }
        }
    }
}

private data class BoosterInfo(val id: String, val icon: Int, val name: String)

private val boosterInfos = listOf(
    BoosterInfo(BoosterIds.BIG_START, R.drawable.ui_snowball, "Big Start"),
    BoosterInfo(BoosterIds.TURBO_START, R.drawable.ui_boost, "Turbo"),
    BoosterInfo(BoosterIds.SHIELD_START, R.drawable.ui_shield, "Shield"),
)

@Composable
private fun BoosterRow(save: SaveData) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (b in boosterInfos) {
            val count = save.economy.boosters[b.id] ?: 0
            val selected = b.id in nav.boosters
            val pulse = rememberInfiniteTransition(label = "bp").animateFloat(1f, 1.06f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "bps").value
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.pressable {
                if (selected) nav.boosters = nav.boosters - b.id
                else if (count > 0) nav.boosters = nav.boosters + b.id
                else {
                    val updated = c.save.data.let { s -> if (s.economy.coins >= GameRules.BOOSTER_PRICE) s else null }
                    if (updated != null) {
                        c.save.update { s ->
                            val m = s.economy.boosters.toMutableMap(); m[b.id] = (m[b.id] ?: 0) + 1
                            s.copy(economy = s.economy.copy(coins = s.economy.coins - GameRules.BOOSTER_PRICE, boosters = m))
                        }
                        nav.boosters = nav.boosters + b.id
                        c.audio.play(com.ngstudio.snowrace.audio.Sfx.COIN)
                        nav.showToast("${b.name} ready for the next race!")
                    } else {
                        c.audio.play(com.ngstudio.snowrace.audio.Sfx.ERROR)
                        nav.showToast("Need ${GameRules.BOOSTER_PRICE} coins")
                    }
                }
            }) {
                Box(
                    Modifier.size(58.dp).scale(if (selected) pulse else 1f).clip(RoundedCornerShape(18.dp))
                        .background(if (selected) Brush.verticalGradient(listOf(Color(0xFF7DFBDC), Palette.MintDeep)) else Brush.verticalGradient(listOf(Color(0xCC3B37A0), Color(0xCC26236A))))
                        .border(3.dp, if (selected) Color.White else Palette.Navy, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    UiIcon(b.icon, 42.dp)
                    Box(Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp).clip(RoundedCornerShape(8.dp)).background(if (count > 0) Palette.Berry else Palette.Gold).border(2.dp, Palette.Navy, RoundedCornerShape(8.dp)).padding(horizontal = 5.dp)) {
                        BodyText(if (count > 0) "x$count" else "${GameRules.BOOSTER_PRICE}", color = if (count > 0) Color.White else Palette.Navy, style = Type.Small.copy(fontSize = 11.sp))
                    }
                    if (selected) Box(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp)) { UiIcon(R.drawable.ui_check, 22.dp) }
                }
                OutlinedText(b.name, style = Type.Small, outlineWidth = 3.dp)
            }
        }
    }
}
