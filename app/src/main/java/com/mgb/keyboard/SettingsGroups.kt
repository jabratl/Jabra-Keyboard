package com.mgb.keyboard

import android.content.Context
import android.view.View
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.ChangedPlan
import com.mgb.keyboard.typing.SettingsIndex
import com.mgb.keyboard.typing.TypingLevels

/**
 * v0.1.7 settings page in 5 groups (Ridmik-like), shown on the launcher screen (SettingsActivity).
 * It only reads / writes the SAME pref keys and defaults as the in-keyboard panels (gear, More settings, Typing options),
 * so both places always agree. Nothing is renamed. Built in code; build() is called inside try/catch by the hook,
 * so on any problem the launcher screen looks exactly like v0.1.6.
 * Changes are read by the keyboard the next time it opens (or after "ABC (apply)" inside the keyboard).
 */
object SettingsGroups {

    fun build(ctx: Context, link: PreviewLink? = null, sink: PageSink? = null): View {   // v0.10.25: link = live preview card (null = no preview, exactly v0.10.24); v0.11.0 (M1): sink != null = groups are handed to the new app pages instead of the accordion (same rows, same registry)
        val prefs = JabraPrefs(ctx)
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL

        val intro = TextView(ctx)
        intro.text = "Keyboard settings (same settings as inside the keyboard). Changes apply when the keyboard opens next time."
        intro.setPadding(0, dp(12), 0, dp(4))
        root.addView(intro)

        fun onOff(b: Boolean) = if (b) "ON" else "OFF"

        fun btn(label: String, onClick: (Button) -> Unit): Button {
            val b = Button(ctx)
            b.text = label
            b.isAllCaps = false
            b.setOnClickListener { try { onClick(b) } catch (_: Throwable) { } }
            return b
        }

        // ---- v0.4.2: row registry = search, recently changed, help line, reset group, undo last (all inside this file) ----
        class Row(val key: String, val title: String, val group: String, val wrap: View,
                  val refresh: () -> Unit, val setIdx: (Int) -> Unit, val cur: () -> Int, val def: Int,
                  val sec: String = "", val changed: () -> Boolean = { false }, val raw: (() -> Int)? = null)   // v0.15.8 (N8): section + changed rule + saved value (new app rows only)
        class Grp(val name: String, val head: Button, val body: LinearLayout) {
            var isOpen = false
            val rows = ArrayList<Row>()
            var resetBtn: Button? = null
        }
        val rows = ArrayList<Row>()
        val groups = ArrayList<Grp>()
        var curGrp: Grp? = null

        val search = EditText(ctx)
        search.hint = "Search settings (vibrate, theme, clipboard...)"
        search.isSingleLine = true
        val undoBtn = Button(ctx)
        undoBtn.isAllCaps = false
        undoBtn.visibility = View.GONE
        val recentLabel = TextView(ctx)
        recentLabel.text = "Recently changed (tap to find):"
        recentLabel.setPadding(0, dp(6), 0, 0)
        recentLabel.visibility = View.GONE
        val recentScroll = HorizontalScrollView(ctx)
        val recentRow = LinearLayout(ctx)
        recentRow.orientation = LinearLayout.HORIZONTAL
        recentScroll.addView(recentRow)
        recentScroll.visibility = View.GONE
        root.addView(search)
        root.addView(undoBtn)
        root.addView(recentLabel)
        root.addView(recentScroll)

        fun rowByKey(k: String): Row? = rows.firstOrNull { it.key == k }

        // ---- v0.12.0 (M2): helpers of the new rows (used only when sink != null) ----
        val dotUpdaters = HashMap<String, () -> Unit>()   // key -> redraw the "changed" dot of that row
        val secBoxes = HashMap<String, LinearLayout>()    // section title -> box of the group being built
        val secList = ArrayList<Pair<View, LinearLayout>>()   // section header + its box (header hides when the box has no visible row)
        var searchOn = false   // v0.15.8 (N8): the section Reset buttons hide while searching
        var bulk: List<Pair<Row, Int>>? = null   // v0.15.8 (N8): rows + values before the last "Reset this section / page" (Undo button text + restore)
        var bulkName = ""
        fun secVis() {
            for ((hd, bx) in secList) {
                var any = false
                for (i in 0 until bx.childCount) { val v = bx.getChildAt(i); if (v.tag != "secreset" && v.visibility == View.VISIBLE) any = true }   // v0.15.8: the reset button does not count as a row
                hd.visibility = if (any) View.VISIBLE else View.GONE
                bx.visibility = if (any) View.VISIBLE else View.GONE   // v0.18: no empty outlined card
                for (i in 0 until bx.childCount) { val v = bx.getChildAt(i); if (v.tag == "secreset") v.visibility = if (any && !searchOn) View.VISIBLE else View.GONE }   // v0.15.8
            }
        }

        fun refreshTop() {
            try {
                val last = SettingsIndex.parseLast(prefs.str("set_last", ""))
                val lr = if (last != null) rowByKey(last[0]) else null
                val bk = bulk   // v0.15.8 (N8): after a section / page reset the button undoes the whole reset
                if (bk != null) { undoBtn.text = com.mgb.keyboard.typing.EditPlan.undoText(bulkName, bk.size); undoBtn.visibility = View.VISIBLE }
                else if (lr != null) { undoBtn.text = "Undo last change: " + lr.title; undoBtn.visibility = View.VISIBLE } else undoBtn.visibility = View.GONE
                recentRow.removeAllViews()
                var n = 0
                for (k in SettingsIndex.parseRecent(prefs.str("set_recent", ""))) {
                    val r = rowByKey(k) ?: continue
                    val b = btn(r.title) { _ -> search.setText(r.title) }
                    b.textSize = 12f
                    recentRow.addView(b)
                    n++
                }
                val show = if (n > 0) View.VISIBLE else View.GONE
                recentLabel.visibility = show
                recentScroll.visibility = show
            } catch (_: Throwable) { }
        }

        var themeGrid: AppThemeGrid.Handle? = null   // v0.15.6 (N6): theme grid (new app screen only)
        var tbEditor: AppToolbarEditor.Handle? = null   // v0.15.5 (N5): inline toolbar editor (new app screen only)
        fun noteChange(key: String, old: Int) {
            bulk = null   // v0.15.8 (N8): a new change takes over the Undo button
            if (key == "theme") { try { themeGrid?.refresh?.invoke() } catch (_: Throwable) { } }   // v0.15.6: Theme row -> grid frame
            if (key == "tb_vault") { try { tbEditor?.refresh?.invoke() } catch (_: Throwable) { } }   // v0.15.5: Vault key row <-> editor list stay in sync
            try { dotUpdaters[key]?.invoke() } catch (_: Throwable) { }   // v0.12.0 (M2)
            try {
                prefs.setStr("set_last", SettingsIndex.encodeLast(key, old))
                prefs.setStr("set_recent", SettingsIndex.pushRecent(prefs.str("set_recent", ""), key))
                refreshTop()
            } catch (_: Throwable) { }
            try { link?.changed?.invoke() } catch (_: Throwable) { }   // v0.10.25: redraw the live preview
        }

        fun afterRowSet(key: String) {   // v0.15.8 (N8): same follow-ups as noteChange(), without touching the undo record
            if (key == "theme") { try { themeGrid?.refresh?.invoke() } catch (_: Throwable) { } }
            if (key == "tb_vault") { try { tbEditor?.refresh?.invoke() } catch (_: Throwable) { } }
        }
        fun undoLast() {
            val bk = bulk   // v0.15.8 (N8): undo of a section / page reset = every changed row gets its old value back
            if (bk != null) {
                for ((r, old) in bk.asReversed()) { try { r.setIdx(old); r.refresh(); afterRowSet(r.key) } catch (_: Throwable) { } }
                bulk = null
                prefs.setStr("set_last", "")
                refreshTop()
                try { link?.changed?.invoke() } catch (_: Throwable) { }
                return
            }
            val last = SettingsIndex.parseLast(prefs.str("set_last", "")) ?: return
            val r = rowByKey(last[0]) ?: return
            r.setIdx(last[1].toInt())
            r.refresh()
            prefs.setStr("set_last", "")
            refreshTop()
            try { link?.changed?.invoke() } catch (_: Throwable) { }   // v0.10.25
        }
        undoBtn.setOnClickListener { try { undoLast() } catch (_: Throwable) { } }
        // v0.15.8 (N8): "Reset this section / page" (new app screen only). Only rows that differ from default are touched; ONE Undo brings all of them back.
        fun resetRows(list: List<Row>, name: String) {
            try {
                val done = ArrayList<Pair<Row, Int>>()
                for (r in list) {
                    if (!r.changed()) continue
                    val before = r.raw?.invoke() ?: r.cur()   // stepper rows: the saved value (so Undo restores exactly that); others: current value
                    r.setIdx(r.def)
                    r.refresh()
                    afterRowSet(r.key)
                    done.add(Pair(r, before))
                }
                if (done.isEmpty()) {
                    try { android.widget.Toast.makeText(ctx, com.mgb.keyboard.typing.EditPlan.doneText(0), android.widget.Toast.LENGTH_SHORT).show() } catch (_: Throwable) { }
                    return
                }
                bulk = done
                bulkName = name
                prefs.setStr("set_last", "")   // the single-row Undo is replaced by this one
                refreshTop()
                try { link?.changed?.invoke() } catch (_: Throwable) { }
                try { android.widget.Toast.makeText(ctx, com.mgb.keyboard.typing.EditPlan.doneText(done.size) + ". Undo is at the top of the page.", android.widget.Toast.LENGTH_LONG).show() } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        }
        try { sink?.clearSearch = { search.setText("") } } catch (_: Throwable) { }   // v0.11.0 (M1)

        fun addRow(box: LinearLayout, key: String, title: String, b: Button, refresh: () -> Unit, setIdx: (Int) -> Unit, cur: () -> Int, def: Int) {
            val wrap = LinearLayout(ctx)
            wrap.orientation = LinearLayout.VERTICAL
            wrap.addView(b)
            val h = SettingsIndex.help(key)
            if (h.isNotEmpty()) {
                val t = TextView(ctx)
                t.text = h
                t.textSize = 12f
                t.setTextColor(0xFF888888.toInt())
                t.setPadding(dp(8), 0, dp(8), dp(4))
                wrap.addView(t)
            }
            box.addView(wrap)
            val r = Row(key, title, curGrp?.name ?: "", wrap, refresh, setIdx, cur, def)
            rows.add(r)
            curGrp?.rows?.add(r)
        }

        // v0.12.0 (M2): same registry entry as addRow, but the view is a new row (switch / segments / chip / stepper) wrapped with the "changed" dot + long-press reset
        fun addRowNew(box: LinearLayout, key: String, title: String, built: AppRows.Built, setIdx: (Int) -> Unit, cur: () -> Int, def: Int, raw: (() -> Int)? = null, valueText: (() -> String)? = null) {
            val dec = AppRows.decorate(ctx, built.view, { ChangedPlan.isChanged(raw?.invoke() ?: cur(), def) }) {
                val old = cur()
                setIdx(def)
                built.refresh()
                noteChange(key, old)
                try { android.widget.Toast.makeText(ctx, "Reset: " + title, android.widget.Toast.LENGTH_SHORT).show() } catch (_: Throwable) { }
            }
            dotUpdaters[key] = { dec.refresh() }
            val secName = ChangedPlan.sectionOf(curGrp?.name, key)
            val target = secBoxes[secName] ?: box
            target.addView(dec.view)
            val r = Row(key, title, curGrp?.name ?: "", dec.view, { built.refresh(); dec.refresh() }, setIdx, cur, def,
                if (secBoxes.containsKey(secName)) secName else "", { ChangedPlan.isChanged(raw?.invoke() ?: cur(), def) }, raw)   // v0.15.8 (N8)
            rows.add(r)
            curGrp?.rows?.add(r)
            try {   // v0.13.0 (M3): the global search on Home reads the rows from here (same view, same dot rule)
                sink?.rows?.add(SinkRow(key, title, curGrp?.name ?: "", SettingsIndex.help(key), dec.view,
                    { ChangedPlan.isChanged(raw?.invoke() ?: cur(), def) }, { valueText?.invoke() ?: "" }))
            } catch (_: Throwable) { }
        }

        fun applySearch(q: String) {
            val query = q.trim()
            val on = query.isNotEmpty()
            searchOn = on   // v0.15.8 (N8)
            val entries = ArrayList<SettingsIndex.Entry>()
            for (r in rows) entries.add(SettingsIndex.Entry(r.key, r.title, SettingsIndex.help(r.key), r.group))
            val hit = HashSet<String>()
            for (e in SettingsIndex.search(entries, query)) hit.add(e.key)
            for (r in rows) r.wrap.visibility = if (!on || hit.contains(r.key)) View.VISIBLE else View.GONE
            for (g in groups) {
                val any = g.rows.any { hit.contains(it.key) }
                if (!on) {
                    g.head.visibility = View.VISIBLE
                    g.body.visibility = if (g.isOpen) View.VISIBLE else View.GONE
                    g.head.text = (if (g.isOpen) "\u25BC  " else "\u25B6  ") + g.name
                    g.resetBtn?.visibility = View.VISIBLE
                } else {
                    g.head.visibility = if (any) View.VISIBLE else View.GONE
                    g.body.visibility = if (any) View.VISIBLE else View.GONE
                    if (any) g.head.text = "\u25BC  " + g.name
                    g.resetBtn?.visibility = View.GONE
                }
            }
            if (secList.isNotEmpty()) secVis()   // v0.12.0 (M2)
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { try { applySearch(s?.toString() ?: "") } catch (_: Throwable) { } }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) { }
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { }
        })
        // ---- end v0.4.2 registry ----

