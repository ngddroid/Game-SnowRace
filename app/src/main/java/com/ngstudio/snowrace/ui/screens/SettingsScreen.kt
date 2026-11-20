package com.ngstudio.snowrace.ui.screens

import android.app.Activity
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ngstudio.snowrace.BuildConfig
import com.ngstudio.snowrace.R
import com.ngstudio.snowrace.analytics.Telemetry
import com.ngstudio.snowrace.notify.Push
import com.ngstudio.snowrace.notify.Reminders
import com.ngstudio.snowrace.data.MAX_LEVEL
import com.ngstudio.snowrace.data.Settings
import com.ngstudio.snowrace.ui.LocalNav
import com.ngstudio.snowrace.ui.Screen
import com.ngstudio.snowrace.ui.components.BodyText
import com.ngstudio.snowrace.ui.components.Buttons
import com.ngstudio.snowrace.ui.components.GameBackground
import com.ngstudio.snowrace.ui.components.GameButton
import com.ngstudio.snowrace.ui.components.GameDialog
import com.ngstudio.snowrace.ui.components.GameToggle
import com.ngstudio.snowrace.ui.components.LocalContainer
import com.ngstudio.snowrace.ui.components.OutlinedText
import com.ngstudio.snowrace.ui.components.Panel
import com.ngstudio.snowrace.ui.components.StudioCredit
import com.ngstudio.snowrace.ui.components.UiIcon
import com.ngstudio.snowrace.ui.components.pressable
import com.ngstudio.snowrace.ui.theme.Palette
import com.ngstudio.snowrace.ui.theme.Type

