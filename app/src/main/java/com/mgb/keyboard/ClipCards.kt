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
    val info: (ClipList.Item) -> String = { "" },   // v0.4.3: small grey line under the text ("123 chars"), "" = none
    val holdMs: Int = 0,   // v0.16.16 (E2): hold time (ms) to start select mode; 0 = old system long-click
    val afterPaste: (() -> Unit)? = null,   // v0.17.37 (M4): what to do after a card paste (null = old: back to letters)
    val hasFmt: (ClipList.Item) -> Boolean = { false },   // v0.17.40 (M7): has a stored format = shows the Aa tag
    val paste: ((ClipList.Item, Boolean) -> Unit)? = null   // v0.17.40 (M7): paste(item, badgeTapped); null = old way (plain, below)
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
        sm: SelectMode = SelectMode(),   // v0.2.5.3: select mode (default = off = part 2 behaviour)
        recentFirst: Boolean = false,   // v0.15.2 (N2): true = Recent section above Pinned (pref clip_order); default = old order
        onHeader: (() -> Unit)? = null   // v0.15.2: tap on a section header swaps the order (null = headers are plain text as before)
    ): View? = try {
        val lv = lines.coerceIn(0, 2)
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val secs = if (recentFirst) listOf(Pair("Recent", recent), Pair("Pinned", pinned)) else listOf(Pair("Pinned", pinned), Pair("Recent", recent))   // v0.15.2
        var firstHdr = true
        for (sec in secs) {
            if (sec.second.isEmpty()) continue
            if (showHeaders) { col.addView(header(ctx, sec.first, onHeader, firstHdr)); firstHdr = false }
            addGrid(ctx, host, col, sec.second, lv, onPin, onDel, sm)
        }
        col
    } catch (_: Throwable) { null }

    private fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    /** v0.17.33 (UI9): clip card size % (pref clip_card_pct, 70..140, 100 = old). Any problem = 100. */
    private fun cardPct(ctx: Context): Int = try { com.mgb.keyboard.typing.ClipCardSize.sanitize(JabraPrefs(ctx).int("clip_card_pct", com.mgb.keyboard.typing.ClipCardSize.DEF)) } catch (_: Throwable) { 100 }
    private fun cardMinDp(ctx: Context): Int = try { com.mgb.keyboard.typing.ClipCardSize.minHeightDp(72, cardPct(ctx)) } catch (_: Throwable) { 72 }
    private fun cardSp(ctx: Context, sp: Float): Float = try { com.mgb.keyboard.typing.ClipCardSize.textSize(sp, cardPct(ctx)) } catch (_: Throwable) { sp }

    private fun header(ctx: Context, label: String, onTap: (() -> Unit)? = null, arrow: Boolean = false): View {   // v0.15.2: onTap = swap Pinned / Recent order; arrow only on the first header
        val h = TextView(ctx)
        val base = if (label == "Pinned") "\u2605 " + label else label   // v0.18: gold star on the Pinned header
        h.text = if (arrow) base + "  \u21C5" else base
        if (onTap != null) { h.minimumHeight = dp(ctx, 30); h.gravity = android.view.Gravity.CENTER_VERTICAL; h.setOnClickListener { try { onTap() } catch (_: Throwable) { } } }
        h.textSize = 12f
        h.setTypeface(h.typeface, Typeface.BOLD)
        h.setTextColor(try { if (label == "Pinned") ClipLook.pinText(JabraPrefs(ctx), JabraTokens.isDark(JabraPrefs(ctx))) else JabraTokens.dimText(JabraPrefs(ctx)) } catch (_: Throwable) { Color.DKGRAY })   // v0.8.7; v0.18: Pinned = gold
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
            r.weightSum = 2f   // v0.10.5: a lone card (odd count, e.g. ONE pinned clip) keeps half width WITHOUT the old 1 px spacer view (that row collapsed to no height = pinned card invisible)
            r.addView(card(ctx, host, items[i], lv, onPin, onDel, sm))
            if (i + 1 < items.size) r.addView(card(ctx, host, items[i + 1], lv, onPin, onDel, sm))
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
        if (item.pinned && !picked) { try { box.background = pinnedBg(ctx, pf!!) } catch (_: Throwable) { } }   // v0.9.10.5: pinned card is clearly visible (tint + 2dp border)
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)   // equal width, same height as the other card in the row
        lp.setMargins(dp(ctx, 3), dp(ctx, 3), dp(ctx, 3), dp(ctx, 3))
        box.layoutParams = lp
        box.minimumHeight = dp(ctx, cardMinDp(ctx))   // v0.17.33 (UI9): scaled by clip_card_pct (100 = 72 as before); v0.10.5: a card can never collapse to zero height (normal cards are taller than this, so no visible change)

        // body = the clip text; tap = paste + back to letters (same 3 lines as the List view)
        val t = TextView(ctx)
        t.text = item.text.replace("\n", " ⏎ ")
        t.textSize = cardSp(ctx, 12.5f)   // v0.17.33 (UI9): clip_card_pct (100 = 12.5 as before)
        t.setTextColor(try { JabraTokens.cardText(pf!!) } catch (_: Throwable) { Color.BLACK })
        t.maxLines = 2 + 2 * lv
        t.ellipsize = TextUtils.TruncateAt.END
        t.minLines = 2
        t.setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 2))
        t.isClickable = true
        try {   // v0.18: soft tint while the card is pressed (top corners rounded like the card)
            val pr = GradientDrawable()
            pr.setColor(JabraTokens.withAlpha(try { JabraTokens.cardText(pf!!) } catch (_: Throwable) { Color.BLACK }, 0x1F))
            val rr = dp(ctx, JabraTokens.R_CARD).toFloat()
            pr.cornerRadii = floatArrayOf(rr, rr, rr, rr, 0f, 0f, 0f, 0f)
            val sl = android.graphics.drawable.StateListDrawable()
            sl.addState(intArrayOf(android.R.attr.state_pressed), pr)
            sl.addState(intArrayOf(), android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            t.foreground = sl
        } catch (_: Throwable) { }
        val act: () -> Unit = {   // v0.16.16 (E2): the tap action, shared by the click listener and the hold listener
            try { host.feedback(t) } catch (_: Throwable) { }
            if (sm.on) {   // v0.2.5.3: select mode = tap toggles, NO paste
                try { sm.onToggle(item) } catch (_: Throwable) { }
            } else try {
                val pm = sm.paste   // v0.17.40 (M7): plain or with format, decided in ClipPanel
                if (pm != null) pm(item, false) else {
                host.commitWord()
                host.typeText(item.text)
                try { sm.onPaste(item) } catch (_: Throwable) { }   // v0.4.3
                val ap = sm.afterPaste   // v0.17.37 (M4)
                if (ap != null) ap() else host.showLetters()
                }
            } catch (_: Throwable) { }
        }
        t.setOnClickListener { act() }
        val held = ClipHold.attach(t, sm.holdMs, sm.on, { try { sm.onLong(item) } catch (_: Throwable) { } }, { act() }, { try { host.feedback(t) } catch (_: Throwable) { } })   // v0.16.16 (E2): hold = select mode
        if (!held) t.setOnLongClickListener {   // v0.2.5.3: long-press = select mode with this clip selected (old way, used when clip_hold = System)
            if (sm.on) false else { try { sm.onLong(item) } catch (_: Throwable) { }; true }
        }
        t.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        box.addView(t)
        try {   // v0.17.60 (C5): long clip = small "More" under the text; tap opens the whole card in place (12 lines), "Less" closes it. New look only.
            if (ClipLook.newLook(pf) && !sm.on && item.text.length > 40) {
                val lim = t.maxLines
                val more = TextView(ctx)
                more.text = "More \u25BE"
                more.textSize = 11f
                more.setTypeface(more.typeface, Typeface.BOLD)
                more.setTextColor(try { JabraTokens.cardNote(pf!!) } catch (_: Throwable) { Color.GRAY })
                more.gravity = Gravity.CENTER_VERTICAL
                more.minimumHeight = dp(ctx, 28)
                more.setPadding(dp(ctx, 8), 0, dp(ctx, 8), 0)
                more.visibility = View.GONE
                var open = false
                more.setOnClickListener {
                    try { host.feedback(more) } catch (_: Throwable) { }
                    open = !open
                    t.maxLines = if (open) 12 else lim
                    more.text = if (open) "Less \u25B4" else "More \u25BE"
                }
                box.addView(more)
                t.post {   // show the tag only when the text really is cut (ellipsis on the last line)
                    try {
                        val l = t.layout
                        if (l != null && l.lineCount > 0 && l.getEllipsisCount(l.lineCount - 1) > 0) more.visibility = View.VISIBLE
                    } catch (_: Throwable) { }
                }
            }
        } catch (_: Throwable) { }
        try {   // v0.4.3: char count line (v0.10.24 C1: + kind tag on the left + age in the text)
            val inf = sm.info(item)
            val tg = ClipLook.tag(ctx, pf, item.text)
            val pmode = sm.paste   // v0.17.40 (M7)
            val ft = if (pmode != null && sm.hasFmt(item)) ClipLook.fmtTag(ctx, pf) else null
            if (ft != null) ft.setOnClickListener { if (!sm.on) { try { host.feedback(ft) } catch (_: Throwable) { }; try { pmode!!(item, true) } catch (_: Throwable) { } } }
            if (inf.isNotEmpty() || tg != null || ft != null) {
                val line = LinearLayout(ctx)
                line.orientation = LinearLayout.HORIZONTAL
                line.gravity = Gravity.CENTER_VERTICAL
                if (tg != null) {
                    val tlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    tlp.setMargins(dp(ctx, 8), 0, 0, 0)
                    tg.layoutParams = tlp
                    line.addView(tg)
                }
                if (ft != null) {   // v0.17.40 (M7): Aa = paste with the OTHER choice
                    val flp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    flp.setMargins(dp(ctx, 8), 0, 0, 0)
                    ft.layoutParams = flp
                    line.addView(ft)
                }
                if (inf.isNotEmpty()) {
                    val ct = TextView(ctx)
                    ct.text = inf
                    ct.textSize = 10f
                    ct.setTextColor(try { JabraTokens.cardNote(pf!!) } catch (_: Throwable) { Color.GRAY })
                    ct.setPadding(dp(ctx, 8), 0, dp(ctx, 8), 0)
                    line.addView(ct)
                }
                box.addView(line)
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
            Glyphs.dress(ck)   // v0.17.68 (S1): own drawn check box instead of the text sign
            ck.setPadding(dp(ctx, 8), 0, 0, 0)
            ck.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(ctx, 30))
            ck.gravity = Gravity.CENTER_VERTICAL
            bar.addView(ck)
            val tickAct: () -> Unit = { try { host.feedback(bar) } catch (_: Throwable) { }; try { sm.onToggle(item) } catch (_: Throwable) { } }   // v0.16.16 (E2): the tick box (and the whole bottom bar) toggles; before it was a plain TextView with no click
            ck.setOnClickListener { tickAct() }
            bar.setOnClickListener { tickAct() }
            val fill2 = View(ctx)
            fill2.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            bar.addView(fill2)
            if (item.pinned) {
                val tag = TextView(ctx)
                tag.text = "★ Pinned"
                tag.textSize = 11f
                tag.setTextColor(try { JabraTokens.onCardDim(pf!!) } catch (_: Throwable) { Color.DKGRAY })
                Glyphs.dress(tag)   // v0.17.68 (S1): own drawn pin instead of the star
                tag.setPadding(0, 0, dp(ctx, 8), 0)
                bar.addView(tag)
            }
        } else {
            bar.addView(small(ctx, host, if (item.pinned) "★" else "☆", if (item.pinned) "pinned" else "pin", iconsOn) { onPin(item) })
            if (item.pinned) {   // v0.9.10.5: visible "Pinned" tag on every pinned card (before: only in select mode)
                val pt = TextView(ctx)
                pt.text = "Pinned"
                pt.textSize = 11f
                pt.setTypeface(pt.typeface, Typeface.BOLD)
                pt.setTextColor(try { ClipLook.pinText(pf, JabraTokens.isDark(pf!!)) } catch (_: Throwable) { Color.parseColor("#B7791F") })   // v0.16.15 (E3)
                bar.addView(pt)
            }
            val fill = View(ctx)
            fill.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            bar.addView(fill)
            bar.addView(small(ctx, host, "✕", "close", iconsOn) { onDel(item) })
        }
        box.addView(bar)
        return box
    }

    /** v0.9.10.5: look of a PINNED card: normal card fill + light blue tint, 2dp accent border (before: 1dp grey-blue border only). */
    private fun pinnedBg(ctx: Context, p: JabraPrefs): GradientDrawable {
        val g = GradientDrawable()
        val base = JabraTokens.cardFill(p, false)
        val gold = ClipLook.pinColor(try { JabraTokens.isDark(p) } catch (_: Throwable) { false })   // v0.16.15 (E3): gold, not the blue of "selected"
        g.setColor(androidx.core.graphics.ColorUtils.compositeColors(JabraTokens.withAlpha(gold, 0x22), base))
        g.cornerRadius = JabraTokens.dp(ctx, JabraTokens.R_CARD).toFloat()
        g.setStroke(dp(ctx, 2), gold)
        return g
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
