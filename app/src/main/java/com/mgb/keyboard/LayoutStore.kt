package com.mgb.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.mgb.keyboard.layout.LayoutCodec
import com.mgb.keyboard.layout.LayoutDef
import com.mgb.keyboard.layout.LayoutDefaults
import com.mgb.keyboard.layout.LayoutGuard
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
}
