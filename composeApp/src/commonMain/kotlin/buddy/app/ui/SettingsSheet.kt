package buddy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import buddy.app.AppController
import buddy.app.AppInfo
import buddy.app.SyncStatus
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

    Scrim(onClose) {
        Panel(Modifier.widthIn(max = 440.dp).padding(12.dp)) {
            Title("Settings")
            Spacer(Modifier.height(8.dp))
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Section("Focus") {
                    if (p.leavingAppInterrupts) {
                        Text("Grace period when leaving the app", style = label(14.sp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                            listOf(10, 20, 30, 60, 120).forEach { g -> Chip("${g}s", Ink.leaf, s.graceSec == g) { controller.updateSettings { it.copy(graceSec = g) } } }
                        }
                    }
                    Toggle("Keep screen on while focusing", s.screenOn) { v -> controller.updateSettings { it.copy(screenOn = v) } }
                    if (p.supportsExactAlarms && !p.exactAlarmsGranted()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("On-time break alerts", style = label(14.sp), modifier = Modifier.weight(1f))
                        ChunkyButton("Allow", fontSize = 14.sp, padding = PaddingValues(12.dp, 8.dp)) { p.requestExactAlarms() }
                    }
                }
                if (p.supportsDeepFocus) Section("Deep Focus") {
                    val ready = p.deepFocusReady()
                    Text(
                        if (ready) "Permissions granted ✓" else "Needs Usage access and Display over other apps.",
                        style = label(13.sp, FontWeight.Medium, if (ready) Ink.leafDark else Ink.berry),
                    )
                    if (!ready) ChunkyButton("Grant permissions", fontSize = 14.sp, padding = PaddingValues(12.dp, 8.dp)) { p.requestDeepFocusPermissions() }
                    Spacer(Modifier.height(6.dp))
                    Tabs(listOf("Block chosen apps", "Allow only chosen"), if (s.allowMode) 1 else 0, { i -> controller.updateSettings { it.copy(allowMode = i == 1) } })
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text("${s.packages.size} apps chosen", style = label(14.sp), modifier = Modifier.weight(1f))
                        ChunkyButton("Choose apps", fontSize = 14.sp, padding = PaddingValues(12.dp, 8.dp)) { pickingApps = true }
                    }
                }
                Section("Tags") {
                    state.tags.values.forEach { t ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(14.dp).clip(RoundedCornerShape(50)).background(Color(t.color)).border(1.5.dp, Ink.outline, RoundedCornerShape(50)))
                            Spacer(Modifier.width(8.dp))
                            Text(t.name, style = label(14.sp, color = if (t.archived) Ink.muted else Ink.outline), modifier = Modifier.weight(1f))
                            if (t.id != buddy.core.Rules.DEFAULT_TAG) Text(
                                if (t.archived) "restore" else "hide", style = label(12.sp, color = Ink.muted),
                                modifier = Modifier.clickable { controller.upsertTag(t.id, t.name, t.color, !t.archived) }.padding(6.dp),
                            )
                        }
                    }
                    Text("Add tags when starting a session.", style = label(12.sp, FontWeight.Medium, Ink.muted))
                }
                Section("Sound") {
                    Toggle("Ambient music while focusing", s.music) { v -> controller.updateSettings { it.copy(music = v) } }
                    Toggle("Sound effects", s.sfx) { v ->
                        controller.updateSettings { it.copy(sfx = v) }
                        if (v) controller.platform.playSound(buddy.app.Sound.TAP)
                    }
                }
                Section("Sync") {
                    Text("Your own server (see deploy/README.md). Leave empty to stay offline.", style = label(12.sp, FontWeight.Medium, Ink.muted))
                    var url by remember { mutableStateOf(s.syncUrl) }
                    var token by remember { mutableStateOf(s.syncToken) }
                    Spacer(Modifier.height(6.dp))
                    TextInput(url, { url = it.trim() }, "http://100.x.y.z:8787", Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    TextInput(token, { token = it.trim() }, "token", Modifier.fillMaxWidth(), secret = true)
                    Toggle("Sync automatically", s.autoSync) { v -> controller.updateSettings { it.copy(autoSync = v) } }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChunkyButton("Save & sync", color = Ink.leaf, textColor = Color.White, fontSize = 14.sp, padding = PaddingValues(12.dp, 8.dp)) {
                            controller.updateSettings { it.copy(syncUrl = url, syncToken = token) }
                            scope.launch { controller.syncNow() }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            when (val v = sync) {
                                SyncStatus.Off -> "off"
                                SyncStatus.Syncing -> "syncing…"
                                is SyncStatus.Ok -> if (v.at == 0L) "not synced yet" else "synced"
                                is SyncStatus.Failed -> "failed: ${v.message.take(60)}"
                            },
                            style = label(12.sp, FontWeight.Medium, Ink.muted),
                        )
                    }
                    Text("This device: ${controller.deviceId.take(8)}", style = label(11.sp, FontWeight.Medium, Ink.muted))
                }
                Section("Backup") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChunkyButton("Export", fontSize = 14.sp, padding = PaddingValues(12.dp, 8.dp)) {
                            note = runCatching { "Saved to ${p.saveBackup(controller.exportBackup())}" }.getOrElse { "Export failed: ${it.message}" }
                        }
                        ChunkyButton("Import newest", fontSize = 14.sp, padding = PaddingValues(12.dp, 8.dp)) {
                            note = runCatching {
                                val f = p.newestBackup() ?: error("no backup in ${p.backupDir}")
                                "Imported ${controller.importBackup(f.readText())} new events from ${f.name}"
                            }.getOrElse { "Import failed: ${it.message}" }
                        }
                    }
                    Text("Folder: ${p.backupDir}", style = label(11.sp, FontWeight.Medium, Ink.muted))
                    note?.let { Text(it, style = label(12.sp, FontWeight.Medium, Ink.leafDark)) }
                }
                Section("About") {
                    Text(
                        "Focus Buddy — free forever. No accounts, no ads, no purchases. Everything is earned by focusing, and your data stays on your devices and your own server.",
                        style = label(13.sp, FontWeight.Medium, Ink.muted),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            CloseButton(onClose)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(shape).background(Color.White).border(2.dp, Ink.outline, shape).padding(12.dp)) {
        Text(title, style = label(17.sp, FontWeight.ExtraBold))
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun Toggle(text: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = label(14.sp), modifier = Modifier.weight(1f))
        BuddySwitch(value, onChange)
    }
}

@Composable
private fun AppPicker(controller: AppController, onClose: () -> Unit) {
    val s by controller.settings.collectAsState()
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    LaunchedEffect(Unit) { apps = controller.platform.launchableApps() }
    Scrim(onClose) {
        Panel(Modifier.widthIn(max = 440.dp).padding(12.dp)) {
            Title(if (s.allowMode) "Allowed apps" else "Blocked apps", 22.sp)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                val list = apps
                if (list == null) Text("Loading…", style = label(14.sp))
                list?.forEach { a ->
                    val on = a.packageName in s.packages
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            controller.updateSettings { it.copy(packages = if (on) it.packages - a.packageName else it.packages + a.packageName) }
                        }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(on, null, colors = CheckboxDefaults.colors(checkedColor = Ink.leaf, uncheckedColor = Ink.outline))
                        Column {
                            Text(a.label, style = label(14.sp))
                            Text(a.packageName, style = label(10.sp, FontWeight.Normal, Ink.muted))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ChunkyButton("Done", color = Ink.leaf, textColor = Color.White) { onClose() }
        }
    }
}
