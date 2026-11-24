package com.example.geckobrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SpaceSyncTest {

    @Test
    fun snapshotRoundTripsAllSpaceRows() {
        val s = Space(UUID.randomUUID(), "Work", "W", 1, 2, icon = "globe", container = "work")
        val e = Essential(UUID.randomUUID(), "https://a.com", "A")
        val n = PinNode(UUID.randomUUID(), s.id, type = PinType.FOLDER, title = "F")
        val snapshot = SpaceSyncSnapshot(listOf(s), listOf(e), listOf(n))
        val parsed = SpaceSyncSnapshot.fromJson(snapshot.toJson())
        assertNotNull(parsed)
        assertEquals("Work", parsed!!.spaces.first().name)
        assertEquals("globe", parsed.spaces.first().icon)
        assertEquals("work", parsed.spaces.first().container)
        assertEquals("A", parsed.essentials.first().title)
        assertEquals("F", parsed.pinNodes.first().title)
    }

    @Test
    fun lwwPicksNewerRowAndTombstoneWinsTie() {
        val id = UUID.randomUUID()
        val older = Space(id, "Old", "O", 1, 2, updatedAt = 100)
        val newer = Space(id, "New", "N", 1, 2, updatedAt = 200)
        val merged = SpaceSyncSnapshot.merge(
            SpaceSyncSnapshot(listOf(older)),
            SpaceSyncSnapshot(listOf(newer)),
        )
        assertEquals("New", merged.spaces.first().name)

        val tomb = Space(id, "Deleted", "D", 1, 2, updatedAt = 200, deletedAt = 201)
        val tied = SpaceSyncSnapshot.merge(
            SpaceSyncSnapshot(listOf(newer)),
            SpaceSyncSnapshot(listOf(tomb)),
        )
        assertNotNull(tied.spaces.first().deletedAt)
    }

    @Test
    fun hkdfCryptoRoundTrips() {
        val key = "kFM3Zm9vYmFyYmF6cXV1eA" // base64url-ish non-empty key
        val encrypted = FxaSync.encrypt("{\"hello\":\"world\"}", key)
        assertTrue(encrypted.contains("ciphertext"))
        assertEquals("{\"hello\":\"world\"}", FxaSync.decrypt(encrypted, key))
        assertNull(FxaSync.decrypt(encrypted.replace("ciphertext", "bogus"), key))
    }
}
