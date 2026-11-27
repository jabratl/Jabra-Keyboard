package com.mgb.keyboard

import android.content.Context
import android.graphics.Canvas
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
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.typing.FixNames
import com.mgb.keyboard.typing.NovelBook

/**
 * v0.58.0 (FX2): the "Fix terms" screen, rebuilt exactly like docs/Fix_terms_mock.html (replaces the v0.57.0 "Fix names" layout; same Api, same tested logic in typing/FixNames.java).
 * Header: back, "Fix terms", Undo (inside the text box), novel button, "..." (More). Live text box: wrong term struck through in red, correct term in green, kept names in gold with a lock.
 * Tabs: [Fix] [Terms n] [Keep as is n].
 *  Fix = Wrong term, arrow, Correct term, ONE big button ("Fix 3" / "Fix all . 4"), "or", [Use <novel> list . N fixes] and [Save to list].
 *  Terms = the novel's terms grouped by the correct term ("Lin Feng <- Lin Fen, Lin Fong"), search, + Add, tap a term = load it into Fix.
 *  Keep as is = the names Replace never touches (= Never change), tap a name to remove it, + Add opens the Never change screen.
 *  More = Import from plugin (preview + Import), Show raw names (switch), How it works.   Novel button = the novels list.
 * Wrong / Correct / search boxes open the usual mini keyboard of Replace (api.type), Done brings you back here.
 * Everything is built in try / catch by the caller; any error = the panel falls back to the old Sets screen.
 */
