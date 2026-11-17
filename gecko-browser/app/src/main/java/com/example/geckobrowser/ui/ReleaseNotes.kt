package com.example.geckobrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geckobrowser.BrowserViewModel
import com.example.geckobrowser.R
import com.example.geckobrowser.ui.LocalArc

@Composable
fun ReleaseNotesScreen(vm: BrowserViewModel, onBack: () -> Unit) {
    val c = LocalArc.current
    val context = LocalContext.current
    val notes = remember {
        runCatching {
            context.resources.openRawResource(R.raw.release_notes).bufferedReader().use { it.readText() }
        }.getOrDefault("No release notes were bundled with this build.")
    }

    Column(Modifier.fillMaxSize().background(c.settingsBg)) {
        SettingsHeader(
            title = "What's new",
            leading = {
                Box(Modifier.size(34.dp).clip(CircleShape).clickable { onBack() },
                    contentAlignment = Alignment.Center) {
                    Glyph(Ico.chevronLeft, c.text, size = 20.dp, strokeWidth = 2.2f)
                }
            },
            icon = { Glyph(Ico.refresh, c.text, size = 18.dp, strokeWidth = 1.8f) },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            Spacer(Modifier.height(6.dp))
            Text(
                com.example.geckobrowser.BuildConfig.VERSION_NAME,
                color = c.muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 14.dp),
            )

            rawMarkdown(notes)
        }
    }
}

@Composable
internal fun ColumnScope.rawMarkdown(raw: String) {
    val c = LocalArc.current
    raw.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.startsWith("# ") -> Text(
                line.removePrefix("# "),
                color = c.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            line.startsWith("## ") -> Text(
                line.removePrefix("## "),
                color = c.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp, start = 2.dp),
            )
            line.startsWith("- ") -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 7.dp, start = 2.dp),
            ) {
                Text("•", color = c.accent, fontSize = 15.sp, modifier = Modifier.width(16.dp))
                Text(line.removePrefix("- "), color = c.text, fontSize = 13.sp, lineHeight = 18.sp)
            }
            line.isNotEmpty() -> Text(
                line,
                color = c.text,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )
        }
    }
}
