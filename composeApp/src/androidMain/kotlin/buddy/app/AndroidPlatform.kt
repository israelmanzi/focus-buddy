package buddy.app

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.Application
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference

class AndroidPlatform(private val app: Application) : Platform {
    override val name = "android"
    override val dataDir: File = File(app.filesDir, "buddy")
    override val leavingAppInterrupts = true
    override val supportsDeepFocus = true

    private val prefs = prefs(app)
    private val alarms = app.getSystemService(AlarmManager::class.java)
    private var askedNotifications = false

    init {
        Notify.createChannels(app)
        app.registerActivityLifecycleCallbacks(ActivityTracker)
    }

    override fun onSessionStarted(endsAtMs: Long?, deepFocus: Boolean, rules: BlockRules) {
        Notify.cancel(app, Notify.ID_BREAK)
        askNotificationPermission()
        // Count-up sessions have no end, so remember when this one began for the chronometer.
        val startedAt = prefs.getLong(KEY_START, 0).takeIf { it > 0 }
            ?: System.currentTimeMillis().also { prefs.edit { putLong(KEY_START, it) } }
        FocusService.start(app, endsAtMs, startedAt, deepFocus, rules)
    }

    override fun onSessionEnded() {
        prefs.edit { remove(KEY_START) }
        Notify.cancel(app, Notify.ID_NUDGE)
        app.stopService(Intent(app, FocusService::class.java))
    }

    override fun onLeftDuringSession(deadlineMs: Long) = Notify.nudge(app, deadlineMs)

    override fun onReturned() = Notify.cancel(app, Notify.ID_NUDGE)

    override fun onBreakStarted(endsAtMs: Long) {
        Notify.cancel(app, Notify.ID_BREAK)
        prefs.edit { putLong(KEY_BREAK, endsAtMs) }
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMs, breakAlarm())
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMs, breakAlarm())
        }
    }

    override fun onBreakEnded() {
        alarms.cancel(breakAlarm())
        val endsAt = prefs.getLong(KEY_BREAK, Long.MAX_VALUE)
        prefs.edit { remove(KEY_BREAK) }
        // The in-app ticker can beat the alarm; a break that ran its course still gets its notification.
        if (System.currentTimeMillis() >= endsAt - 1_000) Notify.breakOver(app)
    }

    override fun keepScreenOn(on: Boolean) {
        ActivityTracker.keepScreenOn = on
    }

    override fun deepFocusReady() = hasUsageAccess() && Settings.canDrawOverlays(app)

    override fun requestDeepFocusPermissions() {
        val pkg = "package:${app.packageName}".toUri()
        val intent = when {
            !hasUsageAccess() -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, pkg)
            !Settings.canDrawOverlays(app) -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
            else -> return
        }
        val ctx: Context = ActivityTracker.resumed ?: app
        val launch = { i: Intent -> ctx.startActivity(if (ctx is Activity) i else i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        try {
            launch(intent)
        } catch (_: ActivityNotFoundException) {
            launch(Intent(intent.action)) // some builds have no per-app page
        }
    }

    override suspend fun launchableApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = app.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != app.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    private fun hasUsageAccess(): Boolean {
        val ops = app.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            app.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || askedNotifications) return
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val launcher = ActivityTracker.notificationLauncher ?: return
        askedNotifications = true
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun breakAlarm(): PendingIntent = PendingIntent.getBroadcast(
        app, 0, Intent(app, BreakReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val KEY_START = "sessionStart"
        const val KEY_BREAK = "breakEndsAt"
        fun prefs(ctx: Context) = ctx.getSharedPreferences("platform", Context.MODE_PRIVATE)!!
    }
}

/** Tracks the resumed activity so the platform can reach a window and the permission launcher. */
internal object ActivityTracker : Application.ActivityLifecycleCallbacks {
    private var ref: WeakReference<Activity>? = null
    val resumed: Activity? get() = ref?.get()

    /** Registered by [MainActivity]; null while it is not alive. */
    var notificationLauncher: ActivityResultLauncher<String>? = null

    var keepScreenOn = false
        set(value) {
            field = value
            resumed?.let(::applyScreenOn)
        }

    private fun applyScreenOn(a: Activity) {
        if (keepScreenOn) a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else a.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onActivityResumed(activity: Activity) {
        ref = WeakReference(activity)
        applyScreenOn(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed === activity) ref = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
