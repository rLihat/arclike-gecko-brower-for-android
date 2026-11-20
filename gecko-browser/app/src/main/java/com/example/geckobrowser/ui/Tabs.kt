package com.example.geckobrowser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geckobrowser.BrowserViewModel
import com.example.geckobrowser.TabInfo
import kotlinx.coroutines.launch

private fun accentFor(host: String): Color = when {
    host.contains("youtube") -> Color(0xFFFF0033)
    host.contains("github") -> Color(0xFF24292F)
    host.contains("google") || host.contains("gmail") -> Color(0xFFD93B30)
    host.contains("mozilla") -> Color(0xFFFF7A59)
    host.contains("duckduckgo") -> Color(0xFFDE5833)
    host.contains("example") -> Color(0xFF7AA2B8)
    else -> Color(0xFF2F5C69)
}

@Composable
fun TabSwitcher(
    vm: BrowserViewModel,
    modifier: Modifier = Modifier,
    /** Blurred snapshot of the live page, captured when the carousel opens. */
    frost: ImageBitmap? = null,
) {
    val c = LocalArc.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val tabs = vm.tabs
    val state = rememberLazyListState()
    val activeIndex = tabs.indexOfFirst { it.id == vm.activeId }.coerceAtLeast(0)
    var grid by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var showClearAll by remember { mutableStateOf(false) }
    // The view switcher belongs to real pages only, never an empty tab.
    val activeEmpty = vm.activeTab?.isHome != false

    LaunchedEffect(vm.overlay) {
        if (vm.overlay == com.example.geckobrowser.Overlay.Tabs) state.scrollToItem(activeIndex)
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .then(if (frost == null) Modifier.background(c.switcherBg) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { vm.closeOverlay() }
            .swipeActions(threshold = 70.dp, onDown = { vm.closeOverlay() })
    ) {
        if (frost != null) {
            Image(
                bitmap = frost,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(c.switcherBg.copy(alpha = 0.72f)))
        }

        val cardWidth = if (grid) (maxWidth - 48.dp) / 2f else maxWidth * 0.88f
        if (grid) {
            val normal = tabs.filter { !it.isPrivate }
            val private = tabs.filter { it.isPrivate }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(top = 56.dp, bottom = 76.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                gridItems(normal, key = { it.id }) { tab ->
                    TabCard(
                        vm = vm,
                        tab = tab, active = tab.id == vm.activeId, width = cardWidth,
                        grid = true, selecting = selecting, selected = tab.id in selected,
                        onSelect = {
                            if (selecting) {
                                selected = if (tab.id in selected) selected - tab.id else selected + tab.id
                            } else if (tab.id == vm.activeId) vm.closeOverlay() else vm.selectTab(tab.id)
                        },
                        onClose = { vm.closeTab(tab.id) },
                        onToggleSelect = {
                            selecting = true
                            selected = if (tab.id in selected) selected - tab.id else selected + tab.id
                        },
                        onSwipeStart = {
                            if (tabs.size > 2 && !showClearAll) showClearAll = true
                        },
                    )
                }
                if (private.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Text(
                            "PRIVATE",
                            color = Color(0xFF9059FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                    gridItems(private, key = { it.id }) { tab ->
                        TabCard(
                            vm = vm,
                            tab = tab, active = tab.id == vm.activeId, width = cardWidth,
                            grid = true, selecting = selecting, selected = tab.id in selected,
                            onSelect = {
                                if (selecting) {
                                    selected = if (tab.id in selected) selected - tab.id else selected + tab.id
                                } else if (tab.id == vm.activeId) vm.closeOverlay() else vm.selectTab(tab.id)
                            },
                            onClose = { vm.closeTab(tab.id) },
                            onToggleSelect = {
                                selecting = true
                                selected = if (tab.id in selected) selected - tab.id else selected + tab.id
                            },
                            onSwipeStart = {
                                if (tabs.size > 2 && !showClearAll) showClearAll = true
                            },
                        )
                    }
                }
            }
        } else {
            LazyRow(
                state = state,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = maxWidth * 0.06f,
                    end = maxWidth * 0.06f,
                    top = 26.dp,
                    bottom = 76.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(tabs, key = { it.id }) { tab ->
                    TabCard(
                        vm = vm,
                        tab = tab, active = tab.id == vm.activeId, width = cardWidth,
                        grid = false, selecting = selecting, selected = tab.id in selected,
                        onSelect = {
                            if (selecting) {
                                selected = if (tab.id in selected) selected - tab.id else selected + tab.id
                            } else if (tab.id == vm.activeId) vm.closeOverlay() else vm.selectTab(tab.id)
                        },
                        onClose = { vm.closeTab(tab.id) },
                        onToggleSelect = {
                            selecting = true
                            selected = if (tab.id in selected) selected - tab.id else selected + tab.id
                        },
                        onSwipeStart = {
                            if (tabs.size > 2 && !showClearAll) showClearAll = true
                        },
                    )
                }
            }
        }

        // View switcher sits where the Extensions chip lives in the menu.
        if (!activeEmpty && !selecting) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 24.dp)
                    .size(46.dp)
                    .clickable {
                        grid = !grid
                        if (vm.hapticFeedback) {
                            haptics.performHapticFeedback(
                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Glyph(if (grid) Ico.tabs else Ico.grid, c.text, size = 22.dp, strokeWidth = 1.9f)
            }
        }

        if (selecting) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Cancel", color = c.text, fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        selecting = false
                        selected = emptySet()
                    })
                Text("Delete", color = Color(0xFFE0453C), fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        selected.forEach { vm.closeTab(it) }
                        selecting = false
                        selected = emptySet()
                    })
            }
        }

        // Same capsule treatment as the immersive/download banners.
        val clearBannerAlpha by animateFloatAsState(
            if (showClearAll) 1f else 0f,
            tween(260),
            label = "clearAllBanner",
        )
        if (clearBannerAlpha > 0.01f) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 52.dp)
                    .fillMaxWidth(0.7f)
                    .height(58.dp)
                    .graphicsLayer {
                        alpha = clearBannerAlpha
                        val s = 0.96f + 0.04f * clearBannerAlpha
                        scaleX = s
                        scaleY = s
                        translationY = (1f - clearBannerAlpha) * -14.dp.toPx()
                    }
                    .clip(RoundedCornerShape(29.dp))
                    .background(c.navSoft)
                    .border(1.dp, c.stroke, RoundedCornerShape(29.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(c.chip),
                    contentAlignment = Alignment.Center,
                ) { Glyph(Ico.trash, c.accent, size = 15.dp, strokeWidth = 2f) }
                Spacer(Modifier.width(9.dp))
                Text(
                    "Clear all tabs?",
                    color = c.text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Clear",
                    color = c.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        vm.clearAllTabs()
                        showClearAll = false
                    },
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Dismiss",
                    color = c.muted,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { showClearAll = false },
                )
            }
        }
    }
}

