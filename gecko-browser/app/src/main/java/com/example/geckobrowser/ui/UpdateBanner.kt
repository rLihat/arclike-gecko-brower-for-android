package com.example.geckobrowser.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geckobrowser.BrowserViewModel
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Banner shown under the notch/status bar when a newer rolling release is available.
 * Shows the branch and version, with Download / Ignore / Release-details actions.
 */
@Composable
fun UpdateBanner(vm: BrowserViewModel) {
    val c = LocalArc.current
    val info = vm.updateInfo ?: return
    val progress = vm.updateProgress
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(info.runNumber) {
        if (vm.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Column(
        Modifier
            .fillMaxWidth(0.92f)
            .shadow(18.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .then(rememberGlass(c.surface, fps = 15))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.chip),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(Ico.download, c.accent, size = 16.dp, strokeWidth = 2f)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(info.displayName, color = c.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (progress >= 0f) "Downloading update…" else "New release available",
                    color = c.muted,
                    fontSize = 10.5.sp,
                )
            }
            if (progress >= 0f) {
                Text(
                    "${(progress * 100).toInt()}%",
                    color = c.muted,
                    fontSize = 11.sp,
                )
            }
        }
        if (progress >= 0f) {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .height(4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.chip),
            ) {
                val clamped = progress.coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth(clamped)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(c.accent),
                )
            }
        } else {
            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BannerAction("Download", primary = true) { vm.downloadAndInstallUpdate() }
                BannerAction("Ignore", primary = false) { vm.ignoreUpdate() }
                BannerAction("Release details", primary = false) {
                    vm.submit(info.releaseUrl)
                    vm.ignoreUpdate()
                }
            }
        }
    }
}

@Composable
private fun BannerAction(label: String, primary: Boolean, onClick: () -> Unit) {
    val c = LocalArc.current
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) c.accent else c.chip)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) c.settingsBg else c.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
