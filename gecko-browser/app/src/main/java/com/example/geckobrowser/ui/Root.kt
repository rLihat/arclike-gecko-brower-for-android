package com.example.geckobrowser.ui

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.geckobrowser.BrowserViewModel
import com.example.geckobrowser.ContextMenuState
import com.example.geckobrowser.Overlay
import com.example.geckobrowser.PendingDownload
import com.example.geckobrowser.TabInfo
import com.example.geckobrowser.downKindLabel
import com.example.geckobrowser.formatBytes
import kotlinx.coroutines.delay
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

private val morphEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

@Composable
fun BrowserApp(vm: BrowserViewModel, onPickAddon: () -> Unit) {
    ArcTheme(vm.darkMode) {
        val c = LocalArc.current
        val overlay = vm.overlay
        val tab = vm.activeTab

        LaunchedEffect(vm.addonPickerRequest.value) {
            if (vm.addonPickerRequest.value) {
                vm.consumeAddonPickerRequest()
                onPickAddon()
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(c.siteBg)
        ) {
            // FLIP-style transition: the page recedes into the carousel card and
            // grows back out of it when a tab is picked.
            val flip by animateFloatAsState(
                if (overlay == Overlay.Tabs) 0.9f else 1f,
                tween(320, easing = morphEasing),
                label = "flipScale",
            )
            val flipAlpha by animateFloatAsState(
                if (overlay == Overlay.Tabs) 0.5f else 1f,
                tween(240),
                label = "flipAlpha",
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = flip
                        scaleY = flip
                        alpha = flipAlpha
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        clip = overlay == Overlay.Tabs
                        shape = RoundedCornerShape(30.dp)
                    }
            ) {
                WebHost(
                    session = vm.activeSession,
                    background = c.siteBg,
                    zoom = vm.zoom / 100f,
                    onTap = { x, y -> vm.recordPageTap(x, y) },
                )
                if (tab == null || tab.isHome) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawBehind {
                                drawRect(
                                    Brush.radialGradient(
                                        colors = listOf(c.pageTop, c.pageBottom),
                                        center = androidx.compose.ui.geometry.Offset(size.width * 0.2f, -size.height * 0.1f),
                                        radius = size.height * 1.25f,
                                    )
                                )
                            }
                    ) {
                        HomeScreen(
                            onSearch = { vm.openSearch() },
                            onOpen = { url -> vm.submit(url) },
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = overlay == Overlay.Tabs,
                enter = fadeIn(tween(220)) + scaleIn(tween(320, easing = morphEasing), initialScale = 0.92f),
                exit = fadeOut(tween(180)),
            ) {
                TabSwitcher(vm)
            }

            ChromeOverlay(vm)
        }
    }
}

@Composable
private fun WebHost(
    session: GeckoSession?,
    background: Color,
    zoom: Float,
    onTap: (Float, Float) -> Unit,
) {
    // The factory lambda only runs once, so read live values through state.
    val zoomState = rememberUpdatedState(zoom)
    val tapState = rememberUpdatedState(onTap)
    AndroidView(
        factory = { ctx ->
            GeckoView(ctx).apply {
                setBackgroundColor(background.toArgb())
                session?.let { setSession(it) }
                setOnTouchListener { view, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        val density = view.resources.displayMetrics.density.coerceAtLeast(0.1f)
                        val width = view.width.toFloat().coerceAtLeast(1f)
                        val z = zoomState.value.coerceAtLeast(0.2f)
                        // Undo the top-center zoom, then convert px to dp for Compose.
                        val x = ((event.x - width / 2f) / z + width / 2f) / density
                        val y = (event.y / z) / density
                        tapState.value(x, y)
                    }
                    false
                }
            }
        },
        update = { view ->
            if (session != null && view.session !== session) {
                view.setSession(session)
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                transformOrigin = TransformOrigin(0.5f, 0f)
            },
    )
}

/* --------------------------------------------------------------- chrome */

private val sheetOverlays = setOf(
    Overlay.Search, Overlay.Menu, Overlay.Settings, Overlay.SiteSettings, Overlay.Account,
    Overlay.Downloads,
)

@Composable
private fun ChromeOverlay(vm: BrowserViewModel) {
    val c = LocalArc.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = maxWidth
        val screenH = maxHeight
        val density = LocalDensity.current
        // Edge-to-edge means the IME overlays us; lift the search sheet above it.
        val imeDp = with(density) { WindowInsets.ime.getBottom(this).toDp() }
        val overlay = vm.overlay
        val open = overlay in sheetOverlays
        val fullHeight = overlay == Overlay.Settings || overlay == Overlay.SiteSettings ||
            overlay == Overlay.Account || overlay == Overlay.Downloads

        var lastSheet by remember { mutableStateOf(Overlay.Menu) }
        if (open) lastSheet = overlay
        var panelVisible by remember { mutableStateOf(false) }
        LaunchedEffect(open) {
            if (open) panelVisible = true
            else {
                delay(470)
                panelVisible = false
            }
        }

        val closing = !open && panelVisible
        // Only the bottom-sheet pages move with the keyboard; full pages stay put.
        val sheetLift = if (overlay == Overlay.Search) imeDp else 0.dp
        val content: @Composable () -> Unit = when (lastSheet) {
            Overlay.Search -> ({ SearchSheet(vm) })
            Overlay.Menu -> ({ MenuSheet(vm) })
            Overlay.Settings -> ({ SettingsScreen(vm) { vm.onBackPressed() } })
            Overlay.SiteSettings -> ({ SiteSettingsScreen(vm) { vm.onBackPressed() } })
            Overlay.Account -> ({ AccountScreen(vm) { vm.onBackPressed() } })
            Overlay.Downloads -> ({ DownloadsScreen(vm) { vm.closeDownloads() } })
            else -> ({})
        }

        // Scrim only behind the bottom sheets; the full pages are opaque.
        val scrim by animateFloatAsState(
            if (open && !fullHeight) 1f else 0f,
            tween(260),
            label = "scrim",
        )
        if (scrim > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.26f * scrim))
                    .clickable { vm.closeOverlay() }
            )
        }

        if (panelVisible) {
            val expandedW = screenW
            val collapsedW = screenW * 0.7f
            val targetW = if (open) expandedW else collapsedW
            val targetH = when {
                !open -> 58.dp
                fullHeight -> screenH
                else -> screenH * 0.47f
            }
            val targetBottom = if (open) sheetLift else 18.dp
            val targetTopRadius = if (open && !fullHeight) 30.dp else if (open) 0.dp else 29.dp
            val targetBottomRadius = if (open) 0.dp else 29.dp

            val w by animateDpAsState(targetW, tween(420, easing = morphEasing), label = "sheetW")
            val h by animateDpAsState(targetH, tween(420, easing = morphEasing), label = "sheetH")
            val bottom by animateDpAsState(targetBottom, tween(420, easing = morphEasing), label = "sheetBottom")
            val topR by animateDpAsState(targetTopRadius, tween(420, easing = morphEasing), label = "sheetTopR")
            val bottomR by animateDpAsState(targetBottomRadius, tween(420, easing = morphEasing), label = "sheetBottomR")
            val contentAlpha by animateFloatAsState(
                if (open) 1f else 0f,
                tween(if (open) 220 else 120, delayMillis = if (open) 150 else 0),
                label = "sheetContent",
            )

            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottom)
                    .width(w)
                    .height(h)
                    .clip(RoundedCornerShape(topR, topR, bottomR, bottomR))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* keep taps on the panel from dismissing it */ }
                    .background(
                        when {
                            closing -> Color.Transparent
                            lastSheet == Overlay.Search || lastSheet == Overlay.Menu -> c.sheetSoft
                            lastSheet == Overlay.Downloads -> c.settingsBg
                            else -> c.surface
                        }
                    ),
            ) {
                Box(Modifier.fillMaxSize().alpha(contentAlpha)) {
                    content()
                }
            }
        }

        // The nav pill floats above the sheet, exactly like the mockup.
        val pillVisible = overlay == Overlay.None || overlay == Overlay.Menu ||
            overlay == Overlay.Search
        if (pillVisible) {
            val searchMode = overlay == Overlay.Search
            val sheetH = screenH * 0.47f
            NavPill(
                vm = vm,
                screenW = screenW,
                menuMode = overlay == Overlay.Menu,
                searchMode = searchMode,
                searchBottom = sheetLift + sheetH - 80.dp,
                searchX = screenW / 2 - 38.dp,
                visible = true,
            )
        }

        // The live download chip sits in the free gutter beside the 70% pill.
        // The expanded menu always offers it; on the page it only appears once
        // something has been downloaded.
        val chipVisible = vm.flight == null && when (overlay) {
            Overlay.Menu -> true
            Overlay.None -> !vm.compactPill && vm.downloads.isNotEmpty()
            else -> false
        }
        DownloadChip(
            vm = vm,
            visible = chipVisible,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = 24.dp),
        )

        DownloadPromptHost(vm)
        ContextMenuHost(vm, screenW, screenH)
        ToastHost(vm)
        DownloadFlightLayer(vm, screenW, screenH)
    }
}

