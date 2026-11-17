package com.example.geckobrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geckobrowser.BrowserViewModel

@Composable
fun SettingsScreen(vm: BrowserViewModel, onBack: () -> Unit) {
    val c = LocalArc.current
    val route = vm.settingsRoute
    // Back inside a sub-page returns to the category hub; from the hub itself, leave Settings.
    BackHandler {
        if (route != "hub") vm.setSettingsRoute("hub")
        else onBack()
    }

    Column(Modifier.fillMaxSize().background(c.settingsBg)) {
        when (route) {
            "look" -> LookAndFeelPage(vm)
            "search" -> SearchPreferencesPage(vm)
            "privacy" -> PrivacyAndSecurityPage(vm)
            "about" -> AboutPage(vm, onBack = { vm.setSettingsRoute("hub") })
            "authorNotes" -> AuthorNotesScreen(vm) { vm.setSettingsRoute("about") }
            "releaseNotes" -> ReleaseNotesScreen(vm) { vm.setSettingsRoute("about") }
            "siteSettings" -> SiteSettingsScreen(vm) { vm.setSettingsRoute("hub") }
            "extensions" -> {
                // Extensions is its own overlay, not a settings sub-route.
                vm.openExtensions()
                SettingsHub(vm, onBack)
            }
            else -> SettingsHub(vm, onBack)
        }
    }
}

/* ------------------------------------------------------------------ hub --- */

@Composable
private fun SettingsHub(vm: BrowserViewModel, onBack: () -> Unit) {
    val c = LocalArc.current
    SettingsHeader(title = "Settings", leading = {
        Box(Modifier.size(34.dp).clip(CircleShape).clickable { onBack() },
            contentAlignment = Alignment.Center) {
            Glyph(Ico.chevronLeft, c.text, size = 20.dp, strokeWidth = 2.2f)
        }
    }, icon = {
        Text("âš™", color = c.text, fontSize = 18.sp)
    })

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 40.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(c.surface)
                .clickable { vm.openAccount() }
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillAvatar("M", 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Mozilla account", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Sync tabs, history & passwords", color = c.muted, fontSize = 12.sp)
                }
                Glyph(Ico.chevronRight, c.muted, size = 16.dp, strokeWidth = 1.9f)
            }
        }

        SectionLabel("Categories")
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            CategoryTile(
                title = "Look and feel",
                description = "Make it feel like yours",
                icon = { Glyph(Ico.contrast, c.text, size = 26.dp, strokeWidth = 1.8f) },
                onClick = { vm.setSettingsRoute("look") },
            )
            CategoryTile(
                title = "Search and preferences",
                description = "Default search provider and suggestions",
                icon = { Glyph(Ico.find, c.text, size = 24.dp, strokeWidth = 1.8f) },
                onClick = { vm.setSettingsRoute("search") },
            )
            CategoryTile(
                title = "Extensions",
                description = "Discover, install and manage extensions",
                icon = { Glyph(Ico.puzzle, c.text, size = 24.dp, strokeWidth = 1.8f) },
                onClick = { vm.openExtensions() },
            )
            CategoryTile(
                title = "Privacy and security",
                description = "Fine-tune how your data is handled",
                icon = { Glyph(Ico.lock, c.text, size = 24.dp, strokeWidth = 1.8f) },
                onClick = { vm.setSettingsRoute("privacy") },
            )
            CategoryTile(
                title = "About",
                description = "App info, updates and acknowledgements",
                icon = { Glyph(Ico.globe, c.text, size = 24.dp, strokeWidth = 1.6f) },
                onClick = { vm.setSettingsRoute("about") },
            )
        }
    }
}

@Composable
private fun CategoryTile(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val c = LocalArc.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.chip),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Glyph(Ico.chevronRight, c.muted, size = 17.dp, strokeWidth = 1.9f)
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit, icon: @Composable (() -> Unit)? = null) {
    val c = LocalArc.current
    SettingsHeader(
        title = title,
        leading = {
            Box(Modifier.size(34.dp).clip(CircleShape).clickable { onBack() },
                contentAlignment = Alignment.Center) {
                Glyph(Ico.chevronLeft, c.text, size = 20.dp, strokeWidth = 2.2f)
            }
        },
        icon = icon,
    )
}

/* ------------------------------------------------------ look and feel --- */

