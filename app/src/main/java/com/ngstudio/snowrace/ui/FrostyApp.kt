package com.ngstudio.snowrace.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
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
import androidx.compose.ui.unit.dp
import com.ngstudio.snowrace.ads.MockAdProvider
import com.ngstudio.snowrace.app.AppContainer
import com.ngstudio.snowrace.audio.Music
import com.ngstudio.snowrace.game.render.MenuMode
import com.ngstudio.snowrace.ui.components.LocalContainer
import com.ngstudio.snowrace.ui.components.OutlinedText
import com.ngstudio.snowrace.ui.screens.LegalDoc
import com.ngstudio.snowrace.ui.screens.LegalScreen
import com.ngstudio.snowrace.ui.screens.MainScreen
import com.ngstudio.snowrace.ui.screens.MockAdOverlay
import com.ngstudio.snowrace.ui.screens.OnboardingScreen
import com.ngstudio.snowrace.ui.screens.RaceScreen
import com.ngstudio.snowrace.ui.screens.SettingsScreen
import com.ngstudio.snowrace.ui.screens.SplashScreen
import com.ngstudio.snowrace.ui.theme.Palette
import com.ngstudio.snowrace.ui.theme.Type
import kotlinx.coroutines.delay

sealed class Screen {
    data object Splash : Screen()
    data object Onboarding : Screen()
    data object Main : Screen()
    data object Settings : Screen()
    data class Legal(val doc: LegalDoc) : Screen()
    data class Race(val level: Int, val boosters: Set<String>, val nonce: Int) : Screen()
}

enum class Tab { SHOP, REWARDS, HOME, LEVELS, PROFILE }

class AppNav {
    val stack = mutableStateListOf<Screen>(Screen.Splash)
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
    val nav = remember { AppNav() }
    CompositionLocalProvider(LocalContainer provides c, LocalNav provides nav) {
        val top = nav.top
        // Sync the 3D layer + music with the current screen.
        LaunchedEffect(top, nav.tab) {
            when (top) {
                Screen.Splash -> { c.stage.showMenu(MenuMode.HOME); c.stage.playMusic(Music.MENU) }
                Screen.Onboarding -> c.stage.playMusic(Music.MENU)
                Screen.Main -> {
                    c.stage.playMusic(Music.MENU)
                    when (nav.tab) {
                        Tab.HOME -> c.stage.showMenu(MenuMode.HOME)
                        Tab.SHOP -> c.stage.showMenu(MenuMode.SHOP)
                        Tab.PROFILE -> c.stage.showMenu(MenuMode.PROFILE)
                        Tab.LEVELS, Tab.REWARDS -> { delay(350); c.stage.setRendering(false) }
                    }
                }
                Screen.Settings, is Screen.Legal -> { delay(350); c.stage.setRendering(false) }
                is Screen.Race -> Unit // RaceScreen drives the stage
            }
        }
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = top,
                transitionSpec = { transitionFor(initialState, targetState) },
                label = "screens",
                contentKey = { s -> if (s is Screen.Race) "race${s.nonce}" else s.toString() },
            ) { screen ->
                when (screen) {
                    Screen.Splash -> SplashScreen()
                    Screen.Onboarding -> OnboardingScreen()
                    Screen.Main -> MainScreen()
                    Screen.Settings -> SettingsScreen(activity)
                    is Screen.Legal -> LegalScreen(screen.doc)
                    is Screen.Race -> RaceScreen(screen.level, screen.boosters)
                }
            }
            (c.ads as? MockAdProvider)?.let { MockAdOverlay(it) }
            ToastHost(nav)
        }
        BackHandler(enabled = nav.stack.size > 1 && top !is Screen.Race && top !is Screen.Onboarding) { c.back(); nav.pop() }
    }
}

private fun transitionFor(from: Screen, to: Screen): ContentTransform = when {
    to is Screen.Race || from is Screen.Race -> fadeIn(tween(380)) togetherWith fadeOut(tween(320))
    to is Screen.Settings || to is Screen.Legal -> (slideInVertically(tween(380)) { it / 3 } + fadeIn(tween(300))) togetherWith fadeOut(tween(250))
    from is Screen.Settings || from is Screen.Legal -> fadeIn(tween(300)) togetherWith (slideOutVertically(tween(320)) { it / 3 } + fadeOut(tween(260)))
    else -> (fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 1.04f)) togetherWith fadeOut(tween(350))
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
