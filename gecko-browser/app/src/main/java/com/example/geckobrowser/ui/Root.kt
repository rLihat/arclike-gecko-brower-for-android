package com.example.geckobrowser.ui

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.geckobrowser.BrowserViewModel
import com.example.geckobrowser.BrowserPromptState
import com.example.geckobrowser.ChoiceOption
import com.example.geckobrowser.ContextMenuState
import com.example.geckobrowser.NavNotice
import com.example.geckobrowser.Overlay
import com.example.geckobrowser.PendingDownload
import com.example.geckobrowser.PermissionPromptState
import com.example.geckobrowser.PopupNotice
import com.example.geckobrowser.TabInfo
import com.example.geckobrowser.downKindLabel
import com.example.geckobrowser.formatBytes
import kotlinx.coroutines.delay
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import kotlin.math.abs


@Composable
fun BrowserApp(vm: BrowserViewModel, onPickAddon: () -> Unit) {
    ArcTheme(vm.darkMode, vm.accentKey) {
        val systemDark = isSystemInDarkTheme()
        LaunchedEffect(systemDark) { vm.updateSystemDark(systemDark) }
        val c = LocalArc.current
        val overlay = vm.overlay
        val tab = vm.activeTab
        val reduceMotion = rememberReduceMotion()
        val haptics = LocalHapticFeedback.current

        // Pinch-in hides the chrome; it only applies on a live page at 100%
        // zoom with nothing layered over it. Leaving that state brings it back.
        val immersive = vm.immersiveMode
        var immBanner by remember { mutableStateOf(false) }
        val canImmersive = overlay == Overlay.None && tab != null && !tab.isHome &&
            vm.zoom == 100 && vm.pendingDownload == null
        LaunchedEffect(canImmersive) {
            if (!canImmersive && vm.immersiveMode) vm.toggleImmersive()
        }
        LaunchedEffect(immersive) {
            if (immersive) immBanner = true
            else immBanner = false
        }

        LaunchedEffect(vm.addonPickerRequest.value) {
            if (vm.addonPickerRequest.value) {
                vm.consumeAddonPickerRequest()
                onPickAddon()
            }
        }

        // A fresh page load dips the page to 92% and eases it back, matching the
        // mockup's page-load zoom. Back/forward and sheet opens don't bump these.
        val pageScale = remember { Animatable(1f) }
        LaunchedEffect(vm.loadZoomStart) {
            if (vm.loadZoomStart > 0) {
                pageScale.snapTo(if (reduceMotion) 1f else 0.92f)
            }
        }
        LaunchedEffect(vm.loadZoomEnd) {
            if (vm.loadZoomEnd > 0) {
                pageScale.animateTo(
                    1f,
                    tween(if (reduceMotion) 0 else 420, easing = morphEasing),
                )
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
                tween(260, easing = morphEasing),
                label = "flipScale",
            )
            val flipAlpha by animateFloatAsState(
                if (overlay == Overlay.Tabs) 0.5f else 1f,
                tween(240),
                label = "flipAlpha",
            )
            // Swiping the plus swaps tabs: the incoming page slides in from the
            // swiped direction and fades up, mirroring the mockup. The start
            // offset is baked into a keyed Animatable so the first frame of the
            // new page is already off to the side (no flash at rest).
            val slideDistance = with(LocalDensity.current) { 64.dp.toPx() }
            val tabSwitch = vm.tabSwitch
            val pageSlide = remember(tabSwitch) {
                Animatable(tabSwitch?.let { it.dir * slideDistance } ?: 0f)
            }
            LaunchedEffect(tabSwitch) {
                if (tabSwitch == null) return@LaunchedEffect
                if (vm.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                pageSlide.animateTo(0f, tween(if (reduceMotion) 0 else 320, easing = morphEasing))
                vm.consumeTabSwitch()
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = flip * pageScale.value
                        scaleY = flip * pageScale.value
                        alpha = flipAlpha * (1f - 0.9f * (abs(pageSlide.value) / slideDistance).coerceIn(0f, 1f))
                        translationX = pageSlide.value
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        clip = overlay == Overlay.Tabs
                        shape = RoundedCornerShape(30.dp)
                    }
            ) {
                WebHost(
                    session = vm.activeSession,
                    background = c.siteBg,
                    zoom = vm.zoom / 100f,
                    pinchEnabled = canImmersive,
                    onPinchIn = { vm.toggleImmersive() },
                    onTap = { x, y -> vm.recordPageTap(x, y) },
                    onViewReady = { vm.onGeckoViewReady(it) },
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
                            onSearch = { vm.openNewTabSearch() },
                            onOpen = { url -> vm.submit(url) },
                        )
                    }
                }
            }

            // The carousel grows out of the page when opened and flies back into
            // the pill's footprint on close. Reduced motion jumps to the end.
            var tabsVisible by remember { mutableStateOf(false) }
            val tabAnim = remember { Animatable(1f) }
            LaunchedEffect(overlay) {
                if (overlay == Overlay.Tabs) {
                    tabAnim.snapTo(0.92f)
                    tabsVisible = true
                    tabAnim.animateTo(1f, tween(if (reduceMotion) 0 else 320, easing = morphEasing))
                } else if (tabsVisible) {
                    tabAnim.animateTo(0.14f, tween(if (reduceMotion) 0 else 400, easing = morphEasing))
                    tabsVisible = false
                    tabAnim.snapTo(1f)
                }
            }
            val tabAlpha by animateFloatAsState(
                if (overlay == Overlay.Tabs) 1f else 0f,
                tween(if (reduceMotion) 0 else if (overlay == Overlay.Tabs) 220 else 320),
                label = "tabsAlpha",
            )
            if (tabsVisible) {
                TabSwitcher(
                    vm = vm,
                    modifier = Modifier.graphicsLayer {
                        val progress = ((tabAnim.value - 0.14f) / 0.86f).coerceIn(0f, 1f)
                        scaleX = tabAnim.value
                        scaleY = tabAnim.value
                        alpha = tabAlpha
                        translationY = (1f - progress) * 0.36f * size.height
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    },
                )
            }

            val immAppear = remember { Animatable(0f) }
            LaunchedEffect(immBanner) {
                if (immBanner) {
                    immAppear.snapTo(0f)
                    immAppear.animateTo(1f, tween(260, easing = morphEasing))
                    delay(1800)
                    immAppear.animateTo(0f, tween(140))
                    immBanner = false
                } else {
                    immAppear.animateTo(0f, tween(140))
                }
            }
            if (immAppear.value > 0.01f) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                        .fillMaxWidth(0.7f)
                        .height(58.dp)
                        .graphicsLayer {
                            alpha = immAppear.value
                            val s = 0.96f + 0.04f * immAppear.value
                            scaleX = s
                            scaleY = s
                            translationY = (1f - immAppear.value) * 14.dp.toPx()
                        }
                        .clip(RoundedCornerShape(29.dp))
                        .background(c.navSoft)
                        .border(1.dp, c.stroke, RoundedCornerShape(29.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(c.chip),
                            contentAlignment = Alignment.Center,
                        ) {
                            Glyph(Ico.expand, c.accent, size = 15.dp, strokeWidth = 2f)
                        }
                        Spacer(Modifier.width(11.dp))
                        Column {
                            Text("Immersive mode", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Pinch in again to bring the pill back", color = c.muted, fontSize = 11.5.sp)
                        }
                    }
                }
            }

            if (!vm.fullscreen) {
                ChromeOverlay(vm, immersive)
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp),
                ) {
                    UpdateBanner(vm)
                }
            }
        }
    }
}

