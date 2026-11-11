package com.mgb.keyboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.TextView

/**
 * v0.1.3 "Popup on keypress": while a letter key is held, a small bubble with its label shows above the key.
 * Setting key_popup (default OFF). Read at touch time, so no rebuild needed. The touch listener always returns false,
 * so the normal click / haptic / typing code of the key is not changed. Every call is wrapped in try/catch.
 *
 * v0.9.0 (R1): the bubble is built ONCE (TextView + GradientDrawable + PopupWindow) and reused for every press: a press only sets the text
 * and moves the window (update). Release hides the bubble (INVISIBLE, window kept); the window is really removed after IDLE_MS without a press,
 * on invalidate() (keyboard rebuilt = theme / look may have changed) and when a key detaches. Any problem = the v0.8.9 code (showOld) runs instead.
 */
object KeyPopup {

    private const val IDLE_MS = 1500L
    private var pw: PopupWindow? = null          // old-style bubble (fallback only)
    private var cpw: PopupWindow? = null         // v0.9.0 reusable bubble window
    private var ct: TextView? = null             // v0.9.0 its text view (styled once)
    private val hnd = Handler(Looper.getMainLooper())
    private val idle = Runnable { dropCached() }
    private var owner: View? = null              // v0.9.6: the key (or anchor) whose bubble is on screen
    private var refLook = false                  // v0.9.6: cached pop_ref of the bubble that was built last
    private var host: FrameLayout? = null        // v0.9.9 (V4): the keyboard container that holds the overlay bubble
    private var ov: BubbleOverlay? = null        // v0.9.9: overlay bubble view (inside the container, no window call)
    private var ovStyled = false                 // v0.9.9: look set since the last keyboard rebuild
    private var ovRef = true                     // v0.9.9: pop_ref of the overlay look
    private val rects = com.mgb.keyboard.typing.BubblePlan.RectCache<View>()   // v0.9.9: container-relative key rects (cleared on any layout change)
    private var layoutHost: FrameLayout? = null  // v0.9.9: container that already has our layout listener

    /** v0.9.0: hide now (cheap). The reusable window stays for the next press and is dropped by the idle timer. */
    fun dismiss() {
        owner = null   // v0.9.6
        try { ov?.hideNow() } catch (_: Throwable) { }   // v0.9.9 (V4)
        try { pw?.dismiss() } catch (_: Throwable) { }; pw = null
        try {
            ct?.visibility = View.INVISIBLE
            if (cpw != null) { hnd.removeCallbacks(idle); hnd.postDelayed(idle, IDLE_MS) }
        } catch (_: Throwable) { dropCached() }
    }

    /** v0.9.0: forget the cached bubble (called when the keyboard is rebuilt so colours / look refresh on the next press). */
    fun invalidate() { dropCached(); ovStyled = false; try { rects.clear() } catch (_: Throwable) { } }   // v0.9.9: overlay look + rect cache refresh too

    private fun dropCached() {
        try { hnd.removeCallbacks(idle) } catch (_: Throwable) { }
        try { cpw?.dismiss() } catch (_: Throwable) { }
        cpw = null; ct = null
    }

    /** v0.9.2 (R3): hide the bubble now (same as dismiss; named for callers that are not key presses, e.g. the space swipe). */
    fun hide() { dismiss() }

    /** v0.9.2 (R3): show [text] above [anchor] with the same reusable bubble (no key_popup needed). Any problem = no bubble. */
    fun showText(anchor: View, text: String, prefs: JabraPrefs) {
        try {
            showFast(anchor, prefs, text)
        } catch (_: Throwable) {
            dropCached()
        }
    }