@Composable
private fun LookAndFeelPage(vm: BrowserViewModel) {
    val c = LocalArc.current
    var accentPicker by remember { mutableStateOf(false) }
    PageHeader("Look and feel", { vm.setSettingsRoute("hub") }) {
        Glyph(Ico.contrast, c.text, size = 19.dp, strokeWidth = 1.9f)
    }
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 40.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        SectionLabel("Appearance")
        SettingsGroup {
            SettingsRow(
                "Appearance",
                icon = { Glyph(Ico.contrast, c.text, size = 20.dp, strokeWidth = 1.9f) },
                value = vm.appearanceMode.replaceFirstChar { it.uppercase() },
                onClick = {
                    val next = when (vm.appearanceMode) {
                        "system" -> "light"
                        "light" -> "dark"
                        else -> "system"
                    }
                    vm.updateAppearanceMode(next)
                },
            )
            SettingsRow(
                "Accent colour",
                icon = { Glyph(Ico.grid, c.text, size = 19.dp, strokeWidth = 1.7f) },
                value = AccentPresets.firstOrNull { it.key == vm.accentKey }?.label ?: "Firefox purple",
                onClick = { accentPicker = !accentPicker },
                trailing = {
                    Glyph(
                        Ico.chevronRight,
                        c.muted,
                        size = 15.dp,
                        strokeWidth = 2f,
                        modifier = Modifier.rotate(if (accentPicker) 90f else 0f),
                    )
                },
            )
            if (accentPicker) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(c.hairline))
                Column {
                    AccentPresets.forEach { preset ->
                        val selected = preset.key == vm.accentKey
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (selected) c.chip else Color.Transparent)
                                .clickable {
                                    vm.setAccent(preset.key)
                                    accentPicker = false
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(20.dp).clip(CircleShape).background(preset.dark))
                            Spacer(Modifier.width(11.dp))
                            Text(
                                preset.label,
                                color = c.text,
                                fontSize = 13.5.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(if (selected) "âœ“" else "", color = c.accent, fontSize = 13.sp)
                        }
                    }
                }
            }
            SettingsRow(
                "Text size",
                icon = { Glyph(Ico.textSize, c.text, size = 20.dp, strokeWidth = 1.9f) },
                value = "${vm.zoom}%",
                onClick = { vm.resetZoom() },
            )
            SettingsRow(
                "Language",
                icon = { Glyph(Ico.globe, c.text, size = 20.dp, strokeWidth = 1.7f) },
                value = "English (US)",
                onClick = { vm.showToast("Language: English (US)") },
            )
        }
    }
}

@Composable
private fun SearchPreferencesPage(vm: BrowserViewModel) {
    val c = LocalArc.current
    var engineExpanded by remember { mutableStateOf(false) }
    var engineAdding by remember { mutableStateOf(false) }
    var engineAddName by remember { mutableStateOf("") }
    var engineAddUrl by remember { mutableStateOf("") }
    var engineAddError by remember { mutableStateOf<String?>(null) }

    PageHeader("Search and preferences", { vm.setSettingsRoute("hub") }) {
        Glyph(Ico.find, c.text, size = 19.dp, strokeWidth = 1.8f)
    }
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 40.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        SectionLabel("Search")
        SettingsGroup {
            SettingsRow(
                "Default search engine",
                icon = { Glyph(Ico.find, c.text, size = 20.dp, strokeWidth = 1.9f) },
                value = vm.currentEngine.name,
                onClick = { engineExpanded = !engineExpanded },
                trailing = {
                    Glyph(
                        Ico.chevronRight,
                        c.muted,
                        size = 15.dp,
                        strokeWidth = 2f,
                        modifier = Modifier.rotate(if (engineExpanded) 90f else 0f),
                    )
                },
            )
            if (engineExpanded) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(c.hairline))
                EngineList(
                    vm = vm,
                    addState = engineAdding,
                    onToggleAdd = { engineAdding = !engineAdding },
                    name = engineAddName,
                    url = engineAddUrl,
                    error = engineAddError,
                    onNameChange = { engineAddName = it; engineAddError = null },
                    onUrlChange = { engineAddUrl = it; engineAddError = null },
                    onAddSave = {
                        if (vm.addCustomEngine(engineAddName, engineAddUrl)) {
                            engineAdding = false; engineAddName = ""; engineAddUrl = ""; engineAddError = null
                        } else {
                            engineAddError = "Enter a name and a URL containing %s."
                        }
                    },
                    onAddCancel = { engineAdding = false; engineAddError = null },
                )
            }
            SettingsRow(
                "Search suggestions",
                icon = { Glyph(Ico.find, c.text, size = 20.dp, strokeWidth = 1.9f) },
                trailing = { ArcSwitch(vm.suggestionsEnabled) { vm.toggleSearchSuggestions() } },
                onClick = { vm.toggleSearchSuggestions() },
            )
        }

        SectionLabel("Preferences")
        SettingsGroup {
            SettingsRow(
                "Open links in apps",
                icon = { Glyph(Ico.open, c.text, size = 19.dp, strokeWidth = 1.9f) },
                value = when (vm.openLinksMode) {
                    "off" -> "Off"
                    "on" -> "On"
                    else -> "Ask each time"
                },
                onClick = {
                    val next = when (vm.openLinksMode) {
                        "off" -> "on"
                        "on" -> "ask"
                        else -> "off"
                    }
                    vm.updateOpenLinksMode(next)
                },
            )
        }
    }
}

