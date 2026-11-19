package com.example.geckobrowser.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/* ---------------------------------------------------------------- frosting */

/**
 * GeckoView paints the page into a SurfaceView, outside the Compose layer tree,
 * so `Modifier.blur` cannot touch it. PixelCopy can: it reads back the page
 * surface itself, which is also why capturing the *window* looked black (the
 * SurfaceView is composited separately). Every capture here therefore goes
 * straight to the page surface; the window is only used for the home screen,
 * which Compose draws on top of it.
 */
internal fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Full-page frost for the search/menu sheets. Returns null when there is no
 * trustworthy frame (no page surface, PixelCopy error, timeout), so the caller
 * keeps the plain tint instead of risking a black sheet.
 */
internal suspend fun captureSheetFrost(window: Window, pageVisible: Boolean): ImageBitmap? {
    // Only the readback is time-boxed; the blur runs after it so a slow device
    // loses a frame of glass rather than dropping the frost entirely.
    val frame = withTimeoutOrNull(220) {
        val surface = findPageSurface(window.decorView)
        when {
            pageVisible && surface != null -> captureSurface(surface, null)
            !pageVisible -> captureWindow(window)
            else -> null
        }
    } ?: return null
    // A flat frame is either a blank page or a failed readback; both are better
    // served by the plain tint than by a solid rectangle.
    if (looksBlank(frame)) {
        frame.recycle()
        return null
    }
    val out = withContext(Dispatchers.Default) { frost(frame, maxEdge = 220, radius = 9) }
    frame.recycle()
    return out?.asImageBitmap()
}

/**
 * Snapshot of exactly the pill's rect, so the capsule can frost the page
 * underneath it. Only the small rect is read back, which keeps the periodic
 * refresh cheap.
 */
internal suspend fun capturePillFrost(window: Window, rect: Rect): ImageBitmap? {
    val frame = withTimeoutOrNull(160) {
        val surface = findPageSurface(window.decorView) ?: return@withTimeoutOrNull null
        val loc = IntArray(2)
        surface.getLocationInWindow(loc)
        val left = rect.left.toInt() - loc[0]
        val top = rect.top.toInt() - loc[1]
        val right = rect.right.toInt() - loc[0]
        val bottom = rect.bottom.toInt() - loc[1]
        val src = android.graphics.Rect(
            left.coerceIn(0, surface.width),
            top.coerceIn(0, surface.height),
            right.coerceIn(0, surface.width),
            bottom.coerceIn(0, surface.height),
        )
        // 1:1 only: if the surface cannot supply the whole capsule, skip it.
        if (src.width() < 8 || src.height() < 8) return@withTimeoutOrNull null
        if (src.width() != right - left || src.height() != bottom - top) {
            return@withTimeoutOrNull null
        }
        captureSurface(surface, src)
    } ?: return null
    if (looksBlank(frame)) {
        frame.recycle()
        return null
    }
    val out = withContext(Dispatchers.Default) { frost(frame, maxEdge = 150, radius = 7) }
    frame.recycle()
    return out?.asImageBitmap()
}

private fun findPageSurface(view: View): SurfaceView? {
    if (view is SurfaceView && view.isShown && view.width > 0 && view.height > 0) return view
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            findPageSurface(view.getChildAt(i))?.let { return it }
        }
    }
    return null
}

private suspend fun captureSurface(
    view: SurfaceView,
    srcRect: android.graphics.Rect?,
): Bitmap? {
    val rect = srcRect ?: android.graphics.Rect(0, 0, view.width, view.height)
    if (rect.width() <= 0 || rect.height() <= 0) return null
    val bitmap = try {
        Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
    } catch (t: Throwable) {
        return null
    }
    return suspendCancellableCoroutine<Bitmap?> { cont ->
        val handler = Handler(Looper.getMainLooper())
        try {
            PixelCopy.request(view, rect, bitmap, { result ->
                if (!cont.isActive) {
                    bitmap.recycle()
                    return@request
                }
                if (result == PixelCopy.SUCCESS) {
                    cont.resume(bitmap)
                } else {
                    bitmap.recycle()
                    cont.resume(null)
                }
            }, handler)
        } catch (t: Throwable) {
            bitmap.recycle()
            cont.resume(null)
        }
        cont.invokeOnCancellation { bitmap.recycle() }
    }
}

