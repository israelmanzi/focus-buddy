package buddy.app

/**
 * Every user-facing string in Focus Buddy.
 *
 * Copy rules:
 * - Concise, plain, sentence case. Straight to the point.
 * - No em dashes or en dashes. Use full stops or commas.
 * - No exclamation marks.
 * - Notifications: title of 4 words or fewer, body of 8 words or fewer, descriptive.
 * - Buttons: one or two words, no trailing punctuation.
 * - Never mention development, README files, deploy files or internal concepts.
 */
object Strings {
    object Home {
        val idleLines = listOf(
            "Ready when you are",
            "Let's focus together",
            "I found a good book",
            "One little session",
            "Tea, then focus",
            "I believe in us",
        )

        /** Room names are lowercase so the sentence reads naturally. */
        fun decorateRoom(room: String) = "Tap + to decorate $room"

        const val doneDecorating = "Done decorating"
        const val rooms = "Rooms"
        const val stats = "Stats"
        const val shop = "Shop"
        const val focus = "Focus"
        const val decorate = "Decorate"
        const val settings = "Settings"
    }

    object Focus {
        const val stopwatch = "stopwatch"
        const val hoursMinutes = "hours : minutes"
        const val minutes = "minutes"
        const val countdown = "Countdown"
        const val stopwatchTab = "Stopwatch"
        const val tag = "Tag"
        const val newTag = "+ new"
        const val tagHint = "Tag name"
        const val add = "Add"
        const val deepFocus = "Deep Focus"
        const val leavePauses = "Leaving the app is fine; the timer keeps running"
        const val needsPermissions = "Needs permissions. Tap Set up"
        const val noApps = "No apps chosen yet (Settings)"
        fun onlyApps(count: Int) = "Only ${appCount(count)} allowed"
        fun blocksApps(count: Int) = "Blocks ${appCount(count)}"
        const val setUp = "Set up"
        const val back = "Back"
        const val start = "Start"
    }

    object Session {
        const val away = "Come back, I'm waiting"
        val readingLines = listOf("Shh, reading time.", "We've got this.", "Page by page")
        const val deepFocusOn = "Deep Focus on"
        fun cancel(freeLeft: Int) = "Cancel ($freeLeft)"
        const val done = "Done"
        fun stopCountUp(minMinutes: Int) = "Stop (under $minMinutes min earns nothing)"
        const val stopFocusing = "Stop focusing"
        const val keepGoing = "Keep going"
        const val giveUp = "Give up"
        const val rewardTitle = "We did it"
        fun focusOf(minutes: String) = "$minutes of focus"
        const val rewardButton = "Yay"
        const val interruptedTitle = "Oh no"
        fun interruptedFocused(minutes: String) =
            "We focused for $minutes, but the session ended early. No acorns this time. Shall we try again?"
        const val interruptedEarly = "The session ended before it really started. Let's try again?"
        fun interruptedKept(acorns: Int) = "You keep $acorns acorns for the time you focused."
        const val okay = "Okay"
        const val breakTitle = "Tea break?"
        fun breakMinutes(minutes: Int) = "$minutes min"
        const val noThanks = "No thanks"
        const val breakBody = "Break time. Stretch, sip, breathe."
        const val endBreak = "End break"
    }

    object Shop {
        const val title = "Shop"
        fun editTitle(name: String) = "Edit $name"
        const val tabColours = "Colours"
        const val tabHats = "Hats"
        const val tabNeck = "Neck"
        const val tabFace = "Face"
        val tabs = listOf(tabColours, tabHats, tabNeck, tabFace)
        const val none = "None"
        const val name = "Name"
        const val nameHint = "Pip"
        const val wearing = "wearing"
        fun buy(name: String, price: Int) = "Buy $name · $price"
        fun needMore(acorns: Int) = "Need $acorns more acorns"
        const val earnAcorns = "Earn acorns by focusing."
        const val cancel = "Cancel"
        const val save = "Save"
    }

    object Decor {
        const val placed = "placed"
        const val owned = "owned"
        const val close = "Close"
        const val clear = "Clear"
        const val place = "Place"
        fun buy(price: Int) = "Buy · $price"
        fun needMore(acorns: Int) = "Need $acorns more"
        const val roomsTitle = "Rooms"
        const val here = "Here"
        const val go = "Go"
        fun unlock(price: Int) = "Unlock · $price"
    }

    object Stats {
        const val title = "Focus stats"
        val tabs = listOf("Summary", "Day", "Week", "Month", "Year")
        const val last28Days = "Last 28 days"
        const val legendNone = "none"
        const val legendFocus = "focus"
        const val legendDeep = "deep focus"
        const val focused = "Focused"
        const val sessions = "Sessions"
        const val streak = "Streak"
        const val bestStreak = "Best streak"
        fun days(count: Int) = "$count d"
        fun total(minutes: String) = "Total: $minutes"
        const val empty = "No focus sessions in this period yet."
    }

