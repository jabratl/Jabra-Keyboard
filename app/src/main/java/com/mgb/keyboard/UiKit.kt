package com.mgb.keyboard

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.LookColors
import com.mgb.keyboard.typing.PanelSpec
import com.mgb.keyboard.typing.SelectPlan

/**
 * v0.6.3 UiKit (S4): ready-made panel parts, so panels do not each keep a private btn() copy.
 * btn() reuses JabraThemes.style() -> JabraLook.shape(), so Classic / Jabra look / Borderless / Custom theme all apply
 * exactly like they do on the keys. Colours come from JabraTokens (Classic = today's values).
 * Every function has try/catch; on any problem it returns a plain Button / TextView (old look, still works).
 * Nothing calls this file yet: S5-S8 move the panels over one by one. No pref, no storage, no permission.
 * Delete this file + JabraTokens.kt = back to v0.6.2 behaviour.
 */
object UiKit {

    /**
     * Panel button. weight > 0 = equal-share width inside a horizontal LinearLayout, weight 0 = wrap content.
     * textSp 0 = keep the system text size (what ClipboardFeature does today); pass 12f / 14f where a panel sets it.
     * feedback = the panel's host.feedback (haptic / sound); null = none.
     * Same 2dp margins and 0 padding as the old private btn() functions.
     */
    fun btn(
        ctx: Context, p: JabraPrefs, label: String, weight: Float, hDp: Int,
        textSp: Float = 0f, feedback: ((View) -> Unit)? = null, onClick: (Button) -> Unit
    ): Button {
        return try {
            val b = base(ctx, label, weight, hDp, feedback, onClick)
            if (textSp > 0f) b.textSize = textSp
            capFont(ctx, b, if (textSp > 0f) textSp else 14f)   // v0.10.21 (D4)
            try { JabraThemes.style(p, b) } catch (_: Throwable) { }
            Glyphs.dress(b)   // v0.17.68 (S1): own drawn signs instead of emoji / text signs
            b
        } catch (_: Throwable) {
            plain(ctx, label, onClick)
        }
    }

    /**
     * Choice chip (folder / kind rows). Not selected = same as btn(). Selected = soft blue fill + dark bold text
     * (readable in every theme, and not colour-only because of the bold text). Wrap-content width, 10dp side padding.
     */
    fun chip(
        ctx: Context, p: JabraPrefs, label: String, selected: Boolean, hDp: Int = 30,
        feedback: ((View) -> Unit)? = null, onClick: (Button) -> Unit
    ): Button {
        return try {
            val b = base(ctx, label, 0f, hDp, feedback, onClick)
            b.textSize = JabraTokens.T_BTN
            capFont(ctx, b, JabraTokens.T_BTN)   // v0.10.21 (D4)
            val side = JabraTokens.dp(ctx, 10)
            if (selected) {
                val r = JabraTokens.dp(ctx, JabraTokens.R_SM).toFloat()
                val up = GradientDrawable()
                up.setColor(JabraTokens.ACCENT_SOFT)
                up.cornerRadius = r
                val down = GradientDrawable()
                down.setColor(LookColors.darken(JabraTokens.ACCENT_SOFT, 0.18f))
                down.cornerRadius = r
                val sl = StateListDrawable()
                sl.addState(intArrayOf(android.R.attr.state_pressed), down)
                sl.addState(intArrayOf(), up)
                b.background = sl
                b.backgroundTintList = null
                b.stateListAnimator = null
                b.setTextColor(JabraTokens.ON_ACCENT_SOFT)
                b.setTypeface(b.typeface, Typeface.BOLD)
            } else {
                try { JabraThemes.style(p, b) } catch (_: Throwable) { }
            }
            b.setPadding(side, 0, side, 0)
            Glyphs.dress(b)   // v0.17.68 (S1)
            b
        } catch (_: Throwable) {
            plain(ctx, label, onClick)
        }
    }

    /** Small help text under a control (11sp, theme note colour, 6dp / 2dp padding = ThemePanel.note() today). */
    fun noteText(ctx: Context, p: JabraPrefs, text: String): TextView {
        val t = TextView(ctx)
        try {
            t.text = text
            t.textSize = JabraTokens.T_NOTE
            capFont(ctx, t, JabraTokens.T_NOTE)   // v0.10.21 (D4)
            t.setTextColor(JabraTokens.note(p))
            t.setPadding(JabraTokens.dp(ctx, 6), JabraTokens.dp(ctx, 2), JabraTokens.dp(ctx, 6), JabraTokens.dp(ctx, 2))
        } catch (_: Throwable) {
            try { t.text = text } catch (_: Throwable) { }
        }
        return t
    }

