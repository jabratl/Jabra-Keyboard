package com.mgb.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.mgb.keyboard.layout.LayoutBook
import com.mgb.keyboard.layout.LayoutCodec
import com.mgb.keyboard.layout.LayoutDef
import com.mgb.keyboard.layout.LayoutDefaults
import com.mgb.keyboard.layout.LayoutGuard
import com.mgb.keyboard.layout.LayoutPresets
import com.mgb.keyboard.layout.LayoutRoute

/**
 * v0.17.5 (K5a.1) storage + safety net of the custom keyboard layout (plan K 6.8).
 * Own prefs file "jabra_layouts" (NOT the big "jabra" file): layout_v1 = active text, good_v1 = last good copy, safe_n = crash counter.
 * Only the int pref kb_custom (0 classic / 1 custom) lives in "jabra". Any problem = null = the classic keyboard runs (RULE 0).
 */
object LayoutStore {
    private const val FILE = "jabra_layouts"
    private const val K_LAYOUT = "layout_v1"
    private const val K_GOOD = "good_v1"
    private const val K_SAFE = "safe_n"
    private const val K_BUILT = "built_v"      // v0.17.6: version stamp of a layout built from the settings (see LayoutRoute.needsRebuild)
    private const val K_BOOK = "book_v1"       // v0.17.13 (K9): all named layouts (LayoutBook text)
    private const val K_ACTIVE = "active"      // v0.17.13 (K9): name of the active saved layout
    private const val K_EDITED = "edited"      // v0.17.6: set by the editor (K6) so a user's own layout is never rebuilt

    @Volatile private var cacheText: String? = null
    @Volatile private var cacheModel: LayoutDef? = null
    /** Short reason why the last load fell back to classic ("" = no problem). Shown on the app page. */
    @Volatile var lastProblem: String = ""
        private set

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** S1: the active layout, or null. First use = today's keyboard built from the current settings, saved as layout_v1 + good_v1. Never throws. */
    fun load(ctx: Context, prefs: JabraPrefs): LayoutDef? {
        try {
            val s = sp(ctx)
            var text = s.getString(K_LAYOUT, null)
            if (text != null && text == cacheText && cacheModel != null) return cacheModel
            if (text != null && LayoutRoute.needsRebuild(s.getInt(K_BUILT, 0), s.getBoolean(K_EDITED, false))) text = null   // v0.17.6: a v0.17.5 default (built with fewer settings) is rebuilt once; the editor does not exist yet, nothing is lost
            var g = LayoutGuard.gate(text)
            if (g.code == LayoutGuard.EMPTY) {   // nothing saved yet: build the default = exactly the classic keyboard
                text = buildDefaultText(ctx, prefs)
                if (text != null) {
                    s.edit().putString(K_LAYOUT, text).putString(K_GOOD, text).putInt(K_BUILT, LayoutRoute.BUILT_VERSION).apply()
                    g = LayoutGuard.gate(text)
                }
            }
            if (!g.ok()) { lastProblem = g.reason; cacheText = null; cacheModel = null; return null }
            lastProblem = ""
            cacheText = text; cacheModel = g.model
            return g.model
        } catch (_: Throwable) { lastProblem = "layout could not be read"; return null }
    }

