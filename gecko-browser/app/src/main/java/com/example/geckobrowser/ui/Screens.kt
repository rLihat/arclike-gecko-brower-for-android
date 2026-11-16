package com.example.geckobrowser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geckobrowser.BrowserViewModel
import com.example.geckobrowser.DownloadItem
import com.example.geckobrowser.DownloadStatus
import com.example.geckobrowser.ExtensionItem
import com.example.geckobrowser.PermissionKind
import com.example.geckobrowser.formatBytes
import com.example.geckobrowser.formatWhen

/* ------------------------------------------------------------------ home */

private data class Pin(
    val label: String,
    val host: String,
    val url: String,
    val letter: String,
    val color: Color,
)

private val pins = listOf(
    Pin("YouTube", "youtube.com", "https://youtube.com", "▶", Color(0xFFFF0033)),
    Pin("GitHub", "github.com", "https://github.com", "G", Color(0xFF24292F)),
    Pin("Gmail", "mail.google.com", "https://mail.google.com", "M", Color(0xFFD93B30)),
    Pin("Calendar", "calendar.google.com", "https://calendar.google.com", "C", Color(0xFFF6A04D)),
    Pin("News", "news.google.com", "https://news.google.com", "N", Color(0xFF7BC96F)),
    Pin("Music", "music.youtube.com", "https://music.youtube.com", "♪", Color(0xFFE66A8B)),
)

@Composable
fun HomeScreen(onSearch: () -> Unit, onOpen: (String) -> Unit) {
    val c = LocalArc.current
    val progress = remember { Animatable(0f) }
    val offsetY = remember { Animatable(40f) }
    LaunchedEffect(Unit) {
        // Animate the logo up into view only on first show.
        progress.animateTo(1f, tween(520, easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)))
    }
    LaunchedEffect(Unit) {
        offsetY.animateTo(0f, tween(520, easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)))
    }
    // Ultra-minimal new-tab page: nothing but the brand mark. Tapping anywhere
    // opens search; there are no recents, pins or shortcuts.
    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onSearch() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = offsetY.value.dp),
        ) {
            HorizenMark(progress = progress.value, modifier = Modifier.size(152.dp))
        }
    }
}

