package com.mgb.keyboard

import com.mgb.keyboard.clip.ClipList
import com.mgb.keyboard.clip.ClipMeta
import com.mgb.keyboard.clip.ClipTrash
import com.mgb.keyboard.typing.ClipSensitive

/**
 * v0.18.4: disk side of the "Recently deleted" list (logic = clip/ClipTrash.java, tested on a computer).
 * Pref key clip_trash_v1 (one string, text only, stays on the phone, no network). Limit = pref clip_undo_n (default 30, 0 = off, step 10 up to 100).
 *  - keep(items): called BEFORE clips are removed (x, Delete, Clear). Remembers text, pinned flag and the stored format (html).
 *  - undoLast(): brings back the whole newest delete action. restore(list) / restoreAll(): bring back chosen entries.
 *  - maintain(): on every read the list is cut to the limit and old entries leave with "Clip auto-delete" (same rule as copied pictures, v0.18.3).
 * Clip code guard ON (pref clip_sens): code-like clips (one-time codes) are NOT kept here (they should not stay anywhere).
 * Limit 0 = the list is emptied and nothing is kept. Never throws. Delete this file + the hooks marked "v0.18.4" = back to v0.18.3.
 */
class ClipTrashStore(private val prefs: JabraPrefs, private val store: ClipStore) {
    private var raw: String? = null
    private var cache: ClipTrash? = null

    private fun load(): ClipTrash {
        val s = try { prefs.str(KEY, "") } catch (_: Throwable) { "" }
        val c = cache
        if (c != null && raw == s) return c
        val t = try { ClipTrash.parse(s) } catch (_: Throwable) { ClipTrash() }
        cache = t
        raw = s
        return t
    }

    private fun save(t: ClipTrash) {
        try {
            val s = t.serialize()
            prefs.setStr(KEY, s)
            cache = t
            raw = s
        } catch (_: Throwable) { }
    }

    /** Undo limit (clips). 0 = Undo list is off. */
    fun limit(): Int = try { ClipTrash.sanitize(prefs.int(PREF, ClipTrash.DEFAULT_LIMIT)) } catch (_: Throwable) { ClipTrash.DEFAULT_LIMIT }

    fun on(): Boolean = limit() > 0

    /** Cuts to the limit, drops entries older than Clip auto-delete, empties when the limit is 0. Saves only when something changed. */
    private fun maintain(): ClipTrash {
        val t = load()
        try {
            val before = t.size()
            if (before == 0) return t
            var changed = 0
            val lim = limit()
            if (lim == 0) { t.clear(); changed = before } else {
                changed += t.trim(lim)
                changed += t.expire(System.currentTimeMillis(), ClipMeta.ttlMs(prefs.int("clip_expire", 0)))
            }
            if (changed > 0) save(t)
        } catch (_: Throwable) { }
        return t
    }

    /** How many clips wait in the list (0 when the Undo list is off). */
    fun count(): Int = try { if (!on()) { maintain(); 0 } else maintain().size() } catch (_: Throwable) { 0 }

    /** Newest first. */
    fun list(): List<ClipTrash.Entry> = try { if (!on()) { maintain(); emptyList() } else maintain().view() } catch (_: Throwable) { emptyList() }

    /** Call BEFORE the clips are removed from the history. */
    fun keep(items: List<ClipList.Item>) {
        try {
            if (items.isEmpty()) return
            val lim = limit()
            if (lim == 0) { maintain(); return }
            val guard = prefs.bool("clip_sens", false)
            val texts = ArrayList<String>()
            val pins = ArrayList<Boolean>()
            val htmls = ArrayList<String?>()
            for (item in items) {
                if (guard && ClipSensitive.looksLikeCode(item.text)) continue
                texts.add(item.text)
                pins.add(item.pinned)
                htmls.add(store.fmtOf(item.text))
            }
            if (texts.isEmpty()) return
            val t = maintain()
            t.push(texts, pins, htmls, System.currentTimeMillis(), lim)
            save(t)
        } catch (_: Throwable) { }
    }

    private fun put(e: ClipTrash.Entry): Boolean = try {
        store.addCaptured(e.text, e.html)
        if (e.pinned) {
            val x = store.view("").firstOrNull { c -> c.text == e.text }
            if (x != null && !x.pinned) store.togglePin(x)
        }
        store.has(e.text)
    } catch (_: Throwable) { false }

    /** Brings back these entries (oldest first, so the newest ends on top, like before). Returns how many are back in the history. */
    fun restore(entries: List<ClipTrash.Entry>): Int = try {
        val t = maintain()
        var n = 0
        for (e in entries.reversed()) if (put(e)) n++
        t.removeAll(entries)
        save(t)
        n
    } catch (_: Throwable) { 0 }

    fun restoreAll(): Int = try { restore(maintain().view()) } catch (_: Throwable) { 0 }

    /** Undo: brings back the whole newest delete action. */
    fun undoLast(): Int = try { restore(maintain().newestGroup()) } catch (_: Throwable) { 0 }

    /** Undo of an action made in this panel session: brings back the entries with these texts (with their format). Returns the texts that were restored. */
    fun restoreTexts(texts: List<String>): Set<String> = try {
        val t = maintain()
        val want = HashSet<String>(texts)
        val found = ArrayList<ClipTrash.Entry>()
        for (e in t.view()) if (want.contains(e.text)) found.add(e)
        val done = HashSet<String>()
        for (e in found.reversed()) if (put(e)) done.add(e.text)
        t.removeAll(found)
        save(t)
        done
    } catch (_: Throwable) { emptySet<String>() }

    /** Forget everything in the list for good. */
    fun emptyAll() { try { val t = load(); t.clear(); save(t) } catch (_: Throwable) { } }

    companion object {
        const val KEY = "clip_trash_v1"
        const val PREF = "clip_undo_n"

        /** For code that has no ClipStore (Privacy page "Clear clipboard history now"): the deleted clips go too. */
        fun clearNow(prefs: JabraPrefs) { try { prefs.setStr(KEY, "") } catch (_: Throwable) { } }
    }
}
