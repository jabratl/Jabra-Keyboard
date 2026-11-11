package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.DoubleSpace
import com.mgb.keyboard.typing.TypingLevels

/*
 * v0.1.1 typing preferences. All new code lives here + typing/DoubleSpace.java + typing/TypingLevels.java (pure Java, tested).
 * MgbIme.kt / JabraExtras.kt / TypingExtras.kt / JabraIcons.kt only have small hooks marked "v0.1.1"; every hook is try/catch
 * (on a problem the old behaviour stays). Defaults = exactly the old behaviour.
 * Prefs file "jabra", NEW keys only: ds_period(bool,false) ds_tab(bool,false) lp_delay(int,0=System) cursor_speed(int,2=Normal)
 *   key_globe(bool,false) key_voice(bool,false) key_emoji(bool,false)
 *  - SpaceHelper     : double space = period (Bangla mode: danda) or tab
 *  - JabraLongPress  : long-press with an own delay (level 0 = the phone's own long-press, as before)
 *  - ImeSwitcher     : Globe key (next keyboard) + Voice key (the phone's system voice typing, NOT own recording)
 *  - TypingPanel     : "Typing options" page (More settings -> Typing options)
 */

/** Double space: the 2nd tap on space replaces the first space with ". " (or danda / tab). */
class SpaceHelper(private val prefs: JabraPrefs) {
    private var lastAt = 0L

    /** Returns true when it already did the edit (the caller must NOT type a space). Never throws. */
    fun handle(ic: InputConnection?, now: Long, danda: Boolean): Boolean {
        try {
            val per = prefs.bool("ds_period", false)
            val tab = prefs.bool("ds_tab", false)
            if (!per && !tab) return false
            if (ic == null) return false
            val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
            val act = DoubleSpace.decide(per, tab, lastAt, now, DoubleSpace.WINDOW_MS, before)
            if (act == DoubleSpace.NONE) {
                lastAt = now          // the caller types a normal space now
                return false
            }
            ic.beginBatchEdit()
            try {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(if (act == DoubleSpace.PERIOD) (if (danda) "\u0964 " else ". ") else "\t", 1)
            } finally {
                ic.endBatchEdit()
            }
            lastAt = 0L               // a 3rd tap is a normal space again
            return true
        } catch (_: Throwable) {
            return false
        }
    }
}

/** Long-press with a setting. Level 0 (System) = setOnLongClickListener exactly like before v0.1.1. */
object JabraLongPress {
    fun attach(v: View, prefs: JabraPrefs, onLong: () -> Unit) {
        val ms = TypingLevels.longPressMs(prefs.int("lp_delay", TypingLevels.LP_DEFAULT))
        if (ms <= 0) {
            v.setOnLongClickListener { try { onLong() } catch (_: Throwable) { }; true }
            return
        }
        val h = Handler(Looper.getMainLooper())
        var fired = false
        val r = Runnable {
            fired = true
            try { onLong() } catch (_: Throwable) { }
        }
        v.setOnTouchListener { view, ev ->
            try {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        fired = false
                        h.removeCallbacks(r)
                        h.postDelayed(r, ms.toLong())
                        false                       // the key still gets its normal press look + click
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (ev.x < 0f || ev.y < 0f || ev.x > view.width || ev.y > view.height) h.removeCallbacks(r)
                        false
                    }
                    MotionEvent.ACTION_UP -> {
                        h.removeCallbacks(r)
                        if (fired) { fired = false; view.isPressed = false; true }   // long-press happened: no click
                        else false
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        h.removeCallbacks(r)
                        false
                    }
                    else -> false
                }
            } catch (_: Throwable) {
                false
            }
        }
    }
}

/** Globe key + Voice key. No new permission: only asks Android to switch to another keyboard. */
class ImeSwitcher(private val ime: InputMethodService) {

    private fun imm(): InputMethodManager = ime.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    /** The phone's own keyboard picker (old v0.0.9 feature). */
    fun picker() {
        try { imm().showInputMethodPicker() } catch (_: Throwable) { }
    }