        fun toggle(box: LinearLayout, title: String, key: String, def: Boolean) {
            if (sink != null) {   // v0.12.0 (M2): real Switch row
                val built = AppRows.switchRow(ctx, title, SettingsIndex.help(key), { prefs.bool(key, def) }) { nv ->
                    val old = if (prefs.bool(key, def)) 1 else 0
                    prefs.setBool(key, nv)
                    noteChange(key, old)
                }
                addRowNew(box, key, title, built, { prefs.setBool(key, it == 1) }, { if (prefs.bool(key, def)) 1 else 0 }, if (def) 1 else 0, null) { onOff(prefs.bool(key, def)) }
                return
            }
            val b = btn("") { v ->
                val old = if (prefs.bool(key, def)) 1 else 0   // v0.4.2
                val nv = !prefs.bool(key, def)
                prefs.setBool(key, nv)
                (v as Button).text = title + ": " + onOff(nv)
                noteChange(key, old)   // v0.4.2
            }
            b.text = title + ": " + onOff(prefs.bool(key, def))
            addRow(box, key, title, b, { b.text = title + ": " + onOff(prefs.bool(key, def)) }, { prefs.setBool(key, it == 1) }, { if (prefs.bool(key, def)) 1 else 0 }, if (def) 1 else 0)   // v0.4.2 (was box.addView(b))
        }

