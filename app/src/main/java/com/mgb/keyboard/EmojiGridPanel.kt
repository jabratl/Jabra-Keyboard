package com.mgb.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.emoji.EmojiDb
import com.mgb.keyboard.emoji.EmojiSearch
import com.mgb.keyboard.emoji.Kaomoji
import com.mgb.keyboard.emoji.RecentEmoji

/**
 * v0.8.1 (E2): the new emoji panel = tabs (Recent + the groups of the data file) + ONE EmojiGridView + the same bottom row as the old panel
 * (ABC / space / backspace repeat / enter). Same route "emoji", same pref emoji_recent. Built only when EmojiStore has the data.
 */
class EmojiGridPanel(private val ctx: Context, private val host: JabraHost, private val db: EmojiDb) {

    private val prefs = JabraPrefs(ctx)
    private var cat = 0   // 0 = recent, 1.. = tab (cat - 1) in gs
    private var searching = false   // v0.8.3: search screen on
    private var query = ""
    private var toneBase: String? = null   // v0.8.3: base emoji whose tone picker row is showing
    private var fillTopRef: (() -> Unit)? = null
    private var kaoCache: List<String>? = null   // v0.8.4: kaomoji this phone can draw (checked once per panel)
    private var gs: List<Int> = emptyList()   // v0.8.2: data-file groups that have at least one emoji this phone can draw (a group with none gets no tab)

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun btn(label: String, w: Int, weight: Float, h: Int, onClick: (Button) -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        try { JabraThemes.style(prefs, b) } catch (_: Throwable) { }
        b.setOnClickListener {
            try { host.feedback(b) } catch (_: Throwable) { }
            try { onClick(b) } catch (_: Throwable) { }
        }
        val lp = LinearLayout.LayoutParams(w, h, weight)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        b.layoutParams = lp
        return b
    }

    /** Opens on the recent tab (or the first group if there are no recent emoji yet). */
    fun reset() {
        searching = false; query = ""; toneBase = null   // v0.8.3
        cat = if (itemsFor(0).isEmpty()) 1 else 0
    }

    fun build(): View? = try { make() } catch (_: Throwable) { null }

    private fun itemsFor(c: Int): List<String> {
        if (c == 0) {
            val out = ArrayList<String>()
            for (e in RecentEmoji.parse(prefs.str("emoji_recent", ""))) if (!EmojiStore.isHidden(e)) out.add(e)   // v0.8.2: recent also hides what the phone cannot draw
            return out
        }
        if (c - 1 >= gs.size) return emptyList()
        return EmojiStore.groupItems(gs[c - 1])   // v0.8.2: filtered list (Paint.hasGlyph)
    }

    /** v0.8.4: the kaomoji list minus entries with a character the phone cannot draw (Paint.hasGlyph per character). Any problem = whole list. */
    private fun kaomoji(): List<String> {
        val c = kaoCache
        if (c != null) return c
        val l: List<String> = try {
            val p = android.graphics.Paint()
            Kaomoji.filter(Kaomoji.CharCheck { ch -> try { p.hasGlyph(ch) } catch (_: Throwable) { true } })
        } catch (_: Throwable) { Kaomoji.filter(null) }
        kaoCache = l
        return l
    }

    private fun tone(): Int = try { prefs.int("emoji_tone", -1).coerceIn(-1, 4) } catch (_: Throwable) { -1 }   // v0.8.3: default skin tone, -1 = none

