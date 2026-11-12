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

    fun build(ctx: Context): View {
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
                  val refresh: () -> Unit, val setIdx: (Int) -> Unit, val cur: () -> Int, val def: Int)
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

        fun refreshTop() {
            try {
                val last = SettingsIndex.parseLast(prefs.str("set_last", ""))
                val lr = if (last != null) rowByKey(last[0]) else null
                if (lr != null) { undoBtn.text = "Undo last change: " + lr.title; undoBtn.visibility = View.VISIBLE } else undoBtn.visibility = View.GONE
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

        fun noteChange(key: String, old: Int) {
            try {
                prefs.setStr("set_last", SettingsIndex.encodeLast(key, old))
                prefs.setStr("set_recent", SettingsIndex.pushRecent(prefs.str("set_recent", ""), key))
                refreshTop()
            } catch (_: Throwable) { }
        }

        fun undoLast() {
            val last = SettingsIndex.parseLast(prefs.str("set_last", "")) ?: return
            val r = rowByKey(last[0]) ?: return
            r.setIdx(last[1].toInt())
            r.refresh()
            prefs.setStr("set_last", "")
            refreshTop()
        }
        undoBtn.setOnClickListener { try { undoLast() } catch (_: Throwable) { } }

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

        fun applySearch(q: String) {
            val query = q.trim()
            val on = query.isNotEmpty()
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
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { try { applySearch(s?.toString() ?: "") } catch (_: Throwable) { } }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) { }
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { }
        })
        // ---- end v0.4.2 registry ----

        fun toggle(box: LinearLayout, title: String, key: String, def: Boolean) {
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
            try { fill(body) } catch (_: Throwable) { }
            curGrp = null
            if (g.rows.isNotEmpty()) {   // v0.4.2: Reset this group (two taps)
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
                        }
                    } catch (_: Throwable) { }
                }
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
            root.addView(head)
            root.addView(body)
        }

        group("Languages & layouts") { g ->
            cycle(g, "Bangla layout", "bn_layout", { arrayOf("Avro (phonetic)", "Probhat") },
                { if (prefs.str("bn_layout", "avro") == "probhat") 1 else 0 },
                { prefs.setStr("bn_layout", if (it == 1) "probhat" else "avro") })
            cycle(g, "Language key tap", "lang_tap", { Array(3) { i -> com.mgb.keyboard.typing.LangOrder.TAP_NAMES[i] } }, { prefs.int("lang_tap", 0).coerceIn(0, 2) }, { prefs.setInt("lang_tap", it) })   // v0.5.0
            cycle(g, "Bangla first", "lang_fav", { Array(3) { i -> com.mgb.keyboard.typing.LangOrder.FAV_NAMES[i] } }, { prefs.int("lang_fav", 0).coerceIn(0, 2) }, { prefs.setInt("lang_fav", it) })   // v0.5.0
            toggle(g, "Avro available", "lang_avro_on", true)   // v0.5.0
            toggle(g, "Probhat available", "lang_probhat_on", true)   // v0.5.0
            toggle(g, "Space swipe = language", "space_lang", false)   // v0.8.9
            toggle(g, "Language bubble on swipe", "sp_preview", true)   // v0.9.2 (R3): works only while Space swipe is ON
            toggle(g, "Tick on swipe", "sp_tick", true)   // v0.9.8 (V3): soft tick per language step; works only while Space swipe is ON
            level(g, "Swipe distance", "sp_step", com.mgb.keyboard.typing.LangSwipe.STEP_DEFAULT, com.mgb.keyboard.typing.LangSwipe.STEP_NAMES)   // v0.9.8 (V3)
            toggle(g, "Show language key (EN)", "key_lang", true)   // v0.8.9: hidden only when Space swipe is ON
            toggle(g, "Number row", "numrow", true)
            toggle(g, "Auto numpad (number / phone fields)", "numpad_auto", true)
            cycle(g, "Mode", "pc_mode", { arrayOf("Phone", "PC") }, { if (prefs.bool("pc_mode", false)) 1 else 0 }, { prefs.setBool("pc_mode", it == 1) })
            toggle(g, "Mode key", "mode_key", true)
            toggle(g, "Remember mode", "pc_remember", true)
        }

        group("Typing") { g ->
            toggle(g, "Hold backspace", "bs_repeat", true)
            toggle(g, "Long hold deletes words", "bs_word", true)   // v0.3.2
            toggle(g, "Auto caps", "autocap", true)
            toggle(g, "Enter action", "enter_action", true)
            toggle(g, "Double-space period", "ds_period", false)
            toggle(g, "Double-space tab", "ds_tab", false)
            toggle(g, "Long-press characters", "hint_on", true)
        }

        group("Look") { g ->
            cycle(g, "Theme", "theme", { Array(JabraThemes.ALL.size) { i -> JabraThemes.ALL[i].name } }, { JabraThemes.index(prefs) }, { prefs.setInt("theme", it) })
            cycle(g, "Look", "look", { arrayOf("Classic", "Jabra") }, { if (prefs.int("look", 0) == 1) 1 else 0 }, { prefs.setInt("look", it) })
            cycle(g, "Key border", "key_border", { arrayOf("Border", "Borderless") }, { if (prefs.int("key_border", 0) == 1) 1 else 0 }, { prefs.setInt("key_border", it) })   // v0.2.6
            cycle(g, "One-hand", "onehand", { arrayOf("Off", "Left", "Right") }, { JabraOneHand.mode(prefs) }, { prefs.setInt("onehand", it) })
            toggle(g, "Icons", "icons_on", true)
            toggle(g, "Large number row", "numrow_large", false)
            toggle(g, "Hide long-press hints", "hint_hide", false)
            toggle(g, "Toolbar name tip (hold icon)", "tb_tip", true)   // v0.8.8
            toggle(g, "Bangla digit hints", "hint_digits", false)   // v0.8.6
            toggle(g, "Bangla digits on number row", "numrow_bn", false)   // v0.9.4 (R4)
            cycle(g, "Hint size", "hint_size_lv", { com.mgb.keyboard.typing.HintStyle.SIZE_NAMES }, { prefs.int("hint_size_lv", 1).coerceIn(0, 3) }, { prefs.setInt("hint_size_lv", it) }, 1)
            cycle(g, "Hint opacity", "hint_alpha_lv", { com.mgb.keyboard.typing.HintStyle.ALPHA_NAMES }, { prefs.int("hint_alpha_lv", 1).coerceIn(0, 3) }, { prefs.setInt("hint_alpha_lv", it) }, 1)
            val note = TextView(ctx)
            note.text = "Key height % and one-hand width %: inside the keyboard (gear > More settings > Size)."
            g.addView(note)
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
            toggle(g, "Suggestion row", "sg_on", true)   // v0.10.1 (G2): word suggestions (needs the toolbar unfolded)
            toggle(g, "Fast bubble", "pop_fast", true)   // v0.9.9 (V4): bubble drawn inside the keyboard (no window call) on rows with room above; works only while Key popup is ON
            toggle(g, "Same keyboard height", "h_lock", true)   // v0.9.7 (V2): no height jump when the language changes (keeps the Probhat row space)
            toggle(g, "Globe key", "key_globe", false)
            toggle(g, "Voice key", "key_voice", false)
            toggle(g, "Emoji key", "key_emoji", false)
            level(g, "Long-press time", "lp_delay", TypingLevels.LP_DEFAULT, TypingLevels.LP_NAMES)
            level(g, "Cursor speed (space drag)", "cursor_speed", TypingLevels.CS_DEFAULT, TypingLevels.CS_NAMES)
        }

        group("Clipboard & tools") { g ->
            toggle(g, "Edit bar", "edit_bar", true)
            toggle(g, "Clip history", "clip_on", true)
            toggle(g, "Formatter (Aa button)", "fmt_on", true)
            // v0.5.5: switch for the Vault key on the toolbar (same data as Edit toolbar > Vault; pseudo row key "tb_vault", no new pref)
            cycle(g, "Vault key (toolbar)", "tb_vault", { arrayOf("OFF", "ON") },
                { if (com.mgb.keyboard.typing.ToolbarOrder.off(prefs.str("tb_hidden", ""), prefs.str("tb_on", ""), "vault")) 0 else 1 },
                { i -> prefs.setStr("tb_on", com.mgb.keyboard.typing.ToolbarOrder.setOn(prefs.str("tb_on", ""), "vault", i == 1)) }, 0)
            level(g, "Clip lines", "clip_lines", 0, arrayOf("2 lines", "4 lines", "6 lines"))   // v0.2.5.1
            level(g, "Clipboard view", "clip_view", 0, arrayOf("List", "Cards"))   // v0.2.5.2
            level(g, "Clip sort", "clip_sort", 0, com.mgb.keyboard.clip.ClipMeta.SORT_NAMES)   // v0.4.3
            toggle(g, "Clip character count", "clip_count", true)   // v0.4.3
            level(g, "Clip auto-delete (unpinned)", "clip_expire", 0, com.mgb.keyboard.clip.ClipMeta.EXPIRE_NAMES)   // v0.4.3
            toggle(g, "Clip code guard (2 min)", "clip_sens", false)   // v0.10.10 (W4): copied one-time codes leave the history after 2 minutes (default OFF)
        }

        try { group("Data & Backup") { g -> g.addView(DataPage.build(ctx)) } } catch (_: Throwable) { }   // v0.4.1

        refreshTop()   // v0.4.2

        return root
    }
}
