package com.example.geckobrowser

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.json.JSONArray
import org.json.JSONObject

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

enum class Overlay { None, Search, Menu, Tabs, Settings, SiteSettings, Account }

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

    var settingsSearch by mutableStateOf("")
        private set

    var searchEngineId by mutableStateOf(prefs.getString("search_engine", "duckduckgo") ?: "duckduckgo")
        private set

    var customEngines by mutableStateOf(readCustomEngines())
        private set

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

    // ---- tabs -------------------------------------------------------------

    fun newTab() {
        val id = createSession()
        tabs = tabs + TabInfo(id)
        activeId = id
        closeOverlay()
        expandPill()
    }

    fun selectTab(id: Long) {
        if (!sessions.containsKey(id)) return
        activeId = id
        closeOverlay()
        expandPill()
    }

    fun closeTab(id: Long) {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx < 0) return
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
        overlay = Overlay.None
        compactPill = false
    }

    fun openSearch() {
        searchText = ""
        overlay = Overlay.Search
        compactPill = false
    }

    fun openMenu() {
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
        overlay = Overlay.Tabs
        compactPill = false
    }

    fun toggleTabs() {
        if (overlay == Overlay.Tabs) closeOverlay() else openTabs()
    }

    fun openSettings() {
        overlay = Overlay.Settings
        compactPill = false
    }

    fun openSiteSettings() {
        overlay = Overlay.SiteSettings
    }

    fun openAccount() {
        accountReturn = when (overlay) {
            Overlay.Settings -> Overlay.Settings
            Overlay.Menu -> Overlay.Menu
            else -> Overlay.Menu
        }
        overlay = Overlay.Account
    }

    /** Handles the system back button; returns true when it was consumed. */
    fun onBackPressed(): Boolean {
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
        toastMessage = message
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
