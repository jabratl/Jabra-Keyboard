package com.mgb.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.typing.FixNames
import com.mgb.keyboard.typing.NovelBook

/**
 * v0.59.0 (FX3): the "Fix terms" screen, easy-to-use version (design = replace-glossary-mock-v2.html). Same Api idea, same tested logic in typing/FixNames.java.
 * Header: back, "Fix terms", novel button, "...". Live text box (Undo inside). Tabs: [Fix (red badge = fixes left in text)] [Terms n] [Keep as is n]. One primary button at the bottom of the body.
 *  Fix = toggle [One term | Whole list].
 *        One term: Wrong, Correct, "Remember for next chapters" checkbox (default on, saves the pair when you tap Fix), button "Fix n".
 *        Whole list: tick list of the terms found in the text (x times each, untick = skip), button "Fix all . n". Opens on Whole list by itself when the text has fixes.
 *  Terms = terms found in the text first, each with its own "Fix n" button; the rest dim ("not in text"). The "Raw names" switch sits above the list.
 *  Layout rule: the list of each tab is a ScrollView straight in root (the host gives it the spare height), the primary button + toast sit under it.
 *  Keep as is = type a name + Add, chips (tap x to remove), and "Seen in your text" suggestions (Elder Mo ...).
 *  After a fix a toast with Undo shows at the bottom.   "?" = How it works.
 * v0.59.2: no plugin / glossary import and no raw names here. Only: wrong form -> right form, pairs saved per novel, one tap to use them.
 * Wrong / Correct / Keep / search boxes open the usual mini keyboard of Replace (api.type), Done brings you back here.
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
        fun type(which: Int)                     // 1 = wrong forms, 2 = right form, 12 = search, 13 = name to keep, 14 = new novel name
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
        fun clearFields()                        // v0.58.0: empty Wrong + Correct (after a fix)
        fun loadPair(forms: String, to: String)  // v0.58.0: put a term into Wrong + Correct
        fun openKeep()                           // v0.58.0: the Never change screen (add names), Done / back returns here
        // v0.59.0 (FX3)
        fun fixOne(rule: com.mgb.keyboard.typing.ReplaceRules.Rule, forms: List<String>, to: String, save: Boolean): String  // fix + (optional) remember, one toast, fields emptied
        fun keepInput(): String                  // the name typed in the Keep as is box
        fun addKeep(name: String)                // add a name to Keep as is (typed or suggested)
        fun clearToast()
        fun newNovelInput(): String             // v0.59.1: the name typed for a new novel
        fun addNovel(name: String)               // v0.59.1: create a new empty novel and make it active
    }

    // state that lives as long as the panel (memory only, like the mock)
    var tab = 1                                   // 1 = Fix, 2 = Terms, 3 = Keep as is
    private var sheet = 0                         // 0 none, 3 Novels, 4 How it works
    private var mode = 0                          // Fix tab: 0 = decide when drawn (Whole list if the text has fixes), 1 = One term, 2 = Whole list
    private var remember = true                   // "Remember for next chapters" (default on, like the mock)

    /** Called when the screen is opened from the Replace panel. */
    fun open() { tab = 1; sheet = 0; mode = 0 }

    /** Open straight on the Novels list (the old Novels menu item). */
    fun showNovels() { sheet = 3 }

    private val none = HashSet<String>()          // Row.key() values that are unticked in Whole list

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

    /** Filled accent button (Fix N, Fix all). */
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

    private fun checkTint(cb: CheckBox) { try { cb.buttonTintList = ColorStateList.valueOf(acc) } catch (_: Throwable) { } }

    /** A text box that opens the mini keyboard. which: 1 Wrong, 2 Correct, 12 search, 13 keep name. */
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
        t.setOnClickListener { v -> fb(v); if (which == 1 || which == 2) mode = 1; api.clearToast(); api.type(which) }
        return t
    }

    private fun spacer(): View { val v = View(ctx); v.layoutParams = LinearLayout.LayoutParams(0, 1, 1f); return v }

    private fun twoLines(top: String, sub: String, topBold: Boolean = false): LinearLayout {
        val g = LinearLayout(ctx)
        g.orientation = LinearLayout.VERTICAL
        val a = label(top, 15f, ink(), topBold)
        a.maxLines = 1; a.ellipsize = TextUtils.TruncateAt.END
        g.addView(a)
        if (sub.isNotEmpty()) {
            val s = label(sub, 12f, dim())
            s.maxLines = 1; s.ellipsize = TextUtils.TruncateAt.END
            g.addView(s)
        }
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

    private fun sub(text: String): TextView {
        val t = label(text.uppercase(), 11f, dim(), true)
        t.layoutParams = lp(MATCH, WRAP, 0f, 2, 6, 2, 4)
        return t
    }

    // ---------------------------------------------------------------- state of one build
    private class Term(val to: String, val forms: ArrayList<String>, val idx: ArrayList<Int>)

    private class St(
        val novel: String, val rows: List<FixNames.Row>, val protect: List<String>, val forms: List<String>, val to: String,
        val text: String, val shown: String, val listSc: FixNames.Scan?, val fieldSc: FixNames.Scan, val groups: List<Term>
    ) {
        /** Hits of one term in the text now (all its wrong forms), whatever its tick. */
        fun count(t: Term): Int {
            val c = listSc?.counts ?: return 0
            var n = 0
            for (i in t.idx) if (i < c.size) n += c[i]
            return n
        }
    }

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
        val m = LinkedHashMap<String, Term>()
        for (i in rows.indices) {
            val r = rows[i]
            val k = r.to.lowercase()
            val t = m.getOrPut(k) { Term(r.to, ArrayList<String>(), ArrayList<Int>()) }
            t.idx.add(i)
            for (f in r.forms) if (t.forms.none { it.equals(f, true) }) t.forms.add(f)
        }
        // the mock: the screen opens on Whole list when the text has fixes (and again after each fix / undo)
        if (listSc == null) mode = 1 else if (mode == 0) mode = if (listSc.hits > 0) 2 else 1
        return St(novel, rows, protect, forms, to, text, shown, listSc, fieldSc, ArrayList(m.values))
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
        // the middle part of every tab adds its own views to root; its list is a ScrollView straight in root (MgbIme.stretchToLetters gives the LAST one the spare height)
        when (tab) { 2 -> termsTab(root, st); 3 -> keepTab(root, st); else -> fixTab(root, st) }
        val t = toastRow()
        if (t != null) root.addView(t)
        return root
    }

    private fun header(st: St): View {
        val h = UiKit.row(ctx)
        h.gravity = Gravity.CENTER_VERTICAL
        h.addView(UiKit.btn(ctx, prefs, "<", 0f, 36, JabraTokens.T_BTN, { v -> fb(v) }) { api.back() })
        h.addView(label("Fix terms", 15f, ink(), true), lp(WRAP, WRAP, 0f, 6, 0, 0, 0))
        h.addView(spacer())
        // no novel yet: one clear "+ New novel" button; with a novel: its name (tap = novels list) and a visible "+" next to it
        h.addView(UiKit.chip(ctx, prefs, if (st.novel.isEmpty()) "+ New novel" else st.novel + "  ▾", true, 34, { v -> fb(v) }) { sheet = 3; api.refresh() })
        if (st.novel.isNotEmpty()) h.addView(UiKit.btn(ctx, prefs, "+", 0f, 36, JabraTokens.T_BTN, { v -> fb(v) }) { sheet = 3; api.refresh() })
        h.addView(UiKit.btn(ctx, prefs, "?", 0f, 36, JabraTokens.T_BTN, { v -> fb(v) }) { sheet = 4; api.refresh() })
        return h
    }

    /** The rounded container of the mock's tabs / toggle. */
    private fun segBar(): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.HORIZONTAL
        c.gravity = Gravity.CENTER_VERTICAL
        try { c.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
        c.setPadding(dp(3), dp(3), dp(3), dp(3))
        c.layoutParams = lp(MATCH, WRAP, 0f, 2, 4, 2, 6)
        return c
    }

    /** One flat button inside a segBar. on = filled (accent for tabs, soft accent for the toggle). */
    private fun segBtn(text: CharSequence, on: Boolean, soft: Boolean, click: (View) -> Unit): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = 14f
        t.gravity = Gravity.CENTER
        t.setTypeface(t.typeface, Typeface.BOLD)
        t.maxLines = 1
        t.isClickable = true
        if (on) t.background = pill(if (soft) JabraTokens.ACCENT_SOFT else acc, 10) else t.setBackgroundColor(Color.TRANSPARENT)
        t.setTextColor(if (on) (if (soft) JabraTokens.ON_ACCENT_SOFT else Color.WHITE) else dim())
        t.setOnClickListener { v -> fb(v); click(v) }
        return t
    }

    private fun tabs(st: St): View {
        val bar = segBar()
        val left = st.listSc?.hits ?: 0
        val names = listOf("Fix", "Terms", "Keep as is")
        val counts = listOf("", st.groups.size.toString(), st.protect.size.toString())
        for (i in 0..2) {
            val sb = SpannableStringBuilder(names[i])
            if (counts[i].isNotEmpty()) {
                sb.append(" ")
                val a = sb.length
                sb.append(counts[i])
                sb.setSpan(RelativeSizeSpan(0.85f), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val b = segBtn(sb, tab == i + 1, false) { tab = i + 1; api.clearToast(); api.refresh() }
            val fl = FrameLayout(ctx)
            fl.addView(b, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(36)))
            // red number on Fix = how many fixes are still left in the text
            if (i == 0 && left > 0 && tab != 1) {
                val d = label(left.toString(), 10f, Color.WHITE, true)
                d.gravity = Gravity.CENTER
                d.minWidth = dp(16)
                d.setPadding(dp(4), 0, dp(4), 0)
                d.background = pill(0xFFE5484D.toInt(), 8)
                val dl = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dp(16), Gravity.TOP or Gravity.END)
                dl.setMargins(0, dp(1), dp(3), 0)
                fl.addView(d, dl)
            }
            fl.layoutParams = lp(0, WRAP, 1f, 1, 0, 1, 0)
            bar.addView(fl)
        }
        return bar
    }

    // ---------------------------------------------------------------- live text box
    private fun docBox(st: St): View {
        val fl = FrameLayout(ctx)
        // adaptive: about 11% of the screen height, 64..104 dp (a short screen / landscape = a smaller box, more room for the list)
        val hDp = try { (ctx.resources.displayMetrics.heightPixels / ctx.resources.displayMetrics.density * 0.11f).toInt().coerceIn(64, 104) } catch (_: Throwable) { 88 }
        fl.layoutParams = lp(MATCH, dp(hDp), 0f, 0, 4, 0, 4)
        val sc = if (mode == 2 && st.listSc != null) st.listSc else st.fieldSc
        fl.addView(preview(st.shown, sc, st.text.isEmpty()), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        if (api.canUndo()) {
            val u = UiKit.chip(ctx, prefs, "Undo", false, 28, { v -> fb(v) }) { doUndo() }
            fl.addView(u, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
        }
        return fl
    }

    private fun doUndo() { mode = 0; api.undo() }

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

    // ---------------------------------------------------------------- toast with Undo (under the body)
    private fun toastRow(): View? {
        val raw = api.toast()
        if (raw.isEmpty()) return null
        val msg = raw.substringBefore(". Tap Undo")
        val bg = if (dark()) 0xFFE8ECF4.toInt() else 0xFF162033.toInt()
        val fg = if (dark()) 0xFF162033.toInt() else 0xFFFFFFFF.toInt()
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = pill(bg, 14)
        r.setPadding(dp(14), dp(4), dp(6), dp(4))
        r.layoutParams = lp(MATCH, WRAP, 0f, 2, 6, 2, 0)
        val t = label(msg, 13f, fg, true)
        t.maxLines = 2; t.ellipsize = TextUtils.TruncateAt.END
        r.addView(t, lp(0, WRAP, 1f))
        if (api.canUndo() && msg.startsWith("Fixed")) {
            val u = label("Undo", 13f, fg, true)
            u.gravity = Gravity.CENTER
            u.setPadding(dp(14), dp(6), dp(14), dp(6))
            u.background = outline(Color.TRANSPARENT, fg, 15)
            u.isClickable = true
            u.setOnClickListener { v -> fb(v); doUndo() }
            r.addView(u, lp(WRAP, WRAP))
        }
        return r
    }

    // ---------------------------------------------------------------- tab 1: Fix
    private fun seg(st: St, lm: Boolean): View {
        val bar = segBar()
        val hits = st.listSc?.hits ?: 0
        val a = segBtn("One term", !lm, true) { mode = 1; api.clearToast(); api.refresh() }
        val b = segBtn("Whole list" + (if (hits > 0) " · $hits" else ""), lm, true) { if (st.listSc != null) { mode = 2; api.clearToast(); api.refresh() } }
        a.layoutParams = lp(0, dp(34), 1f, 1, 0, 1, 0)
        b.layoutParams = lp(0, dp(34), 1f, 1, 0, 1, 0)
        bar.addView(a); bar.addView(b)
        return bar
    }

    private fun numField(n: Int, hint: String, value: String, which: Int): View {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        val c = label(n.toString(), 12f, JabraTokens.ON_ACCENT_SOFT, true)
        c.gravity = Gravity.CENTER
        c.background = pill(JabraTokens.ACCENT_SOFT, 11)
        r.addView(c, lp(dp(22), dp(22), 0f, 0, 0, 8, 0))
        r.addView(fieldBox(hint, value, which), lp(0, dp(42), 1f))
        r.layoutParams = lp(MATCH, WRAP, 0f, 2, 0, 2, 6)
        return r
    }

    /** A list area = a ScrollView straight in root. baseDp is its height when nobody stretches it. */
    private fun scrollArea(baseDp: Int): ScrollView {
        val sv = ScrollView(ctx)
        sv.layoutParams = lp(MATCH, dp(baseDp), 0f, 0, 0, 0, 6)
        return sv
    }

    private fun fixTab(root: LinearLayout, st: St) {
        val lm = mode == 2 && st.listSc != null
        val n = if (lm) (st.listSc?.hits ?: 0) else st.fieldSc.draftHits
        root.addView(seg(st, lm))

        var rule: com.mgb.keyboard.typing.ReplaceRules.Rule? = null
        var saveIt = false
        if (lm) {
            root.addView(wholeList(st))
        } else {
            val sv = scrollArea(112)
            val box = LinearLayout(ctx)
            box.orientation = LinearLayout.VERTICAL
            box.addView(numField(1, "Wrong term (what is in the text)", api.find(), 1))
            box.addView(numField(2, "Correct term", api.repl(), 2))
            val exists = st.novel.isNotEmpty() && st.forms.isNotEmpty() && st.to.isNotEmpty() &&
                st.rows.any { r -> r.to.equals(st.to, true) && st.forms.all { f -> r.forms.any { it.equals(f, true) } } }
            val canSave = st.novel.isNotEmpty() && st.forms.isNotEmpty() && st.to.isNotEmpty() && !exists
            if (canSave) {
                val cb = CheckBox(ctx)
                cb.text = "Remember for next chapters"
                cb.textSize = 13f
                cb.setTextColor(ink())
                cb.setTypeface(cb.typeface, Typeface.BOLD)
                cb.isChecked = remember
                checkTint(cb)
                cb.setOnCheckedChangeListener { _, c -> remember = c }
                cb.layoutParams = lp(MATCH, WRAP, 0f, 0, 0, 0, 4)
                box.addView(cb)
                saveIt = remember
            } else if (st.forms.isNotEmpty() && st.to.isNotEmpty() && st.novel.isEmpty()) {
                box.addView(hint("No novel yet: tap + New novel above to remember pairs."))
            } else if (exists) {
                box.addView(hint("Already in your list."))
            } else {
                box.addView(hint("Tip: tap a term in Terms to edit it. Several wrong spellings? Use ;"))
            }
            if (st.novel.isEmpty()) {
                box.addView(hint("No novel yet. Pairs are saved per novel."))
                val nn = UiKit.btn(ctx, prefs, "+ New novel", 0f, 40, JabraTokens.T_BTN, { v -> fb(v) }) { sheet = 3; api.refresh() }
                fill(nn)
                nn.layoutParams = lp(MATCH, dp(40), 0f, 2, 2, 2, 4)
                box.addView(nn)
            }
            rule = FixNames.draftRule(st.forms, st.to)
            sv.addView(box)
            root.addView(sv)
        }

        val btnText = when {
            lm -> if (n > 0) "Fix all · $n" else "Nothing to fix"
            n > 0 -> "Fix $n"
            st.forms.isNotEmpty() && st.to.isEmpty() -> "Type the correct term"
            st.forms.isNotEmpty() -> "Not found in text"
            else -> "Fix"
        }
        val r0 = rule
        val save = saveIt
        // the one primary button: pinned under the scroll area, never scrolls away
        val go = UiKit.btn(ctx, prefs, btnText, 0f, 48, JabraTokens.T_BODY, { v -> fb(v) }) {
            mode = 0
            if (lm) {
                api.fix(FixNames.activeRules(st.rows, none))
            } else if (r0 != null) {
                api.fixOne(r0, st.forms, st.to, save)
            }
        }
        fill(go, n > 0)
        go.layoutParams = lp(MATCH, dp(48), 0f, 0, 2, 0, 0)
        root.addView(go)
    }

    /** Whole list: the terms found in the text, tick = fix, untick = skip. */
    private fun wholeList(st: St): View {
        val sv = scrollArea(72)
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        var any = false
        for (t in st.groups) {
            val c = st.count(t)
            if (c <= 0) continue
            any = true
            val keys = t.idx.map { st.rows[it].key() }
            val on = keys.none { none.contains(it) }
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            try { row.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
            row.setPadding(dp(10), dp(6), dp(12), dp(6))
            row.layoutParams = lp(MATCH, WRAP, 0f, 0, 0, 0, 6)
            row.alpha = if (on) 1f else 0.55f
            val cb = CheckBox(ctx)
            cb.isChecked = on
            cb.isClickable = false
            cb.isFocusable = false
            checkTint(cb)
            row.addView(cb, lp(WRAP, WRAP, 0f, 0, 0, 6, 0))
            row.addView(twoLines(t.to, "← " + t.forms.joinToString(", "), true))
            val cnt = label(c.toString() + "×", 12f, JabraTokens.ON_ACCENT_SOFT, true)
            cnt.setPadding(dp(8), dp(2), dp(8), dp(2))
            cnt.background = pill(JabraTokens.ACCENT_SOFT, 12)
            row.addView(cnt, lp(WRAP, WRAP))
            row.isClickable = true
            row.setOnClickListener { v ->
                fb(v)
                if (on) none.addAll(keys) else none.removeAll(keys.toSet())
                api.refresh()
            }
            box.addView(row)
        }
        if (!any) {
            val e = label("Nothing from the list is left in this text.", 13f, dim())
            e.gravity = Gravity.CENTER
            e.setPadding(dp(10), dp(18), dp(10), dp(18))
            e.background = outline(Color.TRANSPARENT, strokeInk(), 12)
            box.addView(e)
        }
        sv.addView(box)
        return sv
    }

    // ---------------------------------------------------------------- tab 2: Terms
    private fun termsTab(root: LinearLayout, st: St) {
        val bar = UiKit.row(ctx)
        bar.gravity = Gravity.CENTER_VERTICAL
        val sb = fieldBox("Search terms", api.query(), 12)
        sb.layoutParams = lp(0, dp(42), 1f, 2, 0, 4, 0)
        bar.addView(sb)
        bar.addView(UiKit.btn(ctx, prefs, "+ Add", 0f, 42, JabraTokens.T_BTN, { v -> fb(v) }) { tab = 1; mode = 1; api.clearToast(); api.type(1) })
        bar.layoutParams = lp(MATCH, WRAP, 0f, 0, 0, 0, 4)
        root.addView(bar)

        val sv = scrollArea(72)
        val list = LinearLayout(ctx)
        list.orientation = LinearLayout.VERTICAL
        val q = api.query().trim().lowercase()
        val items = st.groups
            .filter { t -> q.isEmpty() || (t.to + " " + t.forms.joinToString(" ")).lowercase().indexOf(q) >= 0 }
            .map { t -> Pair(t, st.count(t)) }
            .sortedByDescending { it.second > 0 }       // terms that are in the text come first (stable sort)
        for (p in items) {
            val t = p.first
            val c = p.second
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            try { row.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
            row.setPadding(dp(12), dp(6), dp(10), dp(6))
            row.layoutParams = lp(MATCH, WRAP, 0f, 0, 0, 0, 6)
            val mid = LinearLayout(ctx)
            mid.orientation = LinearLayout.VERTICAL
            mid.addView(label(t.to, 15f, ink(), true))
            val w = label("← " + t.forms.joinToString(", "), 13f, dim())
            w.maxLines = 1; w.ellipsize = TextUtils.TruncateAt.END
            mid.addView(w)
            mid.layoutParams = lp(0, WRAP, 1f)
            mid.isClickable = true
            mid.setOnClickListener { v -> fb(v); mode = 1; tab = 1; api.loadPair(t.forms.joinToString("; "), t.to) }
            row.addView(mid)
            if (c > 0) {
                val rules = t.idx.map { st.rows[it].rule }
                val b = UiKit.btn(ctx, prefs, "Fix $c", 0f, 32, JabraTokens.T_BTN, { v -> fb(v) }) { mode = 0; api.fix(rules) }
                fill(b)
                try { b.background = pill(acc, 16) } catch (_: Throwable) { }   // round pill like the mock's small button
                row.addView(b, lp(WRAP, dp(32)))
            } else {
                row.alpha = 0.55f
                row.addView(label("not in text", 12f, dim()), lp(WRAP, WRAP))
            }
            list.addView(row)
        }
        if (items.isEmpty()) list.addView(hint(if (st.novel.isEmpty()) "No novel yet. Tap + New novel above." else if (q.isNotEmpty()) "No match." else "No terms yet. Add one in Fix with Remember on."))
        sv.addView(list)
        root.addView(sv)
    }

    // ---------------------------------------------------------------- tab 3: Keep as is
    /** Chips in rows of about 30 characters. */
    private fun flow(chips: List<Pair<View, Int>>): LinearLayout {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        var cur = UiKit.row(ctx)
        var used = 0
        for (c in chips) {
            if (used > 0 && used + c.second > 30) { box.addView(cur); cur = UiKit.row(ctx); used = 0 }
            cur.addView(c.first); used += c.second
        }
        box.addView(cur)
        return box
    }

    private fun keepTab(root: LinearLayout, st: St) {
        val bar = UiKit.row(ctx)
        bar.gravity = Gravity.CENTER_VERTICAL
        val kb = fieldBox("Name to keep as is", api.keepInput(), 13)
        kb.layoutParams = lp(0, dp(42), 1f, 2, 0, 4, 0)
        bar.addView(kb)
        bar.addView(UiKit.btn(ctx, prefs, "Add", 0f, 42, JabraTokens.T_BTN, { v -> fb(v) }) {
            val n = api.keepInput().trim()
            if (n.isEmpty()) api.type(13) else api.addKeep(n)
        })
        bar.layoutParams = lp(MATCH, WRAP, 0f, 0, 0, 0, 4)
        root.addView(bar)

        val sv = scrollArea(72)
        val all = LinearLayout(ctx)
        all.orientation = LinearLayout.VERTICAL
        if (st.protect.isEmpty()) all.addView(hint("Nothing kept yet."))
        else all.addView(flow(st.protect.map { k ->
            Pair<View, Int>(UiKit.chip(ctx, prefs, k + "  ✕", false, 34, { v -> fb(v) }) { api.removeLock(k) }, k.length + 5)
        }))

        // names found in the text that look like titles, one tap to keep them
        val seen = try {
            Regex("\\b(?:Master|Elder|Lord|Sect)\\s+[A-Z][a-z]+").findAll(st.text).map { it.value }.distinct()
                .filter { s -> st.protect.none { p -> p.contains(s, true) } }.take(8).toList()
        } catch (_: Throwable) { emptyList<String>() }
        if (seen.isNotEmpty()) {
            all.addView(sub("Seen in your text"))
            all.addView(flow(seen.map { s ->
                Pair<View, Int>(UiKit.chip(ctx, prefs, "+ $s", true, 34, { v -> fb(v) }) { api.addKeep(s) }, s.length + 4)
            }))
        }
        all.addView(hint("Replace never touches these, even when a rule matches part of them."))
        sv.addView(all)
        root.addView(sv)
    }

    // ---------------------------------------------------------------- sheets: Novels / How it works
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
            3 -> {
                root.addView(sheetHeader("Novels", 0))
                // new novel: type a name, tap Create (empty novel, active at once; add pairs in Fix with Remember on)
                val nb = UiKit.row(ctx)
                nb.gravity = Gravity.CENTER_VERTICAL
                val nf = fieldBox("New novel name", api.newNovelInput(), 14)
                nf.layoutParams = lp(0, dp(42), 1f, 2, 0, 4, 0)
                nb.addView(nf)
                nb.addView(UiKit.btn(ctx, prefs, "+ Create", 0f, 42, JabraTokens.T_BTN, { v -> fb(v) }) {
                    val nn = api.newNovelInput().trim()
                    if (nn.isEmpty()) api.type(14) else { sheet = 0; tab = 1; mode = 0; api.addNovel(nn) }
                })
                nb.layoutParams = lp(MATCH, WRAP, 0f, 0, 0, 0, 6)
                root.addView(nb)
                val list = api.novels()
                val sv = ScrollView(ctx)
                sv.layoutParams = lp(MATCH, dp(100))
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
                    row.setOnClickListener { v -> fb(v); sheet = 0; tab = 1; mode = 0; api.pickNovel(n.name) }
                    box.addView(row)
                }
                if (list.isEmpty()) box.addView(hint("No novel yet. Type a name above and tap Create."))
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
                root.addView(sheetHeader("How it works", 0))
                val c = card()
                c.addView(label(
                    "Whole list: tick the terms, one tap fixes them all.\n\n" +
                    "One term: type wrong and correct, tap Fix. Several wrong spellings: separate them with ;\n\n" +
                    "Remember: the pair is saved for the next chapters when you tap Fix.\n\n" +
                    "Novels: tap the novel name, type a name, + Create. Every novel keeps its own pairs.\n\n" +
                    "Keep as is: names that are never changed.", 13f, ink()))
                root.addView(c)
            }
        }
    }
}
