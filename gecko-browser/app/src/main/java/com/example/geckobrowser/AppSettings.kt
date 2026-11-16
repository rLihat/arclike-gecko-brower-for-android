package com.example.geckobrowser

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** Everything read out of DataStore at startup, applied before the first session. */
data class SettingsSnapshot(
    val dark: Boolean = false,
    val suggestions: Boolean = true,
    val schema: Int = 0,
    /** Selected search engine id; blank on a store written before engines were saved. */
    val engine: String = "",
    val zoom: Map<String, Int> = emptyMap(),
    /** Keyed by `host|kind`, value is "allow" or "deny". */
    val permissions: Map<String, String> = emptyMap(),
    val desktop: Map<String, Boolean> = emptyMap(),
    /** Standard tracking protection unless the user switched to strict. */
    val etpStrict: Boolean = false,
    /** Whether Gecko tries to answer cookie banners on the user's behalf. */
    val cookieBanner: Boolean = false,
    /** Hosts where tracking protection is switched off. */
    val trackingExceptions: Set<String> = emptySet(),
    /** Accent preset key (see AccentPresets). Defaults to Firefox purple. */
    val accent: String = "firefox_purple",
    /** "system" | "light" | "dark". Defaults to system. */
    val appearance: String = "system",
    /** Whether external (non-web) links open in the matching app. */
    val openLinksInApps: Boolean = true,
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "arc_settings")

/**
 * Preferences storage for the browser's own settings. Lightweight, typed values
 * live in DataStore; tab/session/download payloads live in small private files
 * (see [BrowserFiles]) because they are too large for the preferences model.
 */
class AppSettings(context: Context) {

    private val appContext = context.applicationContext
    private val store = appContext.settingsStore

    suspend fun snapshot(): SettingsSnapshot {
        val p = store.data.first()
        val zoom = HashMap<String, Int>()
        val permissions = HashMap<String, String>()
        val desktop = HashMap<String, Boolean>()
        val trackingExceptions = HashSet<String>()
        for ((key, value) in p.asMap()) {
            val name = key.name
            when {
                name.startsWith(ZOOM_PREFIX) -> {
                    val host = name.removePrefix(ZOOM_PREFIX)
                    (value as? String)?.toIntOrNull()?.let { zoom[host] = it }
                }
                name.startsWith(PERM_PREFIX) -> {
                    val rest = name.removePrefix(PERM_PREFIX)
                    val host = rest.substringBefore('|')
                    val kind = rest.substringAfter('|', "")
                    if (host.isNotEmpty() && kind.isNotEmpty() && value is String) {
                        permissions["$host|$kind"] = value
                    }
                }
                name.startsWith(DESKTOP_PREFIX) -> {
                    val host = name.removePrefix(DESKTOP_PREFIX)
                    (value as? Boolean)?.let { desktop[host] = it }
                }
                name.startsWith(TRACKING_PREFIX) -> {
                    val host = name.removePrefix(TRACKING_PREFIX)
                    if (host.isNotEmpty() && value == true) trackingExceptions.add(host)
                }
            }
        }
        return SettingsSnapshot(
            dark = p[KEY_DARK] ?: false,
            suggestions = p[KEY_SUGGESTIONS] ?: true,
            schema = p[KEY_SCHEMA] ?: 0,
            engine = p[KEY_ENGINE].orEmpty(),
            zoom = zoom,
            permissions = permissions,
            desktop = desktop,
            etpStrict = p[KEY_ETP_STRICT] ?: false,
            cookieBanner = p[KEY_COOKIE_BANNER] ?: false,
            trackingExceptions = trackingExceptions,
            accent = p[KEY_ACCENT] ?: "firefox_purple",
            appearance = p[KEY_APPEARANCE] ?: if (dark) "dark" else "system",
            openLinksInApps = p[KEY_OPEN_LINKS] ?: true,
        )
    }

    suspend fun setDark(value: Boolean) = store.edit { it[KEY_DARK] = value }

    suspend fun setSuggestions(value: Boolean) = store.edit { it[KEY_SUGGESTIONS] = value }

    suspend fun setEngine(value: String) {
        if (value.isBlank()) return
        store.edit { it[KEY_ENGINE] = value }
    }

    suspend fun setSchema(value: Int) = store.edit { it[KEY_SCHEMA] = value }

    suspend fun setZoom(host: String, percent: Int) {
        if (host.isBlank()) return
        store.edit { it[stringPreferencesKey(ZOOM_PREFIX + host)] = percent.toString() }
    }

    suspend fun setPermission(host: String, kind: String, allowed: Boolean) {
        if (host.isBlank() || kind.isBlank()) return
        store.edit {
            it[stringPreferencesKey(PERM_PREFIX + host + "|" + kind)] =
                if (allowed) "allow" else "deny"
        }
    }

    suspend fun clearPermission(host: String, kind: String) {
        if (host.isBlank() || kind.isBlank()) return
        store.edit { it.remove(stringPreferencesKey(PERM_PREFIX + host + "|" + kind)) }
    }

    suspend fun setDesktop(host: String, enabled: Boolean) {
        if (host.isBlank()) return
        store.edit { it[booleanPreferencesKey(DESKTOP_PREFIX + host)] = enabled }
    }

    suspend fun setEtpStrict(strict: Boolean) = store.edit { it[KEY_ETP_STRICT] = strict }

    suspend fun setAccent(key: String) = store.edit { it[KEY_ACCENT] = key }

    suspend fun setAppearance(mode: String) = store.edit { it[KEY_APPEARANCE] = mode }

    suspend fun setOpenLinksInApps(enabled: Boolean) = store.edit { it[KEY_OPEN_LINKS] = enabled }

    suspend fun setCookieBanner(enabled: Boolean) =
        store.edit { it[KEY_COOKIE_BANNER] = enabled }

    /** Tracking protection is on by default, so only exemptions are stored. */
    suspend fun setTrackingException(host: String, exempt: Boolean) {
        if (host.isBlank()) return
        store.edit {
            if (exempt) it[booleanPreferencesKey(TRACKING_PREFIX + host)] = true
            else it.remove(booleanPreferencesKey(TRACKING_PREFIX + host))
        }
    }

    companion object {
        private val KEY_DARK = booleanPreferencesKey("dark")
        private val KEY_SUGGESTIONS = booleanPreferencesKey("search_suggestions")
        private val KEY_ENGINE = stringPreferencesKey("search_engine")
        private val KEY_SCHEMA = intPreferencesKey("schema_version")
        private val KEY_ETP_STRICT = booleanPreferencesKey("etp_strict")
        private val KEY_COOKIE_BANNER = booleanPreferencesKey("cookie_banner_handling")
    private val KEY_ACCENT = stringPreferencesKey("accent")
    private val KEY_APPEARANCE = stringPreferencesKey("appearance")
    private val KEY_OPEN_LINKS = booleanPreferencesKey("open_links_in_apps")
        private const val ZOOM_PREFIX = "zoom|"
        private const val PERM_PREFIX = "perm|"
        private const val DESKTOP_PREFIX = "desktop|"
        private const val TRACKING_PREFIX = "tracking|"
    }
}
