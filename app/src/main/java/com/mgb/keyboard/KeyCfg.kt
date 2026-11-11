package com.mgb.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import com.mgb.keyboard.typing.FeedbackGate
import com.mgb.keyboard.typing.TypingLevels

/**
 * v0.6.0 PERF-1: per-tap settings cache for the key-press path.
 * One immutable Snap holds haptic / sound / popup / long-press values. It is rebuilt ONLY after a pref changed
 * (SharedPreferences listener sets dirty), so a tap costs one volatile read instead of 3-6 pref reads.
 * The Vibrator service is looked up once. Every read has try/catch; any problem = callers use their old direct pref reads.
 * Nothing here runs a timer or thread. Same pref keys and meanings as before (no new pref).
 */
class KeyCfg(private val ctx: Context) {

    class Snap(
        val haptic: Boolean, val hapticMs: Long, val sound: Boolean,
        val popup: Boolean, val hintOn: Boolean, val lpMs: Int,
        val fbDown: Boolean = false, val hapticNative: Boolean = false,   // v0.9.1 (R2)
        val popFast: Boolean = true,   // v0.9.9 (V4)
        val perf: Boolean = false   // v0.9.10 (V5): speed meter, default OFF
    )

    @Volatile private var dirty = true
    @Volatile private var snap: Snap? = null
    private var vib: Vibrator? = null
    private var vibDone = false
    private var vibHas = false

    // must be kept in a field: SharedPreferences holds listeners weakly
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> dirty = true }

    init {
        try { ctx.getSharedPreferences("jabra", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener) } catch (_: Throwable) { }
        current = this
    }

    /** Current values, rebuilt only when a pref changed. Returns null if reading fails (caller uses the old path). */
    fun get(): Snap? {
        val s = snap
        if (s != null && !dirty) return s
        return try {
            dirty = false   // clear first: a change during the read sets it again
            val sp = ctx.getSharedPreferences("jabra", Context.MODE_PRIVATE)
            val n = Snap(
                haptic = sp.getBoolean("haptic", true),
                hapticMs = try { TypingLevels.hapticMs(sp.getInt("haptic_lv", TypingLevels.HAPTIC_DEFAULT)).toLong() } catch (_: Throwable) { 18L },
                sound = sp.getBoolean("sound", false),
                popup = sp.getBoolean("key_popup", false),
                hintOn = sp.getBoolean("hint_on", true),
                lpMs = try { TypingLevels.longPressMs(sp.getInt("lp_delay", TypingLevels.LP_DEFAULT)) } catch (_: Throwable) { 0 },
                fbDown = sp.getBoolean("fb_down", false),   // v0.9.1
                hapticNative = sp.getBoolean("haptic_native", false),   // v0.9.1
                popFast = sp.getBoolean("pop_fast", true),   // v0.9.9
                perf = sp.getBoolean("perf_meter", false)   // v0.9.10
            )
            snap = n
            n
        } catch (_: Throwable) { dirty = true; null }
    }

    /** The Vibrator, looked up once. null = none / failed (caller uses View.performHapticFeedback like before). */
    @Suppress("DEPRECATION")
    fun vibrator(): Vibrator? {
        if (!vibDone) {
            vibDone = true
            try {
                val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (v != null && v.hasVibrator()) { vib = v; vibHas = true }
            } catch (_: Throwable) { }
        }
        return if (vibHas) vib else null
    }

    fun release() {
        try { ctx.getSharedPreferences("jabra", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(listener) } catch (_: Throwable) { }
        if (current === this) current = null
    }

    companion object {
        @Volatile var current: KeyCfg? = null

        /** v0.9.1 (R2): who already gave feedback for a key at finger-down (typing/FeedbackGate.java, tested). */
        val gate = FeedbackGate()
        private fun kid(v: View): Int = System.identityHashCode(v)

        /** fb_down (default OFF = feedback at click like v0.9.0). */
        fun fbDown(p: JabraPrefs): Boolean = try { current?.get()?.fbDown ?: p.bool("fb_down", false) } catch (_: Throwable) { false }

        /** Finger down on a key: true = give feedback now. */
        fun downBuzz(v: View, p: JabraPrefs): Boolean = try { gate.onDown(kid(v), fbDown(p)) } catch (_: Throwable) { false }

        /** Key click: true = give feedback now (down did not). Any problem = true (old behaviour). */
        fun clickBuzz(v: View): Boolean = try { gate.onClick(kid(v)) } catch (_: Throwable) { true }

        /** Long-press hint fired: true = give feedback (down did not). Any problem = true (old behaviour). */
        fun hintBuzz(v: View): Boolean = try { gate.onHint(kid(v)) } catch (_: Throwable) { true }

        fun cancelBuzz(v: View) { try { gate.onCancel(kid(v)) } catch (_: Throwable) { } }

        /** Keys that have no other touch listener (number row without hints): feedback at down when fb_down is ON. Never consumes the touch. */
        fun attachDown(b: View, p: JabraPrefs, fire: (View) -> Unit) {
            b.setOnTouchListener { v, e ->
                try {
                    if (e.actionMasked == MotionEvent.ACTION_DOWN) { if (downBuzz(v, p)) fire(v) }
                    else if (e.actionMasked == MotionEvent.ACTION_CANCEL) cancelBuzz(v)
                } catch (_: Throwable) { }
                false
            }
        }

        /** key_popup for KeyPopup.touch (fallback = direct pref read). */
        fun popupOn(p: JabraPrefs): Boolean = try { current?.get()?.popup ?: p.bool("key_popup", false) } catch (_: Throwable) { p.bool("key_popup", false) }

        /** v0.9.9 (V4): pop_fast (default ON; fallback = direct pref read). */
        fun popFast(p: JabraPrefs): Boolean = try { current?.get()?.popFast ?: p.bool("pop_fast", true) } catch (_: Throwable) { try { p.bool("pop_fast", true) } catch (_: Throwable) { false } }

        /** v0.9.10 (V5): speed meter. OFF (default) = every hook below is one cached boolean check, no samples. */
        val timing = com.mgb.keyboard.typing.KeyTiming()
        @Volatile private var downNs = 0L
        fun perfOn(): Boolean = try { current?.get()?.perf ?: false } catch (_: Throwable) { false }
        /** Returns a start stamp (0 = meter OFF). */
        fun pStart(): Long = try { if (perfOn()) System.nanoTime() else 0L } catch (_: Throwable) { 0L }
        fun pEnd(kind: Int, start: Long) { try { if (start != 0L) timing.add(kind, System.nanoTime() - start) } catch (_: Throwable) { } }
        fun pDown() { try { downNs = if (perfOn()) System.nanoTime() else 0L } catch (_: Throwable) { } }
        fun pCommit() { try { val d = downNs; if (d != 0L) { downNs = 0L; timing.add(com.mgb.keyboard.typing.KeyTiming.KEY, System.nanoTime() - d) } } catch (_: Throwable) { } }

        /** hint_on for HintKeys (fallback = direct pref read). */
        fun hintOn(p: JabraPrefs): Boolean = try { current?.get()?.hintOn ?: p.bool("hint_on", true) } catch (_: Throwable) { p.bool("hint_on", true) }

        /** Long-press delay in ms (0 = system default), same as TypingLevels.longPressMs(lp_delay). */
        fun lpMs(p: JabraPrefs): Int = try { current?.get()?.lpMs ?: TypingLevels.longPressMs(p.int("lp_delay", TypingLevels.LP_DEFAULT)) } catch (_: Throwable) { TypingLevels.longPressMs(p.int("lp_delay", TypingLevels.LP_DEFAULT)) }
    }
}
