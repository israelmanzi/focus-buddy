package buddy.app

import java.io.File

data class AppInfo(val packageName: String, val label: String)

/** What the shared controller needs from the host OS. Android implements all of it; desktop mostly no-ops. */
interface Platform {
    val name: String
    val dataDir: File

    /** True on platforms where leaving the app during a session should count as an interruption. */
    val leavingAppInterrupts: Boolean

    /** Session started or changed. `endsAtMs` is null for count-up sessions. */
    fun onSessionStarted(endsAtMs: Long?, deepFocus: Boolean, rules: BlockRules) {}
    fun onSessionEnded() {}

    /** The user left the app mid-session; nudge them back before `deadlineMs`. */
    fun onLeftDuringSession(deadlineMs: Long) {}
    fun onReturned() {}

    fun onBreakStarted(endsAtMs: Long) {}
    fun onBreakEnded() {}

    fun keepScreenOn(on: Boolean) {}

    /** Deep Focus support. */
    val supportsDeepFocus: Boolean get() = false
    fun deepFocusReady(): Boolean = false
    fun requestDeepFocusPermissions() {}
    suspend fun launchableApps(): List<AppInfo> = emptyList()
}

data class BlockRules(val allowMode: Boolean, val packages: Set<String>)
