package com.mgb.keyboard

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.HomePlan
import com.mgb.keyboard.typing.PagePlan
import com.mgb.keyboard.typing.SettingsIndex

/** v0.13.0 (M3): [view] = search box + results (goes under the header of Home), [lists] = recently changed + changed from default (goes at the end of Home),
 *  [bindRest] = the other Home views that are hidden while a search text is typed, [refresh] = rebuild the lists (Home shown again / a setting changed). */
class AppSearchView(val view: View, val lists: View, val bindRest: (List<View>) -> Unit, val refresh: () -> Unit)

/**
 * v0.13.0 (M3 of the "App UI" plan): ONE search for all settings on Home, grouped by page. Tap a result = open its page, scroll to the row, flash it.
 * Data = PageSink.rows (the same rows the pages show; filled by SettingsGroups.addRowNew), rules = typing/HomePlan.java (pure, tested).
 * Never throws out of build(): the caller then builds Home without search.
 */
object AppSearch {

    fun build(act: SettingsActivity, sink: PageSink, open: (String, View?) -> Unit): AppSearchView {
        val prefs = JabraPrefs(act)
        val d = act.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * d).toInt()
        fun safe(b: () -> Unit) { try { b() } catch (_: Throwable) { } }

        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        // the search box must not grab the first focus when the app opens (no keyboard pop-up): the box itself takes it, TalkBack skips the box
        box.isFocusable = true
        box.isFocusableInTouchMode = true
        box.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

        val field = EditText(act)
        field.hint = "Search all settings (vibrate, theme, gap...)"
        field.isSingleLine = true
        field.imeOptions = EditorInfo.IME_ACTION_SEARCH
        field.minHeight = dp(48)
        field.contentDescription = "Search all settings"
        box.addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val results = LinearLayout(act)
        results.orientation = LinearLayout.VERTICAL
        results.visibility = View.GONE
        box.addView(results)

        val lists = LinearLayout(act)
        lists.orientation = LinearLayout.VERTICAL

        var rest: List<View> = emptyList()
        var changedOpen = false

        fun hideKeyboard() {
            safe { (act.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(field.windowToken, 0) }
        }

        fun go(r: SinkRow) {
            safe { hideKeyboard() }
            safe { open(PagePlan.pageOfGroup(r.group), r.view) }
        }

        fun label(text: String, accent: Boolean): TextView {
            val t = TextView(act)
            t.text = text
            t.textSize = if (accent) 13f else 16f
            t.setTypeface(null, Typeface.BOLD)
            safe { t.setTextColor(if (accent) AppUi.accent(act) else AppUi.text(act)) }
            t.setPadding(0, dp(if (accent) 12 else 16), 0, dp(4))
            safe { t.isAccessibilityHeading = true }
            return t
        }

        /** One result / list row: title on the left, value on the right, 48 dp, card background. */
        fun rowView(r: SinkRow, showPage: Boolean): View {
            val line = LinearLayout(act)
            line.orientation = LinearLayout.HORIZONTAL
            line.gravity = android.view.Gravity.CENTER_VERTICAL
            line.setPadding(dp(12), dp(6), dp(12), dp(6))
            line.minimumHeight = dp(48)
            safe { line.background = AppUi.cardBg(act, 10) }
            line.isClickable = true
            line.isFocusable = true
            val tt = TextView(act)
            tt.text = r.title
            tt.textSize = 15f
            safe { tt.setTextColor(AppUi.text(act)) }
            val left = LinearLayout(act)
            left.orientation = LinearLayout.VERTICAL
            left.addView(tt)
            if (showPage) {
                val pg = TextView(act)
                pg.text = PagePlan.title(PagePlan.pageOfGroup(r.group))
                pg.textSize = 12f
                safe { pg.setTextColor(AppUi.dim(act)) }
                left.addView(pg)
            }
            line.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val v = try { r.value() } catch (_: Throwable) { "" }
            val vt = TextView(act)
            vt.text = v
            vt.textSize = 13f
            vt.setPadding(dp(8), 0, 0, 0)
            val ch = try { r.changed() } catch (_: Throwable) { false }
            safe { vt.setTextColor(if (ch) AppUi.accent(act) else AppUi.dim(act)) }
            if (ch) vt.setTypeface(null, Typeface.BOLD)
            line.addView(vt)
            line.contentDescription = HomePlan.withValue(r.title, v) + ", " + PagePlan.title(PagePlan.pageOfGroup(r.group)) + (if (ch) ", changed" else "")
            AppA11y.asButton(line)
            line.setOnClickListener { go(r) }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, dp(2), 0, dp(2))
            line.layoutParams = lp
            return line
        }

