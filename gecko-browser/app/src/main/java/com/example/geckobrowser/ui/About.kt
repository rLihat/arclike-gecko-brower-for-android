package com.example.geckobrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geckobrowser.BrowserViewModel

@Composable
fun AboutScreen(vm: BrowserViewModel, onBack: () -> Unit) {
    val c = LocalArc.current

    Column(
        Modifier
            .fillMaxSize()
            .background(c.settingsBg),
    ) {
        SettingsHeader(
            title = "About horiZen",
            leading = {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(Ico.chevronLeft, c.text, size = 20.dp, strokeWidth = 2.2f) }
            },
            icon = null,
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            HorizenMark(progress = 1f, modifier = Modifier.size(140.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                "horiZen",
                color = c.text,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "horiZen for Android",
                color = c.muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(28.dp))
            SettingsGroup {
                SettingsRow(
                    "Version",
                    icon = { Glyph(Ico.grid, c.text, size = 19.dp, strokeWidth = 1.7f) },
                    value = com.example.geckobrowser.BuildConfig.VERSION_NAME,
                    onClick = null,
                )
                SettingsRow(
                    "GeckoView",
                    icon = { Glyph(Ico.globe, c.text, size = 19.dp, strokeWidth = 1.7f) },
                    value = "138",
                    onClick = null,
                )
                SettingsRow(
                    "Engine",
                    icon = { Glyph(Ico.refresh, c.text, size = 19.dp, strokeWidth = 1.7f) },
                    value = "Firefox / Gecko",
                    onClick = null,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "horiZen is a fast, private browser built on GeckoView — Mozilla's " +
                    "open-source browser engine — with an Arc-inspired chrome, on-device " +
                    "translation and robust tracking protection.",
                color = c.muted,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "Add-ons install as AMO-compatible signed XPIs — the app uses Mozilla's " +
                    "add-on system rather than shipping its own store.",
                color = c.muted,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "© horiZen. GeckoView and Firefox are trademarks of Mozilla Foundation.",
                color = c.muted,
                fontSize = 10.5.sp,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}