    object Settings {
        const val title = "Settings"
        const val focusSection = "Focus"
        const val deepFocusSection = "Deep Focus"
        const val tagsSection = "Tags"
        const val soundSection = "Sound"
        const val syncSection = "Sync"
        const val backupSection = "Backup"
        const val aboutSection = "About"

        const val pauseNote = "Switch to Chrome or notes whenever you need. The timer keeps running; Deep Focus is the only thing that blocks apps."
        fun seconds(seconds: Int) = "${seconds}s"
        const val keepScreenOn = "Keep awake while focusing"
        const val onTimeAlerts = "On-time break alerts"
        const val allow = "Allow"
        const val permissionsGranted = "Permissions granted ✓"
        const val permissionsNeeded = "Needs Usage access and Display over other apps."
        const val grantPermissions = "Grant permissions"
        const val blockChosen = "Block chosen apps"
        const val allowOnly = "Allow only chosen"
        fun appsChosen(count: Int) = "$count ${appCount(count)} chosen"
        const val chooseApps = "Choose apps"
        const val restore = "restore"
        const val hide = "hide"
        const val tagsHint = "Add tags when starting a session."
        const val ambientMusic = "Ambient music while focusing"
        const val soundEffects = "Sound effects"
        const val allowedApps = "Allowed apps"
        const val blockedApps = "Blocked apps"
        const val loading = "Loading…"
        const val done = "Done"
    }

    object Sync {
        const val description = "Sync across your devices with a private address. Leave empty to stay offline."
        const val urlHint = "http://100.x.y.z:8787"
        const val tokenHint = "token"
        const val automatic = "Sync automatically"
        const val save = "Save"
        const val off = "off"
        const val syncing = "syncing…"
        const val notYet = "not synced yet"
        const val synced = "synced"
        fun failed(message: String) = "failed: $message"
        fun httpFailed(status: Int) = "Sync failed: HTTP $status"
        fun device(id: String) = "This device: $id"
        const val cancelled = "cancelled"
        const val error = "error"
        const val dotSyncing = "syncing"
        const val dotSynced = "synced"
        const val dotOffline = "offline"
        /** Pulls the whole log again; repairs a device that missed events. */
        const val resync = "Re-download everything"
    }

    object Backup {
        const val export = "Export"
        const val importNewest = "Import newest"
        fun saved(path: String) = "Saved to $path"
        fun exportFailed(message: String) = "Export failed: $message"
        fun importFailed(message: String) = "Import failed: $message"
        fun noBackup(dir: String) = "No backup in $dir"
        fun imported(count: Int, file: String) = "Imported $count new events from $file"
        fun folder(dir: String) = "Folder: $dir"
    }

    object Updates {
        const val section = "Updates"
        fun version(v: String) = "Version $v"
        const val check = "Check for updates"
        const val checking = "Checking"
        const val upToDate = "You are on the latest version"
        const val available = "A new version is available"
        const val install = "Download and install"
        const val openPage = "Open the release page"
        const val failed = "Could not check for updates"
        const val started = "Starting the installer"
    }

    object About {
        const val appName = "Focus Buddy"
        const val text =
            "Focus Buddy. Everything is unlocked. No accounts, no ads, no purchases. " +
                "Acorns come from focusing, and your data stays on your devices or your own server."
    }

    object Notifications {
        // Channel names appear in the system notification settings.
        const val sessionChannel = "Focus session"
        const val nudgeChannel = "Session paused"
        const val breakChannel = "Break reminders"
        const val generalChannel = "Session updates"

        const val sessionTitle = "Pip is focusing"
        const val sessionBodyTimed = "Stay with Pip until the timer ends"
        const val sessionBodyOpen = "Focusing with Pip"

        const val nudgeTitle = "Focus paused"
        const val nudgeBody = "Return to continue"

        const val breakTitle = "Break over"
        const val breakBody = "Ready to focus again?"

        const val completeTitle = "Session complete"
        fun completeBody(acorns: Int, minutes: Int) = "+$acorns acorns, $minutes ${minuteCount(minutes)} focused"
        const val endedTitle = "Session ended early"
        const val endedBody = "No acorns this time"
    }

    object Overlay {
        const val focusing = "Pip is focusing"
        fun appCanWait(app: String) = "$app can wait."
        fun sessionLeft(clock: String) = "$clock left in this session"
        const val stayLonger = "Stay with Pip a little longer"
        const val backToApp = "Go back"
    }

    object Tray {
        const val title = "Focus Buddy"
        const val open = "Open"
        const val startSession = "Start session"
        const val stopSession = "Stop session"
        const val quit = "Quit"
    }
}

private fun appCount(count: Int) = if (count == 1) "1 app" else "$count apps"

private fun minuteCount(minutes: Int) = if (minutes == 1) "minute" else "minutes"
