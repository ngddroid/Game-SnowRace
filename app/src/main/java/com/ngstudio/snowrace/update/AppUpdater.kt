package com.ngstudio.snowrace.update

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.ngstudio.snowrace.BuildConfig
import com.ngstudio.snowrace.analytics.Telemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Google Play in-app updates.
 *
 * - Checked once per launch when the home screen opens, never during a race.
 * - Normal releases use the FLEXIBLE flow: Play asks, the update downloads while the player keeps playing, and the
 *   home screen shows "Update ready - Restart". If the player says no, we don't ask again for that version for 3 days.
 * - Releases published with in-app update priority 4 or 5 (set through the Play Developer API when you upload, e.g.
 *   for a crash fix) use the IMMEDIATE full-screen flow instead.
 *
 * Play only answers for installs that came from Play (internal testing / internal app sharing count); anywhere else
 * the check fails quietly. Debug builds can simulate the flexible flow from Settings > Debug tools.
 */
class AppUpdater(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("app_update", Context.MODE_PRIVATE)
    private var manager: AppUpdateManager = AppUpdateManagerFactory.create(app)
    private var fake: FakeAppUpdateManager? = null
    private var launcher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var checked = false
    private var pendingVersion = 0
    private var pendingType = AppUpdateType.FLEXIBLE

    private val _ready = MutableStateFlow(false)
    /** A flexible update finished downloading: show the "Restart to update" banner. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val listener = InstallStateUpdatedListener { st ->
        when (st.installStatus()) {
            InstallStatus.DOWNLOADED -> { _ready.value = true; Telemetry.appUpdate("downloaded") }
            InstallStatus.FAILED -> Telemetry.appUpdate("failed")
            else -> Unit
        }
    }

    init { manager.registerListener(listener) }

    /** MainActivity registers the Play update dialog launcher here. */
    fun bind(l: ActivityResultLauncher<IntentSenderRequest>) { launcher = l }

    fun unbind(l: ActivityResultLauncher<IntentSenderRequest>) { if (launcher === l) launcher = null }

    /** Home screen opened: ask Play once per launch. */
    fun check() {
        if (checked) return
        checked = true
        manager.appUpdateInfo.addOnSuccessListener(::decide).addOnFailureListener { Log.d(TAG, "update check unavailable: ${it.message}") }
    }

    /** Activity resumed: finish what an earlier session started (downloaded update, interrupted immediate update). */
    fun onResume() {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) _ready.value = true
            else if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) start(info, AppUpdateType.IMMEDIATE)
        }
    }

    private fun decide(info: AppUpdateInfo) {
        if (info.installStatus() == InstallStatus.DOWNLOADED) { _ready.value = true; return }
        if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return
        val version = info.availableVersionCode()
        when {
            info.updatePriority() >= 4 && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> start(info, AppUpdateType.IMMEDIATE)
            info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) && !recentlyDeclined(version) -> start(info, AppUpdateType.FLEXIBLE)
        }
    }

    private fun start(info: AppUpdateInfo, type: Int) {
        val l = launcher ?: return
        pendingVersion = info.availableVersionCode()
        pendingType = type
        Telemetry.appUpdate(if (type == AppUpdateType.IMMEDIATE) "prompt_immediate" else "prompt_flexible")
        try {
            manager.startUpdateFlowForResult(info, l, AppUpdateOptions.newBuilder(type).build())
        } catch (t: Throwable) {
            Log.w(TAG, "update flow failed", t)
            return
        }
        fake?.let { f -> // the fake manager has no UI: play the whole flexible flow through
            f.userAcceptsUpdate(); f.downloadStarts(); f.downloadCompletes()
        }
    }

    /** Result of Play's update dialog. */
    fun onFlowResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            Telemetry.appUpdate("accepted")
        } else {
            Telemetry.appUpdate("declined")
            if (pendingType == AppUpdateType.FLEXIBLE) prefs.edit().putInt(KEY_VERSION, pendingVersion).putLong(KEY_AT, System.currentTimeMillis()).apply()
        }
    }

    private fun recentlyDeclined(version: Int): Boolean =
        prefs.getInt(KEY_VERSION, -1) == version && System.currentTimeMillis() - prefs.getLong(KEY_AT, 0L) < ASK_AGAIN_MS

    /** "Restart" on the banner: Play installs the downloaded update and relaunches the game. */
    fun complete() {
        Telemetry.appUpdate("install")
        _ready.value = false
        manager.completeUpdate()
        fake?.installCompletes()
    }

    /** Debug only: pretend Play has a newer version, then run the flexible flow. */
    fun simulate() {
        if (!BuildConfig.DEBUG) return
        manager.unregisterListener(listener)
        val f = FakeAppUpdateManager(app).apply { setUpdateAvailable(BuildConfig.VERSION_CODE + 1, AppUpdateType.FLEXIBLE) }
        fake = f
        manager = f
        manager.registerListener(listener)
        prefs.edit().clear().apply()
        checked = false
        check()
    }

    private companion object {
        const val TAG = "AppUpdater"
        const val KEY_VERSION = "declined_version"
        const val KEY_AT = "declined_at"
        const val ASK_AGAIN_MS = 3L * 24 * 3_600_000
    }
}
