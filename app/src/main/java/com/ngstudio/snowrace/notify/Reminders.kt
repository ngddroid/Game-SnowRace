package com.ngstudio.snowrace.notify

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ngstudio.snowrace.R
import com.ngstudio.snowrace.data.GameRules
import com.ngstudio.snowrace.data.SaveData
import com.ngstudio.snowrace.data.SaveRepository
import com.ngstudio.snowrace.data.TimeUtil
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Local "come back" notifications, planned every time the game goes to the background and cancelled as soon as it
 * comes back, so they never fire while someone is playing:
 *
 * - daily: 6 pm tomorrow, when the daily gift and free spin have reset: "your daily gift is ready"
 * - comeback3 / comeback7: 6 pm three and seven days later, only if the game wasn't opened in between
 *
 * WorkManager keeps them across reboots. Nothing is posted if the player turned notifications off in Settings or
 * the system doesn't allow them.
 */
object Reminders {
    private const val TAG = "reminder"
    private const val TEST_TAG = "reminder-test"
    private const val KEY_KIND = "kind"
    private const val KEY_TEST = "test"
    private const val EVENING_HOUR = 18
    private val kinds = listOf("daily", "comeback3", "comeback7")

    fun schedule(ctx: Context, s: SaveData) {
        val wm = WorkManager.getInstance(ctx)
        wm.cancelAllWorkByTag(TAG)
        if (!s.settings.notifications) return
        val now = System.currentTimeMillis()
        enqueue(wm, "daily", nextEvening(now, minGapHours = 0, plusDays = 1) - now)
        enqueue(wm, "comeback3", nextEvening(now, minGapHours = 0, plusDays = 3) - now)
        enqueue(wm, "comeback7", nextEvening(now, minGapHours = 0, plusDays = 7) - now)
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx).cancelAllWorkByTag(TAG)
    }

    /**
     * Debug: fire every reminder text a few seconds from now, including both daily variants (gift and wheel) whatever
     * the save says. Own tag, so leaving the game doesn't replace them.
     */
    fun scheduleTest(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        (kinds + "wheel").forEachIndexed { i, k -> enqueue(wm, k, 5_000L + i * 3_000L, TEST_TAG, test = true) }
    }

    private fun enqueue(wm: WorkManager, kind: String, delayMs: Long, tag: String = TAG, test: Boolean = false) {
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_KIND to kind, KEY_TEST to test))
            .addTag(tag)
            .build()
        wm.enqueueUniqueWork("$tag-$kind", ExistingWorkPolicy.REPLACE, req)
    }

    /** 6 pm local time, [plusDays] days from today, moved a day later if it is less than [minGapHours] away. */
    private fun nextEvening(now: Long, minGapHours: Int, plusDays: Int): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, plusDays)
            set(Calendar.HOUR_OF_DAY, EVENING_HOUR); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        while (c.timeInMillis - now < minGapHours * 3_600_000L || c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** [test] shows the daily gift text even when nothing is claimable (debug "Test reminders" only). */
    internal fun post(ctx: Context, kind: String, test: Boolean = false) {
        val s = SaveRepository.peek(ctx) ?: SaveData()
        if (!s.settings.notifications) return
        val level = s.progress.highestUnlocked
        val today = TimeUtil.today()
        val (title, body, icon) = when (kind) {
            "daily" -> when {
                test || GameRules.loginClaimable(s, today) -> Triple(
                    ctx.getString(R.string.notif_daily_title),
                    ctx.getString(R.string.notif_daily_body, (s.rewards.loginIndex % 7) + 1),
                    R.drawable.ui_gift,
                )
                GameRules.wheelFree(s, today) -> Triple(ctx.getString(R.string.notif_wheel_title), ctx.getString(R.string.notif_wheel_body), R.drawable.ui_wheel)
                else -> return // nothing is actually waiting: say nothing rather than something untrue
            }
            "wheel" -> Triple(ctx.getString(R.string.notif_wheel_title), ctx.getString(R.string.notif_wheel_body), R.drawable.ui_wheel)
            "comeback3" -> Triple(ctx.getString(R.string.notif_back3_title), ctx.getString(R.string.notif_back3_body, level), R.drawable.ui_snowball)
            else -> Triple(ctx.getString(R.string.notif_back7_title), ctx.getString(R.string.notif_back7_body, level), R.drawable.ui_trophy)
        }
        val screen = if (kind == "daily" || kind == "wheel") "rewards" else "home"
        Notifications.show(ctx, 100 + (kinds + "wheel").indexOf(kind).coerceAtLeast(0), Notifications.CH_REMINDERS, title, body, "reminder_$kind", screen, icon)
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        Reminders.post(applicationContext, inputData.getString("kind") ?: "daily", inputData.getBoolean("test", false))
        return Result.success()
    }
}
