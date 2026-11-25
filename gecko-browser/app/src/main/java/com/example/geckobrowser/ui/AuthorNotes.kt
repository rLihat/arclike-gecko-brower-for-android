package com.example.geckobrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geckobrowser.BrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Replaces the old author-note page with a real call to support the project. */
@Composable
fun AuthorNotesScreen(vm: BrowserViewModel, onBack: () -> Unit) {
    val c = LocalArc.current
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val url = withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL("https://api.github.com/users/rLihat").openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", "horiZen-Android")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(body).optString("avatar_url").takeIf { it.isNotBlank() }
            }.getOrNull()
        }
        avatarUrl = url
    }
    Column(Modifier.fillMaxSize().background(c.settingsBg)) {
        SettingsHeader(
            title = "Star horiZen on GitHub",
            leading = {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph(Ico.chevronLeft, c.text, size = 20.dp, strokeWidth = 2.2f)
                }
            },
            icon = { Glyph(Ico.person, c.text, size = 18.dp, strokeWidth = 1.8f) },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 40.dp),
        ) {
            Spacer(Modifier.height(6.dp))
            Text(
                com.example.geckobrowser.BuildConfig.VERSION_NAME,
                color = c.muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 14.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.menuCard)
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AccountAvatar(avatarUrl, "R", 52.dp)
                Spacer(Modifier.height(12.dp))
                Text("rLihat", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Developer by interest.", color = c.muted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "horiZen is built in the open. A star helps others find it, "
                        + "and issues and pull requests are welcome.",
                    color = c.text, fontSize = 12.5.sp, lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            ActionButton("Star horiZen on GitHub") {
                vm.openInNewTab("https://github.com/rLihat/arclike-gecko-brower-for-android")
            }
            Spacer(Modifier.height(10.dp))
            ActionButton("View GitHub profile") { vm.openInNewTab("https://github.com/rLihat") }
            Spacer(Modifier.height(10.dp))
            ActionButton("Open the issue tracker") {
                vm.openInNewTab("https://github.com/rLihat/arclike-gecko-brower-for-android/issues")
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    val c = LocalArc.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(CircleShape)
            .background(c.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
