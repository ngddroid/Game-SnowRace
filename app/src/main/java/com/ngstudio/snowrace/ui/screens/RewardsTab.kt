package com.ngstudio.snowrace.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.snowrace.R
import com.ngstudio.snowrace.audio.Haptic
import com.ngstudio.snowrace.audio.Sfx
import com.ngstudio.snowrace.data.GameRules
import com.ngstudio.snowrace.data.SaveData
import com.ngstudio.snowrace.data.TimeUtil
import com.ngstudio.snowrace.ui.LocalNav
import com.ngstudio.snowrace.ui.components.BodyText
import com.ngstudio.snowrace.ui.components.Buttons
import com.ngstudio.snowrace.ui.components.GameBackground
import com.ngstudio.snowrace.ui.components.GameButton
import com.ngstudio.snowrace.ui.components.GameProgressBar
import com.ngstudio.snowrace.ui.components.LocalContainer
import com.ngstudio.snowrace.ui.components.OutlinedText
import com.ngstudio.snowrace.ui.components.Panel
import com.ngstudio.snowrace.ui.components.UiIcon
import com.ngstudio.snowrace.ui.components.pressable
import com.ngstudio.snowrace.ui.theme.Palette
import com.ngstudio.snowrace.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

@Composable
fun RewardsTab(save: SaveData) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    val today = TimeUtil.today()
    val scroll = rememberScrollState()
    val offsets = remember { FloatArray(4) }
    LaunchedEffect(Unit) {
        c.save.update { GameRules.refreshMissions(it, TimeUtil.today()) }
        delay(120)
        val target = offsets[nav.rewardsFocus.coerceIn(0, 3)].toInt()
        if (target > 0) scroll.animateScrollTo(target)
    }
    GameBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(save)
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 12.dp)) {
                GameRules.milestoneReady(save)?.let { m ->
                    Box(Modifier.onGloballyPositioned { offsets[3] = it.positionInParent().y }) { MilestoneCard(m) }
                    Spacer(Modifier.height(14.dp))
                }
                Box(Modifier.onGloballyPositioned { offsets[0] = it.positionInParent().y }) { DailyCalendar(save, today) }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.onGloballyPositioned { offsets[1] = it.positionInParent().y }) { LuckyWheel(save, today) }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.onGloballyPositioned { offsets[2] = it.positionInParent().y }) { Missions(save) }
                Spacer(Modifier.height(110.dp))
            }
        }
    }
}

private fun androidx.compose.ui.layout.LayoutCoordinates.positionInParent(): Offset =
    parentLayoutCoordinates?.localPositionOf(this, Offset.Zero) ?: Offset.Zero

@Composable
private fun SectionTitle(icon: Int, title: String, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        UiIcon(icon, 36.dp)
        Spacer(Modifier.width(6.dp))
        OutlinedText(title, style = Type.Heading, outlineWidth = 3.dp, color = Palette.Navy, outline = Color.White)
        Spacer(Modifier.weight(1f))
        if (trailing != null) BodyText(trailing, color = Palette.Muted, style = Type.Small)
    }
}

// ------------------------------------------------------------------ daily login
@Composable
private fun DailyCalendar(save: SaveData, today: Long) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    val claimable = GameRules.loginClaimable(save, today)
    val idx = GameRules.loginDisplayIndex(save, today)
    Panel(Modifier.fillMaxWidth()) {
        SectionTitle(R.drawable.ui_calendar, "Daily Gifts", if (claimable) "Ready!" else "Next in ${TimeUtil.formatCountdown(TimeUtil.msUntilTomorrow())}")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in 0 until 6) DayTile(d, save, today, idx, claimable, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        DayTile(6, save, today, idx, claimable, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        GameButton(if (claimable) "Claim Day ${idx + 1}" else "Come back tomorrow", onClick = {
            val r = GameRules.claimLogin(c.save.data, today) ?: return@GameButton
            c.save.update { r.first }
            c.audio.play(Sfx.CHEST); c.haptics.play(Haptic.SUCCESS)
            val rw = r.second
            nav.showToast(when { rw.item != null -> "Unlocked Flurry the Snowman!"; rw.gems > 0 -> "+${rw.gems} gems!"; else -> "+${rw.coins} coins!" })
        }, enabled = claimable, modifier = Modifier.fillMaxWidth(), colors = Buttons.Green, height = 52.dp, shine = claimable)
    }
}

