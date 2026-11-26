package com.example.geckobrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** One tab as it survives a restart; the session blob is stored beside it. */
data class TabRecord(
    val id: Long,
    val url: String,
    val title: String,
    val desktopMode: Boolean = false,
    val secure: Boolean = false,
    val private: Boolean = false,
    /** Which space the tab belongs to. Defaults to the Personal space so v1 files still load. */
    val spaceId: UUID = Space.PERSONAL_ID,
)

/** One pinned page as it survives a restart. [key] is the canonical URL. */
data class PinRecord(
    val key: String,
    val url: String,
    val title: String,
)

data class TabListRecord(val activeId: Long, val tabs: List<TabRecord>)

/**
 * Small private JSON/state files. Preferences live in DataStore; these are the
 * payloads that are too big or too opaque for a preference value.
 */
object BrowserFiles {

    private const val LIST_VERSION = 2

    private fun browserDir(context: Context): File =
        File(context.filesDir, "browser").apply { if (!exists()) mkdirs() }

    private fun sessionFile(context: Context, id: Long): File =
        File(browserDir(context), "session-$id.json")

    fun downloadsFile(context: Context): File = File(browserDir(context), "downloads.json")

    private fun tabsFile(context: Context): File = File(browserDir(context), "tabs.json")

    fun pinsFile(context: Context): File = File(browserDir(context), "pins.json")

    fun spacesFile(context: Context): File = File(browserDir(context), "spaces.json")

    fun essentialsFile(context: Context): File = File(browserDir(context), "essentials.json")

    fun pinNodesFile(context: Context): File = File(browserDir(context), "pin-nodes.json")

