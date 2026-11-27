package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.typing.FixNames

/**
 * v0.62.0 (PROJECT): the ONLY screen next to Replace. It replaces the old "Fix terms" screen (Fix / Terms / Keep as is tabs, Sets source, live text box).
 * Replace stays the normal Find & Replace. Its top bar has a Project button. This screen is what that button opens:
 *  - Projects: a list + one "+ Create" (type a name, empty project, active at once), No project, Delete.
 *  - One project: Add box (one line, several wrong forms:  gaam; geme; gome => game), the term list (tap a row = edit it, X = delete, 2 taps),
 *    Whole words switch, Paste list (many lines from the clipboard), and a second tab Keep as is (names that are never changed).
 *  Everything is saved at once. Back = Replace with the project active; there "Fix with project" fixes the chapter in the field.
 * Typing codes of the Replace mini keyboard: 12 search, 13 new project name, 14 name to keep, 15 the pair line.
 * Built in try / catch by the caller; any error = the panel falls back to the Sets screen.
 */
class ProjectUi(private val ctx: Context, private val prefs: JabraPrefs, private val host: JabraHost, private val api: Api) {

    class NovelRow(val name: String, val pairs: Int, val locked: Int, val active: Boolean)

    interface Api {
        fun novel(): String
        fun text(): String                       // the text being fixed (field / selection)
        fun find(): String
        fun repl(): String
        fun query(): String
        fun protect(): List<String>              // Never change list of the active novel
        fun setNames(): List<String>
        fun setTexts(): List<String>             // same order as setNames
        fun type(which: Int)                     // 1 = wrong forms, 2 = right form, 12 = search, 13 = new novel name, 14 = name to keep
        fun addPair(forms: List<String>, to: String, topic: String): String
        fun fix(rules: List<com.mgb.keyboard.typing.ReplaceRules.Rule>): String
        fun undo()
        fun canUndo(): Boolean
        fun toast(): String
        fun back()
        fun novels(): List<NovelRow>
        fun pickNovel(name: String)
        fun deleteAsk(): Boolean
        fun deleteNovel()
        fun removeLock(term: String)
        fun refresh()
        fun clearFields()                        // empty Wrong + Correct (after a fix)
        fun loadPair(forms: String, to: String)  // put a term into Wrong + Correct
        fun fixOne(rule: com.mgb.keyboard.typing.ReplaceRules.Rule, forms: List<String>, to: String, save: Boolean): String  // fix + (optional) remember, one toast, fields emptied
        fun keepInput(): String                  // the name typed in the Keep as is box
        fun addKeep(name: String)                // add a name to Keep as is (typed or suggested)
        fun keepSelection()                      // v0.60.0: + Selection (from the old Never change screen)
        fun keepClipboard()                      // v0.60.0: + Clipboard, one name per line
        fun keepClear()                          // v0.60.0: Clear (second tap confirms)
        fun keepClearAsk(): Boolean
        fun pasteTerms()                         // v0.60.0: Paste list: lines "wrong;forms => right" from the clipboard are saved to the novel
        fun clearToast()
        fun newNovelInput(): String              // the name typed for a new novel
        fun addNovel(name: String): String       // create a new empty novel and make it active
        // v0.62.0 (PROJECT): one line "wrong; wrong => right" is typed (code 15), saved, edited in place, deleted
        fun pairInput(): String                  // the line typed in the Add box
        fun commitPair()                         // add (or save the edit of) the typed line
        fun clearPair()                          // empty the box, forget the edit
        fun editTerm(find: String, line: String) // open the typing view with this term's line; Save replaces it
        fun deleteTerm(find: String)             // remove one term from the project
        fun wholeWord(): Boolean
        fun setWholeWord(on: Boolean)
    }


    var tab = 1                                   // 1 = Terms, 2 = Keep as is
    private var sheet = 0                         // 0 = the project (or the list when none is active), 3 = the project list
    private var delAsk = ""                       // key of the term whose X was tapped once
    private var pendingFrom: String? = null       // set while a NEW project name is being typed: when the active project changes, open it

