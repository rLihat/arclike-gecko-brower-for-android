package com.example.geckobrowser

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URL

/** A single rolling release hosted on GitHub Releases (`ota-beta`). */
data class UpdateInfo(
    /** e.g. "Beta v17-11-2026-1234" */
    val displayName: String,
    val runNumber: Int,
    val apkUrl: String,
    val releaseUrl: String,
    /** Expected APK size in bytes from the release asset; 0 when unknown. */
    val apkSize: Long = 0L,
    val releaseNotes: String = "",
)

/**
 * OTA updater. The `ota-beta` release on GitHub is replaced on every push to
 * `main`; this checks GitHub for the rolling release and reports whether the
 * installed build is older. It also downloads the APK and hands it to the
 * system package installer.
 */
object AppUpdater {

    private const val OWNER = "rLihat"
    private const val REPO = "arclike-gecko-brower-for-android"
    private const val API_BASE = "https://api.github.com"

    /** Callback for download progress as a fraction in [0f, 1f]. */
    fun interface Progress {
        fun emit(fraction: Float)
    }

    /**
     * Deletes stale APKs left behind in `updates/`. The file for the install that
     * is about to happen is preserved via [keep]; anything older than the running
     * build is dead weight. Safe to call on every cold start.
     */
    fun purgeStaleUpdates(context: Context, keep: File? = null) {
        try {
            val dir = File(context.filesDir, "updates")
            if (dir.isDirectory) {
                val keepPath = keep?.absolutePath
                dir.listFiles { f -> f.isFile && f.name.endsWith(".apk", ignoreCase = true) }
                    ?.forEach { f ->
                        // The installed APK is dead weight; interrupted or oddly
                        // named downloads are too. Only preserve the file we are
                        // about to hand to the installer.
                        if (keepPath == null || f.absolutePath != keepPath) f.delete()
                    }
            }
            // Staged add-on files are one-shot; a crashed install leaves them.
            context.cacheDir.listFiles { f ->
                f.isFile && f.name.startsWith("addon-") && f.name.endsWith(".xpi")
            }?.forEach { it.delete() }
        } catch (_: Throwable) {
            // Storage hygiene must never break the updater.
        }
    }

    /** Returns the latest release for the configured channel, otherwise null. */
    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val current = installedRunNumber(context)
        val channel = context.getSharedPreferences("arc_browser", Context.MODE_PRIVATE)
            .getString("update_channel", "beta") ?: "beta"
        val prefix = channelPrefix(channel)
        try {
            val json = httpGet("$API_BASE/repos/$OWNER/$REPO/releases?per_page=20")
            val releases = org.json.JSONArray(json)
            val prefixMatches = mutableListOf<org.json.JSONObject>()
            for (i in 0 until releases.length()) {
                val r = releases.getJSONObject(i)
                if (r.optString("tag_name").startsWith(prefix)) prefixMatches += r
            }
            val root = prefixMatches.maxWithOrNull(
                compareBy { parseRunNumber(it.optString("name")) ?: parseRunNumber(it.optString("tag_name")) ?: -1 }
            ) ?: return@withContext null
            val name = root.optString("name").ifBlank { root.optString("tag_name") }
            val remote = parseRunNumber(name) ?: parseRunNumber(root.optString("tag_name")) ?: return@withContext null
            // The installed build's versionName carries the same run number as the
            // release's tag and name, so compare like-for-like.
            if (remote <= current) return@withContext null
            val assets = root.optJSONArray("assets") ?: return@withContext null
            var apkUrl: String? = null
            var apkSize = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.getString("browser_download_url")
                    apkSize = a.optLong("size", 0L)
                    break
                }
            }
            if (apkUrl == null) return@withContext null
            UpdateInfo(
                displayName = name,
                runNumber = remote,
                apkUrl = apkUrl,
                releaseUrl = root.optString("html_url"),
                apkSize = apkSize,
                releaseNotes = root.optString("body"),
            )
        } catch (_: Throwable) {
            null
        }
    }

    /** Structural + size check: truncated APKs (GitHub serves the asset over CDN) fail to parse. */
    private fun isValidApk(file: File, expectedSize: Long): Boolean {
        if (expectedSize > 0 && file.length() != expectedSize) return false
        return try {
            java.util.zip.ZipFile(file).use { zip ->
                zip.getEntry("AndroidManifest.xml") != null && zip.size() > 0
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Downloads the APK into the app-specific `updates/` dir; returns it on success. */
    suspend fun download(context: Context, info: UpdateInfo, progress: Progress? = null): File? =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "updates").apply { mkdirs() }
            val out = File(dir, "horiZen-${info.runNumber}.apk")
            // A fresh check means anything already there is superseded (or a duplicate).
            purgeStaleUpdates(context, keep = out)
            try {
                if (out.exists() && out.length() > 0) {
                    if (isValidApk(out, info.apkSize)) {
                        progress?.emit(1f)
                        return@withContext out
                    }
                    out.delete()
                }
                val conn = URL(info.apkUrl).openConnection()
                conn.setRequestProperty("User-Agent", "horiZen-Android")
                conn.setRequestProperty("Accept", "application/octet-stream")
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                val total = (conn.contentLengthLong).takeIf { it > 0 } ?: 0L
                out.outputStream().use { outStream ->
                    conn.getInputStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            outStream.write(buf, 0, n)
                            copied += n
                            if (total > 0) progress?.emit((copied.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                if (!isValidApk(out, info.apkSize)) {
                    android.util.Log.w("AppUpdater", "Downloaded file failed validation (size=${out.length()}, expected=${info.apkSize}); deleting")
                    out.delete()
                    return@withContext null
                }
                android.util.Log.d("AppUpdater", "Downloaded ${info.displayName} to ${out.absolutePath}")
                out
            } catch (e: IOException) {
                out.delete()
                null
            } catch (_: Throwable) {
                out.delete()
                null
            }
        }

    /** Builds the install intent for a file previously written by [download]. */
    fun installIntent(context: Context, file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setData(uri)
            setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection()
        conn.setRequestProperty("User-Agent", "horiZen-Android")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return conn.getInputStream().bufferedReader().use { it.readText() }
    }

    /** The installed build's run number, parsed from its versionName (e.g. "Beta v17-11-2026-99"). */
    private fun installedRunNumber(context: Context): Int = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
            ?.let { parseRunNumber(it) }
    }.getOrNull() ?: installedVersionCode(context)

    private fun installedVersionCode(context: Context): Int =
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            } catch (_: PackageManager.NameNotFoundException) { 1 }
        } else {
            try {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            } catch (_: PackageManager.NameNotFoundException) { 1 }
        }

    /** The tag prefix that marks releases of each channel (`beta-build-` / `stable-build-`). */
    internal fun channelPrefix(channel: String): String =
        if (channel == "stable") "stable-build-" else "beta-build-"

    /** Parses the trailing run number from `Beta vDD-MM-YYYY-NNN`. */
    internal fun parseRunNumber(name: String): Int? =
        Regex("-(\\d+)$").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
}
