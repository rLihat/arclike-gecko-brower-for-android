package com.example.geckobrowser.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geckobrowser.BrowserViewModel

@Composable
fun SettingsScreen(
    vm: BrowserViewModel,
    onBack: () -> Unit,
    /** Blurred snapshot of the page behind Settings. */
    frost: ImageBitmap? = null,
) {
    val c = LocalArc.current
    val route = vm.settingsRoute
    // Back inside a sub-page returns to the category hub; from the hub itself, leave Settings.
    BackHandler {
        if (route != "hub") vm.updateSettingsRoute("hub")
        else onBack()
    }

    ScreenHaze(enabled = LocalFrost.current && frost != null) {
    Box(Modifier.fillMaxSize()) {
        if (frost != null) {
            Image(
                bitmap = frost,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().hazeBackdrop(),
            )
            Box(Modifier.fillMaxSize().background(c.surface.copy(alpha = 0.74f)))
        }
        Column(
            Modifier
                .fillMaxSize()
                .then(if (frost == null) Modifier.background(c.settingsBg) else Modifier),
        ) {
        when (route) {
            "look" -> LookAndFeelPage(vm)
            "search" -> SearchPreferencesPage(vm)
            "privacy" -> PrivacyAndSecurityPage(vm)
            "about" -> AboutPage(vm, onBack = { vm.updateSettingsRoute("hub") })
            "authorNotes" -> AuthorNotesScreen(vm) { vm.updateSettingsRoute("about") }
            "releaseNotes" -> ReleaseNotesScreen(vm) { vm.updateSettingsRoute("about") }
            "siteSettings" -> SiteSettingsScreen(vm) { vm.updateSettingsRoute("hub") }
            "extensions" -> {
                // Extensions is its own overlay, not a settings sub-route.
                vm.openExtensions()
                SettingsHub(vm, onBack)
            }
            else -> SettingsHub(vm, onBack)
        }
        }
    }
    }
}

/* ------------------------------------------------------------------ hub --- */

private data class SettingsEntry(val label: String, val description: String, val route: String, val icon: String)

private val settingsEntries = listOf(
    SettingsEntry("Appearance", "Light or dark, scheduled by the system", "look", "contrast"),
    SettingsEntry("Accent color", "Personalize the browser accent", "look", "grid"),
    SettingsEntry("Text size", "Adjust page and UI text scale", "look", "textSize"),
    SettingsEntry("Language", "App and content language", "look", "globe"),
    SettingsEntry("Haptic feedback", "Short acknowledgements for gestures and actions", "look", "grid"),
    SettingsEntry("Search provider", "Default engine for typed queries", "search", "engine"),
    SettingsEntry("Search suggestions", "Live suggestions as you type", "search", "find"),
    SettingsEntry("Open links in apps", "Hand app links off to installed apps", "search", "open"),
    SettingsEntry("Default browser", "Set horiZen as the default browser", "search", "globe"),
    SettingsEntry("Download folder", "Where completed downloads are saved", "search", "download"),
    SettingsEntry("Tracking protection", "Block cross-site trackers", "privacy", "lock"),
    SettingsEntry("Cookie banners", "Auto-answer cookie consent banners", "privacy", "close"),
    SettingsEntry("HTTPS-Only Mode", "Require secure connections", "privacy", "lock"),
    SettingsEntry("History", "Visit history and clearing options", "privacy", "clock"),
    SettingsEntry("Clear browsing data", "Delete history, cookies and cache", "privacy", "trash"),
    SettingsEntry("Site permissions", "Per-site camera/mic/notification choices", "siteSettings", "globe"),
    SettingsEntry("Extensions", "Install and manage add-ons", "extensions", "puzzle"),
    SettingsEntry("Update channel", "Choose between Beta and Stable builds", "about", "refresh"),
    SettingsEntry("Check for updates on launch", "Auto-check on cold start", "about", "download"),
    SettingsEntry("Check for updates", "Manual update check", "about", "refresh"),
    SettingsEntry("What's new", "Release notes for the current build", "releaseNotes", "copy"),
    SettingsEntry("Author notes", "Notes from the author", "authorNotes", "person"),
)

