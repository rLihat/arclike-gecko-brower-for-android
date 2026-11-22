package com.example.geckobrowser

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.SystemClock
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

enum class DownloadStatus { Downloading, Paused, Done, Canceled, Failed }

/** A download as Compose sees it. Bytes are streamed by [DownloadService]. */
data class DownloadItem(
    val id: Long,
    val name: String,
    val size: Long = 0L,
    val done: Long = 0L,
    val status: DownloadStatus = DownloadStatus.Downloading,
    val host: String = "",
    val url: String = "",
    val mime: String = "",
    val localUri: String? = null,
    val started: Long = System.currentTimeMillis(),
    val ended: Long = 0L,
) {
    val active: Boolean get() = status == DownloadStatus.Downloading
}

/** A response that GeckoView handed us, waiting for the confirm prompt. */
data class PendingDownload(
    val name: String,
    val size: Long,
    val mime: String,
    val host: String,
    val url: String,
    val body: InputStream?,
    val originX: Float? = null,
    val originY: Float? = null,
)

/** The tile that jumps from the tap point into the live chip. */
data class DownloadFlight(
    val id: Long,
    val name: String,
    val startX: Float,
    val startY: Float,
)

/**
 * Process-wide download list shared by the manager UI and [DownloadService].
 * The manager reads [items] as a StateFlow; every change is mirrored to a small
 * private JSON file so finished (and paused) transfers survive a restart.
 */
object DownloadCenter {

    private val lock = Any()
    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    private val io = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val writer = CoroutineScope(SupervisorJob() + io)

    fun current(): List<DownloadItem> = _items.value

    fun find(id: Long): DownloadItem? = _items.value.firstOrNull { it.id == id }

    fun add(item: DownloadItem) = synchronized(lock) {
        if (_items.value.any { it.id == item.id }) {
            _items.value = _items.value.map { if (it.id == item.id) item else it }
        } else {
            _items.value = listOf(item) + _items.value
        }
    }

    fun update(id: Long, transform: (DownloadItem) -> DownloadItem) = synchronized(lock) {
        _items.value = _items.value.map { if (it.id == id) transform(it) else it }
    }

    fun remove(id: Long) = synchronized(lock) {
        _items.value = _items.value.filterNot { it.id == id }
    }

    fun replaceAll(list: List<DownloadItem>) = synchronized(lock) {
        _items.value = list
    }

    fun persistAsync(context: Context) {
        val appContext = context.applicationContext
        writer.launch { write(appContext, _items.value) }
    }

