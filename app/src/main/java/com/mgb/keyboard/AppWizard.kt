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
 * v0.13.0 (M3 of the "App UI" plan): first-run setup wizard card, 3 steps (Enable, Choose, Test). It takes the place of the plain setup card on Home.
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
            head.textSize = 16f
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
            val btn = Button(act)
            btn.isAllCaps = false
            btn.minHeight = dp(AppTokens.TOUCH); btn.minimumHeight = dp(AppTokens.TOUCH)
            card.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            var step = WizardPlan.ENABLE
            fun refresh() {
                try {
                    val st = state(act)
                    step = WizardPlan.step(st.first, st.second, prefs.bool("wiz_done", false))
                    if (step == WizardPlan.DONE) { card.visibility = View.GONE; return }
                    card.visibility = View.VISIBLE
                    head.text = WizardPlan.title(step)
                    prog.text = WizardPlan.progress(step)
                    prog.contentDescription = WizardPlan.title(step) + ". " + WizardPlan.progress(step).replace("\u2713", "done").replace("\u25CF", "now").replace("\u25CB", "next")
                    body.text = WizardPlan.text(step)
                    btn.text = WizardPlan.button(step)
                    test.visibility = if (step == WizardPlan.TEST) View.VISIBLE else View.GONE
                } catch (_: Throwable) { card.visibility = View.GONE }
            }
            btn.setOnClickListener {
                try {
                    when (step) {
                        WizardPlan.ENABLE -> act.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        WizardPlan.CHOOSE -> (act.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                        WizardPlan.TEST -> { prefs.setBool("wiz_done", true); refresh(); onDone() }
                    }
                } catch (_: Throwable) { }
            }
            refresh()
            return AppWizardView(card) { refresh() }
        } catch (_: Throwable) { return null }
    }
}
