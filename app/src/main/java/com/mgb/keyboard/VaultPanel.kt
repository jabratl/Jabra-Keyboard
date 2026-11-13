package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.typing.PanelLayout
import com.mgb.keyboard.typing.VarFill
import com.mgb.keyboard.typing.VaultView
import com.mgb.keyboard.vault.VaultList

/*
 * v0.4.4 PERSONAL TEXT VAULT panel (route showPanel("vault"), toolbar item "vault", OFF until the user adds it).
 * v0.4.5 adds the kind chips (All / Snippets / Notes / Glossary), Note + Term entries and the Meaning button.
 * Built in code like ClipPanel (same look). Data = VaultStore (file vault_v1.json). NOTHING here runs on the key-press path.
 * Screens: LIST (chips, search, entries) / EDIT (title, text, kind, folder + mini keys) / FILL ({name} fields) / MOVE / FOLDERS / NEWFOLDER.
 * Tap an entry = paste it (entry with {name} fields opens FILL first). Long-press = select mode (Move / Edit / Delete).
 * build() returns null on any problem -> the keyboard shows the letters.
 */
class VaultPanel(private val ctx: Context, private val host: JabraHost, private val store: VaultStore, private val fieldText: () -> String = { "" }) {

    private enum class Scr { LIST, EDIT, FILL, MOVE, FOLDERS, NEWFOLDER }

    private val prefs = JabraPrefs(ctx)

    private var scr = Scr.LIST
    private var kindF = 0                  // v0.4.5 chips: 0 All, 1 Snippets (+ templates), 2 Notes, 3 Glossary
    private var folderF: String? = null    // null = all folders, "" = Unfiled
    private var searching = false
    private var query = ""
    private var msg = ""
    private var selOn = false
    private val sel = LinkedHashSet<Int>()
    private var confirmDel = false
    private var confirmFolder = ""         // folder name waiting for the 2nd tap (delete)
    private var sheetId = 0                // v0.16.10 (X10): entry whose long-press actions sheet is open (0 = none), New style only

    // edit screen
    private var editId = 0                 // 0 = new entry
    private var eKind = VaultList.SNIPPET
    private var eFolder = ""
    private var eTitle = ""
    private var eText = ""
    private var eField = 1                 // 0 = title, 1 = text
    private var eCaps = false
    private var eConfirm = false

    // fill screen
    private var fillId = 0
    private var fillNames: List<String> = emptyList()
    private val fillVals = LinkedHashMap<String, String>()
    private var fillIdx = 0

    // new folder screen
    private var fName = ""

    /** Call when the panel is opened from outside (fresh start). */
    fun reset() {
        scr = Scr.LIST; searching = false; query = ""; msg = ""; selOn = false; sel.clear(); confirmDel = false; confirmFolder = ""
        eConfirm = false; eCaps = false; fName = ""; fillVals.clear()
        keepY = -1; fromRefresh = false   // v0.16.1 (X1)
        sheetId = 0   // v0.16.10 (X10)
        store.unload()   // always read the file fresh (a backup may have been restored)
    }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()
    private fun now(): Long = System.currentTimeMillis()
    // v0.16.1 (X1): refresh() keeps the list position (panel rebuild = new ScrollView at 0). refreshTop() = top on purpose (search, sort, chips).
    private var curSv: ScrollView? = null
    private var keepY = -1
    private var fromRefresh = false
    private fun refresh() {
        try { if (scr == Scr.LIST) keepY = curSv?.scrollY ?: -1; fromRefresh = true } catch (_: Throwable) { keepY = -1; fromRefresh = false }
        host.showPanel("vault")
    }
    private fun refreshTop() { keepY = -1; fromRefresh = false; host.showPanel("vault") }

    // v0.6.6 theme: buttons = UiKit.btn (follows theme / Jabra look / Borderless), old body kept as btnOld() = automatic fallback
    private fun btn(label: String, weight: Float, hDp: Int, onClick: (Button) -> Unit): Button {
        try { return UiKit.btn(ctx, prefs, label, weight, hDp, 0f, { v -> host.feedback(v) }, onClick) } catch (_: Throwable) { }
        return btnOld(label, weight, hDp, onClick)
    }

    // v0.6.6 theme colours (Classic = the same values as before; any problem = old value)
    private fun paintBg(v: View) { try { JabraThemes.paint(prefs, v) } catch (_: Throwable) { v.setBackgroundColor(Color.parseColor("#DDDDDD")) } }
    private fun dim(): Int = try { JabraTokens.dimText(prefs) } catch (_: Throwable) { Color.DKGRAY }             // grey text on the panel
    private fun ink(): Int = try { if (JabraTokens.isClassic(prefs)) Color.BLACK else JabraTokens.panelText(prefs) } catch (_: Throwable) { Color.BLACK }   // main text on the panel
    private fun rowInk(): Int = try { JabraTokens.cardText(prefs) } catch (_: Throwable) { Color.BLACK }          // main text on a row
    private fun rowDim(): Int = try { JabraTokens.onCardDim(prefs) } catch (_: Throwable) { Color.DKGRAY }        // grey text on a row
    private fun rowNote(): Int = try { JabraTokens.cardNote(prefs) } catch (_: Throwable) { Color.GRAY }          // small count on a row
    private fun rowFill(picked: Boolean): Int = try {
        if (JabraTokens.isClassic(prefs)) (if (picked) Color.parseColor("#BBDEFB") else Color.WHITE)
        else if (picked) JabraTokens.withAlpha(JabraTokens.ACCENT, 0x66) else (JabraTokens.btnFill(prefs) ?: Color.WHITE)
    } catch (_: Throwable) { if (picked) Color.parseColor("#BBDEFB") else Color.WHITE }

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

    private fun small(b: Button): Button { b.textSize = 12f; return b }

    // v0.10.12 (D2.2): row() = UiKit.row (same horizontal LinearLayout). Old body kept as rowOld() = automatic fallback.
    private fun row(): LinearLayout {
        try { return UiKit.row(ctx) } catch (_: Throwable) { }
        return rowOld()
    }

