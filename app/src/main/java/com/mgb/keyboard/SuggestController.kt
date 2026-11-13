package com.mgb.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import com.mgb.keyboard.typing.AutoCorrect
import com.mgb.keyboard.typing.SuggestPlan
import com.mgb.keyboard.typing.UserWords
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
    private val openMic: () -> Unit,
    private val selected: () -> String = { "" },                       // v0.10.23 (W9): selected text of the field
    private val replaceSel: (String, Int) -> Unit = { _, _ -> },        // v0.10.23 (W9): replace the selection (text, start) and keep it selected
    private val copyText: (String) -> Unit = { _ -> }                   // v0.10.23 (W9): put text on the clipboard
) {
    private val h = Handler(Looper.getMainLooper())
    private var strip: SuggestStrip? = null
    @Volatile private var en: WordIndex? = null
    @Volatile private var bn: WordIndex? = null
    @Volatile private var loading = false
    private var curWord = ""
    private var curAvro = false
    private val run = Runnable { try { refresh() } catch (_: Throwable) { } }
    private var selS = 0; private var selE = 0     // v0.10.23 (W9): selection (start <= end), set by MgbIme.onUpdateSelection (plain assignment, no work)
    private var qaSel = ""; private var qaStart = 0

    fun selection(a: Int, b: Int) { selS = minOf(a, b); selE = maxOf(a, b) }

    /** v0.10.23 (W9): selection quick actions in the strip. True = shown (the caller stops). Pref qa_on (default OFF); only called from the debounced refresh, never from a key press. */
    private fun quick(s: SuggestStrip): Boolean {
        try {
            if (!com.mgb.keyboard.typing.QuickActionPlan.active(prefs.bool("qa_on", false), blocked(), selS, selE)) return false
            val t = selected()
            val ids = com.mgb.keyboard.typing.QuickActionPlan.ids(t)
            if (ids.isEmpty()) return false
            qaSel = t; qaStart = selS
            s.setActions(ids.map { com.mgb.keyboard.typing.QuickActionPlan.label(it, t) }) { i -> act(ids.getOrNull(i) ?: "") }
            return true
        } catch (_: Throwable) { return false }
    }

    private fun act(id: String) {
        try {
            val t = qaSel
            if (t.isEmpty()) return
            when (id) {
                com.mgb.keyboard.typing.QuickActionPlan.COPY -> { copyText(t); BackupStore.toast(ctx, "Copied") }
                com.mgb.keyboard.typing.QuickActionPlan.COUNT -> BackupStore.toast(ctx, com.mgb.keyboard.typing.TextTools.countsLine(t))
                else -> {
                    val r = com.mgb.keyboard.typing.QuickActionPlan.apply(id, t)
                    if (r != null && r != t) replaceSel(r, qaStart) else BackupStore.toast(ctx, "Nothing to change")
                }
            }
        } catch (_: Throwable) { }
    }

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
        if (selE > selS && quick(s)) return   // v0.10.23 (W9): text selected = quick actions (pref qa_on)
        val a = avroWord()
        val word = if (a.isNotEmpty()) a else SuggestPlan.currentWord(textBefore())
        curWord = word; curAvro = a.isNotEmpty()
        if (!SuggestPlan.wantsLookup(word)) { s.set(emptyList()); return }
        val idx = if (SuggestPlan.lang(banglaOn(), false) == "bn") bn else en
        val base = if (idx == null) emptyList<String>() else idx.lookup(word, SuggestPlan.GRID_MAX, true)
        val mine = try { learned(word) } catch (_: Throwable) { emptyList<String>() }   // v0.10.2 (G3.1): words you typed before (only when learn_on is ON)
        val l = (if (mine.isEmpty()) base else UserWords.merge(mine, base, SuggestPlan.GRID_MAX)).map { WordIndex.matchCase(word, it) }
        s.set(SuggestPlan.layout(word, l), SuggestPlan.grid(l))   // [suggestion 1][typed word][suggestion 2] + grid
    }

    /** v0.10.2 (G3.1): learned words for [word]; empty when learn_on is OFF (default) or the file is still loading. */
    private fun learned(word: String): List<String> {
        if (!prefs.bool("learn_on", false)) return emptyList()
        LearnStore.ensure(ctx)
        val u = LearnStore.words() ?: return emptyList()
        return u.lookup(word, SuggestPlan.GRID_MAX, true)
    }

    /** v0.10.2 (G3.1): called for every text the keyboard types. When it ends a word (space / punctuation / newline) the finished word is learned. Only with learn_on ON; never in password / email / URL / number fields. */
    fun noteTyped(s: String) {
        acFixed = ""   // v0.10.3 (G3.2): any other typing ends the "backspace = undo" chance
        try {
            if (!UserWords.isBoundary(s)) return   // letters / digits: nothing to do (no pref read on the key path)
            if (!prefs.bool("learn_on", false) || blocked()) return
            LearnStore.ensure(ctx)
            val w = SuggestPlan.currentWord(textBefore())
            if (w.isNotEmpty()) LearnStore.learn(w)
        } catch (_: Throwable) { }
    }

    private var acTyped = ""          // v0.10.3 (G3.2): the word as typed, before auto-correct changed it
    private var acFixed = ""          // the correction just made ("" = nothing to undo)
    private val acRejected = HashSet<String>()   // words whose correction was undone: not corrected again (until the keyboard service restarts)

    private var pickSpace = false   // v0.10.6: the last thing typed was the space that a tapped suggestion added

    /** v0.10.6: called first for every text the keyboard types. Right after a tapped suggestion (word + space), typing , . ! ? ; : ) or the danda removes that space: "hello ," becomes "hello,". Pref sp_punct (default ON). Any other typing ends the chance. */
    fun punctFix(s: String) {
        try {
            val p = pickSpace
            val k = kbSpace   // v0.17.34 (M1)
            pickSpace = false
            kbSpace = false
            if ((!p && !k) || s.length != 1 || ",.!?;:)\u0964".indexOf(s[0]) < 0) return
            if (!prefs.bool("sp_punct", true) || blocked()) return
            if (com.mgb.keyboard.typing.PunctSpace.shouldRemove(textBefore(), s[0], k, p)) onApply("", 1, false)   // deletes the one space before the cursor
        } catch (_: Throwable) { }
    }

    private var kbSpace = false   // v0.17.34 (M1): the last thing typed was a space from the space key

    /** v0.17.34 (M1): MgbIme calls this right AFTER a space typed with the space key (set AFTER typeText: typing text clears it). Plain assignment, no work. */
    fun noteSpace() { kbSpace = true }

    /** v0.10.3 (G3.2): called when space is pressed (after the Avro word is committed). Pref ac_on (default OFF). English plain words only. True = the word was replaced by its correction + a space (the space key then does nothing more). */
    fun autoCorrectOnSpace(): Boolean {
        try {
            acFixed = ""
            if (!prefs.bool("ac_on", false) || blocked()) return false
            val e = en
            if (e == null) { load(); return false }
            val word = SuggestPlan.currentWord(textBefore())
            if (word.isEmpty() || acRejected.contains(word.lowercase())) return false
            val mine = if (prefs.bool("learn_on", false)) LearnStore.words() else null
            val fix = AutoCorrect.fix(word, AutoCorrect.Known { w -> e.contains(w) }, if (mine == null) null else AutoCorrect.Known { w -> mine.count(w) > 0 })
            if (fix.isEmpty()) return false
            onApply(fix + " ", word.length, false)
            acTyped = word; acFixed = fix   // set AFTER onApply (typing text clears it)
            return true
        } catch (_: Throwable) { return false }
    }

    /** v0.10.3 (G3.2): Backspace right after an auto-correct = the word you typed comes back (no space), and it is not corrected again. True = handled. */
    fun undoAutoCorrect(): Boolean {
        try {
            val f = acFixed
            if (f.isEmpty()) return false
            acFixed = ""
            if (!AutoCorrect.canUndo(textBefore(), f)) return false
            acRejected.add(acTyped.lowercase())
            onApply(acTyped, f.length + 1, false)
            acFixed = ""
            return true
        } catch (_: Throwable) { return false }
    }

    /** v0.10.2 (G3.1): keyboard closed = write the learned words to the file (only when something changed). */
    fun flushLearned() { try { if (prefs.bool("learn_on", false)) LearnStore.flush() } catch (_: Throwable) { } }

    private fun pick(w: String) {
        try {
            val t = SuggestPlan.toType(curWord, w)
            if (t.isEmpty()) return
            try { if (prefs.bool("learn_on", false) && !blocked()) LearnStore.learn(w) } catch (_: Throwable) { }   // v0.10.2 (G3.1): a picked word counts as used
            onApply(t, if (curAvro) 0 else curWord.length, curAvro)
            pickSpace = true   // v0.10.6 (set AFTER onApply: typing text clears it)
            strip?.set(emptyList())
        } catch (_: Throwable) { }
    }
}
