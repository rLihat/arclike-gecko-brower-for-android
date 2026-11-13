package com.example.geckobrowser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.geckobrowser.BrowserViewModel
import com.example.geckobrowser.Overlay
import com.example.geckobrowser.TabInfo
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
                WebHost(session = vm.activeSession, background = c.siteBg, zoom = vm.zoom / 100f)
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
private fun WebHost(session: GeckoSession?, background: Color, zoom: Float) {
    AndroidView(
        factory = { ctx ->
            GeckoView(ctx).apply {
                setBackgroundColor(background.toArgb())
                session?.let { setSession(it) }
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
    Overlay.Search, Overlay.Menu, Overlay.Settings, Overlay.SiteSettings, Overlay.Account
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
            overlay == Overlay.Account

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
                    .background(if (closing) Color.Transparent else c.surface),
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

        ToastHost(vm)
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
                .background(if (menuMode || searchMode) Color.Transparent else c.navSurface)
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
