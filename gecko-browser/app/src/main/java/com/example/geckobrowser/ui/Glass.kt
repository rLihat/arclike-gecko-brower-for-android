package com.example.geckobrowser.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Live frosted glass for the floating chrome cards (prompts, find bar, toast,
 * OTA banner, download chip). Add the returned modifier after the card's own
 * background and before its border: the blurred page pixels are drawn over the
 * flat fallback fill, then the tint, with the card's existing clip and border
 * untouched. A failed or blank readback leaves the original fill in place.
 *
 * Each card owns a small reusable readback buffer; [readbackMutex] keeps the
 * captures from overlapping, and the loops stop with the composable.
 */
@Composable
fun rememberGlass(
    tint: Color,
    fps: Int = 20,
    maxEdge: Int = 130,
    radius: Int = 5,
): Modifier {
    val context = LocalContext.current
    val bounds = remember { mutableStateOf<Rect?>(null) }
    val frost = remember { mutableStateOf<ImageBitmap?>(null) }
    val scratch = remember { FrostScratch() }

    LaunchedEffect(fps, maxEdge, radius) {
        val window = context.findActivity()?.window ?: return@LaunchedEffect
        val interval = 1_000_000_000L / fps.coerceAtLeast(1)
        var last = 0L
        try {
            while (true) {
                var due = false
                withFrameNanos { now ->
                    if (now - last >= interval) {
                        last = now
                        due = true
                    }
                }
                val rect = bounds.value
                if (due && rect != null) {
                    captureAreaFrost(window, rect, scratch, maxEdge, radius)
                        ?.let { frost.value = it }
                }
            }
        } finally {
            frost.value = null
            scratch.release()
        }
    }

    // Read in composition so a new frame produces a new draw modifier (and a
    // redraw); reading the snapshot only inside the draw lambda would not
    // invalidate the node on its own.
    val bitmap = frost.value
    return Modifier
        .onGloballyPositioned { bounds.value = it.boundsInWindow() }
        .drawBehind {
            if (bitmap == null) return@drawBehind
            val scale = maxOf(size.width / bitmap.width, size.height / bitmap.height)
            val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
            drawImage(
                image = bitmap,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstSize = IntSize(w, h),
                dstOffset = IntOffset(((size.width - w) / 2f).toInt(), ((size.height - h) / 2f).toInt()),
            )
            drawRect(tint)
        }
}