    /** Globe: next keyboard. If Android refuses, the picker opens. */
    fun globe() {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                if (ime.switchToNextInputMethod(false)) return
            } else {
                val tok = ime.window?.window?.attributes?.token
                if (tok != null && imm().switchToNextInputMethod(tok, false)) return
            }
        } catch (_: Throwable) { }
        picker()
    }

    /**
     * Voice: stage 1 = switch to an enabled keyboard that has a "voice" subtype (the phone's system voice typing).
     * Stage 2 (nothing found / refused) = the keyboard picker, so the user can pick voice typing by hand.
     */
    fun voice() {
        try {
            val m = imm()
            for (imi in m.enabledInputMethodList) {
                if (imi.packageName == ime.packageName) continue
                for (st in m.getEnabledInputMethodSubtypeList(imi, true)) {
                    if (st.mode.equals("voice", true)) {
                        if (Build.VERSION.SDK_INT >= 28) {
                            ime.switchInputMethod(imi.id, st)
                            return
                        } else {
                            val tok = ime.window?.window?.attributes?.token
                            if (tok != null) {
                                                m.setInputMethodAndSubtype(tok, imi.id, st)
                                return
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) { }
        picker()
    }
}

/** "Typing options" page (More settings -> Typing options). Built in code like the other panels. */
class TypingPanel(private val ctx: Context, private val host: JabraHost) {

    private val prefs = JabraPrefs(ctx)

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun pk(label: String, weight: Float, onClick: (Button) -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        b.textSize = 12f
        try { JabraThemes.style(prefs, b) } catch (_: Throwable) { }
        b.setOnClickListener {
            try { host.feedback(b) } catch (_: Throwable) { }
            try { onClick(b) } catch (_: Throwable) { }
        }
        val lp = LinearLayout.LayoutParams(0, dp(host.keyHeightDp()), weight)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        b.layoutParams = lp
        return b
    }

    private fun row(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    private fun onOff(b: Boolean): String = if (b) "ON" else "OFF"

    private fun toggleKey(title: String, key: String, def: Boolean): Button {
        val b = pk("", 1f) { btn ->
            val nv = !prefs.bool(key, def)
            prefs.setBool(key, nv)
            btn.text = title + ": " + onOff(nv)
        }
        b.text = title + ": " + onOff(prefs.bool(key, def))
        return b
    }

    /** Tap = next level (wraps around). */
    private fun levelKey(title: String, key: String, def: Int, names: Array<String>): Button {
        val b = pk("", 1f) { btn ->
            val cur = prefs.int(key, def).coerceIn(0, names.size - 1)
            val nv = (cur + 1) % names.size
            prefs.setInt(key, nv)
            btn.text = title + ": " + names[nv]
        }
        b.text = title + ": " + names[prefs.int(key, def).coerceIn(0, names.size - 1)]
        return b
    }

    private fun note(text: String): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = 11f
        t.setTextColor(try { JabraThemes.noteColor(prefs) } catch (_: Throwable) { Color.parseColor("#555555") })
        t.setPadding(dp(6), dp(2), dp(6), dp(2))
        return t
    }

    /** Returns null on any problem -> keyboard shows normal letters. */
    fun build(): View? = try { make() } catch (_: Throwable) { null }

    private fun make(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        try { JabraThemes.paint(prefs, root) } catch (_: Throwable) { }   // v0.2.6
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        val r1 = row()
        r1.addView(toggleKey("Double-space period", "ds_period", false))
        r1.addView(toggleKey("Double-space tab", "ds_tab", false))
        root.addView(r1)

        val r2 = row()
        r2.addView(levelKey("Long-press", "lp_delay", TypingLevels.LP_DEFAULT, TypingLevels.LP_NAMES))
        r2.addView(levelKey("Cursor speed", "cursor_speed", TypingLevels.CS_DEFAULT, TypingLevels.CS_NAMES))
        root.addView(r2)

        val r3 = row()
        r3.addView(toggleKey("Globe key", "key_globe", false))
        r3.addView(toggleKey("Voice key", "key_voice", false))
        r3.addView(toggleKey("Emoji key", "key_emoji", false))
        root.addView(r3)

        try { val r35 = row(); r35.addView(toggleKey("Key popup", "key_popup", false)); r35.addView(toggleKey("Large number row", "numrow_large", false)); root.addView(r35) } catch (_: Throwable) { }
        try { val r35b = row(); r35b.addView(toggleKey("Reference-style bubble", "pop_ref", true)); root.addView(r35b) } catch (_: Throwable) { }   // v0.9.6 (V1): look of the key bubble (only while Key popup is ON)   // v0.1.3
        try { val r35c = row(); r35c.addView(toggleKey("Same keyboard height", "h_lock", true)); root.addView(r35c) } catch (_: Throwable) { }   // v0.9.7 (V2): keyboard height stays the same when the language changes
        try {   // v0.5.0: custom character row (extra row above the numbers)
            val r37 = row()
            val cur = prefs.str("xrow_chars", "")
            r37.addView(pk("Char row: " + (if (cur.isEmpty()) "none" else cur), 2f) { b ->
                val s = com.mgb.keyboard.typing.LangOrder.customRowText(host.fieldText())
                if (s.isEmpty()) b.text = "Field has no characters" else { prefs.setStr("xrow_chars", s); b.text = "Char row: " + s }
            })
            r37.addView(pk("Clear row", 1f) { _ -> prefs.setStr("xrow_chars", ""); host.showPanel("typing") })
            root.addView(r37)
        } catch (_: Throwable) { }
        try {   // v0.5.2: vibration strength + custom long-press characters
            val r38 = row()
            r38.addView(levelKey("Vibration strength", "haptic_lv", TypingLevels.HAPTIC_DEFAULT, TypingLevels.HAPTIC_NAMES))
            root.addView(r38)
            val r38b = row()   // v0.9.1 (R2): feedback at finger-down + system key tap
            r38b.addView(toggleKey("Feedback at touch-down", "fb_down", false))
            r38b.addView(toggleKey("System key tap vibration", "haptic_native", false))
            root.addView(r38b)
            val r39 = row()
            val hc = prefs.str("hint_custom", "")
            r39.addView(pk("Hint override: " + (if (hc.isEmpty()) "none" else hc), 2f) { b ->
                val s = com.mgb.keyboard.typing.LongPressHints.normalizeCustom(host.fieldText().replace('\n', ','))
                if (s.isEmpty()) b.text = "Field has no a=@ pairs" else { prefs.setStr("hint_custom", s); b.text = "Hint override: " + s }
            })
            r39.addView(pk("Clear", 1f) { _ -> prefs.setStr("hint_custom", ""); host.showPanel("typing") })
            root.addView(r39)
        } catch (_: Throwable) { }
        try {   // v0.8.9: space swipe language + optional EN key (ABC apply)
            val r41 = row()
            r41.addView(toggleKey("Space swipe = language", "space_lang", false))
            r41.addView(toggleKey("Show language key (EN)", "key_lang", true))
            root.addView(r41)
            val r41b = row()   // v0.9.2 (R3)
            r41b.addView(toggleKey("Language bubble on swipe", "sp_preview", true))
            root.addView(r41b)
        } catch (_: Throwable) { }
        try {   // v0.8.6: hint look (Bangla digits on number row, hint size, hint opacity). ABC (apply) redraws the keys.
            val r40 = row()
            r40.addView(toggleKey("Bangla digit hints", "hint_digits", false))
            r40.addView(levelKey("Hint size", "hint_size_lv", com.mgb.keyboard.typing.HintStyle.LV_DEFAULT, com.mgb.keyboard.typing.HintStyle.SIZE_NAMES))
            r40.addView(levelKey("Hint opacity", "hint_alpha_lv", com.mgb.keyboard.typing.HintStyle.LV_DEFAULT, com.mgb.keyboard.typing.HintStyle.ALPHA_NAMES))
            root.addView(r40)
            val r40n = row()   // v0.9.4 (R4)
            r40n.addView(toggleKey("Bangla digits on number row", "numrow_bn", false))
            root.addView(r40n)
        } catch (_: Throwable) { }
        try { val r36 = row(); r36.addView(toggleKey("Long-press characters", "hint_on", true)); r36.addView(toggleKey("Hide long-press hints", "hint_hide", false)); root.addView(r36) } catch (_: Throwable) { }   // v0.1.6

        root.addView(note("Double-space: tap space twice = \". \" (Bangla mode: \"\u0964 \"). Tab only when the period does not fit. Not used in password / email / web fields. " +
            "Long-press: System = the phone's own time. Cursor speed: drag on the space key. Globe = next keyboard. Voice = the phone's voice typing (no recording by Jabra). " +
            "Keys appear on the bottom row after ABC (apply). Key popup: a bubble above the pressed letter (works at once). Large number row: ABC (apply). " +
            "Long-press characters: hold a letter key = types its small corner character (English only; not in Avro / Probhat). Hide hints: hides only the small label (ABC apply), long-press still works. " +
            "Char row: type up to 10 characters (e.g. @ # _ - ) in any text field, tap this button = they become an extra row above the numbers (ABC apply). " +
            "Vibration strength: Light / Medium (old) / Strong. Hint override: type pairs like a=@,s=# (letter = one character, max 26) in any text field, tap the button = those letters use your character on long-press (ABC apply)."))

        val rb = row()
        rb.addView(pk("< More settings", 1f) { host.showPanel("more") })
        rb.addView(pk("ABC  (apply)", 1f) { host.showLetters() })
        root.addView(rb)
        return root
    }
}
