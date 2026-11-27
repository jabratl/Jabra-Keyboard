package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.clip.ClipList   // v0.38.0 (P1)
import com.mgb.keyboard.typing.FixNames   // v0.57.0 (FX1)
import com.mgb.keyboard.typing.LookColors
import com.mgb.keyboard.typing.NovelBook   // v0.55.0 (NV1)
import com.mgb.keyboard.typing.PanelLayout
import com.mgb.keyboard.typing.QuickPaste   // v0.38.0 (P1)
import com.mgb.keyboard.typing.RecentPairs
import com.mgb.keyboard.typing.ReplaceCheck   // v0.40.0
import com.mgb.keyboard.typing.ReplaceEngine
import com.mgb.keyboard.typing.ReplaceExtras
import com.mgb.keyboard.typing.ReplaceFloat   // v0.39.1 (FLOAT F2)
import com.mgb.keyboard.typing.ReplaceHelp
import com.mgb.keyboard.typing.ReplacePads
import com.mgb.keyboard.typing.ReplacePairList   // v0.40.0
import com.mgb.keyboard.typing.ReplacePaste   // v0.41.0
import com.mgb.keyboard.typing.ReplaceMore   // v0.42.3
import com.mgb.keyboard.typing.ReplacePeek
import com.mgb.keyboard.typing.ReplaceSlimPlan   // v0.42.2
import com.mgb.keyboard.typing.ReplaceProtect   // v0.41.0
import com.mgb.keyboard.typing.ReplaceRules

/*
 * v0.24.0 (R1) ADVANCED FIND & REPLACE panel (route showPanel("replace"), toolbar item "rp", OFF until the user adds it in Edit toolbar).
 * Engine = typing/ReplaceEngine.java (pure Java, tested). Keys of the small keyboard = typing/ReplacePads.java.
 * Main view: Find / With boxes (tap = type; Sel = take the selected text; Clip = take the clipboard), options (Text | Word | Regex, Aa, \n),
 * live match count + preview of the first matches, [Replace all (N)] [1st] [In: Field / Selection] [Undo / Copy].
 * Typing view: an IME panel cannot hold a normal text box, so a small keyboard is drawn inside the panel (same idea as the clipboard search):
 * pages abc | #+= (with regex helper keys) (v0.42.0: the Bangla pages were removed). Typing only updates one text line (no rebuild per key).
 * Source = the selected text or the whole field (same reader as the Formatter / Text tools: FmtIo). Replace on the WHOLE field = two taps (Sure?) + Undo.
 * Saved: ONLY the option switches (pref rp_opt, one number). Find / With texts stay in memory while the keyboard runs (never written to disk).
 * build() returns null on any problem -> the keyboard shows the letters.
 */
class ReplacePanel(private val ctx: Context, private val host: JabraHost, private val ioField: FmtIo) {

    private val prefs = JabraPrefs(ctx)
    private var clipIo: FmtIo? = null   // v0.29.0: set = the Replace works on ONE CLIP (ClipReplaceIo) instead of the field; reset() clears it
    private val io: FmtIo get() = clipIo ?: ioField

    private var opt = ReplaceEngine.Opt()
    private var find = ""
    private var repl = ""
    private var typing = 0                 // 0 = main view, 1 = typing Find, 2 = typing With
    private var padPage = 0
    private var shift = false
    private var src = ""                   // text being worked on
    private var usedSel = false            // true = src is the selection
    private var part = 0                   // v0.29.0: 0 = whole field, 1 = from the cursor to the end, 2 = from the start to the cursor
    private var partCut = -1               // cursor position used by part (kept fixed while the panel works, -1 = read it again)
    private var copiedOut: String? = null    // v0.37.2: result of the last Copy (the Copy button becomes Paste while the result is the same)
    private var copiedHtml: String? = null
    private var fullText = ""              // the whole field text when part != 0 (src is only a piece of it)
    private var setPreviewOn = false      // v0.31.0: Sets view: a tap shows a preview instead of loading
    private var setPrev: Pair<String, ReplaceStore.Def>? = null
    private var caretPos = 0               // caret inside Find / With while typing
    private var hasSel = false             // the field has a selection right now
    private var msg = ""
    private var confirmAll = false
    private val undoStack = ReplaceExtras.UndoStack()   // v0.28.0 (R3b): last 3 Replace taps, memory only (was: undoRes / undoOrig = one level)
    private var helpPage = 0                                // v0.28.0 (R3b): typing == 5 = help view, page of ReplaceHelp
    private var valueTv: TextView? = null  // typing view: the line that shows the field
    private var keysBox: LinearLayout? = null
    // v0.25.0 (R2): rule sets + step through matches
    private var setName: String? = null                       // a set is active = rules run instead of Find / With
    private var setRules: List<ReplaceRules.Rule>? = null
    private var setOpt = ReplaceEngine.Opt()
    private var setRun: ReplaceRules.RunResult? = null
    private var step = 0                                      // index of the match shown by [<] [>]
    private var rprAsk = false                                // v0.26.0 (R3): Recent pairs > Clear needs two taps
    // v0.40.0: PAIR LIST ("+Pair") = a rule list kept in MEMORY only (typing/ReplacePairList.java); it runs through the same set code (setRules), pairMode = true
    private var pairMode = false
    private val pairRules = ArrayList<ReplaceRules.Rule>()
    private var checkAck = false                              // the user saw the Check warnings of this list (a tap on Replace all then runs it)
    private var checkWarn: List<ReplaceCheck.Warn>? = null
    private var clearAsk = false                              // Clear needs two taps
    private var editIdx = -1                                  // v0.41.1: index of the pair being edited IN PLACE (from the Pairs view); -1 = none. typing == 8 = the Pairs view.
    // v0.41.0: NEVER-CHANGE list (typing/ReplaceProtect.java). Pref rp_protect = one term per line (the user's own list, written only when the user adds / removes a term). typing == 7 = its view.
    private val protectList = ArrayList<String>()
    private var protectAsk = false                            // Clear needs two taps
    // v0.55.0 (NV1): ONE GLOSSARY PER NOVEL. novelCur = active novel ("" = none: all sets + the general Never change list, as before). typing == 9 = the Novels view.
    // Prefs: rp_novels (registry, one name per line), rp_novel_cur, rp_nv_<hash> (Never change list of one novel). Logic = typing/NovelBook.java (tested).
    private var novelCur = ""
    private var topicChip = NovelBook.ALL                     // topic chip of the Sets view
    private var novelDelAsk = false                           // Delete novel needs two taps
    private var novelNote = ""                                // line under the Novels list
    // v0.38.0 (P1): paste keys in the typing view. typing == 6 = the paste list (Recent / Pinned); pasteBack = the field (1 Find / 2 With) to return to
    var clipList: (() -> List<ClipList.Item>)? = null          // set by MgbIme: the clipboard history (null = the list shows only the latest copy)
    private var pasteBack = 1
    private var pasteTab = 0
    private var slimFail = false                                // v0.42.1 (M1): the slim Options menu could not open: back to the old option chips
    private var optsOpen = false                                // v0.38.1 (P2): mini look: the option chips (Aa, Keep, List ...) are folded away until "Options" is tapped

    /** v0.29.0: open on ONE clip: reset first (clears any old clip), then the clip text is the source. */
    fun openForClip(cio: FmtIo) {
        reset()
        clipIo = cio
        usedSel = false
        readSource(false)
    }

    /** Call when the panel is opened from outside: reads the options and the field once. */
    fun reset() {
        PeekPopup.dismiss()   // v0.32.0
        fwinFail = false   // v0.39.3 (FLOAT F4)
        optsOpen = false   // v0.38.1 (P2)
        dMenu = 0; dSkip.clear(); dKey = ""   // v0.53.0 (DIFF)
        fnBack = false; fnToast = ""   // v0.57.0 (FX1)
        setPreviewOn = false; setPrev = null; part = 0; partCut = -1; clipIo = null; msg = ""; confirmAll = false; undoStack.clear(); helpPage = 0; typing = 0; padPage = 0; shift = false; step = 0; rprAsk = false
        try { opt = ReplaceEngine.unpack(prefs.int("rp_opt", ReplaceEngine.pack(ReplaceEngine.Opt()))) } catch (_: Throwable) { opt = ReplaceEngine.Opt() }
        loadProtect()   // v0.41.0
        readSource(true)
    }

    private fun saveOpt() { try { prefs.setInt("rp_opt", ReplaceEngine.pack(opt)) } catch (_: Throwable) { } }

    /** pickSel = true: scope starts as the selection when there is one. false: keep the scope, but drop it when the selection is gone. */
    private fun readSource(pickSel: Boolean) {
        try {
            if (io.blocked()) { src = ""; hasSel = false; usedSel = false; msg = "Not available in password fields."; return }
            val sel = io.selected()
            hasSel = !sel.isNullOrEmpty()
            if (pickSel) usedSel = hasSel else if (!hasSel) usedSel = false
            src = if (usedSel && hasSel && sel != null) sel else (io.wholeText() ?: "")
            fullText = src   // v0.29.0: From / To cursor = only a piece of the field is src
            if (usedSel) { part = 0; partCut = -1 }
            else if (part != 0) {
                if (partCut < 0) partCut = io.cursorPos()
                if (partCut < 0 || partCut > fullText.length) { part = 0; partCut = -1; msg = "Cursor position is not available here (select text or use Field)" }
                else src = if (part == 1) fullText.substring(partCut) else fullText.substring(0, partCut)
            }
        } catch (_: Throwable) { src = ""; usedSel = false; hasSel = false }
    }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()
    private fun refresh() { host.showPanel("replace") }

    // ------------------------------------------------------------------ v0.32.0 (PEEK): long-press card (PeekPopup.kt); a short tap works as before, a long press never loads / replaces anything

    /** Card of [Replace all]: first matches (or what the active set would change). Password field = one line. */
    private fun peekResult(res: ReplaceEngine.Result): ReplacePeek.Peek {
        if (io.blocked()) return ReplacePeek.message("Replace all", "Not available in password fields.")
        val rl = setRules
        if (rl != null) { val pk = ReplacePeek.forSet(setName, ReplaceRules.serialize(rl), setOpt, src); pk.hint = "Release to close"; return pk }
        return ReplacePeek.forResult(src, res, 3, 24)
    }

    /** Card of a set (Sets view). */
    private fun peekSet(name: String, d: ReplaceStore.Def): ReplacePeek.Peek =
        if (io.blocked()) ReplacePeek.message(name, "Not available in password fields.") else ReplacePeek.forSet(name, d.rules, dOpt(d), src)

    /** Long press on a set: card above; when no card can be shown = FALLBACK = the v0.31.0 inline preview box. */
    private fun peekSetAt(a: View, name: String, d: ReplaceStore.Def) {
        try { if (!PeekPopup.show(a, prefs, peekSet(name, d), host)) { setPrev = Pair(name, d); refresh() } } catch (_: Throwable) { }
    }

    /** Long click + release listener on a button. Long click is consumed (true) = no click after a long press. */
    private fun peekOn(v: View, make: () -> ReplacePeek.Peek) {
        try {
            v.setOnLongClickListener { a -> try { PeekPopup.show(a, prefs, make(), host) } catch (_: Throwable) { }; true }
            PeekPopup.watchRelease(v)
        } catch (_: Throwable) { }
    }

    private fun btn(label: String, weight: Float, hDp: Int, onClick: (Button) -> Unit): Button {
        try { return UiKit.btn(ctx, prefs, label, weight, hDp, 12f, { v -> host.feedback(v) }, onClick) } catch (_: Throwable) { }
        return btnOld(label, weight, hDp, onClick)
    }

    private fun paintBg(v: View) { try { JabraThemes.paint(prefs, v) } catch (_: Throwable) { v.setBackgroundColor(Color.parseColor("#DDDDDD")) } }
    private fun dim(): Int = try { JabraTokens.dimText(prefs) } catch (_: Throwable) { Color.DKGRAY }
    private fun ink(): Int = try { JabraTokens.panelText(prefs) } catch (_: Throwable) { Color.BLACK }

    private fun btnOld(label: String, weight: Float, hDp: Int, onClick: (Button) -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        b.textSize = 12f
        b.setOnClickListener {
            try { host.feedback(b) } catch (_: Throwable) { }
            try { onClick(b) } catch (_: Throwable) { }
        }
        val lp = LinearLayout.LayoutParams(if (weight > 0f) 0 else LinearLayout.LayoutParams.WRAP_CONTENT, dp(hDp), if (weight > 0f) weight else 0f)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        b.layoutParams = lp
        return b
    }