class FixNamesUi(private val ctx: Context, private val prefs: JabraPrefs, private val host: JabraHost, private val api: Api) {

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
        fun type(which: Int)                     // 1 = wrong forms, 2 = right form, 12 = search
        fun addPair(forms: List<String>, to: String, topic: String): String
        fun fix(rules: List<com.mgb.keyboard.typing.ReplaceRules.Rule>): String
        fun undo()
        fun canUndo(): Boolean
        fun toast(): String
        fun back()
        fun novels(): List<NovelRow>
        fun pickNovel(name: String)
        fun pasteGlossary()
        fun deleteAsk(): Boolean
        fun deleteNovel()
        fun removeLock(term: String)
        fun refresh()
        fun clearFields()                        // v0.58.0: empty Wrong + Correct (after a fix)
        fun loadPair(forms: String, to: String)  // v0.58.0: put a term into Wrong + Correct
        fun openKeep()                           // v0.58.0: the Never change screen (add names), Done / back returns here
        fun clipInfo(): String                   // v0.58.0: "Novel A . 5 terms . 2 kept" when the clipboard holds a glossary, else ""
    }

    // state that lives as long as the panel (memory only, like the mock)
    var tab = 1                                   // 1 = Fix, 2 = Terms, 3 = Keep as is
    private var sheet = 0                         // 0 none, 1 More, 2 Import, 3 Novels, 4 How it works
    private var listMode = false                  // Fix tab: true = "Use list" (every term of the novel)
    private var showRaw = false

    /** Called when the screen is opened from the Replace panel. */
    fun open() { tab = 1; sheet = 0; listMode = false }

    private val none = HashSet<String>()

    private fun dp(v: Int): Int = JabraTokens.dp(ctx, v)
    private fun ink(): Int = try { JabraTokens.panelText(prefs) } catch (_: Throwable) { Color.BLACK }
    private fun dim(): Int = try { JabraTokens.dimText(prefs) } catch (_: Throwable) { Color.DKGRAY }
    private fun fb(v: View) { try { host.feedback(v) } catch (_: Throwable) { } }
    private fun dark(): Boolean = try { val c = ink(); (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000 > 140 } catch (_: Throwable) { false }

    // colours of the mock (del / ins / lock), a light variant for light themes
    private fun delBg() = if (dark()) 0xFF4A2626.toInt() else 0xFFFBDAD7.toInt()
    private fun delInk() = if (dark()) 0xFFFFB4AE.toInt() else 0xFF8A1C14.toInt()
    private fun insBg() = if (dark()) 0xFF233F2B.toInt() else 0xFFD6F0DB.toInt()
    private fun insInk() = if (dark()) 0xFFB5E8BD.toInt() else 0xFF1B5E2B.toInt()
    private fun lockInk() = if (dark()) 0xFFE8C887.toInt() else 0xFF8A6A12.toInt()
    private fun redInk() = if (dark()) 0xFFEF7A74.toInt() else 0xFFB3261E.toInt()
    private val acc: Int get() = JabraTokens.ACCENT

    private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

    // ---------------------------------------------------------------- small widgets
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

    private fun strokeInk(): Int = if (dark()) 0xFF2B3A62.toInt() else 0xFFC4CBD9.toInt()

    /** Filled accent button (Fix N, Import, Paste glossary). */
    private fun fill(b: Button, enabled: Boolean = true) {
        try {
            val r = dp(JabraTokens.R_SM).toFloat()
            val up = GradientDrawable(); up.setColor(acc); up.cornerRadius = r
            val down = GradientDrawable(); down.setColor(com.mgb.keyboard.typing.LookColors.darken(acc, 0.18f)); down.cornerRadius = r
            val sl = StateListDrawable()
            sl.addState(intArrayOf(android.R.attr.state_pressed), down)
            sl.addState(intArrayOf(), up)
            b.background = sl
            b.setTextColor(Color.WHITE)
        } catch (_: Throwable) { }
        b.isEnabled = enabled
        b.alpha = if (enabled) 1f else 0.45f
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        try { c.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
        c.setPadding(dp(10), dp(8), dp(10), dp(8))
        return c
    }

    /** The on / off switch of "Show raw names". */
    private inner class Switch(val on: Boolean) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            p.color = if (on) acc else strokeInk()
            c.drawRoundRect(0f, 0f, w, h, h / 2, h / 2, p)
            p.color = Color.WHITE
            val r = h / 2 - dp(3)
            c.drawCircle(if (on) w - h / 2 else h / 2, h / 2, r, p)
        }
    }

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
        t.setOnClickListener { v -> fb(v); listMode = false; api.type(which) }
        return t
    }

    private fun spacer(): View { val v = View(ctx); v.layoutParams = LinearLayout.LayoutParams(0, 1, 1f); return v }

    private fun twoLines(top: String, sub: String, topBold: Boolean = false): LinearLayout {
        val g = LinearLayout(ctx)
        g.orientation = LinearLayout.VERTICAL
        val a = label(top, 15f, ink(), topBold)
        a.maxLines = 1; a.ellipsize = TextUtils.TruncateAt.END
        g.addView(a)
        if (sub.isNotEmpty()) g.addView(label(sub, 12f, dim()))
        g.layoutParams = lp(0, WRAP, 1f)
        return g
    }

    private fun rowBox(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        try { r.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
        r.setPadding(dp(12), dp(8), dp(12), dp(8))
        r.layoutParams = lp(MATCH, WRAP, 0f, 0, 0, 0, 5)
        return r
    }

    private fun hint(text: String): TextView {
        val t = label(text, 12f, dim())
        t.layoutParams = lp(MATCH, WRAP, 0f, 2, 4, 2, 6)
        return t
    }

    // ---------------------------------------------------------------- state of one build
    private class St(
        val novel: String, val rows: List<FixNames.Row>, val protect: List<String>, val forms: List<String>, val to: String,
        val text: String, val shown: String, val listSc: FixNames.Scan?, val fieldSc: FixNames.Scan
    )

    private fun state(): St {
        val novel = api.novel()
        val rows = if (novel.isEmpty()) emptyList<FixNames.Row>() else FixNames.rows(novel, api.setNames(), api.setTexts())
        val protect = api.protect()
        val forms = FixNames.forms(api.find())
        val to = api.repl().trim()
        val text = api.text()
        val shown = if (text.length > 3000) text.substring(0, 3000) else text
        val listSc = if (rows.isEmpty()) null else FixNames.scan(shown, rows, none, protect, ArrayList<String>(), "")
        val fieldSc = FixNames.scan(shown, ArrayList<FixNames.Row>(), none, protect, forms, to)
        if (listSc == null) listMode = false
        return St(novel, rows, protect, forms, to, text, shown, listSc, fieldSc)
    }

    // ---------------------------------------------------------------- the screen
    fun build(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        try { JabraThemes.paint(prefs, root) } catch (_: Throwable) { root.setBackgroundColor(Color.parseColor("#DDDDDD")) }
        root.setPadding(dp(4), dp(4), dp(4), dp(4))
        if (sheet != 0) { buildSheet(root); return root }
        val st = state()
        root.addView(header(st))
        root.addView(docBox(st))
        root.addView(tabs(st))
        root.addView(when (tab) { 2 -> termsTab(st); 3 -> keepTab(st); else -> fixTab(st) })
        return root
    }

    private fun header(st: St): View {
        val h = UiKit.row(ctx)
        h.gravity = Gravity.CENTER_VERTICAL
        h.addView(UiKit.btn(ctx, prefs, "<", 0f, 36, JabraTokens.T_BTN, { v -> fb(v) }) { api.back() })
        h.addView(label("Fix terms", 15f, ink(), true), lp(WRAP, WRAP, 0f, 6, 0, 0, 0))
        h.addView(spacer())
        h.addView(UiKit.chip(ctx, prefs, (if (st.novel.isEmpty()) "Pick a novel" else st.novel) + "  ▾", true, 34, { v -> fb(v) }) { sheet = 3; api.refresh() })
        h.addView(UiKit.btn(ctx, prefs, "⋯", 0f, 36, JabraTokens.T_BTN, { v -> fb(v) }) { sheet = 1; api.refresh() })
        return h
    }

    private fun tabs(st: St): View {
        val r = UiKit.row(ctx)
        val names = listOf("Fix", "Terms " + groups(st).size, "Keep as is " + st.protect.size)
        for (i in 0..2) {
            val b = UiKit.chip(ctx, prefs, names[i], tab == i + 1, 34, { v -> fb(v) }) { tab = i + 1; api.refresh() }
            b.layoutParams = lp(0, WRAP, 1f, 2, 4, 2, 4)
            r.addView(b)
        }
        return r
    }

    // ---------------------------------------------------------------- live text box
    private fun docBox(st: St): View {
        val fl = FrameLayout(ctx)
        fl.layoutParams = lp(MATCH, dp(96), 0f, 0, 4, 0, 2)
        val sc = if (listMode && st.listSc != null) st.listSc else st.fieldSc
        fl.addView(preview(st.shown, sc, st.text.isEmpty()), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        if (api.canUndo()) {
            val u = UiKit.chip(ctx, prefs, "Undo", false, 28, { v -> fb(v) }) { api.undo() }
            fl.addView(u, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
        }
        return fl
    }

    private fun preview(shown: String, sc: FixNames.Scan, empty: Boolean): View {
        val sv = ScrollView(ctx)
        val t = TextView(ctx)
        t.textSize = 15f
        t.setLineSpacing(0f, 1.3f)
        t.setTextColor(ink())
        try { t.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
        t.setPadding(dp(12), dp(8), dp(12), dp(8))
        if (empty) {
            t.text = "Type or select text in the field. The fixes show here before you tap."
            t.setTextColor(dim())
            sv.addView(t); return sv
        }
        val sb = SpannableStringBuilder()
        var pos = 0
        for (s in sc.segs) {
            if (s.start < pos || s.end > shown.length) continue
            sb.append(shown.substring(pos, s.start))
            if (s.lock) {
                val a = sb.length
                sb.append(shown.substring(s.start, s.end)).append(" 🔒")
                sb.setSpan(ForegroundColorSpan(lockInk()), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(UnderlineSpan(), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                val a = sb.length
                sb.append(shown.substring(s.start, s.end))
                val b = sb.length
                sb.setSpan(StrikethroughSpan(), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(BackgroundColorSpan(delBg()), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(ForegroundColorSpan(delInk()), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.append(" ")
                val c = sb.length
                sb.append(s.to)
                val d = sb.length
                sb.setSpan(BackgroundColorSpan(insBg()), c, d, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(ForegroundColorSpan(insInk()), c, d, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), c, d, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            pos = s.end
        }
        if (pos < shown.length) sb.append(shown.substring(pos))
        t.text = sb
        sv.addView(t)
        return sv
    }

    // ---------------------------------------------------------------- tab 1: Fix
    private fun fixTab(st: St): View {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val lm = listMode && st.listSc != null
        val listHits = st.listSc?.hits ?: 0
        val n = if (lm) listHits else st.fieldSc.draftHits

        val fields = LinearLayout(ctx)
        fields.orientation = LinearLayout.VERTICAL
        fields.addView(fieldBox("Wrong term", api.find(), 1), lp(MATCH, dp(42)))
        val arrow = label("↓", 13f, dim())
        arrow.gravity = Gravity.CENTER
        fields.addView(arrow, lp(MATCH, WRAP))
        fields.addView(fieldBox("Correct term", api.repl(), 2), lp(MATCH, dp(42)))
        if (lm) fields.alpha = 0.45f
        col.addView(fields)

        col.addView(hint(if (lm) "Showing every fix from the " + st.novel + " list." else "Tap a field to type. Several wrong spellings? Separate with ;"))

        val btnText = when {
            lm -> if (n > 0) "Fix all · $n" else "Nothing to fix"
            n > 0 -> "Fix $n"
            st.forms.isNotEmpty() && st.to.isEmpty() -> "Type the correct term"
            st.forms.isNotEmpty() -> "Not found in text"
            else -> "Fix"
        }
        val go = UiKit.btn(ctx, prefs, btnText, 0f, 46, JabraTokens.T_BODY, { v -> fb(v) }) {
            if (lm) { api.fix(FixNames.activeRules(st.rows, none)); listMode = false }
            else {
                val r = FixNames.draftRule(st.forms, st.to)
                if (r != null) { val msg = api.fix(listOf(r)); if (msg.startsWith("Fixed")) api.clearFields() }
            }
        }
        fill(go, n > 0)
        go.layoutParams = lp(MATCH, dp(46))
        col.addView(go)

        if (st.rows.isNotEmpty() || st.novel.isNotEmpty()) {
            col.addView(orLine())
            val r2 = UiKit.row(ctx)
            if (st.rows.isNotEmpty()) {
                val lb = if (lm) "Back to one term" else "Use " + st.novel + " list · " + listHits + " fixes"
                val b = UiKit.btn(ctx, prefs, lb, 0f, 40, JabraTokens.T_BTN, { v -> fb(v) }) { listMode = !lm; api.refresh() }
                b.layoutParams = lp(0, dp(40), 1f, 2, 0, 2, 0)
                r2.addView(b)
            }
            val canSave = !lm && st.novel.isNotEmpty() && st.forms.isNotEmpty() && st.to.isNotEmpty() &&
                st.rows.none { r -> r.to.equals(st.to, true) && st.forms.all { f -> r.forms.any { it.equals(f, true) } } }
            if (canSave) {
                val b = UiKit.btn(ctx, prefs, "Save to list", 0f, 40, JabraTokens.T_BTN, { v -> fb(v) }) { api.addPair(st.forms, st.to, NovelBook.MY_TOPIC) }
                b.layoutParams = lp(0, dp(40), 1f, 2, 0, 2, 0)
                r2.addView(b)
            }
            col.addView(r2)
        } else {
            col.addView(hint("No novel yet. Tap the novel button above, then Import from plugin (...)."))
        }
        if (api.toast().isNotEmpty()) col.addView(hint(api.toast()))
        return col
    }

    private fun orLine(): View {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        fun line(): View { val v = View(ctx); v.setBackgroundColor(JabraTokens.withAlpha(dim(), 0x55)); v.layoutParams = lp(0, dp(1), 1f); return v }
        r.addView(line())
        r.addView(label("or", 11f, dim()), lp(WRAP, WRAP, 0f, 8, 0, 8, 0))
        r.addView(line())
        r.layoutParams = lp(MATCH, WRAP, 0f, 0, 6, 0, 6)
        return r
    }

    // ---------------------------------------------------------------- tab 2: Terms
    private class Term(val to: String, val forms: ArrayList<String>, val raw: String)

    private fun groups(st: St): List<Term> {
        val raw = try { FixNames.rawOf(st.novel, api.setNames(), api.setTexts()) } catch (_: Throwable) { HashMap<String, String>() }
        val m = LinkedHashMap<String, Term>()
        for (r in st.rows) {
            val k = r.to.lowercase()
            val t = m.getOrPut(k) { Term(r.to, ArrayList<String>(), raw[k] ?: "") }
            for (f in r.forms) if (t.forms.none { it.equals(f, true) }) t.forms.add(f)
        }
        return ArrayList(m.values)
    }

    private fun termsTab(st: St): View {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val bar = UiKit.row(ctx)
        bar.gravity = Gravity.CENTER_VERTICAL
        val sb = fieldBox("Search terms", api.query(), 12)
        sb.layoutParams = lp(0, dp(42), 1f, 2, 0, 4, 0)
        sb.setOnClickListener { v -> fb(v); api.type(12) }
        bar.addView(sb)
        bar.addView(UiKit.btn(ctx, prefs, "+ Add", 0f, 42, JabraTokens.T_BTN, { v -> fb(v) }) { tab = 1; listMode = false; api.refresh() })
        bar.layoutParams = lp(MATCH, WRAP, 0f, 0, 0, 0, 6)
        col.addView(bar)

        val sv = ScrollView(ctx)
        sv.layoutParams = lp(MATCH, dp(150))
        val list = LinearLayout(ctx)
        list.orientation = LinearLayout.VERTICAL
        val q = api.query().trim().lowercase()
        var shown = 0
        for (t in groups(st)) {
            if (q.isNotEmpty() && (t.to + " " + t.forms.joinToString(" ") + " " + t.raw).lowercase().indexOf(q) < 0) continue
            shown++
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.VERTICAL
            try { row.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
            row.setPadding(dp(12), dp(8), dp(12), dp(8))
            row.layoutParams = lp(MATCH, WRAP, 0f, 0, 0, 0, 5)
            row.addView(label(t.to, 15f, ink(), true))
            row.addView(label("← " + t.forms.joinToString(", "), 13f, dim()))
            if (showRaw && t.raw.isNotEmpty()) row.addView(label(t.raw, 12f, acc))
            row.isClickable = true
            row.setOnClickListener { v -> fb(v); listMode = false; tab = 1; api.loadPair(t.forms.joinToString("; "), t.to) }
            list.addView(row)
        }
        if (shown == 0) list.addView(hint(if (st.novel.isEmpty()) "No novel yet. Tap the novel button above." else "No terms yet. Add one in Fix, then Save to list."))
        sv.addView(list)
        col.addView(sv)
        return col
    }

    // ---------------------------------------------------------------- tab 3: Keep as is
    private fun keepTab(st: St): View {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.addView(hint("Names and words Replace never touches, even when a rule matches part of them. Tap a name to remove it."))
        val sv = ScrollView(ctx)
        sv.layoutParams = lp(MATCH, dp(150))
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        var cur = UiKit.row(ctx)
        var used = 0
        fun put(b: View, len: Int) {
            if (used > 0 && used + len > 30) { box.addView(cur); cur = UiKit.row(ctx); used = 0 }
            cur.addView(b); used += len
        }
        for (k in st.protect) {
            val b = UiKit.chip(ctx, prefs, k + "  ✕", false, 34, { v -> fb(v) }) { api.removeLock(k) }
            put(b, k.length + 5)
        }
        put(UiKit.chip(ctx, prefs, "+ Add", true, 34, { v -> fb(v) }) { api.openKeep() }, 7)
        box.addView(cur)
        sv.addView(box)
        col.addView(sv)
        if (api.toast().isNotEmpty()) col.addView(hint(api.toast()))
        return col
    }

    // ---------------------------------------------------------------- sheets: More / Import / Novels / How it works
    private fun sheetHeader(title: String, backTo: Int): View {
        val h = UiKit.row(ctx)
        h.gravity = Gravity.CENTER_VERTICAL
        h.addView(UiKit.btn(ctx, prefs, "<", 0f, 36, JabraTokens.T_BTN, { v -> fb(v) }) { sheet = backTo; api.refresh() })
        h.addView(label(title, 15f, ink(), true), lp(WRAP, WRAP, 0f, 6, 0, 0, 0))
        h.layoutParams = lp(MATCH, WRAP, 0f, 0, 0, 0, 8)
        return h
    }

    private fun buildSheet(root: LinearLayout) {
        when (sheet) {
            1 -> {
                root.addView(sheetHeader("More", 0))
                val imp = rowBox()
                imp.addView(twoLines("Import from plugin", "Plugin > Glossary > Copy for keyboard", true))
                imp.isClickable = true
                imp.setOnClickListener { v -> fb(v); sheet = 2; api.refresh() }
                root.addView(imp)
                val raw = rowBox()
                raw.addView(twoLines("Show raw names", "Japanese / Korean / Chinese originals under each term", true))
                val sw = Switch(showRaw)
                sw.layoutParams = lp(dp(40), dp(24))
                raw.addView(sw)
                raw.isClickable = true
                raw.setOnClickListener { v -> fb(v); showRaw = !showRaw; api.refresh() }
                root.addView(raw)
                val help = rowBox()
                help.addView(twoLines("How it works", "One short page", true))
                help.isClickable = true
                help.setOnClickListener { v -> fb(v); sheet = 4; api.refresh() }
                root.addView(help)
            }
            2 -> {
                root.addView(sheetHeader("Import from plugin", 1))
                val info = try { api.clipInfo() } catch (_: Throwable) { "" }
                val c = card()
                c.addView(label(if (info.isNotEmpty()) "Found on clipboard:\n" + info else "Nothing to import yet.\nIn the plugin: Glossary > Copy for keyboard, then come back here.", 13f, if (info.isNotEmpty()) ink() else dim()))
                c.layoutParams = lp(MATCH, WRAP, 0f, 0, 0, 0, 4)
                root.addView(c)
                root.addView(hint("Importing again updates this novel's list. Terms you added yourself stay."))
                val b = UiKit.btn(ctx, prefs, "Import", 0f, 46, JabraTokens.T_BODY, { v -> fb(v) }) { sheet = 0; tab = 1; api.pasteGlossary() }
                fill(b, info.isNotEmpty())
                b.layoutParams = lp(MATCH, dp(46))
                root.addView(b)
            }
            3 -> {
                root.addView(sheetHeader("Novels", 0))
                val paste = UiKit.btn(ctx, prefs, "Import from plugin", 0f, 40, JabraTokens.T_BTN, { v -> fb(v) }) { sheet = 2; api.refresh() }
                fill(paste)
                paste.layoutParams = lp(MATCH, dp(40), 0f, 0, 0, 0, 6)
                root.addView(paste)
                val list = api.novels()
                val sv = ScrollView(ctx)
                sv.layoutParams = lp(MATCH, dp(150))
                val box = LinearLayout(ctx)
                box.orientation = LinearLayout.VERTICAL
                for (n in list) {
                    val row = rowBox()
                    if (n.active) { try { row.background = outline(if (dark()) 0xFF1B2745.toInt() else 0xFFEAF1FB.toInt(), acc, 10) } catch (_: Throwable) { } }
                    val dot = View(ctx)
                    dot.background = if (n.active) pill(acc, 6) else outline(Color.TRANSPARENT, dim(), 6)
                    row.addView(dot, lp(dp(12), dp(12), 0f, 0, 0, 8, 0))
                    row.addView(twoLines(n.name, n.pairs.toString() + (if (n.pairs == 1) " term" else " terms") + " · " + n.locked + " kept"))
                    row.isClickable = true
                    row.setOnClickListener { v -> fb(v); sheet = 0; tab = 1; listMode = false; api.pickNovel(n.name) }
                    box.addView(row)
                }
                if (list.isEmpty()) box.addView(hint("No novel yet. In the plugin tap Glossary > Copy for keyboard, then Import here."))
                sv.addView(box)
                root.addView(sv)
                val cur = api.novel()
                if (cur.isNotEmpty()) {
                    val del = UiKit.btn(ctx, prefs, if (api.deleteAsk()) "Sure? Delete " + cur else "Delete " + cur, 0f, 36, JabraTokens.T_BTN, { v -> fb(v) }) { api.deleteNovel() }
                    del.setTextColor(redInk())
                    root.addView(del, lp(WRAP, WRAP, 0f, 0, 4, 0, 0))
                }
                if (api.toast().isNotEmpty()) root.addView(hint(api.toast()))
            }
            else -> {
                root.addView(sheetHeader("How it works", 1))
                val c = card()
                c.addView(label(
                    "Fix: type the wrong term and the correct term. The text above shows the change before you tap.\n\n" +
                    "Several wrong spellings of one term: separate them with ;\n\n" +
                    "Use list: fixes every term of the novel in one tap.\n\n" +
                    "Save to list: keeps the pair for the next chapters. Terms shows them under the correct term.\n\n" +
                    "Keep as is: names that are never changed.", 13f, ink()))
                root.addView(c)
            }
        }
    }
}
