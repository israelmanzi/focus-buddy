package buddy.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/** Shows the ongoing session notification while the timer runs. */
class FocusService : Service() {

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY.also { stopSelf() }
        val endsAt = intent.getLongExtra(EXTRA_ENDS, 0).takeIf { it > 0 }
        val startedAt = intent.getLongExtra(EXTRA_STARTED, System.currentTimeMillis())
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notify.ID_SESSION, Notify.session(this, endsAt, startedAt), type)
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        // A stopped service can leave the notification behind on some OEM builds.
        Notify.cancel(this, Notify.ID_SESSION)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FocusService"
        private const val EXTRA_ENDS = "endsAt"
        private const val EXTRA_STARTED = "startedAt"

        fun start(ctx: Context, endsAt: Long?, startedAt: Long) {
            val intent = Intent(ctx, FocusService::class.java)
                .putExtra(EXTRA_ENDS, endsAt ?: 0L)
                .putExtra(EXTRA_STARTED, startedAt)
            try {
                ContextCompat.startForegroundService(ctx, intent)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Could not start focus service", e)
            }
        }
    }
}
