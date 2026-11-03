package buddy.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import buddy.app.AppController
import buddy.app.AppInfo
import buddy.app.Strings
import buddy.app.SyncStatus
import buddy.core.Rules
import kotlinx.coroutines.launch

@Composable
fun SettingsSheet(controller: AppController, onClose: () -> Unit) {
    val s by controller.settings.collectAsState()
    val state by controller.state.collectAsState()
    val sync by controller.sync.collectAsState()
    val p = controller.platform
    val scope = rememberCoroutineScope()
    var pickingApps by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    if (pickingApps) { AppPicker(controller) { pickingApps = false }; return }

    SheetCard(onClose) {
        Title(Strings.Settings.title)
        Spacer(Modifier.height(Space.md))
        ScrollColumn(Modifier.weight(1f, fill = false)) {
            Section(Strings.Settings.focusSection) {
                if (p.leavingAppInterrupts) {
                    Text(Strings.Settings.pauseNote, style = label(Type.foot, FontWeight.Medium, Ink.muted))
                    Spacer(Modifier.height(Space.sm))
                }
                Toggle(Strings.Settings.keepScreenOn, s.screenOn) { v -> controller.updateSettings { it.copy(screenOn = v) } }
                if (p.supportsExactAlarms && !p.exactAlarmsGranted()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(Strings.Settings.onTimeAlerts, style = label(Type.body), modifier = Modifier.weight(1f))
                    ChunkyButton(Strings.Settings.allow, fontSize = Type.body, padding = PaddingValues(Space.xl, Space.md)) { p.requestExactAlarms() }
                }
            }
            if (p.supportsDeepFocus) Section(Strings.Settings.deepFocusSection) {
                val ready = p.deepFocusReady()
                Text(
                    if (ready) Strings.Settings.permissionsGranted else Strings.Settings.permissionsNeeded,
                    style = label(Type.small, FontWeight.Medium, if (ready) Ink.leafDark else Ink.berry),
                )
                if (!ready) ChunkyButton(Strings.Settings.grantPermissions, fontSize = Type.body, padding = PaddingValues(Space.xl, Space.md)) { p.requestDeepFocusPermissions() }
                Spacer(Modifier.height(Space.sm))
                Tabs(listOf(Strings.Settings.blockChosen, Strings.Settings.allowOnly), if (s.allowMode) 1 else 0, { i -> controller.updateSettings { it.copy(allowMode = i == 1) } })
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.md)) {
                    Text(Strings.Settings.appsChosen(s.packages.size), style = label(Type.body), modifier = Modifier.weight(1f))
                    ChunkyButton(Strings.Settings.chooseApps, fontSize = Type.body, padding = PaddingValues(Space.xl, Space.md)) { pickingApps = true }
                }
            }
            Section(Strings.Settings.tagsSection) {
                val active = state.tags.values.filter { !it.archived }
                val removed = state.tags.values.filter { it.archived }
                active.forEach { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.xxs), verticalAlignment = Alignment.CenterVertically) {
                        TagDot(Color(t.color))
                        Spacer(Modifier.width(Space.md))
                        Text(t.name, style = label(Type.body), modifier = Modifier.weight(1f))
                        if (t.id != Rules.DEFAULT_TAG) {
                            Text(
                                Strings.Settings.remove, style = label(Type.foot, FontWeight.Bold, Ink.berry),
                                modifier = Modifier.tap { controller.upsertTag(t.id, t.name, t.color, archived = true) }.padding(Space.sm),
                            )
                        }
                    }
                }
                Text(Strings.Settings.tagsHint, style = label(Type.foot, FontWeight.Medium, Ink.muted))
                if (removed.isNotEmpty()) {
                    Spacer(Modifier.height(Space.sm))
                    Text(Strings.Settings.removedTags, style = label(Type.foot, FontWeight.Bold, Ink.muted))
                    removed.forEach { t ->
                        Row(Modifier.fillMaxWidth().padding(vertical = Space.xxs), verticalAlignment = Alignment.CenterVertically) {
                            TagDot(Color(t.color).copy(alpha = 0.45f))
                            Spacer(Modifier.width(Space.md))
                            Text(t.name, style = label(Type.body, color = Ink.muted), modifier = Modifier.weight(1f))
                            Text(
                                Strings.Settings.restore, style = label(Type.foot, FontWeight.Bold, Ink.leafDark),
                                modifier = Modifier.tap { controller.upsertTag(t.id, t.name, t.color, archived = false) }.padding(Space.sm),
                            )
                        }
                    }
                }
            }
            Section(Strings.Settings.soundSection) {
                Toggle(Strings.Settings.ambientMusic, s.music) { v -> controller.updateSettings { it.copy(music = v) } }
                Toggle(Strings.Settings.soundEffects, s.sfx) { v ->
                    controller.updateSettings { it.copy(sfx = v) }
                    if (v) controller.platform.playSound(buddy.app.Sound.TAP)
                }
            }
            Section(Strings.Settings.syncSection) {
                Text(Strings.Sync.description, style = label(Type.foot, FontWeight.Medium, Ink.muted))
                var url by remember { mutableStateOf(s.syncUrl) }
                var token by remember { mutableStateOf(s.syncToken) }
                Spacer(Modifier.height(Space.sm))
                TextInput(url, { url = it.trim() }, Strings.Sync.urlHint, Modifier.fillMaxWidth())
                Spacer(Modifier.height(Space.sm))
                TextInput(token, { token = it.trim() }, Strings.Sync.tokenHint, Modifier.fillMaxWidth(), secret = true)
                Toggle(Strings.Sync.automatic, s.autoSync) { v -> controller.updateSettings { it.copy(autoSync = v) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChunkyButton(Strings.Sync.save, color = Ink.leaf, textColor = Ink.onSelected, fontSize = Type.body, padding = PaddingValues(Space.xl, Space.md)) {
                        controller.updateSettings { it.copy(syncUrl = url, syncToken = token) }
                        scope.launch { controller.syncNow() }
                    }
                    Spacer(Modifier.width(Space.lg))
                    ChunkyButton(Strings.Sync.resync, fontSize = Type.foot, padding = PaddingValues(Space.lg, Space.sm)) {
                        controller.resyncFromScratch()
                    }
                    Spacer(Modifier.width(Space.lg))
                    Text(
                        when (val v = sync) {
                            SyncStatus.Off -> Strings.Sync.off
                            SyncStatus.Syncing -> Strings.Sync.syncing
                            is SyncStatus.Ok -> if (v.at == 0L) Strings.Sync.notYet else Strings.Sync.synced
                            is SyncStatus.Failed -> Strings.Sync.failed(v.message.take(60))
                        },
                        style = label(Type.foot, FontWeight.Medium, Ink.muted),
                    )
                }
                Text(Strings.Sync.device(controller.deviceId.take(8)), style = label(Type.caption, FontWeight.Medium, Ink.muted))
            }
            Section(Strings.Settings.backupSection) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    ChunkyButton(Strings.Backup.export, fontSize = Type.body, padding = PaddingValues(Space.xl, Space.md)) {
                        note = runCatching { Strings.Backup.saved(p.saveBackup(controller.exportBackup())) }.getOrElse { Strings.Backup.exportFailed(it.message.orEmpty()) }
                    }
                    ChunkyButton(Strings.Backup.importNewest, fontSize = Type.body, padding = PaddingValues(Space.xl, Space.md)) {
                        note = runCatching {
                            val f = p.newestBackup() ?: error(Strings.Backup.noBackup(p.backupDir.toString()))
                            Strings.Backup.imported(controller.importBackup(f.readText()), f.name)
                        }.getOrElse { Strings.Backup.importFailed(it.message.orEmpty()) }
                    }
                }
                Text(Strings.Backup.folder(p.backupDir.toString()), style = label(Type.caption, FontWeight.Medium, Ink.muted))
                note?.let { Text(it, style = label(Type.foot, FontWeight.Medium, Ink.leafDark)) }
            }
            Section(Strings.Updates.section) {
                Text(Strings.Updates.version(p.appVersion), style = label(Type.small, FontWeight.Medium, Ink.muted))
                var busy by remember { mutableStateOf(false) }
                var available by remember { mutableStateOf<buddy.app.UpdateInfo?>(null) }
                var status by remember { mutableStateOf<String?>(null) }
                Spacer(Modifier.height(Space.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChunkyButton(Strings.Updates.check, fontSize = Type.body, padding = PaddingValues(Space.xl, Space.md)) {
                        if (!busy) {
                            busy = true
                            status = null
                            scope.launch {
                                runCatching { p.checkForUpdate() }
                                    .onSuccess { info ->
                                        available = info
                                        status = if (info == null) Strings.Updates.upToDate else Strings.Updates.available
                                    }
                                    .onFailure { status = Strings.Updates.failed }
                                busy = false
                            }
                        }
                    }
                    Spacer(Modifier.width(Space.lg))
                    Text(
                        when {
                            busy -> Strings.Updates.checking
                            else -> status ?: ""
                        },
                        style = label(Type.foot, FontWeight.Medium, Ink.muted),
                    )
                }
                available?.let { info ->
                    Spacer(Modifier.height(Space.md))
                    Text("${Strings.Updates.available}: ${info.version}", style = label(Type.foot, FontWeight.Bold, Ink.leafDark))
                    Spacer(Modifier.height(Space.sm))
                    if (p.name == "android") {
                        var progress by remember(info.version) { mutableStateOf<Float?>(null) }
                        ChunkyButton(Strings.Updates.install, color = Ink.leaf, textColor = Ink.onSelected, fontSize = Type.body, padding = PaddingValues(Space.xl, Space.md)) {
                            progress = 0f
                            status = Strings.Updates.downloading(0)
                            scope.launch {
                                val r = p.installUpdate(info) { f ->
                                    if (f >= 0f) {
                                        progress = f
                                        status = Strings.Updates.downloading((f * 100).toInt())
                                    } else progress = null
                                }
                                when (r) {
                                    buddy.app.InstallResult.Started -> status = Strings.Updates.started
                                    buddy.app.InstallResult.SignatureMismatch -> status = Strings.Updates.signatureMismatch
                                    buddy.app.InstallResult.Failed -> { status = Strings.Updates.failed; progress = null }
                                }
                            }
                        }
                        progress?.let { f ->
                            Spacer(Modifier.height(Space.sm))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { f.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        ChunkyButton(Strings.Updates.openPage, fontSize = Type.body, padding = PaddingValues(Space.xl, Space.md)) {
                            p.openUrl(info.pageUrl)
                        }
                    }
                }
            }
            Section(Strings.Settings.aboutSection) {
                Text(
                    Strings.About.text,
                    style = label(Type.small, FontWeight.Medium, Ink.muted),
                )
            }
        }
        Spacer(Modifier.height(Space.md))
        CloseButton(onClose)
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = Space.lg)
            .sticker(Shapes.card, Ink.card, Strokes.line)
            .padding(Space.xl),
    ) {
        Text(title, style = label(Type.section, FontWeight.ExtraBold))
        Spacer(Modifier.height(Space.sm))
        content()
    }
}