@Composable
private fun WebHost(
    session: GeckoSession?,
    background: Color,
    zoom: Float,
    pinchEnabled: Boolean,
    onPinchIn: () -> Unit,
    onTap: (Float, Float) -> Unit,
    onViewReady: (GeckoView) -> Unit,
) {
    // The factory lambda only runs once, so read live values through state.
    val zoomState = rememberUpdatedState(zoom)
    val tapState = rememberUpdatedState(onTap)
    val pinchEnabledState = rememberUpdatedState(pinchEnabled)
    val pinchState = rememberUpdatedState(onPinchIn)
    val readyState = rememberUpdatedState(onViewReady)
    AndroidView(
        factory = { ctx ->
            val density = ctx.resources.displayMetrics.density.coerceAtLeast(0.1f)
            var lastWheelAt = 0L
            // Two-finger pinch-in mirrors the mockup: the fingers must start
            // 90dp apart, the span has to fall under 0.82x, and the detector
            // fires at most once per gesture. Pinching out does nothing.
            val scaleDetector = ScaleGestureDetector(
                ctx,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    private var startSpan = 0f
                    private var consumed = false

                    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                        consumed = false
                        startSpan = if (pinchEnabledState.value) detector.currentSpan else 0f
                        return startSpan >= 90f * density
                    }

                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        if (consumed || startSpan <= 0f) return false
                        if (detector.currentSpan < startSpan * 0.82f) {
                            consumed = true
                            if (pinchEnabledState.value) pinchState.value()
                        }
                        return false
                    }
                },
            )
            GeckoView(ctx).apply {
                readyState.value(this)
                setBackgroundColor(background.toArgb())
                session?.let { setSession(it) }
                setOnTouchListener { view, event ->
                    scaleDetector.onTouchEvent(event)
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
                // Trackpads deliver a pinch as ctrl+scroll; only the inward
                // direction (positive VSCROLL) toggles, debounced like the wheel.
                setOnGenericMotionListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_SCROLL &&
                        (event.metaState and android.view.KeyEvent.META_CTRL_ON) != 0 &&
                        event.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0f &&
                        pinchEnabledState.value
                    ) {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastWheelAt >= 900) {
                            lastWheelAt = now
                            pinchState.value()
                        }
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
    Overlay.Downloads, Overlay.Extensions, Overlay.ExtensionPopup, Overlay.History,
    Overlay.Translate, Overlay.Passwords, Overlay.About, Overlay.ExtensionMenu, Overlay.ReleaseNotes,
)

