package com.mgb.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.widget.Button

/*
 * v0.0.7 own icons. Drawn with Canvas lines (no emoji, no image files, no new dependency, no permission).
 * Pref key "icons_on" (default true). OFF = old text/emoji labels come back.
 * Safe rule: JabraIcons.apply() never throws. If anything fails the button keeps its old label.
 * Names: clip all cut copy paste plain search close pin pinned gear shift back enter space
 */
object JabraIcons {

    val NAMES = listOf(
        "clip", "all", "cut", "copy", "paste", "plain", "search", "close",
        "pin", "pinned", "gear", "shift", "back", "enter", "space", "fmt", "globe", "mic", "emoji", "mode_phone", "mode_pc", "numpad", "sym", "theme", "onehand", "size", "pad", "vault", "tt", "profiles", "keymap",
        "selword", "selline", "delword", "delline", "linestart", "lineend", "dupline", "selnone",   // v0.16.13 (X13): 8 edit items
        "tab",   // v0.17.7 (K5b)
        "warn", "tick", "undo", "sort", "checkon", "checkoff", "dot", "prev", "next", "caret", "edit", "more", "up", "info"   // v0.17.68 (S1): own signs for Clipboard / Vault / Text tools options (were cheap emoji)
        // v0.16.5: keymap; v0.5.1: profiles; v0.4.6: tt; v0.4.4: vault; v0.1.2; v0.2.2: 5 names; v0.2.3: pad
    )

