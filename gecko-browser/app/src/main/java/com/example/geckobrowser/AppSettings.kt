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

    companion object {
        private val KEY_DARK = booleanPreferencesKey("dark")
        private val KEY_SUGGESTIONS = booleanPreferencesKey("search_suggestions")
        private val KEY_ENGINE = stringPreferencesKey("search_engine")
        private val KEY_SCHEMA = intPreferencesKey("schema_version")
        private const val ZOOM_PREFIX = "zoom|"
        private const val PERM_PREFIX = "perm|"
        private const val DESKTOP_PREFIX = "desktop|"
    }
}
