package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView   // v0.16.12 (X12)
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.typing.PanelLayout   // v0.16.12 (X12)
import com.mgb.keyboard.typing.ToolbarOrder

/**
 * v0.2.7: hold a toolbar key for 1.5 seconds -> a small "Edit shortcut" bubble shows above it.
 * Tap the bubble -> the quick add / remove page (ToolbarQuickPanel, route "tbquick").
 * The touch listener returns false except after the long hold (then it eats the click, so the key does NOT also run its action).
 * A swipe on the bar sends CANCEL to the key, which cancels the hold. Everything is wrapped in try/catch: on any problem the key just works as before.
 */
object ToolbarEdit {

    const val HOLD_MS = 1500L
    private const val AUTO_HIDE_MS = 4000L

    private var pw: PopupWindow? = null
    private val h = Handler(Looper.getMainLooper())
    private val autoHide = Runnable { dismiss() }

    fun dismiss() {
        try { h.removeCallbacks(autoHide) } catch (_: Throwable) { }
        try { pw?.dismiss() } catch (_: Throwable) { }
        pw = null
    }

    fun attach(b: Button, prefs: JabraPrefs, tip: String? = null, onEdit: () -> Unit) {   // v0.8.8: tip = name shown after 0.3 s (null = no tip, as before)
        var fired = false
        var tipFired = false
        val tr = Runnable { tipFired = true; ToolTip.show(b, prefs, tip) }
        val r = Runnable {
            fired = true
            ToolTip.dismiss()   // v0.8.8: the Edit bubble replaces the name tip
            try { b.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) } catch (_: Throwable) { }
            try { show(b, prefs, onEdit) } catch (_: Throwable) { dismiss() }
        }
        b.setOnTouchListener { view, ev ->
            try {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        fired = false
                        tipFired = false
                        dismiss()
                        ToolTip.dismiss()
                        view.removeCallbacks(r)
                        view.postDelayed(r, HOLD_MS)
                        view.removeCallbacks(tr)
                        if (tip != null && ToolTip.enabled(prefs)) view.postDelayed(tr, com.mgb.keyboard.typing.TipPlace.HOLD_MS)   // v0.8.8
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (ev.x < 0f || ev.y < 0f || ev.x > view.width || ev.y > view.height) { view.removeCallbacks(r); view.removeCallbacks(tr); ToolTip.dismiss() }
                        false
                    }
                    MotionEvent.ACTION_UP -> {
                        view.removeCallbacks(r)
                        view.removeCallbacks(tr)
                        ToolTip.dismiss()
                        if (fired) { fired = false; tipFired = false; view.isPressed = false; true }
                        else if (tipFired) { tipFired = false; view.isPressed = false; true }   // v0.8.8: a held key only shows its name, it does not press
                        else false
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        view.removeCallbacks(r)
                        view.removeCallbacks(tr)
                        ToolTip.dismiss()
                        tipFired = false
                        false
                    }
                    else -> false
                }
            } catch (_: Throwable) {
                false
            }
        }
        b.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { }
            override fun onViewDetachedFromWindow(v: View) { try { v.removeCallbacks(r); v.removeCallbacks(tr) } catch (_: Throwable) { }; dismiss(); ToolTip.dismiss() }
        })
    }

    private fun show(b: Button, prefs: JabraPrefs, onEdit: () -> Unit) {
        dismiss()
        val d = b.resources.displayMetrics.density
        val t = TextView(b.context)
        t.text = "✎  Edit shortcut"
        Glyphs.dress(t)   // v0.17.72 (S5)
        t.textSize = 15f
        t.gravity = Gravity.CENTER
        t.setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
        val lookOn = try { JabraLook.on(prefs) } catch (_: Throwable) { false }
        t.setTextColor(try { JabraThemes.iconColor(prefs) } catch (_: Throwable) { Color.parseColor("#222222") })
        val bg = GradientDrawable()
        bg.setColor(if (lookOn) (try { JabraLook.fill(prefs) } catch (_: Throwable) { Color.parseColor("#FFFFFF") }) else Color.parseColor("#FFFFFF"))
        bg.setStroke((1 * d).toInt().coerceAtLeast(1), Color.parseColor("#888888"))
        bg.cornerRadius = 14 * d
        t.background = bg
        t.setOnClickListener {
            dismiss()
            try { onEdit() } catch (_: Throwable) { }
        }
        t.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val w = t.measuredWidth
        val hh = t.measuredHeight
        val p = PopupWindow(t, w, hh, false)
        p.isTouchable = true
        p.isFocusable = false
        ToolTip.inScreen(p)   // v0.10.4: "Edit shortcut" above the icon, not at the bottom
        val loc = IntArray(2)
        b.getLocationOnScreen(loc)
        val screenW = b.resources.displayMetrics.widthPixels
        val x = (loc[0] + b.width / 2 - w / 2).coerceIn(0, (screenW - w).coerceAtLeast(0))
        val y = (loc[1] - hh - (6 * d).toInt()).coerceAtLeast(0)
        p.showAtLocation(b, Gravity.NO_GRAVITY, x, y)
        pw = p
        h.postDelayed(autoHide, AUTO_HIDE_MS)
    }
}

