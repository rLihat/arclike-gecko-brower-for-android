package com.example.geckobrowser

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.geckobrowser.ui.BrowserRoot
import com.example.geckobrowser.ui.GlassLayers

class MainActivity : ComponentActivity() {

    private val vm: BrowserViewModel by viewModels()

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* granted or not; notifier simply skips when denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash holds the first frame; keep it only long enough for
        // the restored tab list to exist, then hand over to the Compose splash.
        val splash = installSplashScreen()
        val splashStartedAt = SystemClock.uptimeMillis()
        super.onCreate(savedInstanceState)
        (application as ArcApplication).browserVm = vm
        // Layer status is logged once; nothing here claims device verification.
        GlassLayers.configure(
            context = this,
            haze = true,
            usingSnapshot = true,
        )
        GlassLayers.logStatus()
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        splash.setKeepOnScreenCondition {
            !vm.ready && SystemClock.uptimeMillis() - splashStartedAt < 900
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        if (savedInstanceState == null) vm.handleIntent(intent)

        setContent {
            val addonPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri?.let { vm.installExtension(it) }
            }

            // The document picker answers one GeckoView file prompt at a time.
            var pickingId by remember { mutableStateOf<Long?>(null) }
            val filePicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                val id = pickingId
                pickingId = null
                if (id != null) vm.answerFilePick(id, uri)
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { result ->
                vm.onAndroidPermissionsResult(result)
            }

            LaunchedEffect(vm.filePickerRequest?.id) {
                val request = vm.filePickerRequest ?: return@LaunchedEffect
                pickingId = request.id
                filePicker.launch(request.mimeTypes.toTypedArray())
            }

            LaunchedEffect(vm.androidPermissionRequest?.id) {
                val request = vm.androidPermissionRequest ?: return@LaunchedEffect
                permissionLauncher.launch(request.permissions.toTypedArray())
            }

            // Fullscreen video gets the whole screen and may turn; leaving it
            // hands the phone back to the portrait chrome.
            // Screenshots stay blocked unless the user opts in; this covers
            // private tabs and the rest of the app alike.
            LaunchedEffect(vm.allowPrivateScreenshots) {
                if (vm.allowPrivateScreenshots) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            LaunchedEffect(vm.fullscreen) {
                if (vm.fullscreen) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            BrowserRoot(vm, onPickAddon = {
                addonPicker.launch(
                    arrayOf(
                        "application/x-xpinstall",
                        "application/octet-stream",
                        "application/zip",
                        "*/*"
                    )
                )
            }, onExit = { finish() })

            BackHandler {
                val consumed = vm.onBackPressed()
                if (!consumed) {
                    val tab = vm.activeTab
                    if (tab != null && tab.canGoBack) {
                        vm.goBack()
                    } else {
                        vm.requestExitWithPrivateTabs()
                        if (!vm.exitConfirm) finish()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.handleIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        // A paused app can't hold a page in fullscreen; the chrome returns and
        // the page gets its normal viewport back. A rotation is not a pause:
        // fullscreen is allowed to turn with the phone.
        if (!isChangingConfigurations) vm.onActivityPause()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        vm.refreshDefaultBrowser()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
