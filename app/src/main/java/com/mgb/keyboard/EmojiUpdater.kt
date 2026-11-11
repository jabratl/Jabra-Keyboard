package com.mgb.keyboard

import android.content.Context
import android.util.AtomicFile
import com.mgb.keyboard.emoji.EmojiBuild
import com.mgb.keyboard.emoji.EmojiDb
import com.mgb.keyboard.emoji.EmojiUpdateCheck
import java.net.HttpURLConnection
import java.net.URL

/**
 * v0.8.5 (E6): "Update emoji list". Runs ONLY when the person taps the button in the app screen (never in the background).
 * Downloads Unicode's emoji-test.txt, builds the data file with EmojiBuild, checks it (EmojiUpdateCheck + EmojiDb.parseOrNull),
 * and only then saves it as filesDir/emoji_user_v1.txt (AtomicFile). EmojiStore prefers that file, else the built-in asset, else the old panel.
 * Any failure = nothing changes. run() blocks: call it on a background Thread. It never throws and returns a short message for the screen.
 */
object EmojiUpdater {

    private fun download(): String? {
        val c = URL(EmojiUpdateCheck.URL).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "JabraKeyboard-emoji-update")
            if (c.responseCode != 200) return null
            val o = java.io.ByteArrayOutputStream(600000)
            val buf = ByteArray(16384)
            c.inputStream.use { inp ->
                while (true) {
                    val n = inp.read(buf)
                    if (n <= 0) break
                    o.write(buf, 0, n)
                    if (o.size() > EmojiUpdateCheck.MAX_DOWNLOAD) return null   // far bigger than the real file: refuse
                }
            }
            return String(o.toByteArray(), Charsets.UTF_8)
        } finally {
            try { c.disconnect() } catch (_: Throwable) { }
        }
    }

    fun run(ctx: Context): String {
        try {
            val app = ctx.applicationContext ?: ctx
            val cur = EmojiStore.get() ?: return "The emoji list is still loading. Try again in a few seconds."
            val text = (try { download() } catch (_: Throwable) { null }) ?: return "Could not download the list (offline, or the site refused). Nothing changed."
            val r = (try { EmojiBuild.build(text) } catch (_: Throwable) { null }) ?: return "The downloaded list could not be read. Nothing changed."
            when (EmojiUpdateCheck.verdict(r.unicodeVersion, r.count, r.groups, cur.unicodeVersion(), cur.size())) {
                EmojiUpdateCheck.UP_TO_DATE -> return "Already up to date (Unicode " + cur.unicodeVersion() + ", " + cur.size() + " emoji). Nothing changed."
                EmojiUpdateCheck.TOO_FEW -> return "The new list looks too small (" + r.count + " emoji), so it was not used. Nothing changed."
                EmojiUpdateCheck.BAD -> return "The new list looks wrong, so it was not used. Nothing changed."
            }
            if (EmojiDb.parseOrNull(r.text) == null) return "The new list failed the check, so it was not used. Nothing changed."
            val af = AtomicFile(EmojiStore.userFile(app))
            val out = af.startWrite()
            try {
                out.write(r.text.toByteArray(Charsets.UTF_8))
                af.finishWrite(out)
            } catch (t: Throwable) {
                af.failWrite(out)
                return "Could not save the new list. Nothing changed."
            }
            EmojiStore.reload(app)
            return "Emoji list updated: Unicode " + r.unicodeVersion + ", " + r.count + " emoji. It is ready in a few seconds (the old panel shows until then)."
        } catch (_: Throwable) {
            return "Emoji update failed. Nothing changed."
        }
    }

    /** Deletes the downloaded list and goes back to the built-in one. */
    fun useBuiltIn(ctx: Context): String {
        try {
            val app = ctx.applicationContext ?: ctx
            val f = EmojiStore.userFile(app)
            if (!f.exists()) return "Already using the built-in emoji list."
            if (!f.delete()) return "Could not remove the downloaded list."
            EmojiStore.reload(app)
            return "Back to the built-in emoji list. It is ready in a few seconds."
        } catch (_: Throwable) {
            return "Could not switch the emoji list."
        }
    }
}