/**
 * v0.2.7 quick add / remove page (route showPanel("tbquick")). Opened only from the "Edit shortcut" bubble.
 * A grid of ALL toolbar items (icon + name). Highlighted frame + check mark = on the toolbar. Tap = add / remove at once (saved at once).
 * Uses the same prefs as the old Edit toolbar page: tb_hidden (normal items), tb_on (extra items). Adding Formatter / Mode also turns on fmt_on / mode_key.
 * "Order >" opens the old Edit toolbar page (move First / up / down). "Done" = back to letters (the bar is rebuilt there).
 */
class ToolbarQuickPanel(private val ctx: Context, private val host: JabraHost) {

    private val prefs = JabraPrefs(ctx)
    private var scrollY = 0

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    // v0.10.15 (D2.5): pk() = UiKit.btn (given height, 11sp, same 2dp margins, theme style, feedback before click). Old body kept as pkOld() = fallback.
    private fun pk(label: String, weight: Float, heightDp: Int, onClick: (Button) -> Unit): Button {
        try { return UiKit.btn(ctx, prefs, label, weight, heightDp, 11f, { v -> host.feedback(v) }, onClick) } catch (_: Throwable) { }
        return pkOld(label, weight, heightDp, onClick)
    }

    private fun pkOld(label: String, weight: Float, heightDp: Int, onClick: (Button) -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        b.textSize = 11f
        try { JabraThemes.style(prefs, b) } catch (_: Throwable) { }
        b.setOnClickListener {
            try { host.feedback(b) } catch (_: Throwable) { }
            try { onClick(b) } catch (_: Throwable) { }
        }
        val lp = LinearLayout.LayoutParams(0, dp(heightDp), weight)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        b.layoutParams = lp
        return b
    }

    // v0.10.15 (D2.5): row() = UiKit.row (same horizontal LinearLayout). Old body kept as rowOld() = automatic fallback.
    private fun row(): LinearLayout {
        try { return UiKit.row(ctx) } catch (_: Throwable) { }
        return rowOld()
    }

