package com.mgb.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.probhat.ProbhatLayout
import com.mgb.keyboard.typing.BottomPlan
import com.mgb.keyboard.typing.EdgePlan
import com.mgb.keyboard.typing.RowGap
import com.mgb.keyboard.typing.SizePct

/**
 * v0.10.25: link between the settings page (SettingsGroups) and the live preview card (PreviewCard).
 * changed = settings page tells the preview "a setting changed, redraw".
 * refreshRows = preview tells the settings page "I changed the theme, redraw your row labels".
 */
class PreviewLink {
    var changed: (() -> Unit)? = null
    var refreshRows: (() -> Unit)? = null
}

/**
 * v0.10.25: a small picture of the keyboard drawn from the SAME prefs and the SAME style helpers as the real keyboard
 * (JabraThemes.paint / style, JabraLook.shape, JabraIcons.putThemed, SizePct, RowGap). Not touchable, nothing is typed.
 * Heights are scaled (SCALE) so the card is not too tall; widths are real. Never throws: on any problem an empty view.
 * Not shown on purpose: toolbar / suggestion row, long-press hint letters, globe / voice / emoji keys.
 */
object KeyboardPreview {
    const val SCALE = 0.62f

    fun build(ctx: Context, p: JabraPrefs, probhat: Boolean): View = try { make(ctx, p, probhat) } catch (_: Throwable) { View(ctx) }

    private fun make(ctx: Context, p: JabraPrefs, probhat: Boolean): View {
        val d = ctx.resources.displayMetrics.density
        fun px(dpv: Float): Int = Math.max(1, Math.round(dpv * d))

        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFFDDDDDD.toInt())
        try { JabraThemes.paint(p, root) } catch (_: Throwable) { }
        val edgeDp = EdgePlan.edgeDp(p.int("edge_dp", -1))   // v0.10.26: same edge distance as the real keyboard (real dp, widths are real)
        root.setPadding(px(edgeDp.toFloat()), px(4f * SCALE), px(edgeDp.toFloat()), px(4f * SCALE))

        val keyH = p.keyHeightDp(false) * SCALE
        val gapRealDp = SizePct.gapDp(p.int("key_gap", -1))
        val gap = gapRealDp * SCALE   // heights are scaled
        val gapH = gapRealDp.toFloat()   // v0.10.26: left-right gap is a real width, not scaled (the old picture drew it too narrow)
        val gapV = EdgePlan.vGapDp(p.int("row_vgap", -1), gapRealDp) * SCALE   // v0.10.26: up-down gap (unset = key gap = old)
        val lpct = p.int("letter_pct", -1)
        val letterSp = (if (SizePct.letterValid(lpct)) SizePct.letterSp(lpct) else 14f) * 0.8f
        val bold = p.bool("letter_bold", false)

