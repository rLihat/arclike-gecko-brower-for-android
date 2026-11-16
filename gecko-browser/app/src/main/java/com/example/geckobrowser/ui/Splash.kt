package com.example.geckobrowser.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geckobrowser.BrowserViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/** horiZen night field; deliberately independent of the light/dark chrome. */
val HorizenNight = Color(0xFF160C42)
private val HorizenDeep = Color(0xFF140A38)

private data class Band(
    val radius: Float,
    val start: Color,
    val mid: Color,
    val end: Color,
)

/** The ring stack, outermost first, straight from the logo SVG. */
private val bands = listOf(
    Band(236f, Color(0xFF8A3DF0), Color(0xFFE0309F), Color(0xFF3B6DF0)),
    Band(176f, Color(0xFFE0309F), Color(0xFFC23FD8), Color(0xFFE0309F)),
    Band(116f, Color(0xFFC23FD8), Color(0xFF6A4CF5), Color(0xFF3FB0FF)),
    Band(56f, Color(0xFFFFB3E6), Color(0xFFB49CFF), Color(0xFF9FD2FF)),
)

private val wordmarkGradient = Brush.linearGradient(
    listOf(Color(0xFFFF9BE0), Color(0xFFB49CFF), Color(0xFF3FB0FF)),
)

/**
 * The logo, drawn natively so the rings can move. [progress] 0..1 expands the
 * stack out of the horizon like a sunrise; 1 is the resting mark.
 */
@Composable
fun HorizenMark(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = min(size.width, size.height) / 512f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val discR = 256f * unit
        val p = progress.coerceIn(0f, 1f)

        // A quiet halo so the disc doesn't sit on the field as a hard circle.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF6B2BD9).copy(alpha = 0.34f * p),
                    Color(0xFF2B5FE0).copy(alpha = 0.16f * p),
                    Color.Transparent,
                ),
                center = Offset(cx, cy + discR * 0.15f),
                radius = discR * 1.35f,
            ),
            radius = discR * 1.35f,
            center = Offset(cx, cy + discR * 0.15f),
        )

        val disc = Path().apply {
            addOval(Rect(Offset(cx - discR, cy - discR), Size(discR * 2f, discR * 2f)))
        }

        clipPath(disc) {
            // Faint magenta and blue glows behind the stack, as on the SVG disc.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFB0268F).copy(alpha = 0.52f * p),
                        Color.Transparent,
                    ),
                    center = Offset(cx - 166f * unit, cy - 146f * unit),
                    radius = 140f * unit,
                ),
                radius = 140f * unit,
                center = Offset(cx - 166f * unit, cy - 146f * unit),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2B5FE0).copy(alpha = 0.46f * p),
                        Color.Transparent,
                    ),
                    center = Offset(cx + 184f * unit, cy + 144f * unit),
                    radius = 150f * unit,
                ),
                radius = 150f * unit,
                center = Offset(cx + 184f * unit, cy + 144f * unit),
            )

            // Above the horizon: stacked half-discs read as rings because each
            // smaller one covers the middle of the previous one.
            clipRect(0f, 0f, size.width, cy) {
                bands.forEachIndexed { index, band ->
                    val stagger = (bands.lastIndex - index) * 0.06f
                    val local = ((p - stagger) / 0.72f).coerceIn(0f, 1f)
                    if (local <= 0f) return@forEachIndexed
                    val eased = 1f - (1f - local) * (1f - local)
                    val r = band.radius * unit * (0.52f + 0.48f * eased)
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                band.start.copy(alpha = 0.35f + 0.65f * eased),
                                band.mid.copy(alpha = 0.35f + 0.65f * eased),
                                band.end.copy(alpha = 0.35f + 0.65f * eased),
                            ),
                            start = Offset(cx - r, cy),
                            end = Offset(cx + r, cy - r * 0.75f),
                        ),
                        radius = r,
                        center = Offset(cx, cy),
                    )
                }
            }

            // Below: the same stack mirrored and fading into the water.
            val mirror = ((p - 0.30f) / 0.55f).coerceIn(0f, 1f)
            if (mirror > 0f) {
                clipRect(0f, cy, size.width, size.height) {
                    drawRect(
                        color = HorizenDeep.copy(alpha = 0.85f * mirror),
                        topLeft = Offset(0f, cy),
                        size = Size(size.width, size.height - cy),
                    )
                    bands.forEachIndexed { index, band ->
                        val inner = bands.getOrNull(index + 1)?.radius ?: 0f
                        val r = band.radius * unit
                        val path = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addOval(Rect(Offset(cx - r, cy - r), Size(r * 2f, r * 2f)))
                            if (inner > 0f) {
                                val ir = inner * unit
                                addOval(Rect(Offset(cx - ir, cy - ir), Size(ir * 2f, ir * 2f)))
                            }
                        }
                        drawPath(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    band.start.copy(alpha = 0.42f * mirror),
                                    band.mid.copy(alpha = 0.16f * mirror),
                                    Color.Transparent,
                                ),
                                startY = cy,
                                endY = cy + r,
                            ),
                            path = path,
                        )
                    }
                }
            }

            // Glossy water: a pale wash from the horizon down, then a diagonal
            // sheen across the glass (both fade with the mark's progress).
            val wash = ((p - 0.30f) / 0.55f).coerceIn(0f, 1f)
            if (wash > 0f) {
                clipRect(0f, cy, size.width, size.height) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFD9C8FF).copy(alpha = 0.10f * wash),
                                Color.Transparent,
                            ),
                            startY = cy,
                            endY = size.height,
                        ),
                        topLeft = Offset(0f, cy),
                        size = Size(size.width, size.height - cy),
                    )
                    drawRect(
                        brush = Brush.linearGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.38f to Color.Transparent,
                                0.5f to Color(0xFFE6DCFF).copy(alpha = 0.30f * wash),
                                0.62f to Color.Transparent,
                                1f to Color.Transparent,
                            ),
                            start = Offset(cx - discR, cy),
                            end = Offset(cx + discR, cy + discR),
                        ),
                        topLeft = Offset(0f, cy),
                        size = Size(size.width, size.height - cy),
                    )
                }
            }
        }

        // Bright glint along the horizon.
        val glint = ((p - 0.18f) / 0.42f).coerceIn(0f, 1f)
        if (glint > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.9f * glint),
                        Color.Transparent,
                    ),
                    startX = cx - 216f * unit,
                    endX = cx + 216f * unit,
                ),
                topLeft = Offset(cx - 216f * unit, cy - 1.5f * unit),
                size = Size(432f * unit, 3f * unit),
            )
        }
    }
}

