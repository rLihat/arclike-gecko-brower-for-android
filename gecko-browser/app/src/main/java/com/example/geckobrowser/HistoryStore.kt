package com.example.geckobrowser

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * One row of the local history table: `url` is unique, [visitCount] grows on
 * every revisit and [lastVisit] moves with it.
 */
data class Visit(
    val id: Long,
    val url: String,
    val title: String,
    val lastVisit: Long,
    val visitCount: Int,
) {
    val host: String get() = BrowserViewModel.hostOf(url)

    /** The page title when there was one, otherwise the host. */
    val label: String
        get() = title.ifBlank { host.ifBlank { url } }
}

private const val HISTORY_FILE = "history.db"
private const val HISTORY_VERSION = 1
private const val TABLE_VISIT = "visit"
private const val MAX_AGE_MS = 90L * 24L * 60L * 60L * 1000L
private const val MAX_ROWS = 5000
private const val PRUNE_EVERY_MS = 6L * 60L * 60L * 1000L

private class HistoryDatabase(context: Context) :
    SQLiteOpenHelper(context, HISTORY_FILE, null, HISTORY_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE_VISIT (" +
                "id INTEGER PRIMARY KEY, " +
                "url TEXT NOT NULL UNIQUE, " +
                "title TEXT NOT NULL DEFAULT '', " +
                "lastVisit INTEGER NOT NULL, " +
                "visitCount INTEGER NOT NULL DEFAULT 1)"
        )
        db.execSQL("CREATE INDEX visit_last_visit ON $TABLE_VISIT(lastVisit)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

/**
 * Local browsing history in one SQLite table (`id`, `url`, `title`, `lastVisit`,
 * `visitCount` — the shape planned for a Room entity, kept behind this class so
 * moving to Room later only touches this file).
 *
 * Reads come from an in-memory mirror, so the history sheet, autocomplete and
 * `HistoryDelegate.getVisited` never wait on disk; writes queue on one thread in
 * the order they happened. Everything here is safe to call from any thread.
 */
class HistoryStore(context: Context) {

    private val db = HistoryDatabase(context.applicationContext)
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "history-db").apply { isDaemon = true }
    }
    private val lock = Any()
    private val byUrl = HashMap<String, Visit>()
    private var nextId = 1L
    private var lastPruneAt = 0L

    /** Reads the whole table into the mirror. Blocking; call off the main thread. */
    fun load() {
        val rows = ArrayList<Visit>()
        runCatching {
            db.readableDatabase.rawQuery(
                "SELECT id, url, title, lastVisit, visitCount FROM $TABLE_VISIT " +
                    "ORDER BY lastVisit DESC",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    rows.add(
                        Visit(
                            id = cursor.getLong(0),
                            url = cursor.getString(1),
                            title = cursor.getString(2).orEmpty(),
                            lastVisit = cursor.getLong(3),
                            visitCount = cursor.getInt(4),
                        )
                    )
                }
            }
        }
        synchronized(lock) {
            byUrl.clear()
            rows.forEach { byUrl[it.url] = it }
            nextId = (rows.maxOfOrNull { it.id } ?: 0L) + 1L
        }
    }

    /** Counts a fresh visit for [url], creating the row the first time. */
    fun record(url: String, title: String?) {
        if (url.isBlank()) return
        val now = System.currentTimeMillis()
        val row: Visit
        synchronized(lock) {
            val existing = byUrl[url]
            row = if (existing == null) {
                Visit(nextId++, url, title.orEmpty(), now, 1).also { byUrl[url] = it }
            } else {
                existing.copy(
                    title = title?.takeIf { it.isNotBlank() } ?: existing.title,
                    lastVisit = now,
                    visitCount = existing.visitCount + 1,
                ).also { byUrl[url] = it }
            }
        }
        submit {
            db.writableDatabase.insertWithOnConflict(
                TABLE_VISIT,
                null,
                row.toValues(),
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
        maybePrune(now)
    }

    /** Keeps a late-arriving title in sync; returns true when the row changed. */
    fun rename(url: String, title: String): Boolean {
        if (url.isBlank() || title.isBlank()) return false
        synchronized(lock) {
            val existing = byUrl[url] ?: return false
            if (existing.title == title) return false
            byUrl[url] = existing.copy(title = title)
        }
        submit {
            val values = ContentValues().apply { put("title", title) }
            db.writableDatabase.update(TABLE_VISIT, values, "url = ?", arrayOf(url))
        }
        return true
    }

    fun remove(id: Long) {
        val url = synchronized(lock) {
            val match = byUrl.values.firstOrNull { it.id == id } ?: return
            byUrl.remove(match.url)
            match.url
        }
        submit { db.writableDatabase.delete(TABLE_VISIT, "url = ?", arrayOf(url)) }
    }

    /** Deletes everything visited at or after [since]; `0` clears the table. */
    fun clear(since: Long) {
        synchronized(lock) {
            if (since <= 0L) byUrl.clear()
            else byUrl.values.removeAll { it.lastVisit >= since }
        }
        submit {
            val database = db.writableDatabase
            if (since <= 0L) database.delete(TABLE_VISIT, null, null)
            else database.delete(TABLE_VISIT, "lastVisit >= ?", arrayOf(since.toString()))
        }
    }

    /** Drops rows older than 90 days, then the oldest beyond [MAX_ROWS]. */
    fun prune(now: Long) {
        val cutoff = now - MAX_AGE_MS
        synchronized(lock) {
            lastPruneAt = now
            byUrl.values.removeAll { it.lastVisit < cutoff }
            if (byUrl.size > MAX_ROWS) {
                byUrl.values
                    .sortedByDescending { it.lastVisit }
                    .drop(MAX_ROWS)
                    .forEach { byUrl.remove(it.url) }
            }
        }
        submit {
            val database = db.writableDatabase
            database.delete(TABLE_VISIT, "lastVisit < ?", arrayOf(cutoff.toString()))
            database.execSQL(
                "DELETE FROM $TABLE_VISIT WHERE id NOT IN " +
                    "(SELECT id FROM $TABLE_VISIT ORDER BY lastVisit DESC LIMIT $MAX_ROWS)"
            )
        }
    }

    /** Newest first, optionally narrowed by whitespace-separated terms. */
    fun search(query: String): List<Visit> {
        val terms = query.trim().lowercase(Locale.US).split(' ').filter { it.isNotEmpty() }
        return synchronized(lock) {
            byUrl.values
                .asSequence()
                .filter { visit ->
                    if (terms.isEmpty()) true
                    else {
                        val haystack = (visit.title + ' ' + visit.url).lowercase(Locale.US)
                        terms.all { haystack.contains(it) }
                    }
                }
                .sortedByDescending { it.lastVisit }
                .toList()
        }
    }

    fun visit(url: String): Visit? = synchronized(lock) { byUrl[url] }

    fun visited(urls: Array<out String>): BooleanArray = synchronized(lock) {
        BooleanArray(urls.size) { byUrl.containsKey(urls[it]) }
    }

    val count: Int get() = synchronized(lock) { byUrl.size }

    private fun maybePrune(now: Long) {
        val due = synchronized(lock) {
            if (now - lastPruneAt < PRUNE_EVERY_MS) false
            else {
                lastPruneAt = now
                true
            }
        }
        if (due) prune(now)
    }

    private fun submit(block: () -> Unit) {
        worker.execute {
            runCatching(block)
        }
    }

    private fun Visit.toValues() = ContentValues().apply {
        put("id", id)
        put("url", url)
        put("title", title)
        put("lastVisit", lastVisit)
        put("visitCount", visitCount)
    }
}
