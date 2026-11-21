package com.example.geckobrowser.ui

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.staticCompositionLocalOf

/** Which blur layers are currently active, for the startup log and diagnostics. */
object GlassLayers {
    private const val TAG = "horiZenGlass"

    /** True when the Compose-side Haze layer is available and enabled. */
    var hazeActive: Boolean = false
        private set

    /** True when cross-window (blur-behind) is supported by this device. */
    var crossWindowBlur: Boolean = false
        private set

    /** The snapshot fallback is always compiled in; this records if it is the active path. */
    var snapshotFallback: Boolean = true
        private set

    private var blurListener: java.util.function.Consumer<Boolean>? = null

    fun configure(context: Context, haze: Boolean, usingSnapshot: Boolean) {
        hazeActive = haze
        snapshotFallback = usingSnapshot
        if (Build.VERSION.SDK_INT >= 31) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            crossWindowBlur = wm?.isCrossWindowBlurEnabled == true
            // Blur can switch off in battery saver / on low-end devices.
            val listener = java.util.function.Consumer { enabled: Boolean ->
                crossWindowBlur = enabled
                logStatus()
            }
            blurListener = listener
            wm?.addCrossWindowBlurEnabledListener(listener)
        } else {
            crossWindowBlur = false
        }
    }

    /** One line on startup: active layer status. Nothing claims device-verified. */
    fun logStatus() {
        Log.i(
            TAG,
            "layers: hazeActive=$hazeActive, crossWindowBlurEnabled=$crossWindowBlur, " +
                "snapshotFallback=$snapshotFallback, api=${Build.VERSION.SDK_INT} " +
                "(not device-verified)",
        )
    }
}

/** True when the app's glass effects should run (all layers honour this). */
val LocalFrost = staticCompositionLocalOf { true }