@Composable
private fun TabCard(
    vm: BrowserViewModel,
    tab: TabInfo,
    active: Boolean,
    width: androidx.compose.ui.unit.Dp,
    grid: Boolean = false,
    selecting: Boolean = false,
    selected: Boolean = false,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onToggleSelect: () -> Unit,
    onSwipeStart: () -> Unit,
) {
    val c = LocalArc.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var longPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (active) 1f else 0.93f, tween(220), label = "cardScale")
    val baseAlpha by animateFloatAsState(if (active) 1f else 0.68f, tween(220), label = "cardAlpha")
    // Drag tracking for the two vertical card gestures: up dismisses, down
    // focuses the tab (the same result as a tap).
    var drag by remember { mutableFloatStateOf(0f) }
    val dismiss = remember { Animatable(0f) }
    val threshold = with(density) { 26.dp.toPx() }
    val lift = with(density) { 64.dp.toPx() }

    Column(
        Modifier
            .width(width)
            .then(if (grid) Modifier.height(210.dp) else Modifier.fillMaxHeight())
            .pointerInput(tab.id) {
                detectTapGestures(onLongPress = {
                    longPressed = true
                    if (vm.hapticFeedback) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    onToggleSelect()
                })
            }
            .border(
                if (selected || tab.isPrivate) 2.dp else 0.dp,
                when {
                    selected -> c.accent
                    tab.isPrivate -> Color(0xFF9059FF)
                    else -> Color.Transparent
                },
                RoundedCornerShape(22.dp),
            )
            .scale(scale)
            .alpha(baseAlpha * (1f - dismiss.value))
            .graphicsLayer { translationY = drag - lift * dismiss.value }
            .padding(vertical = 2.dp)
            .pointerInput(tab.id) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        if (dismiss.value == 0f) {
                            if (amount < 0f) onSwipeStart()
                            drag = (drag + amount).coerceIn(-160f, 90f)
                        }
                    },
                    onDragEnd = {
                        when {
                            drag <= -threshold -> scope.launch {
                                // Let the card lift away, then let the row close
                                // the gap by removing the tab.
                                dismiss.animateTo(1f, tween(260))
                                onClose()
                            }
                            drag >= threshold -> {
                                drag = 0f
                                onSelect()
                            }
                            else -> drag = 0f
                        }
                    },
                    onDragCancel = { drag = 0f },
                )
            }
            .clickable { if (longPressed) longPressed = false else onSelect() },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SiteFavicon(
                host = BrowserViewModel.hostOf(tab.url),
                letter = if (tab.isHome) "A" else tab.title.take(1),
                size = 18.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (tab.isHome) "New tab" else tab.title,
                color = c.muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(if (tab.isHome) (if (c.dark) Color.Black else c.siteBg) else Color.White)
                .border(
                    if (tab.isPrivate) 2.dp else 1.dp,
                    if (tab.isPrivate) Color(0xFF9059FF) else c.hairline,
                    RoundedCornerShape(20.dp),
                )
                .clickable { if (longPressed) longPressed = false else onSelect() },
        ) {
            TabMiniature(tab)
        }
    }
}

