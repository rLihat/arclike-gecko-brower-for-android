package com.example.geckobrowser

import java.util.UUID

/* ---------------------------------------------------------- spaces (phase 1) */

/**
 * User-created space. Open tabs carry a spaceId; spaces appear as avatars on
 * the nav pill. [updatedAt] drives the "sorted by recently used" default; the
 * user may override ordering via [sortIndex].
 */
data class Space(
    val id: UUID,
    val name: String,
    /** Avatar initial, shown when there is no favicon/letter to load. */
    val letter: String,
    /** Avatar gradient start (ARGB). */
    val colorA: Int,
    /** Avatar gradient end (ARGB). */
    val colorB: Int,
    val sortIndex: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    /** Soft-delete timestamp; non-null = tombstoned. */
    val deletedAt: Long? = null,
) {
    companion object {
        /**
         * Fixed id of the default "Personal" space. Chosen as a constant so the
         * backfill in `BrowserViewModel` can recreate it and so any pre-spaces
         * tab can be stamped onto it without needing the spaces list loaded.
         */
        val PERSONAL_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }
}

/**
 * Page pinned globally by URL (the hardened shape of the existing `PinRecord`).
 * Hard cap [MAX] per the spec.
 */
data class Essential(
    val id: UUID,
    /** Canonical URL (`pinKeyOf`), unique among active essentials. */
    val url: String,
    val title: String,
    val faviconUrl: String? = null,
    val sortIndex: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
) {
    companion object {
        /** Maximum number of active essentials at once. */
        const val MAX = 12
    }
}

enum class PinType { PIN, FOLDER }

/**
 * A node in a space's pinned tree. A `PIN` is a page; a `FOLDER` nests other
 * nodes. Folders nest to at most [MAX_DEPTH] below the space's top level
 * (depth 0).
 */
data class PinNode(
    val id: UUID,
    val spaceId: UUID,
    /** Null = top level of the space. */
    val parentId: UUID? = null,
    val type: PinType,
    /** PIN only: the canonical URL being pinned. Folders leave it empty. */
    val url: String = "",
    val title: String = "",
    val faviconUrl: String? = null,
    val sortIndex: Int = 0,
    /** FOLDER only: collapsed state. */
    val collapsed: Boolean = false,
    /** 0 = top level; enforced <= MAX_DEPTH. */
    val depth: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
) {
    companion object {
        const val MAX_DEPTH = 3
    }
}

/* -------------------------------------------------- TabInfo gains spaceId */

// Intended integration (presented, not edited into existing files):
//
// data class TabInfo(
//     val id: Long,
//     val title: String = "New tab",
//     val url: String = "",
//     val isHome: Boolean = true,
//     ...
//     val spaceId: UUID = Space.PERSONAL_ID,   // NEW: stamps onto backfilled tabs
// )
//
// `BrowserFiles.TabRecord` mirrors it (new optional "spaceId" field, defaulting
// to the Personal id so v1 files still parse), and `saveTabList`/`loadTabList`
// round-trip it with `o.optString("spaceId").ifBlank { Space.PERSONAL_ID.toString() }.let(UUID::fromString)`.

/* -------------------------------------------- persistence (BrowserFiles v2) */

// Room is NOT available (app/build.gradle has no room deps), so the app's
// JSON-file style is the migration path:
//
// New files under filesDir/browser/, written with BrowserFiles.writeText's
// atomic tmp+rename pattern:
//   spaces.json     -> JSONArray of Space (id, name, letter, colorA, colorB,
//                      sortIndex, updatedAt, deletedAt)
//   pin-nodes.json  -> JSONArray of PinNode (id, spaceId, parentId, type, url,
//                      title, faviconUrl, sortIndex, collapsed, depth, updatedAt,
//                      deletedAt) — supersedes pins.json
//   essentials.json -> JSONArray of Essential (id, url, title, faviconUrl,
//                      sortIndex, updatedAt, deletedAt)
//   tabs.json       -> version bumped 1 -> 2; each tab gains "spaceId".
//
// Backfill on VM init (before restoreTabs):
//   1. spaces = loadSpaces(app); if empty ->
//      saveSpaces(app, listOf(Space(id = Space.PERSONAL_ID, name = "Personal",
//        letter = "P", colorA = 0xFF2F5C69, colorB = 0xFF4F7C8A)))
//   2. loadTabList: TabRecord.spaceId defaults to Space.PERSONAL_ID, so every
//      pre-spaces tab lands in the default space (per spec: "existing tabs
//      migrate into a default Personal space").
//
// New-space creation: Space(id = UUID.randomUUID(), ...) persisted via
// saveSpaces(spaces + it); new essential: Essential(id = UUID.randomUUID(), ...)
// capped by checking `essentials.count { it.deletedAt == null } < Essential.MAX`.
//
// Depth rule: a FOLDER's parent depth + 1 must be <= PinNode.MAX_DEPTH (3);
// enforced in the VM when adding a node (`parentDepth(parentId) + 1`).
