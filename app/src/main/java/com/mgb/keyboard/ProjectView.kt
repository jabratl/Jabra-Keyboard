package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.typing.FixNames
import com.mgb.keyboard.typing.ProjectPlan

/**
 * v0.66.0 (PROJECT 3): the Project screen is drawn like docs/project-screen-mock.html. Logic (rows, counts, labels) = typing/FixNames.java + typing/ProjectPlan.java (tested).
 *   Field text card (the start of the text, every place the project would fix is highlighted, "N to fix")
 *   [<] [Project  Name v]   |   [Terms N | Keep as is N]   |   [wrong; wrong => right] [+ Add]  + one hint line
 *   [Whole words] [Paste list] [Search]   |   term cards: right form, "N in text" badge, wrong-form chips, X (tap twice)   |   [Fix N in text]
 *   After a fix the Fix button is replaced by a bar "Fixed N in the text  Undo".
 * The Project button opens the project list IN PLACE of the lower part (same height, no overlay): projects, + New project, No project, Delete (2 taps).
 * Typing still happens in the Replace mini keyboard (typing codes 12 search, 13 new project name, 14 name to keep, 15 the pair line). The mock's inline keyboard is NOT built.
 * Colours come from the keyboard theme (JabraTokens), buttons are the Clean look buttons (ReplaceClean.button). Everything is saved at once, as before.
 * Built in try / catch by the caller; any error = the panel falls back to the Sets screen. Kotlin NOT compiled here, phone NOT tested.
 * Older notes (v0.62.0): this screen replaced the old "Fix terms" screen; Back = Replace with the project active.
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
        // v0.66.0 (PROJECT 3): the Fix button of the Project screen + the term that is being edited (for the accent border)
        fun fixProject() { }                     // fix the field text with ALL terms of the active project (smart fix), one toast, Undo works
        fun editKey(): String = ""               // Rule.find of the term being edited ("" = a new term)
    }


    var tab = 1                                   // 1 = Terms, 2 = Keep as is
    private var popOpen = false                   // true = the project list is shown in place of the lower part
    private var delAsk = ""                       // key of the term whose X was tapped once
    private var pendingFrom: String? = null       // set while a NEW project name is being typed: when the active project changes, open it

    fun open() { tab = 1; popOpen = false; delAsk = ""; pendingFrom = null }
    fun showNovels() { popOpen = true }

    /** A ScrollView that keeps the drag for itself (the Replace panel around it may scroll too; without this the list could not be scrolled). */
    private inner class NestedScroll(c: Context) : ScrollView(c) {
        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN && (canScrollVertically(1) || canScrollVertically(-1))) parent?.requestDisallowInterceptTouchEvent(true)
            return super.onInterceptTouchEvent(ev)
        }
    }

    // ---------------------------------------------------------------- small helpers
    private fun dp(v: Int): Int = JabraTokens.dp(ctx, v)
    private fun ink(): Int = try { JabraTokens.panelText(prefs) } catch (_: Throwable) { Color.BLACK }
    private fun dim(): Int = try { JabraTokens.dimText(prefs) } catch (_: Throwable) { Color.DKGRAY }
    private fun fb(v: View) { try { host.feedback(v) } catch (_: Throwable) { } }
    private val fbf: (View) -> Unit = { v -> fb(v) }
    private fun dark(): Boolean = try { val c = ink(); (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000 > 140 } catch (_: Throwable) { false }
    private fun redInk() = if (dark()) 0xFFEF7A74.toInt() else 0xFFB3261E.toInt()
    private fun hitFill() = if (dark()) 0xFF5A4A14.toInt() else 0xFFFFE08A.toInt()
    private fun hitInk() = if (dark()) 0xFFFFF3C4.toInt() else 0xFF3A2E00.toInt()
    private val acc: Int get() = JabraTokens.ACCENT
    private fun cText(): Int = JabraTokens.cardText(prefs)
    private fun cNote(): Int = JabraTokens.cardNote(prefs)
    private fun cFill(): Int = JabraTokens.cardFill(prefs, false)
    private fun cLine(): Int = JabraTokens.cardStroke(prefs, false, false)
    private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

    private fun landscape(): Boolean = try { ctx.resources.displayMetrics.let { it.widthPixels > it.heightPixels } } catch (_: Throwable) { false }

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

    private fun shape(fill: Int, stroke: Int, rDp: Int, strokeDp: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(rDp).toFloat()
        if (strokeDp > 0) g.setStroke(dp(strokeDp), stroke)
        return g
    }

    /** No fill; a soft accent wash while pressed (X buttons, menu rows). */
    private fun softPress(rDp: Int): StateListDrawable {
        val sl = StateListDrawable()
        sl.addState(intArrayOf(android.R.attr.state_pressed), shape(JabraTokens.withAlpha(acc, 0x33), Color.TRANSPARENT, rDp))
        sl.addState(intArrayOf(), shape(Color.TRANSPARENT, Color.TRANSPARENT, rDp))
        return sl
    }

    private fun hrow(bottomDp: Int = 0): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.layoutParams = lp(MATCH, WRAP, 0f, 0, 0, 0, bottomDp)
        return r
    }

    /** A tap-to-type box: the real typing happens in the Replace mini keyboard (code = which). [edge] = accent border (a term is being edited). */
    private fun inputBox(placeholder: String, value: String, hDp: Int, sp: Float, which: Int, edge: Boolean = false): TextView {
        val t = TextView(ctx)
        val has = value.isNotEmpty()
        t.text = if (has) value else placeholder
        t.textSize = sp
        t.setTextColor(if (has) cText() else cNote())
        t.maxLines = 1
        t.ellipsize = TextUtils.TruncateAt.END
        t.gravity = Gravity.CENTER_VERTICAL
        t.setPadding(dp(14), 0, dp(12), 0)
        t.background = shape(cFill(), if (edge) acc else cLine(), 14, 1)
        t.contentDescription = placeholder
        t.isClickable = true
        t.setOnClickListener { v -> fb(v); api.clearToast(); if (which == 13) pendingFrom = api.novel(); api.type(which) }
        t.layoutParams = lp(0, dp(hDp), 1f, 3, 3, 3, 3)
        return t
    }

    private fun hintLine(text: String, problem: Boolean): TextView {
        val h = label(text, 12.5f, if (problem) redInk() else dim())
        h.setPadding(dp(8), dp(1), dp(8), dp(1))
        h.minHeight = dp(17)
        h.maxLines = 2
        h.ellipsize = TextUtils.TruncateAt.END
        return h
    }

    private fun listArea(): ScrollView {
        val sv = NestedScroll(ctx)
        sv.isVerticalScrollBarEnabled = false
        val h = ProjectPlan.listHeightDp(try { host.keyHeightDp() } catch (_: Throwable) { 48 }) - (if (landscape()) 20 else 0)
        sv.layoutParams = lp(MATCH, dp(if (h < 56) 56 else h), 0f, 0, 4, 0, 3)
        return sv
    }

    /** A small square X (or "Delete?" while armed). */
    private fun xButton(armed: Boolean, desc: String, click: () -> Unit): TextView {
        val x = TextView(ctx)
        x.gravity = Gravity.CENTER
        x.isClickable = true
        x.contentDescription = desc
        if (armed) {
            x.text = "Delete?"
            x.textSize = 13f
            x.setTypeface(x.typeface, Typeface.BOLD)
            x.setTextColor(Color.WHITE)
            x.background = shape(if (dark()) 0xFFB3261E.toInt() else 0xFFC0392B.toInt(), Color.TRANSPARENT, 11)
            x.setPadding(dp(10), 0, dp(10), 0)
            x.layoutParams = lp(WRAP, dp(40), 0f, 2, 0, 4, 0)
        } else {
            x.text = "\u2715"
            x.textSize = 16f
            x.setTextColor(cNote())
            x.background = softPress(11)
            x.layoutParams = lp(dp(40), dp(40), 0f, 0, 0, 2, 0)
        }
        x.setOnClickListener { v -> fb(v); click() }
        return x
    }

    // ---------------------------------------------------------------- the screen
    fun build(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        try { JabraThemes.paint(prefs, root) } catch (_: Throwable) { root.setBackgroundColor(Color.parseColor("#DDDDDD")) }
        root.setPadding(dp(4), dp(4), dp(4), dp(4))
        val novel = api.novel()
        val pf = pendingFrom
        if (pf != null && novel.isNotEmpty() && !novel.equals(pf, true)) { popOpen = false; tab = 1; pendingFrom = null }

        val rows: List<FixNames.Row> = if (novel.isEmpty()) ArrayList<FixNames.Row>() else try { FixNames.rows(novel, api.setNames(), api.setTexts()) } catch (_: Throwable) { ArrayList<FixNames.Row>() }
        val protect = api.protect()
        val text = try { api.text() } catch (_: Throwable) { "" }
        // one scan gives the highlights, the number on every term and the number on the Fix button (the same count the Replace screen shows)
        val scan: FixNames.Scan? = try { if (rows.isEmpty() || text.isEmpty() || text.length > 30000) null else FixNames.scan(text, rows, null, protect, null, null) } catch (_: Throwable) { null }
        val hits = if (scan != null) scan.hits else if (rows.isEmpty() || text.isEmpty()) 0 else -1   // -1 = not counted (a very long text)

        if (!landscape()) root.addView(previewCard(text, scan, hits))
        root.addView(headerRow(novel))
        if (popOpen || novel.isEmpty()) { projectMenu(root, novel); return root }
        root.addView(segRow(rows.size, protect.size))
        if (tab == 2) keepTab(root, protect) else termsTab(root, rows, scan)
        root.addView(fixSlot(hits, rows.isNotEmpty()))
        return root
    }

    // ---------------------------------------------------------------- Field text card
    private fun previewCard(text: String, scan: FixNames.Scan?, hits: Int): View {
        val c = ReplaceClean.card(ctx, prefs)
        c.setPadding(dp(14), dp(7), dp(14), dp(8))
        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        val l = label("Field text", 11f, cNote())
        l.layoutParams = lp(0, WRAP, 1f)
        head.addView(l)
        head.addView(label(ProjectPlan.headCount(hits), 11f, cNote()))
        c.addView(head)
        val body = TextView(ctx)
        body.textSize = 13.5f
        body.setLineSpacing(0f, 1.15f)
        body.maxLines = 2
        body.ellipsize = TextUtils.TruncateAt.END
        if (text.isBlank()) { body.text = "No text in the field yet."; body.setTextColor(cNote()) }
        else {
            body.setTextColor(cText())
            val ss = ArrayList<Int>()
            val ee = ArrayList<Int>()
            if (scan != null) for (s in scan.segs) { if (!s.lock && !s.draft) { ss.add(s.start); ee.add(s.end) } }
            val w = ProjectPlan.window(text, ss.toIntArray(), ee.toIntArray(), ProjectPlan.PREVIEW_MAX)
            val sp = SpannableStringBuilder(w.text)
            for (i in w.starts.indices) {
                val a = w.starts[i]; val b = w.ends[i]
                if (a >= 0 && b > a && b <= sp.length) {
                    sp.setSpan(BackgroundColorSpan(hitFill()), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sp.setSpan(ForegroundColorSpan(hitInk()), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            body.text = sp
        }
        c.addView(body)
        return c
    }

    // ---------------------------------------------------------------- [<] [Project  Name v]
    private fun headerRow(novel: String): View {
        val r = hrow()
        val back = ReplaceClean.button(ctx, prefs, host, "\u2039", 40, 0f, 0, 22f) { api.back() }
        back.minWidth = dp(44)
        back.contentDescription = "Back to Replace"
        r.addView(back)
        val sb = SpannableStringBuilder("Project  ")
        sb.setSpan(ForegroundColorSpan(cNote()), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (novel.isNotEmpty()) {
            val s = sb.length
            sb.append(ProjectPlan.chipName(novel))
            sb.setSpan(StyleSpan(Typeface.BOLD), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            val s = sb.length
            sb.append("none")
            sb.setSpan(ForegroundColorSpan(cNote()), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val s2 = sb.length
        sb.append("  \u25BE")
        sb.setSpan(ForegroundColorSpan(cNote()), s2, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val pb = ReplaceClean.button(ctx, prefs, host, sb, 40, 1f, 0) { popOpen = !popOpen; delAsk = ""; api.clearToast(); api.refresh() }
        pb.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        pb.contentDescription = if (novel.isEmpty()) "Projects, none active" else "Project " + novel + ", tap to switch"
        r.addView(pb)
        return r
    }

    // ---------------------------------------------------------------- [Terms N | Keep as is N]
    private fun segRow(nTerms: Int, nKeep: Int): View {
        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.background = shape(JabraTokens.withAlpha(JabraTokens.panelText(prefs), 0x14), cLine(), 14, 1)
        bar.setPadding(dp(3), dp(3), dp(3), dp(3))
        bar.layoutParams = lp(MATCH, WRAP, 0f, 3, 3, 3, 3)
        bar.addView(seg("Terms", nTerms, tab == 1) { tab = 1; delAsk = ""; api.clearToast(); api.refresh() })
        bar.addView(seg("Keep as is", nKeep, tab == 2) { tab = 2; api.clearToast(); api.refresh() })
        return bar
    }

    private fun seg(name: String, n: Int, on: Boolean, click: () -> Unit): TextView {
        val t = TextView(ctx)
        val sb = SpannableStringBuilder(name + "  " + n)
        sb.setSpan(RelativeSizeSpan(0.85f), name.length + 2, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        t.text = sb
        t.textSize = 14f
        t.gravity = Gravity.CENTER
        t.maxLines = 1
        t.isClickable = true
        if (on) { t.background = shape(acc, Color.TRANSPARENT, 11); t.setTextColor(Color.WHITE); t.setTypeface(t.typeface, Typeface.BOLD) }
        else { t.background = softPress(11); t.setTextColor(dim()) }
        t.setOnClickListener { v -> fb(v); click() }
        t.layoutParams = lp(0, dp(34), 1f, 1, 0, 1, 0)
        return t
    }

    // ---------------------------------------------------------------- Terms tab
    private fun termsTab(root: LinearLayout, rows: List<FixNames.Row>, scan: FixNames.Scan?) {
        val editKey = api.editKey()
        val editing = editKey.isNotEmpty()
        val typed = api.pairInput().isNotEmpty()

        val add = hrow()
        add.addView(inputBox("wrong; wrong => right", api.pairInput(), 44, 15f, 15, editing))
        add.addView(ReplaceClean.button(ctx, prefs, host, if (editing) "Save" else "+ Add", 44, 0f, acc) { if (api.pairInput().trim().isEmpty()) api.type(15) else api.commitPair() })
        if (typed) {
            val cx = ReplaceClean.button(ctx, prefs, host, "\u2715", 44, 0f, 0) { api.clearPair() }
            cx.contentDescription = "Clear the box"
            add.addView(cx)
        }
        root.addView(add)

        val raw = api.toast()
        val note = if (ProjectPlan.isFixed(raw) && api.canUndo()) "" else ProjectPlan.toastText(raw)
        root.addView(hintLine(ProjectPlan.hint(editing, note), ProjectPlan.isProblem(note)))

        val tools = hrow()
        tools.addView(UiKit.chip(ctx, prefs, (if (api.wholeWord()) "\u2713 " else "") + "Whole words", api.wholeWord(), 36, fbf) { api.setWholeWord(!api.wholeWord()) })
        tools.addView(UiKit.chip(ctx, prefs, "Paste list", false, 36, fbf) { api.clearToast(); api.pasteTerms() })
        tools.addView(inputBox("Search", api.query(), 36, 14f, 12))
        root.addView(tools)

        val sv = listArea()
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val q = api.query().trim().lowercase()
        var shown = 0
        for (i in rows.indices) {
            val r = rows[i]
            if (q.isNotEmpty() && (r.formsText() + " " + r.to).lowercase().indexOf(q) < 0) continue
            shown++
            val n = if (scan != null && i < scan.counts.size) scan.counts[i] else 0
            col.addView(termCard(r, n, editing && r.rule != null && r.rule.find == editKey))
        }
        if (shown == 0) col.addView(emptyNote(if (q.isNotEmpty()) "No term matches \u201C" + api.query().trim() + "\u201D." else "No terms yet. Type one above and tap + Add."))
        sv.addView(col)
        root.addView(sv)
    }

    private fun emptyNote(text: String): TextView {
        val e = label(text, 14f, dim())
        e.gravity = Gravity.CENTER
        e.setPadding(dp(8), dp(18), dp(8), dp(18))
        return e
    }

    private fun cardRow(editing: Boolean): LinearLayout {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = shape(if (editing) JabraTokens.rowPicked(prefs) else cFill(), if (editing) acc else cLine(), 14, 1)
        row.setPadding(dp(14), dp(8), dp(4), dp(8))
        row.layoutParams = lp(MATCH, WRAP, 0f, 3, 0, 3, 7)
        return row
    }

    private fun termCard(r: FixNames.Row, n: Int, editing: Boolean): View {
        val key = r.key()
        val row = cardRow(editing)
        val mid = LinearLayout(ctx)
        mid.orientation = LinearLayout.VERTICAL
        mid.isClickable = true
        mid.layoutParams = lp(0, WRAP, 1f)
        mid.setOnClickListener { v -> fb(v); delAsk = ""; api.editTerm(r.rule.find, r.formsText() + " => " + r.to) }

        val top = LinearLayout(ctx)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val toTv = label(r.to, 15.5f, cText(), true)
        toTv.maxLines = 1
        toTv.ellipsize = TextUtils.TruncateAt.END
        top.addView(toTv)
        val badge = ProjectPlan.inText(n)
        if (badge.isNotEmpty()) {
            val bt = label(badge, 11f, hitInk(), true)
            bt.maxLines = 1
            bt.background = shape(hitFill(), Color.TRANSPARENT, 9)
            bt.setPadding(dp(7), dp(1), dp(7), dp(1))
            bt.layoutParams = lp(WRAP, WRAP, 0f, 8, 0, 0, 0)
            top.addView(bt)
        }
        mid.addView(top)
        mid.addView(formChips(r.forms))
        row.addView(mid)

        val ask = delAsk == key
        row.addView(xButton(ask, "Delete term " + r.to) {
            if (ask) { delAsk = ""; api.deleteTerm(r.rule.find) } else { delAsk = key; api.refresh() }
        })
        return row
    }

    /** The wrong forms as small chips; a new line when the row is full (there is no flow layout in the keyboard views). */
    private fun formChips(forms: List<String>): LinearLayout {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        var cur = LinearLayout(ctx)
        cur.orientation = LinearLayout.HORIZONTAL
        var used = 0
        for (f in forms) {
            val w = f.length + 3
            if (used > 0 && used + w > 34) { col.addView(cur); cur = LinearLayout(ctx); cur.orientation = LinearLayout.HORIZONTAL; used = 0 }
            val c = label(f, 12.5f, cNote())
            c.maxLines = 1
            c.maxWidth = dp(250)
            c.ellipsize = TextUtils.TruncateAt.END
            c.setPadding(dp(8), dp(1), dp(8), dp(1))
            c.background = shape(JabraTokens.withAlpha(cText(), 0x12), cLine(), 9, 1)
            c.layoutParams = lp(WRAP, WRAP, 0f, 0, 4, 4, 0)
            cur.addView(c)
            used += w
        }
        col.addView(cur)
        return col
    }

    // ---------------------------------------------------------------- Keep as is tab
    private fun keepTab(root: LinearLayout, protect: List<String>) {
        val add = hrow()
        add.addView(inputBox("Name to keep as is", api.keepInput(), 44, 15f, 14))
        add.addView(ReplaceClean.button(ctx, prefs, host, "+ Add", 44, 0f, acc) {
            val n = api.keepInput().trim()
            if (n.isEmpty()) api.type(14) else api.addKeep(n)
        })
        root.addView(add)

        val raw = api.toast()
        val note = if (ProjectPlan.isFixed(raw) && api.canUndo()) "" else ProjectPlan.toastText(raw)
        root.addView(hintLine(if (note.isNotEmpty()) note else "Names Replace never changes, even inside a longer match.", ProjectPlan.isProblem(note)))

        val tools = hrow()
        tools.addView(UiKit.chip(ctx, prefs, "+ Clipboard", false, 36, fbf) { api.clearToast(); api.keepClipboard() })
        tools.addView(UiKit.chip(ctx, prefs, "+ Selection", false, 36, fbf) { api.clearToast(); api.keepSelection() })
        val clr = UiKit.chip(ctx, prefs, if (api.keepClearAsk()) "Sure? Clear all" else "Clear", api.keepClearAsk(), 36, fbf) { api.keepClear() }
        if (protect.isEmpty()) { clr.alpha = 0.4f; clr.isClickable = false }
        tools.addView(clr)
        root.addView(tools)

        val sv = listArea()
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        for (k in protect) {
            val row = cardRow(false)
            val nm = label(k, 15.5f, cText(), true)
            nm.maxLines = 1
            nm.ellipsize = TextUtils.TruncateAt.END
            nm.layoutParams = lp(0, WRAP, 1f)
            row.addView(nm)
            row.addView(xButton(false, "Remove " + k) { api.removeLock(k) })
            col.addView(row)
        }
        if (protect.isEmpty()) col.addView(emptyNote("Nothing kept yet."))
        sv.addView(col)
        root.addView(sv)
    }

    // ---------------------------------------------------------------- the Fix button (or the Undo bar after a fix)
    private fun fixSlot(hits: Int, hasTerms: Boolean): View {
        val raw = api.toast()
        if (ProjectPlan.isFixed(raw) && api.canUndo()) return undoBar(ProjectPlan.toastText(raw))
        val b = ReplaceClean.button(ctx, prefs, host, ProjectPlan.fixLabel(hits, hasTerms), 44, 0f, acc, 15f) { api.clearToast(); api.fixProject() }
        b.layoutParams = lp(MATCH, dp(44), 0f, 3, 4, 3, 3)
        b.contentDescription = if (ProjectPlan.fixEnabled(hits, hasTerms)) "Fix the field text with this project" else "Nothing to fix"
        ReplaceClean.dim(b, ProjectPlan.fixEnabled(hits, hasTerms))
        return b
    }

    private fun undoBar(text: String): View {
        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.background = shape(ink(), Color.TRANSPARENT, 12)
        bar.setPadding(dp(14), 0, dp(4), 0)
        bar.layoutParams = lp(MATCH, dp(44), 0f, 3, 4, 3, 3)
        val on = try { JabraTokens.panelBg(prefs) } catch (_: Throwable) { Color.WHITE }
        val t = label(text, 14f, on)
        t.maxLines = 1
        t.ellipsize = TextUtils.TruncateAt.END
        t.layoutParams = lp(0, WRAP, 1f)
        bar.addView(t)
        val u = label("Undo", 14f, on, true)
        u.paintFlags = u.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        u.gravity = Gravity.CENTER
        u.setPadding(dp(14), 0, dp(14), 0)
        u.isClickable = true
        u.contentDescription = "Undo the fix"
        u.setOnClickListener { v -> fb(v); api.undo() }
        u.layoutParams = lp(WRAP, MATCH)
        bar.addView(u)
        return bar
    }

    // ---------------------------------------------------------------- the project list (opens in place of the lower part)
    private fun menuRow(left: CharSequence, right: String, bold: Boolean, color: Int, click: () -> Unit): View {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(12), 0, dp(12), 0)
        r.background = softPress(10)
        r.isClickable = true
        r.layoutParams = lp(MATCH, dp(44))
        val l = label("", 15f, color, bold)
        l.text = left
        l.maxLines = 1
        l.ellipsize = TextUtils.TruncateAt.END
        l.layoutParams = lp(0, WRAP, 1f)
        r.addView(l)
        if (right.isNotEmpty()) { val x = label(right, 13f, cNote()); x.maxLines = 1; r.addView(x) }
        r.setOnClickListener { v -> fb(v); click() }
        return r
    }

    private fun projectMenu(root: LinearLayout, novel: String) {
        val list = api.novels()
        val card = ReplaceClean.card(ctx, prefs)
        card.setPadding(dp(6), dp(6), dp(6), dp(6))
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        var count = 0
        for (n in list) {
            val mark = (if (n.active) "\u25CF  " else "\u25CB  ") + n.name
            val right = ProjectPlan.termsLabel(n.pairs) + (if (n.locked > 0) " \u00B7 " + n.locked + " kept" else "")
            box.addView(menuRow(mark, right, n.active, cText()) { popOpen = false; tab = 1; delAsk = ""; pendingFrom = null; api.clearToast(); api.pickNovel(n.name) })
            count++
        }
        if (list.isEmpty()) { box.addView(emptyNote("No project yet. Tap + New project.")); count += 1 }
        val dv = View(ctx)
        dv.setBackgroundColor(cLine())
        dv.layoutParams = lp(MATCH, 1, 0f, 6, 4, 6, 4)
        box.addView(dv)

        val typed = api.newNovelInput().trim()
        box.addView(menuRow(if (typed.isEmpty()) "+ New project" else "+ Create \u201C" + typed + "\u201D", "", true, cText()) {
            if (typed.isEmpty()) { pendingFrom = novel; api.clearToast(); api.type(13) }
            else { popOpen = false; tab = 1; pendingFrom = null; api.addNovel(typed) }
        })
        count++
        if (novel.isNotEmpty()) {
            box.addView(menuRow("No project", "", false, cText()) { popOpen = false; pendingFrom = null; api.clearToast(); api.pickNovel("") })
            box.addView(menuRow(if (api.deleteAsk()) "Sure? Delete " + novel else "Delete " + novel, "", true, redInk()) { api.deleteNovel() })
            count += 2
        }
        val t = ProjectPlan.toastText(api.toast())
        if (t.isNotEmpty() && !ProjectPlan.isFixed(api.toast())) { box.addView(hintLine(t, ProjectPlan.isProblem(t))); count++ }

        val sv = NestedScroll(ctx)
        sv.isVerticalScrollBarEnabled = false
        val want = count * 44 + 12
        val cap = ProjectPlan.listHeightDp(try { host.keyHeightDp() } catch (_: Throwable) { 48 }) + 110
        sv.layoutParams = LinearLayout.LayoutParams(MATCH, dp(if (want < cap) want else cap))
        sv.addView(box)
        card.addView(sv)
        root.addView(card)
    }
}