@Composable
private fun Toggle(text: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = label(Type.body), modifier = Modifier.weight(1f))
        BuddySwitch(value, onChange)
    }
}

@Composable
private fun AppPicker(controller: AppController, onClose: () -> Unit) {
    val s by controller.settings.collectAsState()
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    LaunchedEffect(Unit) { apps = controller.platform.launchableApps() }
    SheetCard(onClose) {
        Title(if (s.allowMode) Strings.Settings.allowedApps else Strings.Settings.blockedApps, Type.title)
        Spacer(Modifier.height(Space.md))
        ScrollColumn(Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.Start) {
            val list = apps
            if (list == null) Text(Strings.Settings.loading, style = label(Type.body))
            list?.forEach { a ->
                val on = a.packageName in s.packages
                Row(
                    Modifier.fillMaxWidth().tap {
                        controller.updateSettings { it.copy(packages = if (on) it.packages - a.packageName else it.packages + a.packageName) }
                    }.padding(vertical = Space.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(on, null, colors = CheckboxDefaults.colors(checkedColor = Ink.leaf, uncheckedColor = Ink.outline))
                    Column {
                        Text(a.label, style = label(Type.body))
                        Text(a.packageName, style = label(Type.micro, FontWeight.Normal, Ink.muted))
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.md))
        ChunkyButton(Strings.Settings.done, color = Ink.leaf, textColor = Ink.onSelected) { onClose() }
    }
}
