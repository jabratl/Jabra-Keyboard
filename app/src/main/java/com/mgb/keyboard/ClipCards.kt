package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.clip.ClipList

/*
 * v0.2.5.2: clipboard CARDS view (2 columns). New file; ClipPanel (ClipboardFeature.kt) has one marked branch.
 * Same data as the List view (ClipStore). Nothing is saved here. No new permission, no network.
 * build() returns null on ANY problem -> ClipPanel shows the old List view.
 */
/** v0.2.5.3: what the cards need to know about select mode. Default = off (nothing changes). */
class SelectMode(
    val on: Boolean = false,
    val isSel: (ClipList.Item) -> Boolean = { false },
    val onToggle: (ClipList.Item) -> Unit = { },
    val onLong: (ClipList.Item) -> Unit = { },
    val onPaste: (ClipList.Item) -> Unit = { },   // v0.4.3: paste counter
    val info: (ClipList.Item) -> String = { "" }    // v0.4.3: small grey line under the text ("123 chars"), "" = none
)

object ClipCards {

    /**
     * Builds the cards block (Pinned section, then Recent section).
     * [lines] = 0/1/2 (clip_lines pref) -> 2/4/6 text lines.
     * [onPin] / [onDel] are called after a tap on the pin / x of a card (the panel then refreshes).
     */
    fun build(
        ctx: Context, host: JabraHost,
        pinned: List<ClipList.Item>, recent: List<ClipList.Item>,
        showHeaders: Boolean, lines: Int,
        onPin: (ClipList.Item) -> Unit, onDel: (ClipList.Item) -> Unit,
        sm: SelectMode = SelectMode()   // v0.2.5.3: select mode (default = off = part 2 behaviour)
    ): View? = try {
        val lv = lines.coerceIn(0, 2)
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        if (pinned.isNotEmpty()) {
            if (showHeaders) col.addView(header(ctx, "Pinned"))
            addGrid(ctx, host, col, pinned, lv, onPin, onDel, sm)
        }
        if (recent.isNotEmpty()) {
            if (showHeaders) col.addView(header(ctx, "Recent"))
            addGrid(ctx, host, col, recent, lv, onPin, onDel, sm)
        }
        col
    } catch (_: Throwable) { null }

    private fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun header(ctx: Context, label: String): View {
        val h = TextView(ctx)
        h.text = label
        h.textSize = 12f
        h.setTypeface(h.typeface, Typeface.BOLD)
        h.setTextColor(try { JabraTokens.dimText(JabraPrefs(ctx)) } catch (_: Throwable) { Color.DKGRAY })   // v0.8.7
        h.setPadding(dp(ctx, 8), dp(ctx, 4), dp(ctx, 8), dp(ctx, 2))
        return h
    }

    private fun addGrid(
        ctx: Context, host: JabraHost, col: LinearLayout, items: List<ClipList.Item>, lv: Int,
        onPin: (ClipList.Item) -> Unit, onDel: (ClipList.Item) -> Unit, sm: SelectMode
    ) {
        var i = 0
        while (i < items.size) {
            val r = LinearLayout(ctx)
            r.orientation = LinearLayout.HORIZONTAL
            r.addView(card(ctx, host, items[i], lv, onPin, onDel, sm))
            if (i + 1 < items.size) r.addView(card(ctx, host, items[i + 1], lv, onPin, onDel, sm))
            else r.addView(spacer(ctx))   // odd count: empty slot keeps the card width
            col.addView(r)
            i += 2
        }
    }

    private fun spacer(ctx: Context): View {
        val s = View(ctx)
        val lp = LinearLayout.LayoutParams(0, 1, 1f)
        lp.setMargins(dp(ctx, 3), dp(ctx, 3), dp(ctx, 3), dp(ctx, 3))
        s.layoutParams = lp
        return s
    }

    private fun card(
        ctx: Context, host: JabraHost, item: ClipList.Item, lv: Int,
        onPin: (ClipList.Item) -> Unit, onDel: (ClipList.Item) -> Unit, sm: SelectMode
    ): View {
        val picked = sm.on && sm.isSel(item)
        val iconsOn = try { JabraPrefs(ctx).bool("icons_on", true) } catch (_: Throwable) { true }
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        val pf = try { JabraPrefs(ctx) } catch (_: Throwable) { null }   // v0.8.7: theme colours (Classic = same as before)
        box.background = try { UiKit.cardBackground(ctx, pf!!, picked, item.pinned) } catch (_: Throwable) { oldCardBg(ctx, picked, item.pinned) }
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)   // equal width, same height as the other card in the row
        lp.setMargins(dp(ctx, 3), dp(ctx, 3), dp(ctx, 3), dp(ctx, 3))
        box.layoutParams = lp