    private fun rowOld(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    /** True when the item is really visible on the bar (also respects the old Formatter / Mode key settings). */
    private fun isOn(id: String): Boolean {
        if (ToolbarOrder.off(prefs.str("tb_hidden", ""), prefs.str("tb_on", ""), id)) return false
        if (id == "fmt" && !prefs.bool("fmt_on", true)) return false
        if (id == "mode" && !prefs.bool("mode_key", true)) return false
        return true
    }

    private fun toggle(id: String, sv: ScrollView?) {
        val hidden = prefs.str("tb_hidden", "")
        val on = prefs.str("tb_on", "")
        val turnOn = !isOn(id)
        if (ToolbarOrder.isExtra(id)) prefs.setStr("tb_on", ToolbarOrder.setOn(on, id, turnOn))
        else prefs.setStr("tb_hidden", ToolbarOrder.setHidden(hidden, id, !turnOn))
        if (turnOn) {
            if (id == "fmt") prefs.setBool("fmt_on", true)
            if (id == "mode") prefs.setBool("mode_key", true)
        }
        try { if (sv != null) scrollY = sv.scrollY } catch (_: Throwable) { }
        host.showPanel("tbquick")
    }

    /** Returns null on any problem -> keyboard shows normal letters. */
    fun build(): View? = try { make() } catch (_: Throwable) { null }

    private fun make(): View {
        // v0.16.12 (X12): pref panel_ui = 1 -> header + preview strip of the real bar (makeNew below). Default 0 / any problem = the classic page that follows, untouched.
        try { if (PanelLayout.isNewStyle(prefs.int("panel_ui", 0))) { val nv = makeNew(); if (nv != null) return nv } } catch (_: Throwable) { }
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        try { JabraThemes.paint(prefs, root) } catch (_: Throwable) { }
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        val iconColor = try { JabraThemes.iconColor(prefs) } catch (_: Throwable) { Color.parseColor("#222222") }
        val noteColor = try { JabraThemes.noteColor(prefs) } catch (_: Throwable) { Color.parseColor("#555555") }

        val head = TextView(ctx)
        head.text = "Edit shortcuts: tap an icon to add / remove it on the toolbar"
        head.textSize = 12f
        head.setTextColor(noteColor)
        head.setPadding(dp(6), dp(2), dp(6), dp(4))
        root.addView(head)

        val sv = ScrollView(ctx)
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val ids = ToolbarOrder.parse(prefs.str("tb_order", ""))
        val cols = 4
        var i = 0
        while (i < ids.size) {
            val r = row()
            for (c in 0 until cols) {
                val idx = i + c
                if (idx < ids.size) r.addView(chip(ids[idx], sv, iconColor)) else r.addView(spacer())
            }
            col.addView(r)
            i += cols
        }
        sv.addView(col)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3 * 74))
        root.addView(sv)
        sv.post { try { sv.scrollTo(0, scrollY) } catch (_: Throwable) { } }

        val rb = row()
        rb.addView(pk("Order  >", 1f, 40) { scrollY = 0; host.showPanel("toolbar") })
        rb.addView(pk("Done", 1f, 40) { scrollY = 0; host.showLetters() })
        root.addView(rb)
        return root
    }

    // ===================== v0.16.12 (X12): NEW quick toolbar page (pref panel_ui = 1) =====================
    private fun makeNew(): View? {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        try { JabraThemes.paint(prefs, root) } catch (_: Throwable) { }
        root.setPadding(dp(2), dp(4), dp(2), dp(4))
        val iconColor = try { JabraThemes.iconColor(prefs) } catch (_: Throwable) { Color.parseColor("#222222") }

        root.addView(PanelKit.header(ctx, prefs, host, "Edit shortcuts", "<", listOf(Pair("Order >") { scrollY = 0; host.showPanel("toolbar") })) { scrollY = 0; host.showLetters() })
        root.addView(ToolbarPreview.strip(ctx, prefs))
        root.addView(PanelKit.sectionLabel(ctx, prefs, "Tap an icon to add / remove it"))

        val sv = ScrollView(ctx)
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val ids = ToolbarOrder.parse(prefs.str("tb_order", ""))
        var i = 0
        while (i < ids.size) {
            val r = row()
            for (c in 0 until 4) { val idx = i + c; if (idx < ids.size) r.addView(chip(ids[idx], sv, iconColor)) else r.addView(spacer()) }
            col.addView(r)
            i += 4
        }
        sv.addView(col)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2 * 74 + 37))
        root.addView(sv)
        sv.post { try { sv.scrollTo(0, scrollY) } catch (_: Throwable) { } }

        root.addView(PanelKit.stickyBar(ctx, prefs, host, listOf(Pair("Done") { scrollY = 0; host.showLetters() })))
        return root
    }

    private fun spacer(): View {
        val v = View(ctx)
        v.layoutParams = LinearLayout.LayoutParams(0, dp(70), 1f)
        return v
    }

    private fun chip(id: String, sv: ScrollView, iconColor: Int): View {
        val on = isOn(id)
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER_HORIZONTAL
        val lp = LinearLayout.LayoutParams(0, dp(70), 1f)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        c.layoutParams = lp
        c.setPadding(dp(2), dp(3), dp(2), dp(2))
        val bg = GradientDrawable()
        bg.cornerRadius = dp(8).toFloat()
        if (on) bg.setStroke(dp(2), iconColor)
        c.background = bg

        val ic = Button(ctx)
        ic.isAllCaps = false
        ic.minWidth = 0
        ic.minimumWidth = 0
        ic.setPadding(0, 0, 0, 0)
        ic.textSize = 12f
        ic.text = Toolbar.label(id)
        try { JabraThemes.style(prefs, ic) } catch (_: Throwable) { }
        try { JabraIcons.putThemed(ctx, ic, Toolbar.icon(id)) } catch (_: Throwable) { }
        ic.isClickable = false
        ic.isFocusable = false
        ic.alpha = if (on) 1f else 0.45f
        ic.layoutParams = LinearLayout.LayoutParams(dp(44), dp(34))
        c.addView(ic)

        val nm = TextView(ctx)
        nm.text = (if (on) "✓ " else "+ ") + Toolbar.title(id)
        Glyphs.dress(nm)   // v0.17.72 (S5): tick drawn; "+ " stays text
        nm.textSize = 9f
        nm.gravity = Gravity.CENTER
        nm.maxLines = 2
        nm.setTextColor(iconColor)
        nm.alpha = if (on) 1f else 0.6f
        nm.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        c.addView(nm)

        c.setOnClickListener {
            try { host.feedback(c) } catch (_: Throwable) { }
            try { toggle(id, sv) } catch (_: Throwable) { }
        }
        return c
    }
}

