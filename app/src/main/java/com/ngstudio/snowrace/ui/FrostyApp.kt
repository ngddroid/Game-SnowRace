package com.ngstudio.snowrace.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.snowrace.R
import com.ngstudio.snowrace.ads.MockAdProvider
import com.ngstudio.snowrace.analytics.Telemetry
import com.ngstudio.snowrace.app.AppContainer
import com.ngstudio.snowrace.app.LoadingCover
import com.ngstudio.snowrace.app.Stage
import com.ngstudio.snowrace.audio.Music
import com.ngstudio.snowrace.game.render.MenuMode
import com.ngstudio.snowrace.ui.components.GameBackground
import com.ngstudio.snowrace.ui.components.LocalContainer
import com.ngstudio.snowrace.ui.components.OutlinedText
import com.ngstudio.snowrace.ui.components.UiIcon
import com.ngstudio.snowrace.ui.screens.LegalDoc
import com.ngstudio.snowrace.ui.screens.LegalScreen
import com.ngstudio.snowrace.ui.screens.MainScreen
import com.ngstudio.snowrace.ui.screens.MockAdOverlay
import com.ngstudio.snowrace.ui.screens.FirstLaunchNotificationRequest
import com.ngstudio.snowrace.ui.screens.OnboardingScreen
import com.ngstudio.snowrace.ui.screens.RaceScreen
import com.ngstudio.snowrace.ui.screens.SettingsScreen
import com.ngstudio.snowrace.ui.screens.SplashScreen
import com.ngstudio.snowrace.ui.screens.StudioIntroScreen
import com.ngstudio.snowrace.ui.theme.Palette
import com.ngstudio.snowrace.ui.theme.Type
import kotlinx.coroutines.delay

sealed class Screen {
    /** NG Studio ident, shown on every launch before the game's own splash. */
    data object StudioIntro : Screen()
    data object Splash : Screen()
    data object Onboarding : Screen()
    data object Main : Screen()
    data object Settings : Screen()
    data class Legal(val doc: LegalDoc) : Screen()
    data class Race(val level: Int, val boosters: Set<String>, val nonce: Int) : Screen()
}

enum class Tab { SHOP, REWARDS, HOME, LEVELS, PROFILE }

class AppNav(start: Screen = Screen.StudioIntro) {
    val stack = mutableStateListOf<Screen>(start)
    var tab by mutableStateOf(Tab.HOME)
    var toast by mutableStateOf<String?>(null)
    var boosters by mutableStateOf(setOf<String>())
    var rewardsFocus by mutableStateOf(0)
    var toastId by mutableIntStateOf(0)
    private var nonce = 0

    val top: Screen get() = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.size - 1) }
    fun replaceAll(s: Screen) { stack.clear(); stack.add(s) }
    fun startRace(level: Int, boosters: Set<String>) {
        nonce++
        val s = Screen.Race(level, boosters, nonce)
        // keep a single race on the stack
        while (stack.size > 1 && stack.last() !is Screen.Main) stack.removeAt(stack.size - 1)
        stack.add(s)
    }
    fun showToast(text: String) { toast = text; toastId++ }
}

val LocalNav = staticCompositionLocalOf<AppNav> { error("No nav") }

