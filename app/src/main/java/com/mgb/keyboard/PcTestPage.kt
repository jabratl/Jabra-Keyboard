package com.mgb.keyboard

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.typing.AppTokens
import com.mgb.keyboard.typing.PcKeyPlan
import com.mgb.keyboard.typing.PcLog

/**
 * v0.17.52 (PC4): "PC key test" page of the app screen (button in More settings, under the PC rows options).
 * A full-screen page with a test box. Keys you tap on the PC rows / chips while the box is focused are listed as
 * "sent ... accepted yes / NO" and the keys the box REALLY received as "got ...". "Run self-test" types a fixed sequence
 * (Left, Right, Home, End, Ctrl+A, Ctrl+C + Ctrl+V) into the box and reads the box back: ok / FAIL per step + a one-line summary to copy.
 * PRIVACY: typing/PcLog records only while this page is open, only PC-row keys and the box's own non-typing keys, in memory, last 20 lines;
 * closing the page drops everything; nothing goes to disk or network. Everything in try / catch; the key path only gets one extra log call.
 */
object PcTestPage {
    private fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun button(ctx: Context): Button {
        val b = Button(ctx)
        b.isAllCaps = false
        b.text = "PC key test (what does the app receive?)"
        b.contentDescription = "Open the PC key test page"
        AppRows.styleAction(ctx, b)
        b.setOnClickListener { open(ctx) }
        return b
    }

    fun open(ctx: Context) {
        try { show(ctx) } catch (_: Throwable) { }
    }

    private fun show(ctx: Context) {
        val text = AppUi.text(ctx)
        val dim = AppUi.dim(ctx)
        val okC = AppUi.ok(ctx)
        val dlg = Dialog(ctx, android.R.style.Theme_Material_NoActionBar)
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(AppUi.bg(ctx))
        root.setPadding(dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S2), dp(ctx, AppTokens.S3), 0)

        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(ctx)
        title.text = "PC key test"
        title.textSize = 20f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(text)
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val close = Button(ctx)
        close.isAllCaps = false
        close.text = "Close"
        close.minimumHeight = dp(ctx, AppTokens.TOUCH)
        AppRows.styleAction(ctx, close)
        close.setOnClickListener { try { dlg.dismiss() } catch (_: Throwable) { } }
        bar.addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(bar)

        val sv = ScrollView(ctx)
        val content = LinearLayout(ctx)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(0, dp(ctx, AppTokens.S2), 0, dp(ctx, AppTokens.S4))
        sv.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        fun tv(size: Float, color: Int, bold: Boolean = false, mono: Boolean = false): TextView {
            val t = TextView(ctx)
            t.textSize = size
            t.setTextColor(color)
            if (mono) t.typeface = Typeface.MONOSPACE
            if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
            content.addView(t, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return t
        }

        val intro = tv(12f, dim)
        intro.text = "1) Switch the keyboard to Jabra, PC mode ON. 2) Tap the box. 3) Tap PC keys or chips, or press Run self-test. " +
            "\"sent\" = what Jabra sent, \"got\" = what this box really received. Only PC-row keys are listed, never normal typing; nothing is saved."
        val input = EditText(ctx)
        input.hint = "Tap here, then use the PC rows"
        input.setSingleLine(true)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        input.setTextColor(text)
        input.setHintTextColor(dim)
        input.textSize = 18f
        input.typeface = Typeface.MONOSPACE
        input.background = AppUi.cardBg(ctx, 8)
        input.setPadding(dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S2), dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S2))
        input.minimumHeight = dp(ctx, AppTokens.TOUCH)
        input.contentDescription = "Test box for the PC keys"
        input.setOnKeyListener { _, code, ev ->
            try { PcLog.recv(ev.action, code, ev.metaState) } catch (_: Throwable) { }
            false   // never consume: the box behaves as a normal box
        }
        content.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val btns = LinearLayout(ctx)
        btns.orientation = LinearLayout.HORIZONTAL
        content.addView(btns, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        fun addBtn(label: String, onClick: () -> Unit) {
            val b = Button(ctx)
            b.isAllCaps = false
            b.text = label
            AppRows.styleAction(ctx, b)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(ctx, 2), dp(ctx, AppTokens.S2), dp(ctx, 2), 0)
            b.layoutParams = lp
            b.setOnClickListener { try { onClick() } catch (_: Throwable) { } }
            btns.addView(b)
        }

        val summary = tv(14f, okC, true)
        summary.setPadding(0, dp(ctx, AppTokens.S3), 0, dp(ctx, AppTokens.S1))
        val results = tv(13f, text, false, true)
        val logTitle = tv(12f, dim, true)
        logTitle.text = "Last 20 lines (sent / got):"
        logTitle.setPadding(0, dp(ctx, AppTokens.S3), 0, dp(ctx, AppTokens.S1))
        val log = tv(11f, text, false, true)

        fun refresh() {
            try {
                summary.text = PcLog.summary("Jabra test box")
                results.text = PcLog.testLines().joinToString("\n")
                log.text = PcLog.lines().reversed().joinToString("\n").ifEmpty { "(nothing yet)" }   // newest first
            } catch (_: Throwable) { }
        }

        addBtn("Run self-test") {
            input.requestFocus()
            PcLog.clear()
            val r = PcSelfTest.run
            if (r == null) { PcLog.add("self-test: Jabra keyboard is not running. Switch the keyboard to Jabra, tap the box, try again.") } else r()
        }
        addBtn("Clear") { PcLog.clear(); refresh() }
        addBtn("Copy summary") {
            try {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("PC key test", PcLog.summary("Jabra test box")))
                BackupStore.toast(ctx, "Copied")
            } catch (_: Throwable) { }
        }

        dlg.setContentView(root)
        try {
            val w = dlg.window
            w?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            w?.setBackgroundDrawable(ColorDrawable(AppUi.bg(ctx)))
            w?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        } catch (_: Throwable) { }
        dlg.setOnDismissListener { try { PcLog.setOpen(false) } catch (_: Throwable) { } }   // privacy: closing the page drops everything
        PcLog.setOpen(true)
        PcLog.setListener(Runnable { refresh() })
        refresh()
        dlg.show()
    }
}

