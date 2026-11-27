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
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.typing.FixNames
import com.mgb.keyboard.typing.NovelBook

/**
 * v0.57.0 (FX1): ONE SCREEN "Fix names, per novel" (design = Fix_names_per_novel_mock.html).
 * Tabs: Fix names | Novels. Fix names = novel bar + Undo, live preview (struck wrong form + green right form, dashed = the pair you are typing, gold + lock = never change),
 * "new pair" box (wrong forms split by ;, right form, topic chips, Add pair = already saved to the novel), search, filter chips, one list (pairs with hit count + on/off switch, locked names),
 * one big "Fix N in text" button. Novels = one row per novel (dot = active), Paste glossary, Delete.
 * Logic = typing/FixNames.java (tested). All actions go through [Api] (implemented by ReplacePanel with its old, tested code: undo, never-change list, glossary paste, delete).
 * Everything is built in try / catch by the caller; any error = the panel falls back to the old Pairs / Sets screens.
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
    }

    // state that lives as long as the panel (the mock keeps the same in memory)
    var tab = 1                                   // 1 = Fix names, 2 = Novels
    private var chip = NovelBook.ALL
    private var topic = ""
    private val off = HashSet<String>()           // switched-off rows (memory only, like the mock)

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

    // ---------------------------------------------------------------- small widgets
    private fun lp(w: Int, h: Int, weight: Float = 0f, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): LinearLayout.LayoutParams {
        val p = if (weight > 0f) LinearLayout.LayoutParams(w, h, weight) else LinearLayout.LayoutParams(w, h)
        p.setMargins(dp(l), dp(t), dp(r), dp(b))
        return p
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        try { c.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
        c.setPadding(dp(8), dp(8), dp(8), dp(8))
        return c
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(ctx)
        t.text = text; t.textSize = size; t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    private fun pill(color: Int, radiusDp: Int = 10): GradientDrawable { val g = GradientDrawable(); g.setColor(color); g.cornerRadius = dp(radiusDp).toFloat(); return g }

    private fun outline(fill: Int, stroke: Int, radiusDp: Int = 8): GradientDrawable {
        val g = GradientDrawable(); g.setColor(fill); g.cornerRadius = dp(radiusDp).toFloat(); g.setStroke(dp(1), stroke); return g
    }

    /** Filled accent button (Add pair, Fix N in text, Paste glossary): same idea as the old accentButton. */
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

    /** The on / off switch of a row (34 x 20 dp pill with a knob, like .sw in the mock). */
    private inner class Switch(val on: Boolean) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            p.color = if (on) acc else (if (dark()) 0xFF2B3A62.toInt() else 0xFFC4CBD9.toInt())
            c.drawRoundRect(0f, 0f, w, h, h / 2, h / 2, p)
            p.color = if (on) Color.WHITE else (if (dark()) 0xFFE8EDF8.toInt() else Color.WHITE)
            val r = h / 2 - dp(3)
            c.drawCircle(if (on) w - h / 2 else h / 2, h / 2, r, p)
        }
    }

    private fun hscroll(row: LinearLayout): HorizontalScrollView {
        val hs = HorizontalScrollView(ctx)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(row)
        return hs
    }

    private fun fieldBox(hint: String, value: String, which: Int): TextView {
        val t = TextView(ctx)
        t.text = if (value.isEmpty()) hint else value
        t.textSize = 14f
        t.setTextColor(if (value.isEmpty()) dim() else ink())
        t.maxLines = 1
        t.ellipsize = TextUtils.TruncateAt.END
        t.gravity = Gravity.CENTER_VERTICAL
        t.setPadding(dp(10), 0, dp(10), 0)
        t.background = outline(Color.TRANSPARENT, if (dark()) 0xFF2B3A62.toInt() else 0xFFC4CBD9.toInt())
        t.isClickable = true
        t.setOnClickListener { v -> fb(v); api.type(which) }
        return t
    }

    private fun spacer(): View { val v = View(ctx); v.layoutParams = LinearLayout.LayoutParams(0, 1, 1f); return v }

    // ---------------------------------------------------------------- the screen
    fun build(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        try { JabraThemes.paint(prefs, root) } catch (_: Throwable) { root.setBackgroundColor(Color.parseColor("#DDDDDD")) }
        root.setPadding(dp(4), dp(4), dp(4), dp(4))
        root.addView(tabs())
        root.addView(if (tab == 2) novelsTab() else fixTab())
        return root
    }

    private fun tabs(): View {
        val r = UiKit.row(ctx)
        val names = listOf("Fix names", "Novels")
        for (i in 0..1) {
            val b = UiKit.chip(ctx, prefs, names[i], tab == i + 1, 34, { v -> fb(v) }) { tab = i + 1; api.refresh() }
            b.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f, 2, 2, 2, 2)
            r.addView(b)
        }
        // header: back to the Replace screen
        val h = UiKit.row(ctx)
        val back = UiKit.btn(ctx, prefs, "<", 0f, 34, JabraTokens.T_BTN, { v -> fb(v) }) { api.back() }
        h.addView(back)
        r.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        h.addView(r)
        return h
    }

    private fun fixTab(): View {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val novel = api.novel()
        val rows = if (novel.isEmpty()) emptyList<FixNames.Row>() else FixNames.rows(novel, api.setNames(), api.setTexts())
        val protect = api.protect()
        val forms = FixNames.forms(api.find())
        val to = api.repl().trim()
        val text = api.text()
        val shown = if (text.length > 3000) text.substring(0, 3000) else text
        val sc = FixNames.scan(shown, rows, off, protect, forms, to)

        // bar: novel button + spacer + Undo
        val bar = UiKit.row(ctx)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.addView(UiKit.chip(ctx, prefs, (if (novel.isEmpty()) "Pick a novel" else novel) + "  \u25BE", novel.isNotEmpty(), 36, { v -> fb(v) }) { tab = 2; api.refresh() })
        bar.addView(spacer())
        val undo = UiKit.btn(ctx, prefs, "Undo", 0f, 36, JabraTokens.T_BTN, { v -> fb(v) }) { api.undo() }
        undo.isEnabled = api.canUndo(); undo.alpha = if (api.canUndo()) 1f else 0.45f
        bar.addView(undo)
        col.addView(bar)

        // preview
        col.addView(preview(shown, sc, text.isEmpty()))

        if (novel.isEmpty()) {
            col.addView(UiKit.noteText(ctx, prefs, "No novel yet. Tap Pick a novel above, then Paste glossary."))
            return col
        }

        // add box
        val add = card()
        add.layoutParams = lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, 0, 6, 0, 6)
        add.addView(fieldBox("Wrong forms, split by ;", api.find(), 1), lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(38)))
        if (forms.isNotEmpty()) {
            val vr = UiKit.row(ctx)
            for (f in forms) {
                val t = label(f, 12f, delInk())
                t.background = pill(delBg(), 6)
                t.setPadding(dp(7), dp(1), dp(7), dp(1))
                vr.addView(t, lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, 0, 0, 4, 0))
            }
            val hs = hscroll(vr)
            hs.layoutParams = lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, 0, 6, 0, 0)
            add.addView(hs)
        }
        add.addView(fieldBox("Right form", api.repl(), 2), lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(38), 0f, 0, 6, 0, 0))
        val choices = FixNames.topicChoices(rows)
        if (topic.isEmpty() || !choices.any { it.equals(topic, true) }) topic = choices[0]
        val ar = UiKit.row(ctx)
        ar.gravity = Gravity.CENTER_VERTICAL
        val tr = UiKit.row(ctx)
        for (t in choices) tr.addView(UiKit.chip(ctx, prefs, t, t.equals(topic, true), 32, { v -> fb(v) }) { topic = t; api.refresh() })
        val ths = hscroll(tr)
        ths.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        ar.addView(ths)
        val addB = UiKit.btn(ctx, prefs, "Add pair", 0f, 38, JabraTokens.T_BTN, { v -> fb(v) }) { api.addPair(forms, to, topic) }
        fill(addB)
        ar.addView(addB)
        ar.layoutParams = lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, 0, 6, 0, 0)
        add.addView(ar)
        col.addView(add)

        // search + filter chips
        col.addView(fieldBox("Search this novel", api.query(), 12), lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(38)))
        val chips = ArrayList<String>()
        chips.add(NovelBook.ALL)
        for (t in FixNames.topicsOf(rows)) chips.add(t)
        if (protect.isNotEmpty()) chips.add("Locked")
        if (chips.none { it.equals(chip, true) }) chip = NovelBook.ALL
        val cr = UiKit.row(ctx)
        for (t in chips) {
            val n = if (t == "Locked") protect.size else FixNames.countFor(rows, t)
            cr.addView(UiKit.chip(ctx, prefs, t + " " + n, t.equals(chip, true), 32, { v -> fb(v) }) { chip = t; api.refresh() })
        }
        val chs = hscroll(cr)
        chs.layoutParams = lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, 0, 6, 0, 0)
        col.addView(chs)

        // the one list
        val sv = ScrollView(ctx)
        sv.layoutParams = lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(130), 0f, 0, 4, 0, 0)
        val list = LinearLayout(ctx)
        list.orientation = LinearLayout.VERTICAL
        val q = api.query().trim().lowercase()
        if (chip == "Locked") {
            for (l in protect) if (q.isEmpty() || l.lowercase().contains(q)) list.addView(lockRow(l))
        } else {
            val vis = FixNames.visible(rows, sc.counts, chip, q)
            for (i in vis) list.addView(pairRow(rows[i], if (i < sc.counts.size) sc.counts[i] else 0, chip == NovelBook.ALL))
            if (vis.isEmpty()) list.addView(UiKit.noteText(ctx, prefs, "Nothing here yet. Type wrong forms above, then tap Add pair."))
        }
        sv.addView(list)
        col.addView(sv)

        // the one big button
        val fixB = UiKit.btn(ctx, prefs, FixNames.fixLabel(sc.hits), 0f, 44, JabraTokens.T_BODY, { v -> fb(v) }) { api.fix(FixNames.activeRules(rows, off)) }
        fill(fixB, sc.hits > 0)
        fixB.layoutParams = lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(44), 0f, 0, 6, 0, 0)
        col.addView(fixB)
        if (api.toast().isNotEmpty()) col.addView(UiKit.noteText(ctx, prefs, api.toast()))
        return col
    }

    private fun preview(shown: String, sc: FixNames.Scan, empty: Boolean): View {
        val sv = ScrollView(ctx)
        sv.layoutParams = lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(96), 0f, 0, 0, 0, 6)
        val t = TextView(ctx)
        t.textSize = 14f
        t.setLineSpacing(0f, 1.35f)
        t.setTextColor(ink())
        try { t.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
        t.setPadding(dp(10), dp(8), dp(10), dp(8))
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
                sb.append(shown.substring(s.start, s.end)).append(" \uD83D\uDD12")
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
                if (s.draft) {   // the pair you are typing: underlined + bold (the mock draws a dashed outline)
                    sb.setSpan(UnderlineSpan(), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), c, d, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            pos = s.end
        }
        if (pos < shown.length) sb.append(shown.substring(pos))
        t.text = sb
        sv.addView(t)
        return sv
    }

    private fun rowBox(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        try { r.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
        r.setPadding(dp(10), dp(6), dp(10), dp(6))
        r.layoutParams = lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, 0, 0, 0, 4)
        return r
    }

    private fun twoLines(top: String, sub: String): LinearLayout {
        val g = LinearLayout(ctx)
        g.orientation = LinearLayout.VERTICAL
        val a = label(top, 14f, ink())
        a.maxLines = 1; a.ellipsize = TextUtils.TruncateAt.END
        g.addView(a)
        if (sub.isNotEmpty()) g.addView(label(sub, 12f, dim()))
        g.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        return g
    }

    private fun pairRow(r: FixNames.Row, count: Int, withTopic: Boolean): View {
        val row = rowBox()
        val isOff = off.contains(r.key())
        if (isOff || count == 0) row.alpha = 0.5f
        row.addView(twoLines(r.formsText(), "to " + r.to + (if (withTopic) " \u00B7 " + r.topic else "")))
        val cnt = label(count.toString(), 12f, if (count == 0) dim() else Color.WHITE, true)
        cnt.gravity = Gravity.CENTER
        cnt.minWidth = dp(24)
        cnt.setPadding(dp(8), 0, dp(8), 0)
        if (count > 0) cnt.background = pill(acc, 10)
        row.addView(cnt, lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, 8, 0, 8, 0))
        val sw = Switch(!isOff)
        sw.layoutParams = lp(dp(34), dp(20))
        sw.minimumHeight = dp(44)
        sw.isClickable = true
        sw.setOnClickListener { v -> fb(v); if (!off.remove(r.key())) off.add(r.key()); api.refresh() }
        row.addView(sw)
        return row
    }

    private fun lockRow(term: String): View {
        val row = rowBox()
        row.addView(twoLines(term, "never changed"))
        val x = UiKit.btn(ctx, prefs, "Remove", 0f, 32, JabraTokens.T_BTN, { v -> fb(v) }) { api.removeLock(term) }
        row.addView(x)
        return row
    }

    // ---------------------------------------------------------------- Novels tab
    private fun novelsTab(): View {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val bar = UiKit.row(ctx)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.addView(label("Novels", 14f, ink(), true), lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, 6, 0, 0, 0))
        bar.addView(spacer())
        val paste = UiKit.btn(ctx, prefs, "Paste glossary", 0f, 38, JabraTokens.T_BTN, { v -> fb(v) }) { api.pasteGlossary() }
        fill(paste)
        bar.addView(paste)
        bar.layoutParams = lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, 0, 6, 0, 6)
        col.addView(bar)
        val list = api.novels()
        val sv = ScrollView(ctx)
        sv.layoutParams = lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(170))
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        for (n in list) {
            val row = rowBox()
            if (n.active) { try { row.background = outline(if (dark()) 0xFF1B2745.toInt() else 0xFFEAF1FB.toInt(), acc, 10) } catch (_: Throwable) { } }
            val dot = View(ctx)
            dot.background = if (n.active) pill(acc, 6) else outline(Color.TRANSPARENT, dim(), 6)
            row.addView(dot, lp(dp(12), dp(12), 0f, 0, 0, 8, 0))
            row.addView(twoLines(n.name, n.pairs.toString() + (if (n.pairs == 1) " pair" else " pairs") + " \u00B7 " + n.locked + " locked"))
            row.isClickable = true
            row.setOnClickListener { v -> fb(v); api.pickNovel(n.name) }
            box.addView(row)
        }
        if (list.isEmpty()) box.addView(UiKit.noteText(ctx, prefs, "No novel yet. In the plugin tap Glossary > Copy for keyboard, then tap Paste glossary here."))
        sv.addView(box)
        col.addView(sv)
        val cur = api.novel()
        if (cur.isNotEmpty()) {
            val del = UiKit.btn(ctx, prefs, if (api.deleteAsk()) "Sure? Delete " + cur else "Delete " + cur, 0f, 36, JabraTokens.T_BTN, { v -> fb(v) }) { api.deleteNovel() }
            del.setTextColor(redInk())
            col.addView(del, lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, 0, 4, 0, 0))
        }
        if (api.toast().isNotEmpty()) col.addView(UiKit.noteText(ctx, prefs, api.toast()))
        return col
    }
}