    /** Panel heading (16sp bold, theme text colour). */
    fun sectionTitle(ctx: Context, p: JabraPrefs, text: String): TextView {
        val t = TextView(ctx)
        try {
            t.text = text
            t.textSize = JabraTokens.T_HEAD
            t.setTypeface(t.typeface, Typeface.BOLD)
            t.setTextColor(JabraTokens.panelText(p))
            t.setPadding(JabraTokens.dp(ctx, JabraTokens.S2), JabraTokens.dp(ctx, JabraTokens.S1), JabraTokens.dp(ctx, JabraTokens.S2), JabraTokens.dp(ctx, JabraTokens.S1))
        } catch (_: Throwable) {
            try { t.text = text } catch (_: Throwable) { }
        }
        return t
    }

    /**
     * Card background: rounded rectangle, fill + border from the theme (Classic = ClipCards colours today).
     * picked = selected in select mode (thicker blue border), pinned = favourite (tinted border).
     */
    fun cardBackground(ctx: Context, p: JabraPrefs, picked: Boolean = false, pinned: Boolean = false): GradientDrawable {
        val g = GradientDrawable()
        try {
            g.setColor(JabraTokens.cardFill(p, picked))
            g.cornerRadius = JabraTokens.dp(ctx, JabraTokens.R_CARD).toFloat()
            g.setStroke(JabraTokens.dp(ctx, if (picked) 2 else 1), JabraTokens.cardStroke(p, picked, pinned))
        } catch (_: Throwable) { }
        return g
    }


    /** v0.10.11 (D2.1): empty horizontal row (what every panel built by hand as row()). */
    fun row(ctx: Context): LinearLayout {
        val r = LinearLayout(ctx)
        try { r.orientation = LinearLayout.HORIZONTAL } catch (_: Throwable) { }
        return r
    }

    /**
     * v0.10.11 (D2.1): shared panel header = [back button] + bold title, 40dp tall (PanelSpec.H_BTN), same look as btn().
     * Ready for the panels but NOT inserted anywhere yet: adding it would make a panel taller, so each panel decides
     * in its own D2.x step (it replaces an existing back row, never adds height). backLabel e.g. "<" or "< More settings".
     */
    fun header(
        ctx: Context, p: JabraPrefs, title: String, backLabel: String = "<",
        feedback: ((View) -> Unit)? = null, onBack: () -> Unit
    ): LinearLayout {
        val r = row(ctx)
        try {
            val back = btn(ctx, p, backLabel, 0f, PanelSpec.H_BTN, JabraTokens.T_BTN, feedback) { onBack() }
            try { back.minWidth = JabraTokens.dp(ctx, PanelSpec.backWidthDp(backLabel)) } catch (_: Throwable) { }
            r.addView(back)
            val t = sectionTitle(ctx, p, title)
            t.gravity = android.view.Gravity.CENTER_VERTICAL
            t.layoutParams = LinearLayout.LayoutParams(0, JabraTokens.dp(ctx, PanelSpec.H_BTN), 1f)
            r.addView(t)
        } catch (_: Throwable) { }
        return r
    }

    // ---- v0.19.0 (dropdown plan S1): PillSelect. Nothing calls it yet (S2+ move the screens over; pref ui_pill comes with S2). ----

    @Volatile private var openSelect: android.widget.PopupWindow? = null

    /** Closes the dropdown list that is open (if any). Panels call it when they are replaced. Never throws. */
    fun closeSelect() { try { openSelect?.dismiss() } catch (_: Throwable) { }; openSelect = null }

