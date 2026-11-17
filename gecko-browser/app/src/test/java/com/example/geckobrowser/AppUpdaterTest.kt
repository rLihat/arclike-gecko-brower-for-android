package com.example.geckobrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {

    @Test
    fun channelPrefixPicksTagForChannel() {
        assertEquals("beta-build-", AppUpdater.channelPrefix("beta"))
        assertEquals("stable-build-", AppUpdater.channelPrefix("stable"))
        assertEquals("beta-build-", AppUpdater.channelPrefix("anything-else"))
    }

    @Test
    fun parseRunNumberFindsTrailingNumber() {
        assertEquals(41, AppUpdater.parseRunNumber("beta-build-41"))
        assertEquals(1234, AppUpdater.parseRunNumber("Beta v17-11-2026-1234"))
        assertEquals(0, AppUpdater.parseRunNumber("stable-build-0"))
    }

    @Test
    fun parseRunNumberRejectsMissingOrNonNumeric() {
        assertNull(AppUpdater.parseRunNumber("Beta v17-11-2026"))
        assertNull(AppUpdater.parseRunNumber(""))
        assertNull(AppUpdater.parseRunNumber("beta-build-x"))
    }

    @Test
    fun channelPrefixAndParseRunNumberRoundTrip() {
        // Tags the CI generates must be understood by the updater exactly the same way.
        for (channel in listOf("beta", "stable")) {
            val tag = "${AppUpdater.channelPrefix(channel)}${123 + 1}"
            assertTrue(tag.endsWith("-124"))
            assertEquals(124, AppUpdater.parseRunNumber(tag))
        }
    }
}
