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
