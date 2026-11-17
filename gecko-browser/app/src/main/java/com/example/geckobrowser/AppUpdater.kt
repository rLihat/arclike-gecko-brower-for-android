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
    private const val ROLLING_TAG = "ota-beta"
    private const val API_BASE = "https://api.github.com"

    /** Callback for download progress as a fraction in [0f, 1f]. */
    fun interface Progress {
        fun emit(fraction: Float)
    }

    /** Returns the rolling release if it is newer than the installed build, otherwise null. */
    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val current = installedVersionCode(context)
        try {
            val json = httpGet("$API_BASE/repos/$OWNER/$REPO/releases/tags/$ROLLING_TAG")
            val root = JSONObject(json)
            val name = root.optString("name").ifBlank { root.optString("tag_name") }
            val remote = parseRunNumber(name) ?: return@withContext null
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

    /** Parses the trailing run number from `Beta vDD-MM-YYYY-NNN`. */
    private fun parseRunNumber(name: String): Int? =
        Regex("-(\\d+)$").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
}
