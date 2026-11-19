package com.example.geckobrowser.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/* ---------------------------------------------------------------- frosting */

/**
 * The page is drawn by GeckoView into its own SurfaceView, so Compose cannot
 * sample it: `Modifier.blur` would only blur the chrome on top. The only way to
 * frost real page pixels is to snapshot the composited window with PixelCopy,
 * shrink it hard, blur the small copy once, and draw that behind the sheet.
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
 * Snapshots [window] and returns a pre-blurred, heavily downscaled frame, or
 * null when the capture cannot be trusted (PixelCopy failure, all-black
 * SurfaceView readback, timeout). Null callers just keep the plain tint, so a
 * device that refuses the capture never sees a broken sheet.
 */
internal suspend fun captureFrost(window: Window): ImageBitmap? = withTimeoutOrNull(180) {
    val frame = captureWindow(window) ?: return@withTimeoutOrNull null
    val frosted = withContext(Dispatchers.Default) { frost(frame) }
    frame.recycle()
    frosted?.asImageBitmap()
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
                if (result == PixelCopy.SUCCESS) {
                    cont.resume(bitmap)
                } else {
                    bitmap.recycle()
                    cont.resume(null)
                }
            }, handler)
        } catch (t: Throwable) {
            // Unattached window / mismatched buffer / unsupported capture.
            bitmap.recycle()
            cont.resume(null)
        }
        cont.invokeOnCancellation { bitmap.recycle() }
    }
}

/** Downscale + box-blur pass, all on a tiny bitmap so the cost stays ~10ms. */
private fun frost(source: Bitmap): Bitmap? {
    if (looksBlank(source)) return null
    val scale = 340f / maxOf(source.width, source.height)
    if (scale >= 1f) return null
    val w = maxOf(2, (source.width * scale).toInt())
    val h = maxOf(2, (source.height * scale).toInt())
    val small = try {
        Bitmap.createScaledBitmap(source, w, h, true)
    } catch (t: Throwable) {
        return null
    } ?: return null
    val pixels = IntArray(w * h)
    small.getPixels(pixels, 0, w, 0, 0, w, h)
    boxBlur(pixels, w, h, radius = 6, passes = 3)
    val out = try {
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    } catch (t: Throwable) {
        null
    } ?: return null
    out.setPixels(pixels, 0, w, 0, 0, w, h)
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