@Composable
fun SettingsScreen(activity: Activity) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    val save by c.save.state.collectAsState()
    val s = save.settings
    var confirmReset by remember { mutableStateOf(false) }
    var restartNote by remember { mutableStateOf(false) }

    fun set(block: (Settings) -> Settings) {
        c.save.update { it.copy(settings = block(it.settings)) }
        c.applySettings(c.save.data.settings)
    }

    GameBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).pressable { nav.pop() }) { UiIcon(R.drawable.ui_back, 48.dp) }
                Spacer(Modifier.width(10.dp))
                UiIcon(R.drawable.ui_gear, 38.dp)
                Spacer(Modifier.width(6.dp))
                OutlinedText("Settings", style = Type.Title, outlineWidth = 4.dp)
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
                Panel(Modifier.fillMaxWidth()) {
                    SectionHeader("Sound")
                    ToggleRow(R.drawable.ui_music, "Music", s.music) { v -> set { it.copy(music = v) } }
                    VolumeRow(s.musicVolume, s.music) { v -> set { it.copy(musicVolume = v) } }
                    ToggleRow(R.drawable.ui_sound, "Sound Effects", s.sfx) { v -> set { it.copy(sfx = v) }; if (v) c.tap() }
                    VolumeRow(s.sfxVolume, s.sfx) { v -> set { it.copy(sfxVolume = v) } }
                    ToggleRow(R.drawable.ui_vibrate, "Vibration", s.haptics) { v -> set { it.copy(haptics = v) }; if (v) c.haptics.play(com.ngstudio.snowrace.audio.Haptic.CLICK) }
                }
                Spacer(Modifier.height(12.dp))
                Panel(Modifier.fillMaxWidth()) {
                    SectionHeader("Gameplay")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        UiIcon(R.drawable.ui_graphics, 36.dp)
                        Spacer(Modifier.width(8.dp))
                        BodyText("Graphics", style = Type.Label, modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Auto", "Low", "Medium", "High").forEachIndexed { i, label ->
                            val on = s.quality == i
                            Box(
                                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (on) Brush.verticalGradient(listOf(Color(0xFF7DFBDC), Palette.MintDeep)) else Brush.verticalGradient(listOf(Color(0xFFE6F1FC), Color(0xFFCADCEF))))
                                    .border(2.5.dp, Palette.Navy, RoundedCornerShape(12.dp))
                                    .pressable { if (s.quality != i) { set { it.copy(quality = i) }; restartNote = true } },
                                contentAlignment = Alignment.Center,
                            ) { if (on) OutlinedText(label, style = Type.Label, outlineWidth = 3.dp) else BodyText(label, style = Type.Label) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        UiIcon(R.drawable.ui_controller, 36.dp)
                        Spacer(Modifier.width(8.dp))
                        BodyText("Steering sensitivity", style = Type.Label, modifier = Modifier.weight(1f))
                    }
                    Slider(
                        value = s.sensitivity, onValueChange = { v -> set { it.copy(sensitivity = v) } }, valueRange = 0.6f..1.6f,
                        colors = SliderDefaults.colors(thumbColor = Palette.Berry, activeTrackColor = Palette.Berry, inactiveTrackColor = Palette.PanelEdge),
                    )
                    Spacer(Modifier.height(6.dp))
                    GameButton("Replay Tutorial", onClick = {
                        c.save.update { it.copy(profile = it.profile.copy(onboardingDone = false)) }
                        nav.replaceAll(Screen.Onboarding)
                    }, modifier = Modifier.fillMaxWidth(), colors = Buttons.Sky, height = 50.dp, icon = R.drawable.ui_restart)
                }
                Spacer(Modifier.height(12.dp))
                Panel(Modifier.fillMaxWidth()) {
                    SectionHeader("Notifications & Privacy")
                    val notif = rememberNotificationAccess(openSettingsIfBlocked = true)
                    ToggleRow(R.drawable.ui_gift, "Notifications", s.notifications && notif.allowed) { v ->
                        c.setNotifications(v)
                        if (v && !notif.allowed) notif.request()
                    }
                    BodyText(
                        if (s.notifications && !notif.allowed) "Turned off in Android settings. Switch on to allow them again."
                        else "Daily gift reminders and news about new levels and events.",
                        color = Palette.Muted, style = Type.Small, modifier = Modifier.padding(start = 44.dp),
                    )
                    ToggleRow(R.drawable.ui_privacy, "Share usage & crash data", s.shareData) { v -> c.setShareData(v) }
                    BodyText(
                        "Anonymous game stats and crash reports help us fix bugs and balance levels. No ads tracking, no account.",
                        color = Palette.Muted, style = Type.Small, modifier = Modifier.padding(start = 44.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Panel(Modifier.fillMaxWidth()) {
                    SectionHeader("About")
                    LinkRow(R.drawable.ui_doc, "Terms of Service") { nav.push(Screen.Legal(LegalDoc.TERMS)) }
                    LinkRow(R.drawable.ui_privacy, "Privacy Policy") { nav.push(Screen.Legal(LegalDoc.PRIVACY)) }
                    Spacer(Modifier.height(8.dp))
                    GameButton("Reset Progress", onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth(), colors = Buttons.Coral, height = 50.dp, icon = R.drawable.ui_trash)
                    Spacer(Modifier.height(10.dp))
                    BodyText("Version ${BuildConfig.VERSION_NAME}${if (BuildConfig.DEBUG) " (debug)" else ""}", Modifier.fillMaxWidth(), color = Palette.Muted, style = Type.Small, align = TextAlign.Center)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        StudioCredit(inline = true, logoWidth = 64.dp)
                    }
                }
                if (BuildConfig.DEBUG) {
                    Spacer(Modifier.height(12.dp))
                    Panel(Modifier.fillMaxWidth()) {
                        SectionHeader("Debug tools")
                        BodyText("Visible in debug builds only.", color = Palette.Muted, style = Type.Small)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GameButton("+5000", onClick = { c.save.update { it.copy(economy = it.economy.copy(coins = it.economy.coins + 5000)) } }, modifier = Modifier.weight(1f), height = 44.dp, colors = Buttons.Gold, icon = R.drawable.ui_coin, textStyle = Type.Label)
                            GameButton("+100", onClick = { c.save.update { it.copy(economy = it.economy.copy(gems = it.economy.gems + 100)) } }, modifier = Modifier.weight(1f), height = 44.dp, colors = Buttons.Berry, icon = R.drawable.ui_gem, textStyle = Type.Label)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GameButton("Unlock +10", onClick = { c.save.update { it.copy(progress = it.progress.copy(highestUnlocked = (it.progress.highestUnlocked + 10).coerceAtMost(MAX_LEVEL))) } }, modifier = Modifier.weight(1f), height = 44.dp, colors = Buttons.Sky, textStyle = Type.Label)
                            GameButton("Unlock all", onClick = { c.save.update { it.copy(progress = it.progress.copy(highestUnlocked = MAX_LEVEL)) } }, modifier = Modifier.weight(1f), height = 44.dp, colors = Buttons.Purple, textStyle = Type.Label)
                        }
                        Spacer(Modifier.height(6.dp))
                        GameButton("Autopilot next race", onClick = { nav.showToast("Autopilot armed for the next race"); DebugFlags.autopilot = true }, modifier = Modifier.fillMaxWidth(), height = 44.dp, colors = Buttons.Mint, textStyle = Type.Label)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GameButton("Test reminders", onClick = { Reminders.scheduleTest(activity); nav.showToast("4 reminders in 5-14 s: leave the game") }, modifier = Modifier.weight(1f), height = 44.dp, colors = Buttons.Sky, textStyle = Type.Label)
                            GameButton("Fake update", onClick = { c.updater.simulate(); nav.showToast("Update downloaded: see Home") }, modifier = Modifier.weight(1f), height = 44.dp, colors = Buttons.Gold, textStyle = Type.Label)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GameButton("Log FCM token", onClick = { Push.logToken(); nav.showToast(if (Telemetry.available) "Token in logcat (tag Push)" else "No google-services.json") }, modifier = Modifier.weight(1f), height = 44.dp, colors = Buttons.Purple, textStyle = Type.Label)
                            GameButton("Test crash", onClick = { throw RuntimeException("Crashlytics test crash (debug button)") }, modifier = Modifier.weight(1f), height = 44.dp, colors = Buttons.Coral, textStyle = Type.Label)
                        }
                        BodyText(
                            "Firebase: " + (if (!Telemetry.available) "not configured (add app/google-services.json)" else if (Telemetry.active) "sending" else "off (Share usage & crash data is off)"),
                            color = Palette.Muted, style = Type.Small,
                        )
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    if (confirmReset) {
        GameDialog("Reset Progress?", onDismiss = { confirmReset = false }, ribbon = Palette.Coral, ribbonDark = Palette.CoralDeep) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                UiIcon(R.drawable.ui_trash, 72.dp)
                BodyText("All levels, coins, gems and unlocked items on this device will be erased. This cannot be undone.", align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                GameButton("Erase Everything", onClick = {
                    c.save.resetAll(); Telemetry.resetIdentity(); confirmReset = false; nav.showToast("Progress reset")
                    nav.replaceAll(Screen.Main)
                }, modifier = Modifier.fillMaxWidth(), colors = Buttons.Coral)
                Spacer(Modifier.height(8.dp))
                BodyText("Keep my progress", color = Palette.Muted, style = Type.Label, modifier = Modifier.pressable { confirmReset = false }.padding(8.dp))
            }
        }
    }
    if (restartNote) {
        GameDialog("Graphics", onDismiss = { restartNote = false }, ribbon = Palette.Sky, ribbonDark = Palette.SkyDeep) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                BodyText("Apply the new graphics quality now? The game will reload for a moment.", align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                GameButton("Apply Now", onClick = { restartNote = false; c.save.flush(); activity.recreate() }, modifier = Modifier.fillMaxWidth(), colors = Buttons.Green)
                Spacer(Modifier.height(8.dp))
                BodyText("Later", color = Palette.Muted, style = Type.Label, modifier = Modifier.pressable { restartNote = false }.padding(8.dp))
            }
        }
    }
}

object DebugFlags { @Volatile var autopilot = false }

@Composable
private fun SectionHeader(t: String) {
    OutlinedText(t, style = Type.Heading, color = Palette.Navy, outline = Color.White, outlineWidth = 3.dp)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ToggleRow(icon: Int, label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        UiIcon(icon, 36.dp)
        Spacer(Modifier.width(8.dp))
        BodyText(label, style = Type.Label, modifier = Modifier.weight(1f))
        GameToggle(value, onChange)
    }
}

@Composable
private fun VolumeRow(value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    Slider(
        value = value, onValueChange = onChange, enabled = enabled, modifier = Modifier.padding(start = 44.dp),
        colors = SliderDefaults.colors(thumbColor = Palette.Sky, activeTrackColor = Palette.Sky, inactiveTrackColor = Palette.PanelEdge),
    )
}

@Composable
private fun LinkRow(icon: Int, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFE8F3FF)).border(2.dp, Palette.PanelEdge, RoundedCornerShape(16.dp)).pressable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UiIcon(icon, 36.dp)
        Spacer(Modifier.width(10.dp))
        BodyText(label, style = Type.Label, modifier = Modifier.weight(1f))
        UiIcon(R.drawable.ui_play, 26.dp)
    }
}