@Composable
private fun ChromeOverlay(vm: BrowserViewModel, immersive: Boolean) {
    val c = LocalArc.current
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(vm.downloadsDonePulse) {
        if (vm.downloadsDonePulse > 0 && vm.hapticFeedback) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = maxWidth
        val screenH = maxHeight
        val density = LocalDensity.current
        val reduceMotion = rememberReduceMotion()
        // Edge-to-edge means the IME overlays us; lift the search sheet above it.
        val imeDp = with(density) { WindowInsets.ime.getBottom(this).toDp() }
        val overlay = vm.overlay
        val open = overlay in sheetOverlays
        val fullHeight = overlay == Overlay.Settings || overlay == Overlay.SiteSettings ||
            overlay == Overlay.Account || overlay == Overlay.Downloads ||
            overlay == Overlay.Extensions || overlay == Overlay.History ||
            overlay == Overlay.Translate || overlay == Overlay.Passwords ||
            overlay == Overlay.About || overlay == Overlay.ReleaseNotes

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

        // Swipe-up-to-cancel on the chip: the drag progress drives the floating
        // trash confirm, and `cancelDone` plays it out after the release.
        var cancelProgress by remember { mutableFloatStateOf(0f) }
        var cancelDone by remember { mutableStateOf(false) }

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
            Overlay.Extensions -> ({ ExtensionsScreen(vm) { vm.onBackPressed() } })
            Overlay.History -> ({ HistoryScreen(vm) { vm.closeHistory() } })
            Overlay.Translate -> ({ TranslateScreen(vm) { vm.closeTranslate() } })
            Overlay.Passwords -> ({ PasswordsScreen(vm) { vm.closePasswords() } })
            Overlay.About -> ({ AboutScreen(vm) { vm.closeAbout() } })
            Overlay.ReleaseNotes -> ({ ReleaseNotesScreen(vm) { vm.closeReleaseNotes() } })
            Overlay.ExtensionPopup -> ({ ExtensionPopupSheet(vm) })
            Overlay.ExtensionMenu -> ({ ExtensionMenuSheet(vm) })
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

        // The panel content rises 26dp into place with a short delay, so it
        // isn't a bare fade. The full-page settings views skip the lift.
        val contentRise by animateDpAsState(
            if (open) 0.dp else 26.dp,
            tween(
                if (reduceMotion) 0 else 420,
                easing = morphEasing,
                delayMillis = if (open && !reduceMotion) 100 else 0,
            ),
            label = "sheetRise",
        )
        val rises = lastSheet == Overlay.Search || lastSheet == Overlay.Menu

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

            val morphSpec = tween<Dp>(
                if (reduceMotion) 0 else if (open) 420 else 300,
                easing = morphEasing,
            )
            val w by animateDpAsState(targetW, morphSpec, label = "sheetW")
            val h by animateDpAsState(targetH, morphSpec, label = "sheetH")
            val bottom by animateDpAsState(targetBottom, morphSpec, label = "sheetBottom")
            val topR by animateDpAsState(targetTopRadius, morphSpec, label = "sheetTopR")
            val bottomR by animateDpAsState(targetBottomRadius, morphSpec, label = "sheetBottomR")
            val contentAlpha by animateFloatAsState(
                if (open) 1f else 0f,
                tween(
                    if (reduceMotion) 0 else if (open) 220 else 120,
                    delayMillis = if (open && !reduceMotion) 100 else 0,
                ),
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
                            lastSheet == Overlay.Search || lastSheet == Overlay.Menu ||
                            lastSheet == Overlay.ExtensionMenu -> c.sheetSoft
                            lastSheet == Overlay.Downloads || lastSheet == Overlay.History ||
                                lastSheet == Overlay.Translate || lastSheet == Overlay.Passwords ||
                                lastSheet == Overlay.About || lastSheet == Overlay.ReleaseNotes -> c.settingsBg
                            else -> c.surface
                        }
                    ),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = contentAlpha
                            translationY = if (rises) contentRise.toPx() else 0f
                        },
                ) {
                    content()
                }
            }
        }

        // The nav pill floats above the sheet, exactly like the mockup. The
        // back/forward notice keeps the pill on screen so its swipe zone stays
        // usable while the popup is up; immersive mode, the download question,
        // a blocked popup, find-in-page and a page-level toast all still hide it.
        val popupPill = vm.popupBlocked != null && overlay == Overlay.None &&
            !vm.findOpen
        val pillVisible = (overlay == Overlay.None || overlay == Overlay.Menu ||
            overlay == Overlay.Search || overlay == Overlay.Tabs) && !immersive &&
            vm.pendingDownload == null && !popupPill && !vm.findOpen &&
            !(overlay == Overlay.None && vm.toastMessage != null)
        if (pillVisible) {
            val searchMode = overlay == Overlay.Search
            val sheetH = screenH * 0.47f
            NavPill(
                vm = vm,
                screenW = screenW,
                menuMode = overlay == Overlay.Menu,
                searchMode = searchMode,
                searchBottom = sheetLift + sheetH - 61.dp,
                searchX = screenW / 2 - 47.dp,
                visible = true,
            )
        }

        // The live download chip sits in the free gutter beside the 70% pill.
        // The expanded menu always offers it; on the page it only appears while
        // a transfer is moving, then lingers three seconds after the last one
        // finishes before hiding itself.
        val activeDownloadCount = vm.activeDownloads.size
        var showDownloadsChip by remember { mutableStateOf(false) }
        LaunchedEffect(activeDownloadCount) {
            if (activeDownloadCount > 0) {
                showDownloadsChip = true
            } else if (showDownloadsChip) {
                delay(3000)
                showDownloadsChip = false
            }
        }
        val chipVisible = !immersive && vm.flight == null && !vm.findOpen &&
            vm.pendingDownload == null && !popupPill &&
            !(overlay == Overlay.None && vm.toastMessage != null) && (
            when (overlay) {
                Overlay.Menu -> true
                Overlay.None -> !vm.compactPill && showDownloadsChip && vm.downloads.isNotEmpty()
                else -> false
            }
            )
        DownloadChip(
            vm = vm,
            visible = chipVisible,
            iconOnly = overlay == Overlay.Menu,
            onCancelProgress = { cancelProgress = it },
            onCanceled = { cancelDone = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = 24.dp),
        )
        // Extensions mirror the downloads chip, opposite edge: icon-only, menu only.
        val extensionsChipVisible = overlay == Overlay.Menu
        ExtensionChip(
            visible = extensionsChipVisible,
            onClick = { vm.openExtensionMenu() },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, bottom = 24.dp),
        )
        DownloadCancelPreview(
            progress = cancelProgress,
            done = cancelDone,
            onDonePlayed = { cancelDone = false },
        )
        NavNoticeHost(vm)

        // Live page requests borrow the pill footprint; a permission question is
        // deliberately its own card so the site and permission stay readable.
        PermissionPromptHost(vm)
        BrowserPromptHost(vm)

        DownloadPromptHost(vm)
        FindBarHost(vm)
        PopupBlockedHost(vm)
        SelectionHost(vm, screenW, screenH)
        ContextMenuHost(vm, screenW, screenH)
        ToastHost(vm, raised = pillVisible)
        DownloadFlightLayer(vm, screenW, screenH)
    }
}

