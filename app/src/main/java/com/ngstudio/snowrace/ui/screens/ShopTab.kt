package com.ngstudio.snowrace.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.snowrace.R
import com.ngstudio.snowrace.audio.Sfx
import com.ngstudio.snowrace.data.BallDef
import com.ngstudio.snowrace.data.BallPattern
import com.ngstudio.snowrace.data.Catalog
import com.ngstudio.snowrace.data.GameRules
import com.ngstudio.snowrace.data.Price
import com.ngstudio.snowrace.data.PriceType
import com.ngstudio.snowrace.data.Rarity
import com.ngstudio.snowrace.data.SaveData
import com.ngstudio.snowrace.data.TrailDef
import com.ngstudio.snowrace.data.TrailShape
import com.ngstudio.snowrace.game.render.MenuMode
import com.ngstudio.snowrace.ui.LocalNav
import com.ngstudio.snowrace.ui.components.BodyText
import com.ngstudio.snowrace.ui.components.Buttons
import com.ngstudio.snowrace.ui.components.GameButton
import com.ngstudio.snowrace.ui.components.GameDialog
import com.ngstudio.snowrace.ui.components.LocalContainer
import com.ngstudio.snowrace.ui.components.OutlinedText
import com.ngstudio.snowrace.ui.components.Panel
import com.ngstudio.snowrace.ui.components.UiIcon
import com.ngstudio.snowrace.ui.components.formatNum
import com.ngstudio.snowrace.ui.components.pressable
import com.ngstudio.snowrace.ui.theme.Palette
import com.ngstudio.snowrace.ui.theme.Type
import kotlin.math.cos
import kotlin.math.sin

private enum class ShopCat(val title: String) { CHARS("Snowpals"), BALLS("Snowballs"), TRAILS("Trails") }

private class ShopItem(val id: String, val name: String, val rarity: Rarity, val price: Price, val note: String)