    /** v0.1.6: the touch handling, shared with HintKeys (one listener per key). */
    fun touch(v: View, e: MotionEvent, prefs: JabraPrefs) {
        try {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> if (KeyCfg.popupOn(prefs)) show(v as Button, prefs)   // v0.6.0: cached flag (fallback = pref read)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    if (com.mgb.keyboard.typing.PopupStyle.releaseHides(owner, v)) dismiss()   // v0.9.6: finger A lifting must not hide the bubble of finger B
            }
        } catch (_: Throwable) { dismiss() }
    }

    fun attach(b: Button, prefs: JabraPrefs) {
        b.setOnTouchListener { v, e ->
            touch(v, e, prefs)   // v0.1.6
            false
        }
        b.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { }
            override fun onViewDetachedFromWindow(v: View) { dismiss(); dropCached() }   // v0.9.0
        })
    }

    /** v0.9.0: reusable bubble. Same size / colours / position as showOld(); any failure falls back to showOld(). */
    private fun show(b: Button, prefs: JabraPrefs) {
        try {
            showFast(b, prefs)
        } catch (_: Throwable) {
            dropCached()
            try { showOld(b, prefs) } catch (_: Throwable) { dismiss() }
        }
    }

    private fun showFast(b: View, prefs: JabraPrefs, text: String? = null) {   // v0.9.2: View + optional text (key press passes none = button label)
        hnd.removeCallbacks(idle)
        val label = text ?: ((b as? Button)?.text?.toString() ?: "")
        if (text != null && owner === b && cpw?.isShowing == true && ct?.visibility == View.VISIBLE && ct?.text?.toString() == label) return   // v0.9.8 (V3): same text, same anchor = nothing to redraw (no flicker, no window call)
        if (label.isEmpty() || b.width <= 0 || b.height <= 0) { dismiss(); return }   // v0.9.6: key not laid out yet = no bubble (never a bubble at a stale / zero position)
        if (text == null && overlayShow(b, prefs, label)) return   // v0.9.9 (V4): rows with room above = overlay view (no PopupWindow.update); false = the v0.9.6 window path below
        try { ov?.hideNow() } catch (_: Throwable) { }   // v0.9.9: window path takes over: the overlay bubble must not stay
        val d = b.resources.displayMetrics.density
        var t = ct
        if (t == null || cpw == null) {   // first press (or after idle / rebuild): build + style ONCE
            dropCached()
            t = TextView(b.context)
            t.gravity = Gravity.CENTER
            val ref = try { prefs.bool("pop_ref", true) } catch (_: Throwable) { true }   // v0.9.6: reference-style look (OFF = v0.9.5 look exactly)
            refLook = ref
            val bg = GradientDrawable()
            if (ref) {
                val th = try { JabraThemes.cur(prefs) } catch (_: Throwable) { null }
                val key: Int? = th?.key
                val fill = com.mgb.keyboard.typing.PopupStyle.fill(key)
                t.setTextColor(com.mgb.keyboard.typing.PopupStyle.text(th?.text, fill))
                bg.setColor(fill)
                bg.setStroke((1 * d).toInt().coerceAtLeast(1), com.mgb.keyboard.typing.PopupStyle.stroke(key, fill))
                bg.cornerRadius = com.mgb.keyboard.typing.PopupStyle.radiusPx(d)
            } else {
                t.textSize = 26f
                val lookOn = try { JabraLook.on(prefs) } catch (_: Throwable) { false }
                t.setTextColor(if (lookOn) (try { JabraThemes.iconColor(prefs) } catch (_: Throwable) { Color.parseColor("#222222") }) else Color.parseColor("#222222"))
                bg.setColor(if (lookOn) (try { JabraLook.fill(prefs) } catch (_: Throwable) { Color.parseColor("#FFFFFF") }) else Color.parseColor("#FFFFFF"))
                bg.setStroke((1 * d).toInt().coerceAtLeast(1), Color.parseColor("#888888"))
                bg.cornerRadius = (if (lookOn) 16 else 10) * d
            }
            t.background = bg
            ct = t
        }
        val tv = ct ?: return
        val w: Int
        val h: Int
        if (refLook) {
            w = com.mgb.keyboard.typing.PopupStyle.width(b.width, d)
            h = com.mgb.keyboard.typing.PopupStyle.height(b.height, d)
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, com.mgb.keyboard.typing.PopupStyle.textSp(b.height, d))
        } else {
            w = (b.width + 12 * d).toInt().coerceAtLeast((44 * d).toInt())
            h = (b.height * 1.25f).toInt().coerceAtLeast((52 * d).toInt())
        }
        tv.text = label
        tv.visibility = View.VISIBLE
        val loc = IntArray(2)
        b.getLocationOnScreen(loc)   // v0.9.6: fresh position at EVERY press (never reuse the last key's place)
        val sw = b.resources.displayMetrics.widthPixels
        val x = com.mgb.keyboard.typing.TipPlace.x(loc[0], b.width, w, sw)   // centred over the key, kept on screen
        val y = (loc[1] - h - (4 * d).toInt()).coerceAtLeast(0)
        val p = cpw
        if (p != null && p.isShowing) {
            p.update(x, y, w, h)
        } else {
            val np = PopupWindow(tv, w, h, false)
            np.isTouchable = false
            np.isFocusable = false
            np.showAtLocation(b, Gravity.NO_GRAVITY, x, y)
            cpw = np
        }
        owner = b   // v0.9.6
    }

    /** v0.9.9 (V4): call after the keyboard / container is set up (onCreateInputView) and after every showLetters(): puts the overlay on top of the container. */
    fun attachHost(c: FrameLayout) {
        try {
            host = c
            rects.clear()
            if (layoutHost !== c) {
                layoutHost = c
                c.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> try { rects.clear() } catch (_: Throwable) { } }   // rotate / resize / one-hand / extras row = rects are recalculated
            }
            var o = ov
            if (o == null) { o = BubbleOverlay(c.context); ov = o; ovStyled = false }
            o.hideNow()
            if (o.parent !== c) {
                (o.parent as? ViewGroup)?.removeView(o)
                c.addView(o, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
        } catch (_: Throwable) { host = null }
    }

    /** v0.9.9: key position inside the container (cached; cache cleared on layout change). null = cannot tell = window path. */
    private fun relRect(b: View, c: View): IntArray? {
        val cached = rects.get(b)
        if (com.mgb.keyboard.typing.BubblePlan.rectValid(cached, b.width, b.height)) return cached
        var x = 0
        var y = 0
        var v: View = b
        var guard = 0
        while (v !== c) {
            val p = v.parent as? View ?: return null
            x += v.left + v.translationX.toInt() - p.scrollX
            y += v.top + v.translationY.toInt() - p.scrollY
            v = p
            if (++guard > 24) return null
        }
        rects.put(b, x, y, b.width, b.height)
        return rects.get(b)
    }

    /** v0.9.9: true = the bubble was drawn by the overlay. Any problem / no room above the key = false (window path as in v0.9.6). */
    private fun overlayShow(b: View, prefs: JabraPrefs, label: String): Boolean {
        try {
            if (!KeyCfg.popFast(prefs)) return false
            val c = host ?: return false
            val o = ov ?: return false
            if (o.parent !== c || c.width <= 0) return false
            val d = b.resources.displayMetrics.density
            if (!ovStyled) { styleOverlay(o, prefs, d); ovStyled = true }
            val w: Int
            val h: Int
            val sp: Float
            if (ovRef) {
                w = com.mgb.keyboard.typing.PopupStyle.width(b.width, d)
                h = com.mgb.keyboard.typing.PopupStyle.height(b.height, d)
                sp = com.mgb.keyboard.typing.PopupStyle.textSp(b.height, d)
            } else {
                w = (b.width + 12 * d).toInt().coerceAtLeast((44 * d).toInt())
                h = (b.height * 1.25f).toInt().coerceAtLeast((52 * d).toInt())
                sp = 26f
            }
            val r = relRect(b, c) ?: return false
            val gap = (4 * d).toInt()
            if (com.mgb.keyboard.typing.BubblePlan.mode(true, true, r[1], h, gap) != com.mgb.keyboard.typing.BubblePlan.OVERLAY) return false
            val x = com.mgb.keyboard.typing.BubblePlan.x(r[0], b.width, w, c.width)
            val y = com.mgb.keyboard.typing.BubblePlan.y(r[1], h, gap)
            try { if (ct?.visibility == View.VISIBLE) { ct?.visibility = View.INVISIBLE; if (cpw != null) { hnd.removeCallbacks(idle); hnd.postDelayed(idle, IDLE_MS) } } } catch (_: Throwable) { }   // a window bubble of the last key goes away
            o.show(label, x, y, w, h, android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, sp, b.resources.displayMetrics))
            owner = b
            return true
        } catch (_: Throwable) {
            try { ov?.hideNow() } catch (_: Throwable) { }
            return false
        }
    }

    /** v0.9.9: same colours / radius as the window bubble of showFast (pop_ref ON = reference look, OFF = v0.9.5 look). */
    private fun styleOverlay(o: BubbleOverlay, prefs: JabraPrefs, d: Float) {
        val ref = try { prefs.bool("pop_ref", true) } catch (_: Throwable) { true }
        ovRef = ref
        val sw = (1 * d).toInt().coerceAtLeast(1).toFloat()
        if (ref) {
            val th = try { JabraThemes.cur(prefs) } catch (_: Throwable) { null }
            val key: Int? = th?.key
            val fill = com.mgb.keyboard.typing.PopupStyle.fill(key)
            o.setLook(fill, com.mgb.keyboard.typing.PopupStyle.stroke(key, fill), sw, com.mgb.keyboard.typing.PopupStyle.text(th?.text, fill), com.mgb.keyboard.typing.PopupStyle.radiusPx(d))
        } else {
            val lookOn = try { JabraLook.on(prefs) } catch (_: Throwable) { false }
            val tc = if (lookOn) (try { JabraThemes.iconColor(prefs) } catch (_: Throwable) { Color.parseColor("#222222") }) else Color.parseColor("#222222")
            val fc = if (lookOn) (try { JabraLook.fill(prefs) } catch (_: Throwable) { Color.parseColor("#FFFFFF") }) else Color.parseColor("#FFFFFF")
            o.setLook(fc, Color.parseColor("#888888"), sw, tc, (if (lookOn) 16 else 10) * d)
        }
    }

    /** v0.8.9 body, unchanged (fallback). */
    private fun showOld(b: Button, prefs: JabraPrefs) {
        try { pw?.dismiss() } catch (_: Throwable) { }; pw = null
        val label = b.text?.toString() ?: ""
        if (label.isEmpty()) return
        val d = b.resources.displayMetrics.density
        val w = (b.width + 12 * d).toInt().coerceAtLeast((44 * d).toInt())
        val h = (b.height * 1.25f).toInt().coerceAtLeast((52 * d).toInt())
        val t = TextView(b.context)
        t.text = label
        t.textSize = 26f
        t.gravity = Gravity.CENTER
        val lookOn = try { JabraLook.on(prefs) } catch (_: Throwable) { false }   // v0.1.5: own bubble look
        t.setTextColor(if (lookOn) (try { JabraThemes.iconColor(prefs) } catch (_: Throwable) { Color.parseColor("#222222") }) else Color.parseColor("#222222"))
        val bg = GradientDrawable()
        bg.setColor(if (lookOn) (try { JabraLook.fill(prefs) } catch (_: Throwable) { Color.parseColor("#FFFFFF") }) else Color.parseColor("#FFFFFF"))
        bg.setStroke((1 * d).toInt().coerceAtLeast(1), Color.parseColor("#888888"))
        bg.cornerRadius = (if (lookOn) 16 else 10) * d
        t.background = bg
        val p = PopupWindow(t, w, h, false)
        p.isTouchable = false
        p.isFocusable = false
        val loc = IntArray(2)
        b.getLocationOnScreen(loc)
        val x = loc[0] + b.width / 2 - w / 2
        val y = (loc[1] - h - (4 * d).toInt()).coerceAtLeast(0)
        p.showAtLocation(b, Gravity.NO_GRAVITY, x, y)
        pw = p
    }
}
