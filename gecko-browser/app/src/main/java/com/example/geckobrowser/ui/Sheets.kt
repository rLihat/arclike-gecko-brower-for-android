package com.example.geckobrowser.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geckobrowser.BrowserViewModel

/* ---------------------------------------------------------------- search */

@Composable
fun DuckDuckGoBadge(size: Int = 26) {
    Canvas(Modifier.size(size.dp)) {
        val r = this.size.minDimension / 2f
        drawCircle(Color(0xFFDE5833), radius = r, center = Offset(r, r))
        // Duck body
        drawCircle(Color.White, radius = r * 0.62f, center = Offset(r * 0.94f, r * 1.02f))
        // Eye
        drawCircle(Color(0xFF1F3B43), radius = r * 0.13f, center = Offset(r * 1.16f, r * 0.82f))
        // Beak
        val beak = androidx.compose.ui.graphics.Path().apply {
            moveTo(r * 1.34f, r * 1.0f)
            lineTo(r * 1.78f, r * 1.12f)
            lineTo(r * 1.34f, r * 1.24f)
            close()
        }
        drawPath(beak, Color(0xFFF5A623))
    }
}

@Composable
fun SearchSheet(vm: BrowserViewModel) {
    val c = LocalArc.current
    val query = vm.searchText
    val suggestions = buildList {
        if (query.isNotBlank()) {
            add(query)
            add("$query duckduckgo")
            add("$query github")
            add("$query news")
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .width(42.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(c.handle)
            )
        }

        // Flat field: engine badge left, centred text, close at the right end.
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterStart) {
                DuckDuckGoBadge(26)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                BasicTextField(
                    value = query,
                    onValueChange = vm::onSearchTextChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = c.text,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.isEmpty()) {
                    Text(
                        "Search or enter a site",
                        color = c.muted,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Box(Modifier.width(44.dp))
        }

        if (suggestions.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(8.dp))
                suggestions.forEach { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { vm.submit(s) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(Ico.find, c.muted, size = 17.dp, strokeWidth = 2f)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            s,
                            color = c.text,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Glyph(Ico.chevronLeft, c.muted, size = 15.dp, strokeWidth = 2f,
                            modifier = Modifier.rotate(180f))
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ menu */

@Composable
private fun AddressButton(
    paths: List<androidx.compose.ui.graphics.Path>,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = LocalArc.current
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Glyph(paths, if (enabled) c.text else c.muted.copy(alpha = 0.45f), size = 16.dp, strokeWidth = 2f)
    }
}

@Composable
fun MenuSheet(vm: BrowserViewModel) {
    val c = LocalArc.current
    val tab = vm.activeTab

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(42.dp).height(5.dp).clip(CircleShape).background(c.handle))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (c.dark) c.surfaceStrong else Color.White)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AddressButton(Ico.back, tab?.canGoBack == true) { vm.goBack() }
            AddressButton(Ico.forward, tab?.canGoForward == true) { vm.goForward() }
            Text(
                vm.domain,
                color = c.text,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clickable { vm.copyCurrentUrl() }
                    .padding(horizontal = 4.dp),
            )
            if (vm.pipLoading) {
                AddressButton(Ico.close) { vm.stop() }
            } else {
                AddressButton(Ico.refresh) { vm.reload() }
            }
            AddressButton(Ico.copy) { vm.copyCurrentUrl() }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(c.chip)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MenuAction("Translate", Ico.translate) { vm.showToast("Translate page") }
            MenuAction("Find", Ico.find) { vm.showToast("Find in page") }
            MenuAction("Share", Ico.share) { vm.shareCurrentUrl() }
            MenuAction("Extensions", Ico.puzzle, badge = "1") { vm.openAddonPicker() }
        }

        Text(
            "PAGE",
            color = c.muted,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 5.dp),
        )

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (c.dark) c.surfaceStrong else Color.White),
        ) {
            Row(
                Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(Ico.find, c.muted, size = 19.dp, strokeWidth = 1.9f)
                Spacer(Modifier.width(11.dp))
                Text("Zoom", color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(c.chip)
                        .clickable { vm.changeZoom(-10) },
                    contentAlignment = Alignment.Center,
                ) { Text("−", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Medium) }
                Text(
                    "${vm.zoom}%",
                    color = c.text,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(52.dp),
                )
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(c.chip)
                        .clickable { vm.changeZoom(10) },
                    contentAlignment = Alignment.Center,
                ) { Text("+", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Medium) }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable { vm.toggleDesktopSite() }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (vm.desktopSite) Glyph(Ico.grid, c.muted, size = 19.dp, strokeWidth = 1.8f)
                else Glyph(Ico.tabs, c.muted, size = 19.dp, strokeWidth = 1.8f)
                Spacer(Modifier.width(11.dp))
                Text(
                    if (vm.desktopSite) "Desktop site" else "Mobile site",
                    color = c.text,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                ArcSwitch(vm.desktopSite) { vm.toggleDesktopSite() }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable { vm.openSiteSettings() }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(Ico.lock, c.muted, size = 19.dp, strokeWidth = 1.8f)
                Spacer(Modifier.width(11.dp))
                Text("Site settings", color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Glyph(Ico.chevronRight, c.muted, size = 16.dp, strokeWidth = 1.9f)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RowScope.MenuAction(
    label: String,
    paths: List<androidx.compose.ui.graphics.Path>,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val c = LocalArc.current
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(13.dp))
            .clickable { onClick() }
            .padding(vertical = 7.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Glyph(paths, c.text, size = 20.dp, strokeWidth = 1.8f)
            if (badge != null) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDE5833)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(badge, color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = c.text, fontSize = 11.sp, maxLines = 1)
    }
}
