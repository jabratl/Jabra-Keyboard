package com.mgb.keyboard

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.mgb.keyboard.typing.HoldSelect

/*
 * v0.16.16 (E2): hold-to-select for ONE clip row / card (uses the pure state machine typing/HoldSelect, tested).
 * ONE OnTouchListener per view. Hold the clip [ms] = select mode starts with that clip; a short tap = [onTap];
 * in select mode a tap = [onTap] (toggle). Scrolling (parent takes the touch) or moving more than the touch slop = nothing happens.
 * attach() returns false (and touches nothing) when [ms] is not a valid hold time: the caller then keeps the OLD long-click.
 * The old click listener stays on the view (TalkBack / keyboard clicks still work); a touch is fully handled here.
 */
object ClipHold {
    fun attach(v: View, ms: Int, selectOn: Boolean, onStart: () -> Unit, onTap: () -> Unit, feedback: () -> Unit): Boolean {
        if (!HoldSelect.isValid(ms)) return false
        return try {
            val hs = HoldSelect(ms, ViewConfiguration.get(v.context).scaledTouchSlop.toFloat())
            val h = Handler(Looper.getMainLooper())
            val tick = Runnable {
                try {
                    if (hs.tick(SystemClock.uptimeMillis()) == HoldSelect.Result.START_SELECT) {
                        v.isPressed = false
                        try { feedback() } catch (_: Throwable) { }
                        onStart()
                    }
                } catch (_: Throwable) { }
            }
            v.setOnTouchListener { view, e ->
                try {
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            hs.down(SystemClock.uptimeMillis(), e.x, e.y, selectOn)
                            view.isPressed = true
                            h.removeCallbacks(tick)
                            if (!selectOn) h.postDelayed(tick, ms.toLong())
                        }
                        MotionEvent.ACTION_MOVE -> {
                            hs.move(SystemClock.uptimeMillis(), e.x, e.y)
                            if (hs.state() == HoldSelect.State.CANCELLED) { h.removeCallbacks(tick); view.isPressed = false }
                        }
                        MotionEvent.ACTION_UP -> {
                            h.removeCallbacks(tick)
                            view.isPressed = false
                            when (hs.up(SystemClock.uptimeMillis())) {
                                HoldSelect.Result.START_SELECT -> { try { feedback() } catch (_: Throwable) { }; onStart() }
                                HoldSelect.Result.TAP_PASTE, HoldSelect.Result.TAP_TOGGLE -> onTap()
                                else -> { }
                            }
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            h.removeCallbacks(tick)
                            hs.cancel()
                            view.isPressed = false
                        }
                        else -> { }
                    }
                } catch (_: Throwable) { }
                true
            }
            true
        } catch (_: Throwable) { false }
    }
}
