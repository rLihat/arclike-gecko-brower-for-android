package com.example.geckobrowser.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 64.dp, bottom = 120.dp)
            .padding(horizontal = 22.dp),
    ) {
        Text(
            "Where to?",
            color = c.text,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.6).sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Search-first, edge to edge. No persistent address bar.",
            color = c.muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(22.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(c.navSurface)
                .clickable { onSearch() }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(c.chip),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(Ico.plus, c.text, size = 17.dp, strokeWidth = 2.2f)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("Search or enter a site", color = c.muted, fontSize = 13.5.sp)
            }
            Spacer(Modifier.size(30.dp))
        }

        SectionLabel("Pinned")
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            pins.chunked(3).forEach { rowPins ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    rowPins.forEach { pin ->
                        PinCell(pin, Modifier.weight(1f)) { onOpen(pin.url) }
                    }
                    repeat(3 - rowPins.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        SectionLabel("Recent")
        RecentCard("Example", "example.com") { onOpen("https://example.com") }
        RecentCard("GitHub", "github.com") { onOpen("https://github.com") }
        RecentCard("Add-ons", "addons.mozilla.org") { onOpen("https://addons.mozilla.org") }
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
    var openLinksInApps by remember { mutableStateOf(true) }
    var closeOnExit by remember { mutableStateOf(false) }
    var httpsOnly by remember { mutableStateOf(true) }

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

                val appearance = listOf("Dark mode", "Text size", "Search suggestions")
                    .filter { matches(query, it) }
                if (appearance.isNotEmpty()) {
                    SectionLabel("Appearance")
                    SettingsGroup {
                        if ("Dark mode" in appearance) {
                            SettingsRow(
                                label = "Dark mode",
                                icon = { Glyph(Ico.moon, c.text, size = 20.dp, strokeWidth = 1.9f) },
                                trailing = { ArcSwitch(vm.darkMode) { vm.toggleDarkMode() } },
                                onClick = { vm.toggleDarkMode() },
                            )
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

                val general = listOf("Search engine", "History", "Language", "Open links in apps", "Downloads")
                    .filter { matches(query, it) }
                if (general.isNotEmpty()) {
                    SectionLabel("General")
                    SettingsGroup {
                        if ("Search engine" in general) SettingsRow(
                            "Search engine",
                            icon = { Glyph(Ico.find, c.text, size = 20.dp, strokeWidth = 1.9f) },
                            value = vm.currentEngine.name,
                            onClick = { vm.showToast("Search engine: ${vm.currentEngine.name}") },
                        )
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
                            icon = { Glyph(Ico.link, c.text, size = 20.dp, strokeWidth = 1.9f) },
                            trailing = { ArcSwitch(openLinksInApps) { openLinksInApps = it } },
                            onClick = { openLinksInApps = !openLinksInApps },
                        )
                        if ("Downloads" in general) SettingsRow(
                            "Downloads",
                            icon = { Glyph(Ico.download, c.text, size = 20.dp, strokeWidth = 1.9f) },
                            onClick = { vm.openDownloads() },
                        )
                    }
                }

                val tabsGroup = listOf("Tab view", "New tabs", "Close tabs on exit").filter { matches(query, it) }
                if (tabsGroup.isNotEmpty()) {
                    SectionLabel("Tabs")
                    SettingsGroup {
                        if ("Tab view" in tabsGroup) SettingsRow(
                            "Tab view",
                            icon = { Glyph(Ico.grid, c.text, size = 19.dp, strokeWidth = 1.7f) },
                            value = "Carousel",
                            onClick = { vm.showToast("Tab view: carousel") },
                        )
                        if ("New tabs" in tabsGroup) SettingsRow(
                            "New tabs",
                            icon = { Glyph(Ico.tabs, c.text, size = 20.dp, strokeWidth = 1.7f) },
                            value = "Homepage",
                            onClick = { vm.showToast("New tabs open the homepage") },
                        )
                        if ("Close tabs on exit" in tabsGroup) SettingsRow(
                            "Close tabs on exit",
                            icon = { Glyph(Ico.close, c.text, size = 19.dp, strokeWidth = 2f) },
                            trailing = { ArcSwitch(closeOnExit) { closeOnExit = it } },
                            onClick = { closeOnExit = !closeOnExit },
                        )
                    }
                }

                val privacy = listOf(
                    "Tracking protection", "HTTPS-Only Mode", "Site permissions",
                    "Autofill and passwords", "Clear browsing data"
                ).filter { matches(query, it) }
                if (privacy.isNotEmpty()) {
                    SectionLabel("Privacy & security")
                    SettingsGroup {
                        if ("Tracking protection" in privacy) SettingsRow(
                            "Tracking protection",
                            icon = { Glyph(Ico.lock, c.text, size = 20.dp, strokeWidth = 1.8f) },
                            value = "Strict",
                            onClick = { vm.showToast("Tracking protection: strict") },
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
                        if ("Autofill and passwords" in privacy) SettingsRow(
                            "Autofill and passwords",
                            icon = { Glyph(Ico.person, c.text, size = 20.dp, strokeWidth = 1.8f) },
                            onClick = { vm.showToast("Autofill is off") },
                        )
                        if ("Clear browsing data" in privacy) SettingsRow(
                            "Clear browsing data",
                            icon = { Glyph(Ico.trash, c.text, size = 19.dp, strokeWidth = 1.8f) },
                            onClick = { vm.showToast("Browsing data cleared") },
                        )
                    }
                }

                Text(
                    "Firefox for Android · GeckoView 138",
                    color = c.muted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
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

            SectionLabel("Appearance")
            SettingsGroup {
                SettingsRow(
                    "Dark mode",
                    icon = { Glyph(Ico.moon, c.text, size = 20.dp, strokeWidth = 1.9f) },
                    trailing = { ArcSwitch(vm.darkMode) { vm.toggleDarkMode() } },
                    onClick = { vm.toggleDarkMode() },
                )
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
                vm.extensions.forEach { ExtensionRow(vm, it) }
            }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(CircleShape)
                    .background(c.accent)
                    .clickable { vm.openAddonPicker() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Install add-on",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
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
                Glyph(Ico.puzzle, c.accent, size = 18.dp, strokeWidth = 1.8f)
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
                if (item.permissions.isEmpty()) "No special permissions"
                else "${item.permissions.size} permission${if (item.permissions.size == 1) "" else "s"}",
                color = c.muted,
                fontSize = 10.5.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Remove",
                color = c.accent,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { vm.uninstallExtension(item) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
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
