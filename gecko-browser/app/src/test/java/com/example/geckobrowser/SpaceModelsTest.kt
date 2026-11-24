package com.example.geckobrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SpaceModelsTest {

    private fun space(id: String = "11111111-1111-1111-1111-111111111111", name: String = "X") =
        Space(id = UUID.fromString(id), name = name, letter = "X", colorA = 0, colorB = 0)

    @Test
    fun essentialsCapIsTwelve() {
        assertEquals(12, Essential.MAX)
    }

    @Test
    fun pinNodeMaxDepthIsThree() {
        assertEquals(3, PinNode.MAX_DEPTH)
    }

    @Test
    fun containerPresetsCoverFirefoxDefaults() {
        assertEquals(listOf("none", "personal", "work", "banking", "shopping"),
            ContainerPresets.map { it.key })
        assertEquals("none", space().container)
        assertEquals("work", space().copy(container = "work").container)
    }

    @Test
    fun personalSpaceHasFixedId() {
        assertEquals("00000000-0000-0000-0000-000000000001", Space.PERSONAL_ID.toString())
    }

    @Test
    fun everyRowHasUuidAndTombstones() {
        // The data classes must expose id/updatedAt/deletedAt by construction.
        val s = space()
        val e = Essential(UUID.randomUUID(), "https://x", "x")
        val n = PinNode(UUID.randomUUID(), s.id, type = PinType.PIN)
        assertNotNull(s.updatedAt); assertNotNull(e.updatedAt); assertNotNull(n.updatedAt)
        assertNull(s.deletedAt); assertNull(e.deletedAt); assertNull(n.deletedAt)
        // Tombstoned rows are representable.
        assertNotEquals(0, s.copy(deletedAt = 1).deletedAt)
    }

    @Test
    fun foldersNestToMaxDepthThree() {
        val spaceId = space().id
        val root = PinNode(UUID.randomUUID(), spaceId, type = PinType.FOLDER, depth = 0)
        val child = root.copy(id = UUID.randomUUID(), parentId = root.id, type = PinType.FOLDER, depth = 1)
        val grandchild = child.copy(id = UUID.randomUUID(), parentId = child.id, depth = 2)
        val greatGrandchild = grandchild.copy(id = UUID.randomUUID(), parentId = grandchild.id, depth = 3)
        val nodes = listOf(root, child, grandchild, greatGrandchild)
        assertEquals(0, BrowserViewModel.pinDepth(nodes, null))
        assertEquals(1, BrowserViewModel.pinDepth(nodes, root.id))
        assertEquals(2, BrowserViewModel.pinDepth(nodes, child.id))
        assertEquals(3, BrowserViewModel.pinDepth(nodes, grandchild.id))
        assertEquals(4, BrowserViewModel.pinDepth(nodes, greatGrandchild.id))
        assertTrue(BrowserViewModel.pinDepth(nodes, greatGrandchild.id) > PinNode.MAX_DEPTH)
    }
}
