package com.example.geckobrowser.ui

import android.app.Dialog
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.geckobrowser.BrowserViewModel

/**
 * Layer 2: hosts the browser chrome in its own window so Android can apply
 * blur-behind to the GeckoView surface underneath. The sheet is drawn inside
 * this window (not behind it), so its text stays sharp while the page blurs.
 *
 * If cross-window blur is unsupported or disabled, callers keep the in-Activity
 * ChromeOverlay and the existing snapshot fallback instead.
 */
@Composable
fun ChromeBlurWindow(
    vm: BrowserViewModel,
    blurRadiusDp: Float,
    chrome: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val savedStateOwner = LocalSavedStateRegistryOwner.current
    val viewModelStoreOwner = LocalViewModelStoreOwner.current
    val chromeState = rememberUpdatedState(chrome)

    DisposableEffect(context, blurRadiusDp) {
        val dialog = Dialog(context)
        val compose = androidx.compose.ui.platform.ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(savedStateOwner)
            setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            setContent { chromeState.value() }
        }
        dialog.setContentView(compose)
        dialog.window?.let { w ->
            w.setDimAmount(0f)
            w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
            if (Build.VERSION.SDK_INT >= 31) {
                // Animate the radius from 0 so the blur doesn't pop in.
                w.setBlurBehindRadius(0)
                w.post { w.setBlurBehindRadius(blurRadiusDp.toInt()) }
            }
        }
        dialog.setOnCancelListener { vm.onBackPressed() }
        dialog.show()
        onDispose {
            runCatching { dialog.dismiss() }
        }
    }
}
