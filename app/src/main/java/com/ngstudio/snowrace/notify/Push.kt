package com.ngstudio.snowrace.notify

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ngstudio.snowrace.analytics.Telemetry
import com.ngstudio.snowrace.data.SaveRepository

/**
 * Firebase Cloud Messaging. There is no game server: send pushes from the Firebase console
 * (Messaging > New campaign > Notifications), targeting the app or the "all" topic.
 *
 * Optional custom data key: screen = home | levels | shop | rewards | profile opens that tab when the player taps it.
 */
object Push {
    const val TOPIC_ALL = "all"
    private const val TAG = "Push"

    /**
     * Turns push on or off to match the Notifications setting. Turning it off deletes the device token, so the
     * console can't reach this device at all (not even with system-displayed notifications).
     */
    fun setEnabled(on: Boolean, changed: Boolean) {
        if (!Telemetry.available) return
        try {
            val fm = FirebaseMessaging.getInstance()
            fm.isAutoInitEnabled = on
            if (on) {
                fm.subscribeToTopic(TOPIC_ALL)
            } else if (changed) {
                fm.unsubscribeFromTopic(TOPIC_ALL)
                fm.deleteToken()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "FCM unavailable", t)
        }
    }

    /** Debug helper: prints this device's token so a test message can be sent to it from the console. */
    fun logToken() {
        if (!Telemetry.available) return
        runCatching { FirebaseMessaging.getInstance().token.addOnSuccessListener { Log.i(TAG, "FCM token: $it") } }
    }
}

class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        // No game backend stores tokens: console campaigns and the "all" topic reach the device without it.
    }

    /** Called for data messages, and for notification messages while the game is in the foreground. */
    override fun onMessageReceived(message: RemoteMessage) {
        val save = SaveRepository.peek(this)
        if (save != null && !save.settings.notifications) return
        val n = message.notification
        val title = n?.title ?: message.data["title"] ?: return
        val body = n?.body ?: message.data["body"] ?: ""
        val id = 200 + ((message.messageId ?: title).hashCode() and 0xFFFF)
        Notifications.show(this, id, Notifications.CH_NEWS, title, body, "push", message.data[Notifications.EXTRA_SCREEN])
    }
}
