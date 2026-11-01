package buddy.app

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps the session alive in the background and, with Deep Focus on, covers blocked apps with an overlay. */
class FocusService : Service() {
    private val scope = MainScope()
    private var watcher: Job? = null
    private var endsAt: Long? = null
    private var rules = BlockRules(false, emptySet())
    private var exempt = emptySet<String>()
    private var current: String? = null
    private var pausedUntil = 0L
    private val labels = HashMap<String, String>()

    private val wm by lazy { getSystemService(WindowManager::class.java) }
    private var overlay: View? = null
    private lateinit var appText: TextView
    private lateinit var timeText: TextView

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY.also { stopSelf() }
        endsAt = intent.getLongExtra(EXTRA_ENDS, 0).takeIf { it > 0 }
        val startedAt = intent.getLongExtra(EXTRA_STARTED, System.currentTimeMillis())
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notify.ID_SESSION, Notify.session(this, endsAt, startedAt), type)

        rules = BlockRules(intent.getBooleanExtra(EXTRA_ALLOW, false), intent.getStringArrayExtra(EXTRA_PACKAGES).orEmpty().toSet())
        watcher?.cancel()
        hideOverlay()
        if (intent.getBooleanExtra(EXTRA_DEEP, false)) {
            exempt = exemptPackages()
            watcher = scope.launch { watch() }
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        scope.cancel()
        hideOverlay()
        super.onDestroy()
    }

    private suspend fun watch() {
        val usm = getSystemService(UsageStatsManager::class.java)
        val power = getSystemService(PowerManager::class.java)
        var since = System.currentTimeMillis() - 10 * 60_000L
        while (currentCoroutineContext().isActive) {
            val now = System.currentTimeMillis()
            val interactive = power.isInteractive
            if (interactive) {
                withContext(Dispatchers.IO) { lastResumed(usm, since, now) }?.let { current = it }
                val pkg = current
                val ended = endsAt?.let { now >= it } == true
                if (pkg != null && !ended && now >= pausedUntil && isBlocked(pkg)) showOverlay(pkg, now) else hideOverlay()
            }
            // Keep the query window small even after the screen was off for hours, and don't
            // wake the CPU ten times a second with a foreground app that cannot change.
            since = now - 2_000
            delay(if (interactive) POLL_MS else SLEEP_POLL_MS)
        }
    }

    private fun isBlocked(pkg: String) = pkg !in exempt && (pkg in rules.packages) != rules.allowMode

    /** The package of the most recent activity to come to the foreground in [from, to], if any. */
    private fun lastResumed(usm: UsageStatsManager, from: Long, to: Long): String? {
        val events = usm.queryEvents(from, to) ?: return null
        val e = UsageEvents.Event()
        var last: String? = null
        @Suppress("DEPRECATION")
        while (events.getNextEvent(e)) if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) last = e.packageName
        return last
    }

    /** Apps that must never be covered: ourselves, the home screen, system UI and anything for calls. */
    private fun exemptPackages(): Set<String> {
        val home = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
        val dialer = runCatching { getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
        return setOfNotNull(
            packageName, home, dialer, "android", "com.android.systemui",
            "com.android.phone", "com.android.server.telecom", "com.android.incallui", "com.android.dialer",
            "com.google.android.dialer", "com.android.emergency", "com.google.android.apps.safetyhub",
        )
    }

    private fun label(pkg: String) = labels.getOrPut(pkg) {
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }

    private fun showOverlay(pkg: String, now: Long) {
        if (overlay == null) {
            if (!Settings.canDrawOverlays(this)) return
            val view = buildOverlay()
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE,
            ).apply {
                if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            try {
                wm.addView(view, params)
                overlay = view
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not show overlay", e)
                return
            }
        }
        appText.text = Strings.Overlay.appCanWait(label(pkg))
        timeText.text = endsAt?.let { Strings.Overlay.sessionLeft(formatLeft(it - now)) } ?: Strings.Overlay.stayLonger
    }

    private fun hideOverlay() {
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
    }

    private fun buildOverlay(): View {
        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt() }
        fun text(size: Float, bold: Boolean = false) = TextView(this).apply {
            setTextColor(BROWN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(8))
        }
        appText = text(20f)
        timeText = text(18f)
        val button = TextView(this).apply {
            text = Strings.Overlay.backToApp
            setTextColor(CREAM)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(16), dp(28), dp(16))
            background = GradientDrawable().apply { setColor(BROWN); cornerRadius = dp(28).toFloat() }
            isClickable = true
            setOnClickListener { backToApp() }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(CREAM)
            setPadding(dp(32), dp(32), dp(32), dp(32))
            addView(text(34f, bold = true).apply { text = Strings.Overlay.focusing })
            addView(appText)
            addView(timeText)
            addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(32) })
        }
    }

    private fun backToApp() {
        // Give our activity a moment to show up in usage events before checking again.
        pausedUntil = System.currentTimeMillis() + 2_000
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        hideOverlay()
    }

    companion object {
        private const val TAG = "FocusService"
        private const val POLL_MS = 1_200L
        private const val SLEEP_POLL_MS = 5_000L
        private const val EXTRA_ENDS = "endsAt"
        private const val EXTRA_STARTED = "startedAt"
        private const val EXTRA_DEEP = "deepFocus"
        private const val EXTRA_ALLOW = "allowMode"
        private const val EXTRA_PACKAGES = "packages"
        private val CREAM = Color.parseColor("#FBF3E4")
        private val BROWN = Color.parseColor("#4A342E")

        private fun formatLeft(ms: Long): String {
            val s = (ms / 1000).coerceAtLeast(0)
            return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
        }

        fun start(ctx: Context, endsAt: Long?, startedAt: Long, deepFocus: Boolean, rules: BlockRules) {
            val intent = Intent(ctx, FocusService::class.java)
                .putExtra(EXTRA_ENDS, endsAt ?: 0L)
                .putExtra(EXTRA_STARTED, startedAt)
                .putExtra(EXTRA_DEEP, deepFocus)
                .putExtra(EXTRA_ALLOW, rules.allowMode)
                .putExtra(EXTRA_PACKAGES, rules.packages.toTypedArray())
            try {
                ContextCompat.startForegroundService(ctx, intent)
            } catch (e: IllegalStateException) {
                // Android 12+ refuses to start foreground services from the background (e.g. a cold start
                // from the break alarm); the redelivered service intent or the next app launch covers it.
                Log.w(TAG, "Could not start focus service", e)
            }
        }
    }
}