@Composable
private fun DayTile(d: Int, save: SaveData, today: Long, idx: Int, claimable: Boolean, modifier: Modifier) {
    val reward = GameRules.loginRewards[d]
    val claimed = if (claimable) d < idx else d <= idx
    val isToday = claimable && d == idx
    val pulse = if (isToday) rememberInfiniteTransition(label = "day").animateFloat(1f, 1.07f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "dp").value else 1f
    Box(
        modifier.height(if (d == 6) 70.dp else 78.dp).scale(pulse).clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    claimed -> Brush.verticalGradient(listOf(Color(0xFFD7E1EE), Color(0xFFB0BED3)))
                    isToday -> Brush.verticalGradient(listOf(Color(0xFFFFE27A), Palette.Gold))
                    d == 6 -> Brush.verticalGradient(listOf(Color(0xFFD4A6FF), Palette.Purple))
                    else -> Brush.verticalGradient(listOf(Color(0xFF9BDDFF), Palette.Sky))
                }
            )
            .border(2.5.dp, Palette.Navy, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (d == 6) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar("flurry", 46.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    OutlinedText("Day 7", style = Type.Label, outlineWidth = 3.dp, align = TextAlign.Start)
                    OutlinedText("Flurry the Snowman", style = Type.Small, outlineWidth = 2.dp, color = Palette.Gold, align = TextAlign.Start)
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedText("Day ${d + 1}", style = Type.Small.copy(fontSize = 11.sp), outlineWidth = 2.dp)
                UiIcon(if (reward.gems > 0) R.drawable.ui_gem else R.drawable.ui_coin, 28.dp)
                OutlinedText(reward.label, style = Type.Small, outlineWidth = 2.dp)
            }
        }
        if (claimed) Box(Modifier.align(Alignment.TopEnd).padding(2.dp)) { UiIcon(R.drawable.ui_check, 20.dp) }
    }
}

// ------------------------------------------------------------------ lucky wheel
@Composable
private fun LuckyWheel(save: SaveData, today: Long) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val rot = remember { Animatable(0f) }
    var spinning by remember { mutableStateOf(false) }
    val free = GameRules.wheelFree(save, today)
    val adLeft = GameRules.wheelAdSpinsLeft(save, today)
    val slices = GameRules.wheel
    val n = slices.size
    var lastTick by remember { mutableFloatStateOf(0f) }

    fun spin(viaAd: Boolean) {
        if (spinning) return
        spinning = true
        val index = GameRules.pickWheel(System.nanoTime() / 1000)
        val sliceAngle = 360f / n
        val center = index * sliceAngle + sliceAngle / 2f
        val base = rot.value - (rot.value % 360f)
        val target = base + 360f * 6 + (360f - center)
        scope.launch {
            rot.animateTo(target, tween(4200, easing = CubicBezierEasing(0.12f, 0.75f, 0.2f, 1f))) {
                val slice = floor(value / sliceAngle)
                if (slice != lastTick) { lastTick = slice; c.audio.play(Sfx.WHEEL_TICK, 0.6f, 1.1f); c.haptics.play(Haptic.TICK) }
            }
            c.save.update { GameRules.applyWheel(it, index, today, viaAd) }
            c.audio.play(Sfx.WHEEL_WIN); c.haptics.play(Haptic.SUCCESS)
            val s = slices[index]
            nav.showToast(when { s.booster != null -> "Won a ${if (s.booster.contains("big")) "Big Start" else "Turbo"} booster!"; s.gems > 0 -> "Won ${s.gems} gems!"; else -> "Won ${s.coins} coins!" })
            spinning = false
        }
    }

    Panel(Modifier.fillMaxWidth()) {
        SectionTitle(R.drawable.ui_wheel, "Lucky Wheel", if (free) "Free spin ready!" else "Free spin in ${TimeUtil.formatCountdown(TimeUtil.msUntilTomorrow())}")
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1.15f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth(0.82f).aspectRatio(1f).rotate(rot.value)) {
                val r = size.minDimension / 2
                val sweep = 360f / n
                drawCircle(Palette.Navy, r)
                for (i in 0 until n) {
                    drawArc(Color(slices[i].color), startAngle = -90f + i * sweep, sweepAngle = sweep, useCenter = true, topLeft = Offset(8f, 8f), size = Size(size.width - 16f, size.height - 16f))
                    drawArc(Color(0x22FFFFFF), startAngle = -90f + i * sweep, sweepAngle = sweep / 2, useCenter = true, topLeft = Offset(8f, 8f), size = Size(size.width - 16f, size.height - 16f))
                }
                for (i in 0 until n) {
                    val a = Math.toRadians((-90f + i * sweep).toDouble())
                    drawLine(Palette.Navy, center, center + Offset((cos(a) * (r - 8f)).toFloat(), (sin(a) * (r - 8f)).toFloat()), strokeWidth = 5f)
                }
                for (k in 0 until n * 2) {
                    val a = Math.toRadians((k * 180.0 / n))
                    drawCircle(if (k % 2 == 0) Color.White else Palette.Gold, 7f, center + Offset((cos(a) * (r - 4f)).toFloat(), (sin(a) * (r - 4f)).toFloat()))
                }
                drawCircle(Palette.Navy, r, style = Stroke(8f))
            }
            // labels rotate with the wheel
            Box(Modifier.fillMaxWidth(0.82f).aspectRatio(1f).rotate(rot.value)) {
                for (i in 0 until n) {
                    val s = slices[i]
                    val ang = (i + 0.5f) * (360f / n)
                    Box(Modifier.fillMaxSize().rotate(ang), contentAlignment = Alignment.TopCenter) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 18.dp)) {
                            UiIcon(when { s.booster != null -> if (s.booster.contains("big")) R.drawable.ui_snowball else R.drawable.ui_boost; s.gems > 0 -> R.drawable.ui_gem; else -> R.drawable.ui_coin }, 30.dp)
                            OutlinedText(s.label, style = Type.Small, outlineWidth = 2.dp)
                        }
                    }
                }
            }
            // hub and pointer
            Box(Modifier.size(64.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFFFFE27A), Palette.GoldDeep))).border(4.dp, Palette.Navy, CircleShape), contentAlignment = Alignment.Center) {
                UiIcon(R.drawable.ui_snowflake, 40.dp)
            }
            Canvas(Modifier.align(Alignment.TopCenter).size(44.dp, 48.dp).offset(y = 2.dp)) {
                val p = Path().apply { moveTo(size.width / 2, size.height); lineTo(0f, 0f); lineTo(size.width, 0f); close() }
                drawPath(p, Palette.Berry)
                drawPath(p, Palette.Navy, style = Stroke(6f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameButton("Free Spin", onClick = { spin(false) }, enabled = free && !spinning, modifier = Modifier.weight(1f), colors = Buttons.Berry, height = 52.dp, shine = free)
            if (c.ads.rewardedAvailable) {
                GameButton("x$adLeft", onClick = {
                    c.ads.showRewarded("wheel_spin") { ok -> if (ok) spin(true) }
                }, enabled = !free && adLeft > 0 && !spinning, modifier = Modifier.weight(1f), colors = Buttons.Gold, height = 52.dp, icon = R.drawable.ui_ad)
            }
        }
    }
}