        fun level(box: LinearLayout, title: String, key: String, def: Int, names: Array<String>) {
            fun cur() = prefs.int(key, def).coerceIn(0, names.size - 1)
            if (sink != null) {   // v0.12.0 (M2): segments (2-4 short options) or value chip
                val h = SettingsIndex.help(key)
                val built = if (ChangedPlan.useSegments(names.size, names.sumOf { it.length }))
                    AppRows.choiceRow(ctx, title, h, { names }, { cur() }) { i -> val old = cur(); prefs.setInt(key, i); noteChange(key, old) }
                else AppRows.chipRow(ctx, title, h, { names[cur()] }) { val old = cur(); prefs.setInt(key, (old + 1) % names.size); noteChange(key, old) }
                addRowNew(box, key, title, built, { prefs.setInt(key, it.coerceIn(0, names.size - 1)) }, { cur() }, def.coerceIn(0, names.size - 1), null) { names[cur()] }
                return
            }
            val b = btn("") { v ->
                val old = cur()   // v0.4.2
                val nv = (cur() + 1) % names.size
                prefs.setInt(key, nv)
                (v as Button).text = title + ": " + names[nv]
                noteChange(key, old)   // v0.4.2
            }
            b.text = title + ": " + names[cur()]
            addRow(box, key, title, b, { b.text = title + ": " + names[cur()] }, { prefs.setInt(key, it.coerceIn(0, names.size - 1)) }, { cur() }, def.coerceIn(0, names.size - 1))   // v0.4.2 (was box.addView(b))
        }

        fun cycle(box: LinearLayout, title: String, key: String, names: () -> Array<String>, get: () -> Int, set: (Int) -> Unit, def: Int = 0) {   // v0.4.2: key + def added
            if (sink != null) {   // v0.12.0 (M2)
                val h = SettingsIndex.help(key)
                fun cur() = get().coerceIn(0, names().size - 1)
                val built = if (ChangedPlan.useSegments(names().size, names().sumOf { it.length }))
                    AppRows.choiceRow(ctx, title, h, names, { cur() }) { i -> val old = cur(); set(i); noteChange(key, old) }
                else AppRows.chipRow(ctx, title, h, { names()[cur()] }) { val old = cur(); set((get() + 1) % names().size); noteChange(key, old) }
                addRowNew(box, key, title, built, { set(it) }, { cur() }, def, null) { names()[cur()] }
                return
            }
            val b = btn("") { v ->
                val n = names()
                val old = get().coerceIn(0, n.size - 1)   // v0.4.2
                val nv = (get() + 1) % n.size
                set(nv)
                (v as Button).text = title + ": " + n[nv.coerceIn(0, n.size - 1)]
                noteChange(key, old)   // v0.4.2
            }
            val n0 = names()
            b.text = title + ": " + n0[get().coerceIn(0, n0.size - 1)]
            addRow(box, key, title, b, { val n = names(); b.text = title + ": " + n[get().coerceIn(0, n.size - 1)] }, { set(it) }, { get().coerceIn(0, names().size - 1) }, def)   // v0.4.2 (was box.addView(b))
        }

        // v0.15.4 (N4): text pref row (new app screen only: the old screen keeps its keyboard-panel buttons). Chip shows the text, tap = dialog, OK saves a cleaned value.
        // Row value for dot / reset / undo: 0 = empty (default), 1 = has text. The last text is kept so Undo after a reset / replace brings it back.
        fun textRow(box: LinearLayout, title: String, key: String, hint: String, clean: (String) -> String, bad: String) {
            if (sink == null) return
            var stash = ""
            fun now() = prefs.str(key, "")
            val built = AppRows.textRow(ctx, title, SettingsIndex.help(key), { now().ifEmpty { "none" } }, { now() }, hint) { raw ->
                val old = if (now().isEmpty()) 0 else 1
                val s = if (raw.isBlank()) "" else clean(raw)
                if (raw.isNotBlank() && s.isEmpty()) bad else {
                    if (s != now()) { stash = now(); prefs.setStr(key, s); noteChange(key, old) }
                    null
                }
            }
            val setIdx = { i: Int ->
                if (i == 0) { if (now().isNotEmpty()) stash = now(); prefs.setStr(key, "") }
                else if (stash.isNotEmpty()) prefs.setStr(key, stash)
            }
            addRowNew(box, key, title, built, setIdx, { if (now().isEmpty()) 0 else 1 }, 0, null) { now().ifEmpty { "none" } }
        }

        // v0.15.4 (N4): vault folder names (read-only look at the vault file; empty list when unreadable)
        fun vaultFolders(): List<String> = try { com.mgb.keyboard.VaultStore(ctx).get()?.folders() ?: emptyList() } catch (_: Throwable) { emptyList() }

        // v0.10.25: [-] [title: value] [+] row (same prefs as the in-keyboard Size panel). def = value written by "Reset this group" (-1 = unset = the old default).
        fun stepper(box: LinearLayout, title: String, key: String, min: Int, max: Int, step: Int, unit: String, read: () -> Int, def: Int) {
            if (sink != null) {   // v0.12.0 (M2): [-] value [+], hold = repeat, ONE undo record per hold
                var holdOld = 0
                val built = AppRows.stepperRow(ctx, title, SettingsIndex.help(key), { read().toString() + unit },
                    { holdOld = read() },
                    { dir -> prefs.setInt(key, com.mgb.keyboard.typing.SizePct.step(read(), dir, min, max, step)) },
                    { noteChange(key, holdOld) },
                    "$min - $max$unit",
                    { typed ->   // v0.15.8 (N8): tap the number = type the exact value, clamped to the row range; same pref, same undo record as a - / + tap
                        val v = com.mgb.keyboard.typing.EditPlan.parseValue(typed, min, max)
                        if (v == null) "Type a number from $min to $max" else {
                            val old = read()
                            if (v != old) { prefs.setInt(key, v); noteChange(key, old) }
                            val free = com.mgb.keyboard.typing.EditPlan.parseValue(typed, Int.MIN_VALUE, Int.MAX_VALUE)
                            if (free != null && free != v) "Set to $v$unit (allowed: $min - $max$unit)" else null
                        }
                    })
                addRowNew(box, key, title, built, { prefs.setInt(key, it) }, { read() }, def, { prefs.int(key, -1) }) { read().toString() + unit }
                return
            }
            val line = LinearLayout(ctx)
            line.orientation = LinearLayout.HORIZONTAL
            val mid = TextView(ctx)
            mid.gravity = android.view.Gravity.CENTER
            mid.textSize = 14f
            fun show() { mid.text = title + ": " + read() + unit }
            fun move(dir: Int) {
                val old = read()
                val nv = com.mgb.keyboard.typing.SizePct.step(old, dir, min, max, step)
                prefs.setInt(key, nv)
                show()
                noteChange(key, old)
            }
            val minus = btn("-") { _ -> move(-1) }
            val plus = btn("+") { _ -> move(1) }
            line.addView(minus, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 3f))
            line.addView(plus, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            show()
            val wrap = LinearLayout(ctx)
            wrap.orientation = LinearLayout.VERTICAL
            wrap.addView(line)
            val h = SettingsIndex.help(key)
            if (h.isNotEmpty()) {
                val t = TextView(ctx)
                t.text = h
                t.textSize = 12f
                t.setTextColor(0xFF888888.toInt())
                t.setPadding(dp(8), 0, dp(8), dp(4))
                wrap.addView(t)
            }
            box.addView(wrap)
            val r = Row(key, title, curGrp?.name ?: "", wrap, { show() }, { prefs.setInt(key, it) }, { read() }, def)
            rows.add(r)
            curGrp?.rows?.add(r)
        }

