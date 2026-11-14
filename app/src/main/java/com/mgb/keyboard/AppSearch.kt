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
import com.mgb.keyboard.typing.AppTokens
import com.mgb.keyboard.typing.HomePlan
import com.mgb.keyboard.typing.PagePlan
import com.mgb.keyboard.typing.PersonalPlan
import com.mgb.keyboard.typing.SettingsIndex

/** v0.13.0 (M3): [view] = search box + results (goes under the header of Home), [lists] = recently changed + changed from default (goes at the end of Home),
 *  [bindRest] = the other Home views that are hidden while a search text is typed, [refresh] = rebuild the lists (Home shown again / a setting changed). */
class AppSearchView(val view: View, val lists: View, val favs: View, val bindRest: (List<View>) -> Unit, val refresh: () -> Unit, val clearSearch: () -> Boolean = { false }, val searchOn: () -> Boolean = { false })   // v0.17.83 (U7): clearSearch = Back clears the typed text / the Changed filter first (true = something was cleared)   // v0.15.0 (M5): favs = pinned settings (Home puts it right under the search box)

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
        field.minHeight = dp(AppTokens.TOUCH)
        field.contentDescription = "Search all settings"
        box.addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val results = LinearLayout(act)
        results.orientation = LinearLayout.VERTICAL
        results.visibility = View.GONE
        box.addView(results)

        // ---- v0.17.83 (U7): "Changed (N)" filter chip. It sits AFTER the results in the box, so AppHome (moves child 1 = results) leaves it in the sticky search line ----
        var changedOnly = false
        val chipRow = LinearLayout(act)
        chipRow.orientation = LinearLayout.HORIZONTAL
        chipRow.setPadding(0, dp(AppTokens.S1), 0, 0)
        box.addView(chipRow)

        val lists = LinearLayout(act)
        lists.orientation = LinearLayout.VERTICAL

        val favs = LinearLayout(act)   // v0.15.0 (M5)
        favs.orientation = LinearLayout.VERTICAL
        var onFav: () -> Unit = { }    // v0.15.0 (M5): set below; redraws the lists + open results after a pin / unpin

        var rest: List<View> = emptyList()
        var changedOpen = false

        fun changedKeys(): Set<String> {   // v0.17.83 (U7)
            val out = HashSet<String>()
            for (r in sink.rows) { try { if (r.changed()) out.add(r.key) } catch (_: Throwable) { } }
            return out
        }
        fun isActive(): Boolean = HomePlan.searchActive(field.text?.toString() ?: "", changedOnly)

        var onChip: () -> Unit = { }
        fun drawChip() {   // v0.17.83 (U7): one toggle pill, selected = filter ON
            safe {
                chipRow.removeAllViews()
                val n = changedKeys().size
                val chip = AppUi.pill(act, HomePlan.chipLabel(n), changedOnly)
                chip.minHeight = dp(AppTokens.TOUCH)
                chip.isClickable = true
                chip.isFocusable = true
                chip.contentDescription = HomePlan.chipLabel(n) + (if (changedOnly) ", on" else ", off")
                AppA11y.asButton(chip)
                chip.setOnClickListener { safe { changedOnly = !changedOnly; drawChip(); onChip() } }
                chipRow.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }

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
            t.setPadding(0, dp(if (accent) 12 else 16), 0, dp(AppTokens.S1))
            safe { t.isAccessibilityHeading = true }
            return t
        }

        /** One result / list row: title on the left, value on the right, 48 dp, card background. */
        fun rowView(r: SinkRow, showPage: Boolean): View {
            val line = LinearLayout(act)
            line.orientation = LinearLayout.HORIZONTAL
            line.gravity = android.view.Gravity.CENTER_VERTICAL
            line.setPadding(dp(AppTokens.S3), dp(6), dp(AppTokens.S3), dp(6))
            line.minimumHeight = dp(AppTokens.TOUCH)
            safe { line.background = AppUi.cardBg(act, 10) }
            line.isClickable = true
            line.isFocusable = true
            val tt = TextView(act)
            tt.text = r.title
            tt.textSize = AppTokens.T_ROW.toFloat()
            safe { tt.setTextColor(AppUi.text(act)) }
            val left = LinearLayout(act)
            left.orientation = LinearLayout.VERTICAL
            left.addView(tt)
            if (showPage) {
                val pg = TextView(act)
                pg.text = PagePlan.title(PagePlan.pageOfGroup(r.group))
                pg.textSize = AppTokens.T_CAPTION.toFloat()
                safe { pg.setTextColor(AppUi.dim(act)) }
                left.addView(pg)
            }
            line.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val v = try { r.value() } catch (_: Throwable) { "" }
            val vt = TextView(act)
            vt.text = v
            vt.textSize = AppTokens.T_LABEL.toFloat()
            vt.setPadding(dp(AppTokens.S2), 0, 0, 0)
            val ch = try { r.changed() } catch (_: Throwable) { false }
            safe { vt.setTextColor(if (ch) AppUi.accent(act) else AppUi.dim(act)) }
            if (ch) vt.setTypeface(null, Typeface.BOLD)
            line.addView(vt)
            safe {   // v0.15.0 (M5): star = pin / unpin this setting on Home
                val pinned = PersonalPlan.isFav(prefs.str(PersonalPlan.PREF_FAVS, ""), r.key)
                val star = Button(act)
                star.isAllCaps = false
                star.background = null
                star.text = if (pinned) "\u2605" else "\u2606"
                star.textSize = AppTokens.T_HEAD.toFloat()
                star.minWidth = dp(AppTokens.TOUCH); star.minimumWidth = dp(AppTokens.TOUCH)
                star.minHeight = dp(AppTokens.TOUCH); star.minimumHeight = dp(AppTokens.TOUCH)
                safe { star.setTextColor(if (pinned) AppUi.accent(act) else AppUi.dim(act)) }
                star.contentDescription = PersonalPlan.pinLabel(r.title, pinned)
                star.setOnClickListener {
                    safe {
                        val raw = prefs.str(PersonalPlan.PREF_FAVS, "")
                        val keys = sink.rows.map { it.key }
                        if (!PersonalPlan.canPin(raw, r.key, keys)) {
                            android.widget.Toast.makeText(act, PersonalPlan.fullNote(), android.widget.Toast.LENGTH_SHORT).show()
                            return@safe
                        }
                        prefs.setStr(PersonalPlan.PREF_FAVS, PersonalPlan.toggleFav(raw, r.key, keys))
                        onFav()
                    }
                }
                line.addView(star)
            }
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
            safe { val was = results.visibility == View.VISIBLE; results.visibility = if (on) View.VISIBLE else View.GONE; if (on && !was) AppMotion.fadeIn(results, AppTokens.FADE_MS, false) }   // v0.14.0 (M4)
        }

        fun renderResults(q: String) {
            results.removeAllViews()
            if (!HomePlan.searchActive(q, changedOnly)) { setActive(false); return }   // v0.17.83 (U7): the Changed filter alone also opens the results
            setActive(true)
            val entries = ArrayList<SettingsIndex.Entry>()
            for (r in sink.rows) entries.add(SettingsIndex.Entry(r.key, r.title, r.help, r.group))
            val groups = HomePlan.searchFiltered(entries, q, if (changedOnly) changedKeys() else null, changedOnly)
            val n = HomePlan.count(groups)
            val info = TextView(act)
            info.text = HomePlan.resultLine(n, q, changedOnly)
            info.textSize = AppTokens.T_CAPTION.toFloat()
            safe { info.setTextColor(AppUi.dim(act)) }
            info.setPadding(0, dp(AppTokens.S2), 0, 0)
            info.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            results.addView(info)
            if (n == 0) {   // v0.17.83 (U7): empty state = what to do next + tap-to-search words
                val help = TextView(act)
                help.text = HomePlan.emptyHelp(q, changedOnly)
                help.textSize = AppTokens.T_CAPTION.toFloat()
                safe { help.setTextColor(AppUi.dim(act)) }
                help.setPadding(0, dp(AppTokens.S1), 0, dp(AppTokens.S1))
                results.addView(help)
                if (!(changedOnly && q.trim().isEmpty())) {
                    val sc = HorizontalScrollView(act)
                    sc.isHorizontalScrollBarEnabled = false
                    val row = LinearLayout(act)
                    row.orientation = LinearLayout.HORIZONTAL
                    for (w in HomePlan.SUGGEST) {
                        val c = AppUi.pill(act, w, false)
                        c.minHeight = dp(AppTokens.TOUCH)
                        c.isClickable = true
                        c.isFocusable = true
                        c.contentDescription = "Search " + w
                        AppA11y.asButton(c)
                        c.setOnClickListener { safe { field.setText(w); field.setSelection(w.length) } }
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        lp.rightMargin = dp(AppTokens.S2)
                        row.addView(c, lp)
                    }
                    sc.addView(row)
                    results.addView(sc)
                }
                return
            }
            for (g in groups) {
                results.addView(label(PagePlan.title(g.page), true))
                for (e in g.items) {
                    val r = sink.rows.firstOrNull { it.key == e.key } ?: continue
                    results.addView(rowView(r, false))
                }
            }
        }

        onChip = { safe { renderResults(field.text?.toString() ?: "") } }
        drawChip()

        field.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { safe { renderResults(s?.toString() ?: "") } }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) { }
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { }
        })
        field.setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_SEARCH) { hideKeyboard(); true } else false }

        // ---- v0.15.0 (M5): favourites on Home (pinned settings, tap = jump to the row; the star unpins) ----
        fun refreshFavs() {
            favs.removeAllViews()
            val keys = sink.rows.map { it.key }
            if (keys.isEmpty()) return
            val list = PersonalPlan.favs(prefs.str(PersonalPlan.PREF_FAVS, ""), keys)
            if (list.isEmpty()) {
                val hint = TextView(act)
                hint.text = PersonalPlan.favHint()
                hint.textSize = AppTokens.T_CAPTION.toFloat()
                safe { hint.setTextColor(AppUi.dim(act)) }
                hint.setPadding(0, dp(AppTokens.S2), 0, 0)
                favs.addView(hint)
                return
            }
            favs.addView(label(PersonalPlan.favTitle(list.size), false))
            for (k in list) { val r = sink.rows.firstOrNull { it.key == k } ?: continue; favs.addView(rowView(r, true)) }
        }

        // ---- lists under the tiles: recently changed (chips) + changed from default (collapsed) ----
        fun refreshLists() {
            lists.removeAllViews()
            safe { refreshFavs() }
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
                    b.textSize = AppTokens.T_CAPTION.toFloat()
                    b.minHeight = dp(AppTokens.TOUCH); b.minimumHeight = dp(AppTokens.TOUCH); b.minWidth = 0; b.minimumWidth = 0
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
                head.minHeight = dp(AppTokens.TOUCH); head.minimumHeight = dp(AppTokens.TOUCH)
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
        onFav = {
            safe { refreshLists() }
            safe { if (isActive()) renderResults(field.text.toString()) }   // stars in open results follow
        }

        // v0.17.83 (U7): Back on Home with a search in use = clear it (text + Changed filter), close the keyboard, show Home again
        val clear: () -> Boolean = {
            var did = false
            safe {
                if (isActive()) {
                    did = true
                    changedOnly = false
                    field.setText("")   // the TextWatcher re-renders (= closes the results)
                    renderResults("")
                    drawChip()
                    hideKeyboard()
                    field.clearFocus()
                }
            }
            did
        }

        return AppSearchView(box, lists, favs, { l -> rest = l; safe { setActive(isActive()) } }, {
            safe { refreshLists() }
            safe { drawChip() }   // the count follows a changed setting
            safe { if (isActive()) renderResults(field.text.toString()) }   // values / dots in open results follow
        }, clear, { isActive() })
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