    /**
     * Compact dropdown: ONE pill that shows the CURRENT value + a small down triangle ("Newest v"). Tap = a list pops up (max 6 rows, then it scrolls,
     * current row ticked); pick = onPick(index) and the pill shows the new value. Look = btn() look of the theme (Classic / Jabra look = soft key-coloured pill,
     * Borderless = text + triangle only, Custom = Theme studio colours). weight > 0 = equal share in a row, 0 = wrap content.
     * title = spoken name for TalkBack ("Sort: Newest"). names / cur are functions so the pill always reads the live value.
     * If the list cannot be shown (any problem) a tap CYCLES to the next value, so the control is never dead.
     */
    fun pillSelect(
        ctx: Context, p: JabraPrefs, title: String, names: () -> Array<String>, cur: () -> Int,
        hDp: Int = 32, weight: Float = 0f, feedback: ((View) -> Unit)? = null, textSp: Float = 0f, onPick: (Int) -> Unit   // v0.19.1: textSp 0 = 12 sp; panels pass fs(12f) (clip_btn_pct)
    ): Button {
        return try {
            val b = base(ctx, "", weight, hDp, feedback) { btn -> showSelectList(ctx, p, btn, title, names, cur, feedback, onPick) }
            val sp0 = if (textSp > 0f) textSp else JabraTokens.T_BTN
            b.textSize = sp0
            capFont(ctx, b, sp0)
            try { JabraThemes.style(p, b) } catch (_: Throwable) { }
            val side = JabraTokens.dp(ctx, 10)
            b.setPadding(side, 0, side, 0)
            b.maxLines = 1
            b.ellipsize = android.text.TextUtils.TruncateAt.END
            paintPill(b, title, names, cur)
            b
        } catch (_: Throwable) {
            val b = plain(ctx, "", { })
            try { b.text = SelectPlan.label(names(), cur()); b.setOnClickListener { try { onPick(SelectPlan.next(cur(), names().size)); b.text = SelectPlan.label(names(), cur()) } catch (_: Throwable) { } } } catch (_: Throwable) { }
            b
        }
    }

    private fun paintPill(b: Button, title: String, names: () -> Array<String>, cur: () -> Int) {
        try {
            val n = names()
            Glyphs.relabel(b, SelectPlan.label(n, cur()))
            b.contentDescription = SelectPlan.describe(title, n, cur())
        } catch (_: Throwable) { }
    }