/**
 * Full-screen splash: the mark on the night field, the wordmark fading in
 * below it, all driven by one 0..1 [progress] so the caller owns the clock.
 */
@Composable
fun HorizenSplash(progress: Float, modifier: Modifier = Modifier) {
    val p = progress.coerceIn(0f, 1f)
    val wordIn = ((p - 0.22f) / 0.38f).coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxSize()
            .background(HorizenNight),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.offset(y = (-18).dp),
        ) {
            HorizenMark(progress = p, modifier = Modifier.size(188.dp))
            Spacer(Modifier.height(26.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .offset(y = ((1f - wordIn) * 10f).dp)
                    .alpha(wordIn),
            ) {
                Text(
                    "hori",
                    color = Color(0xFFF4F0FF),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-1).sp,
                )
                Text(
                    "Z",
                    style = TextStyle(
                        brush = wordmarkGradient,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-1).sp,
                    ),
                )
                Text(
                    "en",
                    color = Color(0xFFF4F0FF),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-1).sp,
                )
            }
        }
    }
}

private val splashEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/**
 * The app plus its splash. The splash runs for at most 1.2s and skips ahead the
 * moment the restored tabs are ready (never less than a beat, so the rings are
 * seen). Hand-over shrinks the logo while the browser rises into place.
 */
@Composable
fun BrowserRoot(vm: BrowserViewModel, onPickAddon: () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    var splashVisible by remember { mutableStateOf(true) }
    val progress = remember { Animatable(0f) }
    val handover = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val start = SystemClock.uptimeMillis()
        if (reduceMotion) {
            progress.snapTo(1f)
        } else {
            launch { progress.animateTo(1f, tween(1200, easing = splashEasing)) }
        }
        while (!vm.ready && SystemClock.uptimeMillis() - start < 1200) delay(30)
        val elapsed = SystemClock.uptimeMillis() - start
        // Give the mark one visible beat, but never hold the browser back.
        if (!reduceMotion && elapsed < 420) delay(420 - elapsed)
        handover.animateTo(1f, tween(if (reduceMotion) 0 else 340, easing = splashEasing))
        splashVisible = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(HorizenNight),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = handover.value
                    val s = 0.965f + 0.035f * handover.value
                    scaleX = s
                    scaleY = s
                    translationY = (1f - handover.value) * 26.dp.toPx()
                    transformOrigin = TransformOrigin(0.5f, 0.42f)
                },
        ) {
            BrowserApp(vm, onPickAddon)
        }
        if (splashVisible) {
            HorizenSplash(
                progress = progress.value,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = (1f - handover.value).coerceIn(0f, 1f)
                        val s = 1f - 0.06f * handover.value
                        scaleX = s
                        scaleY = s
                        translationY = -14.dp.toPx() * handover.value
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    },
            )
        }
    }
}
