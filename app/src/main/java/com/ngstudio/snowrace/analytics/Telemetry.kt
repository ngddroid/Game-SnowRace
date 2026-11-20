package com.ngstudio.snowrace.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.ngstudio.snowrace.BuildConfig
import com.ngstudio.snowrace.game.RaceResult

/**
 * Firebase Analytics + Crashlytics behind one switch: the "Share usage & crash data" setting.
 *
 * - Without app/google-services.json (see app/build.gradle.kts) Firebase isn't initialised and every call here is a no-op.
 * - Debug builds send too, so crashes and events can be tested from Android Studio. To watch events live in the
 *   Firebase DebugView: `adb shell setprop debug.firebase.analytics.app com.ngstudio.frostyroll`, then restart the app
 *   (`.none.` turns it off again). Filter debug devices out of reports with the "Debug" stream or a test device list.
 * - No advertising ID, no user ID, no nickname: events only carry game facts (level, rank, item ids).
 */
object Telemetry {
    private const val TAG = "Telemetry"
    private var analytics: FirebaseAnalytics? = null
    private var crashlytics: FirebaseCrashlytics? = null

    /** True when events and crash reports are actually being sent. */
    @Volatile var active = false
        private set

    /** Firebase is configured for this build (google-services.json was present). */
    val available: Boolean get() = analytics != null

    fun init(context: Context, consent: Boolean) {
        if (!BuildConfig.FIREBASE_CONFIGURED) return
        try {
            if (FirebaseApp.getApps(context).isEmpty() && FirebaseApp.initializeApp(context) == null) return
            analytics = FirebaseAnalytics.getInstance(context)
            crashlytics = FirebaseCrashlytics.getInstance()
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase unavailable", t)
            analytics = null; crashlytics = null
            return
        }
        setConsent(consent)
    }

    fun setConsent(on: Boolean) {
        val a = analytics ?: return
        val send = on
        active = send
        a.setAnalyticsCollectionEnabled(send)
        // Consent mode: analytics storage follows the setting; nothing ad-related until an ad SDK (and its consent form) exists.
        a.setConsent(
            mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to if (send) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED,
            )
        )
        crashlytics?.let { c ->
            c.isCrashlyticsCollectionEnabled = send
            // reports cached while collection was off are dropped, never sent later without consent
            if (!send) c.deleteUnsentReports()
        }
    }


    /** "Reset progress": forget this installation's analytics ID so new data can't be linked to the old. */
    fun resetIdentity() { analytics?.resetAnalyticsData() }

    // ------------------------------------------------------------------ crash context

    /** A breadcrumb that shows up in the Crashlytics log of the next crash. */
    fun crumb(msg: String) { crashlytics?.log(msg) }

    fun key(k: String, v: String) { crashlytics?.setCustomKey(k, v) }
    fun key(k: String, v: Int) { crashlytics?.setCustomKey(k, v) }

    /** Logs a caught exception as a non-fatal issue. */
    fun nonFatal(t: Throwable) { if (active) crashlytics?.recordException(t) }

    // ------------------------------------------------------------------ events

    private fun log(name: String, b: Bundle? = null) {
        analytics?.logEvent(name, b)
        crashlytics?.log(if (b == null) name else "$name ${b.keySet().joinToString { "$it=${b.get(it)}" }}")
    }

    private inline fun bundle(block: Bundle.() -> Unit) = Bundle().apply(block)

    fun screen(name: String) {
        key("screen", name)
        log(FirebaseAnalytics.Event.SCREEN_VIEW, bundle {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, name)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
        })
    }

    fun userProps(highestLevel: Int, quality: String) {
        analytics?.setUserProperty("highest_level", bucket(highestLevel))
        analytics?.setUserProperty("graphics", quality)
        key("highest_level", highestLevel)
        key("graphics", quality)
    }

    /** Level ranges keep the user property low-cardinality (Analytics limits distinct values). */
    private fun bucket(level: Int) = when {
        level <= 10 -> level.toString()
        level <= 50 -> "${(level - 1) / 10 * 10 + 1}-${((level - 1) / 10 + 1) * 10}"
        level <= 500 -> "${(level - 1) / 50 * 50 + 1}-${((level - 1) / 50 + 1) * 50}"
        else -> "500+"
    }

    fun levelStart(level: Int, boss: Boolean) {
        key("level", level)
        log(FirebaseAnalytics.Event.LEVEL_START, bundle {
            putString(FirebaseAnalytics.Param.LEVEL_NAME, "level_$level")
            putLong(FirebaseAnalytics.Param.LEVEL, level.toLong())
            putLong("boss", if (boss) 1 else 0)
        })
    }

    fun levelEnd(r: RaceResult) {
        log(FirebaseAnalytics.Event.LEVEL_END, bundle {
            putString(FirebaseAnalytics.Param.LEVEL_NAME, "level_${r.level}")
            putLong(FirebaseAnalytics.Param.LEVEL, r.level.toLong())
            putLong(FirebaseAnalytics.Param.SUCCESS, if (r.cleared) 1 else 0)
            putLong("rank", r.rank.toLong())
            putLong("stars", r.stars.toLong())
            putDouble("multiplier", r.multiplier.toDouble())
            putLong("coins", r.totalCoins.toLong())
            putLong("bonks", r.bonks.toLong())
            putLong("seconds", r.seconds.toLong())
        })
    }

    fun levelQuit(level: Int) = log("level_quit", bundle { putLong(FirebaseAnalytics.Param.LEVEL, level.toLong()) })

    fun levelUp(level: Int) = log(FirebaseAnalytics.Event.LEVEL_UP, bundle { putLong(FirebaseAnalytics.Param.LEVEL, level.toLong()) })

    fun tutorialBegin() = log(FirebaseAnalytics.Event.TUTORIAL_BEGIN)
    fun tutorialComplete() = log(FirebaseAnalytics.Event.TUTORIAL_COMPLETE)

    fun spend(item: String, currency: String, amount: Int) = log(FirebaseAnalytics.Event.SPEND_VIRTUAL_CURRENCY, bundle {
        putString(FirebaseAnalytics.Param.ITEM_NAME, item)
        putString(FirebaseAnalytics.Param.VIRTUAL_CURRENCY_NAME, currency)
        putLong(FirebaseAnalytics.Param.VALUE, amount.toLong())
    })

    /** A reward collected outside a race (daily login, wheel, mission, milestone, achievement). */
    fun reward(source: String, coins: Int = 0, gems: Int = 0) {
        log("reward_claim", bundle {
            putString("source", source)
            putLong("coins", coins.toLong())
            putLong("gems", gems.toLong())
        })
    }

    fun rewardedAd(placement: String, rewarded: Boolean) = log("rewarded_ad", bundle {
        putString("placement", placement.substringBefore("_level_").take(40))
        putLong("rewarded", if (rewarded) 1 else 0)
    })

    fun notificationOpen(kind: String) = log("notification_open", bundle { putString("kind", kind) })

    fun notificationPermission(granted: Boolean) = log("notification_permission", bundle { putLong("granted", if (granted) 1 else 0) })

    fun appUpdate(step: String) = log("app_update", bundle { putString("step", step) })
}
