package com.mgb.keyboard

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.typing.AppTokens
import com.mgb.keyboard.typing.KeyMapData
import com.mgb.keyboard.typing.PracticePlan

/**
 * v0.16.5 (plan X, X5): the "Practice" page (বাংলা লেখা চর্চা) of the app screen.
 * A button (Typing page > Languages & layouts > section "Bangla", under the key map button) opens a full-screen page:
 * a Bangla word, a box where you type the roman letters (the Bangলা key map), live feedback:
 *   green = the engine makes exactly the target, red = shows what the engine made from your letters.
 * Next / Skip (new word, streak back to 0), Hint (shows one roman that works), streak + best counter.
 * Logic + word list = typing/PracticePlan.java (every word is checked against the real engine in tools/PracticePlanTest.java).
 * Only data: pref kp_best (best streak, local, not in profiles / backups of settings). No network, no permission.
 * The box is a normal EditText of the page; use the phone keyboard (any keyboard, roman letters). Everything in try / catch.
 */
object PracticePage {
    private fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun button(ctx: Context): Button {
        val b = Button(ctx)
        b.isAllCaps = false
        b.text = "Practice typing (বাংলা লেখা চর্চা)"
        b.contentDescription = "Open the Bangla typing practice"
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
        val accent = AppUi.accent(ctx)
        val okC = AppUi.ok(ctx)
        val warnC = AppUi.warn(ctx)
        val prefs = JabraPrefs(ctx)

        var cur = PracticePlan.next(-1, System.nanoTime())
        var streak = 0
        var best = prefs.int("kp_best", 0).coerceAtLeast(0)

        val dlg = Dialog(ctx, android.R.style.Theme_Material_NoActionBar)
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(AppUi.bg(ctx))
        root.setPadding(dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S2), dp(ctx, AppTokens.S3), 0)

        // ---- top bar ----
        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(ctx)
        title.text = "Practice (বাংলা লেখা চর্চা)"
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

        fun tv(size: Float, color: Int, bold: Boolean = false): TextView {
            val t = TextView(ctx)
            t.textSize = size
            t.setTextColor(color)
            if (bold) t.setTypeface(null, Typeface.BOLD)
            t.gravity = Gravity.CENTER_HORIZONTAL
            content.addView(t, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return t
        }

        val streakView = tv(14f, dim)
        val intro = tv(12f, dim)
        intro.text = "Type this word with roman letters (Bangলা key map). Capital letters matter."
        intro.setPadding(0, dp(ctx, AppTokens.S2), 0, dp(ctx, AppTokens.S2))
        val target = tv(40f, text, true)
        target.setPadding(0, dp(ctx, AppTokens.S3), 0, dp(ctx, AppTokens.S3))
        val rule = tv(12f, dim)

        val input = EditText(ctx)
        input.hint = "Type here, e.g. ami"
        input.setSingleLine(true)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        input.setTextColor(text)
        input.setHintTextColor(dim)
        input.textSize = 18f
        input.typeface = Typeface.MONOSPACE
        input.background = AppUi.cardBg(ctx, 8)
        input.setPadding(dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S2), dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S2))
        input.minimumHeight = dp(ctx, AppTokens.TOUCH)
        input.contentDescription = "Type the roman letters for the shown Bangla word"
        content.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val made = tv(22f, text)
        made.setPadding(0, dp(ctx, AppTokens.S3), 0, dp(ctx, AppTokens.S1))
        val verdict = tv(14f, dim, true)
        val hint = tv(13f, accent)
        hint.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        hint.setPadding(0, dp(ctx, AppTokens.S1), 0, dp(ctx, AppTokens.S1))

        val btns = LinearLayout(ctx)
        btns.orientation = LinearLayout.HORIZONTAL
        content.addView(btns, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        fun addBtn(label: String, onClick: () -> Unit): Button {
            val b = Button(ctx)
            b.isAllCaps = false
            b.text = label
            AppRows.styleAction(ctx, b)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(ctx, 2), dp(ctx, AppTokens.S2), dp(ctx, 2), 0)
            b.layoutParams = lp
            b.setOnClickListener { try { onClick() } catch (_: Throwable) { } }
            btns.addView(b)
            return b
        }

        fun showStreak() { streakView.text = PracticePlan.streakText(streak, best) }

        fun newWord() {
            cur = PracticePlan.next(cur, System.nanoTime())
            val it = PracticePlan.at(cur)
            target.text = KeyMapData.shown(it.bangla)
            rule.text = if (it.rule.isNotEmpty()) "Hint: " + it.rule else ""
            input.setText("")
            made.text = ""
            verdict.text = ""
            hint.text = ""
            showStreak()
        }

        var counted = false   // the streak counts a word only once, even if you edit the letters after a right answer

        fun evaluate() {
            val it = PracticePlan.at(cur)
            val r = PracticePlan.check(it.bangla, input.text.toString())
            when (r.state) {
                PracticePlan.RIGHT -> {
                    made.text = KeyMapData.shown(r.made)
                    made.setTextColor(okC)
                    verdict.text = "Right! Tap Next."
                    verdict.setTextColor(okC)
                    if (!counted) {
                        counted = true
                        val s = PracticePlan.streak(streak, best, true)
                        streak = s[0]
                        if (s[1] > best) { best = s[1]; prefs.setInt("kp_best", best) }
                        showStreak()
                    }
                }
                PracticePlan.WRONG -> {
                    made.text = KeyMapData.shown(r.made)
                    made.setTextColor(warnC)
                    verdict.text = "Not yet. Your letters make the text above."
                    verdict.setTextColor(warnC)
                }
                else -> {
                    made.text = ""
                    verdict.text = ""
                }
            }
        }

        addBtn("Next") { counted = false; newWord() }
        addBtn("Skip") { streak = 0; counted = false; newWord() }
        addBtn("Hint") { hint.text = "Type: " + PracticePlan.at(cur).hint; if (!counted) { streak = 0; showStreak() } }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
            override fun afterTextChanged(s: Editable?) { try { evaluate() } catch (_: Throwable) { } }
        })

        newWord()
        dlg.setContentView(root)
        try {
            val w = dlg.window
            w?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            w?.setBackgroundDrawable(ColorDrawable(AppUi.bg(ctx)))
            w?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        } catch (_: Throwable) { }
        dlg.show()
    }
}
