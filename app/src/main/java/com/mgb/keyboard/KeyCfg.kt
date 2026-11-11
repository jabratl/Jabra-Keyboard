package com.mgb.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.os.Vibrator
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
        val popup: Boolean, val hintOn: Boolean, val lpMs: Int
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
                lpMs = try { TypingLevels.longPressMs(sp.getInt("lp_delay", TypingLevels.LP_DEFAULT)) } catch (_: Throwable) { 0 }
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

        /** key_popup for KeyPopup.touch (fallback = direct pref read). */
        fun popupOn(p: JabraPrefs): Boolean = try { current?.get()?.popup ?: p.bool("key_popup", false) } catch (_: Throwable) { p.bool("key_popup", false) }

        /** hint_on for HintKeys (fallback = direct pref read). */
        fun hintOn(p: JabraPrefs): Boolean = try { current?.get()?.hintOn ?: p.bool("hint_on", true) } catch (_: Throwable) { p.bool("hint_on", true) }

        /** Long-press delay in ms (0 = system default), same as TypingLevels.longPressMs(lp_delay). */
        fun lpMs(p: JabraPrefs): Int = try { current?.get()?.lpMs ?: TypingLevels.longPressMs(p.int("lp_delay", TypingLevels.LP_DEFAULT)) } catch (_: Throwable) { TypingLevels.longPressMs(p.int("lp_delay", TypingLevels.LP_DEFAULT)) }
    }
}