@Composable
fun ShopTab(save: SaveData) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    var cat by remember { mutableStateOf(ShopCat.CHARS) }
    var selected by remember { mutableStateOf(save.profile.character) }
    var reveal by remember { mutableStateOf<String?>(null) }

    val items: List<ShopItem> = when (cat) {
        ShopCat.CHARS -> Catalog.characters.map { ShopItem(it.id, it.name, it.rarity, it.price, it.unlockNote) }
        ShopCat.BALLS -> Catalog.balls.map { ShopItem(it.id, it.name, it.rarity, it.price, "") }
        ShopCat.TRAILS -> Catalog.trails.map { ShopItem(it.id, it.name, it.rarity, it.price, "") }
    }
    val equippedId = when (cat) { ShopCat.CHARS -> save.profile.character; ShopCat.BALLS -> save.profile.ball; ShopCat.TRAILS -> save.profile.trail }
    LaunchedEffect(cat) { selected = equippedId }
    LaunchedEffect(selected, cat) {
        when (cat) {
            ShopCat.CHARS -> c.stage.preview(selected, null, null, MenuMode.SHOP)
            ShopCat.BALLS -> c.stage.preview(null, selected, null, MenuMode.SHOP)
            ShopCat.TRAILS -> c.stage.preview(null, null, selected, MenuMode.SHOP)
        }
    }
    DisposableEffect(Unit) { onDispose { c.stage.showMenu(MenuMode.HOME) } }
    val sel = items.firstOrNull { it.id == selected } ?: items.first()

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(160.dp).background(Brush.verticalGradient(listOf(Color(0x991E1B4B), Color.Transparent))))
        Column(Modifier.fillMaxSize()) {
            TopBar(save)
            // 3D preview area (drag to spin)
            Box(
                Modifier.fillMaxWidth().weight(0.78f).pointerInput(Unit) { detectDragGestures { _, d -> c.stage.drag(d.x * 0.014f) } },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 6.dp)) {
                    RarityChip(sel.rarity)
                    OutlinedText(sel.name, style = Type.Title, outlineWidth = 4.dp)
                    if (sel.note.isNotEmpty()) BodyText(sel.note, color = Palette.Gold, style = Type.Small)
                }
            }
            Panel(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp).padding(bottom = 92.dp), padding = 10.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (k in ShopCat.entries) {
                        val on = k == cat
                        Box(
                            Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (on) Brush.verticalGradient(listOf(Color(0xFFFF86B3), Palette.Berry)) else Brush.verticalGradient(listOf(Color(0xFFDDEBFA), Color(0xFFC3D7EE))))
                                .border(2.5.dp, Palette.Navy, RoundedCornerShape(14.dp)).pressable { cat = k },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) OutlinedText(k.title, style = Type.Label, outlineWidth = 3.dp) else BodyText(k.title, style = Type.Label, color = Palette.Navy)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items, key = { it.id }) { it ->
                        ItemCard(it, cat, owned = GameRules.owns(save, it.id), equipped = it.id == equippedId, selected = it.id == selected) { selected = it.id }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val owned = GameRules.owns(save, sel.id)
                    val equipped = sel.id == equippedId
                    val action: @Composable () -> Unit = {
                        when {
                            equipped -> GameButton("Equipped", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth(), height = 52.dp)
                            owned -> GameButton("Equip", onClick = {
                                c.save.update { s ->
                                    when (cat) {
                                        ShopCat.CHARS -> s.copy(profile = s.profile.copy(character = sel.id))
                                        ShopCat.BALLS -> s.copy(profile = s.profile.copy(ball = sel.id))
                                        ShopCat.TRAILS -> s.copy(profile = s.profile.copy(trail = sel.id))
                                    }
                                }
                                c.audio.play(Sfx.POWERUP); c.stage.wave()
                            }, modifier = Modifier.fillMaxWidth(), colors = Buttons.Mint, height = 52.dp)
                            sel.price.type == PriceType.REWARD -> GameButton("Reward Only", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth(), height = 52.dp)
                            else -> {
                                val afford = GameRules.canAfford(save, sel.price)
                                GameButton(
                                    formatNum(sel.price.amount), onClick = {
                                        val res = GameRules.buy(c.save.data, sel.id, sel.price)
                                        if (res != null) {
                                            c.save.update { res }
                                            c.audio.play(Sfx.UNLOCK); c.stage.wave()
                                            reveal = sel.id
                                        } else { c.audio.play(Sfx.ERROR); nav.showToast("Not enough ${if (sel.price.type == PriceType.GEMS) "gems" else "coins"}") }
                                    },
                                    modifier = Modifier.fillMaxWidth(), height = 52.dp,
                                    colors = if (afford) Buttons.Gold else Buttons.Grey,
                                    icon = if (sel.price.type == PriceType.GEMS) R.drawable.ui_gem else R.drawable.ui_coin,
                                )
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) { action() }
                    Spacer(Modifier.width(8.dp))
                    MysteryBox(save) { id -> reveal = id }
                }
            }
        }
    }
    reveal?.let { id -> RevealDialog(id) { reveal = null } }
}

@Composable
private fun RarityChip(r: Rarity) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(r.color)).border(2.dp, Palette.Navy, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 2.dp)) {
        OutlinedText(r.label.uppercase(), style = Type.Small, outlineWidth = 2.dp)
    }
}

@Composable
private fun ItemCard(item: ShopItem, cat: ShopCat, owned: Boolean, equipped: Boolean, selected: Boolean, onClick: () -> Unit) {
    val rc = Color(item.rarity.color)
    Box(
        Modifier.aspectRatio(0.8f).pressable(onClick = onClick).clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(rc.copy(alpha = 0.35f), Color.White)))
            .border(if (selected) 3.5.dp else 2.dp, if (selected) Palette.Berry else Palette.Navy, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(5.dp).align(Alignment.TopCenter).background(rc))
        when (cat) {
            ShopCat.CHARS -> Avatar(item.id, 46.dp)
            ShopCat.BALLS -> BallSwatch(Catalog.ball(item.id), 44.dp)
            ShopCat.TRAILS -> TrailSwatch(Catalog.trail(item.id), 46.dp)
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp)) {
            when {
                equipped -> UiIcon(R.drawable.ui_check, 20.dp)
                owned -> {}
                item.price.type == PriceType.REWARD -> UiIcon(R.drawable.ui_gift, 20.dp)
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    UiIcon(if (item.price.type == PriceType.GEMS) R.drawable.ui_gem else R.drawable.ui_coin, 14.dp)
                    BodyText(formatNum(item.price.amount), style = Type.Small.copy(fontSize = 10.sp), color = Palette.Navy)
                }
            }
        }
        if (!owned) Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) { UiIcon(R.drawable.ui_lock, 16.dp) }
    }
}

