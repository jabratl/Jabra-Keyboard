package com.mgb.keyboard

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

/**
 * v0.17.50 (PC2): hold-repeat for a PC key that is a layout key (same behaviour as PcRows.held in PC1):
 * key DOWN once at touch, repeat DOWN with a growing count after ~350 ms and then every ~50 ms, key UP on release / cancel / detach.
 * Everything goes through PcIo (MgbIme.pcHoldStart / pcHoldRepeat / pcHoldEnd). Never throws.
 */
object PcHold {
    private const val FIRST_MS = 350L
    private const val REPEAT_MS = 50L
    private val handler = Handler(Looper.getMainLooper())

    fun attach(b: View, code: Int, io: PcIo) {
        var down = false
        var n = 0
        val rep = object : Runnable {
            override fun run() {
                if (!down) return
                n++
                try { io.holdRepeat(code, n) } catch (_: Throwable) { }
                handler.postDelayed(this, REPEAT_MS)
            }
        }
        fun release() {
            handler.removeCallbacks(rep)
            if (down) { down = false; try { io.holdEnd() } catch (_: Throwable) { } }
            b.isPressed = false
        }
        b.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { }
            override fun onViewDetachedFromWindow(v: View) { release() }   // never leave a key down when the row disappears
        })
        b.setOnTouchListener { _, ev ->
            try {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        release()
                        b.isPressed = true
                        try { io.feedback(b) } catch (_: Throwable) { }
                        down = true
                        n = 0
                        io.holdStart(code)
                        handler.postDelayed(rep, FIRST_MS)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { release(); true }
                    else -> true
                }
            } catch (_: Throwable) { release(); true }
        }
    }
}
