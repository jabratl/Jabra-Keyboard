package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.clip.ClipList
import com.mgb.keyboard.clip.ClipMeta
import com.mgb.keyboard.typing.ClipSelect
import com.mgb.keyboard.typing.ClipSensitive   // v0.10.10 (W4)

/*
 * v0.0.6: clipboard history (+ pinned, delete, search). All new code is here; MgbIme.kt has small hooks marked "v0.0.6".
 * Data stays ONLY on the phone: prefs file "jabra", new key "clips_v1". No network, no new permission.
 * New prefs keys: clip_on(bool,true) = remember copied text, edit_bar(bool,true) = show the Select all/Cut/Copy/Paste bar.
 */

/** Saves/loads the history. Never throws. */
class ClipStore(private val prefs: JabraPrefs) {
    private var list: ClipList = try { ClipList.parse(prefs.str("clips_v1", "")) } catch (_: Throwable) { ClipList() }   // v0.10.10: var (reload after "Clear clipboard history now")

    init { live = this }   // v0.10.10 (W4)

    /** v0.10.10 (W4): re-reads the list from prefs (used after the Privacy page cleared it) and drops meta of clips that are gone. */
    fun reload() { try { list = ClipList.parse(prefs.str("clips_v1", "")); if (meta.prune(list.view(""))) saveMeta() } catch (_: Throwable) { } }

    companion object {   // v0.10.10 (W4)
        @Volatile private var live: ClipStore? = null
        /** Deletes all UNPINNED clips straight in prefs (Privacy page has no ClipStore). Keeps pinned. Returns how many were removed. */
        fun clearUnpinnedNow(prefs: JabraPrefs): Int = try {
            val l = ClipList.parse(prefs.str("clips_v1", ""))
            val before = l.size()
            l.clearUnpinned()
            prefs.setStr("clips_v1", l.serialize())
            val m = ClipMeta.parse(prefs.str("clip_meta_v1", ""))
            m.prune(l.view(""))
            prefs.setStr("clip_meta_v1", m.serialize())
            live?.reload()
            before - l.size()
        } catch (_: Throwable) { 0 }
    }

    private fun save() { try { prefs.setStr("clips_v1", list.serialize()) } catch (_: Throwable) { } }

    // v0.4.3: side table (made / used / count) in NEW pref clip_meta_v1; clips_v1 is untouched. Loaded lazily.
    private val meta: ClipMeta by lazy { try { ClipMeta.parse(prefs.str("clip_meta_v1", "")) } catch (_: Throwable) { ClipMeta() } }
    private fun saveMeta() { try { prefs.setStr("clip_meta_v1", meta.serialize()) } catch (_: Throwable) { } }
    /** v0.4.3.1: edit one clip. Returns ClipList.replace(): 0 ok, 1 empty, 2 too long, 3 duplicate, 4 not found. */
    fun replace(i: ClipList.Item, t: String): Int = try {
        val old = i.text
        val rc = list.replace(i, t)
        if (rc == 0 && old != t) { save(); meta.rename(old, t); saveMeta() }
        rc
    } catch (_: Throwable) { 4 }
    fun has(t: String): Boolean = try { list.view("").any { it.text == t } } catch (_: Throwable) { false }
    fun countOf(t: String): Int = try { meta.count(t) } catch (_: Throwable) { 0 }
    fun madeOf(t: String): Long = try { meta.madeAt(t) } catch (_: Throwable) { 0L }   // v0.10.24 (C1): when the clip was copied (0 = unknown)
    fun noteUse(i: ClipList.Item) { try { meta.used(i.text, System.currentTimeMillis()); saveMeta() } catch (_: Throwable) { } }
    fun sorted(l: List<ClipList.Item>, mode: Int): List<ClipList.Item> = try { if (mode == 0) l else meta.sort(l, mode) } catch (_: Throwable) { l }
    /** Auto-delete (pref clip_expire 0..3): unpinned only; runs when the panel opens or a clip is added, never on a timer. Returns how many were removed. */
    fun expireNow(): Int = try { expireCodes() + expireOld() } catch (_: Throwable) { 0 }   // v0.10.10 (W4): code guard first, then the old Auto-delete

