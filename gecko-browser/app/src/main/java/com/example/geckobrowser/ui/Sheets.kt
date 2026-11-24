package com.example.geckobrowser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.delay
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import com.example.geckobrowser.PinType
import com.example.geckobrowser.PinNode
import com.example.geckobrowser.Space
import com.example.geckobrowser.SpacePalettes
import com.example.geckobrowser.SpaceIcons
import com.example.geckobrowser.DEFAULT_SPACE_C1
import com.example.geckobrowser.DEFAULT_SPACE_C2
import com.example.geckobrowser.ContainerPresets
import java.util.Locale

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
fun SearchSheet(
    vm: BrowserViewModel,
    // Private-search toggle in the field's trailing badge slot, replacing X.
    showPrivate: Boolean = true,
    // The X pill is drawn by the chrome overlay, outside this sheet. Reporting
    // the trailing badge's bounds lets the pill land exactly in that slot (the
    // mirror of the engine badge) instead of guessing dp offsets.
    onFieldAnchor: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    // Tells the sheet's changer (chrome) when the expanded list is open, so the
    // sheet height and the pill hand-off stay in sync (58% → 78% of the screen).
    onExpandedChange: ((Boolean) -> Unit)? = null,
) {
    val c = LocalArc.current
    var expanded by remember { mutableStateOf(vm.searchExpanded) }
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
        // Typing collapses the expanded list back to suggestions.
        expanded = false
        vm.searchExpanded = false
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
        // v5 grab handle — hidden once the card fills the screen.
        if (!expanded) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clickable { vm.closeOverlay() },
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    Modifier
                        .size(36.dp, 4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(c.muted.copy(alpha = 0.45f)),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 14.dp)
                .swipeActions(onDown = { vm.closeOverlay() }),
        )

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
            // Mirrors the engine badge: a 34dp box pinned to the trailing edge.
            // The X pill centres on this rect, so its position comes from the
            // real layout instead of arithmetic. After the sheet settles the X
            // itself lives here, so closing never depends on the overlay pill.
            Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterEnd) {
                Box(
                    Modifier
                        .size(34.dp)
                        .onGloballyPositioned { coords ->
                            onFieldAnchor?.invoke(coords.boundsInRoot())
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (showPrivate) {
                        val haptics = LocalHapticFeedback.current
                        val private = vm.pendingPrivateSearch
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    vm.updatePendingPrivateSearch(!private)
                                    if (vm.hapticFeedback) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Glyph(
                                Ico.mask,
                                if (private) Color(0xFF9059FF) else c.muted,
                                size = 22.dp,
                                strokeWidth = 2.2f,
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = vm.essentialsSorted.isNotEmpty(),
            enter = fadeIn(tween(150)) +
                expandVertically(animationSpec = tween(240), expandFrom = Alignment.Top),
            exit = fadeOut(tween(110)) +
                shrinkVertically(animationSpec = tween(150), shrinkTowards = Alignment.Top),
        ) {
            EssentialRow(vm)
        }
        SpaceRow(vm, c, expanded) { expanded = !expanded; onExpandedChange?.invoke(expanded) }

        LaunchedEffect(expanded) {
            vm.searchExpanded = expanded
        }

        if (expanded) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                PinTreeSection(vm, c)
                OpenTabsSection(vm, c)
                SyncStrip(vm, c)
            }
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
                        .background(c.menuCard)
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
            visible = rows.isNotEmpty() && !engineMenu && !expanded,
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

        // v5: a resting sheet shows four quick suggestions, not a blank panel.
        AnimatedVisibility(
            visible = rows.isEmpty() && !engineMenu && !expanded,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(110)),
        ) {
            Column(Modifier.fillMaxWidth()) {
                listOf(
                    Triple("Search with ${engine.name}", "", true),
                    Triple("github.com", "github.com", false),
                    Triple("docs.google.com", "docs.google.com", false),
                    Triple("addons.mozilla.org", "addons.mozilla.org", false),
                ).forEach { (label, url, isSearch) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (isSearch) {
                                    focus.requestFocus()
                                    keyboard?.show()
                                } else {
                                    vm.submit(url)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SiteFavicon(if (isSearch) engine.host else url, label.take(1), 20.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(label, color = c.text, fontSize = 14.sp,
                            modifier = Modifier.weight(1f), maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        if (!isSearch) {
                            Text(url, color = c.muted, fontSize = 11.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
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
private fun EssentialRow(vm: BrowserViewModel) {
    val c = LocalArc.current
    val pins = vm.essentialsSorted
    var unpinMode by remember { mutableStateOf<java.util.UUID?>(null) }
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val gap = 10.dp
    val listState = rememberLazyListState()
    var rowLeftPx by remember { mutableStateOf(0f) }
    var rowRightPx by remember { mutableStateOf(0f) }
    var dragTileWindowX by remember { mutableStateOf(Float.NaN) }
    // -1 scrolls left, +1 scrolls right, 0 idle; used while a tile is dragged near the row's edge.
    var edgeScroll by remember { mutableStateOf(0) }
    var accumulatedDragPx by remember { mutableFloatStateOf(0f) }
    val tileWidth = ((config.screenWidthDp - 32 - 20).dp / 3f)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val tileWidthPx = with(density) { tileWidth.toPx() }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(edgeScroll) {
        if (edgeScroll == 0) return@LaunchedEffect
        while (edgeScroll != 0 && dragTileWindowX.isFinite()) {
            listState.scrollBy(edgeScroll * 22f)
            delay(16)
        }
    }
    // Tap on dead space clears unpin mode.
    Box(
        Modifier
            .fillMaxWidth()
            .then(if (unpinMode != null) Modifier.clickable { unpinMode = null } else Modifier),
    ) {
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(gap),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    rowLeftPx = coords.localToWindow(androidx.compose.ui.geometry.Offset.Zero).x
                    rowRightPx = rowLeftPx + coords.size.width
                },
        ) {
            items(pins.size, key = { i -> pins[i].id }) { i ->
                val pin = pins[i]
                val key = pin.id
                val host = BrowserViewModel.hostOf(pin.url)
                val active = vm.activeTab?.url?.let { vm.pinKeyOf(it) } == vm.pinKeyOf(pin.url)
                val showUnpin = unpinMode == key
                val accentColor = faviconAccent(host)
                Column(
                    Modifier
                        .width(tileWidth)
                        .animateItem(
                            fadeInSpec = null,
                            placementSpec = androidx.compose.animation.core.spring(),
                            fadeOutSpec = null,
                        )
                        .onGloballyPositioned { coords ->
                            dragTileWindowX = coords.localToWindow(androidx.compose.ui.geometry.Offset.Zero).x
                        }
                        .clip(RoundedCornerShape(22.dp))
                        .background(c.menuCard)
                        .border(if (active) 2.dp else 1.dp, if (active) accentColor ?: c.accent else c.stroke, RoundedCornerShape(22.dp))
                        .alpha(1f)
                        .padding(vertical = 10.dp)
                        .pointerInput(pin.id) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val slop = viewConfiguration.touchSlop
                                val startX = down.position.x
                                val startY = down.position.y
                                // Phase A: hold until the long-press fires (armed), or the
                                // gesture ends/moves before then.
                                val phase: Int = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis.toLong()) {
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val ch = ev.changes.first()
                                        if (!ch.pressed) return@withTimeoutOrNull 1
                                        if (kotlin.math.abs(ch.position.x - startX) > slop ||
                                            kotlin.math.abs(ch.position.y - startY) > slop
                                        ) return@withTimeoutOrNull 2
                                    }
                                    @Suppress("UNREACHABLE_CODE") 0
                                } ?: 0
                                if (phase == 1) {
                                    // Plain tap: open the essential in the active space.
                                    if (unpinMode != null) {
                                        if (unpinMode == key) vm.removeEssential(pin.id)
                                        unpinMode = null
                                    } else {
                                        vm.openTabInSpace(vm.activeSpaceId, pin.url, pin.title)
                                    }
                                    return@awaitEachGesture
                                }
                                if (phase == 2) {
                                    // Moved before the hold fired: not a pin gesture.
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        if (ev.changes.all { !it.pressed }) break
                                    }
                                    return@awaitEachGesture
                                }
                                // Armed: the favicon swaps to the red x.
                                unpinMode = key
                                if (vm.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                var dragging = false
                                var lastX = startX
                                var swapLock = 0
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.first()
                                    if (!ch.pressed) {
                                        edgeScroll = 0
                                        dragTileWindowX = Float.NaN
                                        swapLock = 0
                                        if (!dragging) {
                                            vm.removeEssential(pin.id)
                                        } else {
                                            vm.persistEssentials()
                                        }

                                        unpinMode = null
                                        accumulatedDragPx = 0f
                                        if (vm.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        return@awaitEachGesture
                                    }
                                }
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        val faviconAlpha by animateFloatAsState(if (showUnpin) 0f else 1f, tween(140), label = "pinFaviconOut")
                        val faviconScale by animateFloatAsState(if (showUnpin) 0.55f else 1f, tween(180), label = "pinFaviconScale")
                        val unpinAlpha by animateFloatAsState(if (showUnpin) 1f else 0f, tween(140), label = "pinUnpinIn")
                        val unpinScale by animateFloatAsState(if (showUnpin) 1f else 0.55f, tween(220), label = "pinUnpinScale")
                        Box(
                            Modifier
                                .matchParentSize()
                                .alpha(faviconAlpha)
                                .scale(faviconScale),
                            contentAlignment = Alignment.Center,
                        ) {
                            SiteFavicon(host, host.take(1).uppercase(Locale.US), 34.dp)
                        }
                        if (unpinAlpha > 0f) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .scale(unpinScale)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFE23D3D))
                                    .alpha(unpinAlpha),
                                contentAlignment = Alignment.Center,
                            ) {
                                Glyph(Ico.close, Color(0xFFFFFFFF), size = 19.dp, strokeWidth = 2.6f)
                            }
                        }
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
    val haptics = LocalHapticFeedback.current
    val toggleImmersive: () -> Unit = {
        vm.toggleImmersive()
        if (vm.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
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
        )

        Row(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.menuCard)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AddressButton(Ico.chevronLeft, tab?.canGoBack == true) { vm.goBack() }
            AddressButton(Ico.chevronRight, tab?.canGoForward == true) { vm.goForward() }
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
                .background(c.menuCard)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MenuAction("Translate", Ico.translate) { vm.openTranslate() }
            MenuAction("Find", Ico.find) { vm.openFind() }
            MenuAction("Share", Ico.share) { vm.shareCurrentUrl() }
            // Replaces the old Pin action, in the same slot with the same icon.
            val essential = vm.activeTabEssential
            MenuAction(
                if (essential != null) "Remove from Essentials" else "Add to Essentials",
                if (essential != null) Ico.pinOff else Ico.pin,
                accent = essential != null,
                enabled = essential != null || vm.activePinKey.isNotEmpty(),
                labelSize = 9.5.sp,
            ) {
                if (essential != null) {
                    vm.removeEssential(essential.id)
                } else {
                    val url = vm.activeTab?.url.orEmpty()
                    if (url.isNotBlank() && !vm.createEssential(url, vm.activeTab?.title.orEmpty())) {
                        vm.showToast("Essentials full for this space")
                    }
                }
                if (vm.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                .background(c.menuCard),
        ) {
            Row(
                Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(Ico.lens, c.muted, size = 19.dp, strokeWidth = 1.9f)
                Spacer(Modifier.width(11.dp))
                Text("Zoom", color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(c.chip)
                        .clickable { vm.changeZoom(-10) },
                    contentAlignment = Alignment.Center,
                ) { Glyph(Ico.zoomOut, c.text, size = 15.dp, strokeWidth = 2f) }
                Text(
                    if (vm.zoom != 100) "Reset zoom" else "${vm.zoom}%",
                    color = if (vm.zoom != 100) c.accent else c.text,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .width(64.dp)
                        .clickable { vm.resetZoom() },
                )
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(c.chip)
                        .clickable { vm.changeZoom(10) },
                    contentAlignment = Alignment.Center,
                ) { Glyph(Ico.zoomIn, c.text, size = 15.dp, strokeWidth = 2f) }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable { toggleImmersive() }
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
                ArcSwitch(vm.immersiveMode) { toggleImmersive() }
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
                Glyph(Ico.panelTop, c.muted, size = 19.dp, strokeWidth = 1.8f)
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
    enabled: Boolean = true,
    labelSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    onClick: () -> Unit,
) {
    val c = LocalArc.current
    val tint = when {
        accent -> c.accent
        enabled -> c.text
        else -> c.muted
    }
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(13.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 7.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Glyph(paths, tint, size = 20.dp, strokeWidth = 1.8f)
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
        Text(label, color = c.text, fontSize = labelSize, maxLines = 1)
    }
}

/* ------------------------------------------------------------- spaces (phase 1) */

/** Lucide icon paths for a space's icon key. */
private fun spaceIcon(key: String) = when (key) {
    "globe" -> Ico.globe
    "tabs" -> Ico.tabs
    "folder" -> Ico.folder
    "sparkles" -> Ico.sparkles
    "lightbulb" -> Ico.lightbulb
    "palette" -> Ico.palette
    "shield" -> Ico.shield
    "mask" -> Ico.mask
    "gear" -> Ico.gear
    else -> Ico.plus
}

/** 46dp space row: gradient icon disc + name + "pins · open" subtitle + dots + up chevron. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SpaceRow(vm: BrowserViewModel, c: ArcColors, expanded: Boolean, onToggle: () -> Unit) {
    val spaces = vm.spacesSorted
    if (spaces.isEmpty()) return
    val idx = spaces.indexOfFirst { it.id == vm.activeSpaceId }.coerceIn(0, spaces.lastIndex)
    val space = spaces[idx]
    var showManager by remember { mutableStateOf(false) }
    // v5: the card and its disc grow as the sheet fills the screen; the chevron
    // morphs from down (collapsed) to up (expanded).
    val cardH by animateDpAsState(if (expanded) 58.dp else 46.dp, tween(380), label = "spaceCardH")
    val discSize by animateDpAsState(if (expanded) 38.dp else 32.dp, tween(380), label = "spaceDisc")
    val chevronAngle by animateFloatAsState(if (expanded) 0f else 180f, tween(300), label = "spaceChevron")
    val nameAlpha = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(space.id) {
        nameAlpha.animateTo(0f, tween(160, easing = LinearEasing))
        nameAlpha.animateTo(1f, tween(160, easing = LinearEasing))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(cardH)
            .swipeActions(
                onLeft = { vm.switchSpace(spaces[(idx + spaces.size - 1) % spaces.size].id) },
                onRight = { vm.switchSpace(spaces[(idx + 1) % spaces.size].id) },
                strictHorizontal = true,
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onToggle() },
                onLongClick = { showManager = true },
            )
            .semantics { contentDescription = "Space ${space.name}, swipe left/right to switch spaces" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(discSize)
                .clip(RoundedCornerShape(if (expanded) 11.dp else 8.dp))
                .background(Brush.horizontalGradient(listOf(Color(space.colorA), Color(space.colorB)))),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(spaceIcon(space.icon), Color.White, size = 17.dp, strokeWidth = 2f)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(space.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, modifier = Modifier.alpha(nameAlpha.value))
            val pins = vm.pinsOfActiveSpace.count { it.type == PinType.PIN }
            Text("$pins pins · ${vm.activeSpaceTabs.size} open", color = c.muted, fontSize = 11.5.sp, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(Ico.chevronUp, c.muted, size = 16.dp, strokeWidth = 2f,
                modifier = Modifier.rotate(chevronAngle))
        }
    }
    if (showManager) SpaceManager(vm, spaces, space.id) { showManager = false }
}

/** Long-press space manager: name, icon, colours, reorder, collapse, delete, create. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SpaceManager(
    vm: BrowserViewModel,
    spaces: List<Space>,
    initialId: java.util.UUID,
    onDismiss: () -> Unit,
) {
    val c = LocalArc.current
    var selectedId by remember { mutableStateOf(initialId) }
    val selected = spaces.firstOrNull { it.id == selectedId } ?: spaces.firstOrNull()
    var name by remember(selectedId) { mutableStateOf(selected?.name.orEmpty()) }
    var icon by remember(selectedId) { mutableStateOf(selected?.icon ?: "plus") }
    var container by remember(selectedId) { mutableStateOf(selected?.container ?: "none") }
    var cA by remember(selectedId) { mutableLongStateOf(selected?.colorA ?: DEFAULT_SPACE_C1) }
    var cB by remember(selectedId) { mutableLongStateOf(selected?.colorB ?: DEFAULT_SPACE_C2) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // v5: bottom sheet, not a cramped centred dialog.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
                    .clickable(onClick = onDismiss),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(c.sheet)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 24.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(36.dp, 4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(c.muted.copy(alpha = 0.45f)),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (selected == null) "New space" else "Edit · ${selected.name}",
                    color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                // A disc per space; tap one to edit it.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    spaces.take(6).forEach { sp ->
                        val on = sp.id == selectedId
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Brush.horizontalGradient(listOf(Color(sp.colorA), Color(sp.colorB))))
                                .border(2.dp, if (on) c.accent else Color.Transparent, RoundedCornerShape(9.dp))
                                .clickable {
                                    selectedId = sp.id
                                    name = sp.name; icon = sp.icon; container = sp.container; cA = sp.colorA; cB = sp.colorB
                                },
                            contentAlignment = Alignment.Center,
                        ) { Glyph(spaceIcon(sp.icon), Color.White, size = 15.dp, strokeWidth = 2f) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Name field with its brush button in one row (mockup v5).
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.chip)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(c.sheet),
                        contentAlignment = Alignment.Center,
                    ) { Glyph(Ico.sparkles, c.accent, size = 18.dp, strokeWidth = 2f) }
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.weight(1f).padding(end = 6.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Icon", color = c.muted, fontSize = 11.5.sp)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SpaceIcons.take(8).forEach { key ->
                        val on = key == icon
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (on) c.chipStrong else c.chip)
                                .border(2.dp, if (on) c.accent else Color.Transparent, RoundedCornerShape(9.dp))
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) { Glyph(spaceIcon(key), if (on) c.accent else c.muted, size = 16.dp, strokeWidth = 2f) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Container", color = c.muted, fontSize = 11.5.sp)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ContainerPresets.forEach { p ->
                        val on = p.key == container
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (on) Color(p.color) else c.chip)
                                .border(2.dp, if (on) c.accent else Color.Transparent, CircleShape)
                                .clickable { container = p.key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(p.label.take(1), color = if (on) Color.White else c.text,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Colour", color = c.muted, fontSize = 11.5.sp)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SpacePalettes.take(7).forEach { p ->
                        val on = p.c1 == cA && p.c2 == cB
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Brush.horizontalGradient(listOf(Color(p.c1), Color(p.c2))))
                                .border(2.dp, if (on) c.accent else Color.Transparent, CircleShape)
                                .clickable { cA = p.c1; cB = p.c2 },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1.2f)) {
                        ManagerButton("Save", primary = true) {
                            if (selected == null) {
                                val sp = vm.createSpace(
                                    name.ifBlank { "Space" }, name.take(1).uppercase(), cA, cB, icon, container,
                                )
                                vm.switchSpace(sp.id)
                            } else {
                                vm.updateSpace(selected.id, name, name.take(1), icon, container, cA, cB)
                            }
                            onDismiss()
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        ManagerButton("+ New") {
                            // Blank draft: the next Save creates a new space.
                            selectedId = java.util.UUID(0, 0)
                            name = "Space"; icon = "plus"; container = "none"
                            cA = DEFAULT_SPACE_C1; cB = DEFAULT_SPACE_C2
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { ManagerButton("◀ Left") { selected?.let { vm.moveSpace(it.id, -1) } } }
                    Box(Modifier.weight(1f)) { ManagerButton("Right ▶") { selected?.let { vm.moveSpace(it.id, 1) } } }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        ManagerButton("Delete", color = c.press) { selected?.let { vm.deleteSpace(it.id) }; onDismiss() }
                    }
                    Box(Modifier.weight(1f)) { ManagerButton("Cancel") { onDismiss() } }
                }
            }
        }
    }
}

@Composable
private fun ManagerButton(
    text: String,
    color: Color = Color.Unspecified,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalArc.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (primary) c.accent else c.chip)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (primary) Color.White else if (color != Color.Unspecified) color else c.text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** One row of the expanded pins tree: folder or pin, with depth indent + rule. */
@Composable
private fun PinTreeSection(vm: BrowserViewModel, c: ArcColors) {
    val space = vm.activeSpace
    Column(Modifier.padding(bottom = 10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { space?.let { vm.toggleSpaceCollapsed(it.id) } }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Pins",
                color = c.text,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (space?.collapsed == true) "tap to expand" else "tap to collapse",
                color = c.muted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            Glyph(Ico.chevronUp, c.muted, size = 14.dp, strokeWidth = 2f,
                modifier = Modifier.rotate(if (space?.collapsed == true) 180f else 0f))
        }
        if (space?.collapsed == true) return
        val topLevel = vm.pinNodes
            .filter { it.spaceId == vm.activeSpaceId && it.parentId == null && it.deletedAt == null }
            .sortedBy { it.sortIndex }
        if (topLevel.isEmpty()) {
            Text("No pins yet", color = c.muted, fontSize = 12.5.sp, modifier = Modifier.padding(vertical = 8.dp))
        }
        topLevel.forEach { node ->
            PinNodeRow(vm, node, c, 0)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PinNodeRow(vm: BrowserViewModel, node: PinNode, c: ArcColors, depth: Int) {
    var showMenu by remember { mutableStateOf(false) }
    val indent = 18.dp * depth
    val host = BrowserViewModel.hostOf(node.url)
    val isActive = node.type == PinType.PIN &&
        vm.activeTab?.url?.let { vm.pinKeyOf(it) == vm.pinKeyOf(node.url) } == true
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .padding(start = indent)
            .then(if (isActive) Modifier
                .clip(RoundedCornerShape(10.dp))
                .border(2.dp, faviconAccent(host) ?: c.accent, RoundedCornerShape(10.dp)) else Modifier)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (node.type == PinType.PIN) vm.openTabInSpace(node.spaceId, node.url, node.title) },
                onLongClick = { showMenu = true },
            )
            .then(if (depth > 0) Modifier.border(1.dp, c.hairline, RoundedCornerShape(6.dp)).padding(start = 12.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (node.type == PinType.FOLDER) {
            val children = vm.pinNodes
                .filter { it.spaceId == node.spaceId && it.parentId == node.id && it.deletedAt == null }
                .sortedBy { it.sortIndex }
            val rot by animateFloatAsState(if (node.collapsed) 0f else 90f, tween(140), label = "folderChevron")
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(c.chip), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(2) { i ->
                        repeat(2) { j ->
                            Box(Modifier.size(9.dp).padding(1.5.dp).clip(RoundedCornerShape(2.dp)).background(faviconAccent(host) ?: c.accent))
                        }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(node.title.ifBlank { "Folder" }, color = c.text, fontSize = 13.5.sp, maxLines = 1)
                if (children.isNotEmpty()) {
                    Text(" pins", color = c.muted, fontSize = 11.5.sp)
                }
            }
            Glyph(Ico.chevronDown, c.muted, size = 14.dp, strokeWidth = 2f, modifier = Modifier.rotate(rot))
            if (!node.collapsed) {
                children.forEach { PinNodeRow(vm, it, c, depth + 1) }
            }
        } else {
            SiteFavicon(host, node.title.take(1), 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(node.title.ifBlank { host.ifBlank { "Pin" } }, color = c.text, fontSize = 13.5.sp, maxLines = 1)
        }
    }
    if (showMenu) PinContextMenu(vm, node, c) { showMenu = false }
}

/** Long-press context menu on a pin, folder or essential: Rename / Delete. */
@Composable
private fun PinContextMenu(vm: BrowserViewModel, node: PinNode, c: ArcColors, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 260.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.menuCard)
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MenuItem(c, "Rename") { vm.renamePinNode(node.id, node.title); onDismiss() }
            MenuItem(c, if (node.type == PinType.FOLDER) "Delete" else "Delete", color = c.press) {
                vm.deletePinNode(node.id); onDismiss()
            }
        }
    }
}

@Composable
private fun MenuItem(c: ArcColors, text: String, color: Color = Color.Unspecified, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text, color = if (color != Color.Unspecified) color else c.text, fontSize = 14.sp)
    }
}

/** Open-tabs section shown when the sheet is expanded. */
@Composable
private fun OpenTabsSection(vm: BrowserViewModel, c: ArcColors) {
    val tabs = vm.activeSpaceTabs.filter { it.url.isNotBlank() }
    if (tabs.isEmpty()) return
    var clearStage by remember { mutableStateOf(0) }
    LaunchedEffect(tabs.size) { clearStage = 0 }
    Column(Modifier.padding(top = 10.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Open tabs", color = c.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            // v5: always reads "Clear" — first tap closes the others, second closes current.
            Text(
                "Clear",
                color = c.muted,
                fontSize = 11.5.sp,
                modifier = Modifier.clickable {
                    if (clearStage == 0) {
                        if (tabs.size > 1) {
                            tabs.filter { it.id != vm.activeId }.forEach { vm.closeTab(it.id) }
                            clearStage = 1
                            vm.showToast("Tap Clear again to close the current tab")
                        } else {
                            clearStage = 1
                            vm.showToast("Tap Clear again to close this tab")
                        }
                    } else {
                        vm.closeTab(vm.activeId)
                        clearStage = 0
                    }
                }.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        tabs.forEach { tab ->
            val host = BrowserViewModel.hostOf(tab.url)
            val active = tab.id == vm.activeId
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (active) Modifier.border(2.dp, faviconAccent(host) ?: c.accent, RoundedCornerShape(12.dp)) else Modifier)
                    .clickable { vm.openTabOfSpace(vm.activeSpaceId, tab.id) }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SiteFavicon(host, BrowserViewModel.hostOf(tab.url).ifBlank { tab.title.take(1) }, 20.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(tab.title.ifBlank { "New tab" }, color = c.text, fontSize = 14.sp, maxLines = 1)
                }
            }
        }
    }
}

/** Sync status at the bottom of the expanded list — wired to Mozilla account. */
@Composable
private fun SyncStrip(vm: BrowserViewModel, c: ArcColors) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .clickable { if (vm.fxaSignedIn) vm.syncSpacesNow() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(vm.syncStatus, color = c.muted, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
        if (vm.fxaSignedIn) Text("Sync now", color = c.accent, fontSize = 11.5.sp)
    }
}
