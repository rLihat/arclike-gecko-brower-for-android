package com.example.geckobrowser.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The mockup's design tokens, transcribed for native Compose. */
data class ArcColors(
    val dark: Boolean,
    val pageTop: Color,
    val pageBottom: Color,
    val text: Color,
    val muted: Color,
    val surface: Color,
    val surfaceStrong: Color,
    /** Card/row fill used by settings groups and download rows. */
    val field: Color,
    val navSurface: Color,
    /** Translucent surfaces used only by the nav pill and the search/menu sheets. */
    val navSoft: Color,
    val sheetSoft: Color,
    val sheet: Color,
    val settingsBg: Color,
    val switcherBg: Color,
    val siteBg: Color,
    val chip: Color,
    val chipStrong: Color,
    val accent: Color,
    val iconInk: Color,
    val stroke: Color,
    val hairline: Color,
    val switchOff: Color,
    val knob: Color,
    val press: Color,
    val rowHover: Color,
    val glow: Color,
    val handle: Color,
)

val LightArc = ArcColors(
    dark = false,
    pageTop = Color(0xFFF4F7F6),
    pageBottom = Color(0xFFCFD8D3),
    text = Color(0xFF101314),
    muted = Color(0x8C101314),
    surface = Color(0xFFF4F4F2),
    surfaceStrong = Color(0xFFEBEBEA),
    field = Color(0xFFFFFFFF),
    navSurface = Color(0xFFF2F2F0),
    navSoft = Color(0xCCF6F6F4),
    sheetSoft = Color(0xE0F9F9F7),
    sheet = Color(0xFFF3F3F1),
    settingsBg = Color(0xFFF6F6F4),
    switcherBg = Color(0xFFFFFFFF),
    siteBg = Color(0xFFFFFFFF),
    chip = Color(0xFFE9E9E6),
    chipStrong = Color(0xFFDEDEDB),
    accent = Color(0xFF2F5C69),
    iconInk = Color(0xFF2F5C69),
    stroke = Color(0x14101314),
    hairline = Color(0x0F000000),
    switchOff = Color(0xFFDCDCD9),
    knob = Color(0xFFFFFFFF),
    press = Color(0x0F101314),
    rowHover = Color(0xFFF0F0EE),
    glow = Color(0x8C4F93A8),
    handle = Color(0x2E000000),
)

val DarkArc = ArcColors(
    dark = true,
    pageTop = Color(0xFF1B2831),
    pageBottom = Color(0xFF090D10),
    text = Color(0xFFEEF3F2),
    muted = Color(0x94EEF3F2),
    surface = Color(0xFF1A2024),
    surfaceStrong = Color(0xFF232A2E),
    field = Color(0xFF232A2E),
    navSurface = Color(0xFF1C2226),
    navSoft = Color(0xCC1C2226),
    sheetSoft = Color(0xE0181E22),
    sheet = Color(0xFF1A2024),
    settingsBg = Color(0xFF12171A),
    switcherBg = Color(0xFF0D1114),
    siteBg = Color(0xFF0E1317),
    chip = Color(0xFF232A2E),
    chipStrong = Color(0xFF2E363B),
    accent = Color(0xFF4F93A8),
    iconInk = Color(0xFF9FD0DF),
    stroke = Color(0x1AFFFFFF),
    hairline = Color(0x14FFFFFF),
    switchOff = Color(0xFF394248),
    knob = Color(0xFFEEF3F2),
    press = Color(0x14FFFFFF),
    rowHover = Color(0xFF2B3237),
    glow = Color(0x8078C8E1),
    handle = Color(0x38FFFFFF),
)

val LocalArc = staticCompositionLocalOf { LightArc }

@Composable
fun ArcTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalArc provides if (dark) DarkArc else LightArc, content = content)
}
