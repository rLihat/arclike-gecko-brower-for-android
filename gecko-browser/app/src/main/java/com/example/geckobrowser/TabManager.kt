package com.example.geckobrowser

import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

/**
 * Owns exactly one open GeckoSession per tab. The browser keeps every session
 * alive; only the active one is ever attached to the GeckoView.
 */
class TabManager(private val runtime: GeckoRuntime) {

    private val sessions = LinkedHashMap<Long, GeckoSession>()
    private var nextId = 1L

    val ids: Set<Long> get() = sessions.keys
    val all: Map<Long, GeckoSession> get() = sessions

    /** Creates and opens a session, restoring history when a blob is supplied. */
    fun create(
        restored: GeckoSession.SessionState? = null,
        attach: (Long, GeckoSession) -> Unit,
    ): Long {
        val id = nextId++
        open(id, restored, attach)
        return id
    }

    /** Restores a tab under the id it had when the app was closed. */
    fun createWithId(
        id: Long,
        restored: GeckoSession.SessionState? = null,
        attach: (Long, GeckoSession) -> Unit,
    ): Long {
        reserveAbove(id)
        open(id, restored, attach)
        return id
    }

    private fun open(id: Long, restored: GeckoSession.SessionState?, attach: (Long, GeckoSession) -> Unit) {
        val session = GeckoSession()
        session.open(runtime)
        sessions[id] = session
        attach(id, session)
        if (restored != null) {
            try {
                session.restoreState(restored)
            } catch (_: Throwable) {
                // A corrupt blob just costs the tab its history.
            }
        }
    }

    /** Keeps generated ids above anything that came back from disk. */
    fun reserveAbove(id: Long) {
        if (id >= nextId) nextId = id + 1
    }

    fun session(id: Long): GeckoSession? = sessions[id]

    fun load(id: Long, url: String) {
        if (url.isBlank()) return
        sessions[id]?.loadUri(url)
    }

    fun close(id: Long) {
        sessions.remove(id)?.close()
    }

    fun closeAll() {
        sessions.values.forEach { it.close() }
        sessions.clear()
    }

    /** Replaces a crashed session while keeping the tab id stable. */
    fun reopen(
        id: Long,
        restored: GeckoSession.SessionState? = null,
        attach: (Long, GeckoSession) -> Unit,
    ): GeckoSession? {
        sessions.remove(id)?.close()
        val session = GeckoSession()
        session.open(runtime)
        sessions[id] = session
        attach(id, session)
        if (restored != null) {
            try {
                session.restoreState(restored)
            } catch (_: Throwable) {
                // Fall back to a plain reload of the tab URL.
            }
        }
        return session
    }
}
