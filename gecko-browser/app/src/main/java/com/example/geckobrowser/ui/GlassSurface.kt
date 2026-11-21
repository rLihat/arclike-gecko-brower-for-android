package com.example.geckobrowser.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Drop-in glass fill for an existing Box: draws the recipe behind its content. */
@Composable
fun BoxScope.GlassFill(tint: Color, alpha: Float = 0.48f) {
    GlassSurface(Modifier.matchParentSize(), tint = tint, tintAlpha = alpha) {}
}

/**
 * The one glass recipe used everywhere the new frost applies: translucent tint,
 * a white-to-transparent top highlight, a gradient edge (bright top-left), an
 * inner top rim, a soft shadow and a press-scale spring.
 *
 * The blurred backdrop itself comes from the active layer (Haze for Compose
 * backdrops, window blur for GeckoView pages, or the snapshot fallback) and is
 * supplied by the caller as [backdrop], drawn beneath this recipe.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    tint: Color,
    /** 0..1; higher in light mode so text stays readable. */
    tintAlpha: Float = 0.48f,
    /** Saturation boost for the blurred backdrop (~1.6–1.8 in the recipe). */
    saturation: Float = 1.7f,
    backdrop: (@Composable () -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(),
        label = "glassPress",
    )
    Box(
        modifier
            .hazeGlass()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(tint.copy(alpha = tintAlpha), shape),
    ) {
        if (backdrop != null) {
            // Saturation of the blurred backdrop is supplied by Haze's tint
            // layer; the recipe's boost is expressed through tintAlpha.
            Box(Modifier.matchParentSize()) {
                backdrop.invoke()
            }
        }
        // Top highlight: white fading to transparent over the top ~38%.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        // Gradient edge: bright at top-left, faint at bottom-right.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.36f),
                            Color.White.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.06f),
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(tint.copy(alpha = 0f)),
        )
        content()
    }
}