/**
 * v0.17.52 (PC4): self-test of the PC key path. MgbIme sets [run] (in onCreate) so the page can ask the keyboard to run it.
 * It only runs when the open field belongs to THIS app (the page's own test box), never in another app. Each step sends one key through
 * InputSafe.pcKey (same path as the PC rows), waits 150 ms, then reads the box back (text before / after the cursor, selection).
 */
object PcSelfTest {
    var run: (() -> Unit)? = null

    private class Step(val name: String, val code: Int, val mods: Int, val check: (InputConnection) -> Boolean)

    private fun before(ic: InputConnection): String = try { ic.getTextBeforeCursor(1000, 0)?.toString() ?: "" } catch (_: Throwable) { "" }
    private fun after(ic: InputConnection): String = try { ic.getTextAfterCursor(1000, 0)?.toString() ?: "" } catch (_: Throwable) { "" }

    private val TEXT = "abc def"

    /** Helper steps are sent but not listed: Ctrl+C cannot be read back (it is judged together with Ctrl+V), End (collapse) only prepares the paste. */
    private fun helper(name: String): Boolean = name == "Ctrl+C" || name == "End (collapse)"

    fun execute(ic: InputConnection?, real: Boolean) {
        if (ic == null) { PcLog.add("self-test: no input connection"); return }
        val h = Handler(Looper.getMainLooper())
        try { ic.deleteSurroundingText(1000, 1000); ic.commitText(TEXT, 1) } catch (_: Throwable) { }
        val c = PcKeyPlan.MOD_CTRL
        val steps = listOf(
            Step("Left", KeyEvent.KEYCODE_DPAD_LEFT, 0) { before(it).length == TEXT.length - 1 },
            Step("Right", KeyEvent.KEYCODE_DPAD_RIGHT, 0) { before(it).length == TEXT.length },
            Step("Home", KeyEvent.KEYCODE_MOVE_HOME, 0) { before(it).isEmpty() && after(it) == TEXT },
            Step("End", KeyEvent.KEYCODE_MOVE_END, 0) { after(it).isEmpty() && before(it) == TEXT },
            Step("Ctrl+A", KeyEvent.KEYCODE_A, c) { (try { it.getSelectedText(0)?.toString() } catch (_: Throwable) { null }) == TEXT },
            Step("Ctrl+C", KeyEvent.KEYCODE_C, c) { true },   // cannot be read back here: checked together with Ctrl+V below
            Step("End (collapse)", KeyEvent.KEYCODE_MOVE_END, 0) { after(it).isEmpty() },
            Step("Ctrl+C then Ctrl+V", KeyEvent.KEYCODE_V, c) { before(it) + after(it) == TEXT + TEXT }
        )
        fun go(i: Int) {
            if (i >= steps.size) { PcLog.add("self-test finished"); return }
            val s = steps[i]
            try {
                val acc = InputSafe.pcKey(ic, s.code, s.mods, real)
                PcLog.sent(s.code, s.mods, real && s.mods != 0, acc)
            } catch (_: Throwable) { }
            h.postDelayed({
                try {
                    if (!helper(s.name)) PcLog.testAdd(s.name, s.check(ic))
                } catch (_: Throwable) { if (!helper(s.name)) PcLog.testAdd(s.name, false) }
                go(i + 1)
            }, 150L)
        }
        PcLog.testClear()
        h.postDelayed({ go(0) }, 150L)
    }
}
