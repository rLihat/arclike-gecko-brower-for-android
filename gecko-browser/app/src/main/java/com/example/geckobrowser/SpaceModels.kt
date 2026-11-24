package com.example.geckobrowser

import androidx.compose.ui.graphics.Color
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
    val colorA: Long,
    /** Avatar gradient end (ARGB). */
    val colorB: Long,
    val sortIndex: Int = 0,
    /** Lucide icon key (plus, globe, star, briefcase, music, heart, code, home). */
    val icon: String = "plus",
    /** Firefox container association (metadata only under GeckoView). */
    val container: String = "none",
    /** Collapses this space's pins in the expanded list. */
    val collapsed: Boolean = false,
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

/** Icon keys offered when creating or editing a space (Lucide glyphs available in the app). */
val SpaceIcons = listOf("plus", "globe", "tabs", "folder", "sparkles", "lightbulb", "palette", "shield", "mask", "gear")

/** Firefox contextual-identity presets (association only — GeckoView has no cookie jars). */
data class ContainerPreset(val key: String, val label: String, val color: Long, val icon: String)

val ContainerPresets = listOf(
    ContainerPreset("none", "No container", 0xFF9AA0A6, "plus"),
    ContainerPreset("personal", "Personal", 0xFF4F93A8, "person"),
    ContainerPreset("work", "Work", 0xFF2E6B4F, "briefcase"),
    ContainerPreset("banking", "Banking", 0xFFB3672D, "shield"),
    ContainerPreset("shopping", "Shopping", 0xFFB34D6B, "palette"),
)

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

/* -------------------------------------------------- default space palette */

/** The mockup's named palettes, used when creating a space ("+" long-press). */
data class SpacePalette(val name: String, val letter: String, val c1: Long, val c2: Long)

val SpacePalettes = listOf(
    SpacePalette("Dusk", "D", 0xFF2F4C6A, 0xFF5B3D7A),
    SpacePalette("Moss", "M", 0xFF2E6B4F, 0xFF8C7A3A),
    SpacePalette("Rose", "R", 0xFFB34D6B, 0xFF7A2D4F),
    SpacePalette("Amber", "A", 0xFFB3672D, 0xFF8C4D0A),
    SpacePalette("Slate", "S", 0xFF4A5568, 0xFF2D3748),
    SpacePalette("Cyan", "C", 0xFF0F9C9E, 0xFF1F6F73),
    SpacePalette("Violet", "V", 0xFF7F3FB5, 0xFF41247B),
)

/**
 * Sheet-glass tokens used when no space is selected yet; spaces override
 * them via [BrowserViewModel.spaceC1]/[spaceC2] and they animate on switch.
 */
val DEFAULT_SPACE_C1: Long = 0xFF2F4C6A
val DEFAULT_SPACE_C2: Long = 0xFF5B3D7A

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
//   1. spaces = loadSpaces(app); if empty -> create Personal.
//   2. loadTabList: TabRecord.spaceId defaults to Space.PERSONAL_ID, so every
//      pre-spaces tab lands in the default space.
//
// New-space creation: Space(id = UUID.randomUUID(), ...) persisted via
// saveSpaces(spaces + it); new essential: Essential(id = UUID.randomUUID(), ...)
// capped by checking `essentials.count { it.deletedAt == null } < Essential.MAX`.
//
// Depth rule: a FOLDER's parent depth + 1 must be <= PinNode.MAX_DEPTH (3);
// enforced in the VM when adding a node.
