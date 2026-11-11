package com.mgb.keyboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
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

    /** v0.9.0: hide now (cheap). The reusable window stays for the next press and is dropped by the idle timer. */
    fun dismiss() {
        try { pw?.dismiss() } catch (_: Throwable) { }; pw = null
        try {
            ct?.visibility = View.INVISIBLE
            if (cpw != null) { hnd.removeCallbacks(idle); hnd.postDelayed(idle, IDLE_MS) }
        } catch (_: Throwable) { dropCached() }
    }

    /** v0.9.0: forget the cached bubble (called when the keyboard is rebuilt so colours / look refresh on the next press). */
    fun invalidate() { dropCached() }

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
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dismiss()
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
        if (label.isEmpty()) { dismiss(); return }
        val d = b.resources.displayMetrics.density
        val w = (b.width + 12 * d).toInt().coerceAtLeast((44 * d).toInt())
        val h = (b.height * 1.25f).toInt().coerceAtLeast((52 * d).toInt())
        var t = ct
        if (t == null || cpw == null) {   // first press (or after idle / rebuild): build + style ONCE
            dropCached()
            t = TextView(b.context)
            t.textSize = 26f
            t.gravity = Gravity.CENTER
            val lookOn = try { JabraLook.on(prefs) } catch (_: Throwable) { false }
            t.setTextColor(if (lookOn) (try { JabraThemes.iconColor(prefs) } catch (_: Throwable) { Color.parseColor("#222222") }) else Color.parseColor("#222222"))
            val bg = GradientDrawable()
            bg.setColor(if (lookOn) (try { JabraLook.fill(prefs) } catch (_: Throwable) { Color.parseColor("#FFFFFF") }) else Color.parseColor("#FFFFFF"))
            bg.setStroke((1 * d).toInt().coerceAtLeast(1), Color.parseColor("#888888"))
            bg.cornerRadius = (if (lookOn) 16 else 10) * d
            t.background = bg
            ct = t
        }
        val tv = ct ?: return
        tv.text = label
        tv.visibility = View.VISIBLE
        val loc = IntArray(2)
        b.getLocationOnScreen(loc)
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
