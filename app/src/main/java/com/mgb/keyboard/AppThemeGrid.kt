package com.mgb.keyboard

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.AppTokens

/**
 * v0.15.6 (N6): theme picker for the app screen (Look page, under the Theme row).
 * Grid of the 20 built-in themes (Classic first, order = JabraThemes.ALL, never reordered) + the custom slot ONLY when ct_json holds a valid custom theme.
 * Tap = pref "theme" (same value as the Theme cycle row and the keyboard picker). Chosen tile = accent frame. Tiles are 64 dp high (>= 48 dp touch).
 * Below the grid: the 4 Look presets (same ProfileStore path as the keyboard Theme panel).
 * The tile drawing is the existing ThemeTile of KeyboardPreview.kt. Everything in try / catch; a failure = no grid, the Theme row stays.
 */
object AppThemeGrid {
    class Handle(val view: View, val refresh: () -> Unit)

    private const val COLS = 3   // v0.17.85 (U9): fallback only, the real column count = ThemeCardPlan.cols(free width)

    /** [onPick] gets the OLD theme index before it is changed (for Undo); [onChange] redraws rows + preview. */
    fun build(ctx: Context, prefs: JabraPrefs, onPick: (Int) -> Unit, onChange: () -> Unit): Handle {
        fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        val grid = LinearLayout(ctx)
        grid.orientation = LinearLayout.VERTICAL
        val cols = try {   // v0.17.85 (U9): columns follow the width the grid really has (right pane on two panes)
            val sw = ctx.resources.configuration.screenWidthDp
            val two = com.mgb.keyboard.typing.HomePlan.twoPane(sw)
            com.mgb.keyboard.typing.ThemeCardPlan.cols(com.mgb.keyboard.typing.ThemeCardPlan.availDp(sw, two, if (two) com.mgb.keyboard.typing.HomePlan.leftPaneDp(sw) else 0, 2 * AppTokens.S4 + 2 * AppTokens.S3))
        } catch (_: Throwable) { COLS }
        val cur0 = TextView(ctx)   // v0.17.85 (U9): "Current theme: Name (3 of 20)"
        cur0.textSize = AppTokens.T_LABEL.toFloat()
        cur0.setTypeface(null, android.graphics.Typeface.BOLD)
        cur0.setTextColor(AppUi.text(ctx))
        cur0.setPadding(0, dp(AppTokens.S1), 0, dp(AppTokens.S1))

        fun fill() {
            grid.removeAllViews()
            val n = try { JabraThemes.count(prefs) } catch (_: Throwable) { JabraThemes.ALL.size }
            val cur = JabraThemes.index(prefs)
            try {
                val nm0 = if (cur in JabraThemes.ALL.indices) JabraThemes.ALL[cur].name else "Custom"
                cur0.text = com.mgb.keyboard.typing.ThemeCardPlan.currentLine(nm0, cur, JabraThemes.ALL.size)
            } catch (_: Throwable) { }
            var i = 0
            while (i < n) {
                val r = LinearLayout(ctx)
                r.orientation = LinearLayout.HORIZONTAL
                for (j in 0 until cols) {
                    val idx = i + j
                    val lp = LinearLayout.LayoutParams(0, dp(com.mgb.keyboard.typing.ThemeCardPlan.TILE_DP), 1f)
                    lp.setMargins(dp(AppTokens.S1), dp(AppTokens.S1), dp(AppTokens.S1), dp(AppTokens.S1))
                    if (idx < n) {
                        val t = ThemeTile(ctx, prefs, idx, idx == cur)
                        t.ring = AppUi.accent(ctx)
                        t.rows = com.mgb.keyboard.typing.ThemeCardPlan.MINI_ROWS   // v0.17.85 (U9)
                        val nm = if (idx == JabraThemes.ALL.size) "Custom" else JabraThemes.ALL[idx].name
                        t.contentDescription = com.mgb.keyboard.typing.ThemeCardPlan.describe(nm, idx == cur)
                        t.isClickable = true
                        t.isFocusable = true
                        t.setOnClickListener {
                            try {
                                val old = JabraThemes.index(prefs)
                                if (old != idx) {
                                    prefs.setInt("theme", idx)
                                    try { onPick(old) } catch (_: Throwable) { }
                                }
                                fill()
                                try { onChange() } catch (_: Throwable) { }
                            } catch (_: Throwable) { }
                        }
                        r.addView(t, lp)
                    } else {
                        r.addView(View(ctx), lp)
                    }
                }
                grid.addView(r)
                i += cols
            }
        }
        fill()

        val note = TextView(ctx)
        note.text = "Tap a theme. The frame shows the current one. Same choice as the Theme row above and the keyboard's theme list."
        note.textSize = AppTokens.T_CAPTION.toFloat()
        note.setTextColor(AppUi.dim(ctx))
        note.setPadding(0, dp(AppTokens.S1), 0, dp(AppTokens.S1))
        box.addView(cur0)
        box.addView(note)
        box.addView(grid)

        try {   // Look presets: one tap sets theme + look + key border + gap + bold (existing prefs, same path as Profiles)
            val head = TextView(ctx)
            head.text = "Look presets (sets theme, look, key border, gap, bold)"
            head.textSize = AppTokens.T_CAPTION.toFloat()
            head.setTextColor(AppUi.dim(ctx))
            head.setPadding(0, dp(AppTokens.S2), 0, dp(AppTokens.S1))
            box.addView(head)
            val pr = LinearLayout(ctx)
            pr.orientation = LinearLayout.HORIZONTAL
            for (i in 0 until com.mgb.keyboard.typing.LookPresets.count()) {
                val b = Button(ctx)
                b.text = com.mgb.keyboard.typing.LookPresets.name(i)
                b.textSize = AppTokens.T_LABEL.toFloat()
                AppRows.styleAction(ctx, b)
                val lp = LinearLayout.LayoutParams(0, dp(AppTokens.TOUCH), 1f)
                lp.setMargins(dp(AppTokens.S1), 0, dp(AppTokens.S1), 0)
                b.layoutParams = lp
                b.setPadding(dp(AppTokens.S1), 0, dp(AppTokens.S1), 0)
                b.setOnClickListener {
                    try {
                        val pf = com.mgb.keyboard.typing.LookPresets.profile(i)
                        if (pf != null && ProfileStore(ctx).apply(pf)) {
                            fill()
                            try { onChange() } catch (_: Throwable) { }
                            android.widget.Toast.makeText(ctx, "Applied: " + com.mgb.keyboard.typing.LookPresets.name(i) + ". To go back, tap your old theme.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Throwable) { }
                }
                pr.addView(b)
            }
            box.addView(pr)
        } catch (_: Throwable) { }
        return Handle(box) { try { fill() } catch (_: Throwable) { } }
    }
}