    /** v0.17.6: today's keyboard (all the settings that shape it) as layout text. null = could not be built. */
    private fun buildDefaultText(ctx: Context, prefs: JabraPrefs): String? {
        val o = LayoutDefaults.Options()
        try { o.rowGap = prefs.bool("row_gap", true) } catch (_: Throwable) { }
        try { o.numRow = prefs.bool("numrow", true) } catch (_: Throwable) { }
        try { o.keyDp = prefs.keyHeightDp(JabraPrefs.isLand(ctx)) } catch (_: Throwable) { }
        try { o.hints = prefs.bool("hint_on", true); o.hintCustom = prefs.str("hint_custom", "") } catch (_: Throwable) { }
        try { o.nrMode = com.mgb.keyboard.typing.NumRowPlan.sanitizeMode(prefs.int("nr_mode", 0)); o.nrChars = prefs.str("nr_chars", "") } catch (_: Throwable) { }
        try { o.nrHpct = prefs.int("nr_hpct", 100); o.numRowLarge = prefs.bool("numrow_large", false) } catch (_: Throwable) { }
        try { o.pbHomeGap = prefs.bool("pb_home_gap", false) } catch (_: Throwable) { }
        try { o.botStyle = prefs.int("bot_style", 0) } catch (_: Throwable) { }
        try { o.qmShown = prefs.bool("key_qm", false) } catch (_: Throwable) { }
        try { o.keyLang = com.mgb.keyboard.typing.LangSwipe.langKeyVisible(prefs.bool("key_lang", true), prefs.bool("space_lang", false)) } catch (_: Throwable) { }
        try { o.keyGlobe = prefs.bool("key_globe", false); o.keyVoice = prefs.bool("key_voice", false); o.keyEmoji = prefs.bool("key_emoji", false) } catch (_: Throwable) { }
        try { o.spWpct = prefs.int("sp_wpct", 100); o.spHpct = prefs.int("sp_hpct", 100) } catch (_: Throwable) { }
        try {
            val dens = ctx.resources.displayMetrics.density
            o.rowDp = ctx.resources.displayMetrics.widthPixels / dens - 2f * com.mgb.keyboard.typing.EdgePlan.edgeDp(prefs.int("edge_dp", -1))
        } catch (_: Throwable) { }
        return try { LayoutCodec.encode(LayoutDefaults.build(o)) } catch (_: Throwable) { null }
    }

    /** v0.17.8 (K6a): the editor saves [m]. "" = saved (layout_v1 + good_v1, edited = true, built_v = 2: a user's layout is never rebuilt away); else the reason, nothing written (S4). */
    fun save(ctx: Context, m: LayoutDef): String {
        try {
            val bad = LayoutGuard.gateModel(m)
            if (bad != null) return bad
            val text = LayoutCodec.encode(m) ?: return "layout too big"
            sp(ctx).edit().putString(K_LAYOUT, text).putString(K_GOOD, text).putInt(K_BUILT, LayoutRoute.BUILT_VERSION).putBoolean(K_EDITED, true).apply()
            cacheText = null; cacheModel = null; lastProblem = ""
            return ""
        } catch (_: Throwable) { return "could not save" }
    }

    /** v0.17.6: "Rebuild layout from current settings" (app page): the layout becomes today's classic keyboard again, built from the settings now. True = done. */
    fun rebuildFromSettings(ctx: Context, prefs: JabraPrefs): Boolean {
        try {
            val text = buildDefaultText(ctx, prefs) ?: return false
            if (!LayoutGuard.gate(text).ok()) return false
            sp(ctx).edit().putString(K_LAYOUT, text).putString(K_GOOD, text).putInt(K_BUILT, LayoutRoute.BUILT_VERSION).putBoolean(K_EDITED, false).putInt(K_SAFE, 0).apply()
            cacheText = null; cacheModel = null; lastProblem = ""
            return true
        } catch (_: Throwable) { return false }
    }

    /**
     * What buildKeyboard0 asks: the model to draw, or null = classic. kb_custom must be 1; the crash counter is checked (S2: 3 hard stops =
     * kb_custom back to 0 once, the layout text stays untouched) and armed here; [disarmLater] resets it after the first good 5 seconds.
     */
    fun activeModel(ctx: Context, prefs: JabraPrefs): LayoutDef? {
        try {
            if (!LayoutGuard.customOn(prefs.int("kb_custom", 0))) return null
            val s = sp(ctx)
            val n = s.getInt(K_SAFE, 0)
            if (LayoutGuard.tripped(n)) {
                prefs.setInt("kb_custom", 0)
                s.edit().putInt(K_SAFE, 0).apply()
                lastProblem = "custom keyboard crashed 3 times, classic keyboard is back"
                return null
            }
            val m = load(ctx, prefs) ?: return null
            s.edit().putInt(K_SAFE, LayoutGuard.armed(n)).apply()
            return m
        } catch (_: Throwable) { return null }
    }

    /** S2: call after the custom rows were built; 5 s later (Handler, no thread) the crash counter goes back to 0. */
    fun disarmLater(ctx: Context) {
        try { Handler(Looper.getMainLooper()).postDelayed({ try { sp(ctx).edit().putInt(K_SAFE, 0).apply() } catch (_: Throwable) { } }, 5000L) } catch (_: Throwable) { }
    }