        fun setActive(on: Boolean) {
            for (v in rest) safe { v.visibility = if (on) View.GONE else View.VISIBLE }
            safe { results.visibility = if (on) View.VISIBLE else View.GONE }
        }

        fun renderResults(q: String) {
            results.removeAllViews()
            if (q.trim().isEmpty()) { setActive(false); return }
            setActive(true)
            val entries = ArrayList<SettingsIndex.Entry>()
            for (r in sink.rows) entries.add(SettingsIndex.Entry(r.key, r.title, r.help, r.group))
            val groups = HomePlan.searchByPage(entries, q)
            val n = HomePlan.count(groups)
            val info = TextView(act)
            info.text = HomePlan.resultLine(n, q)
            info.textSize = 12f
            safe { info.setTextColor(AppUi.dim(act)) }
            info.setPadding(0, dp(8), 0, 0)
            info.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            results.addView(info)
            for (g in groups) {
                results.addView(label(PagePlan.title(g.page), true))
                for (e in g.items) {
                    val r = sink.rows.firstOrNull { it.key == e.key } ?: continue
                    results.addView(rowView(r, false))
                }
            }
        }

        field.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { safe { renderResults(s?.toString() ?: "") } }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) { }
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { }
        })
        field.setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_SEARCH) { hideKeyboard(); true } else false }

        // ---- lists under the tiles: recently changed (chips) + changed from default (collapsed) ----
        fun refreshLists() {
            lists.removeAllViews()
            val keys = sink.rows.map { it.key }
            val recent = HomePlan.recentKeys(prefs.str("set_recent", ""), keys)
            if (recent.isNotEmpty()) {
                lists.addView(label("Recently changed", false))
                val sc = HorizontalScrollView(act)
                sc.isHorizontalScrollBarEnabled = false
                val row = LinearLayout(act)
                row.orientation = LinearLayout.HORIZONTAL
                for (k in recent) {
                    val r = sink.rows.firstOrNull { it.key == k } ?: continue
                    val b = Button(act)
                    b.isAllCaps = false
                    b.text = r.title
                    b.textSize = 12f
                    b.minHeight = dp(48); b.minimumHeight = dp(48); b.minWidth = 0; b.minimumWidth = 0
                    b.contentDescription = "Open " + r.title
                    b.setOnClickListener { go(r) }
                    row.addView(b)
                }
                sc.addView(row)
                lists.addView(sc)
            }
            val ch = sink.rows.filter { try { it.changed() } catch (_: Throwable) { false } }
            val order = HomePlan.changedKeys(ch.map { it.key }, BooleanArray(ch.size) { true })   // keep registry (page) order
            if (order.isNotEmpty()) {
                val head = Button(act)
                head.isAllCaps = false
                head.text = HomePlan.changedTitle(order.size, changedOpen)
                head.gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                head.minHeight = dp(48); head.minimumHeight = dp(48)
                head.contentDescription = "Changed from default, " + order.size + (if (changedOpen) ", expanded" else ", collapsed")
                lists.addView(head)
                val body = LinearLayout(act)
                body.orientation = LinearLayout.VERTICAL
                body.visibility = if (changedOpen) View.VISIBLE else View.GONE
                for (k in order) { val r = ch.firstOrNull { it.key == k } ?: continue; body.addView(rowView(r, true)) }
                lists.addView(body)
                head.setOnClickListener {
                    safe {
                        changedOpen = !changedOpen
                        body.visibility = if (changedOpen) View.VISIBLE else View.GONE
                        head.text = HomePlan.changedTitle(order.size, changedOpen)
                    }
                }
            }
        }
        refreshLists()

        return AppSearchView(box, lists, { l -> rest = l; safe { setActive(field.text.toString().trim().isNotEmpty()) } }) {
            safe { refreshLists() }
            safe { if (field.text.toString().trim().isNotEmpty()) renderResults(field.text.toString()) }   // values / dots in open results follow
        }
    }
}

/** v0.13.0 (M3): small TalkBack helpers. */
object AppA11y {
    /** Makes a clickable layout announce itself as a button ("title, button"). Any problem = left as it is. */
    fun asButton(v: View) {
        try {
            v.accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = Button::class.java.name
                }
            }
        } catch (_: Throwable) { }
    }
}
