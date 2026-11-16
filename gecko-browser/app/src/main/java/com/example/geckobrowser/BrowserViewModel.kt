package com.example.geckobrowser

import android.Manifest
import android.app.Application
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.TranslationsController
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One tab: its GeckoSession lives in [TabManager], this is the data Compose sees. */
data class TabInfo(
    val id: Long,
    val title: String = "New tab",
    val url: String = "",
    val isHome: Boolean = true,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val secure: Boolean = false,
    val desktopMode: Boolean = false,
    val thumbnail: Bitmap? = null,
)

/** Horizontal plus-swipe that moved between tabs; drives the slide-in. */
data class TabSwitch(val dir: Int, val at: Long = System.currentTimeMillis())

/** A short lived back/forward notice shown in the nav pill's footprint. */
data class NavNotice(
    val forward: Boolean,
    val url: String?,
    val at: Long = System.currentTimeMillis(),
)

/** Long-press target, already flattened out of GeckoView's ContextElement. */
data class ContextMenuState(
    val link: String? = null,
    val src: String? = null,
    val isImage: Boolean = false,
    val title: String? = null,
    val alt: String? = null,
    val text: String? = null,
    val x: Float = 0f,
    val y: Float = 0f,
) {
    val head: String
        get() = when {
            isImage -> alt?.takeIf { it.isNotBlank() }
                ?: title?.takeIf { it.isNotBlank() }
                ?: src?.substringAfterLast('/')?.substringBefore('?').orEmpty().ifBlank { "Image" }
            else -> title?.takeIf { it.isNotBlank() }
                ?: link?.let { BrowserViewModel.hostOf(it) }.orEmpty().ifBlank { "Link" }
        }
}

enum class Overlay {
    None, Search, Menu, Tabs, Settings, SiteSettings, Account, Downloads, Extensions, ExtensionPopup,
    History, Translate, Passwords,
}

