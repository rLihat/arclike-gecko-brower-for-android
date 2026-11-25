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
    /** Privacy-first: live suggestions send keystrokes to the search engine. */
    val suggestions: Boolean = false,
    val schema: Int = 0,
    /** Selected search engine id; blank on a store written before engines were saved. */
    val engine: String = "",
    val zoom: Map<String, Int> = emptyMap(),
    /** Keyed by `host|kind`, value is "allow" or "deny". */
    val permissions: Map<String, String> = emptyMap(),
    val desktop: Map<String, Boolean> = emptyMap(),
    /** Strict tracking protection is the privacy-focused default. */
    val etpStrict: Boolean = true,
    /** Rejecting cookie banners is the privacy-focused default. */
    val cookieBanner: Boolean = true,
    /** Hosts where tracking protection is switched off. */
    val trackingExceptions: Set<String> = emptySet(),
    /** Accent preset key (see AccentPresets). Defaults to Dynamic. */
    val accent: String = "dynamic",
    /** "system" | "light" | "dark" | "oled". Defaults to system. */
    val appearance: String = "system",
    /** Frosted glass mode: auto | on | off | v1 (previous design). */
    val frostedGlassMode: String = "auto",
    /** Force plain tints everywhere, even when frosted glass is on. */
    val reduceTransparency: Boolean = false,
    /** "off" | "on" | "ask". Defaults to on. */
    val openLinksMode: String = "on",
    /** Subfolder inside the Downloads collection; "" = the Download root. */
    val downloadSubfolder: String = "",
    /** Short haptic acknowledgements for gestures/actions. */
    val haptics: Boolean = true,
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
            suggestions = p[KEY_SUGGESTIONS] ?: false,
            schema = p[KEY_SCHEMA] ?: 0,
            engine = p[KEY_ENGINE].orEmpty(),
            zoom = zoom,
            permissions = permissions,
            desktop = desktop,
            etpStrict = p[KEY_ETP_STRICT] ?: true,
            cookieBanner = p[KEY_COOKIE_BANNER] ?: true,
            trackingExceptions = trackingExceptions,
            accent = p[KEY_ACCENT] ?: "dynamic",
            appearance = p[KEY_APPEARANCE] ?: if (p[KEY_DARK] == true) "dark" else "system",
            frostedGlassMode = p[KEY_FROSTED_GLASS_MODE] ?: "auto",
            reduceTransparency = p[KEY_REDUCE_TRANSPARENCY] ?: false,
            openLinksMode = p[KEY_OPEN_LINKS_MODE] ?: "on",
            downloadSubfolder = sanitizeDownloadSubfolder(p[KEY_DOWNLOAD_SUBFOLDER].orEmpty()),
            haptics = p[KEY_HAPTICS] ?: true,
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

    suspend fun setFrostedGlassMode(mode: String) = store.edit { it[KEY_FROSTED_GLASS_MODE] = mode }

    suspend fun setReduceTransparency(value: Boolean) = store.edit { it[KEY_REDUCE_TRANSPARENCY] = value }

    suspend fun setOpenLinksMode(mode: String) = store.edit { it[KEY_OPEN_LINKS_MODE] = mode }

    suspend fun setDownloadSubfolder(value: String) =
        store.edit { it[KEY_DOWNLOAD_SUBFOLDER] = sanitizeDownloadSubfolder(value) }

    suspend fun setHaptics(value: Boolean) = store.edit { it[KEY_HAPTICS] = value }
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
    private val KEY_FROSTED_GLASS_MODE = stringPreferencesKey("frosted_glass_mode")
    private val KEY_REDUCE_TRANSPARENCY = booleanPreferencesKey("reduce_transparency")
    private val KEY_OPEN_LINKS_MODE = stringPreferencesKey("open_links_mode")
    private val KEY_DOWNLOAD_SUBFOLDER = stringPreferencesKey("download_subfolder")
    private val KEY_HAPTICS = booleanPreferencesKey("haptics")
        private const val ZOOM_PREFIX = "zoom|"
        private const val PERM_PREFIX = "perm|"
        private const val DESKTOP_PREFIX = "desktop|"
        private const val TRACKING_PREFIX = "tracking|"
    }
}