@Composable
private fun BoxScope.ToastHost(vm: BrowserViewModel, raised: Boolean) {
    val c = LocalArc.current
    val reduceMotion = rememberReduceMotion()
    val message = vm.toastMessage
    // Over a visible pill the message sits just above the capsule; when the
    // pill is hidden (on a page toast, or a full-height overlay like Settings
    // where there is no capsule) it takes the pill's own footprint.
    LaunchedEffect(message) {
        if (message != null) {
            delay(2100)
            vm.clearToast()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = popupEnter(reduceMotion),
        exit = popupExit(reduceMotion),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Box(
            Modifier
                .padding(bottom = if (raised) 92.dp else 18.dp)
                .fillMaxWidth(0.7f)
                .height(58.dp)
                .clip(RoundedCornerShape(29.dp))
                .background(c.navSurface)
                .border(1.dp, c.stroke, RoundedCornerShape(29.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                message.orEmpty(),
                color = c.text,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
    }
}

/* ---------------------------------------------------------- downloads UI */

@Composable
private fun DownloadChip(
    vm: BrowserViewModel,
    visible: Boolean,
    iconOnly: Boolean,
    onCancelProgress: (Float) -> Unit,
    onCanceled: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    // 0..1 upward drag. Local state keeps every pointer sample, then mirrors the
    // value up so the trash confirm can float above the chip.
    var drag by remember { mutableFloatStateOf(0f) }
    var cancelBucket by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val label = when {
        active.isNotEmpty() -> "${active.size} download${if (active.size == 1) "" else "s"} in progress"
        vm.downloads.isNotEmpty() -> "${vm.downloads.size} download${if (vm.downloads.size == 1) "" else "s"}"
        else -> "Downloads"
    }

    AnimatedVisibility(
        visible = visible,
        enter = popupEnter(false),
        exit = popupExit(false),
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
                .pointerInput(active.isNotEmpty()) {
                    if (active.isEmpty()) return@pointerInput
                    val travel = 76.dp.toPx()
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            drag = (drag - amount / travel).coerceIn(0f, 1f)
                            val bucket = (drag * 4f).toInt().coerceIn(0, 4)
                            if (bucket > cancelBucket && vm.hapticFeedback) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            cancelBucket = bucket
                            onCancelProgress(drag)
                        },
                        onDragEnd = {
                            if (drag >= 0.8f) {
                                vm.cancelNewestDownload()
                                onCanceled()
                            }
                            drag = 0f
                            cancelBucket = 0
                            onCancelProgress(0f)
                        },
                        onDragCancel = {
                            drag = 0f
                            cancelBucket = 0
                            onCancelProgress(0f)
                        },
                    )
                }
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
                    // In the expanded menu the chip reads as one more pill icon:
                    // no bead, just the glyph (the ring still reports progress).
                    .background(if (iconOnly) Color.Transparent else c.navSoft)
                    .then(if (iconOnly) Modifier else Modifier.border(1.dp, c.stroke, CircleShape)),
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
private fun ExtensionChip(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalArc.current
    AnimatedVisibility(
        visible = visible,
        enter = popupEnter(false),
        exit = popupExit(false),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Glyph(
                Ico.puzzle,
                c.text,
                size = 24.dp,
                strokeWidth = 1.9f,
            )
        }
    }
}

/* -------------------------------------------------- swipe-to-cancel UI */

@Composable
private fun BoxScope.DownloadCancelPreview(
    progress: Float,
    done: Boolean,
    onDonePlayed: () -> Unit,
) {
    val c = LocalArc.current
    val shown = progress > 0.02f || done
    if (shown) {
        LaunchedEffect(done) {
            if (done) {
                delay(320)
                onDonePlayed()
            }
        }
    }
    if (!shown) return

    val armed = progress >= 0.55f
    val near = progress >= 0.75f
    val red = if (c.dark) Color(0xFFFF9D92) else Color(0xFFD14C40)
    val trashAlpha by animateFloatAsState(
        if (done) 0f else 0.3f + 0.7f * progress,
        tween(if (done) 220 else 60),
        label = "trashAlpha",
    )
    val trashScale by animateFloatAsState(
        if (done) 0.5f else 0.74f + 0.28f * progress,
        tween(if (done) 220 else 60),
        label = "trashScale",
    )
    val xAlpha by animateFloatAsState(
        if (near && !done) 1f else 0f,
        tween(140),
        label = "xAlpha",
    )

    Column(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 6.dp, bottom = 76.dp)
            .width(46.dp)
            .height(104.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Box(Modifier.size(42.dp)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = trashAlpha
                        scaleX = trashScale
                        scaleY = trashScale
                        translationY = (1f - progress) * 18.dp.toPx()
                    }
                    .clip(CircleShape)
                    .background(if (armed) red.copy(alpha = 0.14f) else c.navSoft)
                    .border(
                        1.dp,
                        if (armed) red.copy(alpha = 0.5f) else c.stroke,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(Ico.trash, if (armed) red else c.text, size = 20.dp, strokeWidth = 2f)
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(16.dp)
                    .alpha(xAlpha)
                    .clip(CircleShape)
                    .background(red),
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(7.dp))
        Glyph(
            Ico.arrowUp,
            if (armed) red else c.muted.copy(alpha = 0.35f + 0.65f * progress),
            size = 22.dp,
            strokeWidth = 2f,
            modifier = Modifier
                .alpha(if (done) 0f else 1f)
                .offset(y = ((1f - progress) * 6f).dp),
        )
    }
}

/* ---------------------------------------------- back/forward nav notice */

@Composable
private fun BoxScope.NavNoticeHost(vm: BrowserViewModel) {
    val c = LocalArc.current
    val notice = vm.navNotice
    var last by remember { mutableStateOf<NavNotice?>(null) }
    if (notice != null) last = notice
    // Driven by hand so a second swipe re-plays the entrance instead of just
    // swapping the text inside an already-visible popup.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(notice?.at) {
        if (notice == null) {
            appear.animateTo(0f, tween(140))
        } else {
            appear.snapTo(0f)
            appear.animateTo(1f, tween(260, easing = morphEasing))
        }
    }
    if (notice == null && appear.value <= 0.01f) return
    val n = last ?: return
    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 84.dp)
            .fillMaxWidth(0.7f)
            .height(58.dp)
            .graphicsLayer {
                alpha = appear.value
                val s = 0.96f + 0.04f * appear.value
                scaleX = s
                scaleY = s
                translationY = (1f - appear.value) * 14.dp.toPx()
            }
            .clip(RoundedCornerShape(29.dp))
            .background(c.navSoft)
            .border(1.dp, c.stroke, RoundedCornerShape(29.dp)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(c.chip),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(
                if (n.forward) Ico.forward else Ico.back,
                c.accent,
                size = 15.dp,
                strokeWidth = 2f,
            )
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(
                if (n.forward) "Forward" else "Back",
                color = c.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                n.url?.let { BrowserViewModel.hostOf(it) }
                    ?: if (n.forward) "No page to go forward to" else "No page to go back to",
                color = c.muted,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ------------------------------------------------------ prompts and loading */

@Composable
private fun BoxScope.LoadingDotsHost(vm: BrowserViewModel, immersive: Boolean) {
    if (!vm.pipLoading || vm.overlay != Overlay.None || immersive || vm.fullscreen) return
    val c = LocalArc.current
    val t by rememberInfiniteTransition(label = "dots").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "dotPhase",
    )
    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 86.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(3) { index ->
            val phase = (t + index * 0.17f) % 1f
            val bump = (1f - abs(phase - 0.5f) * 2f).coerceIn(0f, 1f)
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(c.accent.copy(alpha = 0.22f + 0.68f * bump))
            )
        }
    }
}

@Composable
private fun BoxScope.PermissionPromptHost(vm: BrowserViewModel) {
    val prompt = vm.permissionPrompt ?: return
    val c = LocalArc.current
    var rememberChoice by remember(prompt.id) { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.26f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { vm.cancelPermissionPrompt() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .padding(bottom = 18.dp)
                .fillMaxWidth(0.82f)
                .shadow(20.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(c.navSurface)
                .border(1.dp, c.stroke, RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* keep taps on the card */ }
                .padding(16.dp),
        ) {
            Text(
                "${prompt.host} wants to use ${prompt.kind.label.lowercase()}",
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Remember for this site",
                    color = c.muted,
                    fontSize = 11.5.sp,
                    modifier = Modifier.weight(1f),
                )
                ArcSwitch(rememberChoice) { rememberChoice = it }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PromptTextButton("Block", primary = false) {
                    vm.answerPermission(false, rememberChoice)
                }
                Spacer(Modifier.width(8.dp))
                PromptTextButton("Allow", primary = true) {
                    vm.answerPermission(true, rememberChoice)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.BrowserPromptHost(vm: BrowserViewModel) {
    val prompt = vm.browserPrompt ?: return
    val c = LocalArc.current

    // Field state is keyed to the prompt id so a new question starts clean.
    var textValue by remember(prompt.id) {
        mutableStateOf((prompt as? BrowserPromptState.Text)?.value.orEmpty())
    }
    var userValue by remember(prompt.id) {
        mutableStateOf((prompt as? BrowserPromptState.Auth)?.username.orEmpty())
    }
    var passValue by remember(prompt.id) { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.30f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { vm.cancelPrompt() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .padding(bottom = 18.dp)
                .fillMaxWidth(0.86f)
                .shadow(22.dp, RoundedCornerShape(26.dp))
                .clip(RoundedCornerShape(26.dp))
                .background(c.navSurface)
                .border(1.dp, c.stroke, RoundedCornerShape(26.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* keep taps on the card */ }
                .padding(18.dp),
        ) {
            when (prompt) {
                is BrowserPromptState.Alert -> {
                    PromptHeading(prompt.title)
                    if (prompt.message.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        PromptNote(prompt.message)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        PromptTextButton("OK", primary = true) { vm.answerAlert(prompt.id) }
                    }
                }

                is BrowserPromptState.Button -> {
                    PromptHeading(prompt.title)
                    if (prompt.message.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        PromptNote(prompt.message)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        PromptTextButton("Cancel", primary = false) {
                            vm.answerButton(prompt.id, confirm = false)
                        }
                        Spacer(Modifier.width(8.dp))
                        PromptTextButton("OK", primary = true) {
                            vm.answerButton(prompt.id, confirm = true)
                        }
                    }
                }

                is BrowserPromptState.Text -> {
                    PromptHeading(prompt.title)
                    if (prompt.message.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        PromptNote(prompt.message)
                    }
                    PromptField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        placeholder = "Type here",
                        onGo = { vm.answerText(prompt.id, textValue) },
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        PromptTextButton("Cancel", primary = false) { vm.cancelPrompt() }
                        Spacer(Modifier.width(8.dp))
                        PromptTextButton("OK", primary = true) {
                            vm.answerText(prompt.id, textValue)
                        }
                    }
                }

                is BrowserPromptState.Choice -> {
                    PromptHeading(prompt.title)
                    if (prompt.message.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        PromptNote(prompt.message)
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        prompt.options.forEach { option: ChoiceOption ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(if (option.selected) c.chip else Color.Transparent)
                                    .clickable { vm.answerChoice(prompt.id, option.id) }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    option.label,
                                    color = c.text,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                if (option.selected) {
                                    Text("✓", color = c.accent, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        PromptTextButton("Cancel", primary = false) { vm.cancelPrompt() }
                    }
                }

                is BrowserPromptState.Auth -> {
                    PromptHeading(prompt.host.ifBlank { "Sign in" })
                    if (prompt.message.isNotBlank() && prompt.message != prompt.host) {
                        Spacer(Modifier.height(6.dp))
                        PromptNote(prompt.message)
                    }
                    PromptField(
                        value = userValue,
                        onValueChange = { userValue = it },
                        placeholder = "Username",
                    )
                    PromptField(
                        value = passValue,
                        onValueChange = { passValue = it },
                        placeholder = "Password",
                        password = true,
                        onGo = { vm.answerAuth(prompt.id, userValue, passValue) },
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        PromptTextButton("Cancel", primary = false) { vm.cancelPrompt() }
                        Spacer(Modifier.width(8.dp))
                        PromptTextButton("Sign in", primary = true) {
                            vm.answerAuth(prompt.id, userValue, passValue)
                        }
                    }
                }

                is BrowserPromptState.ExternalLink -> {
                    PromptHeading(if (prompt.appName.isNotBlank()) "Open in ${prompt.appName}?"
                        else "Open in another app?")
                    Spacer(Modifier.height(6.dp))
                    PromptNote(prompt.host)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        prompt.url,
                        color = c.muted,
                        fontSize = 10.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        PromptTextButton("Cancel", primary = false) { vm.cancelPrompt() }
                        Spacer(Modifier.width(8.dp))
                        PromptTextButton("Open", primary = true) {
                            vm.answerExternalLink(prompt.id, open = true)
                        }
                    }
                }

                is BrowserPromptState.ExtensionInstall -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (prompt.icon != null) {
                            Image(
                                bitmap = prompt.icon.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                            )
                        } else {
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(c.chip),
                                contentAlignment = Alignment.Center,
                            ) { Glyph(Ico.puzzle, c.accent, size = 24.dp, strokeWidth = 1.8f) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            PromptHeading("Add ${prompt.name}?")
                            Text(
                                "This add-on is asking for:",
                                color = c.muted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    prompt.permissions.take(6).forEach { permission ->
                        Text("· $permission", color = c.muted, fontSize = 11.5.sp)
                    }
                    if (prompt.origins.isNotEmpty()) {
                        Text(
                            "· Sites: ${prompt.origins.take(3).joinToString()}",
                            color = c.muted,
                            fontSize = 11.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        PromptTextButton("Cancel", primary = false) {
                            vm.answerExtensionInstall(prompt.id, allow = false)
                        }
                        Spacer(Modifier.width(8.dp))
                        PromptTextButton("Add", primary = true) {
                            vm.answerExtensionInstall(prompt.id, allow = true)
                        }
                    }
                }

                is BrowserPromptState.File -> {
                    PromptHeading("Choose a file")
                    Spacer(Modifier.height(8.dp))
                    PromptNote("The system file picker is open.")
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        PromptTextButton("Cancel", primary = false) { vm.cancelPrompt() }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptHeading(text: String) {
    val c = LocalArc.current
    Text(
        text,
        color = c.text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PromptNote(text: String) {
    val c = LocalArc.current
    Text(
        text,
        color = c.muted,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
    )
}

@Composable
private fun PromptTextButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val c = LocalArc.current
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) c.accent else c.chip)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) Color.White else c.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PromptField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    onGo: (() -> Unit)? = null,
) {
    val c = LocalArc.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.chip)
            .border(1.dp, c.stroke, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = c.muted, fontSize = 13.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 13.sp),
            cursorBrush = SolidColor(c.accent),
            visualTransformation = if (password) {
                PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(
                imeAction = if (onGo != null) ImeAction.Go else ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onGo = { onGo?.invoke() },
                onDone = { onGo?.invoke() },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/* ----------------------------------------------------------- extension popup */

@Composable
private fun ExtensionPopupSheet(vm: BrowserViewModel) {
    val c = LocalArc.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(Ico.puzzle, c.text, size = 17.dp, strokeWidth = 1.8f)
            Spacer(Modifier.width(9.dp))
            Text(
                vm.extensionPopupTitle.ifBlank { "Extension" },
                color = c.text,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { vm.closeExtensionPopup() },
                contentAlignment = Alignment.Center,
            ) {
                Glyph(Ico.close, c.muted, size = 14.dp, strokeWidth = 2f)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
        AndroidView(
            factory = { ctx ->
                GeckoView(ctx).apply {
                    setBackgroundColor(c.sheet.toArgb())
                    vm.extensionPopup?.let { setSession(it) }
                }
            },
            update = { view ->
                val session = vm.extensionPopup
                if (session != null && view.session !== session) view.setSession(session)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun BoxScope.DownloadPromptHost(vm: BrowserViewModel) {
    val c = LocalArc.current
    val reduceMotion = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    val current = vm.pendingDownload
    var last by remember { mutableStateOf<PendingDownload?>(null) }
    // Keep the last pending file so the popup has content while it animates out.
    if (current != null) last = current
    LaunchedEffect(current?.url) {
        if (current != null && vm.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    AnimatedVisibility(
        visible = current != null,
        enter = popupEnter(reduceMotion),
        exit = popupExit(reduceMotion),
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
                // A pill-sized question in the pill's own footprint: file tile,
                // name and size, a quiet ✕ and the download button.
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                        .fillMaxWidth(0.7f)
                        .height(58.dp)
                        .clip(RoundedCornerShape(29.dp))
                        .background(c.navSurface)
                        .border(1.dp, c.stroke, RoundedCornerShape(29.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { /* keep taps on the popup */ }
                        .padding(start = 10.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DownloadFileTile(pending.name, size = 32.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            pending.name,
                            color = c.text,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            "${if (pending.size > 0) formatBytes(pending.size) else "Unknown size"} · ${pending.host}",
                            color = c.muted,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { vm.cancelDownloadPrompt() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Glyph(Ico.close, c.muted, size = 14.dp, strokeWidth = 2f)
                    }
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(c.accent)
                            .clickable { vm.confirmDownloadPrompt() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Glyph(Ico.download, Color.White, size = 17.dp, strokeWidth = 2f)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.PopupBlockedHost(vm: BrowserViewModel) {
    val c = LocalArc.current
    val reduceMotion = rememberReduceMotion()
    val notice = vm.popupBlocked
    var last by remember { mutableStateOf<PopupNotice?>(null) }
    // Keep the last request so the pill has content while it animates away.
    if (notice != null) last = notice
    // A sheet takes the stage; the request times out and is denied on its own.
    AnimatedVisibility(
        visible = notice != null && vm.overlay == Overlay.None && !vm.fullscreen &&
            !vm.findOpen,
        enter = popupEnter(reduceMotion),
        exit = popupExit(reduceMotion),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            val shown = last
            if (shown != null) {
                // The same footprint as the pill, so it reads as the browser
                // speaking rather than the page.
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                        .fillMaxWidth(0.7f)
                        .height(58.dp)
                        .clip(RoundedCornerShape(29.dp))
                        .background(c.navSurface)
                        .border(1.dp, c.stroke, RoundedCornerShape(29.dp))
                        .padding(start = 10.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.chip),
                        contentAlignment = Alignment.Center,
                    ) {
                        Glyph(Ico.tabs, c.iconInk, size = 17.dp, strokeWidth = 1.9f)
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Popup blocked",
                            color = c.text,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            shown.host,
                            color = c.muted,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "Allow once",
                        color = c.accent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(c.chip)
                            .clickable { vm.answerPopup(shown.id, allow = true) }
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { vm.dismissPopupNotice() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Glyph(Ico.close, c.muted, size = 14.dp, strokeWidth = 2f)
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

/**
 * Find in page: a field in the pill's footprint with the match counter and
 * previous/next. It keeps focus and raises the keyboard as it opens, and Gecko
 * highlights every hit and dims the rest of the page while it is up.
 */
@Composable
private fun BoxScope.FindBarHost(vm: BrowserViewModel) {
    val c = LocalArc.current
    val reduceMotion = rememberReduceMotion()
    val open = vm.findOpen
    AnimatedVisibility(
        visible = open && vm.overlay == Overlay.None,
        enter = popupEnter(reduceMotion),
        exit = popupExit(reduceMotion),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            val focus = remember { FocusRequester() }
            val keyboard = LocalSoftwareKeyboardController.current
            val density = LocalDensity.current
            // With edge-to-edge the keyboard is an inset, not a window resize.
            val imeBottom = with(density) { WindowInsets.ime.getBottom(this).toDp() }
            // The field is the reason the bar exists, so it starts focused.
            LaunchedEffect(Unit) {
                runCatching { focus.requestFocus() }
                keyboard?.show()
            }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp + imeBottom)
                    .fillMaxWidth(0.88f)
                    .widthIn(max = 430.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(c.navSurface)
                    .border(1.dp, c.stroke, RoundedCornerShape(27.dp))
                    .padding(start = 13.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(Ico.find, c.muted, size = 17.dp, strokeWidth = 1.9f)
                Spacer(Modifier.width(9.dp))
                BasicTextField(
                    value = vm.findQuery,
                    onValueChange = { vm.updateFindQuery(it) },
                    singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.findNext() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus),
                    decorationBox = { inner ->
                        if (vm.findQuery.isEmpty()) {
                            Text("Find in page", color = c.muted, fontSize = 14.sp)
                        }
                        inner()
                    },
                )
                val counter = when {
                    vm.findQuery.isBlank() -> ""
                    vm.findTotal == 0 -> "No results"
                    else -> "${vm.findCurrent}/${vm.findTotal}"
                }
                if (counter.isNotEmpty()) {
                    Text(
                        counter,
                        color = if (vm.findTotal == 0) c.muted else c.text,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp, end = 2.dp),
                    )
                }
                FindButton(Ico.chevronUp, enabled = vm.findTotal > 1) { vm.findPrev() }
                FindButton(Ico.chevronUp, enabled = vm.findTotal > 1, flipped = true) {
                    vm.findNext()
                }
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { vm.closeFind() },
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph(Ico.close, c.muted, size = 15.dp, strokeWidth = 2f)
                }
            }
        }
    }
}

@Composable
private fun FindButton(
    icon: List<Path>,
    enabled: Boolean,
    flipped: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalArc.current
    Box(
        Modifier
            .size(31.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(
            icon,
            if (enabled) c.text else c.muted,
            size = 16.dp,
            strokeWidth = 2f,
            modifier = if (flipped) Modifier.rotate(180f) else Modifier,
        )
    }
}

@Composable
private fun BoxScope.SelectionHost(vm: BrowserViewModel, screenW: Dp, screenH: Dp) {
    val c = LocalArc.current
    val haptics = LocalHapticFeedback.current
    val state = vm.selectionState ?: return
    if (vm.overlay != Overlay.None || vm.fullscreen) return
    LaunchedEffect(state) {
        if (vm.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val width = 216.dp
    val maxX = (screenW - width - 8.dp).coerceAtLeast(8.dp)
    val x = (state.x.dp - width / 2).coerceIn(8.dp, maxX)
    val maxY = (screenH - 64.dp).coerceAtLeast(8.dp)
    val y = (state.y.dp + 10.dp).coerceIn(8.dp, maxY)

    Row(
        Modifier
            .offset(x = x, y = y)
            .widthIn(min = 186.dp, max = width)
            .shadow(18.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(c.sheet)
            .border(1.dp, c.stroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.canCopy) {
            SelectionAction("Copy", Ico.copy) {
                vm.runSelectionAction(GeckoSession.SelectionActionDelegate.ACTION_COPY)
            }
        }
        if (state.canSelectAll) {
            SelectionAction("Select all", Ico.tabs) {
                vm.runSelectionAction(GeckoSession.SelectionActionDelegate.ACTION_SELECT_ALL)
            }
        }
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .clickable { vm.hideSelection() },
            contentAlignment = Alignment.Center,
        ) {
            Glyph(Ico.close, c.muted, size = 14.dp, strokeWidth = 2f)
        }
    }
}

@Composable
private fun SelectionAction(label: String, icon: List<Path>, onClick: () -> Unit) {
    val c = LocalArc.current
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(icon, c.iconInk, size = 15.dp, strokeWidth = 1.9f)
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = c.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun BoxScope.ContextMenuHost(vm: BrowserViewModel, screenW: Dp, screenH: Dp) {
    val c = LocalArc.current
    val ctx = vm.contextMenu ?: return
    // A long-press should feel like one: the sheet and the buzz land together.
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(ctx) {
        if (vm.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
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
        add(CtxAction("Copy image address", Ico.link) {
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
    add(CtxAction("Copy link", Ico.link) {
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
    val reduceMotion = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
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
    // In the menu the pill stays visible as a capsule in the sheet's base:
    // chip fill and a hairline stroke, handed over slowly.
    val capsuleBg by animateColorAsState(
        when {
            searchMode -> Color.Transparent
            menuMode -> Color.Transparent
            vm.tabs.isEmpty() -> Color.Transparent
            else -> c.navSoft
        },
        tween(if (reduceMotion) 0 else 420, easing = morphEasing),
        label = "pillBg",
    )
    val capsuleStroke by animateColorAsState(
        Color.Transparent,
        tween(if (reduceMotion) 0 else 420, easing = morphEasing),
        label = "pillStroke",
    )

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
                .background(capsuleBg)
                .border(1.dp, capsuleStroke, RoundedCornerShape(h / 2))
                .then(if (mode == PillMode.Compact) Modifier.clickable { vm.expandPill() } else Modifier),
        ) {
            Crossfade(targetState = mode, animationSpec = tween(150), label = "pillSet") { m ->
                when (m) {
                    PillMode.Search -> Box(
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .swipeActions(onDown = { vm.closeOverlay() })
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
                        PillButton(
                            onClick = { vm.toggleMenu() },
                            onSwipeUp = { vm.openMenu() },
                            onSwipeDown = { vm.closeOverlay() },
                            onSwipeLeft = { vm.navGesture(forward = false); if (vm.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onSwipeRight = { vm.navGesture(forward = true); if (vm.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                            strictHorizontal = true,
                        ) {
                            Glyph(Ico.chevronUp, c.text, size = 24.dp,
                                strokeWidth = 2.2f, modifier = Modifier.rotate(chevronRotation))
                        }
                    }

                    PillMode.Normal -> Row(
                        Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tabsOrEmpty = vm.overlay == Overlay.Tabs || vm.tabs.isEmpty()
                        PillButton(
                            onClick = { if (tabsOrEmpty) vm.openHistory() else vm.toggleTabs() },
                            onSwipeUp = { if (!tabsOrEmpty) vm.openTabs() },
                        ) {
                            if (tabsOrEmpty) {
                                Glyph(Ico.clock, c.text, size = 21.dp, strokeWidth = 2.1f)
                            } else {
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
                        }
                        PillButton(
                            onClick = { vm.openSearch() },
                            onSwipeUp = {
                                if (vm.overlay == Overlay.Tabs || vm.tabs.isEmpty()) vm.openNewTabSearch()
                                else vm.openMenu()
                            },
                            onSwipeDown = { vm.closeOverlay() },
                            onSwipeLeft = { if (vm.tabs.size > 1) { vm.switchTab(1); if (vm.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress) } },
                            onSwipeRight = { if (vm.tabs.size > 1) { vm.switchTab(-1); if (vm.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress) } },
                        ) {
                            if (vm.pipLoading) {
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    repeat(3) { index ->
                                        val phase = ((System.currentTimeMillis() / 300f + index * 0.33f) % 1f)
                                        val bump = (1f - abs(phase - 0.5f) * 2f).coerceIn(0f, 1f)
                                        Box(
                                            Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(c.accent.copy(alpha = 0.3f + 0.7f * bump))
                                        )
                                    }
                                }
                            } else {
                                Glyph(Ico.plus, c.text, size = 24.dp, strokeWidth = 2.2f)
                            }
                        }
                        PillButton(
                            onClick = { vm.toggleMenu() },
                            onSwipeUp = { vm.openMenu() },
                            onSwipeDown = { vm.closeOverlay() },
                            onSwipeLeft = { vm.navGesture(forward = false); if (vm.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onSwipeRight = { vm.navGesture(forward = true); if (vm.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                            strictHorizontal = true,
                        ) {
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