    fun open() { tab = 1; sheet = 0; delAsk = ""; pendingFrom = null }
    fun showNovels() { sheet = 3 }

    // ---------------------------------------------------------------- small widgets
    private fun dp(v: Int): Int = JabraTokens.dp(ctx, v)
    private fun ink(): Int = try { JabraTokens.panelText(prefs) } catch (_: Throwable) { Color.BLACK }
    private fun dim(): Int = try { JabraTokens.dimText(prefs) } catch (_: Throwable) { Color.DKGRAY }
    private fun fb(v: View) { try { host.feedback(v) } catch (_: Throwable) { } }
    private fun dark(): Boolean = try { val c = ink(); (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000 > 140 } catch (_: Throwable) { false }
    private fun redInk() = if (dark()) 0xFFEF7A74.toInt() else 0xFFB3261E.toInt()
    private fun strokeInk(): Int = if (dark()) 0xFF2B3A62.toInt() else 0xFFC4CBD9.toInt()
    private val acc: Int get() = JabraTokens.ACCENT
    private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

    private fun lp(w: Int, h: Int, weight: Float = 0f, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): LinearLayout.LayoutParams {
        val p = if (weight > 0f) LinearLayout.LayoutParams(w, h, weight) else LinearLayout.LayoutParams(w, h)
        p.setMargins(dp(l), dp(t), dp(r), dp(b))
        return p
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(ctx)
        t.text = text; t.textSize = size; t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    private fun pill(color: Int, radiusDp: Int = 10): GradientDrawable { val g = GradientDrawable(); g.setColor(color); g.cornerRadius = dp(radiusDp).toFloat(); return g }

    private fun outline(fill: Int, stroke: Int, radiusDp: Int = 10): GradientDrawable {
        val g = GradientDrawable(); g.setColor(fill); g.cornerRadius = dp(radiusDp).toFloat(); g.setStroke(dp(1), stroke); return g
    }

    private fun hint(text: String): TextView { val t = label(text, 12f, dim()); t.setPadding(dp(6), dp(4), dp(6), dp(4)); return t }

    private fun rowBox(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        try { r.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
        r.setPadding(dp(12), dp(6), dp(8), dp(6))
        r.layoutParams = lp(MATCH, WRAP, 0f, 0, 0, 0, 5)
        return r
    }

    private fun twoLines(top: String, sub: String): LinearLayout {
        val g = LinearLayout(ctx)
        g.orientation = LinearLayout.VERTICAL
        val a = label(top, 15f, ink(), true)
        a.maxLines = 1; a.ellipsize = TextUtils.TruncateAt.END
        g.addView(a)
        if (sub.isNotEmpty()) { val s = label(sub, 13f, dim()); s.maxLines = 1; s.ellipsize = TextUtils.TruncateAt.END; g.addView(s) }
        g.layoutParams = lp(0, WRAP, 1f)
        return g
    }

    /** A tap-to-type box: the real typing happens in the Replace mini keyboard (code = which). */
    private fun fieldBox(hint: String, value: String, which: Int): TextView {
        val t = TextView(ctx)
        t.text = if (value.isEmpty()) hint else value
        t.textSize = 15f
        t.setTextColor(if (value.isEmpty()) dim() else ink())
        t.maxLines = 1
        t.ellipsize = TextUtils.TruncateAt.END
        t.gravity = Gravity.CENTER_VERTICAL
        t.setPadding(dp(12), 0, dp(12), 0)
        t.background = outline(Color.TRANSPARENT, strokeInk(), 10)
        t.isClickable = true
        t.setOnClickListener { v -> fb(v); api.clearToast(); if (which == 13) pendingFrom = api.novel(); api.type(which) }
        return t
    }

    private fun scrollArea(baseDp: Int): ScrollView {
        val sv = ScrollView(ctx)
        sv.layoutParams = lp(MATCH, dp(baseDp), 0f, 0, 0, 0, 6)
        return sv
    }

    private fun header(title: String, onBack: () -> Unit, chip: View?): View {
        val h = UiKit.row(ctx)
        h.gravity = Gravity.CENTER_VERTICAL
        h.addView(UiKit.btn(ctx, prefs, "<", 0f, 36, JabraTokens.T_BTN, { v -> fb(v) }) { onBack() })
        if (chip != null) h.addView(chip, lp(WRAP, WRAP, 0f, 6, 0, 0, 0))
        else h.addView(label(title, 15f, ink(), true), lp(WRAP, WRAP, 0f, 6, 0, 0, 0))
        h.layoutParams = lp(MATCH, WRAP, 0f, 0, 0, 0, 6)
        return h
    }

    private fun segBtn(text: String, on: Boolean, click: () -> Unit): TextView {
        val t = TextView(ctx)
        t.text = text; t.textSize = 14f; t.gravity = Gravity.CENTER
        t.setTypeface(t.typeface, Typeface.BOLD)
        t.maxLines = 1; t.isClickable = true
        if (on) t.background = pill(acc, 10) else t.setBackgroundColor(Color.TRANSPARENT)
        t.setTextColor(if (on) Color.WHITE else dim())
        t.setOnClickListener { v -> fb(v); click() }
        t.layoutParams = lp(0, dp(36), 1f, 1, 0, 1, 0)
        return t
    }

    // ---------------------------------------------------------------- the screen
    fun build(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        try { JabraThemes.paint(prefs, root) } catch (_: Throwable) { root.setBackgroundColor(Color.parseColor("#DDDDDD")) }
        root.setPadding(dp(4), dp(4), dp(4), dp(4))
        val novel = api.novel()
        val pf = pendingFrom
        if (pf != null && novel.isNotEmpty() && !novel.equals(pf, true)) { sheet = 0; tab = 1; pendingFrom = null }
        if (sheet == 3 || novel.isEmpty()) listScreen(root, novel) else projectScreen(root, novel)
        return root
    }

    // ---------------------------------------------------------------- projects list
    private fun listScreen(root: LinearLayout, novel: String) {
        root.addView(header("Projects", { if (novel.isEmpty()) api.back() else { sheet = 0; api.refresh() } }, null))

        // create: type a name, tap Create (empty project, active at once)
        val nb = UiKit.row(ctx)
        nb.gravity = Gravity.CENTER_VERTICAL
        val nf = fieldBox("New project name", api.newNovelInput(), 13)
        nf.layoutParams = lp(0, dp(42), 1f, 2, 0, 4, 0)
        nb.addView(nf)
        nb.addView(UiKit.btn(ctx, prefs, "+ Create", 0f, 42, JabraTokens.T_BTN, { v -> fb(v) }) {
            val nn = api.newNovelInput().trim()
            if (nn.isEmpty()) { pendingFrom = novel; api.type(13) } else { sheet = 0; tab = 1; pendingFrom = null; api.addNovel(nn) }
        })
        nb.layoutParams = lp(MATCH, WRAP, 0f, 0, 0, 0, 6)
        root.addView(nb)

        val sv = scrollArea(100)
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        val list = api.novels()
        for (n in list) {
            val row = rowBox()
            if (n.active) { try { row.background = outline(if (dark()) 0xFF1B2745.toInt() else 0xFFEAF1FB.toInt(), acc, 10) } catch (_: Throwable) { } }
            val dot = View(ctx)
            dot.background = if (n.active) pill(acc, 6) else outline(Color.TRANSPARENT, dim(), 6)
            row.addView(dot, lp(dp(12), dp(12), 0f, 0, 0, 8, 0))
            row.addView(twoLines(n.name, n.pairs.toString() + (if (n.pairs == 1) " term" else " terms") + (if (n.locked > 0) " \u00B7 " + n.locked + " kept" else "")))
            row.isClickable = true
            row.setOnClickListener { v -> fb(v); sheet = 0; tab = 1; delAsk = ""; pendingFrom = null; api.pickNovel(n.name) }
            box.addView(row)
        }
        if (list.isEmpty()) box.addView(hint("No project yet. Type a name above and tap Create."))
        if (novel.isNotEmpty()) {
            val del = UiKit.btn(ctx, prefs, if (api.deleteAsk()) "Sure? Delete " + novel else "Delete " + novel, 0f, 36, JabraTokens.T_BTN, { v -> fb(v) }) { api.deleteNovel() }
            del.setTextColor(redInk())
            box.addView(del, lp(WRAP, WRAP, 0f, 0, 4, 0, 0))
            box.addView(UiKit.btn(ctx, prefs, "No project", 0f, 36, JabraTokens.T_BTN, { v -> fb(v) }) { sheet = 0; pendingFrom = null; api.pickNovel("") }, lp(WRAP, WRAP, 0f, 0, 4, 0, 0))
        }
        if (api.toast().isNotEmpty()) box.addView(hint(api.toast().substringBefore(". Tap Undo")))
        sv.addView(box)
        root.addView(sv)
    }

    // ---------------------------------------------------------------- one project
    private fun projectScreen(root: LinearLayout, novel: String) {
        val shortName = if (novel.length > 18) novel.substring(0, 17) + "\u2026" else novel
        root.addView(header("", { api.back() }, UiKit.chip(ctx, prefs, shortName + "  \u25BE", true, 34, { v -> fb(v) }) { sheet = 3; api.clearToast(); api.refresh() }))

        val rows = try { FixNames.rows(novel, api.setNames(), api.setTexts()) } catch (_: Throwable) { ArrayList<FixNames.Row>() }
        val protect = api.protect()

        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        try { bar.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
        bar.setPadding(dp(3), dp(3), dp(3), dp(3))
        bar.layoutParams = lp(MATCH, WRAP, 0f, 2, 0, 2, 6)
        bar.addView(segBtn("Terms  " + rows.size, tab == 1) { tab = 1; delAsk = ""; api.clearToast(); api.refresh() })
        bar.addView(segBtn("Keep as is  " + protect.size, tab == 2) { tab = 2; api.clearToast(); api.refresh() })
        root.addView(bar)

        if (tab == 2) keepTab(root, protect) else termsTab(root, rows)
        val t = api.toast().substringBefore(". Tap Undo")
        if (t.isNotEmpty()) root.addView(hint(t))
    }

    private fun termsTab(root: LinearLayout, rows: List<FixNames.Row>) {
        val editing = api.pairInput().isNotEmpty()
        val bar = UiKit.row(ctx)
        bar.gravity = Gravity.CENTER_VERTICAL
        val pf = fieldBox("wrong; wrong => right", api.pairInput(), 15)
        pf.layoutParams = lp(0, dp(42), 1f, 2, 0, 4, 0)
        bar.addView(pf)
        bar.addView(UiKit.btn(ctx, prefs, if (editing) "Save" else "+ Add", 0f, 42, JabraTokens.T_BTN, { v -> fb(v) }) {
            if (api.pairInput().trim().isEmpty()) api.type(15) else api.commitPair()
        })
        if (editing) bar.addView(UiKit.btn(ctx, prefs, "\u2715", 0f, 42, JabraTokens.T_BTN, { v -> fb(v) }) { api.clearPair() })
        bar.layoutParams = lp(MATCH, WRAP, 0f, 0, 0, 0, 4)
        root.addView(bar)

        val opts = UiKit.row(ctx)
        opts.gravity = Gravity.CENTER_VERTICAL
        opts.addView(UiKit.chip(ctx, prefs, if (api.wholeWord()) "\u2713 Whole words" else "Whole words", api.wholeWord(), 34, { v -> fb(v) }) { api.setWholeWord(!api.wholeWord()) })
        opts.addView(UiKit.chip(ctx, prefs, "Paste list", false, 34, { v -> fb(v) }) { api.clearToast(); api.pasteTerms() })
        val sb = fieldBox("Search", api.query(), 12)
        sb.layoutParams = lp(0, dp(34), 1f, 4, 0, 2, 0)
        opts.addView(sb)
        opts.layoutParams = lp(MATCH, WRAP, 0f, 0, 0, 0, 4)
        root.addView(opts)

        val sv = scrollArea(72)
        val list = LinearLayout(ctx)
        list.orientation = LinearLayout.VERTICAL
        val q = api.query().trim().lowercase()
        val shown = rows.filter { r -> q.isEmpty() || (r.formsText() + " " + r.to).lowercase().indexOf(q) >= 0 }
        for (r in shown) {
            val key = r.key()
            val row = rowBox()
            val mid = twoLines(r.to, "\u2190 " + r.formsText())
            mid.isClickable = true
            mid.setOnClickListener { v -> fb(v); delAsk = ""; api.editTerm(r.rule.find, r.formsText() + " => " + r.to) }
            row.addView(mid)
            val ask = delAsk == key
            val x = UiKit.btn(ctx, prefs, if (ask) "Delete?" else "\u2715", 0f, 34, JabraTokens.T_BTN, { v -> fb(v) }) {
                if (ask) { delAsk = ""; api.deleteTerm(r.rule.find) } else { delAsk = key; api.refresh() }
            }
            if (ask) x.setTextColor(redInk())
            row.addView(x, lp(WRAP, dp(34)))
            list.addView(row)
        }
        if (shown.isEmpty()) list.addView(hint(if (q.isNotEmpty()) "No match." else "No terms yet. Type a pair above, like  gaam; geme; gome => game  and tap + Add."))
        else list.addView(hint("Tap a term to edit it. \u2715 deletes (tap twice)."))
        sv.addView(list)
        root.addView(sv)
    }

    private fun keepTab(root: LinearLayout, protect: List<String>) {
        val bar = UiKit.row(ctx)
        bar.gravity = Gravity.CENTER_VERTICAL
        val kb = fieldBox("Name to keep as is", api.keepInput(), 14)
        kb.layoutParams = lp(0, dp(42), 1f, 2, 0, 4, 0)
        bar.addView(kb)
        bar.addView(UiKit.btn(ctx, prefs, "Add", 0f, 42, JabraTokens.T_BTN, { v -> fb(v) }) {
            val n = api.keepInput().trim()
            if (n.isEmpty()) api.type(14) else api.addKeep(n)
        })
        bar.layoutParams = lp(MATCH, WRAP, 0f, 0, 0, 0, 4)
        root.addView(bar)

        val more = UiKit.row(ctx)
        more.gravity = Gravity.CENTER_VERTICAL
        more.addView(UiKit.chip(ctx, prefs, "+ Selection", false, 34, { v -> fb(v) }) { api.clearToast(); api.keepSelection() })
        more.addView(UiKit.chip(ctx, prefs, "+ Clipboard", false, 34, { v -> fb(v) }) { api.clearToast(); api.keepClipboard() })
        if (protect.isNotEmpty()) more.addView(UiKit.chip(ctx, prefs, if (api.keepClearAsk()) "Sure? Clear" else "Clear", api.keepClearAsk(), 34, { v -> fb(v) }) { api.keepClear() })
        more.layoutParams = lp(MATCH, WRAP, 0f, 0, 0, 0, 4)
        root.addView(more)

        val sv = scrollArea(72)
        val all = LinearLayout(ctx)
        all.orientation = LinearLayout.VERTICAL
        if (protect.isEmpty()) all.addView(hint("Nothing kept yet."))
        else {
            var cur = UiKit.row(ctx)
            var used = 0
            for (k in protect) {
                val w = k.length + 5
                if (used > 0 && used + w > 30) { all.addView(cur); cur = UiKit.row(ctx); used = 0 }
                cur.addView(UiKit.chip(ctx, prefs, k + "  \u2715", false, 34, { v -> fb(v) }) { api.removeLock(k) })
                used += w
            }
            all.addView(cur)
        }
        all.addView(hint("Replace never touches these, even when a term matches part of them."))
        sv.addView(all)
        root.addView(sv)
    }
}
