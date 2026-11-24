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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
    ScreenHaze(enabled = LocalFrost.current) {
    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onSearch() },
        contentAlignment = Alignment.Center,
    ) {
        // Compose-drawn backdrop for Haze (home has no web page behind it).
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(c.pageTop, c.pageBottom)))
                .hazeBackdrop(),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = offsetY.value.dp),
        ) {
            HorizenMark(progress = progress.value, modifier = Modifier.size(152.dp))
        }
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
            icon = { Glyph(Ico.panelTop, c.text, size = 19.dp, strokeWidth = 1.8f) },
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
                        icon = { Glyph(Ico.shieldCheck, c.text, size = 19.dp, strokeWidth = 1.7f) },
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
                PermissionRow(vm, host, PermissionKind.Camera, "Camera", Ico.camera)
                PermissionRow(vm, host, PermissionKind.Microphone, "Microphone", Ico.mic)
                PermissionRow(vm, host, PermissionKind.Location, "Location", Ico.mapPin)
                PermissionRow(vm, host, PermissionKind.Notifications, "Notifications", Ico.bell)
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
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var pendingDelete by remember { mutableStateOf<Set<Long>?>(null) }
    var alsoDeleteFile by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    val active = vm.downloads.filter { it.status == DownloadStatus.Downloading && it.name.contains(query, ignoreCase = true) }
    val paused = vm.downloads.filter { it.status == DownloadStatus.Paused && it.name.contains(query, ignoreCase = true) }
    val finished = vm.downloads.filter {
        it.status == DownloadStatus.Done ||
            it.status == DownloadStatus.Failed ||
            it.status == DownloadStatus.Canceled && it.name.contains(query, ignoreCase = true)
    }

    ScreenHaze(enabled = LocalFrost.current) {
    Column(
        Modifier
            .fillMaxSize()
            .background(c.settingsBg)
            .hazeBackdrop(),
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
                if (selecting) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            "Cancel", color = c.text, fontSize = 13.sp,
                            modifier = Modifier.clickable {
                                selecting = false
                                selected = emptySet()
                            },
                        )
                        Text(
                            "Delete", color = Color(0xFFE0453C), fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                if (selected.isNotEmpty()) pendingDelete = selected
                            },
                        )
                    }
                } else if (vm.downloads.isNotEmpty()) {
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
        Box(Modifier.weight(1f).fillMaxWidth().imePadding()) {


        Column(
            Modifier
                .fillMaxHeight()
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
                    active.forEach { DownloadRow(vm, it, selecting, it.id in selected, { selecting = true; selected = if (it.id in selected) selected - it.id else selected + it.id }, { pendingDelete = setOf(it.id) }) }
                }
                if (paused.isNotEmpty()) {
                    DownloadGroupLabel("Paused")
                    paused.forEach { DownloadRow(vm, it, selecting, it.id in selected, { selecting = true; selected = if (it.id in selected) selected - it.id else selected + it.id }, { pendingDelete = setOf(it.id) }) }
                }
                if (finished.isNotEmpty()) {
                    DownloadGroupLabel(
                        if (active.isNotEmpty() || paused.isNotEmpty()) "Earlier" else "Downloads"
                    )
                    finished.forEach { DownloadRow(vm, it, selecting, it.id in selected, { selecting = true; selected = if (it.id in selected) selected - it.id else selected + it.id }, { pendingDelete = setOf(it.id) }) }
                }
            }
        }
            Box(Modifier.fillMaxSize()) {
                GlassSearchPill(
                    value = query,
                    placeholder = "Search downloads",
                    onChange = { query = it },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                )
            }
    }
    }
    val ids = pendingDelete
    if (ids != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = c.surface,
            titleContentColor = c.text,
            textContentColor = c.text,
            title = { Text("Delete ${ids.size} download${if (ids.size == 1) "" else "s"}?") },
            text = {
                Column {
                    Text("The download will be removed from the list.", fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (alsoDeleteFile) "☑ Also delete downloaded files"
                        else "☐ Also delete downloaded files",
                        color = c.text,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { alsoDeleteFile = !alsoDeleteFile },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeDownloads(ids, alsoDeleteFile)
                    pendingDelete = null
                    selecting = false
                    selected = emptySet()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
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
                .background(c.menuCard),
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
private fun DownloadRow(
    vm: BrowserViewModel,
    d: DownloadItem,
    selecting: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val c = LocalArc.current
    val haptics = LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var drag by remember(d.id) { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val deleteWidth = with(density) { 88.dp.toPx() }
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

    Box(Modifier.fillMaxWidth().padding(bottom = 9.dp).clip(RoundedCornerShape(16.dp))) {
        Box(
            Modifier.matchParentSize().background(Color(0xFFE0453C)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Glyph(Ico.trash, Color.White, size = 22.dp, strokeWidth = 1.9f,
                modifier = Modifier.padding(end = 22.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = drag }
                .background(if (selected) c.menuCard else c.field)
                .border(1.dp, c.stroke, RoundedCornerShape(16.dp))
                .alpha(if (canceled || failed) 0.72f else 1f)
                .pointerInput(d.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            drag = (drag + amount).coerceIn(-deleteWidth, 0f)
                        },
                        onDragEnd = {
                            if (drag <= -deleteWidth * 0.6f) onRequestDelete() else drag = 0f
                        },
                        onDragCancel = { drag = 0f },
                    )
                }
                .pointerInput(d.id) {
                    detectTapGestures(
                        onLongPress = {
                            if (vm.hapticFeedback) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onToggleSelect()
                        },
                        onTap = { if (selecting) onToggleSelect() else vm.openDownload(d.id) },
                    )
                }
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
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.menuCard)) {
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
            if (downloading || paused || failed) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        downloading -> DlAction(Ico.pause) { vm.pauseDownload(d.id) }
                        paused -> DlAction(Ico.play) { vm.resumeDownload(d.id) }
                        failed -> DlAction(Ico.refresh) { vm.retryDownload(d.id) }
                    }
                    DlAction(Ico.close) { vm.cancelDownload(d.id) }
                }
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
                            .background(c.menuCard)
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
                        .background(c.menuCard)
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
                .background(c.menuCard),
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
            .background(c.menuCard)
            .border(1.dp, c.stroke, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.menuCard),
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
    Box(Modifier.fillMaxSize()) {
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
                    .background(c.menuCard)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PillAvatar("M", 62.dp)
                Spacer(Modifier.height(12.dp))
                Text(vm.fxaEmail ?: "Not signed in", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(if (vm.fxaSignedIn) "Signed in · spaces sync active" else "Sign in to sync Spaces across devices",
                    color = c.muted, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                if (vm.fxaSignedIn) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(CircleShape)
                            .background(c.accent)
                            .clickable { vm.syncSpacesNow() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Sync now", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(vm.syncStatus, color = c.muted, fontSize = 11.5.sp)
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(CircleShape)
                            .background(c.accent)
                            .clickable { vm.beginFxaSignIn() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Sign in with Mozilla account", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            SectionLabel("Synced data")
            SettingsGroup {
                SettingsRow("Spaces & pins", value = if (vm.fxaSignedIn) "On" else "Off", onClick = { vm.syncSpacesNow() })
            }

            SectionLabel("Device")
            SettingsGroup {
                SettingsRow("This phone", value = "Active", onClick = { vm.showToast("This device is active") })
                SettingsRow("Disconnect", onClick = { vm.signOutFxa(); vm.showToast("Signed out") })
            }

            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(CircleShape)
                    .background(c.menuCard)
                    .clickable { vm.signOutFxa(); vm.showToast("Signed out") },
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
    FxaSignInDialog(vm)
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
