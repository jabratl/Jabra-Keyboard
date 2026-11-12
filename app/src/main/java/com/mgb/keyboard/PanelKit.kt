package com.mgb.keyboard

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.PanelLayout

/**
 * v0.16.8 (plan X, step X8): PANEL KIT v2 = a few small reusable parts so the redesigned panels (X9-X12) look the same and stay short:
 *   header (back + title + small actions) | segmented (one choice out of 2-4, no scrolling) | sectionLabel | tileGrid (columns from the width) | stickyBar (action buttons at the bottom)
 * Built ONLY from what exists: UiKit.btn / chip / sectionTitle / noteText / cardBackground, JabraTokens (theme colours, spacing, text sizes), PanelLayout (numbers).
 * Every control is at least 40 dp high; text 12 sp / 11 sp; radii 9-12 dp; colours follow the theme (Classic = same greys as today).
 * NO screen uses this kit yet (nothing changes on the phone). Every function returns a view, never throws: a failure gives an empty LinearLayout.
 */
object PanelKit {

    /** One tile: [label] (+ optional small [sub] line), tap = [onClick]. [selected] = soft blue like a picked card. */
    class Tile(val label: String, val sub: String = "", val selected: Boolean = false, val onClick: () -> Unit)

    private fun dp(ctx: Context, v: Int): Int = JabraTokens.dp(ctx, v)
    private fun fb(host: JabraHost?): ((View) -> Unit)? = if (host == null) null else { v -> try { host.feedback(v) } catch (_: Throwable) { } }

    /** Back button + title (bold) + up to 3 small action buttons on the right. [actions] = label to click. */
    fun header(ctx: Context, p: JabraPrefs, host: JabraHost?, title: String, back: String = "<", actions: List<Pair<String, () -> Unit>> = emptyList(), onBack: () -> Unit): LinearLayout {
        val r = UiKit.row(ctx)
        try {
            r.gravity = Gravity.CENTER_VERTICAL
            val b = UiKit.btn(ctx, p, back, 0f, PanelLayout.MIN_TOUCH, JabraTokens.T_BTN, fb(host)) { onBack() }
            try { b.minWidth = dp(ctx, PanelLayout.MIN_TOUCH + 8) } catch (_: Throwable) { }
            r.addView(b)
            val t = UiKit.sectionTitle(ctx, p, title)
            t.gravity = Gravity.CENTER_VERTICAL
            t.maxLines = 1
            t.ellipsize = android.text.TextUtils.TruncateAt.END
            t.layoutParams = LinearLayout.LayoutParams(0, dp(ctx, PanelLayout.MIN_TOUCH), 1f)
            r.addView(t)
            for (a in actions.take(3)) r.addView(UiKit.btn(ctx, p, a.first, 0f, PanelLayout.MIN_TOUCH, JabraTokens.T_BTN, fb(host)) { a.second() }.also { try { it.minWidth = dp(ctx, PanelLayout.MIN_TOUCH + 8) } catch (_: Throwable) { } })
        } catch (_: Throwable) { }
        return r
    }

    /** 2-4 options side by side with equal width (no scrolling); [selected] is highlighted; tap = [onSelect](index). */
    fun segmented(ctx: Context, p: JabraPrefs, host: JabraHost?, options: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout {
        val r = UiKit.row(ctx)
        try {
            for (i in options.indices.take(4)) {
                val c = UiKit.chip(ctx, p, options[i], i == selected, PanelLayout.MIN_TOUCH, fb(host)) { onSelect(i) }
                c.layoutParams = LinearLayout.LayoutParams(0, dp(ctx, PanelLayout.MIN_TOUCH), 1f).also { it.setMargins(dp(ctx, 2), dp(ctx, 2), dp(ctx, 2), dp(ctx, 2)) }
                r.addView(c)
            }
        } catch (_: Throwable) { }
        return r
    }

    /** Small bold label above a group of tiles / buttons. */
    fun sectionLabel(ctx: Context, p: JabraPrefs, text: String): TextView {
        val t = UiKit.noteText(ctx, p, text)
        try { t.setTypeface(t.typeface, Typeface.BOLD); t.setPadding(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 2)) } catch (_: Throwable) { }
        return t
    }

    /** Grid of tiles: [columns] per row (use PanelLayout.columns(widthDp)); the last row is filled with spacers so every tile keeps the same width. */
    fun tileGrid(ctx: Context, p: JabraPrefs, host: JabraHost?, columns: Int, tiles: List<Tile>): LinearLayout {
        val box = LinearLayout(ctx)
        try {
            box.orientation = LinearLayout.VERTICAL
            val cols = columns.coerceIn(1, PanelLayout.MAX_COLS)
            val hasSub = tiles.any { it.sub.isNotEmpty() }
            val h = dp(ctx, PanelLayout.tileHeightDp(hasSub))
            var i = 0
            while (i < tiles.size) {
                val line = UiKit.row(ctx)
                for (k in 0 until cols) {
                    val lp = LinearLayout.LayoutParams(0, h, 1f)
                    lp.setMargins(dp(ctx, 2), dp(ctx, 2), dp(ctx, 2), dp(ctx, 2))
                    val t = tiles.getOrNull(i + k)
                    line.addView(if (t == null) View(ctx) else tile(ctx, p, host, t), lp)
                }
                box.addView(line)
                i += cols
            }
        } catch (_: Throwable) { }
        return box
    }

    private fun tile(ctx: Context, p: JabraPrefs, host: JabraHost?, t: Tile): View {
        val b = Button(ctx)
        try {
            b.isAllCaps = false
            b.minWidth = 0; b.minimumWidth = 0; b.minHeight = 0; b.minimumHeight = 0
            b.setPadding(dp(ctx, 4), 0, dp(ctx, 4), 0)
            b.text = if (t.sub.isEmpty()) t.label else t.label + "\n" + t.sub
            b.textSize = JabraTokens.T_BTN
            b.maxLines = if (t.sub.isEmpty()) 2 else 3
            b.setTextColor(JabraTokens.panelText(p))
            val g: GradientDrawable = UiKit.cardBackground(ctx, p, t.selected, false)
            b.background = g
            b.contentDescription = if (t.sub.isEmpty()) t.label else t.label + ". " + t.sub
            b.setOnClickListener { try { host?.feedback(b) } catch (_: Throwable) { }; try { t.onClick() } catch (_: Throwable) { } }
        } catch (_: Throwable) { }
        return b
    }

    /** Row of full-width action buttons for the bottom of a panel (e.g. Pin / Delete / Done); [weights] = same length as [actions], null = equal. */
    fun stickyBar(ctx: Context, p: JabraPrefs, host: JabraHost?, actions: List<Pair<String, () -> Unit>>, weights: List<Float>? = null): LinearLayout {
        val r = UiKit.row(ctx)
        try {
            for (i in actions.indices) {
                val w = weights?.getOrNull(i) ?: 1f
                r.addView(UiKit.btn(ctx, p, actions[i].first, w, PanelLayout.MIN_TOUCH, JabraTokens.T_BTN, fb(host)) { actions[i].second() })
            }
        } catch (_: Throwable) { }
        return r
    }
}
