package com.mgb.keyboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app update from GitHub Releases (public repo).
 * Stage 1: fetch latest release, download the .apk, open the system installer.
 * Stage 2 (fallback): open the release page in the browser.
 * The keyboard itself never depends on this file.
 */
object Updater {

    data class Info(val tag: String, val build: Int, val apkUrl: String?, val pageUrl: String, val notes: String)

    enum class InstallResult { STARTED, NEED_PERMISSION }

    /** Returns null on any problem (offline, private repo, bad json...). */
    fun fetchLatest(): Info? {
        val repo = BuildConfig.GITHUB_REPO
        if (repo.isEmpty()) return null
        val c = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) return null
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = j.optString("tag_name")
            val build = Regex("-b(\\d+)$").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            var apk: String? = null
            val assets = j.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break }
                }
            }
            return Info(tag, build, apk, j.optString("html_url"), j.optString("body"))
        } finally {
            c.disconnect()
        }
    }

    fun isNewer(i: Info): Boolean = i.build > BuildConfig.VERSION_CODE

    /** Downloads the apk into cache/updates/update.apk. Throws on failure. */
    fun download(ctx: Context, url: String): File {
        val dir = File(ctx.cacheDir, "updates"); dir.mkdirs()
        val out = File(dir, "update.apk")
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.instanceFollowRedirects = true
            if (c.responseCode != 200) throw RuntimeException("HTTP ${c.responseCode}")
            c.inputStream.use { inp -> out.outputStream().use { o -> inp.copyTo(o) } }
        } finally {
            c.disconnect()
        }
        if (out.length() < 100_000) throw RuntimeException("file too small")
        return out
    }

    fun install(ctx: Context, apk: File): InstallResult {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            return InstallResult.NEED_PERMISSION
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", apk)
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(uri, "application/vnd.android.package-archive")
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return InstallResult.STARTED
    }

    fun openPage(ctx: Context, url: String) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}
