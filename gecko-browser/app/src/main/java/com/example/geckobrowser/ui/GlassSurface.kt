package com.example.geckobrowser.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Frost v2 tokens, transcribed from the attached specification. */
object FrostV2 {
    val BlurRadius = 30.dp
    const val Saturation = 1.8f

    val LightFill = Color(0x80FFFFFF)
    val LightFillStrong = Color(0xA3FFFFFF)
    val LightSheen = Color(0x8CFFFFFF)
    val LightTop = Color(0xF2FFFFFF)
    val LightBottom = Color(0x47FFFFFF)
    val LightRing = Color(0x73FFFFFF)
    val LightGlow = Color(0x382F5C69)

    val DarkFill = Color(0x611A242E)
    val DarkFillStrong = Color(0x8518202A)
    val DarkSheen = Color(0x2BFFFFFF)
    val DarkTop = Color(0x52FFFFFF)
    val DarkBottom = Color(0x0FFFFFFF)
    val DarkRing = Color(0x24FFFFFF)
    val DarkGlow = Color(0x385AAAC8)
}

/** Drop-in Frost v2 fill for an existing Box. */
@Composable
fun BoxScope.GlassFill(dark: Boolean, strong: Boolean = false) {
    val fill = when {
        dark && strong -> FrostV2.DarkFillStrong
        dark -> FrostV2.DarkFill
        strong -> FrostV2.LightFillStrong
        else -> FrostV2.LightFill
    }
    val sheen = if (dark) FrostV2.DarkSheen else FrostV2.LightSheen
    val top = if (dark) FrostV2.DarkTop else FrostV2.LightTop
    val bottom = if (dark) FrostV2.DarkBottom else FrostV2.LightBottom
    val ring = if (dark) FrostV2.DarkRing else FrostV2.LightRing
    val glow = if (dark) FrostV2.DarkGlow else FrostV2.LightGlow
    val shape = RoundedCornerShape(20.dp)

    Box(
        Modifier
            .matchParentSize()
            .shadow(14.dp, shape, spotColor = glow, ambientColor = glow)
            .background(fill, shape),
    )
    // Top sheen fades out by ~38% of the surface.
    Box(
        Modifier
            .matchParentSize()
            .background(
                Brush.verticalGradient(listOf(sheen, Color.Transparent), endY = 900f),
                shape,
            ),
    )
    // Diagonal light sweep.
    Box(
        Modifier
            .matchParentSize()
            .background(
                Brush.linearGradient(listOf(top.copy(alpha = 0.10f), Color.Transparent)),
                shape,
            ),
    )
    // Inner top rim and faint bottom rim.
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.5.dp)
            .align(Alignment.TopCenter)
            .background(top),
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .align(Alignment.BottomCenter)
            .background(bottom),
    )
    // Gradient edge ring, brighter at top-left.
    Box(
        Modifier
            .matchParentSize()
            .background(
                Brush.linearGradient(listOf(ring.copy(alpha = 0.6f), Color.Transparent)),
                shape,
            )
            .then(Modifier.background(Color.Transparent)),
    )
}

/** Full Frost v2 glass surface; the backdrop is sampled by the active layer. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    dark: Boolean = false,
    strong: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(),
        label = "glassPress",
    )
    Box(
        modifier
            .hazeGlass(radius = FrostV2.BlurRadius)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        GlassFill(dark = dark, strong = strong)
        content()
    }
}