@Composable
fun BallSwatch(def: BallDef, size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        val base = Color(def.base).copy(alpha = 1f)
        val acc = Color(def.accent).copy(alpha = 1f)
        drawCircle(Palette.Navy, r)
        drawCircle(base, r - 3f)
        when (def.pattern) {
            BallPattern.STRIPES, BallPattern.BANDS, BallPattern.SWIRL -> for (k in 0 until 5) {
                val a = k / 5f * 6.283f
                drawLine(acc, center + Offset(cos(a) * r * 0.2f, sin(a) * r * 0.2f), center + Offset(cos(a + 0.6f) * (r - 5f), sin(a + 0.6f) * (r - 5f)), strokeWidth = r * 0.22f)
            }
            BallPattern.DOTS, BallPattern.CRATERS -> for (k in 0 until 6) {
                val a = k / 6f * 6.283f
                drawCircle(acc, r * 0.16f, center + Offset(cos(a) * r * 0.55f, sin(a) * r * 0.55f))
            }
            BallPattern.SPRINKLES, BallPattern.GLITTER, BallPattern.STARS -> for (k in 0 until 10) {
                val a = k * 2.4f
                drawCircle(if (def.pattern == BallPattern.SPRINKLES) listOf(Palette.Berry, Palette.Mint, Palette.Gold, Palette.Sky)[k % 4] else acc, r * 0.08f, center + Offset(cos(a) * r * (0.2f + (k % 4) * 0.18f), sin(a) * r * (0.2f + (k % 4) * 0.18f)))
            }
            BallPattern.CHECKER -> for (k in 0 until 8) {
                val a = k / 8f * 6.283f
                drawCircle(acc.copy(alpha = 0.7f), r * 0.14f, center + Offset(cos(a) * r * 0.6f, sin(a) * r * 0.6f))
            }
            BallPattern.CRACKS -> for (k in 0 until 4) {
                val a = k * 1.57f + 0.4f
                drawLine(acc, center, center + Offset(cos(a) * r * 0.9f, sin(a) * r * 0.9f), strokeWidth = r * 0.12f)
            }
            BallPattern.RAINBOW -> {
                val cols = listOf(Color(0xFFFF4D4D), Color(0xFFFFA64D), Color(0xFFFFE14D), Color(0xFF4DFF88), Color(0xFF4DB8FF), Color(0xFF9A4DFF))
                cols.forEachIndexed { i, cc -> drawCircle(cc, (r - 3f) * (1f - i / 6f)) }
            }
            BallPattern.PLAIN -> {}
        }
        drawCircle(Color(0x66FFFFFF), r * 0.28f, center + Offset(-r * 0.35f, -r * 0.35f))
    }
}

@Composable
private fun TrailSwatch(def: TrailDef, size: androidx.compose.ui.unit.Dp) {
    val t = rememberInfiniteTransition(label = "trail").animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "tt").value
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        for (k in 0 until 6) {
            val p = ((k / 6f) + t) % 1f
            val x = w * (0.15f + 0.7f * p)
            val y = w * (0.75f - 0.45f * p) + sin(p * 9f) * 4f
            val s = w * (0.09f + 0.07f * (1f - p))
            val col = Color(def.colors[k % def.colors.size]).copy(alpha = 1f - p * 0.6f)
            shape(def.shape, Offset(x, y), s, col)
        }
    }
}