    /** v0.10.10 (W4): code guard (pref clip_sens, default OFF): an unpinned clip that looks like a one-time code is removed 2 minutes after it was copied. Same trigger as Auto-delete (clip added / panel opened), no timer. */
    private fun expireCodes(): Int = try {
        if (!prefs.bool("clip_sens", false)) 0 else {
            val now = System.currentTimeMillis()
            var touched = false
            val gone = list.view("").filter { c ->
                var m = meta.madeAt(c.text)
                if (m <= 0L && !c.pinned && ClipSensitive.looksLikeCode(c.text)) { meta.made(c.text, now); touched = true; m = now }
                ClipSensitive.isDue(c.pinned, c.text, m, now)
            }
            val n = if (gone.isNotEmpty()) list.removeAll(gone) else 0
            if (n > 0) { save(); meta.prune(list.view("")); touched = true }
            if (touched) saveMeta()
            n
        }
    } catch (_: Throwable) { 0 }

    private fun expireOld(): Int = try {
        val ttl = ClipMeta.ttlMs(prefs.int("clip_expire", 0))
        if (ttl <= 0L) 0 else {
            val before = meta.size()
            val gone = meta.expired(list.view(""), ttl, System.currentTimeMillis())
            val n = if (gone.isNotEmpty()) list.removeAll(gone) else 0
            if (n > 0) save()
            val pr = meta.prune(list.view(""))
            if (pr || n > 0 || meta.size() != before) saveMeta()
            n
        }
    } catch (_: Throwable) { 0 }

    fun add(t: String) { try { val isNew = !has(t); if (list.add(t)) save(); if (isNew) { meta.made(t, System.currentTimeMillis()); saveMeta() } else if (prefs.bool("clip_sens", false) && ClipSensitive.looksLikeCode(t)) { meta.remake(t, System.currentTimeMillis()); saveMeta() }; expireNow() } catch (_: Throwable) { } }   // v0.4.3: records "made" for new clips
    fun view(q: String): List<ClipList.Item> = try { list.view(q) } catch (_: Throwable) { emptyList() }
    fun togglePin(i: ClipList.Item): Boolean = try { val ok = list.togglePin(i); if (ok) save(); ok } catch (_: Throwable) { false }
    fun remove(i: ClipList.Item) { try { if (list.remove(i)) save() } catch (_: Throwable) { } }
    fun clearUnpinned() { try { list.clearUnpinned(); save() } catch (_: Throwable) { } }

    // v0.2.5.1: engine wrappers (all safe)
    fun pinnedView(q: String): List<ClipList.Item> = try { list.viewPinned(q) } catch (_: Throwable) { emptyList() }
    fun recentView(q: String): List<ClipList.Item> = try { list.viewRecent(q) } catch (_: Throwable) { emptyList() }
    fun pinnedCount(): Int = try { list.pinnedCount() } catch (_: Throwable) { 0 }
    fun setPinned(sel: List<ClipList.Item>, pin: Boolean): Int = try { val n = list.setPinned(sel, pin); if (n > 0) save(); n } catch (_: Throwable) { 0 }
    fun removeAll(sel: List<ClipList.Item>): Int = try { val n = list.removeAll(sel); if (n > 0) save(); n } catch (_: Throwable) { 0 }
    /** Saves a text as a new clip. Returns ClipList.check(): 0 = saved, 1 = empty, 2 = too long. */
    fun addChecked(t: String): Int = try { val c = ClipList.check(t); if (c == 0) add(t); c } catch (_: Throwable) { 1 }
}

/** The clipboard screen: list (pinned first), tap = paste, star = pin, x = delete, search, clear (asks twice). */
class ClipPanel(private val ctx: Context, private val host: JabraHost, private val store: ClipStore, private val fieldText: () -> String = { "" }) {   // v0.2.5.1: fieldText = selection or whole field text