/** What a tab's translation looks like right now; kept per tab. */
private data class TabTranslation(
    /** Language Gecko detected in the document, or blank. */
    val detected: String = "",
    val detectedSupported: Boolean = true,
    /** Language the user picked to translate from; blank means the detected one. */
    val from: String = "",
    /** Target language; blank until the supported list arrives. */
    val to: String = "",
    val active: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * A search engine: built in, or added by the user with a %s URL template.
 * [suggestTemplate] is the matching autocomplete endpoint, `%s` being the query;
 * engines without a usable endpoint leave it null and simply offer no network
 * suggestions.
 */
data class SearchEngine(
    val id: String,
    val name: String,
    val urlTemplate: String,
    val host: String,
    val color: Long = 0xFF2F5C69,
    val custom: Boolean = false,
    val suggestTemplate: String? = null,
)

/** History kept for the swipe-back/forward notice text only; navigation is Gecko's. */
private class History {
    val urls = mutableListOf<String>()
    var index = -1
}

class BrowserViewModel(app: Application) : AndroidViewModel(app) {

    private val application = getApplication<Application>()
    private val runtime: GeckoRuntime = ArcApplication.runtimeOf(app)
    private val prefs = app.getSharedPreferences("arc_browser", Context.MODE_PRIVATE)
    private val settingsStore = AppSettings(app)
    private val tabsManager = TabManager(runtime)
    private val historyStore = HistoryStore(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val webExecutor by lazy { GeckoWebExecutor(runtime) }

    private val histories = HashMap<Long, History>()
    private val loadingJobs = HashMap<Long, Job>()
    private val loadStartedAt = HashMap<Long, Long>()
    private val noLoadUi = HashSet<Long>()
    private val stateBlobs = HashMap<Long, String>()
    private val lastRecoverAt = HashMap<Long, Long>()
    private val scrollY = HashMap<Long, Int>()
    /** Accumulated scroll since the pill last changed state. */
    private val scrollAcc = HashMap<Long, Int>()
    private var bootstrappedState by mutableStateOf(false)
    private var pendingIntent: Intent? = null

    /** Tabs whose visits stay out of local history, for private browsing later. */
    private val privateTabs = HashSet<Long>()

    /** Bumped whenever the history table changes, so the sheet re-reads it. */
    private var historyVersionState by mutableIntStateOf(0)

    /** True once settings, downloads and the restored tab list are in place. */
    val ready: Boolean get() = bootstrappedState

    private val zoomByHost = HashMap<String, Int>()
    private val permissionsByHost = HashMap<String, Boolean>()
    private val desktopByHost = HashMap<String, Boolean>()

    private var geckoView: GeckoView? = null
    private var nextPromptId = 1L

    private val promptObjects = HashMap<Long, GeckoSession.PromptDelegate.BasePrompt>()
    private val promptResults =
        HashMap<Long, GeckoResult<GeckoSession.PromptDelegate.PromptResponse>>()
    private val extensionInstallResults =
        HashMap<Long, GeckoResult<WebExtension.PermissionPromptResponse>>()
    private val runtimePermissionCallbacks = HashMap<Long, (Boolean) -> Unit>()

    private sealed class PermissionTarget {
        class Content(
            val permission: GeckoSession.PermissionDelegate.ContentPermission,
            val result: GeckoResult<Int>,
        ) : PermissionTarget()

        class Media(
            val callback: GeckoSession.PermissionDelegate.MediaCallback,
            val video: GeckoSession.PermissionDelegate.MediaSource?,
            val audio: GeckoSession.PermissionDelegate.MediaSource?,
        ) : PermissionTarget()
    }

    private val permissionTargets = HashMap<Long, PermissionTarget>()
    private val extensionObjects = HashMap<String, WebExtension>()
    private var extensionPopupSession: GeckoSession? = null

    /* ------------------------------------------------------------- public state */

    var tabs by mutableStateOf<List<TabInfo>>(emptyList())
        private set

    var activeId by mutableStateOf(0L)
        private set

    var overlay by mutableStateOf(Overlay.None)
        private set

    private var accountReturn = Overlay.Menu
    private var downloadsReturn = Overlay.Menu
    private var extensionsReturn = Overlay.Menu
    private var historyReturn = Overlay.Menu
    private var translateReturn = Overlay.Menu
    private var passwordReturn = Overlay.Menu
    private var popupReturn = Overlay.None

    var darkMode by mutableStateOf(prefs.getBoolean("dark", false))
        private set

    var suggestionsEnabled by mutableStateOf(true)
        private set

    var compactPill by mutableStateOf(false)
        private set

    var zoom by mutableStateOf(100)
        private set

    /** The active tab's desktop-site setting; the menu toggle reads this. */
    val desktopSite: Boolean get() = activeTab?.desktopMode == true

    var searchText by mutableStateOf("")
        private set

    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set

    /** History rows that match what is typed in the search field, best first. */
    var historySuggestions by mutableStateOf<List<Visit>>(emptyList())
        private set

    var toastMessage by mutableStateOf<String?>(null)
        private set

    var navNotice by mutableStateOf<NavNotice?>(null)
        private set

    var tabSwitch by mutableStateOf<TabSwitch?>(null)
        private set

    var settingsSearch by mutableStateOf("")
        private set

    var searchEngineId by mutableStateOf(prefs.getString("search_engine", "duckduckgo") ?: "duckduckgo")
        private set

    var customEngines by mutableStateOf(readCustomEngines())
        private set

    // ---- downloads --------------------------------------------------------

    var downloads by mutableStateOf<List<DownloadItem>>(emptyList())
        private set

    var pendingDownload by mutableStateOf<PendingDownload?>(null)
        private set

    var contextMenu by mutableStateOf<ContextMenuState?>(null)
        private set

    var flight by mutableStateOf<DownloadFlight?>(null)
        private set

    var chipPulse by mutableStateOf(0)
        private set

    private var nextDownloadId = 1L

    // ---- prompts and permissions -----------------------------------------

    var browserPrompt by mutableStateOf<BrowserPromptState?>(null)
        private set

    var permissionPrompt by mutableStateOf<PermissionPromptState?>(null)
        private set

    var androidPermissionRequest by mutableStateOf<AndroidPermissionRequest?>(null)
        private set

    var filePickerRequest by mutableStateOf<FilePickerRequest?>(null)
        private set

    /**
     * A popup waiting on the user's answer. Hosted in the pill's footprint for a
     * few seconds; silence means no.
     */
    var popupBlocked by mutableStateOf<PopupNotice?>(null)
        private set

    /** The text-selection action bar, when Gecko has a live selection. */
    var selectionState by mutableStateOf<SelectionState?>(null)
        private set

    private var selectionObject: GeckoSession.SelectionActionDelegate.Selection? = null
    private var selectionTabId = -1L

    // ---- privacy ----------------------------------------------------------

    /** Strict tracking protection; standard is the default. */
    var etpStrict by mutableStateOf(false)
        private set

    var cookieBannerHandling by mutableStateOf(false)
        private set

    /** Trackers blocked on the active page, shown on the shield. */
    var blockedCount by mutableStateOf(0)
        private set

    private val trackingExceptions = HashSet<String>()
    private val blockedByTab = HashMap<Long, Int>()
    private val popupDismissJobs = HashMap<Long, Job>()
    private val offlineRetries = HashMap<Long, Int>()
    private val offlineFailedUrl = HashMap<Long, String>()
    private val offlineJobs = HashMap<Long, Job>()
    private val errorToastSkip = HashSet<Long>()

    // ---- find in page -----------------------------------------------------

    var findOpen by mutableStateOf(false)
        private set

    var findQuery by mutableStateOf("")
        private set

    /** 1-based index of the highlighted match, and how many there are. */
    var findCurrent by mutableStateOf(0)
        private set

    var findTotal by mutableStateOf(0)
        private set

    var findFound by mutableStateOf(false)
        private set

    /** Bumped on every request so a slow reply can't overwrite a newer one. */
    private var findGeneration = 0

    // ---- translate --------------------------------------------------------

    /** Runtime-wide translation support, fetched once and cached. */
    var translateEngineSupported by mutableStateOf(true)
        private set

    var translateSupportLoading by mutableStateOf(false)
        private set

    var translateSupportLoaded by mutableStateOf(false)
        private set

    var translateFromLanguages by mutableStateOf<List<TranslateLanguage>>(emptyList())
        private set

    var translateToLanguages by mutableStateOf<List<TranslateLanguage>>(emptyList())
        private set

    /** Per-tab translation state; a snapshot map so sheets recompose. */
    private val translations = mutableStateMapOf<Long, TabTranslation>()

    // ---- extensions -------------------------------------------------------

    var extensions by mutableStateOf<List<ExtensionItem>>(emptyList())
        private set

    var extensionBadge by mutableStateOf(false)
        private set

    var extensionPopupTitle by mutableStateOf("")
        private set

    // ---- page state -------------------------------------------------------

    var fullscreen by mutableStateOf(false)
        private set

    /** Bumped when a fresh page load starts; Root snaps its scale to 0.92. */
    var loadZoomStart by mutableStateOf(0)
        private set

    /** Bumped when that load finishes; Root tweens its scale back to 1. */
    var loadZoomEnd by mutableStateOf(0)
        private set

    val pipLoading: Boolean get() = activeTab?.isLoading == true

    val activeDownloads: List<DownloadItem>
        get() = downloads.filter { it.status == DownloadStatus.Downloading }

    /** 2..100, the average progress of everything currently downloading. */
    val downloadRingProgress: Int
        get() {
            val active = activeDownloads
            if (active.isEmpty()) return 100
            val sum = active.sumOf { d ->
                if (d.size > 0) d.done.toDouble() / d.size.toDouble() else 0.05
            }
            return (sum / active.size * 100).toInt().coerceIn(2, 100)
        }

    val downloadSummary: String
        get() {
            if (downloads.isEmpty()) return "Nothing downloaded yet"
            val active = activeDownloads
            if (active.isNotEmpty()) {
                return "${active.size} downloading · $downloadRingProgress% overall"
            }
            val done = downloads.filter { it.status == DownloadStatus.Done }
            val paused = downloads.count { it.status == DownloadStatus.Paused }
            val failed = downloads.count { it.status == DownloadStatus.Failed }
            val canceled = downloads.count { it.status == DownloadStatus.Canceled }
            if (done.isEmpty() && paused == 0 && failed == 0) {
                return "${downloads.size} canceled"
            }
            val total = done.sumOf { it.size }
            val tail = buildString {
                if (paused > 0) append(" · $paused paused")
                if (failed > 0) append(" · $failed failed")
                if (canceled > 0) append(" · $canceled canceled")
            }
            return "${done.size} file${if (done.size == 1) "" else "s"} · ${formatBytes(total)}$tail"
        }

    val engines: List<SearchEngine>
        get() = BUILTIN_ENGINES + customEngines

    val currentEngine: SearchEngine
        get() = engines.firstOrNull { it.id == searchEngineId } ?: BUILTIN_ENGINES.first()

    var editingOverlayHeight by mutableStateOf(false)

    val activeTab: TabInfo? get() = tabs.firstOrNull { it.id == activeId }
    val activeSession: GeckoSession? get() = tabsManager.session(activeId)
    val extensionPopup: GeckoSession? get() = extensionPopupSession

    val domain: String
        get() {
            val t = activeTab ?: return "example.com"
            if (t.isHome || t.url.isBlank()) return "new tab"
            return hostOf(t.url)
        }

    init {
        scope.launch { DownloadCenter.items.collect { downloads = it } }
        scope.launch { bootstrap() }
    }

    /* ---------------------------------------------------------------- bootstrap */

    private suspend fun bootstrap() {
        val snapshot = withContext(Dispatchers.IO) {
            runCatching { settingsStore.snapshot() }.getOrDefault(SettingsSnapshot())
        }
        applySnapshot(snapshot)
        if (snapshot.schema < SETTINGS_SCHEMA) {
            // One-time migration from the SharedPreferences-only build.
            if (prefs.contains("dark")) {
                darkMode = prefs.getBoolean("dark", false)
                withContext(Dispatchers.IO) { settingsStore.setDark(darkMode) }
            }
            withContext(Dispatchers.IO) { settingsStore.setSchema(SETTINGS_SCHEMA) }
        }
        DownloadCenter.restore(application)
        nextDownloadId = (DownloadCenter.current().maxOfOrNull { it.id } ?: 0L) + 1
        withContext(Dispatchers.IO) {
            historyStore.load()
            historyStore.prune(System.currentTimeMillis())
        }
        historyVersionState++
        configureExtensions()
        restoreTabs()
        refreshExtensions()
        bootstrappedState = true
        pendingIntent?.let { pending ->
            pendingIntent = null
            handleIntent(pending)
        }
    }

    private fun applySnapshot(snapshot: SettingsSnapshot) {
        darkMode = snapshot.dark
        suggestionsEnabled = snapshot.suggestions
        // DataStore is the source of truth; a store written before engines were
        // saved there falls back to the prefs value and migrates it once.
        val savedEngine = snapshot.engine.ifBlank {
            prefs.getString("search_engine", "").orEmpty()
        }
        if (savedEngine.isNotBlank() && engines.any { it.id == savedEngine }) {
            searchEngineId = savedEngine
            if (snapshot.engine.isBlank()) {
                scope.launch(Dispatchers.IO) { settingsStore.setEngine(savedEngine) }
            }
        }
        zoomByHost.clear()
        zoomByHost.putAll(snapshot.zoom)
        permissionsByHost.clear()
        snapshot.permissions.forEach { (key, value) ->
            permissionsByHost[key] = value == "allow"
        }
        desktopByHost.clear()
        desktopByHost.putAll(snapshot.desktop)
        etpStrict = snapshot.etpStrict
        cookieBannerHandling = snapshot.cookieBanner
        trackingExceptions.clear()
        trackingExceptions.addAll(snapshot.trackingExceptions)
        // Runtime-wide font prefs are ready before the first session opens.
        runtime.settings.setFontInflationEnabled(true)
        runtime.settings.setFontSizeFactor(zoomByHost[hostOfActive()]?.div(100f) ?: 1f)
        applyContentBlocking()
    }

    /** Pushes the stored privacy choices into Gecko before any page loads. */
    private fun applyContentBlocking() {
        val settings = runtime.settings.contentBlocking
        settings.setEnhancedTrackingProtectionLevel(
            if (etpStrict) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.DEFAULT,
        )
        settings.setCookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
        settings.setCookieBannerMode(
            if (cookieBannerHandling) 2 /* reject or accept */
            else 0 /* disabled */,
        )
    }

    private fun restoreTabs() {
        val list = BrowserFiles.loadTabList(application)
        if (list == null || list.tabs.isEmpty()) {
            if (tabs.isEmpty()) {
                // No home tab: an empty browser shows the logo page until the
                // user searches, which creates the first real tab.
                tabs = emptyList()
                activeId = 0
                persistTabs()
            }
            return
        }
        if (tabs.isNotEmpty()) return // the user already opened something

        BrowserFiles.pruneSessions(application, list.tabs.map { it.id }.toSet())
        val restored = mutableListOf<TabInfo>()
        list.tabs.forEach { record ->
            val blob = BrowserFiles.loadSession(application, record.id)
            val state = blob?.let {
                runCatching { GeckoSession.SessionState.fromString(it) }.getOrNull()
            }
            if (state != null) stateBlobs[record.id] = blob
            val id = tabsManager.createWithId(record.id, state, ::attachDelegates)
            if (record.desktopMode) applyDesktop(id, true)
            val tab = TabInfo(
                id = id,
                title = record.title,
                url = record.url,
                isHome = record.url.isBlank(),
                secure = record.secure,
                desktopMode = record.desktopMode,
            )
            restored += tab
            histories[id] = History().apply {
                if (record.url.isNotBlank()) {
                    urls.add(record.url)
                    index = 0
                }
            }
            if (state == null && record.url.isNotBlank()) {
                tabsManager.load(id, record.url)
            }
        }
        if (restored.isEmpty()) return
        tabs = restored
        activeId = restored.firstOrNull { it.id == list.activeId }?.id ?: restored.first().id
        syncZoomForActive()
        persistTabs()
    }

    private fun persistTabs() {
        val records = tabs.map {
            TabRecord(it.id, it.url, it.title, it.desktopMode, it.secure)
        }
        val active = activeId
        scope.launch {
            withContext(Dispatchers.IO) { BrowserFiles.saveTabList(application, records, active) }
        }
    }

    private fun saveSessionState(id: Long, state: GeckoSession.SessionState) {
        val serialized = try {
            state.toString()
        } catch (_: Throwable) {
            return
        }
        if (serialized.isBlank() || stateBlobs[id] == serialized) return
        stateBlobs[id] = serialized
        scope.launch {
            withContext(Dispatchers.IO) { BrowserFiles.saveSession(application, id, serialized) }
        }
    }

    private fun idOf(session: GeckoSession): Long =
        tabsManager.all.entries.firstOrNull { it.value === session }?.key ?: -1L

    private fun updateTab(id: Long, transform: (TabInfo) -> TabInfo) {
        var structural = false
        tabs = tabs.map { current ->
            if (current.id != id) return@map current
            val next = transform(current)
            if (next.url != current.url || next.title != current.title ||
                next.desktopMode != current.desktopMode || next.secure != current.secure
            ) {
                structural = true
            }
            next
        }
        if (structural) persistTabs()
    }

    private fun addTab(
        restored: GeckoSession.SessionState? = null,
        url: String? = null,
        title: String? = null,
        desktop: Boolean = false,
        makeActive: Boolean = true,
        forcedId: Long? = null,
    ): Long {
        val id = if (forcedId == null) {
            tabsManager.create(restored, ::attachDelegates)
        } else {
            tabsManager.createWithId(forcedId, restored, ::attachDelegates)
        }
        if (desktop) applyDesktop(id, true)
        histories[id] = History().apply {
            if (!url.isNullOrBlank()) {
                urls.add(url)
                index = 0
            }
        }
        tabs = tabs + TabInfo(
            id = id,
            title = title ?: "New tab",
            url = url.orEmpty(),
            isHome = url.isNullOrBlank(),
            desktopMode = desktop,
        )
        if (!url.isNullOrBlank() && restored == null) tabsManager.load(id, url)
        if (makeActive) activeId = id
        persistTabs()
        return id
    }

    /* ---------------------------------------------------------------- delegates */

    private fun attachDelegates(id: Long, session: GeckoSession) {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                val target = url ?: return
                if (isBlankUrl(target)) return
                recordNavigation(id, target)
                updateTab(id) { it.copy(url = target, isHome = false) }
                if (id == activeId) syncZoomForHost(target)
                applyTrackingProtection(session, target)
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                updateTab(id) { it.copy(canGoBack = canGoBack) }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                updateTab(id) { it.copy(canGoForward = canGoForward) }
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny> {
                val raw = request.uri ?: return GeckoResult.allow()
                val scheme = runCatching { Uri.parse(raw).scheme?.lowercase(Locale.US) }.getOrNull()
                if (scheme == null || scheme in INTERNAL_SCHEMES) return GeckoResult.allow()
                if (id == activeId) askExternalLink(id, raw)
                // The external app gets the URL; the page itself must not load it.
                return GeckoResult.deny()
            }

            override fun onNewSession(
                session: GeckoSession,
                uri: String,
            ): GeckoResult<GeckoSession>? {
                val target = uri
                val newId = addTab(url = target, makeActive = false)
                val opened = tabsManager.session(newId) ?: return GeckoResult.fromValue(session)
                return GeckoResult.fromValue(opened)
            }

            /**
             * Top-level load failures get a themed local page instead of Gecko's
             * own. Returning null hands the failure back to Gecko.
             */
            override fun onLoadError(
                session: GeckoSession,
                uri: String?,
                error: WebRequestError,
            ): GeckoResult<String>? {
                val target = uri ?: return null
                val page = ErrorPages.forError(target, error) ?: return null
                // Our page speaks for itself; don't stack a toast on top of it.
                if (id == activeId) errorToastSkip.add(id)
                if (error.code == WebRequestError.ERROR_OFFLINE) scheduleOfflineRetry(id, target)
                return GeckoResult.fromValue(page)
            }
        }

        session.historyDelegate = object : GeckoSession.HistoryDelegate {
            override fun onVisited(
                session: GeckoSession,
                url: String,
                title: String?,
                visitType: Int,
            ): GeckoResult<Boolean> {
                // Claim the visit so Gecko keeps no second copy of it.
                val recorded = recordVisit(id, url, title, visitType)
                return GeckoResult.fromValue(recorded)
            }

            override fun getVisited(
                session: GeckoSession,
                urls: Array<out String>,
            ): GeckoResult<BooleanArray> {
                // Link colouring; a private tab learns nothing from local history.
                if (id in privateTabs) return GeckoResult.fromValue(BooleanArray(urls.size))
                return GeckoResult.fromValue(historyStore.visited(urls))
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (isBlankUrl(url)) return
                scrollY[id] = 0
                scrollAcc[id] = 0
                blockedByTab[id] = 0
                if (id == activeId) blockedCount = 0
                // A new document has none of the old matches in it.
                if (id == activeId && findOpen) closeFind()
                resetTranslation(id)
                updateTab(id) { it.copy(url = url, isHome = false) }
                if (noLoadUi.contains(id)) {
                    updateTab(id) { it.copy(isLoading = false) }
                    return
                }
                loadStartedAt[id] = SystemClock.uptimeMillis()
                loadingJobs.remove(id)?.cancel()
                updateTab(id) { it.copy(isLoading = true, progress = 0) }
                if (id == activeId) {
                    loadZoomStart++
                    compactPill = false
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                val suppressed = noLoadUi.remove(id)
                if (suppressed) {
                    updateTab(id) { it.copy(isLoading = false) }
                    return
                }
                if (success) {
                    // A real page arrived — but the offline page also reports the
                    // failed URL, so only a different URL resets the budget.
                    val failed = offlineFailedUrl[id]
                    val current = tabs.firstOrNull { it.id == id }?.url
                    if (failed == null || failed != current) {
                        offlineFailedUrl.remove(id)
                        offlineRetries.remove(id)
                        offlineJobs.remove(id)?.cancel()
                    }
                }
                if (id == activeId) {
                    loadZoomEnd++
                    val selfExplained = errorToastSkip.remove(id)
                    if (!success && activeTab?.isHome != true && !selfExplained) {
                        showToast("Couldn't load the page")
                    }
                }
                val started = loadStartedAt[id] ?: SystemClock.uptimeMillis()
                val wait = (LOADING_HOLD_MS - (SystemClock.uptimeMillis() - started)).coerceAtLeast(0L)
                loadingJobs.remove(id)?.cancel()
                loadingJobs[id] = scope.launch {
                    if (wait > 0L) delay(wait)
                    updateTab(id) { it.copy(isLoading = false, progress = 100) }
                    loadingJobs.remove(id)
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                updateTab(id) { it.copy(progress = progress.coerceIn(0, 100)) }
            }

            override fun onSecurityChange(
                session: GeckoSession,
                security: GeckoSession.ProgressDelegate.SecurityInformation,
            ) {
                updateTab(id) { it.copy(secure = security.isSecure) }
            }

            override fun onSessionStateChange(
                session: GeckoSession,
                state: GeckoSession.SessionState,
            ) {
                saveSessionState(id, state)
            }
        }

        val scrollPositions = scrollY
        session.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                if (id != activeId || fullscreen || overlay != Overlay.None) return
                val previous = scrollPositions[id] ?: 0
                scrollPositions[id] = scrollY
                val delta = scrollY - previous
                // The pill condenses after a deliberate downward run and comes
                // back on a short upward run (or at the top of the page).
                if (!compactPill) {
                    if (delta > 0) {
                        val acc = (scrollAcc[id] ?: 0) + delta
                        scrollAcc[id] = acc
                        if (acc >= 24 && scrollY > 120) {
                            compactPill = true
                            scrollAcc[id] = 0
                        }
                    } else {
                        scrollAcc[id] = 0
                    }
                } else {
                    if (delta < 0) {
                        val acc = (scrollAcc[id] ?: 0) + delta
                        scrollAcc[id] = acc
                        if (acc <= -16 || scrollY <= 4) {
                            compactPill = false
                            scrollAcc[id] = 0
                        }
                    } else {
                        scrollAcc[id] = 0
                    }
                }
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                if (title.isNullOrBlank()) return
                val current = tabs.firstOrNull { it.id == id }
                updateTab(id) { if (it.isHome) it else it.copy(title = title) }
                // The visit may have been recorded before the title arrived.
                if (current != null && !current.isHome && current.url.isNotBlank() &&
                    id !in privateTabs && historyStore.rename(current.url, title)
                ) {
                    historyVersionState++
                }
            }

            override fun onContextMenu(
                session: GeckoSession,
                screenX: Int,
                screenY: Int,
                element: GeckoSession.ContentDelegate.ContextElement,
            ) {
                if (id != activeId) return
                showContextMenu(element, screenX.toFloat(), screenY.toFloat())
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                val body = response.body
                if (body == null) {
                    showToast("Couldn't start this download")
                    return
                }
                val headers = response.headers
                val name = fileNameFor(headers, response.uri)
                val size = sizeFor(headers)
                val mime = mimeFor(headers, name)
                val host = hostOf(response.uri)
                val origin = if (id == activeId) recentTapOrigin() else null
                offerDownload(
                    PendingDownload(
                        name = name,
                        size = size,
                        mime = mime,
                        host = host,
                        url = response.uri,
                        body = body,
                        originX = origin?.first,
                        originY = origin?.second,
                    )
                )
            }

            override fun onCrash(session: GeckoSession) = recoverSession(id)

            override fun onKill(session: GeckoSession) = recoverSession(id)

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                if (id != activeId) return
                // The bar lives in the chrome, which fullscreen video hides.
                if (fullScreen && findOpen) closeFind()
                fullscreen = fullScreen
            }
        }

        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(
                session: GeckoSession,
                permission: GeckoSession.PermissionDelegate.ContentPermission,
            ): GeckoResult<Int> {
                val result = GeckoResult<Int>()
                if (permission.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE ||
                    permission.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE
                ) {
                    // Media autoplay has no UI in this chrome; keep it permissive.
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                    return result
                }
                val kind = permissionKind(permission.permission)
                if (kind == null) {
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                    return result
                }
                val host = hostOf(permission.uri ?: activeTab?.url.orEmpty())
                val remembered = sitePermission(host, kind)
                if (remembered != null) {
                    result.complete(
                        if (remembered) GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                        else GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                    )
                    return result
                }
                ensureRuntimePermissions(androidPermissionsFor(kind)) { granted ->
                    if (!granted) {
                        result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                    } else {
                        askPermission(kind, host, PermissionTarget.Content(permission, result))
                    }
                }
                return result
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                videoSources: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                audioSources: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback,
            ) {
                val host = hostOf(uri ?: activeTab?.url.orEmpty())
                val needsVideo = videoSources?.isNotEmpty() == true
                val needsAudio = audioSources?.isNotEmpty() == true
                val kind = if (needsVideo) PermissionKind.Camera else PermissionKind.Microphone
                val remembered = sitePermission(host, kind)
                if (remembered != null) {
                    if (remembered) {
                        callback.grant(videoSources?.firstOrNull(), audioSources?.firstOrNull())
                    } else {
                        callback.reject()
                    }
                    return
                }
                val wanted = buildList {
                    if (needsVideo) add(Manifest.permission.CAMERA)
                    if (needsAudio) add(Manifest.permission.RECORD_AUDIO)
                }
                ensureRuntimePermissions(wanted) { granted ->
                    if (!granted) {
                        callback.reject()
                    } else {
                        askPermission(
                            kind,
                            host,
                            PermissionTarget.Media(
                                callback,
                                videoSources?.firstOrNull(),
                                audioSources?.firstOrNull(),
                            ),
                        )
                    }
                }
            }

            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<String>?,
                callback: GeckoSession.PermissionDelegate.Callback,
            ) {
                ensureRuntimePermissions(permissions?.toList().orEmpty()) { granted ->
                    if (granted) callback.grant() else callback.reject()
                }
            }
        }

        session.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onAlertPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.AlertPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val id = nextPromptId++
                val result = registerPrompt(id, prompt)
                val tabId = idOf(session)
                if (tabId < 0 || tabId != activeId) {
                    completePrompt(id, prompt.dismiss())
                } else {
                    clearShownPrompt()
                    browserPrompt = BrowserPromptState.Alert(
                        id = id,
                        tabId = tabId,
                        title = prompt.title.orEmpty().ifBlank { "This page says" },
                        message = prompt.message.orEmpty(),
                    )
                    compactPill = false
                }
                return result
            }

            override fun onButtonPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.ButtonPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val id = nextPromptId++
                val result = registerPrompt(id, prompt)
                val tabId = idOf(session)
                if (tabId < 0 || tabId != activeId) {
                    completePrompt(id, prompt.dismiss())
                } else {
                    clearShownPrompt()
                    browserPrompt = BrowserPromptState.Button(
                        id = id,
                        tabId = tabId,
                        title = prompt.title.orEmpty().ifBlank { "Confirm" },
                        message = prompt.message.orEmpty(),
                    )
                    compactPill = false
                }
                return result
            }

            override fun onTextPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.TextPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val id = nextPromptId++
                val result = registerPrompt(id, prompt)
                val tabId = idOf(session)
                if (tabId < 0 || tabId != activeId) {
                    completePrompt(id, prompt.dismiss())
                } else {
                    clearShownPrompt()
                    browserPrompt = BrowserPromptState.Text(
                        id = id,
                        tabId = tabId,
                        title = prompt.title.orEmpty().ifBlank { "This page asks" },
                        message = prompt.message.orEmpty(),
                        value = prompt.defaultValue.orEmpty(),
                    )
                    compactPill = false
                }
                return result
            }

            override fun onChoicePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.ChoicePrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val id = nextPromptId++
                val result = registerPrompt(id, prompt)
                val tabId = idOf(session)
                if (tabId < 0 || tabId != activeId) {
                    completePrompt(id, prompt.dismiss())
                } else {
                    val options = prompt.choices.orEmpty().map { choice ->
                        ChoiceOption(
                            id = choice.id.orEmpty(),
                            label = choice.label.orEmpty().ifBlank { "Option" },
                            selected = choice.selected,
                        )
                    }
                    clearShownPrompt()
                    browserPrompt = BrowserPromptState.Choice(
                        id = id,
                        tabId = tabId,
                        title = prompt.title.orEmpty().ifBlank { "Choose an option" },
                        message = prompt.message.orEmpty(),
                        options = options,
                        multiple = false,
                    )
                    compactPill = false
                }
                return result
            }

            override fun onAuthPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.AuthPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val id = nextPromptId++
                val result = registerPrompt(id, prompt)
                val tabId = idOf(session)
                if (tabId < 0 || tabId != activeId) {
                    completePrompt(id, prompt.dismiss())
                } else {
                    clearShownPrompt()
                    browserPrompt = BrowserPromptState.Auth(
                        id = id,
                        tabId = tabId,
                        host = hostOf(prompt.authOptions?.uri ?: activeTab?.url.orEmpty()),
                        message = prompt.message.orEmpty()
                            .ifBlank { prompt.title.orEmpty().ifBlank { "Sign in" } },
                        username = prompt.authOptions?.username.orEmpty(),
                    )
                    compactPill = false
                }
                return result
            }

            override fun onFilePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.FilePrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val id = nextPromptId++
                val result = registerPrompt(id, prompt)
                val tabId = idOf(session)
                if (tabId < 0 || tabId != activeId) {
                    completePrompt(id, prompt.dismiss())
                } else {
                    val mimes = prompt.mimeTypes?.toList().orEmpty().ifEmpty { listOf("*/*") }
                    filePickerRequest = FilePickerRequest(id, mimes)
                    compactPill = false
                }
                return result
            }

            override fun onPopupPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.PopupPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                // A popup from a background tab, or while a sheet is open, is an
                // interruption the user didn't ask for: deny it outright. The
                // active page gets a short-lived pill instead.
                if (id != activeId || overlay != Overlay.None || fullscreen) {
                    return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))
                }
                val promptId = nextPromptId++
                val result = registerPrompt(promptId, prompt)
                popupBlocked = PopupNotice(
                    id = promptId,
                    host = hostOf(prompt.targetUri ?: activeTab?.url.orEmpty()),
                )
                compactPill = false
                dismissPopupAfterDwell(promptId)
                return result
            }
        }

        session.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(
                session: GeckoSession,
                event: ContentBlocking.BlockEvent,
            ) {
                if (!event.isBlocking) return
                val count = (blockedByTab[id] ?: 0) + 1
                blockedByTab[id] = count
                if (id == activeId) blockedCount = count
            }
        }

        session.selectionActionDelegate = object : GeckoSession.SelectionActionDelegate {
            override fun onShowActionRequest(
                session: GeckoSession,
                selection: GeckoSession.SelectionActionDelegate.Selection,
            ) {
                if (id != activeId) {
                    selection.hide()
                    return
                }
                val density = application.resources.displayMetrics.density.coerceAtLeast(0.1f)
                val rect = selection.screenRect
                selectionObject = selection
                selectionTabId = id
                selectionState = SelectionState(
                    text = selection.text.orEmpty(),
                    canCopy = selection.isActionAvailable(
                        GeckoSession.SelectionActionDelegate.ACTION_COPY
                    ),
                    canSelectAll = selection.isActionAvailable(
                        GeckoSession.SelectionActionDelegate.ACTION_SELECT_ALL
                    ),
                    x = if (rect != null) rect.centerX() / density else 0f,
                    y = if (rect != null) rect.bottom / density else 0f,
                    editable = (selection.flags and
                        GeckoSession.SelectionActionDelegate.FLAG_IS_EDITABLE) != 0,
                )
                compactPill = false
            }

            override fun onHideAction(session: GeckoSession, reason: Int) {
                if (selectionTabId != id) return
                selectionObject = null
                selectionState = null
            }
        }

        session.translationsSessionDelegate =
            object : TranslationsController.SessionTranslation.Delegate {
                override fun onTranslationStateChange(
                    session: GeckoSession,
                    state: TranslationsController.SessionTranslation.TranslationState?,
                ) {
                    if (state == null) return
                    val detected = state.detectedLanguages
                    val pair = state.requestedTranslationPair
                    val visible = state.hasVisibleChange
                    val errorText = translateStateError(state.error)
                    updateTranslation(id) { current ->
                        current.copy(
                            detected = detected?.docLangTag?.takeIf { it.isNotBlank() }
                                ?: current.detected,
                            detectedSupported = detected?.isDocLangTagSupported != false,
                            from = pair?.fromLanguage?.takeIf { it.isNotBlank() }
                                ?: current.from,
                            to = pair?.toLanguage?.takeIf { it.isNotBlank() } ?: current.to,
                            active = when {
                                visible == true -> true
                                pair == null -> false
                                else -> current.active
                            },
                            busy = if (visible == true || errorText != null) false else current.busy,
                            error = errorText ?: current.error,
                        )
                    }
                }
            }

        // Restored tabs have no location yet; the session default applies until
        // onLocationChange refines it for the host that actually loads.
        runCatching { session.settings.setUseTrackingProtection(true) }
    }

    /* -------------------------------------------------------------- privacy */

    /** Whether tracking protection is on for [host] on the current session. */
    fun trackingProtectionEnabled(host: String): Boolean =
        host.isNotBlank() && host !in trackingExceptions

    fun setTrackingStrict(strict: Boolean) {
        if (etpStrict == strict) return
        etpStrict = strict
        applyContentBlocking()
        scope.launch(Dispatchers.IO) { settingsStore.setEtpStrict(strict) }
    }

    fun toggleCookieBannerHandling() {
        cookieBannerHandling = !cookieBannerHandling
        applyContentBlocking()
        val value = cookieBannerHandling
        scope.launch(Dispatchers.IO) { settingsStore.setCookieBanner(value) }
    }

    /**
     * Turns tracking protection off for one host and reloads the page so the
     * change takes effect. Exemptions are stored, so they survive a restart.
     */
    fun setSiteTrackingProtection(host: String, enabled: Boolean) {
        if (host.isBlank()) return
        if (enabled) trackingExceptions.remove(host) else trackingExceptions.add(host)
        scope.launch(Dispatchers.IO) {
            settingsStore.setTrackingException(host, exempt = !enabled)
        }
        val tab = activeTab ?: return
        val session = tabsManager.session(tab.id) ?: return
        runCatching { session.settings.setUseTrackingProtection(enabled) }
        blockedByTab[tab.id] = 0
        blockedCount = 0
        if (tab.url.startsWith("http")) session.reload()
    }

    private fun applyTrackingProtection(session: GeckoSession, url: String) {
        val host = hostOf(url)
        runCatching { session.settings.setUseTrackingProtection(host !in trackingExceptions) }
    }

    /* ---------------------------------------------------------- error pages */

    private fun scheduleOfflineRetry(id: Long, url: String) {
        val attempt = offlineRetries[id] ?: 0
        if (attempt >= MAX_OFFLINE_RETRIES) return
        offlineRetries[id] = attempt + 1
        offlineFailedUrl[id] = url
        offlineJobs.remove(id)?.cancel()
        offlineJobs[id] = scope.launch {
            delay(OFFLINE_RETRY_MS)
            offlineJobs.remove(id)
            if (!tabsManager.ids.contains(id)) return@launch
            // A page that has since moved elsewhere shouldn't be yanked back.
            val tab = tabs.firstOrNull { it.id == id } ?: return@launch
            // reload() would reload the error document itself, so go to the URL.
            if (tab.url == url) tabsManager.session(id)?.loadUri(url)
        }
    }

    /* ------------------------------------------------------------------ prompts */

    private fun registerPrompt(
        id: Long,
        prompt: GeckoSession.PromptDelegate.BasePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        promptObjects[id] = prompt
        promptResults[id] = result
        prompt.setDelegate(object : GeckoSession.PromptDelegate.PromptInstanceDelegate {
            override fun onPromptDismiss(prompt: GeckoSession.PromptDelegate.BasePrompt) {
                // Keep the pending result: our own answer callbacks still have to
                // complete it after dismiss() has already fired this callback.
                promptObjects.remove(id)
                if (browserPrompt?.id == id) browserPrompt = null
                // A popup the page withdrew shouldn't leave its pill behind.
                if (popupBlocked?.id == id) {
                    popupBlocked = null
                    popupDismissJobs.remove(id)?.cancel()
                }
            }
        })
        return result
    }

    private fun completePrompt(
        id: Long,
        response: GeckoSession.PromptDelegate.PromptResponse?,
    ) {
        if (browserPrompt?.id == id) browserPrompt = null
        promptObjects.remove(id)
        val result = promptResults.remove(id) ?: return
        if (response != null) {
            runCatching { result.complete(response) }
        } else {
            runCatching { result.completeExceptionally(IllegalStateException("Prompt dismissed")) }
        }
    }

    private fun clearShownPrompt() {
        val current = browserPrompt ?: return
        cancelPromptById(current.id)
    }

    private fun cancelPromptById(id: Long) {
        val prompt = promptObjects[id]
        val response = prompt?.let { runCatching { it.dismiss() }.getOrNull() }
        completePrompt(id, response)
        val ext = extensionInstallResults.remove(id)
        ext?.let { runCatching { it.complete(WebExtension.PermissionPromptResponse(false, false)) } }
        if (browserPrompt?.id == id) browserPrompt = null
    }

    fun answerAlert(id: Long) {
        val prompt = promptObjects[id]
        completePrompt(id, prompt?.let { runCatching { it.dismiss() }.getOrNull() })
    }

    /* --------------------------------------------------------------- popups */

    private fun dismissPopupAfterDwell(id: Long) {
        popupDismissJobs.remove(id)?.cancel()
        popupDismissJobs[id] = scope.launch {
            delay(POPUP_DWELL_MS)
            popupDismissJobs.remove(id)
            if (popupBlocked?.id == id) answerPopup(id, allow = false)
        }
    }

    /** Answers a held popup; [allow] opens it as a new tab. */
    fun answerPopup(id: Long, allow: Boolean) {
        if (popupBlocked?.id == id) popupBlocked = null
        popupDismissJobs.remove(id)?.cancel()
        val prompt = promptObjects[id] as? GeckoSession.PromptDelegate.PopupPrompt
        val response = prompt?.let {
            runCatching {
                it.confirm(if (allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
            }.getOrNull()
        }
        completePrompt(id, response ?: prompt?.dismiss())
    }

    /** Quietly drops the popup notice, denying the request. */
    fun dismissPopupNotice() {
        popupBlocked?.let { answerPopup(it.id, allow = false) }
    }

    /* ------------------------------------------------------------ selection */

    fun runSelectionAction(action: String) {
        val selection = selectionObject ?: return
        selectionObject = null
        selectionState = null
        runCatching { selection.execute(action) }
    }

    fun hideSelection() {
        val selection = selectionObject ?: return
        selectionObject = null
        selectionState = null
        runCatching { selection.hide() }
    }

    /* ----------------------------------------------------------- find in page */

    /** Opens the find bar over the page, dismissing any sheet first. */
    fun openFind() {
        val tab = activeTab ?: return
        if (tab.isHome || tab.url.isBlank()) {
            showToast("Open a page to search it")
            return
        }
        if (overlay != Overlay.None) closeOverlay()
        findOpen = true
        findQuery = ""
        findCurrent = 0
        findTotal = 0
        findFound = false
        findGeneration++
        compactPill = false
        // Dim the page and highlight every hit while the bar is up; Gecko keeps
        // these flags on the session, so closing has to clear them by hand.
        runCatching {
            tabsManager.session(tab.id)?.finder?.setDisplayFlags(
                GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL or GeckoSession.FINDER_DISPLAY_DIM_PAGE,
            )
        }
    }

    /** Closes the bar, drops the highlight and forgets the query. */
    fun closeFind() {
        if (!findOpen) return
        findOpen = false
        findQuery = ""
        findCurrent = 0
        findTotal = 0
        findFound = false
        findGeneration++
        runCatching {
            activeSession?.finder?.let {
                it.clear()
                it.setDisplayFlags(0)
            }
        }
    }

    fun updateFindQuery(text: String) {
        if (!findOpen) return
        findQuery = text
        runFind(forward = true)
    }

    fun findNext() = runFind(forward = true)

    fun findPrev() = runFind(forward = false)

    /**
     * Asks the page for the next (or previous) match. Gecko answers
     * asynchronously; [findGeneration] discards a reply that belongs to a query
     * the user has already typed past.
     */
    private fun runFind(forward: Boolean) {
        val tab = activeTab ?: return
        val session = tabsManager.session(tab.id) ?: return
        val query = findQuery
        val generation = ++findGeneration
        if (query.isBlank()) {
            runCatching { session.finder.clear() }
            findCurrent = 0
            findTotal = 0
            findFound = false
            return
        }
        val flags = if (forward) GeckoSession.FINDER_FIND_FORWARD
        else GeckoSession.FINDER_FIND_BACKWARDS
        val result = runCatching { session.finder.find(query, flags) }.getOrNull() ?: return
        result.accept(
            { reply ->
                if (reply != null && generation == findGeneration) {
                    findFound = reply.found
                    findTotal = reply.total
                    findCurrent = reply.current
                }
            },
            { _ ->
                // A failed search leaves the previous count in place.
            },
        )
    }

    /* ------------------------------------------------------------- translate */

    private val activeTranslation: TabTranslation
        get() = translations[activeId] ?: TabTranslation()

    val translateDetected: String get() = activeTranslation.detected

    val translateFrom: String get() = activeTranslation.from

    val translateTo: String get() = activeTranslation.to

    val translateActive: Boolean get() = activeTranslation.active

    val translateBusy: Boolean get() = activeTranslation.busy

    val translateError: String? get() = activeTranslation.error

    /** True when Gecko detected a document language it can translate from. */
    val translateDetectedSupported: Boolean get() = activeTranslation.detectedSupported

    /** Display name for a language code, falling back to the system locale. */
    fun translateLanguageName(code: String): String {
        if (code.isBlank()) return "Detect automatically"
        translateFromLanguages.firstOrNull { it.code == code }?.let { return it.label }
        translateToLanguages.firstOrNull { it.code == code }?.let { return it.label }
        return runCatching {
            Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
        }.getOrNull().orEmpty().ifBlank { code }
    }

    /** Opens the translate page from the menu, dismissing any sheet first. */
    fun openTranslate() {
        val current = overlay
        translateReturn = when (current) {
            Overlay.Menu, Overlay.Settings, Overlay.SiteSettings, Overlay.Account -> current
            else -> Overlay.Menu
        }
        clearActiveLoading()
        cancelDownloadPrompt()
        dismissContextMenu()
        overlay = Overlay.Translate
        compactPill = false
        loadTranslateSupport()
        updateTranslation(activeId) { it.copy(error = null) }
        if ((translations[activeId]?.to ?: "").isBlank()) {
            val target = defaultTargetLanguage()
            if (target.isNotBlank()) updateTranslation(activeId) { it.copy(to = target) }
        }
    }

    fun closeTranslate() {
        if (overlay != Overlay.Translate) return
        overlay = translateReturn
        translateReturn = Overlay.Menu
        compactPill = false
    }

    fun retryTranslateSupport() {
        translateSupportLoaded = false
        loadTranslateSupport()
    }

    /**
     * Asks the runtime whether translations are available and, if so, which
     * language pairs it can handle. Cached for the life of the process.
     */
    fun loadTranslateSupport() {
        if (translateSupportLoading) return
        if (translateSupportLoaded) return
        translateSupportLoading = true
        val engine = runCatching {
            TranslationsController.RuntimeTranslation.isTranslationsEngineSupported()
        }.getOrNull()
        if (engine == null) {
            translateEngineSupported = false
            translateSupportLoading = false
            translateSupportLoaded = true
            return
        }
        engine.accept(
            { supported ->
                translateEngineSupported = supported == true
                translateSupportLoading = false
                translateSupportLoaded = true
                if (supported == true) loadTranslateLanguages()
            },
            { _ ->
                translateEngineSupported = false
                translateSupportLoading = false
                translateSupportLoaded = true
            },
        )
    }

    private fun loadTranslateLanguages() {
        val result = runCatching {
            TranslationsController.RuntimeTranslation.listSupportedLanguages()
        }.getOrNull() ?: return
        result.accept(
            { support ->
                translateFromLanguages = support?.fromLanguages.orEmpty().map {
                    TranslateLanguage(it.code, it.localizedDisplayName.orEmpty().ifBlank { it.code })
                }
                translateToLanguages = support?.toLanguages.orEmpty().map {
                    TranslateLanguage(it.code, it.localizedDisplayName.orEmpty().ifBlank { it.code })
                }
                val target = defaultTargetLanguage()
                if (target.isNotBlank()) {
                    translations.keys.toList().forEach { id ->
                        if ((translations[id]?.to ?: "").isBlank()) {
                            updateTranslation(id) { it.copy(to = target) }
                        }
                    }
                }
                refineTargetFromPreferred()
            },
            { _ ->
                // The screen shows the empty state and offers a retry.
            },
        )
    }

    /**
     * Gecko knows the user's preferred translation targets; the platform locale
     * is only a fallback. This quietly overrides a fallback target.
     */
    private fun refineTargetFromPreferred() {
        val preferred = runCatching {
            TranslationsController.RuntimeTranslation.preferredLanguages()
        }.getOrNull() ?: return
        preferred.accept(
            { tags ->
                val match = tags.orEmpty().firstNotNullOfOrNull { tag -> matchLanguage(tag) }
                if (!match.isNullOrBlank()) {
                    translations.keys.toList().forEach { id ->
                        val current = translations[id] ?: TabTranslation()
                        if (current.to.isBlank() || current.to == defaultTargetLanguage()) {
                            updateTranslation(id) { it.copy(to = match) }
                        }
                    }
                }
            },
            { _ -> /* keep the locale-based default */ },
        )
    }

    /** Finds the supported language whose base code matches [tag] ("en-US" → "en"). */
    private fun matchLanguage(tag: String): String? {
        val base = tag.substringBefore('-').lowercase(Locale.US)
        return translateToLanguages.firstOrNull {
            it.code.substringBefore('-').lowercase(Locale.US) == base
        }?.code
    }

    private fun defaultTargetLanguage(): String {
        val codes = translateToLanguages.map { it.code }
        if (codes.isEmpty()) return ""
        val device = Locale.getDefault().language
        return matchLanguage(device) ?: matchLanguage("en") ?: codes.first()
    }

    fun setTranslateFrom(code: String) {
        updateTranslation(activeId) { it.copy(from = code, error = null) }
    }

    fun setTranslateTo(code: String) {
        updateTranslation(activeId) { it.copy(to = code, error = null) }
    }

    /** Translates the active page, downloading the language model if needed. */
    fun translatePage() {
        val tab = activeTab ?: return
        if (tab.isHome || tab.url.isBlank()) {
            showToast("Open a page to translate it")
            return
        }
        val session = tabsManager.session(tab.id) ?: return
        val state = translations[tab.id] ?: TabTranslation()
        val from = state.from.ifBlank { state.detected }
        if (from.isBlank()) {
            updateTranslation(tab.id) {
                it.copy(error = "Pick the language of this page first")
            }
            return
        }
        val to = state.to.ifBlank { defaultTargetLanguage() }
        if (to.isBlank()) {
            updateTranslation(tab.id) {
                it.copy(error = "No translation languages are available")
            }
            return
        }
        if (from == to) {
            updateTranslation(tab.id) {
                it.copy(error = "That is already the page language — pick another")
            }
            return
        }
        val options = TranslationsController.SessionTranslation.TranslationOptions.Builder()
            .downloadModel(true)
            .build()
        updateTranslation(tab.id) { it.copy(from = from, to = to, busy = true, error = null) }
        val result = runCatching {
            session.sessionTranslation?.translate(from, to, options)
        }.getOrNull()
        if (result == null) {
            updateTranslation(tab.id) {
                it.copy(busy = false, error = "Translation is not available for this page")
            }
            return
        }
        result.accept(
            { updateTranslation(tab.id) { it.copy(busy = false, active = true) } },
            { throwable ->
                updateTranslation(tab.id) {
                    it.copy(busy = false, error = translateErrorMessage(throwable))
                }
            },
        )
    }

    /** Puts the original text back on screen. */
    fun restoreTranslation() {
        val tab = activeTab ?: return
        val session = tabsManager.session(tab.id) ?: return
        updateTranslation(tab.id) { it.copy(busy = true, error = null) }
        val result = runCatching { session.sessionTranslation?.restoreOriginalPage() }.getOrNull()
        if (result == null) {
            updateTranslation(tab.id) { it.copy(busy = false) }
            return
        }
        result.accept(
            {
                updateTranslation(tab.id) {
                    it.copy(busy = false, active = false)
                }
            },
            { throwable ->
                updateTranslation(tab.id) {
                    it.copy(busy = false, error = translateErrorMessage(throwable))
                }
            },
        )
    }

    private fun updateTranslation(id: Long, block: (TabTranslation) -> TabTranslation) {
        translations[id] = block(translations[id] ?: TabTranslation())
    }

    /** A fresh document has no translation; keep the language picks. */
    private fun resetTranslation(id: Long) {
        val current = translations[id] ?: return
        if (current.detected.isEmpty() && !current.active && !current.busy &&
            current.error == null
        ) {
            return
        }
        translations[id] = current.copy(detected = "", active = false, busy = false, error = null)
    }

    private fun translateErrorMessage(error: Throwable?): String {
        val translationsError = error as? TranslationsController.TranslationsException
            ?: error?.cause as? TranslationsController.TranslationsException
        return when (translationsError?.code) {
            TranslationsController.TranslationsException.ERROR_ENGINE_NOT_SUPPORTED ->
                "Translation is not supported on this device"
            TranslationsController.TranslationsException.ERROR_LANGUAGE_NOT_SUPPORTED ->
                "That language pair is not supported"
            TranslationsController.TranslationsException.ERROR_MODEL_DOWNLOAD_REQUIRED,
            TranslationsController.TranslationsException.ERROR_MODEL_LANGUAGE_REQUIRED ->
                "Choose both languages before translating"
            TranslationsController.TranslationsException.ERROR_MODEL_COULD_NOT_DOWNLOAD,
            TranslationsController.TranslationsException.ERROR_MODEL_COULD_NOT_RETRIEVE ->
                "The language model could not be downloaded — check your connection"
            TranslationsController.TranslationsException.ERROR_COULD_NOT_LOAD_LANGUAGES ->
                "The list of languages could not be loaded"
            TranslationsController.TranslationsException.ERROR_COULD_NOT_RESTORE ->
                "The original page could not be restored"
            else -> error?.message?.takeIf { it.isNotBlank() }?.let { message ->
                message.replace('-', ' ').replaceFirstChar { it.uppercase() }
            } ?: "Translation failed"
        }
    }

    /** Turns Gecko's state-machine error codes into something readable. */
    private fun translateStateError(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return when (text) {
            "engine-not-supported" -> "Translation is not supported on this device"
            "language-not-supported", "unsupported-language" ->
                "That language pair is not supported"
            "model-download-required" -> "The language model needs to be downloaded"
            else -> text.replace('-', ' ').replaceFirstChar { it.uppercase() }
        }
    }

    /* ------------------------------------------------------------- passwords */

    var passwordVersion by mutableIntStateOf(0)
        private set

    var passwordSearch by mutableStateOf("")
        private set

    private fun appContext(): Context = getApplication<Application>().applicationContext

    fun passwords(): List<SavedLogin> = PasswordStore.all(appContext())

    fun passwordsForHost(host: String): List<SavedLogin> =
        PasswordStore.forHost(appContext(), host)

    fun openPasswords() {
        val current = overlay
        passwordReturn = when (current) {
            Overlay.Settings, Overlay.Menu, Overlay.SiteSettings, Overlay.Account -> current
            else -> Overlay.Menu
        }
        clearActiveLoading()
        cancelDownloadPrompt()
        dismissContextMenu()
        overlay = Overlay.Passwords
        compactPill = false
        passwordVersion++
    }

    fun closePasswords() {
        if (overlay != Overlay.Passwords) return
        overlay = passwordReturn
        passwordReturn = Overlay.Menu
        compactPill = false
    }

    fun saveLogin(login: SavedLogin) {
        PasswordStore.upsert(appContext(), login)
        passwordVersion++
    }

    fun deleteLogin(id: String) {
        PasswordStore.remove(appContext(), id)
        passwordVersion++
    }

    fun setNeverSavePassword(host: String, never: Boolean) {
        PasswordStore.setNeverSave(appContext(), host, never)
        passwordVersion++
    }

    fun isNeverSavePassword(host: String): Boolean =
        PasswordStore.isNeverSave(appContext(), host)

    fun setAutofillEnabled(enabled: Boolean) {
        PasswordStore.setAutofillEnabled(appContext(), enabled)
        passwordVersion++
    }

    fun isAutofillEnabled(): Boolean = PasswordStore.isAutofillEnabled(appContext())

    fun setOfferToSave(enabled: Boolean) {
        PasswordStore.setOfferToSave(appContext(), enabled)
        passwordVersion++
    }

    fun isOfferToSave(): Boolean = PasswordStore.isOfferToSave(appContext())

    fun generatePassword(length: Int = 16, symbols: Boolean = true): String =
        PasswordStore.generatePassword(length, symbols)

    fun onPasswordSearchChange(text: String) { passwordSearch = text }

    fun copyPasswordToClipboard(text: String) {
        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE)
            as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("password", text))
        showToast("Copied")
    }

    fun isAutofillServiceActive(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return false
        val am = appContext().getSystemService(android.view.autofill.AutofillManager::class.java)
            ?: return false
        if (!am.hasEnabledAutofillServices()) return false
        val component = am.autofillServiceComponentName ?: return false
        return component.packageName == appContext().packageName
    }

    fun openAutofillSettings() {
        val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { appContext().startActivity(intent) }
    }

    /* ------------------------------------------------------------- default browser */

    var defaultBrowserVersion by mutableIntStateOf(0)
        private set

    fun isDefaultBrowser(): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val rm = appContext().getSystemService(android.app.role.RoleManager::class.java)
                rm?.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER) == true
            } else {
                val pm = appContext().packageManager
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("http://example.com"),
                )
                val info = pm.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                info?.activityInfo?.packageName == appContext().packageName
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun defaultBrowserIntent(): android.content.Intent? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val rm = appContext().getSystemService(android.app.role.RoleManager::class.java)
                rm?.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER)
            } else {
                android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun openDefaultBrowserSettings() {
        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { appContext().startActivity(intent) }
    }

    fun refreshDefaultBrowser() { defaultBrowserVersion++ }

    fun answerButton(id: Long, confirm: Boolean) {
        val prompt = promptObjects[id] as? GeckoSession.PromptDelegate.ButtonPrompt
        val response = prompt?.let {
            runCatching { it.confirm(if (confirm) 0 else 1) }.getOrNull()
        }
        completePrompt(id, response ?: prompt?.dismiss())
    }

    fun answerText(id: Long, text: String) {
        val prompt = promptObjects[id] as? GeckoSession.PromptDelegate.TextPrompt
        val response = prompt?.let { runCatching { it.confirm(text) }.getOrNull() }
        completePrompt(id, response ?: prompt?.dismiss())
    }

    fun answerChoice(id: Long, optionId: String) {
        val prompt = promptObjects[id] as? GeckoSession.PromptDelegate.ChoicePrompt
        val choice = prompt?.choices?.firstOrNull { it.id == optionId }
        val response = when {
            prompt == null -> null
            choice != null -> runCatching { prompt.confirm(choice) }.getOrNull()
            else -> prompt.dismiss()
        }
        completePrompt(id, response ?: prompt?.dismiss())
    }

    fun answerAuth(id: Long, username: String, password: String) {
        val prompt = promptObjects[id] as? GeckoSession.PromptDelegate.AuthPrompt
        val response = if (prompt != null && (username.isNotBlank() || password.isNotBlank())) {
            runCatching { prompt.confirm(username, password) }.getOrNull()
        } else {
            prompt?.dismiss()
        }
        completePrompt(id, response ?: prompt?.dismiss())
    }

    fun answerFilePick(id: Long, uri: Uri?) {
        val request = filePickerRequest
        if (request == null || request.id != id) return
        filePickerRequest = null
        val prompt = promptObjects[id] as? GeckoSession.PromptDelegate.FilePrompt
        val response = if (prompt != null && uri != null) {
            runCatching { prompt.confirm(application, uri) }.getOrNull()
        } else {
            prompt?.dismiss()
        }
        completePrompt(id, response ?: prompt?.dismiss())
    }

    fun cancelFilePick(id: Long) {
        answerFilePick(id, null)
    }

    fun cancelPrompt() {
        val current = browserPrompt ?: return
        cancelPromptById(current.id)
        browserPrompt = null
    }

    fun answerExternalLink(id: Long, open: Boolean) {
        val link = browserPrompt as? BrowserPromptState.ExternalLink ?: return
        if (link.id != id) return
        browserPrompt = null
        if (open) openExternal(link.url)
    }

    fun answerExtensionInstall(id: Long, allow: Boolean) {
        if (browserPrompt?.id == id) browserPrompt = null
        val result = extensionInstallResults.remove(id) ?: return
        runCatching {
            result.complete(WebExtension.PermissionPromptResponse(allow, false))
        }
        if (allow) extensionBadge = true
    }

    fun dismissPromptsForTab(tabId: Long) {
        val current = browserPrompt
        if (current != null && current.tabId == tabId) cancelPrompt()
        val file = filePickerRequest
        if (file != null && promptObjects[file.id] != null) cancelFilePick(file.id)
        if (permissionPrompt != null) cancelPermissionPrompt()
    }

    /* -------------------------------------------------------------- permissions */

    fun sitePermission(host: String, kind: PermissionKind): Boolean? {
        if (host.isBlank()) return null
        return permissionsByHost["$host|${kind.key}"]
    }

    fun setSitePermission(kind: PermissionKind, allowed: Boolean?) {
        val host = hostOf(activeTab?.url.orEmpty())
        if (host.isBlank()) return
        if (allowed == null) {
            permissionsByHost.remove("$host|${kind.key}")
            scope.launch { withContext(Dispatchers.IO) { settingsStore.clearPermission(host, kind.key) } }
        } else {
            rememberPermission(host, kind, allowed)
        }
    }

    private fun rememberPermission(host: String, kind: PermissionKind, allowed: Boolean) {
        permissionsByHost["$host|${kind.key}"] = allowed
        scope.launch {
            withContext(Dispatchers.IO) { settingsStore.setPermission(host, kind.key, allowed) }
        }
    }

    private fun askPermission(kind: PermissionKind, host: String, target: PermissionTarget) {
        val id = nextPromptId++
        permissionTargets[id] = target
        permissionPrompt = PermissionPromptState(id, kind, host)
        compactPill = false
    }

    fun answerPermission(allow: Boolean, remember: Boolean) {
        val prompt = permissionPrompt ?: return
        permissionPrompt = null
        val target = permissionTargets.remove(prompt.id) ?: return
        if (remember) rememberPermission(prompt.host, prompt.kind, allow)
        when (target) {
            is PermissionTarget.Content -> {
                val value = if (allow) {
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                } else {
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                }
                runCatching { target.result.complete(value) }
                // Mirror the choice into Gecko's permission store so a remembered
                // answer survives even when the delegate isn't consulted first.
                runCatching {
                    runtime.storageController.setPermission(
                        target.permission,
                        if (remember) value
                        else GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT,
                    )
                }
            }

            is PermissionTarget.Media -> {
                if (allow) {
                    runCatching { target.callback.grant(target.video, target.audio) }
                } else {
                    runCatching { target.callback.reject() }
                }
            }
        }
    }

    fun cancelPermissionPrompt() {
        answerPermission(allow = false, remember = false)
    }

    private fun ensureRuntimePermissions(
        permissions: List<String>,
        onResult: (Boolean) -> Unit,
    ) {
        val missing = permissions.distinct().filter {
            ContextCompat.checkSelfPermission(application, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            onResult(true)
            return
        }
        if (androidPermissionRequest != null) {
            // One system request at a time.
            onResult(false)
            return
        }
        val id = nextPromptId++
        runtimePermissionCallbacks[id] = onResult
        androidPermissionRequest = AndroidPermissionRequest(id, missing)
    }

    fun onAndroidPermissionsResult(result: Map<String, Boolean>) {
        val request = androidPermissionRequest ?: return
        androidPermissionRequest = null
        val callback = runtimePermissionCallbacks.remove(request.id) ?: return
        val granted = request.permissions.all { result[it] == true }
        callback(granted)
    }

    fun onAndroidPermissionsCanceled() {
        val request = androidPermissionRequest ?: return
        androidPermissionRequest = null
        runtimePermissionCallbacks.remove(request.id)?.invoke(false)
    }

    private fun permissionKind(permission: Int): PermissionKind? = when (permission) {
        GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> PermissionKind.Location
        GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> PermissionKind.Notifications
        GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE,
        GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE,
        -> null
        else -> null
    }

    private fun androidPermissionsFor(kind: PermissionKind): List<String> = when (kind) {
        PermissionKind.Camera -> listOf(Manifest.permission.CAMERA)
        PermissionKind.Microphone -> listOf(Manifest.permission.RECORD_AUDIO)
        PermissionKind.Location -> listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        PermissionKind.Notifications ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyList()
            }
    }

    /* ---------------------------------------------------------- external links */

    private fun askExternalLink(tabId: Long, url: String) {
        val existing = browserPrompt
        if (existing is BrowserPromptState.ExternalLink && existing.url == url) return
        if (existing != null) clearShownPrompt()
        browserPrompt = BrowserPromptState.ExternalLink(nextPromptId++, tabId, url)
        compactPill = false
    }

    private fun openExternal(url: String) {
        if (url.isBlank()) return
        val intent = if (url.startsWith("intent:", ignoreCase = true)) {
            runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull()
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }
        if (intent == null) {
            showToast("No app can open this link")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            application.startActivity(intent)
        } catch (_: Throwable) {
            showToast("No app can open this link")
        }
    }

    /* ------------------------------------------------------------ intent entry */

    fun handleIntent(intent: Intent?) {
        if (intent == null) return
        // On a cold start the activity is created before tabs and settings are
        // restored; hold the intent so it can't wipe the restored tab list.
        if (!bootstrappedState) {
            pendingIntent = intent
            return
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false)) {
            openDownloads()
            return
        }
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val url = intent.dataString ?: return
                val scheme = runCatching { Uri.parse(url).scheme?.lowercase(Locale.US) }.getOrNull()
                if (scheme == "http" || scheme == "https" || scheme == "about" || scheme == "file") {
                    openInNewTab(url)
                } else {
                    openExternal(url)
                }
            }

            Intent.ACTION_WEB_SEARCH -> {
                val query = intent.getStringExtra(SearchManager.QUERY) ?: return
                openQueryInNewTab(query)
            }

            Intent.ACTION_SEND -> {
                if (!intent.type.orEmpty().startsWith("text/")) return
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim() ?: return
                if (text.isEmpty()) return
                if (looksLikeUrl(text)) openInNewTab(toUrl(text)) else openQueryInNewTab(text)
            }
        }
    }

    private fun openQueryInNewTab(query: String) {
        val url = currentEngine.urlTemplate.replace("%s", Uri.encode(query))
        openInNewTab(url)
    }

    /* --------------------------------------------------------------- navigation */

    private fun recordNavigation(id: Long, url: String) {
        val history = histories.getOrPut(id) { History() }
        val current = history.urls.getOrNull(history.index)
        when {
            current == url -> Unit
            history.index < 0 -> {
                history.urls.add(url)
                history.index = 0
            }

            history.index >= history.urls.size - 1 -> {
                history.urls.add(url)
                history.index = history.urls.size - 1
            }

            else -> {
                history.urls[history.index] = url
            }
        }
    }

    fun submit(input: String) {
        val text = input.trim()
        if (text.isEmpty()) return
        cancelDownloadPrompt()
        dismissContextMenu()
        val url = toUrl(text)
        val tab = activeTab ?: run {
            val id = addTab(url = url)
            closeOverlay()
            expandPill()
            return
        }
        noLoadUi.remove(tab.id)
        resetOfflineRetries(tab.id)
        historySeed(tab.id, url)
        tabsManager.load(tab.id, url)
        updateTab(tab.id) { it.copy(url = url, isHome = false, title = hostOf(url)) }
        closeOverlay()
        expandPill()
        suggestions = emptyList()
        historySuggestions = emptyList()
    }

    private fun historySeed(id: Long, url: String) {
        val history = histories.getOrPut(id) { History() }
        while (history.urls.size > history.index + 1) history.urls.removeAt(history.urls.size - 1)
        history.urls.add(url)
        history.index = history.urls.size - 1
    }

    fun goBack() {
        val tab = activeTab ?: return
        if (!tab.canGoBack) return
        resetOfflineRetries(tab.id)
        noLoadUi.add(tab.id)
        clearLoadingNow(tab.id)
        loadingJobs.remove(tab.id)?.cancel()
        tabsManager.session(tab.id)?.goBack()
        expandPill()
        loadZoomEnd++
    }

    fun goForward() {
        val tab = activeTab ?: return
        if (!tab.canGoForward) return
        resetOfflineRetries(tab.id)
        noLoadUi.add(tab.id)
        clearLoadingNow(tab.id)
        loadingJobs.remove(tab.id)?.cancel()
        tabsManager.session(tab.id)?.goForward()
        expandPill()
        loadZoomEnd++
    }

    fun reload() {
        val tab = activeTab ?: return
        if (tab.isHome) return
        resetOfflineRetries(tab.id)
        noLoadUi.remove(tab.id)
        tabsManager.session(tab.id)?.reload()
        expandPill()
    }

    private fun resetOfflineRetries(id: Long) {
        offlineRetries.remove(id)
        offlineFailedUrl.remove(id)
        offlineJobs.remove(id)?.cancel()
    }

    /** Leaves a page's fullscreen video and restores the chrome. */
    fun exitFullscreen() {
        if (!fullscreen) return
        fullscreen = false
        runCatching { tabsManager.session(activeId)?.exitFullScreen() }
    }

    /** Called from the activity when the app goes to the background. */
    fun onActivityPause() {
        exitFullscreen()
    }

    fun stop() {
        val tab = activeTab ?: return
        tabsManager.session(tab.id)?.stop()
        clearLoadingNow(tab.id)
        loadingJobs.remove(tab.id)?.cancel()
        loadZoomEnd++
    }

    /* ---- history and tab gestures --------------------------------------- */

    private var navNoticeJob: Job? = null

    fun navGesture(forward: Boolean) {
        val tab = activeTab ?: return
        val history = histories.getOrPut(tab.id) { History() }
        val target = history.urls.getOrNull(history.index + if (forward) 1 else -1)
        val canMove = if (forward) tab.canGoForward else tab.canGoBack
        if (canMove) {
            if (forward) goForward() else goBack()
            // Same slide as a tab swipe, but read from the direction of travel:
            // back enters from the left, forward from the right.
            tabSwitch = TabSwitch(if (forward) 1 else -1)
        }
        navNotice = NavNotice(forward = forward, url = target)
        navNoticeJob?.cancel()
        navNoticeJob = scope.launch {
            delay(1150)
            navNotice = null
        }
    }

    fun clearNavNotice() {
        navNoticeJob?.cancel()
        navNotice = null
    }

    /** Horizontal swipes on the plus walk the open tabs. */
    fun switchTab(delta: Int) {
        if (tabs.size < 2) return
        val idx = tabs.indexOfFirst { it.id == activeId }
        if (idx < 0) return
        val next = tabs[(idx + delta + tabs.size) % tabs.size]
        selectTab(next.id)
        // Set after selectTab cleared it: this is a swipe, so slide the page in
        // from the side the swipe came from.
        tabSwitch = TabSwitch(delta)
    }

    fun consumeTabSwitch() {
        tabSwitch = null
    }

    /** Tapping the menu address hands the whole URL to the search field. */
    fun editAddressBar() {
        val tab = activeTab ?: run {
            openSearch()
            return
        }
        if (tab.isHome || tab.url.isBlank()) {
            openSearch()
            return
        }
        val full = if (tab.url.startsWith("http://") || tab.url.startsWith("https://")) tab.url
        else "https://${tab.url}"
        openSearch(full)
    }

    /* ------------------------------------------------------------- page tools */

    fun toggleDesktopSite() {
        val tab = activeTab ?: return
        val next = !tab.desktopMode
        updateTab(tab.id) { it.copy(desktopMode = next) }
        applyDesktop(tab.id, next)
        val host = hostOf(tab.url)
        if (host.isNotBlank() && host != "new tab") {
            desktopByHost[host] = next
            scope.launch { withContext(Dispatchers.IO) { settingsStore.setDesktop(host, next) } }
        }
        if (!tab.isHome) {
            tabsManager.session(tab.id)?.reload(GeckoSession.LOAD_FLAGS_BYPASS_CACHE)
        }
    }

    private fun applyDesktop(id: Long, enabled: Boolean) {
        val session = tabsManager.session(id) ?: return
        session.settings.userAgentMode = if (enabled) {
            GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        }
        session.settings.viewportMode = if (enabled) {
            GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        }
    }

    fun changeZoom(delta: Int) {
        val next = (zoom + delta).coerceIn(50, 200)
        zoom = next
        applyZoom(next)
        val host = hostOfActive()
        if (host.isNotBlank()) {
            zoomByHost[host] = next
            scope.launch { withContext(Dispatchers.IO) { settingsStore.setZoom(host, next) } }
        }
    }

    fun resetZoom() {
        zoom = 100
        applyZoom(100)
        val host = hostOfActive()
        if (host.isNotBlank()) {
            zoomByHost[host] = 100
            scope.launch { withContext(Dispatchers.IO) { settingsStore.setZoom(host, 100) } }
        }
    }

    private fun applyZoom(percent: Int) {
        runtime.settings.setFontInflationEnabled(true)
        runtime.settings.setFontSizeFactor(percent.coerceIn(50, 200) / 100f)
    }

    private fun hostOfActive(): String {
        val tab = activeTab ?: return ""
        if (tab.isHome || tab.url.isBlank()) return ""
        return hostOf(tab.url)
    }

    private fun syncZoomForHost(url: String) {
        val host = hostOf(url)
        val saved = zoomByHost[host] ?: 100
        if (saved != zoom) {
            zoom = saved
            applyZoom(saved)
        }
    }

    private fun syncZoomForActive() {
        val host = hostOfActive()
        val saved = if (host.isBlank()) 100 else zoomByHost[host] ?: 100
        zoom = saved
        applyZoom(saved)
    }

    fun copyCurrentUrl() {
        val url = activeTab?.url.orEmpty()
        if (url.isBlank()) return
        val cm = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("url", url))
        showToast("Link copied")
    }

    fun shareCurrentUrl() {
        val tab = activeTab ?: return
        val url = tab.url
        if (url.isBlank()) return
        val title = tab.title.ifBlank { hostOf(url) }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
            putExtra(Intent.EXTRA_TITLE, title)
        }
        val chooser = Intent.createChooser(send, "Share page").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            getApplication<Application>().startActivity(chooser)
        } catch (_: Throwable) {
            showToast("No app can share this page")
        }
    }

    fun openUrlExternally(url: String) = openExternal(url)

    /* ---------------------------------------------------------------- extension */

    private fun configureExtensions() {
        val controller = runtime.webExtensionController
        controller.promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
            ): GeckoResult<WebExtension.PermissionPromptResponse>? {
                val id = nextPromptId++
                val result = GeckoResult<WebExtension.PermissionPromptResponse>()
                extensionInstallResults[id] = result
                clearShownPrompt()
                browserPrompt = BrowserPromptState.ExtensionInstall(
                    id = id,
                    name = extension.metaData?.name ?: "Add-on",
                    permissions = permissions?.toList().orEmpty(),
                    origins = origins?.toList().orEmpty(),
                )
                compactPill = false
                return result
            }

            override fun onUpdatePrompt(
                extension: WebExtension,
                updated: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
            ): GeckoResult<AllowOrDeny>? = GeckoResult.allow()

            override fun onOptionalPrompt(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
            ): GeckoResult<AllowOrDeny>? = GeckoResult.deny()
        }
    }

    private val extensionActionDelegate = object : WebExtension.ActionDelegate {
        override fun onOpenPopup(
            extension: WebExtension,
            action: WebExtension.Action,
        ): GeckoResult<GeckoSession> = openExtensionPopup(extension, action)

        override fun onTogglePopup(
            extension: WebExtension,
            action: WebExtension.Action,
        ): GeckoResult<GeckoSession> = openExtensionPopup(extension, action)
    }

    private fun openExtensionPopup(
        extension: WebExtension,
        action: WebExtension.Action,
    ): GeckoResult<GeckoSession> {
        closeExtensionPopup(returnToPrevious = false)
        popupReturn = overlay
        val session = GeckoSession()
        session.open(runtime)
        extensionPopupSession = session
        extensionPopupTitle = action.title ?: extension.metaData?.name ?: "Extension"
        overlay = Overlay.ExtensionPopup
        compactPill = false
        return GeckoResult.fromValue(session)
    }

    fun closeExtensionPopup(returnToPrevious: Boolean = true) {
        extensionPopupSession?.close()
        extensionPopupSession = null
        extensionPopupTitle = ""
        if (overlay == Overlay.ExtensionPopup) {
            overlay = if (returnToPrevious) popupReturn else Overlay.None
        }
        popupReturn = Overlay.None
        compactPill = false
    }

    fun refreshExtensions() {
        try {
            runtime.webExtensionController.list().accept(
                { list ->
                    val mapped = mutableListOf<ExtensionItem>()
                    extensionObjects.clear()
                    list.orEmpty().forEach { extension ->
                        extensionObjects[extension.id] = extension
                        runCatching { extension.setActionDelegate(extensionActionDelegate) }
                        val meta = extension.metaData
                        mapped += ExtensionItem(
                            id = extension.id,
                            name = meta?.name ?: extension.id,
                            version = meta?.version.orEmpty(),
                            description = meta?.description.orEmpty(),
                            enabled = meta?.enabled ?: true,
                            permissions = meta?.requiredPermissions?.toList().orEmpty(),
                            origins = meta?.requiredOrigins?.toList().orEmpty(),
                            optionalPermissions = meta?.optionalPermissions?.toList().orEmpty(),
                            optionalOrigins = meta?.optionalOrigins?.toList().orEmpty(),
                            grantedOptionalPermissions = meta?.grantedOptionalPermissions?.toList().orEmpty(),
                            grantedOptionalOrigins = meta?.grantedOptionalOrigins?.toList().orEmpty(),
                            updateDate = meta?.updateDate.orEmpty(),
                            amoListingUrl = meta?.amoListingUrl.orEmpty(),
                            homepageUrl = meta?.homepageUrl.orEmpty(),
                        )
                    }
                    extensions = mapped
                },
                { /* listing failures leave the last snapshot in place */ },
            )
        } catch (_: Throwable) {
            // Extensions are optional.
        }
    }

    fun installExtension(uri: Uri) {
        try {
            // GeckoView installs from a URL/file path, so stage the picked XPI in cache.
            val staged = File(application.cacheDir, "addon-${System.currentTimeMillis()}.xpi")
            application.contentResolver.openInputStream(uri)?.use { input ->
                staged.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Cannot read the chosen file")
            installFromUri(Uri.fromFile(staged))
        } catch (_: Throwable) {
            showToast("Add-on install failed")
        }
    }

    fun installExtensionFromUrl(url: String) {
        if (url.isBlank()) return
        installFromUri(Uri.parse(url))
    }

    private fun installFromUri(uri: Uri) {
        try {
            // Local XPI files need the file installation method; remote URLs go
            // through the normal install path.
            val local = uri.scheme == "file" || uri.scheme == "content"
            val result = if (local) {
                runtime.webExtensionController.install(
                    uri.toString(),
                    WebExtensionController.INSTALLATION_METHOD_FROM_FILE,
                )
            } else {
                runtime.webExtensionController.install(uri.toString())
            }
            result.accept(
                {
                    // GeckoView only installs signed add-ons; unsigned XPI files fail here.
                    extensionBadge = true
                    refreshExtensions()
                    showToast("Add-on installed")
                },
                { showToast("Add-on install failed") },
            )
            showToast("Installing add-on…")
        } catch (_: Throwable) {
            showToast("Add-on install failed")
        }
    }

    fun toggleExtension(item: ExtensionItem) {
        val extension = extensionObjects[item.id] ?: return
        val controller = runtime.webExtensionController
        val result = if (item.enabled) {
            controller.disable(extension, WebExtensionController.EnableSource.USER)
        } else {
            controller.enable(extension, WebExtensionController.EnableSource.USER)
        }
        result.accept(
            {
                refreshExtensions()
                showToast(if (item.enabled) "Add-on disabled" else "Add-on enabled")
            },
            { showToast("Couldn't change the add-on") },
        )
    }

    fun uninstallExtension(item: ExtensionItem) {
        val extension = extensionObjects[item.id] ?: return
        runtime.webExtensionController.uninstall(extension).accept(
            {
                extensionObjects.remove(item.id)
                refreshExtensions()
                showToast("Add-on removed")
            },
            { showToast("Couldn't remove the add-on") },
        )
    }

    fun updateExtension(item: ExtensionItem) {
        val extension = extensionObjects[item.id] ?: return
        runtime.webExtensionController.update(extension).accept(
            { updated ->
                if (updated != null) extensionObjects[item.id] = updated
                refreshExtensions()
                showToast("Add-on updated")
            },
            { showToast("Couldn't update the add-on") },
        )
    }

    fun updateAllExtensions() {
        val items = extensions.toList()
        if (items.isEmpty()) return
        showToast("Checking for updates…")
        items.forEach { updateExtension(it) }
    }

    fun openAmoListing(item: ExtensionItem) {
        val url = item.amoListingUrl.ifBlank { item.homepageUrl }
        if (url.isBlank()) {
            showToast("No listing page available")
            return
        }
        submit(url)
    }

    fun openExtensions() {
        extensionsReturn = when (overlay) {
            Overlay.Menu, Overlay.Settings -> overlay
            else -> Overlay.Menu
        }
        clearActiveLoading()
        extensionBadge = false
        cancelDownloadPrompt()
        dismissContextMenu()
        overlay = Overlay.Extensions
        compactPill = false
        refreshExtensions()
    }

    fun openAddonPicker() {
        addonPickerRequest.value = true
    }

    val addonPickerRequest = mutableStateOf(false)

    fun consumeAddonPickerRequest() {
        addonPickerRequest.value = false
    }

    /* ---------------------------------------------------------------- downloads */

    private var lastTapX = -1f
    private var lastTapY = -1f
    private var lastTapAt = 0L

    fun recordPageTap(x: Float, y: Float) {
        if (x < 0f || y < 0f) return
        lastTapX = x
        lastTapY = y
        lastTapAt = SystemClock.uptimeMillis()
    }

    private fun recentTapOrigin(): Pair<Float, Float>? {
        if (lastTapX < 0f || lastTapY < 0f) return null
        if (SystemClock.uptimeMillis() - lastTapAt > 10_000L) return null
        return lastTapX to lastTapY
    }

    private fun showContextMenu(
        element: GeckoSession.ContentDelegate.ContextElement,
        x: Float,
        y: Float,
    ) {
        if (overlay != Overlay.None) return
        val link = element.linkUri?.takeIf { it.isNotBlank() }
        val src = element.srcUri?.takeIf { it.isNotBlank() }
        if (link == null && src == null) return
        val image = element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE ||
            (src != null && link == null)
        contextMenu = ContextMenuState(
            link = link,
            src = src,
            isImage = image,
            title = element.title?.takeIf { it.isNotBlank() },
            alt = element.altText?.takeIf { it.isNotBlank() },
            text = element.textContent?.trim()?.take(70)?.takeIf { it.isNotBlank() },
            x = x,
            y = y,
        )
        compactPill = false
    }

    fun dismissContextMenu() {
        contextMenu = null
    }

    fun offerDownload(pending: PendingDownload) {
        pendingDownload?.body?.closeQuietly()
        pendingDownload = pending
    }

    fun cancelDownloadPrompt() {
        val p = pendingDownload ?: return
        p.body?.closeQuietly()
        pendingDownload = null
    }

    fun confirmDownloadPrompt() {
        val p = pendingDownload ?: return
        pendingDownload = null
        val id = (DownloadCenter.current().maxOfOrNull { it.id } ?: 0L).coerceAtLeast(nextDownloadId - 1) + 1
        nextDownloadId = id + 1
        val mime = p.mime.ifBlank { mimeFor(emptyMap(), p.name) }
        val item = DownloadItem(
            id = id,
            name = p.name,
            size = p.size.coerceAtLeast(0L),
            status = DownloadStatus.Downloading,
            host = p.host,
            url = p.url,
            mime = mime,
            started = System.currentTimeMillis(),
        )
        if (p.originX != null && p.originY != null) {
            flight = DownloadFlight(id, item.name, p.originX, p.originY)
        } else {
            chipPulse++
        }
        // Ask for the notification permission once; the transfer starts either way.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ensureRuntimePermissions(listOf(Manifest.permission.POST_NOTIFICATIONS)) { }
        }
        DownloadService.start(application, item, p.body)
    }

    /** Fetches a link from the context menu, then shows the same confirm prompt. */
    fun downloadLink(url: String, suggestedName: String?, originX: Float?, originY: Float?) {
        dismissContextMenu()
        if (url.isBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) {
            showToast("This link can't be downloaded")
            return
        }
        try {
            webExecutor.fetch(WebRequest.Builder(url).method("GET").build()).accept(
                { response ->
                    val body = response?.body
                    if (body == null) {
                        showToast("Couldn't download this file")
                        return@accept
                    }
                    val name = if (!suggestedName.isNullOrBlank()) sanitizeName(suggestedName)
                    else fileNameFor(response.headers, response.uri)
                    offerDownload(
                        PendingDownload(
                            name = name,
                            size = sizeFor(response.headers),
                            mime = mimeFor(response.headers, name),
                            host = hostOf(response.uri),
                            url = response.uri.ifBlank { url },
                            body = body,
                            originX = originX,
                            originY = originY,
                        )
                    )
                },
                { showToast("Couldn't download this file") },
            )
        } catch (_: Throwable) {
            showToast("Couldn't download this file")
        }
    }

    fun cancelDownload(id: Long) {
        DownloadService.cancel(application, id)
    }

    fun pauseDownload(id: Long) {
        DownloadService.pause(application, id)
    }

    fun resumeDownload(id: Long) {
        val item = downloads.firstOrNull { it.id == id } ?: return
        if (item.status != DownloadStatus.Paused && item.status != DownloadStatus.Failed) return
        if (item.url.isBlank()) {
            showToast("This download can't be resumed")
            return
        }
        DownloadCenter.update(id) {
            it.copy(status = DownloadStatus.Downloading, localUri = null, ended = 0L)
        }
        DownloadService.resume(application, item.copy(done = item.done))
    }

    /** Swipe-up on the chip: cancel the newest transfer and clear its row. */
    fun cancelNewestDownload(): DownloadItem? {
        val item = activeDownloads.firstOrNull()
            ?: downloads.firstOrNull { it.status == DownloadStatus.Paused }
            ?: return null
        removeDownload(item.id)
        showToast("Canceled ${item.name}")
        return item
    }

    fun retryDownload(id: Long) {
        val item = downloads.firstOrNull { it.id == id } ?: return
        if (item.status == DownloadStatus.Downloading || item.status == DownloadStatus.Done) return
        if (item.url.isBlank()) {
            showToast("This download can't be retried")
            return
        }
        DownloadCenter.update(id) {
            it.copy(
                status = DownloadStatus.Downloading,
                done = 0L,
                ended = 0L,
                localUri = null,
                started = System.currentTimeMillis(),
            )
        }
        DownloadCenter.partFile(application, id).delete()
        DownloadService.resume(
            application,
            item.copy(done = 0L, status = DownloadStatus.Downloading),
        )
    }

    fun removeDownload(id: Long) {
        DownloadService.cancel(application, id)
        DownloadService.forget(id)
        DownloadCenter.remove(id)
        DownloadCenter.persistAsync(application)
    }

    fun clearDownloads() {
        downloads.forEach {
            if (it.status == DownloadStatus.Downloading || it.status == DownloadStatus.Paused) {
                DownloadService.cancel(application, it.id)
                DownloadService.forget(it.id)
            }
        }
        DownloadCenter.replaceAll(emptyList())
        DownloadCenter.persistAsync(application)
    }

    fun openDownload(id: Long) {
        val item = downloads.firstOrNull { it.id == id } ?: return
        val uri = item.localUri
        if (item.status != DownloadStatus.Done || uri.isNullOrBlank()) {
            showToast("This file isn't ready yet")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uri), item.mime.ifBlank { "*/*" })
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            getApplication<Application>().startActivity(intent)
        } catch (_: Throwable) {
            showToast("No app can open this file")
        }
    }

    fun finishFlight(id: Long) {
        val f = flight ?: return
        if (f.id != id) return
        flight = null
        chipPulse++
    }

    /* -------------------------------------------------------------- session api */

    fun onGeckoViewReady(view: GeckoView) {
        geckoView = view
    }

    fun captureActiveThumbnail() {
        val view = geckoView ?: return
        val tab = activeTab ?: return
        if (tab.isHome) return
        val id = tab.id
        runCatching {
            view.capturePixels().accept(
                { bitmap ->
                    if (bitmap != null) updateTab(id) { it.copy(thumbnail = bitmap) }
                },
                { /* capture can fail while the page is changing */ },
            )
        }
    }

    private fun recoverSession(id: Long) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        val now = SystemClock.uptimeMillis()
        val previous = lastRecoverAt[id] ?: 0L
        if (now - previous < 1500L) return
        lastRecoverAt[id] = now
        val state = stateBlobs[id]?.let {
            runCatching { GeckoSession.SessionState.fromString(it) }.getOrNull()
        }
        tabsManager.reopen(id, state, ::attachDelegates)
        if (tab.desktopMode) applyDesktop(id, true)
        if (state == null && tab.url.isNotBlank() && !tab.isHome) {
            tabsManager.load(id, tab.url)
        }
        if (id == activeId) {
            showToast("Tab recovered")
        }
    }

    /* ------------------------------------------------------------------- tabs */

    fun newTab() {
        // No home tab: creating a tab happens only when a search is submitted.
        openSearch()
    }

    fun selectTab(id: Long) {
        if (!tabsManager.ids.contains(id)) return
        if (id == activeId) return
        captureActiveThumbnail()
        dismissPromptsForTab(activeId)
        dismissPopupNotice()
        if (findOpen) closeFind()
        selectionObject?.let { runCatching { it.hide() } }
        selectionObject = null
        selectionState = null
        cancelDownloadPrompt()
        dismissContextMenu()
        tabSwitch = null
        exitFullscreen()
        // A tab that was still loading when we left it shouldn't throw the
        // loading glow back on screen just because we returned to it.
        if (tabs.firstOrNull { it.id == id }?.isLoading == true) {
            noLoadUi.add(id)
            clearLoadingNow(id)
        }
        loadZoomEnd++
        activeId = id
        blockedCount = blockedByTab[id] ?: 0
        closeOverlay()
        expandPill()
        syncZoomForActive()
        persistTabs()
    }

    fun closeTab(id: Long) {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx < 0) return
        dismissPromptsForTab(id)
        dismissPopupNotice()
        if (id == activeId) closeFind()
        cancelDownloadPrompt()
        dismissContextMenu()
        tabsManager.close(id)
        histories.remove(id)
        stateBlobs.remove(id)
        loadingJobs.remove(id)?.cancel()
        noLoadUi.remove(id)
        scrollY.remove(id)
        scrollAcc.remove(id)
        blockedByTab.remove(id)
        translations.remove(id)
        offlineJobs.remove(id)?.cancel()
        offlineRetries.remove(id)
        offlineFailedUrl.remove(id)

        val remaining = tabs.filterNot { it.id == id }
        if (remaining.isEmpty()) {
            // No home tab: closing the last tab returns to the empty logo page.
            tabs = emptyList()
            activeId = 0
        } else {
            tabs = remaining
            if (activeId == id) {
                activeId = remaining[getSafeIndex(idx, remaining.size)].id
            }
        }
        blockedCount = blockedByTab[activeId] ?: 0
        persistTabs()
        compactPill = false
        loadZoomEnd++
        syncZoomForActive()
    }

    private fun getSafeIndex(index: Int, size: Int) =
        (index - 1).coerceAtLeast(0).coerceAtMost(size - 1)

    fun openInNewTab(url: String) {
        if (url.isBlank()) return
        dismissContextMenu()
        addTab(url = url, title = hostOf(url))
        overlay = Overlay.None
        expandPill()
        syncZoomForActive()
    }

    /* ---------------------------------------------------------------- history */

    /** True when the tab's visits are private; nothing marks a tab yet. */
    fun isPrivateTab(id: Long): Boolean = id in privateTabs

    /** Private-tab hook: the tab's visits then never reach local history. */
    fun setPrivateTab(id: Long, private: Boolean) {
        if (private) privateTabs.add(id) else privateTabs.remove(id)
    }

    /**
     * Records a top-level visit. Redirects, error pages and anything that isn't a
     * web page stay out, and private tabs are skipped entirely.
     */
    private fun recordVisit(tabId: Long, url: String, title: String?, visitType: Int): Boolean {
        if (tabId in privateTabs) return false
        if (visitType != GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL) return false
        val scheme = runCatching { Uri.parse(url).scheme?.lowercase(Locale.US) }.getOrNull()
        if (scheme != "http" && scheme != "https") return false
        historyStore.record(url, title)
        historyVersionState++
        return true
    }

    /** Changes whenever history is written to, so the sheet re-reads it. */
    val historyVersion: Int get() = historyVersionState

    fun historyItems(query: String): List<Visit> = historyStore.search(query)

    val historyCount: Int get() = historyStore.count

    fun deleteVisit(id: Long) {
        historyStore.remove(id)
        historyVersionState++
    }

    /** Clears visits newer than [since]; `0` wipes the table. */
    fun clearHistory(since: Long) {
        historyStore.clear(since)
        historyVersionState++
    }

    /** Opens a history row in the active tab (or a new one) and closes the sheet. */
    fun openHistoryUrl(url: String) {
        if (url.isBlank()) return
        submit(url)
    }

    /* ---------------------------------------------------------------- overlays */

    private fun clearLoadingNow(id: Long) {
        loadingJobs.remove(id)?.cancel()
        updateTab(id) { it.copy(isLoading = false) }
    }

    private fun clearActiveLoading() {
        val tab = activeTab ?: return
        clearLoadingNow(tab.id)
    }

    fun closeOverlay() {
        if (overlay == Overlay.ExtensionPopup) closeExtensionPopup(returnToPrevious = false)
        cancelDownloadPrompt()
        dismissContextMenu()
        dismissPopupNotice()
        hideSelection()
        overlay = Overlay.None
        compactPill = false
    }

    fun openSearch(prefill: String? = null) {
        clearActiveLoading()
        cancelDownloadPrompt()
        dismissContextMenu()
        clearNavNotice()
        searchText = prefill.orEmpty()
        suggestions = emptyList()
        historySuggestions = historyStore.suggest(searchText, 4)
        overlay = Overlay.Search
        compactPill = false
        requestSuggestions(searchText)
    }

    fun openMenu() {
        clearActiveLoading()
        captureActiveThumbnail()
        cancelDownloadPrompt()
        dismissContextMenu()
        overlay = Overlay.Menu
        compactPill = false
    }

    fun toggleMenu() {
        if (overlay == Overlay.Menu || overlay == Overlay.Settings ||
            overlay == Overlay.SiteSettings || overlay == Overlay.Account ||
            overlay == Overlay.Extensions
        ) {
            closeOverlay()
        } else {
            openMenu()
        }
    }

    fun openTabs() {
        clearActiveLoading()
        captureActiveThumbnail()
        cancelDownloadPrompt()
        dismissContextMenu()
        overlay = Overlay.Tabs
        compactPill = false
    }

    /** Expands the compact pill back to the three-button bar. */
    fun expandPill() {
        compactPill = false
    }

    fun toggleTabs() {
        if (overlay == Overlay.Tabs) closeOverlay() else openTabs()
    }

    fun openSettings() {
        clearActiveLoading()
        cancelDownloadPrompt()
        dismissContextMenu()
        overlay = Overlay.Settings
        compactPill = false
    }

    fun openSiteSettings() {
        clearActiveLoading()
        cancelDownloadPrompt()
        overlay = Overlay.SiteSettings
        compactPill = false
    }

    fun openDownloads() {
        val current = overlay
        if (current == Overlay.Downloads) return
        downloadsReturn = when (current) {
            Overlay.Menu, Overlay.Settings, Overlay.SiteSettings, Overlay.Account -> current
            else -> Overlay.None
        }
        clearActiveLoading()
        cancelDownloadPrompt()
        dismissContextMenu()
        overlay = Overlay.Downloads
        compactPill = false
    }

    fun closeDownloads() {
        if (overlay != Overlay.Downloads) return
        overlay = downloadsReturn
        downloadsReturn = Overlay.Menu
        compactPill = false
    }

    fun openHistory() {
        val current = overlay
        if (current == Overlay.History) return
        historyReturn = when (current) {
            Overlay.Menu, Overlay.Settings, Overlay.SiteSettings, Overlay.Account -> current
            else -> Overlay.None
        }
        clearActiveLoading()
        cancelDownloadPrompt()
        dismissContextMenu()
        overlay = Overlay.History
        compactPill = false
        historyVersionState++
    }

    fun closeHistory() {
        if (overlay != Overlay.History) return
        overlay = historyReturn
        historyReturn = Overlay.Menu
        compactPill = false
    }

    fun openAccount() {
        clearActiveLoading()
        cancelDownloadPrompt()
        dismissContextMenu()
        accountReturn = when (overlay) {
            Overlay.Settings -> Overlay.Settings
            Overlay.Menu -> Overlay.Menu
            else -> Overlay.Menu
        }
        overlay = Overlay.Account
        compactPill = false
    }

    /** Handles the system back button; returns true when it was consumed. */
    fun onBackPressed(): Boolean {
        if (browserPrompt != null) {
            cancelPrompt()
            return true
        }
        if (permissionPrompt != null) {
            cancelPermissionPrompt()
            return true
        }
        if (androidPermissionRequest != null) {
            onAndroidPermissionsCanceled()
            return true
        }
        if (filePickerRequest != null) {
            cancelFilePick(filePickerRequest!!.id)
            return true
        }
        if (pendingDownload != null) {
            cancelDownloadPrompt()
            return true
        }
        if (popupBlocked != null) {
            dismissPopupNotice()
            return true
        }
        if (selectionState != null) {
            hideSelection()
            return true
        }
        if (contextMenu != null) {
            dismissContextMenu()
            return true
        }
        if (findOpen) {
            closeFind()
            return true
        }
        if (fullscreen) {
            exitFullscreen()
            return true
        }
        return when (overlay) {
            Overlay.None -> false
            Overlay.Translate -> {
                closeTranslate()
                true
            }
            Overlay.Account -> {
                overlay = accountReturn
                true
            }
            Overlay.Settings, Overlay.SiteSettings -> {
                overlay = Overlay.Menu
                true
            }
            Overlay.Extensions -> {
                overlay = extensionsReturn
                true
            }
            Overlay.ExtensionPopup -> {
                closeExtensionPopup()
                true
            }
            Overlay.Downloads -> {
                closeDownloads()
                true
            }
            Overlay.History -> {
                closeHistory()
                true
            }
            Overlay.Passwords -> {
                closePasswords()
                true
            }
            else -> {
                overlay = Overlay.None
                true
            }
        }
    }

    fun toggleDarkMode() {
        darkMode = !darkMode
        prefs.edit().putBoolean("dark", darkMode).apply()
        scope.launch { withContext(Dispatchers.IO) { settingsStore.setDark(darkMode) } }
    }

    fun toggleSearchSuggestions() {
        suggestionsEnabled = !suggestionsEnabled
        if (!suggestionsEnabled) suggestions = emptyList()
        val value = suggestionsEnabled
        scope.launch { withContext(Dispatchers.IO) { settingsStore.setSuggestions(value) } }
        requestSuggestions(searchText)
    }

    fun onSearchTextChange(value: String) {
        searchText = value
        // History is in memory, so this never waits on the network.
        historySuggestions = if (value.isBlank()) emptyList() else historyStore.suggest(value, 4)
        requestSuggestions(value)
    }

    /** The inline completion for what has been typed, or null when nothing fits. */
    fun inlineCompletion(query: String): String? = historyStore.complete(query)

    fun onSettingsSearchChange(value: String) {
        settingsSearch = value
    }

    /* -------------------------------------------------------- search suggestions */

    private var suggestJob: Job? = null

    private fun requestSuggestions(query: String) {
        suggestJob?.cancel()
        if (!suggestionsEnabled || query.isBlank() || looksLikeUrl(query) ||
            currentEngine.suggestTemplate == null
        ) {
            suggestions = emptyList()
            return
        }
        val engineId = currentEngine.id
        suggestJob = scope.launch {
            delay(150)
            val found = withContext(Dispatchers.IO) {
                runCatching { fetchSuggestions(query) }.getOrDefault(emptyList())
            }
            // Drop a reply for an engine or a query the user has moved on from.
            if (searchText == query && searchEngineId == engineId) suggestions = found.take(6)
        }
    }

    private fun fetchSuggestions(query: String): List<String> {
        val template = currentEngine.suggestTemplate ?: return emptyList()
        val conn = URL(template.replace("%s", Uri.encode(query))).openConnection() as HttpURLConnection
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) horiZen")
        return try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parseSuggestions(body, query)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Reads the suggestion payloads the built-in engines actually return:
     * OpenSearch (`["query", ["a", "b"], …]`, Google/Bing), DuckDuckGo's
     * `[{"phrase": "a"}, …]`, and a plain or wrapped string array. Anything
     * unexpected yields no suggestions rather than an error.
     */
    private fun parseSuggestions(body: String, query: String): List<String> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        val found = LinkedHashSet<String>()
        fun add(value: String?) {
            val phrase = value?.trim().orEmpty()
            if (phrase.isEmpty() || phrase.equals(query, ignoreCase = true)) return
            if (found.none { it.equals(phrase, ignoreCase = true) }) found.add(phrase)
        }
        fun collect(node: Any?) {
            when (node) {
                is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i))
                is JSONObject -> {
                    add(node.optString("phrase"))
                    add(node.optString("text"))
                    add(node.optString("name"))
                    for (key in WRAPPED_SUGGESTION_KEYS) {
                        val nested = node.optJSONArray(key) ?: continue
                        for (i in 0 until nested.length()) collect(nested.opt(i))
                    }
                }
                is String -> add(node)
            }
        }
        runCatching { collect(JSONTokener(trimmed).nextValue()) }
        return found.take(6)
    }

    fun looksLikeUrl(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() }) return false
        val lower = t.lowercase(Locale.US)
        if (lower.startsWith("http://") || lower.startsWith("https://") ||
            lower.startsWith("about:") || lower.startsWith("file:") ||
            lower.startsWith("data:") || lower.startsWith("javascript:") ||
            lower.startsWith("view-source:") || lower.startsWith("resource:")
        ) return true
        if (lower.contains("://")) return true

        // Judge only the authority: drop a path, query and any userinfo.
        val authority = t.substringBefore('/').substringBefore('?').substringBefore('#')
        val hostPart = authority.substringAfterLast('@')
        val bracketed = hostPart.startsWith("[")
        val host = if (bracketed) {
            hostPart.substringBefore(']').removePrefix("[").lowercase(Locale.US)
        } else {
            hostPart.substringBefore(':').lowercase(Locale.US)
        }
        if (host.isEmpty()) return false
        // A bracketed IPv6 literal, e.g. [::1].
        if (bracketed) return hostPart.contains(']') && host.contains(':')
        if (host == "localhost" || isIpv4(host)) return true

        // host.tld: at least two labels, a final label of letters, no empty labels.
        val labels = host.split('.')
        if (labels.size < 2 || labels.any { it.isEmpty() }) return false
        val tld = labels.last()
        return tld.length in 2..63 && tld.all { it.isLetter() }
    }

    private fun isIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            if (part.isEmpty() || part.length > 3 || !part.all { it.isDigit() }) return@all false
            val value = part.toIntOrNull() ?: return@all false
            value in 0..255
        }
    }

    /* ------------------------------------------------------------ search engines */

    fun selectEngine(id: String) {
        if (id == searchEngineId || engines.none { it.id == id }) return
        searchEngineId = id
        prefs.edit().putString("search_engine", id).apply()
        scope.launch { withContext(Dispatchers.IO) { settingsStore.setEngine(id) } }
        // Drop the old engine's suggestions and ask the new one.
        suggestions = emptyList()
        requestSuggestions(searchText)
    }

    /** Adds and selects a custom engine. Returns false when the input is invalid. */
    fun addCustomEngine(name: String, template: String): Boolean {
        val label = name.trim()
        var url = template.trim()
        if (label.isEmpty() || !url.contains("%s")) return false
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        val host = hostOfTemplate(url)
        if (host.isEmpty() || !host.contains(".")) return false
        val engine = SearchEngine(
            id = "custom-${System.currentTimeMillis()}",
            name = label,
            urlTemplate = url,
            host = host,
            color = 0xFF2F5C69,
            custom = true,
        )
        customEngines = customEngines + engine
        persistCustomEngines()
        selectEngine(engine.id)
        return true
    }

    private fun readCustomEngines(): List<SearchEngine> {
        val raw = prefs.getString("custom_engines", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    val name = o.optString("name")
                    val url = o.optString("url")
                    if (id.isBlank() || name.isBlank() || !url.contains("%s")) continue
                    add(
                        SearchEngine(
                            id = id,
                            name = name,
                            urlTemplate = url,
                            host = o.optString("host").ifBlank { hostOfTemplate(url) },
                            color = o.optLong("color", 0xFF2F5C69),
                            custom = true,
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun persistCustomEngines() {
        val arr = JSONArray()
        customEngines.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("name", e.name)
                    put("url", e.urlTemplate)
                    put("host", e.host)
                    put("color", e.color)
                }
            )
        }
        prefs.edit().putString("custom_engines", arr.toString()).apply()
    }

    fun toUrl(input: String): String {
        val t = input.trim()
        val lower = t.lowercase(Locale.US)
        if (lower.startsWith("http://") || lower.startsWith("https://") ||
            lower.startsWith("about:") || lower.startsWith("file:") ||
            lower.startsWith("data:") || lower.startsWith("javascript:") ||
            lower.startsWith("view-source:")
        ) return t
        return if (looksLikeUrl(t)) "https://$t"
        else currentEngine.urlTemplate.replace("%s", Uri.encode(t))
    }

    fun showToast(message: String) {
        // The manager shows live state; a floating toast on top of it only competes.
        if (overlay == Overlay.Downloads || overlay == Overlay.History) return
        toastMessage = message
    }

    fun copyText(value: String, message: String) {
        if (value.isBlank()) return
        val cm = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("link", value))
        showToast(message)
    }

    fun shareText(value: String) {
        if (value.isBlank()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, value)
        }
        val chooser = Intent.createChooser(send, "Share").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            getApplication<Application>().startActivity(chooser)
        } catch (_: Throwable) {
            showToast("No app can share this")
        }
    }

    fun clearToast() {
        toastMessage = null
    }

    /* ------------------------------------------------------------------ cleanup */

    override fun onCleared() {
        browserPrompt?.let { cancelPromptById(it.id) }
        browserPrompt = null
        popupBlocked?.let { answerPopup(it.id, allow = false) }
        popupBlocked = null
        popupDismissJobs.values.forEach { it.cancel() }
        popupDismissJobs.clear()
        offlineJobs.values.forEach { it.cancel() }
        offlineJobs.clear()
        permissionPrompt?.let {
            permissionTargets.remove(it.id)
        }
        permissionPrompt = null
        pendingDownload?.body?.closeQuietly()
        pendingDownload = null
        filePickerRequest = null
        if (tabs.isNotEmpty()) {
            // Best-effort synchronous save on the way out; normal saves are async.
            runCatching {
                BrowserFiles.saveTabList(
                    application,
                    tabs.map { TabRecord(it.id, it.url, it.title, it.desktopMode, it.secure) },
                    activeId,
                )
            }
        }
        downloadFlightCleanup()
        closeExtensionPopup(returnToPrevious = false)
        stateBlobs.forEach { (id, blob) ->
            runCatching { BrowserFiles.saveSession(application, id, blob) }
        }
        scope.cancel()
        tabsManager.closeAll()
        super.onCleared()
    }

    private fun downloadFlightCleanup() {
        flight = null
    }

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "com.example.geckobrowser.OPEN_DOWNLOADS"
        private const val SETTINGS_SCHEMA = 1
        private const val LOADING_HOLD_MS = 1700L
        /** How long a blocked popup waits for an Allow before it is denied. */
        private const val POPUP_DWELL_MS = 6000L
        private const val OFFLINE_RETRY_MS = 5000L
        private const val MAX_OFFLINE_RETRIES = 4

        private val INTERNAL_SCHEMES = setOf(
            "http", "https", "about", "file", "data", "blob", "javascript",
            "resource", "wyciwyg", "view-source",
        )

        /** Object keys some suggestion endpoints wrap the phrase array in. */
        private val WRAPPED_SUGGESTION_KEYS = listOf("suggestions", "phrases", "results")

        fun isBlankUrl(url: String): Boolean =
            url.isEmpty() || url == "about:blank" || url.startsWith("about:blank#")

        val BUILTIN_ENGINES = listOf(
            SearchEngine(
                "duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s", "duckduckgo.com", 0xFFDE5833,
                suggestTemplate = "https://duckduckgo.com/ac/?q=%s&type=list",
            ),
            SearchEngine(
                "google", "Google", "https://www.google.com/search?q=%s", "google.com", 0xFF4285F4,
                suggestTemplate = "https://www.google.com/complete/search?client=firefox&q=%s",
            ),
            SearchEngine(
                "bing", "Bing", "https://www.bing.com/search?q=%s", "bing.com", 0xFF008373,
                suggestTemplate = "https://api.bing.com/osjson.aspx?query=%s",
            ),
            SearchEngine(
                "brave", "Brave", "https://search.brave.com/search?q=%s", "search.brave.com", 0xFFFB542B,
                suggestTemplate = "https://search.brave.com/api/suggest?q=%s",
            ),
            // No suggestion endpoint (a request just returns 404 HTML).
            SearchEngine("ecosia", "Ecosia", "https://www.ecosia.org/search?q=%s", "ecosia.org", 0xFF008009),
            // Startpage publishes no autocomplete API we can rely on.
            SearchEngine("startpage", "Startpage", "https://www.startpage.com/sp/search?query=%s", "startpage.com", 0xFF6573FF),
        )

        fun fileNameFor(headers: Map<String, String>, url: String): String {
            val disposition = headerOf(headers, "content-disposition")
            if (disposition != null) {
                val star = Regex("filename\\*\\s*=\\s*(?:UTF-8'')?([^;]+)", RegexOption.IGNORE_CASE)
                    .find(disposition)?.groupValues?.getOrNull(1)
                val plain = Regex("filename\\s*=\\s*\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
                    .find(disposition)?.groupValues?.getOrNull(1)
                val raw = (star ?: plain)?.trim()
                if (!raw.isNullOrBlank()) {
                    val decoded = try {
                        java.net.URLDecoder.decode(raw.trim('"'), "UTF-8")
                    } catch (_: Throwable) {
                        raw
                    }
                    return sanitizeName(decoded)
                }
            }
            val last = Uri.parse(url).lastPathSegment
            return sanitizeName(last ?: "download.bin")
        }

        fun sanitizeName(value: String): String {
            val base = value.substringAfterLast('/').substringAfterLast('\\').trim().trim('"')
            val cleaned = base
                .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
                .trim()
                .trimEnd('.')
            return cleaned.ifBlank { "download.bin" }.take(120)
        }

        fun sizeFor(headers: Map<String, String>): Long {
            val length = headerOf(headers, "content-length")?.toLongOrNull()
            if (length != null && length > 0) return length
            val range = headerOf(headers, "content-range")
            val total = range?.substringAfterLast('/')?.toLongOrNull()
            return if (total != null && total > 0) total else 0L
        }

        fun mimeFor(headers: Map<String, String>, name: String): String {
            val type = headerOf(headers, "content-type")?.substringBefore(';')?.trim()
            if (!type.isNullOrBlank() && type.contains('/')) return type
            return when (downExt(name)) {
                "xpi" -> "application/x-xpinstall"
                "apk" -> "application/vnd.android.package-archive"
                "zip" -> "application/zip"
                "pdf" -> "application/pdf"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "svg" -> "image/svg+xml"
                "mp4" -> "video/mp4"
                "txt" -> "text/plain"
                "json" -> "application/json"
                else -> android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(downExt(name)) ?: "application/octet-stream"
            }
        }

        fun headerOf(headers: Map<String, String>, name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

        fun hostOfTemplate(template: String): String = try {
            Uri.parse(template.replace("%s", "")).host.orEmpty().removePrefix("www.")
        } catch (_: Throwable) {
            ""
        }

        fun hostOf(url: String): String {
            val cleaned = url.removePrefix("https://").removePrefix("http://")
            val host = cleaned.substringBefore('/').substringBefore('?').substringBefore('#')
            return host.ifBlank { "new tab" }
        }
    }
}

