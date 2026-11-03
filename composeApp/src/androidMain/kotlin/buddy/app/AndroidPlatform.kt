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
import java.net.HttpURLConnection
import java.net.URL

@kotlinx.serialization.Serializable
private class ReleaseAsset(val name: String, val browser_download_url: String)

@kotlinx.serialization.Serializable
private class Release(val tag_name: String, val html_url: String, val assets: List<ReleaseAsset> = emptyList())

private const val RELEASES_API = "https://api.github.com/repos/israelmanzi/focus-buddy/releases/latest"
private val UpdateJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

class AndroidPlatform(private val app: Application) : Platform {
    override val name = "android"
    override val dataDir: File = File(app.filesDir, "buddy")
    override val leavingAppInterrupts = true
    override val backupDir: File get() = File(app.getExternalFilesDir(null) ?: app.filesDir, "backups")
    override val supportsDeepFocus = true

    private val prefs = prefs(app)
    private val alarms = app.getSystemService(AlarmManager::class.java)
    private var askedNotifications = false

    init {
        Notify.createChannels(app)
        app.registerActivityLifecycleCallbacks(ActivityTracker)
    }

    override fun onSessionStarted(startedAtMs: Long, endsAtMs: Long?, deepFocus: Boolean, rules: BlockRules) {
        Notify.cancel(app, Notify.ID_BREAK)
        askNotificationPermission()
        FocusService.start(app, endsAtMs, startedAtMs, deepFocus, rules)
    }

    override fun onSessionEnded() {
        Notify.cancel(app, Notify.ID_NUDGE)
        Notify.cancel(app, Notify.ID_SESSION)
        app.stopService(Intent(app, FocusService::class.java))
    }

    override fun onSessionPaused() = Notify.paused(app)

    override fun onSessionResumed() = Notify.cancel(app, Notify.ID_NUDGE)

    override val appVersion: String
        get() = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { buddy.app.APP_VERSION }

    override suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        val body = try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val release = UpdateJson.decodeFromString(Release.serializer(), body)
        val version = release.tag_name.removePrefix("v")
        if (!isNewerVersion(version)) null
        else UpdateInfo(
            version = version,
            downloadUrl = release.assets.firstOrNull { it.name.endsWith(".apk") }?.browser_download_url,
            pageUrl = release.html_url,
        )
    }

    override fun installUpdate(info: UpdateInfo, onProgress: (Float) -> Unit): InstallResult {
        val url = info.downloadUrl ?: return InstallResult.Failed
        val target = File(app.cacheDir, "focus-buddy-update.apk")
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        return runCatching {
            Thread {
                val result = runCatching {
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10_000; readTimeout = 60_000
                        instanceFollowRedirects = true
                    }
                    val total = conn.contentLengthLong
                    conn.inputStream.use { input ->
                        target.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var readTotal = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                readTotal += n
                                if (total > 0) main.post { onProgress((readTotal.toFloat() / total).coerceIn(0f, 1f)) }
                            }
                        }
                    }
                    conn.disconnect()
                    if (!sameSigner(target)) return@runCatching InstallResult.SignatureMismatch
                    main.post {
                        val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.updates", target)
                        val intent = Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        app.startActivity(intent)
                    }
                    InstallResult.Started
                }.getOrDefault(InstallResult.Failed)
                main.post { onProgress(if (result == InstallResult.Started) 1f else -1f) }
            }.start()
            InstallResult.Started
        }.getOrDefault(InstallResult.Failed)
    }

    /** The system refuses an APK signed by a different key; detect it before opening the installer. */
    private fun sameSigner(apk: File): Boolean = runCatching {
        val pm = app.packageManager
        val flags = android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
        val mine = pm.getPackageInfo(app.packageName, flags).signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        val theirs = pm.getPackageArchiveInfo(apk.absolutePath, flags)?.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        mine != null && theirs != null && mine.contentEquals(theirs)
    }.getOrDefault(false)

    override fun openUrl(url: String) {
        runCatching {
            app.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun showNotification(title: String, body: String) = Notify.general(app, title, body)

    override fun playSound(sound: Sound) = SoundFx.play(sound)

    override fun setAmbient(on: Boolean) = SoundFx.ambient(on)

    override val supportsExactAlarms = true

    override fun exactAlarmsGranted(): Boolean =
        Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    override fun requestExactAlarms() {
        if (Build.VERSION.SDK_INT < 31) return
        runCatching {
            app.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${app.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    override fun onBreakStarted(endsAtMs: Long) {
        Notify.cancel(app, Notify.ID_BREAK)
        prefs.edit { putLong(KEY_BREAK, endsAtMs) }
        val exact = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
        if (exact) {
            // Permission can be revoked between the check and the call.
            runCatching { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMs, breakAlarm()) }
                .onFailure { runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMs, breakAlarm()) } }
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
