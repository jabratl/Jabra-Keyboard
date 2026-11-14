package com.mgb.keyboard

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.widget.Button
import android.widget.TextView

/*
 * v0.17.68 (S1): one place that swaps the cheap emoji / text signs in option labels for the keyboard's OWN drawn icons (JabraIcons).
 * A label like "🔍 Search", "✓ Newest", "↶ Undo", "Folder ▾" keeps its words; the sign becomes a small drawn icon at the start / end
 * and takes the text colour LIVE (so selected / pressed / dark / light looks stay readable). A label that is only a sign
 * ("◀", "⌫") becomes an icon-only button. Respects the existing "icons_on" pref: OFF = the old text, nothing changes.
 * Never throws; any problem = the label stays as it was.
 */
object Glyphs {
    private val LEAD = linkedMapOf(
        "🔍" to "search", "✕" to "close", "✓" to "tick", "↶" to "undo", "↕" to "sort",
        "★" to "pinned", "☆" to "pin", "☑" to "checkon", "☐" to "checkoff", "●" to "dot",
        "⇧" to "shift", "✎" to "edit", "⚠" to "warn"
    )
    private val SOLO = mapOf("◀" to "prev", "▶" to "next", "⌫" to "back", "␣" to "space", "⏎" to "enter", "⋯" to "more", "▴" to "up", "▲" to "up", "▼" to "caret")
    /** Put this (a zero-width space) first in a label that is user content, so no sign in it is ever replaced. */
    val KEEP: String = 0x200B.toChar().toString()
    private const val TAIL = "▾"   // small down triangle = "opens a list"

    /** Dress [b] (button or text) if its text starts / ends with a known sign. Returns the same view. */
    fun <T : TextView> dress(b: T): T {
        try {
            if (!JabraPrefs(b.context).bool("icons_on", true)) return b
            val raw = (b.text?.toString() ?: "").replace(0xFE0F.toChar().toString(), "")
            if (raw.isEmpty() || raw.startsWith(KEEP)) return b   // KEEP = the label is user content (clip text, folder name): never touch it
            if (b is Button) {
                val solo = SOLO[raw.trim()]
                if (solo != null) { JabraIcons.apply(b, solo, true, b.currentTextColor); return b }
            }
            val names = ArrayList<String>()
            var rest = raw.trim()
            var again = true
            while (again && names.size < 3) {   // "checkbox + pinned star" = two signs in a row
                again = false
                for ((k, v) in LEAD) if (rest.startsWith(k)) { names.add(v); rest = rest.substring(k.length).trim(); again = true; break }
            }
            val name: String? = names.firstOrNull()
            var tail: String? = null
            if (rest.endsWith(TAIL)) { tail = "caret"; rest = rest.substring(0, rest.length - TAIL.length).trim() }
            if (name == null && tail == null) return b
            if (rest.isEmpty() && tail == null && b is Button) { JabraIcons.apply(b, name!!, true, b.currentTextColor); return b }   // sign only = icon-only button
            b.text = rest
            val lead = if (names.isNotEmpty()) GlyphDrawable(b, names) else null
            val end = if (tail != null) GlyphDrawable(b, listOf(tail)) else null
            b.setCompoundDrawablesRelative(lead, null, end, null)
            b.compoundDrawablePadding = (b.textSize * 0.35f).toInt()
        } catch (_: Throwable) { }
        return b
    }

    /** Change the label of an already built button / text and dress it again (use instead of `b.text = "🔍 ..."`). */
    fun relabel(b: TextView, label: String) {
        try {
            b.setCompoundDrawablesRelative(null, null, null, null)
            b.text = label
            dress(b)
        } catch (_: Throwable) { try { b.text = label } catch (_: Throwable) { } }
    }

    /** A drawn icon sized to the text; colour = the view's CURRENT text colour (read at draw time). */
    private class GlyphDrawable(private val v: TextView, private val names: List<String>) : Drawable() {
        private val box = (v.textSize * 1.1f).toInt().coerceAtLeast(8)
        private val gap = (box * 0.25f).toInt()
        private val wide = box * names.size + gap * (names.size - 1)
        init { setBounds(0, 0, wide, box) }
        override fun getIntrinsicWidth(): Int = wide
        override fun getIntrinsicHeight(): Int = box
        override fun draw(c: Canvas) {
            try {
                val now = v.currentTextColor
                val b = bounds
                val size = Math.min(box.toFloat(), v.textSize * 1.1f)
                val big = (size / 0.46f).toInt()   // IconDrawable draws at 46% of its box
                val cy = b.centerY()
                for (i in names.indices) {
                    val d = JabraIcons.IconDrawable(names[i], now)
                    val cx = b.left + box / 2 + i * (box + gap)
                    d.setBounds(cx - big / 2, cy - big / 2, cx + big / 2, cy + big / 2)
                    d.setAlpha(alpha255)
                    d.draw(c)
                }
            } catch (_: Throwable) { }
        }
        private var alpha255 = 255
        override fun setAlpha(alpha: Int) { alpha255 = alpha }
        override fun setColorFilter(cf: ColorFilter?) { }
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
