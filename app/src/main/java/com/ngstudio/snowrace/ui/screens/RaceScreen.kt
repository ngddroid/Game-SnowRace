package com.ngstudio.snowrace.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.snowrace.R
import com.ngstudio.snowrace.app.LoadingCover
import com.ngstudio.snowrace.audio.Haptic
import com.ngstudio.snowrace.audio.Music
import com.ngstudio.snowrace.audio.Sfx
import com.ngstudio.snowrace.data.Catalog
import com.ngstudio.snowrace.data.GameRules
import com.ngstudio.snowrace.data.MAX_LEVEL
import com.ngstudio.snowrace.game.HudState
import com.ngstudio.snowrace.game.PopupKind
import com.ngstudio.snowrace.game.RaceController
import com.ngstudio.snowrace.game.RaceResult
import com.ngstudio.snowrace.game.UiEvent
import com.ngstudio.snowrace.game.sim.Phase
import com.ngstudio.snowrace.game.sim.PlayerSetup
import com.ngstudio.snowrace.level.LevelRepository
import com.ngstudio.snowrace.ui.LocalNav
import com.ngstudio.snowrace.ui.Screen
import com.ngstudio.snowrace.ui.Tab
import com.ngstudio.snowrace.ui.components.BodyText
import com.ngstudio.snowrace.ui.components.Buttons
import com.ngstudio.snowrace.ui.components.GameButton
import com.ngstudio.snowrace.ui.components.GameDialog
import com.ngstudio.snowrace.ui.components.GameToggle
import com.ngstudio.snowrace.ui.components.LocalContainer
import com.ngstudio.snowrace.ui.components.OutlinedText
import com.ngstudio.snowrace.ui.components.Panel
import com.ngstudio.snowrace.ui.components.UiIcon
import com.ngstudio.snowrace.ui.components.formatNum
import com.ngstudio.snowrace.ui.components.pressable
import com.ngstudio.snowrace.ui.theme.Palette
import com.ngstudio.snowrace.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

private class Popup(val id: Long, val text: String, val x: Float, val y: Float, val kind: PopupKind, val born: Long)

