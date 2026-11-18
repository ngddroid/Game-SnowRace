package com.ngstudio.snowrace.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ngstudio.snowrace.MainActivity
import com.ngstudio.snowrace.R

/** Notification channels, posting, permission checks and the extras a tapped notification carries into [MainActivity]. */
object Notifications {
    const val CH_REMINDERS = "reminders"
    const val CH_NEWS = "news"

    /** Intent extra: what kind of notification was tapped (analytics). */
    const val EXTRA_KIND = "notif_kind"
    /** Intent extra / push data key: which home tab to open (home, levels, shop, rewards, profile). */
    const val EXTRA_SCREEN = "screen"

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_REMINDERS, ctx.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = ctx.getString(R.string.channel_reminders_desc) },
                NotificationChannel(CH_NEWS, ctx.getString(R.string.channel_news), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = ctx.getString(R.string.channel_news_desc) },
            )
        )
    }

    /** Android 13+ needs the POST_NOTIFICATIONS runtime permission; older versions allow notifications by default. */
    fun needsPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** The system lets this app post (permission granted and notifications not blocked in system settings). */
    fun systemAllows(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** Opens the app's notification page in system settings (used once the permission was permanently denied). */
    fun openSystemSettings(ctx: Context) {
        val i = if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", ctx.packageName, null))
        }
        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    @SuppressLint("MissingPermission") // checked by systemAllows(); a late revoke is caught below
    fun show(
        ctx: Context,
        id: Int,
        channel: String,
        title: String,
        body: String,
        kind: String,
        screen: String? = null,
        largeIcon: Int? = null,
    ) {
        if (!systemAllows(ctx)) return
        val open = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_KIND, kind)
            .apply { if (screen != null) putExtra(EXTRA_SCREEN, screen) }
        val pi = PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(ContextCompat.getColor(ctx, R.color.berry_pink))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(if (channel == CH_REMINDERS) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (largeIcon != null) runCatching { BitmapFactory.decodeResource(ctx.resources, largeIcon) }.getOrNull()?.let { b.setLargeIcon(it) }
        try {
            NotificationManagerCompat.from(ctx).notify(id, b.build())
        } catch (_: SecurityException) {
            // permission revoked between the check and the post
        }
    }
}