private suspend fun captureWindow(window: Window): Bitmap? {
    val decor = window.decorView
    val width = decor.width
    val height = decor.height
    if (width <= 0 || height <= 0) return null
    val bitmap = try {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    } catch (t: Throwable) {
        return null
    }
    return suspendCancellableCoroutine<Bitmap?> { cont ->
        val handler = Handler(Looper.getMainLooper())
        try {
            PixelCopy.request(window, bitmap, { result ->
                if (!cont.isActive) {
                    bitmap.recycle()
                    return@request
                }
                if (result == PixelCopy.SUCCESS && !looksBlank(bitmap)) {
                    cont.resume(bitmap)
                } else {
                    bitmap.recycle()
                    cont.resume(null)
                }
            }, handler)
        } catch (t: Throwable) {
            bitmap.recycle()
            cont.resume(null)
        }
        cont.invokeOnCancellation { bitmap.recycle() }
    }
}

/** Downscale + box-blur pass, all on a tiny bitmap so the cost stays ~10ms. */
private fun frost(source: Bitmap, maxEdge: Int, radius: Int): Bitmap? {
    val scale = maxEdge.toFloat() / maxOf(source.width, source.height)
    val w = maxOf(2, (source.width * scale).toInt())
    val h = maxOf(2, (source.height * scale).toInt())
    val small = if (scale < 1f) {
        try {
            Bitmap.createScaledBitmap(source, w, h, true)
        } catch (t: Throwable) {
            return null
        } ?: return null
    } else {
        source
    }
    val pixels = IntArray(small.width * small.height)
    small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
    boxBlur(pixels, small.width, small.height, radius, passes = 3)
    val out = try {
        Bitmap.createBitmap(small.width, small.height, Bitmap.Config.ARGB_8888)
    } catch (t: Throwable) {
        null
    } ?: return null
    out.setPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
    if (small !== source) small.recycle()
    return out
}

/**
 * Some devices hand back an all-black frame for SurfaceView content. Rejecting
 * a flat capture keeps the fallback instead of painting a black rectangle.
 */
private fun looksBlank(bitmap: Bitmap): Boolean {
    val stepX = maxOf(1, bitmap.width / 12)
    val stepY = maxOf(1, bitmap.height / 12)
    var min = 255
    var max = 0
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val p = bitmap.getPixel(x, y)
            val lum = (((p shr 16) and 0xFF) * 77 + ((p shr 8) and 0xFF) * 150 +
                (p and 0xFF) * 29) shr 8
            if (lum < min) min = lum
            if (lum > max) max = lum
            x += stepX
        }
        y += stepY
    }
    return max - min < 10
}

private fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int) {
    val scratch = IntArray(pixels.size)
    repeat(passes) {
        blurPass(pixels, scratch, width, height, radius, horizontal = true)
        blurPass(scratch, pixels, width, height, radius, horizontal = false)
    }
}

private fun blurPass(
    src: IntArray,
    dst: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean,
) {
    val span = radius * 2 + 1
    if (horizontal) {
        for (y in 0 until height) {
            val row = y * width
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val p = src[row + i.coerceIn(0, width - 1)]
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
            }
            for (x in 0 until width) {
                dst[row + x] = (0xFF shl 24) or ((r / span) shl 16) or
                    ((g / span) shl 8) or (b / span)
                val out = src[row + (x - radius).coerceIn(0, width - 1)]
                val inn = src[row + (x + radius + 1).coerceIn(0, width - 1)]
                r += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                g += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                b += (inn and 0xFF) - (out and 0xFF)
            }
        }
    } else {
        for (x in 0 until width) {
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val p = src[i.coerceIn(0, height - 1) * width + x]
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
            }
            for (y in 0 until height) {
                dst[y * width + x] = (0xFF shl 24) or ((r / span) shl 16) or
                    ((g / span) shl 8) or (b / span)
                val out = src[(y - radius).coerceIn(0, height - 1) * width + x]
                val inn = src[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                r += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                g += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                b += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }
}