@Composable
private fun SettingsHub(vm: BrowserViewModel, onBack: () -> Unit) {
    val c = LocalArc.current
    val query = vm.settingsSearch
    Column(Modifier.fillMaxSize().background(c.settingsBg)) {
        SettingsHeader(title = "Settings", leading = {
            Box(Modifier.size(34.dp).clip(CircleShape).clickable { onBack() },
                contentAlignment = Alignment.Center) {
                Glyph(Ico.chevronLeft, c.text, size = 20.dp, strokeWidth = 2.2f)
            }
        }, icon = {
            Glyph(Ico.gear, c.text, size = 19.dp, strokeWidth = 1.8f)
        })

        // Shrink the list + pill above the IME so the search field is never
        // covered by the keyboard.
        Box(Modifier.weight(1f).fillMaxWidth().imePadding()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 96.dp),
            ) {
                Spacer(Modifier.height(10.dp))
                if (query.isBlank()) {
                    accountCard(vm, c)
                    SectionLabel("Categories")
                    CategoryGrid(vm, c)
                } else {
                    SearchResults(vm, c, query)
                }
            }

            SearchPill(query, vm::onSettingsSearchChange, Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp))
        }
    }
}

@Composable
private fun accountCard(vm: BrowserViewModel, c: ArcColors) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.menuCard)
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
}

@Composable
private fun CategoryGrid(vm: BrowserViewModel, c: ArcColors) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CategoryTile(
            title = "Look and feel",
            description = "Make it feel like yours",
            icon = { Glyph(Ico.contrast, c.text, size = 26.dp, strokeWidth = 1.8f) },
            onClick = { vm.updateSettingsRoute("look") },
        )
        CategoryTile(
            title = "Search and preferences",
            description = "Default search provider and suggestions",
            icon = { Glyph(Ico.userCog, c.text, size = 24.dp, strokeWidth = 1.8f) },
            onClick = { vm.updateSettingsRoute("search") },
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
            icon = { Glyph(Ico.shieldCheck, c.text, size = 24.dp, strokeWidth = 1.8f) },
            onClick = { vm.updateSettingsRoute("privacy") },
        )
        CategoryTile(
            title = "About",
            description = "App info, updates and acknowledgements",
            icon = { Glyph(Ico.globe, c.text, size = 24.dp, strokeWidth = 1.6f) },
            onClick = { vm.updateSettingsRoute("about") },
        )
    }
}

@Composable
private fun SearchResults(vm: BrowserViewModel, c: ArcColors, query: String) {
    Text(
        "Matches for \"$query\"",
        color = c.muted,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
    )
    val filtered = settingsEntries.filter { matches(query, it.label) }
    if (filtered.isEmpty()) {
        Text(
            "No matches",
            color = c.muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 4.dp, top = 20.dp),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        filtered.forEach { entry ->
            val ico = when (entry.icon) {
                "contrast" -> Ico.contrast
                "grid" -> Ico.grid
                "textSize" -> Ico.textSize
                "globe" -> Ico.globe
                "engine" -> Ico.engine
                "open" -> Ico.open
                "lock" -> Ico.lock
                "close" -> Ico.close
                "clock" -> Ico.clock
                "trash" -> Ico.trash
                "puzzle" -> Ico.puzzle
                "refresh" -> Ico.refresh
                "download" -> Ico.download
                "copy" -> Ico.copy
                "person" -> Ico.person
                else -> Ico.grid
            }
            CategoryTile(
                title = entry.label,
                description = entry.description,
                icon = { Glyph(ico, c.text, size = 22.dp, strokeWidth = 1.8f) },
                onClick = {
                    when (entry.route) {
                        "siteSettings" -> vm.openSiteSettings()
                        "extensions" -> vm.openExtensions()
                        else -> vm.updateSettingsRoute(entry.route)
                    }
                },
            )
        }
    }
}

private fun matches(query: String, label: String) =
    query.isBlank() || label.contains(query, ignoreCase = true)