@Composable
private fun BoxScope.ToastHost(vm: BrowserViewModel) {
    val c = LocalArc.current
    val message = vm.toastMessage
    LaunchedEffect(message) {
        if (message != null) {
            delay(2100)
            vm.clearToast()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(160)),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Box(
            Modifier
                .padding(bottom = 92.dp)
                .clip(CircleShape)
                .background(if (c.dark) Color(0xFF2E363B) else Color(0xFF101314))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(message.orEmpty(), color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/* ---------------------------------------------------------- downloads UI */

@Composable
private fun DownloadChip(vm: BrowserViewModel, visible: Boolean, modifier: Modifier = Modifier) {
    val c = LocalArc.current
    val active = vm.activeDownloads
    val progress = vm.downloadRingProgress
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(vm.chipPulse) {
        if (vm.chipPulse == 0 || active.isEmpty()) return@LaunchedEffect
        pulse.snapTo(1f)
        pulse.animateTo(1.22f, tween(100, easing = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1.08f)))
        pulse.animateTo(0.94f, tween(140))
        pulse.animateTo(1f, tween(240))
    }
    val bob by rememberInfiniteTransition(label = "dlBob").animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(675, easing = LinearEasing), RepeatMode.Reverse),
        label = "dlBobY",
    )
    val label = when {
        active.isNotEmpty() -> "${active.size} download${if (active.size == 1) "" else "s"} in progress"
        vm.downloads.isNotEmpty() -> "${vm.downloads.size} download${if (vm.downloads.size == 1) "" else "s"}"
        else -> "Downloads"
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) +
            scaleIn(tween(340, easing = morphEasing), initialScale = 0.78f),
        exit = fadeOut(tween(150)) + scaleOut(tween(170), targetScale = 0.78f),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .graphicsLayer {
                    scaleX = pulse.value
                    scaleY = pulse.value
                }
                .clip(CircleShape)
                .semantics { contentDescription = label }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { vm.openDownloads() },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (active.isNotEmpty()) {
                    val stroke = 4.dp.toPx()
                    val radius = (size.minDimension - stroke) / 2f
                    drawCircle(
                        color = c.stroke,
                        radius = radius,
                        style = Stroke(stroke),
                    )
                    drawArc(
                        color = c.accent,
                        startAngle = -90f,
                        sweepAngle = 360f * progress / 100f,
                        useCenter = false,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Box(
                Modifier
                    .size(38.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(c.navSoft)
                    .border(1.dp, c.stroke, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(
                    Ico.download,
                    if (active.isEmpty()) c.text else c.accent,
                    size = 19.dp,
                    strokeWidth = 2f,
                    modifier = if (active.isNotEmpty()) Modifier.offset(y = bob.dp) else Modifier,
                )
            }
            if (active.size > 1) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 7.dp, y = (-6).dp)
                        .size(21.dp)
                        .clip(CircleShape)
                        .background(c.pageTop),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .defaultMinSize(minWidth = 17.dp, minHeight = 17.dp)
                            .clip(CircleShape)
                            .background(c.accent)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            active.size.toString(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.DownloadPromptHost(vm: BrowserViewModel) {
    val c = LocalArc.current
    val current = vm.pendingDownload
    var last by remember { mutableStateOf<PendingDownload?>(null) }
    // Keep the last pending file so the card has content while it animates out.
    if (current != null) last = current
    AnimatedVisibility(
        visible = current != null,
        enter = fadeIn(tween(160)) +
            scaleIn(tween(260, easing = morphEasing), initialScale = 0.965f) +
            slideInVertically(tween(260, easing = morphEasing)) { it / 6 },
        exit = fadeOut(tween(130)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { vm.cancelDownloadPrompt() },
        ) {
            val pending = last
            if (pending != null) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 88.dp)
                        .padding(horizontal = 14.dp)
                        .fillMaxWidth()
                        .shadow(24.dp, RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp))
                        .background(c.sheet)
                        .border(1.dp, c.stroke, RoundedCornerShape(22.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { /* keep taps on the card */ }
                        .padding(14.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "DOWNLOAD FILE?",
                            color = c.muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .clickable { vm.cancelDownloadPrompt() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Glyph(Ico.close, c.muted, size = 15.dp, strokeWidth = 2f)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DownloadFileTile(pending.name)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                pending.name,
                                color = c.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${if (pending.size > 0) formatBytes(pending.size) else "Unknown size"} · ${pending.host}",
                                color = c.muted,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(c.chip)
                                .clickable { vm.cancelDownloadPrompt() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Cancel",
                                color = c.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(c.accent)
                                .clickable { vm.confirmDownloadPrompt() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Download",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class CtxAction(
    val label: String,
    val icon: List<Path>,
    val onClick: () -> Unit,
)

@Composable
private fun BoxScope.ContextMenuHost(vm: BrowserViewModel, screenW: Dp, screenH: Dp) {
    val c = LocalArc.current
    val ctx = vm.contextMenu ?: return
    val actions = contextActions(vm, ctx)
    if (actions.isEmpty()) return
    val width = 248.dp
    val estimated = 46.dp + 40.dp * actions.size.toFloat()
    val maxX = (screenW - width - 8.dp).coerceAtLeast(8.dp)
    val maxY = (screenH - estimated - 8.dp).coerceAtLeast(8.dp)
    val x = ctx.x.dp.coerceIn(8.dp, maxX)
    val y = ctx.y.dp.coerceIn(8.dp, maxY)

    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { vm.dismissContextMenu() },
    ) {
        Column(
            Modifier
                .offset(x = x, y = y)
                .widthIn(min = 198.dp, max = width)
                .shadow(20.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(c.sheet)
                .border(1.dp, c.stroke, RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* keep taps inside */ }
                .padding(6.dp),
        ) {
            Text(
                ctx.head,
                color = c.muted,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 6.dp),
            )
            actions.forEach { action ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { action.onClick() }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(action.icon, c.muted, size = 17.dp, strokeWidth = 1.8f)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        action.label,
                        color = c.text,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun contextActions(vm: BrowserViewModel, ctx: ContextMenuState): List<CtxAction> = buildList {
    if (ctx.isImage) {
        val src = ctx.src ?: ctx.link ?: return@buildList
        add(CtxAction("Open image", Ico.open) {
            vm.dismissContextMenu()
            vm.openInNewTab(src)
        })
        add(CtxAction("Save image", Ico.download) {
            vm.downloadLink(src, ctx.head, ctx.x, ctx.y)
        })
        add(CtxAction("Copy image address", Ico.copy) {
            vm.dismissContextMenu()
            vm.copyText(src, "Image address copied")
        })
        add(CtxAction("Share image", Ico.share) {
            vm.dismissContextMenu()
            vm.shareText(src)
        })
        return@buildList
    }
    val link = ctx.link ?: ctx.src ?: return@buildList
    add(CtxAction("Open link", Ico.open) {
        vm.dismissContextMenu()
        vm.submit(link)
    })
    add(CtxAction("Open in new tab", Ico.tabs) {
        vm.dismissContextMenu()
        vm.openInNewTab(link)
    })
    add(CtxAction("Download link", Ico.download) {
        vm.downloadLink(link, null, ctx.x, ctx.y)
    })
    add(CtxAction("Copy link", Ico.copy) {
        vm.dismissContextMenu()
        vm.copyText(link, "Link copied")
    })
    add(CtxAction("Share link", Ico.share) {
        vm.dismissContextMenu()
        vm.shareText(link)
    })
}

@Composable
private fun BoxScope.DownloadFlightLayer(vm: BrowserViewModel, screenW: Dp, screenH: Dp) {
    val flight = vm.flight ?: return
    val c = LocalArc.current
    val progress = remember(flight.id) { Animatable(0f) }
    LaunchedEffect(flight.id) {
        try {
            progress.animateTo(
                1f,
                tween(720, easing = CubicBezierEasing(0.42f, 0.06f, 0.18f, 1f)),
            )
        } finally {
            // Clear the flight even if the animation is cut short, or the chip would
            // never come back.
            vm.finishFlight(flight.id)
        }
    }
    val t = progress.value
    val targetX = screenW.value - 6f - 23f
    val targetY = screenH.value - 24f - 23f
    val startX = (flight.startX ?: targetX).coerceAtLeast(6f)
    val startY = (flight.startY ?: targetY).coerceAtLeast(6f)
    val controlX = startX + (targetX - startX) * 0.46f
    val controlY = minOf(startY, targetY) - 34f
    val inv = 1f - t
    val cx = inv * inv * startX + 2f * inv * t * controlX + t * t * targetX
    val cy = inv * inv * startY + 2f * inv * t * controlY + t * t * targetY
    val side = when {
        t < 0.16f -> 42f
        t < 0.56f -> lerpF(42f, 50f, (t - 0.16f) / 0.40f)
        else -> lerpF(50f, 46f, (t - 0.56f) / 0.44f)
    }
    val radius = when {
        t < 0.16f -> 12f
        t < 0.56f -> lerpF(12f, 15f, (t - 0.16f) / 0.40f)
        else -> lerpF(15f, 23f, (t - 0.56f) / 0.44f)
    }
    val fade = when {
        t < 0.16f -> t / 0.16f
        t < 0.9f -> 1f
        else -> lerpF(1f, 0.9f, (t - 0.9f) / 0.1f)
    }
    val scale = when {
        t < 0.16f -> lerpF(0.62f, 1f, t / 0.16f)
        t < 0.56f -> lerpF(1f, 1.04f, (t - 0.16f) / 0.40f)
        else -> lerpF(1.04f, 1f, (t - 0.56f) / 0.44f)
    }
    val kind = fileKindOf(flight.name)
    val shape = RoundedCornerShape(radius.dp)
    Box(
        Modifier
            .offset(x = (cx - side / 2f).dp, y = (cy - side / 2f).dp)
            .size(side.dp)
            .graphicsLayer {
                alpha = fade
                scaleX = scale
                scaleY = scale
            }
            .shadow(20.dp, shape)
            .clip(shape)
            .background(c.sheet)
            .border(1.dp, c.stroke, shape),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(1.dp)
                .clip(RoundedCornerShape((radius - 1f).dp))
                .background(fileTileBg(kind)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                downKindLabel(flight.name),
                color = fileTileInk(kind),
                fontSize = (side * 0.238f).sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.3.sp,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.5f), Color.Transparent),
                        start = Offset.Zero,
                        end = Offset(160f, 60f),
                    )
                )
                .alpha(0.55f)
        )
    }
}

private fun lerpF(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

/* ------------------------------------------------------------- nav pill */

private enum class PillMode { Normal, Menu, Compact, Search }

@Composable
private fun BoxScope.NavPill(
    vm: BrowserViewModel,
    screenW: Dp,
    menuMode: Boolean,
    searchMode: Boolean,
    searchBottom: Dp,
    searchX: Dp,
    visible: Boolean,
) {
    val c = LocalArc.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val tab = vm.activeTab
    val compact = vm.compactPill && !menuMode && !searchMode

    val mode = when {
        searchMode -> PillMode.Search
        compact -> PillMode.Compact
        menuMode -> PillMode.Menu
        else -> PillMode.Normal
    }

    val style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
    val measured = remember(vm.domain, style) {
        measurer.measure(vm.domain, style).size.width
    }
    val chipWidth = (measured / density.density).dp + 24.dp
    val maxChip = screenW * 0.58f
    val minChip = 84.dp.coerceAtMost(maxChip)

    val targetW = when (mode) {
        PillMode.Compact -> chipWidth.coerceIn(minChip, maxChip)
        PillMode.Search -> 58.dp
        else -> screenW * 0.7f
    }
    val targetH = if (mode == PillMode.Compact) 34.dp else 58.dp
    val targetBottom = when (mode) {
        PillMode.Compact -> 9.dp
        PillMode.Search -> searchBottom
        else -> 18.dp
    }
    val targetX = if (mode == PillMode.Search) searchX else 0.dp

    val w by animateDpAsState(targetW, tween(380, easing = morphEasing), label = "pillW")
    val h by animateDpAsState(targetH, tween(380, easing = morphEasing), label = "pillH")
    val bottom by animateDpAsState(targetBottom, tween(380, easing = morphEasing), label = "pillBottom")
    val x by animateDpAsState(targetX, tween(380, easing = morphEasing), label = "pillX")
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(160), label = "pillAlpha")

    val chevronRotation by animateFloatAsState(if (menuMode) 540f else 0f, tween(380), label = "chev")
    // The + spins 315° into the × that closes the search field.
    val searchRotation by animateFloatAsState(if (searchMode) 315f else 0f, tween(380), label = "spin")

    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .offset(x = x)
            .padding(bottom = bottom)
            .width(w)
            .height(h)
            .alpha(alpha),
    ) {
        LoadingGlow(vm.pipLoading, radius = h / 2, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(h / 2))
                .background(if (menuMode || searchMode) Color.Transparent else c.navSoft)
                .then(if (mode == PillMode.Compact) Modifier.clickable { vm.expandPill() } else Modifier),
        ) {
            Crossfade(targetState = mode, animationSpec = tween(150), label = "pillSet") { m ->
                when (m) {
                    PillMode.Search -> Box(
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { vm.closeOverlay() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Glyph(
                            Ico.plus, c.text, size = 24.dp, strokeWidth = 2.2f,
                            modifier = Modifier.rotate(searchRotation),
                        )
                    }

                    PillMode.Compact -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            vm.domain,
                            color = c.text,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }

                    PillMode.Menu -> Row(
                        Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PillButton(onClick = { vm.openAccount() }) { PillAvatar("M", 26.dp) }
                        PillButton(onClick = { vm.openSettings() }) {
                            Text("⚙", color = c.text, fontSize = 21.sp)
                        }
                        PillButton(onClick = { vm.toggleMenu() }) {
                            Glyph(Ico.chevronUp, c.text, size = 24.dp,
                                strokeWidth = 2.2f, modifier = Modifier.rotate(chevronRotation))
                        }
                    }

                    PillMode.Normal -> Row(
                        Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PillButton(onClick = { vm.toggleTabs() }) {
                            val t = tab
                            if (t == null || t.isHome || t.url.isBlank()) {
                                Glyph(Ico.tabs, c.iconInk, size = 21.dp, strokeWidth = 2.1f)
                            } else {
                                SiteFavicon(
                                    host = BrowserViewModel.hostOf(t.url),
                                    letter = hostLetter(t),
                                    size = 24.dp,
                                )
                            }
                        }
                        PillButton(onClick = { vm.openSearch() }) {
                            Glyph(Ico.plus, c.text, size = 24.dp, strokeWidth = 2.2f)
                        }
                        PillButton(onClick = { vm.toggleMenu() }) {
                            Glyph(Ico.chevronUp, c.text, size = 24.dp, strokeWidth = 2.2f)
                        }
                    }
                }
            }
        }
    }
}

private fun hostLetter(tab: TabInfo): String {
    val host = BrowserViewModel.hostOf(tab.url)
    return host.firstOrNull { it.isLetterOrDigit() }?.toString() ?: "A"
}
