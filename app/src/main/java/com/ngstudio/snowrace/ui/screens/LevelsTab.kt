package com.ngstudio.snowrace.ui.screens

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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.snowrace.R
import com.ngstudio.snowrace.ads.AdConfig
import com.ngstudio.snowrace.audio.Sfx
import com.ngstudio.snowrace.data.MAX_LEVEL
import com.ngstudio.snowrace.data.SaveData
import com.ngstudio.snowrace.level.Biome
import com.ngstudio.snowrace.level.LevelGenerator
import com.ngstudio.snowrace.ui.LocalNav
import com.ngstudio.snowrace.ui.components.BodyText
import com.ngstudio.snowrace.ui.components.Buttons
import com.ngstudio.snowrace.ui.components.GameBackground
import com.ngstudio.snowrace.ui.components.GameButton
import com.ngstudio.snowrace.ui.components.GameDialog
import com.ngstudio.snowrace.ui.components.LocalContainer
import com.ngstudio.snowrace.ui.components.OutlinedText
import com.ngstudio.snowrace.ui.components.UiIcon
import com.ngstudio.snowrace.ui.components.pressable
import com.ngstudio.snowrace.ui.theme.Palette
import com.ngstudio.snowrace.ui.theme.Type
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sin

private const val BLOCK = 25
private const val ROW_H = 92

private fun indexOfLevel(level: Int): Int {
    val b = (level - 1) / BLOCK
    return b * (BLOCK + 1) + 1 + (level - 1) % BLOCK
}

private fun nodeX(level: Int): Float = sin(level * 0.85f) * 0.3f

@Composable
fun LevelsTab(save: SaveData) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    val current = save.progress.highestUnlocked
    val state = rememberLazyListState(initialFirstVisibleItemIndex = max(0, indexOfLevel(current) - 2))
    val scope = rememberCoroutineScope()
    var locked by remember { mutableStateOf<Int?>(null) }
    val blocks = (MAX_LEVEL + BLOCK - 1) / BLOCK

    GameBackground {
        LazyColumn(state = state, reverseLayout = true, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 120.dp, bottom = 120.dp)) {
            for (b in 0 until blocks) {
                item(key = "h$b") { BiomeHeader(b) }
                val first = b * BLOCK + 1
                val last = minOf(MAX_LEVEL, first + BLOCK - 1)
                items(count = last - first + 1, key = { "l${first + it}" }) { k ->
                    val level = first + k
                    LevelRow(level, save, onTap = {
                        if (save.progress.isUnlocked(level)) nav.startRace(level, emptySet())
                        else { c.audio.play(Sfx.UI_OPEN); locked = level }
                    })
                }
            }
        }
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xF21E1B4B), Color(0xB31E1B4B), Color.Transparent)))) {
                Column {
                    TopBar(save)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedText("World Map", style = Type.Title, outlineWidth = 4.dp)
                        Spacer(Modifier.weight(1f))
                        UiIcon(R.drawable.ui_star, 28.dp)
                        OutlinedText(" ${save.progress.totalStars()}", style = Type.Heading, outlineWidth = 3.dp)
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 108.dp)) {
            Column(
                Modifier.pressable { scope.launch { state.animateScrollToItem(max(0, indexOfLevel(current) - 2)) } }
                    .clip(RoundedCornerShape(18.dp)).background(Palette.Berry).border(3.dp, Palette.Navy, RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UiIcon(R.drawable.ui_flag, 26.dp)
                OutlinedText("Current", style = Type.Small, outlineWidth = 2.dp)
            }
        }
    }

    locked?.let { lv ->
        LockedLevelDialog(lv, save.progress.highestUnlocked, onClose = { locked = null }, onUnlocked = {
            locked = null
            c.save.update { s -> s.copy(progress = s.progress.copy(extraUnlocked = s.progress.extraUnlocked + lv)) }
            c.audio.play(Sfx.UNLOCK)
            nav.showToast("Level $lv unlocked!")
        }, onPlayCurrent = {
            locked = null
            nav.startRace(save.progress.highestUnlocked, emptySet())
        })
    }
}

@Composable
private fun BiomeHeader(block: Int) {
    val level = block * BLOCK + 1
    val biome = LevelGenerator.biomeFor(level)
    Box(Modifier.fillMaxWidth().height(86.dp).padding(horizontal = 24.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF000000 or biome.skyTop.toLong()), Color(0xFF000000 or biome.accent.toLong()))))
                .border(3.dp, Palette.Navy, RoundedCornerShape(20.dp)),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedText(biome.title, style = Type.Heading, outlineWidth = 3.dp)
            BodyText("Levels $level–${level + BLOCK - 1}", color = Color.White, style = Type.Small)
        }
    }
}