        // kind: 0 = normal key, 1 = function key, 2 = space (pill)
        fun key(label: String, weight: Float, kind: Int, h: Float = keyH, sp: Float = 12f): Button {
            val b = Button(ctx)
            b.text = label
            b.isAllCaps = false
            b.minWidth = 0; b.minimumWidth = 0; b.minHeight = 0; b.minimumHeight = 0
            b.setPadding(0, 0, 0, 0)
            b.isClickable = false
            b.isFocusable = false
            b.textSize = sp
            try { JabraThemes.style(p, b) } catch (_: Throwable) { }
            try { if (kind == 1) JabraLook.shape(p, b, JabraLook.FUNC) else if (kind == 2) JabraLook.shape(p, b, JabraLook.PILL) } catch (_: Throwable) { }
            if (bold && kind == 0) b.setTypeface(b.typeface, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(0, px(h), weight)
            lp.setMargins(px(gapH), px(gapV), px(gapH), px(gapV))
            b.layoutParams = lp
            return b
        }

        fun row(): LinearLayout { val r = LinearLayout(ctx); r.orientation = LinearLayout.HORIZONTAL; return r }

        fun letter(r: LinearLayout, c: Char) {
            val t = if (probhat) ProbhatLayout.label(c) else c.toString()
            r.addView(key(t, 1f, 0, keyH, letterSp))
        }
        fun extras(r: LinearLayout, list: Array<out Array<out String>>) {
            if (!probhat) return
            for (pair in list) r.addView(key(ProbhatLayout.extraLabel(pair[0]), 1f, 0, keyH, letterSp))
        }
        fun spacer(w: Float): View {
            val v = View(ctx)
            v.layoutParams = LinearLayout.LayoutParams(0, px(keyH), w)
            return v
        }

        // v0.17.8 (K6a): with the custom keyboard ON the number row, letter rows and bottom row are drawn from the saved layout (layer of the preview language); any problem = null = the classic rows below
        val customRows: List<LinearLayout>? = try {
            if (p.int("kb_custom", 0) != 1) null else LayoutStore.load(ctx, p)?.let { mdl ->
                val layerId = if (probhat) "pb" else "en"
                val rowsOut = mutableListOf<LinearLayout>()
                for (rd in mdl.effectiveRows(layerId)) {
                    val cr = row()
                    val rh = keyH * rd.heightPct / 100f
                    for (k in rd.keys) {
                        if (k.kind == com.mgb.keyboard.layout.KeyDef.SPACER) { cr.addView(spacer(k.weight)); continue }
                        val isAct = k.kind == com.mgb.keyboard.layout.KeyDef.ACTION
                        val kind = if (isAct) (if (k.action == "space") 2 else 1) else 0
                        val b = key(com.mgb.keyboard.typing.LayoutEditPlan.shownLabel(k, false), k.weight, kind, rh, if (isAct) 12f else letterSp)
                        if (isAct) { try { JabraIcons.putThemed(ctx, b, when (k.action) { "shift" -> "shift"; "backspace" -> "back"; "enter" -> "enter"; "globe" -> "globe"; "voice" -> "mic"; "emoji" -> "emoji"; "tab" -> "tab"; "clip" -> "clip"; else -> "" }) } catch (_: Throwable) { } }
                        cr.addView(b)
                    }
                    rowsOut.add(cr)
                }
                if (rowsOut.isEmpty()) null else rowsOut
            }
        } catch (_: Throwable) { null }

        // number row
        if (customRows == null && p.bool("numrow", true)) {
            val nr = row()
            val large = p.bool("numrow_large", false)
            val h0 = if (large) keyH + 12f * SCALE else keyH
            val h = try { h0 * com.mgb.keyboard.typing.NumRowPlan.sanitizePct(p.int("nr_hpct", 100)) / 100f } catch (_: Throwable) { h0 }   // v0.16.7 (X7): height % (100 = same)
            val nrMode = try { com.mgb.keyboard.typing.NumRowPlan.sanitizeMode(p.int("nr_mode", 0)) } catch (_: Throwable) { 0 }
            if (nrMode != 0) {   // v0.16.7 (X7): digits + symbols (label only) / own characters
                val labs = com.mgb.keyboard.typing.NumRowPlan.labels(nrMode, p.str("nr_chars", ""))
                labs.forEach { nr.addView(key(it, 1f, 0, h, if (large) 18f * 0.8f else letterSp)) }
            } else {
            val digits = if (p.bool("numrow_bn", false)) "১২৩৪৫৬৭৮৯০" else "1234567890"
            digits.forEach { nr.addView(key(it.toString(), 1f, 0, h, if (large) 18f * 0.8f else letterSp)) }
            }
            root.addView(nr)
        }

        // letter rows (same structure as MgbIme: Probhat adds extra keys INSIDE the rows)
        val r1 = row()
        "qwertyuiop".forEach { letter(r1, it) }
        extras(r1, ProbhatLayout.EXTRA_ROW1)
        if (customRows == null) root.addView(r1)

        val r2 = row()
        val rowGapOn = p.bool("row_gap", true)
        val pbGap = BottomPlan.pbGap(probhat, p.bool("pb_home_gap", false), rowGapOn)   // v0.10.27: Probhat home row gap (left gap + half width extra key)
        val side = if (pbGap) 0.5f else RowGap.side(rowGapOn, RowGap.homeKeys(probhat))
        if (side > 0f) r2.addView(spacer(side))
        "asdfghjkl".forEach { letter(r2, it) }
        if (pbGap) { for (pair in ProbhatLayout.EXTRA_ROW2) r2.addView(key(ProbhatLayout.extraLabel(pair[0]), 0.5f, 0, keyH, letterSp)) }
        else extras(r2, ProbhatLayout.EXTRA_ROW2)
        if (side > 0f && !pbGap) r2.addView(spacer(side))
        if (customRows == null) root.addView(r2)

        val r3 = row()
        val sh = key("\u21E7", 1.5f, 1)
        try { JabraIcons.putThemed(ctx, sh, "shift") } catch (_: Throwable) { }
        r3.addView(sh)
        "zxcvbnm".forEach { letter(r3, it) }
        extras(r3, ProbhatLayout.EXTRA_ROW3)
        val bk = key("\u232B", 1.5f, 1)
        try { JabraIcons.putThemed(ctx, bk, "back") } catch (_: Throwable) { }
        r3.addView(bk)
        try {   // v0.10.26: gap beside Shift / Backspace, same rule as the real keyboard
            val fg = EdgePlan.fnGapDp(p.int("fn_gap", -1))
            if (fg > 0) {
                val sdp = ctx.resources.displayMetrics.widthPixels / d
                val fw = EdgePlan.fnWeight(fg, sdp, edgeDp)
                (sh.layoutParams as LinearLayout.LayoutParams).let { it.weight = fw; it.rightMargin += px(fg.toFloat()) }
                (bk.layoutParams as LinearLayout.LayoutParams).let { it.weight = fw; it.leftMargin += px(fg.toFloat()) }
            }
        } catch (_: Throwable) { }
        if (customRows == null) root.addView(r3)

        val r4 = row()
        val bst = BottomPlan.style(p.int("bot_style", 0))   // v0.10.27
        val qmShown = try { p.bool("key_qm", false) } catch (_: Throwable) { false }   // v0.17.1 (K1)
        val hB = try { keyH * com.mgb.keyboard.typing.SpacePlan.sanitizeH(p.int("sp_hpct", 100)) / 100f } catch (_: Throwable) { keyH }   // v0.17.2 (K2): bottom row height % (100 = same)
        r4.addView(key("?123", BottomPlan.symWeight(bst), 1, hB))
        r4.addView(key(if (probhat) com.mgb.keyboard.typing.LangNames.keyLabel(true) else "EN", BottomPlan.langWeight(bst), 1, hB))
        r4.addView(key(",", 1f, 0, hB))
        val spaceLabel = if (p.bool("space_lang", false)) (if (probhat) com.mgb.keyboard.typing.LangNames.name(true) else "English") else "space"
        val spOthers = BottomPlan.symWeight(bst) + BottomPlan.langWeight(bst) + BottomPlan.enterWeight(bst) + (if (qmShown) 3f else 2f)
        val spBase = BottomPlan.spaceWeight(bst, spOthers, qmShown)
        val spW = try { com.mgb.keyboard.typing.SpacePlan.spaceWeight(spBase, p.int("sp_wpct", 100), spOthers, 1f, ctx.resources.displayMetrics.widthPixels / d - 2f * edgeDp) } catch (_: Throwable) { spBase }   // v0.17.2 (K2): same width rule as the real keyboard
        r4.addView(key(spaceLabel, spW, 2, hB))
        if (qmShown) r4.addView(key(if (probhat) "\u0964" else "?", 1f, 0, hB))   // v0.17.1 (K1): follows the setting key_qm (default hidden)
        r4.addView(key(com.mgb.keyboard.layout.LayoutRoute.dotLabel(com.mgb.keyboard.layout.LayoutRoute.dandaDot(p.bool("danda_key", com.mgb.keyboard.layout.LayoutRoute.DANDA_DEF), qmShown), probhat), 1f, 0, hB))   // v0.17.18 (L1)
        val en = key("\u23CE", BottomPlan.enterWeight(bst), 1, hB)
        try { JabraIcons.putThemed(ctx, en, "enter") } catch (_: Throwable) { }
        r4.addView(en)
        if (customRows == null) root.addView(r4)
        customRows?.forEach { root.addView(it) }   // v0.17.8 (K6a)

        // one-hand: same width rule as the real keyboard (needs a FrameLayout parent)
        val wrap = FrameLayout(ctx)
        wrap.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        try { JabraOneHand.apply(ctx, p, root) } catch (_: Throwable) { }
        wrap.addView(root)
        return wrap
    }
}

