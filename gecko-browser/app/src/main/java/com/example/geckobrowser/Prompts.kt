package com.example.geckobrowser

/** The site permissions the browser knows how to remember. */
enum class PermissionKind(val key: String, val label: String) {
    Camera("camera", "Camera"),
    Microphone("microphone", "Microphone"),
    Location("location", "Location"),
    Notifications("notifications", "Notifications"),
}

/** A native permission question shown as a pill-sized popup. */
data class PermissionPromptState(
    val id: Long,
    val kind: PermissionKind,
    val host: String,
)

/**
 * A popup Gecko wanted to open, held rather than allowed. The pill offers a
 * one-off Allow; doing nothing (or ✕) denies it after a short dwell.
 */
data class PopupNotice(
    val id: Long,
    val host: String,
)

/**
 * A text selection's action menu, mirrored for Compose. The real Gecko
 * `Selection` object stays in the view model; this only carries what to draw.
 */
data class SelectionState(
    val text: String,
    val canCopy: Boolean,
    val canSelectAll: Boolean,
    /** Anchor in dp, relative to the browser surface. */
    val x: Float,
    val y: Float,
    val editable: Boolean,
)

/** A language Gecko's translation engine can read or write. */
data class TranslateLanguage(
    val code: String,
    val label: String,
)

/** Android runtime permissions the activity has to request before we can ask. */
data class AndroidPermissionRequest(
    val id: Long,
    val permissions: List<String>,
)

/** A file input handed to the activity's document picker. */
data class FilePickerRequest(
    val id: Long,
    val mimeTypes: List<String>,
)

data class ChoiceOption(
    val id: String,
    val label: String,
    val selected: Boolean,
)

/** One extension as the sheet renders it. */
data class ExtensionItem(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val enabled: Boolean,
    val permissions: List<String>,
)

/**
 * Every scripted/AI prompt GeckoView can raise. Each one is backed by a pending
 * GeckoResult in the view model; answering or dismissing always completes it.
 */
sealed interface BrowserPromptState {
    val id: Long
    val tabId: Long

    data class Alert(
        override val id: Long,
        override val tabId: Long,
        val title: String,
        val message: String,
    ) : BrowserPromptState

    data class Button(
        override val id: Long,
        override val tabId: Long,
        val title: String,
        val message: String,
    ) : BrowserPromptState

    data class Text(
        override val id: Long,
        override val tabId: Long,
        val title: String,
        val message: String,
        val value: String,
    ) : BrowserPromptState

    data class Choice(
        override val id: Long,
        override val tabId: Long,
        val title: String,
        val message: String,
        val options: List<ChoiceOption>,
        val multiple: Boolean,
    ) : BrowserPromptState

    data class Auth(
        override val id: Long,
        override val tabId: Long,
        val host: String,
        val message: String,
        val username: String,
    ) : BrowserPromptState

    data class File(
        override val id: Long,
        override val tabId: Long,
        val mimeTypes: List<String>,
        val multiple: Boolean,
    ) : BrowserPromptState

    data class ExternalLink(
        override val id: Long,
        override val tabId: Long,
        val url: String,
    ) : BrowserPromptState {
        val host: String get() = BrowserViewModel.hostOf(url)
    }

    data class ExtensionInstall(
        override val id: Long,
        val name: String,
        val permissions: List<String>,
        val origins: List<String>,
    ) : BrowserPromptState {
        override val tabId: Long get() = -1L
    }
}
