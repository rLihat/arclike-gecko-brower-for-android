package com.example.geckobrowser.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layer 1 (Compose-side Haze) is intentionally disabled. Third-party Haze
 * builds are tied to a specific Compose runtime and caused startup crashes
 * when the Compose/Kotlin version moved for GeckoView 161. Frosting still
 * comes from Layer 2 (the page-surface snapshot) and plain tint fallbacks.
 * These helpers stay as no-ops so call sites don't have to change.
 */
val LocalHazeState = compositionLocalOf<Any?> { null }

@Composable
fun ScreenHaze(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    content()
}

@Composable
fun Modifier.hazeBackdrop(): Modifier = this

@Composable
fun Modifier.hazeGlass(radius: Dp = 24.dp, enabled: Boolean = true): Modifier = this
