package com.ngstudio.snowrace.ui.screens

import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ngstudio.snowrace.BuildConfig
import com.ngstudio.snowrace.R
import com.ngstudio.snowrace.ads.MockAdProvider
import com.ngstudio.snowrace.audio.Sfx
import com.ngstudio.snowrace.data.Catalog
import com.ngstudio.snowrace.data.GameRules
import com.ngstudio.snowrace.data.TimeUtil
import com.ngstudio.snowrace.game.render.MenuMode
import com.ngstudio.snowrace.ui.LocalNav
import com.ngstudio.snowrace.ui.Screen
import com.ngstudio.snowrace.ui.components.BodyText
import com.ngstudio.snowrace.ui.components.Buttons
import com.ngstudio.snowrace.ui.components.GameBackground
import com.ngstudio.snowrace.ui.components.GameButton
import com.ngstudio.snowrace.ui.components.GameProgressBar
import com.ngstudio.snowrace.ui.components.LocalContainer
import com.ngstudio.snowrace.ui.components.OutlinedText
import com.ngstudio.snowrace.ui.components.Panel
import com.ngstudio.snowrace.ui.components.SnowfallLayer
import com.ngstudio.snowrace.ui.components.StudioCredit
import com.ngstudio.snowrace.ui.components.UiIcon
import com.ngstudio.snowrace.ui.components.pressable
import com.ngstudio.snowrace.ui.theme.Palette
import com.ngstudio.snowrace.ui.theme.Type
import kotlinx.coroutines.delay
import kotlin.math.min

// =============================================================================== Splash
@Composable
fun SplashScreen() {
    val c = LocalContainer.current
    val nav = LocalNav.current
    val logo = remember { Animatable(0f) }
    val mascot = remember { Animatable(0f) }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        logo.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 180f))
    }
    LaunchedEffect(Unit) {
        delay(350)
        mascot.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 140f))
    }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        val renderer = c.stage.renderer
        while (true) {
            val glReady = renderer?.ready?.value ?: true
            val audio = c.audio.loadProgress
            val time = min(1f, (System.currentTimeMillis() - start) / 1700f)
            val real = (if (glReady) 0.45f else 0.15f) + audio * 0.35f + (if (c.levelSaltsLoaded) 0.2f else 0f)
            progress = min(time, real.coerceAtMost(1f))
            if (progress >= 0.999f) break
            if (System.currentTimeMillis() - start > 6000) { progress = 1f; break }
            delay(32)
        }
        delay(250)
        c.save.update { GameRules.refreshMissions(it, TimeUtil.today()) }
        val auto = LaunchOptions.autoLevel
        if (auto > 0) {
            // debug automation: adb shell am start ... --ei autoLevel N --ez autopilot true
            LaunchOptions.autoLevel = 0
            c.save.update { it.copy(profile = it.profile.copy(onboardingDone = true)) }
            DebugFlags.autopilot = LaunchOptions.autopilot
            nav.replaceAll(Screen.Main)
            nav.startRace(auto, emptySet())
        } else if (c.save.data.profile.onboardingDone) nav.replaceAll(Screen.Main) else nav.replaceAll(Screen.Onboarding)
    }
    GameBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.9f))
            UiIcon(R.drawable.logo_frosty, 300.dp, Modifier.graphicsLayer {
                val v = logo.value
                scaleX = 0.4f + 0.6f * v; scaleY = 0.4f + 0.6f * v
                translationY = (1f - v) * -300f
                alpha = v.coerceIn(0f, 1f)
            })
            Spacer(Modifier.height(4.dp))
            val inf = rememberInfiniteTransition(label = "bob")
            val bob by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
            UiIcon(R.drawable.mascot_pip, 220.dp, Modifier.graphicsLayer {
                val v = mascot.value
                translationY = (1f - v) * 600f - bob * 18f
                rotationZ = (bob - 0.5f) * 4f
                alpha = v.coerceIn(0f, 1f)
            })
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth().padding(horizontal = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                GameProgressBar(progress, Modifier.fillMaxWidth(), height = 20.dp, fill = Brush.horizontalGradient(listOf(Palette.Mint, Palette.Sky, Palette.Berry)))
                Spacer(Modifier.height(10.dp))
                OutlinedText(loadingLine(progress), style = Type.Label, outlineWidth = 3.dp)
            }
            Spacer(Modifier.height(18.dp))
            StudioCredit(logoWidth = 96.dp)
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun loadingLine(p: Float) = when {
    p < 0.3f -> "Packing snowballs..."
    p < 0.6f -> "Waking up the Snowpals..."
    p < 0.9f -> "Polishing the ice..."
    else -> "Let's roll!"
}

/** Debug-only launch options (set from intent extras in MainActivity). */
object LaunchOptions {
    @Volatile var autoLevel = 0
    @Volatile var autopilot = false
}