    suspend fun restore(context: Context) = withContext(Dispatchers.IO) {
        val file = BrowserFiles.downloadsFile(context)
        if (!file.exists()) return@withContext
        val parsed = try {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val status = runCatching {
                        DownloadStatus.valueOf(o.optString("status"))
                    }.getOrDefault(DownloadStatus.Canceled)
                    // Anything that was mid-flight when the process died is parked,
                    // never reported as a live transfer.
                    val restored = when (status) {
                        DownloadStatus.Downloading -> DownloadStatus.Paused
                        else -> status
                    }
                    add(
                        DownloadItem(
                            id = o.optLong("id"),
                            name = o.optString("name").ifBlank { "download.bin" },
                            size = o.optLong("size"),
                            done = o.optLong("done"),
                            status = restored,
                            host = o.optString("host"),
                            url = o.optString("url"),
                            mime = o.optString("mime"),
                            localUri = o.optString("localUri").takeIf { it.isNotBlank() },
                            started = o.optLong("started", System.currentTimeMillis()),
                            ended = o.optLong("ended"),
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
        if (parsed.isNotEmpty()) _items.value = parsed
    }

    private fun write(context: Context, list: List<DownloadItem>) {
        try {
            val arr = JSONArray()
            list.forEach { d ->
                arr.put(JSONObject().apply {
                    put("id", d.id)
                    put("name", d.name)
                    put("size", d.size)
                    put("done", d.done)
                    put("status", d.status.name)
                    put("host", d.host)
                    put("url", d.url)
                    put("mime", d.mime)
                    put("localUri", d.localUri ?: "")
                    put("started", d.started)
                    put("ended", d.ended)
                })
            }
            val file = BrowserFiles.downloadsFile(context)
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(arr.toString())
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) {
                file.writeText(arr.toString())
                tmp.delete()
            }
        } catch (_: Throwable) {
            // Persistence is a convenience; never break an active transfer.
        }
    }

    fun partDir(context: Context): File =
        File(context.filesDir, "downloads").apply { if (!exists()) mkdirs() }

    fun partFile(context: Context, id: Long): File =
        File(partDir(context), "download-$id.bin")
}

/**
 * Streams downloads in the foreground so they keep running while the app is in
 * the background. Bytes land in app-private storage first; completed files are
 * published to Downloads with MediaStore on API 29+, or moved to the app's
 * external Downloads directory on older releases.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val id = intent?.getLongExtra(EXTRA_ID, -1L) ?: -1L
        when (intent?.action) {
            ACTION_CANCEL -> if (id > 0) cancelDownload(id)
            ACTION_PAUSE -> if (id > 0) pauseDownload(id)
            ACTION_START -> if (id > 0) {
                // Foreground immediately: the platform gives us five seconds.
                postForeground(id)
                startDownload(id)
            }
            else -> Unit
        }
        return START_NOT_STICKY
    }

    private fun startDownload(id: Long) {
        if (jobs.containsKey(id)) return
        val request = requests[id] ?: requestFromStore(id) ?: run {
            stopIfIdle()
            return
        }
        val job = scope.launch {
            val context = applicationContext
            val part = DownloadCenter.partFile(context, id)
            var input: InputStream? = handedOff.remove(id)
            var output: OutputStream? = null
            var startOffset = if (part.exists()) part.length() else 0L
            var append = startOffset > 0L
            try {
                DownloadCenter.update(id) {
                    it.copy(status = DownloadStatus.Downloading, url = request.url, mime = request.mime)
                }
                if (input == null) {
                    val conn = openConnection(request.url, if (startOffset > 0) startOffset else null)
                    val code = conn.responseCode
                    if (startOffset > 0 && code == HttpURLConnection.HTTP_PARTIAL) {
                        input = conn.inputStream
                    } else if (startOffset > 0 && code == HttpURLConnection.HTTP_OK) {
                        // The server ignored our Range: restart cleanly.
                        startOffset = 0L
                        append = false
                        input = conn.inputStream
                    } else if (code in 200..299) {
                        startOffset = 0L
                        append = false
                        input = conn.inputStream
                    } else {
                        throw IOException("HTTP $code")
                    }
                    val length = conn.contentLengthLong
                    if (length > 0) DownloadCenter.update(id) {
                        it.copy(size = length + startOffset)
                    }
                }
                val stream = input ?: throw IOException("No response stream")
                output = FileOutputStream(part, append)
                val buffer = ByteArray(64 * 1024)
                var written = startOffset
                var lastUi = 0L
                var lastNote = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    written += read
                    val now = SystemClock.uptimeMillis()
                    if (now - lastUi >= 150L) {
                        lastUi = now
                        DownloadCenter.update(id) {
                            it.copy(done = it.size.takeIf { s -> s > 0 }?.let { s -> written.coerceAtMost(s) } ?: written)
                        }
                    }
                    if (now - lastNote >= 700L) {
                        lastNote = now
                        notifyProgress(id, written)
                    }
                }
                output.flush()
                output.close()
                output = null
                publish(context, id, request, part, written)
                DownloadCenter.persistAsync(context)
                notifyDone(id)
            } catch (_: CancellationException) {
                if (DownloadCenter.find(id)?.status == DownloadStatus.Downloading) {
                    DownloadCenter.update(id) { it.copy(status = DownloadStatus.Paused) }
                    DownloadCenter.persistAsync(context)
                }
            } catch (_: Throwable) {
                if (DownloadCenter.find(id)?.status == DownloadStatus.Downloading) {
                    DownloadCenter.update(id) {
                        it.copy(status = DownloadStatus.Failed, ended = System.currentTimeMillis())
                    }
                    DownloadCenter.persistAsync(context)
                    notifyFailed(id)
                }
            } finally {
                try {
                    output?.close()
                } catch (_: Throwable) {
                    // The stream is already gone.
                }
                try {
                    input?.close()
                } catch (_: Throwable) {
                    // The stream is already gone.
                }
                jobs.remove(id)
                stopIfIdle()
            }
        }
        jobs[id] = job
    }

    private suspend fun publish(
        context: Context,
        id: Long,
        request: DownloadRequest,
        part: File,
        written: Long,
    ) {
        val mime = request.mime.ifBlank { mimeForFile(request.name) }
        val subfolder = runCatching { AppSettings(context).snapshot().downloadSubfolder }.getOrDefault("")
        val uri: Uri? = withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, request.name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, downloadRelativePath(subfolder))
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (target == null) {
                    null
                } else {
                    try {
                        resolver.openOutputStream(target)?.use { out ->
                            part.inputStream().use { it.copyTo(out, 64 * 1024) }
                        } ?: throw IOException("Couldn't open the published file")
                        val done = ContentValues().apply {
                            put(MediaStore.Downloads.IS_PENDING, 0)
                        }
                        resolver.update(target, done, null, null)
                        part.delete()
                        target
                    } catch (t: Throwable) {
                        resolver.delete(target, null, null)
                        // Leave the private copy in place so the row can retry.
                        throw t
                    }
                }
            } else {
                val dir = downloadLegacyDir(context, subfolder)
                    ?: DownloadCenter.partDir(context)
                if (!dir.exists()) dir.mkdirs()
                val target = uniqueFile(dir, request.name)
                if (!part.renameTo(target)) {
                    part.inputStream().use { input ->
                        target.outputStream().use { input.copyTo(it, 64 * 1024) }
                    }
                    part.delete()
                }
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    target,
                )
            }
        }
        val finalSize = DownloadCenter.find(id)?.size?.takeIf { it > 0 } ?: written
        DownloadCenter.update(id) {
            it.copy(
                status = DownloadStatus.Done,
                done = finalSize,
                size = finalSize,
                ended = System.currentTimeMillis(),
                localUri = uri?.toString(),
            )
        }
    }

    private fun cancelDownload(id: Long) {
        handedOff.remove(id)?.closeQuietly()
        requests.remove(id)
        jobs.remove(id)?.cancel()
        DownloadCenter.update(id) {
            if (it.status == DownloadStatus.Done) it else it.copy(
                status = DownloadStatus.Canceled,
                ended = System.currentTimeMillis(),
            )
        }
        DownloadCenter.partFile(applicationContext, id).delete()
        DownloadCenter.persistAsync(applicationContext)
        stopIfIdle()
    }

    private fun pauseDownload(id: Long) {
        DownloadCenter.update(id) {
            if (it.status == DownloadStatus.Downloading) it.copy(status = DownloadStatus.Paused) else it
        }
        jobs.remove(id)?.cancel()
        DownloadCenter.persistAsync(applicationContext)
        stopIfIdle()
    }

    private fun requestFromStore(id: Long): DownloadRequest? {
        val item = DownloadCenter.find(id) ?: return null
        if (item.url.isBlank()) return null
        return DownloadRequest(item.name, item.mime, item.url, item.size)
    }

    private fun stopIfIdle() {
        if (jobs.isNotEmpty()) return
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        // Park anything still moving; the manager can resume it later.
        DownloadCenter.current()
            .filter { it.status == DownloadStatus.Downloading }
            .forEach { d ->
                DownloadCenter.update(d.id) { it.copy(status = DownloadStatus.Paused) }
            }
        DownloadCenter.persistAsync(applicationContext)
        scope.cancel()
        super.onDestroy()
    }

    /* ------------------------------------------------------------- notification */

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Download progress"
                setShowBadge(false)
            }
        )
    }

    private fun notification(id: Long, progress: Int, indeterminate: Boolean, done: Boolean = false, failed: Boolean = false): android.app.Notification {
        val item = DownloadCenter.find(id)
        val name = item?.name ?: "Download"
        val open = PendingIntent.getActivity(
            this,
            REQ_OPEN,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(BrowserViewModel.EXTRA_OPEN_DOWNLOADS, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun serviceIntent(action: String): PendingIntent = PendingIntent.getService(
            this,
            when (action) {
                ACTION_CANCEL -> REQ_CANCEL
                ACTION_PAUSE -> REQ_PAUSE
                else -> REQ_RESUME
            } + id.toInt(),
            Intent(this, DownloadService::class.java).apply {
                this.action = action
                putExtra(EXTRA_ID, id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val status = item?.status
        val paused = status == DownloadStatus.Paused
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(name)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(!done && !failed && !paused)
            .setAutoCancel(done || failed)
        when {
            failed || status == DownloadStatus.Failed -> {
                builder.setContentText("Download failed")
                builder.addAction(android.R.drawable.ic_menu_rotate, "Retry", serviceIntent(ACTION_START))
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", serviceIntent(ACTION_CANCEL))
            }
            done || status == DownloadStatus.Done -> builder.setContentText("Download complete")
            paused -> {
                builder.setContentText("Paused")
                builder.addAction(android.R.drawable.ic_media_play, "Resume", serviceIntent(ACTION_START))
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", serviceIntent(ACTION_CANCEL))
            }
            else -> {
                builder.setContentText(if (indeterminate) "Downloading." else "$progress%")
                builder.setProgress(100, progress, indeterminate)
                builder.addAction(android.R.drawable.ic_media_pause, "Pause", serviceIntent(ACTION_PAUSE))
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", serviceIntent(ACTION_CANCEL))
            }
        }
        return builder.build()
    }

    private fun postForeground(id: Long) {
        val item = DownloadCenter.find(id)
        val indeterminate = item == null || item.size <= 0
        val progress = if (item != null && item.size > 0) {
            ((item.done * 100) / item.size).toInt().coerceIn(0, 100)
        } else {
            0
        }
        try {
            startForeground(NOTIF_ID, notification(id, progress, indeterminate))
        } catch (_: Throwable) {
            // A missing notification permission must not kill the transfer.
        }
    }

    private fun notifyProgress(id: Long, written: Long) {
        val item = DownloadCenter.find(id) ?: return
        val progress = if (item.size > 0) ((written * 100) / item.size).toInt().coerceIn(0, 100) else 0
        notifySafe(notification(id, progress, item.size <= 0))
    }

    private fun notifyDone(id: Long) {
        notifySafe(notification(id, 100, false, done = true))
    }

    private fun notifyFailed(id: Long) {
        notifySafe(notification(id, 0, false, failed = true))
    }

    private fun notifySafe(notification: android.app.Notification) {
        try {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            manager.notify(NOTIF_ID, notification)
        } catch (_: Throwable) {
            // Notifications are best-effort.
        }
    }

    companion object {
        const val ACTION_START = "com.example.geckobrowser.DOWNLOAD_START"
        const val ACTION_CANCEL = "com.example.geckobrowser.DOWNLOAD_CANCEL"
        const val ACTION_PAUSE = "com.example.geckobrowser.DOWNLOAD_PAUSE"
        const val EXTRA_ID = "download_id"

        private const val CHANNEL_ID = "downloads"
        private const val NOTIF_ID = 4101
        private const val REQ_OPEN = 4102
        private const val REQ_CANCEL = 4200
        private const val REQ_PAUSE = 4300
        private const val REQ_RESUME = 4400

        data class DownloadRequest(val name: String, val mime: String, val url: String, val size: Long)

        private val requests = ConcurrentHashMap<Long, DownloadRequest>()
        private val handedOff = ConcurrentHashMap<Long, InputStream>()

        /** Queues a download and starts the foreground service for it. */
        fun start(context: Context, item: DownloadItem, body: InputStream?) {
            requests[item.id] = DownloadRequest(item.name, item.mime, item.url, item.size)
            body?.let { handedOff[item.id] = it }
            DownloadCenter.add(item)
            DownloadCenter.persistAsync(context)
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ID, item.id)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Throwable) {
                // If the service can't start, the row still reports its state.
            }
        }

        /** Re-opens an existing private part file; the service uses a Range request. */
        fun resume(context: Context, item: DownloadItem) {
            requests[item.id] = DownloadRequest(item.name, item.mime, item.url, item.size)
            DownloadCenter.update(item.id) { it.copy(status = DownloadStatus.Downloading) }
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ID, item.id)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Throwable) {
                DownloadCenter.update(item.id) { it.copy(status = DownloadStatus.Paused) }
            }
        }

        fun cancel(context: Context, id: Long) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_ID, id)
            }
            try {
                context.startService(intent)
            } catch (_: Throwable) {
                // No service running: clear the rows directly.
            }
            handedOff.remove(id)?.closeQuietly()
            requests.remove(id)
            DownloadCenter.update(id) { item ->
                if (item.status == DownloadStatus.Done) item else item.copy(
                    status = DownloadStatus.Canceled,
                    ended = System.currentTimeMillis(),
                )
            }
            DownloadCenter.partFile(context, id).delete()
            DownloadCenter.persistAsync(context)
        }

        fun pause(context: Context, id: Long) {
            DownloadCenter.update(id) {
                if (it.status == DownloadStatus.Downloading) it.copy(status = DownloadStatus.Paused) else it
            }
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_ID, id)
            }
            try {
                context.startService(intent)
            } catch (_: Throwable) {
                // Status already reflects the pause.
            }
            DownloadCenter.persistAsync(context)
        }

        fun forget(id: Long) {
            handedOff.remove(id)?.closeQuietly()
            requests.remove(id)
        }
    }
}