        // body = the clip text; tap = paste + back to letters (same 3 lines as the List view)
        val t = TextView(ctx)
        t.text = item.text.replace("\n", " ⏎ ")
        t.textSize = 12.5f
        t.setTextColor(try { JabraTokens.cardText(pf!!) } catch (_: Throwable) { Color.BLACK })
        t.maxLines = 2 + 2 * lv
        t.ellipsize = TextUtils.TruncateAt.END
        t.minLines = 2
        t.setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 2))
        t.isClickable = true
        t.setOnClickListener {
            try { host.feedback(t) } catch (_: Throwable) { }
            if (sm.on) {   // v0.2.5.3: select mode = tap toggles, NO paste
                try { sm.onToggle(item) } catch (_: Throwable) { }
            } else try {
                host.commitWord()
                host.typeText(item.text)
                try { sm.onPaste(item) } catch (_: Throwable) { }   // v0.4.3
                host.showLetters()
            } catch (_: Throwable) { }
        }
        t.setOnLongClickListener {   // v0.2.5.3: long-press = select mode with this clip selected
            if (sm.on) false else { try { sm.onLong(item) } catch (_: Throwable) { }; true }
        }
        t.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        box.addView(t)
        try {   // v0.4.3: char count line
            val inf = sm.info(item)
            if (inf.isNotEmpty()) {
                val ct = TextView(ctx)
                ct.text = inf
                ct.textSize = 10f
                ct.setTextColor(try { JabraTokens.cardNote(pf!!) } catch (_: Throwable) { Color.GRAY })
                ct.setPadding(dp(ctx, 8), 0, dp(ctx, 8), 0)
                box.addView(ct)
            }
        } catch (_: Throwable) { }

        // bottom line: pin (left) and x (right)
        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        if (sm.on) {   // v0.2.5.3: check box + visible "Pinned" tag, no pin / x while selecting
            val ck = TextView(ctx)
            ck.text = if (picked) "☑" else "☐"
            ck.textSize = 18f
            ck.setTextColor(try { if (picked) JabraTokens.cardStroke(pf!!, true, false) else JabraTokens.onCardDim(pf!!) } catch (_: Throwable) { if (picked) Color.parseColor("#2F6FD0") else Color.DKGRAY })
            ck.setPadding(dp(ctx, 8), 0, 0, 0)
            ck.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(ctx, 30))
            ck.gravity = Gravity.CENTER_VERTICAL
            bar.addView(ck)
            val fill2 = View(ctx)
            fill2.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            bar.addView(fill2)
            if (item.pinned) {
                val tag = TextView(ctx)
                tag.text = "★ Pinned"
                tag.textSize = 11f
                tag.setTextColor(try { JabraTokens.onCardDim(pf!!) } catch (_: Throwable) { Color.DKGRAY })
                tag.setPadding(0, 0, dp(ctx, 8), 0)
                bar.addView(tag)
            }
        } else {
            bar.addView(small(ctx, host, if (item.pinned) "★" else "☆", if (item.pinned) "pinned" else "pin", iconsOn) { onPin(item) })
            val fill = View(ctx)
            fill.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            bar.addView(fill)
            bar.addView(small(ctx, host, "✕", "close", iconsOn) { onDel(item) })
        }
        box.addView(bar)
        return box
    }

    /** v0.8.7: the v0.8.6 card look, kept as the fallback of UiKit.cardBackground. */
    private fun oldCardBg(ctx: Context, picked: Boolean, pinned: Boolean): GradientDrawable {
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor(if (picked) "#DCE9FA" else "#F7F7F7"))
        bg.cornerRadius = dp(ctx, 10).toFloat()
        bg.setStroke(dp(ctx, if (picked) 2 else 1), if (picked) Color.parseColor("#2F6FD0") else if (pinned) Color.parseColor("#7A9CC6") else Color.parseColor("#C8C8C8"))
        return bg
    }

    private fun small(ctx: Context, host: JabraHost, label: String, icon: String, iconsOn: Boolean, onClick: () -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        b.setBackgroundColor(Color.TRANSPARENT)
        b.setOnClickListener {
            try { host.feedback(b) } catch (_: Throwable) { }
            try { onClick() } catch (_: Throwable) { }
        }
        b.layoutParams = LinearLayout.LayoutParams(dp(ctx, 40), dp(ctx, 30))
        JabraIcons.putThemed(ctx, b, icon)   // v0.8.7: icon colour follows the theme
        return b
    }
}
