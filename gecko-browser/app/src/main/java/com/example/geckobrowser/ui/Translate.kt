package com.example.geckobrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geckobrowser.BrowserViewModel
import com.example.geckobrowser.TranslateLanguage

private enum class TranslateTarget { From, To }

private val translateRed = Color(0xFFE0453C)

/**
 * Full-page translation controls: source and target language, a single
 * Translate/Restore action, and one level of language picking. Gecko owns the
 * actual translation; this screen only drives it.
 */
@Composable
fun TranslateScreen(vm: BrowserViewModel, onBack: () -> Unit) {
    val c = LocalArc.current
    var picker by remember { mutableStateOf<TranslateTarget?>(null) }
    LaunchedEffect(Unit) { vm.loadTranslateSupport() }
    // Back inside the language list returns to the controls, not to the menu.
    BackHandler(enabled = picker != null) { picker = null }

    val detected = vm.translateDetected
    val fromCode = vm.translateFrom.ifBlank { detected }
    val toCode = vm.translateTo
    val languages = if (picker == TranslateTarget.From) vm.translateFromLanguages
    else vm.translateToLanguages
    val headerIcon: (@Composable () -> Unit)? = if (picker == null) {
        { Glyph(Ico.translate, c.text, size = 19.dp, strokeWidth = 1.8f) }
    } else {
        null
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.settingsBg),
    ) {
        SettingsHeader(
            title = when (picker) {
                TranslateTarget.From -> "Page language"
                TranslateTarget.To -> "Translate into"
                null -> "Translate"
            },
            leading = {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).clickable {
                        if (picker == null) onBack() else picker = null
                    },
                    contentAlignment = Alignment.Center,
                ) { Glyph(Ico.chevronLeft, c.text, size = 20.dp, strokeWidth = 2.2f) }
            },
            icon = headerIcon,
        )

        if (picker != null) {
            LanguagePicker(
                vm = vm,
                target = picker!!,
                languages = languages,
                selected = if (picker == TranslateTarget.From) vm.translateFrom else toCode,
                onPick = { code ->
                    if (picker == TranslateTarget.From) vm.setTranslateFrom(code)
                    else vm.setTranslateTo(code)
                    picker = null
                },
                modifier = Modifier.weight(1f),
            )
            return@Column
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 40.dp),
        ) {
            Text(
                vm.domain,
                color = c.muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
            )

            when {
                !vm.translateSupportLoaded || vm.translateSupportLoading -> {
                    Spacer(Modifier.height(10.dp))
                    Text("Checking translation support…", color = c.muted, fontSize = 13.sp)
                }

                !vm.translateEngineSupported -> {
                    Spacer(Modifier.height(10.dp))
                    SupportCard(
                        title = "Translation is not available",
                        body = "This build of GeckoView has no translation engine, so pages " +
                            "cannot be translated here.",
                        action = "Check again",
                        onAction = { vm.retryTranslateSupport() },
                    )
                }

                else -> {
                    if (vm.translateActive) {
                        Spacer(Modifier.height(10.dp))
                        SupportCard(
                            title = "Translated into " + vm.translateLanguageName(toCode),
                            body = "The page is showing its translated text. Restore the " +
                                "original at any time.",
                            action = null,
                            onAction = {},
                        )
                    }

                    if (vm.translateToLanguages.isEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        SupportCard(
                            title = "No language pairs yet",
                            body = "Gecko can translate, but has not reported any supported " +
                                "languages.",
                            action = "Reload",
                            onAction = { vm.retryTranslateSupport() },
                        )
                    }

                    SectionLabel("Languages")
                    SettingsGroup {
                        SettingsRow(
                            "Page language",
                            icon = { Glyph(Ico.globe, c.text, size = 19.dp, strokeWidth = 1.8f) },
                            value = vm.translateLanguageName(fromCode),
                            onClick = { picker = TranslateTarget.From },
                        )
                        SettingsRow(
                            "Translate into",
                            icon = { Glyph(Ico.translate, c.text, size = 18.dp, strokeWidth = 1.9f) },
                            value = if (toCode.isBlank()) "Select a language"
                            else vm.translateLanguageName(toCode),
                            onClick = { picker = TranslateTarget.To },
                        )
                    }

                    if (detected.isNotBlank()) {
                        Text(
                            if (vm.translateDetectedSupported) {
                                "Detected on the page: " + vm.translateLanguageName(detected)
                            } else {
                                "Detected " + vm.translateLanguageName(detected) +
                                    ", which Gecko cannot translate from"
                            },
                            color = c.muted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 6.dp, top = 8.dp),
                        )
                    }

                    vm.translateError?.let { message ->
                        Text(
                            message,
                            color = translateRed,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 6.dp, top = 12.dp),
                        )
                    }

                    Spacer(Modifier.height(22.dp))
                    TranslateAction(
                        label = when {
                            vm.translateBusy -> "Working…"
                            vm.translateActive -> "Show original page"
                            else -> "Translate page"
                        },
                        enabled = !vm.translateBusy && vm.translateToLanguages.isNotEmpty() &&
                            (vm.translateActive || fromCode.isNotBlank()),
                        onClick = {
                            if (vm.translateActive) vm.restoreTranslation() else vm.translatePage()
                        },
                    )
                    if (fromCode.isBlank() && !vm.translateActive) {
                        Text(
                            "Pick the language of this page to start.",
                            color = c.muted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 6.dp, top = 8.dp),
                        )
                    }
                    if (vm.translateActive && !vm.translateBusy) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { vm.translatePage() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Translate again with these languages",
                                color = c.accent,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    Text(
                        "Language models download the first time a pair is used, so the " +
                            "first translation can take a minute.",
                        color = c.muted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(start = 6.dp, top = 18.dp, end = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguagePicker(
    vm: BrowserViewModel,
    target: TranslateTarget,
    languages: List<TranslateLanguage>,
    selected: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalArc.current
    if (languages.isEmpty()) {
        Column(modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp)) {
            Text(
                "No languages are available. Check translation support and try again.",
                color = c.muted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Reload",
                color = c.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { vm.retryTranslateSupport() }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
        return
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (target == TranslateTarget.From) {
            item(key = "auto") {
                LanguageRow(
                    label = "Detect automatically",
                    selected = selected.isBlank(),
                    onClick = { onPick("") },
                )
            }
        }
        items(languages, key = { it.code }) { language ->
            LanguageRow(
                label = language.label,
                selected = language.code == selected,
                onClick = { onPick(language.code) },
            )
        }
        item(key = "note") {
            Text(
                if (target == TranslateTarget.From) {
                    "Gecko usually detects the page language for you."
                } else {
                    "Only languages Gecko has a model for are listed."
                },
                color = c.muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 6.dp, top = 16.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalArc.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = c.text,
            fontSize = 15.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text("✓", color = c.accent, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SupportCard(
    title: String,
    body: String,
    action: String?,
    onAction: () -> Unit,
) {
    val c = LocalArc.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.menuCard)
            .padding(16.dp),
    ) {
        Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(body, color = c.muted, fontSize = 12.sp, lineHeight = 17.sp)
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                action,
                color = c.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { onAction() }
                    .padding(horizontal = 6.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun TranslateAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalArc.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) c.accent else c.chip)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) c.settingsBg else c.muted,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
