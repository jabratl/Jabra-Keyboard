package com.mgb.keyboard

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.ClipPop
import com.mgb.keyboard.typing.ReplaceEngine
import com.mgb.keyboard.typing.ReplaceMore
import com.mgb.keyboard.typing.ReplaceOpts
import com.mgb.keyboard.typing.ReplaceSlimPlan

/**
 * v0.42.1 (M1 of PLAN_RP_MINIMAL): the slim "Options" button + its floating menu for the Find & Replace panel (look of docs/Replace_Tool_Minimal_Mock.html).
 * Used while pref rp_slim is ON (v0.42.4: default ON; an explicit saved false = old view). Rows come from the pure model typing/ReplaceOpts.java; the window is ClipPopover (same as the clipboard menu).
 * Tick rows keep the menu open: the state changes, the panel refreshes (new Options button), and the menu is shown again on the new button (no fade = no blink).
 * Any problem returns null / calls onFail: the panel then uses the OLD option chips. Delete this file + the lines marked "v0.42.1" = back to v0.42.0.
 * NOT tested on a phone (Kotlin not compiled here).
 */
object ReplaceSlim {
    private var reopenAt = 0L   // uptime of the last tick; 0 = nothing pending
    private var reopenKind = 0  // v0.42.3 (M3): which menu to show again: 1 = Options, 2 = More (so the two never re-open each other)

    fun on(p: JabraPrefs): Boolean = try { p.bool("rp_slim", true) } catch (_: Throwable) { false }   // v0.42.4 (M4): default ON (an explicit saved false stays OFF)

    /** True once after a tick: the freshly built panel shows the menu again. */
    private fun takeReopen(kind: Int): Boolean {   // v0.42.3: only the button of the SAME kind takes the flag
        if (reopenKind != kind) return false
        val r = reopenAt > 0L && SystemClock.uptimeMillis() - reopenAt < 1500L
        reopenAt = 0L; reopenKind = 0
        return r   // a stale flag (panel did not rebuild) never re-opens later
    }

    /** The panel is built without a pending re-show: close a menu that may still be open from the previous view. */
    fun closeIfIdle() { if (reopenAt <= 0L) { try { ClipPopover.dismiss() } catch (_: Throwable) { } } }

    /**
     * The Options button. onChange = the panel's optChanged() (save + refresh); onNever = open the never-change page; onFail = the menu could not open (use the old chips).
     * Returns null on any problem (the caller then builds the old chip).
     */
    fun optionsButton(
        ctx: android.content.Context, p: JabraPrefs, host: JabraHost, opt: ReplaceEngine.Opt, protectCount: Int,
        onChange: () -> Unit, onNever: () -> Unit, onFail: () -> Unit, onHelp: () -> Unit = { }
    ): Button? {
        return try {
            val b = UiKit.chip(ctx, p, "Options", false, 34, { v -> host.feedback(v) }) { btn ->
                try {
                    if (ClipPopover.isOpen()) { ClipPopover.dismiss(); return@chip }
                    if (!ClipPop.reopenGuard(SystemClock.uptimeMillis(), ClipPopover.lastDismiss())) return@chip   // the tap that just closed it must not re-open it
                    if (!show(btn, p, host, opt, protectCount, true, onChange, onNever, onHelp)) onFail()
                } catch (_: Throwable) { onFail() }
            }
            if (takeReopen(1)) b.post { try { show(b, p, host, opt, protectCount, false, onChange, onNever, onHelp) } catch (_: Throwable) { } }
            val n = ReplaceOpts.activeCount(opt, protectCount)
            if (n > 0) { dotOn(b, n); b.contentDescription = "Options, " + n + " on" }
            b
        } catch (_: Throwable) { null }
    }