private fun DrawScope.shape(s: TrailShape, at: Offset, r: Float, col: Color) {
    when (s) {
        TrailShape.STAR, TrailShape.SPARKLE -> {
            val path = Path()
            val n = if (s == TrailShape.STAR) 5 else 4
            for (k in 0 until n * 2) {
                val rr = if (k % 2 == 0) r else r * 0.42f
                val a = Math.PI * k / n - Math.PI / 2
                val p = at + Offset((cos(a) * rr).toFloat(), (sin(a) * rr).toFloat())
                if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close(); drawPath(path, col)
        }
        TrailShape.HEART -> {
            drawCircle(col, r * 0.5f, at + Offset(-r * 0.35f, -r * 0.2f)); drawCircle(col, r * 0.5f, at + Offset(r * 0.35f, -r * 0.2f))
            val p = Path().apply { moveTo(at.x - r * 0.8f, at.y); lineTo(at.x + r * 0.8f, at.y); lineTo(at.x, at.y + r * 0.9f); close() }
            drawPath(p, col)
        }
        TrailShape.NOTE -> { drawCircle(col, r * 0.45f, at + Offset(0f, r * 0.4f)); drawLine(col, at + Offset(r * 0.4f, r * 0.4f), at + Offset(r * 0.4f, -r * 0.8f), strokeWidth = r * 0.25f) }
        TrailShape.FLAKE -> for (k in 0 until 3) { val a = k * 1.047f; drawLine(col, at + Offset(cos(a) * r, sin(a) * r), at - Offset(cos(a) * r, sin(a) * r), strokeWidth = r * 0.25f) }
        TrailShape.CIRCLE -> drawCircle(col, r * 0.7f, at)
    }
}

@Composable
private fun MysteryBox(save: SaveData, onReveal: (String) -> Unit) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    val wob = rememberInfiniteTransition(label = "mb").animateFloat(-6f, 6f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "mw").value
    Column(
        Modifier.width(92.dp).pressable {
            if (save.economy.coins < GameRules.MYSTERY_BOX_PRICE) { c.audio.play(Sfx.ERROR); nav.showToast("Need ${GameRules.MYSTERY_BOX_PRICE} coins"); return@pressable }
            val id = GameRules.mysteryPick(c.save.data, System.nanoTime())
            if (id == null) { nav.showToast("You own every common and rare item!"); return@pressable }
            c.save.update { s -> GameRules.grant(s.copy(economy = s.economy.copy(coins = s.economy.coins - GameRules.MYSTERY_BOX_PRICE)), id) }
            c.audio.play(Sfx.CHEST)
            onReveal(id)
        }.clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(Color(0xFFD4A6FF), Palette.Purple))).border(3.dp, Palette.Navy, RoundedCornerShape(16.dp)).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UiIcon(R.drawable.ui_chest, 34.dp, Modifier.offset(x = (wob * 0.3f).dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            UiIcon(R.drawable.ui_coin, 14.dp)
            OutlinedText(formatNum(GameRules.MYSTERY_BOX_PRICE), style = Type.Small, outlineWidth = 2.dp)
        }
    }
}

@Composable
private fun RevealDialog(id: String, onClose: () -> Unit) {
    val c = LocalContainer.current
    val name = Catalog.characters.firstOrNull { it.id == id }?.name ?: Catalog.balls.firstOrNull { it.id == id }?.name ?: Catalog.trails.firstOrNull { it.id == id }?.name ?: id
    val rarity = Catalog.characters.firstOrNull { it.id == id }?.rarity ?: Catalog.balls.firstOrNull { it.id == id }?.rarity ?: Catalog.trails.firstOrNull { it.id == id }?.rarity ?: Rarity.COMMON
    val spin = rememberInfiniteTransition(label = "rv").animateFloat(0f, 360f, infiniteRepeatable(tween(6000)), label = "rs").value
    LaunchedEffect(Unit) { c.audio.play(Sfx.UNLOCK) }
    GameDialog("New Item!", onDismiss = onClose, ribbon = Palette.Purple, ribbonDark = Color(0xFF7D3FC4)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    for (k in 0 until 12) {
                        val a = Math.toRadians((spin + k * 30).toDouble())
                        drawLine(Color(rarity.color).copy(alpha = 0.35f), center, center + Offset((cos(a) * size.width).toFloat(), (sin(a) * size.width).toFloat()), strokeWidth = 18f)
                    }
                }
                when {
                    Catalog.characters.any { it.id == id } -> Avatar(id, 96.dp)
                    Catalog.balls.any { it.id == id } -> BallSwatch(Catalog.ball(id), 92.dp)
                    else -> TrailSwatch(Catalog.trail(id), 96.dp)
                }
            }
            RarityChip(rarity)
            OutlinedText(name, style = Type.Title)
            Spacer(Modifier.height(12.dp))
            GameButton("Awesome!", onClick = onClose, modifier = Modifier.fillMaxWidth(), colors = Buttons.Green)
        }
    }
}