@Composable
private fun TabMiniature(tab: TabInfo) {
    val c = LocalArc.current
    if (tab.isHome) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(c.pageTop, c.pageBottom),
                        radius = 900f,
                    )
                )
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Where to?", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.White.copy(alpha = 0.7f))
                )
                Spacer(Modifier.height(14.dp))
                repeat(3) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 9.dp)) {
                        repeat(3) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .padding(end = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.75f))
                            )
                        }
                    }
                }
            }
        }
        return
    }

    val accent = accentFor(tab.url)

    // A real captured page beats the placeholder skeleton whenever we have one.
    val thumbnail = tab.thumbnail
    if (thumbnail != null) {
        Box(Modifier.fillMaxSize().background(Color.White)) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFFBFBFA))) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(accent)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tab.title,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(Modifier.padding(12.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.16f))
                )
                Spacer(Modifier.height(12.dp))
                repeat(2) {
                    Box(
                        Modifier
                            .fillMaxWidth(if (it == 0) 0.72f else 0.52f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE6E6E3))
                    )
                    Spacer(Modifier.height(7.dp))
                }
                Spacer(Modifier.height(10.dp))
                Repeat(3) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(accent.copy(alpha = 0.12f))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Box(Modifier.fillMaxWidth(0.8f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFE9E9E6)))
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth(0.5f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFF0F0EE)))
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(34.dp)
                .background(Color.White.copy(alpha = 0.8f))
                .border(1.dp, Color(0xFFEDEDEA))
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(70.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFFE8E8E5))
            )
        }
    }
}

@Composable
private fun Repeat(count: Int, content: @Composable () -> Unit) {
    repeat(count) { content() }
}