@Composable
fun RaceScreen(level: Int, boosters: Set<String>) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    val ctrl = remember(level) {
        val save = c.save.data
        val usable = boosters.filter { (save.economy.boosters[it] ?: 0) > 0 }.toSet()
        if (usable.isNotEmpty()) {
            c.save.update { s ->
                val m = s.economy.boosters.toMutableMap()
                for (b in usable) m[b] = ((m[b] ?: 0) - 1).coerceAtLeast(0)
                s.copy(economy = s.economy.copy(boosters = m))
            }
            nav.boosters = emptySet()
        }
        val auto = if (DebugFlags.autopilot) { DebugFlags.autopilot = false; 0.85f } else null
        RaceController(
            LevelRepository.get(level),
            PlayerSetup(save.profile.name, Catalog.character(save.profile.character), Catalog.ball(save.profile.ball), Catalog.trail(save.profile.trail), usable, auto),
        )
    }
    val hud by ctrl.hud.collectAsState()
    var countdown by remember { mutableIntStateOf(-1) }
    var countdownShown by remember { mutableIntStateOf(3) }
    var banner by remember { mutableStateOf<Pair<String, String>?>(null) }
    var bannerId by remember { mutableIntStateOf(0) }
    val popups = remember { mutableStateListOf<Popup>() }
    var result by remember { mutableStateOf<RaceResult?>(null) }
    var lost by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var finaleMult by remember { mutableStateOf<Float?>(null) }
    var popupSeq by remember { mutableLongStateOf(0L) }

    LaunchedEffect(ctrl) {
        // hold the sim while the level is built behind the loading cover, so the intro starts as the cover lifts
        ctrl.paused = true
        c.stage.playMusic(if (ctrl.spec.isBoss) Music.BOSS else Music.RACE)
        val sub = (if (ctrl.spec.isBoss) "BOSS · " else "") + ctrl.spec.name.ifBlank { ctrl.spec.biome.title }
        c.stage.swapScene(LoadingCover("LEVEL $level", sub)) { c.stage.showRace(ctrl) }
        com.ngstudio.snowrace.analytics.Telemetry.levelStart(level, ctrl.spec.isBoss)
        if (!paused && result == null) ctrl.paused = false
    }
    DisposableEffect(ctrl) { onDispose { ctrl.paused = true } }
    LaunchedEffect(ctrl) {
        ctrl.events.collect { e ->
            when (e) {
                is UiEvent.Countdown -> { countdown = e.n; countdownShown = e.n }
                UiEvent.Go -> { countdown = 0; countdownShown = 0; launch { delay(800); countdown = -1 } }
                is UiEvent.Popup -> { popupSeq++; popups += Popup(popupSeq, e.text, e.x, e.y, e.kind, System.nanoTime()); if (popups.size > 8) popups.removeAt(0) }
                is UiEvent.Banner -> { banner = e.text to e.sub; bannerId++ }
                is UiEvent.FinaleStep -> finaleMult = e.mult
                UiEvent.RaceLost -> lost = true
                is UiEvent.Finished -> {
                    val before = c.save.data.progress.highestUnlocked
                    c.save.update { GameRules.applyRace(it, e.result, emptySet()) }
                    com.ngstudio.snowrace.analytics.Telemetry.levelEnd(e.result)
                    c.save.data.progress.highestUnlocked.let { if (it > before) com.ngstudio.snowrace.analytics.Telemetry.levelUp(it) }
                    delay(if (e.result.cleared) 600 else 300)
                    c.stage.playMusic(Music.MENU)
                    result = e.result
                }
            }
        }
    }
    LaunchedEffect(bannerId) { if (banner != null) { delay(1900); banner = null } }

    val settings = c.save.data.settings
    val density = LocalDensity.current
    var stickOrigin by remember { mutableStateOf<Offset?>(null) }
    var stickPos by remember { mutableStateOf(Offset.Zero) }

    fun pause() { if (result == null) { ctrl.paused = true; paused = true; c.audio.play(Sfx.UI_OPEN) } }
    BackHandler(enabled = result == null) { if (paused) { ctrl.paused = false; paused = false } else pause() }

    Box(Modifier.fillMaxSize()) {
        // joystick input layer
        val radiusPx = with(density) { (58.dp / settings.sensitivity).toPx() }
        Box(
            Modifier.fillMaxSize().pointerInput(ctrl) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    stickOrigin = down.position; stickPos = down.position
                    ctrl.stickX = 0f; ctrl.stickY = 0f; ctrl.touchActive = true
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        if (ch.positionChange() != Offset.Zero) {
                            var d = ch.position - (stickOrigin ?: ch.position)
                            val len = sqrt(d.x * d.x + d.y * d.y)
                            if (len > radiusPx) {
                                // drag the origin along so direction changes stay responsive
                                val o = (stickOrigin ?: ch.position) + d * ((len - radiusPx) / len)
                                stickOrigin = o
                                d = ch.position - o
                            }
                            stickPos = ch.position
                            ctrl.stickX = d.x / radiusPx
                            ctrl.stickY = -d.y / radiusPx
                        }
                    }
                    ctrl.touchActive = false; ctrl.stickX = 0f; ctrl.stickY = 0f
                    stickOrigin = null
                }
            },
        )
        stickOrigin?.let { o ->
            Canvas(Modifier.fillMaxSize()) {
                val r = radiusPx
                drawCircle(Color(0x33FFFFFF), r, o)
                drawCircle(Color(0x88FFFFFF), r, o, style = Stroke(5f))
                val d = stickPos - o
                val len = sqrt(d.x * d.x + d.y * d.y).coerceAtLeast(1e-3f)
                val k = (len.coerceAtMost(r)) / len
                drawCircle(Color(0xCCFFFFFF), r * 0.42f, o + d * k)
                drawCircle(Color(0x66FF4D8D), r * 0.42f, o + d * k, style = Stroke(6f))
            }
        }

        if (result == null) Hud(hud, ctrl, settings.leftHanded, settings.showFps, onPause = { pause() })
        Tutorial(ctrl.spec.tutorial, hud)

        // countdown
        AnimatedVisibility(countdown >= 0, enter = scaleIn(spring(dampingRatio = 0.4f)) + fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            val n = countdownShown
            val txt = if (n == 0) "GO!" else "$n"
            val pop = remember(n) { Animatable(1.8f) }
            LaunchedEffect(n) { pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 300f)) }
            OutlinedText(txt, style = Type.Huge.copy(fontSize = if (n == 0) 96.sp else 120.sp), outlineWidth = 8.dp,
                brush = Brush.verticalGradient(if (n == 0) listOf(Color(0xFFB8FF9C), Palette.Mint) else listOf(Color.White, Palette.Gold)),
                modifier = Modifier.scale(pop.value))
        }
        // banner
        AnimatedVisibility(banner != null, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut(), modifier = Modifier.align(Alignment.TopCenter).padding(top = 150.dp)) {
            val b = banner
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(Brush.horizontalGradient(listOf(Color(0xE6FF4D8D), Color(0xE6B46CFF)))).border(3.dp, Palette.Navy, RoundedCornerShape(22.dp)).padding(horizontal = 22.dp, vertical = 8.dp)) {
                OutlinedText(b?.first ?: "", style = Type.Title, outlineWidth = 4.dp)
                if (!b?.second.isNullOrEmpty()) OutlinedText(b!!.second, style = Type.Label, outlineWidth = 3.dp)
            }
        }
        // finale multiplier
        finaleMult?.let { m ->
            if (result == null) {
                val max = m >= 5f
                val pop = remember(m) { Animatable(if (max) 2.4f else 1.6f) }
                LaunchedEffect(m) { pop.animateTo(1f, spring(dampingRatio = 0.35f)) }
                OutlinedText(if (max) "MAX x5!" else "x" + multText(m), style = if (max) Type.Huge.copy(fontSize = 60.sp) else Type.Huge, outlineWidth = 8.dp,
                    brush = Brush.verticalGradient(if (max) listOf(Color(0xFFFFB3E0), Palette.Gold, Palette.Mint) else listOf(Color(0xFFFFF3B0), Palette.Gold)),
                    // up in the sky, clear of Pip (the camera frames Pip around the middle of the screen)
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 92.dp).scale(pop.value))
            }
        }
        if (lost && result == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedText("So close!", style = Type.Huge.copy(fontSize = 52.sp), outlineWidth = 7.dp, brush = Brush.verticalGradient(listOf(Color.White, Palette.Sky)))
                OutlinedText("Three rivals finished first", style = Type.Heading, outlineWidth = 4.dp)
            }
        }
        PopupLayer(popups)

        if (paused && result == null) PauseDialog(
            onResume = { paused = false; ctrl.paused = false },
            onRestart = { paused = false; nav.startRace(level, emptySet()) },
            onHome = { paused = false; com.ngstudio.snowrace.analytics.Telemetry.levelQuit(level); nav.tab = Tab.HOME; nav.replaceAll(Screen.Main) },
        )
        result?.let { r ->
            ResultOverlay(r,
                onNext = { if (r.level < MAX_LEVEL) nav.startRace(r.level + 1, nav.boosters) },
                onRetry = { nav.startRace(r.level, nav.boosters) },
                onHome = { nav.tab = Tab.HOME; nav.replaceAll(Screen.Main) },
            )
        }
    }
}