/**
 * v0.16.12 (X12): preview strip = the REAL toolbar order as small icons (same prefs tb_order / tb_hidden / tb_on; Formatter / Mode also need fmt_on / mode_key).
 * Shared by the New "Edit toolbar" page and the New quick page. Never throws: a failure gives an empty row.
 */
object ToolbarPreview {
    /** Ids that really show on the bar, in bar order. */
    fun ids(prefs: JabraPrefs): List<String> {
        val out = ArrayList<String>()
        try {
            for (id in ToolbarOrder.visible(prefs.str("tb_order", ""), prefs.str("tb_hidden", ""), prefs.str("tb_on", ""))) {
                if (id == "fmt" && !prefs.bool("fmt_on", true)) continue
                if (id == "mode" && !prefs.bool("mode_key", true)) continue
                out.add(id)
            }
        } catch (_: Throwable) { }
        return out
    }

    fun strip(ctx: Context, prefs: JabraPrefs): View {
        val hs = HorizontalScrollView(ctx)
        try {
            val d = ctx.resources.displayMetrics.density
            hs.isHorizontalScrollBarEnabled = false
            val r = LinearLayout(ctx)
            r.orientation = LinearLayout.HORIZONTAL
            r.setPadding((4 * d).toInt(), (2 * d).toInt(), (4 * d).toInt(), (2 * d).toInt())
            for (id in ids(prefs)) {
                val b = Button(ctx)
                b.isAllCaps = false; b.minWidth = 0; b.minimumWidth = 0; b.minHeight = 0; b.minimumHeight = 0
                b.setPadding(0, 0, 0, 0); b.textSize = 10f
                b.text = Toolbar.label(id)
                try { JabraThemes.style(prefs, b) } catch (_: Throwable) { }
                try { JabraIcons.putThemed(ctx, b, Toolbar.icon(id)) } catch (_: Throwable) { }
                b.isClickable = false; b.isFocusable = false
                val lp = LinearLayout.LayoutParams((38 * d).toInt(), (34 * d).toInt())
                lp.setMargins((2 * d).toInt(), (2 * d).toInt(), (2 * d).toInt(), (2 * d).toInt())
                r.addView(b, lp)
            }
            hs.addView(r)
            hs.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (40 * d).toInt())
        } catch (_: Throwable) { }
        return hs
    }
}
