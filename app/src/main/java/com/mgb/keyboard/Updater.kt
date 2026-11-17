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
import java.security.MessageDigest
import com.mgb.keyboard.typing.ShaCheck

/**
 * In-app update from GitHub Releases (public repo).
 * Stage 1: fetch latest release, download the .apk, open the system installer.
 * Stage 2 (fallback): open the release page in the browser.
 * The keyboard itself never depends on this file.
 */
object Updater {

    data class Info(val tag: String, val build: Int, val apkUrl: String?, val pageUrl: String, val notes: String, val sha: String? = null)   // v0.10.8: sha = asset "digest" (sha256:<hex>) when GitHub gives it

    enum class InstallResult { STARTED, NEED_PERMISSION }

    /** v0.36.4: WHY the last check failed (shown in Settings instead of the old guess "offline or private"). */
    @Volatile var lastError: String = ""

    /** Returns null on any problem (offline, private repo, bad json...). v0.36.4: if the GitHub API fails (rate limit 403 on a shared mobile IP, blocked host ...) the normal release page is tried too. */
    fun fetchLatest(): Info? {
        lastError = ""
        val repo = BuildConfig.GITHUB_REPO
        if (repo.isEmpty()) { lastError = "this build has no repo name (use the APK built by GitHub Actions)"; return null }
        val a = try { viaApi(repo) } catch (e: Throwable) { lastError = "API " + e.javaClass.simpleName; null }
        if (a != null) { lastError = ""; return a }
        val api = lastError
        val b = try { viaPage(repo) } catch (e: Throwable) { lastError = api + "; page " + e.javaClass.simpleName; null }
        if (b != null) { lastError = ""; return b }
        if (!lastError.contains(api)) lastError = api + "; " + lastError
        return null
    }

    /** Fallback: github.com/<repo>/releases/latest redirects to .../tag/<tag>; the apk name is Jabra-Keyboard-<tag>.apk (same rule as the workflow). No notes / no hash in this way (hash missing = allowed, as for old releases). */
    private fun viaPage(repo: String): Info? {
        val c = URL("https://github.com/$repo/releases/latest").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "Jabra-Keyboard-Updater")
            val code = c.responseCode
            if (code !in 301..303 && code != 307 && code != 308) { lastError = "page HTTP $code"; return null }
            val loc = c.getHeaderField("Location") ?: run { lastError = "page: no redirect"; return null }
            val tag = loc.substringAfterLast("/tag/", "").substringBefore("?")
            if (tag.isEmpty()) { lastError = "page: no release yet"; return null }
            val build = Regex("-b(\\d+)$").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            return Info(tag, build, "https://github.com/$repo/releases/download/$tag/Jabra-Keyboard-$tag.apk", "https://github.com/$repo/releases/tag/$tag", "", null)
        } finally {
            c.disconnect()
        }
    }

    private fun viaApi(repo: String): Info? {
        val c = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "Jabra-Keyboard-Updater")   // v0.36.4: GitHub wants a User-Agent
            if (c.responseCode != 200) { lastError = "API HTTP " + c.responseCode; return null }
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = j.optString("tag_name")
            val build = Regex("-b(\\d+)$").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            var apk: String? = null
            var dig: String? = null   // v0.10.8
            val assets = j.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); dig = a.optString("digest").ifEmpty { null }; break }
                }
            }
            return Info(tag, build, apk, j.optString("html_url"), j.optString("body"), dig)
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

    /** v0.10.8: SHA-256 of the downloaded file vs the published hash. OK / MISMATCH / UNKNOWN (no hash published = UNKNOWN = allowed). Background Thread only. */
    fun verify(apk: File, info: Info): Int {
        val exp0: String? = try { ShaCheck.expected(info.sha, info.notes) } catch (_: Throwable) { null }
        val exp: String = exp0 ?: return ShaCheck.UNKNOWN
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            apk.inputStream().use { i -> val buf = ByteArray(65536); while (true) { val n = i.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
            ShaCheck.compare(exp, ShaCheck.toHex(md.digest()))
        } catch (_: Throwable) { ShaCheck.MISMATCH }   // a hash exists but the file could not be read: do not install
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