    private var searching = false
    private var query = ""
    private var confirmClear = false
    private var msg = ""   // v0.2.5.1: one short message for the header line (shown once, then cleared)
    private val sel = ClipSelect()        // v0.2.5.3: select mode state lives HERE (not in the view), so it survives refresh()
    private var confirmDel = false        // v0.2.5.3: "Sure? delete N" two-tap safety
    private var editing: ClipList.Item? = null   // v0.4.3.1: clip being edited (null = normal panel)
    private var editText = ""
    private var editCaps = false
    private var confirmSave = false
    /** v0.4.4: set by MgbIme: saves the given clip texts into the Text Vault and returns a short message. null = no "To Vault" button. */
    var toVault: ((List<String>) -> String)? = null

    /** Call when the panel is opened from outside (fresh start). */
    // v0.3.7: Undo for deleted clips (x, Delete, Clear). Kept until you leave the clipboard or delete something else.
    private var undoBuf: List<Pair<String, Boolean>> = emptyList()
    private fun keepUndo(items: List<ClipList.Item>) { if (items.isNotEmpty()) undoBuf = items.map { Pair(it.text, it.pinned) } }
    private fun doUndo() {
        var n = 0
        for ((t, p) in undoBuf.reversed()) {
            try {
                store.add(t)
                if (p) { val x = store.view("").firstOrNull { c -> c.text == t }; if (x != null && !x.pinned) store.togglePin(x) }
                n++
            } catch (_: Throwable) { }
        }
        undoBuf = emptyList()
        msg = "Restored " + n
        confirmClear = false; confirmDel = false
        refresh()
    }

    fun reset() { editing = null; confirmSave = false; undoBuf = emptyList(); searching = false; query = ""; confirmClear = false; msg = ""; sel.leave(); confirmDel = false }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    // v0.8.7: theme helpers (Classic theme + Classic look = exactly the old colours; any error = old colour)
    private val pf: JabraPrefs by lazy { JabraPrefs(ctx) }
    private fun paintBg(v: View) { try { JabraThemes.paint(pf, v) } catch (_: Throwable) { v.setBackgroundColor(Color.parseColor("#DDDDDD")) } }
    private fun dim(): Int = try { JabraTokens.dimText(pf) } catch (_: Throwable) { Color.DKGRAY }
    private fun ink(): Int = try { if (JabraTokens.isClassic(pf)) Color.BLACK else JabraTokens.panelText(pf) } catch (_: Throwable) { Color.BLACK }
    private fun hush(): Int = try { JabraTokens.cardNote(pf) } catch (_: Throwable) { Color.GRAY }

    /** v0.8.7: themed button (UiKit.btn follows theme colour + look); old plain button = automatic fallback. */
    private fun btn(label: String, weight: Float, hDp: Int, onClick: (Button) -> Unit): Button {
        try { return UiKit.btn(ctx, pf, label, weight, hDp, 0f, { v -> try { host.feedback(v) } catch (_: Throwable) { } }, onClick) } catch (_: Throwable) { }
        return btnOld(label, weight, hDp, onClick)
    }

    private fun btnOld(label: String, weight: Float, hDp: Int, onClick: (Button) -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        b.setOnClickListener {
            try { host.feedback(b) } catch (_: Throwable) { }
            try { onClick(b) } catch (_: Throwable) { }
        }
        val lp = LinearLayout.LayoutParams(if (weight > 0f) 0 else LinearLayout.LayoutParams.WRAP_CONTENT, dp(hDp), if (weight > 0f) weight else 0f)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        b.layoutParams = lp
        return b
    }

    // v0.10.13 (D2.3): row() = UiKit.row (same horizontal LinearLayout). Old body kept as rowOld() = automatic fallback.
    private fun row(): LinearLayout {
        try { return UiKit.row(ctx) } catch (_: Throwable) { }
        return rowOld()
    }

