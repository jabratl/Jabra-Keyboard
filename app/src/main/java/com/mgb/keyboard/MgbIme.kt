package com.mgb.keyboard

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout

/** v0.1: minimal English QWERTY keyboard. Every key press is wrapped in try/catch. */
class MgbIme : InputMethodService() {

    private var shift = false
    private val letterKeys = mutableListOf<Button>()

    override fun onCreateInputView(): View {
        return try {
            letterKeys.clear()
            buildKeyboard()
        } catch (e: Throwable) {
            LinearLayout(this) // empty view instead of crashing
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun key(label: String, weight: Float, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        b.setOnClickListener {
            try { onClick() } catch (_: Throwable) { }
        }
        val lp = LinearLayout.LayoutParams(0, dp(48), weight)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        b.layoutParams = lp
        return b
    }

    private fun row(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    private fun typeText(s: String) {
        currentInputConnection?.commitText(s, 1)
    }

    private fun letter(c: Char): Button {
        val b = key(c.toString(), 1f) {
            typeText(if (shift) c.uppercaseChar().toString() else c.toString())
            if (shift) { shift = false; refreshLetters() }
        }
        letterKeys.add(b)
        return b
    }

    private fun refreshLetters() {
        letterKeys.forEach { b ->
            val t = b.text.toString()
            b.text = if (shift) t.uppercase() else t.lowercase()
        }
    }

    private fun buildKeyboard(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        val r1 = row()
        "qwertyuiop".forEach { r1.addView(letter(it)) }
        root.addView(r1)

        val r2 = row()
        "asdfghjkl".forEach { r2.addView(letter(it)) }
        root.addView(r2)

        val r3 = row()
        r3.addView(key("⇧", 1.5f) { shift = !shift; refreshLetters() })
        "zxcvbnm".forEach { r3.addView(letter(it)) }
        r3.addView(key("⌫", 1.5f) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) })
        root.addView(r3)

        val r4 = row()
        r4.addView(key(",", 1f) { typeText(",") })
        r4.addView(key("space", 5f) { typeText(" ") })
        r4.addView(key(".", 1f) { typeText(".") })
        r4.addView(key("⏎", 1.5f) { sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) })
        root.addView(r4)

        return root
    }
}