// ================================================================================== HUD
@Composable
private fun Hud(h: HudState, ctrl: RaceController, leftHanded: Boolean, showFps: Boolean, onPause: () -> Unit) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!leftHanded) PauseButton(onPause)
            Spacer(Modifier.width(8.dp))
            ProgressTrack(h, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            RankPill(h.rank, h.racers)
            if (leftHanded) { Spacer(Modifier.width(8.dp)); PauseButton(onPause) }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedText("STAGE ${h.stage}/${h.stages}", style = Type.Label, outlineWidth = 3.dp)
            Spacer(Modifier.width(10.dp))
            if (h.showBridgeMeter && h.phase == Phase.RACE) BridgeMeter(h.bridgeRatio, h.bridgeReady, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            if (h.giftCoins > 0) {
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { UiIcon(R.drawable.ui_coin, 26.dp); OutlinedText("+${h.giftCoins}", style = Type.Label, outlineWidth = 3.dp) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (h.boost > 0f) PowerChip(R.drawable.ui_boost, h.boost, 3.5f)
            if (h.shield > 0f) PowerChip(R.drawable.ui_shield, h.shield, 8f)
            if (h.magnet > 0f) PowerChip(R.drawable.ui_magnet, h.magnet, 7f)
        }
        if (showFps) BodyText("${h.fps} fps", color = Color.White, style = Type.Small)
    }
}

@Composable
private fun PauseButton(onPause: () -> Unit) {
    Box(Modifier.size(48.dp).pressable(onClick = onPause)) { UiIcon(R.drawable.ui_pause, 48.dp) }
}

@Composable
private fun RankPill(rank: Int, total: Int) {
    val col = when (rank) { 1 -> Palette.Gold; 2 -> Color(0xFFD7E1EE); 3 -> Color(0xFFFFA27A); else -> Palette.Sky }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(rank) { pop.snapTo(1.3f); pop.animateTo(1f, spring(dampingRatio = 0.4f)) }
    Box(Modifier.scale(pop.value).clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(col, col.copy(alpha = 0.8f)))).border(3.dp, Palette.Navy, RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedText(ordinal(rank), style = Type.Title.copy(fontSize = 26.sp), outlineWidth = 4.dp)
            OutlinedText("/$total", style = Type.Label, outlineWidth = 3.dp, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

private fun multText(m: Float) = if (m % 1f == 0f) m.toInt().toString() else m.toString()

private fun ordinal(n: Int) = when (n) { 1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"; else -> "${n}th" }

@Composable
private fun ProgressTrack(h: HudState, modifier: Modifier) {
    BoxWithConstraints(modifier.height(30.dp)) {
        val w = maxWidth
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x99141235)).border(2.dp, Palette.Navy, RoundedCornerShape(6.dp)))
        // stage separators
        for (i in 1 until h.stages) {
            Box(Modifier.align(Alignment.CenterStart).offset(x = w * (i / h.stages.toFloat()) - 1.dp).size(3.dp, 16.dp).background(Color(0x99FFFFFF)))
        }
        Box(Modifier.align(Alignment.CenterEnd).offset(x = 4.dp)) { UiIcon(R.drawable.ui_flag, 24.dp) }
        for (d in h.dots.sortedBy { it.isPlayer }) {
            val x by animateFloatAsState(d.progress, tween(200), label = "dot")
            val size = if (d.isPlayer) 22.dp else 15.dp
            Box(
                Modifier.align(Alignment.CenterStart).offset(x = (w - size) * x)
                    .size(size).clip(CircleShape).background(Color(d.color).copy(alpha = 1f)).border(if (d.isPlayer) 3.dp else 2.dp, if (d.isPlayer) Color.White else Palette.Navy, CircleShape),
            )
        }
    }
}

@Composable
private fun BridgeMeter(ratio: Float, ready: Boolean, modifier: Modifier) {
    val glow = if (ready) rememberInfiniteTransition(label = "bm").animateFloat(0.85f, 1.08f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "bmg").value else 1f
    Row(modifier.height(34.dp).scale(glow).clip(RoundedCornerShape(17.dp)).background(Color(0xB3141235)).border(2.dp, if (ready) Palette.Mint else Color(0x66FFFFFF), RoundedCornerShape(17.dp)).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        UiIcon(R.drawable.ui_snowball, 26.dp)
        Spacer(Modifier.width(4.dp))
        Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x66FFFFFF))) {
            val p by animateFloatAsState(ratio.coerceIn(0f, 1f), tween(250), label = "bmr")
            Box(Modifier.fillMaxWidth(p).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Brush.horizontalGradient(if (ready) listOf(Palette.Mint, Color(0xFF7CF06A)) else listOf(Palette.Sky, Palette.Berry))))
        }
        Spacer(Modifier.width(4.dp))
        if (ready) OutlinedText("READY", style = Type.Small, outlineWidth = 2.dp, color = Palette.Mint) else UiIcon(R.drawable.ui_bridge, 26.dp)
    }
}