    private fun showSelectList(
        ctx: Context, p: JabraPrefs, anchor: Button, title: String, names: () -> Array<String>, cur: () -> Int,
        feedback: ((View) -> Unit)?, onPick: (Int) -> Unit
    ) {
        val n = try { names() } catch (_: Throwable) { return }
        if (n.size < 2) return   // nothing to choose from
        fun pick(i: Int) { try { onPick(i) } catch (_: Throwable) { }; paintPill(anchor, title, names, cur) }
        try {
            closeSelect()
            val dm = ctx.resources.displayMetrics
            val rowPx = JabraTokens.dp(ctx, com.mgb.keyboard.typing.PanelLayout.MIN_TOUCH)
            val pad = JabraTokens.dp(ctx, JabraTokens.S3)
            val tickRoom = JabraTokens.dp(ctx, 24)
            val now = SelectPlan.clamp(cur(), n.size)
            val ink = JabraTokens.panelText(p)
            val paint = android.graphics.Paint()
            paint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, JabraTokens.T_BTN, dm)
            paint.typeface = Typeface.DEFAULT_BOLD
            var maxText = 0f
            val col = LinearLayout(ctx)
            col.orientation = LinearLayout.VERTICAL
            for (i in n.indices) {
                val r = TextView(ctx)
                r.textSize = JabraTokens.T_BTN
                capFont(ctx, r, JabraTokens.T_BTN)
                r.setTextColor(ink)
                r.gravity = android.view.Gravity.CENTER_VERTICAL
                r.maxLines = 1
                r.ellipsize = android.text.TextUtils.TruncateAt.END
                val isNow = i == now
                r.setPadding(if (isNow) pad else pad + tickRoom, 0, pad, 0)
                if (isNow) { r.setTypeface(r.typeface, Typeface.BOLD); r.setBackgroundColor(JabraTokens.rowPicked(p)) }
                r.text = SelectPlan.rowText(n[i], isNow)
                Glyphs.dress(r)
                r.contentDescription = n[i] + (if (isNow) ", selected" else "")
                r.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowPx)
                r.setOnClickListener {
                    try { feedback?.invoke(anchor) } catch (_: Throwable) { }
                    closeSelect()
                    pick(i)
                }
                col.addView(r)
                val w = paint.measureText(n[i])
                if (w > maxText) maxText = w
            }
            val margin = JabraTokens.dp(ctx, JabraTokens.S2)
            val h = SelectPlan.capHeight(SelectPlan.popupHeight(n.size, rowPx), dm.heightPixels, margin)   // v0.19.6 (S7): never taller than the screen
            val content: View = if (SelectPlan.scrolls(n.size) || h < n.size * rowPx) { val sv = android.widget.ScrollView(ctx); sv.addView(col); sv } else col
            val box = android.widget.FrameLayout(ctx)
            val bg = GradientDrawable()
            bg.setColor(JabraTokens.panelBg(p) or (0xFF shl 24))
            bg.setStroke(JabraTokens.dp(ctx, 1), JabraTokens.withAlpha(ink, 0x40))
            bg.cornerRadius = JabraTokens.dp(ctx, JabraTokens.R_MD).toFloat()
            box.background = bg
            box.clipToOutline = true
            box.addView(content)
            val contentW = Math.ceil(maxText.toDouble()).toInt() + 2 * pad + tickRoom
            val w = SelectPlan.popupWidth(anchor.width, contentW, dm.widthPixels, margin, JabraTokens.dp(ctx, 96))
            val loc = IntArray(2)
            anchor.getLocationOnScreen(loc)
            val gap = JabraTokens.dp(ctx, JabraTokens.S1)
            val x = SelectPlan.popupX(loc[0], w, dm.widthPixels, margin)
            val y = SelectPlan.popupY(loc[1], anchor.height, h, dm.heightPixels, gap, margin)
            val pop = android.widget.PopupWindow(box, w, h, false)
            pop.isTouchable = true
            pop.isOutsideTouchable = true
            pop.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0))
            try { pop.elevation = JabraTokens.dp(ctx, 6).toFloat() } catch (_: Throwable) { }
            pop.setTouchInterceptor { _, ev -> if (ev.action == android.view.MotionEvent.ACTION_OUTSIDE) { closeSelect(); true } else false }
            ToolTip.inScreen(pop)
            pop.setOnDismissListener { if (openSelect === pop) openSelect = null }
            anchor.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) { }
                override fun onViewDetachedFromWindow(v: View) {
                    try { if (openSelect === pop) closeSelect() } catch (_: Throwable) { }
                    try { v.removeOnAttachStateChangeListener(this) } catch (_: Throwable) { }
                }
            })
            pop.showAtLocation(anchor, android.view.Gravity.NO_GRAVITY, x, y)
            openSelect = pop
        } catch (_: Throwable) {
            closeSelect()
            pick(SelectPlan.next(cur(), n.size))   // fallback: the pill cycles
        }
    }

    // ---- private ----

    /** The button every variant starts from: text, no caps, no min width, no padding, click (feedback first), size + margins. */
    private fun base(ctx: Context, label: String, weight: Float, hDp: Int, feedback: ((View) -> Unit)?, onClick: (Button) -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        try { com.mgb.keyboard.typing.KeyDesc.describe(label)?.let { b.contentDescription = it } } catch (_: Throwable) { }   // v0.10.20 (W7): spoken name for symbol labels
        b.setOnClickListener {
            try { feedback?.invoke(b) } catch (_: Throwable) { }
            try { onClick(b) } catch (_: Throwable) { }
        }
        val lp = LinearLayout.LayoutParams(
            if (weight > 0f) 0 else LinearLayout.LayoutParams.WRAP_CONTENT,
            JabraTokens.dp(ctx, hDp),
            if (weight > 0f) weight else 0f
        )
        val m = JabraTokens.dp(ctx, JabraTokens.GAP)
        lp.setMargins(m, m, m, m)
        b.layoutParams = lp
        return b
    }

    /** v0.10.21 (D4): panel buttons have fixed dp heights, so their text may grow with the phone's font size only up to 1.15x. At font scale <= 1.15 nothing is touched. */
    private fun capFont(ctx: Context, t: TextView, sp: Float) {
        try {
            val fs = ctx.resources.configuration.fontScale
            if (com.mgb.keyboard.typing.FitPlan.needsCap(fs)) t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, com.mgb.keyboard.typing.FitPlan.cappedSp(sp, fs))
        } catch (_: Throwable) { }
    }

    /** Last-resort button: no theme, no layout params, still clickable. */
    private fun plain(ctx: Context, label: String, onClick: (Button) -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.setOnClickListener { try { onClick(b) } catch (_: Throwable) { } }
        return b
    }
}
