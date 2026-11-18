package com.ngstudio.snowrace.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.snowrace.R
import com.ngstudio.snowrace.audio.Haptic
import com.ngstudio.snowrace.audio.Sfx
import com.ngstudio.snowrace.data.Catalog
import com.ngstudio.snowrace.data.GameRules
import com.ngstudio.snowrace.data.SaveData
import com.ngstudio.snowrace.ui.LocalNav
import com.ngstudio.snowrace.ui.components.BodyText
import com.ngstudio.snowrace.ui.components.Buttons
import com.ngstudio.snowrace.ui.components.GameButton
import com.ngstudio.snowrace.ui.components.GameProgressBar
import com.ngstudio.snowrace.ui.components.LocalContainer
import com.ngstudio.snowrace.ui.components.OutlinedText
import com.ngstudio.snowrace.ui.components.Panel
import com.ngstudio.snowrace.ui.components.UiIcon
import com.ngstudio.snowrace.ui.components.formatNum
import com.ngstudio.snowrace.ui.components.pressable
import com.ngstudio.snowrace.ui.theme.Palette
import com.ngstudio.snowrace.ui.theme.Type

@Composable
fun ProfileTab(save: SaveData) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(save.profile.name) }
    val focus = LocalFocusManager.current
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(160.dp).background(Brush.verticalGradient(listOf(Color(0x991E1B4B), Color.Transparent))))
        Column(Modifier.fillMaxSize()) {
            TopBar(save)
            Box(Modifier.fillMaxWidth().height(250.dp).pointerInput(Unit) { detectDragGestures { _, d -> c.stage.drag(d.x * 0.014f) } }, contentAlignment = Alignment.BottomCenter) {
                if (!editing) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.pressable { editing = true; name = save.profile.name }) {
                        OutlinedText(save.profile.name, style = Type.Title, outlineWidth = 4.dp)
                        Spacer(Modifier.width(6.dp))
                        UiIcon(R.drawable.ui_edit, 32.dp)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 30.dp)) {
                        Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).border(3.dp, Palette.Navy, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            BasicTextField(
                                value = name, onValueChange = { v -> name = v.filter { it.isLetterOrDigit() || it == ' ' || it == '_' }.take(12) }, singleLine = true,
                                textStyle = Type.Label.copy(fontSize = 20.sp, color = Palette.Navy, textAlign = TextAlign.Center), cursorBrush = SolidColor(Palette.Berry),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(48.dp).pressable {
                            val n = name.trim().ifEmpty { "Player" }
                            c.save.update { it.copy(profile = it.profile.copy(name = n)) }
                            editing = false; focus.clearFocus()
                        }) { UiIcon(R.drawable.ui_check, 48.dp) }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                Panel(Modifier.fillMaxWidth()) {
                    OutlinedText("Stats", style = Type.Heading, color = Palette.Navy, outline = Color.White, outlineWidth = 3.dp)
                    Spacer(Modifier.height(8.dp))
                    val s = save.stats
                    val stats = listOf(
                        Triple(R.drawable.ui_flag, "Races", formatNum(s.races)),
                        Triple(R.drawable.ui_trophy, "Wins", formatNum(s.wins)),
                        Triple(R.drawable.ui_crown, "Best streak", "${s.bestStreak}"),
                        Triple(R.drawable.ui_bonk, "Bonks", formatNum(s.bonks)),
                        Triple(R.drawable.ui_bridge, "Blocks", formatNum(s.bridgeBlocks)),
                        Triple(R.drawable.ui_snowball, "Rolled", "${formatNum(s.metersRolled.toInt())} m"),
                        Triple(R.drawable.ui_star, "Stars", formatNum(save.progress.totalStars())),
                        Triple(R.drawable.ui_chest, "Items", "${save.economy.owned.size}/${Catalog.characters.size + Catalog.balls.size + Catalog.trails.size}"),
                        Triple(R.drawable.ui_coin, "Coins earned", formatNum(s.coinsEarned)),
                    )
                    stats.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for ((icon, label, value) in row) {
                                Column(
                                    Modifier.weight(1f).padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE8F3FF)).border(2.dp, Palette.PanelEdge, RoundedCornerShape(14.dp)).padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    UiIcon(icon, 30.dp)
                                    BodyText(value, style = Type.Label, color = Palette.Navy)
                                    BodyText(label, style = Type.Small.copy(fontSize = 10.sp), color = Palette.Muted)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Panel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UiIcon(R.drawable.ui_trophy, 34.dp)
                        Spacer(Modifier.width(6.dp))
                        OutlinedText("Achievements", style = Type.Heading, color = Palette.Navy, outline = Color.White, outlineWidth = 3.dp)
                        Spacer(Modifier.weight(1f))
                        BodyText("${save.achievementsClaimed.size}/${GameRules.achievements.size}", style = Type.Label, color = Palette.Muted)
                    }
                    Spacer(Modifier.height(8.dp))
                    val sorted = GameRules.achievements.sortedBy { a ->
                        val claimed = a.id in save.achievementsClaimed
                        val ready = !claimed && a.metric(save) >= a.target
                        if (ready) 0 else if (!claimed) 1 else 2
                    }
                    for (a in sorted) {
                        val v = a.metric(save)
                        val claimed = a.id in save.achievementsClaimed
                        val ready = !claimed && v >= a.target
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (ready) Color(0xFFFFF4C9) else Color(0xFFE8F3FF)).border(2.dp, if (ready) Palette.Gold else Palette.PanelEdge, RoundedCornerShape(14.dp)).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UiIcon(if (claimed) R.drawable.ui_check else R.drawable.ui_trophy, 34.dp, alpha = if (claimed || ready) 1f else 0.45f)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                BodyText(a.title, style = Type.Label, color = Palette.Navy)
                                BodyText(a.desc, style = Type.Small.copy(fontSize = 11.sp), color = Palette.Muted)
                                if (!claimed) GameProgressBar(v / a.target.toFloat(), Modifier.fillMaxWidth().padding(top = 3.dp), height = 10.dp)
                            }
                            Spacer(Modifier.width(6.dp))
                            if (ready) GameButton("${a.reward}", onClick = {
                                val r = GameRules.claimAchievement(c.save.data, a.id) ?: return@GameButton
                                c.save.update { r }
                                com.ngstudio.snowrace.analytics.Telemetry.reward("achievement_${a.id}", a.reward, a.gems)
                                c.audio.play(Sfx.UNLOCK); c.haptics.play(Haptic.SUCCESS)
                                nav.showToast("${a.title}: +${a.reward} coins, +${a.gems} gems")
                            }, modifier = Modifier.width(88.dp), height = 40.dp, colors = Buttons.Gold, icon = R.drawable.ui_coin, textStyle = Type.Label)
                        }
                    }
                }
                Spacer(Modifier.height(110.dp))
            }
        }
    }
}
