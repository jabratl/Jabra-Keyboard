package com.mgb.keyboard

import android.content.Context
import android.graphics.Paint
import android.os.Build
import android.util.AtomicFile
import com.mgb.keyboard.emoji.EmojiDb
import com.mgb.keyboard.emoji.EmojiVisible
import java.io.File

/**
 * v0.8.1 (E2): loads the emoji data file (assets/emoji/emoji_v1.txt) ONCE on a background thread and keeps it in memory.
 * v0.8.2 (E3): on the same thread, finds which emoji THIS phone can draw (Paint.hasGlyph) and keeps only those in the group lists.
 * The answer is cached in filesDir/emoji_ok_v1.txt (key = phone build + data Unicode version + count), so the next start is instant.
 * get() = null until the file is parsed AND the glyph check is done, or for good if loading failed: then the OLD emoji panel is used (never blank).
 * No timer, no service. Nothing here ever throws.
 */
object EmojiStore {
    @Volatile private var db: EmojiDb? = null
    @Volatile private var lists: Array<List<String>>? = null
    @Volatile private var hidden: HashSet<String> = HashSet()
    private var started = false

    fun get(): EmojiDb? = db

    /** Emoji of group g that this phone can draw, in file order. Empty if not ready / bad index. */
    fun groupItems(g: Int): List<String> {
        val l = lists ?: return emptyList()
        return if (g >= 0 && g < l.size) l[g] else emptyList()
    }

    /** True for an emoji of the data file that this phone cannot draw. Unknown strings = false (kept). */
    fun isHidden(e: String): Boolean = hidden.contains(e)

    fun preload(ctx: Context) {
        synchronized(this) {
            if (started) return
            started = true
        }
        try {
            val app = ctx.applicationContext ?: ctx
            val t = Thread(Runnable {
                try {
                    val s = app.assets.open("emoji/emoji_v1.txt")
                    var parsed: EmojiDb? = null
                    try { parsed = EmojiDb.parseOrNull(s) } finally { try { s.close() } catch (_: Throwable) { } }
                    if (parsed != null) publish(app, parsed)
                } catch (_: Throwable) { db = null }
            }, "jabra-emoji")
            t.priority = Thread.MIN_PRIORITY
            t.start()
        } catch (_: Throwable) { }
    }

    private fun publish(ctx: Context, d: EmojiDb) {
        val ok = glyphs(ctx, d)
        val hid = HashSet<String>()
        val ls = Array<List<String>>(d.groupCount()) { g ->
            val l = ArrayList<String>()
            var i = d.groupStart(g)
            val e = d.groupEnd(g)
            while (i < e) {
                if (ok[i]) l.add(d.emoji(i)) else hid.add(d.emoji(i))
                i++
            }
            l
        }
        hidden = hid
        lists = ls
        db = d   // last: get() != null means the lists are ready
    }

    /** ok[i] = this phone can draw emoji i. From the cache file when it matches, else Paint.hasGlyph (then cached). Any problem = everything shown. */
    private fun glyphs(ctx: Context, d: EmojiDb): BooleanArray {
        val n = d.size()
        var key = ""
        try { key = EmojiVisible.key(Build.FINGERPRINT ?: "", d.unicodeVersion(), n) } catch (_: Throwable) { }
        val f = File(ctx.filesDir, "emoji_ok_v1.txt")
        try {
            if (f.exists()) {
                val r = EmojiVisible.decode(String(AtomicFile(f).readFully(), Charsets.UTF_8), key, n)
                if (r != null) return r
            }
        } catch (_: Throwable) { }
        val ok = BooleanArray(n)
        try {
            val p = Paint()
            for (i in 0 until n) ok[i] = try { p.hasGlyph(d.emoji(i)) } catch (_: Throwable) { true }
        } catch (_: Throwable) { java.util.Arrays.fill(ok, true) }
        if (EmojiVisible.looksBroken(ok)) { java.util.Arrays.fill(ok, true); return ok }   // not believable: show all, do not cache
        try {
            val af = AtomicFile(f)
            val o = af.startWrite()
            try { o.write(EmojiVisible.encode(key, ok).toByteArray(Charsets.UTF_8)); af.finishWrite(o) } catch (t: Throwable) { af.failWrite(o) }
        } catch (_: Throwable) { }
        return ok
    }
}