/**
 * Reduces a user-typed "Download/..." path to a safe relative subfolder.
 * Dot segments and illegal characters are dropped; the result never leaves
 * the Downloads collection. "" means the root of Download.
 */
internal fun sanitizeDownloadSubfolder(raw: String): String {
    if (raw.isBlank()) return ""
    val segments = raw.replace('\\', '/')
        .split('/')
        .map { it.trim().replace(Regex("[^A-Za-z0-9 _\\-\\.]"), "_").trim('.', ' ') }
        .filter { it.isNotEmpty() && it != ".." }
        .toMutableList()
    // A typed "Download/..." shouldn't nest as Download/Download/...
    if (segments.isNotEmpty() && segments.first().equals("download", ignoreCase = true)) {
        segments.removeAt(0)
    }
    return segments.joinToString("/").take(64)
}

/** What MediaStore.Downloads.RELATIVE_PATH should look like (trailing slash). */
internal fun downloadRelativePath(subfolder: String): String =
    if (subfolder.isBlank()) {
        Environment.DIRECTORY_DOWNLOADS + "/"
    } else {
        Environment.DIRECTORY_DOWNLOADS + "/" + subfolder + "/"
    }

/** Resolves the on-disk directory pre-29 downloads land in (honours the subfolder setting). */
internal fun downloadLegacyDir(context: Context, subfolder: String): File? {
    val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
    val dir = if (subfolder.isBlank()) base else File(base, subfolder)
    if (!dir.exists() && !dir.mkdirs()) return base
    return dir
}