@Composable
private fun SearchPill(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalArc.current
    Box(
        modifier
            .fillMaxWidth(0.7f)
            .height(58.dp)
            .clip(RoundedCornerShape(29.dp))
            .background(c.navSurface)
            .then(rememberGlass(c.navSurface, fps = 20, maxEdge = 170, radius = 6)),
    ) {
        if (LocalFrost.current && LocalHazeState.current != null) {
            GlassFill(dark = c.dark)
        }
        Row(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Glyph(Ico.search, c.muted, size = 18.dp, strokeWidth = 2f)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text("Search settings", color = c.muted, fontSize = 14.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
            .background(c.menuCard)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.menuCard),
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
    var appearancePicker by remember { mutableStateOf(false) }
    PageHeader("Look and feel", { vm.updateSettingsRoute("hub") }) {
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
                onClick = { appearancePicker = !appearancePicker },
                trailing = {
                    Glyph(
                        Ico.chevronRight, c.muted, size = 15.dp, strokeWidth = 2f,
                        modifier = Modifier.rotate(if (appearancePicker) 90f else 0f),
                    )
                },
            )
            if (appearancePicker) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(c.hairline))
                Column {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark", "oled" to "OLED").forEach { (key, label) ->
                        val selected = key == vm.appearanceMode
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (selected) c.menuCard else Color.Transparent)
                                .clickable {
                                    vm.updateAppearanceMode(key)
                                    appearancePicker = false
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, color = c.text, fontSize = 13.5.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            Spacer(Modifier.weight(1f))
                            if (selected) Glyph(Ico.check, c.accent, size = 15.dp, strokeWidth = 2.2f)
                        }
                    }
                }
            }
            SettingsRow(
                "Accent colour",
                icon = { Glyph(Ico.palette, c.text, size = 19.dp, strokeWidth = 1.7f) },
                value = AccentPresets.firstOrNull { it.key == vm.accentKey }?.label ?: "Dynamic",
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
                                .background(if (selected) c.menuCard else Color.Transparent)
                                .clickable {
                                    vm.setAccent(preset.key)
                                    accentPicker = false
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(preset.dark)
                                    .border(1.dp, c.stroke, CircleShape),
                            )
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
                            if (selected) {
                                Glyph(Ico.check, c.accent, size = 15.dp, strokeWidth = 2.2f)
                            }
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.menuCard).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(Ico.opacity, c.text, size = 16.dp, strokeWidth = 1.9f)
                    Spacer(Modifier.width(8.dp))
                    Text("Frosted glass", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("auto" to "Auto", "on" to "On", "off" to "Off", "v1" to "V1").forEach { (key, label) ->
                        val selected = key == vm.frostedGlassMode
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selected) c.accent else c.surface)
                                .clickable { vm.updateFrostedGlassMode(key) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (key == "v1") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(c.surface)
                                            .border(1.dp, c.stroke, RoundedCornerShape(3.dp)),
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        "V1",
                                        color = if (selected) Color.White else c.text,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            } else {
                                Text(
                                    label,
                                    color = if (selected) Color.White else c.text,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
            SettingsRow(
                "Reduce transparency",
                icon = { Glyph(Ico.layersMinus, c.text, size = 20.dp, strokeWidth = 1.9f) },
                trailing = {
                    ArcSwitch(vm.reduceTransparency) { vm.updateReduceTransparency(it) }
                },
                onClick = { vm.updateReduceTransparency(!vm.reduceTransparency) },
            )
            SettingsRow(
                "Text size",
                icon = { Glyph(Ico.textSize, c.text, size = 20.dp, strokeWidth = 1.9f) },
                value = "${vm.zoom}%",
                onClick = { vm.resetZoom() },
            )
            SettingsRow(
                "Language",
                icon = { Glyph(Ico.languages, c.text, size = 20.dp, strokeWidth = 1.7f) },
                value = "English (US)",
                onClick = { vm.showToast("Language: English (US)") },
            )
        }
        SectionLabel("Touch and motion")
        SettingsGroup {
            SettingsRow(
                "Haptic feedback",
                icon = { Glyph(Ico.vibrate, c.text, size = 21.dp, strokeWidth = 1.9f) },
                trailing = {
                    ArcSwitch(vm.hapticFeedback) { vm.toggleHapticFeedback() }
                },
                onClick = { vm.toggleHapticFeedback() },
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
    var folderDialog by remember { mutableStateOf(false) }
    var folderText by remember { mutableStateOf("") }

    val defaultBrowserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { vm.refreshDefaultBrowser() }
    val defaultBrowserVersion = vm.defaultBrowserVersion

    PageHeader("Search and preferences", { vm.updateSettingsRoute("hub") }) {
        Glyph(Ico.userCog, c.text, size = 19.dp, strokeWidth = 1.8f)
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
                icon = { Glyph(Ico.engine, c.text, size = 20.dp, strokeWidth = 1.9f) },
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
                icon = { Glyph(Ico.lightbulb, c.text, size = 20.dp, strokeWidth = 1.9f) },
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
            SettingsRow(
                "Default browser",
                icon = { Glyph(Ico.badgeCheck, c.text, size = 20.dp, strokeWidth = 1.7f) },
                value = if (defaultBrowserVersion >= 0 && vm.isDefaultBrowser()) "horiZen" else "Not set",
                onClick = {
                    val intent = vm.defaultBrowserIntent()
                    if (intent != null) defaultBrowserLauncher.launch(intent)
                    else vm.openDefaultBrowserSettings()
                },
            )
            SettingsRow(
                "Download folder",
                icon = { Glyph(Ico.folder, c.text, size = 19.dp, strokeWidth = 1.9f) },
                value = if (vm.downloadSubfolder.isBlank()) "Download" else "Download/${vm.downloadSubfolder}",
                onClick = {
                    folderText = vm.downloadSubfolder
                    folderDialog = true
                },
            )
        }
    }

    if (folderDialog) {
        AlertDialog(
            onDismissRequest = { folderDialog = false },
            title = { Text("Download folder", color = c.text, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(
                        "Subfolder inside the Downloads collection. Leave blank to use Download.",
                        color = c.muted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    EngineField(folderText, { folderText = it }, "e.g. horiZen")
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        vm.updateDownloadSubfolder("")
                        folderDialog = false
                    }) { Text("Reset", color = c.muted, fontSize = 13.sp) }
                    TextButton(onClick = {
                        vm.updateDownloadSubfolder(folderText)
                        folderDialog = false
                    }) { Text("Save", color = c.accent, fontSize = 13.sp) }
                }
            },
            dismissButton = {
                TextButton(onClick = { folderDialog = false }) {
                    Text("Cancel", color = c.text, fontSize = 13.sp)
                }
            },
            containerColor = c.surface,
        )
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
                    .background(if (selected) c.menuCard else Color.Transparent)
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
                Text("+", color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
                            .background(c.menuCard)
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
    PageHeader("Privacy and security", { vm.updateSettingsRoute("hub") }) {
        Glyph(Ico.shieldCheck, c.text, size = 19.dp, strokeWidth = 1.8f)
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
                icon = { Glyph(Ico.shieldCheck, c.text, size = 20.dp, strokeWidth = 1.8f) },
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
                icon = { Glyph(Ico.cookie, c.text, size = 19.dp, strokeWidth = 2f) },
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
                icon = { Glyph(Ico.panelTop, c.text, size = 20.dp, strokeWidth = 1.7f) },
                onClick = { vm.updateSettingsRoute("siteSettings") },
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
        Glyph(Ico.info, c.text, size = 19.dp, strokeWidth = 1.8f)
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
            SettingsRow("Version", icon = { Glyph(Ico.packageIcon, c.text, size = 19.dp, strokeWidth = 1.7f) }, value = com.example.geckobrowser.BuildConfig.VERSION_NAME, onClick = null)
            SettingsRow("GeckoView", icon = { Glyph(Ico.squareCode, c.text, size = 19.dp, strokeWidth = 1.7f) }, value = "138", onClick = null)
        }

        SectionLabel("Updates")
        SettingsGroup {
            SettingsRow(
                "Update channel",
                icon = { Glyph(Ico.diagramFlow, c.text, size = 20.dp, strokeWidth = 1.8f) },
                value = vm.updateChannel.replaceFirstChar { it.uppercase() },
                onClick = {
                    val next = if (vm.updateChannel == "beta") "stable" else "beta"
                    vm.updateUpdateChannel(next)
                },
            )
            SettingsRow(
                "Check for updates on launch",
                icon = { Glyph(Ico.circleFadingArrowUp, c.text, size = 19.dp, strokeWidth = 1.9f) },
                trailing = { ArcSwitch(vm.checkOnLaunch) { vm.updateCheckOnLaunch(it) } },
                onClick = { vm.updateCheckOnLaunch(!vm.checkOnLaunch) },
            )
            SettingsRow(
                "Check for updates",
                icon = { Glyph(Ico.searchCheck, c.text, size = 20.dp, strokeWidth = 1.8f) },
                value = when {
                    vm.updateProgress >= 0f -> "Downloading..."
                    vm.updateChecking -> "Checking..."
                    vm.updateInfo != null -> "New: ${vm.updateInfo!!.displayName}"
                    else -> "Check now"
                },
                onClick = { vm.checkForUpdates(manual = true) },
            )
        }

        SectionLabel("Learn more")
        SettingsGroup {
            SettingsRow(
                "What's new",
                icon = { Glyph(Ico.sparkles, c.text, size = 19.dp, strokeWidth = 1.7f) },
                value = "Release notes",
                onClick = { vm.updateSettingsRoute("releaseNotes") },
            )
            SettingsRow(
                "Author notes",
                icon = { Glyph(Ico.badgeInfo, c.text, size = 19.dp, strokeWidth = 1.8f) },
                value = "Notes from the author",
                onClick = { vm.updateSettingsRoute("authorNotes") },
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "horiZen is a fast, private browser built on GeckoView - Mozilla's open-source browser engine - with an Arc-inspired chrome. Add-ons install as AMO-compatible signed XPIs.",
            color = c.muted,
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Copyright horiZen. GeckoView and Firefox are trademarks of Mozilla Foundation.",
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
            .background(c.menuCard)
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