// =============================================================================== Onboarding
private data class Page(val title: String, val body: String, val mode: MenuMode)

private val pages = listOf(
    Page("Roll to Grow", "Hold and drag anywhere to steer your Snowpal. Roll over fresh snow and watch your snowball grow!", MenuMode.ONBOARD_ROLL),
    Page("Build Bridges", "Push your snowball into the water to pack it into a bridge. The bigger the ball, the longer the bridge!", MenuMode.ONBOARD_BRIDGE),
    Page("Bigger Bonks", "A bigger snowball bonks smaller rivals out of the way. Grow big, then go for it!", MenuMode.ONBOARD_BONK),
    Page("Collect Them All", "Win races, earn coins and unlock adorable Snowpals, snowball skins and sparkly trails!", MenuMode.ONBOARD_COLLECT),
)

@Composable
fun OnboardingScreen() {
    val c = LocalContainer.current
    val nav = LocalNav.current
    var page by remember { mutableIntStateOf(0) }
    val total = pages.size + 1 // last step: name & starter
    LaunchedEffect(Unit) { com.ngstudio.snowrace.analytics.Telemetry.tutorialBegin() }
    LaunchedEffect(page) {
        if (page < pages.size) c.stage.showMenu(pages[page].mode) else c.stage.showMenu(MenuMode.HOME)
    }
    // No skip: back goes to the previous page only.
    BackHandler(enabled = page > 0) { c.back(); page-- }
    Box(Modifier.fillMaxSize()) {
        // gradient so text reads well over the 3D scene
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xE61E1B4B))))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
                UiIcon(R.drawable.logo_frosty, 150.dp)
            }
            Spacer(Modifier.weight(1f))
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(380)) { it * dir } + fadeIn(tween(300))) togetherWith (slideOutHorizontally(tween(380)) { -it * dir } + fadeOut(tween(250)))
                },
                label = "onb",
            ) { p ->
                if (p < pages.size) {
                    Panel(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        OutlinedText(pages[p].title, style = Type.Title, modifier = Modifier.fillMaxWidth(), brush = Brush.verticalGradient(listOf(Color.White, Palette.Ice)))
                        Spacer(Modifier.height(8.dp))
                        BodyText(pages[p].body, Modifier.fillMaxWidth(), align = TextAlign.Center)
                    }
                } else {
                    StarterPicker { name, starter ->
                        c.save.update { s ->
                            var out = GameRules.grant(s, starter)
                            out = out.copy(profile = out.profile.copy(name = name, character = starter, onboardingDone = true))
                            GameRules.refreshMissions(out, TimeUtil.today())
                        }
                        com.ngstudio.snowrace.analytics.Telemetry.tutorialComplete()
                        c.audio.play(Sfx.UNLOCK)
                        nav.replaceAll(Screen.Main)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            if (page < pages.size) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    for (i in 0 until total) {
                        val active = i == page
                        Box(
                            Modifier.padding(4.dp).height(12.dp).width(if (active) 30.dp else 12.dp).clip(CircleShape)
                                .background(if (active) Palette.Mint else Color(0x66FFFFFF)).border(2.dp, Palette.Navy, CircleShape),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                GameButton(
                    if (page == pages.size - 1) "Let's Roll!" else "Next",
                    onClick = { page++ },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                    colors = if (page == pages.size - 1) Buttons.Berry else Buttons.Green,
                    shine = true,
                )
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
private fun StarterPicker(onDone: (String, String) -> Unit) {
    val c = LocalContainer.current
    var name by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf("pip") }
    val focus = LocalFocusManager.current
    LaunchedEffect(pick) { c.stage.preview(pick, null, null, MenuMode.HOME) }
    Panel(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        OutlinedText("Your Snowpal", style = Type.Title, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (id in listOf("pip", "minty", "bubbles")) {
                val def = Catalog.character(id)
                val sel = id == pick
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.pressable { pick = id; c.stage.wave() }) {
                    Box(
                        Modifier.size(64.dp).scale(if (sel) 1.08f else 1f).clip(CircleShape)
                            .background(Color(def.body))
                            .border(if (sel) 4.dp else 2.dp, if (sel) Palette.Berry else Palette.Navy, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(def.belly)))
                    }
                    BodyText(def.name, style = Type.Label, color = if (sel) Palette.Berry else Palette.Navy)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        BodyText("What should we call you?", Modifier.fillMaxWidth(), align = TextAlign.Center, style = Type.BodySmall)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).border(3.dp, Palette.Navy, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (name.isEmpty()) BodyText("Enter a nickname", color = Palette.Muted, style = Type.Label.copy(fontSize = 18.sp))
            BasicTextField(
                value = name,
                onValueChange = { v -> name = v.filter { it.isLetterOrDigit() || it == ' ' || it == '_' }.take(12) },
                singleLine = true,
                textStyle = Type.Label.copy(fontSize = 20.sp, color = Palette.Navy, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(Palette.Berry),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
        GameButton("Let's Roll!", onClick = {
            focus.clearFocus()
            onDone(name.trim().ifEmpty { "Player" }, pick)
        }, modifier = Modifier.fillMaxWidth(), colors = Buttons.Berry, shine = true)
    }
}

// =============================================================================== Legal
enum class LegalDoc(val file: String, val title: String) { TERMS("terms.html", "Terms of Service"), PRIVACY("privacy.html", "Privacy Policy") }

@Composable
fun LegalScreen(doc: LegalDoc) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val studio = stringResource(R.string.legal_studio_name)
    val email = stringResource(R.string.legal_contact_email)
    val effective = stringResource(R.string.legal_effective_date)
    val html = remember(doc, appName, studio, email, effective) {
        val raw = ctx.assets.open("legal/${doc.file}").bufferedReader(Charsets.UTF_8).use { it.readText() }
        fun esc(s: String) = TextUtils.htmlEncode(s)
        raw.replace("{{APP_NAME}}", esc(appName))
            .replace("{{STUDIO_NAME}}", esc(studio))
            .replace("{{CONTACT_EMAIL}}", esc(email))
            .replace("{{EFFECTIVE_DATE}}", esc(effective))
            .replace("{{APP_VERSION}}", esc(BuildConfig.VERSION_NAME))
    }
    Column(Modifier.fillMaxSize().background(Palette.bgGradient)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).pressable { nav.pop() }, contentAlignment = Alignment.Center) { UiIcon(R.drawable.ui_back, 46.dp) }
            Spacer(Modifier.width(8.dp))
            UiIcon(if (doc == LegalDoc.TERMS) R.drawable.ui_doc else R.drawable.ui_privacy, 34.dp)
            Spacer(Modifier.width(8.dp))
            OutlinedText(doc.title, style = Type.Heading, outlineWidth = 3.dp)
        }
        Box(
            Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, bottom = 10.dp).navigationBarsPadding()
                .clip(RoundedCornerShape(22.dp)).border(3.dp, Palette.Navy, RoundedCornerShape(22.dp)),
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = false
                        settings.allowFileAccess = true
                        settings.allowContentAccess = false
                        settings.blockNetworkLoads = true
                        setBackgroundColor(android.graphics.Color.parseColor("#DFF6FF"))
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val url = request.url
                                if (url.scheme == "mailto") {
                                    try { context.startActivity(Intent(Intent.ACTION_SENDTO, url)) } catch (_: Throwable) { }
                                    return true
                                }
                                return url.scheme != "file"
                            }
                        }
                        loadDataWithBaseURL("file:///android_asset/legal/", html, "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    LaunchedEffect(Unit) { c.audio.play(Sfx.UI_OPEN) }
}

// =============================================================================== Mock ad (debug only)
@Composable
fun MockAdOverlay(provider: MockAdProvider) {
    val req by provider.request.collectAsState()
    val r = req ?: return
    var left by remember(r) { mutableIntStateOf(2) }
    LaunchedEffect(r) {
        while (left > 0) { delay(1000); left-- }
        delay(250)
        provider.complete(true) // debug: the simulated ad always rewards
    }
    Box(Modifier.fillMaxSize().background(Color(0xF20B0A22)), contentAlignment = Alignment.Center) {
        SnowfallLayer(count = 30)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Palette.Gold).border(2.dp, Palette.Navy, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                BodyText("DEBUG AD", style = Type.Label, color = Palette.Navy)
            }
            Spacer(Modifier.height(18.dp))
            UiIcon(R.drawable.ui_ad, 120.dp)
            Spacer(Modifier.height(10.dp))
            OutlinedText("Simulated rewarded ad", style = Type.Heading, outlineWidth = 3.dp)
            BodyText("Debug builds only. Real ads plug in later.", color = Color(0xFFBFD9F2), style = Type.BodySmall, align = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Box(Modifier.size(72.dp).clip(CircleShape).background(Color(0x33FFFFFF)).border(3.dp, Palette.Mint, CircleShape), contentAlignment = Alignment.Center) {
                if (left > 0) OutlinedText("$left", style = Type.Title) else UiIcon(R.drawable.ui_check, 48.dp)
            }
            Spacer(Modifier.height(14.dp))
            BodyText("Close (no reward)", color = Color(0xFF8FA6C9), style = Type.BodySmall, modifier = Modifier.pressable { provider.complete(false) }.padding(8.dp))
        }
    }
    BackHandler { provider.complete(false) }
    Spacer(Modifier.offset(0.dp))
}