@Composable
private fun PowerChip(icon: Int, left: Float, max: Float) {
    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xB3141235)).border(2.dp, Palette.Gold, RoundedCornerShape(14.dp)).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        UiIcon(icon, 26.dp)
        Box(Modifier.width(42.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x55FFFFFF))) {
            Box(Modifier.fillMaxWidth((left / max).coerceIn(0f, 1f)).height(8.dp).background(Palette.Gold))
        }
    }
}

@Composable
private fun PopupLayer(popups: MutableList<Popup>) {
    var now by remember { mutableLongStateOf(System.nanoTime()) }
    // tick only while popups are on screen (an idle per-frame loop recomposed this layer 60x a second)
    val active = popups.isNotEmpty()
    LaunchedEffect(active) { if (active) while (true) withFrameNanos { now = System.nanoTime() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth; val h = maxHeight
        val dead = ArrayList<Popup>()
        for (p in popups) {
            val t = ((now - p.born) / 1e9f).coerceAtLeast(0f) // `now` can lag a new popup by one frame
            if (t > 1.2f) { dead += p; continue }
            val rise = (t * 70f).dp
            val a = if (t < 0.85f) 1f else 1f - (t - 0.85f) / 0.35f
            val s = if (t < 0.15f) 0.6f + t / 0.15f * 0.6f else 1.2f - (t - 0.15f).coerceAtMost(0.2f)
            val col = when (p.kind) { PopupKind.GOOD -> Palette.Gold; PopupKind.BAD -> Color(0xFFFF8FA8); PopupKind.COIN -> Palette.Gold; PopupKind.INFO -> Palette.Mint; PopupKind.BIG -> Color.White }
            val px = (w * p.x).coerceIn(110.dp, w - 110.dp)
            OutlinedText(p.text, style = Type.Title.copy(fontSize = if (p.kind == PopupKind.GOOD) 34.sp else 24.sp), outlineWidth = 5.dp, color = col,
                modifier = Modifier.offset(x = px - 110.dp, y = h * p.y - 30.dp - rise).width(220.dp).alpha(a.coerceIn(0f, 1f)).scale(s))
        }
        if (dead.isNotEmpty()) LaunchedEffect(dead.size, dead.first().id) { popups.removeAll(dead) }
    }
}

// ================================================================================== tutorial
@Composable
private fun Tutorial(id: Int, h: HudState) {
    if (id == 0) return
    var shownSince by remember { mutableLongStateOf(0L) }
    val racing = h.phase == Phase.RACE
    LaunchedEffect(racing) { if (racing) shownSince = System.currentTimeMillis() }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(250); tick++ } }
    val nowMs = remember(tick) { System.currentTimeMillis() }
    val elapsed = if (shownSince == 0L) 0L else nowMs - shownSince
    val tip: String? = when (id) {
        1 -> when {
            !racing -> null
            h.snow < 40 -> "Hold & drag anywhere to move"
            h.snow < 220 && h.stage == 1 && !h.bridgeReady -> "Roll over fresh snow to grow your snowball!"
            h.stage == 1 -> "Push your snowball into the water to build a bridge!"
            else -> null
        }
        2 -> if (racing && elapsed < 7000) "When the meter says READY, your ball can bridge the gap!" else null
        3 -> if (racing && elapsed < 7000) "Bigger snowballs BONK smaller rivals!" else null
        4 -> if (racing && elapsed < 6000) "Grab gift boxes for bonus coins!" else null
        5 -> if (racing && elapsed < 6000) "Sled Boost = super speed. Grab it!" else null
        6 -> if (racing && elapsed < 6000) "Ice is slippery: steer gently!" else null
        7 -> if (racing && elapsed < 6000) "Watch out for rolling logs!" else null
        8 -> if (racing && elapsed < 6000) "Frost Shield blocks every bonk!" else null
        9 -> if (racing && elapsed < 7000) "Save extra snow for the finale stairs: more steps, more coins!" else null
        10 -> if (racing && elapsed < 6000) "Avalanche! Keep moving forward!" else null
        else -> null
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(tip != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 70.dp, start = 24.dp, end = 24.dp)) {
                if (id == 1 && h.snow < 40) {
                    val inf = rememberInfiniteTransition(label = "hand")
                    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "ht")
                    UiIcon(R.drawable.ui_hand, 86.dp, Modifier.graphicsLayer { translationX = (kotlin.math.sin(t * 6.283f) * 90f); translationY = -t * 60f })
                }
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xE6FFFFFF)).border(3.dp, Palette.Navy, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
                    BodyText(tip ?: "", style = Type.Label.copy(fontSize = 17.sp), color = Palette.Navy, align = TextAlign.Center)
                }
            }
        }
    }
}

