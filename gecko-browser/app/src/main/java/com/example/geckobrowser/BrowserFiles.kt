package com.example.geckobrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One tab as it survives a restart; the session blob is stored beside it. */
data class TabRecord(
    val id: Long,
    val url: String,
    val title: String,
    val desktopMode: Boolean = false,
    val secure: Boolean = false,
)

data class TabListRecord(val activeId: Long, val tabs: List<TabRecord>)

/**
 * Small private JSON/state files. Preferences live in DataStore; these are the
 * payloads that are too big or too opaque for a preference value.
 */
object BrowserFiles {

    private const val LIST_VERSION = 1

    private fun browserDir(context: Context): File =
        File(context.filesDir, "browser").apply { if (!exists()) mkdirs() }

    private fun sessionFile(context: Context, id: Long): File =
        File(browserDir(context), "session-$id.json")

    fun downloadsFile(context: Context): File = File(browserDir(context), "downloads.json")

    private fun tabsFile(context: Context): File = File(browserDir(context), "tabs.json")

    fun saveTabList(context: Context, tabs: List<TabRecord>, activeId: Long) {
        val root = JSONObject().apply {
            put("version", LIST_VERSION)
            put("activeId", activeId)
            put("tabs", JSONArray().apply {
                tabs.forEach { t ->
                    put(JSONObject().apply {
                        put("id", t.id)
                        put("url", t.url)
                        put("title", t.title)
                        put("desktop", t.desktopMode)
                        put("secure", t.secure)
                    })
                }
            })
        }
        writeText(tabsFile(context), root.toString())
    }

    fun loadTabList(context: Context): TabListRecord? {
        val raw = readText(tabsFile(context)) ?: return null
        return try {
            val root = JSONObject(raw)
            if (root.optInt("version", 0) != LIST_VERSION) return null
            val arr = root.optJSONArray("tabs") ?: return null
            val tabs = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optLong("id", 0L)
                    if (id <= 0L) continue
                    add(
                        TabRecord(
                            id = id,
                            url = o.optString("url"),
                            title = o.optString("title").ifBlank { "New tab" },
                            desktopMode = o.optBoolean("desktop", false),
                            secure = o.optBoolean("secure", false),
                        )
                    )
                }
            }
            if (tabs.isEmpty()) null else TabListRecord(root.optLong("activeId", tabs.first().id), tabs)
        } catch (_: Throwable) {
            null
        }
    }

    /** A [org.mozilla.geckoview.GeckoSession.SessionState] serialized with toString(). */
    fun saveSession(context: Context, id: Long, serialized: String) {
        if (serialized.isBlank()) return
        writeText(sessionFile(context, id), serialized)
    }

    fun loadSession(context: Context, id: Long): String? =
        readText(sessionFile(context, id))?.takeIf { it.isNotBlank() }

    fun pruneSessions(context: Context, keep: Set<Long>) {
        val dir = browserDir(context)
        dir.listFiles()?.forEach { file ->
            val name = file.name
            if (!name.startsWith("session-") || !name.endsWith(".json")) return@forEach
            val id = name.removePrefix("session-").removeSuffix(".json").toLongOrNull()
            if (id == null || id !in keep) file.delete()
        }
    }

    private fun writeText(file: File, text: String) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        } catch (_: Throwable) {
            // A failed write only costs one restore; never block the UI thread.
        }
    }

    private fun readText(file: File): String? = try {
        if (file.exists()) file.readText() else null
    } catch (_: Throwable) {
        null
    }
}