private fun InputStream.closeQuietly() {
    try {
        close()
    } catch (_: Throwable) {
        // Nothing left to close.
    }
}

private val DOWNLOAD_KINDS = mapOf(
    "zip" to "ZIP", "pdf" to "PDF", "png" to "PNG", "jpg" to "JPG", "jpeg" to "JPG",
    "gif" to "GIF", "webp" to "WEBP", "svg" to "SVG", "xpi" to "XPI", "apk" to "APK",
    "mp4" to "MP4", "txt" to "TXT", "json" to "JSON",
)

fun downExt(name: String): String {
    val m = Regex("\\.([a-zA-Z0-9]+)(?:[?#].*)?$").find(name) ?: return "bin"
    return m.groupValues[1].lowercase(Locale.US)
}

fun downKindLabel(name: String): String {
    val ext = downExt(name)
    return DOWNLOAD_KINDS[ext] ?: ext.take(3).uppercase(Locale.US)
}

fun formatBytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L)
    return when {
        value >= 1_073_741_824L -> String.format(Locale.US, "%.1f GB", value / 1_073_741_824.0)
        value >= 1_048_576L -> String.format(Locale.US, "%.1f MB", value / 1_048_576.0)
        value >= 1024L -> "${(value + 512L) / 1024L} KB"
        else -> "$value B"
    }
}

fun formatWhen(timestamp: Long): String {
    if (timestamp <= 0L) return "just now"
    val seconds = ((System.currentTimeMillis() - timestamp) / 1000L).coerceAtLeast(0L)
    if (seconds < 20L) return "just now"
    if (seconds < 90L) return "$seconds sec ago"
    val minutes = Math.round(seconds / 60.0)
    if (minutes < 60L) return "$minutes min ago"
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}
