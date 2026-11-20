package com.ngstudio.snowrace

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.ngstudio.snowrace.analytics.Telemetry
import com.ngstudio.snowrace.notify.Notifications
import com.ngstudio.snowrace.notify.Reminders
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ngstudio.snowrace.app.container
import com.ngstudio.snowrace.game.render.GameRenderer
import com.ngstudio.snowrace.game.render.GameSurfaceView
import com.ngstudio.snowrace.ui.FrostyApp

class MainActivity : ComponentActivity() {
    private var surface: GameSurfaceView? = null

    private val updateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        container.updater.onFlowResult(r.resultCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val c = container
        c.updater.bind(updateLauncher)
        if (savedInstanceState == null) handleNotificationTap(intent)
        if (BuildConfig.DEBUG) {
            com.ngstudio.snowrace.ui.screens.LaunchOptions.autoLevel = intent.getIntExtra("autoLevel", 0)
            com.ngstudio.snowrace.ui.screens.LaunchOptions.autopilot = intent.getBooleanExtra("autopilot", false)
            if (intent.hasExtra("xmas")) com.ngstudio.snowrace.game.render.Holiday.force = intent.getBooleanExtra("xmas", true)
        }
        val settings = c.save.data.settings
        val renderer = GameRenderer(applicationContext, c.audio, c.haptics, settings.quality)
        val gl = GameSurfaceView(this, renderer, msaa = settings.quality != 1)
        surface = gl
        c.stage.attach(gl, renderer)

        val compose = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { FrostyApp(c, this@MainActivity) }
        }
        val root = FrameLayout(this)
        root.addView(gl, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(compose, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationTap(intent)
    }

    /** A tapped reminder / push: count it and open the tab it points at. */
    private fun handleNotificationTap(i: Intent?) {
        val extras = i?.extras ?: return
        val kind = extras.getString(Notifications.EXTRA_KIND)
            ?: if (extras.containsKey("google.message_id")) "push" else return
        Telemetry.notificationOpen(kind)
        extras.getString(Notifications.EXTRA_SCREEN)?.let { container.openTab.value = it }
        i.removeExtra(Notifications.EXTRA_KIND)
        i.removeExtra("google.message_id")
    }

    override fun onStart() {
        super.onStart()
        // the player is back: drop the pending "come back" reminders
        Reminders.cancel(this)
    }

    override fun onResume() {
        super.onResume()
        surface?.onResume()
        container.audio.onResume()
        container.updater.onResume()
    }

    override fun onStop() {
        Reminders.schedule(this, container.save.data)
        super.onStop()
    }

    override fun onPause() {
        surface?.onPause()
        container.audio.onPause()
        container.save.flush()
        super.onPause()
    }

    override fun onDestroy() {
        container.updater.unbind(updateLauncher)
        if (isFinishing) container.stage.detach()
        super.onDestroy()
    }
}