    /**
     * Put icon [name] on [b] (icon replaces the text). Returns true if the icon was applied.
     * [on] = prefs icons_on. When false or on error nothing changes.
     */
    fun apply(b: Button, name: String, on: Boolean, color: Int = Color.parseColor("#222222")): Boolean {   // v0.1.0: optional colour (default = old #222222)
        if (!on) return false
        return try {
            if (name !in NAMES) return false
            val d = IconDrawable(name, color)
            val old = b.text?.toString() ?: ""
            b.contentDescription = try { com.mgb.keyboard.typing.KeyDesc.describe(old) ?: (if (old.isBlank()) com.mgb.keyboard.typing.KeyDesc.iconName(name) else old) } catch (_: Throwable) { b.text }   // v0.10.20 (W7): spoken name (was: raw label)
            b.text = ""
            b.foreground = d                // API 23+ (minSdk is 24)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Same as apply(), but reads the "icons_on" pref itself. Never throws; returns the same button. */
    fun put(ctx: Context, b: Button, name: String): Button {
        try { apply(b, name, JabraPrefs(ctx).bool("icons_on", true)) } catch (_: Throwable) { }
        return b
    }

    /** v0.1.0: same as put(), but the icon colour follows the theme (Classic = same #222222 as before; Dark = light icon). Used on themed panels only. */
    fun putThemed(ctx: Context, b: Button, name: String): Button {
        try {
            val p = JabraPrefs(ctx)
            val c = try { JabraThemes.iconColor(p) } catch (_: Throwable) { Color.parseColor("#222222") }
            apply(b, name, p.bool("icons_on", true), c)
        } catch (_: Throwable) { }
        return b
    }

    /** Draws one icon centered in the view bounds. Everything is relative to bounds, so any key size works. */
    class IconDrawable(private val name: String, private val color: Int) : Drawable() {

        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        override fun draw(c: Canvas) {
            try {
                val bw = bounds.width().toFloat()
                val bh = bounds.height().toFloat()
                if (bw <= 0f || bh <= 0f) return
                val s = Math.min(bw, bh) * 0.46f          // icon size
                val cx = bounds.left + bw / 2f
                val cy = bounds.top + bh / 2f
                c.save()
                c.translate(cx - s / 2f, cy - s / 2f)     // icon box 0..s
                p.color = color
                f.color = color
                p.strokeWidth = Math.max(2f, s * 0.10f)
                drawIcon(c, s)
                c.restore()
            } catch (_: Throwable) { }
        }

        private fun line(c: Canvas, s: Float, x1: Float, y1: Float, x2: Float, y2: Float) =
            c.drawLine(x1 * s, y1 * s, x2 * s, y2 * s, p)

        private fun rr(c: Canvas, s: Float, l: Float, t: Float, r: Float, b: Float, rad: Float = 0.1f) =
            c.drawRoundRect(RectF(l * s, t * s, r * s, b * s), rad * s, rad * s, p)

        private fun drawIcon(c: Canvas, s: Float) {
            when (name) {
                "clip" -> {   // clipboard board + clip on top + two text lines
                    rr(c, s, 0.14f, 0.16f, 0.86f, 0.98f)
                    f.style = Paint.Style.FILL
                    c.drawRoundRect(RectF(0.34f * s, 0.02f * s, 0.66f * s, 0.26f * s), 0.06f * s, 0.06f * s, f)
                    line(c, s, 0.30f, 0.52f, 0.70f, 0.52f)
                    line(c, s, 0.30f, 0.74f, 0.58f, 0.74f)
                }
                "all" -> {    // dashed selection box with a filled block inside
                    val dash = android.graphics.DashPathEffect(floatArrayOf(s * 0.14f, s * 0.12f), 0f)
                    p.pathEffect = dash
                    rr(c, s, 0.04f, 0.10f, 0.96f, 0.90f, 0.08f)
                    p.pathEffect = null
                    c.drawRoundRect(RectF(0.30f * s, 0.34f * s, 0.70f * s, 0.66f * s), 0.05f * s, 0.05f * s, f)
                }
                "cut" -> {    // scissors: two rings at bottom, blades crossing to the top
                    c.drawCircle(0.24f * s, 0.80f * s, 0.15f * s, p)
                    c.drawCircle(0.76f * s, 0.80f * s, 0.15f * s, p)
                    line(c, s, 0.32f, 0.68f, 0.78f, 0.04f)
                    line(c, s, 0.68f, 0.68f, 0.22f, 0.04f)
                }
                "copy" -> {   // two overlapping sheets
                    rr(c, s, 0.34f, 0.06f, 0.94f, 0.68f)
                    rr(c, s, 0.06f, 0.32f, 0.66f, 0.96f)
                }
                "paste" -> {  // board + clip + down arrow
                    rr(c, s, 0.10f, 0.14f, 0.90f, 0.96f)
                    c.drawRoundRect(RectF(0.36f * s, 0.02f * s, 0.64f * s, 0.24f * s), 0.05f * s, 0.05f * s, f)
                    line(c, s, 0.50f, 0.38f, 0.50f, 0.78f)
                    line(c, s, 0.32f, 0.62f, 0.50f, 0.80f)
                    line(c, s, 0.68f, 0.62f, 0.50f, 0.80f)
                }
                "plain" -> {  // letter T with a slash through the style (plain text)
                    line(c, s, 0.18f, 0.14f, 0.82f, 0.14f)
                    line(c, s, 0.50f, 0.14f, 0.50f, 0.78f)
                    line(c, s, 0.12f, 0.96f, 0.88f, 0.96f)
                    line(c, s, 0.86f, 0.30f, 0.14f, 0.76f)
                }
                "search" -> { // ring + handle
                    c.drawCircle(0.42f * s, 0.42f * s, 0.30f * s, p)
                    line(c, s, 0.64f, 0.64f, 0.96f, 0.96f)
                }
                "close" -> {  // plain X
                    line(c, s, 0.14f, 0.14f, 0.86f, 0.86f)
                    line(c, s, 0.86f, 0.14f, 0.14f, 0.86f)
                }
                "pin", "pinned" -> {  // push-pin: round head, needle. pinned = head filled
                    val head = RectF(0.24f * s, 0.04f * s, 0.76f * s, 0.46f * s)
                    if (name == "pinned") c.drawOval(head, f) else c.drawOval(head, p)
                    line(c, s, 0.50f, 0.46f, 0.50f, 0.96f)
                    line(c, s, 0.30f, 0.56f, 0.70f, 0.56f)
                }
                "gear" -> {   // ring with 8 teeth
                    c.drawCircle(0.50f * s, 0.50f * s, 0.22f * s, p)
                    for (i in 0 until 8) {
                        val a = Math.toRadians(i * 45.0)
                        val x1 = (0.50 + 0.30 * Math.cos(a)).toFloat()
                        val y1 = (0.50 + 0.30 * Math.sin(a)).toFloat()
                        val x2 = (0.50 + 0.46 * Math.cos(a)).toFloat()
                        val y2 = (0.50 + 0.46 * Math.sin(a)).toFloat()
                        line(c, s, x1, y1, x2, y2)
                    }
                }
                "shift" -> {  // arrow up (outline) + stem
                    val path = Path()
                    path.moveTo(0.50f * s, 0.06f * s)
                    path.lineTo(0.94f * s, 0.50f * s)
                    path.lineTo(0.68f * s, 0.50f * s)
                    path.lineTo(0.68f * s, 0.90f * s)
                    path.lineTo(0.32f * s, 0.90f * s)
                    path.lineTo(0.32f * s, 0.50f * s)
                    path.lineTo(0.06f * s, 0.50f * s)
                    path.close()
                    c.drawPath(path, p)
                }
                "back" -> {   // backspace: tag shape with x inside
                    val path = Path()
                    path.moveTo(0.34f * s, 0.18f * s)
                    path.lineTo(0.96f * s, 0.18f * s)
                    path.lineTo(0.96f * s, 0.82f * s)
                    path.lineTo(0.34f * s, 0.82f * s)
                    path.lineTo(0.04f * s, 0.50f * s)
                    path.close()
                    c.drawPath(path, p)
                    line(c, s, 0.50f, 0.36f, 0.78f, 0.64f)
                    line(c, s, 0.78f, 0.36f, 0.50f, 0.64f)
                }
                "enter" -> {  // return arrow: down then left
                    val path = Path()
                    path.moveTo(0.92f * s, 0.12f * s)
                    path.lineTo(0.92f * s, 0.64f * s)
                    path.lineTo(0.14f * s, 0.64f * s)
                    c.drawPath(path, p)
                    line(c, s, 0.34f, 0.44f, 0.14f, 0.64f)
                    line(c, s, 0.34f, 0.84f, 0.14f, 0.64f)
                }
                "tab" -> {    // v0.17.7 (K5b): arrow to the right + stop bar
                    line(c, s, 0.08f, 0.50f, 0.78f, 0.50f)
                    line(c, s, 0.56f, 0.28f, 0.78f, 0.50f)
                    line(c, s, 0.56f, 0.72f, 0.78f, 0.50f)
                    line(c, s, 0.92f, 0.20f, 0.92f, 0.80f)
                }
                "fmt" -> {    // v0.0.8 formatter: paragraph lines, first line indented
                    line(c, s, 0.30f, 0.14f, 0.96f, 0.14f)
                    line(c, s, 0.04f, 0.38f, 0.96f, 0.38f)
                    line(c, s, 0.04f, 0.62f, 0.96f, 0.62f)
                    line(c, s, 0.04f, 0.86f, 0.64f, 0.86f)
                }
                "globe" -> {  // v0.1.1: circle, meridian ellipse, equator
                    c.drawCircle(0.5f * s, 0.5f * s, 0.46f * s, p)
                    c.drawOval(RectF(0.26f * s, 0.04f * s, 0.74f * s, 0.96f * s), p)
                    line(c, s, 0.04f, 0.5f, 0.96f, 0.5f)
                }
                "mic" -> {    // v0.1.1: capsule, cup arc, stem, base
                    rr(c, s, 0.34f, 0.02f, 0.66f, 0.58f, 0.16f)
                    c.drawArc(RectF(0.16f * s, 0.20f * s, 0.84f * s, 0.78f * s), 0f, 180f, false, p)
                    line(c, s, 0.5f, 0.78f, 0.5f, 0.96f)
                    line(c, s, 0.32f, 0.96f, 0.68f, 0.96f)
                }
                "emoji" -> {  // v0.1.1: face with two eyes and a smile
                    c.drawCircle(0.5f * s, 0.5f * s, 0.46f * s, p)
                    c.drawCircle(0.36f * s, 0.40f * s, 0.06f * s, f)
                    c.drawCircle(0.64f * s, 0.40f * s, 0.06f * s, f)
                    c.drawArc(RectF(0.26f * s, 0.28f * s, 0.74f * s, 0.74f * s), 25f, 130f, false, p)
                }
                "mode_phone" -> {  // v0.1.2: own robot head: round top, 2 antennas, 2 eyes
                    val path = Path()
                    path.moveTo(0.10f * s, 0.92f * s)
                    path.lineTo(0.10f * s, 0.62f * s)
                    path.arcTo(RectF(0.10f * s, 0.30f * s, 0.90f * s, 0.94f * s), 180f, 180f)
                    path.lineTo(0.90f * s, 0.92f * s)
                    path.close()
                    c.drawPath(path, p)
                    line(c, s, 0.28f, 0.38f, 0.18f, 0.10f)
                    line(c, s, 0.72f, 0.38f, 0.82f, 0.10f)
                    c.drawCircle(0.36f * s, 0.62f * s, 0.05f * s, f)
                    c.drawCircle(0.64f * s, 0.62f * s, 0.05f * s, f)
                }
                "mode_pc" -> {  // v0.1.2: own monitor + stand + small keyboard line
                    rr(c, s, 0.06f, 0.04f, 0.94f, 0.58f, 0.08f)
                    line(c, s, 0.50f, 0.58f, 0.50f, 0.72f)
                    line(c, s, 0.30f, 0.72f, 0.70f, 0.72f)
                    rr(c, s, 0.10f, 0.82f, 0.90f, 0.98f, 0.04f)
                }
                "numpad" -> {   // v0.2.2: 3 x 3 dots
                    for (ix in 0..2) for (iy in 0..2) c.drawCircle((0.15f + 0.35f * ix) * s, (0.15f + 0.35f * iy) * s, 0.07f * s, f)
                }
                "sym" -> {   // v0.2.2: hash sign
                    line(c, s, 0.36f, 0.08f, 0.28f, 0.92f)
                    line(c, s, 0.72f, 0.08f, 0.64f, 0.92f)
                    line(c, s, 0.10f, 0.36f, 0.92f, 0.36f)
                    line(c, s, 0.08f, 0.66f, 0.90f, 0.66f)
                }
                "theme" -> {   // v0.2.2: circle with a filled half
                    c.drawCircle(0.5f * s, 0.5f * s, 0.46f * s, p)
                    c.drawArc(RectF(0.04f * s, 0.04f * s, 0.96f * s, 0.96f * s), 90f, 180f, true, f)
                }
                "onehand" -> {   // v0.2.2: phone outline + filled block bottom-left
                    rr(c, s, 0.22f, 0.02f, 0.78f, 0.98f, 0.10f)
                    c.drawRoundRect(RectF(0.30f * s, 0.62f * s, 0.52f * s, 0.88f * s), 0.04f * s, 0.04f * s, f)
                }
                "size" -> {   // v0.2.2: vertical double arrow
                    line(c, s, 0.50f, 0.10f, 0.50f, 0.90f)
                    line(c, s, 0.30f, 0.30f, 0.50f, 0.10f)
                    line(c, s, 0.70f, 0.30f, 0.50f, 0.10f)
                    line(c, s, 0.30f, 0.70f, 0.50f, 0.90f)
                    line(c, s, 0.70f, 0.70f, 0.50f, 0.90f)
                }
                "selword" -> {   // v0.16.13: a word bar with a filled selection block
                    c.drawRoundRect(RectF(0.14f * s, 0.34f * s, 0.86f * s, 0.66f * s), 0.06f * s, 0.06f * s, f)
                    line(c, s, 0.14f, 0.18f, 0.14f, 0.82f)
                    line(c, s, 0.86f, 0.18f, 0.86f, 0.82f)
                }
                "selline" -> {   // v0.16.13: three lines, the middle one selected
                    line(c, s, 0.10f, 0.22f, 0.90f, 0.22f)
                    c.drawRoundRect(RectF(0.08f * s, 0.42f * s, 0.92f * s, 0.58f * s), 0.05f * s, 0.05f * s, f)
                    line(c, s, 0.10f, 0.78f, 0.90f, 0.78f)
                }
                "delword" -> {   // v0.16.13: a word bar crossed out
                    line(c, s, 0.10f, 0.50f, 0.90f, 0.50f)
                    line(c, s, 0.30f, 0.20f, 0.70f, 0.80f)
                    line(c, s, 0.70f, 0.20f, 0.30f, 0.80f)
                }
                "delline" -> {   // v0.16.13: three lines, the middle one removed with a cross
                    line(c, s, 0.10f, 0.18f, 0.90f, 0.18f)
                    line(c, s, 0.10f, 0.82f, 0.90f, 0.82f)
                    line(c, s, 0.28f, 0.36f, 0.72f, 0.64f)
                    line(c, s, 0.72f, 0.36f, 0.28f, 0.64f)
                }
                "linestart" -> {   // v0.16.13: bar on the left + arrow to it
                    line(c, s, 0.14f, 0.16f, 0.14f, 0.84f)
                    line(c, s, 0.30f, 0.50f, 0.90f, 0.50f)
                    line(c, s, 0.50f, 0.30f, 0.30f, 0.50f)
                    line(c, s, 0.50f, 0.70f, 0.30f, 0.50f)
                }
                "lineend" -> {   // v0.16.13: bar on the right + arrow to it
                    line(c, s, 0.86f, 0.16f, 0.86f, 0.84f)
                    line(c, s, 0.10f, 0.50f, 0.70f, 0.50f)
                    line(c, s, 0.50f, 0.30f, 0.70f, 0.50f)
                    line(c, s, 0.50f, 0.70f, 0.70f, 0.50f)
                }
                "dupline" -> {   // v0.16.13: two stacked line cards (copy below)
                    rr(c, s, 0.10f, 0.12f, 0.90f, 0.40f, 0.06f)
                    c.drawRoundRect(RectF(0.10f * s, 0.58f * s, 0.90f * s, 0.86f * s), 0.06f * s, 0.06f * s, f)
                }
                "selnone" -> {   // v0.16.13: selection block with a caret (selection collapsed)
                    rr(c, s, 0.08f, 0.34f, 0.60f, 0.66f, 0.05f)
                    line(c, s, 0.80f, 0.16f, 0.80f, 0.84f)
                }
                "keymap" -> {   // v0.16.5: a keyboard outline with 3 key dots (key map)
                    rr(c, s, 0.06f, 0.22f, 0.94f, 0.78f, 0.08f)
                    c.drawCircle(0.26f * s, 0.40f * s, 0.05f * s, f)
                    c.drawCircle(0.50f * s, 0.40f * s, 0.05f * s, f)
                    c.drawCircle(0.74f * s, 0.40f * s, 0.05f * s, f)
                    line(c, s, 0.26f, 0.62f, 0.74f, 0.62f)
                }
                "profiles" -> {   // v0.5.1: three stacked cards (layout sets)
                    rr(c, s, 0.10f, 0.10f, 0.90f, 0.32f, 0.06f)
                    rr(c, s, 0.10f, 0.40f, 0.90f, 0.62f, 0.06f)
                    rr(c, s, 0.10f, 0.70f, 0.90f, 0.92f, 0.06f)
                }
                "tt" -> {   // v0.4.6: letter T (bar + stem) with an underline
                    line(c, s, 0.18f, 0.16f, 0.82f, 0.16f)
                    line(c, s, 0.50f, 0.16f, 0.50f, 0.74f)
                    line(c, s, 0.30f, 0.90f, 0.70f, 0.90f)
                }
                "vault" -> {   // v0.4.4: a box (safe) with a keyhole dot and a lid line
                    rr(c, s, 0.08f, 0.12f, 0.92f, 0.90f, 0.10f)
                    line(c, s, 0.08f, 0.38f, 0.92f, 0.38f)
                    c.drawCircle(0.5f * s, 0.64f * s, 0.08f * s, f)
                }
                "pad" -> {   // v0.2.3: four arrows (cross)
                    line(c, s, 0.50f, 0.08f, 0.50f, 0.92f)
                    line(c, s, 0.08f, 0.50f, 0.92f, 0.50f)
                    line(c, s, 0.34f, 0.24f, 0.50f, 0.08f)
                    line(c, s, 0.66f, 0.24f, 0.50f, 0.08f)
                    line(c, s, 0.34f, 0.76f, 0.50f, 0.92f)
                    line(c, s, 0.66f, 0.76f, 0.50f, 0.92f)
                    line(c, s, 0.24f, 0.34f, 0.08f, 0.50f)
                    line(c, s, 0.24f, 0.66f, 0.08f, 0.50f)
                    line(c, s, 0.76f, 0.34f, 0.92f, 0.50f)
                    line(c, s, 0.76f, 0.66f, 0.92f, 0.50f)
                }
                "tick" -> {   // v0.17.68 (S1)
                    line(c, s, 0.12f, 0.54f, 0.38f, 0.8f)
                    line(c, s, 0.38f, 0.8f, 0.9f, 0.22f)
                }
                "undo" -> {   // v0.17.68 (S1)
                    line(c, s, 0.08f, 0.3f, 0.62f, 0.3f)
                    line(c, s, 0.08f, 0.3f, 0.3f, 0.1f)
                    line(c, s, 0.08f, 0.3f, 0.3f, 0.52f)
                    c.drawArc(RectF(0.34f * s, 0.3f * s, 0.9f * s, 0.86f * s), -90f, 180f, false, p)
                    line(c, s, 0.62f, 0.86f, 0.28f, 0.86f)
                }
                "sort" -> {   // v0.17.68 (S1)
                    line(c, s, 0.08f, 0.22f, 0.92f, 0.22f)
                    line(c, s, 0.08f, 0.5f, 0.66f, 0.5f)
                    line(c, s, 0.08f, 0.78f, 0.4f, 0.78f)
                }
                "checkon" -> {   // v0.17.68 (S1)
                    rr(c, s, 0.1f, 0.1f, 0.9f, 0.9f, 0.18f)
                    line(c, s, 0.28f, 0.52f, 0.44f, 0.68f)
                    line(c, s, 0.44f, 0.68f, 0.74f, 0.32f)
                }
                "checkoff" -> {   // v0.17.68 (S1)
                    rr(c, s, 0.1f, 0.1f, 0.9f, 0.9f, 0.18f)
                }
                "dot" -> {   // v0.17.68 (S1)
                    c.drawCircle(0.5f * s, 0.5f * s, 0.22f * s, f)
                }
                "prev" -> {   // v0.17.68 (S1)
                    line(c, s, 0.64f, 0.14f, 0.3f, 0.5f)
                    line(c, s, 0.3f, 0.5f, 0.64f, 0.86f)
                }
                "next" -> {   // v0.17.68 (S1)
                    line(c, s, 0.36f, 0.14f, 0.7f, 0.5f)
                    line(c, s, 0.7f, 0.5f, 0.36f, 0.86f)
                }
                "caret" -> {   // v0.17.68 (S1)
                    line(c, s, 0.14f, 0.34f, 0.5f, 0.7f)
                    line(c, s, 0.5f, 0.7f, 0.86f, 0.34f)
                }
                "edit" -> {   // v0.17.68 (S1)
                    line(c, s, 0.62f, 0.14f, 0.86f, 0.38f)
                    line(c, s, 0.62f, 0.14f, 0.14f, 0.62f)
                    line(c, s, 0.86f, 0.38f, 0.38f, 0.86f)
                    line(c, s, 0.14f, 0.62f, 0.1f, 0.9f)
                    line(c, s, 0.1f, 0.9f, 0.38f, 0.86f)
                }
                "info" -> {   // v0.17.78 (U3): circled i = "show help"
                    c.drawCircle(0.5f * s, 0.5f * s, 0.4f * s, p)
                    c.drawCircle(0.5f * s, 0.3f * s, 0.05f * s, f)
                    line(c, s, 0.5f, 0.44f, 0.5f, 0.72f)
                }
                "more" -> {   // v0.17.68 (S1)
                    c.drawCircle(0.16f * s, 0.5f * s, 0.1f * s, f)
                    c.drawCircle(0.5f * s, 0.5f * s, 0.1f * s, f)
                    c.drawCircle(0.84f * s, 0.5f * s, 0.1f * s, f)
                }
                "warn" -> {   // v0.17.72 (S5): triangle + exclamation
                    line(c, s, 0.5f, 0.14f, 0.9f, 0.82f)
                    line(c, s, 0.9f, 0.82f, 0.1f, 0.82f)
                    line(c, s, 0.1f, 0.82f, 0.5f, 0.14f)
                    line(c, s, 0.5f, 0.38f, 0.5f, 0.6f)
                    line(c, s, 0.5f, 0.71f, 0.5f, 0.72f)
                }
                "up" -> {   // v0.17.68 (S1)
                    line(c, s, 0.14f, 0.66f, 0.5f, 0.3f)
                    line(c, s, 0.5f, 0.3f, 0.86f, 0.66f)
                }
                "space" -> {  // open bracket shape
                    val path = Path()
                    path.moveTo(0.04f * s, 0.34f * s)
                    path.lineTo(0.04f * s, 0.70f * s)
                    path.lineTo(0.96f * s, 0.70f * s)
                    path.lineTo(0.96f * s, 0.34f * s)
                    c.drawPath(path, p)
                }
            }
        }

        override fun setAlpha(alpha: Int) { p.alpha = alpha; f.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf; f.colorFilter = cf }
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