    private fun rowOld(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    private fun rootView(): LinearLayout {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)   // v0.6.6
        root.setPadding(dp(2), dp(4), dp(2), dp(4))
        return root
    }

    private fun info(text: String): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = 12f
        t.setTextColor(dim())   // v0.6.6
        t.setPadding(dp(8), dp(2), dp(8), dp(2))
        return t
    }

    fun build(): View? = try {
        when (scr) {
            Scr.LIST -> makeList()
            Scr.EDIT -> makeEdit()
            Scr.FILL -> makeFill()
            Scr.MOVE -> makeMove()
            Scr.FOLDERS -> makeFolders()
            Scr.NEWFOLDER -> makeNewFolder()
        }
    } catch (_: Throwable) { scr = Scr.LIST; null }

    // ------------------------------------------------------------------ helpers

    private fun kindFilter(): String? = when (kindF) { 1 -> VaultList.SNIPPET; 2 -> VaultList.NOTE; 3 -> VaultList.TERM; else -> null }

    private fun leaveSel() { selOn = false; sel.clear(); confirmDel = false }

    private fun folderName(f: String): String = if (f.isEmpty()) "Unfiled" else f

    /** Save after a change. Returns true when saved; otherwise [msg] holds the reason. */
    private fun saved(): Boolean {
        val e = store.commit()
        if (e.isNotEmpty()) { msg = e; return false }
        return true
    }

    private fun paste(e: VaultList.Entry, text: String) {
        try { store.get()?.noteUse(e.id, now()); store.commit() } catch (_: Throwable) { }
        store.unload()
        host.commitWord()
        host.typeText(text)
        host.showLetters()
    }

    private fun useEntry(e: VaultList.Entry) {
        val text = VaultList.pasteText(e)
        if (e.kind != VaultList.TERM && VarFill.hasFields(text)) {
            fillId = e.id
            fillNames = VarFill.fields(text)
            fillVals.clear()
            fillIdx = 0
            scr = Scr.FILL
            refresh()
        } else paste(e, text)
    }

    /** Called from the clipboard select bar ("To Vault"): saves the texts as snippets in the folder last used here. Returns a short message. */
    fun saveFromClips(texts: List<String>): String {
        try {
            val l = store.get() ?: return store.problem
            val dest = prefs.str("vault_dest", "")
            val folder = if (l.hasFolder(dest)) dest else ""
            var n = 0
            var dup = 0
            for (t in texts.reversed()) {   // first ticked clip ends on top
                if (l.hasText(t)) { dup++; continue }
                if (l.add(VaultList.SNIPPET, folder, "", t, now()) == VaultList.OK) n++
            }
            if (n > 0) { val e = store.commit(); if (e.isNotEmpty()) return e }
            store.unload()
            return if (n == 0 && dup > 0) "Already in Vault" else "To Vault: " + n + " in " + folderName(folder) + (if (dup > 0) " (" + dup + " already there)" else "")
        } catch (_: Throwable) { return "Could not save to Vault" }
    }

    private fun addFromField() {
        val t = try { fieldText() } catch (_: Throwable) { "" }
        val l = store.get()
        if (l == null) { msg = store.problem; refresh(); return }
        if (t.isBlank() || kindF == 3) {
            // empty field = open the editor (type a new entry); Glossary needs a term + meaning = editor with the field text as meaning
            openEdit(0, if (kindF == 3) VaultList.TERM else if (kindF == 2) VaultList.NOTE else VaultList.SNIPPET, folderF ?: "", "", t)
            return
        }
        val kind = if (kindF == 2) VaultList.NOTE else VaultList.SNIPPET
        if (l.hasText(t)) { msg = "Already saved"; refresh(); return }
        when (l.add(kind, folderF ?: "", "", t, now())) {
            VaultList.OK -> { if (saved()) msg = "Saved to " + folderName(folderF ?: "") }
            VaultList.TOO_LONG -> msg = "Too long (max " + VaultList.MAX_LEN + ")"
            VaultList.FULL -> msg = "Vault is full (" + VaultList.MAX_ENTRIES + ")"
            else -> msg = "Field is empty"
        }
        refresh()
    }

    private fun openEdit(id: Int, kind: String, folder: String, title: String, text: String) {
        editId = id; eKind = kind; eFolder = folder; eTitle = title; eText = text
        eField = if (kind == VaultList.TERM && title.isEmpty()) 0 else 1
        eCaps = false; eConfirm = false
        scr = Scr.EDIT
        refresh()
    }

    // ------------------------------------------------------------------ LIST

    private fun makeList(): View {
        val restoreY = if (fromRefresh) keepY else -1   // v0.16.1 (X1)
        fromRefresh = false
        if (restoreY < 0) keepY = -1
        // v0.16.10 (X10): pref panel_ui = 1 -> the NEW list (makeListNew below). Default 0 / any problem = the classic list that follows, untouched.
        val newUi = try { PanelLayout.isNewStyle(prefs.int("panel_ui", 0)) } catch (_: Throwable) { false }
        if (newUi) { try { return makeListNew(restoreY) } catch (_: Throwable) { sheetId = 0 } }
        val root = rootView()
        val l = store.get()

        val top = row()
        top.addView(btn("ABC", 1f, 40) { leaveSel(); store.unload(); host.showLetters() })
        top.addView(btn(if (searching) "✕ Search" else "🔍 Search", 1.6f, 40) { searching = !searching; query = ""; refreshTop() })
        top.addView(btn("+ Add", 1f, 40) { addFromField() })
        top.addView(btn("Folders", 1.3f, 40) { leaveSel(); scr = Scr.FOLDERS; msg = ""; refreshTop() })
        root.addView(top)

        if (l == null) {
            root.addView(info(if (store.problem.isEmpty()) "Vault not available" else store.problem))
            return root
        }

        // header line: message / "N selected" on the left, Sort + Manage on the right
        val shown = msg
        msg = ""
        val sortLv = try { prefs.int("vault_sort", 0).coerceIn(0, 2) } catch (_: Throwable) { 0 }
        val head = row()
        head.gravity = Gravity.CENTER_VERTICAL
        val m = TextView(ctx)
        m.text = if (selOn && shown.isEmpty()) sel.size.toString() + " selected" else shown
        m.textSize = 12f
        m.setTextColor(dim())   // v0.6.6
        m.setPadding(dp(8), 0, dp(4), 0)
        m.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        head.addView(m)
        if (!selOn) {
            val sortBtn = small(btn("↕ " + arrayOf("Newest", "A-Z", "Used")[sortLv], 0f, 30) {
                try { prefs.setInt("vault_sort", (sortLv + 1) % 3) } catch (_: Throwable) { }
                refreshTop()   // v0.16.1
            })
            sortBtn.setPadding(dp(10), 0, dp(10), 0)
            head.addView(sortBtn)
            val manage = small(btn("Manage", 0f, 30) { selOn = true; sel.clear(); confirmDel = false; refresh() })
            manage.setPadding(dp(10), 0, dp(10), 0)
            head.addView(manage)
        }
        root.addView(head)
        if (selOn) root.addView(selectBar(l))

        // v0.4.5: kind chips
        val chips = row()
        val names = arrayOf("All", "Snippets", "Notes", "Glossary")
        val kinds = arrayOf<String?>(null, VaultList.SNIPPET, VaultList.NOTE, VaultList.TERM)
        for (i in 0..3) {
            val label = (if (kindF == i) "✓ " else "") + names[i] + " " + l.countKind(kinds[i])
            chips.addView(small(btn(label, 1f, 30) { kindF = i; confirmDel = false; refreshTop() }))
        }
        root.addView(chips)

        // folder chips (scroll sideways)
        if (!searching) {
            val hs = HorizontalScrollView(ctx)
            hs.isHorizontalScrollBarEnabled = false
            val fr = row()
            fun chip(label: String, value: String?) {
                val on = folderF == value
                fr.addView(small(btn((if (on) "✓ " else "") + label, 0f, 30) {
                    folderF = value
                    if (value != null) { try { prefs.setStr("vault_dest", value) } catch (_: Throwable) { } }
                    confirmDel = false
                    refreshTop()   // v0.16.1
                }).also { it.setPadding(dp(10), 0, dp(10), 0) })
            }
            chip("All folders", null)
            chip("Unfiled", "")
            for (f in l.folders()) chip(f, f)
            hs.addView(fr)
            root.addView(hs)
        }

        if (searching) {
            val q = TextView(ctx)
            q.text = "🔍 " + query + "▏"
            q.textSize = 15f
            q.setTextColor(ink())   // v0.6.6
            q.setPadding(dp(8), dp(2), dp(8), dp(2))
            root.addView(q)
        }

        // list
        val items = l.view(kindFilter(), if (searching) null else folderF, query, sortLv)
        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(if (searching) 64 else 120))
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        if (items.isEmpty()) {
            val t = TextView(ctx)
            t.text = if (searching) "No match" else if (l.size() == 0) "Vault is empty. Type or select text in a field, then tap + Add. Or tap + Add on an empty field to write one here." else "Nothing here. Change the chips above."
            t.setPadding(dp(8), dp(12), dp(8), dp(12))
            t.setTextColor(dim())   // v0.6.6
            col.addView(t)
        }
        for (e in items) col.addView(entryRow(e))
        sv.addView(col)
        root.addView(sv)
        try {   // v0.16.1 (X1): same list position after a rebuild (restored after the first layout, clamped)
            curSv = sv
            if (com.mgb.keyboard.typing.ScrollKeep.wants(restoreY)) {
                keepY = -1
                sv.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        try {
                            if (sv.viewTreeObserver.isAlive) sv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            val ch = col.height
                            if (ch > 0) sv.scrollTo(0, com.mgb.keyboard.typing.ScrollKeep.clamp(restoreY, ch, sv.height))
                        } catch (_: Throwable) { }
                    }
                })
            }
        } catch (_: Throwable) { }

        if (searching) {
            for (letters in arrayOf("qwertyuiop", "asdfghjkl", "zxcvbnm")) {
                val r = row()
                letters.forEach { ch -> r.addView(btn(ch.toString(), 1f, 34) { query += ch; refreshTop() }) }
                if (letters == "asdfghjkl") r.addView(JabraIcons.putThemed(ctx, btn("␣", 1f, 34) { query += " "; refreshTop() }, "space"))
                if (letters == "zxcvbnm") r.addView(JabraIcons.putThemed(ctx, btn("⌫", 1.5f, 34) { if (query.isNotEmpty()) query = query.dropLast(1); refreshTop() }, "back"))
                root.addView(r)
            }
        }
        return root
    }

    // ===================== v0.16.10 (X10): NEW Vault list (pref panel_ui = 1) =====================
    // Sticky top bar + folder chips (with counts) + kind chips + info line, card list (title + 1-line preview), tap = insert,
    // long-press = actions sheet (Edit / Move / Delete / Select), select mode = bottom bar. Edit / Add / Move / Folders screens are the old ones.
    // ---- v0.18 (easy browse): rounded card + kind badge + count pill, used by the New list AND the classic list ----
    private fun kindColor(kind: String): Int = when (kind) {
        VaultList.NOTE -> 0xFFF9A825.toInt()
        VaultList.TERM -> 0xFF2E7D32.toInt()
        VaultList.TEMPLATE -> 0xFF7B1FA2.toInt()
        else -> JabraTokens.ACCENT
    }
    private fun kindGlyph(kind: String): String = when (kind) {
        VaultList.NOTE -> "\uD83D\uDCDD"       // memo
        VaultList.TERM -> "\uD83D\uDCD6"       // open book
        VaultList.TEMPLATE -> "\uD83D\uDCC4"   // page
        else -> "\uD83D\uDCCB"                 // clipboard
    }
    private fun cardBg(picked: Boolean): android.graphics.drawable.GradientDrawable {
        val g = android.graphics.drawable.GradientDrawable()
        try {
            g.setColor(rowFill(picked))
            g.cornerRadius = dp(10).toFloat()
            g.setStroke(dp(if (picked) 2 else 1), JabraTokens.cardStroke(prefs, picked, false))
        } catch (_: Throwable) { }
        return g
    }
    private fun badge(kind: String): View {
        val t = TextView(ctx)
        t.text = kindGlyph(kind)
        t.textSize = 16f
        t.gravity = Gravity.CENTER
        try {
            val g = android.graphics.drawable.GradientDrawable()
            g.shape = android.graphics.drawable.GradientDrawable.OVAL
            g.setColor(JabraTokens.withAlpha(kindColor(kind), 0x33))
            t.background = g
        } catch (_: Throwable) { }
        val lp = LinearLayout.LayoutParams(dp(32), dp(32))
        lp.leftMargin = dp(8)
        t.layoutParams = lp
        return t
    }
    private fun countPill(n: Int): TextView {
        val c = TextView(ctx)
        c.text = n.toString() + "\u00D7"
        c.textSize = 11f
        c.setTextColor(rowNote())
        try {
            val g = android.graphics.drawable.GradientDrawable()
            g.setColor(JabraTokens.withAlpha(rowInk(), 0x14))
            g.cornerRadius = dp(8).toFloat()
            c.background = g
        } catch (_: Throwable) { }
        c.setPadding(dp(7), dp(2), dp(7), dp(2))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.rightMargin = dp(8)
        c.layoutParams = lp
        return c
    }

    private fun makeListNew(restoreY: Int): View {
        val root = rootView()
        val l = store.get()

        val top = row()
        top.addView(btn("ABC", 1f, 40) { leaveSel(); sheetId = 0; store.unload(); host.showLetters() })
        top.addView(btn(if (searching) "\u2715 Search" else "\uD83D\uDD0D Search", 1.6f, 40) { searching = !searching; query = ""; sheetId = 0; refreshTop() })
        if (!selOn) {
            top.addView(btn("+ Add", 1f, 40) { sheetId = 0; addFromField() })
            top.addView(btn("Folders", 1.3f, 40) { leaveSel(); sheetId = 0; scr = Scr.FOLDERS; msg = ""; refreshTop() })
        } else {
            top.addView(btn("Done", 1f, 40) { leaveSel(); sheetId = 0; refresh() })
        }
        root.addView(top)

        if (l == null) {
            root.addView(info(if (store.problem.isEmpty()) "Vault not available" else store.problem))
            return root
        }
        // forget selected / sheet ids that no longer exist (after delete / move)
        val all = l.view(null, null, "", 0)
        try { val live = VaultView.liveIds(ArrayList(sel), all); sel.retainAll(live.toSet()) } catch (_: Throwable) { sel.clear() }
        if (sheetId != 0 && l.find(sheetId) == null) sheetId = 0

        // folder chips with counts (scroll sideways)
        if (!searching) {
            val hs = HorizontalScrollView(ctx)
            hs.isHorizontalScrollBarEnabled = false
            val fr = row()
            fun chip(label: String, count: Int, value: String?) {
                val on = folderF == value
                val nm = if (label.isEmpty()) "Unfiled" else label
                fr.addView(UiKit.chip(ctx, prefs, nm + " " + Math.max(0, count), on, 32, { v -> host.feedback(v) }) {
                    folderF = value
                    if (value != null) { try { prefs.setStr("vault_dest", value) } catch (_: Throwable) { } }
                    leaveSel(); sheetId = 0
                    refreshTop()
                })
            }
            chip("All", l.size(), null)
            chip("", l.countIn(""), "")
            for (f in l.folders()) chip(f, l.countIn(f), f)
            hs.addView(fr)
            root.addView(hs)
        }

        // kind chips (same filter as the classic list)
        val chips = row()
        val names = arrayOf("All", "Snippets", "Notes", "Glossary")
        val kinds = arrayOf<String?>(null, VaultList.SNIPPET, VaultList.NOTE, VaultList.TERM)
        for (i in 0..3) {
            val kc = UiKit.chip(ctx, prefs, names[i] + " " + l.countKind(kinds[i]), kindF == i, 32, { v -> host.feedback(v) }) { kindF = i; leaveSel(); sheetId = 0; refreshTop() }
            try { val klp = kc.layoutParams as LinearLayout.LayoutParams; klp.width = 0; klp.weight = 1f; kc.layoutParams = klp } catch (_: Throwable) { }
            chips.addView(kc)
        }
        root.addView(chips)

        if (searching) {
            val q = TextView(ctx)
            q.text = "\uD83D\uDD0D " + query + "\u258F"
            q.textSize = 15f
            q.setTextColor(rowInk())
            q.background = cardBg(false)
            q.setPadding(dp(12), dp(6), dp(12), dp(6))
            val qlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            qlp.setMargins(dp(4), dp(2), dp(4), dp(2))
            q.layoutParams = qlp
            root.addView(q)
        }

        val sortLv = try { prefs.int("vault_sort", 0).coerceIn(0, 2) } catch (_: Throwable) { 0 }
        val items = l.view(kindFilter(), if (searching) null else folderF, query, sortLv)

        // info line: message / "N selected" / "12 items" + Sort (hidden while searching: the tiny keyboard needs the room)
        val shown = msg
        msg = ""
        if (!searching) {
            val head = row()
            head.gravity = Gravity.CENTER_VERTICAL
            val m = TextView(ctx)
            m.text = if (shown.isNotEmpty()) shown else if (selOn) VaultView.selectSummary(sel.size) else VaultView.countLine(items.size)
            m.textSize = 12f
            m.setTextColor(dim())
            m.setPadding(dp(8), 0, dp(4), 0)
            m.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            head.addView(m)
            if (!selOn) {
                val sortBtn = small(btn("\u2195 " + arrayOf("Newest", "A-Z", "Used")[sortLv], 0f, 30) {
                    try { prefs.setInt("vault_sort", (sortLv + 1) % 3) } catch (_: Throwable) { }
                    refreshTop()
                })
                sortBtn.setPadding(dp(10), 0, dp(10), 0)
                head.addView(sortBtn)
            }
            root.addView(head)
        }

        // long-press actions sheet (one entry), not in select mode
        val sheetE = if (!selOn && sheetId != 0) l.find(sheetId) else null
        if (sheetE != null) root.addView(sheetNew(l, sheetE))

        // card list
        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(if (searching) 64 else 140))
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        if (items.isEmpty()) {
            val t = TextView(ctx)
            t.text = if (searching) "No match" else if (l.size() == 0) "Vault is empty. Type or select text in a field, then tap + Add. Or tap + Add on an empty field to write one here." else "Nothing here. Change the chips above."
            t.setPadding(dp(16), dp(12), dp(16), dp(12))
            t.setTextColor(dim())
            t.gravity = Gravity.CENTER
            if (!searching && l.size() == 0) t.text = "\uD83D\uDDC2\n" + t.text
            col.addView(t)
        }
        for (e in items) col.addView(cardNew(e))
        sv.addView(col)
        root.addView(sv)
        try {   // keep the list position after a rebuild (same as the classic list, X1)
            curSv = sv
            if (com.mgb.keyboard.typing.ScrollKeep.wants(restoreY)) {
                keepY = -1
                sv.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        try {
                            if (sv.viewTreeObserver.isAlive) sv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            val ch = col.height
                            if (ch > 0) sv.scrollTo(0, com.mgb.keyboard.typing.ScrollKeep.clamp(restoreY, ch, sv.height))
                        } catch (_: Throwable) { }
                    }
                })
            }
        } catch (_: Throwable) { }

        if (searching) {
            for (letters in arrayOf("qwertyuiop", "asdfghjkl", "zxcvbnm")) {
                val r = row()
                letters.forEach { ch -> r.addView(btn(ch.toString(), 1f, 34) { query += ch; refreshTop() }) }
                if (letters == "asdfghjkl") r.addView(JabraIcons.putThemed(ctx, btn("\u2423", 1f, 34) { query += " "; refreshTop() }, "space"))
                if (letters == "zxcvbnm") r.addView(JabraIcons.putThemed(ctx, btn("\u232B", 1.5f, 34) { if (query.isNotEmpty()) query = query.dropLast(1); refreshTop() }, "back"))
                root.addView(r)
            }
        } else if (selOn) {
            root.addView(selectBarNew(l, items))
        }
        return root
    }

    /** One card: title (bold) + 1-line preview with tag. Tap = insert (or toggle in select mode), long-press = actions sheet. */
    private fun cardNew(e: VaultList.Entry): View {
        val r = row()
        r.gravity = Gravity.CENTER_VERTICAL
        val picked = selOn && sel.contains(e.id)
        r.background = cardBg(picked)   // v0.18: rounded card
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(4), dp(3), dp(4), dp(3))
        r.layoutParams = lp
        r.minimumHeight = dp(52)
        r.addView(badge(e.kind))   // v0.18: kind icon on a tinted circle

        val colL = LinearLayout(ctx)
        colL.orientation = LinearLayout.VERTICAL
        colL.setPadding(dp(10), dp(4), dp(4), dp(4))
        colL.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val title = TextView(ctx)
        title.text = (if (picked) "\u2713 " else "") + VaultView.title(e)
        title.textSize = 15f
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        title.setTextColor(rowInk())
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        colL.addView(title)
        val l2 = VaultView.line2(e, VaultView.PREVIEW_DEFAULT)
        if (l2.isNotEmpty()) {
            val prev = TextView(ctx)
            prev.text = l2
            prev.textSize = 11f
            prev.setTextColor(rowDim())
            prev.maxLines = 1
            prev.ellipsize = TextUtils.TruncateAt.END
            colL.addView(prev)
        }
        r.addView(colL)
        if (e.count > 0) r.addView(countPill(e.count))   // v0.18
        if (e.kind == VaultList.TERM && !selOn) {
            val mb = small(btn("Meaning", 0f, 34) { sheetId = 0; paste(e, VaultList.meaning(e)) })
            mb.setPadding(dp(8), 0, dp(8), 0)
            r.addView(mb)
        }
        r.setOnClickListener {
            try { host.feedback(r) } catch (_: Throwable) { }
            try {
                if (selOn) { if (!sel.remove(e.id)) sel.add(e.id); confirmDel = false; refresh() }
                else { sheetId = 0; useEntry(e) }
            } catch (_: Throwable) { }
        }
        r.setOnLongClickListener {
            try { if (!selOn) { sheetId = e.id; confirmDel = false; refresh() } } catch (_: Throwable) { }
            true
        }
        return r
    }

    /** Long-press sheet for one entry: Edit | Move | Delete (two taps) | Select | Close. */
    private fun sheetNew(l: VaultList, e: VaultList.Entry): View {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        paintBg(box)
        box.addView(info(VaultView.title(e)))
        val r = row()
        for (a in VaultView.sheetActions(e, selOn)) {
            when (a) {
                "Edit" -> r.addView(small(btn("Edit", 1f, 36) { sheetId = 0; openEdit(e.id, e.kind, e.folder, e.title, e.text) }))
                "Move" -> r.addView(small(btn("Move", 1f, 36) { sheetId = 0; sel.clear(); sel.add(e.id); scr = Scr.MOVE; msg = ""; confirmDel = false; refreshTop() }))
                "Delete" -> r.addView(small(btn(if (confirmDel) "Sure? delete" else "Delete", if (confirmDel) 2f else 1.2f, 36) {
                    if (confirmDel) {
                        val gone = l.removeAll(arrayListOf(e.id))
                        if (saved()) msg = "Deleted " + gone
                        sheetId = 0; confirmDel = false
                    } else confirmDel = true
                    refresh()
                }))
                "Select" -> r.addView(small(btn("Select", 1f, 36) { sheetId = 0; selOn = true; sel.clear(); sel.add(e.id); confirmDel = false; refresh() }))
            }
        }
        r.addView(small(btn("\u2715", 0.6f, 36) { sheetId = 0; confirmDel = false; refresh() }))
        box.addView(r)
        return box
    }

    /** Select mode bottom bar: All (only if not all picked) | Move + Edit(one) + Delete (only if something picked) | Done. */
    private fun selectBarNew(l: VaultList, shownItems: List<VaultList.Entry>): View {
        val acts = ArrayList<Pair<String, () -> Unit>>()
        fun act(label: String, f: () -> Unit): Pair<String, () -> Unit> = Pair(label, f)
        val n = sel.size
        for (a in VaultView.barActions(n, shownItems.size)) {
            when (a) {
                "All" -> acts.add(act("All") { sel.clear(); for (e in shownItems) sel.add(e.id); confirmDel = false; refresh() })
                "Move" -> acts.add(act("Move") { scr = Scr.MOVE; msg = ""; confirmDel = false; refreshTop() })
                "Edit" -> acts.add(act("Edit") {
                    val e = if (sel.size == 1) l.find(sel.first()) else null
                    if (e == null) { msg = "Select ONE entry to edit"; confirmDel = false; refresh() } else openEdit(e.id, e.kind, e.folder, e.title, e.text)
                })
                "Delete" -> acts.add(act(if (confirmDel) "Sure? delete " + n else "Delete") {
                    if (confirmDel) {
                        val gone = l.removeAll(ArrayList(sel))
                        if (saved()) msg = "Deleted " + gone
                        sel.clear(); confirmDel = false
                    } else confirmDel = true
                    refresh()
                })
                "Done" -> acts.add(act("Done") { leaveSel(); refresh() })
            }
        }
        return PanelKit.stickyBar(ctx, prefs, host, acts)
    }

    private fun entryRow(e: VaultList.Entry): View {
        val r = row()
        r.gravity = Gravity.CENTER_VERTICAL
        val picked = selOn && sel.contains(e.id)
        r.background = cardBg(picked)   // v0.18: rounded card (v0.6.6 colours)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(4), dp(3), dp(4), dp(3))
        r.layoutParams = lp
        r.minimumHeight = dp(52)
        r.addView(badge(e.kind))   // v0.18

        val colL = LinearLayout(ctx)
        colL.orientation = LinearLayout.VERTICAL
        colL.setPadding(dp(8), dp(3), dp(4), dp(3))
        colL.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val title = TextView(ctx)
        title.text = (if (picked) "✓ " else "") + e.title
        title.textSize = 14f
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        title.setTextColor(rowInk())   // v0.6.6
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        colL.addView(title)
        val prev = TextView(ctx)
        val tag = if (e.kind == VaultList.TEMPLATE) "Template · " else if (e.kind == VaultList.NOTE) "Note · " else ""
        prev.text = tag + e.text.replace("\n", " ")
        prev.textSize = 11f
        prev.setTextColor(rowDim())   // v0.6.6
        prev.maxLines = 1
        prev.ellipsize = TextUtils.TruncateAt.END
        colL.addView(prev)
        r.addView(colL)

        if (e.count > 0) r.addView(countPill(e.count))   // v0.18
        if (e.kind == VaultList.TERM && !selOn) {   // v0.4.5: tap row = paste the term, this button = paste the meaning
            val mb = small(btn("Meaning", 0f, 32) { paste(e, VaultList.meaning(e)) })
            mb.setPadding(dp(8), 0, dp(8), 0)
            r.addView(mb)
        }

        r.setOnClickListener {
            try { host.feedback(r) } catch (_: Throwable) { }
            try {
                if (selOn) { if (!sel.remove(e.id)) sel.add(e.id); confirmDel = false; refresh() }
                else useEntry(e)
            } catch (_: Throwable) { }
        }
        r.setOnLongClickListener {
            try { selOn = true; sel.clear(); sel.add(e.id); confirmDel = false; refresh() } catch (_: Throwable) { }
            true
        }
        return r
    }

    /** Select mode actions: All | Move | Edit | Delete | Done. */
    private fun selectBar(l: VaultList): View {
        val r = row()
        r.addView(small(btn("All", 1f, 34) {
            val items = l.view(kindFilter(), if (searching) null else folderF, query, 0)
            sel.clear(); for (e in items) sel.add(e.id)
            confirmDel = false; refresh()
        }))
        r.addView(small(btn("Move", 1f, 34) {
            if (sel.isEmpty()) msg = "Nothing selected" else { scr = Scr.MOVE; msg = "" }
            confirmDel = false; refresh()
        }))
        r.addView(small(btn("Edit", 1f, 34) {
            val e = if (sel.size == 1) l.find(sel.first()) else null
            if (e == null) { msg = "Select ONE entry to edit"; confirmDel = false; refresh() }
            else openEdit(e.id, e.kind, e.folder, e.title, e.text)
        }))
        val n = sel.size
        val del = small(btn(if (confirmDel) "Sure? delete " + n else "Delete", if (confirmDel) 2.2f else 1.2f, 34) {
            if (n == 0) { msg = "Nothing selected"; confirmDel = false }
            else if (confirmDel) {
                val gone = l.removeAll(ArrayList(sel))
                if (saved()) msg = "Deleted " + gone
                sel.clear(); confirmDel = false
            } else confirmDel = true
            refresh()
        })
        r.addView(del)
        r.addView(small(btn("Done", 1f, 34) { leaveSel(); refresh() }))
        return r
    }

    // ------------------------------------------------------------------ MOVE

    private fun makeMove(): View {
        val root = rootView()
        val l = store.get()
        if (l == null) { scr = Scr.LIST; return makeList() }
        val top = row()
        top.addView(btn("Cancel", 1f, 40) { scr = Scr.LIST; refresh() })
        root.addView(top)
        root.addView(info("Move " + sel.size + " to:"))
        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150))
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val targets = ArrayList<String>()
        targets.add("")
        targets.addAll(l.folders())
        for (f in targets) {
            val b = btn(folderName(f) + "  (" + l.countIn(f) + ")", 0f, 38) {
                val n = l.move(ArrayList(sel), f)
                if (saved()) msg = "Moved " + n + " to " + folderName(f)
                leaveSel(); scr = Scr.LIST; refresh()
            }
            b.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38)).also { it.setMargins(dp(2), dp(2), dp(2), dp(2)) }
            col.addView(b)
        }
        sv.addView(col)
        root.addView(sv)
        return root
    }

    // ------------------------------------------------------------------ FOLDERS

    private fun makeFolders(): View {
        val root = rootView()
        val l = store.get()
        val top = row()
        top.addView(btn("Back", 1f, 40) { scr = Scr.LIST; confirmFolder = ""; refresh() })
        top.addView(btn("+ New folder", 1.5f, 40) { fName = ""; scr = Scr.NEWFOLDER; refresh() })
        root.addView(top)
        if (l == null) { root.addView(info(if (store.problem.isEmpty()) "Vault not available" else store.problem)); return root }
        val shown = msg
        msg = ""
        root.addView(info(if (shown.isEmpty()) "Folders (" + l.folders().size + " / " + VaultList.MAX_FOLDERS + "). Deleting a folder never deletes its entries." else shown))
        root.addView(btn("Add the \"Novel Translation\" set", 0f, 36) {
            val n = l.addPresetFolders()
            if (n == 0) msg = "Those folders already exist" else if (saved()) msg = "Added " + n + " folders"
            refresh()
        }.also { it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)).also { p -> p.setMargins(dp(2), dp(2), dp(2), dp(2)) }; it.textSize = 12f })
        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(130))
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        if (l.folders().isEmpty()) col.addView(info("No folders yet. Entries without a folder are shown as Unfiled."))
        for (f in l.folders()) {
            val r = row()
            r.gravity = Gravity.CENTER_VERTICAL
            val t = TextView(ctx)
            t.text = f + "  (" + l.countIn(f) + ")"
            t.textSize = 14f
            t.setTextColor(ink())   // v0.6.6
            t.maxLines = 1
            t.ellipsize = TextUtils.TruncateAt.END
            t.setPadding(dp(8), 0, dp(4), 0)
            t.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            r.addView(t)
            val ask = confirmFolder == f
            val d = small(btn(if (ask) "Sure? (" + l.countIn(f) + " → Unfiled)" else "Delete", 0f, 34) {
                if (ask) {
                    val moved = l.removeFolder(f)
                    if (folderF == f) folderF = null
                    if (saved()) msg = "Folder deleted" + (if (moved > 0) ", " + moved + " entries are Unfiled now" else "")
                    confirmFolder = ""
                } else confirmFolder = f
                refresh()
            })
            d.setPadding(dp(10), 0, dp(10), 0)
            r.addView(d)
            col.addView(r)
        }
        sv.addView(col)
        root.addView(sv)
        return root
    }

    private fun makeNewFolder(): View {
        val root = rootView()
        val top = row()
        top.addView(btn("Cancel", 1f, 40) { scr = Scr.FOLDERS; refresh() })
        top.addView(btn("From field", 1.3f, 40) {
            val t = try { fieldText() } catch (_: Throwable) { "" }
            if (t.isBlank()) msg = "Field is empty" else fName = t.replace("\n", " ").trim().take(VaultList.MAX_FOLDER_NAME)
            refresh()
        })
        top.addView(btn("Create", 1f, 40) {
            val l = store.get()
            if (l == null) { msg = store.problem; scr = Scr.FOLDERS }
            else when (l.addFolder(fName)) {
                VaultList.OK -> { if (saved()) msg = "Folder created"; scr = Scr.FOLDERS }
                VaultList.EXISTS -> msg = "That folder exists"
                VaultList.FULL -> msg = "Folder limit (" + VaultList.MAX_FOLDERS + ")"
                VaultList.TOO_LONG -> msg = "Name too long (max " + VaultList.MAX_FOLDER_NAME + ")"
                else -> msg = "Type a name first"
            }
            refresh()
        })
        root.addView(top)
        val shown = msg
        msg = ""
        root.addView(info(if (shown.isEmpty()) "New folder name" else shown))
        val box = TextView(ctx)
        box.text = fName + "▏"
        box.textSize = 16f
        box.setTextColor(ink())   // v0.6.6
        box.setPadding(dp(8), dp(4), dp(8), dp(4))
        root.addView(box)
        addKeys(root, { s -> if (fName.length < VaultList.MAX_FOLDER_NAME) fName += s.replace("\n", " ") }, { if (fName.isNotEmpty()) fName = fName.dropLast(1) })
        return root
    }

    // ------------------------------------------------------------------ EDIT

    private fun makeEdit(): View {
        val root = rootView()
        val l = store.get()
        if (l == null) { scr = Scr.LIST; return makeList() }
        val top = row()
        top.addView(btn("Cancel", 1f, 40) { scr = Scr.LIST; eConfirm = false; refresh() })
        top.addView(btn("From field", 1.3f, 40) {
            val t = try { fieldText() } catch (_: Throwable) { "" }
            if (t.isBlank()) msg = "Field is empty"
            else if (eField == 0) eTitle = t.replace("\n", " ").trim().take(VaultList.MAX_TITLE)
            else eText = t.take(VaultList.MAX_LEN)
            eConfirm = false
            refresh()
        })
        val needTwo = editId != 0
        top.addView(btn(if (eConfirm) "Sure? Save" else "Save", 1.2f, 40) {
            if (needTwo && !eConfirm) { eConfirm = true; refresh() }
            else {
                val code = if (editId == 0) l.add(eKind, eFolder, eTitle, eText, now()) else l.edit(editId, eKind, eFolder, eTitle, eText)
                when (code) {
                    VaultList.OK -> { if (saved()) { msg = "Saved"; scr = Scr.LIST; leaveSel() } }
                    VaultList.EMPTY -> msg = if (eKind == VaultList.TERM && eTitle.isBlank()) "A term needs a title" else "Text is empty"
                    VaultList.TOO_LONG -> msg = "Too long (max " + VaultList.MAX_LEN + ")"
                    VaultList.FULL -> msg = "Vault is full (" + VaultList.MAX_ENTRIES + ")"
                    else -> { msg = "Entry not found"; scr = Scr.LIST }
                }
                eConfirm = false
                refresh()
            }
        })
        root.addView(top)

        val isTerm = eKind == VaultList.TERM
        val r2 = row()
        r2.addView(small(btn(if (eField == 0) "Typing: Title" else if (isTerm) "Typing: Meaning" else "Typing: Text", 1.5f, 32) { eField = 1 - eField; eConfirm = false; refresh() }))
        r2.addView(small(btn(VaultList.kindLabel(eKind) + " ▾", 1f, 32) { eKind = VaultList.nextKind(eKind); eConfirm = false; refresh() }))
        r2.addView(small(btn(folderName(eFolder) + " ▾", 1.4f, 32) {
            val all = ArrayList<String>(); all.add(""); all.addAll(l.folders())
            val i = all.indexOf(eFolder)
            eFolder = all[(i + 1) % all.size]
            eConfirm = false; refresh()
        }))
        root.addView(r2)

        val shown = msg
        msg = ""
        root.addView(info((if (shown.isEmpty()) (if (editId == 0) "New entry" else "Edit entry") else shown) + "   " + eText.length + "/" + VaultList.MAX_LEN))

        val ttl = TextView(ctx)
        ttl.text = (if (isTerm) "Term: " else "Title: ") + eTitle + (if (eField == 0) "▏" else "")
        ttl.textSize = 13f
        ttl.setTextColor(ink())   // v0.6.6
        ttl.maxLines = 1
        ttl.ellipsize = TextUtils.TruncateAt.START
        ttl.setPadding(dp(8), dp(1), dp(8), dp(1))
        root.addView(ttl)

        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
        val tv = TextView(ctx)
        tv.text = eText.replace("\n", " ⏎ ") + (if (eField == 1) "▏" else "")
        tv.textSize = 14f
        tv.setTextColor(ink())   // v0.6.6
        tv.setPadding(dp(8), dp(1), dp(8), dp(1))
        sv.addView(tv)
        root.addView(sv)

        addKeys(root, { s ->
            if (eField == 0) { if (eTitle.length < VaultList.MAX_TITLE) eTitle += s.replace("\n", " ") }
            else if (eText.length < VaultList.MAX_LEN) eText += s
            eConfirm = false
        }, {
            if (eField == 0) { if (eTitle.isNotEmpty()) eTitle = eTitle.dropLast(1) } else if (eText.isNotEmpty()) eText = eText.dropLast(1)
            eConfirm = false
        })
        return root
    }

    // ------------------------------------------------------------------ FILL ({name} fields)

    private fun makeFill(): View {
        val root = rootView()
        val l = store.get()
        val e = if (l == null) null else l.find(fillId)
        if (l == null || e == null || fillNames.isEmpty()) { scr = Scr.LIST; return makeList() }
        if (fillIdx !in fillNames.indices) fillIdx = 0
        val top = row()
        top.addView(btn("Cancel", 1f, 40) { scr = Scr.LIST; refresh() })
        top.addView(btn("As is", 1f, 40) { paste(e, e.text) })   // paste with the {name} parts untouched
        top.addView(btn("From field", 1.3f, 40) {
            val t = try { fieldText() } catch (_: Throwable) { "" }
            if (t.isBlank()) msg = "Field is empty" else fillVals[fillNames[fillIdx]] = t.take(2000)
            refresh()
        })
        top.addView(btn("Paste", 1f, 40) { paste(e, VarFill.fill(e.text, fillVals)) })
        root.addView(top)
        val shown = msg
        msg = ""
        root.addView(info(if (shown.isEmpty()) e.title + ": fill the fields (empty ones stay as {name})" else shown))

        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(78))
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        for (i in fillNames.indices) {
            val nm = fillNames[i]
            val t = TextView(ctx)
            t.text = nm + ":  " + (fillVals[nm] ?: "").replace("\n", " ⏎ ") + (if (i == fillIdx) "▏" else "")
            t.textSize = 14f
            t.setTextColor(rowInk())   // v0.6.6
            t.setPadding(dp(8), dp(4), dp(8), dp(4))
            t.setBackgroundColor(rowFill(i == fillIdx))   // v0.6.6
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(2), dp(1), dp(2), dp(1))
            t.layoutParams = lp
            t.setOnClickListener { fillIdx = i; refresh() }
            col.addView(t)
        }
        sv.addView(col)
        root.addView(sv)

        addKeys(root, { s ->
            val nm = fillNames[fillIdx]
            val cur = fillVals[nm] ?: ""
            if (cur.length < 2000) fillVals[nm] = cur + s
        }, {
            val nm = fillNames[fillIdx]
            val cur = fillVals[nm] ?: ""
            if (cur.isNotEmpty()) fillVals[nm] = cur.dropLast(1)
        })
        return root
    }

    // ------------------------------------------------------------------ mini keys (Latin + digits + { } ; Bangla = type in the real field, then "From field")

    private fun addKeys(root: LinearLayout, onText: (String) -> Unit, onBack: () -> Unit) {
        fun touched(a: () -> Unit) { a(); refresh() }
        fun ch(c: Char): String = if (eCaps) c.uppercaseChar().toString() else c.toString()
        for (letters in arrayOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm")) {
            val r = row()
            if (letters == "zxcvbnm") r.addView(btn(if (eCaps) "⇧ ON" else "⇧", 1.5f, 32) { touched { eCaps = !eCaps } })
            letters.forEach { c -> r.addView(btn(ch(c), 1f, 32) { touched { onText(ch(c)); eCaps = false } }) }
            if (letters == "zxcvbnm") r.addView(btn("⌫", 1.5f, 32) { touched { onBack() } })
            root.addView(r)
        }
        val last = row()
        for (p in arrayOf(".", ",", "?", "-", "{", "}")) last.addView(btn(p, 1f, 32) { touched { onText(p) } })
        last.addView(btn("space", 2.2f, 32) { touched { onText(" ") } })
        last.addView(btn("⏎", 1.2f, 32) { touched { onText("\n") } })
        root.addView(last)
    }
}
