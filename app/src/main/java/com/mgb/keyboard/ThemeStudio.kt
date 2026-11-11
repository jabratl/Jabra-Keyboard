package com.mgb.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.CustomTheme

/*
 * v0.4.8 THEME STUDIO A (route showPanel("tstudio"); opened from the theme picker "Custom theme (colours)...").
 * Edits ONE user theme = slot 20 (pref ct_json). Colours by typed hex (small hex keypad) or swatches, roundness + key opacity with [-] value [+],
 * 4 ready colour sets (AMOLED / Reading / Cyber / Professional). The preview tile shows the DRAFT; nothing is saved until [Save].
 * Roundness and opacity only show on the keys with Look = Jabra (as the other Jabra look settings).
 * Built in code like the other panels; build() returns null on any problem.
 */
class ThemeStudio(private val ctx: Context, private val host: JabraHost) {

    private val prefs = JabraPrefs(ctx)
    private var draft = CustomTheme.defaults()
    private var field = 0                  // 0 bg, 1 bg2, 2 key, 3 text, 4 func
    private var hexBuf = ""                // typed digits of the chosen field (no #)
    private var msg = ""
    private var confirmReset = false
    private var loaded = false

    private val fieldNames = arrayOf("Background", "Bg 2", "Keys", "Text", "Func keys")
    private val swatches = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF1E1F22.toInt(), 0xFF3A3C42.toInt(), 0xFFF4ECD8.toInt(),
        0xFFD6E6F5.toInt(), 0xFF1C6BC0.toInt(), 0xFF0E2A24.toInt(), 0xFFF6D3DE.toInt(), 0xFFFF9A7B.toInt())

    /** Call when the panel is opened from outside: loads the saved custom theme (or defaults) into the draft. */
    fun reset() {
        draft = try { JabraThemes.custom(prefs)?.let { CustomTheme.parse(it.toJson()) } ?: CustomTheme.defaults() } catch (_: Throwable) { CustomTheme.defaults() }
        field = 0; msg = ""; confirmReset = false; loaded = true
        hexBuf = currentHex()
    }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()
    private fun refresh() { host.showPanel("tstudio") }

    private fun getColor(): Int = when (field) { 0 -> draft.bg; 1 -> draft.bg2; 2 -> draft.key; 3 -> draft.text; else -> draft.func }

    private fun currentHex(): String = if (field == 1 && !draft.hasBg2) "" else CustomTheme.toHex(getColor()).substring(1)

    private fun setColor(c: Int) {
        when (field) { 0 -> draft.bg = c; 1 -> { draft.bg2 = c; draft.hasBg2 = true }; 2 -> draft.key = c; 3 -> draft.text = c; else -> draft.func = c }
    }

    private fun btn(label: String, weight: Float, hDp: Int, onClick: (Button) -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        b.textSize = 12f
        b.setOnClickListener {
            try { host.feedback(b) } catch (_: Throwable) { }
            try { onClick(b) } catch (_: Throwable) { }
        }
        val lp = LinearLayout.LayoutParams(if (weight > 0f) 0 else LinearLayout.LayoutParams.WRAP_CONTENT, dp(hDp), if (weight > 0f) weight else 0f)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        b.layoutParams = lp
        return b
    }

    private fun row(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    /** Live preview of the DRAFT: background (gradient if Bg 2 is set), 3 keys (2 normal + 1 func), sample text. */
    private inner class Preview(c: Context) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            p.style = Paint.Style.FILL
            if (draft.hasBg2) p.shader = android.graphics.LinearGradient(0f, 0f, w, h, draft.bg, draft.bg2, android.graphics.Shader.TileMode.CLAMP) else p.color = draft.bg
            rect.set(0f, 0f, w, h)
            canvas.drawRoundRect(rect, dp(8).toFloat(), dp(8).toFloat(), p)
            p.shader = null
            val pad = dp(8).toFloat()
            val gap = dp(4).toFloat()
            val kw = (w - 2f * pad - 2f * gap) / 3f
            val kh = h - 2f * dp(6).toFloat()
            val rad = draft.radiusDp() * ctx.resources.displayMetrics.density
            p.textAlign = Paint.Align.CENTER
            p.textSize = dp(14).toFloat()
            val labels = arrayOf("Abc", "আমি", "123")
            for (i in 0..2) {
                val base = if (i == 2) draft.func else draft.key
                p.style = Paint.Style.FILL
                p.color = draft.withKeyAlpha(base)
                val l = pad + i * (kw + gap)
                rect.set(l, dp(6).toFloat(), l + kw, dp(6).toFloat() + kh)
                canvas.drawRoundRect(rect, rad, rad, p)
                p.color = draft.text
                canvas.drawText(labels[i], l + kw / 2f, dp(6).toFloat() + kh / 2f + dp(5).toFloat(), p)
            }
        }
    }

    fun build(): View? = try { if (!loaded) reset(); make() } catch (_: Throwable) { null }

    private fun make(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        val pv = Preview(ctx)
        val plp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46))
        plp.setMargins(dp(3), dp(2), dp(3), dp(2))
        pv.layoutParams = plp
        root.addView(pv)

        // which colour is edited
        val fr = row()
        for (i in 0..4) fr.addView(btn((if (field == i) "✓ " else "") + fieldNames[i], 1f, 32) { field = i; hexBuf = currentHex(); confirmReset = false; refresh() })
        root.addView(fr)

        // hex line
        val shown = msg
        msg = ""
        val hl = row()
        val hv = TextView(ctx)
        hv.text = if (shown.isNotEmpty()) shown else if (field == 1 && !draft.hasBg2) "Bg 2: none (type a colour for a gradient)" else "#" + hexBuf + "▏"
        hv.textSize = 15f
        hv.setTextColor(Color.BLACK)
        hv.setPadding(dp(8), 0, dp(4), 0)
        hv.gravity = android.view.Gravity.CENTER_VERTICAL
        hv.layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f)
        hl.addView(hv)
        hl.addView(btn("⌫", 0f, 30) { if (hexBuf.isNotEmpty()) hexBuf = hexBuf.dropLast(1); confirmReset = false; refresh() }.also { it.setPadding(dp(14), 0, dp(14), 0) })
        if (field == 1) hl.addView(btn("None", 0f, 30) { draft.hasBg2 = false; hexBuf = ""; refresh() }.also { it.setPadding(dp(10), 0, dp(10), 0) })
        root.addView(hl)

        // hex keypad: 0-9 A-F (8 + 8)
        val digits = "0123456789ABCDEF"
        for (line in 0..1) {
            val r = row()
            for (k in 0..7) {
                val ch = digits[line * 8 + k]
                r.addView(btn(ch.toString(), 1f, 30) {
                    if (hexBuf.length < 6) hexBuf += ch
                    if (hexBuf.length == 6) { val v = CustomTheme.hex(hexBuf); if (v != null) setColor(v) }
                    confirmReset = false
                    refresh()
                })
            }
            root.addView(r)
        }

        // swatches
        val sr = row()
        for (c in swatches) {
            val v = View(ctx)
            v.setBackgroundColor(c)
            val lp = LinearLayout.LayoutParams(0, dp(26), 1f)
            lp.setMargins(dp(2), dp(3), dp(2), dp(3))
            v.layoutParams = lp
            v.setOnClickListener { try { host.feedback(v) } catch (_: Throwable) { }; setColor(c); hexBuf = currentHex(); confirmReset = false; refresh() }
            sr.addView(v)
        }
        root.addView(sr)

        // roundness + opacity
        val nr = row()
        nr.addView(btn("Round −", 1f, 30) { draft.round = CustomTheme.clamp(draft.round - 10, CustomTheme.ROUND_MIN, CustomTheme.ROUND_MAX); refresh() })
        nr.addView(btn(draft.round.toString(), 0.7f, 30) { })
        nr.addView(btn("+", 0.6f, 30) { draft.round = CustomTheme.clamp(draft.round + 10, CustomTheme.ROUND_MIN, CustomTheme.ROUND_MAX); refresh() })
        nr.addView(btn("Opacity −", 1.1f, 30) { draft.alpha = CustomTheme.clamp(draft.alpha - 10, CustomTheme.ALPHA_MIN, CustomTheme.ALPHA_MAX); refresh() })
        nr.addView(btn(draft.alpha.toString() + "%", 0.8f, 30) { })
        nr.addView(btn("+", 0.6f, 30) { draft.alpha = CustomTheme.clamp(draft.alpha + 10, CustomTheme.ALPHA_MIN, CustomTheme.ALPHA_MAX); refresh() })
        root.addView(nr)

        // ready colour sets
        val pr = row()
        for (i in CustomTheme.PRESET_NAMES.indices) {
            pr.addView(btn(CustomTheme.PRESET_NAMES[i], 1f, 30) {
                val keepRound = draft.round
                val keepAlpha = draft.alpha
                draft = CustomTheme.preset(i)
                draft.round = keepRound; draft.alpha = keepAlpha
                hexBuf = currentHex(); confirmReset = false
                refresh()
            })
        }
        root.addView(pr)

        val note = TextView(ctx)
        note.text = "Round / Opacity show with Look = Jabra. Save makes it theme 20 (picker)."
        note.textSize = 11f
        note.setTextColor(Color.DKGRAY)
        note.setPadding(dp(6), dp(1), dp(6), dp(1))
        root.addView(note)

        // actions
        val ar = row()
        ar.addView(btn("< Themes", 1f, 38) { loaded = false; host.showPanel("themes") })
        ar.addView(btn(if (confirmReset) "Sure? Delete" else "Reset", 1f, 38) {
            if (!confirmReset) { confirmReset = true; msg = "Tap again: deletes the custom theme"; refresh() }
            else {
                prefs.setStr("ct_json", "")
                if (prefs.int("theme", 0) == JabraThemes.ALL.size) prefs.setInt("theme", 0)
                draft = CustomTheme.defaults(); field = 0; hexBuf = currentHex(); confirmReset = false
                msg = "Custom theme deleted"
                refresh()
            }
        })
        ar.addView(btn("Save", 1f, 38) {
            prefs.setStr("ct_json", draft.toJson())
            prefs.setInt("theme", JabraThemes.ALL.size)
            confirmReset = false
            msg = "Saved. Custom theme is ON (tap ABC)"
            refresh()
        })
        ar.addView(btn("ABC", 0.8f, 38) { loaded = false; host.showLetters() })
        root.addView(ar)
        return root
    }
}