    private fun rowOld(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    private fun refresh() { host.showPanel("clipboard") }

    /** Returns null on any problem -> keyboard shows normal letters. */
    fun build(): View? = try { make() } catch (_: Throwable) { null }

    private fun make(): View {
        if (editing != null) { try { return makeEdit() } catch (_: Throwable) { editing = null } }   // v0.4.3.1
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)   // v0.8.7
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        // v0.2.5.3: forget selected clips that no longer exist (after delete / Clear)
        try { sel.prune(store.pinnedView("") + store.recentView("")) } catch (_: Throwable) { sel.leave() }
        val selOn = sel.isOn

        // top bar
        val top = row()
        top.addView(btn("ABC", 1.0f, 40) { sel.leave(); confirmDel = false; host.showLetters() })   // v0.2.5.3: ABC leaves select mode
        val searchBtn = btn(if (searching) "✕ Search" else "🔍 Search", 1.6f, 40) {
            searching = !searching
            query = ""
            confirmClear = false
            refresh()
        }
        JabraIcons.putThemed(ctx, searchBtn, if (searching) "close" else "search")   // v0.0.7
        top.addView(searchBtn)
        top.addView(btn("Add", 1.0f, 40) { addFromField() })   // v0.2.5.1: save the current field text as a clip
        val clearBtn = btn(if (confirmClear) "Sure? (unpinned)" else "Clear", 1.8f, 40) {
            if (confirmClear) { keepUndo(store.recentView("")); store.clearUnpinned(); confirmClear = false } else confirmClear = true
            refresh()
        }
        if (confirmClear) clearBtn.textSize = 12f   // v0.2.5.1: the button is a bit narrower now
        top.addView(clearBtn)
        root.addView(top)

        // v0.2.5.1: header line message (e.g. "Added", "Field is empty"); shown once
        // v0.2.5.2: the header line is now always there: left = message, right = [List|Cards] switch
        val shownMsg = msg
        msg = ""
        val cardsOn = try { JabraPrefs(ctx).int("clip_view", 0) == 1 } catch (_: Throwable) { false }
        val head = row()
        head.gravity = Gravity.CENTER_VERTICAL
        val m = TextView(ctx)
        m.text = if (selOn && shownMsg.isEmpty()) sel.count().toString() + " selected" else shownMsg   // v0.2.5.3
        m.textSize = 12f
        m.setTextColor(dim())
        m.setPadding(dp(8), 0, dp(4), 0)
        m.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        head.addView(m)
        if (undoBuf.isNotEmpty()) {   // v0.3.7
            val undoBtn = btn("↶ Undo", 0f, 30) { doUndo() }
            undoBtn.textSize = 12f
            undoBtn.setPadding(dp(10), 0, dp(10), 0)
            head.addView(undoBtn)
        }
        val sortLv = try { JabraPrefs(ctx).int("clip_sort", 0).coerceIn(0, 3) } catch (_: Throwable) { 0 }   // v0.4.3
        val sortBtn = btn("\u2195 " + arrayOf("Newest", "Oldest", "A-Z", "Used")[sortLv], 0f, 30) {
            try { JabraPrefs(ctx).setInt("clip_sort", (sortLv + 1) % 4) } catch (_: Throwable) { }
            confirmClear = false
            refresh()
        }
        sortBtn.textSize = 12f
        sortBtn.setPadding(dp(10), 0, dp(10), 0)
        val viewBtn = btn(if (cardsOn) "View: Cards" else "View: List", 0f, 30) {
            try { JabraPrefs(ctx).setInt("clip_view", if (cardsOn) 0 else 1) } catch (_: Throwable) { }
            confirmClear = false
            refresh()
        }
        viewBtn.textSize = 12f
        viewBtn.setPadding(dp(10), 0, dp(10), 0)
        if (!selOn) {   // v0.2.5.3: in select mode the action row below replaces View / Manage
            head.addView(sortBtn)   // v0.4.3
            head.addView(viewBtn)
            val manageBtn = btn("Manage", 0f, 30) { sel.enter(); confirmClear = false; confirmDel = false; refresh() }
            manageBtn.textSize = 12f
            manageBtn.setPadding(dp(10), 0, dp(10), 0)
            head.addView(manageBtn)
        }
        root.addView(head)
        if (selOn) root.addView(selectBar())

        if (searching) {
            val q = TextView(ctx)
            q.text = (if (JabraPrefs(ctx).bool("icons_on", true)) "" else "🔍 ") + query + "▏"   // v0.0.7: icon is on the button now
            q.textSize = 15f
            q.setTextColor(ink())
            q.setPadding(dp(8), dp(2), dp(8), dp(2))
            root.addView(q)
        }

        // list
        try { store.expireNow() } catch (_: Throwable) { }   // v0.4.3: auto-delete (only when the panel is drawn, no timer)
        val items = store.view(query)
        val listH = if (searching) 88 else 176
        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(listH))
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        if (items.isEmpty()) {
            val t = TextView(ctx)
            t.text = if (searching) "No match" else "Nothing copied yet. Copy some text and it will show here."
            t.setPadding(dp(8), dp(12), dp(8), dp(12))
            t.setTextColor(dim())
            col.addView(t)
        }
        // v0.2.5.1: "Pinned" / "Recent" header lines (only when both groups have items and not searching)
        val pinnedItems = store.sorted(store.pinnedView(query), sortLv)   // v0.4.3 (Newest = unchanged)
        val recentItems = store.sorted(store.recentView(query), sortLv)
        val showHeaders = !searching && pinnedItems.isNotEmpty() && recentItems.isNotEmpty()
        // v0.2.5.2: Cards view (clip_view = 1); any problem -> null -> the old List code below
        val lines = try { JabraPrefs(ctx).int("clip_lines", 0).coerceIn(0, 2) } catch (_: Throwable) { 0 }
        val cards: View? = if (cardsOn && items.isNotEmpty()) {
            ClipCards.build(ctx, host, pinnedItems, recentItems, !searching, lines,
                { it2 -> store.togglePin(it2); refresh() },
                { it2 -> keepUndo(listOf(it2)); store.remove(it2); refresh() },
                SelectMode(selOn, { x -> sel.isSelected(x) }, { x -> sel.toggle(x); confirmDel = false; refresh() }, { x -> sel.enterWith(x); confirmDel = false; refresh() },
                    { x -> store.noteUse(x) }, { x -> infoLine(x) }))   // v0.4.3
        } else null
        if (cards != null) {
            col.addView(cards)
        } else {
            if (showHeaders) col.addView(sectionHeader("Pinned"))
            for (entry in pinnedItems) col.addView(itemRow(entry))
            if (showHeaders) col.addView(sectionHeader("Recent"))
            for (entry in recentItems) col.addView(itemRow(entry))
        }
        sv.addView(col)
        root.addView(sv)