    /** S3: "Restore last good layout" (app page). True = restored. */
    fun restoreLastGood(ctx: Context): Boolean {
        try {
            val s = sp(ctx)
            val g = LayoutGuard.gate(s.getString(K_GOOD, null))
            if (!g.ok()) return false
            s.edit().putString(K_LAYOUT, s.getString(K_GOOD, "")).putInt(K_SAFE, 0).apply()
            cacheText = null; cacheModel = null; lastProblem = ""
            return true
        } catch (_: Throwable) { return false }
    }

    /** Status line for the app page: "off" / "OK" / the problem. */
    fun status(ctx: Context, prefs: JabraPrefs): String {
        if (!LayoutGuard.customOn(prefs.int("kb_custom", 0))) return "Custom layout: off (classic keyboard)"
        val m = load(ctx, prefs)
        return if (m != null) "Custom layout: OK" else "Custom layout invalid, classic keyboard used (" + lastProblem + ")"
    }

    // ---------- v0.17.13 (K9): named layouts, import / export, backup pack ----------
    private fun putBook(ctx: Context, b: LayoutBook) { sp(ctx).edit().putString(K_BOOK, b.encode()).putString(K_ACTIVE, b.active()).apply() }

    /** All saved named layouts (never null). */
    fun book(ctx: Context): LayoutBook = try { LayoutBook.decode(sp(ctx).getString(K_BOOK, null)) } catch (_: Throwable) { LayoutBook.empty() }

    /** The active layout text when it passes the load gate, else null. */
    fun currentText(ctx: Context): String? = try {
        val t = sp(ctx).getString(K_LAYOUT, null)
        if (LayoutGuard.gate(t).ok()) t else null
    } catch (_: Throwable) { null }

    /** True when the active layout differs from the saved copy under the active name (or has no name): switching would lose edits. */
    fun hasUnsavedChanges(ctx: Context): Boolean = try {
        val b = book(ctx)
        val cur = sp(ctx).getString(K_LAYOUT, null) ?: ""
        b.active().isEmpty() || b.textOf(b.active()) != cur
    } catch (_: Throwable) { true }

    /** Saves the active layout under [name] (same name = replaces that one) and makes it the active name. "" = done, else the reason. */
    fun saveCurrentAs(ctx: Context, name: String): String {
        try {
            val text = currentText(ctx) ?: return "the current layout is not valid"
            val b = book(ctx)
            val why = b.saveProblem(name, text)
            if (why.isNotEmpty()) return why
            putBook(ctx, b.saveAs(name, text))
            return ""
        } catch (_: Throwable) { return "could not save" }
    }

    /** Makes the saved layout [name] the active layout (checked by the load gate first). "" = done, else the reason. */
    fun switchTo(ctx: Context, name: String): String {
        try {
            val b = book(ctx)
            if (!b.has(name)) return "no such saved layout"
            val text = b.textOf(name)
            if (!LayoutGuard.gate(text).ok()) return "that saved layout is damaged"
            sp(ctx).edit().putString(K_LAYOUT, text).putString(K_GOOD, text).putInt(K_BUILT, LayoutRoute.BUILT_VERSION).putBoolean(K_EDITED, true).putInt(K_SAFE, 0).apply()
            putBook(ctx, b.setActive(name))
            cacheText = null; cacheModel = null; lastProblem = ""
            return ""
        } catch (_: Throwable) { return "could not switch" }
    }

    fun renameSaved(ctx: Context, old: String, new: String): String {
        try {
            val b = book(ctx)
            val nb = b.rename(old, new)
            if (nb === b) return if (LayoutBook.cleanName(new).isEmpty()) "type a name" else "that name is taken"
            putBook(ctx, nb); return ""
        } catch (_: Throwable) { return "could not rename" }
    }

    fun duplicateSaved(ctx: Context, name: String): String {
        try {
            val b = book(ctx)
            val nb = b.duplicate(name)
            if (nb === b) return "could not duplicate (at most " + LayoutBook.MAX_LAYOUTS + " layouts)"
            putBook(ctx, nb); return ""
        } catch (_: Throwable) { return "could not duplicate" }
    }

