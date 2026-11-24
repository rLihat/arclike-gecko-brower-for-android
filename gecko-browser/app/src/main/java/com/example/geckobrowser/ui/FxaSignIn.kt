package com.example.geckobrowser.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.geckobrowser.BrowserViewModel

/**
 * The FxA OAuth flow runs in an in-app WebView so the redirect is intercepted
 * directly. A Custom Tab cannot hand `accounts.firefox.com` back to us — that
 * is why the previous sign-in never completed.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FxaSignInDialog(vm: BrowserViewModel) {
    val url = vm.fxaAuthUrl ?: return
    val c = LocalArc.current
    Dialog(
        onDismissRequest = { vm.fxaAuthUrl = null },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(c.sheet),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Mozilla account", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Cancel",
                    color = c.accent,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { vm.fxaAuthUrl = null },
                )
            }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        webViewClient = object : WebViewClient() {
                            private fun complete(u: String?): Boolean {
                                if (u == null || !u.contains("/oauth/success/")) return false
                                val parsed = Uri.parse(u)
                                val code = parsed.getQueryParameter("code")
                                val state = parsed.getQueryParameter("state")
                                if (code == null || state == null) return false
                                vm.completeFxaSignIn(code, state)
                                return true
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean = complete(request.url.toString())

                            @Deprecated("Deprecated for old platforms")
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                                complete(url)
                        }
                        loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
