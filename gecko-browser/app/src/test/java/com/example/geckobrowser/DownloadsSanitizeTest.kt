package com.example.geckobrowser

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsSanitizeTest {

    @Test
    fun blankFallsBackToRoot() {
        assertEquals("", sanitizeDownloadSubfolder(""))
        assertEquals("", sanitizeDownloadSubfolder("   "))
        assertEquals("", sanitizeDownloadSubfolder("/"))
    }

    @Test
    fun simpleSubfolderIsKept() {
        assertEquals("horiZen", sanitizeDownloadSubfolder("horiZen"))
        assertEquals("work/photos", sanitizeDownloadSubfolder("work/photos"))
    }

    @Test
    fun leadingDownloadSegmentIsNotDoubled() {
        assertEquals("horiZen", sanitizeDownloadSubfolder("Download/horiZen"))
        assertEquals("work/photos", sanitizeDownloadSubfolder("download/work/photos"))
    }

    @Test
    fun dotSegmentsAreDropped() {
        assertEquals("a/b", sanitizeDownloadSubfolder("a/./b"))
        assertEquals("b", sanitizeDownloadSubfolder("../b"))
        assertEquals("a", sanitizeDownloadSubfolder("a/.."))
        assertEquals("", sanitizeDownloadSubfolder(".."))
    }

    @Test
    fun illegalCharactersBecomeUnderscore() {
        assertEquals("a_b", sanitizeDownloadSubfolder("a:b"))
        assertEquals("a-b", sanitizeDownloadSubfolder("a-b"))
        assertEquals("hello_", sanitizeDownloadSubfolder("hello;"))
    }

    @Test
    fun trailingSlashIsDropped() {
        assertEquals("horiZen", sanitizeDownloadSubfolder("horiZen/"))
        assertEquals("horiZen", sanitizeDownloadSubfolder("/horiZen/"))
    }

    @Test
    fun backslashSeparatorsAreAccepted() {
        assertEquals("work/photos", sanitizeDownloadSubfolder("work\\photos"))
    }

    @Test
    fun resultIsCappedAt64Characters() {
        val input = (1..40).joinToString("") { "abcdefgh" }
        assertEquals(64, sanitizeDownloadSubfolder(input).length)
    }
}