    private fun row(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    private fun calc(): ReplaceEngine.Result {
        applyProtect()   // v0.41.0: the never-change list reaches opt / setOpt (also after a set was loaded or a pair list started)
        val rules = setRules
        if (rules != null) {   // v0.25.0 (R2): a set is active
            val r = ReplaceEngine.Result()
            try {
                val rr = ReplaceRules.run(src, rules, setOpt)
                setRun = rr
                r.out = rr.out; r.count = rr.changed; r.changed = rr.changed; r.skipped = rr.skipped
                r.edits = rr.edits   // v0.42.5: a pair list is now written match by match too (the format of the field stays) and Copy can keep the format
            } catch (_: Throwable) { setRun = null }
            return r
        }
        return try { ReplaceEngine.replaceAll(src, find, repl, opt) } catch (_: Throwable) { ReplaceEngine.Result() }
    }

    fun build(): View? = try { PeekPopup.dismiss(); if (typing != 0) { try { fwin?.hide() } catch (_: Throwable) { } }; if (typing == 3) setsViewPick() else if (typing == 4) recentView() else if (typing == 5) helpView() else if (typing == 6) replacePicker() else if (typing == 7) protectView() else if (typing == 8) pairsView() else if (typing == 9) novelsView() else if (typing == 10) fnView() else if (typing != 0) typeView() else mainViewPick() } catch (_: Throwable) { null }   // v0.26.0 (R3): 4 = Recent pairs; Pick = new look when panel_ui = New, else the classic view

    // ------------------------------------------------------------------ main view

    private fun fieldLabel(name: String, v: String): String {
        if (v.isEmpty()) return name + ": (tap to type)"
        val t = v.replace("\n", "\u23CE").replace("\t", "\u21E5")
        return name + ": " + (if (t.length > 22) ReplaceExtras.head(t, 22) + "\u2026" else t)
    }

    private fun optChanged() { confirmAll = false; saveOpt(); refresh() }

    private fun mainView(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)
        root.setPadding(dp(2), dp(4), dp(2), dp(4))
        valueTv = null
        keysBox = null

        val res = calc()
        val shown = msg
        msg = ""

        if (setRules != null) {
            val rs = row()
            rs.addView(btn("ABC", 0.7f, 36) { host.showLetters() })
            rs.addView(btn("Set: " + (setName ?: "") + " (" + (setRules?.count { it.kind != ReplaceRules.K_COMMENT } ?: 0) + ")", 3f, 36) { typing = 3; refresh() })
            rs.addView(btn("\u2715 Set", 1f, 36) { dropSet(); refresh() })
            rs.addView(btn("Fmt", 0.8f, 36) { copyForFormatter() })   // v0.28.0 (R3b): Formatter bridge
            root.addView(rs)
        } else {
        // Find
        val r1 = row()
        r1.addView(btn("ABC", 0.7f, 36) { host.showLetters() })
        r1.addView(btn(fieldLabel("Find", find), 3f, 36) { startTyping(1) })
        r1.addView(btn("Sel", 0.8f, 36) { take(true, io.selected(), "Nothing is selected") })
        r1.addView(btn("Clip", 0.8f, 36) { take(true, io.clipText(), "Clipboard is empty") })
        root.addView(r1)

        // With
        val r2 = row()
        r2.addView(btn("\u21C4", 0.7f, 36) { swap() })
        r2.addView(btn(fieldLabel("With", repl), 3f, 36) { startTyping(2) })
        r2.addView(btn("Sel", 0.8f, 36) { take(false, io.selected(), "Nothing is selected") })
        r2.addView(btn("Clip", 0.8f, 36) { take(false, io.clipText(), "Clipboard is empty") })
        root.addView(r2)

        // options
        val r3 = row()
        for (i in 0..2) {
            r3.addView(btn((if (opt.mode == i) "\u2713 " else "") + ReplaceEngine.MODE_NAMES[i], 1f, 32) { opt.mode = i; optChanged() })
        }
        r3.addView(btn((if (opt.matchCase) "\u2713 " else "") + "Aa", 0.8f, 32) { opt.matchCase = !opt.matchCase; optChanged() })
        r3.addView(btn((if (opt.keepCase) "\u2713 " else "") + "Keep", 0.9f, 32) { opt.keepCase = !opt.keepCase; if (opt.keepCase) opt.matchCase = false; optChanged() })   // v0.29.0: keep the case look
        if (opt.mode != ReplaceEngine.MODE_REGEX) {
            r3.addView(btn((if (opt.list) "\u2713 " else "") + "List", 0.9f, 32) { opt.list = !opt.list; optChanged() })   // v0.31.0: Find = a | b | c
            r3.addView(btn((if (opt.smart) "\u2713 " else "") + "Smart", 0.9f, 32) { opt.smart = !opt.smart; optChanged() })   // v0.41.0
            r3.addView(btn((if (opt.escapes) "\u2713 " else "") + "\\n", 0.8f, 32) { opt.escapes = !opt.escapes; optChanged() })
        }
        root.addView(r3)

        // v0.25.0 (R2): sets + step through the matches
        val r4 = row()
        r4.addView(btn("Sets", 1f, 32) { typing = 3; confirmAll = false; refresh() })
        r4.addView(btn("Recent", 1f, 32) { typing = 4; confirmAll = false; rprAsk = false; refresh() })   // v0.26.0 (R3)
        r4.addView(btn("Never", 1f, 32) { openProtect() })   // v0.41.0: never-change list
        r4.addView(btn("?", 0.5f, 32) { typing = 5; helpPage = 0; confirmAll = false; refresh() })   // v0.28.0 (R3b): help
        val nHits = res.hits.size
        if (nHits > 0 && res.err == null) {
            step = step.coerceIn(0, nHits - 1)
            r4.addView(btn("<", 0.6f, 32) { step = if (step <= 0) nHits - 1 else step - 1; showStep(res) })
            r4.addView(btn((step + 1).toString() + "/" + res.count, 1f, 32) { showStep(res) })
            r4.addView(btn("Skip", 0.8f, 32) { step = if (step >= nHits - 1) 0 else step + 1; showStep(res) })   // v0.29.0: Skip = next match, nothing replaced
            r4.addView(btn("Replace this", 1.3f, 32) { onReplace(ReplaceEngine.replaceNth(src, find, repl, opt, step + 1), true) })   // the next match moves into this place = replace + next
            r4.addView(btn("Rest", 0.8f, 32) { onReplace(ReplaceEngine.run(src, find, repl, opt, step + 1, Int.MAX_VALUE), true) })   // v0.29.0: this match and all after it
        }
        root.addView(r4)
        }   // end of: no set active

        // info line
        val info = TextView(ctx)
        var line = ReplaceEngine.info(res)
        if (src.isEmpty() && res.err == null) line = "No text: select text or type some first"
        else if (setRules != null) { val rr = setRun; if (rr != null) line = ReplaceRules.info(rr, setRules?.count { it.kind != ReplaceRules.K_COMMENT } ?: 0) + "  \u00B7  " + scopeWord() + src.length }
        else if (res.err == null) line += "  \u00B7  " + scopeWord() + src.length
        info.text = if (shown.isNotEmpty()) shown else line
        info.textSize = 12f
        info.setTextColor(dim())
        info.setPadding(dp(8), dp(2), dp(8), dp(2))
        root.addView(info)

        // preview
        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56))
        val pv = TextView(ctx)
        pv.text = previewText(res)
        pv.textSize = 13f
        pv.setTextColor(try { JabraTokens.cardText(prefs) } catch (_: Throwable) { Color.BLACK })
        pv.setBackgroundColor(try { if (JabraTokens.isClassic(prefs)) Color.WHITE else (JabraTokens.btnFill(prefs) ?: Color.WHITE) } catch (_: Throwable) { Color.WHITE })
        pv.setPadding(dp(8), dp(4), dp(8), dp(4))
        sv.addView(pv)
        root.addView(sv)

        // actions
        val act = row()
        val ask = confirmAll && !usedSel
        val allLabel = if (ask) "Sure? Replace all" else if (res.err == null && res.count > 0) "Replace all (" + res.count + ")" else "Replace all"
        val allB = btn(allLabel, 1.5f, 40) { onReplace(res, false) }
        peekOn(allB) { peekResult(res) }   // v0.32.0
        act.addView(allB)
        if (setRules == null) act.addView(btn("1st", 0.6f, 40) { onReplace(ReplaceEngine.replaceFirst(src, find, repl, opt), true) })
        act.addView(btn(scopeLabel(), 1.1f, 40) { toggleScope() })
        if (undoStack.canUndo()) act.addView(btn("\u21B6 " + ReplaceExtras.undoLabel(undoStack.size()), 0.9f, 40) { undo() })
        else if (copiedOut != null && copiedOut == res.out) act.addView(btn("Paste", 0.9f, 40) { pasteCopied() })
        else act.addView(btn("Copy", 0.9f, 40) { copyResult(res) })
        root.addView(act)
        return root
    }

    private val PREV_MAX = 12   // v0.45.6: lines in the preview box (was 6), the panel is as tall as the keyboard now and the box takes the free room

    private fun previewText(res: ReplaceEngine.Result): String {
        val rules = setRules
        if (rules != null) {   // v0.25.0 (R2): one line per rule that changed something
            val rr = setRun ?: return "(no result)"
            val sb = StringBuilder()
            var n = 0
            for (i in rules.indices) {
                if (i >= rr.counts.size || rr.counts[i] == 0) continue
                if (n >= PREV_MAX) { sb.append("\n\u2026"); break }
                if (n > 0) sb.append("\n")
                val r = rules[i]
                sb.append(rr.counts[i]).append("\u00D7  ").append(r.find.take(24)).append(if (r.kind == ReplaceRules.K_DEL || r.kind == ReplaceRules.K_DELRE) "  (line removed)" else "  \u2192  " + r.with.take(24))
                n++
            }
            if (n == 0) return if (rr.err != null) rr.err!! else "(no match)"
            return sb.toString()
        }
        val e = res.err
        if (e != null) return "(fix the Find to see matches)"
        if (res.blank) {
            return when (opt.mode) {
                ReplaceEngine.MODE_REGEX -> "Regex: \$1 = group 1, \\n = new line, \\U...\\E = CAPITALS. ^ and \$ work per line."
                ReplaceEngine.MODE_WORD -> "Whole word only: cat finds cat, not concat. Tap Find to type, Sel = selected text, Clip = clipboard."
                else -> "Tap Find / With to type. Sel = take the selected text, Clip = take the clipboard."
            }
        }
        if (res.hits.isEmpty()) return "(no match)"
        val sb = StringBuilder()
        var n = 0
        val from = if (step in res.hits.indices) step else 0
        for (hi in from until res.hits.size) {
            if (n >= PREV_MAX) break
            if (n > 0) sb.append("\n")
            if (n == 0 && res.hits.size > 1) sb.append("\u25B6 ")
            sb.append(ReplaceEngine.line(src, res.hits[hi], 14))
            n++
        }
        if (res.count > from + n) sb.append("\n\u2026 +").append(res.count - from - n).append(" more")
        return sb.toString()
    }

    private fun startTyping(which: Int) {
        typing = which; padPage = 0; shift = false; confirmAll = false; caretPos = cur().length
        refresh()
    }

    private fun swap() {
        val t = find; find = repl; repl = t
        confirmAll = false
        refresh()
    }

    private fun take(isFind: Boolean, v: String?, empty: String) {
        if (v.isNullOrEmpty()) { msg = empty; refresh(); return }
        val t = v.take(ReplacePads.FIELD_MAX)
        if (v.length > ReplacePads.FIELD_MAX) msg = "Only the first " + ReplacePads.FIELD_MAX + " characters were taken"
        if (isFind) { find = t; usedSel = false } else repl = t
        confirmAll = false
        readSource(false)
        refresh()
    }

    private fun toggleScope() {
        readSource(false)
        // v0.29.0: Field > Selection (only with a selection) > From cursor > To cursor > Field
        if (usedSel) { usedSel = false; part = 1; partCut = -1 }
        else if (part == 0) { if (hasSel) usedSel = true else { part = 1; partCut = -1 } }
        else if (part == 1) { part = 2; partCut = -1 }
        else { part = 0; partCut = -1 }
        readSource(false)
        confirmAll = false; step = 0
        refresh()
    }

    private fun scopeLabel(): String = if (usedSel) "In: Selection" else if (part == 1) "In: From cursor" else if (part == 2) "In: To cursor" else "In: Field"
    private fun scopeWord(): String = if (usedSel) "selection " else if (part == 1) "from cursor " else if (part == 2) "to cursor " else "whole field "

    /** v0.29.0: the whole field text after a From / To cursor replace (src was only a piece). */
    private fun composeFull(out: String): String {
        val c = partCut.coerceIn(0, fullText.length)
        return when (part) { 1 -> fullText.substring(0, c) + out; 2 -> out + fullText.substring(c); else -> out }
    }

    private fun onReplace(r: ReplaceEngine.Result, first: Boolean) {
        if (io.blocked()) { msg = "Not available in password fields."; refresh(); return }
        if (pairMode && find.isNotEmpty()) { addPair(true); return }   // v0.40.0: Find has text that is not in the list yet: add it first, the next tap runs the list
        if (setRules == null && find.isEmpty()) { msg = "Type what to find first"; refresh(); return }
        val e = r.err
        if (e != null) { msg = e; refresh(); return }
        if (r.count == 0) { msg = if (r.skipped > 0) "Nothing to change: " + r.skipped + " kept (never change)" else "No match"; refresh(); return }   // v0.41.0
        if (r.changed == 0 || r.out == src) { msg = "No change"; refresh(); return }
        if (setRules != null && !checkAck) {   // v0.40.0: Check pairs BEFORE the first run of a list: warnings = shown once, the next tap runs it anyway
            val w = try { ReplaceCheck.check(setRules ?: ArrayList(), setOpt) } catch (_: Throwable) { ArrayList<ReplaceCheck.Warn>() }
            checkAck = true
            if (ReplaceCheck.serious(w) > 0) { checkWarn = w; confirmAll = false; msg = ReplaceCheck.line(w) + "  \u00B7  tap Replace all again to run anyway"; refresh(); return }
        }
        if (!usedSel && !first && !confirmAll) { confirmAll = true; refresh(); return }
        doReplace(r)
    }

    private fun doReplace(r: ReplaceEngine.Result) {
        val before = src
        val partial = part != 0 && !usedSel
        val fullBefore = fullText
        val fullAfter = if (partial) composeFull(r.out) else r.out
        var ok = false
        var byEdits = false   // v0.42.5: true = written match by match (the format of the field stays)
        if (!usedSel && r.edits.isNotEmpty()) {   // v0.37.1: match by match first (the text between the matches is never rewritten = its format stays; also works in apps that cannot "select all")
            try { ok = io.replaceEdits(0, fullBefore, fullAfter, if (part == 1) com.mgb.keyboard.typing.ReplaceEdits.shift(r.edits, partCut.coerceIn(0, fullText.length)) else r.edits) } catch (_: Throwable) { ok = false }
            byEdits = ok
        }
        if (!ok) ok = try { if (usedSel) io.replaceSelection(r.out) else io.replaceAll(fullAfter) } catch (_: Throwable) { false }
        confirmAll = false
        if (ok && partial && part == 2) partCut = r.out.length   // the piece before the cursor now has the new length
        if (ok) { undoStack.push(if (usedSel) r.out else fullAfter, if (usedSel) before else fullBefore); msg = "Replaced " + r.changed + (if (r.skipped > 0) "  \u00B7  " + r.skipped + " kept (never change)" else "") + (if (!usedSel && !byEdits && io !is ClipReplaceIo) "  \u00B7  plain rewrite: format of the field may be lost (Undo)" else ""); recordPair(); readSource(false) } else msg = "Could not replace in this app. Use Copy, then paste"   // v0.42.5: the user is told when the format could not be kept. // v0.26.0 (R3): recordPair = only when Recent pairs is ON
        refresh()
    }

    private fun undo() {
        val st = undoStack.peek()
        if (st != null && io.undoReplace(st.res, st.orig)) {   // v0.28.0 (R3b): newest step first; a failed step (text changed) clears the stack
            undoStack.drop(); if (part != 0) { part = 0; partCut = -1 }   // v0.29.0: after Undo the scope is the whole field again
            msg = if (undoStack.canUndo()) "Undone (" + undoStack.size() + " more)" else "Undone"
        } else { undoStack.clear(); msg = "Cannot undo (text changed)" }
        readSource(false)
        refresh()
    }

    /** v0.37.1: HTML of the result with the styles of the original text. null = not possible (no styles from the app, From / To cursor scope, text changed) -> plain copy. */
    private fun richHtml(res: ReplaceEngine.Result): String? {
        if (part != 0 || res.edits.isEmpty()) return null
        val rt = io.richText(usedSel) ?: return null
        if (rt.toString() != src) return null
        val sb = SpannableStringBuilder(rt)
        if (sb.getSpans(0, sb.length, Any::class.java).none { it is android.text.style.CharacterStyle || it is android.text.style.ParagraphStyle }) return null   // the app gave plain text only
        for (h in res.edits.sortedByDescending { it.start }) sb.replace(h.start, h.end, h.repl)   // spans outside a match stay
        if (sb.toString() != res.out) return null
        return android.text.Html.toHtml(sb, android.text.Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
    }

    /** v0.37.2: puts the copied result into the field in ONE tap (the user selected all in the app first): with its format when we have it, else plain. */
    private fun pasteCopied() {
        val t = copiedOut
        if (t == null) { refresh(); return }
        val h = copiedHtml
        var done = false
        var withFmt = false
        if (h != null) { done = try { host.pasteFormatted(t, h) } catch (_: Throwable) { false }; withFmt = done }
        if (!done) done = try { host.commitWord(); host.typeText(t); true } catch (_: Throwable) { false }
        if (done) { copiedOut = null; copiedHtml = null }
        msg = if (done) (if (withFmt) "Pasted with format" else "Pasted") else "Could not paste. Use the app's Paste"
        readSource(false)
        refresh()
    }

    private fun copyResult(res: ReplaceEngine.Result) {
        var how = ""
        var ok = false
        var lastHtml: String? = null
        if (res.err == null && !res.blank && res.count > 0) {
            val html = try { richHtml(res) } catch (_: Throwable) { null }
            lastHtml = html   // v0.37.1: keep the bold / italic / links of the rest of the text when the app gives them
            ok = io.setClip(res.out, html)
            how = if (html != null) " (with format)" else " (plain text: this app gives no format)"
        }
        if (ok) { copiedOut = res.out; copiedHtml = lastHtml }
        msg = if (ok) "Copied" + how + ". In the app: select all, then tap Paste" else "Nothing to copy"
        confirmAll = false
        refresh()
    }

    /** v0.25.0 (R2): [<] [>] moved. Tries to select the match in the field (works only where the app allows setSelection); else the preview shows it. */
    private fun showStep(res: ReplaceEngine.Result) {
        confirmAll = false
        try {
            val h = res.hits.getOrNull(step)
            if (h != null && !usedSel && io.selectRange(h.start + (if (part == 1) partCut else 0), h.end + (if (part == 1) partCut else 0))) msg = "Match " + (step + 1) + " selected in the field"
        } catch (_: Throwable) { }
        refresh()
    }

    // ------------------------------------------------------------------ sets view (v0.25.0 R2)

    private fun setsView(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)
        root.setPadding(dp(2), dp(4), dp(2), dp(4))
        val head = row()
        head.addView(btn("\u2190 Back", 1f, 34) { typing = 0; refresh() })
        head.addView(btn((if (setPreviewOn) "\u2713 " else "") + "Preview", 1.2f, 34) { setPreviewOn = !setPreviewOn; setPrev = null; refresh() })   // v0.31.0
        head.addView(btn("Novels", 1.2f, 34) { openNovels() })
        val hint = TextView(ctx)
        hint.text = if (setPreviewOn) "Tap a set to preview it" else "Pick a set. Edit: app > Tools & Panels"
        hint.textSize = 12f
        hint.setTextColor(dim())
        hint.gravity = android.view.Gravity.CENTER_VERTICAL
        hint.layoutParams = LinearLayout.LayoutParams(0, dp(34), 2f)
        head.addView(hint)
        root.addView(head)
        previewBox()?.let { root.addView(it) }
        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(if (setPrev != null) 100 else 170))
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        sv.addView(col)
        val all = ArrayList<Pair<String, ReplaceStore.Def>>()
        try { for ((n, d) in ReplaceStore.load(prefs)) if (NovelBook.visible(n, false, novelCur, NovelBook.ALL)) all.add(Pair("\u2605 " + n, d)) } catch (_: Throwable) { }   // v0.55.0: only the active novel's glossary sets
        try { for ((n, d) in ReplaceStore.ready()) if (NovelBook.visible(n, true, novelCur, NovelBook.ALL)) all.add(Pair(n, d)) } catch (_: Throwable) { }
        var i = 0
        while (i < all.size) {
            val r = row()
            for (j in 0..1) {
                val e = all.getOrNull(i + j)
                if (e == null) { val sp = View(ctx); sp.layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f); r.addView(sp); continue }
                val sb = btn(e.first, 1f, 36) { tapSet(e.first.removePrefix("\u2605 "), e.second) }
                try { sb.setOnLongClickListener { a -> peekSetAt(a, e.first.removePrefix("\u2605 "), e.second); true }; PeekPopup.watchRelease(sb) } catch (_: Throwable) { }   // v0.32.0
                r.addView(sb)
            }
            col.addView(r)
            i += 2
        }
        root.addView(sv)
        return root
    }

    /** v0.31.0: tap on a set: Preview ON = show what it would do on the current text (nothing loaded), else load it as before. */
    private fun tapSet(name: String, d: ReplaceStore.Def) { if (setPreviewOn) { setPrev = Pair(name, d); refresh() } else pickSet(name, d) }

    private fun previewBox(): View? {
        val sp = setPrev ?: return null
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        val tv = TextView(ctx)
        tv.textSize = 12f
        tv.setTextColor(ink())
        tv.setPadding(dp(8), dp(2), dp(8), dp(2))
        tv.text = ReplaceExtras.setPreview(sp.first, sp.second.rules, dOpt(sp.second), src)
        box.addView(tv)
        val r = row()
        r.addView(btn("Use this set", 1.3f, 34) { pickSet(sp.first, sp.second) })
        r.addView(btn("Close", 0.8f, 34) { setPrev = null; refresh() })
        box.addView(r)
        return box
    }

    private fun pickSet(name: String, d: ReplaceStore.Def) {
        val p = ReplaceRules.parse(d.rules)
        if (p.active() == 0) { msg = "That set has no rules"; typing = 0; refresh(); return }
        val hadPairs = pairMode && pairRules.isNotEmpty()
        pairMode = false; pairRules.clear(); checkAck = false; checkWarn = null; clearAsk = false   // v0.40.0: loading a set replaces the pair list
        setRules = p.rules; setOpt = dOpt(d); setName = name; setRun = null; setPrev = null
        typing = 0; confirmAll = false; step = 0
        msg = "Set loaded: " + name + (if (p.bad.isNotEmpty()) " (" + p.bad.size + " unreadable lines skipped)" else "") + (if (hadPairs) "  \u00B7  pair list replaced" else "")
        readSource(false)
        refresh()
    }

    // ------------------------------------------------------------------ v0.26.0 (R3): NEW LOOK (pref panel_ui = 1), Recent pairs view

    private fun newLook(): Boolean = try { PanelLayout.isNewStyle(prefs.int("panel_ui", 0)) } catch (_: Throwable) { false }

    /** New look when panel_ui = New; any problem = the classic main view (pattern HistCard.buildModern / buildClassic). */
    private fun mainViewPick(): View {
        if (diffOn() && !diffFail && !floatSwitch()) {   // v0.53.0: one-screen diff look first (pref rp_diff, default ON); any problem = the views below
            val keepD = msg
            try { return mainViewDiff() } catch (_: Throwable) { diffFail = true; msg = keepD }
        }
        if (miniOn()) {   // v0.38.1 (P2): minimal look first (pref rp_mini, default ON); any problem = the look that was used before
            val keep = msg
            try { return mainViewMini() } catch (_: Throwable) { msg = keep }
        }
        if (newLook()) {
            val keep = msg
            try { return mainViewNew() } catch (_: Throwable) { msg = keep }
        }
        return mainView()
    }

    private fun setsViewPick(): View {
        if (newLook()) { try { return setsViewNew() } catch (_: Throwable) { } }
        return setsView()
    }

    private fun small(label: String, hDp: Int, onClick: (Button) -> Unit): Button {
        val b = btn(label, 0f, hDp, onClick)
        try { b.minWidth = dp(44) } catch (_: Throwable) { }
        return b
    }

    /** Filled button in one colour (Replace all). Same drawing idea as UiKit.chip. */
    private fun accentButton(b: Button, color: Int) {
        try {
            val r = dp(JabraTokens.R_SM).toFloat()
            val up = GradientDrawable(); up.setColor(color); up.cornerRadius = r
            val down = GradientDrawable(); down.setColor(LookColors.darken(color, 0.18f)); down.cornerRadius = r
            val sl = StateListDrawable()
            sl.addState(intArrayOf(android.R.attr.state_pressed), down)
            sl.addState(intArrayOf(), up)
            b.background = sl
            b.backgroundTintList = null
            b.stateListAnimator = null
            b.setTextColor(Color.WHITE)
            b.setTypeface(b.typeface, Typeface.BOLD)
        } catch (_: Throwable) { }
    }

    /** Match counter pill (rounded, soft blue; warning colour for an error). */
    private fun pill(text: String, warn: Boolean): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = JabraTokens.T_BTN
        t.setTypeface(t.typeface, Typeface.BOLD)
        t.gravity = Gravity.CENTER
        t.maxLines = 1
        t.setPadding(dp(10), 0, dp(10), 0)
        val g = GradientDrawable()
        g.cornerRadius = dp(20).toFloat()
        g.setColor(if (warn) JabraTokens.WARN else JabraTokens.ACCENT_SOFT)
        t.background = g
        t.setTextColor(if (warn) Color.WHITE else JabraTokens.ON_ACCENT_SOFT)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28))
        lp.setMargins(dp(4), 0, dp(6), 0)
        t.layoutParams = lp
        return t
    }

    private fun shortVal(v: String): String {
        val t = v.replace("\n", "\u23CE").replace("\t", "\u21E5")
        return if (t.length > 60) ReplaceExtras.head(t, 60) + "\u2026" else t
    }

    /** One rounded card: small label on top, the text under it (tap = type), small buttons on the right. */
    private fun fieldCard(label: String, value: String, onTap: () -> Unit, actions: List<Pair<String, () -> Unit>>): View {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.HORIZONTAL
        c.gravity = Gravity.CENTER_VERTICAL
        c.setPadding(dp(10), dp(2), dp(4), dp(2))
        try { c.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val lb = TextView(ctx)
        lb.text = label
        lb.textSize = JabraTokens.T_NOTE
        lb.setTextColor(try { JabraTokens.cardNote(prefs) } catch (_: Throwable) { Color.GRAY })
        val vv = TextView(ctx)
        vv.textSize = JabraTokens.T_BODY
        vv.maxLines = 1
        vv.ellipsize = android.text.TextUtils.TruncateAt.END
        if (value.isEmpty()) { vv.text = "tap to type"; vv.setTextColor(try { JabraTokens.cardNote(prefs) } catch (_: Throwable) { Color.GRAY }) }
        else { vv.text = shortVal(value); vv.setTextColor(try { JabraTokens.cardText(prefs) } catch (_: Throwable) { Color.BLACK }) }
        col.addView(lb)
        col.addView(vv)
        col.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        c.addView(col)
        for (a in actions) c.addView(small(a.first, 36) { a.second() })
        c.isClickable = true
        c.setOnClickListener { try { host.feedback(c) } catch (_: Throwable) { }; try { onTap() } catch (_: Throwable) { } }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        c.layoutParams = lp
        return c
    }

    private fun addSpan(sb: SpannableStringBuilder, s: String, vararg spans: Any) {
        val a = sb.length
        sb.append(s)
        val b = sb.length
        if (b > a) for (sp in spans) sb.setSpan(sp, a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /** Preview rows: found part bold, replacement in the accent colour. Same rows as previewText(); anything else = that plain text. */
    private fun previewSpan(res: ReplaceEngine.Result): CharSequence {
        val plain = previewText(res)
        val accent = try { if (JabraTokens.isDark(prefs)) JabraTokens.ACCENT_SOFT else JabraTokens.ACCENT } catch (_: Throwable) { JabraTokens.ACCENT }
        val sb = SpannableStringBuilder()
        val rules = setRules
        if (rules != null) {
            val rr = setRun ?: return plain
            var n = 0
            for (i in rules.indices) {
                if (i >= rr.counts.size || rr.counts[i] == 0) continue
                if (n >= PREV_MAX) { sb.append("\n\u2026"); break }
                if (n > 0) sb.append("\n")
                val r = rules[i]
                sb.append(rr.counts[i].toString()).append("\u00D7  ")
                addSpan(sb, r.find.take(24), StyleSpan(Typeface.BOLD))
                if (r.kind == ReplaceRules.K_DEL || r.kind == ReplaceRules.K_DELRE) sb.append("  (line removed)")
                else { sb.append("  \u2192  "); addSpan(sb, r.with.take(24), ForegroundColorSpan(accent), StyleSpan(Typeface.BOLD)) }
                n++
            }
            return if (n == 0) plain else sb
        }
        if (res.err != null || res.blank || res.hits.isEmpty()) return plain
        var n = 0
        val from = if (step in res.hits.indices) step else 0
        for (hi in from until res.hits.size) {
            if (n >= PREV_MAX) break
            if (n > 0) sb.append("\n")
            if (n == 0 && res.hits.size > 1) sb.append("\u25B6 ")
            val p = ReplaceEngine.lineParts(src, res.hits[hi], 14)
            sb.append(p[0])
            addSpan(sb, p[1], StyleSpan(Typeface.BOLD))
            sb.append(" \u2192 ")
            addSpan(sb, p[2], ForegroundColorSpan(accent), StyleSpan(Typeface.BOLD))
            sb.append(p[3])
            n++
        }
        if (res.count > from + n) sb.append("\n\u2026 +").append((res.count - from - n).toString()).append(" more")
        return sb
    }

    private fun mainViewNew(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)
        root.setPadding(dp(2), dp(4), dp(2), dp(4))
        valueTv = null
        keysBox = null

        val res = calc()
        val shown = msg
        msg = ""
        val nRules = setRules?.count { it.kind != ReplaceRules.K_COMMENT } ?: 0

        // top bar: [ABC] [Sets] [Recent] ........ match counter pill
        val top = row()
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(small("ABC", 40) { host.showLetters() })
        top.addView(small("Sets", 40) { typing = 3; confirmAll = false; refresh() })
        top.addView(small("Recent", 40) { typing = 4; confirmAll = false; rprAsk = false; refresh() })
        top.addView(small("?", 40) { typing = 5; helpPage = 0; confirmAll = false; refresh() })   // v0.28.0 (R3b): help
        top.addView(small("Never", 40) { openProtect() })   // v0.41.0: never-change list
        val sp = View(ctx)
        sp.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        top.addView(sp)
        val rr0 = setRun
        val pillText: String
        var warn = false
        if (res.err != null || (setRules != null && rr0 != null && rr0.err != null)) { pillText = "Error"; warn = true }
        else if (setRules != null) pillText = if (res.changed == 0) "No match" else (res.changed.toString() + (if (res.changed == 1) " change" else " changes"))
        else if (res.blank) pillText = "Type Find"
        else pillText = ReplaceEngine.info(res)
        top.addView(pill(pillText, warn))
        root.addView(top)

        if (setRules != null) {
            root.addView(fieldCard("Set", (setName ?: "") + " (" + nRules + ")", { typing = 3; refresh() },
                listOf(Pair("\u2715 Set") { dropSet(); refresh() }, Pair("To Formatter") { copyForFormatter() })))   // v0.28.0 (R3b): Formatter bridge
        } else {
            // Text | Word | Regex
            root.addView(PanelKit.segmented(ctx, prefs, host, ReplaceEngine.MODE_NAMES.toList(), opt.mode) { i -> opt.mode = i; optChanged() })
            // option chips
            val oc = row()
            oc.addView(UiKit.chip(ctx, prefs, "Aa", opt.matchCase, 36, { v -> host.feedback(v) }) { opt.matchCase = !opt.matchCase; optChanged() })
            oc.addView(UiKit.chip(ctx, prefs, "Keep", opt.keepCase, 36, { v -> host.feedback(v) }) { opt.keepCase = !opt.keepCase; if (opt.keepCase) opt.matchCase = false; optChanged() })   // v0.29.0
            if (opt.mode != ReplaceEngine.MODE_REGEX) {
                oc.addView(UiKit.chip(ctx, prefs, "List", opt.list, 36, { v -> host.feedback(v) }) { opt.list = !opt.list; optChanged() })   // v0.31.0
                oc.addView(UiKit.chip(ctx, prefs, "Smart", opt.smart, 36, { v -> host.feedback(v) }) { opt.smart = !opt.smart; optChanged() })   // v0.41.0
                oc.addView(UiKit.chip(ctx, prefs, "\\n", opt.escapes, 36, { v -> host.feedback(v) }) { opt.escapes = !opt.escapes; optChanged() })
            }
            root.addView(oc)
            root.addView(fieldCard("Find", find, { startTyping(1) },
                listOf(Pair("Sel") { take(true, io.selected(), "Nothing is selected") }, Pair("Clip") { take(true, io.clipText(), "Clipboard is empty") })))
            root.addView(fieldCard("With", repl, { startTyping(2) },
                listOf(Pair("\u21C4") { swap() }, Pair("Sel") { take(false, io.selected(), "Nothing is selected") }, Pair("Clip") { take(false, io.clipText(), "Clipboard is empty") })))
        }

        if (shown.isNotEmpty()) root.addView(UiKit.noteText(ctx, prefs, shown))

        // step row: [<] n/N [>] [Replace this]
        val nHits = res.hits.size
        if (setRules == null && nHits > 0 && res.err == null) {
            step = step.coerceIn(0, nHits - 1)
            val sr = row()
            sr.gravity = Gravity.CENTER_VERTICAL
            sr.addView(small("<", 36) { step = if (step <= 0) nHits - 1 else step - 1; showStep(res) })
            sr.addView(small((step + 1).toString() + "/" + res.count, 36) { showStep(res) })
            sr.addView(small("Skip", 36) { step = if (step >= nHits - 1) 0 else step + 1; showStep(res) })   // v0.29.0
            sr.addView(btn("Replace this", 1f, 36) { onReplace(ReplaceEngine.replaceNth(src, find, repl, opt, step + 1), true) })
            sr.addView(btn("Rest", 0.8f, 36) { onReplace(ReplaceEngine.run(src, find, repl, opt, step + 1, Int.MAX_VALUE), true) })   // v0.29.0
            root.addView(sr)
        }

        // preview card
        val sv = ScrollView(ctx)
        val svLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64))
        svLp.setMargins(dp(2), dp(2), dp(2), dp(2))
        sv.layoutParams = svLp
        try { sv.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
        val pv = TextView(ctx)
        pv.textSize = 13f
        pv.setTextColor(try { JabraTokens.cardText(prefs) } catch (_: Throwable) { Color.BLACK })
        pv.setPadding(dp(8), dp(4), dp(8), dp(4))
        var pt: CharSequence = try { previewSpan(res) } catch (_: Throwable) { previewText(res) }
        if (src.isEmpty() && res.err == null) pt = "No text: select text or type some first"
        pv.text = pt
        sv.addView(pv)
        root.addView(sv)
        root.addView(UiKit.noteText(ctx, prefs, scopeWord() + src.length))

        // actions
        val act = row()
        val ask = confirmAll && !usedSel
        val canGo = res.err == null && res.count > 0 && !res.blank
        val allLabel = if (ask) "Sure? Replace all" else if (res.err == null && res.count > 0) "Replace all (" + res.count + ")" else "Replace all"
        val all = btn(allLabel, 2f, 44) { onReplace(res, false) }
        accentButton(all, if (ask) JabraTokens.WARN else JabraTokens.ACCENT)
        peekOn(all) { peekResult(res) }   // v0.32.0
        all.alpha = if (canGo) 1f else 0.6f
        act.addView(all)
        if (setRules == null) act.addView(btn("1st", 0.7f, 44) { onReplace(ReplaceEngine.replaceFirst(src, find, repl, opt), true) })
        act.addView(btn(scopeLabel(), 1.3f, 44) { toggleScope() })
        if (undoStack.canUndo()) act.addView(btn("\u21B6 " + ReplaceExtras.undoLabel(undoStack.size()), 1f, 44) { undo() })
        else if (copiedOut != null && copiedOut == res.out) act.addView(btn("Paste", 1f, 44) { pasteCopied() })
        else act.addView(btn("Copy", 1f, 44) { copyResult(res) })
        root.addView(act)
        return root
    }

    // ------------------------------------------------------------------ v0.38.1 (P2): MINIMAL LOOK of the main view (pref rp_mini, default ON; switch: "?" help page > Look)
    // Same buttons, same handlers, same order of work as mainViewNew / mainView. Only the arrangement changes:
    // one row for the mode + an "Options" fold, ONE card for Find + With (label, text, Sel / Clip on one line), one note line (message or scope), one primary button.

    // ------------------------------------------------------------------ v0.39.1 (FLOAT F2): the float preview card INSIDE the main view (hidden test switch rp_float_view, default OFF; "?" help page > Look)
    // View only (ReplaceFloatView.kt + typing/ReplaceFloat.java). [<] [>] [Replace this] use the SAME code paths as the step row (showStep, onReplace).
    // Any problem = null = the old preview card + step row are drawn.

    private fun floatOn(): Boolean = try { prefs.bool("rp_float_view", false) } catch (_: Throwable) { false }

    private fun floatLines(res: ReplaceEngine.Result): List<String>? {
        val setMode = setRules != null
        if (pairMode && pairRules.isNotEmpty()) {   // v0.40.0: one line per pair ("3x  cat -> dog"), also the ones that found nothing
            val cs = setRun?.counts
            return pairRules.mapIndexed { i, r -> ReplacePairList.label(r, if (cs != null && i < cs.size) cs[i] else 0, 34) }
        }
        return if (setMode && res.changed > 0) previewText(res).split("\n") else if (setMode) ArrayList<String>() else null
    }

    private fun floatActions(res: ReplaceEngine.Result, shown: String): ReplaceFloatView.Actions {
        val nHits = res.hits.size
        val replaced = shown.startsWith("Replaced")
        val copied = shown.startsWith("Copied")   // v0.42.5: the window shows a short "Copied" too (the long hint stays in the note line)
        return ReplaceFloatView.Actions(
            onTab = { t -> prefs.setInt("rp_float_tab", ReplaceFloat.sanitizeTab(t)); refresh() },
            onPrev = { step = ReplaceFloat.step(step, nHits, -1); showStep(res) },
            onNext = { step = ReplaceFloat.step(step, nHits, 1); showStep(res) },
            onReplace = { onReplace(ReplaceEngine.replaceNth(src, find, repl, opt, step + 1), true) },
            onUndo = if (replaced && undoStack.canUndo()) ({ undo() }) else null,
            notice = if (replaced || copied) shown.substringBefore("  \u00B7  ").substringBefore(". ") else "",
            onPick = { i -> step = i.coerceIn(0, (nHits - 1).coerceAtLeast(0)); showStep(res) },   // v0.39.6: tap a row = that match
            onReplaceAll = { onReplace(res, false) },   // v0.42.5: same as the main Replace all (asks "Sure?" first)
            allLabel = if (confirmAll) "Sure? Replace all" else if (res.err == null && res.count > 0) "Replace all (" + res.count + ")" else "Replace all",
            onCopy = { copyResult(res) },
            onPaste = if (copiedOut != null && copiedOut == res.out) ({ pasteCopied() }) else null   // v0.45.0: [Paste] after Copy (same as the main Paste)
        )
    }

    private fun floatCard(res: ReplaceEngine.Result, shown: String): View? {
        val nHits = res.hits.size
        if (nHits > 0) step = step.coerceIn(0, nHits - 1)
        val snap = ReplaceFloat.snap(src, res, step, setRules != null, floatLines(res))
        val tab = ReplaceFloat.sanitizeTab(prefs.int("rp_float_tab", 0))
        return ReplaceFloatView(ctx, prefs) { v -> host.feedback(v) }.window(snap, tab, floatActions(res, shown))
    }

    // ------------------------------------------------------------------ v0.39.3 (FLOAT F4): the FLOATING window (ReplaceFloatWin.kt). Pref rp_float (default OFF, switch on the "?" page).
    // ON + state not closed = the window shows the preview and the inline preview card is hidden. The window is updated in place on every refresh (floatSync, posted after the view is attached).
    // Any problem = fwinFail = the old inline preview comes back. Replace all stays in the main view. [<] [>] [Replace this] / Undo use the same code paths as the step row.

    private var fwin: ReplaceFloatWin? = null
    private var fwinFail = false

    private fun floatSwitch(): Boolean = try { prefs.bool("rp_float", false) } catch (_: Throwable) { false }

    private fun winShows(): Boolean = try { floatSwitch() && !fwinFail && ReplaceFloat.sanitizeState(prefs.int("rp_float_state", ReplaceFloat.OPEN)) != ReplaceFloat.CLOSED } catch (_: Throwable) { false }

    private fun toggleFloat() {
        val closed = ReplaceFloat.sanitizeState(prefs.int("rp_float_state", ReplaceFloat.OPEN)) == ReplaceFloat.CLOSED || fwinFail
        fwinFail = false
        prefs.setInt("rp_float_state", if (closed) ReplaceFloat.OPEN else ReplaceFloat.CLOSED)
        refresh()
    }

    private fun floatSync(anchor: View, snap: ReplaceFloat.Snap, tab: Int, a: ReplaceFloatView.Actions) {
        try {
            val w = fwin ?: ReplaceFloatWin(ctx, prefs) { v -> host.feedback(v) }.also { it.onChange = { refresh() }; it.onFail = { r -> fwinFail = true; try { fwin?.hide() } catch (_: Throwable) { }; msg = "Float window failed: " + r.take(70); refresh() }; fwin = it }
            if (!winShows()) { w.hide(); return }
            if (!w.show(anchor, snap, tab, a)) { fwinFail = true; w.hide(); msg = "Float window failed: " + w.lastError.take(70); refresh() }   // v0.39.4: the reason is shown in the note line
        } catch (e: Throwable) {
            fwinFail = true
            msg = "Float window failed: " + (e.javaClass.simpleName)
            try { fwin?.hide() } catch (_: Throwable) { }
            refresh()
        }
    }

    // ------------------------------------------------------------------ v0.53.0 (DIFF): ONE-SCREEN Find & Replace with a diff preview (look of docs/replace-redesign-mock.html).
    // NEW code only: the old views (Slim / Mini / New / classic) are untouched and stay as the fallback (pref rp_diff = false, a floating window, or any exception = the old view).
    // What is new: tap a match in the preview = skip it, ONE Replace button ("Replace 3 of 4"), Changes | Result preview, locked (Never change) matches shown in the text,
    // smart suggestion chips ("N inside longer words > Whole word", "Try Smart"), One | Pairs switch, scope menu, Recent chips when Find is empty.
    private var diffFail = false
    private var dPairs = false                 // Pairs tab is shown (the engine state is still pairMode / setRules)
    private val dSkip = HashSet<Int>()         // start offsets (in src) of matches the user tapped off
    private var dKey = ""                      // when the inputs change, dSkip is cleared
    private var dView = 0                      // 0 = Changes, 1 = Result
    private var dMenu = 0                      // 0 none, 1 scope, 2 tools, 3 more
    private var dScroll = 0                    // preview scroll, kept over the rebuild of a tap
    private var dRestore = false
    private var dSv: ScrollView? = null

    private fun diffOn(): Boolean = try { prefs.bool("rp_diff", true) } catch (_: Throwable) { true }

    /** One match of the preview: prot = touches a Never change term (locked), skip = the user tapped it off. */
    private class DM(val h: ReplaceEngine.Hit, val prot: Boolean, val skip: Boolean) { val live: Boolean get() = !prot && !skip }

    private fun diffMatches(res: ReplaceEngine.Result): List<DM> {
        if (res.err != null) return emptyList()
        val live = HashSet<Int>()
        for (h in res.edits) live.add(h.start)
        // single Find / With with a Never change list: run once more WITHOUT the list to know where the locked matches are
        val all: List<ReplaceEngine.Hit> = if (setRules == null && res.skipped > 0) {
            val o = opt.copy(); o.protect = null
            try { ReplaceEngine.replaceAll(src, find, repl, o).edits } catch (_: Throwable) { res.edits }
        } else res.edits
        return all.map { DM(it, !live.contains(it.start), dSkip.contains(it.start)) }
    }

    private fun diffSelect(ms: List<DM>, kind: String, curMs: Int): List<ReplaceEngine.Hit> {
        val live = ms.filter { it.live }
        val l = when (kind) {
            "first" -> live.take(1)
            "this" -> live.filter { ms.indexOf(it) == curMs }
            "rest" -> live.filter { ms.indexOf(it) >= curMs }
            else -> live
        }
        return l.map { it.h }
    }

    /** A Result for a part of the matches (skipped / locked ones stay). Same fields the old doReplace reads. */
    private fun diffResult(res: ReplaceEngine.Result, sel: List<ReplaceEngine.Hit>): ReplaceEngine.Result {
        val r = ReplaceEngine.Result()
        val sb = StringBuilder()
        var pos = 0
        for (h in sel) { sb.append(src, pos, h.start); sb.append(h.repl); pos = h.end }
        sb.append(src, pos, src.length)
        r.out = sb.toString(); r.count = res.count; r.changed = sel.size; r.skipped = res.skipped
        r.edits.addAll(sel)
        return r
    }

    private fun diffRun(res: ReplaceEngine.Result, ms: List<DM>, curMs: Int, kind: String) {
        if (pairMode && find.isNotEmpty()) { onReplace(res, false); return }   // the old rule: Find has text that is not in the list yet = add it first
        val sel = diffSelect(ms, kind, curMs)
        if (sel.isEmpty()) {
            if (dSkip.isEmpty() && kind == "all") onReplace(res, false) else { msg = "Nothing to replace"; refresh() }   // onReplace gives the usual message (No match / error ...)
            return
        }
        onReplace(if (sel.size == res.edits.size) res else diffResult(res, sel), kind != "all")
    }

    private fun diffInside(h: ReplaceEngine.Hit): Boolean {
        val f = h.found
        if (f.isEmpty()) return false
        val b = if (h.start > 0) src[h.start - 1] else ' '
        val a = if (h.end < src.length) src[h.end] else ' '
        return (b.isLetterOrDigit() && f.first().isLetterOrDigit()) || (a.isLetterOrDigit() && f.last().isLetterOrDigit())
    }

    private fun dChip(label: String, on: Boolean, onClick: () -> Unit): Button =
        UiKit.chip(ctx, prefs, label, on, 32, { v -> host.feedback(v) }) { onClick() }

    private fun dScrollRow(r: LinearLayout): View {
        val h = android.widget.HorizontalScrollView(ctx)
        h.isHorizontalScrollBarEnabled = false
        h.addView(r)
        h.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        return h
    }

    private fun dText(t: String, warn: Boolean): TextView {
        val v = TextView(ctx)
        v.text = t
        v.textSize = JabraTokens.T_BTN
        v.setTypeface(v.typeface, Typeface.BOLD)
        v.maxLines = 1
        v.gravity = Gravity.CENTER_VERTICAL
        v.setPadding(dp(6), 0, dp(6), 0)
        v.setTextColor(if (warn) JabraTokens.WARN else (try { JabraTokens.cardText(prefs) } catch (_: Throwable) { Color.BLACK }))
        v.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(30))
        return v
    }

    private fun diffToOne() {
        if (pairMode) {   // the pairs stay in pairRules, they only stop running
            setRules = null; setName = null; setRun = null; pairMode = false
            checkAck = false; checkWarn = null; clearAsk = false; editIdx = -1
        }
        dPairs = false; confirmAll = false; step = 0; dMenu = 0
        refresh()
    }

    private fun diffToPairs() {
        if (setRules != null && !pairMode) { msg = "A set is loaded. Tap \u2715 Set first, then use Pairs"; refresh(); return }
        dPairs = true; dMenu = 0; confirmAll = false; step = 0
        if (!pairMode && pairRules.isNotEmpty()) {
            val o = opt.copy(); o.mode = ReplaceEngine.MODE_TEXT; o.list = false; o.keepCase = false; o.smart = false
            setOpt = o; pairMode = true; syncPairs()
        }
        refresh()
    }

    private fun diffScope(sel: Boolean, p: Int) {
        if (sel && !hasSel) { msg = "Nothing is selected"; dMenu = 0; refresh(); return }
        usedSel = sel; part = p; partCut = -1
        confirmAll = false; step = 0; dMenu = 0
        readSource(false)
        refresh()
    }

    private fun diffNav(d: Int, nav: List<Int>, ms: List<DM>) {
        val n = nav.size
        if (n == 0) return
        step = ((step + d) % n + n) % n
        dRestore = false
        try {
            val h = ms[nav[step]].h
            val off = if (part == 1) partCut else 0
            if (!usedSel) io.selectRange(h.start + off, h.end + off)
        } catch (_: Throwable) { }
        refresh()
    }

    private fun diffTap(i: Int, nav: List<Int>, ms: List<DM>) {
        val m = ms.getOrNull(i) ?: return
        dScroll = try { dSv?.scrollY ?: 0 } catch (_: Throwable) { 0 }
        dRestore = true
        confirmAll = false
        if (m.prot) msg = "Protected by Never change"
        else { if (dSkip.contains(m.h.start)) dSkip.remove(m.h.start) else dSkip.add(m.h.start); step = nav.indexOf(i).coerceAtLeast(0) }
        refresh()
    }

    private fun diffClick(sb: SpannableStringBuilder, a: Int, b: Int, act: () -> Unit) {
        if (b <= a) return
        sb.setSpan(object : android.text.style.ClickableSpan() {
            override fun onClick(w: View) { try { act() } catch (_: Throwable) { } }
            override fun updateDrawState(ds: android.text.TextPaint) { }
        }, a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /** The preview text. Changes: found text struck out (red) + the new text (green). Result: only the new text. Skipped = dimmed + underlined, locked = dimmed + lock. curOff[0] = where the current match starts. */
    private fun diffSpan(ms: List<DM>, curMs: Int, result: Boolean, nav: List<Int>, curOff: IntArray): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        val dk = try { JabraTokens.isDark(prefs) } catch (_: Throwable) { false }
        val delBg = if (dk) 0xFF4A2626.toInt() else 0xFFFBDCDA.toInt()
        val delInk = if (dk) 0xFFFFB4AE.toInt() else 0xFF8F1F18.toInt()
        val insBg = if (dk) 0xFF233F2B.toInt() else 0xFFD5F0DA.toInt()
        val insInk = if (dk) 0xFFB5E8BD.toInt() else 0xFF1D5A2B.toInt()
        val dimC = try { JabraTokens.cardNote(prefs) } catch (_: Throwable) { Color.GRAY }
        val ranges = ArrayList<IntArray>()
        val shownIdx: List<Int>
        if (src.length <= 3000) { ranges.add(intArrayOf(0, src.length)); shownIdx = ms.indices.toList() }
        else {   // long text: only a little text around the first matches
            shownIdx = ms.indices.take(80)
            for (i in shownIdx) {
                val a = Math.max(0, ms[i].h.start - 40); val b = Math.min(src.length, ms[i].h.end + 40)
                val last = ranges.lastOrNull()
                if (last != null && a <= last[1]) last[1] = Math.max(last[1], b) else ranges.add(intArrayOf(a, b))
            }
            if (ranges.isEmpty()) ranges.add(intArrayOf(0, Math.min(src.length, 400)))
        }
        var mi = 0
        for ((ri, rg) in ranges.withIndex()) {
            if (ri > 0) sb.append("\n\u2026\n")
            var pos = rg[0]
            while (mi < shownIdx.size && ms[shownIdx[mi]].h.start < rg[1]) {
                val i = shownIdx[mi]; mi++
                val m = ms[i]
                if (m.h.start < pos) continue
                sb.append(src, pos, m.h.start); pos = m.h.end
                val a0 = sb.length
                if (i == curMs) curOff[0] = a0
                if (m.prot) {
                    sb.append(m.h.found).append(" \uD83D\uDD12")
                    sb.setSpan(android.text.style.UnderlineSpan(), a0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(ForegroundColorSpan(dimC), a0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else if (m.skip) {
                    sb.append(m.h.found)
                    sb.setSpan(android.text.style.UnderlineSpan(), a0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(ForegroundColorSpan(dimC), a0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    if (!result) {
                        val d0 = sb.length
                        sb.append(m.h.found)
                        sb.setSpan(android.text.style.BackgroundColorSpan(delBg), d0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        sb.setSpan(ForegroundColorSpan(delInk), d0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        sb.setSpan(android.text.style.StrikethroughSpan(), d0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    if (m.h.repl.isNotEmpty()) {
                        val n0 = sb.length
                        sb.append(m.h.repl)
                        sb.setSpan(android.text.style.BackgroundColorSpan(insBg), n0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        sb.setSpan(ForegroundColorSpan(insInk), n0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    if (i == curMs && sb.length > a0) sb.setSpan(StyleSpan(Typeface.BOLD), a0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                diffClick(sb, a0, sb.length) { diffTap(i, nav, ms) }
            }
            sb.append(src, pos, rg[1])
        }
        if (mi < shownIdx.size || shownIdx.size < ms.size) sb.append("\n\u2026 +").append((ms.size - mi).toString()).append(" more")
        return sb
    }

    private fun diffMenuInto(root: LinearLayout, res: ReplaceEngine.Result, ms: List<DM>, nav: List<Int>, curMs: Int) {
        val items = ArrayList<Triple<String, String, () -> Unit>>()
        val title: String
        when (dMenu) {
            1 -> {
                title = "Replace where?"
                items.add(Triple("Selection", if (hasSel) "the selected text" else "none selected") { diffScope(true, 0) })
                items.add(Triple("Whole field", "asks to confirm") { diffScope(false, 0) })
                items.add(Triple("From cursor", "cursor to the end") { diffScope(false, 1) })
                items.add(Triple("To cursor", "start to the cursor") { diffScope(false, 2) })
            }
            2 -> {
                title = "Tools"
                items.add(Triple("Novels", if (novelCur.isEmpty()) "none active" else novelCur) { dMenu = 0; openNovels() })   // v0.55.0 (NV1)
                items.add(Triple("Fix names", "one screen") { dMenu = 0; openFixNames() })   // v0.57.0 (FX1)
                items.add(Triple("Sets", "") { dMenu = 0; typing = 3; confirmAll = false; refresh() })
                items.add(Triple("Recent", "") { dMenu = 0; typing = 4; confirmAll = false; rprAsk = false; refresh() })
                items.add(Triple("Never change", protectList.size.toString() + (if (novelCur.isEmpty()) " terms" else " terms \u00B7 " + novelCur)) { dMenu = 0; openProtect() })
                items.add(Triple("Help", "") { dMenu = 0; typing = 5; helpPage = 0; confirmAll = false; refresh() })
            }
            else -> {
                title = "More"
                items.add(Triple("Only the first", "") { dMenu = 0; diffRun(res, ms, curMs, "first") })
                items.add(Triple("Only this one", if (nav.isNotEmpty()) (step + 1).toString() + " of " + nav.size else "") { dMenu = 0; diffRun(res, ms, curMs, "this") })
                items.add(Triple("This one and all after", "") { dMenu = 0; diffRun(res, ms, curMs, "rest") })
                val sel = diffSelect(ms, "all", curMs)
                val rr = if (sel.size == res.edits.size) res else diffResult(res, sel)
                if (copiedOut != null && copiedOut == rr.out) items.add(Triple("Paste copied result", "") { dMenu = 0; pasteCopied() })
                else items.add(Triple("Copy result", "") { dMenu = 0; copyResult(rr) })
            }
        }
        val hd = row()
        hd.gravity = Gravity.CENTER_VERTICAL
        hd.setPadding(dp(8), dp(2), dp(4), 0)
        val tt = dText(title, false)
        tt.layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f)
        hd.addView(tt)
        hd.addView(dChip("Close", true) { dMenu = 0; refresh() })
        root.addView(hd)
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        for (it in items) {
            val b = btn(it.first + (if (it.second.isNotEmpty()) "   \u00B7  " + it.second else ""), 0f, 38) { _ -> it.third() }
            b.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38)).also { lp -> lp.setMargins(dp(6), dp(2), dp(6), dp(2)) }
            b.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            b.setPadding(dp(12), 0, dp(8), 0)
            col.addView(b)
        }
        val sv = ScrollView(ctx)
        try { sv.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150)).also { it.setMargins(dp(2), dp(2), dp(2), dp(2)) }
        sv.addView(col)
        root.addView(sv)   // the LAST ScrollView directly in the panel: MgbIme.stretchToLetters gives it the spare height
    }

    private fun mainViewDiff(): View {
        try { ReplaceSlim.closeIfIdle() } catch (_: Throwable) { }   // the slim Options window of the old view must not stay open
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)
        root.setPadding(dp(4), dp(4), dp(4), dp(4))
        valueTv = null
        keysBox = null

        val res = calc()
        val shown = msg
        msg = ""
        val key = find + "\u0001" + repl + "\u0001" + ReplaceEngine.pack(opt) + "\u0001" + part + "|" + usedSel + "|" + src.hashCode() + "|" + (setName ?: "") + "|" + pairRules.hashCode() + "|" + protectList.size
        if (key != dKey) { dKey = key; dSkip.clear() }
        val isSet = setRules != null && !pairMode
        if (pairMode) dPairs = true
        if (isSet) dPairs = false

        val ms = diffMatches(res)
        val nav = ms.indices.filter { !ms[it].prot }
        step = if (nav.isEmpty()) 0 else step.coerceIn(0, nav.size - 1)
        val curMs = nav.getOrNull(step) ?: -1
        val eff = ms.count { it.live }
        val nProt = ms.count { it.prot }
        val nSkip = ms.count { it.skip }
        val nRules = setRules?.count { it.kind != ReplaceRules.K_COMMENT } ?: 0

        // 1) top bar: [ABC] [One] [Pairs n] [scope v] [...]
        val top = row()
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(small("ABC", 36) { host.showLetters() })
        top.addView(dChip("One", !dPairs && !isSet) { diffToOne() })
        top.addView(dChip("Pairs " + pairRules.size, dPairs) { diffToPairs() })
        val sc = btn(scopeLabel().removePrefix("In: ") + " \u25BE", 1f, 36) { dMenu = if (dMenu == 1) 0 else 1; refresh() }
        if (!usedSel && part == 0) { try { sc.setTextColor(JabraTokens.WARN) } catch (_: Throwable) { } }   // the whole field is the wide scope: shown in the warning colour
        top.addView(sc)
        top.addView(small("\u22EF", 36) { dMenu = if (dMenu == 2) 0 else 2; refresh() })
        root.addView(top)

        // 2) Find / With (or the loaded Set) in ONE card, swap on the right
        val io2 = row()
        io2.gravity = Gravity.CENTER_VERTICAL
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(10), dp(2), dp(4), dp(2))
        try { card.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
        if (isSet) {
            card.addView(miniLine("Set", (setName ?: "") + " (" + nRules + ")", { typing = 3; refresh() },
                listOf(Pair("\u2715 Set") { dropSet(); dPairs = false; refresh() }, Pair("Check") { checkNow() }, Pair("Never") { openProtect() }, Pair("Fmt") { copyForFormatter() })))
        } else {
            card.addView(miniLine("Find", find, { startTyping(1) },
                listOf(Pair("Sel") { take(true, io.selected(), "Nothing is selected") }, Pair("Clip") { take(true, io.clipText(), "Clipboard is empty") })))
            card.addView(miniDivider())
            card.addView(miniLine("With", repl, { startTyping(2) },
                listOf(Pair("Sel") { take(false, io.selected(), "Nothing is selected") }, Pair("Clip") { take(false, io.clipText(), "Clipboard is empty") })))
        }
        card.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.setMargins(dp(2), dp(4), dp(2), dp(2)) }
        io2.addView(card)
        if (!isSet) io2.addView(small("\u21C4", 72) { swap() })
        root.addView(io2)

        // 3) options: Text | Word | Regex | Aa | Keep | List | Smart | \n | Never change   (not in Pairs: each pair keeps its own mode)
        if (!isSet && !dPairs) {
            val orow = row()
            orow.gravity = Gravity.CENTER_VERTICAL
            val names = arrayOf("Text", "Word", "Regex")
            for (i in 0..2) orow.addView(dChip(names[i], opt.mode == i) { opt.mode = i; optChanged() })
            orow.addView(dChip("Aa", opt.matchCase) { opt.matchCase = !opt.matchCase; if (opt.matchCase) opt.keepCase = false; optChanged() })
            orow.addView(dChip("Keep case", opt.keepCase) { opt.keepCase = !opt.keepCase; if (opt.keepCase) opt.matchCase = false; optChanged() })
            if (opt.mode != ReplaceEngine.MODE_REGEX) {
                orow.addView(dChip("List a | b", opt.list) { opt.list = !opt.list; optChanged() })
                orow.addView(dChip("Smart", opt.smart) { opt.smart = !opt.smart; optChanged() })
                orow.addView(dChip("\\n", opt.escapes) { opt.escapes = !opt.escapes; optChanged() })
            }
            orow.addView(dChip(if (protectList.isEmpty()) "Never change" else "Never change (" + protectList.size + ")", protectList.isNotEmpty()) { openProtect() })
            root.addView(dScrollRow(orow))
        }

        // 4) stage: a menu card, or [pair list + pair buttons] + the preview card
        if (dMenu != 0) {
            diffMenuInto(root, res, ms, nav, curMs)
        } else {
            if (dPairs) {
                if (pairRules.isEmpty()) root.addView(UiKit.noteText(ctx, prefs, "No pairs yet. Type Find / With and tap + Pair, or tap + Paste (one find => with per line)."))
                else root.addView(pairListView())
                val pr = row()
                pr.gravity = Gravity.CENTER_VERTICAL
                if (find.isNotEmpty() || editIdx >= 0) pr.addView(dChip(if (editIdx >= 0) "Update" else "+ Pair", true) { addPair(false) })
                pr.addView(dChip("+ Paste", false) { pastePairs() })
                if (pairMode) {
                    pr.addView(dChip("Check", false) { checkNow() })
                    pr.addView(dChip("List " + pairRules.size, false) { typing = 8; clearAsk = false; refresh() })
                    pr.addView(dChip("Save as Set", false) { savePairs() })
                    pr.addView(dChip("\u2192 Formatter", false) { copyForFormatter() })
                    pr.addView(dChip(if (clearAsk) "Sure? Clear" else "Clear", false) { clearPairs() })
                }
                root.addView(dScrollRow(pr))
            }
            // header: [<] k of N [>] ..... Changes | Result
            val hd = row()
            hd.gravity = Gravity.CENTER_VERTICAL
            hd.setPadding(dp(4), dp(2), dp(4), 0)
            hd.addView(small("\u2039", 30) { diffNav(-1, nav, ms) })
            hd.addView(dText(if (nav.isEmpty()) "0 of 0" else (step + 1).toString() + " of " + nav.size, false))
            hd.addView(small("\u203A", 30) { diffNav(1, nav, ms) })
            val sp = View(ctx)
            sp.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            hd.addView(sp)
            hd.addView(dChip("Changes", dView == 0) { dView = 0; dRestore = true; dScroll = try { dSv?.scrollY ?: 0 } catch (_: Throwable) { 0 }; refresh() })
            hd.addView(dChip("Result", dView == 1) { dView = 1; dRestore = true; dScroll = try { dSv?.scrollY ?: 0 } catch (_: Throwable) { 0 }; refresh() })
            root.addView(hd)
            // body: the diff text (tap a match = skip it)
            val sv = ScrollView(ctx)
            try { sv.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
            sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(if (dPairs) 40 else 46)).also { it.setMargins(dp(2), dp(2), dp(2), dp(2)) }   // small start height, MgbIme.stretchToLetters adds the spare height
            val pv = TextView(ctx)
            pv.textSize = 13f
            pv.setTextColor(try { JabraTokens.cardText(prefs) } catch (_: Throwable) { Color.BLACK })
            pv.setPadding(dp(10), dp(2), dp(10), dp(4))
            pv.highlightColor = Color.TRANSPARENT
            val curOff = intArrayOf(-1)
            if (io.blocked()) pv.text = "Not available in password fields."
            else if (src.isEmpty()) pv.text = "No text: select text or type some first"
            else if (ms.isEmpty()) pv.text = if (src.length > 400) src.substring(0, 400) + "\u2026" else src
            else { pv.text = diffSpan(ms, curMs, dView == 1, nav, curOff); pv.movementMethod = android.text.method.LinkMovementMethod.getInstance() }
            sv.addView(pv)
            dSv = sv
            val keepY = dScroll; val restore = dRestore
            dRestore = false
            pv.post {
                try {
                    val lay = pv.layout
                    if (restore) sv.scrollTo(0, keepY)
                    else if (lay != null && curOff[0] >= 0) sv.scrollTo(0, Math.max(0, lay.getLineTop(lay.getLineForOffset(Math.min(curOff[0], pv.text.length))) - dp(18)))
                } catch (_: Throwable) { }
            }
            root.addView(sv)
            // footer: count + suggestion chips
            val ft = row()
            ft.gravity = Gravity.CENTER_VERTICAL
            ft.setPadding(dp(4), 0, dp(4), dp(2))
            val rr0 = setRun
            if (res.err != null) ft.addView(dText(res.err ?: "Error", true))
            else if (setRules != null && rr0 != null && rr0.err != null) ft.addView(dText("Error in a pair", true))
            else if (ms.isEmpty()) ft.addView(dText(if (setRules == null && find.isEmpty()) "Type what to find" else "No matches", false))
            else ft.addView(dText(eff.toString() + (if (eff == 1) " match" else " matches") + (if (nProt > 0) "  \u00B7  " + nProt + " \uD83D\uDD12" else "") + (if (nSkip > 0) "  \u00B7  " + nSkip + " skipped" else ""), false))
            val inside = if (opt.mode == ReplaceEngine.MODE_TEXT && setRules == null) ms.count { it.live && diffInside(it.h) } else 0
            if (res.err == null && inside > 0) ft.addView(dChip("$inside inside longer words \u2192 Whole word", false) { opt.mode = ReplaceEngine.MODE_WORD; optChanged() })
            if (res.err == null && ms.isEmpty() && find.isNotEmpty() && setRules == null && !opt.smart && opt.mode != ReplaceEngine.MODE_REGEX) ft.addView(dChip("Try Smart", false) { opt.smart = true; optChanged() })
            val nShort = ReplaceCheck.shortCount(checkWarn)
            if (setRules != null && nShort > 0) ft.addView(dChip("Whole word (" + nShort + ")", false) { fixShort() })
            if (setRules == null && find.isEmpty() && !dPairs) {   // Recent pairs (only when that option is ON): one tap fills Find / With
                try {
                    if (prefs.bool(RecentPairs.PREF_ON, false)) for (p in RecentPairs.parse(prefs.str(RecentPairs.PREF_LIST, "")).take(3)) ft.addView(dChip(p.find.take(12) + " \u2192 " + p.with.take(12), false) { usePair(p) })
                } catch (_: Throwable) { }
            }
            root.addView(dScrollRow(ft))
        }
        if (shown.isNotEmpty()) root.addView(UiKit.noteText(ctx, prefs, shown))

        // 5) ONE Replace button (+ undo, + more)
        val act = row()
        val ask = confirmAll && !usedSel
        val tot = ms.count { !it.prot }
        val label = if (ask) "Sure? Replace all" else if (eff == 0) "Replace" else if (eff < tot) "Replace " + eff + " of " + tot else "Replace " + eff
        val go = btn(label, 2.4f, 44) { diffRun(res, ms, curMs, "all") }
        accentButton(go, if (ask) JabraTokens.WARN else JabraTokens.ACCENT)
        peekOn(go) { peekResult(res) }
        go.alpha = if (res.err == null && eff > 0) 1f else 0.6f
        act.addView(go)
        if (undoStack.canUndo()) act.addView(btn("\u21B6 " + ReplaceExtras.undoLabel(undoStack.size()), 1f, 44) { undo() })
        act.addView(btn("\u22EF", 0.7f, 44) { dMenu = if (dMenu == 3) 0 else 3; refresh() })
        root.addView(act)
        return root
    }

    private fun miniOn(): Boolean = try { prefs.bool("rp_mini", true) } catch (_: Throwable) { true }

    /** One line of the Find / With card: bold small label, the text (tap = type), small buttons on the right. */
    private fun miniLine(label: String, value: String, onTap: () -> Unit, actions: List<Pair<String, () -> Unit>>): View {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        val noteInk = try { JabraTokens.cardNote(prefs) } catch (_: Throwable) { Color.GRAY }
        val lb = TextView(ctx)
        lb.text = label
        lb.textSize = JabraTokens.T_NOTE
        lb.setTypeface(lb.typeface, Typeface.BOLD)
        lb.setTextColor(noteInk)
        lb.layoutParams = LinearLayout.LayoutParams(dp(38), LinearLayout.LayoutParams.WRAP_CONTENT)
        r.addView(lb)
        val vv = TextView(ctx)
        vv.textSize = JabraTokens.T_BODY
        vv.maxLines = 1
        vv.ellipsize = android.text.TextUtils.TruncateAt.END
        vv.gravity = Gravity.CENTER_VERTICAL
        if (value.isEmpty()) { vv.text = "tap to type"; vv.setTextColor(noteInk) }
        else { vv.text = shortVal(value); vv.setTextColor(try { JabraTokens.cardText(prefs) } catch (_: Throwable) { Color.BLACK }) }
        vv.layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f)
        vv.isClickable = true
        vv.setOnClickListener { try { host.feedback(vv) } catch (_: Throwable) { }; try { onTap() } catch (_: Throwable) { } }
        r.addView(vv)
        for (a in actions) r.addView(small(a.first, 32) { a.second() })
        return r
    }

    private fun miniDivider(): View {
        val d = View(ctx)
        d.setBackgroundColor(try { JabraTokens.cardStroke(prefs, false, false) } catch (_: Throwable) { Color.LTGRAY })
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        lp.setMargins(dp(4), dp(1), dp(4), dp(1))
        d.layoutParams = lp
        return d
    }

    private fun mainViewMini(): View {
        // v0.42.2 (M2): slim look (pref rp_slim, default OFF). Any exception = slimFail = the exact old view below.
        if (ReplaceSlim.on(prefs) && !slimFail) {
            val keepMsg = msg
            try { return mainViewSlim() } catch (_: Throwable) { slimFail = true; msg = keepMsg }
        }
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)
        root.setPadding(dp(4), dp(4), dp(4), dp(4))
        valueTv = null
        keysBox = null

        val res = calc()
        val shown = msg
        msg = ""
        val nRules = setRules?.count { it.kind != ReplaceRules.K_COMMENT } ?: 0

        val wOn = winShows()   // v0.39.3: the floating window owns the preview (v0.39.5: it now always wins over the inline test card)
        val fcard: View? = if (floatOn() && !floatSwitch()) (try { floatCard(res, shown) } catch (_: Throwable) { null }) else null   // v0.39.5: inline test card only while the floating switch is OFF   // v0.39.1 (FLOAT F2): hidden test switch; null = the old preview + step row below

        // 1) top bar: [ABC] [Sets] [Recent] [?] ........ pill
        val top = row()
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(small("ABC", 38) { host.showLetters() })
        top.addView(small("Sets", 38) { typing = 3; confirmAll = false; refresh() })
        top.addView(small("Recent", 38) { typing = 4; confirmAll = false; rprAsk = false; refresh() })
        top.addView(small("?", 38) { typing = 5; helpPage = 0; confirmAll = false; refresh() })
        if (pairMode || (setRules == null && find.isNotEmpty())) { if (find.isEmpty()) editIdx = -1; top.addView(small(if (editIdx >= 0) "Update" else "+Pair", 38) { addPair(false) }) }   // v0.40.0
        else if (setRules == null) top.addView(small("+Paste", 38) { pastePairs() })   // v0.41.0: Find is empty and no list yet: paste many pairs at once
        val sp = View(ctx)
        sp.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        top.addView(sp)
        val rr0 = setRun
        val pillText: String
        var warn = false
        if (res.err != null || (setRules != null && rr0 != null && rr0.err != null)) { pillText = "Error"; warn = true }
        else if (setRules != null) pillText = if (res.changed == 0) "No match" else (res.changed.toString() + (if (res.changed == 1) " change" else " changes"))
        else if (res.blank) pillText = "Type Find"
        else pillText = ReplaceEngine.info(res)
        if (floatSwitch()) top.addView(small(if (winShows()) "\u2713 Float" else "Float", 38) { toggleFloat() })   // v0.39.3
        if (!wOn) top.addView(pill(pillText, warn))   // v0.39.3: the window header shows the count
        root.addView(top)

        // 2) mode row (Text | Word | Regex | Options) + the folded option chips
        if (setRules == null || pairMode) {   // v0.40.0: also while a pair list is being made
            val mr = row()
            mr.gravity = Gravity.CENTER_VERTICAL
            for (i in 0..2) {
                val c = UiKit.chip(ctx, prefs, ReplaceEngine.MODE_NAMES[i], opt.mode == i, 34, { v -> host.feedback(v) }) { opt.mode = i; optChanged() }
                c.layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).also { it.setMargins(dp(2), dp(2), dp(2), dp(2)) }
                mr.addView(c)
            }
            // v0.42.1 (M1): pref rp_slim ON (default OFF) = slim Options button + floating menu; any problem = the old chip + folded rows below
            var slimBtn: android.widget.Button? = null
            if (ReplaceSlim.on(prefs) && !slimFail) {
                slimBtn = try { ReplaceSlim.optionsButton(ctx, prefs, host, opt, protectList.size, { optChanged() }, { openProtect() }, { slimFail = true; refresh() }) } catch (_: Throwable) { null }
                if (slimBtn == null) slimFail = true
                else if (slimFail) slimBtn = null
            }
            val oc = slimBtn ?: UiKit.chip(ctx, prefs, if (optsOpen) "Options \u25B4" else "Options \u25BE", optsOpen, 34, { v -> host.feedback(v) }) { optsOpen = !optsOpen; refresh() }
            oc.layoutParams = LinearLayout.LayoutParams(0, dp(34), 1.5f).also { it.setMargins(dp(2), dp(2), dp(2), dp(2)) }
            mr.addView(oc)
            root.addView(mr)
            if (slimBtn == null) { try { ReplaceSlim.closeIfIdle() } catch (_: Throwable) { } }   // v0.42.1
            if (optsOpen && slimBtn == null) {
                val orow = row()
                orow.addView(UiKit.chip(ctx, prefs, "Aa", opt.matchCase, 34, { v -> host.feedback(v) }) { opt.matchCase = !opt.matchCase; optChanged() })
                orow.addView(UiKit.chip(ctx, prefs, "Keep", opt.keepCase, 34, { v -> host.feedback(v) }) { opt.keepCase = !opt.keepCase; if (opt.keepCase) opt.matchCase = false; optChanged() })
                if (opt.mode != ReplaceEngine.MODE_REGEX) {
                    orow.addView(UiKit.chip(ctx, prefs, "List", opt.list, 34, { v -> host.feedback(v) }) { opt.list = !opt.list; optChanged() })
                    orow.addView(UiKit.chip(ctx, prefs, "\\n", opt.escapes, 34, { v -> host.feedback(v) }) { opt.escapes = !opt.escapes; optChanged() })
                }
                root.addView(orow)
                val orow2 = row()   // v0.41.0: Smart matching + the never-change list
                if (opt.mode != ReplaceEngine.MODE_REGEX) orow2.addView(UiKit.chip(ctx, prefs, "Smart", opt.smart, 34, { v -> host.feedback(v) }) { opt.smart = !opt.smart; optChanged() })
                orow2.addView(UiKit.chip(ctx, prefs, if (protectList.isEmpty()) "Never change" else "Never change (" + protectList.size + ")", protectList.isNotEmpty(), 34, { v -> host.feedback(v) }) { openProtect() })
                root.addView(orow2)
            }
        }

        // 3) ONE card: Find + With (or the active set)
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(10), dp(2), dp(4), dp(2))
        try { card.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
        if (setRules != null && !pairMode) {
            card.addView(miniLine("Set", (setName ?: "") + " (" + nRules + ")", { typing = 3; refresh() },
                listOf(Pair("\u2715 Set") { dropSet(); refresh() }, Pair("Check") { checkNow() }, Pair("Never") { openProtect() }, Pair("Fmt") { copyForFormatter() })))   // v0.41.0: Never = the never-change list
        } else {
            card.addView(miniLine("Find", find, { startTyping(1) },
                listOf(Pair("Sel") { take(true, io.selected(), "Nothing is selected") }, Pair("Clip") { take(true, io.clipText(), "Clipboard is empty") })))
            card.addView(miniDivider())
            card.addView(miniLine("With", repl, { startTyping(2) },
                listOf(Pair("\u21C4") { swap() }, Pair("Sel") { take(false, io.selected(), "Nothing is selected") }, Pair("Clip") { take(false, io.clipText(), "Clipboard is empty") })))
        }
        val cardLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        cardLp.setMargins(dp(2), dp(4), dp(2), dp(2))
        card.layoutParams = cardLp
        root.addView(card)

        // 3b) v0.40.0: pair list buttons (only while a pair list exists) + "Whole word (N)" after a Check found short words
        if (setRules != null) {
            val nShort = ReplaceCheck.shortCount(checkWarn)
            if (pairMode || nShort > 0) {
                val pr = row()
                pr.gravity = Gravity.CENTER_VERTICAL
                if (pairMode) pr.addView(btn("Check", 0.8f, 34) { checkNow() })
                if (pairMode) pr.addView(btn("Paste", 0.8f, 34) { pastePairs() })   // v0.41.0: add many pairs from the clipboard
                if (pairMode) pr.addView(btn("List " + pairRules.size, 0.9f, 34) { typing = 8; clearAsk = false; refresh() })   // v0.41.1: view / edit / remove / clear the pairs
                if (nShort > 0) pr.addView(btn("Whole word (" + nShort + ")", 1.5f, 34) { fixShort() })
                if (pairMode) {
                    pr.addView(btn("Save as Set", 1.2f, 34) { savePairs() })
                    pr.addView(btn(if (clearAsk) "Sure? Clear" else "Clear", 1f, 34) { clearPairs() })
                }
                root.addView(pr)
            }
        }

        // 4) step row (only when there are matches)
        val nHits = res.hits.size
        if (fcard == null && setRules == null && nHits > 0 && res.err == null) {   // v0.39.1: the float card has its own < n/N > and Replace this
            step = step.coerceIn(0, nHits - 1)
            val sr = row()
            sr.gravity = Gravity.CENTER_VERTICAL
            sr.addView(small("<", 34) { step = if (step <= 0) nHits - 1 else step - 1; showStep(res) })
            sr.addView(small((step + 1).toString() + "/" + res.count, 34) { showStep(res) })
            sr.addView(small("Skip", 34) { step = if (step >= nHits - 1) 0 else step + 1; showStep(res) })
            sr.addView(btn("Replace this", 1f, 34) { onReplace(ReplaceEngine.replaceNth(src, find, repl, opt, step + 1), true) })
            sr.addView(btn("Rest", 0.8f, 34) { onReplace(ReplaceEngine.run(src, find, repl, opt, step + 1, Int.MAX_VALUE), true) })
            root.addView(sr)
        }

        // 5) preview card + ONE note line (the message if there is one, else the scope)
        if (fcard != null) root.addView(fcard) else if (!wOn && pairMode) root.addView(pairListView())   // v0.40.0: the pair list takes the place of the preview card
        else if (!wOn) {
            val sv = ScrollView(ctx)
            val svLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56))
            svLp.setMargins(dp(2), dp(2), dp(2), dp(0))
            sv.layoutParams = svLp
            try { sv.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
            val pv = TextView(ctx)
            pv.textSize = 13f
            pv.setTextColor(try { JabraTokens.cardText(prefs) } catch (_: Throwable) { Color.BLACK })
            pv.setPadding(dp(10), dp(4), dp(10), dp(4))
            var pt: CharSequence = try { previewSpan(res) } catch (_: Throwable) { previewText(res) }
            if (src.isEmpty() && res.err == null) pt = "No text: select text or type some first"
            pv.text = pt
            sv.addView(pv)
            root.addView(sv)
        }
        root.addView(UiKit.noteText(ctx, prefs, if (shown.isNotEmpty()) shown else scopeWord().trim() + ": " + src.length + " characters"))

        // 6) actions: ONE filled primary button, the rest quiet
        val act = row()
        val ask = confirmAll && !usedSel
        val canGo = res.err == null && res.count > 0 && !res.blank
        val allLabel = if (ask) "Sure? Replace all" else if (res.err == null && res.count > 0) "Replace all (" + res.count + ")" else "Replace all"
        val all = btn(allLabel, 2f, 44) { onReplace(res, false) }
        accentButton(all, if (ask) JabraTokens.WARN else JabraTokens.ACCENT)
        peekOn(all) { peekResult(res) }
        all.alpha = if (canGo) 1f else 0.6f
        act.addView(all)
        if (setRules == null) act.addView(btn("1st", 0.7f, 44) { onReplace(ReplaceEngine.replaceFirst(src, find, repl, opt), true) })
        act.addView(btn(scopeLabel(), 1.3f, 44) { toggleScope() })
        if (undoStack.canUndo()) act.addView(btn("\u21B6 " + ReplaceExtras.undoLabel(undoStack.size()), 1f, 44) { undo() })
        else if (copiedOut != null && copiedOut == res.out) act.addView(btn("Paste", 1f, 44) { pasteCopied() })
        else act.addView(btn("Copy", 1f, 44) { copyResult(res) })
        root.addView(act)
        if (floatSwitch() && fcard == null) {   // v0.39.3 (FLOAT F4): show / update the floating window once the view is attached
            val fSnap = ReplaceFloat.snap(src, res, step, setRules != null, floatLines(res), ReplaceFloat.WIN_MAX_ROWS, if (setRules != null) setRun?.edits else null)   // v0.44.0 / v0.45.0 (context rows of a pair list)
            val fTab = ReplaceFloat.sanitizeTab(prefs.int("rp_float_tab", 0))
            val fAct = floatActions(res, shown)
            root.post { floatSync(root, fSnap, fTab, fAct) }
        }
        return root
    }

    // ------------------------------------------------------------------ v0.42.2 (M2) + v0.42.3 (M3): SLIM main view (look of docs/Replace_Tool_Minimal_Mock.html). NEW code, the old mainViewMini above is untouched.
    // The head (calc / msg / wOn / fcard) and the tail (floatSync) are copies of mainViewMini on purpose: if the float feed changes, change BOTH.
    private fun mainViewSlim(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)
        root.setPadding(dp(4), dp(4), dp(4), dp(4))
        valueTv = null
        keysBox = null
        ReplaceSlim.closeIfIdle()   // a menu left open without a pending re-show is closed

        val res = calc()
        val shown = msg
        msg = ""
        val nRules = setRules?.count { it.kind != ReplaceRules.K_COMMENT } ?: 0
        val wOn = winShows()
        val fcard: View? = if (floatOn() && !floatSwitch()) (try { floatCard(res, shown) } catch (_: Throwable) { null }) else null
        val setLoaded = setRules != null && !pairMode

        // 1) top bar: [ABC] [Sets] [Recent] [?] [Pair] ........ [Float] pill
        val top = row()
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(small("ABC", 38) { host.showLetters() })
        top.addView(small("Sets", 38) { typing = 3; confirmAll = false; refresh() })
        top.addView(small("Recent", 38) { typing = 4; confirmAll = false; rprAsk = false; refresh() })
        if (pairMode && find.isEmpty()) editIdx = -1
        val pb = ReplaceSlimPlan.pairButton(pairMode, setLoaded, find.isEmpty(), editIdx)
        if (pb == "+Paste") top.addView(small(pb, 38) { pastePairs() })
        else if (pb.isNotEmpty()) top.addView(small(pb, 38) { addPair(false) })
        val sp = View(ctx)
        sp.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        top.addView(sp)
        val rr0 = setRun
        val pillText: String
        var warn = false
        if (res.err != null || (setRules != null && rr0 != null && rr0.err != null)) { pillText = "Error"; warn = true }
        else if (setRules != null) pillText = if (res.changed == 0) "No match" else (res.changed.toString() + (if (res.changed == 1) " change" else " changes"))
        else if (res.blank) pillText = "Type Find"
        else pillText = ReplaceEngine.info(res)
        if (floatSwitch()) top.addView(small(if (winShows()) "\u2713 Float" else "Float", 38) { toggleFloat() })
        if (!wOn) top.addView(pill(pillText, warn))
        root.addView(top)

        // 2) mode row: segmented [Text | Word | Regex] + the Options button
        if (setRules == null || pairMode) {
            val mr = row()
            mr.gravity = Gravity.CENTER_VERTICAL
            val seg = ReplaceSlim.segmented(ctx, prefs, host, opt.mode) { i -> opt.mode = i; optChanged() }
            seg.layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).also { it.setMargins(dp(2), dp(2), dp(2), dp(2)) }
            mr.addView(seg)
            val ob = ReplaceSlim.optionsButton(ctx, prefs, host, opt, protectList.size, { optChanged() }, { openProtect() }, { slimFail = true; refresh() }, { typing = 5; helpPage = 0; confirmAll = false; refresh() })   // v0.42.4: Options > Help (the "?" moved here)
                ?: throw IllegalStateException("options button")   // caller: slimFail = true = old view
            ob.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)).also { it.setMargins(dp(6), dp(2), dp(2), dp(2)) }
            mr.addView(ob)
            root.addView(mr)
        }

        // 3) ONE card: Find + With (D5: With has only the swap icon), or the loaded set (unchanged line)
        val card = ReplaceSlim.slimCard(ctx, prefs)
        if (setLoaded) {
            card.addView(miniLine("Set", (setName ?: "") + " (" + nRules + ")", { typing = 3; refresh() },
                listOf(Pair("\u2715 Set") { dropSet(); refresh() }, Pair("Check") { checkNow() }, Pair("Never") { openProtect() }, Pair("Fmt") { copyForFormatter() })))
        } else {
            card.addView(ReplaceSlim.slimLine(ctx, prefs, host, "Find", find, "Type the word to find", shortVal(find), { startTyping(1) },
                listOf(Pair("Sel") { take(true, io.selected(), "Nothing is selected") }, Pair("Clip") { take(true, io.clipText(), "Clipboard is empty") })))
            card.addView(ReplaceSlim.slimDivider(ctx, prefs))
            card.addView(ReplaceSlim.slimLine(ctx, prefs, host, "With", repl, "Replace with", shortVal(repl), { startTyping(2) },
                listOf(Pair("\u21C4") { swap() })))
        }
        root.addView(card)

        // 3b) pair row (only while a pair list exists): [Check] [List N] [More]
        val nShort = if (setRules != null) ReplaceCheck.shortCount(checkWarn) else 0
        if (pairMode) {
            root.addView(ReplaceSlim.pairRow(ctx, prefs, host, pairRules.size, nShort, clearAsk,
                { checkNow() }, { typing = 8; clearAsk = false; refresh() },
                { id ->
                    if (id == ReplaceMore.PASTE) pastePairs()
                    else if (id == ReplaceMore.SAVE) savePairs()
                    else if (id == ReplaceMore.WORD) fixShort()
                    else if (id == ReplaceMore.CLEAR) clearPairs()   // keeps its own 2-tap logic
                },
                { slimFail = true; refresh() }))
        } else if (nShort > 0) {   // D7: a loaded set keeps "Whole word (N)" reachable
            val wr = row()
            wr.gravity = Gravity.CENTER_VERTICAL
            wr.addView(btn("Whole word (" + nShort + ")", 0f, 34) { fixShort() })
            root.addView(wr)
        }

        // 4) step row (D1 / D3: no preview card, no "1st"); hidden while the float test card is shown (it has its own < n/N > and Replace this)
        val ask = confirmAll && !usedSel
        if (fcard != null) {
            root.addView(fcard)
        } else {
            val nHits = res.hits.size
            val plan = ReplaceSlimPlan.step(nHits, res.count, step, res.err != null, res.blank, setLoaded, pairMode, find.isEmpty(), pairRules.size, ask)
            if (nHits > 0) step = step.coerceIn(0, nHits - 1)
            root.addView(ReplaceSlim.stepRow(ctx, prefs, host, plan,
                { if (nHits > 0) { step = if (step <= 0) nHits - 1 else step - 1; showStep(res) } },
                { if (nHits > 0) { step = if (step >= nHits - 1) 0 else step + 1; showStep(res) } },
                { showStep(res) },
                { onReplace(ReplaceEngine.run(src, find, repl, opt, step + 1, Int.MAX_VALUE), true) },
                { onReplace(ReplaceEngine.replaceNth(src, find, repl, opt, step + 1), true) },
                { onReplace(res, false) },
                { b -> accentButton(b, if (ask) JabraTokens.WARN else JabraTokens.ACCENT); peekOn(b) { peekResult(res) } }))
        }

        // 5) ONE note line: message or scope text (tap = scope), link on the right: Undo > Paste back > Copy (D2)
        val canPasteBack = copiedOut != null && copiedOut == res.out
        val note = ReplaceSlimPlan.note(shown, scopeWord(), src.length, undoStack.canUndo(), ReplaceExtras.undoLabel(undoStack.size()), canPasteBack)
        root.addView(ReplaceSlim.noteLine(ctx, prefs, host, note, { toggleScope() }, {
            if (note.rightKind == ReplaceSlimPlan.R_UNDO) undo()
            else if (note.rightKind == ReplaceSlimPlan.R_PASTE_BACK) pasteCopied()
            else copyResult(res)
        }))

        if (floatSwitch() && fcard == null) {   // floating window feed (same as mainViewMini)
            val fSnap = ReplaceFloat.snap(src, res, step, setRules != null, floatLines(res), ReplaceFloat.WIN_MAX_ROWS, if (setRules != null) setRun?.edits else null)   // v0.44.0 / v0.45.0 (context rows of a pair list)
            val fTab = ReplaceFloat.sanitizeTab(prefs.int("rp_float_tab", 0))
            val fAct = floatActions(res, shown)
            root.post { floatSync(root, fSnap, fTab, fAct) }
        }
        return root
    }

    private fun setsViewNew(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)
        root.setPadding(dp(3), dp(4), dp(3), dp(4))
        root.addView(PanelKit.header(ctx, prefs, host, "Sets", "<", emptyList()) { typing = 0; refresh() })

        // v0.58.4: Novel-wise pairs are managed only in Fix terms. Sets shows custom sets and ready-made rules.
        val saved = try { ReplaceStore.load(prefs) } catch (_: Throwable) { LinkedHashMap<String, ReplaceStore.Def>() }
        val ready = try { ReplaceStore.ready() } catch (_: Throwable) { LinkedHashMap<String, ReplaceStore.Def>() }
        // v0.58.6: filter NovelBook-owned sets through the existing API.
        val knownNovels = NovelBook.all(NovelBook.parse(prefs.str("rp_novels", "")), saved.keys)
        val custom = saved.filterKeys { name -> !NovelBook.isMine(name) && knownNovels.none { NovelBook.belongs(name, it) } }
        val novelPairs = if (novelCur.isNotEmpty()) try {
            val mine = saved.filterKeys { NovelBook.isMine(it) && NovelBook.belongs(it, novelCur) }
            FixNames.rows(novelCur, ArrayList(mine.keys), mine.values.map { it.rules }).size
        } catch (_: Throwable) { 0 } else 0

        // Compact top controls; long novel names can scroll instead of forcing other buttons off-screen.
        val topScroll = android.widget.HorizontalScrollView(ctx)
        topScroll.isHorizontalScrollBarEnabled = false
        val top = row()
        top.addView(UiKit.chip(ctx, prefs, if (novelCur.isEmpty()) "Choose novel  ▾" else novelCur + "  ▾", novelCur.isNotEmpty(), 36, { v -> host.feedback(v) }) { openNovels() })
        top.addView(UiKit.chip(ctx, prefs, "Fix terms", false, 36, { v -> host.feedback(v) }) { openFixNames() })
        top.addView(UiKit.chip(ctx, prefs, "Preview", setPreviewOn, 36, { v -> host.feedback(v) }) { setPreviewOn = !setPreviewOn; setPrev = null; refresh() })
        topScroll.addView(top)
        root.addView(topScroll)

        // Novel pairs are deliberately not duplicated in this screen: this shortcut opens their single manager.
        if (novelCur.isNotEmpty()) {
            val pairShortcut = btn("Novel pairs  ·  $novelPairs  ·  Manage in Fix terms", 1f, 34) { openFixNames() }
            root.addView(pairShortcut)
        }

        // Two clear categories, not glossary/topic chips. My pairs and imported glossary sets stay stored but hidden here.
        val chipScroll = android.widget.HorizontalScrollView(ctx)
        chipScroll.isHorizontalScrollBarEnabled = false
        val chips = row()
        val myCount = custom.size
        val readyCount = ready.size
        if (topicChip != NovelBook.MINE && topicChip != NovelBook.READY || topicChip == NovelBook.READY && readyCount == 0) topicChip = NovelBook.MINE
        chips.addView(UiKit.chip(ctx, prefs, "My sets  $myCount", topicChip == NovelBook.MINE, 34, { v -> host.feedback(v) }) { topicChip = NovelBook.MINE; setPrev = null; refresh() })
        if (readyCount > 0) chips.addView(UiKit.chip(ctx, prefs, "Ready-made  $readyCount", topicChip == NovelBook.READY, 34, { v -> host.feedback(v) }) { topicChip = NovelBook.READY; setPrev = null; refresh() })
        chipScroll.addView(chips)
        root.addView(chipScroll)

        previewBox()?.let { root.addView(it) }
        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(if (setPrev != null) 104 else 174))
        val tiles = ArrayList<PanelKit.Tile>()
        try {
            if (topicChip == NovelBook.MINE) {
                for ((n, d) in custom) {
                    val rules = try { ReplaceRules.parse(d.rules).active() } catch (_: Throwable) { 0 }
                    tiles.add(PanelKit.Tile(n, rules.toString() + (if (rules == 1) " rule" else " rules"), n == setName) { tapSet(n, d) }.also { t -> t.onLong = { a -> peekSetAt(a, n, d) } })
                }
            } else if (topicChip == NovelBook.READY) {
                for ((n, d) in ready) tiles.add(PanelKit.Tile(n, "ready-made", n == setName) { tapSet(n, d) }.also { t -> t.onLong = { a -> peekSetAt(a, n, d) } })
            }
        } catch (_: Throwable) { }
        if (novelCur.isNotEmpty() && topicChip == NovelBook.MINE) tiles.add(PanelKit.Tile("Never change", protectList.size.toString() + (if (protectList.size == 1) " name" else " names") + " · not replaced") { openProtect() })
        if (tiles.isEmpty()) {
            val emptyText = if (topicChip == NovelBook.READY) "No ready-made sets available." else "No custom sets yet. Novel pairs are managed in Fix terms."
            root.addView(UiKit.noteText(ctx, prefs, emptyText))
        }
        sv.addView(PanelKit.tileGrid(ctx, prefs, host, 1, tiles))
        root.addView(sv)
        return root
    }

    // ------------------------------------------------------------------ NOVELS (independent saved wrong -> right lists)
    private fun novelNames(): List<String> = try { NovelBook.parse(prefs.str("rp_novels", "")) } catch (_: Throwable) { emptyList() }

    private fun openNovels() { typing = 9; novelDelAsk = false; novelNote = ""; confirmAll = false; setPrev = null; refresh() }

    /** Makes a novel active (name "" = none). Its sets and its Never change list load together; other novels stay untouched. */
    private fun pickNovel(name: String) {
        try {
            novelCur = name; topicChip = NovelBook.ALL; novelDelAsk = false
            prefs.setStr("rp_novel_cur", name)
            loadProtect()
            setRun = null; confirmAll = false; checkAck = false; checkWarn = null; step = 0; setPrev = null
            novelNote = ""
        } catch (_: Throwable) { novelNote = "Could not switch" }
        refresh()
    }

    /** v0.58.1: manually create an independent novel pair list. */
    private fun fnAddNovel(name: String): String {
        return try {
            val clean = name.trim()
            if (clean.isEmpty()) return "Enter a novel name."
            val reg = NovelBook.parse(prefs.str("rp_novels", ""))
            if (NovelBook.has(reg, clean)) {
                pickNovel(reg.first { it.equals(clean, true) })
                return "Novel already exists — selected it."
            }
            if (!NovelBook.add(reg, clean)) return "Novel limit reached (${NovelBook.MAX_NOVELS})."
            // Verify the registry write instead of trusting the prefs wrapper (which intentionally swallows errors).
            prefs.setStr("rp_novels", NovelBook.text(reg))
            val saved = NovelBook.parse(prefs.str("rp_novels", ""))
            if (!NovelBook.has(saved, clean)) return "Could not save novel. Storage write failed."
            pickNovel(clean)
            "Added " + clean + "."
        } catch (e: Throwable) {
            "Could not save novel. Please try again."
        }
    }

    /** Deletes the active novel with its saved pairs and Never change list (two taps). Other novels are not touched. */
    private fun deleteNovel() {
        val gone = novelCur
        if (gone.isEmpty()) return
        if (!novelDelAsk) { novelDelAsk = true; refresh(); return }
        try {
            val all = ReplaceStore.load(prefs)
            all.keys.removeAll { NovelBook.belongs(it, gone) }
            ReplaceStore.save(prefs, all)
            val reg = NovelBook.parse(prefs.str("rp_novels", ""))
            reg.removeAll { it.equals(gone, true) }
            prefs.setStr("rp_novels", NovelBook.text(reg))
            prefs.setStr(NovelBook.neverKey(gone), "")
            val sn = setName
            if (sn != null && NovelBook.belongs(sn, gone)) dropSet()
            val next = reg.firstOrNull() ?: ""
            novelDelAsk = false
            pickNovel(next)
            novelNote = "Deleted " + gone + " with its sets and Never change list."
        } catch (_: Throwable) { novelNote = "Could not delete" }
        refresh()
    }

    /** typing == 9: the Novels list. Tap a novel = active; add manually. */
    private fun showNovelNameDialog() {
        try {
            val input = android.widget.EditText(ctx)
            input.hint = "Novel name"
            input.setSingleLine(true)
            android.app.AlertDialog.Builder(ctx).setTitle("Add novel").setView(input)
                .setNegativeButton("Cancel", null).setPositiveButton("Add") { _, _ ->
                    novelNote = fnAddNovel(input.text?.toString() ?: "")
                    refresh()
                }.show()
        } catch (_: Throwable) { novelNote = "Could not add novel"; refresh() }
    }

    private fun novelsView(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)
        root.setPadding(dp(2), dp(4), dp(2), dp(4))
        root.addView(PanelKit.header(ctx, prefs, host, "Novels", "<", emptyList()) { typing = 3; novelDelAsk = false; refresh() })
        val names = novelNames()
        // v0.58.1: manual novel creation; no glossary import.
        val top = row()
        top.addView(btn("+ Add novel", 1f, 40) { showNovelNameDialog() })
        root.addView(top)
        if (names.isEmpty()) {
            root.addView(UiKit.noteText(ctx, prefs, "No novel yet. Add a novel to create its own saved wrong → right pairs."))
        } else {
            val sv = ScrollView(ctx)
            sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(130))
            val tiles = ArrayList<PanelKit.Tile>()
            val saved = try { ReplaceStore.load(prefs) } catch (_: Throwable) { LinkedHashMap<String, ReplaceStore.Def>() }
            for (n in names) {
                val on = n.equals(novelCur, true)
                val mine = saved.filterKeys { NovelBook.isMine(it) && NovelBook.belongs(it, n) }
                val pairCount = FixNames.rows(n, ArrayList(mine.keys), mine.values.map { it.rules }).size
                val never = try { ReplaceProtect.parse(prefs.str(NovelBook.neverKey(n), "")).size } catch (_: Throwable) { 0 }
                tiles.add(PanelKit.Tile((if (on) "\u2713 " else "") + n, "${pairCount}${if (pairCount == 1) " pair" else " pairs"} · ${never} kept", on, { pickNovel(n) }))
            }
            sv.addView(PanelKit.tileGrid(ctx, prefs, host, 1, tiles))
            root.addView(sv)
            val r = row()
            if (novelCur.isNotEmpty()) r.addView(btn(if (novelDelAsk) "Sure? Delete " + novelCur else "Delete " + novelCur, 1.4f, 34) { deleteNovel() })
            else r.addView(UiKit.noteText(ctx, prefs, "No novel active: all novels’ pairs show."))
            if (novelCur.isNotEmpty()) r.addView(btn("No novel", 0.8f, 34) { pickNovel("") })
            root.addView(r)
        }
        if (novelNote.isNotEmpty()) root.addView(UiKit.noteText(ctx, prefs, novelNote))
        return root
    }

    // ------------------------------------------------------------------ v0.57.0 (FX1): ONE SCREEN "Fix names, per novel" (design = Fix_names_per_novel_mock.html)
    // UI = FixNamesView.kt (class FixNamesUi), logic = typing/FixNames.java (tested). typing == 10 = the screen (tabs Fix names | Novels), typing == 12 = typing in its search box.
    // Everything below uses the OLD code (undo stack, never-change list, glossary paste, delete novel, doReplace). If the screen cannot be built, fnView() falls back to the old Sets screen.
    private var fnToast = ""
    private var fnKeepBack = false                        // v0.58.0 (FX2): the Never change screen was opened from Fix terms (Keep as is > + Add): back returns there
    private var fnBack = false                                // true while Find / With / search is typed from this screen: Done returns here
    private var fnQuery = ""                                  // search box of this screen (memory only)

    private val fnUi: FixNamesUi by lazy { FixNamesUi(ctx, prefs, host, fnApi) }

    private val fnApi = object : FixNamesUi.Api {
        override fun novel(): String = novelCur
        override fun text(): String = src
        override fun find(): String = find
        override fun repl(): String = repl
        override fun query(): String = fnQuery
        override fun protect(): List<String> = ArrayList(protectList)
        override fun setNames(): List<String> = try { ReplaceStore.load(prefs).keys.filter { NovelBook.isMine(it) } } catch (_: Throwable) { ArrayList<String>() }
        override fun setTexts(): List<String> = try { ReplaceStore.load(prefs).filterKeys { NovelBook.isMine(it) }.values.map { it.rules } } catch (_: Throwable) { ArrayList<String>() }
        override fun type(which: Int) { fnBack = true; startTyping(which) }
        override fun addPair(forms: List<String>, to: String, topic: String): String { fnToast = fnAddPair(forms, to, topic); fnRefresh(); return fnToast }
        override fun fix(rules: List<ReplaceRules.Rule>): String { fnToast = fnFix(rules); fnRefresh(); return fnToast }
        override fun undo() { fnUndo() }
        override fun canUndo(): Boolean = undoStack.canUndo()
        override fun toast(): String = if (fnToast.isNotEmpty()) fnToast else novelNote
        override fun back() { typing = 0; fnToast = ""; fnRefresh() }
        override fun novels(): List<FixNamesUi.NovelRow> = fnNovelRows()
        override fun pickNovel(name: String) { fnToast = name + " is active."; fnPick(name) }
        // Do not refresh the panel from inside the dialog callback. The UI refreshes once
        // after the dialog closes; refreshing here could throw and falsely report failure
        // even when the novel was already saved.
        override fun addNovel(name: String): String { fnToast = fnAddNovel(name); return fnToast }
        override fun deleteAsk(): Boolean = novelDelAsk
        override fun deleteNovel() { fnToast = ""; fnDelete() }
        override fun removeLock(term: String) { fnRemoveLock(term) }
        override fun refresh() { fnRefresh() }
        // v0.58.0 (FX2): Fix terms screen
        override fun clearFields() { find = ""; repl = ""; caretPos = 0; fnRefresh() }
        override fun loadPair(forms: String, to: String) { find = forms; repl = to; caretPos = 0; fnToast = ""; fnRefresh() }
        override fun openKeep() { fnKeepBack = true; fnUi.tab = 3; openProtect() }

    }

    private fun fnRefresh() { refresh() }

    private fun openFixNames() {
        try { fnToast = ""; fnUi.open(); typing = 10; confirmAll = false; setPrev = null; novelNote = ""; readSource(false) } catch (_: Throwable) { typing = 0 }
        refresh()
    }

    private fun fnView(): View = try { fnUi.build() } catch (_: Throwable) { typing = 3; setsViewNew() }

    private fun fnPick(name: String) { pickNovel(name) }
    private fun fnDelete() { deleteNovel() }
    private fun fnUndo() { fnToast = ""; undo() }

    private fun fnRemoveLock(term: String) {
        try {
            val i = protectList.indexOf(term)
            if (i >= 0) { protectList.removeAt(i); protectChanged(); fnToast = "Removed from Never change: " + term }
        } catch (_: Throwable) { fnToast = "Could not remove" }
        refresh()
    }

    private fun fnNovelRows(): List<FixNamesUi.NovelRow> = try {
        val sets = ReplaceStore.load(prefs)
        val mine = sets.filterKeys { NovelBook.isMine(it) }
        val names = ArrayList<String>(mine.keys)
        val texts = mine.values.map { it.rules }
        novelNames().map { n ->
            val never = try { ReplaceProtect.parse(prefs.str(NovelBook.neverKey(n), "")).size } catch (_: Throwable) { 0 }
            FixNamesUi.NovelRow(n, FixNames.rows(n, names, texts).size, never, n.equals(novelCur, true))
        }
    } catch (_: Throwable) { ArrayList<FixNamesUi.NovelRow>() }

    /** "Add pair" = saved to the active novel at once (its My pairs set, under the topic). Same Find = updated in place. Nothing else is stored. */
    private fun fnAddPair(forms: List<String>, to: String, topic: String): String {
        try {
            if (novelCur.isEmpty()) return "Pick a novel first: tap the novel name."
            if (forms.isEmpty()) return "Type the wrong forms first."
            if (to.isEmpty()) return "Type the right form."
            val m = ReplacePairList.make(forms.joinToString("; "), to, ReplaceEngine.MODE_TEXT, false, false)
            val r = m.rule ?: return m.error ?: "Could not add this pair"
            val line = ReplacePairList.text(listOf(r)).trim()
            val key = line.substringBefore("=>").trim()
            val all = ReplaceStore.load(prefs)
            val mine = all.keys.filter { NovelBook.belongs(it, novelCur) && NovelBook.isMine(it) }
            var target: String? = null
            for (n in mine) {
                val d = all[n] ?: continue
                if (d.rules.split("\n").any { l -> val t = l.trim(); t.isNotEmpty() && t[0] != '#' && t.substringBefore("=>").trim() == key }) { target = n; break }
            }
            if (target == null) for (n in mine) {
                val d = all[n] ?: continue
                if (ReplaceRules.parse(d.rules).active() < NovelBook.RULES_PER_SET) { target = n; break }
            }
            var updated = false
            val tg = target
            if (tg != null) {
                val d = all[tg] ?: return "Could not save"
                val mg = FixNames.mergeWithTopic(d.rules, line, topic, NovelBook.RULES_PER_SET)
                all[tg] = ReplaceStore.Def(mg.text, d.matchCase, false)
                updated = mg.updated > 0
            } else {
                if (NovelBook.setsOf(all.keys, novelCur).size >= NovelBook.MAX_SETS_PER_NOVEL || all.size >= ReplaceStore.MAX_ALL) return "No room for a new set: delete old sets first."
                var num = mine.size + 1
                var nm = NovelBook.mySetName(novelCur, num)
                while (all.containsKey(nm)) { num++; nm = NovelBook.mySetName(novelCur, num) }
                val mg = FixNames.mergeWithTopic("# " + nm + " (pairs saved by me)\n", line, topic, NovelBook.RULES_PER_SET)
                all[nm] = ReplaceStore.Def(mg.text, false, false)
            }
            ReplaceStore.save(prefs, all)
            find = ""; repl = ""; caretPos = 0
            return (if (updated) "Updated, saved to " else "Saved to ") + novelCur + "."
        } catch (_: Throwable) { return "Could not save" }
    }

    /** "Fix N in text": the pairs that are switched on run through the OLD set code (never-change list, edits match by match, Undo stack). */
    private fun fnFix(rules: List<ReplaceRules.Rule>): String {
        try {
            if (io.blocked()) return "Not available in password fields."
            readSource(false)
            if (rules.isEmpty() || src.isEmpty()) return "Nothing to fix"
            val o = opt.copy(); o.mode = ReplaceEngine.MODE_TEXT; o.list = false; o.keepCase = false; o.smart = false; o.matchCase = false; o.protect = protectList
            setRules = ArrayList(rules); setOpt = o; setName = "Fix names"; setRun = null; pairMode = false
            val r = calc()
            dropSet()
            if (r.changed == 0 || r.out == src) return if (r.skipped > 0) "Nothing to change: " + r.skipped + " kept (never change)" else "Nothing to fix"
            doReplace(r)
            dropSet()
            readSource(false)
            return "Fixed " + r.changed + ". Tap Undo to go back."
        } catch (_: Throwable) { try { dropSet() } catch (_: Throwable) { }; return "Could not fix" }
    }

    // ------------------------------------------------------------------ v0.40.0: PAIR LIST ("+Pair"), Check pairs, Save as Set
    // The list lives in MEMORY (pairRules) and is run through the set code (setRules / setOpt / ReplaceRules.run). Logic = typing/ReplacePairList.java + ReplaceCheck.java (tested).
    // "Save as Set" is the only thing written to disk (a normal set, auto name "Pairs 1"...). Everything is in try / catch by the callers (button handlers).

    /** Forgets the active set / pair list (the old "x Set" code, now in one place). */
    private fun dropSet() {
        setRules = null; setName = null; setRun = null
        pairMode = false; pairRules.clear(); checkAck = false; checkWarn = null; clearAsk = false; confirmAll = false; editIdx = -1
    }

    /** pairRules changed: the set code gets a fresh copy, the old check result is gone. */
    private fun syncPairs() {
        setRules = ArrayList(pairRules); setName = "Pairs"; setRun = null
        confirmAll = false; checkAck = false; checkWarn = null; clearAsk = false; step = 0; editIdx = -1
    }

    private fun addPair(fromReplace: Boolean) {
        confirmAll = false
        if (io.blocked()) { msg = "Not available in password fields."; refresh(); return }
        if (setRules != null && !pairMode) { msg = "A set is loaded. Tap \u2715 Set first, then make a pair list"; refresh(); return }
        val m = ReplacePairList.make(find, repl, opt.mode, opt.keepCase, opt.list, opt.smart)
        val r = m.rule
        if (r == null) { msg = m.error ?: "Could not add this pair"; refresh(); return }
        var note = ""
        if (!pairMode) {
            val o = opt.copy(); o.mode = ReplaceEngine.MODE_TEXT; o.list = false; o.keepCase = false; o.smart = false   // the mode / keep / smart of each pair are in the pair itself (word: / re: / keep: / smart:)
            setOpt = o
        } else if (opt.matchCase != setOpt.matchCase || opt.escapes != setOpt.escapes) {
            note = "  \u00B7  the list keeps the Aa / \\n settings of its first pair"
        }
        val idx = ReplacePairList.indexOf(pairRules, r)
        val ei = editIdx   // v0.41.1: a pair opened with Edit in the Pairs view is written back to the SAME place
        var upd = false
        if (ei in pairRules.indices) {
            var t = ei
            if (idx >= 0 && idx != ei) { pairRules.removeAt(idx); if (idx < ei) t = ei - 1 }   // same Find already elsewhere: keep one
            pairRules[t] = r
            upd = true
        } else {
            if (idx < 0 && pairRules.size >= ReplacePairList.MAX) { msg = "The list is full (" + ReplacePairList.MAX + " pairs)"; refresh(); return }
            if (idx >= 0) pairRules[idx] = r else pairRules.add(r)
        }
        pairMode = true
        syncPairs()
        find = ""; repl = ""; caretPos = 0
        msg = (if (idx >= 0 || upd) "Pair updated" else "Pair " + pairRules.size + " added") + (if (fromReplace) ". Tap Replace all to run the list" else "") + note + (if (novelCur.isNotEmpty()) "  \u00B7  keep it: Pairs > Save to " + novelCur else "")
        refresh()
    }

    /**
     * v0.56.0 (NV2): "Save to <novel>". The pairs of the list (Find = wrong forms split by ";", With = the right name) are kept in the "My pairs" sets of the ACTIVE novel,
     * so they are saved for the novel and managed in Fix terms. Same Find = updated, new = added (50 rules per set, then a new set). A new glossary paste never removes them.
     */
    private fun saveToNovel() {
        try {
            if (novelCur.isEmpty()) { msg = "Pick a novel first: Sets > novel name"; refresh(); return }
            var pending: List<String> = ReplacePairList.text(pairRules).split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (pending.isEmpty()) { msg = "No pairs to save"; refresh(); return }
            val all = ReplaceStore.load(prefs)
            var added = 0; var updated = 0
            val names = all.keys.filter { NovelBook.belongs(it, novelCur) && NovelBook.isMine(it) }
            for (n in names) {
                if (pending.isEmpty()) break
                val d = all[n] ?: continue
                val m = NovelBook.merge(d.rules, pending, NovelBook.RULES_PER_SET)
                all[n] = ReplaceStore.Def(m.text, d.matchCase, false)
                added += m.added; updated += m.updated; pending = ArrayList(m.overflow)
            }
            var num = names.size + 1
            while (pending.isNotEmpty()) {
                if (NovelBook.setsOf(all.keys, novelCur).size >= NovelBook.MAX_SETS_PER_NOVEL || all.size >= ReplaceStore.MAX_ALL) break
                var nm = NovelBook.mySetName(novelCur, num)
                while (all.containsKey(nm)) { num++; nm = NovelBook.mySetName(novelCur, num) }
                val m = NovelBook.merge("# " + nm + " (pairs saved by me)\n", pending, NovelBook.RULES_PER_SET)
                all[nm] = ReplaceStore.Def(m.text, setOpt.matchCase, false)
                added += m.added; updated += m.updated; pending = ArrayList(m.overflow)
                num++
            }
            ReplaceStore.save(prefs, all)
            msg = "Saved to " + novelCur + ": " + added + " new" + (if (updated > 0) ", " + updated + " updated" else "") + (if (pending.isNotEmpty()) ". " + pending.size + " did not fit: delete old sets" else "") + ". Manage them in Fix terms"
        } catch (_: Throwable) { msg = "Could not save" }
        refresh()
    }

    private fun removePair(i: Int) {
        if (i in pairRules.indices) pairRules.removeAt(i)
        if (pairRules.isEmpty()) { dropSet(); msg = "Pair list is empty" } else { syncPairs(); msg = "Pair removed" }
        refresh()
    }

    /** Tap on a pair: it goes back into Find / With (with its mode) and leaves the list; "+Pair" puts it back. */
    private fun editPair(i: Int) {
        val r = pairRules.getOrNull(i) ?: return
        find = r.find.take(ReplacePads.FIELD_MAX)
        repl = r.with.take(ReplacePads.FIELD_MAX)
        opt.mode = if (r.kind == ReplaceRules.K_REGEX) ReplaceEngine.MODE_REGEX else if (r.word) ReplaceEngine.MODE_WORD else ReplaceEngine.MODE_TEXT
        opt.keepCase = r.keep; opt.list = false; opt.smart = r.smart   // v0.41.0: smart
        saveOpt()
        pairRules.removeAt(i)
        if (pairRules.isEmpty()) dropSet() else syncPairs()
        msg = "Pair is in Find / With: change it, then tap +Pair"
        refresh()
    }

    private fun clearPairs() {
        if (pairRules.size >= 2 && !clearAsk) { clearAsk = true; msg = "Tap again to clear all " + pairRules.size + " pairs"; refresh(); return }
        dropSet()
        msg = "Pair list cleared"
        refresh()
    }

    private fun checkNow() {
        val rl = setRules
        if (rl == null) { msg = "Make a pair list or load a set first"; refresh(); return }
        val w = try { ReplaceCheck.check(rl, setOpt) } catch (_: Throwable) { ArrayList<ReplaceCheck.Warn>() }
        checkWarn = w; checkAck = true; confirmAll = false
        msg = ReplaceCheck.line(w)
        refresh()
    }

    /** "Whole word (N)": adds word: to the short / common words the Check found. A loaded set is changed for this run only (the saved set stays as it is). */
    private fun fixShort() {
        val rl = setRules ?: return
        val fixed = ReplaceCheck.withWholeWord(rl, setOpt)
        setRules = fixed
        if (pairMode) { pairRules.clear(); pairRules.addAll(fixed) }
        setRun = null; confirmAll = false; checkAck = true
        checkWarn = try { ReplaceCheck.check(fixed, setOpt) } catch (_: Throwable) { null }
        msg = "Short words are now whole-word" + (if (pairMode) "" else " (this run only, the saved set is not changed)")
        refresh()
    }

    private fun savePairs() {
        try {
            if (pairRules.isEmpty()) { msg = "The list is empty"; refresh(); return }
            val all = ReplaceStore.load(prefs)
            if (ReplaceStore.userCount(all) >= ReplaceStore.MAX) { msg = "Sets are full (" + ReplaceStore.MAX + "). Delete one in app > Tools & Panels"; refresh(); return }
            val name = ReplacePairList.nextName(all.keys)
            all[name] = ReplaceStore.Def(ReplacePairList.text(pairRules), setOpt.matchCase, false)
            ReplaceStore.save(prefs, all)
            msg = "Saved as set \"" + name + "\" (Sets > \u2605). Rename it in app > Tools & Panels" + (if (setOpt.escapes) "  \u00B7  the \\n switch is not kept in sets" else "")
        } catch (_: Throwable) { msg = "Could not save the set" }
        refresh()
    }

    /** The pair list inside the panel: one line per pair ("3x cat -> dog", a warning mark after a Check), tap = edit, x = remove. */
    private fun pairListView(): View {
        val sv = ScrollView(ctx)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(78))
        lp.setMargins(dp(2), dp(2), dp(2), dp(0))
        sv.layoutParams = lp
        try { sv.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val cs = setRun?.counts
        val ws = checkWarn
        for ((i, r) in pairRules.withIndex()) {
            val line = LinearLayout(ctx)
            line.orientation = LinearLayout.HORIZONTAL
            line.gravity = Gravity.CENTER_VERTICAL
            val t = TextView(ctx)
            val warned = ws != null && ws.any { it.rule == i + 1 }
            t.text = (if (warned) "\u26A0 " else "") + ReplacePairList.label(r, if (cs != null && i < cs.size) cs[i] else 0, 60)
            t.textSize = 13f
            t.maxLines = 1
            t.ellipsize = android.text.TextUtils.TruncateAt.END
            t.gravity = Gravity.CENTER_VERTICAL
            t.setTextColor(try { JabraTokens.cardText(prefs) } catch (_: Throwable) { Color.BLACK })
            t.setPadding(dp(10), 0, dp(4), 0)
            t.layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f)
            t.isClickable = true
            t.setOnClickListener { try { host.feedback(t) } catch (_: Throwable) { }; try { editPair(i) } catch (_: Throwable) { } }
            line.addView(t)
            line.addView(small("\u2715", 30) { removePair(i) })
            col.addView(line)
        }
        sv.addView(col)
        return sv
    }

    // ------------------------------------------------------------------ v0.41.1: PAIRS VIEW (typing == 8): see every pair, edit it in place, remove one, clear all
    /** Edit: the pair goes into Find / With (with its mode) but STAYS in the list; "Update" writes it back to the same place. */
    private fun editPairHere(i: Int) {
        val r = pairRules.getOrNull(i) ?: return
        find = r.find.take(ReplacePads.FIELD_MAX)
        repl = r.with.take(ReplacePads.FIELD_MAX)
        opt.mode = if (r.kind == ReplaceRules.K_REGEX) ReplaceEngine.MODE_REGEX else if (r.word) ReplaceEngine.MODE_WORD else ReplaceEngine.MODE_TEXT
        opt.keepCase = r.keep; opt.list = false; opt.smart = r.smart
        saveOpt()
        caretPos = find.length
        typing = 0; confirmAll = false; clearAsk = false; step = 0
        editIdx = i
        msg = "Editing pair " + (i + 1) + ": change it, then tap Update"
        refresh()
    }

    /** Clear all from the Pairs view: 2 or more pairs need a second tap. */
    private fun pairsClear() {
        if (pairRules.size >= 2 && !clearAsk) { clearAsk = true; refresh(); return }
        dropSet()
        typing = 0
        msg = "Pair list cleared"
        refresh()
    }

    private fun pairsView(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)
        root.setPadding(dp(2), dp(4), dp(2), dp(4))
        val back: () -> Unit = { typing = 0; clearAsk = false; refresh() }
        val clearLabel = if (clearAsk) "Sure? Clear" else "Clear"
        val title = "Pairs (" + pairRules.size + ")"
        val acts: List<Pair<String, () -> Unit>> = if (pairRules.isEmpty()) emptyList() else listOf(Pair(clearLabel, { pairsClear() }))
        var newHead: View? = null
        if (newLook()) { try { newHead = PanelKit.header(ctx, prefs, host, title, "<", acts) { back() } } catch (_: Throwable) { newHead = null } }
        val headView: View = newHead ?: run {
            val h = row()
            h.addView(btn("\u2190 Back", 1f, 34) { back() })
            val tv = TextView(ctx)
            tv.text = title
            tv.textSize = 12f
            tv.setTextColor(dim())
            tv.gravity = Gravity.CENTER_VERTICAL
            tv.layoutParams = LinearLayout.LayoutParams(0, dp(34), 2f)
            h.addView(tv)
            if (pairRules.isNotEmpty()) h.addView(btn(clearLabel, 1.2f, 34) { pairsClear() })
            h
        }
        root.addView(headView)
        if (pairRules.isEmpty()) {
            val t = TextView(ctx)
            t.text = "No pairs yet.\nFind: the wrong forms, split by ;  (Lin Fen; Lin Fong; Lin Fan)\nWith: the right form (Lin Feng)\nThen tap +Pair."
            t.textSize = 13f
            t.setTextColor(dim())
            t.setPadding(dp(8), dp(8), dp(8), dp(8))
            root.addView(t)
            return root
        }
        if (novelCur.isNotEmpty()) {   // v0.56.0 (NV2): ONE clear action: keep these pairs for the active novel
            val sr = row()
            sr.addView(btn("Save to " + novelCur, 1f, 40) { saveToNovel() })
            root.addView(sr)
        } else root.addView(UiKit.noteText(ctx, prefs, "To keep pairs for a novel: Sets > pick a novel, then come back here."))
        root.addView(UiKit.noteText(ctx, prefs, "Edit = change a pair in Find / With. \u2715 = remove it."))
        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(170))
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val cs = setRun?.counts
        val ws = checkWarn
        val ink = try { JabraTokens.cardText(prefs) } catch (_: Throwable) { Color.BLACK }
        for ((i, r) in pairRules.withIndex()) {
            val line = LinearLayout(ctx)
            line.orientation = LinearLayout.HORIZONTAL
            line.gravity = Gravity.CENTER_VERTICAL
            val t = TextView(ctx)
            val warned = ws != null && ws.any { it.rule == i + 1 }
            t.text = (i + 1).toString() + ".  " + (if (warned) "\u26A0 " else "") + ReplacePairList.label(r, if (cs != null && i < cs.size) cs[i] else 0, 140)
            t.textSize = 13f
            t.maxLines = 2
            t.ellipsize = android.text.TextUtils.TruncateAt.END
            t.gravity = Gravity.CENTER_VERTICAL
            t.setTextColor(ink)
            t.setPadding(dp(8), dp(2), dp(4), dp(2))
            t.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            line.addView(t)
            line.addView(small("Edit", 36) { editPairHere(i) })
            line.addView(small("\u2715", 36) { removePair(i) })
            col.addView(line)
        }
        sv.addView(col)
        root.addView(sv)
        return root
    }

    // ------------------------------------------------------------------ v0.41.0: PASTE PAIRS, never-change list
    // Logic = typing/ReplacePaste.java + typing/ReplaceProtect.java (pure Java, tested). Smart matching = typing/ReplaceSmart.java (Opt.smart / "smart:" per pair).

    /** "+Paste" / "Paste": reads many pairs from the clipboard (one per line) and adds them to the pair list in one tap. Nothing is changed in the text yet. */
    private fun pastePairs() {
        try {
            confirmAll = false
            if (io.blocked()) { msg = "Not available in password fields."; refresh(); return }
            if (setRules != null && !pairMode) { msg = "A set is loaded. Tap \u2715 Set first, then paste pairs"; refresh(); return }
            val res = ReplacePaste.parse(io.clipText() ?: "", pairRules, ReplacePairList.MAX)
            if (res.changed() == 0) { msg = res.summary(); refresh(); return }
            if (!pairMode) {
                val o = opt.copy(); o.mode = ReplaceEngine.MODE_TEXT; o.list = false; o.keepCase = false; o.smart = false
                setOpt = o
            }
            pairRules.clear(); pairRules.addAll(res.rules)
            pairMode = true
            syncPairs()
            msg = res.summary()
        } catch (_: Throwable) { msg = "Could not paste pairs" }
        refresh()
    }

    private fun loadProtect() {
        try {
            protectList.clear()
            val cur = prefs.str("rp_novel_cur", "")   // v0.55.0 (NV1): the Never change list of the active novel (none = the general list rp_protect, as before)
            novelCur = if (cur.isNotEmpty() && NovelBook.has(novelNames(), cur)) cur else ""
            protectList.addAll(ReplaceProtect.parse(prefs.str(protectKey(), "")))
        } catch (_: Throwable) { protectList.clear() }
        protectAsk = false
        applyProtect()
    }

    private fun protectKey(): String = if (novelCur.isEmpty()) "rp_protect" else NovelBook.neverKey(novelCur)

    private fun saveProtect() { try { prefs.setStr(protectKey(), ReplaceProtect.text(protectList)) } catch (_: Throwable) { } }

    /** opt and setOpt read the list itself (the same object): a term added later is used at once. */
    private fun applyProtect() { try { opt.protect = protectList; setOpt.protect = protectList } catch (_: Throwable) { } }

    /** A saved set's options + the never-change list. */
    private fun dOpt(d: ReplaceStore.Def): ReplaceEngine.Opt { val o = d.opt(); o.protect = protectList; return o }

    private fun openProtect() { typing = 7; protectAsk = false; confirmAll = false; refresh() }

    /** The list changed: save it, and the running list / set must be checked and counted again. */
    private fun protectChanged() {
        protectAsk = false
        saveProtect(); applyProtect()
        setRun = null; confirmAll = false; checkAck = false; checkWarn = null; step = 0
    }

    private fun protectAdd(term: String?, emptyMsg: String) {
        try {
            val r = ReplaceProtect.add(protectList, term ?: "")
            if (r == ReplaceProtect.ADDED) { protectChanged(); msg = "Added. Replace will not change it" }
            else if (r == ReplaceProtect.DUPLICATE) msg = "Already on the list"
            else if (r == ReplaceProtect.FULL) msg = "The list is full (" + ReplaceProtect.MAX_TERMS + ")"
            else msg = emptyMsg
        } catch (_: Throwable) { msg = "Could not add" }
        refresh()
    }

    private fun protectAddLines(text: String?) {
        try {
            if (text.isNullOrBlank()) { msg = "Clipboard is empty"; refresh(); return }
            val n = ReplaceProtect.addAll(protectList, text)
            if (n[0] > 0) protectChanged()
            msg = (if (n[0] > 0) "Added " + n[0] else "Nothing new") + (if (n[1] > 0) "  \u00B7  " + n[1] + " already there" else "") + (if (n[2] > 0) "  \u00B7  " + n[2] + " not added: the list is full" else "")
        } catch (_: Throwable) { msg = "Could not add" }
        refresh()
    }

    private fun protectRemove(i: Int) {
        if (i in protectList.indices) { protectList.removeAt(i); protectChanged(); msg = "Removed" }
        refresh()
    }

    private fun protectClear() {
        if (protectList.size >= 2 && !protectAsk) { protectAsk = true; msg = "Tap again to clear all " + protectList.size + " words"; refresh(); return }
        protectList.clear(); protectChanged(); msg = "List cleared"
        refresh()
    }

    /** typing == 7: the never-change list. Add from Find / the selection / the clipboard (one word or name per line), x removes one. */
    private fun protectView(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)
        root.setPadding(dp(2), dp(4), dp(2), dp(4))
        val shown = msg
        msg = ""
        val back: () -> Unit = { typing = if (fnKeepBack) 10 else 0; fnKeepBack = false; protectAsk = false; refresh() }   // v0.58.0 (FX2): back to Fix terms when opened from there
        val title = ReplaceProtect.label(protectList.size) + (if (novelCur.isEmpty()) "" else " \u00B7 " + novelCur)   // v0.55.0 (NV1): the list belongs to the active novel
        val clearLabel = if (protectAsk) "Sure? Clear" else "Clear"
        val acts: List<Pair<String, () -> Unit>> = if (protectList.isEmpty()) emptyList() else listOf(Pair(clearLabel) { protectClear() })
        var newHead: View? = null
        if (newLook()) { try { newHead = PanelKit.header(ctx, prefs, host, title, "<", acts) { back() } } catch (_: Throwable) { newHead = null } }
        val headView: View = newHead ?: run {
            val h = row()
            h.addView(btn("\u2190 Back", 1f, 34) { back() })
            val hint = TextView(ctx)
            hint.text = title
            hint.textSize = 12f
            hint.setTextColor(dim())
            hint.gravity = Gravity.CENTER_VERTICAL
            hint.layoutParams = LinearLayout.LayoutParams(0, dp(34), 2f)
            h.addView(hint)
            if (protectList.isNotEmpty()) h.addView(btn(clearLabel, 1.2f, 34) { protectClear() })
            h
        }
        root.addView(headView)
        val ar = row()
        ar.addView(btn("+ Find", 1f, 38) { protectAdd(find, "Find is empty: type the word in Find first") })
        ar.addView(btn("+ Selection", 1.2f, 38) { protectAdd(try { io.selected() } catch (_: Throwable) { null }, "Nothing is selected") })
        ar.addView(btn("+ Clipboard", 1.3f, 38) { protectAddLines(try { io.clipText() } catch (_: Throwable) { null }) })
        root.addView(ar)
        root.addView(UiKit.noteText(ctx, prefs, if (shown.isNotEmpty()) shown else "Replace skips these words, even inside a longer match. Clipboard: one word per line."))
        if (protectList.isEmpty()) return root
        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(128))
        try { sv.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        for ((i, t0) in protectList.withIndex()) {
            val line = LinearLayout(ctx)
            line.orientation = LinearLayout.HORIZONTAL
            line.gravity = Gravity.CENTER_VERTICAL
            val t = TextView(ctx)
            t.text = t0
            t.textSize = 13f
            t.maxLines = 1
            t.ellipsize = android.text.TextUtils.TruncateAt.END
            t.gravity = Gravity.CENTER_VERTICAL
            t.setTextColor(try { JabraTokens.cardText(prefs) } catch (_: Throwable) { Color.BLACK })
            t.setPadding(dp(10), 0, dp(4), 0)
            t.layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f)
            line.addView(t)
            line.addView(small("\u2715", 30) { protectRemove(i) })
            col.addView(line)
        }
        sv.addView(col)
        root.addView(sv)
        return root
    }

    // ------------------------------------------------------------------ v0.28.0 (R3b): Formatter bridge + help view

    /** Copies the active set as Formatter rules text. In the Formatter: Rules > Rules from clipboard. Nothing is written to the Formatter itself. */
    private fun copyForFormatter() {
        try {
            val rl = setRules
            val text = if (rl == null) "" else ReplaceExtras.formatterText(setName, ReplaceRules.serialize(rl), setOpt.matchCase, setOpt.mode == ReplaceEngine.MODE_WORD)
            msg = if (text.isEmpty()) "Nothing to copy" else if (io.setClip(text, null)) "Rules copied. Formatter > Rules > Rules from clipboard" else "Copy failed"
        } catch (_: Throwable) { msg = "Copy failed" }
        confirmAll = false
        refresh()
    }

    /** Loads help example [i] into Find / With and the mode, then shows the main view. */
    private fun useExample(i: Int) {
        val e = ReplaceHelp.example(i) ?: return
        find = e.find.take(ReplacePads.FIELD_MAX)
        repl = e.with.take(ReplacePads.FIELD_MAX)
        opt.mode = e.mode
        opt.matchCase = e.matchCase
        if (!pairMode) dropSet()   // v0.40.0: a pair list stays while an example is only put into Find / With
        usedSel = false; typing = 0; confirmAll = false; step = 0
        msg = "Example loaded"
        readSource(false)
        refresh()
    }

    private fun helpView(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)
        root.setPadding(dp(2), dp(4), dp(2), dp(4))
        val back: () -> Unit = { typing = 0; refresh() }
        val h = row()
        h.addView(btn("\u2190 Back", 1f, 34) { back() })
        for (i in ReplaceHelp.PAGES.indices) {
            val on = i == ReplaceHelp.sanitizePage(helpPage)
            h.addView(btn((if (on) "\u2713 " else "") + ReplaceHelp.PAGES[i], 1f, 34) { helpPage = i; refresh() })
        }
        root.addView(h)
        try {   // v0.38.1 (P2): look switch (pref rp_mini)
            val lk = row()
            lk.addView(UiKit.chip(ctx, prefs, if (miniOn()) "\u2713 Minimal look (tap = previous look)" else "Previous look (tap = minimal look)", miniOn(), 34, { v -> host.feedback(v) }) { prefs.setBool("rp_mini", !miniOn()); refresh() })
            root.addView(lk)
        } catch (_: Throwable) { }
        try {   // v0.42.2 (M2): slim look switch (pref rp_slim, default OFF until M4)
            val sl = row()
            sl.addView(UiKit.chip(ctx, prefs, if (ReplaceSlim.on(prefs)) "\u2713 Slim look: ON (tap = off)" else "Slim look: OFF (tap = on)", ReplaceSlim.on(prefs), 34, { v -> host.feedback(v) }) {
                prefs.setBool("rp_slim", !ReplaceSlim.on(prefs)); slimFail = false; refresh()
            })
            root.addView(sl)
        } catch (_: Throwable) { }
        try {   // v0.39.1 (FLOAT F2): test switch of the float preview card (pref rp_float_view, default OFF)
            val fl = row()
            fl.addView(UiKit.chip(ctx, prefs, if (floatOn()) "\u2713 Float preview, test (tap = off)" else "Float preview, test (tap = on)", floatOn(), 34, { v -> host.feedback(v) }) { prefs.setBool("rp_float_view", !floatOn()); refresh() })
            root.addView(fl)
        } catch (_: Throwable) { }
        try {   // v0.39.3 (FLOAT F4): switch of the floating preview window (pref rp_float, default OFF)
            val fw = row()
            fw.addView(UiKit.chip(ctx, prefs, if (floatSwitch()) "\u2713 Floating preview window (tap = off)" else "Floating preview window (tap = on)", floatSwitch(), 34, { v -> host.feedback(v) }) {
                prefs.setBool("rp_float", !floatSwitch()); fwinFail = false
                if (floatSwitch()) { prefs.setBool("rp_float_view", false); prefs.setInt("rp_float_state", ReplaceFloat.OPEN) }   // v0.39.5: floating ON = inline test card OFF, window opens
                if (!floatSwitch()) { try { fwin?.hide() } catch (_: Throwable) { } }
                refresh()
            })
            root.addView(fw)
        } catch (_: Throwable) { }
        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(170))
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val page = ReplaceHelp.sanitizePage(helpPage)
        val intro = TextView(ctx)
        intro.text = ReplaceHelp.pageText(page)
        intro.textSize = 13f
        intro.setTextColor(dim())
        intro.setPadding(dp(8), dp(6), dp(8), dp(6))
        col.addView(intro)
        if (page == ReplaceHelp.P_EXAMPLES) {
            for (i in 0 until ReplaceHelp.exampleCount()) {
                val e = ReplaceHelp.example(i) ?: continue
                val r = row()
                r.addView(btn(e.lang + ": " + e.find + " \u2192 " + e.with, 1f, 40) { useExample(i) })
                col.addView(r)
                val t = TextView(ctx)
                t.text = e.what + "\n" + e.input + "\n\u2192 " + e.expected
                t.textSize = 12f
                t.setTextColor(dim())
                t.setPadding(dp(8), dp(0), dp(8), dp(6))
                col.addView(t)
            }
        }
        sv.addView(col)
        root.addView(sv)
        return root
    }

    // ------------------------------------------------------------------ Recent pairs (v0.26.0 R3, step 3f)

    private fun recordPair() {
        try {
            if (!RecentPairs.shouldRecord(prefs.bool(RecentPairs.PREF_ON, false), io.blocked(), find, setRules != null)) return
            val list = RecentPairs.add(RecentPairs.parse(prefs.str(RecentPairs.PREF_LIST, "")), find, repl)
            prefs.setStr(RecentPairs.PREF_LIST, RecentPairs.serialize(list))
        } catch (_: Throwable) { }
    }

    private fun usePair(p: RecentPairs.Pair) {
        find = p.find.take(ReplacePads.FIELD_MAX)
        repl = p.with.take(ReplacePads.FIELD_MAX)
        if (!pairMode) dropSet()   // v0.40.0: a pair list stays while a recent pair is only put into Find / With
        usedSel = false; typing = 0; confirmAll = false; rprAsk = false; step = 0
        msg = "Pair loaded"
        readSource(false)
        refresh()
    }

    private fun recentView(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)
        root.setPadding(dp(2), dp(4), dp(2), dp(4))
        val on = prefs.bool(RecentPairs.PREF_ON, false)
        val list = if (on) RecentPairs.parse(prefs.str(RecentPairs.PREF_LIST, "")) else ArrayList<RecentPairs.Pair>()
        val clear: () -> Unit = {
            if (!rprAsk) { rprAsk = true; refresh() }
            else { rprAsk = false; prefs.setStr(RecentPairs.PREF_LIST, ""); refresh() }
        }
        val clearLabel = if (rprAsk) "Sure? Clear" else "Clear"
        val back: () -> Unit = { typing = 0; rprAsk = false; refresh() }
        val acts: List<Pair<String, () -> Unit>> = if (list.isEmpty()) emptyList() else listOf(Pair(clearLabel, clear))
        var newHead: View? = null
        if (newLook()) { try { newHead = PanelKit.header(ctx, prefs, host, "Recent pairs", "<", acts) { back() } } catch (_: Throwable) { newHead = null } }
        val headView: View = newHead ?: run {
            val h = row()
            h.addView(btn("\u2190 Back", 1f, 34) { back() })
            val hint = TextView(ctx)
            hint.text = "Recent pairs"
            hint.textSize = 12f
            hint.setTextColor(dim())
            hint.gravity = Gravity.CENTER_VERTICAL
            hint.layoutParams = LinearLayout.LayoutParams(0, dp(34), 2f)
            h.addView(hint)
            if (list.isNotEmpty()) h.addView(btn(clearLabel, 1.2f, 34) { clear() })
            h
        }
        root.addView(headView)
        if (list.isEmpty()) {
            val t = TextView(ctx)
            t.text = RecentPairs.emptyText(on)
            t.textSize = 13f
            t.setTextColor(dim())
            t.setPadding(dp(8), dp(8), dp(8), dp(8))
            root.addView(t)
            return root
        }
        root.addView(UiKit.noteText(ctx, prefs, "Tap a pair to fill Find and With."))
        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(170))
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        for (p in list) {
            val r = row()
            r.addView(btn(RecentPairs.label(p, 18), 1f, 40) { usePair(p) })
            col.addView(r)
        }
        sv.addView(col)
        root.addView(sv)
        return root
    }

    // ------------------------------------------------------------------ typing view (small keyboard)

    private fun cur(): String = if (typing == 1) find else if (typing == 12) fnQuery else repl   // v0.57.0 (FX1): 12 = the search box of Fix names
    private fun setCur(v: String) { if (typing == 1) find = v else if (typing == 12) fnQuery = v else repl = v; confirmAll = false }

    private fun showValue() {
        val v = cur()
        valueTv?.text = (if (typing == 1) "Find: " else "With: ") + ReplacePads.view(v, caretPos, 60)   // v0.29.0: caret can move (surrogate-safe window)
    }

    private fun typeText(s: String) {
        val c = ReplacePads.clampCaret(cur(), caretPos)
        val n = ReplacePads.insertAt(cur(), c, s)
        if (n != cur()) { setCur(n); caretPos = c + s.length }
        showValue()
    }

    private fun backKey() {
        val c = ReplacePads.clampCaret(cur(), caretPos)
        val n = ReplacePads.backspaceAt(cur(), c)
        caretPos = c - (cur().length - n.length)
        setCur(n); showValue()
    }

    private fun typeTok(tok: String) {
        var v = ReplacePads.value(tok)
        if (shift && padPage == 0) { v = v.uppercase(); shift = false; fillKeys() }
        if ((v == "\\n" || v == "\\t") && opt.mode != ReplaceEngine.MODE_REGEX && !opt.escapes) { opt.escapes = true; saveOpt(); msg = "Option \\n turned ON (it makes \\n a new line)" }
        typeText(v)
    }

    private fun typeView(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        val head = row()
        val tv = TextView(ctx)
        tv.textSize = 15f
        tv.setTextColor(ink())
        tv.setPadding(dp(8), 0, dp(4), 0)
        tv.gravity = android.view.Gravity.CENTER_VERTICAL
        tv.maxLines = 1
        val lp = LinearLayout.LayoutParams(0, dp(34), 3.2f)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        tv.layoutParams = lp
        head.addView(tv)
        valueTv = tv
        if (miniOn()) { try { tv.background = UiKit.cardBackground(ctx, prefs, false, false); tv.setPadding(dp(10), 0, dp(6), 0) } catch (_: Throwable) { } }   // v0.38.1 (P2): the typed text sits in a card
        head.addView(btn("Clear", 1f, 34) { setCur(""); caretPos = 0; showValue() })
        head.addView(btn(if (typing == 1) "Next: With" else "Next: Find", 1.4f, 34) { typing = if (typing == 1) 2 else 1; padPage = 0; shift = false; caretPos = cur().length; refresh() })
        root.addView(head)
        showValue()

        // v0.38.0 (P1): paste keys. Paste = latest copy at the cursor; hold Paste or tap the arrow = pick from Recent / Pinned; Sel = the selected text of the field; hold on the Find / With line = paste
        try {
            val pr = row()
            val pb = btn("Paste", 1.6f, 32) { pasteCur(latestCopy()) }
            if (miniOn()) accentButton(pb, JabraTokens.ACCENT)   // v0.38.1 (P2): Paste = the one filled key
            try { pb.setOnLongClickListener { v -> try { host.feedback(v) } catch (_: Throwable) { }; openPicker(); true } } catch (_: Throwable) { }
            pr.addView(pb)
            pr.addView(btn("\u25BE", 0.7f, 32) { openPicker() })
            pr.addView(btn("Sel", 1f, 32) { pasteSel() })
            root.addView(pr)
            tv.setOnLongClickListener { v -> try { host.feedback(v) } catch (_: Throwable) { }; pasteCur(latestCopy()); true }
        } catch (_: Throwable) { }

        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        root.addView(box)
        keysBox = box
        fillKeys()
        return root
    }

    // ------------------------------------------------------------------ v0.38.0 (P1): paste into Find / With

    private fun note(s: String) { try { android.widget.Toast.makeText(ctx, s, android.widget.Toast.LENGTH_SHORT).show() } catch (_: Throwable) { } }

    private fun latestCopy(): String = try { io.clipText() ?: "" } catch (_: Throwable) { "" }

    /** Puts [raw] at the cursor of the field that is open (Find / With), cut to the room that is left (FIELD_MAX). The shown line is updated in place. */
    private fun pasteCur(raw: String) {
        val room = ReplacePads.FIELD_MAX - cur().length
        val t = QuickPaste.fit(raw, room)
        if (t.isEmpty()) { note(QuickPaste.noneText(raw, room)); return }
        if (QuickPaste.wasCut(raw, room)) note(QuickPaste.doneText(t.length, true))
        typeText(t)
    }

    private fun pasteSel() {
        val s = try { io.selected() } catch (_: Throwable) { null }
        if (s.isNullOrEmpty()) { note("Nothing is selected in the field"); return }
        pasteCur(s)
    }

    private fun openPicker() { pasteBack = if (typing == 2) 2 else if (typing == 12) 12 else 1; pasteTab = 0; typing = 6; refresh() }

    /** The paste list (typing == 6): Recent (latest copy first) / Pinned. A tap puts the text at the cursor and goes back to the typing view. */
    private fun replacePicker(): View {
        val all: List<ClipList.Item> = try { clipList?.invoke() ?: emptyList<ClipList.Item>() } catch (_: Throwable) { emptyList<ClipList.Item>() }
        return PasteKit.picker(ctx, prefs, pasteTab, PasteKit.recentTexts(all, latestCopy()), PasteKit.pinnedTexts(all),
            { l, w, h, c -> btn(l, w, h, c) }, { v -> paintBg(v) },
            { t -> pasteTab = t; refresh() },
            { t -> typing = pasteBack; pasteCur(t); refresh() },
            { typing = pasteBack; refresh() })
    }

    private fun fillKeys() {
        val box = keysBox ?: return
        box.removeAllViews()
        for (ri in 0..3) {
            val r = row()
            if (padPage == 0 && ri == 3) r.addView(btn((if (shift) "\u2713" else "") + "Aa", 1.4f, 34) { shift = !shift; fillKeys() })
            for (tok in ReplacePads.tokens(padPage, ri)) {
                val lab = ReplacePads.label(tok)
                r.addView(btn(if (shift && padPage == 0) lab.uppercase() else lab, 1f, 34) { typeTok(tok) })
            }
            box.addView(r)
        }
        val bot = row()
        bot.addView(btn(ReplacePads.PAGE_NAMES[ReplacePads.next(padPage)], 1.3f, 34) { padPage = ReplacePads.next(padPage); shift = false; fillKeys() })
        bot.addView(btn("\u25C0", 0.8f, 34) { caretPos = ReplacePads.left(cur(), caretPos); showValue() })   // v0.29.0: caret left / right
        bot.addView(btn("\u25B6", 0.8f, 34) { caretPos = ReplacePads.right(cur(), caretPos); showValue() })
        bot.addView(btn("\u2423", 2.2f, 34) { typeText(" ") })
        bot.addView(btn("\u232B", 1.3f, 34) { backKey() })
        val doneB = btn("\u2713 Done", 1.4f, 34) { typing = if (fnBack) 10 else 0; fnBack = false; valueTv = null; keysBox = null; readSource(false); refresh() }   // v0.57.0 (FX1): Fix names gets you back
        if (miniOn()) accentButton(doneB, JabraTokens.ACCENT)   // v0.38.1 (P2)
        bot.addView(doneB)
        box.addView(bot)
    }
}
