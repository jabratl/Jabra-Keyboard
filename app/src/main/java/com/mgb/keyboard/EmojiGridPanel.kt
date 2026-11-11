package com.mgb.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.mgb.keyboard.emoji.EmojiDb
import com.mgb.keyboard.emoji.RecentEmoji

/**
 * v0.8.1 (E2): the new emoji panel = tabs (Recent + the groups of the data file) + ONE EmojiGridView + the same bottom row as the old panel
 * (ABC / space / backspace repeat / enter). Same route "emoji", same pref emoji_recent. Built only when EmojiStore has the data.
 */
class EmojiGridPanel(private val ctx: Context, private val host: JabraHost, private val db: EmojiDb) {

    private val prefs = JabraPrefs(ctx)
    private var cat = 0   // 0 = recent, 1.. = tab (cat - 1) in gs
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

    private fun make(): View {
        val gl = ArrayList<Int>()
        for (g in 0 until db.groupCount()) if (EmojiStore.groupItems(g).isNotEmpty()) gl.add(g)
        gs = gl
        val groups = gl.size
        if (cat < 0 || cat > groups) cat = if (groups > 0) 1 else 0
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        try { JabraThemes.paint(prefs, root) } catch (_: Throwable) { }
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        val grid = EmojiGridView(ctx)
        try {
            val ink = JabraThemes.iconColor(prefs)
            grid.setColors((ink and 0x00FFFFFF) or 0x33000000, JabraThemes.noteColor(prefs))
        } catch (_: Throwable) { grid.setColors(0x33000000, Color.parseColor("#555555")) }
        grid.onPick = { e, v ->
            try { host.feedback(v) } catch (_: Throwable) { }
            host.commitWord()
            host.typeText(e)
            try { prefs.setStr("emoji_recent", RecentEmoji.add(prefs.str("emoji_recent", ""), e)) } catch (_: Throwable) { }
        }

        // tabs: recent + one per group (the icon = the first emoji of the group)
        val tabs = LinearLayout(ctx)
        tabs.orientation = LinearLayout.HORIZONTAL
        fun fillTabs() {
            tabs.removeAllViews()
            for (i in 0..groups) {
                val label = if (i == 0) "\uD83D\uDD58" else EmojiStore.groupItems(gl[i - 1])[0]
                val t = btn(label, dp(44), 0f, dp(36)) {
                    cat = i
                    fillTabs()
                    grid.setItems(itemsFor(cat), if (cat == 0) "No recent emoji yet. Pick one from the other tabs." else "")
                }
                t.textSize = 16f
                if (i == cat) t.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#9CC3E6"))
                tabs.addView(t)
            }
        }
        fillTabs()
        val hs = HorizontalScrollView(ctx)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(tabs)
        root.addView(hs)

        grid.setItems(itemsFor(cat), if (cat == 0) "No recent emoji yet. Pick one from the other tabs." else "")
        grid.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(host.keyHeightDp()) * 4)
        root.addView(grid)

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
