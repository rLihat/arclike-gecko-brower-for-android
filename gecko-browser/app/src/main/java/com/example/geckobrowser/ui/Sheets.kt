package com.example.geckobrowser.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
            EssentialLabel(vm, c)
            EssentialRow(vm)
        }
        SpaceRow(vm, c)

        LaunchedEffect(expanded) {
            vm.searchExpanded = expanded
        }

        if (expanded) {
            PinTreeSection(vm, c)
            OpenTabsSection(vm, c)
            SyncStrip(c)
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
                                    val dx = ch.position.x - lastX
                                    lastX = ch.position.x
                                    if (!dragging) {
                                        if (kotlin.math.abs(ch.position.x - startX) > slop) {
                                            // Drag engaged instead of unpin (horizontal only).
                                            dragging = true
                                            unpinMode = null
                                            accumulatedDragPx = 0f
                                            swapLock = 0
                                            ch.consume()
                                            if (vm.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    } else {
                                        ch.consume()
                                        accumulatedDragPx += dx
                                        val threshold = tileWidthPx * 0.33f
                                        if (kotlin.math.abs(accumulatedDragPx) >= threshold) {
                                            // One swap per event; carry the remainder so fast
                                            // flicks step one tile per frame, never two at once.
                                            val direction = if (accumulatedDragPx > 0f) 1 else -1
                                            if (swapLock != 0 && direction != swapLock) {
                                                // Direction reversal: the accumulator must build up
                                                // again from zero before the row swaps back.
                                                accumulatedDragPx = 0f
                                            } else {
                                                val from = vm.pins.indexOfFirst { it.key == key }
                                                if (from >= 0) {
                                                    val to = (from + direction).coerceIn(0, vm.pins.lastIndex)
                                                    if (to != from) vm.movePin(from, to)
                                                }
                                                accumulatedDragPx -= direction * threshold
                                                swapLock = direction
                                            }
                                        }
                                        val edge = tileWidthPx * 0.25f
                                        edgeScroll = if (dragTileWindowX.isNaN()) 0 else when {
                                            dragTileWindowX < rowLeftPx + edge -> -1
                                            dragTileWindowX + tileWidthPx > rowRightPx - edge -> 1
                                            else -> 0
                                        }
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
            val pinKey = vm.activePinKey
            val pinned = vm.isActiveTabPinned
            if (pinKey.isNotEmpty()) {
                MenuAction(
                    if (pinned) "Unpin" else "Pin",
                    if (pinned) Ico.pinOff else Ico.pin,
                    accent = pinned,
                ) {
                    vm.togglePinForActiveUrl()
                    if (vm.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            } else {
                MenuAction(
                    "Pin",
                    Ico.pin,
                    enabled = false,
                ) { /* pin needs a site; no-op on home */ }
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
        Text(label, color = c.text, fontSize = 11.sp, maxLines = 1)
    }
}

/* ------------------------------------------------------------- spaces (phase 1) */

/** "Essentials N/12" label above the essentials row. */
@Composable
private fun EssentialLabel(vm: BrowserViewModel, c: ArcColors) {
    val essentials = vm.essentialsSorted
    Text(
        "Essentials /",
        color = c.muted,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 12.dp, top = 4.dp),
    )
}

/** 46dp space row: letter disc + name + "pins · open" subtitle + dots + chevron. */
@Composable
private fun SpaceRow(vm: BrowserViewModel, c: ArcColors) {
    val spaces = vm.spacesSorted
    if (spaces.isEmpty()) return
    val idx = spaces.indexOfFirst { it.id == vm.activeSpaceId }.coerceIn(0, spaces.lastIndex)
    val space = spaces[idx]
    var showMenu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = { showMenu = true },
            )
            .semantics { contentDescription = "Space , swipe left/right to switch spaces" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.horizontalGradient(listOf(Color(space.colorA), Color(space.colorB)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                space.letter.take(2).uppercase(),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                space.name,
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            val pins = vm.pinsOfActiveSpace.count { it.deletedAt == null && it.type == PinType.PIN }
            Text(
                " pins ·  open",
                color = c.muted,
                fontSize = 11.5.sp,
                maxLines = 1,
            )
        }
        repeat(3) { i ->
            val active = i == idx
            Box(
                Modifier
                    .size(if (active) 16.dp else 6.dp)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(if (active) c.accent else c.chip),
            )
        }
        Spacer(Modifier.width(10.dp))
        Glyph(Ico.chevronDown, c.muted, size = 15.dp, strokeWidth = 2f)
    }
    if (showMenu) SpaceCreateDialog(vm, spaces) { showMenu = false }
}

/** Long-press dialog on the space row: creates a space from a palette preset. */
@Composable
private fun SpaceCreateDialog(vm: BrowserViewModel, spaces: List<Space>, onDismiss: () -> Unit) {
    val c = LocalArc.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.menuCard)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Spaces", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                spaces.forEach { sp ->
                    Box(
                        Modifier
                            .size(34.dp)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.horizontalGradient(listOf(Color(sp.colorA), Color(sp.colorB))))
                            .clickable {
                                vm.createSpace(sp.name, sp.letter, sp.colorA, sp.colorB)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(Ico.plus, Color.White, size = 14.dp, strokeWidth = 2.2f) }
                }
                Text(
                    SpacePalettes.map { it.name }.joinToString(" · "),
                    color = c.muted,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/** One row of the expanded pins tree: folder or pin, with depth indent + rule. */
@Composable
private fun PinTreeSection(vm: BrowserViewModel, c: ArcColors) {
    val space = vm.activeSpace
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(
            "Pins · ",
            color = c.text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
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

@Composable
private fun PinNodeRow(vm: BrowserViewModel, node: PinNode, c: ArcColors, depth: Int) {
    var showMenu by remember { mutableStateOf(false) }
    val indent = 18.dp * depth
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .padding(start = indent)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (node.type == PinType.PIN) vm.openTabInSpace(node.spaceId, node.url, node.title) },
                onLongClick = { showMenu = true },
            )
            .then(if (depth > 0) Modifier.border(1.dp, c.hairline, RoundedCornerShape(6.dp)).padding(start = 12.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val host = BrowserViewModel.hostOf(node.url)
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
            verticalAlignment = Alignment.CenterHorizontally,
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
    Column(Modifier.padding(top = 10.dp, bottom = 4.dp)) {
        Text("Open tabs", color = c.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        tabs.forEach { tab ->
            val host = BrowserViewModel.hostOf(tab.url)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
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

/** Phase-2 placeholder: sync status strip at the bottom of the expanded list. */
@Composable
private fun SyncStrip(c: ArcColors) {
    Text(
        "Not synced",
        color = c.muted,
        fontSize = 11.5.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
    )
}
