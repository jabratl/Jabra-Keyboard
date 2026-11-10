package com.mgb.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.emoji.EmojiData
import com.mgb.keyboard.emoji.RecentEmoji

/*
 * v0.1.0: local themes, one-handed mode, emoji panel.
 * All new code lives here (+ emoji/EmojiData.java, emoji/RecentEmoji.java, pure Java, tested).
 * MgbIme.kt / JabraExtras.kt / TypingExtras.kt only have tiny hooks marked "v0.1.0"; every hook is try/catch
 * (on a problem the old look / old behaviour stays).
 * Prefs file "jabra", NEW keys only: theme(int,0) onehand(int 0 off / 1 left / 2 right, default 0) emoji_recent(string)
 * Theme 0 = Classic = EXACTLY the old look (nothing is changed on the keys).
 */

/** Local colour themes (no network, no store). */
object JabraThemes {
    class T(val name: String, val bg: Int, val key: Int?, val text: Int?)

    val ALL: Array<T> = arrayOf(
        T("Classic", 0xFFDDDDDD.toInt(), null, null),
        T("Blue", 0xFFD6E6F5.toInt(), 0xFFFFFFFF.toInt(), 0xFF1B3A57.toInt()),
        T("Mint", 0xFFD8EFE3.toInt(), 0xFFFFFFFF.toInt(), 0xFF1E4D3A.toInt()),
        T("Sand", 0xFFEFE6D6.toInt(), 0xFFFFFAF0.toInt(), 0xFF4A3B22.toInt()),
        T("Dark", 0xFF1E1F22.toInt(), 0xFF3A3C42.toInt(), 0xFFF2F2F2.toInt()),
        T("Sandstone", 0xFFE8DFCB.toInt(), 0xFFF7F1E3.toInt(), 0xFF2B2620.toInt()),   // v0.2.4: indexes 5..17 appended, NEVER reorder
        T("Seaweed Grey", 0xFF5F7369.toInt(), 0xFF2F3E38.toInt(), 0xFFEAF2EE.toInt()),
        T("Ivory", 0xFFF3F0E8.toInt(), 0xFFFFFFFF.toInt(), 0xFF2A2A2A.toInt()),
        T("Gloss Black", 0xFF0B0B0D.toInt(), 0xFF222226.toInt(), 0xFFFFFFFF.toInt()),
        T("Charcoal", 0xFF1A1A1A.toInt(), 0xFF2E2E2E.toInt(), 0xFFE6E6E6.toInt()),
        T("Midnight Navy", 0xFF0F1626.toInt(), 0xFF1E2A44.toInt(), 0xFFE8EEFF.toInt()),
        T("Moon Blue", 0xFFC9D6EE.toInt(), 0xFFF4F7FF.toInt(), 0xFF1F2F55.toInt()),
        T("Ocean", 0xFF1C6BC0.toInt(), 0xFF3F89D6.toInt(), 0xFFFFFFFF.toInt()),
        T("Deep Green", 0xFF0E2A24.toInt(), 0xFF1B4036.toInt(), 0xFFDDF3EA.toInt()),
        T("Aqua", 0xFFBFEEE3.toInt(), 0xFFEAFBF7.toInt(), 0xFF0F4A40.toInt()),
        T("Rose", 0xFFF6D3DE.toInt(), 0xFFFFF1F5.toInt(), 0xFF5A1F35.toInt()),
        T("Silver Grey", 0xFFB9BDC4.toInt(), 0xFFE6E8EC.toInt(), 0xFF23262B.toInt()),
        T("Dusk", 0xFF2A2340.toInt(), 0xFF3C3460.toInt(), 0xFFEDE8FF.toInt())
    )

    fun index(p: JabraPrefs): Int = p.int("theme", 0).coerceIn(0, ALL.size - 1)
    fun cur(p: JabraPrefs): T = ALL[index(p)]
    fun bg(p: JabraPrefs): Int = cur(p).bg
    fun keyTint(p: JabraPrefs): ColorStateList? = cur(p).key?.let { ColorStateList.valueOf(it) }

    /** Line-icon colour: Classic = #222222 (as before), other themes = the theme text colour (Dark = light icons). */
    fun iconColor(p: JabraPrefs): Int = cur(p).text ?: Color.parseColor("#222222")

    /** Colour for small grey help texts: Classic/light themes = #555555 (as before), Dark = light grey. */
    fun noteColor(p: JabraPrefs): Int = if (Color.luminance(cur(p).bg) < 0.4f) Color.parseColor("#B8B8B8") else Color.parseColor("#555555")

