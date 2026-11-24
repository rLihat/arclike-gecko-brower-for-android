package com.example.geckobrowser

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One encrypted Sync 1.5 record holding every Zen-style space object. Mirrors
 * Zen's "Workspaces & Tabs" engine: last-writer-wins on [updatedAt], with soft
 * delete tombstones winning over missing rows. No passwords/logins are included.
 */
data class SpaceSyncSnapshot(
    val spaces: List<Space> = emptyList(),
    val essentials: List<Essential> = emptyList(),
    val pinNodes: List<PinNode> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): String = JSONObject().apply {
        put("version", 1)
        put("updatedAt", updatedAt)
        put("spaces", JSONArray().apply {
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
        })
        put("essentials", JSONArray().apply {
            essentials.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id.toString())
                    put("url", e.url)
                    put("title", e.title)
                    put("faviconUrl", e.faviconUrl ?: JSONObject.NULL)
                    put("sortIndex", e.sortIndex)
                    put("updatedAt", e.updatedAt)
                    put("deletedAt", e.deletedAt ?: JSONObject.NULL)
                })
            }
        })
        put("pins", JSONArray().apply {
            pinNodes.forEach { n ->
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
        })
    }.toString()

    companion object {
        private fun optUuid(o: JSONObject, key: String): UUID? =
            o.optString(key).takeUnless { it.isBlank() }?.let(UUID::fromString)

        private fun tombstone(o: JSONObject): Long? = o.optLong("deletedAt", 0L).takeIf { it > 0 }

        fun fromJson(raw: String): SpaceSyncSnapshot? {
          return try {
            val root = JSONObject(raw)
            if (root.optInt("version", 0) != 1) return null
            val spaces = mutableListOf<Space>()
            val arr = root.optJSONArray("spaces") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = optUuid(o, "id") ?: continue
                spaces += Space(
                    id = id,
                    name = o.optString("name").ifBlank { id.toString() },
                    letter = o.optString("letter").ifBlank { id.toString().take(2) },
                    colorA = o.optLong("colorA", 0L),
                    colorB = o.optLong("colorB", 0L),
                    sortIndex = o.optInt("sortIndex", 0),
                    icon = o.optString("icon").ifBlank { "plus" },
                    container = o.optString("container").ifBlank { "none" },
                    collapsed = o.optBoolean("collapsed", false),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                    deletedAt = tombstone(o),
                )
            }
            val essentials = mutableListOf<Essential>()
            val ess = root.optJSONArray("essentials") ?: JSONArray()
            for (i in 0 until ess.length()) {
                val o = ess.optJSONObject(i) ?: continue
                val id = optUuid(o, "id") ?: continue
                essentials += Essential(
                    id = id,
                    url = o.optString("url"),
                    title = o.optString("title"),
                    faviconUrl = o.optString("faviconUrl").takeUnless { it.isBlank() || it == "null" },
                    sortIndex = o.optInt("sortIndex", 0),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                    deletedAt = tombstone(o),
                )
            }
            val pins = mutableListOf<PinNode>()
            val pinArr = root.optJSONArray("pins") ?: JSONArray()
            for (i in 0 until pinArr.length()) {
                val o = pinArr.optJSONObject(i) ?: continue
                val id = optUuid(o, "id") ?: continue
                pins += PinNode(
                    id = id,
                    spaceId = optUuid(o, "spaceId") ?: Space.PERSONAL_ID,
                    parentId = optUuid(o, "parentId"),
                    type = runCatching { PinType.valueOf(o.optString("type").ifBlank { "PIN" }) }
                        .getOrDefault(PinType.PIN),
                    url = o.optString("url"),
                    title = o.optString("title"),
                    faviconUrl = o.optString("faviconUrl").takeUnless { it.isBlank() || it == "null" },
                    sortIndex = o.optInt("sortIndex", 0),
                    collapsed = o.optBoolean("collapsed", false),
                    depth = o.optInt("depth", 0),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                    deletedAt = tombstone(o),
                )
            }
            SpaceSyncSnapshot(spaces, essentials, pins, root.optLong("updatedAt", System.currentTimeMillis()))
          } catch (_: Throwable) {
            null
          }
        }

        /** Last-writer-wins per row. A tombstone at equal timestamps wins. */
        fun merge(local: SpaceSyncSnapshot, remote: SpaceSyncSnapshot): SpaceSyncSnapshot {
            val spaces = mergeById(local.spaces, remote.spaces,
                idOf = { it.id },
                pick = { a, b ->
                    if (a.updatedAt == b.updatedAt && (a.deletedAt != null) != (b.deletedAt != null)) {
                        if (a.deletedAt != null) a else b
                    } else if (a.updatedAt >= b.updatedAt) a else b
                })
            val essentials = mergeById(local.essentials, remote.essentials,
                idOf = { it.id }, pick = { a, b -> if (a.updatedAt >= b.updatedAt) a else b })
            val pins = mergeById(local.pinNodes, remote.pinNodes,
                idOf = { it.id }, pick = { a, b -> if (a.updatedAt >= b.updatedAt) a else b })
            return SpaceSyncSnapshot(spaces, essentials, pins, maxOf(local.updatedAt, remote.updatedAt))
        }

        private fun <T> mergeById(
            local: List<T>,
            remote: List<T>,
            idOf: (T) -> UUID,
            pick: (T, T) -> T,
        ): List<T> {
            val byId = LinkedHashMap<UUID, T>()
            local.forEach { byId[idOf(it)] = it }
            remote.forEach { item ->
                val id = idOf(item)
                val existing = byId[id]
                byId[id] = if (existing == null) item else pick(existing, item)
            }
            return byId.values.toList()
        }
    }
}
