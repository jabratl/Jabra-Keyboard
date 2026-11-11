package com.mgb.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import com.mgb.keyboard.typing.SuggestPlan
import com.mgb.keyboard.typing.WordIndex

/**
 * v0.10.1 (G2) glue between the keyboard (MgbIme) and the strip. Owns: the two word lists (loaded once, lazily, on a background thread),
 * the 50 ms debounce (so nothing heavy runs on the key-press path: a key only posts a tiny Runnable), the current word and the tap action.
 * Everything is try/catch; a missing / broken word list = blank strip.
 * Pref sg_on (default ON). The row REPLACES the toolbar row while sg_on is ON; the left icon opens the toolbar (toolsOpen), its first key comes back.
 */
class SuggestController(
    private val ctx: Context,
    private val prefs: JabraPrefs,
    private val avroWord: () -> String,            // Avro word being typed (already Bangla), "" when none
    private val textBefore: () -> String,          // up to SuggestPlan.LOOKBACK chars before the cursor
    private val banglaOn: () -> Boolean,
    private val blocked: () -> Boolean,            // password / email / URL / number field
    private val onApply: (text: String, deleteLen: Int, fromAvro: Boolean) -> Unit,
    private val openClip: () -> Unit,
    private val hasTools: () -> Boolean,           // toolbar row exists (edit_bar ON)
    private val openTools: () -> Unit,
    private val openMic: () -> Unit
) {
    private val h = Handler(Looper.getMainLooper())
    private var strip: SuggestStrip? = null
    @Volatile private var en: WordIndex? = null
    @Volatile private var bn: WordIndex? = null
    @Volatile private var loading = false
    private var curWord = ""
    private var curAvro = false
    private val run = Runnable { try { refresh() } catch (_: Throwable) { } }

    /** True when the strip takes the toolbar's place now (pref sg_on ON and the toolbar page is not opened). */
    fun wants(toolsOpen: Boolean): Boolean = SuggestPlan.show(prefs.bool("sg_on", true), toolsOpen)

    /** Called by buildKeyboard: adds the strip row to [root] when it should show (else forgets the old strip). Returns true when added. */
    fun attach(root: LinearLayout, toolsOpen: Boolean): Boolean {
        strip = null
        if (!wants(toolsOpen)) return false
        val s = SuggestStrip(ctx, prefs, { w -> pick(w) }, { if (hasTools()) openTools() else openClip() }, { openMic() })
        strip = s
        root.addView(s.view)
        load()
        schedule()
        return true
    }

    private fun load() {
        if (loading || (en != null && bn != null)) return
        loading = true
        Thread {
            try {
                if (en == null) en = try { ctx.assets.open("words/en.txt").use { WordIndex.parseOrNull(it) } } catch (_: Throwable) { null }
                if (bn == null) bn = try { ctx.assets.open("words/bn.txt").use { WordIndex.parseOrNull(it) } } catch (_: Throwable) { null }
            } catch (_: Throwable) { }
            loading = false
            try { h.post(run) } catch (_: Throwable) { }
        }.start()
    }

    /** Cheap: only (re)starts a 50 ms timer. Safe to call from the key path. */
    fun schedule() {
        try {
            if (strip == null) return
            h.removeCallbacks(run)
            h.postDelayed(run, 50L)
        } catch (_: Throwable) { }
    }

    private fun refresh() {
        val s = strip ?: return
        if (!s.view.isAttachedToWindow) return
        if (blocked()) { s.set(emptyList(), emptyList(), false); return }
        val a = avroWord()
        val word = if (a.isNotEmpty()) a else SuggestPlan.currentWord(textBefore())
        curWord = word; curAvro = a.isNotEmpty()
        if (!SuggestPlan.wantsLookup(word)) { s.set(emptyList()); return }
        val idx = if (SuggestPlan.lang(banglaOn(), false) == "bn") bn else en
        val l = if (idx == null) emptyList<String>() else idx.lookup(word, SuggestPlan.GRID_MAX, true).map { WordIndex.matchCase(word, it) }
        s.set(SuggestPlan.layout(word, l), SuggestPlan.grid(l))   // [suggestion 1][typed word][suggestion 2] + grid
    }

    private fun pick(w: String) {
        try {
            val t = SuggestPlan.toType(curWord, w)
            if (t.isEmpty()) return
            onApply(t, if (curAvro) 0 else curWord.length, curAvro)
            strip?.set(emptyList())
        } catch (_: Throwable) { }
    }
}
