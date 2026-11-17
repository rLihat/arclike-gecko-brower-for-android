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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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

private data class SearchRow(
    /** Text handed to submit: the typed query, a page URL, or a suggestion. */
    val text: String,
    val label: String,
    val detail: String? = null,
    val favicon: String? = null,
    /** 0 = search the raw query, 1 = history page, 2 = engine suggestion. */
    val kind: Int = 0,
)

@Composable
fun SearchSheet(vm: BrowserViewModel) {
    val c = LocalArc.current
    // The field owns the text so a prefilled URL can arrive fully selected, and
    // so an inline completion can be shown as a selected suffix.
    var field by remember {
        mutableStateOf(TextFieldValue(vm.searchText, TextRange(0, vm.searchText.length)))
    }
    /** What the user actually typed, without the completion suffix. */
    var typed by remember { mutableStateOf(vm.searchText) }
    /** The full string currently shown as typed + selected completion, if any. */
    var completion by remember { mutableStateOf<String?>(null) }
    val engine = vm.currentEngine
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var engineMenu by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }

    fun applyEdit(value: TextFieldValue) {
        val text = value.text
        val hadCompletion = completion != null
        // The first backspace only drops the selected completion, exactly like a
        // browser address bar; typing over it replaces it and completes again.
        val suffixGone = hadCompletion && text.length <= typed.length
        val deleting = text.length < typed.length
        completion = null
        typed = text
        field = value
        vm.onSearchTextChange(text)
        if (suffixGone || deleting || text.length < 2 || text.any { it.isWhitespace() }) return
        if (!value.selection.collapsed || value.selection.end != text.length) return
        if (value.composition != null) return
        val done = vm.inlineCompletion(text) ?: return
        if (done.length > text.length && done.startsWith(text, ignoreCase = true)) {
            completion = done
            field = TextFieldValue(done, TextRange(text.length, done.length))
        }
    }

    val rows = buildList {
        if (typed.isNotBlank()) {
            add(
                SearchRow(
                    text = typed,
                    label = typed,
                    detail = "Search with ${engine.name}",
                    favicon = engine.host,
                    kind = 0,
                )
            )
            vm.historySuggestions
                .filter { !it.url.equals(typed, ignoreCase = true) }
                .forEach { visit ->
                    add(
                        SearchRow(
                            text = visit.url,
                            label = visit.label,
                            detail = visit.host,
                            favicon = visit.host,
                            kind = 1,
                        )
                    )
                }
            val seen = vm.historySuggestions.map { it.url }.toMutableSet()
            vm.suggestions
                .asSequence()
                .filter { it.isNotBlank() && !it.equals(typed, ignoreCase = true) }
                .filter { seen.add(it) }
                .take(6)
                .forEach { phrase ->
                    add(SearchRow(text = phrase, label = phrase, favicon = engine.host, kind = 2))
                }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 14.dp)
                .swipeActions(onDown = { vm.closeOverlay() }),
            contentAlignment = Alignment.Center,
        ) {
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
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    SiteFavicon(engine.host, engine.name, 26.dp)
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                BasicTextField(
                    value = field,
                    onValueChange = { applyEdit(it) },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = c.text,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { vm.submit(field.text.ifBlank { typed }) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (field.text.isEmpty()) {
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
            visible = vm.pins.isNotEmpty(),
            enter = fadeIn(tween(150)) +
                expandVertically(animationSpec = tween(240), expandFrom = Alignment.Top),
            exit = fadeOut(tween(110)) +
                shrinkVertically(animationSpec = tween(150), shrinkTowards = Alignment.Top),
        ) {
            PinnedRow(vm)
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
                    Column(Modifier.heightIn(max = 210.dp).verticalScroll(rememberScrollState())) {
                        vm.engines.forEach { e ->
                            val selected = e.id == engine.id
                            val isDefault = e.id == vm.defaultSearchEngineId
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(if (selected) c.chip else Color.Transparent)
                                    .clickable {
                                        vm.setTemporaryEngine(e.id)
                                        engineMenu = false
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
                                if (isDefault) {
                                    Text(
                                        "Default",
                                        color = c.accent,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.4.sp,
                                        modifier = Modifier
                                            .border(1.dp, c.accent, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(if (selected) "✓" else "", color = c.accent, fontSize = 13.sp)
                            }
                        }
                    }

                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .clickable {
                                engineMenu = false
                                vm.closeOverlay()
                                vm.updateSettingsRoute("search")
                                vm.openSettings()
                            }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(Ico.chevronRight, c.accent, size = 15.dp, strokeWidth = 2.2f)
                        Spacer(Modifier.width(8.dp))
                        Text("Manage search providers", color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = rows.isNotEmpty() && !engineMenu,
            enter = fadeIn(tween(160)) +
                slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(260)) +
                expandVertically(animationSpec = tween(260), expandFrom = Alignment.Top),
            exit = fadeOut(tween(110)) +
                shrinkVertically(animationSpec = tween(160), shrinkTowards = Alignment.Top),
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(8.dp))
                rows.forEach { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { vm.submit(row.text) }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when (row.kind) {
                            0 -> SiteFavicon(row.favicon ?: engine.host, engine.name, 18.dp)
                            1 -> SiteFavicon(row.favicon.orEmpty(), row.label.take(1), 22.dp)
                            else -> SiteFavicon(row.favicon ?: engine.host, engine.name, 16.dp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.label,
                                color = c.text,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (row.detail != null) {
                                Spacer(Modifier.height(1.dp))
                                Text(
                                    row.detail,
                                    color = c.muted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (row.kind == 1) {
                            Glyph(Ico.clock, c.muted, size = 14.dp, strokeWidth = 1.8f)
                            Spacer(Modifier.width(8.dp))
                        }
                        Glyph(Ico.chevronLeft, c.muted, size = 15.dp, strokeWidth = 2f,
                            modifier = Modifier.rotate(180f))
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PinnedRow(vm: BrowserViewModel) {
    val c = LocalArc.current
    val pins = vm.pins
    var unpinMode by remember { mutableStateOf<String?>(null) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(pins.size, key = { i -> pins[i].origin }) { i ->
                val pin = pins[i]
                val origin = pin.origin
                val host = origin.substringAfter("://")
                val active = vm.activeOrigin == origin
                val showUnpin = unpinMode == origin
                Column(
                    Modifier
                        .width(76.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .combinedClickable(
                            onClick = {
                                if (unpinMode != null) {
                                    if (unpinMode == origin) vm.unpin(origin)
                                    unpinMode = null
                                    return@combinedClickable
                                }
                                vm.openPin(pin)
                            },
                            onLongClick = { unpinMode = origin },
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .then(
                                if (active) {
                                    Modifier.border(2.dp, c.accent, CircleShape)
                                } else {
                                    Modifier.background(c.hairline, CircleShape)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        SiteFavicon(host, pin.title.ifBlank { host }, 46.dp)
                        if (showUnpin) {
                            Box(
                                Modifier
                                    .size(17.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Color(0xFFE23D3D))
                                    .align(Alignment.TopEnd),
                                contentAlignment = Alignment.Center,
                            ) {
                                Glyph(Ico.close, Color(0xFFFFFFFF), size = 11.dp, strokeWidth = 2.4f)
                            }
                        }
                    }
                    Text(
                        pin.title.ifBlank { host },
                        color = c.text,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
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
            // Leave room under the rows for the capsule seated in the sheet base.
            .padding(start = 16.dp, end = 16.dp, bottom = 92.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 10.dp)
                .swipeActions(onDown = { vm.closeOverlay() }),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(42.dp).height(5.dp).clip(CircleShape).background(c.handle))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.sheetSoft)
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
            AddressButton(Ico.link) { vm.copyCurrentUrl() }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(c.navSoft)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MenuAction("Translate", Ico.translate) { vm.openTranslate() }
            MenuAction("Find", Ico.find) { vm.openFind() }
            MenuAction("Share", Ico.share) { vm.shareCurrentUrl() }
            val activeOrigin = vm.activeOrigin
            if (activeOrigin.isNotEmpty()) {
                val pinned = vm.isActiveTabSitePinned
                MenuAction(
                    if (pinned) "Unpin" else "Pin",
                    Ico.pin,
                    accent = pinned,
                ) { vm.togglePinForActiveSite() }
            }
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
                .background(c.sheetSoft),
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
                    .clickable { vm.toggleImmersive() }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(Ico.expand, c.muted, size = 19.dp, strokeWidth = 1.8f)
                Spacer(Modifier.width(11.dp))
                Text(
                    "Immersive view",
                    color = c.text,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                ArcSwitch(vm.immersiveMode) { vm.toggleImmersive() }
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
    accent: Boolean = false,
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
            Glyph(paths, if (accent) c.accent else c.text, size = 20.dp, strokeWidth = 1.8f)
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