    fun deleteSaved(ctx: Context, name: String): String {
        try {
            val b = book(ctx)
            val nb = b.delete(name)
            if (nb === b) return "no such saved layout"
            putBook(ctx, nb); return ""
        } catch (_: Throwable) { return "could not delete" }
    }

    /** Export = the active layout as text (for the clipboard), or null. v0.17.14: header "JKL1 <active name>" when the layout has a name. */
    fun exportText(ctx: Context): String? = try {
        val t = currentText(ctx)
        val n = book(ctx).active()
        if (t != null && n.isNotEmpty()) LayoutBook.withName(t, n) else t
    } catch (_: Throwable) { currentText(ctx) }

    /** v0.17.14 (K9b): adds preset [i] as a NEW saved layout [name] (never replaces: a taken name is refused). The active layout is NOT changed. "" = done, else the reason. */
    fun addPreset(ctx: Context, i: Int, name: String): String {
        try {
            val text = LayoutPresets.text(i) ?: return "that preset is not available"
            if (!LayoutGuard.gate(text).ok()) return "that preset is not available"
            val b = book(ctx)
            if (b.has(name)) return "that name is taken"
            val why = b.saveProblem(name, text)
            if (why.isNotEmpty()) return why
            putBook(ctx, b.add(name, text))
            return ""
        } catch (_: Throwable) { return "could not add the preset" }
    }

    /** Import: [text] must pass the load gate; it is added as a NEW saved layout [name] (same name = replaced); the active layout is NOT changed. "" = done. */
    fun importText(ctx: Context, text: String?, name: String): String {
        try {
            val t = text?.trim() ?: ""
            if (t.isEmpty()) return "the clipboard has no text"
            val g = LayoutGuard.gate(t)
            if (!g.ok()) return "not a valid layout (" + g.reason + ")"
            val b = book(ctx)
            val why = b.saveProblem(name, t)
            if (why.isNotEmpty()) return why
            putBook(ctx, b.add(name, t))
            return ""
        } catch (_: Throwable) { return "could not import" }
    }

    /** Backup: the data of file jabra_layouts as one text (LayoutBook.pack), or null when nothing is saved. */
    fun packForBackup(ctx: Context): String? = try {
        val s = sp(ctx)
        val kv = LinkedHashMap<String, String>()
        s.getString(K_LAYOUT, null)?.let { kv[K_LAYOUT] = it }
        s.getString(K_BOOK, null)?.let { kv[K_BOOK] = it }
        s.getString(K_ACTIVE, null)?.let { kv[K_ACTIVE] = it }
        kv[K_EDITED] = if (s.getBoolean(K_EDITED, false)) "1" else "0"
        kv[K_BUILT] = s.getInt(K_BUILT, 0).toString()
        if (kv.containsKey(K_LAYOUT) || kv.containsKey(K_BOOK)) LayoutBook.pack(kv) else null
    } catch (_: Throwable) { null }

    /** Restore: every layout goes through the load gate, invalid ones are dropped. Returns "" or a short note. */
    fun restorePack(ctx: Context, text: String): String {
        try {
            val kv = LayoutBook.unpack(text) ?: return ""
            var note = ""
            val e = sp(ctx).edit()
            val cur = kv[K_LAYOUT]
            if (cur != null) {
                if (LayoutGuard.gate(cur).ok()) {
                    e.putString(K_LAYOUT, cur).putString(K_GOOD, cur).putInt(K_BUILT, LayoutRoute.BUILT_VERSION).putBoolean(K_EDITED, kv[K_EDITED] == "1").putInt(K_SAFE, 0)
                } else note = " The saved keyboard layout was damaged and was not restored."
            }
            val bt = kv[K_BOOK]
            if (bt != null) {
                val full = LayoutBook.decode(bt)
                val clean = full.dropInvalid()
                e.putString(K_BOOK, clean.encode()).putString(K_ACTIVE, clean.active())
                if (clean.size() < full.size()) note += " " + (full.size() - clean.size()) + " damaged saved layout(s) were dropped."
            }
            e.apply()
            cacheText = null; cacheModel = null; lastProblem = ""
            return note
        } catch (_: Throwable) { return "" }
    }
}
