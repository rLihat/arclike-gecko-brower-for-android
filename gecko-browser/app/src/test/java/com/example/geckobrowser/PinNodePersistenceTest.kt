package com.example.geckobrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pin/folder tree must round-trip exactly, like the proven Essentials file. */
class PinNodePersistenceTest {

    private val raw = """
    [
      {"id":"11111111-1111-1111-1111-111111111111",
       "spaceId":"22222222-2222-2222-2222-222222222222",
       "parentId":null,"type":"FOLDER","url":"","title":"New folder",
       "faviconUrl":null,"sortIndex":0,"collapsed":false,"depth":0,
       "updatedAt":1,"deletedAt":null},
      {"id":"33333333-3333-3333-3333-333333333333",
       "spaceId":"22222222-2222-2222-2222-222222222222",
       "parentId":"11111111-1111-1111-1111-111111111111","type":"PIN",
       "url":"https://example.com/","title":"Example",
       "faviconUrl":null,"sortIndex":0,"collapsed":false,"depth":1,
       "updatedAt":1,"deletedAt":null}
    ]
    """.trimIndent()

    @Test
    fun parsesFolderAndPinTree() {
        val nodes = BrowserFiles.parsePinNodes(raw)
        assertEquals(2, nodes.size)

        val folder = nodes.first()
        assertEquals(PinType.FOLDER, folder.type)
        assertEquals("New folder", folder.title)
        assertNull(folder.parentId)
        assertEquals(0, folder.depth)
        assertNull(folder.deletedAt)

        val pin = nodes[1]
        assertEquals(PinType.PIN, pin.type)
        assertEquals("https://example.com/", pin.url)
        assertEquals(folder.id, pin.parentId)
        assertEquals(folder.spaceId, pin.spaceId)
        assertEquals(1, pin.depth)
    }

    @Test
    fun emptyJsonArrayIsAnEmptyTree() {
        assertTrue(BrowserFiles.parsePinNodes("[]").isEmpty())
    }

    @Test
    fun unknownTypeFallsBackToPinAndKeepsSiblings() {
        val mixed = raw.replaceFirst("\"type\":\"PIN\"", "\"type\":\"SOMETHING_ELSE\"")
        val nodes = BrowserFiles.parsePinNodes(mixed)
        assertEquals(2, nodes.size)
        assertEquals(PinType.PIN, nodes[1].type)
    }
}