@Composable
private fun LevelRow(level: Int, save: SaveData, onTap: () -> Unit) {
    val unlocked = save.progress.isUnlocked(level)
    val stars = save.progress.starsFor(level)
    val isCurrent = level == save.progress.highestUnlocked
    val boss = level % 10 == 0
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().height(ROW_H.dp)) {
        val w = maxWidth
        val x0 = w / 2 + w * nodeX(level)
        val x1 = w / 2 + w * nodeX(level + 1)
        val reached = level < save.progress.highestUnlocked
        Canvas(Modifier.fillMaxSize()) {
            if (level < MAX_LEVEL && (level % BLOCK != 0)) {
                val a = Offset(with(density) { x0.toPx() }, size.height / 2)
                val bpt = Offset(with(density) { x1.toPx() }, -size.height / 2)
                drawLine(Color(0x55000000), a + Offset(0f, 4f), bpt + Offset(0f, 4f), strokeWidth = 14f, cap = StrokeCap.Round)
                drawLine(if (reached) Color(0xFFFFE27A) else Color(0xCCDFF6FF), a, bpt, strokeWidth = 10f, cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 16f)))
            }
        }
        LevelNode(level, unlocked, stars, isCurrent, boss, save.profile.character, Modifier.offset(x = x0 - 36.dp, y = ((ROW_H - 72) / 2).dp), onTap)
    }
}

@Composable
private fun LevelNode(level: Int, unlocked: Boolean, stars: Int, current: Boolean, boss: Boolean, character: String, modifier: Modifier, onTap: () -> Unit) {
    val size: Dp = 72.dp
    val pulse = if (current) rememberInfiniteTransition(label = "cur").animateFloat(1f, 1.1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "cp").value else 1f
    val fill = when {
        !unlocked -> Brush.verticalGradient(listOf(Color(0xFF9AA8C0), Color(0xFF6F7D96)))
        current -> Brush.verticalGradient(listOf(Color(0xFF7CF06A), Color(0xFF2FC25B)))
        boss -> Brush.verticalGradient(listOf(Color(0xFFFF7A9C), Color(0xFFD6276A)))
        stars >= 3 -> Brush.verticalGradient(listOf(Color(0xFFFFE27A), Color(0xFFE09A12)))
        else -> Brush.verticalGradient(listOf(Color(0xFF9BDDFF), Color(0xFF2E8BE6)))
    }
    Box(modifier.size(size + 6.dp, size + 20.dp)) {
        Box(
            Modifier.size(size).scale(pulse).pressable(onClick = onTap).clip(CircleShape).background(fill).border(4.dp, Palette.Navy, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxWidth(0.7f).height(10.dp).offset(y = (-18).dp).clip(RoundedCornerShape(5.dp)).background(Color(0x44FFFFFF)))
            if (unlocked) OutlinedText("$level", style = Type.Heading.copy(fontSize = if (level >= 1000) 17.sp else 22.sp), outlineWidth = 3.dp)
            else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    UiIcon(R.drawable.ui_lock, 30.dp)
                    BodyText("$level", color = Color.White, style = Type.Small.copy(fontSize = 11.sp), align = TextAlign.Center)
                }
            }
        }
        if (boss) Box(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp)) { UiIcon(R.drawable.ui_crown, 28.dp) }
        if (current) Box(Modifier.align(Alignment.TopStart).offset(x = (-14).dp, y = (-10).dp)) { Avatar(character, 34.dp) }
        if (unlocked && !current) {
            Row(Modifier.align(Alignment.BottomCenter).offset(x = (-3).dp), horizontalArrangement = Arrangement.Center) {
                for (s in 1..3) UiIcon(if (s <= stars) R.drawable.ui_star else R.drawable.ui_star_empty, 20.dp, Modifier.offset(y = if (s == 2) 2.dp else (-2).dp))
            }
        }
    }
}

@Composable
fun LockedLevelDialog(level: Int, current: Int, onClose: () -> Unit, onUnlocked: () -> Unit, onPlayCurrent: () -> Unit) {
    val c = LocalContainer.current
    if (AdConfig.rewardedUnlockEnabled && c.ads.rewardedAvailable) {
        GameDialog("Locked Level", onDismiss = onClose) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    UiIcon(R.drawable.ui_lock, 86.dp)
                }
                Spacer(Modifier.height(6.dp))
                OutlinedText("Level $level", style = Type.Title, color = Palette.Gold)
                Spacer(Modifier.height(6.dp))
                BodyText("Watch a short ad to unlock this level right now!", align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                GameButton("Watch Ad", onClick = {
                    c.ads.showRewarded("unlock_level_$level") { ok -> if (ok) onUnlocked() else onClose() }
                }, modifier = Modifier.fillMaxWidth(), colors = Buttons.Gold, icon = R.drawable.ui_ad, shine = true)
                Spacer(Modifier.height(8.dp))
                BodyText("Not now", color = Palette.Muted, style = Type.Label, modifier = Modifier.pressable(onClick = onClose).padding(8.dp))
            }
        }
    } else {
        GameDialog("Locked Level", onDismiss = onClose) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                UiIcon(R.drawable.ui_lock, 86.dp)
                Spacer(Modifier.height(6.dp))
                OutlinedText("Level $level", style = Type.Title, color = Palette.Gold)
                Spacer(Modifier.height(6.dp))
                BodyText("Clear Level $current first to keep rolling forward.", align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                GameButton("Play Level $current", onClick = onPlayCurrent, modifier = Modifier.fillMaxWidth(), colors = Buttons.Green, icon = R.drawable.ui_play)
            }
        }
    }
}

@Suppress("unused")
private fun biomeColor(b: Biome) = Color(0xFF000000 or b.accent.toLong())