// ================================================================================== pause
@Composable
private fun PauseDialog(onResume: () -> Unit, onRestart: () -> Unit, onHome: () -> Unit) {
    val c = LocalContainer.current
    val save by c.save.state.collectAsState()
    GameDialog("Paused", onDismiss = onResume, ribbon = Palette.Sky, ribbonDark = Palette.SkyDeep) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    UiIcon(R.drawable.ui_music, 40.dp)
                    GameToggle(save.settings.music, { v -> c.save.update { it.copy(settings = it.settings.copy(music = v)) }; c.applySettings(c.save.data.settings) })
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    UiIcon(R.drawable.ui_sound, 40.dp)
                    GameToggle(save.settings.sfx, { v -> c.save.update { it.copy(settings = it.settings.copy(sfx = v)) }; c.applySettings(c.save.data.settings) })
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    UiIcon(R.drawable.ui_vibrate, 40.dp)
                    GameToggle(save.settings.haptics, { v -> c.save.update { it.copy(settings = it.settings.copy(haptics = v)) }; c.applySettings(c.save.data.settings) })
                }
            }
            Spacer(Modifier.height(16.dp))
            GameButton("Resume", onClick = onResume, modifier = Modifier.fillMaxWidth(), colors = Buttons.Green, icon = R.drawable.ui_play)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameButton("Restart", onClick = onRestart, modifier = Modifier.weight(1f), colors = Buttons.Sky, icon = R.drawable.ui_restart, height = 52.dp, textStyle = Type.Label)
                GameButton("Home", onClick = onHome, modifier = Modifier.weight(1f), colors = Buttons.Berry, icon = R.drawable.ui_home, height = 52.dp, textStyle = Type.Label)
            }
        }
    }
}

