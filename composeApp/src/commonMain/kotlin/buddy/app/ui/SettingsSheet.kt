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
        Title("Settings")
        Spacer(Modifier.height(Space.md))
        ScrollColumn(Modifier.weight(1f, fill = false)) {
            Section("Focus") {
                if (p.leavingAppInterrupts) {
                    Text("Grace period when leaving the app", style = label(Type.body))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.padding(vertical = Space.sm)) {
                        listOf(10, 20, 30, 60, 120).forEach { g ->
                            Chip("${g}s", Ink.leaf, s.graceSec == g) { controller.updateSettings { it.copy(graceSec = g) } }
                        }
                    }
                }
                Toggle("Keep screen on while focusing", s.screenOn) { v -> controller.updateSettings { it.copy(screenOn = v) } }
                if (p.supportsExactAlarms && !p.exactAlarmsGranted()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("On-time break alerts", style = label(Type.body), modifier = Modifier.weight(1f))
                    ChunkyButton("Allow", fontSize = Type.body, padding = PaddingValues(Space.xl, Space.md)) { p.requestExactAlarms() }
                }
            }
            if (p.supportsDeepFocus) Section("Deep Focus") {
                val ready = p.deepFocusReady()
                Text(
                    if (ready) "Permissions granted ✓" else "Needs Usage access and Display over other apps.",
                    style = label(Type.small, FontWeight.Medium, if (ready) Ink.leafDark else Ink.berry),
                )
                if (!ready) ChunkyButton("Grant permissions", fontSize = Type.body, padding = PaddingValues(Space.xl, Space.md)) { p.requestDeepFocusPermissions() }
                Spacer(Modifier.height(Space.sm))
                Tabs(listOf("Block chosen apps", "Allow only chosen"), if (s.allowMode) 1 else 0, { i -> controller.updateSettings { it.copy(allowMode = i == 1) } })
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.md)) {
                    Text("${s.packages.size} apps chosen", style = label(Type.body), modifier = Modifier.weight(1f))
                    ChunkyButton("Choose apps", fontSize = Type.body, padding = PaddingValues(Space.xl, Space.md)) { pickingApps = true }
                }
            }
            Section("Tags") {
                state.tags.values.forEach { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.xxs), verticalAlignment = Alignment.CenterVertically) {
                        TagDot(Color(t.color))
                        Spacer(Modifier.width(Space.md))
                        Text(t.name, style = label(Type.body, color = if (t.archived) Ink.muted else Ink.outline), modifier = Modifier.weight(1f))
                        if (t.id != Rules.DEFAULT_TAG) Text(
                            if (t.archived) "restore" else "hide", style = label(Type.foot, color = Ink.muted),
                            modifier = Modifier.tap { controller.upsertTag(t.id, t.name, t.color, !t.archived) }.padding(Space.sm),
                        )
                    }
                }
                Text("Add tags when starting a session.", style = label(Type.foot, FontWeight.Medium, Ink.muted))
            }
            Section("Sound") {
                Toggle("Ambient music while focusing", s.music) { v -> controller.updateSettings { it.copy(music = v) } }
                Toggle("Sound effects", s.sfx) { v ->
                    controller.updateSettings { it.copy(sfx = v) }
                    if (v) controller.platform.playSound(buddy.app.Sound.TAP)
                }
            }
            Section("Sync") {
                Text("Sync through a server you control. Leave empty to stay offline.", style = label(Type.foot, FontWeight.Medium, Ink.muted))
                var url by remember { mutableStateOf(s.syncUrl) }
                var token by remember { mutableStateOf(s.syncToken) }
                Spacer(Modifier.height(Space.sm))
                TextInput(url, { url = it.trim() }, "http://100.x.y.z:8787", Modifier.fillMaxWidth())
                Spacer(Modifier.height(Space.sm))
                TextInput(token, { token = it.trim() }, "token", Modifier.fillMaxWidth(), secret = true)
                Toggle("Sync automatically", s.autoSync) { v -> controller.updateSettings { it.copy(autoSync = v) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChunkyButton("Save & sync", color = Ink.leaf, textColor = Ink.onSelected, fontSize = Type.body, padding = PaddingValues(Space.xl, Space.md)) {
                        controller.updateSettings { it.copy(syncUrl = url, syncToken = token) }
                        scope.launch { controller.syncNow() }
                    }
                    Spacer(Modifier.width(Space.lg))
                    Text(
                        when (val v = sync) {
                            SyncStatus.Off -> "off"
                            SyncStatus.Syncing -> "syncing…"
                            is SyncStatus.Ok -> if (v.at == 0L) "not synced yet" else "synced"
                            is SyncStatus.Failed -> "failed: ${v.message.take(60)}"
                        },
                        style = label(Type.foot, FontWeight.Medium, Ink.muted),
                    )
                }
                Text("This device: ${controller.deviceId.take(8)}", style = label(Type.caption, FontWeight.Medium, Ink.muted))
            }
            Section("Backup") {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    ChunkyButton("Export", fontSize = Type.body, padding = PaddingValues(Space.xl, Space.md)) {
                        note = runCatching { "Saved to ${p.saveBackup(controller.exportBackup())}" }.getOrElse { "Export failed: ${it.message}" }
                    }
                    ChunkyButton("Import newest", fontSize = Type.body, padding = PaddingValues(Space.xl, Space.md)) {
                        note = runCatching {
                            val f = p.newestBackup() ?: error("no backup in ${p.backupDir}")
                            "Imported ${controller.importBackup(f.readText())} new events from ${f.name}"
                        }.getOrElse { "Import failed: ${it.message}" }
                    }
                }
                Text("Folder: ${p.backupDir}", style = label(Type.caption, FontWeight.Medium, Ink.muted))
                note?.let { Text(it, style = label(Type.foot, FontWeight.Medium, Ink.leafDark)) }
            }
            Section("About") {
                Text(
                    "Focus Buddy. Everything is unlocked. No accounts, no ads, no purchases. Acorns come from focusing, and your data stays on your devices or your own server.",
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
        Title(if (s.allowMode) "Allowed apps" else "Blocked apps", Type.title)
        Spacer(Modifier.height(Space.md))
        ScrollColumn(Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.Start) {
            val list = apps
            if (list == null) Text("Loading…", style = label(Type.body))
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
        ChunkyButton("Done", color = Ink.leaf, textColor = Ink.onSelected) { onClose() }
    }
}