        fun group(title: String, fill: (LinearLayout) -> Unit) {
            val body = LinearLayout(ctx)
            body.orientation = LinearLayout.VERTICAL
            body.visibility = View.GONE
            val head = Button(ctx)   // v0.4.2: head built first (Grp needs it); click listener set below
            head.text = "\u25B6  " + title
            head.isAllCaps = false
            val g = Grp(title, head, body)   // v0.4.2
            groups.add(g)
            curGrp = g
            secBoxes.clear()
            if (sink != null) {   // v0.12.0 (M2): sections of this page (rows are put into their section box by addRowNew)
                for (sn in ChangedPlan.sections(title)) {
                    val hd = AppRows.sectionHeader(ctx, sn)
                    val bx = LinearLayout(ctx)
                    bx.orientation = LinearLayout.VERTICAL
                    try {   // v0.18 (easy browse): every section is one outlined card, so the rows of a section read as one block
                        val dn = ctx.resources.displayMetrics.density
                        val sg = android.graphics.drawable.GradientDrawable()
                        sg.setColor(android.graphics.Color.TRANSPARENT)
                        sg.cornerRadius = 12f * dn
                        sg.setStroke(Math.max(1, dn.toInt()), (AppUi.dim(ctx) and 0x00FFFFFF) or 0x40000000)
                        bx.background = sg
                        bx.setPadding((12 * dn).toInt(), (4 * dn).toInt(), (12 * dn).toInt(), (8 * dn).toInt())
                    } catch (_: Throwable) { }
                    body.addView(hd)
                    body.addView(bx)
                    secBoxes[sn] = bx
                    secList.add(Pair(hd, bx))
                }
            }
            try { fill(body) } catch (_: Throwable) { }
            curGrp = null
            if (sink != null) {   // v0.15.8 (N8): "Reset this section" at the end of every section that has settings rows (hidden while searching, see secVis)
                try {
                    for ((sn, bx) in secBoxes) {
                        val inSec = g.rows.filter { it.sec == sn }
                        if (inSec.isEmpty()) continue
                        val sb = AppRows.confirmAction(ctx, "Reset this section", "Tap again: reset \"" + sn + "\"") { resetRows(inSec, sn) }
                        sb.tag = "secreset"
                        bx.addView(sb)
                    }
                    secVis()
                } catch (_: Throwable) { }
            }
            if (sink != null) {   // v0.12.0 (M2): buttons / notes added straight to the group (presets, forget, hints) get the new style
                try {
                    secVis()
                    var firstBtn = -1
                    for (i in 0 until body.childCount) { val v = body.getChildAt(i); if (v is Button) { AppRows.styleAction(ctx, v); if (firstBtn < 0) firstBtn = i } else if (v is TextView) v.setTextColor(AppUi.dim(ctx)) }
                    if (firstBtn >= 0) body.addView(AppRows.sectionHeader(ctx, "Actions"), firstBtn)
                } catch (_: Throwable) { }
            }
            if (g.rows.isNotEmpty() && sink != null) {   // v0.15.8 (N8): new app screen = "Reset this page" (changed rows only, warning colour only on the confirm step, one Undo)
                val pb = AppRows.confirmAction(ctx, "Reset this page", "Tap again: reset all settings on this page") { resetRows(g.rows, title) }
                body.addView(pb)
                g.resetBtn = pb
            } else if (g.rows.isNotEmpty()) {   // v0.4.2: Reset this group (two taps) - old screen keeps exactly this
                var armed = false
                val rb = Button(ctx)
                rb.isAllCaps = false
                rb.text = "Reset this group to default"
                rb.setOnClickListener {
                    try {
                        if (!armed) {
                            armed = true
                            rb.text = "Tap again: reset all settings in this group"
                            rb.postDelayed({ armed = false; rb.text = "Reset this group to default" }, 3000)
                        } else {
                            armed = false
                            rb.text = "Reset this group to default"
                            for (r in g.rows) { r.setIdx(r.def); r.refresh() }
                            prefs.setStr("set_last", "")
                            refreshTop()
                            try { link?.changed?.invoke() } catch (_: Throwable) { }   // v0.10.25
                        }
                    } catch (_: Throwable) { }
                }
                if (sink != null) { try { AppRows.styleAction(ctx, rb) } catch (_: Throwable) { } }   // v0.12.0 (M2)
                body.addView(rb)
                g.resetBtn = rb
            }
            head.setOnClickListener {
                try {
                    val open = body.visibility != View.VISIBLE
                    g.isOpen = open
                    body.visibility = if (open) View.VISIBLE else View.GONE
                    head.text = (if (open) "\u25BC  " else "\u25B6  ") + title
                } catch (_: Throwable) { }
            }
            if (sink != null) {   // v0.11.0 (M1): new app screen: the page shows the body (always open, no head); rows / search / undo stay in this registry
                g.isOpen = true
                body.visibility = View.VISIBLE
                sink.bodies[title] = body
                return
            }
            root.addView(head)
            root.addView(body)
        }

        group("Languages & layouts") { g ->
            cycle(g, "Bangla layout", "bn_layout", { try { com.mgb.keyboard.typing.LangNames.layoutChoices() } catch (_: Throwable) { arrayOf("Avro (phonetic)", "Probhat") } },   // v0.16.2
                { if (prefs.str("bn_layout", "avro") == "probhat") 1 else 0 },
                { prefs.setStr("bn_layout", if (it == 1) "probhat" else "avro") })
            cycle(g, "${com.mgb.keyboard.typing.LangNames.AVRO} key map", "bn_map", { arrayOf("Ridmik", "Classic") },   // v0.16.3 (X3): 1 = Ridmik key map (default), 0 = Classic Avro rules
                { if (prefs.int("bn_map", 1) == 0) 1 else 0 },
                { prefs.setInt("bn_map", if (it == 1) 0 else 1) })
            cycle(g, "Language key tap", "lang_tap", { Array(3) { i -> com.mgb.keyboard.typing.LangOrder.TAP_NAMES[i] } }, { prefs.int("lang_tap", 0).coerceIn(0, 2) }, { prefs.setInt("lang_tap", it) })   // v0.5.0
            cycle(g, "Bangla first", "lang_fav", { Array(3) { i -> com.mgb.keyboard.typing.LangOrder.FAV_NAMES[i] } }, { prefs.int("lang_fav", 0).coerceIn(0, 2) }, { prefs.setInt("lang_fav", it) })   // v0.5.0
            toggle(g, "${com.mgb.keyboard.typing.LangNames.AVRO} available", "lang_avro_on", true)   // v0.5.0
            toggle(g, "${com.mgb.keyboard.typing.LangNames.PROBHAT} available", "lang_probhat_on", true)   // v0.5.0
            if (sink != null) {   // v0.16.4 (X4): "Open key map" button at the end of the Bangla section (new app screen only); any problem = no button
                try {
                    val kb = KeyMapPage.button(ctx)
                    kb.tag = "secreset"   // reuse of the v0.15.8 rule: not counted as a row, hidden while searching (this is NOT a reset button)
                    (secBoxes["Bangla"] ?: g).addView(kb)
                } catch (_: Throwable) { }
                try {   // v0.16.5 (X5): "Practice typing" button right under it
                    val pb = PracticePage.button(ctx)
                    pb.tag = "secreset"   // same rule as the key map button
                    (secBoxes["Bangla"] ?: g).addView(pb)
                } catch (_: Throwable) { }
            }
            toggle(g, "Space swipe = language", "space_lang", false)   // v0.8.9
            toggle(g, "Language bubble on swipe", "sp_preview", true)   // v0.9.2 (R3): works only while Space swipe is ON
            toggle(g, "Tick on swipe", "sp_tick", true)   // v0.9.8 (V3): soft tick per language step; works only while Space swipe is ON
            level(g, "Swipe distance", "sp_step", com.mgb.keyboard.typing.LangSwipe.STEP_DEFAULT, com.mgb.keyboard.typing.LangSwipe.STEP_NAMES)   // v0.9.8 (V3)
            toggle(g, "Label slides while swiping", "sp_anim", true)   // v0.17.2 (K2): works only while Space swipe is ON
            toggle(g, "Show language key (EN)", "key_lang", true)   // v0.8.9: hidden only when Space swipe is ON
            toggle(g, "Number row", "numrow", true)
            toggle(g, "Auto numpad (number / phone fields)", "numpad_auto", true)
            toggle(g, "Tools row on number page", "tools_keep", true)   // v0.17.53 (M1)
            toggle(g, "Avro hints on Bangla keys", "bn_hints", true)   // v0.17.54 (M2)
            toggle(g, "Avro number page (Bangla)", "bn_numpage", true)   // v0.17.55 (M3)
            cycle(g, "Mode", "pc_mode", { arrayOf("Phone", "PC") }, { if (prefs.bool("pc_mode", false)) 1 else 0 }, { prefs.setBool("pc_mode", it == 1) })
            toggle(g, "Mode key", "mode_key", true)
            toggle(g, "Remember mode", "pc_remember", true)
            toggle(g, "Real modifier keys (PC)", "pc_real_mods", true)   // v0.17.49 (PC1)
            toggle(g, "PC rows as layout keys", "pc_layer", true)   // v0.17.50 (PC2): works only with the custom keyboard ON
            toggle(g, "PC F-keys row", "pc_frow", true)   // v0.17.50 (PC2): works only with PC rows as layout keys
            toggle(g, "PC shortcut chips", "pc_chips", true)   // v0.17.51 (PC3): chips + More + Num row (PC rows as layout keys only)
            textRow(g, "PC shortcut chips list", "pc_chips_list", "up to 5, e.g. Ctrl+A,Ctrl+Shift+Z,Alt+F4",   // v0.17.51 (PC3)
                { com.mgb.keyboard.typing.PcChord.parseList(it, com.mgb.keyboard.typing.PcChord.MAX_CHIPS).joinToString(",") { c -> c.text() } }, "No valid shortcut found")
            if (sink != null) { try { val kt = PcTestPage.button(ctx); kt.tag = "secreset"; g.addView(kt) } catch (_: Throwable) { } }   // v0.17.52 (PC4): key test page button (new app screen only)
        }