    /** Colours one key. Classic = does nothing. */
    fun style(p: JabraPrefs, b: Button) {
        val t = cur(p)
        val k = t.key
        if (k != null) b.backgroundTintList = ColorStateList.valueOf(k)
        val x = t.text
        if (x != null) b.setTextColor(x)
        try { JabraLook.shape(p, b, JabraLook.NORMAL) } catch (_: Throwable) { }   // v0.1.5: Jabra look (look = 0 Classic does nothing)
    }
}

/** One-handed mode: the keyboard is narrower and sits on the left or right side. */
object JabraOneHand {
    fun mode(p: JabraPrefs): Int = p.int("onehand", 0).coerceIn(0, 2)

    /** Call on a view BEFORE it is added to the container (a FrameLayout). Mode 0 = does nothing. */
    fun apply(ctx: Context, p: JabraPrefs, v: View) {
        val m = mode(p)
        if (m == 0) return
        val frac = try { com.mgb.keyboard.typing.SizePct.widthFrac(p.int(SizeKeys.widthKey(JabraPrefs.isLand(ctx)), -1), JabraPrefs.isLand(ctx)) } catch (_: Throwable) { if (JabraPrefs.isLand(ctx)) 0.6f else 0.82f }   // v0.1.4: width % (unset = 0.82 / 0.6 as before)
        val w = (ctx.resources.displayMetrics.widthPixels * frac).toInt()
        v.layoutParams = FrameLayout.LayoutParams(w, FrameLayout.LayoutParams.WRAP_CONTENT, if (m == 1) Gravity.START else Gravity.END)
    }
}

/** Emoji panel: category tabs + scrollable grid + recent list. Opens with long-press on the "," key or "More settings" -> Emoji. */
class EmojiPanel(private val ctx: Context, private val host: JabraHost) {

    private val prefs = JabraPrefs(ctx)
    private var cat = 0   // 0 = recent, 1.. = EmojiData category (cat - 1)

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

    /** Opens on the recent tab (or the first category if there are no recent emoji yet). */
    fun reset() {
        cat = if (RecentEmoji.parse(prefs.str("emoji_recent", "")).isEmpty()) 1 else 0
    }

    fun build(): View? = try { make() } catch (_: Throwable) { null }

    private fun make(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        try { root.setBackgroundColor(JabraThemes.bg(prefs)) } catch (_: Throwable) { }
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        // tabs: recent + one per category
        val tabs = LinearLayout(ctx)
        tabs.orientation = LinearLayout.HORIZONTAL
        val icons = arrayOf("\uD83D\uDD58") + EmojiData.ICONS
        for (i in icons.indices) {
            val t = btn(icons[i], dp(44), 0f, dp(36)) { cat = i; host.showPanel("emoji") }
            t.textSize = 16f
            if (i == cat) t.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#9CC3E6"))
            tabs.addView(t)
        }
        val hs = HorizontalScrollView(ctx)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(tabs)
        root.addView(hs)

        // grid
        val items: List<String> = if (cat == 0) RecentEmoji.parse(prefs.str("emoji_recent", "")) else EmojiData.list(cat - 1).toList()
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        if (items.isEmpty()) {
            val n = TextView(ctx)
            n.text = "No recent emoji yet. Pick one from the other tabs."
            n.textSize = 12f
            n.setTextColor(try { JabraThemes.noteColor(prefs) } catch (_: Throwable) { Color.parseColor("#555555") })
            n.setPadding(dp(8), dp(12), dp(8), dp(12))
            col.addView(n)
        }
        val perRow = 8
        var i = 0
        while (i < items.size) {
            val r = LinearLayout(ctx)
            r.orientation = LinearLayout.HORIZONTAL
            for (j in 0 until perRow) {
                if (i + j < items.size) {
                    val e = items[i + j]
                    val b = btn(e, 0, 1f, dp(44)) {
                        host.commitWord()
                        host.typeText(e)
                        try { prefs.setStr("emoji_recent", RecentEmoji.add(prefs.str("emoji_recent", ""), e)) } catch (_: Throwable) { }
                    }
                    b.textSize = 22f
                    r.addView(b)
                } else {
                    val f = View(ctx)   // empty filler keeps the widths equal in the last row
                    f.layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
                    r.addView(f)
                }
            }
            col.addView(r)
            i += perRow
        }
        val sv = ScrollView(ctx)
        sv.addView(col)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(host.keyHeightDp()) * 4)
        root.addView(sv)

        // bottom row
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