    private fun show(a: View, p: JabraPrefs, host: JabraHost, opt: ReplaceEngine.Opt, protectCount: Int, animate: Boolean, onChange: () -> Unit, onNever: () -> Unit, onHelp: () -> Unit): Boolean {
        return ClipPopover.show(a, p, host, ReplaceOpts.items(opt, protectCount), false, animate, ReplaceOpts.WIDTH_DP) { picked ->
            if (picked.id == ReplaceOpts.NEVER) onNever()
            else if (picked.id == ReplaceOpts.HELP) onHelp()
            else if (ReplaceOpts.toggle(opt, picked.id)) { reopenAt = SystemClock.uptimeMillis(); reopenKind = 1; onChange() }
        }
    }

    // ------------------------------------------------------------------ v0.42.2 (M2): slim main view pieces (all return Views; the CALLER wraps them in try/catch, any exception = the old view)

    private fun dp(ctx: android.content.Context, v: Int): Int = JabraTokens.dp(ctx, v)

    private fun lp(w: Int, h: Int, weight: Float = 0f, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): LinearLayout.LayoutParams {
        val x = LinearLayout.LayoutParams(w, h, weight)
        x.setMargins(l, t, r, b)
        return x
    }

    /** Segmented control [ Text | Word | Regex ]: 34 dp, 1 dp border, radius 9, chosen = soft tint + bold. onPick(i) = the old chip action. */
    fun segmented(ctx: android.content.Context, p: JabraPrefs, host: JabraHost, mode: Int, onPick: (Int) -> Unit): View {
        val seg = LinearLayout(ctx)
        seg.orientation = LinearLayout.HORIZONTAL
        seg.gravity = Gravity.CENTER_VERTICAL
        seg.setPadding(dp(ctx, 2), dp(ctx, 2), dp(ctx, 2), dp(ctx, 2))
        val bg = GradientDrawable()
        bg.cornerRadius = dp(ctx, JabraTokens.R_SM).toFloat()
        bg.setColor(Color.TRANSPARENT)
        bg.setStroke(dp(ctx, 1), JabraTokens.cardStroke(p, false, false))
        seg.background = bg
        val dimInk = JabraTokens.withAlpha(JabraTokens.panelText(p), 0xCC)
        for (i in 0..2) {
            val t = TextView(ctx)
            t.text = ReplaceEngine.MODE_NAMES[i]
            t.textSize = JabraTokens.T_BTN
            t.gravity = Gravity.CENTER
            t.maxLines = 1
            val on = mode == i
            if (on) {
                val g = GradientDrawable()
                g.cornerRadius = dp(ctx, JabraTokens.R_SM - 2).toFloat()
                g.setColor(JabraTokens.ACCENT_SOFT)
                t.background = g
                t.setTextColor(JabraTokens.ON_ACCENT_SOFT)
                t.setTypeface(t.typeface, Typeface.BOLD)
            } else t.setTextColor(dimInk)
            t.contentDescription = ReplaceEngine.MODE_NAMES[i] + (if (on) ", selected" else "")
            t.isClickable = true
            t.setOnClickListener { v -> try { host.feedback(v) } catch (_: Throwable) { }; try { onPick(i) } catch (_: Throwable) { } }
            seg.addView(t, lp(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        return seg
    }

    /** Quiet text button (no border, no fill): Sel / Clip / swap / Rest. ink = text colour (card note or panel dim). */
    fun textButton(ctx: android.content.Context, host: JabraHost, label: String, hDp: Int, ink: Int, onClick: () -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0; b.minimumWidth = 0; b.minHeight = 0; b.minimumHeight = 0
        b.setPadding(dp(ctx, 8), 0, dp(ctx, 8), 0)
        b.textSize = JabraTokens.T_BTN
        b.background = null
        b.stateListAnimator = null
        b.setTextColor(ink)
        b.setOnClickListener { v -> try { host.feedback(v) } catch (_: Throwable) { }; try { onClick() } catch (_: Throwable) { } }
        b.layoutParams = lp(LinearLayout.LayoutParams.WRAP_CONTENT, dp(ctx, hDp))
        return b
    }

    /** One 38 dp line of the card: dim label (34 dp wide), value (tap = type; empty = dim placeholder), quiet buttons on the right. */
    fun slimLine(
        ctx: android.content.Context, p: JabraPrefs, host: JabraHost, label: String, value: String, placeholder: String, shown: String,
        onTap: () -> Unit, actions: List<Pair<String, () -> Unit>>
    ): View {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        val noteInk = JabraTokens.cardNote(p)
        val lb = TextView(ctx)
        lb.text = label
        lb.textSize = JabraTokens.T_NOTE + 1f
        lb.setTextColor(noteInk)
        r.addView(lb, lp(dp(ctx, 34), LinearLayout.LayoutParams.WRAP_CONTENT))
        val vv = TextView(ctx)
        vv.textSize = JabraTokens.T_BODY
        vv.maxLines = 1
        vv.ellipsize = TextUtils.TruncateAt.END
        vv.gravity = Gravity.CENTER_VERTICAL
        if (value.isEmpty()) { vv.text = placeholder; vv.setTextColor(noteInk) } else { vv.text = shown; vv.setTextColor(JabraTokens.cardText(p)) }
        vv.isClickable = true
        vv.setOnClickListener { v -> try { host.feedback(v) } catch (_: Throwable) { }; try { onTap() } catch (_: Throwable) { } }
        r.addView(vv, lp(0, dp(ctx, 38), 1f))
        for (a in actions) {
            val b = textButton(ctx, host, a.first, 28, noteInk) { a.second() }
            if (a.first == "\u21C4") b.contentDescription = "Swap Find and With"
            r.addView(b)
        }
        return r
    }

    /** The card shell (radius 10, 1 dp border, padding 0 / 4 / 0 / 12). */
    fun slimCard(ctx: android.content.Context, p: JabraPrefs): LinearLayout {
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(ctx, 12), 0, dp(ctx, 4), 0)
        try { card.background = UiKit.cardBackground(ctx, p, false, false) } catch (_: Throwable) { }
        card.layoutParams = lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(ctx, 2), dp(ctx, 4), dp(ctx, 2), dp(ctx, 2))
        return card
    }

    fun slimDivider(ctx: android.content.Context, p: JabraPrefs): View {
        val d = View(ctx)
        d.setBackgroundColor(JabraTokens.cardStroke(p, false, false))
        d.layoutParams = lp(LinearLayout.LayoutParams.MATCH_PARENT, 1, 0f, dp(ctx, 2), 0, dp(ctx, 4), 0)
        return d
    }

    private fun dimButton(b: Button, on: Boolean) { b.alpha = if (on) 1f else 0.5f; if (!on) b.isClickable = false }

    /**
     * Slim step row: [<] k / N [>] ..... Rest  [This]  [Replace all]. plan = ReplaceSlimPlan.step(...). styleAll = the panel's accentButton + long-press peek + alpha.
     * Arrows / counter / Rest / This only when plan.nav. Disabled = alpha 0.5 + not clickable.
     */
    fun stepRow(
        ctx: android.content.Context, p: JabraPrefs, host: JabraHost, plan: ReplaceSlimPlan.Step,
        onPrev: () -> Unit, onNext: () -> Unit, onCounter: () -> Unit, onRest: () -> Unit, onThis: () -> Unit, onAll: () -> Unit, styleAll: (Button) -> Unit
    ): View {
        val sr = LinearLayout(ctx)
        sr.orientation = LinearLayout.HORIZONTAL
        sr.gravity = Gravity.CENTER_VERTICAL
        val fb: (View) -> Unit = { v -> try { host.feedback(v) } catch (_: Throwable) { } }
        val dimInk = JabraTokens.dimText(p)
        if (plan.nav) {
            val prev = UiKit.btn(ctx, p, "\u2039", 0f, 34, 18f, fb) { onPrev() }
            prev.minWidth = dp(ctx, 34); prev.contentDescription = "Previous match"
            dimButton(prev, plan.navEnabled)
            sr.addView(prev)
            val c = TextView(ctx)
            c.text = plan.counter
            c.textSize = 12.5f
            c.gravity = Gravity.CENTER
            c.minWidth = dp(ctx, 40)
            c.setTextColor(dimInk)
            c.contentDescription = "Match " + plan.counter
            if (plan.navEnabled) { c.isClickable = true; c.setOnClickListener { v -> fb(v); try { onCounter() } catch (_: Throwable) { } } }
            sr.addView(c, lp(LinearLayout.LayoutParams.WRAP_CONTENT, dp(ctx, 34)))
            val next = UiKit.btn(ctx, p, "\u203A", 0f, 34, 18f, fb) { onNext() }
            next.minWidth = dp(ctx, 34); next.contentDescription = "Next match"
            dimButton(next, plan.navEnabled)
            sr.addView(next)
        }
        sr.addView(View(ctx), lp(0, 1, 1f))
        if (plan.nav) {
            val rest = textButton(ctx, host, "Rest", 34, dimInk) { onRest() }
            dimButton(rest, plan.navEnabled)
            sr.addView(rest)
            val one = UiKit.btn(ctx, p, "This", 0f, 34, JabraTokens.T_BTN, fb) { onThis() }
            one.minWidth = dp(ctx, 44)
            dimButton(one, plan.navEnabled)
            sr.addView(one)
        }
        val all = UiKit.btn(ctx, p, plan.allLabel, 0f, 34, 12.5f, fb) { onAll() }
        all.minWidth = dp(ctx, 44)
        val narrow = try { ctx.resources.displayMetrics.let { it.widthPixels / it.density } < 360f } catch (_: Throwable) { false }   // v0.42.4: 320 dp phones: tighter padding
        all.setPadding(dp(ctx, if (narrow) 8 else 14), 0, dp(ctx, if (narrow) 8 else 14), 0)
        styleAll(all)
        if (!plan.allEnabled) all.alpha = 0.6f
        sr.addView(all)
        return sr
    }

    /** One note line (min 20 dp, 12 sp dim): left = message or scope text (scope text is tappable), right = accent underlined link (Undo / Paste back / Copy). */
    fun noteLine(ctx: android.content.Context, p: JabraPrefs, host: JabraHost, note: ReplaceSlimPlan.Note, onScope: () -> Unit, onRight: () -> Unit): View {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.minimumHeight = dp(ctx, 20)
        val left = TextView(ctx)
        left.text = note.left
        left.textSize = 12f
        left.maxLines = 2
        left.ellipsize = TextUtils.TruncateAt.END
        left.setTextColor(JabraTokens.note(p))
        left.setPadding(dp(ctx, 6), dp(ctx, 2), dp(ctx, 6), dp(ctx, 2))
        if (note.leftIsScope) { left.isClickable = true; left.setOnClickListener { v -> try { host.feedback(v) } catch (_: Throwable) { }; try { onScope() } catch (_: Throwable) { } } }
        r.addView(left, lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (note.rightKind != ReplaceSlimPlan.R_NONE) {
            val link = TextView(ctx)
            link.text = note.rightLabel
            link.textSize = 12f
            link.paintFlags = link.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            link.setTextColor(JabraTokens.ACCENT)
            link.setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
            link.isClickable = true
            link.setOnClickListener { v -> try { host.feedback(v) } catch (_: Throwable) { }; try { onRight() } catch (_: Throwable) { } }
            r.addView(link, lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        return r
    }

    // ------------------------------------------------------------------ v0.42.3 (M3): pair row [Check] [List N] [More] + the More menu

    /** Pair row: 3 equal bordered buttons. onMore(id) gets one of ReplaceMore.PASTE / SAVE / WORD / CLEAR; onFail = the More menu could not open. */
    fun pairRow(
        ctx: android.content.Context, p: JabraPrefs, host: JabraHost, nPairs: Int, nShort: Int, clearAsk: Boolean,
        onCheck: () -> Unit, onList: () -> Unit, onMore: (String) -> Unit, onFail: () -> Unit
    ): View {
        val pr = LinearLayout(ctx)
        pr.orientation = LinearLayout.HORIZONTAL
        pr.gravity = Gravity.CENTER_VERTICAL
        val fb: (View) -> Unit = { v -> try { host.feedback(v) } catch (_: Throwable) { } }
        val m = { w: Float -> lp(0, dp(ctx, 34), w, dp(ctx, 2), dp(ctx, 2), dp(ctx, 2), dp(ctx, 2)) }
        val check = UiKit.chip(ctx, p, "Check", false, 34, fb) { onCheck() }
        pr.addView(check, m(1f))
        val list = UiKit.chip(ctx, p, "List", false, 34, fb) { onList() }
        dotOn(list, Math.max(0, nPairs))
        list.contentDescription = "Pair list, " + Math.max(0, nPairs) + " pairs"
        pr.addView(list, m(1f))
        val more = UiKit.chip(ctx, p, "More", false, 34, fb) { btn ->
            try {
                if (ClipPopover.isOpen()) { ClipPopover.dismiss(); return@chip }
                if (!ClipPop.reopenGuard(SystemClock.uptimeMillis(), ClipPopover.lastDismiss())) return@chip
                if (!showMore(btn, p, host, nPairs, nShort, clearAsk, true, onMore)) onFail()
            } catch (_: Throwable) { onFail() }
        }
        pr.addView(more, m(1f))
        if (takeReopen(2)) more.post { try { showMore(more, p, host, nPairs, nShort, clearAsk, false, onMore) } catch (_: Throwable) { } }
        return pr
    }

    private fun showMore(a: View, p: JabraPrefs, host: JabraHost, nPairs: Int, nShort: Int, clearAsk: Boolean, animate: Boolean, onMore: (String) -> Unit): Boolean {
        return ClipPopover.show(a, p, host, ReplaceMore.items(nPairs, nShort, clearAsk), false, animate, ReplaceMore.WIDTH_DP) { picked ->
            // first Clear tap with 2+ pairs only asks (clearPairs() sets clearAsk): the panel rebuilds and the menu is shown again with "Sure? Clear"
            if (picked.id == ReplaceMore.CLEAR && nPairs >= 2 && !clearAsk) { reopenAt = SystemClock.uptimeMillis(); reopenKind = 2 }
            onMore(picked.id)
        }
    }

    // ------------------------------------------------------------------ v0.42.4 (M4): round count dot inside a button (16 dp high, min 16 wide, radius 8, accent fill, white 10.5 sp bold)
    private class DotDrawable(private val ctx: android.content.Context, private val n: Int) : android.graphics.drawable.Drawable() {
        private val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val ink = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val label = n.toString()
        private val h = JabraTokens.dp(ctx, 16)
        private val w: Int
        init {
            fill.color = JabraTokens.ACCENT
            ink.color = Color.WHITE
            ink.textSize = 10.5f * ctx.resources.displayMetrics.scaledDensity
            ink.typeface = Typeface.DEFAULT_BOLD
            ink.textAlign = android.graphics.Paint.Align.CENTER
            w = Math.max(h, ink.measureText(label).toInt() + JabraTokens.dp(ctx, 8))
        }
        override fun getIntrinsicWidth(): Int = w
        override fun getIntrinsicHeight(): Int = h
        override fun draw(c: android.graphics.Canvas) {
            val b = bounds
            c.drawRoundRect(android.graphics.RectF(b), h / 2f, h / 2f, fill)
            val y = b.exactCenterY() - (ink.descent() + ink.ascent()) / 2f
            c.drawText(label, b.exactCenterX(), y, ink)
        }
        override fun setAlpha(a: Int) { }
        override fun setColorFilter(f: android.graphics.ColorFilter?) { }
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    /** Puts the round dot with the number n on the right inside the button text (n <= 0 = nothing). */
    private fun dotOn(b: Button, n: Int) {
        try {
            if (n <= 0) return
            val d = DotDrawable(b.context, n)
            d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
            b.setCompoundDrawables(null, null, d, null)
            b.compoundDrawablePadding = dp(b.context, 5)
        } catch (_: Throwable) { }
    }
}