        group("Typing") { g ->
            toggle(g, "Hold backspace", "bs_repeat", true)
            toggle(g, "Long hold deletes words", "bs_word", true)   // v0.3.2
            toggle(g, "Auto caps", "autocap", true)
            toggle(g, "Enter action", "enter_action", true)
            toggle(g, "Double-space period", "ds_period", false)
            toggle(g, "Double-space tab", "ds_tab", false)
            toggle(g, "Long-press characters", "hint_on", true)
            toggle(g, "Accent letters on hold (é ñ ü ...)", "lp_accents", false)   // v0.17.46 (P3)
            toggle(g, "Symbols on hold of , and .", "lp_punct", false)   // v0.17.46 (P3)
            toggle(g, "Key press animation", "key_press_anim", false)   // v0.17.48 (P5)
            textRow(g, "Long-press override", "hint_custom", "a=@,e=3 (letter=text, comma between)",   // v0.15.4 (N4): same cleaning as the keyboard Typing panel
                { com.mgb.keyboard.typing.LongPressHints.normalizeCustom(it.replace('\n', ',')) }, "No a=@ pairs found")
            textRow(g, "Extra character row", "xrow_chars", "up to 10 characters, e.g. ()[]{}",
                { com.mgb.keyboard.typing.LangOrder.customRowText(it) }, "No characters found")
        }

        group("Look") { g ->
            cycle(g, "Theme", "theme", { Array(JabraThemes.ALL.size) { i -> JabraThemes.ALL[i].name } }, { JabraThemes.index(prefs) }, { prefs.setInt("theme", it) })
            if (sink != null) {   // v0.15.6 (N6): theme grid + Look presets under the Theme row; any problem = no grid, the row above stays
                try {
                    val gr = AppThemeGrid.build(ctx, prefs, { old -> noteChange("theme", old) }) { try { link?.refreshRows?.invoke() } catch (_: Throwable) { }; try { link?.changed?.invoke() } catch (_: Throwable) { } }
                    themeGrid = gr
                    (secBoxes["Theme & style"] ?: g).addView(gr.view)
                } catch (_: Throwable) { }
            }
            if (sink != null) {   // v0.15.7 (N7): custom theme editor in its own section; any problem = no editor
                try {
                    val st = AppThemeStudio.build(ctx, prefs) { old ->
                        if (JabraThemes.index(prefs) != old) noteChange("theme", old)   // Undo of a "Save and use"
                        try { themeGrid?.refresh?.invoke() } catch (_: Throwable) { }
                        try { link?.refreshRows?.invoke() } catch (_: Throwable) { }
                        try { link?.changed?.invoke() } catch (_: Throwable) { }
                    }
                    (secBoxes["Custom theme"] ?: g).addView(st.view)
                } catch (_: Throwable) { }
            }
            cycle(g, "Look", "look", { arrayOf("Classic", "Jabra") }, { if (prefs.int("look", 0) == 1) 1 else 0 }, { prefs.setInt("look", it) })
            cycle(g, "Key border", "key_border", { arrayOf("Border", "Borderless") }, { if (prefs.int("key_border", 0) == 1) 1 else 0 }, { prefs.setInt("key_border", it) })   // v0.2.6
            cycle(g, "One-hand", "onehand", { arrayOf("Off", "Left", "Right") }, { JabraOneHand.mode(prefs) }, { prefs.setInt("onehand", it) })
            toggle(g, "Icons", "icons_on", true)
            toggle(g, "Large number row", "numrow_large", false)
            stepper(g, "Number row height", "nr_hpct", com.mgb.keyboard.typing.NumRowPlan.H_MIN, com.mgb.keyboard.typing.NumRowPlan.H_MAX, com.mgb.keyboard.typing.NumRowPlan.H_STEP, "%",   // v0.16.7 (X7): 100 = old height
                { com.mgb.keyboard.typing.NumRowPlan.sanitizePct(prefs.int("nr_hpct", 100)) }, 100)
            cycle(g, "Number row keys", "nr_mode", { arrayOf("Digits", "Digits + symbols", "My characters") }, { com.mgb.keyboard.typing.NumRowPlan.sanitizeMode(prefs.int("nr_mode", 0)) }, { prefs.setInt("nr_mode", it) })   // v0.16.7 (X7)
            textRow(g, "Number row characters", "nr_chars", "up to 10 characters, e.g. @#&_-+()/",   // v0.16.7 (X7): used when "Number row keys" = My characters
                { com.mgb.keyboard.typing.NumRowPlan.sanitizeChars(it) }, "No characters found")
            toggle(g, "Hide long-press hints", "hint_hide", false)
            toggle(g, "Toolbar name tip (hold icon)", "tb_tip", true)   // v0.8.8
            toggle(g, "Bangla digit hints", "hint_digits", false)   // v0.8.6
            toggle(g, "Bangla digits on number row", "numrow_bn", false)   // v0.9.4 (R4)
            cycle(g, "Hint size", "hint_size_lv", { com.mgb.keyboard.typing.HintStyle.SIZE_NAMES }, { prefs.int("hint_size_lv", 1).coerceIn(0, 3) }, { prefs.setInt("hint_size_lv", it) }, 1)
            cycle(g, "Hint opacity", "hint_alpha_lv", { com.mgb.keyboard.typing.HintStyle.ALPHA_NAMES }, { prefs.int("hint_alpha_lv", 1).coerceIn(0, 3) }, { prefs.setInt("hint_alpha_lv", it) }, 1)
            level(g, "Bangla keyboard design", "bn_design", 0, com.mgb.keyboard.typing.BnDesign.NAMES)   // v0.17.61 (R1): last design option; 2nd choice = the keyboard's original design (old Bangla fixed look)
            val note = TextView(ctx)
            note.text = "Key height % and one-hand width %: inside the keyboard (gear > More settings > Size)."
            g.addView(note)
        }