@Composable
private fun EngineList(
    vm: BrowserViewModel,
    addState: Boolean,
    onToggleAdd: () -> Unit,
    name: String,
    url: String,
    error: String?,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onAddSave: () -> Unit,
    onAddCancel: () -> Unit,
) {
    val c = LocalArc.current
    Column {
        val all = vm.allEngines()
        all.forEach { e ->
            val enabled = vm.isEngineEnabled(e.id)
            val selected = enabled && e.id == vm.defaultSearchEngineId
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) c.chip else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SiteFavicon(e.host, e.name, 21.dp)
                Spacer(Modifier.width(11.dp))
                Text(
                    e.name,
                    color = if (enabled) c.text else c.muted,
                    fontSize = 13.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (e.custom) {
                    Text(
                        "Custom",
                        color = c.muted,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .border(1.dp, c.stroke, RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                IconBtn(Ico.chevronUp) { vm.moveEngine(e.id, up = true) }
                IconBtn(Ico.chevronDown) { vm.moveEngine(e.id, up = false) }
                ArcSwitch(enabled) { vm.setEngineEnabled(e.id, it) }
                if (e.custom) {
                    Spacer(Modifier.width(6.dp))
                    IconBtn(Ico.trash) { vm.removeEngine(e.id) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
        if (!addState) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .clickable { onToggleAdd() }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ï¼‹", color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("Add search engine", color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                EngineField(name, onNameChange, "Name")
                EngineField(url, onUrlChange, "https://example.com/search?q=%s")
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = Color(0xFFC0392B), fontSize = 11.sp)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Cancel",
                        color = c.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(c.chip)
                            .clickable { onAddCancel() }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Add",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(c.accent)
                            .clickable { onAddSave() }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

/* ---------------------------------------------------- privacy and security --- */

@Composable
private fun PrivacyAndSecurityPage(vm: BrowserViewModel) {
    val c = LocalArc.current
    PageHeader("Privacy and security", { vm.setSettingsRoute("hub") }) {
        Glyph(Ico.lock, c.text, size = 19.dp, strokeWidth = 1.8f)
    }
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 40.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        SectionLabel("Privacy")
        SettingsGroup {
            SettingsRow(
                "Tracking protection",
                icon = { Glyph(Ico.lock, c.text, size = 20.dp, strokeWidth = 1.8f) },
                value = if (vm.etpStrict) "Strict" else "Standard",
                onClick = {
                    vm.setTrackingStrict(!vm.etpStrict)
                    vm.showToast(
                        if (vm.etpStrict) "Tracking protection: strict"
                        else "Tracking protection: standard"
                    )
                },
            )
            SettingsRow(
                "Cookie banners",
                icon = { Glyph(Ico.close, c.text, size = 19.dp, strokeWidth = 2f) },
                trailing = {
                    ArcSwitch(vm.cookieBannerHandling) { vm.toggleCookieBannerHandling() }
                },
                onClick = { vm.toggleCookieBannerHandling() },
            )
            SettingsRow(
                "HTTPS-Only Mode",
                icon = { Glyph(Ico.lock, c.text, size = 20.dp, strokeWidth = 1.8f) },
                trailing = { ArcSwitch(true) { } },
                onClick = { },
            )
            SettingsRow(
                "Site permissions",
                icon = { Glyph(Ico.globe, c.text, size = 20.dp, strokeWidth = 1.7f) },
                onClick = { vm.setSettingsRoute("siteSettings") },
            )
        }

        SectionLabel("History")
        SettingsGroup {
            SettingsRow(
                "History",
                icon = { Glyph(Ico.clock, c.text, size = 20.dp, strokeWidth = 1.9f) },
                value = "${vm.historyCount} pages",
                onClick = { vm.openHistory() },
            )
            SettingsRow(
                "Clear browsing data",
                icon = { Glyph(Ico.trash, c.text, size = 19.dp, strokeWidth = 1.8f) },
                onClick = { vm.showToast("Browsing data cleared") },
            )
        }

        SectionLabel("Downloads")
        SettingsGroup {
            SettingsRow(
                "Downloads",
                icon = { Glyph(Ico.download, c.text, size = 20.dp, strokeWidth = 1.9f) },
                onClick = { vm.openDownloads() },
            )
        }
    }
}

/* ----------------------------------------------------------------- about --- */

@Composable
private fun AboutPage(vm: BrowserViewModel, onBack: () -> Unit) {
    val c = LocalArc.current
    PageHeader("About", onBack) {
        Glyph(Ico.globe, c.text, size = 19.dp, strokeWidth = 1.6f)
    }
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(22.dp))
        HorizenMark(progress = 1f, modifier = Modifier.size(120.dp))
        Spacer(Modifier.height(16.dp))
        Text("horiZen", color = c.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("horiZen for Android", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(22.dp))

        SettingsGroup {
            SettingsRow("Version", icon = { Glyph(Ico.grid, c.text, size = 19.dp, strokeWidth = 1.7f) }, value = com.example.geckobrowser.BuildConfig.VERSION_NAME, onClick = null)
            SettingsRow("GeckoView", icon = { Glyph(Ico.globe, c.text, size = 19.dp, strokeWidth = 1.7f) }, value = "138", onClick = null)
        }

        SectionLabel("Updates")
        SettingsGroup {
            SettingsRow(
                "Update channel",
                icon = { Glyph(Ico.refresh, c.text, size = 20.dp, strokeWidth = 1.8f) },
                value = vm.updateChannel.replaceFirstChar { it.uppercase() },
                onClick = {
                    val next = if (vm.updateChannel == "beta") "stable" else "beta"
                    vm.setUpdateChannel(next)
                },
            )
            SettingsRow(
                "Check for updates on launch",
                icon = { Glyph(Ico.download, c.text, size = 19.dp, strokeWidth = 1.9f) },
                trailing = { ArcSwitch(vm.checkOnLaunch) { vm.updateCheckOnLaunch(it) } },
                onClick = { vm.updateCheckOnLaunch(!vm.checkOnLaunch) },
            )
            SettingsRow(
                "Check for updates",
                icon = { Glyph(Ico.refresh, c.text, size = 20.dp, strokeWidth = 1.8f) },
                value = when {
                    vm.updateProgress >= 0f -> "Downloadingâ€¦"
                    vm.updateChecking -> "Checkingâ€¦"
                    vm.updateInfo != null -> "New: ${vm.updateInfo!!.displayName}"
                    else -> "Check now"
                },
                onClick = { vm.checkForUpdates() },
            )
        }

        SectionLabel("Learn more")
        SettingsGroup {
            SettingsRow(
                "What's new",
                icon = { Glyph(Ico.refresh, c.text, size = 19.dp, strokeWidth = 1.7f) },
                value = "Release notes",
                onClick = { vm.setSettingsRoute("releaseNotes") },
            )
            SettingsRow(
                "Author notes",
                icon = { Glyph(Ico.person, c.text, size = 19.dp, strokeWidth = 1.8f) },
                value = "Notes from the author",
                onClick = { vm.setSettingsRoute("authorNotes") },
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "horiZen is a fast, private browser built on GeckoView â€” Mozilla's open-source browser engine â€” with an Arc-inspired chrome. Add-ons install as AMO-compatible signed XPIs.",
            color = c.muted,
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Â© horiZen. GeckoView and Firefox are trademarks of Mozilla Foundation.",
            color = c.muted,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun IconBtn(icon: List<androidx.compose.ui.graphics.Path>, onClick: () -> Unit) {
    val c = LocalArc.current
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Glyph(icon, c.muted, size = 17.dp, strokeWidth = 1.9f)
    }
}

@Composable
private fun EngineField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val c = LocalArc.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.chip)
            .border(1.dp, c.stroke, RoundedCornerShape(10.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 12.5.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isEmpty()) {
            Text(placeholder, color = c.muted, fontSize = 12.5.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}