    private fun make(): View {
        val gl = ArrayList<Int>()
        for (g in 0 until db.groupCount()) if (EmojiStore.groupItems(g).isNotEmpty()) gl.add(g)
        gs = gl
        val groups = gl.size
        val kao = try { kaomoji() } catch (_: Throwable) { emptyList<String>() }
        val kaoTab = if (kao.isNotEmpty()) groups + 1 else -1   // v0.8.4: the last tab = kaomoji
        if (cat < 0 || cat > (if (kaoTab > 0) kaoTab else groups)) cat = if (groups > 0) 1 else 0
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        try { JabraThemes.paint(prefs, root) } catch (_: Throwable) { }
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        val ink = try { JabraThemes.iconColor(prefs) } catch (_: Throwable) { Color.parseColor("#222222") }
        val grid = EmojiGridView(ctx)
        try {
            grid.setColors((ink and 0x00FFFFFF) or 0x33000000, JabraThemes.noteColor(prefs))
        } catch (_: Throwable) { grid.setColors(0x33000000, Color.parseColor("#555555")) }

        // v0.8.3: the grid shows the default skin tone; curBase keeps the base emoji of every shown cell (for the tone picker)
        var curBase: List<String> = emptyList()
        fun setGrid(bases: List<String>, empty: String, keep: Boolean) {
            curBase = bases
            val t = tone()
            val shown = if (t < 0) bases else bases.map { EmojiStore.toned(it, t) }
            grid.setItems(shown, empty, keep)
        }
        val recentHint = "No recent emoji yet. Pick one from the other tabs."
        val searchHint = "Type a word, for example: heart, cat, pizza"
        val kaoView = ScrollView(ctx)   // v0.8.4: text emoticons, 2 per row (plain buttons, about 30 of them), filled on first use
        kaoView.visibility = View.GONE
        fun fillKao() {
            if ((kaoView.getChildAt(0) as? LinearLayout) != null) return
            val col = LinearLayout(ctx)
            col.orientation = LinearLayout.VERTICAL
            var k = 0
            while (k < kao.size) {
                val r = LinearLayout(ctx)
                r.orientation = LinearLayout.HORIZONTAL
                for (j in 0..1) {
                    if (k + j < kao.size) {
                        val txt = kao[k + j]
                        val b = btn(txt, 0, 1f, dp(40)) { host.commitWord(); host.typeText(txt) }   // never saved in emoji_recent (kaomoji contain spaces)
                        b.textSize = 13f
                        b.maxLines = 1
                        r.addView(b)
                    } else r.addView(View(ctx), LinearLayout.LayoutParams(0, dp(40), 1f))
                }
                col.addView(r)
                k += 2
            }
            kaoView.addView(col)
        }
        fun showCat(keep: Boolean) {
            val onKao = !searching && kaoTab > 0 && cat == kaoTab
            if (onKao) {
                try { fillKao() } catch (_: Throwable) { }
                grid.visibility = View.GONE
                kaoView.visibility = View.VISIBLE
                return
            }
            grid.visibility = View.VISIBLE
            kaoView.visibility = View.GONE
            if (searching) {
                setGrid(if (query.isBlank()) emptyList() else EmojiStore.search(query, 120), if (query.isBlank()) searchHint else "No match", keep)
            } else setGrid(itemsFor(cat), if (cat == 0) recentHint else "", keep)
        }

        val top = LinearLayout(ctx)   // v0.8.3: holds the tab row, the search line or the tone row
        top.orientation = LinearLayout.VERTICAL
        val hs = HorizontalScrollView(ctx)
        hs.isHorizontalScrollBarEnabled = false
        val tabs = LinearLayout(ctx)
        tabs.orientation = LinearLayout.HORIZONTAL
        hs.addView(tabs)
        val qv = TextView(ctx)
        qv.textSize = 15f
        qv.setTextColor(ink)
        qv.setPadding(dp(8), dp(2), dp(8), dp(2))
        qv.minHeight = dp(32)
        qv.gravity = Gravity.CENTER_VERTICAL
        fun showQuery() { qv.text = (if (JabraPrefs(ctx).bool("icons_on", true)) "" else "\uD83D\uDD0D ") + query + "\u258F" }
        val toneRow = LinearLayout(ctx)
        toneRow.orientation = LinearLayout.HORIZONTAL

        fun fillTop() {
            top.removeAllViews()
            val tb = toneBase
            if (tb != null) {
                toneRow.removeAllViews()
                val cur = tone()
                val vs = EmojiStore.toneVariants(tb)
                fun pickTone(str: String, t: Int) {
                    host.commitWord()
                    host.typeText(str)
                    try { prefs.setStr("emoji_recent", RecentEmoji.add(prefs.str("emoji_recent", ""), str)) } catch (_: Throwable) { }
                    try { prefs.setInt("emoji_tone", t) } catch (_: Throwable) { }
                    toneBase = null
                    fillTopRef?.invoke()
                    showCat(true)
                }
                val b0 = btn(tb, 0, 1f, dp(36)) { pickTone(tb, -1) }
                b0.textSize = 18f
                if (cur < 0) b0.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#9CC3E6"))
                toneRow.addView(b0)
                for (pr in vs) {
                    val bt = btn(pr.second, 0, 1f, dp(36)) { pickTone(pr.second, pr.first) }
                    bt.textSize = 18f
                    if (pr.first == cur) bt.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#9CC3E6"))
                    toneRow.addView(bt)
                }
                val x = JabraIcons.putThemed(ctx, btn("\u2715", dp(44), 0f, dp(36)) { toneBase = null; fillTopRef?.invoke() }, "close")
                toneRow.addView(x)
                top.addView(toneRow)
            } else if (searching) {
                showQuery()
                top.addView(qv)
            } else top.addView(hs)
        }
        fillTopRef = { fillTop() }

        grid.onPick = { e, v ->
            try { host.feedback(v) } catch (_: Throwable) { }
            host.commitWord()
            host.typeText(e)
            try { prefs.setStr("emoji_recent", RecentEmoji.add(prefs.str("emoji_recent", ""), e)) } catch (_: Throwable) { }
        }
        // v0.8.3: long press on an emoji that takes a skin tone = tone picker (only the variants this phone can draw)
        grid.onLong = { i, v ->
            try {
                val base = curBase.getOrNull(i)
                if (base != null && EmojiStore.toneable(base) && EmojiStore.toneVariants(base).isNotEmpty()) {
                    try { host.feedback(v) } catch (_: Throwable) { }
                    toneBase = base
                    fillTop()
                }
            } catch (_: Throwable) { }
        }

        // tabs: search + recent + one per group (the icon = the first emoji of the group)
        fun fillTabs() {
            tabs.removeAllViews()
            val sb = JabraIcons.putThemed(ctx, btn("\uD83D\uDD0D", dp(44), 0f, dp(36)) {
                searching = true
                query = ""
                toneBase = null
                host.showPanel("emoji")
            }, "search")
            sb.textSize = 16f
            tabs.addView(sb)
            for (i in 0..groups) {
                val label = if (i == 0) "\uD83D\uDD58" else EmojiStore.groupItems(gl[i - 1])[0]
                val t = btn(label, dp(44), 0f, dp(36)) {
                    cat = i
                    toneBase = null
                    fillTabs()
                    showCat(false)
                }
                t.textSize = 16f
                if (i == cat) t.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#9CC3E6"))
                tabs.addView(t)
            }
            if (kaoTab > 0) {   // v0.8.4: kaomoji tab (last)
                val kt = btn("^_^", dp(44), 0f, dp(36)) {
                    cat = kaoTab
                    toneBase = null
                    fillTabs()
                    showCat(false)
                }
                kt.textSize = 13f
                if (cat == kaoTab) kt.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#9CC3E6"))
                tabs.addView(kt)
            }
            val gb = btn("GIF", dp(44), 0f, dp(36)) { toneBase = null; host.showPanel("gif") }   // v0.18.5: last tab = GIF panel (KLIPY)
            gb.textSize = 13f
            tabs.addView(gb)
        }
        fillTabs()
        fillTop()
        root.addView(top)

        showCat(false)
        grid.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, if (searching) dp(88) else dp(host.keyHeightDp()) * 4)
        root.addView(grid)
        kaoView.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(host.keyHeightDp()) * 4)
        root.addView(kaoView)

        if (searching) {
            // v0.8.3: tiny search keyboard (Latin letters only, same pattern as the clipboard search)
            fun upd() { showQuery(); showCat(false) }
            for (letters in arrayOf("qwertyuiop", "asdfghjkl", "zxcvbnm")) {
                val r = LinearLayout(ctx)
                r.orientation = LinearLayout.HORIZONTAL
                letters.forEach { ch -> r.addView(btn(ch.toString(), 0, 1f, dp(34)) { if (query.length < EmojiSearch.MAX_QUERY) { query += ch; upd() } }) }
                if (letters == "asdfghjkl") r.addView(JabraIcons.putThemed(ctx, btn("\u2423", 0, 1f, dp(34)) { if (query.isNotEmpty() && query.length < EmojiSearch.MAX_QUERY) { query += " "; upd() } }, "space"))
                if (letters == "zxcvbnm") r.addView(JabraIcons.putThemed(ctx, btn("\u232B", 0, 1.5f, dp(34)) { if (query.isNotEmpty()) { query = query.dropLast(1); upd() } }, "back"))
                root.addView(r)
            }
            val bot = LinearLayout(ctx)
            bot.orientation = LinearLayout.HORIZONTAL
            bot.addView(btn("ABC", 0, 1f, dp(40)) { searching = false; query = ""; host.showLetters() })
            bot.addView(btn("Back to emoji", 0, 2f, dp(40)) { searching = false; query = ""; toneBase = null; host.showPanel("emoji") })
            root.addView(bot)
            return root
        }

        // bottom row (same as the old panel)
        val bot = LinearLayout(ctx)
        bot.orientation = LinearLayout.HORIZONTAL
        val h = dp(host.keyHeightDp())
        bot.addView(btn("ABC", 0, 1.5f, h) { host.showLetters() })
        bot.addView(btn("space", 0, 4f, h) { host.commitWord(); host.typeText(" ") })
        val bs = JabraIcons.putThemed(ctx, btn("\u232B", 0, 1.5f, h) { host.backspace() }, "back")
        try { if (prefs.bool("bs_repeat", true)) RepeatKeyHelper({ host.backspace() }, { v -> host.feedback(v) }).attach(bs) } catch (_: Throwable) { }
        bot.addView(bs)
        bot.addView(JabraIcons.putThemed(ctx, btn("\u23CE", 0, 1.4f, h) { host.commitWord(); host.enter() }, "enter"))
        root.addView(bot)
        return root
    }
}

/**
 * v0.8.1 (E2): what MgbIme.emojiPanel is now. reset() / build() are the same two calls as before.
 * Data ready (EmojiStore.get() != null) and the new panel builds -> new panel. Anything else (still loading, file missing,
 * any problem) -> the OLD EmojiPanel, so the emoji panel is never blank.
 */
class EmojiPanelSwitch(private val ctx: Context, private val host: JabraHost) {
    private val old by lazy { EmojiPanel(ctx, host) }
    private var grid: EmojiGridPanel? = null
    private var gridDb: EmojiDb? = null

    private fun newPanel(): EmojiGridPanel? {
        val d: EmojiDb? = try { EmojiStore.get() } catch (_: Throwable) { null }
        if (d == null) return null
        if (grid == null || gridDb !== d) { grid = EmojiGridPanel(ctx, host, d); gridDb = d }
        return grid
    }

    fun reset() {
        try { old.reset() } catch (_: Throwable) { }
        try { newPanel()?.reset() } catch (_: Throwable) { }
    }

    fun build(): View? {
        val g = try { newPanel() } catch (_: Throwable) { null }
        if (g != null) {
            val v = g.build()
            if (v != null) return v
        }
        return old.build()
    }
}