/** v0.10.25: one theme tile (background, 3 mini keys, name). Drawn in code, same colours as the real theme. */
internal class ThemeTile(c: Context, private val p: JabraPrefs, val idx: Int, var chosen: Boolean) : View(c) {   // v0.15.6 (N6): internal (reused by AppThemeGrid)
    var ring: Int = 0xFF2271B1.toInt()   // v0.15.6: frame colour of the chosen tile (default = as before)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(cv: Canvas) {
        try {
            val d = resources.displayMetrics.density
            val w = width.toFloat()
            val h = height.toFloat()
            val t: JabraThemes.T = if (idx == JabraThemes.ALL.size) (JabraThemes.customT(p) ?: JabraThemes.ALL[0]) else JabraThemes.ALL[idx]
            rect.set(2f * d, 2f * d, w - 2f * d, h - 2f * d)
            val g2 = t.bg2
            if (g2 != null) paint.shader = LinearGradient(0f, 0f, w, h, t.bg, g2, Shader.TileMode.CLAMP) else { paint.shader = null; paint.color = t.bg }
            paint.style = Paint.Style.FILL
            cv.drawRoundRect(rect, 8f * d, 8f * d, paint)
            paint.shader = null
            // mini keys: 2 normal + 1 function key
            val keyCol = t.key ?: 0xFFFFFFFF.toInt()
            val fnCol = try { JabraThemes.funcFillAt(p, idx) } catch (_: Throwable) { keyCol }
            val kw = (w - 4f * d - 5f * d * 4f) / 3f
            val kh = 14f * d
            val top = 7f * d
            for (i in 0 until 3) {
                val l = 2f * d + 5f * d + i * (kw + 5f * d)
                rect.set(l, top, l + kw, top + kh)
                paint.color = if (i == 2) fnCol else keyCol
                cv.drawRoundRect(rect, 4f * d, 4f * d, paint)
            }
            // name in the theme text colour
            paint.color = t.text ?: 0xFF222222.toInt()
            paint.textSize = 10f * d
            paint.textAlign = Paint.Align.CENTER
            val nm = if (idx == JabraThemes.ALL.size) "Custom" else t.name
            cv.drawText(nm, w / 2f, h - 7f * d, paint)
            if (chosen) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f * d
                paint.color = ring
                rect.set(1.5f * d, 1.5f * d, w - 1.5f * d, h - 1.5f * d)
                cv.drawRoundRect(rect, 8f * d, 8f * d, paint)
                paint.style = Paint.Style.FILL
            }
        } catch (_: Throwable) { }
    }
}