// ------------------------------------------------------------------ missions
@Composable
private fun Missions(save: SaveData) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    Panel(Modifier.fillMaxWidth()) {
        SectionTitle(R.drawable.ui_mission, "Daily Missions", "Resets in ${TimeUtil.formatCountdown(TimeUtil.msUntilTomorrow() + tick * 0)}")
        Spacer(Modifier.height(10.dp))
        save.rewards.missions.forEachIndexed { i, m ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFE8F3FF)).border(2.dp, Palette.PanelEdge, RoundedCornerShape(16.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    BodyText(GameRules.missionTitle(m), style = Type.Body)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GameProgressBar(m.progress / m.target.toFloat(), Modifier.weight(1f), height = 14.dp)
                        Spacer(Modifier.width(6.dp))
                        BodyText("${m.progress}/${m.target}", style = Type.Small, color = Palette.Muted)
                    }
                }
                Spacer(Modifier.width(8.dp))
                val done = m.progress >= m.target
                when {
                    m.claimed -> UiIcon(R.drawable.ui_check, 40.dp)
                    else -> GameButton("${m.reward}", onClick = {
                        val r = GameRules.claimMission(c.save.data, i) ?: return@GameButton
                        c.save.update { r }
                        c.audio.play(Sfx.COIN); c.haptics.play(Haptic.SUCCESS)
                        nav.showToast("+${m.reward} coins" + if (m.rewardGems > 0) ", +${m.rewardGems} gems" else "")
                    }, enabled = done, modifier = Modifier.width(96.dp), height = 44.dp, colors = Buttons.Gold, icon = R.drawable.ui_coin, textStyle = Type.Label)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ milestone chest
@Composable
private fun MilestoneCard(m: Int) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    val (coins, gems) = GameRules.milestoneReward(m)
    val wob = rememberInfiniteTransition(label = "chest").animateFloat(-8f, 8f, infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "cw").value
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UiIcon(R.drawable.ui_chest, 72.dp, Modifier.rotate(wob))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                OutlinedText("Milestone Chest!", style = Type.Heading, outlineWidth = 3.dp, align = TextAlign.Start)
                BodyText("You cleared $m levels. $coins coins + $gems gems inside!", style = Type.BodySmall)
            }
        }
        Spacer(Modifier.height(10.dp))
        GameButton("Open Chest", onClick = {
            c.save.update { GameRules.claimMilestone(it, m) }
            c.audio.play(Sfx.CHEST); c.haptics.play(Haptic.SUCCESS)
            nav.showToast("+$coins coins, +$gems gems!")
        }, modifier = Modifier.fillMaxWidth(), colors = Buttons.Purple, height = 52.dp, shine = true)
    }
}