@Composable
private fun PinCell(pin: Pin, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalArc.current
    Column(
        modifier.clickable { onClick() }.padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(50.dp).clip(RoundedCornerShape(17.dp)).background(pin.color),
            contentAlignment = Alignment.Center,
        ) {
            Text(pin.letter, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(7.dp))
        Text(pin.label, color = c.text, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RecentCard(title: String, host: String, onClick: () -> Unit) {
    val c = LocalArc.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.navSurface)
            .clickable { onClick() }
            .padding(vertical = 15.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(host, color = c.muted, fontSize = 11.5.sp)
    }
}

/* -------------------------------------------------------------- settings */

private fun matches(query: String, label: String) =
    query.isBlank() || label.contains(query, ignoreCase = true)

@Composable
fun SettingsScreen(
    vm: BrowserViewModel,
    onBack: () -> Unit,
) {
    val c = LocalArc.current
    val query = vm.settingsSearch
    var httpsOnly by remember { mutableStateOf(true) }
    var enginePicker by remember { mutableStateOf(false) }
    var engineAdding by remember { mutableStateOf(false) }
    var engineAddName by remember { mutableStateOf("") }
    var engineAddUrl by remember { mutableStateOf("") }
    var engineAddError by remember { mutableStateOf<String?>(null) }
    var accentPicker by remember { mutableStateOf(false) }
    val defaultBrowserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { vm.refreshDefaultBrowser() }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.settingsBg),
    ) {
        Column(Modifier.fillMaxSize()) {
            SettingsHeader(title = "Settings", leading = {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(Ico.chevronLeft, c.text, size = 20.dp, strokeWidth = 2.2f) }
            }, icon = {
                Text("⚙", color = c.text, fontSize = 18.sp)
            })

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 96.dp),
            ) {
                Spacer(Modifier.height(10.dp))
                if (query.isBlank()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(c.surface)
                            .clickable { vm.openAccount() }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PillAvatar("M", 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Mozilla account", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Sync tabs, history & passwords", color = c.muted, fontSize = 12.sp)
                        }
                        Glyph(Ico.chevronRight, c.muted, size = 16.dp, strokeWidth = 1.9f)
                    }
                }

                val appearance = listOf("Appearance", "Accent colour", "Text size", "Search suggestions")
                    .filter { matches(query, it) }
                if (appearance.isNotEmpty()) {
                    SectionLabel("Look and feel")
                    SettingsGroup {
                        if ("Appearance" in appearance) SettingsRow(
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
                        if ("Accent colour" in appearance) SettingsRow(
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
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .height(1.dp)
                                    .background(c.hairline),
                            )
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
                                        Box(
                                            Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(preset.dark),
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
                                        Text(if (selected) "✓" else "", color = c.accent, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        if ("Text size" in appearance) {
                            SettingsRow(
                                label = "Text size",
                                icon = { Glyph(Ico.textSize, c.text, size = 20.dp, strokeWidth = 1.9f) },
                                value = "${vm.zoom}%",
                                onClick = { vm.resetZoom() },
                            )
                        }
                        if ("Search suggestions" in appearance) {
                            SettingsRow(
                                label = "Search suggestions",
                                icon = { Glyph(Ico.find, c.text, size = 20.dp, strokeWidth = 1.9f) },
                                trailing = {
                                    ArcSwitch(vm.suggestionsEnabled) { vm.toggleSearchSuggestions() }
                                },
                                onClick = { vm.toggleSearchSuggestions() },
                            )
                        }
                    }
                }

                val general = listOf("Search engine", "History", "Language", "Open links in apps", "Downloads", "Default browser", "Extensions")
                    .filter { matches(query, it) }
                if (general.isNotEmpty()) {
                    SectionLabel("General")
                    SettingsGroup {
                        if ("Search engine" in general) {
                            SettingsRow(
                                "Search engine",
                                icon = { Glyph(Ico.find, c.text, size = 20.dp, strokeWidth = 1.9f) },
                                value = vm.currentEngine.name,
                                trailing = {
                                    Glyph(
                                        Ico.chevronRight,
                                        c.muted,
                                        size = 15.dp,
                                        strokeWidth = 2f,
                                        modifier = Modifier.rotate(if (enginePicker) 90f else 0f),
                                    )
                                },
                                onClick = { enginePicker = !enginePicker },
                            )
                            if (enginePicker) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                        .height(1.dp)
                                        .background(c.hairline),
                                )
                                Column {
                                    val all = vm.allEngines()
                                    all.forEach { e ->
                                        val enabled = vm.isEngineEnabled(e.id)
                                        val selected = enabled && e.id == vm.currentEngine.id
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
                                    if (!engineAdding) {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(11.dp))
                                                .clickable {
                                                    engineAdding = true
                                                    engineAddError = null
                                                }
                                                .padding(horizontal = 10.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text("＋", color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Add search engine", color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    } else {
                                        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                            EngineField(engineAddName, { engineAddName = it; engineAddError = null }, "Name")
                                            EngineField(engineAddUrl, { engineAddUrl = it; engineAddError = null },
                                                "https://example.com/search?q=%s")
                                            engineAddError?.let {
                                                Spacer(Modifier.height(6.dp))
                                                Text(it, color = Color(0xFFC0392B), fontSize = 11.sp)
                                            }
                                            Row(
                                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.End,
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
                                                        .clickable { engineAdding = false; engineAddError = null }
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
                                                        .clickable {
                                                            if (vm.addCustomEngine(engineAddName, engineAddUrl)) {
                                                                engineAddName = ""; engineAddUrl = ""; engineAddError = null
                                                                engineAdding = false
                                                            } else {
                                                                engineAddError = "Enter a name and a URL containing %s."
                                                            }
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if ("History" in general) SettingsRow(
                            "History",
                            icon = { Glyph(Ico.clock, c.text, size = 20.dp, strokeWidth = 1.9f) },
                            onClick = { vm.openHistory() },
                        )
                        if ("Language" in general) SettingsRow(
                            "Language",
                            icon = { Glyph(Ico.globe, c.text, size = 20.dp, strokeWidth = 1.7f) },
                            value = "English (US)",
                            onClick = { vm.showToast("Language: English (US)") },
                        )
                        if ("Open links in apps" in general) SettingsRow(
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
                        if ("Downloads" in general) SettingsRow(
                            "Downloads",
                            icon = { Glyph(Ico.download, c.text, size = 20.dp, strokeWidth = 1.9f) },
                            onClick = { vm.openDownloads() },
                        )
                        if ("Default browser" in general) SettingsRow(
                            "Default browser",
                            icon = { Glyph(Ico.globe, c.text, size = 20.dp, strokeWidth = 1.7f) },
                            value = if (vm.isDefaultBrowser()) "horiZen" else "Not set",
                            onClick = {
                                val intent = vm.defaultBrowserIntent()
                                if (intent != null) defaultBrowserLauncher.launch(intent)
                                else vm.openDefaultBrowserSettings()
                            },
                        )
                        if ("Extensions" in general) SettingsRow(
                            "Extensions",
                            icon = { Glyph(Ico.puzzle, c.text, size = 20.dp, strokeWidth = 1.8f) },
                            value = "${vm.extensions.size} installed",
                            onClick = { vm.openExtensions() },
                        )
                    }
                }

                val privacy = listOf(
                    "Tracking protection", "Cookie banners", "HTTPS-Only Mode", "Site permissions",
                    "Clear browsing data"
                ).filter { matches(query, it) }
                if (privacy.isNotEmpty()) {
                    SectionLabel("Privacy & security")
                    SettingsGroup {
                        if ("Tracking protection" in privacy) SettingsRow(
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
                        if ("Cookie banners" in privacy) SettingsRow(
                            "Cookie banners",
                            icon = { Glyph(Ico.close, c.text, size = 19.dp, strokeWidth = 2f) },
                            value = if (vm.cookieBannerHandling) "Auto-answer" else "Off",
                            trailing = {
                                ArcSwitch(vm.cookieBannerHandling) { vm.toggleCookieBannerHandling() }
                            },
                            onClick = { vm.toggleCookieBannerHandling() },
                        )
                        if ("HTTPS-Only Mode" in privacy) SettingsRow(
                            "HTTPS-Only Mode",
                            icon = { Glyph(Ico.lock, c.text, size = 20.dp, strokeWidth = 1.8f) },
                            trailing = { ArcSwitch(httpsOnly) { httpsOnly = it } },
                            onClick = { httpsOnly = !httpsOnly },
                        )
                        if ("Site permissions" in privacy) SettingsRow(
                            "Site permissions",
                            icon = { Glyph(Ico.globe, c.text, size = 20.dp, strokeWidth = 1.7f) },
                            onClick = { vm.openSiteSettings() },
                        )
                        if ("Clear browsing data" in privacy) SettingsRow(
                            "Clear browsing data",
                            icon = { Glyph(Ico.trash, c.text, size = 19.dp, strokeWidth = 1.8f) },
                            onClick = { vm.showToast("Browsing data cleared") },
                        )
                    }
                }

                SettingsRow(
                    "About horiZen",
                    icon = { Glyph(Ico.globe, c.text, size = 20.dp, strokeWidth = 1.7f) },
                    value = "horiZen for Android",
                    onClick = { vm.openAbout() },
                )
            }
        }

        SettingsSearchPill(
            value = query,
            onChange = vm::onSettingsSearchChange,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
        )
    }
}

@Composable
private fun SettingsSearchPill(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalArc.current
    Row(
        modifier
            .fillMaxWidth(0.7f)
            .height(58.dp)
            .shadow(14.dp, RoundedCornerShape(29.dp))
            .clip(RoundedCornerShape(29.dp))
            .background(c.navSurface)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(Ico.find, c.muted, size = 18.dp, strokeWidth = 2f)
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

@Composable
fun SettingsHeader(
    title: String,
    leading: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LocalArc.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 6.dp)
            .padding(horizontal = 16.dp)
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(6.dp))
        if (icon != null) {
            icon()
            Spacer(Modifier.width(9.dp))
        }
        Text(
            title,
            color = c.text,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/* --------------------------------------------------------- site settings */

@Composable
fun SiteSettingsScreen(vm: BrowserViewModel, onBack: () -> Unit) {
    val c = LocalArc.current
    val host = vm.domain

    Column(
        Modifier
            .fillMaxSize()
            .background(c.settingsBg),
    ) {
        SettingsHeader(
            title = "Site settings",
            leading = {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(Ico.chevronLeft, c.text, size = 20.dp, strokeWidth = 2.2f) }
            },
            icon = { Glyph(Ico.globe, c.text, size = 19.dp, strokeWidth = 1.8f) },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 40.dp),
        ) {
            Text(
                host,
                color = c.muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
            )

            if (vm.activeTab?.isHome != true) {
                SectionLabel("Privacy")
                SettingsGroup {
                    SettingsRow(
                        "Tracking protection",
                        icon = { Glyph(Ico.lock, c.text, size = 20.dp, strokeWidth = 1.8f) },
                        value = if (vm.trackingProtectionEnabled(host)) "On" else "Off",
                        trailing = {
                            ArcSwitch(vm.trackingProtectionEnabled(host)) {
                                vm.setSiteTrackingProtection(host, it)
                            }
                        },
                        onClick = {
                            vm.setSiteTrackingProtection(host, !vm.trackingProtectionEnabled(host))
                        },
                    )
                    SettingsRow(
                        "Trackers blocked",
                        icon = { Glyph(Ico.grid, c.text, size = 19.dp, strokeWidth = 1.7f) },
                        value = vm.blockedCount.toString(),
                    )
                }
            }

            SectionLabel("Permissions")
            Text(
                "Tap a row to go back to asking every time.",
                color = c.muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
            )
            SettingsGroup {
                PermissionRow(vm, host, PermissionKind.Camera, "Camera", Ico.pin)
                PermissionRow(vm, host, PermissionKind.Microphone, "Microphone", Ico.person)
                PermissionRow(vm, host, PermissionKind.Location, "Location", Ico.globe)
                PermissionRow(vm, host, PermissionKind.Notifications, "Notifications", Ico.find)
            }

            SectionLabel("Data")
            SettingsGroup {
                SettingsRow(
                    "Clear site data",
                    icon = { Glyph(Ico.trash, c.text, size = 19.dp, strokeWidth = 1.8f) },
                    onClick = { vm.showToast("Site data cleared for $host") },
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    vm: BrowserViewModel,
    host: String,
    kind: PermissionKind,
    label: String,
    icon: List<androidx.compose.ui.graphics.Path>,
) {
    val c = LocalArc.current
    val allowed = vm.sitePermission(host, kind)
    SettingsRow(
        label = label,
        icon = { Glyph(icon, c.text, size = 19.dp, strokeWidth = 1.8f) },
        value = when (allowed) {
            true -> "Allowed"
            false -> "Blocked"
            null -> "Ask"
        },
        trailing = { ArcSwitch(allowed == true) { vm.setSitePermission(kind, it) } },
        // Tapping the row forgets the choice, so the next request asks again.
        onClick = { vm.setSitePermission(kind, null) },
    )
}

/* -------------------------------------------------------------- downloads */

@Composable
fun DownloadsScreen(vm: BrowserViewModel, onBack: () -> Unit) {
    val c = LocalArc.current
    val active = vm.downloads.filter { it.status == DownloadStatus.Downloading }
    val paused = vm.downloads.filter { it.status == DownloadStatus.Paused }
    val finished = vm.downloads.filter {
        it.status == DownloadStatus.Done ||
            it.status == DownloadStatus.Failed ||
            it.status == DownloadStatus.Canceled
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.settingsBg),
    ) {
        SettingsHeader(
            title = "Downloads",
            leading = {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(Ico.chevronLeft, c.text, size = 20.dp, strokeWidth = 2.2f) }
            },
            icon = { Glyph(Ico.download, c.text, size = 19.dp, strokeWidth = 1.9f) },
            trailing = {
                if (vm.downloads.isNotEmpty()) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { vm.clearDownloads() }
                            .padding(horizontal = 9.dp, vertical = 7.dp),
                    ) {
                        Text(
                            "Clear",
                            color = c.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
        ) {
            Text(
                vm.downloadSummary,
                color = c.muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
            )
            if (vm.downloads.isEmpty()) {
                EmptyDownloads(vm, Modifier.fillMaxWidth())
            } else {
                if (active.isNotEmpty()) {
                    DownloadGroupLabel("Downloading")
                    active.forEach { DownloadRow(vm, it) }
                }
                if (paused.isNotEmpty()) {
                    DownloadGroupLabel("Paused")
                    paused.forEach { DownloadRow(vm, it) }
                }
                if (finished.isNotEmpty()) {
                    DownloadGroupLabel(
                        if (active.isNotEmpty() || paused.isNotEmpty()) "Earlier" else "Downloads"
                    )
                    finished.forEach { DownloadRow(vm, it) }
                }
            }
        }
    }
}

@Composable
private fun DownloadGroupLabel(text: String) {
    val c = LocalArc.current
    Text(
        text.uppercase(),
        color = c.muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 8.dp),
    )
}

@Composable
private fun EmptyDownloads(vm: BrowserViewModel, modifier: Modifier = Modifier) {
    val c = LocalArc.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp)
            .padding(top = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.chip),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(Ico.download, c.accent, size = 26.dp, strokeWidth = 1.8f)
        }
        Spacer(Modifier.height(12.dp))
        Text("No downloads yet", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(
            "Files you download appear here. Start a download from any page.",
            color = c.muted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(11.dp))
                .background(c.accent)
                .clickable {
                    vm.closeDownloads()
                    vm.openSearch()
                }
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(
                "Browse the web",
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DownloadRow(vm: BrowserViewModel, d: DownloadItem) {
    val c = LocalArc.current
    val downloading = d.status == DownloadStatus.Downloading
    val paused = d.status == DownloadStatus.Paused
    val failed = d.status == DownloadStatus.Failed
    val canceled = d.status == DownloadStatus.Canceled
    val live = downloading || paused || failed
    val pct = if (d.size > 0) ((d.done * 100) / d.size).toInt().coerceIn(0, 100) else 0
    val barTarget = when {
        live -> if (d.size > 0) d.done.toFloat() / d.size else 0.06f
        canceled -> pct / 100f
        else -> 1f
    }
    val bar by animateFloatAsState(barTarget.coerceIn(0f, 1f), tween(180, easing = LinearEasing), label = "dlbar")
    val meta = when {
        downloading -> "${if (d.size > 0) formatBytes(d.size) else "Unknown size"} · ${d.host}"
        paused -> "Paused · ${formatBytes(d.done)} of ${if (d.size > 0) formatBytes(d.size) else "?"} · ${d.host}"
        failed -> "Download failed · ${d.host}"
        canceled -> "Canceled · ${formatBytes(if (d.size > 0) d.size else d.done)} · ${formatWhen(d.ended)}"
        else -> "Downloaded · ${formatBytes(d.size)} · ${formatWhen(d.ended)}"
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.field)
            .border(1.dp, c.stroke, RoundedCornerShape(16.dp))
            .alpha(if (canceled || failed) 0.72f else 1f)
            .padding(11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        DownloadFileTile(d.name)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                d.name,
                color = c.text,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                meta,
                color = c.muted,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(9.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.chip)) {
                Box(
                    Modifier
                        .fillMaxWidth(bar.coerceIn(0.01f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(c.accent.copy(alpha = if (downloading) 1f else if (canceled || failed) 0.38f else 0.55f)),
                )
            }
            if (live) {
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$pct%", color = c.muted, fontSize = 10.5.sp)
                    Text(
                        if (d.size > 0) "${formatBytes(d.done)} of ${formatBytes(d.size)}"
                        else "${formatBytes(d.done)} downloaded",
                        color = c.muted,
                        fontSize = 10.5.sp,
                    )
                }
            }
        }
        Row(Modifier.padding(top = 2.dp)) {
            when {
                downloading -> {
                    DlAction(Ico.pause) { vm.pauseDownload(d.id) }
                    DlAction(Ico.close) { vm.cancelDownload(d.id) }
                }
                paused -> {
                    DlAction(Ico.play) { vm.resumeDownload(d.id) }
                    DlAction(Ico.trash) { vm.removeDownload(d.id) }
                }
                failed || canceled -> {
                    DlAction(Ico.refresh) { vm.retryDownload(d.id) }
                    DlAction(Ico.trash) { vm.removeDownload(d.id) }
                }
                else -> {
                    DlAction(Ico.open) { vm.openDownload(d.id) }
                    DlAction(Ico.trash) { vm.removeDownload(d.id) }
                }
            }
        }
    }
}

@Composable
private fun DlAction(icon: List<androidx.compose.ui.graphics.Path>, onClick: () -> Unit) {
    val c = LocalArc.current
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Glyph(icon, c.muted, size = 16.dp, strokeWidth = 1.9f)
    }
}

/* ------------------------------------------------------------- extensions */

@Composable
fun ExtensionsScreen(vm: BrowserViewModel, onBack: () -> Unit) {
    val c = LocalArc.current
    Column(
        Modifier
            .fillMaxSize()
            .background(c.settingsBg),
    ) {
        SettingsHeader(
            title = "Extensions",
            leading = {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(Ico.chevronLeft, c.text, size = 20.dp, strokeWidth = 2.2f) }
            },
            icon = { Glyph(Ico.puzzle, c.text, size = 19.dp, strokeWidth = 1.8f) },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 40.dp),
        ) {
            if (vm.extensions.isEmpty()) {
                EmptyExtensions()
            } else {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(CircleShape)
                            .background(c.chip)
                            .clickable { vm.updateAllExtensions() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Check for updates", color = c.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                val enabledList = vm.extensions.filter { it.enabled }
                val disabledList = vm.extensions.filter { !it.enabled }
                if (enabledList.isNotEmpty()) {
                    SectionLabel("Enabled (${enabledList.size})")
                    enabledList.forEach { ExtensionRow(vm, it) }
                }
                if (disabledList.isNotEmpty()) {
                    SectionLabel("Disabled (${disabledList.size})")
                    disabledList.forEach { ExtensionRow(vm, it) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(CircleShape)
                        .background(c.chip)
                        .clickable { vm.visitExtensionStore() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Visit Store", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(CircleShape)
                        .background(c.accent)
                        .clickable { vm.openAddonPicker() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Install from local",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "GeckoView only installs signed add-ons. Unsigned XPI files are rejected.",
                color = c.muted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EmptyExtensions() {
    val c = LocalArc.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 34.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.chip),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(Ico.puzzle, c.accent, size = 26.dp, strokeWidth = 1.8f)
        }
        Spacer(Modifier.height(12.dp))
        Text("No extensions yet", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(
            "Add a signed add-on to change how pages look and behave.",
            color = c.muted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ExtensionRow(vm: BrowserViewModel, item: ExtensionItem) {
    val c = LocalArc.current
    var confirmRemove by remember { mutableStateOf(false) }
    var showPerms by remember { mutableStateOf(false) }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove add-on?") },
            text = { Text("Remove ${item.name}? Its data will be deleted.") },
            confirmButton = {
                TextButton(onClick = { vm.uninstallExtension(item); confirmRemove = false }) {
                    Text("Remove", color = Color(0xFFE0453C))
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
    if (showPerms) {
        AlertDialog(
            onDismissRequest = { showPerms = false },
            title = { Text("${item.name} permissions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val lines = buildList {
                        item.permissions.forEach { add("Permission: $it") }
                        item.origins.forEach { add("Site: $it") }
                        item.optionalPermissions.forEach { add("Optional permission: $it") }
                        item.optionalOrigins.forEach { add("Optional site: $it") }
                        if (item.grantedOptionalPermissions.isNotEmpty())
                            add("Granted optional: ${item.grantedOptionalPermissions.joinToString()}")
                        if (item.grantedOptionalOrigins.isNotEmpty())
                            add("Granted sites: ${item.grantedOptionalOrigins.joinToString()}")
                        if (isEmpty()) add("No special permissions or sites.")
                    }
                    lines.forEach { Text(it, fontSize = 12.sp, color = c.text) }
                    if (item.updateDate.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Updated ${item.updateDate}", fontSize = 11.sp, color = c.muted)
                    }
                    if (item.amoListingUrl.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "View on addons.mozilla.org",
                            color = c.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { vm.openAmoListing(item) }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPerms = false }) { Text("Done") } },
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(1.dp, c.stroke, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.chip),
                contentAlignment = Alignment.Center,
            ) {
                if (item.icon != null) {
                    Image(
                        bitmap = item.icon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)),
                    )
                } else {
                    Glyph(Ico.puzzle, c.accent, size = 18.dp, strokeWidth = 1.8f)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    color = c.text,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "v${item.version.ifBlank { "1.0" }}",
                    color = c.muted,
                    fontSize = 11.sp,
                )
            }
            ArcSwitch(item.enabled) { vm.toggleExtension(item) }
        }
        if (item.description.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            Text(
                item.description,
                color = c.muted,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (item.permissions.isEmpty() && item.origins.isEmpty()) "No special permissions"
                else "${item.permissions.size + item.origins.size} permission(s)",
                color = c.muted,
                fontSize = 10.5.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Update",
                color = c.accent,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { vm.updateExtension(item) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            if (item.optionsPageUrl.isNotBlank()) {
                Text(
                    "Options",
                    color = c.accent,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { vm.openExtensionOptions(item) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            Text(
                "Permissions",
                color = c.accent,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showPerms = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            if (item.amoListingUrl.isNotBlank() || item.homepageUrl.isNotBlank()) {
                Text(
                    "Listing",
                    color = c.accent,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { vm.openAmoListing(item) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            Text(
                "Remove",
                color = c.accent,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { confirmRemove = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

/* --------------------------------------------------------------- account */

@Composable
fun AccountScreen(vm: BrowserViewModel, onBack: () -> Unit) {
    val c = LocalArc.current
    Column(
        Modifier
            .fillMaxSize()
            .background(c.settingsBg),
    ) {
        SettingsHeader(
            title = "Mozilla account",
            leading = {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(Ico.chevronLeft, c.text, size = 20.dp, strokeWidth = 2.2f) }
            },
            icon = { PillAvatar("M", 24.dp) },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 40.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(c.surface)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PillAvatar("M", 62.dp)
                Spacer(Modifier.height(12.dp))
                Text("tahilr38@gmail.com", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Signed in · sync active", color = c.muted, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(CircleShape)
                        .background(c.accent)
                        .clickable { vm.showToast("Syncing…") },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Sync now", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Text("Last synced 2 minutes ago", color = c.muted, fontSize = 11.5.sp)
            }

            SectionLabel("Synced data")
            SettingsGroup {
                SettingsRow("Bookmarks", value = "128", onClick = { vm.showToast("Bookmarks synced") })
                SettingsRow("History", value = "1,204", onClick = { vm.showToast("History synced") })
                SettingsRow("Passwords", value = "32", onClick = { vm.showToast("Passwords synced") })
                SettingsRow("Add-ons", value = "4", onClick = { vm.showToast("Add-ons synced") })
            }

            SectionLabel("Device")
            SettingsGroup {
                SettingsRow("This phone", value = "Active", onClick = { vm.showToast("This device is active") })
                SettingsRow("Disconnect", onClick = { vm.showToast("Device disconnected") })
            }

            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(CircleShape)
                    .background(c.surface)
                    .clickable { vm.showToast("Signed out") },
                contentAlignment = Alignment.Center,
            ) {
                Text("Sign out", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Mozilla account · end-to-end encrypted sync",
                color = c.muted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
            textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 12.5.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isEmpty()) {
            Text(placeholder, color = c.muted, fontSize = 12.5.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}
