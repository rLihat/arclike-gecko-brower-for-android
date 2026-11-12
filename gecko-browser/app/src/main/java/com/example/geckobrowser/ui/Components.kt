package com.example.geckobrowser.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ------------------------------------------------------------------ icons */

private fun p(block: Path.() -> Unit) = Path().apply(block)

private val IcoPlus: List<Path> = listOf(
    p {
        moveTo(12f, 5f); lineTo(12f, 19f)
        moveTo(5f, 12f); lineTo(19f, 12f)
    }
)
private val IcoChevronUp: List<Path> = listOf(
    p { moveTo(6f, 14f); lineTo(12f, 8f); lineTo(18f, 14f) }
)
private val IcoChevronLeft: List<Path> = listOf(
    p { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
)
private val IcoChevronRight: List<Path> = listOf(
    p { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
)
private val IcoClose: List<Path> = listOf(
    p {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }
)
private val IcoBack: List<Path> = listOf(
    p { moveTo(15f, 5f); lineTo(7f, 12f); lineTo(15f, 19f) }
)
private val IcoForward: List<Path> = listOf(
    p { moveTo(9f, 5f); lineTo(17f, 12f); lineTo(9f, 19f) }
)
private val IcoRefresh: List<Path> = listOf(
    p {
        addArc(Rect(3.6f, 3.6f, 20.4f, 20.4f), 0f, -300f)
        moveTo(20f, 3.5f); lineTo(20f, 9f); lineTo(14.5f, 9f)
    }
)
private val IcoCopy: List<Path> = listOf(
    p {
        moveTo(10.5f, 13.5f)
        arcTo(Rect(8f, 11f, 20f, 23f), 180f, -90f, false)
        lineTo(16f, 4f)
        arcTo(Rect(4f, 2f, 12f, 10f), 270f, -90f, false)
        lineTo(13f, 3.5f)
    }
)
private val IcoGlobe: List<Path> = listOf(
    p {
        moveTo(12f, 3.5f)
        arcTo(Rect(3.5f, 3.5f, 20.5f, 20.5f), 180f, -360f, false)
        moveTo(3.5f, 12f); lineTo(20.5f, 12f)
        moveTo(12f, 3.5f)
        cubicTo(14.6f, 5.9f, 15.9f, 8.9f, 15.9f, 12f)
        cubicTo(15.9f, 15.1f, 14.6f, 18.1f, 12f, 20.5f)
        cubicTo(9.4f, 18.1f, 8.1f, 15.1f, 8.1f, 12f)
        cubicTo(8.1f, 8.9f, 9.4f, 5.9f, 12f, 3.5f)
        close()
    }
)
private val IcoTabs: List<Path> = listOf(
    p {
        moveTo(8.5f, 7.5f)
        lineTo(18f, 7.5f)
        arcTo(Rect(18f, 7.5f, 21f, 10.5f), 270f, -90f, false)
        lineTo(21f, 17f)
        arcTo(Rect(18f, 17f, 21f, 20f), 0f, -90f, false)
        lineTo(11.5f, 20f)
        arcTo(Rect(8.5f, 17f, 11.5f, 20f), 90f, -90f, false)
        lineTo(8.5f, 10.5f)
        arcTo(Rect(8.5f, 7.5f, 11.5f, 10.5f), 180f, -90f, false)
        moveTo(5.5f, 4.5f); lineTo(12f, 4.5f)
    }
)
private val IcoShare: List<Path> = listOf(
    p {
        moveTo(12f, 15f); lineTo(12f, 4f)
        moveTo(8f, 8f); lineTo(12f, 4f); lineTo(16f, 8f)
        moveTo(5f, 13f); lineTo(5f, 19f)
        lineTo(19f, 19f); lineTo(19f, 13f)
    }
)
private val IcoTranslate: List<Path> = listOf(
    p {
        moveTo(4f, 5f); lineTo(13f, 5f)
        moveTo(8.5f, 3f); lineTo(8.5f, 5f)
        moveTo(6f, 5f)
        cubicTo(6f, 8.8f, 4.5f, 12f, 2.4f, 13.8f)
        moveTo(10.2f, 5f)
        cubicTo(10.2f, 8.4f, 12f, 11.3f, 14.6f, 12.9f)
        moveTo(13f, 20f); lineTo(16.4f, 12f); lineTo(19.8f, 20f)
        moveTo(14.2f, 17.2f); lineTo(18.6f, 17.2f)
    }
)
private val IcoFind: List<Path> = listOf(
    p {
        moveTo(11f, 5f)
        arcTo(Rect(5f, 5f, 17f, 17f), 180f, -360f, false)
        moveTo(20f, 20f); lineTo(15.6f, 15.6f)
    }
)
private val IcoPuzzle: List<Path> = listOf(
    p {
        moveTo(10f, 4.5f)
        arcTo(Rect(10f, 2.5f, 14f, 6.5f), 180f, -180f, false)
        lineTo(14f, 6f)
        lineTo(17.5f, 6f)
        lineTo(17.5f, 10f)
        lineTo(19.5f, 10f)
        arcTo(Rect(17.5f, 10f, 21.5f, 14f), 270f, 180f, false)
        lineTo(17.5f, 14f)
        lineTo(17.5f, 18f)
        lineTo(14f, 18f)
        lineTo(14f, 19.5f)
        arcTo(Rect(10f, 17.5f, 14f, 21.5f), 0f, 180f, false)
        lineTo(10f, 18f)
        lineTo(6.5f, 18f)
        lineTo(6.5f, 14f)
        lineTo(4.5f, 14f)
        arcTo(Rect(2.5f, 10f, 6.5f, 14f), 90f, 180f, false)
        lineTo(6.5f, 10f)
        lineTo(6.5f, 6f)
        lineTo(10f, 6f)
        close()
    }
)
private val IcoMoon: List<Path> = listOf(
    p {
        moveTo(20f, 14.5f)
        arcTo(Rect(4f, 4f, 20f, 20f), 0f, -130f, false)
        arcTo(Rect(4f, 4f, 20f, 20f), 130f, 100f, false)
        close()
    }
)
private val IcoTextSize: List<Path> = listOf(
    p {
        moveTo(4f, 19f); lineTo(9.5f, 5f); lineTo(15f, 19f)
        moveTo(6.2f, 14.5f); lineTo(12.8f, 14.5f)
        moveTo(16f, 19f); lineTo(19f, 11f); lineTo(22f, 19f)
        moveTo(16.9f, 16.6f); lineTo(21.1f, 16.6f)
    }
)
private val IcoDownload: List<Path> = listOf(
    p {
        moveTo(12f, 4f); lineTo(12f, 14f)
        moveTo(8f, 10f); lineTo(12f, 14f); lineTo(16f, 10f)
        moveTo(4f, 19f); lineTo(20f, 19f)
    }
)
private val IcoGrid: List<Path> = listOf(
    p {
        moveTo(5.5f, 4f); lineTo(10.5f, 4f); lineTo(10.5f, 11f); lineTo(5.5f, 11f); close()
        moveTo(13.5f, 4f); lineTo(18.5f, 4f); lineTo(18.5f, 11f); lineTo(13.5f, 11f); close()
        moveTo(5.5f, 13f); lineTo(10.5f, 13f); lineTo(10.5f, 20f); lineTo(5.5f, 20f); close()
        moveTo(13.5f, 13f); lineTo(18.5f, 13f); lineTo(18.5f, 20f); lineTo(13.5f, 20f); close()
    }
)
private val IcoLink: List<Path> = listOf(
    p {
        moveTo(10.5f, 13.5f); lineTo(13.5f, 10.5f)
        moveTo(9f, 15f)
        lineTo(6.5f, 17.5f)
        arcTo(Rect(3f, 14f, 10f, 21f), 180f, -90f, false)
        lineTo(9f, 15f)
        moveTo(15f, 9f)
        lineTo(17.5f, 6.5f)
        arcTo(Rect(14f, 3f, 21f, 10f), 0f, -90f, false)
        lineTo(15f, 9f)
    }
)
private val IcoTrash: List<Path> = listOf(
    p {
        moveTo(4f, 7f); lineTo(20f, 7f)
        moveTo(9f, 7f); lineTo(9f, 5f); lineTo(15f, 5f); lineTo(15f, 7f)
        moveTo(6.5f, 7f); lineTo(7.5f, 19f); lineTo(16.5f, 19f); lineTo(17.5f, 7f)
    }
)
private val IcoLock: List<Path> = listOf(
    p {
        moveTo(5f, 10f); lineTo(19f, 10f); lineTo(19f, 20f); lineTo(5f, 20f); close()
        moveTo(8f, 10f); lineTo(8f, 7f)
        arcTo(Rect(4f, 3f, 12f, 11f), 180f, -180f, false)
        lineTo(8f, 10f)
    }
)
private val IcoPerson: List<Path> = listOf(
    p {
        moveTo(12f, 4f)
        arcTo(Rect(8f, 4f, 16f, 12f), 180f, -360f, false)
        moveTo(4.5f, 20f)
        cubicTo(4.5f, 15.6f, 8f, 13f, 12f, 13f)
        cubicTo(16f, 13f, 19.5f, 15.6f, 19.5f, 20f)
    }
)
private val IcoPin: List<Path> = listOf(
    p {
        moveTo(12f, 21f)
        cubicTo(12f, 21f, 5.5f, 15f, 5.5f, 10.5f)
        arcTo(Rect(5.5f, 4f, 18.5f, 17f), 180f, 180f, false)
        cubicTo(18.5f, 15f, 12f, 21f, 12f, 21f)
        close()
    }
)

@Composable
fun Glyph(
    paths: List<Path>,
    tint: Color,
    size: Dp = 22.dp,
    strokeWidth: Float = 2.2f,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        withTransform({ scale(k, k, Offset.Zero) }) {
            paths.forEach {
                drawPath(
                    it, tint,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

object Ico {
    val plus = IcoPlus
    val chevronUp = IcoChevronUp
    val chevronLeft = IcoChevronLeft
    val chevronRight = IcoChevronRight
    val close = IcoClose
    val back = IcoBack
    val forward = IcoForward
    val refresh = IcoRefresh
    val copy = IcoCopy
    val globe = IcoGlobe
    val tabs = IcoTabs
    val share = IcoShare
    val translate = IcoTranslate
    val find = IcoFind
    val puzzle = IcoPuzzle
    val moon = IcoMoon
    val textSize = IcoTextSize
    val download = IcoDownload
    val grid = IcoGrid
    val link = IcoLink
    val trash = IcoTrash
    val lock = IcoLock
    val person = IcoPerson
    val pin = IcoPin
}

/* --------------------------------------------------------------- switches */

@Composable
fun ArcSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalArc.current
    val x by animateDpAsState(if (checked) 18.dp else 0.dp, tween(180), label = "knob")
    Box(
        Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(CircleShape)
            .background(if (checked) c.accent else c.switchOff)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = 3.dp)
                .offset(x = x)
                .size(20.dp)
                .clip(CircleShape)
                .background(c.knob)
        )
    }
}

/* -------------------------------------------------------------- settings */

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val c = LocalArc.current
    Text(
        text,
        modifier = modifier.padding(start = 4.dp, top = 18.dp, bottom = 7.dp),
        color = c.muted,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.3.sp,
    )
}

@Composable
fun SettingsRow(
    label: String,
    icon: (@Composable () -> Unit)? = null,
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalArc.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.width(34.dp), contentAlignment = Alignment.CenterStart) {
                Box(
                    Modifier.size(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
            }
        }
        Text(
            label,
            color = c.text,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(value, color = c.muted, fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.width(4.dp))
        }
        if (trailing != null) {
            Box(Modifier.padding(start = 8.dp)) { trailing() }
        } else if (onClick != null) {
            Glyph(Ico.chevronRight, c.muted, size = 16.dp, strokeWidth = 1.9f)
        }
    }
}

@Composable
fun SettingsGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = LocalArc.current
    androidx.compose.foundation.layout.Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .padding(vertical = 2.dp),
        content = content,
    )
}