/**
 * v0.10.25: the card at the TOP of the app screen (outside the scroll area, so it stays visible while you change settings):
 * title + [EN / বাংলা] + [view mode], the live keyboard picture, and a strip of theme tiles (tap = choose that theme).
 * Pref pv_mode: 0 = picture + themes (default), 1 = picture only, 2 = hidden (only the title row stays).
 */
object PreviewCard {

    fun build(ctx: Context, link: PreviewLink): View {
        val p = JabraPrefs(ctx)
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * d).toInt()

        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(8), dp(6), dp(8), dp(6))
        try { card.background = AppUi.cardBg(ctx, 12) } catch (_: Throwable) { }
        val clp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        clp.setMargins(dp(8), dp(6), dp(8), dp(2))
        card.layoutParams = clp

        var pro = false   // preview language: false = English / Avro, true = Probhat (only the picture, no setting is changed)
        var mode = p.int("pv_mode", 0).coerceIn(0, 2)

        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(ctx)
        title.text = "Live preview"
        title.textSize = 14f
        title.setTypeface(title.typeface, Typeface.BOLD)
        try { title.setTextColor(AppUi.text(ctx)) } catch (_: Throwable) { }
        head.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        fun small(label: String, onClick: (Button) -> Unit): Button {
            val b = Button(ctx)
            b.text = label
            b.isAllCaps = false
            b.textSize = 11f
            b.minWidth = 0; b.minimumWidth = 0; b.minHeight = 0; b.minimumHeight = 0
            b.setPadding(dp(10), dp(4), dp(10), dp(4))
            b.setOnClickListener { try { onClick(b) } catch (_: Throwable) { } }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32))
            lp.leftMargin = dp(6)
            b.layoutParams = lp
            return b
        }

        val holder = FrameLayout(ctx)
        val strip = HorizontalScrollView(ctx)
        strip.isHorizontalScrollBarEnabled = false
        val stripRow = LinearLayout(ctx)
        stripRow.orientation = LinearLayout.HORIZONTAL
        strip.addView(stripRow)
        val tiles = ArrayList<ThemeTile>()

        fun redraw() {
            try {
                holder.removeAllViews()
                holder.addView(KeyboardPreview.build(ctx, JabraPrefs(ctx), pro))
                val cur = JabraThemes.index(p)
                for (t in tiles) { val on = t.idx == cur; if (t.chosen != on) { t.chosen = on; t.invalidate() } }
            } catch (_: Throwable) { }
        }

        fun applyMode(langBtn: Button, modeBtn: Button) {
            holder.visibility = if (mode == 2) View.GONE else View.VISIBLE
            strip.visibility = if (mode == 0) View.VISIBLE else View.GONE
            langBtn.visibility = if (mode == 2) View.GONE else View.VISIBLE
            modeBtn.text = if (mode == 0) "Themes: ON" else if (mode == 1) "Keys only" else "Show"
        }

        val langBtn = small("EN") { }
        val modeBtn = small("") { }
        langBtn.setOnClickListener { try { pro = !pro; langBtn.text = if (pro) com.mgb.keyboard.typing.LangNames.PROBHAT else "EN"; redraw() } catch (_: Throwable) { } }
        modeBtn.setOnClickListener { try { mode = (mode + 1) % 3; p.setInt("pv_mode", mode); applyMode(langBtn, modeBtn) } catch (_: Throwable) { } }
        head.addView(langBtn)
        head.addView(modeBtn)
        card.addView(head)
        card.addView(holder)

        // theme tiles (20 built-in + the custom one when saved)
        val n = try { JabraThemes.count(p) } catch (_: Throwable) { JabraThemes.ALL.size }
        val cur0 = JabraThemes.index(p)
        for (i in 0 until n) {
            val t = ThemeTile(ctx, p, i, i == cur0)
            val lp = LinearLayout.LayoutParams(dp(84), dp(48))
            lp.setMargins(dp(2), dp(4), dp(2), dp(2))
            t.layoutParams = lp
            t.setOnClickListener {
                try {
                    p.setInt("theme", i)
                    redraw()
                    link.refreshRows?.invoke()
                } catch (_: Throwable) { }
            }
            tiles.add(t)
            stripRow.addView(t)
        }
        card.addView(strip)
        try { strip.post { try { val c = tiles.firstOrNull { it.chosen }; if (c != null) strip.scrollTo(Math.max(0, c.left - dp(90)), 0) } catch (_: Throwable) { } } } catch (_: Throwable) { }

        link.changed = { redraw() }
        redraw()
        applyMode(langBtn, modeBtn)
        return card
    }
}
