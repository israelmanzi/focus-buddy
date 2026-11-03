package buddy.app

import buddy.app.data.lockDown
import buddy.app.data.lockDownDir
import java.io.File

enum class Sound { START, COMPLETE, BREAK_END, TAP }

sealed interface InstallResult {
    data object Started : InstallResult
    /** The downloaded APK is signed differently from the installed app; one manual reinstall is needed. */
    data object SignatureMismatch : InstallResult
    data object Failed : InstallResult
}

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

    /** Kept for hosts that want to react to foreground changes; sessions keep running either way. */
    fun onSessionPaused() {}
    fun onSessionResumed() {}

    fun onBreakStarted(endsAtMs: Long) {}
    fun onBreakEnded() {}

    fun keepScreenOn(on: Boolean) {}

    /** Short synthesized effects; [Sound] names the moment. */
    fun playSound(sound: Sound) {}

    /** Soft looping pad while a session runs. */
    fun setAmbient(on: Boolean) {}

    /** System notification (desktop); Android has its own channels and leaves this unimplemented. */
    fun showNotification(title: String, body: String) {}

    /** Android can deliver break notifications late without this; elsewhere it is always granted. */
    val supportsExactAlarms: Boolean get() = false
    fun exactAlarmsGranted(): Boolean = true
    fun requestExactAlarms() {}

    /** In-app updates: check the project's GitHub releases; installing is Android-only. */
    val appVersion: String get() = APP_VERSION
    suspend fun checkForUpdate(): UpdateInfo? = null
    fun installUpdate(info: UpdateInfo, onProgress: (Float) -> Unit = {}): InstallResult = InstallResult.Failed
    fun openUrl(url: String) {}

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
        lockDownDir(backupDir)
        val f = File(backupDir, "focus-buddy-backup-${System.currentTimeMillis() / 1000}.json")
        f.writeText(json)
        lockDown(f)
        return f.absolutePath
    }

    /** Newest backup file in [backupDir], if any. */
    fun newestBackup(): File? = backupDir.listFiles { f -> f.name.endsWith(".json") }?.maxByOrNull { it.lastModified() }
}

data class BlockRules(val allowMode: Boolean, val packages: Set<String>)
