package com.example.geckobrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceMigrationTest {

    @Test
    fun migrationCapsEssentialsAtTwelve() {
        val pins = (1..20).map { PinRecord("k$it", "https://site$it", "Site $it") }
        val (essentials, extras) = BrowserViewModel.migrateLegacyPins(pins)
        assertEquals(Essential.MAX, essentials.size)
        assertEquals(20 - Essential.MAX, extras.size)
    }

    @Test
    fun extrasBecomePinsInPersonalSpace() {
        val pins = (1..15).map { PinRecord("k$it", "https://site$it", "Site $it") }
        val (essentials, extras) = BrowserViewModel.migrateLegacyPins(pins)
        val extra = extras.firstOrNull()
        assertNotNull(extra)
        assertEquals(PinType.PIN, extra!!.type)
        assertEquals(Space.PERSONAL_ID, extra.spaceId)
        assertTrue(extra.deletedAt == null)
    }

    @Test
    fun migrationDedupesByKeyAndByUrl() {
        val pins = listOf(
            PinRecord("https://a.com/page", "https://a.com/page", "A"),
            PinRecord("https://a.com/page", "https://a.com/page", "A again"), // dup key
            PinRecord("", "https://b.com", "B"),                             // key falls back to url
            PinRecord("blank-url", "", "C"),                                 // dropped (no url)
        )
        val (essentials, extras) = BrowserViewModel.migrateLegacyPins(pins)
        val urls = essentials.map { it.url }
        assertEquals(2, urls.size)
        assertEquals(0, extras.size)
    }

    @Test
    fun migrateEmptyPinListYieldsEmptyResults() {
        val (essentials, extras) = BrowserViewModel.migrateLegacyPins(emptyList())
        assertEquals(0, essentials.size)
        assertEquals(0, extras.size)
    }

    @Test
    fun tabsOfSpaceFiltersCorrectly() {
        val personal = Space.PERSONAL_ID
        val other = java.util.UUID.randomUUID()
        val tabs = listOf(
            TabInfo(id = 1, spaceId = personal),
            TabInfo(id = 2, spaceId = other),
            TabInfo(id = 3, spaceId = other),
        )
        assertEquals(listOf(1L), BrowserViewModel.tabsOfSpace(tabs, personal).map { it.id })
        assertEquals(listOf(2L, 3L), BrowserViewModel.tabsOfSpace(tabs, other).map { it.id })
    }

    @Test
    fun tombstonedSpaceExcludesTabs() {
        val dead = java.util.UUID.randomUUID()
        val tabs = listOf(TabInfo(id = 7, spaceId = dead))
        assertTrue(BrowserViewModel.tabsOfSpace(tabs, dead).isEmpty())
    }
}
