package com.example.geckobrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geckobrowser.BrowserViewModel
import com.example.geckobrowser.ExtensionItem

/**
 * A small sheet with the currently enabled add-ons. Tapping one simulates its
 * toolbar button, which either opens the add-on's own menu (e.g. uBlock
 * Origin's) or its options page when it has no popup.
 */
@Composable
fun ExtensionMenuSheet(vm: BrowserViewModel) {
    val c = LocalArc.current
    val enabled = vm.extensions.filter { it.enabled }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.sheetSoft)
            .padding(start = 16.dp, end = 16.dp, bottom = 92.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp)
                .swipeActions(onDown = { vm.closeExtensionMenu() }),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(42.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.handle))
        }

        Text(
            "EXTENSIONS",
            color = c.muted,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 6.dp),
        )

        if (enabled.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No enabled extensions",
                    color = c.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Browse the Firefox add-ons store to add one.",
                    color = c.muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(12.dp))
                StoreButton(
                    label = "Visit Store",
                    primary = true,
                    onClick = { vm.visitExtensionStore() },
                )
            }
            return@Column
        }

        LazyColumn(Modifier.weight(1f)) {
            items(enabled, key = { it.id }) { item ->
                ExtensionMenuRow(vm, item)
            }
        }

        Spacer(Modifier.height(12.dp))
        StoreButton(
            label = "Manage extensions",
            primary = true,
            onClick = { vm.openExtensions() },
        )
    }
}

@Composable
private fun ExtensionMenuRow(vm: BrowserViewModel, item: ExtensionItem) {
    val c = LocalArc.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { vm.clickExtension(item) }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.chip),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(Ico.puzzle, c.accent, size = 18.dp, strokeWidth = 1.8f)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "v${item.version.ifBlank { "1.0" }}" +
                    (item.optionsPageUrl.takeIf { it.isNotBlank() }?.let { " · configurable" } ?: ""),
                color = c.muted,
                fontSize = 11.sp,
            )
        }
        if (item.optionsPageUrl.isNotBlank()) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { vm.openExtensionOptions(item) },
                contentAlignment = Alignment.Center,
            ) {
                Glyph(Ico.grid, c.muted, size = 17.dp, strokeWidth = 1.8f)
            }
        }
        Glyph(Ico.chevronRight, c.muted, size = 16.dp, strokeWidth = 1.9f)
    }
}

@Composable
private fun StoreButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val c = LocalArc.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) c.accent else c.chip)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) c.settingsBg else c.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}