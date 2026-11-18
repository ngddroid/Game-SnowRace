package com.ngstudio.snowrace.ui.screens

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngstudio.snowrace.R
import com.ngstudio.snowrace.analytics.Telemetry
import com.ngstudio.snowrace.data.SaveData
import com.ngstudio.snowrace.notify.Notifications
import com.ngstudio.snowrace.ui.components.BodyText
import com.ngstudio.snowrace.ui.components.Buttons
import com.ngstudio.snowrace.ui.components.GameButton
import com.ngstudio.snowrace.ui.components.GameDialog
import com.ngstudio.snowrace.ui.components.LocalContainer
import com.ngstudio.snowrace.ui.components.UiIcon
import com.ngstudio.snowrace.ui.components.pressable
import com.ngstudio.snowrace.ui.theme.Palette
import com.ngstudio.snowrace.ui.theme.Type
import kotlinx.coroutines.delay

/** Whether the system currently lets the game post notifications, plus a way to ask for it. */
class NotificationAccess(val allowed: Boolean, val request: () -> Unit)

/**
 * Wraps the Android 13+ POST_NOTIFICATIONS permission. [allowed] refreshes whenever the screen resumes (e.g. after
 * the player comes back from system settings). With [openSettingsIfBlocked], a request that Android refuses without
 * showing its dialog (the player denied it twice before) opens the app's notification settings instead.
 */
@Composable
fun rememberNotificationAccess(openSettingsIfBlocked: Boolean, onResult: (Boolean) -> Unit = {}): NotificationAccess {
    val ctx = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val allowed = remember(lifecycle, refresh) { Notifications.systemAllows(ctx) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        Telemetry.notificationPermission(granted)
        val blocked = !granted && Build.VERSION.SDK_INT >= 33 &&
            (ctx as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == false
        if (blocked && openSettingsIfBlocked) Notifications.openSystemSettings(ctx)
        onResult(granted)
    }
    return NotificationAccess(allowed) {
        when {
            Notifications.needsPermission(ctx) -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            !Notifications.systemAllows(ctx) -> Notifications.openSystemSettings(ctx)
            else -> onResult(true)
        }
    }
}

/**
 * One friendly in-game ask before Android's permission dialog, shown once on the home screen after the second race
 * (asking at first launch gets far more "Don't allow" taps). Android 12 and older don't need it.
 */
@Composable
fun NotificationAsk(save: SaveData) {
    val c = LocalContainer.current
    val ctx = LocalContext.current
    val s = save.settings
    val eligible = !s.notifPrompted && s.notifications && save.stats.races >= 2 && Notifications.needsPermission(ctx)
    var show by remember { mutableStateOf(false) }
    val access = rememberNotificationAccess(openSettingsIfBlocked = false)
    LaunchedEffect(eligible) {
        if (!eligible) return@LaunchedEffect
        delay(900)
        show = true
    }
    if (!show) return
    fun done() {
        show = false
        c.save.update { it.copy(settings = it.settings.copy(notifPrompted = true)) }
    }
    GameDialog("Never miss a gift!", onDismiss = { done() }, ribbon = Palette.Sky, ribbonDark = Palette.SkyDeep) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            UiIcon(R.drawable.ui_gift, 76.dp)
            BodyText(
                "Get a heads-up when your daily gift and free spin are ready, and when new levels and outfits arrive.",
                align = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            GameButton("Turn On", onClick = { done(); access.request() }, modifier = Modifier.fillMaxWidth(), colors = Buttons.Green, icon = R.drawable.ui_check)
            Spacer(Modifier.height(8.dp))
            BodyText("Not now", color = Palette.Muted, style = Type.Label, modifier = Modifier.pressable { done() }.padding(8.dp))
        }
    }
}

/** "Update ready" pill on the home screen once a flexible Play update has downloaded. */
@Composable
fun UpdateReadyBanner(modifier: Modifier = Modifier) {
    val c = LocalContainer.current
    val ready by c.updater.ready.collectAsState()
    AnimatedVisibility(ready, modifier, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 4.dp).fillMaxWidth()
                .clip(RoundedCornerShape(18.dp)).background(Color(0xE61B2A4A)).border(2.dp, Palette.Gold, RoundedCornerShape(18.dp))
                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiIcon(R.drawable.ui_gift, 30.dp)
            Spacer(Modifier.width(8.dp))
            BodyText("A new version is ready!", color = Color.White, style = Type.Label, modifier = Modifier.weight(1f))
            GameButton(
                "Restart", onClick = { c.save.flushBlocking(); c.updater.complete() },
                modifier = Modifier.width(118.dp), colors = Buttons.Green, height = 40.dp, textStyle = Type.Label,
            )
        }
    }
}
