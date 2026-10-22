package buddy.app

import java.io.File

data class AppInfo(val packageName: String, val label: String)

/** What the shared controller needs from the host OS. Android implements all of it; desktop mostly no-ops. */
interface Platform {
    val name: String
    val dataDir: File

    /** True on platforms where leaving the app during a session should count as an interruption. */
    val leavingAppInterrupts: Boolean

    /** Session started or resumed. `endsAtMs` is null for count-up sessions. */
    fun onSessionStarted(startedAtMs: Long, endsAtMs: Long?, deepFocus: Boolean, rules: BlockRules) {}
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

    /** Where backups are written and looked for. */
    val backupDir: File get() = File(dataDir, "backups")

    /** Writes a backup file and returns its path. */
    fun saveBackup(json: String): String {
        backupDir.mkdirs()
        val f = File(backupDir, "focus-buddy-backup-${System.currentTimeMillis() / 1000}.json")
        f.writeText(json)
        return f.absolutePath
    }

    /** Newest backup file in [backupDir], if any. */
    fun newestBackup(): File? = backupDir.listFiles { f -> f.name.endsWith(".json") }?.maxByOrNull { it.lastModified() }
}

data class BlockRules(val allowMode: Boolean, val packages: Set<String>)
