package com.mgb.keyboard

import android.content.Context
import android.graphics.Paint
import android.os.Build
import android.util.AtomicFile
import com.mgb.keyboard.emoji.EmojiDb
import com.mgb.keyboard.emoji.EmojiSearch
import com.mgb.keyboard.emoji.EmojiVisible
import java.io.File

/**
 * v0.8.1 (E2): loads the emoji data file (assets/emoji/emoji_v1.txt) ONCE on a background thread and keeps it in memory.
 * v0.8.2 (E3): on the same thread, finds which emoji THIS phone can draw (Paint.hasGlyph) and keeps only those in the group lists.
 * The answer is cached in filesDir/emoji_ok_v1.txt (key = phone build + data Unicode version + count), so the next start is instant.
 * get() = null until the file is parsed AND the glyph check is done, or for good if loading failed: then the OLD emoji panel is used (never blank).
 * v0.8.3 (E4): also builds the search index (EmojiSearch) and an emoji -> item map on the same thread; search(q) and toned(e, t) / toneVariants(e) use them.
 * v0.8.5 (E6): a downloaded list (filesDir/emoji_user_v1.txt, made by EmojiUpdater) is used first, else the asset; reload() starts over after an update.
 * No timer, no service. Nothing here ever throws.
 */
object EmojiStore {
    @Volatile private var db: EmojiDb? = null
    @Volatile private var lists: Array<List<String>>? = null
    @Volatile private var hidden: HashSet<String> = HashSet()
    @Volatile private var okBits: BooleanArray? = null
    @Volatile private var finder: EmojiSearch? = null
    @Volatile private var index: HashMap<String, Int> = HashMap()
    private val variantOk = HashMap<String, Boolean>()   // v0.8.3: tone variant -> can this phone draw it (filled lazily, UI thread)
    private var variantPaint: Paint? = null
    private var started = false

    fun get(): EmojiDb? = db

    /** v0.8.5: where the downloaded emoji list is saved. */
    fun userFile(ctx: Context): File = File(ctx.filesDir, "emoji_user_v1.txt")

    /** v0.8.5: load again (after "Update emoji list" or "Use built-in"). get() is null until the new list is ready, so the OLD panel shows meanwhile. */
    fun reload(ctx: Context) {
        synchronized(this) { started = false }
        db = null
        preload(ctx)
    }

    /** Emoji of group g that this phone can draw, in file order. Empty if not ready / bad index. */
    fun groupItems(g: Int): List<String> {
        val l = lists ?: return emptyList()
        return if (g >= 0 && g < l.size) l[g] else emptyList()
    }

    /** True for an emoji of the data file that this phone cannot draw. Unknown strings = false (kept). */
    fun isHidden(e: String): Boolean = hidden.contains(e)

    /** v0.8.3: base emoji that match the words (name + subgroup), drawable only, best first, max 'max'. Empty if not ready. */
    fun search(q: String, max: Int): List<String> {
        val d = db ?: return emptyList()
        val f = finder ?: return emptyList()
        return try {
            val out = ArrayList<String>()
            for (i in f.search(q, okBits, max)) out.add(d.emoji(i))
            out
        } catch (_: Throwable) { emptyList() }
    }

    /** v0.8.3: can this phone draw string v? (hasGlyph, answer kept in memory). Any problem = true (show it). */
    private fun drawable(v: String): Boolean {
        try {
            synchronized(variantOk) {
                val c = variantOk[v]
                if (c != null) return c
                var p = variantPaint
                if (p == null) { p = Paint(); variantPaint = p }
                val r = try { p.hasGlyph(v) } catch (_: Throwable) { true }
                variantOk[v] = r
                return r
            }
        } catch (_: Throwable) { return true }
    }

    /** v0.8.3: true when e is a base emoji of the file that accepts a skin tone. */
    fun toneable(e: String): Boolean {
        val d = db ?: return false
        val i = index[e] ?: return false
        return try { d.toneable(i) } catch (_: Throwable) { false }
    }

    /** v0.8.3: e with default tone t (0..4) when e takes a tone AND the phone can draw that variant; else e itself (t < 0 = e). */
    fun toned(e: String, t: Int): String {
        if (t < 0 || t > 4) return e
        val d = db ?: return e
        return try {
            val i = index[e] ?: return e
            if (!d.toneable(i)) return e
            val v = d.tone(i, t)
            if (v != e && drawable(v)) v else e
        } catch (_: Throwable) { e }
    }

    /** v0.8.3: the 5 tone variants of e that this phone can draw, as (tone index, string). Empty when e takes no tone or none can be drawn. */
    fun toneVariants(e: String): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        val d = db ?: return out
        try {
            val i = index[e] ?: return out
            if (!d.toneable(i)) return out
            for (t in 0..4) {
                val v = d.tone(i, t)
                if (v != e && drawable(v)) out.add(Pair(t, v))
            }
        } catch (_: Throwable) { out.clear() }
        return out
    }

    fun preload(ctx: Context) {
        synchronized(this) {
            if (started) return
            started = true
        }
        try {
            val app = ctx.applicationContext ?: ctx
            val t = Thread(Runnable {
                try {
                    var parsed: EmojiDb? = null
                    try {   // v0.8.5: the downloaded list first (any problem with it = the built-in asset)
                        val uf = userFile(app)
                        if (uf.exists() && uf.length() <= EmojiDb.MAX_BYTES) parsed = EmojiDb.parseOrNull(String(AtomicFile(uf).readFully(), Charsets.UTF_8))
                    } catch (_: Throwable) { parsed = null }
                    if (parsed == null) {
                        val s = app.assets.open("emoji/emoji_v1.txt")
                        try { parsed = EmojiDb.parseOrNull(s) } finally { try { s.close() } catch (_: Throwable) { } }
                    }
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
        okBits = ok
        try {   // v0.8.3: search index + emoji -> item map (about 20 ms); a problem only switches search / tones off
            val m = HashMap<String, Int>(d.size() * 2)
            for (i in 0 until d.size()) m[d.emoji(i)] = i
            index = m
            finder = EmojiSearch(d)
        } catch (_: Throwable) { finder = null }
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
