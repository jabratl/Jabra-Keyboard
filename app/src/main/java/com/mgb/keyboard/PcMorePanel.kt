package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.PcActions

/**
 * v0.17.51 (PC3): two small panels of the PC mode (routes showPanel("pcmore") and showPanel("pcnum"), opened by the More / Num keys of the PC rows).
 *  pcmore: PrtSc ScrLk Pause Menu CapsLk NumLk AltGr (one key down + up each, with the sticky Ctrl / Alt / Shift / Win like every PC key) + Num + ABC.
 *  pcnum: keypad 7 8 9 / | 4 5 6 * | 1 2 3 - | 0 . = + , then NumLk Enter(keypad) Backspace ABC.
 * Same look as PadPanel (UiKit buttons, theme paint). Keys go through PcIo.sendKey (= MgbIme.pcSend). build() returns null on any problem -> keyboard shows normal letters.
 * Limit: the app must read key events (same as PC mode); keypad digits only type when the app treats NumLock as ON.
 */
class PcMorePanel(private val ctx: Context, private val host: JabraHost, private val io: PcIo) {

    private val prefs = JabraPrefs(ctx)

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun pk(label: String, onClick: (Button) -> Unit): Button =
        UiKit.btn(ctx, prefs, label, 1f, host.keyHeightDp(), 14f, { v -> host.feedback(v) }, onClick)

    private fun key(label: String, code: Int): Button = pk(label) { io.sendKey(code, 0) }

    private fun frame(): LinearLayout {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        try { JabraThemes.paint(prefs, root) } catch (_: Throwable) { }
        root.setPadding(dp(2), dp(4), dp(2), dp(4))
        return root
    }

    private fun note(text: String): TextView = UiKit.noteText(ctx, prefs, text)

    /** name = "pcmore" or "pcnum". null on any problem. */
    fun build(name: String): View? = try { if (name == "pcnum") makeNum() else makeMore() } catch (_: Throwable) { null }

    private fun makeMore(): View {
        val root = frame()
        val n = PcActions.moreCount()
        var i = 0
        while (i < n) {
            val r = UiKit.row(ctx)
            var c = 0
            while (c < 4) {
                if (i < n) r.addView(key(PcActions.moreLabel(i), PcActions.moreCode(i)))
                else if (c == 3) r.addView(pk("Num") { host.showPanel("pcnum") })   // fills the last cell of the 2nd row
                else r.addView(View(ctx).also { it.layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
                i++; c++
            }
            root.addView(r)
        }
        root.addView(note("Sticky Ctrl / Alt / Shift / Win apply to these keys too. The app must read key events; many plain text fields ignore PrtSc, Pause, Menu."))
        val r3 = UiKit.row(ctx)
        r3.addView(pk("ABC") { host.showLetters() })
        root.addView(r3)
        return root
    }

    private fun makeNum(): View {
        val root = frame()
        var i = 0
        for (rr in 0 until 4) {
            val r = UiKit.row(ctx)
            for (c in 0 until 4) { r.addView(key(PcActions.padLabel(i), PcActions.padCode(i))); i++ }
            root.addView(r)
        }
        val r5 = UiKit.row(ctx)
        r5.addView(key("NumLk", 143))
        r5.addView(key("Enter", PcActions.PAD_ENTER))
        val bs = pk("⌫") { }
        try { JabraIcons.putThemed(ctx, bs, "back") } catch (_: Throwable) { }
        try { RepeatKeyHelper({ host.backspace() }, { v -> try { host.feedback(v) } catch (_: Throwable) { } }).attach(bs) } catch (_: Throwable) { }
        r5.addView(bs)
        r5.addView(pk("ABC") { host.showLetters() })
        root.addView(r5)
        root.addView(note("Keypad keys: the app must read key events and treat NumLock as ON to type digits. NumLk sends the NumLock key."))
        return root
    }
}
