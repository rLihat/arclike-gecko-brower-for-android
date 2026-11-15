package com.example.geckobrowser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geckobrowser.BrowserViewModel
import com.example.geckobrowser.Visit
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val deleteRed = Color(0xFFE0453C)

/**
 * Local history: grouped by day, newest first, filterable, swipe a row left to
 * delete it and clear by range from the header. Rows open in the active tab.
 */
@Composable
fun HistoryScreen(vm: BrowserViewModel, onBack: () -> Unit) {
    val c = LocalArc.current
    var query by remember { mutableStateOf("") }
    var clearing by remember { mutableStateOf(false) }
    // Read the revision so a visit recorded while the page is open refreshes it.
    val version = vm.historyVersion
    val rows = remember(query, version) { vm.historyItems(query) }
    val groups = remember(rows) { groupByDay(rows) }
    val hasHistory = remember(version) { vm.historyCount > 0 }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.settingsBg),
    ) {
        SettingsHeader(
            title = "History",
            leading = {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(Ico.chevronLeft, c.text, size = 20.dp, strokeWidth = 2.2f) }
            },
            icon = { Glyph(Ico.clock, c.text, size = 19.dp, strokeWidth = 1.9f) },
            trailing = {
                if (hasHistory) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { clearing = !clearing }
                            .padding(horizontal = 9.dp, vertical = 7.dp),
                    ) {
                        Text(
                            if (clearing) "Done" else "Clear",
                            color = c.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            },
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp)
                .height(42.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.surface)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(Ico.find, c.muted, size = 16.dp, strokeWidth = 2f)
            Spacer(Modifier.width(9.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text("Search history", color = c.muted, fontSize = 13.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 13.sp),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).clickable { query = "" },
                    contentAlignment = Alignment.Center,
                ) { Glyph(Ico.close, c.muted, size = 12.dp, strokeWidth = 2.2f) }
            }
        }

        if (clearing && hasHistory) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.surface)
                    .border(1.dp, c.stroke, RoundedCornerShape(16.dp))
                    .padding(6.dp),
            ) {
                ClearOption("Last hour") {
                    vm.clearHistory(System.currentTimeMillis() - 3_600_000L)
                    clearing = false
                }
                ClearOption("Today") {
                    vm.clearHistory(startOfDay(System.currentTimeMillis()))
                    clearing = false
                }
                ClearOption("All history", destructive = true) {
                    vm.clearHistory(0L)
                    clearing = false
                }
            }
        }

        Text(
            when {
                rows.isEmpty() && query.isNotBlank() -> "No matches"
                rows.isEmpty() -> ""
                rows.size == 1 -> "1 page"
                else -> "${rows.size} pages"
            },
            color = c.muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp),
        )

        if (rows.isEmpty()) {
            EmptyHistory(query.isNotBlank())
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 40.dp),
            ) {
                groups.forEach { (label, visits) ->
                    item(key = "day-$label") {
                        Text(
                            label.uppercase(Locale.getDefault()),
                            color = c.muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
                        )
                    }
                    items(visits, key = { it.id }) { visit ->
                        HistoryRow(
                            vm = vm,
                            visit = visit,
                            showTime = label == "Today" || label == "Yesterday",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClearOption(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val c = LocalArc.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            color = if (destructive) deleteRed else c.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EmptyHistory(filtered: Boolean) {
    val c = LocalArc.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(c.chip),
            contentAlignment = Alignment.Center,
        ) { Glyph(Ico.clock, c.muted, size = 24.dp, strokeWidth = 1.8f) }
        Spacer(Modifier.height(14.dp))
        Text(
            if (filtered) "No history matches that" else "No history yet",
            color = c.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (filtered) "Try a word from the page title or the address."
            else "Pages you visit show up here, newest first.",
            color = c.muted,
            fontSize = 12.5.sp,
        )
    }
}

@Composable
private fun HistoryRow(vm: BrowserViewModel, visit: Visit, showTime: Boolean) {
    val c = LocalArc.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // Drag follows the finger; `dismiss` flies the row out once it is past the
    // threshold, then the row is deleted from the store.
    var drag by remember(visit.id) { mutableFloatStateOf(0f) }
    val dismiss = remember(visit.id) { Animatable(0f) }
    val threshold = with(density) { 64.dp.toPx() }

    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(14.dp))
                .background(deleteRed)
                .padding(end = 18.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Glyph(Ico.trash, Color.White, size = 18.dp, strokeWidth = 1.9f)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = drag - size.width * dismiss.value }
                .clip(RoundedCornerShape(14.dp))
                .background(if (c.dark) c.surfaceStrong else Color.White)
                .pointerInput(visit.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            if (dismiss.value == 0f) drag = (drag + amount).coerceIn(-160f, 0f)
                        },
                        onDragEnd = {
                            if (drag <= -threshold) {
                                scope.launch {
                                    dismiss.animateTo(1f, tween(220))
                                    vm.deleteVisit(visit.id)
                                }
                            } else {
                                drag = 0f
                            }
                        },
                        onDragCancel = { drag = 0f },
                    )
                }
                .clickable { vm.openHistoryUrl(visit.url) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SiteFavicon(visit.host, visit.label.take(1), 24.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    visit.label,
                    color = c.text,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (showTime) "${visit.host} · ${timeOf(visit.lastVisit)}" else visit.host,
                    color = c.muted,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (visit.visitCount > 1) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "×${visit.visitCount}",
                    color = c.muted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private fun timeOf(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun startOfDay(timestamp: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** Today / Yesterday / weekday date, in the order the rows already arrive. */
private fun groupByDay(rows: List<Visit>): List<Pair<String, List<Visit>>> {
    if (rows.isEmpty()) return emptyList()
    val today = startOfDay(System.currentTimeMillis())
    val yesterday = today - 86_400_000L
    val year = Calendar.getInstance().get(Calendar.YEAR)
    return rows.groupBy { visit ->
        val day = startOfDay(visit.lastVisit)
        when {
            day >= today -> "Today"
            day >= yesterday -> "Yesterday"
            else -> {
                val cal = Calendar.getInstance().apply { timeInMillis = visit.lastVisit }
                if (cal.get(Calendar.YEAR) == year) {
                    SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(visit.lastVisit))
                } else {
                    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(visit.lastVisit))
                }
            }
        }
    }.toList()
}
