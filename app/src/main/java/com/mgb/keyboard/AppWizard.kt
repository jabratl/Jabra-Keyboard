package com.mgb.keyboard

import android.content.Intent
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.AppTokens
import com.mgb.keyboard.typing.WizardPlan

class AppWizardView(val view: View, val refresh: () -> Unit)

/**
 * v0.13.0 (M3 of the "App UI" plan): first-run setup wizard card. It takes the place of the plain setup card on Home.
 * v0.17.84 (U8): 5 steps (Enable, Choose, Language, Theme, Test); Language / Theme can be skipped; a step that is still open after the button was tapped shows
 * "Try again" text; a phone that cannot open the system screen shows a manual how-to line; the Test step has "Choose again".
 * Read-only system checks (no permission). Prefs: wiz_on (set by SettingsActivity when the wizard is shown), wiz_done (set by the Done button).
 * Rules = typing/WizardPlan.java (pure, tested). Any error = null / hidden card (the caller shows the plain setup card).
 */
object AppWizard {

    /** (keyboard enabled, keyboard chosen). Same checks as SettingsActivity.buildSetupCard. Any problem = (false, false). */
    fun state(act: SettingsActivity): Pair<Boolean, Boolean> = try {
        val imm = act.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val en = imm.enabledInputMethodList.any { it.packageName == act.packageName }
        val sel = (Settings.Secure.getString(act.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: "").startsWith(act.packageName + "/")
        Pair(en, sel)
    } catch (_: Throwable) { Pair(false, false) }

    fun build(act: SettingsActivity, onDone: () -> Unit): AppWizardView? {
        try {
            val prefs = JabraPrefs(act)
            val d = act.resources.displayMetrics.density
            fun dp(v: Int): Int = (v * d).toInt()

            val card = LinearLayout(act)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(dp(AppTokens.S3), dp(AppTokens.S3), dp(AppTokens.S3), dp(AppTokens.S3))
            card.background = AppUi.cardBg(act, 12)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(AppTokens.S3); lp.bottomMargin = dp(AppTokens.S3)
            card.layoutParams = lp

            val head = TextView(act)
            head.textSize = AppTokens.T_TITLE.toFloat()
            head.setTypeface(null, android.graphics.Typeface.BOLD)
            head.setTextColor(AppUi.text(act))
            try { head.isAccessibilityHeading = true } catch (_: Throwable) { }
            card.addView(head)
            val prog = TextView(act)
            prog.setTextColor(AppUi.accent(act))
            prog.setPadding(0, dp(AppTokens.S1), 0, 0)
            card.addView(prog)
            val body = TextView(act)
            body.setTextColor(AppUi.text(act))
            body.setPadding(0, dp(AppTokens.S2), 0, dp(AppTokens.S1))
            card.addView(body)
            val test = EditText(act)
            test.hint = "Type here to test the keyboard"
            test.minHeight = dp(AppTokens.TOUCH)
            card.addView(test)
            // v0.17.84 (U8): failure line + Language choices + Theme chip (only the one of the current step is visible)
            val fail = TextView(act)
            fail.textSize = AppTokens.T_CAPTION.toFloat()
            try { fail.setTextColor(AppUi.warn(act)) } catch (_: Throwable) { }
            fail.setPadding(0, dp(AppTokens.S1), 0, dp(AppTokens.S1))
            fail.visibility = View.GONE
            card.addView(fail)
            val langRow = LinearLayout(act)
            langRow.orientation = LinearLayout.HORIZONTAL
            langRow.visibility = View.GONE
            card.addView(langRow)
            val themeBox = LinearLayout(act)
            themeBox.orientation = LinearLayout.HORIZONTAL
            themeBox.visibility = View.GONE
            card.addView(themeBox)
            val btn = Button(act)
            btn.isAllCaps = false
            btn.minHeight = dp(AppTokens.TOUCH); btn.minimumHeight = dp(AppTokens.TOUCH)
            card.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val btn2 = Button(act, null, android.R.attr.borderlessButtonStyle)   // v0.17.84 (U8): Skip / Choose again
            btn2.isAllCaps = false
            btn2.minHeight = dp(AppTokens.TOUCH); btn2.minimumHeight = dp(AppTokens.TOUCH)
            try { btn2.setTextColor(AppUi.accent(act)) } catch (_: Throwable) { }
            btn2.visibility = View.GONE
            card.addView(btn2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            var step = WizardPlan.ENABLE
            var tried = false   // v0.17.84 (U8): the button of Enable / Choose was tapped and the step is still open
            fun pillClick(v: View, desc: String, f: () -> Unit) {
                v.isClickable = true; v.isFocusable = true
                v.minimumHeight = dp(AppTokens.TOUCH)
                v.contentDescription = desc
                AppA11y.asButton(v)
                v.setOnClickListener { try { f() } catch (_: Throwable) { } }
            }
            fun themeNames(): Array<String> = try { Array(JabraThemes.ALL.size) { i -> JabraThemes.ALL[i].name } } catch (_: Throwable) { emptyArray() }
            fun drawLang() {
                langRow.removeAllViews()
                val names = try { com.mgb.keyboard.typing.LangNames.layoutChoices() } catch (_: Throwable) { arrayOf("Avro (phonetic)", "Probhat (fixed)") }
                val cur = WizardPlan.layoutIndex(prefs.str("bn_layout", "avro"))
                for (i in names.indices) {
                    val c = AppUi.pill(act, names[i], i == cur)
                    pillClick(c, names[i] + (if (i == cur) ", selected" else "")) { prefs.setStr("bn_layout", WizardPlan.layoutValue(i)); drawLang(); try { onDone() } catch (_: Throwable) { } }
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp.rightMargin = dp(AppTokens.S2)
                    langRow.addView(c, lp)
                }
            }
            fun drawTheme() {
                themeBox.removeAllViews()
                val names = themeNames()
                val cur = try { JabraThemes.index(prefs) } catch (_: Throwable) { 0 }
                val label = if (cur in names.indices) names[cur] else "Custom"
                val c = AppUi.pill(act, "Theme: " + label + "  \u203A", false)
                pillClick(c, "Theme " + label + ". Tap to try the next one") {
                    val n = names.size
                    if (n > 0) { prefs.setInt("theme", WizardPlan.nextTheme(cur, n)); drawTheme(); try { onDone() } catch (_: Throwable) { } }
                }
                themeBox.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            fun refresh() {
                try {
                    val st = state(act)
                    val prev = step
                    step = WizardPlan.step(st.first, st.second, prefs.bool("wiz_lang", false), prefs.bool("wiz_theme", false), prefs.bool("wiz_done", false))
                    if (step != prev) { tried = false; fail.visibility = View.GONE }   // a new step starts clean
                    if (step == WizardPlan.DONE) { card.visibility = View.GONE; return }
                    card.visibility = View.VISIBLE
                    head.text = WizardPlan.title(step)
                    prog.text = WizardPlan.progress(step)
                    prog.contentDescription = WizardPlan.title(step) + ". " + WizardPlan.progress(step).replace("\u2713", "done").replace("\u25CF", "now").replace("\u25CB", "next")
                    body.text = WizardPlan.text(step, tried)
                    btn.text = WizardPlan.button(step, tried)
                    val sec = WizardPlan.secondary(step)
                    btn2.text = sec
                    btn2.visibility = if (sec.isEmpty()) View.GONE else View.VISIBLE
                    test.visibility = if (step == WizardPlan.TEST) View.VISIBLE else View.GONE
                    langRow.visibility = if (step == WizardPlan.LANGUAGE) View.VISIBLE else View.GONE
                    themeBox.visibility = if (step == WizardPlan.THEME) View.VISIBLE else View.GONE
                    if (step == WizardPlan.LANGUAGE) drawLang()
                    if (step == WizardPlan.THEME) drawTheme()
                } catch (_: Throwable) { card.visibility = View.GONE }
            }
            fun failed() {   // v0.17.84 (U8): the phone refused to open the system screen = a plain how-to line, the button stays for another try
                fail.text = WizardPlan.failText(step)
                fail.visibility = if (fail.text.isEmpty()) View.GONE else View.VISIBLE
                tried = true
                refresh()
            }
            btn.setOnClickListener {
                try {
                    when (step) {
                        WizardPlan.ENABLE -> { fail.visibility = View.GONE; try { act.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)); tried = true; refresh() } catch (_: Throwable) { failed() } }
                        WizardPlan.CHOOSE -> { fail.visibility = View.GONE; try { (act.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker(); tried = true; refresh() } catch (_: Throwable) { failed() } }
                        WizardPlan.LANGUAGE -> { prefs.setBool("wiz_lang", true); refresh(); onDone() }
                        WizardPlan.THEME -> { prefs.setBool("wiz_theme", true); refresh(); onDone() }
                        WizardPlan.TEST -> { prefs.setBool("wiz_done", true); refresh(); onDone() }
                    }
                } catch (_: Throwable) { }
            }
            btn2.setOnClickListener {   // v0.17.84 (U8): Skip (nothing is changed) / Choose again
                try {
                    when (step) {
                        WizardPlan.LANGUAGE -> { prefs.setBool("wiz_lang", true); refresh(); onDone() }
                        WizardPlan.THEME -> { prefs.setBool("wiz_theme", true); refresh(); onDone() }
                        WizardPlan.TEST -> (act.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                    }
                } catch (_: Throwable) { }
            }
            refresh()
            return AppWizardView(card) { refresh() }
        } catch (_: Throwable) { return null }
    }
}
