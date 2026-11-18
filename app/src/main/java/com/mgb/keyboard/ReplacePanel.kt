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
import com.mgb.keyboard.typing.LookColors
import com.mgb.keyboard.typing.PanelLayout
import com.mgb.keyboard.typing.QuickPaste   // v0.38.0 (P1)
import com.mgb.keyboard.typing.RecentPairs
import com.mgb.keyboard.typing.ReplaceEngine
import com.mgb.keyboard.typing.ReplaceExtras
import com.mgb.keyboard.typing.ReplaceFloat   // v0.39.1 (FLOAT F2)
import com.mgb.keyboard.typing.ReplaceHelp
import com.mgb.keyboard.typing.ReplacePads
import com.mgb.keyboard.typing.ReplacePeek
import com.mgb.keyboard.typing.ReplaceRules

/*
 * v0.24.0 (R1) ADVANCED FIND & REPLACE panel (route showPanel("replace"), toolbar item "rp", OFF until the user adds it in Edit toolbar).
 * Engine = typing/ReplaceEngine.java (pure Java, tested). Keys of the small keyboard = typing/ReplacePads.java.
 * Main view: Find / With boxes (tap = type; Sel = take the selected text; Clip = take the clipboard), options (Text | Word | Regex, Aa, Bangla-safe, \n),
 * live match count + preview of the first matches, [Replace all (N)] [1st] [In: Field / Selection] [Undo / Copy].
 * Typing view: an IME panel cannot hold a normal text box, so a small keyboard is drawn inside the panel (same idea as the clipboard search):
 * pages abc | #+= (with regex helper keys) | Bangla consonants | Bangla vowels, signs, digits. Typing only updates one text line (no rebuild per key).
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
    // v0.38.0 (P1): paste keys in the typing view. typing == 6 = the paste list (Recent / Pinned); pasteBack = the field (1 Find / 2 With) to return to
    var clipList: (() -> List<ClipList.Item>)? = null          // set by MgbIme: the clipboard history (null = the list shows only the latest copy)
    private var pasteBack = 1
    private var pasteTab = 0
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
        optsOpen = false   // v0.38.1 (P2)
        setPreviewOn = false; setPrev = null; part = 0; partCut = -1; clipIo = null; msg = ""; confirmAll = false; undoStack.clear(); helpPage = 0; typing = 0; padPage = 0; shift = false; step = 0; rprAsk = false
        try { opt = ReplaceEngine.unpack(prefs.int("rp_opt", ReplaceEngine.pack(ReplaceEngine.Opt()))) } catch (_: Throwable) { opt = ReplaceEngine.Opt() }
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
        if (io.blocked()) ReplacePeek.message(name, "Not available in password fields.") else ReplacePeek.forSet(name, d.rules, d.opt(), src)

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
        val rules = setRules
        if (rules != null) {   // v0.25.0 (R2): a set is active
            val r = ReplaceEngine.Result()
            try {
                val rr = ReplaceRules.run(src, rules, setOpt)
                setRun = rr
                r.out = rr.out; r.count = rr.changed; r.changed = rr.changed
            } catch (_: Throwable) { setRun = null }
            return r
        }
        return try { ReplaceEngine.replaceAll(src, find, repl, opt) } catch (_: Throwable) { ReplaceEngine.Result() }
    }

    fun build(): View? = try { PeekPopup.dismiss(); if (typing == 3) setsViewPick() else if (typing == 4) recentView() else if (typing == 5) helpView() else if (typing == 6) replacePicker() else if (typing != 0) typeView() else mainViewPick() } catch (_: Throwable) { null }   // v0.26.0 (R3): 4 = Recent pairs; Pick = new look when panel_ui = New, else the classic view

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
            rs.addView(btn("\u2715 Set", 1f, 36) { setRules = null; setName = null; setRun = null; confirmAll = false; refresh() })
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
            r3.addView(btn((if (opt.bangla) "\u2713 " else "") + "\u09AC\u09BE\u0982", 0.9f, 32) { opt.bangla = !opt.bangla; optChanged() })
            r3.addView(btn((if (opt.escapes) "\u2713 " else "") + "\\n", 0.8f, 32) { opt.escapes = !opt.escapes; optChanged() })
        }
        root.addView(r3)

        // v0.25.0 (R2): sets + step through the matches
        val r4 = row()
        r4.addView(btn("Sets", 1f, 32) { typing = 3; confirmAll = false; refresh() })
        r4.addView(btn("Recent", 1f, 32) { typing = 4; confirmAll = false; rprAsk = false; refresh() })   // v0.26.0 (R3)
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

    private fun previewText(res: ReplaceEngine.Result): String {
        val rules = setRules
        if (rules != null) {   // v0.25.0 (R2): one line per rule that changed something
            val rr = setRun ?: return "(no result)"
            val sb = StringBuilder()
            var n = 0
            for (i in rules.indices) {
                if (i >= rr.counts.size || rr.counts[i] == 0) continue
                if (n >= 6) { sb.append("\n\u2026"); break }
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
            if (n >= 6) break
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
        if (setRules == null && find.isEmpty()) { msg = "Type what to find first"; refresh(); return }
        val e = r.err
        if (e != null) { msg = e; refresh(); return }
        if (r.count == 0) { msg = "No match"; refresh(); return }
        if (r.changed == 0 || r.out == src) { msg = "No change"; refresh(); return }
        if (!usedSel && !first && !confirmAll) { confirmAll = true; refresh(); return }
        doReplace(r)
    }

    private fun doReplace(r: ReplaceEngine.Result) {
        val before = src
        val partial = part != 0 && !usedSel
        val fullBefore = fullText
        val fullAfter = if (partial) composeFull(r.out) else r.out
        var ok = false
        if (!usedSel && r.edits.isNotEmpty()) {   // v0.37.1: match by match first (the text between the matches is never rewritten = its format stays; also works in apps that cannot "select all")
            try { ok = io.replaceEdits(0, fullBefore, fullAfter, if (part == 1) com.mgb.keyboard.typing.ReplaceEdits.shift(r.edits, partCut.coerceIn(0, fullText.length)) else r.edits) } catch (_: Throwable) { ok = false }
        }
        if (!ok) ok = try { if (usedSel) io.replaceSelection(r.out) else io.replaceAll(fullAfter) } catch (_: Throwable) { false }
        confirmAll = false
        if (ok && partial && part == 2) partCut = r.out.length   // the piece before the cursor now has the new length
        if (ok) { undoStack.push(if (usedSel) r.out else fullAfter, if (usedSel) before else fullBefore); msg = "Replaced " + r.changed; recordPair(); readSource(false) } else msg = "Could not replace in this app. Use Copy, then paste"   // v0.26.0 (R3): recordPair = only when Recent pairs is ON
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
        try { for ((n, d) in ReplaceStore.load(prefs)) all.add(Pair("\u2605 " + n, d)) } catch (_: Throwable) { }
        try { for ((n, d) in ReplaceStore.ready()) all.add(Pair(n, d)) } catch (_: Throwable) { }
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
        tv.text = ReplaceExtras.setPreview(sp.first, sp.second.rules, sp.second.opt(), src)
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
        setRules = p.rules; setOpt = d.opt(); setName = name; setRun = null; setPrev = null
        typing = 0; confirmAll = false; step = 0
        msg = "Set loaded: " + name + (if (p.bad.isNotEmpty()) " (" + p.bad.size + " unreadable lines skipped)" else "")
        readSource(false)
        refresh()
    }

    // ------------------------------------------------------------------ v0.26.0 (R3): NEW LOOK (pref panel_ui = 1), Recent pairs view

    private fun newLook(): Boolean = try { PanelLayout.isNewStyle(prefs.int("panel_ui", 0)) } catch (_: Throwable) { false }

    /** New look when panel_ui = New; any problem = the classic main view (pattern HistCard.buildModern / buildClassic). */
    private fun mainViewPick(): View {
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
                if (n >= 6) { sb.append("\n\u2026"); break }
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
            if (n >= 6) break
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
                listOf(Pair("\u2715 Set") { setRules = null; setName = null; setRun = null; confirmAll = false; refresh() }, Pair("To Formatter") { copyForFormatter() })))   // v0.28.0 (R3b): Formatter bridge
        } else {
            // Text | Word | Regex
            root.addView(PanelKit.segmented(ctx, prefs, host, ReplaceEngine.MODE_NAMES.toList(), opt.mode) { i -> opt.mode = i; optChanged() })
            // option chips
            val oc = row()
            oc.addView(UiKit.chip(ctx, prefs, "Aa", opt.matchCase, 36, { v -> host.feedback(v) }) { opt.matchCase = !opt.matchCase; optChanged() })
            oc.addView(UiKit.chip(ctx, prefs, "Keep", opt.keepCase, 36, { v -> host.feedback(v) }) { opt.keepCase = !opt.keepCase; if (opt.keepCase) opt.matchCase = false; optChanged() })   // v0.29.0
            if (opt.mode != ReplaceEngine.MODE_REGEX) {
                oc.addView(UiKit.chip(ctx, prefs, "List", opt.list, 36, { v -> host.feedback(v) }) { opt.list = !opt.list; optChanged() })   // v0.31.0
                oc.addView(UiKit.chip(ctx, prefs, "\u09AC\u09BE\u0982", opt.bangla, 36, { v -> host.feedback(v) }) { opt.bangla = !opt.bangla; optChanged() })
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

    private fun floatCard(res: ReplaceEngine.Result, shown: String): View? {
        val setMode = setRules != null
        val lines: List<String>? = if (setMode && res.changed > 0) previewText(res).split("\n") else if (setMode) ArrayList<String>() else null
        val nHits = res.hits.size
        if (nHits > 0) step = step.coerceIn(0, nHits - 1)
        val snap = ReplaceFloat.snap(src, res, step, setMode, lines)
        val tab = ReplaceFloat.sanitizeTab(prefs.int("rp_float_tab", 0))
        val replaced = shown.startsWith("Replaced")
        val a = ReplaceFloatView.Actions(
            onTab = { t -> prefs.setInt("rp_float_tab", ReplaceFloat.sanitizeTab(t)); refresh() },
            onPrev = { step = ReplaceFloat.step(step, nHits, -1); showStep(res) },
            onNext = { step = ReplaceFloat.step(step, nHits, 1); showStep(res) },
            onReplace = { onReplace(ReplaceEngine.replaceNth(src, find, repl, opt, step + 1), true) },
            onUndo = if (replaced && undoStack.canUndo()) ({ undo() }) else null,
            notice = if (replaced) shown else ""
        )
        return ReplaceFloatView(ctx, prefs) { v -> host.feedback(v) }.window(snap, tab, a)
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

        val fcard: View? = if (floatOn()) (try { floatCard(res, shown) } catch (_: Throwable) { null }) else null   // v0.39.1 (FLOAT F2): hidden test switch; null = the old preview + step row below

        // 1) top bar: [ABC] [Sets] [Recent] [?] ........ pill
        val top = row()
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(small("ABC", 38) { host.showLetters() })
        top.addView(small("Sets", 38) { typing = 3; confirmAll = false; refresh() })
        top.addView(small("Recent", 38) { typing = 4; confirmAll = false; rprAsk = false; refresh() })
        top.addView(small("?", 38) { typing = 5; helpPage = 0; confirmAll = false; refresh() })
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

        // 2) mode row (Text | Word | Regex | Options) + the folded option chips
        if (setRules == null) {
            val mr = row()
            mr.gravity = Gravity.CENTER_VERTICAL
            for (i in 0..2) {
                val c = UiKit.chip(ctx, prefs, ReplaceEngine.MODE_NAMES[i], opt.mode == i, 34, { v -> host.feedback(v) }) { opt.mode = i; optChanged() }
                c.layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).also { it.setMargins(dp(2), dp(2), dp(2), dp(2)) }
                mr.addView(c)
            }
            val oc = UiKit.chip(ctx, prefs, if (optsOpen) "Options \u25B4" else "Options \u25BE", optsOpen, 34, { v -> host.feedback(v) }) { optsOpen = !optsOpen; refresh() }
            oc.layoutParams = LinearLayout.LayoutParams(0, dp(34), 1.5f).also { it.setMargins(dp(2), dp(2), dp(2), dp(2)) }
            mr.addView(oc)
            root.addView(mr)
            if (optsOpen) {
                val orow = row()
                orow.addView(UiKit.chip(ctx, prefs, "Aa", opt.matchCase, 34, { v -> host.feedback(v) }) { opt.matchCase = !opt.matchCase; optChanged() })
                orow.addView(UiKit.chip(ctx, prefs, "Keep", opt.keepCase, 34, { v -> host.feedback(v) }) { opt.keepCase = !opt.keepCase; if (opt.keepCase) opt.matchCase = false; optChanged() })
                if (opt.mode != ReplaceEngine.MODE_REGEX) {
                    orow.addView(UiKit.chip(ctx, prefs, "List", opt.list, 34, { v -> host.feedback(v) }) { opt.list = !opt.list; optChanged() })
                    orow.addView(UiKit.chip(ctx, prefs, "\u09AC\u09BE\u0982", opt.bangla, 34, { v -> host.feedback(v) }) { opt.bangla = !opt.bangla; optChanged() })
                    orow.addView(UiKit.chip(ctx, prefs, "\\n", opt.escapes, 34, { v -> host.feedback(v) }) { opt.escapes = !opt.escapes; optChanged() })
                }
                root.addView(orow)
            }
        }

        // 3) ONE card: Find + With (or the active set)
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(10), dp(2), dp(4), dp(2))
        try { card.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
        if (setRules != null) {
            card.addView(miniLine("Set", (setName ?: "") + " (" + nRules + ")", { typing = 3; refresh() },
                listOf(Pair("\u2715 Set") { setRules = null; setName = null; setRun = null; confirmAll = false; refresh() }, Pair("Fmt") { copyForFormatter() })))
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
        if (fcard != null) root.addView(fcard) else {
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
        return root
    }

    private fun setsViewNew(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)
        root.setPadding(dp(2), dp(4), dp(2), dp(4))
        root.addView(PanelKit.header(ctx, prefs, host, "Sets", "<", emptyList()) { typing = 0; refresh() })
        root.addView(UiKit.noteText(ctx, prefs, if (setPreviewOn) "Tap a set to preview it" else "Pick a set. Edit: app > Tools & Panels"))
        val pr = row()
        pr.addView(UiKit.chip(ctx, prefs, "Preview", setPreviewOn, 36, { v -> host.feedback(v) }) { setPreviewOn = !setPreviewOn; setPrev = null; refresh() })   // v0.31.0
        root.addView(pr)
        previewBox()?.let { root.addView(it) }
        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(if (setPrev != null) 100 else 170))
        val tiles = ArrayList<PanelKit.Tile>()
        try { for ((n, d) in ReplaceStore.load(prefs)) tiles.add(PanelKit.Tile("\u2605 " + n, "saved", n == setName) { tapSet(n, d) }.also { t -> t.onLong = { a -> peekSetAt(a, n, d) } }) } catch (_: Throwable) { }
        try { for ((n, d) in ReplaceStore.ready()) tiles.add(PanelKit.Tile(n, "ready-made", n == setName) { tapSet(n, d) }.also { t -> t.onLong = { a -> peekSetAt(a, n, d) } }) } catch (_: Throwable) { }
        sv.addView(PanelKit.tileGrid(ctx, prefs, host, 2, tiles))
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
        setRules = null; setName = null; setRun = null
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
        try {   // v0.39.1 (FLOAT F2): test switch of the float preview card (pref rp_float_view, default OFF)
            val fl = row()
            fl.addView(UiKit.chip(ctx, prefs, if (floatOn()) "\u2713 Float preview, test (tap = off)" else "Float preview, test (tap = on)", floatOn(), 34, { v -> host.feedback(v) }) { prefs.setBool("rp_float_view", !floatOn()); refresh() })
            root.addView(fl)
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
        setRules = null; setName = null; setRun = null
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

    private fun cur(): String = if (typing == 1) find else repl
    private fun setCur(v: String) { if (typing == 1) find = v else repl = v; confirmAll = false }

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

    private fun openPicker() { pasteBack = if (typing == 2) 2 else 1; pasteTab = 0; typing = 6; refresh() }

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
        val doneB = btn("\u2713 Done", 1.4f, 34) { typing = 0; valueTv = null; keysBox = null; readSource(false); refresh() }
        if (miniOn()) accentButton(doneB, JabraTokens.ACCENT)   // v0.38.1 (P2)
        bot.addView(doneB)
        box.addView(bot)
    }
}