        // v0.10.25: layout sizes in the app, with the live preview at the top (same prefs as the in-keyboard Size panel: kh_pct_p, key_gap, letter_pct, letter_bold, oh_w_p)
        group("Layout & size") { g ->
            stepper(g, "Key height (portrait)", "kh_pct_p", com.mgb.keyboard.typing.SizePct.H_MIN, com.mgb.keyboard.typing.SizePct.H_MAX, com.mgb.keyboard.typing.SizePct.H_STEP, "%",
                { val v = prefs.int("kh_pct_p", -1); if (com.mgb.keyboard.typing.SizePct.heightValid(v)) v else com.mgb.keyboard.typing.SizePct.pctFromDp(prefs.keyHeightDp(false)) }, -1)
            stepper(g, "Key height (landscape)", "kh_pct_l", com.mgb.keyboard.typing.SizePct.H_MIN, com.mgb.keyboard.typing.SizePct.H_MAX, com.mgb.keyboard.typing.SizePct.H_STEP, "%",   // v0.15.4 (N4): same range / unset rule as the keyboard Size panel
                { val v = prefs.int("kh_pct_l", -1); if (com.mgb.keyboard.typing.SizePct.heightValid(v)) v else com.mgb.keyboard.typing.SizePct.pctFromDp(prefs.keyHeightDp(true)) }, -1)
            stepper(g, "Key gap", "key_gap", com.mgb.keyboard.typing.SizePct.G_MIN, com.mgb.keyboard.typing.SizePct.G_MAX, 1, " dp",
                { com.mgb.keyboard.typing.SizePct.gapDp(prefs.int("key_gap", -1)) }, -1)
            stepper(g, "Letter size", "letter_pct", com.mgb.keyboard.typing.SizePct.L_MIN, com.mgb.keyboard.typing.SizePct.L_MAX, com.mgb.keyboard.typing.SizePct.L_STEP, "%",
                { val v = prefs.int("letter_pct", -1); if (com.mgb.keyboard.typing.SizePct.letterValid(v)) v else 100 }, -1)
            toggle(g, "Bold letters", "letter_bold", false)
            toggle(g, "Home row side gap", "row_gap", true)
            level(g, "Custom keyboard layout (experimental)", "kb_custom", 0, arrayOf("Off", "On"))   // v0.17.5 (K5a.1): On = English letter rows come from the saved layout; any problem = classic keyboard
            try {   // v0.17.5 (K5a.1): status line + the "settings that do nothing" note (plan 6.9) + one-tap safety button (S3)
                val st = android.widget.TextView(ctx)
                val on = prefs.int("kb_custom", 0) == 1
                st.text = LayoutStore.status(ctx, prefs) + (if (on) "\nWhile it is ON the letter rows (English, Bangla Avro, Bangla Probhat), the number row and the bottom row come from the saved layout. These settings do nothing then: Home row side gap, Shift / Backspace gap, Long-press override, Number row options, Bottom row, ? key, EN / Globe / Voice / Emoji keys, Space bar width and height. They come back when you switch to Off; after changing them tap \"Rebuild layout from current settings\" and reopen the keyboard." else "")
                st.textSize = 12f
                st.setTextColor(0xFF888888.toInt())
                st.setPadding(dp(8), 0, dp(8), dp(4))
                g.addView(st)
                g.addView(btn("Restore last good layout") { v -> (v as Button).text = if (LayoutStore.restoreLastGood(ctx)) "Last good layout restored" else "No saved layout yet" })
                g.addView(btn("Edit layout (keys, letters, width)") { _ -> try { (ctx as? android.app.Activity)?.let { LayoutEditorPage(it, prefs) { try { link?.changed?.invoke() } catch (_: Throwable) { } }.show() } } catch (_: Throwable) { } })   // v0.17.8 (K6a)
                g.addView(btn("Rebuild layout from current settings") { v -> (v as Button).text = if (LayoutStore.rebuildFromSettings(ctx, prefs)) "Layout rebuilt: reopen the keyboard" else "Could not rebuild the layout" })   // v0.17.6 (K5a.2)
            } catch (_: Throwable) { }
            stepper(g, "Key corner radius", "key_radius", com.mgb.keyboard.typing.EdgePlan.R_MIN, com.mgb.keyboard.typing.EdgePlan.R_MAX, 1, " dp",
                { com.mgb.keyboard.typing.EdgePlan.radiusDp(prefs.int("key_radius", -1)) }, -1)   // v0.10.28 (P3): Jabra look only; Ridmik about 6
            // v0.10.26 (P1): spacing like Ridmik. All three unset = the old look (edge 2 dp, up-down gap = key gap, no extra gap).
            stepper(g, "Edge distance", "edge_dp", com.mgb.keyboard.typing.EdgePlan.E_MIN, com.mgb.keyboard.typing.EdgePlan.E_MAX, 1, " dp",
                { com.mgb.keyboard.typing.EdgePlan.edgeDp(prefs.int("edge_dp", -1)) }, -1)
            stepper(g, "Row gap (up-down)", "row_vgap", com.mgb.keyboard.typing.EdgePlan.V_MIN, com.mgb.keyboard.typing.EdgePlan.V_MAX, 1, " dp",
                { com.mgb.keyboard.typing.EdgePlan.vGapDp(prefs.int("row_vgap", -1), com.mgb.keyboard.typing.SizePct.gapDp(prefs.int("key_gap", -1))) }, -1)
            stepper(g, "Shift / Backspace gap", "fn_gap", com.mgb.keyboard.typing.EdgePlan.F_MIN, com.mgb.keyboard.typing.EdgePlan.F_MAX, 1, " dp",
                { com.mgb.keyboard.typing.EdgePlan.fnGapDp(prefs.int("fn_gap", -1)) }, -1)
            // v0.10.27 (P2): bottom row like Ridmik, "..." marks on long-press keys, Probhat home row gap. All default = old look.
            cycle(g, "Bottom row", "bot_style", { arrayOf("Old", "Ridmik-like") }, { prefs.int("bot_style", 0).coerceIn(0, 1) }, { prefs.setInt("bot_style", it) }, 0)
            stepper(g, "Space bar width", "sp_wpct", com.mgb.keyboard.typing.SpacePlan.W_MIN, com.mgb.keyboard.typing.SpacePlan.W_MAX, com.mgb.keyboard.typing.SpacePlan.W_STEP, "%",
                { com.mgb.keyboard.typing.SpacePlan.sanitizeW(prefs.int("sp_wpct", -1)) }, -1)   // v0.17.2 (K2): 100 = as before
            stepper(g, "Bottom row height", "sp_hpct", com.mgb.keyboard.typing.SpacePlan.H_MIN, com.mgb.keyboard.typing.SpacePlan.H_MAX, com.mgb.keyboard.typing.SpacePlan.H_STEP, "%",
                { com.mgb.keyboard.typing.SpacePlan.sanitizeH(prefs.int("sp_hpct", -1)) }, -1)   // v0.17.2 (K2): 100 = as before
            toggle(g, "Long-press dots", "key_dots", false)
            toggle(g, "${com.mgb.keyboard.typing.LangNames.PROBHAT} home row gap", "pb_home_gap", false)
            run {   // v0.10.26: one tap = Ridmik-style numbers (height 120 / 100 %, gap 2, edge 1, row gap 2, Shift gap 4). Two taps to apply, one tap to undo.
                val keys = com.mgb.keyboard.typing.EdgePlan.PRESET_KEYS
                var sure = false
                fun refreshAll() { for (r in rows) { try { r.refresh() } catch (_: Throwable) { } }; try { link?.changed?.invoke() } catch (_: Throwable) { } }
                val pb = btn("Ridmik-style spacing") { v ->
                    if (!sure) { sure = true; (v as Button).text = "Sure? Changes height, gaps, bottom row. Tap again" }
                    else {
                        sure = false
                        val bf = com.mgb.keyboard.typing.EdgePlan.BOOL_FROM
                        val old = IntArray(keys.size) { if (com.mgb.keyboard.typing.EdgePlan.isBool(it)) (if (prefs.bool(keys[it], false)) 1 else 0) else prefs.int(keys[it], -1) }   // v0.10.27: last 2 are boolean prefs
                        prefs.setStr("geo_prev", com.mgb.keyboard.typing.EdgePlan.encode(old))
                        for (i in keys.indices) { if (com.mgb.keyboard.typing.EdgePlan.isBool(i)) prefs.setBool(keys[i], com.mgb.keyboard.typing.EdgePlan.RIDMIK[i] == 1) else prefs.setInt(keys[i], com.mgb.keyboard.typing.EdgePlan.RIDMIK[i]) }
                        prefs.setBool("row_gap", true)
                        (v as Button).text = "Ridmik-style spacing (applied)"
                        refreshAll()
                    }
                }
                g.addView(pb)
                g.addView(btn("Undo Ridmik-style spacing") { v ->
                    val old = com.mgb.keyboard.typing.EdgePlan.decodeUpTo(prefs.str("geo_prev", ""), keys.size)   // v0.10.27: also reads the 6 values saved by v0.10.26
                    if (old == null) { (v as Button).text = "Nothing to undo" }
                    else {
                        val bf = com.mgb.keyboard.typing.EdgePlan.BOOL_FROM
                        for (i in keys.indices) { if (com.mgb.keyboard.typing.EdgePlan.isBool(i)) prefs.setBool(keys[i], old[i] == 1) else prefs.setInt(keys[i], old[i]) }
                        prefs.setStr("geo_prev", "")
                        (v as Button).text = "Undone"
                        pb.text = "Ridmik-style spacing"
                        refreshAll()
                    }
                })
            }
            stepper(g, "One-hand width (portrait)", "oh_w_p", com.mgb.keyboard.typing.SizePct.W_MIN, com.mgb.keyboard.typing.SizePct.W_MAX, com.mgb.keyboard.typing.SizePct.W_STEP, "%",
                { val v = prefs.int("oh_w_p", -1); if (com.mgb.keyboard.typing.SizePct.widthValid(v)) v else Math.round(com.mgb.keyboard.typing.SizePct.widthFrac(-1, false) * 100f) }, -1)
            stepper(g, "One-hand width (landscape)", "oh_w_l", com.mgb.keyboard.typing.SizePct.W_MIN, com.mgb.keyboard.typing.SizePct.W_MAX, com.mgb.keyboard.typing.SizePct.W_STEP, "%",   // v0.15.4 (N4)
                { val v = prefs.int("oh_w_l", -1); if (com.mgb.keyboard.typing.SizePct.widthValid(v)) v else Math.round(com.mgb.keyboard.typing.SizePct.widthFrac(-1, true) * 100f) }, -1)
            val note2 = TextView(ctx)
            note2.text = "The picture at the top shows every change at once. One-hand width shows only while One-hand (Look group) is Left or Right."
            note2.textSize = 12f
            note2.setTextColor(0xFF888888.toInt())
            g.addView(note2)
        }