// ================================================================================== result
@Composable
private fun ResultOverlay(r: RaceResult, onNext: () -> Unit, onRetry: () -> Unit, onHome: () -> Unit) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    var starsShown by remember { mutableIntStateOf(0) }
    var coinsShown by remember { mutableIntStateOf(0) }
    var bonusClaimed by remember { mutableStateOf(false) }
    var total by remember { mutableIntStateOf(r.totalCoins) }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 220f))
        for (s in 1..r.stars) {
            delay(330); starsShown = s
            c.audio.play(when (s) { 1 -> Sfx.STAR1; 2 -> Sfx.STAR2; else -> Sfx.STAR3 }); c.haptics.play(Haptic.CLICK)
        }
        delay(250)
        val steps = 24
        for (i in 1..steps) {
            coinsShown = total * i / steps
            c.audio.play(Sfx.COIN_TICK, 0.7f, 0.9f + i * 0.02f)
            delay(35)
        }
        coinsShown = total
        c.audio.play(Sfx.COIN)
    }
    LaunchedEffect(total) { if (coinsShown > 0) coinsShown = total }
    // Title on top, card at the bottom, and a clear band in between where Pip celebrates (the camera frames it there).
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        0f to Color(0x991E1B4B), 0.24f to Color(0x331E1B4B), 0.36f to Color.Transparent,
        0.5f to Color.Transparent, 0.6f to Color(0xB31E1B4B), 1f to Color(0xEE1E1B4B),
    ))) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp)
                .graphicsLayer { val v = enter.value; scaleX = 0.7f + 0.3f * v; scaleY = 0.7f + 0.3f * v; alpha = v.coerceIn(0f, 1f) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val title = when {
                r.rank == 1 -> "VICTORY!"
                r.cleared -> "${ordinal(r.rank)} PLACE!"
                else -> "TRY AGAIN!"
            }
            OutlinedText(title, style = Type.Huge.copy(fontSize = 50.sp), outlineWidth = 7.dp,
                brush = Brush.verticalGradient(if (r.cleared) listOf(Color(0xFFFFF3B0), Palette.Gold) else listOf(Color.White, Palette.Sky)))
            OutlinedText("Level ${r.level}", style = Type.Heading, outlineWidth = 4.dp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                for (s in 1..3) {
                    val on = s <= starsShown
                    val sc by animateFloatAsState(if (on) 1f else 0.85f, spring(dampingRatio = 0.35f, stiffness = 400f), label = "star")
                    UiIcon(if (on) R.drawable.ui_star else R.drawable.ui_star_empty, if (s == 2) 76.dp else 60.dp, Modifier.scale(sc).offset(y = if (s == 2) (-8).dp else 0.dp))
                }
            }
            Spacer(Modifier.weight(1f).heightIn(min = 120.dp))
            Panel(Modifier.fillMaxWidth()) {
                ResultRow(R.drawable.ui_trophy, "Finish reward", "${r.baseCoins}")
                if (r.multiplier > 1f) ResultRow(R.drawable.ui_snowball, "Finale multiplier", "x" + multText(r.multiplier))
                if (r.giftCoins > 0) ResultRow(R.drawable.ui_gift, "Gift boxes", "+${r.giftCoins}")
                ResultRow(R.drawable.ui_bonk, "Bonks", "${r.bonks}")
                ResultRow(R.drawable.ui_bridge, "Bridge blocks", "${r.blocks}")
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFF4C9)).border(2.dp, Palette.Gold, RoundedCornerShape(16.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    UiIcon(R.drawable.ui_coin, 40.dp)
                    Spacer(Modifier.width(8.dp))
                    BodyText("Total", style = Type.Heading, modifier = Modifier.weight(1f))
                    OutlinedText("+${formatNum(coinsShown)}", style = Type.Title, outlineWidth = 4.dp, color = Palette.Gold)
                }
                if (c.ads.rewardedAvailable && !bonusClaimed && r.totalCoins > 0) {
                    Spacer(Modifier.height(8.dp))
                    GameButton("x3 Coins", onClick = {
                        c.ads.showRewarded("triple_coins") { ok ->
                            com.ngstudio.snowrace.analytics.Telemetry.rewardedAd("triple_coins", ok)
                            if (ok) {
                                bonusClaimed = true
                                c.save.update { GameRules.addCoins(it, r.totalCoins * 2) }
                                total = r.totalCoins * 3
                                c.audio.play(Sfx.COIN)
                                nav.showToast("+${r.totalCoins * 2} bonus coins!")
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = Buttons.Gold, icon = R.drawable.ui_ad, height = 52.dp, shine = true)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (r.cleared && r.level < MAX_LEVEL) {
                GameButton("Next Level", onClick = onNext, modifier = Modifier.fillMaxWidth(), colors = Buttons.Green, icon = R.drawable.ui_play, shine = true)
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameButton(if (r.cleared) "Replay" else "Retry", onClick = onRetry, modifier = Modifier.weight(1f), colors = if (r.cleared) Buttons.Sky else Buttons.Green, icon = R.drawable.ui_restart, height = 54.dp, textStyle = Type.Label)
                GameButton("Home", onClick = onHome, modifier = Modifier.weight(1f), colors = Buttons.Berry, icon = R.drawable.ui_home, height = 54.dp, textStyle = Type.Label)
            }
        }
    }
}

@Composable
private fun ResultRow(icon: Int, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        UiIcon(icon, 26.dp)
        Spacer(Modifier.width(8.dp))
        BodyText(label, style = Type.Body, modifier = Modifier.weight(1f))
        BodyText(value, style = Type.Label, color = Palette.Navy)
    }
}
