package com.example.geckobrowser.ui

import android.os.Build
import android.util.Log
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

    fun configure(haze: Boolean, windowBlur: Boolean, usingSnapshot: Boolean) {
        hazeActive = haze
        crossWindowBlur = windowBlur
        snapshotFallback = usingSnapshot
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
