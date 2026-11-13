package com.example.geckobrowser.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geckobrowser.BrowserViewModel
import com.example.geckobrowser.TabInfo

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
fun TabSwitcher(vm: BrowserViewModel) {
    val c = LocalArc.current
    val tabs = vm.tabs
    val state = rememberLazyListState()
    val activeIndex = tabs.indexOfFirst { it.id == vm.activeId }.coerceAtLeast(0)

    LaunchedEffect(vm.overlay) {
        if (vm.overlay == com.example.geckobrowser.Overlay.Tabs) state.scrollToItem(activeIndex)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(c.switcherBg)
    ) {
        val cardWidth = maxWidth * 0.88f
        LazyRow(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = maxWidth * 0.06f,
                end = maxWidth * 0.06f,
                top = 26.dp,
                bottom = 26.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(tabs, key = { it.id }) { tab ->
                TabCard(
                    tab = tab,
                    active = tab.id == vm.activeId,
                    width = cardWidth,
                    onSelect = { vm.selectTab(tab.id) },
                    onClose = { vm.closeTab(tab.id) },
                )
            }
            item {
                Box(
                    Modifier
                        .width(cardWidth)
                        .fillMaxHeight()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .clickable { vm.newTab() },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .background(c.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Glyph(Ico.plus, c.muted, size = 28.dp, strokeWidth = 2f)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("New tab", color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabCard(
    tab: TabInfo,
    active: Boolean,
    width: androidx.compose.ui.unit.Dp,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalArc.current
    val scale by animateFloatAsState(if (active) 1f else 0.955f, tween(220), label = "cardScale")
    val alpha by animateFloatAsState(if (active) 1f else 0.86f, tween(220), label = "cardAlpha")

    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .scale(scale)
            .alpha(alpha)
            .padding(vertical = 2.dp)
            .clickable { onSelect() },
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
                background = if (tab.isHome) null else accentFor(tab.url),
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
                .background(if (tab.isHome) c.siteBg else Color.White)
                .border(1.dp, c.hairline, RoundedCornerShape(20.dp))
                .clickable { onSelect() },
        ) {
            TabMiniature(tab)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.06f))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Glyph(Ico.close, Color(0xFF5D5D59), size = 14.dp, strokeWidth = 2.2f)
            }
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
