package com.example.geckobrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geckobrowser.BrowserViewModel
import com.example.geckobrowser.R

@Composable
fun AuthorNotesScreen(vm: BrowserViewModel, onBack: () -> Unit) {
    val c = LocalArc.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val notes = remember {
        runCatching {
            context.resources.openRawResource(R.raw.author_notes).bufferedReader().use { it.readText() }
        }.getOrDefault("No author notes yet.")
    }

    Column(Modifier.fillMaxSize().background(c.settingsBg)) {
        SettingsHeader(
            title = "Author notes",
            leading = {
                Box(Modifier.size(34.dp).clip(CircleShape).clickable { onBack() },
                    contentAlignment = Alignment.Center) {
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
            rawMarkdown(notes)
        }
    }
}
