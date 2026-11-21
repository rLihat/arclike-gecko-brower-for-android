package com.example.geckobrowser.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/** Haze state shared between a screen's backdrop and its glass surfaces. */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/**
 * Layer 1 wrapper: marks the Compose-drawn backdrop of a screen as a haze source
 * and exposes the state to descendant glass surfaces. No PixelCopy is used for
 * these surfaces.
 */
@Composable
fun ScreenHaze(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val state = remember { HazeState() }
    androidx.compose.runtime.CompositionLocalProvider(LocalHazeState provides state) {
        content()
    }
}

/** Apply to a composable that draws the pixels to be blurred (e.g. a frost image). */
@Composable
fun Modifier.hazeBackdrop(): Modifier {
    val state = LocalHazeState.current
    return if (state != null) this.hazeSource(state) else this
}

/** Apply to a glass surface to sample the screen's backdrop. */
@Composable
fun Modifier.hazeGlass(radius: Dp = 24.dp, enabled: Boolean = true): Modifier {
    val state = LocalHazeState.current
    return if (state != null) {
        this.hazeEffect(state) {
            blurRadius = radius
            noiseFactor = 0.03f
            blurEnabled = enabled
        }
    } else {
        this
    }
}