/* ------------------------------------------------------------------ pill */

@Composable
fun PillButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = LocalArc.current
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The pulsing ring drawn around the nav pill while a page loads. */
@Composable
fun LoadingGlow(active: Boolean, radius: Dp, modifier: Modifier = Modifier) {
    val c = LocalArc.current
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1150, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        modifier.drawBehind {
            if (!active) return@drawBehind
            val base = c.glow.copy(alpha = 0.18f + 0.42f * pulse)
            for (i in 0..3) {
                val grow = i * 3.dp.toPx()
                drawRoundRect(
                    color = base.copy(alpha = base.alpha / (i + 1)),
                    topLeft = Offset(-grow, -grow),
                    size = androidx.compose.ui.geometry.Size(size.width + grow * 2, size.height + grow * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx() + grow),
                    style = Stroke(width = 1.4.dp.toPx()),
                )
            }
        }
    )
}

/** Small letter tile used as the site favicon. */
@Composable
fun FaviconTile(letter: String, size: Dp = 22.dp, background: Color? = null) {
    val c = LocalArc.current
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(background ?: c.chip)
            .border(1.dp, c.stroke, RoundedCornerShape(size / 3)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter.uppercase().take(1),
            color = c.accent,
            fontSize = (size.value * 0.5f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun PillAvatar(text: String = "M", size: Dp = 26.dp) {
    val c = LocalArc.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (c.dark) Color(0xFF2E363B) else Color(0xFFE4E4E1)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = c.accent, fontSize = (size.value * 0.44f).sp, fontWeight = FontWeight.SemiBold)
    }
}
