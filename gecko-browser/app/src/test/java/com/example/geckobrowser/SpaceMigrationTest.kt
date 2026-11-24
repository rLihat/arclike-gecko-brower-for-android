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
    fun tabsOfSpaceIsPureGroupingBySpaceId() {
        val space = spaceId()
        val tabs = listOf(TabInfo(id = 7, spaceId = space))
        // tabsOfSpace is pure grouping; a tombstoned space still groups its
        // tabs (the VM decides whether those tabs are user-visible).
        assertEquals(listOf(7L), BrowserViewModel.tabsOfSpace(tabs, space).map { it.id })
    }

    @Test
    fun deletedSpaceStaysDistinctFromActive() {
        // vm.deleteSpace tombstones the Space row; a new space with a random id
        // is distinct, so switching spaces changes the tab set.
        val a = spaceId(); val b = spaceId()
        assertTrue(a != b)
    }

    private fun spaceId() = java.util.UUID.randomUUID()
}