        group("Keys & gestures") { g ->
            toggle(g, "Vibrate", "haptic", true)
            toggle(g, "Feedback at touch-down", "fb_down", false)   // v0.9.1
            toggle(g, "System key tap vibration", "haptic_native", false)   // v0.9.1
            toggle(g, "Sound", "sound", false)
            toggle(g, "Key popup", "key_popup", false)
            toggle(g, "Reference-style bubble", "pop_ref", true)   // v0.9.6 (V1): bigger rounded bubble in the theme colour; works only while Key popup is ON
            toggle(g, "Learn my words", "learn_on", false)   // v0.10.2 (G3.1): remembers typed words on this phone only (default OFF)
            run {   // v0.10.2 (G3.1): Forget (tap twice)
                var sure = false
                val fb = btn("Forget learned words") { v ->
                    if (!sure) { sure = true; (v as Button).text = "Sure? Tap again" }
                    else { sure = false; val n = LearnStore.clearAll(ctx); (v as Button).text = "Forgot " + n + " words" }
                }
                g.addView(fb)
            }
            toggle(g, "Space before , . ! ?", "sp_punct", true)   // v0.10.6: after a tapped suggestion, punctuation removes its extra space
            toggle(g, "Auto-correct", "ac_on", false)   // v0.10.3 (G3.2): fixes 1-letter English typos on space; backspace undoes (default OFF)
            toggle(g, "Auto-profile per app", "ap_on", false)   // v0.10.19 (W6.2): applies the profile you set for an app (Profiles > "> App") when its field opens; default OFF
            toggle(g, "Selection quick actions", "qa_on", false)   // v0.10.23 (W9): select text = Copy / Case / Clean / word count in the top row; default OFF
            toggle(g, "Suggestion row", "sg_on", true)   // v0.10.1 (G2): word suggestions (needs the toolbar unfolded)
            toggle(g, "Fast bubble", "pop_fast", true)   // v0.9.9 (V4): bubble drawn inside the keyboard (no window call) on rows with room above; works only while Key popup is ON
            toggle(g, "Same keyboard height", "h_lock", true)   // v0.9.7 (V2): no height jump when the language changes (keeps the Probhat row space)
            toggle(g, "Globe key", "key_globe", false)
            toggle(g, "Voice key", "key_voice", false)
            toggle(g, "Emoji key", "key_emoji", false)
            toggle(g, "Show ? / । key", "key_qm", false)   // v0.17.1 (K1): OFF (default) = no ? key in the bottom row, space is wider, long-press the dot for ? / ।
            toggle(g, "Bangla: । on the dot key", "danda_key", com.mgb.keyboard.layout.LayoutRoute.DANDA_DEF)   // v0.17.18 (L1): Bangla = the dot key shows । (tap), long-press = full stop (like Ridmik). Off while "Show ? / । key" is ON.
            level(g, "Long-press time", "lp_delay", TypingLevels.LP_DEFAULT, TypingLevels.LP_NAMES)
            level(g, "Cursor speed (space drag)", "cursor_speed", TypingLevels.CS_DEFAULT, TypingLevels.CS_NAMES)
        }