        // tiny search keyboard (Latin letters only)
        if (searching) {
            for (letters in arrayOf("qwertyuiop", "asdfghjkl", "zxcvbnm")) {
                val r = row()
                letters.forEach { ch -> r.addView(btn(ch.toString(), 1f, 34) { query += ch; confirmClear = false; refresh() }) }
                if (letters == "asdfghjkl") r.addView(JabraIcons.putThemed(ctx, btn("␣", 1f, 34) { query += " "; refresh() }, "space"))   // v0.0.7
                if (letters == "zxcvbnm") r.addView(JabraIcons.putThemed(ctx, btn("⌫", 1.5f, 34) { if (query.isNotEmpty()) query = query.dropLast(1); refresh() }, "back"))   // v0.0.7
                root.addView(r)
            }
        }
        return root
    }

    /** v0.4.3.1: edit screen for ONE clip: Cancel | Use field text | Save (two taps), text box, mini keys (Latin). Bangla = type it in the field, then "Use field text". */
    private fun makeEdit(): View {
        val item = editing ?: return make()
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)   // v0.8.7
        root.setPadding(dp(2), dp(4), dp(2), dp(4))
        fun touched(a: () -> Unit) { a(); confirmSave = false; refresh() }

        val top = row()
        top.addView(btn("Cancel", 1f, 40) { editing = null; confirmSave = false; refresh() })
        top.addView(btn("Use field text", 1.6f, 40) {
            val t = try { fieldText() } catch (_: Throwable) { "" }
            if (t.isBlank()) msg = "Field is empty" else editText = t
            confirmSave = false
            refresh()
        })
        val saveBtn = btn(if (confirmSave) "Sure? Save" else "Save", 1.2f, 40) {
            if (!confirmSave) { confirmSave = true; refresh() }
            else {
                when (store.replace(item, editText)) {
                    0 -> { msg = "Clip saved"; editing = null; sel.leave() }
                    1 -> msg = "Text is empty"
                    2 -> msg = "Too long (max " + ClipList.MAX_LEN + ")"
                    3 -> msg = "Another clip has this text"
                    else -> { msg = "Clip not found"; editing = null }
                }
                confirmSave = false
                refresh()
            }
        }
        top.addView(saveBtn)
        root.addView(top)

        val info = TextView(ctx)
        info.text = (if (msg.isEmpty()) "Edit clip" else msg) + "   " + editText.length + "/" + ClipList.MAX_LEN
        msg = ""
        info.textSize = 12f
        info.setTextColor(dim())
        info.setPadding(dp(8), dp(2), dp(8), dp(2))
        root.addView(info)

        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64))
        val tv = TextView(ctx)
        tv.text = editText.replace("\n", " \u23CE ") + "\u258F"
        tv.textSize = 14f
        tv.setTextColor(ink())
        tv.setPadding(dp(8), dp(2), dp(8), dp(2))
        sv.addView(tv)
        root.addView(sv)

        fun ch(c: Char): String = if (editCaps) c.uppercaseChar().toString() else c.toString()
        for (letters in arrayOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm")) {
            val r = row()
            if (letters == "zxcvbnm") r.addView(btn(if (editCaps) "\u21E7 ON" else "\u21E7", 1.5f, 34) { touched { editCaps = !editCaps } })
            letters.forEach { c -> r.addView(btn(ch(c), 1f, 34) { touched { editText += ch(c); editCaps = false } }) }
            if (letters == "zxcvbnm") r.addView(btn("\u232B", 1.5f, 34) { touched { if (editText.isNotEmpty()) editText = editText.dropLast(1) } })
            root.addView(r)
        }
        val last = row()
        for (p in arrayOf(".", ",", "?", "!", "-", "'")) last.addView(btn(p, 1f, 34) { touched { editText += p } })
        last.addView(btn("space", 2.5f, 34) { touched { editText += " " } })
        last.addView(btn("\u23CE", 1.2f, 34) { touched { editText += "\n" } })
        root.addView(last)
        return root
    }

    /** v0.2.5.1: tap on Add = save selected text (or the whole field) as a new clip. */
    private fun addFromField() {
        val t = try { fieldText() } catch (_: Throwable) { "" }
        val dup = store.has(t)   // v0.4.3
        when (store.addChecked(t)) {
            0 -> msg = if (dup) "Already saved (moved to top)" else "Added to Recent"
            2 -> msg = "Too long (max " + ClipList.MAX_LEN + ")"
            else -> msg = "Field is empty"
        }
        confirmClear = false
        refresh()
    }

    /** v0.2.5.3: action row in select mode: All | Pin | Unpin | Delete | Done. */
    private fun selectBar(): View {
        val r = row()
        r.addView(btn("All", 1f, 34) {
            sel.selectAll(store.pinnedView(query) + store.recentView(query))
            confirmDel = false
            refresh()
        })
        r.addView(btn("Pin", 1f, 34) {
            val chosen = sel.selection()
            val want = chosen.count { !it.pinned }
            if (chosen.isEmpty()) msg = "Nothing selected"
            else {
                val n = store.setPinned(chosen, true)
                msg = if (n < want) "Pinned " + n + " of " + want + " (limit " + ClipList.MAX_PINNED + ")" else "Pinned " + n
            }
            confirmDel = false
            refresh()
        })
        r.addView(btn("Unpin", 1f, 34) {
            val chosen = sel.selection()
            if (chosen.isEmpty()) msg = "Nothing selected" else msg = "Unpinned " + store.setPinned(chosen, false)
            confirmDel = false
            refresh()
        })
        val n = sel.count()
        val del = btn(if (confirmDel) "Sure? delete " + n else "Delete", if (confirmDel) 2.2f else 1.2f, 34) {
            if (n == 0) { msg = "Nothing selected"; confirmDel = false }
            else if (confirmDel) {
                keepUndo(sel.selection())
                val gone = store.removeAll(sel.selection())
                sel.clear()
                msg = "Deleted " + gone
                confirmDel = false
            } else confirmDel = true
            refresh()
        }
        if (confirmDel) del.textSize = 12f
        r.addView(del)
        // v0.3.6: Paste = type ALL selected clips in the order you tapped them, joined by a blank line, then back to letters
        r.addView(btn("Paste", 1.2f, 34) {
            val chosen = sel.selection()
            if (chosen.isEmpty()) { msg = "Nothing selected"; confirmDel = false; refresh() }
            else {
                val joined = chosen.joinToString("\n\n") { it.text }
                sel.leave(); confirmDel = false
                host.commitWord()
                host.typeText(joined)
                host.showLetters()
            }
        })
        r.addView(btn("Done", 1f, 34) { sel.leave(); confirmDel = false; refresh() })
        // v0.3.8: second row: Merge = save the ticked clips as ONE new clip; 1 line = paste them joined by a space (line breaks removed)
        val r2 = row()
        r2.addView(btn("Merge → 1 clip", 1f, 34) {
            val chosen = sel.selection()
            if (chosen.size < 2) msg = "Select 2 or more"
            else when (store.addChecked(chosen.joinToString("\n\n") { it.text })) {
                0 -> { msg = "Merged " + chosen.size + " into 1 (see Recent)"; sel.leave() }
                2 -> msg = "Too long (max " + ClipList.MAX_LEN + ")"
                else -> msg = "Nothing to merge"
            }
            confirmDel = false
            refresh()
        })
        r2.addView(btn("Edit", 0.6f, 34) {   // v0.4.3.1: edit ONE ticked clip
            val chosen = sel.selection()
            if (chosen.size != 1) msg = "Select ONE clip to edit"
            else { editing = chosen[0]; editText = chosen[0].text; editCaps = false; confirmSave = false }
            confirmDel = false
            refresh()
        })
        r2.addView(btn("Paste in 1 line", 1f, 34) {
            val chosen = sel.selection()
            if (chosen.isEmpty()) { msg = "Nothing selected"; confirmDel = false; refresh() }
            else {
                val one = chosen.joinToString(" ") { it.text.replace(Regex("\\s*\\n\\s*"), " ").trim() }
                sel.leave(); confirmDel = false
                host.commitWord()
                host.typeText(one)
                host.showLetters()
            }
        })
        if (toVault != null) {   // v0.4.4: copy the ticked clips into the Text Vault (folder last used there); clips stay in the history
            val vb = btn("To Vault", 0.8f, 34) {
                val chosen = sel.selection()
                if (chosen.isEmpty()) msg = "Nothing selected"
                else { msg = try { toVault?.invoke(chosen.map { it.text }) ?: "" } catch (_: Throwable) { "Could not save to Vault" }; sel.leave() }
                confirmDel = false
                refresh()
            }
            vb.textSize = 12f
            r2.addView(vb)
        }
        val colAll = LinearLayout(ctx)
        colAll.orientation = LinearLayout.VERTICAL
        colAll.addView(r)
        colAll.addView(r2)
        return colAll
    }

    private fun sectionHeader(label: String): View {
        val h = TextView(ctx)
        h.text = label
        h.textSize = 12f
        h.setTypeface(h.typeface, android.graphics.Typeface.BOLD)
        h.setTextColor(dim())
        h.setPadding(dp(8), dp(4), dp(8), dp(2))
        return h
    }

    /** v0.4.3: "123" (chars) or "123 · 2x" (pasted twice). Empty when clip_count is OFF. */
    private fun infoLine(item: ClipList.Item): String = try {
        if (!JabraPrefs(ctx).bool("clip_count", true)) "" else {
            val n = store.countOf(item.text)
            val age = try { com.mgb.keyboard.clip.ClipAge.shortAge(store.madeOf(item.text), System.currentTimeMillis()) } catch (_: Throwable) { "" }   // v0.10.24 (C1)
            item.text.length.toString() + (if (n > 0) " \u00B7 " + n + "x" else "") + (if (age.isNotEmpty()) " \u00B7 " + age else "")
        }
    } catch (_: Throwable) { "" }

    /** v0.10.24 (C1): list row + a thin divider line under it. Any problem = the plain row as before. */
    private fun itemRow(item: ClipList.Item): View {
        val r = itemRowBase(item)
        return try {
            val w = LinearLayout(ctx)
            w.orientation = LinearLayout.VERTICAL
            w.addView(r)
            val ln = View(ctx)
            ln.setBackgroundColor(JabraTokens.withAlpha(dim(), 0x40))
            ln.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxOf(1, dp(1))).also { it.setMargins(dp(8), dp(1), dp(8), dp(1)) }
            w.addView(ln)
            w
        } catch (_: Throwable) { r }
    }

    private fun itemRowBase(item: ClipList.Item): View {
        val lv = try { JabraPrefs(ctx).int("clip_lines", 0).coerceIn(0, 2) } catch (_: Throwable) { 0 }   // v0.2.5.1: 0/1/2 = 2/4/6 lines
        val rowH = 44 + 22 * lv
        val r = row()
        r.gravity = Gravity.CENTER_VERTICAL
        val selOn = sel.isOn
        val picked = selOn && sel.isSelected(item)
        val prefix = if (selOn) (if (picked) "☑ " else "☐ ") + (if (item.pinned) "★ " else "") else (if (item.pinned && !JabraPrefs(ctx).bool("icons_on", true)) "★ " else "")   // v0.2.5.3: check box + pinned tag in select mode
        val t = btn(prefix + item.text.replace("\n", " ⏎ "), 1f, rowH) {
            if (sel.isOn) {   // v0.2.5.3: tap toggles, NO paste
                sel.toggle(item); confirmDel = false; refresh()
            } else {
                host.commitWord()
                host.typeText(item.text)
                store.noteUse(item)   // v0.4.3
                host.showLetters()
            }
        }
        if (picked) t.setBackgroundColor(try { JabraTokens.rowPicked(pf) } catch (_: Throwable) { Color.parseColor("#CFE0F5") })
        t.setOnLongClickListener {   // v0.2.5.3: long-press = select mode with this clip selected
            if (sel.isOn) false else { sel.enterWith(item); confirmDel = false; refresh(); true }
        }
        t.textSize = 13f
        t.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        t.maxLines = 2 + 2 * lv
        t.ellipsize = TextUtils.TruncateAt.END
        t.setPadding(dp(8), 0, dp(8), 0)
        r.addView(t)
        try { ClipLook.tag(ctx, pf, item.text)?.let { tg -> tg.setPadding(dp(5), dp(1), dp(5), dp(1)); r.addView(tg) } } catch (_: Throwable) { }   // v0.10.24 (C1): kind tag (Link / Email / Code / Phone / Bangla)
        try {   // v0.4.3: char count (small grey) between the text and the star
            val inf = infoLine(item)
            if (inf.isNotEmpty()) {
                val ct = TextView(ctx)
                ct.text = inf
                ct.textSize = 10f
                ct.setTextColor(hush())
                ct.setPadding(dp(2), 0, dp(2), 0)
                r.addView(ct)
            }
        } catch (_: Throwable) { }
        if (selOn) return r   // v0.2.5.3: no star / x while selecting
        r.addView(JabraIcons.putThemed(ctx, btn(if (item.pinned) "★" else "☆", 0f, rowH) { store.togglePin(item); refresh() }.also { it.minimumWidth = dp(44); it.minWidth = dp(44) }, if (item.pinned) "pinned" else "pin"))   // v0.0.7
        r.addView(JabraIcons.putThemed(ctx, btn("✕", 0f, rowH) { keepUndo(listOf(item)); store.remove(item); refresh() }.also { it.minimumWidth = dp(44); it.minWidth = dp(44) }, "close"))   // v0.0.7
        return r
    }
}
