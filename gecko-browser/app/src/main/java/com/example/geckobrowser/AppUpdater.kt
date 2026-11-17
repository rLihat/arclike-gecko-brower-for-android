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
            if (!dir.isDirectory) return
            val installed = installedVersionCode(context)
            val keepPath = keep?.absolutePath
            dir.listFiles { f -> f.isFile && f.name.endsWith(".apk", ignoreCase = true) }
                ?.forEach { f ->
                    if (keepPath != null && f.absolutePath == keepPath) return@forEach
                    val n = Regex("-(\\d+)\\.apk$").find(f.name)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ?: return@forEach
                    // versionCode == runNumber + 1, so strictly older builds are purgable.
                    if (n + 1 < installed) f.delete()
                }
        } catch (_: Throwable) {
            // Storage hygiene must never break the updater.
        }
    }

    /** Returns the latest release for the configured channel, otherwise null. */
    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val current = installedVersionCode(context)
        val channel = context.getSharedPreferences("arc_browser", Context.MODE_PRIVATE)
            .getString("update_channel", "beta") ?: "beta"
        val prefix = channelPrefix(channel)
        try {
            val json = httpGet("$API_BASE/repos/$OWNER/$REPO/releases?per_page=20")
            val releases = org.json.JSONArray(json)
            var chosen: org.json.JSONObject? = null
            for (i in 0 until releases.length()) {
                val r = releases.getJSONObject(i)
                val tag = r.optString("tag_name")
                if (tag.startsWith(prefix)) { chosen = r; break }
            }
            val root = chosen ?: return@withContext null
            val name = root.optString("name").ifBlank { root.optString("tag_name") }
            val remote = parseRunNumber(name) ?: parseRunNumber(root.optString("tag_name")) ?: return@withContext null
            if (remote + 1 <= current) return@withContext null
            val assets = root.optJSONArray("assets") ?: return@withContext null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.getString("browser_download_url"); break
                }
            }
            if (apkUrl == null) return@withContext null
            UpdateInfo(
                displayName = name,
                runNumber = remote,
                apkUrl = apkUrl,
                releaseUrl = root.optString("html_url"),
                releaseNotes = root.optString("body"),
            )
        } catch (_: Throwable) {
            null
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
                    progress?.emit(1f)
                    return@withContext out
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
