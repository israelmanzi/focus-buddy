package buddy.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit

/** Notification channels, builders and the break-over alarm receiver. */
internal object Notify {
    private const val CH_SESSION = "session"
    private const val CH_NUDGE = "nudge"
    private const val CH_BREAK = "break"
    const val ID_SESSION = 1
    const val ID_NUDGE = 2
    const val ID_BREAK = 3

    fun createChannels(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
            NotificationChannel(CH_SESSION, "Focus session", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CH_NUDGE, "Come-back reminders", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CH_BREAK, "Break reminders", NotificationManager.IMPORTANCE_HIGH),
        ))
    }

    fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun builder(ctx: Context, channel: String) = NotificationCompat.Builder(ctx, channel)
        .setSmallIcon(R.drawable.ic_stat_pip)
        .setColor(0xFF8B5E3C.toInt())
        .setContentIntent(openApp(ctx))

    /** Ongoing session notification: counts down to [endsAt], or up from [startedAt] when there is no end. */
    fun session(ctx: Context, endsAt: Long?, startedAt: Long) = builder(ctx, CH_SESSION)
        .setContentTitle("Pip is focusing")
        .setContentText(if (endsAt == null) "Focusing with Pip" else "Stay with Pip until the timer ends")
        .setOngoing(true)
        .setShowWhen(true)
        .setUsesChronometer(true)
        .setChronometerCountDown(endsAt != null)
        .setWhen(endsAt ?: startedAt)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()

    fun nudge(ctx: Context, deadline: Long) = post(ctx, ID_NUDGE, builder(ctx, CH_NUDGE)
        .setContentTitle("Come back! Pip is waiting")
        .setContentText("Return before the timer runs out to keep your session")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setShowWhen(true)
        .setUsesChronometer(true)
        .setChronometerCountDown(true)
        .setWhen(deadline)
        .setTimeoutAfter((deadline - System.currentTimeMillis()).coerceAtLeast(1))
        .setAutoCancel(true)
        .build())

    fun breakOver(ctx: Context) = post(ctx, ID_BREAK, builder(ctx, CH_BREAK)
        .setContentTitle("Break's over. Ready to focus?")
        .setContentText("Pip is ready when you are.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setOnlyAlertOnce(true)
        .setAutoCancel(true)
        .build())

    fun cancel(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)

    private fun post(ctx: Context, id: Int, n: android.app.Notification) {
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted.
        }
    }
}

/** Fires when a break ends while the app may not be running. */
class BreakReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AndroidPlatform.prefs(context).edit { remove(AndroidPlatform.KEY_BREAK) }
        Notify.breakOver(context)
    }
}