@Composable
fun FrostyApp(c: AppContainer, activity: Activity) {
    // Debug automation (autoLevel launch extra) skips the studio ident to keep test runs short.
    val nav = remember { AppNav(if (com.ngstudio.snowrace.ui.screens.LaunchOptions.autoLevel > 0) Screen.Splash else Screen.StudioIntro) }
    CompositionLocalProvider(LocalContainer provides c, LocalNav provides nav) {
        val top = nav.top
        // Sync the 3D layer + music with the current screen.
        LaunchedEffect(top, nav.tab) {
            Telemetry.screen(if (top == Screen.Main) "main_${nav.tab.name.lowercase()}" else screenName(top))
            when (top) {
                // Warm up the 3D layer (shaders, home diorama) behind the opaque ident.
                Screen.StudioIntro -> c.stage.showMenu(MenuMode.HOME)
                Screen.Splash -> { c.stage.showMenu(MenuMode.HOME); c.stage.playMusic(Music.MENU) }
                Screen.Onboarding -> c.stage.playMusic(Music.MENU)
                Screen.Main -> {
                    c.stage.playMusic(Music.MENU)
                    val mode = when (nav.tab) {
                        Tab.HOME -> MenuMode.HOME
                        Tab.SHOP -> MenuMode.SHOP
                        Tab.PROFILE -> MenuMode.PROFILE
                        Tab.LEVELS, Tab.REWARDS -> null
                    }
                    when {
                        // back from a race: rebuild the home diorama behind the loading cover (no visible pop)
                        mode != null && c.stage.raceShown -> c.stage.swapScene { c.stage.showMenu(mode) }
                        mode != null -> c.stage.showMenu(mode)
                        else -> { delay(350); c.stage.setRendering(false) }
                    }
                }
                Screen.Settings, is Screen.Legal -> { delay(350); c.stage.setRendering(false) }
                is Screen.Race -> Unit // RaceScreen drives the stage
            }
        }
        // a tapped notification asked for a tab: open it once the player is on the main screen (never mid-race)
        val wantTab by c.openTab.collectAsState()
        LaunchedEffect(wantTab, top) {
            val t = wantTab ?: return@LaunchedEffect
            if (top == Screen.Settings || top is Screen.Legal) { while (nav.stack.size > 1 && nav.top != Screen.Main) nav.pop(); return@LaunchedEffect }
            if (top != Screen.Main) return@LaunchedEffect // race, intro or tutorial: wait until the player is back on the main screen
            nav.tab = when (t) { "levels" -> Tab.LEVELS; "shop" -> Tab.SHOP; "rewards" -> Tab.REWARDS; "profile" -> Tab.PROFILE; else -> Tab.HOME }
            c.openTab.value = null
        }
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = top,
                transitionSpec = { transitionFor(initialState, targetState) },
                label = "screens",
                contentKey = { s -> if (s is Screen.Race) "race${s.nonce}" else s.toString() },
            ) { screen ->
                when (screen) {
                    Screen.StudioIntro -> StudioIntroScreen { nav.replaceAll(Screen.Splash) }
                    Screen.Splash -> SplashScreen()
                    Screen.Onboarding -> OnboardingScreen()
                    Screen.Main -> MainScreen()
                    Screen.Settings -> SettingsScreen(activity)
                    is Screen.Legal -> LegalScreen(screen.doc)
                    is Screen.Race -> RaceScreen(screen.level, screen.boosters)
                }
            }
            LoadingCoverLayer(c)
            (c.ads as? MockAdProvider)?.let { MockAdOverlay(it) }
            ToastHost(nav)
        }
        FirstLaunchNotificationRequest(enabled = top != Screen.StudioIntro && top !is Screen.Race)
        BackHandler(enabled = nav.stack.size > 1 && top !is Screen.Race && top !is Screen.Onboarding) { c.back(); nav.pop() }
    }
}

private fun transitionFor(from: Screen, to: Screen): ContentTransform = when {
    // The ident fades away on top of a fully drawn splash (nothing from the 3D layer bleeds through).
    from is Screen.StudioIntro -> (EnterTransition.None togetherWith fadeOut(tween(450))).apply { targetContentZIndex = -1f }
    to is Screen.Race || from is Screen.Race -> fadeIn(tween(380)) togetherWith fadeOut(tween(320))
    to is Screen.Settings || to is Screen.Legal -> (slideInVertically(tween(380)) { it / 3 } + fadeIn(tween(300))) togetherWith fadeOut(tween(250))
    from is Screen.Settings || from is Screen.Legal -> fadeIn(tween(300)) togetherWith (slideOutVertically(tween(320)) { it / 3 } + fadeOut(tween(260)))
    else -> (fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 1.04f)) togetherWith fadeOut(tween(350))
}

/** Opaque snowy cover shown while the 3D scene is swapped (level loads, back to home). Swallows touches. */
@Composable
private fun LoadingCoverLayer(c: AppContainer) {
    val cover by c.stage.cover.collectAsState()
    var last by remember { mutableStateOf(LoadingCover()) }
    cover?.let { last = it }
    AnimatedVisibility(
        visible = cover != null,
        enter = fadeIn(tween(Stage.COVER_FADE_IN_MS, easing = LinearEasing)),
        exit = fadeOut(tween(Stage.COVER_FADE_OUT_MS, easing = FastOutSlowInEasing)),
    ) {
        GameBackground(Modifier.pointerInput(Unit) {
            awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
        }) {
            val anim = rememberInfiniteTransition(label = "cover")
            val spin by anim.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "spin")
            val bob by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
            Column(Modifier.align(Alignment.Center).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                UiIcon(R.drawable.ui_snowflake, 76.dp, Modifier.graphicsLayer { rotationZ = spin; translationY = -bob * 14f })
                if (last.title.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    OutlinedText(last.title, style = Type.Huge.copy(fontSize = 46.sp), outlineWidth = 6.dp)
                }
                if (last.subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedText(last.subtitle, style = Type.Label, outlineWidth = 3.dp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ToastHost(nav: AppNav) {
    var visible by remember { mutableStateOf(false) }
    val text = nav.toast
    LaunchedEffect(nav.toastId) {
        if (text != null) { visible = true; delay(2200); visible = false }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 90.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
            Column(
                Modifier.padding(horizontal = 32.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xE61B2A4A))
                    .border(2.dp, Palette.Mint, RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OutlinedText(text ?: "", style = Type.Label, outlineWidth = 3.dp, maxLines = 2)
            }
        }
    }
    Spacer(Modifier.height(0.dp).fillMaxWidth())
}

private fun screenName(s: Screen): String = when (s) {
    Screen.StudioIntro -> "studio_intro"
    Screen.Splash -> "splash"
    Screen.Onboarding -> "onboarding"
    Screen.Main -> "main"
    Screen.Settings -> "settings"
    is Screen.Legal -> "legal_${s.doc.name.lowercase()}"
    is Screen.Race -> "race"
}
