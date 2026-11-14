package com.example.geckobrowser

import android.content.Intent
import android.os.Bundle
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
import com.example.geckobrowser.ui.BrowserApp

class MainActivity : ComponentActivity() {

    private val vm: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

            BrowserApp(vm) {
                addonPicker.launch(
                    arrayOf(
                        "application/x-xpinstall",
                        "application/octet-stream",
                        "application/zip",
                        "*/*"
                    )
                )
            }

            BackHandler {
                val consumed = vm.onBackPressed()
                if (!consumed) {
                    val tab = vm.activeTab
                    if (tab != null && tab.canGoBack) vm.goBack() else finish()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
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