/**
 * Deletes the bytes behind a finished download. MediaStore rows go through the
 * resolver; FileProvider URIs are mapped back to the app directories declared in
 * res/xml/file_paths.xml; plain file URIs are deleted directly. Best effort.
 */
internal fun deleteStoredDownload(context: Context, localUri: String?) {
    if (localUri.isNullOrBlank()) return
    val uri = runCatching { Uri.parse(localUri) }.getOrNull() ?: return
    try {
        when (uri.scheme) {
            "file" -> uri.path?.let { File(it).delete() }
            "content" -> if (uri.authority == "${context.packageName}.fileprovider") {
                val segments = uri.pathSegments.filter { it.isNotBlank() }
                val base = when (segments.firstOrNull()) {
                    // file_paths.xml declares these authorities.
                    "downloads" -> context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    "internal_downloads" -> File(context.filesDir, "Downloads")
                    "app_downloads" -> File(context.filesDir, "downloads")
                    else -> null
                }
                val rest = segments.drop(1).joinToString("/")
                if (base != null && rest.isNotBlank()) File(base, rest).delete()
            } else {
                context.contentResolver.delete(uri, null, null)
            }
        }
    } catch (_: Throwable) {
        // A file we can't reach is not worth crashing the list over.
    }
}

/* --------------------------------------------------------------- helpers */

private fun openConnection(url: String, rangeStart: Long?): HttpURLConnection {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 15_000
    conn.readTimeout = 30_000
    conn.instanceFollowRedirects = true
    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) horiZen")
    conn.setRequestProperty("Accept", "*/*")
    if (rangeStart != null && rangeStart > 0L) {
        conn.setRequestProperty("Range", "bytes=$rangeStart-")
    }
    return conn
}

private fun uniqueFile(dir: File, name: String): File {
    val first = File(dir, name)
    if (!first.exists()) return first
    val base = name.substringBeforeLast('.', name)
    val ext = name.substringAfterLast('.', "")
    var i = 1
    while (true) {
        val candidate = File(dir, "$base ($i)${if (ext.isBlank()) "" else ".$ext"}")
        if (!candidate.exists()) return candidate
        i++
    }
}

private fun mimeForFile(name: String): String {
    val ext = downExt(name)
    return when (ext) {
        "xpi" -> "application/x-xpinstall"
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "mp4" -> "video/mp4"
        "txt" -> "text/plain"
        "json" -> "application/json"
        else -> "application/octet-stream"
    }
}

private fun InputStream.closeQuietly() {
    try {
        close()
    } catch (_: Throwable) {
        // Nothing left to close.
    }
}
