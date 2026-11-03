package buddy.app

/** Keep in sync with `versionName` in composeApp/build.gradle.kts and the release tag. */
const val APP_VERSION = "0.2.2"

data class UpdateInfo(
    val version: String,
    /** Direct download for this platform, when the release has one. */
    val downloadUrl: String?,
    /** Release page, always available. */
    val pageUrl: String,
)

/** Compares dotted versions; returns true when [candidate] is newer than [current]. */
fun isNewerVersion(candidate: String, current: String = APP_VERSION): Boolean {
    fun parts(v: String) = v.trim().removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val a = parts(candidate)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
