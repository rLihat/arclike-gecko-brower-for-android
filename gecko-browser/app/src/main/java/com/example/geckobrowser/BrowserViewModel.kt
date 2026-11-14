package com.example.geckobrowser

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One tab: its GeckoSession lives in BrowserViewModel, this is the data Compose sees. */
data class TabInfo(
    val id: Long,
    val title: String = "New tab",
    val url: String = "",
    val isHome: Boolean = true,
    val loading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

enum class DownloadStatus { Downloading, Done, Canceled }

/** A download as Compose sees it; the bytes are streamed by BrowserViewModel. */
data class DownloadItem(
    val id: Long,
    val name: String,
    val size: Long = 0L,
    val done: Long = 0L,
    val status: DownloadStatus = DownloadStatus.Downloading,
    val host: String = "",
    val url: String = "",
    val mime: String = "",
    val localUri: String? = null,
    val started: Long = System.currentTimeMillis(),
    val ended: Long = 0L,
)

/** A response that GeckoView handed us, waiting for the confirm prompt. */
data class PendingDownload(
    val name: String,
    val size: Long,
    val mime: String,
    val host: String,
    val url: String,
    val body: InputStream?,
    val originX: Float? = null,
    val originY: Float? = null,
)

/** The tile that jumps from the tap point into the live chip. */
data class DownloadFlight(
    val id: Long,
    val name: String,
    val startX: Float,
    val startY: Float,
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

enum class Overlay { None, Search, Menu, Tabs, Settings, SiteSettings, Account, Downloads }


/** A search engine: built in, or added by the user with a %s URL template. */
data class SearchEngine(
    val id: String,
    val name: String,
    val urlTemplate: String,
    val host: String,
    val color: Long = 0xFF2F5C69,
    val custom: Boolean = false,
)

private class History {
    val urls = mutableListOf<String>()
    var index = -1
    var navStartedAt = 0L
    var programmatic = false
}

object GeckoRuntimeHolder {
    @Volatile private var runtime: GeckoRuntime? = null

    fun get(context: Context): GeckoRuntime =
        runtime ?: synchronized(this) {
            runtime ?: GeckoRuntime.create(
                context.applicationContext,
                GeckoRuntimeSettings.Builder()
                    .aboutConfigEnabled(true)
                    .javaScriptEnabled(true)
                    .build()
            ).also { runtime = it }
        }
}

class BrowserViewModel(app: Application) : AndroidViewModel(app) {

    private val runtime: GeckoRuntime = GeckoRuntimeHolder.get(app)
    private val prefs = app.getSharedPreferences("arc_browser", Context.MODE_PRIVATE)

    private val sessions = LinkedHashMap<Long, GeckoSession>()
    private val histories = HashMap<Long, History>()
    private var nextId = 1L

    var tabs by mutableStateOf<List<TabInfo>>(emptyList())
        private set

    var activeId by mutableStateOf(0L)
        private set

    var overlay by mutableStateOf(Overlay.None)
        private set

    private var accountReturn = Overlay.Menu

    var darkMode by mutableStateOf(prefs.getBoolean("dark", false))
        private set

    var compactPill by mutableStateOf(false)
        private set

    var pipLoading by mutableStateOf(false)
        private set

    var zoom by mutableStateOf(100)
        private set

    var desktopSite by mutableStateOf(false)
        private set

    var searchText by mutableStateOf("")
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

    /** Bumped when a download lands so the chip can do its little bounce. */
    var chipPulse by mutableStateOf(0)
        private set

    private var downloadsReturn = Overlay.Menu
    private var nextDownloadId = 1L
    private val downloadJobs = HashMap<Long, Job>()
    private val downloadStreams = HashMap<Long, InputStream>()
    private val saveTargets = HashMap<Long, SaveTarget>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val webExecutor by lazy { GeckoWebExecutor(runtime) }

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
            val canceled = downloads.size - done.size
            if (done.isEmpty()) return "${downloads.size} canceled"
            val total = done.sumOf { it.size }
            val tail = if (canceled > 0) " · $canceled canceled" else ""
            return "${done.size} file${if (done.size == 1) "" else "s"} · ${formatBytes(total)}$tail"
        }

    val engines: List<SearchEngine>
        get() = BUILTIN_ENGINES + customEngines

    val currentEngine: SearchEngine
        get() = engines.firstOrNull { it.id == searchEngineId } ?: BUILTIN_ENGINES.first()

    var editingOverlayHeight by mutableStateOf(false)

    val activeTab: TabInfo? get() = tabs.firstOrNull { it.id == activeId }
    val activeSession: GeckoSession? get() = sessions[activeId]

    val domain: String
        get() {
            val t = activeTab ?: return "example.com"
            if (t.isHome || t.url.isBlank()) return "new tab"
            return hostOf(t.url)
        }

    init {
        val id = createSession()
        activeId = id
        tabs = listOf(TabInfo(id))
    }

    private fun createSession(): Long {
        val id = nextId++
        val session = GeckoSession()
        session.open(runtime)
        sessions[id] = session
        histories[id] = History()
        attachDelegates(id, session)
        return id
    }

    private fun idOf(session: GeckoSession): Long =
        sessions.entries.firstOrNull { it.value === session }?.key ?: -1L

    private fun updateTab(id: Long, transform: (TabInfo) -> TabInfo) {
        tabs = tabs.map { if (it.id == id) transform(it) else it }
    }

    private fun attachDelegates(id: Long, session: GeckoSession) {
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                // Every fresh session reports about:blank before any real load.
                if (isBlankUrl(url)) return
                recordNavigation(id, url)
                updateTab(id) { it.copy(url = url, isHome = false, loading = true) }
                if (id == activeId) {
                    pipLoading = true
                    compactPill = false
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                updateTab(id) { if (it.isHome) it else it.copy(loading = false) }
                if (id == activeId) {
                    pipLoading = false
                    if (!success && activeTab?.isHome != true) showToast("Couldn't load the page")
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                if (id == activeId && progress >= 100) pipLoading = false
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                if (title.isNullOrBlank()) return
                updateTab(id) { if (it.isHome) it else it.copy(title = title) }
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
                val url = response.uri
                val origin = if (id == activeId) recentTapOrigin() else null
                offerDownload(
                    PendingDownload(name, size, mime, host, url, body, origin?.first, origin?.second)
                )
            }
        }

        session.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                onPageScroll(id, scrollY)
            }
        }
    }

    // ---- scrolling chrome -------------------------------------------------

    private var lastScrollY = 0

    private fun onPageScroll(id: Long, y: Int) {
        if (id != activeId) return
        val tab = activeTab ?: return
        if (overlay != Overlay.None || tab.isHome) {
            lastScrollY = y
            if (compactPill) compactPill = false
            return
        }
        val delta = y - lastScrollY
        lastScrollY = y
        when {
            y <= 6 -> compactPill = false
            delta > 2 -> compactPill = true
            delta < -2 -> compactPill = false
        }
    }

    fun expandPill() {
        compactPill = false
        lastScrollY = 0
    }

    // ---- navigation -------------------------------------------------------

    private fun recordNavigation(id: Long, url: String) {
        val h = histories.getOrPut(id) { History() }
        val now = SystemClock.uptimeMillis()
        val current = h.urls.getOrNull(h.index)
        val recent = now - h.navStartedAt < 1500
        when {
            current == url -> Unit
            recent || h.programmatic -> {
                if (h.index < 0) {
                    h.urls.add(url)
                    h.index = 0
                } else {
                    h.urls[h.index] = url
                }
            }
            else -> {
                while (h.urls.size > h.index + 1) h.urls.removeAt(h.urls.size - 1)
                h.urls.add(url)
                h.index = h.urls.size - 1
            }
        }
        h.programmatic = false
        syncHistoryFlags(id)
    }

    private fun syncHistoryFlags(id: Long) {
        val h = histories[id] ?: return
        updateTab(id) {
            it.copy(canGoBack = h.index > 0, canGoForward = h.index < h.urls.size - 1)
        }
    }

    fun submit(input: String) {
        val text = input.trim()
        if (text.isEmpty()) return
        cancelDownloadPrompt()
        dismissContextMenu()
        val url = toUrl(text)
        val tab = activeTab ?: return
        val h = histories.getOrPut(tab.id) { History() }
        while (h.urls.size > h.index + 1) h.urls.removeAt(h.urls.size - 1)
        h.urls.add(url)
        h.index = h.urls.size - 1
        h.navStartedAt = SystemClock.uptimeMillis()
        h.programmatic = false
        syncHistoryFlags(tab.id)

        sessions[tab.id]?.loadUri(url)
        updateTab(tab.id) { it.copy(url = url, isHome = false, title = hostOf(url)) }
        closeOverlay()
        expandPill()
    }

    fun goBack() {
        val tab = activeTab ?: return
        val h = histories[tab.id] ?: return
        if (h.index <= 0) return
        h.index--
        h.navStartedAt = SystemClock.uptimeMillis()
        h.programmatic = true
        sessions[tab.id]?.loadUri(h.urls[h.index])
        syncHistoryFlags(tab.id)
        expandPill()
    }

    fun goForward() {
        val tab = activeTab ?: return
        val h = histories[tab.id] ?: return
        if (h.index >= h.urls.size - 1) return
        h.index++
        h.navStartedAt = SystemClock.uptimeMillis()
        h.programmatic = true
        sessions[tab.id]?.loadUri(h.urls[h.index])
        syncHistoryFlags(tab.id)
        expandPill()
    }

    /* ---- history and tab gestures --------------------------------------- */

    private var navNoticeJob: Job? = null

    /**
     * Back/forward via a swipe: the page moves when there is somewhere to go,
     * and either way the pill briefly hands its footprint to a notice, exactly
     * like the immersive-mode coach mark.
     */
    fun navGesture(forward: Boolean) {
        val tab = activeTab ?: return
        val h = histories.getOrPut(tab.id) { History() }
        val target = h.urls.getOrNull(h.index + if (forward) 1 else -1)
        if (target != null) {
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

    fun reload() {
        val tab = activeTab ?: return
        if (tab.isHome) return
        sessions[tab.id]?.reload()
    }

    fun stop() {
        val tab = activeTab ?: return
        sessions[tab.id]?.stop()
        pipLoading = false
    }

    fun toggleDesktopSite() {
        desktopSite = !desktopSite
        val s = activeSession ?: return
        s.settings.userAgentMode =
            if (desktopSite) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        s.settings.viewportMode =
            if (desktopSite) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
            else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        activeTab?.let { if (!it.isHome) s.reload() }
    }

    fun changeZoom(delta: Int) {
        zoom = (zoom + delta).coerceIn(50, 200)
    }

    fun resetZoom() {
        zoom = 100
    }

    fun copyCurrentUrl() {
        val url = activeTab?.url.orEmpty()
        if (url.isBlank()) return
        val cm = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("url", url))
        showToast("Link copied")
    }

    fun shareCurrentUrl() {
        val url = activeTab?.url.orEmpty()
        if (url.isBlank()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        val chooser = Intent.createChooser(send, "Share page").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(chooser)
    }

    fun openUrlExternally(url: String) {
        if (url.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            getApplication<Application>().startActivity(intent)
        } catch (_: Throwable) {
            showToast("No app can open this link")
        }
    }

    fun installExtension(uri: Uri) {
        val app = getApplication<Application>()
        try {
            // GeckoView installs from a URL/file path, so stage the picked XPI in cache.
            val staged = java.io.File(app.cacheDir, "addon-${System.currentTimeMillis()}.xpi")
            app.contentResolver.openInputStream(uri)?.use { input ->
                staged.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Cannot read the chosen file")
            installFromUri(Uri.fromFile(staged))
        } catch (t: Throwable) {
            showToast("Add-on install failed")
        }
    }

    fun installExtensionFromUrl(url: String) {
        if (url.isBlank()) return
        installFromUri(Uri.parse(url))
    }

    private fun installFromUri(uri: Uri) {
        try {
            runtime.webExtensionController.install(uri.toString()).accept(
                { showToast("Add-on installed") },
                { showToast("Add-on install failed") },
            )
            showToast("Installing add-on…")
        } catch (t: Throwable) {
            showToast("Add-on install failed")
        }
    }

    // ---- downloads --------------------------------------------------------

    /** Where the last page tap landed, in dp; the flight starts from here. */
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
        pendingDownload?.body?.safeClose()
        pendingDownload = pending
    }

    fun cancelDownloadPrompt() {
        val p = pendingDownload ?: return
        p.body?.safeClose()
        pendingDownload = null
    }

    fun confirmDownloadPrompt() {
        val p = pendingDownload ?: return
        pendingDownload = null
        val id = nextDownloadId++
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
        downloads = listOf(item) + downloads
        if (p.originX != null && p.originY != null) {
            flight = DownloadFlight(id, item.name, p.originX, p.originY)
        } else {
            chipPulse++
        }
        streamDownload(id, p.body, p.url, mime)
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

    private fun streamDownload(id: Long, body: InputStream?, url: String, mime: String) {
        val job = scope.launch {
            val item = downloads.firstOrNull { it.id == id && it.status == DownloadStatus.Downloading }
                ?: return@launch
            var input: InputStream? = body
            var target: SaveTarget? = null
            var finished = false
            try {
                val sink = withContext(Dispatchers.IO) { createSaveTarget(item.name, mime) }
                target = sink
                saveTargets[id] = sink
                if (input == null) {
                    input = withContext(Dispatchers.IO) { openUrlStream(url) }
                }
                val stream = input ?: throw IllegalStateException("No download stream")
                downloadStreams[id] = stream
                withContext(Dispatchers.IO) {
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var last = 0L
                    while (true) {
                        ensureActive()
                        val read = stream.read(buffer)
                        if (read < 0) break
                        sink.output.write(buffer, 0, read)
                        done += read
                        val now = SystemClock.uptimeMillis()
                        if (now - last >= 120L) {
                            last = now
                            withContext(Dispatchers.Main) { updateDownloadProgress(id, done) }
                        }
                    }
                    sink.output.flush()
                }
                val uri = withContext(Dispatchers.IO) {
                    sink.finish()
                    sink.uriString
                }
                finished = true
                markDownloadDone(id, uri)
            } catch (_: CancellationException) {
                // cancelDownload() already marked the row and closed the streams.
            } catch (_: Throwable) {
                if (downloads.firstOrNull { it.id == id }?.status == DownloadStatus.Downloading) {
                    markDownloadCanceled(id)
                    showToast("Download failed")
                }
            } finally {
                input?.safeClose()
                if (!finished) target?.discard()
                downloadStreams.remove(id)
                saveTargets.remove(id)
                downloadJobs.remove(id)
            }
        }
        downloadJobs[id] = job
    }

    fun cancelDownload(id: Long) {
        val item = downloads.firstOrNull { it.id == id } ?: return
        if (item.status != DownloadStatus.Downloading) return
        markDownloadCanceled(id)
        downloadJobs.remove(id)?.cancel()
        downloadStreams.remove(id)?.safeClose()
        saveTargets.remove(id)?.discard()
    }

    /** Swipe-up on the chip: cancel the newest transfer and clear its row. */
    fun cancelNewestDownload(): DownloadItem? {
        val item = activeDownloads.firstOrNull() ?: return null
        removeDownload(item.id)
        showToast("Canceled ${item.name}")
        return item
    }

    fun retryDownload(id: Long) {
        val item = downloads.firstOrNull { it.id == id } ?: return
        if (item.status == DownloadStatus.Downloading) return
        downloads = downloads.map {
            if (it.id == id) it.copy(
                status = DownloadStatus.Downloading,
                done = 0L,
                ended = 0L,
                localUri = null,
                started = System.currentTimeMillis(),
            ) else it
        }
        streamDownload(id, null, item.url, item.mime)
    }

    fun removeDownload(id: Long) {
        cancelDownload(id)
        downloads = downloads.filterNot { it.id == id }
    }

    fun clearDownloads() {
        downloads.forEach { cancelDownload(it.id) }
        downloads = emptyList()
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

    private fun updateDownloadProgress(id: Long, done: Long) {
        downloads = downloads.map {
            if (it.id == id && it.status == DownloadStatus.Downloading) {
                it.copy(done = if (it.size > 0) done.coerceAtMost(it.size) else done)
            } else it
        }
    }

    private fun markDownloadDone(id: Long, uri: String?) {
        val item = downloads.firstOrNull { it.id == id } ?: return
        val finalSize = if (item.size > 0) item.size else item.done
        downloads = downloads.map {
            if (it.id == id) it.copy(
                status = DownloadStatus.Done,
                done = finalSize,
                size = finalSize,
                ended = System.currentTimeMillis(),
                localUri = uri,
            ) else it
        }
        if (flight == null) chipPulse++
        // No completion popup: the live chip already announces the finish.
    }

    private fun markDownloadCanceled(id: Long) {
        downloads = downloads.map {
            if (it.id == id && it.status == DownloadStatus.Downloading) it.copy(
                status = DownloadStatus.Canceled,
                ended = System.currentTimeMillis(),
            ) else it
        }
    }

    fun finishFlight(id: Long) {
        val f = flight ?: return
        if (f.id != id) return
        flight = null
        chipPulse++
    }

    private fun openUrlStream(url: String): InputStream {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) ArcBrowser")
        conn.setRequestProperty("Accept", "*/*")
        val code = conn.responseCode
        if (code !in 200..299) throw IllegalStateException("HTTP $code")
        return conn.inputStream
    }

    private fun downloadsDir(): File {
        val app = getApplication<Application>()
        val external = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        return external ?: File(app.filesDir, "Downloads")
    }

    @Suppress("DEPRECATION")
    private fun createSaveTarget(name: String, mime: String): SaveTarget {
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = app.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Couldn't create the download")
            val out = try {
                resolver.openOutputStream(uri)
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            } ?: run {
                resolver.delete(uri, null, null)
                throw IllegalStateException("Couldn't open the download")
            }
            return SaveTarget(out, resolver, uri, null, uri.toString())
        }
        val dir = downloadsDir()
        if (!dir.exists()) dir.mkdirs()
        val file = uniqueFile(dir, name)
        val out = FileOutputStream(file)
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        return SaveTarget(out, null, null, file, uri.toString())
    }

    private fun uniqueFile(dir: File, name: String): File {
        val first = File(dir, name)
        if (!first.exists()) return first
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (true) {
            val candidate = File(dir, "$base ($i)${if (ext.isBlank()) "" else ".$ext"}")
            if (!candidate.exists()) return candidate
            i++
        }
    }

    // ---- tabs -------------------------------------------------------------

    fun newTab() {
        cancelDownloadPrompt()
        dismissContextMenu()
        val id = createSession()
        tabs = tabs + TabInfo(id)
        activeId = id
        closeOverlay()
        expandPill()
    }

    fun selectTab(id: Long) {
        if (!sessions.containsKey(id)) return
        cancelDownloadPrompt()
        dismissContextMenu()
        tabSwitch = null
        activeId = id
        closeOverlay()
        expandPill()
    }

    fun closeTab(id: Long) {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx < 0) return
        cancelDownloadPrompt()
        dismissContextMenu()
        val closing = sessions.remove(id)
        histories.remove(id)
        closing?.close()

        val remaining = tabs.filterNot { it.id == id }
        if (remaining.isEmpty()) {
            val fresh = createSession()
            tabs = listOf(TabInfo(fresh))
            activeId = fresh
        } else {
            tabs = remaining
            if (activeId == id) {
                activeId = remaining[getSafeIndex(idx, remaining.size)].id
            }
        }
    }

    private fun getSafeIndex(index: Int, size: Int) = (index - 1).coerceAtLeast(0).coerceAtMost(size - 1)

    fun closeOverlay() {
        cancelDownloadPrompt()
        dismissContextMenu()
        overlay = Overlay.None
        compactPill = false
    }

    fun openSearch(prefill: String? = null) {
        cancelDownloadPrompt()
        dismissContextMenu()
        clearNavNotice()
        searchText = prefill.orEmpty()
        overlay = Overlay.Search
        compactPill = false
    }

    fun openMenu() {
        cancelDownloadPrompt()
        dismissContextMenu()
        overlay = Overlay.Menu
        compactPill = false
    }

    fun toggleMenu() {
        if (overlay == Overlay.Menu || overlay == Overlay.Settings ||
            overlay == Overlay.SiteSettings || overlay == Overlay.Account
        ) {
            closeOverlay()
        } else {
            openMenu()
        }
    }

    fun openTabs() {
        cancelDownloadPrompt()
        dismissContextMenu()
        overlay = Overlay.Tabs
        compactPill = false
    }

    fun toggleTabs() {
        if (overlay == Overlay.Tabs) closeOverlay() else openTabs()
    }

    fun openSettings() {
        cancelDownloadPrompt()
        dismissContextMenu()
        overlay = Overlay.Settings
        compactPill = false
    }

    fun openSiteSettings() {
        cancelDownloadPrompt()
        overlay = Overlay.SiteSettings
    }

    fun openDownloads() {
        val current = overlay
        if (current == Overlay.Downloads) return
        downloadsReturn = when (current) {
            Overlay.Menu, Overlay.Settings, Overlay.SiteSettings, Overlay.Account -> current
            else -> Overlay.None
        }
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

    fun openAccount() {
        cancelDownloadPrompt()
        dismissContextMenu()
        accountReturn = when (overlay) {
            Overlay.Settings -> Overlay.Settings
            Overlay.Menu -> Overlay.Menu
            else -> Overlay.Menu
        }
        overlay = Overlay.Account
    }

    /** Handles the system back button; returns true when it was consumed. */
    fun onBackPressed(): Boolean {
        if (pendingDownload != null) {
            cancelDownloadPrompt()
            return true
        }
        if (contextMenu != null) {
            dismissContextMenu()
            return true
        }
        return when (overlay) {
            Overlay.None -> false
            Overlay.Account -> {
                overlay = accountReturn
                true
            }
            Overlay.Settings, Overlay.SiteSettings -> {
                overlay = Overlay.Menu
                true
            }
            Overlay.Downloads -> {
                closeDownloads()
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
    }

    fun onSearchTextChange(value: String) {
        searchText = value
    }

    fun onSettingsSearchChange(value: String) {
        settingsSearch = value
    }

    // ---- search engines ---------------------------------------------------

    fun selectEngine(id: String) {
        if (engines.none { it.id == id }) return
        searchEngineId = id
        prefs.edit().putString("search_engine", id).apply()
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
        if (t.startsWith("http://") || t.startsWith("https://") || t.startsWith("about:") ||
            t.startsWith("file:")
        ) return t
        if (t.startsWith("www.")) return "https://$t"
        val looksLikeHost = !t.contains(" ") &&
            (t.contains(".") || t == "localhost") &&
            !t.endsWith(".")
        return if (looksLikeHost) "https://$t" else
            currentEngine.urlTemplate.replace("%s", Uri.encode(t))
    }

    fun showToast(message: String) {
        // The manager shows live state; a floating toast on top of it only competes.
        if (overlay == Overlay.Downloads) return
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
        getApplication<Application>().startActivity(chooser)
    }

    fun clearToast() {
        toastMessage = null
    }

    fun openAddonPicker() {
        // The Activity launches the document picker; see MainActivity.
        addonPickerRequest.value = true
    }

    val addonPickerRequest = mutableStateOf(false)

    fun consumeAddonPickerRequest() {
        addonPickerRequest.value = false
    }

    fun openInNewTab(url: String) {
        if (url.isBlank()) return
        dismissContextMenu()
        val id = createSession()
        tabs = tabs + TabInfo(id, url = url, isHome = false, title = hostOf(url))
        val h = histories.getOrPut(id) { History() }
        h.urls.add(url)
        h.index = 0
        h.navStartedAt = SystemClock.uptimeMillis()
        activeId = id
        sessions[id]?.loadUri(url)
        overlay = Overlay.None
        expandPill()
    }

    override fun onCleared() {
        pendingDownload?.body?.safeClose()
        pendingDownload = null
        downloadJobs.values.forEach { it.cancel() }
        downloadJobs.clear()
        downloadStreams.values.forEach { it.safeClose() }
        downloadStreams.clear()
        saveTargets.values.forEach { it.discard() }
        saveTargets.clear()
        scope.cancel()
        sessions.values.forEach { it.close() }
        sessions.clear()
        super.onCleared()
    }

    companion object {
        fun isBlankUrl(url: String): Boolean =
            url.isEmpty() || url == "about:blank" || url.startsWith("about:blank#")

        val BUILTIN_ENGINES = listOf(
            SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s", "duckduckgo.com", 0xFFDE5833),
            SearchEngine("google", "Google", "https://www.google.com/search?q=%s", "google.com", 0xFF4285F4),
            SearchEngine("bing", "Bing", "https://www.bing.com/search?q=%s", "bing.com", 0xFF008373),
            SearchEngine("brave", "Brave", "https://search.brave.com/search?q=%s", "search.brave.com", 0xFFFB542B),
            SearchEngine("ecosia", "Ecosia", "https://www.ecosia.org/search?q=%s", "ecosia.org", 0xFF008009),
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
            val ext = downExt(name)
            return when (ext) {
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
                else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                    ?: "application/octet-stream"
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

/* ------------------------------------------------------- download helpers */

/** A partially written file plus the bits needed to publish or delete it. */
private class SaveTarget(
    val output: OutputStream,
    private val resolver: android.content.ContentResolver?,
    private val mediaUri: Uri?,
    private val file: File?,
    val uriString: String,
) {
    private var discarded = false

    fun finish() {
        output.flush()
        output.close()
        if (mediaUri != null && resolver != null) {
            val values = ContentValues()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(mediaUri, values, null, null)
        }
    }

    fun discard() {
        if (discarded) return
        discarded = true
        output.safeClose()
        if (mediaUri != null && resolver != null) {
            try {
                resolver.delete(mediaUri, null, null)
            } catch (_: Throwable) {
                // The row may already be gone.
            }
        }
        file?.delete()
    }
}

private fun InputStream.safeClose() {
    try {
        close()
    } catch (_: Throwable) {
        // Nothing left to do.
    }
}

private fun OutputStream.safeClose() {
    try {
        close()
    } catch (_: Throwable) {
        // Nothing left to do.
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