        group("Clipboard & tools") { g ->
            toggle(g, "Clip history", "clip_on", true)
            level(g, "Clip lines", "clip_lines", 0, arrayOf("2 lines", "4 lines", "6 lines"))   // v0.2.5.1
            level(g, "Clipboard view", "clip_view", 0, arrayOf("List", "Cards"))   // v0.2.5.2
            level(g, "Clip sort", "clip_sort", 0, com.mgb.keyboard.clip.ClipMeta.SORT_NAMES)   // v0.4.3
            level(g, "Clip order", "clip_order", 0, com.mgb.keyboard.typing.ClipOrder.NAMES)   // v0.15.2 (N2): which group is shown first, List + Cards
            toggle(g, "Clip character count", "clip_count", true)   // v0.4.3
            level(g, "Clip auto-delete (unpinned)", "clip_expire", 0, com.mgb.keyboard.clip.ClipMeta.EXPIRE_NAMES)   // v0.4.3
            toggle(g, "Stay in clipboard after paste", "clip_stay", true)   // v0.17.37 (M4): OFF = old way, back to letters
            toggle(g, "Paste keeps format (card tap)", "clip_fmt_tap", true)   // v0.17.40 (M7): the Aa tag on a card does the other way
            toggle(g, "Clipboard new look", "clip_new_look", true)   // v0.17.58 (C3): OFF = exactly the old clipboard panel (panel_ui decides)
            toggle(g, "Clipboard follows keyboard height", "clip_fit", true)   // v0.15.1 (N1): OFF = old fixed clipboard size
            level(g, "Panel style", "panel_ui", 0, com.mgb.keyboard.typing.PanelLayout.STYLE_NAMES)   // v0.16.9 (X9): 0 Classic = today's clipboard (default in the first build), 1 New
            stepper(g, "Clipboard button size", "clip_btn_pct", com.mgb.keyboard.typing.ClipBtn.MIN, com.mgb.keyboard.typing.ClipBtn.MAX, com.mgb.keyboard.typing.ClipBtn.STEP, "%",
                { com.mgb.keyboard.typing.ClipBtn.sanitize(prefs.int("clip_btn_pct", com.mgb.keyboard.typing.ClipBtn.DEF)) }, com.mgb.keyboard.typing.ClipBtn.DEF)   // v0.17.32 (UI8): 100 = as before; smaller buttons = more list
            stepper(g, "Clipboard card size", "clip_card_pct", com.mgb.keyboard.typing.ClipCardSize.MIN, com.mgb.keyboard.typing.ClipCardSize.MAX, com.mgb.keyboard.typing.ClipCardSize.STEP, "%",
                { com.mgb.keyboard.typing.ClipCardSize.sanitize(prefs.int("clip_card_pct", com.mgb.keyboard.typing.ClipCardSize.DEF)) }, com.mgb.keyboard.typing.ClipCardSize.DEF)   // v0.17.33 (UI9): 100 = as before; smaller cards = more clips visible
            stepper(g, "Clipboard list height", "clip_list_pct", com.mgb.keyboard.typing.ClipListH.MIN, com.mgb.keyboard.typing.ClipListH.MAX, com.mgb.keyboard.typing.ClipListH.STEP, "%",
                { com.mgb.keyboard.typing.ClipListH.sanitize(prefs.int("clip_list_pct", com.mgb.keyboard.typing.ClipListH.DEF)) }, com.mgb.keyboard.typing.ClipListH.DEF)   // v0.17.38 (M5): 100 = as before; smaller = shorter list (never taller)
            level(g, "Hold to select a clip", "clip_hold", com.mgb.keyboard.typing.HoldSelect.LEVEL_DEFAULT, com.mgb.keyboard.typing.HoldSelect.LEVEL_NAMES)   // v0.16.16 (E2): hold the first clip = select mode; System = old long-press
            toggle(g, "Clip code guard (2 min)", "clip_sens", false)   // v0.10.10 (W4): copied one-time codes leave the history after 2 minutes (default OFF)
        }

        group("Tools & Panels") { g ->   // v0.15.3 (N3): 3 rows moved here from "Clipboard & tools" (same keys, same behaviour)
            toggle(g, "Edit bar", "edit_bar", true)
            // v0.5.5: switch for the Vault key on the toolbar (same data as Edit toolbar > Vault; pseudo row key "tb_vault", no new pref)
            cycle(g, "Vault key (toolbar)", "tb_vault", { arrayOf("OFF", "ON") },
                { if (com.mgb.keyboard.typing.ToolbarOrder.off(prefs.str("tb_hidden", ""), prefs.str("tb_on", ""), "vault")) 0 else 1 },
                { i -> prefs.setStr("tb_on", com.mgb.keyboard.typing.ToolbarOrder.setOn(prefs.str("tb_on", ""), "vault", i == 1)) }, 0)
            cycle(g, "Toolbar style", "tb_custom", { arrayOf("Classic", "Custom") }, { if (prefs.bool("tb_custom", true)) 1 else 0 }, { prefs.setBool("tb_custom", it == 1) }, 1)   // v0.15.4 (N4)
            toggle(g, "Most-used tools first", "tb_auto", false)   // v0.15.4 (N4)
            toggle(g, "Fold toolbar", "tb_fold", false)   // v0.15.4 (N4)
            stepper(g, "Toolbar height", "tb_hpct", com.mgb.keyboard.typing.BarSize.MIN, com.mgb.keyboard.typing.BarSize.MAX, com.mgb.keyboard.typing.BarSize.STEP, "%",   // v0.16.6 (X6): 100 = old 36 dp
                { com.mgb.keyboard.typing.BarSize.sanitize(prefs.int("tb_hpct", com.mgb.keyboard.typing.BarSize.DEF)) }, com.mgb.keyboard.typing.BarSize.DEF)
            stepper(g, "Toolbar icon / text size", "tb_ipct", com.mgb.keyboard.typing.BarSize.MIN, com.mgb.keyboard.typing.BarSize.MAX, com.mgb.keyboard.typing.BarSize.STEP, "%",   // v0.16.6 (X6): 100 = old 12 sp, 44 dp wide
                { com.mgb.keyboard.typing.BarSize.sanitize(prefs.int("tb_ipct", com.mgb.keyboard.typing.BarSize.DEF)) }, com.mgb.keyboard.typing.BarSize.DEF)
            toggle(g, "Formatter (Aa button)", "fmt_on", true)
            level(g, "Vault sort", "vault_sort", 0, arrayOf("Newest", "A-Z", "Used"))   // v0.15.4 (N4)
            cycle(g, "Vault save folder", "vault_dest", { arrayOf("Unfiled") + vaultFolders().toTypedArray() },   // v0.15.4 (N4): folder used by "To Vault" (index 0 = Unfiled)
                { val d = prefs.str("vault_dest", ""); if (d.isEmpty()) 0 else vaultFolders().indexOf(d) + 1 },
                { i -> val f = vaultFolders(); prefs.setStr("vault_dest", if (i <= 0 || i > f.size) "" else f[i - 1]) }, 0)
            if (sink != null) {   // v0.15.5 (N5): toolbar editor under the Toolbar section; any problem = no editor, the rows above stay
                try {
                    val ed = AppToolbarEditor.build(ctx, prefs) { try { link?.refreshRows?.invoke() } catch (_: Throwable) { }; try { link?.changed?.invoke() } catch (_: Throwable) { } }
                    tbEditor = ed
                    (secBoxes["Toolbar"] ?: g).addView(ed.view)
                } catch (_: Throwable) { }
            }
        }

        try { group("Data & Backup") { g -> g.addView(DataPage.build(ctx)) } } catch (_: Throwable) { }   // v0.4.1

        refreshTop()   // v0.4.2
        try { sink?.top = root } catch (_: Throwable) { }   // v0.11.0 (M1): intro + search + undo + recent, shown at the top of the open page
        link?.refreshRows = { for (r in rows) { try { r.refresh() } catch (_: Throwable) { } } }   // v0.10.25: the preview theme strip redraws the row labels

        return root
    }
}