    /** Persistent favicon cache, keyed by the normalised host. */
    fun faviconFile(context: Context, key: String): File =
        File(browserDir(context), "favicon-${key.hashCode()}.png")

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
                        put("private", t.private)
                        put("spaceId", t.spaceId.toString())
                    })
                }
            })
        }
        writeText(tabsFile(context), root.toString())
    }

    fun savePins(context: Context, pins: List<PinRecord>) {
        val arr = JSONArray()
        pins.forEach { p ->
            arr.put(JSONObject().apply {
                put("key", p.key)
                put("url", p.url)
                put("title", p.title)
            })
        }
        writeText(pinsFile(context), arr.toString())
    }

    fun loadPins(context: Context): List<PinRecord> {
        val raw = readText(pinsFile(context)) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    // Legacy files stored an origin in "origin"; the view model
                    // promotes those to URL keys when it restores the pins.
                    val key = o.optString("key").ifBlank { o.optString("origin") }
                    if (key.isBlank()) continue
                    add(PinRecord(key, o.optString("url"), o.optString("title").ifBlank { key }))
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun loadTabList(context: Context): TabListRecord? {
        val raw = readText(tabsFile(context)) ?: return null
        return try {
            val root = JSONObject(raw)
            // v1 files predate spaces: they land in the default Personal space.
            val version = root.optInt("version", 0)
            if (version != LIST_VERSION && version != LIST_VERSION - 1) return null
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
                            private = o.optBoolean("private", false),
                            spaceId = o.optString("spaceId")
                                .ifBlank { Space.PERSONAL_ID.toString() }
                                .let(UUID::fromString),
                        )
                    )
                }
            }
            if (tabs.isEmpty()) null else TabListRecord(root.optLong("activeId", tabs.first().id), tabs)
        } catch (_: Throwable) {
            null
        }
    }

    /* -------------------------- spaces, essentials, pin nodes */

    private fun optUuid(o: JSONObject, key: String): UUID? =
        o.optString(key).takeUnless { it.isBlank() }?.let(UUID::fromString)

    fun saveSpaces(context: Context, spaces: List<Space>) {
        writeText(spacesFile(context), JSONArray().apply {
            spaces.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id.toString())
                    put("name", s.name)
                    put("letter", s.letter)
                    put("colorA", s.colorA)
                    put("colorB", s.colorB)
                    put("sortIndex", s.sortIndex)
                    put("icon", s.icon)
                    put("container", s.container)
                    put("collapsed", s.collapsed)
                    put("updatedAt", s.updatedAt)
                    put("deletedAt", s.deletedAt ?: JSONObject.NULL)
                })
            }
        }.toString())
    }

    fun loadSpaces(context: Context): List<Space> = readText(spacesFile(context))?.let { raw ->
        try {
            buildList {
                for (i in 0 until JSONArray(raw).length()) {
                    val o = JSONArray(raw).optJSONObject(i) ?: continue
                    val id = optUuid(o, "id") ?: continue
                    add(
                        Space(
                            id = id,
                            name = o.optString("name").ifBlank { id.toString() },
                            letter = o.optString("letter").ifBlank { id.toString().take(2) },
                            colorA = o.optLong("colorA", 0L),
                            colorB = o.optLong("colorB", 0L),
                            sortIndex = o.optInt("sortIndex", 0),
                            icon = o.optString("icon").ifBlank { "plus" },
                            container = o.optString("container").ifBlank { "none" },
                            collapsed = o.optBoolean("collapsed", true),
                            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                            deletedAt = o.optLong("deletedAt", 0L).takeIf { it > 0 },
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    } ?: emptyList()

    fun saveEssentials(context: Context, essentials: List<Essential>) {
        writeText(essentialsFile(context), JSONArray().apply {
            essentials.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id.toString())
                    put("spaceId", e.spaceId.toString())
                    put("url", e.url)
                    put("title", e.title)
                    put("faviconUrl", e.faviconUrl ?: JSONObject.NULL)
                    put("sortIndex", e.sortIndex)
                    put("updatedAt", e.updatedAt)
                    put("deletedAt", e.deletedAt ?: JSONObject.NULL)
                })
            }
        }.toString())
    }

    fun loadEssentials(context: Context): List<Essential> = readText(essentialsFile(context))?.let { raw ->
        try {
            buildList {
                for (i in 0 until JSONArray(raw).length()) {
                    val o = JSONArray(raw).optJSONObject(i) ?: continue
                    val id = optUuid(o, "id") ?: continue
                    add(
                        Essential(
                            id = id,
                            spaceId = optUuid(o, "spaceId") ?: Space.PERSONAL_ID,
                            url = o.optString("url"),
                            title = o.optString("title"),
                            faviconUrl = o.optString("faviconUrl").takeUnless { it.isBlank() || it == "null" },
                            sortIndex = o.optInt("sortIndex", 0),
                            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                            deletedAt = o.optLong("deletedAt", 0L).takeIf { it > 0 },
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    } ?: emptyList()

    fun savePinNodes(context: Context, nodes: List<PinNode>) {
        val json = JSONArray().apply {
            nodes.forEach { n ->
                put(JSONObject().apply {
                    put("id", n.id.toString())
                    put("spaceId", n.spaceId.toString())
                    put("parentId", n.parentId?.toString() ?: JSONObject.NULL)
                    put("type", n.type.name)
                    put("url", n.url)
                    put("title", n.title)
                    put("faviconUrl", n.faviconUrl ?: JSONObject.NULL)
                    put("sortIndex", n.sortIndex)
                    put("collapsed", n.collapsed)
                    put("depth", n.depth)
                    put("updatedAt", n.updatedAt)
                    put("deletedAt", n.deletedAt ?: JSONObject.NULL)
                })
            }
        }.toString()
        writeText(pinNodesFile(context), json)
        // Keep a prefs copy too: a failed file write must never lose folders.
        runCatching {
            context.getSharedPreferences("arc_browser", Context.MODE_PRIVATE)
                .edit().putString("pin_nodes_backup", json).apply()
        }
        android.util.Log.i("HZ-Pins", "saved ${nodes.size} nodes")
    }

    /**
     * Try the file first, then the prefs copy — including when the file exists
     * but is truncated/corrupt. A valid empty array is a valid (empty) tree.
     */
    fun loadPinNodes(context: Context): List<PinNode> {
        val backup = context.getSharedPreferences("arc_browser", Context.MODE_PRIVATE)
            .getString("pin_nodes_backup", null)
        for (raw in listOfNotNull(readText(pinNodesFile(context)), backup)) {
            val parsed = runCatching { parsePinNodes(raw) }.getOrNull()
            if (parsed != null) {
                android.util.Log.i("HZ-Pins", "loaded ${parsed.size} nodes")
                return parsed
            }
        }
        android.util.Log.w("HZ-Pins", "no readable pin tree")
        return emptyList()
    }

    private fun parsePinNodes(raw: String): List<PinNode> {
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = optUuid(o, "id") ?: continue
                add(
                    PinNode(
                        id = id,
                        spaceId = optUuid(o, "spaceId") ?: Space.PERSONAL_ID,
                        parentId = optUuid(o, "parentId"),
                        type = PinType.valueOf(o.optString("type").ifBlank { "PIN" }),
                        url = o.optString("url"),
                        title = o.optString("title"),
                        faviconUrl = o.optString("faviconUrl").takeUnless { it.isBlank() || it == "null" },
                        sortIndex = o.optInt("sortIndex", 0),
                        collapsed = o.optBoolean("collapsed", false),
                        depth = o.optInt("depth", 0),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                        deletedAt = o.optLong("deletedAt", 0L).takeIf { it > 0 },
                    )
                )
            }
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
