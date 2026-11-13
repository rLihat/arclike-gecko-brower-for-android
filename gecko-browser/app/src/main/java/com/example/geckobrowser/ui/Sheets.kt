package com.example.geckobrowser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
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
    // The field owns the text so a prefilled URL can arrive fully selected.
    var field by remember {
        mutableStateOf(TextFieldValue(vm.searchText, TextRange(0, vm.searchText.length)))
    }
    val query = field.text
    val engine = vm.currentEngine
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var engineMenu by remember { mutableStateOf(false) }
    var addingEngine by remember { mutableStateOf(false) }
    var engineName by remember { mutableStateOf("") }
    var engineUrl by remember { mutableStateOf("") }
    var engineError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    val suggestions = buildList {
        if (query.isNotBlank()) {
            add(query)
            add("$query ${engine.name.lowercase()}")
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
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            engineMenu = !engineMenu
                            if (!engineMenu) addingEngine = false
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    SiteFavicon(engine.host, engine.name, 26.dp)
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                BasicTextField(
                    value = field,
                    onValueChange = {
                        field = it
                        vm.onSearchTextChange(it.text)
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = c.text,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { vm.submit(query) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
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

        AnimatedVisibility(
            visible = engineMenu,
            enter = fadeIn(tween(150)) +
                scaleIn(
                    initialScale = 0.9f,
                    transformOrigin = TransformOrigin(0.5f, 0f),
                    animationSpec = tween(260),
                ) +
                expandVertically(animationSpec = tween(260), expandFrom = Alignment.Top),
            exit = fadeOut(tween(110)) +
                scaleOut(
                    targetScale = 0.96f,
                    transformOrigin = TransformOrigin(0.5f, 0f),
                    animationSpec = tween(150),
                ) +
                shrinkVertically(animationSpec = tween(160), shrinkTowards = Alignment.Top),
        ) {
            Box(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    Modifier
                        .widthIn(max = 322.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.sheet)
                        .border(1.dp, c.stroke, RoundedCornerShape(16.dp))
                        .padding(6.dp),
                ) {
                    if (!addingEngine) {
                        Column(Modifier.heightIn(max = 210.dp).verticalScroll(rememberScrollState())) {
                            vm.engines.forEach { e ->
                                val selected = e.id == engine.id
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(if (selected) c.chip else Color.Transparent)
                                        .clickable {
                                            vm.selectEngine(e.id)
                                            engineMenu = false
                                            addingEngine = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SiteFavicon(e.host, e.name, 20.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        e.name,
                                        color = c.text,
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
                                    Text(if (selected) "✓" else "", color = c.accent, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
                    if (!addingEngine) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(11.dp))
                                .clickable {
                                    addingEngine = true
                                    engineError = null
                                }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("＋", color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text("Add search engine", color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                            EngineField(engineName, { engineName = it; engineError = null }, "Name")
                            EngineField(engineUrl, { engineUrl = it; engineError = null },
                                "https://example.com/search?q=%s")
                            engineError?.let {
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
                                        .clickable {
                                            addingEngine = false
                                            engineError = null
                                        }
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
                                            if (vm.addCustomEngine(engineName, engineUrl)) {
                                                engineName = ""
                                                engineUrl = ""
                                                engineError = null
                                                addingEngine = false
                                                engineMenu = false
                                            } else {
                                                engineError = "Enter a name and a URL containing %s."
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

        AnimatedVisibility(
            visible = suggestions.isNotEmpty() && !engineMenu,
            enter = fadeIn(tween(160)) +
                slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(260)) +
                expandVertically(animationSpec = tween(260), expandFrom = Alignment.Top),
            exit = fadeOut(tween(110)) +
                shrinkVertically(animationSpec = tween(160), shrinkTowards = Alignment.Top),
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(8.dp))
                suggestions.forEachIndexed { index, s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { vm.submit(s) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (index == 0) {
                            SiteFavicon(engine.host, engine.name, 17.dp)
                        } else {
                            Glyph(Ico.find, c.muted, size = 17.dp, strokeWidth = 2f)
                        }
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
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isEmpty()) {
            Text(placeholder, color = c.muted, fontSize = 12.5.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
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
                    .clickable { vm.editAddressBar() }
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
